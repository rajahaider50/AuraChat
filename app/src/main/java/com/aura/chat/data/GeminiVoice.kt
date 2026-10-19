package com.aura.chat.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Voices exposed by the official Gemini TTS models. */
object GeminiVoices {
    val all = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
        "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
    )
}

/**
 * Official Gemini text-to-speech (native audio output, 24 kHz PCM).
 * No Android/system TTS engine is used anywhere in the app.
 */
class GeminiVoice(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var cancelled = false

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    var isSpeaking: Boolean = false
        private set

    private var focusRequest: AudioFocusRequest? = null

    fun stop() {
        cancelled = true
        try {
            track?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause()
                it.flush()
                it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        track = null
        isSpeaking = false
        abandonFocus()
    }

    /**
     * Speaks [text] with a Gemini prebuilt voice. Throws [ApiException] with a
     * readable message when the key, model or voice is invalid.
     */
    suspend fun speak(
        text: String,
        apiKey: String,
        voice: String = "Kore",
        model: String = DEFAULT_MODEL,
    ) = withContext(Dispatchers.IO) {
        val clean = sanitize(text)
        if (clean.isBlank()) return@withContext
        if (apiKey.isBlank()) {
            throw ApiException("Add a Gemini API key in Settings → Voice & calls to hear Aura.")
        }
        cancelled = false
        isSpeaking = true
        requestFocus()
        try {
            for (chunk in chunks(clean)) {
                if (cancelled) break
                val pcm = requestPcm(chunk, apiKey, voice, model)
                if (cancelled) break
                playPcm(pcm)
            }
        } finally {
            isSpeaking = false
            releaseTrack()
            abandonFocus()
        }
    }

    private fun requestPcm(text: String, apiKey: String, voice: String, model: String): ByteArray {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") { addJsonObject { put("text", text) } }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                    }
                }
            }
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { DEFAULT_MODEL }}:generateContent"
        val request = try {
            Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey.trim())
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: Exception) {
            throw ApiException("Invalid Gemini TTS model name: ${e.message}")
        }

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(errorMessage(raw, response.code))
            }
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: throw ApiException("Gemini returned an unreadable TTS response.")
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?: throw ApiException(blockReason(root) ?: "Gemini returned no audio. Check the voice name.")
            val bytes = java.io.ByteArrayOutputStream()
            var mime = ""
            parts.forEach { part ->
                val inline = (part as? JsonObject)?.get("inlineData")?.jsonObject
                    ?: (part as? JsonObject)?.get("inline_data")?.jsonObject
                val data = inline?.get("data")?.jsonPrimitive?.contentOrNull
                if (!data.isNullOrBlank()) {
                    mime = inline["mimeType"]?.jsonPrimitive?.contentOrNull
                        ?: inline["mime_type"]?.jsonPrimitive?.contentOrNull
                        ?: mime
                    bytes.write(Base64.decode(data, Base64.DEFAULT))
                }
            }
            if (bytes.size() == 0) {
                throw ApiException(blockReason(root) ?: "Gemini returned an empty audio clip.")
            }
            lastMime = mime
            return bytes.toByteArray()
        }
    }

    @Volatile
    private var lastMime: String = ""

    private fun playPcm(pcm: ByteArray) {
        val rate = Regex("rate=(\\d+)").find(lastMime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
        val frames = pcm.size / 2
        if (frames <= 0) return
        val minBuffer = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, 64 * 1024)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
        track = audioTrack
        try {
            audioTrack.play()
            var offset = 0
            while (offset < pcm.size) {
                if (cancelled) break
                val count = minOf(4096, pcm.size - offset)
                val written = audioTrack.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            // Let the hardware buffer finish before releasing the track.
            val deadline = System.currentTimeMillis() + 4000
            while (!cancelled && audioTrack.playbackHeadPosition < frames && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
        } finally {
            releaseTrack()
        }
    }

    private fun releaseTrack() {
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    private fun requestFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        }
        focusRequest = null
    }

    private fun sanitize(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " code block ")
        .replace(Regex("[*#`_>~|]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun chunks(text: String, max: Int = 1800): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.!?۔؟])\\s+"))
        val current = StringBuilder()
        sentences.forEach { sentence ->
            if (current.length + sentence.length + 1 > max && current.isNotEmpty()) {
                out += current.toString().trim()
                current.clear()
            }
            if (sentence.length > max) {
                sentence.chunked(max).forEach {
                    if (current.isNotEmpty()) {
                        out += current.toString().trim()
                        current.clear()
                    }
                    out += it
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) out += current.toString().trim()
        return out.ifEmpty { listOf(text.take(max)) }
    }

    private fun blockReason(root: JsonObject): String? {
        val reason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        return if (reason.isNullOrBlank()) null else "Gemini blocked the voice request ($reason)."
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val hint = when (code) {
            400 -> " (check the voice name and TTS model)"
            401, 403 -> " (check your Gemini API key and that the Generative Language API is enabled)"
            404 -> " (this Gemini TTS model is not available on your key)"
            429 -> " (rate limit reached, try again shortly)"
            else -> ""
        }
        return "Gemini TTS HTTP $code$hint" + if (detail.isNullOrBlank()) "" else ": $detail"
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash-preview-tts"
        const val DEFAULT_VOICE = "Kore"
    }
}
