package com.aura.chat.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio helpers. WhatsApp voice notes, AMR, OGG/Opus, M4A and WAV are played
 * directly when the platform supports them. Anything the platform cannot decode
 * is transparently transcoded to PCM/WAV with MediaCodec (no Python or ffmpeg
 * binary needed on the phone).
 */
object AudioFiles {
    fun cacheDir(context: Context): File =
        File(context.cacheDir, "audio").apply { mkdirs() }

    fun transcodeToWav(input: File, output: File): Boolean {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var raf: RandomAccessFile? = null
        return try {
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            var trackIndex = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val m = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (m.startsWith("audio/")) {
                    trackIndex = i
                    mime = m
                    break
                }
            }
            if (trackIndex < 0 || mime.isBlank()) return false
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            output.parentFile?.mkdirs()
            raf = RandomAccessFile(output, "rw")
            raf.setLength(0)
            raf.write(ByteArray(44)) // placeholder WAV header
            var dataBytes = 0L
            var sampleRate = 24000
            var channels = 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                floatToPcm16(buffer)
                            } else {
                                ByteArray(info.size).also { buffer.get(it) }
                            }
                            raf.write(bytes)
                            dataBytes += bytes.size
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            writeWavHeader(raf, dataBytes, sampleRate, channels)
            dataBytes > 0
        } catch (_: Exception) {
            false
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
        }
    }

    private fun floatToPcm16(buffer: ByteBuffer): ByteArray {
        val floats = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val out = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        val f = FloatArray(floats.remaining())
        floats.get(f)
        f.forEach { value ->
            val clamped = value.coerceIn(-1f, 1f)
            out.putShort((clamped * 32767f).toInt().toShort())
        }
        return out.array()
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataBytes: Long, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(header.array())
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}

/**
 * Small one-at-a-time audio preview used by the media sheet and chat bubbles.
 * Falls back to a decoded WAV when the platform rejects the original container.
 */
class AudioPreview(private val context: Context) {
    private var player: MediaPlayer? = null
    var playingPath: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    fun toggle(file: File, onError: (String) -> Unit = {}) {
        if (playingPath == file.absolutePath) {
            stop()
            return
        }
        stop()
        error = null
        if (!file.exists()) {
            onError("This file is missing from local storage.")
            return
        }
        start(file, allowFallback = true, onError = onError)
    }

    private fun start(file: File, allowFallback: Boolean, onError: (String) -> Unit) {
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                it.start()
                playingPath = file.absolutePath
            }
            mp.setOnCompletionListener {
                playingPath = null
                it.release()
                if (player === it) player = null
            }
            mp.setOnErrorListener { _, _, _ ->
                if (allowFallback) {
                    releaseAndTranscode(file, onError)
                } else {
                    playingPath = null
                    error = "This audio format could not be played."
                    onError("This audio format could not be played.")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            if (allowFallback) {
                releaseAndTranscode(file, onError)
            } else {
                error = e.message ?: "Could not play this audio."
                onError(error.orEmpty())
            }
        }
    }

    private fun releaseAndTranscode(file: File, onError: (String) -> Unit): Boolean {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingPath = null
        val wav = File(AudioFiles.cacheDir(context), file.nameWithoutExtension + "-aura.wav")
        if (!wav.exists() || wav.length() < 100) {
            val ok = AudioFiles.transcodeToWav(file, wav)
            if (!ok || !wav.exists() || wav.length() < 100) {
                error = "This audio format is not supported and could not be converted."
                onError(error.orEmpty())
                return false
            }
        }
        start(wav, allowFallback = false, onError = onError)
        return true
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingPath = null
    }

    fun release() = stop()
}
