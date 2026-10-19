package com.aura.chat.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Continuous bidirectional Gemini Live call. It owns one mic and one speaker stream for the whole call. */
class GeminiLiveCall(
    private val apiKey: String,
    private val model: String,
    private val voiceName: String,
    private val systemInstruction: String,
    private val scope: CoroutineScope,
    private val onInputTranscript: (String) -> Unit,
    private val onOutputTranscript: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var captureJob: Job? = null
    @Volatile private var connected = false
    @Volatile private var stopped = false

    fun connect() {
        if (apiKey.isBlank()) { onError("Add a Gemini API key in Settings → Voice & calls."); return }
        stopped = false
        val endpoint = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${apiKey.trim()}"
        val request = Request.Builder().url(endpoint).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                webSocket.send(setupMessage())
                startCapture()
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                if (!stopped) onError(t.message ?: "Gemini Live connection failed")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connected = false }
        })
    }

    fun mute(muted: Boolean) {
        if (muted) stopCapture() else if (connected) startCapture()
    }

    fun close() {
        stopped = true
        connected = false
        stopCapture()
        socket?.close(1000, "Call ended")
        socket = null
        releaseTrack()
    }

    private fun setupMessage(): String = JSONObject().put("setup", JSONObject()
        .put("model", "models/${model.removePrefix("models/").takeIf { it.contains("native-audio", true) || it.contains("live", true) } ?: "gemini-2.5-flash-native-audio-preview-12-2025"}")
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
        .put("generationConfig", JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)))))
        .put("inputAudioTranscription", JSONObject())
        .put("outputAudioTranscription", JSONObject())
    ).toString()

    private fun startCapture() {
        if (captureJob != null || stopped) return
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) { onError("This device cannot open a 16 kHz microphone stream."); return }
        try {
            record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minimum * 2)
            record?.startRecording()
            AcousticEchoCanceler.create(record?.audioSessionId ?: 0)?.enabled = true
        } catch (e: Exception) { onError("Microphone could not start: ${e.message}"); return }
        captureJob = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(minimum / 2)
            while (connected && !stopped && record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = record?.read(samples, 0, samples.size) ?: 0
                if (count <= 0) continue
                val pcm = ByteArray(count * 2)
                for (index in 0 until count) {
                    pcm[index * 2] = (samples[index].toInt() and 0xff).toByte()
                    pcm[index * 2 + 1] = (samples[index].toInt() shr 8).toByte()
                }
                socket?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                    .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=16000"))).toString())
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel(); captureJob = null
        runCatching { record?.stop() }
        record?.release(); record = null
    }

    private fun handleMessage(raw: String) {
        runCatching {
            val root = JSONObject(raw)
            val server = root.optJSONObject("serverContent") ?: return
            val input = server.optJSONObject("inputTranscription")?.optString("text").orEmpty()
            val output = server.optJSONObject("outputTranscription")?.optString("text").orEmpty()
            if (input.isNotBlank()) onInputTranscript(input)
            if (output.isNotBlank()) onOutputTranscript(output)
            val parts = server.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val data = inline.optString("data")
                if (data.isNotBlank()) playPcm(Base64.decode(data, Base64.DEFAULT))
            }
            if (server.optBoolean("turnComplete", false)) onTurnComplete()
        }.onFailure { if (!stopped) onError("Invalid Gemini Live response: ${it.message}") }
    }

    private fun playPcm(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (track == null) {
            val size = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(64 * 1024)
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build()
            track?.play()
        }
        track?.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
    }

    private fun releaseTrack() {
        runCatching { track?.pause(); track?.flush(); track?.stop(); track?.release() }
        track = null
    }
}
