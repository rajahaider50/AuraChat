package com.aura.chat.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.data.ApiException
import com.aura.chat.data.ApiMessage
import com.aura.chat.data.AppSettings
import com.aura.chat.data.MessageEntity
import com.aura.chat.data.Personas
import com.aura.chat.ui.shareText
import com.aura.chat.ui.theme.SystemBarIcons
import com.aura.chat.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CBg = Color(0xFF05040D)
private val CPink = Color(0xFFFF2E93)
private val CCyan = Color(0xFF00E5FF)
private val CViolet = Color(0xFF7A3CFF)
private val CText = Color(0xFFF4F1FF)
private val CDim = Color(0xFF9B94BE)

private data class CallLang(val label: String, val tag: String)

private val CallLangs = listOf(
    CallLang("English", "en-US"),
    CallLang("اردو", "ur-PK"),
    CallLang("العربية", "ar-SA"),
    CallLang("हिन्दी", "hi-IN"),
)

private const val CALL_PROMPT =
    "You are Aura on a live, hands-free phone call. Everything you write is spoken aloud by a natural voice. " +
        "Keep answers to one to three short, natural spoken sentences unless the user explicitly asks for detail. " +
        "Never use markdown, bullet lists, emojis, code fences or raw URLs. " +
        "Be warm, attentive and concise. Reply in the same language the user speaks. " +
        "You have long-term memory about the user when it is provided; use it naturally and never recite it."

private enum class CallPhase { Connecting, Listening, Thinking, Speaking, Ended }

private data class CallLine(val user: Boolean, val text: String)

private enum class CallRoute(val label: String, val setting: Int) {
    Auto("Auto", 0),
    Speaker("Speaker", 1),
    Earpiece("Earpiece", 2),
    Bluetooth("Bluetooth", 3);

    companion object {
        fun fromSetting(value: Int): CallRoute = entries.firstOrNull { it.setting == value } ?: Auto
    }
}

/**
 * Owns the full call: continuous speech recognition (auto-restarts, never a
 * push-to-talk mic), the model request, Gemini native voice, audio routing,
 * camera state, end-call commands and persistent call memory.
 */
private class CallController(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val container: AppContainer,
    private val onEnd: () -> Unit,
) {
    var phase by mutableStateOf(CallPhase.Connecting)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var muted by mutableStateOf(false)
        private set
    var route by mutableStateOf(CallRoute.Auto)
        private set
    var cameraOn by mutableStateOf(false)
        private set
    var frontCamera by mutableStateOf(true)
        private set
    var duration by mutableStateOf(0L)
        private set
    var cameraRequestTick by mutableIntStateOf(0)
        private set
    var lang by mutableStateOf(CallLangs.first())
        private set
    val lines = mutableStateListOf<CallLine>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var recognizer: SpeechRecognizer? = null
    private var job: Job? = null
    private var timerJob: Job? = null
    private var restartJob: Job? = null
    private var watchdogJob: Job? = null
    private var startedAt = 0L
    private var conversationId: String? = null
    private val history = mutableListOf<ApiMessage>()
    private var ending = false
    private var released = false

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (route != CallRoute.Auto) return
            val value = event?.values?.firstOrNull() ?: return
            val near = value < (proximity?.maximumRange ?: 5f)
            try {
                audioManager.isSpeakerphoneOn = !near
            } catch (_: Exception) {
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start(settings: AppSettings) {
        if (released) return
        runCatching { container.voice.stop() }
        route = CallRoute.fromSetting(settings.callRoute)
        frontCamera = settings.callCameraFront
        applyRoute()
        startedAt = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && !released) {
                duration = (System.currentTimeMillis() - startedAt) / 1000
                delay(1000)
            }
        }
        if (settings.geminiKey.isBlank()) {
            note = "Add a Gemini API key in Settings → Voice & calls for Aura's official voice."
        }
        listen()
    }

    fun setLang(l: CallLang) {
        lang = l
        if (phase == CallPhase.Listening) {
            restartListening(250)
        }
    }

    private fun listen() {
        if (released || ending) return
        if (muted) {
            phase = CallPhase.Listening
            partial = ""
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            error = "Speech recognition is not available on this device."
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }
        error = null
        partial = ""
        phase = CallPhase.Listening
        try {
            recognizer?.cancel()
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            restartListening(700)
        }
        armWatchdog()
    }

    /** Keeps the call alive when a recognizer times out on a long silence. */
    private fun armWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(35_000)
            if (phase == CallPhase.Listening && partial.isBlank()) {
                try { recognizer?.cancel() } catch (_: Exception) {}
                restartListening(300)
            }
        }
    }

    private fun restartListening(delayMs: Long = 450) {
        if (released || ending || muted) return
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (!released && !ending && !muted && phase == CallPhase.Listening) listen()
        }
    }

    private fun resumeListening(delayMs: Long = 350) {
        if (released || ending) return
        phase = CallPhase.Listening
        if (muted) return
        restartListening(delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (phase == CallPhase.Listening) partial = ""
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            level = 0f
            if (phase == CallPhase.Listening) {
                // Results normally arrive right after this; restart only if they do not.
                val before = partial
                scope.launch {
                    delay(1_400)
                    if (phase == CallPhase.Listening && partial == before) {
                        restartListening(200)
                    }
                }
            }
        }

        override fun onError(errorCode: Int) {
            level = 0f
            partial = ""
            if (phase == CallPhase.Thinking || phase == CallPhase.Speaking || ending) return
            when (errorCode) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_SERVER,
                -> {
                    phase = CallPhase.Listening
                    restartListening(500)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    phase = CallPhase.Listening
                    error = "Microphone permission is required for calls."
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    error = "Speech recognition lost its network connection. Retrying…"
                    phase = CallPhase.Listening
                    restartListening(2_000)
                }
                else -> {
                    phase = CallPhase.Listening
                    restartListening(900)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            level = 0f
            partial = ""
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isEmpty()) {
                phase = CallPhase.Listening
                restartListening(300)
            } else {
                handleUser(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleUser(text: String) {
        if (ended()) return
        try { recognizer?.cancel() } catch (_: Exception) {}
        watchdogJob?.cancel()
        if (handleCommand(text)) return
        lines.add(CallLine(true, text))
        partial = ""
        phase = CallPhase.Thinking
        job?.cancel()
        job = scope.launch { ask(text) }
    }

    private suspend fun ask(userText: String) {
        try {
            val repo = container.repo
            val provider = repo.activeProvider()
                ?: throw ApiException("No AI provider is connected. Open Settings → Providers and add one.")
            val model = provider.model
            if (model.isBlank()) throw ApiException("No model selected. Open Settings → Model and pick one.")
            val settings = container.settings.flow.first()
            val memory = container.memory.contextFor(userText)
            val skills = container.skills.promptForEnabled(6000)
            val system = buildString {
                append(CALL_PROMPT)
                if (memory.isNotBlank()) append("\n\n").append(memory)
                if (skills.isNotBlank()) append("\n\n").append(skills)
            }
            val payload = buildList {
                add(ApiMessage("system", system))
                addAll(history.takeLast(18))
                add(ApiMessage("user", userText))
            }
            lines.add(CallLine(false, ""))
            val index = lines.lastIndex
            val buffer = StringBuilder()
            container.api.stream(provider, model, payload, settings.temperature).collect { piece ->
                buffer.append(piece)
                lines[index] = CallLine(false, buffer.toString())
            }
            val reply = buffer.toString().trim()
            if (reply.isEmpty()) throw ApiException("The model returned an empty reply.")
            history.add(ApiMessage("user", userText))
            history.add(ApiMessage("assistant", reply))
            persist(userText, reply)
            speakReply(reply, settings)
            if (settings.autoMemory && settings.memoryEnabled) {
                container.appScope.launch {
                    runCatching {
                        container.memory.extractAndSave(
                            provider, model, userText, reply, "call", conversationId,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (lines.lastOrNull()?.let { !it.user && it.text.isEmpty() } == true) lines.removeAt(lines.lastIndex)
            error = e.message ?: "Something went wrong."
            resumeListening(1_200)
        }
    }

    private suspend fun speakReply(reply: String, settings: AppSettings) {
        val key = container.repo.geminiKey()
        if (key.isBlank()) {
            note = "Reply is shown, but voice needs a Gemini API key in Settings → Voice & calls."
            resumeListening(1_500)
            return
        }
        phase = CallPhase.Speaking
        try {
            container.voice.speak(reply, key, settings.geminiVoice, settings.geminiModel)
        } catch (e: Exception) {
            error = e.message ?: "Gemini voice could not speak the reply."
        }
        if (!ended()) resumeListening(250)
    }

    private fun handleCommand(raw: String): Boolean {
        val text = raw.lowercase(Locale.ROOT).trim()
        val endingPhrases = listOf(
            "end call", "end the call", "hang up", "hangup", "cut the call", "call cut", "disconnect the call",
            "end this call", "close the call", "call band", "call end", "call khatam", "کال بند", "کال کٹ",
            "کال ختم", "فون بند", "कॉल बंद", "कॉल काट", "फोन काट", "اقفل المكالمة", "انهي المكالمة", "إنهاء المكالمة",
        )
        if (endingPhrases.any { text.contains(it) }) {
            endWithGoodbye()
            return true
        }
        if (Regex("\\b(remember|yaad rakho|یاد رکھو|याद रखो|تذكر)\\b").containsMatchIn(text)) {
            val fact = raw.substringAfter(':', raw).trim()
            scope.launch {
                container.memory.rememberExplicit(fact.ifBlank { raw }, "call", conversationId)
                speakCommandAck("Saved to long-term memory.")
            }
            return true
        }
        if (text == "mute" || text.contains("mic off") || text.contains("مائیک بند") || text.contains("म्यूट")) {
            muted = true
            try { recognizer?.cancel() } catch (_: Exception) {}
            phase = CallPhase.Listening
            note = "Microphone muted. Tap the mic to resume."
            scope.launch { speakCommandAck("Microphone muted.") }
            return true
        }
        if (text.contains("unmute") || text.contains("mic on") || text.contains("مائیک آن") || text.contains("अनम्यूट")) {
            muted = false
            note = null
            scope.launch { speakCommandAck("Microphone on.") }
            return true
        }
        if (text.contains("speaker")) {
            setRoute(CallRoute.Speaker)
            scope.launch { speakCommandAck("Speaker on.") }
            return true
        }
        if (text.contains("earpiece") || text.contains("phone mode")) {
            setRoute(CallRoute.Earpiece)
            scope.launch { speakCommandAck("Earpiece.") }
            return true
        }
        if (text.contains("bluetooth")) {
            setRoute(CallRoute.Bluetooth)
            scope.launch { speakCommandAck("Bluetooth.") }
            return true
        }
        if (text.contains("front camera")) {
            frontCamera = true
            cameraOn = true
            cameraRequestTick++
            scope.launch { speakCommandAck("Front camera.") }
            return true
        }
        if (text.contains("back camera")) {
            frontCamera = false
            cameraOn = true
            cameraRequestTick++
            scope.launch { speakCommandAck("Back camera.") }
            return true
        }
        if (text.contains("camera on") || text == "camera") {
            cameraOn = true
            cameraRequestTick++
            scope.launch { speakCommandAck("Camera on.") }
            return true
        }
        if (text.contains("camera off") || text.contains("کیمرہ بند") || text.contains("कैमरा बंद")) {
            cameraOn = false
            scope.launch { speakCommandAck("Camera off.") }
            return true
        }
        return false
    }

    private suspend fun speakCommandAck(text: String) {
        if (ended()) return
        val settings = container.settings.flow.first()
        val key = container.repo.geminiKey()
        if (key.isNotBlank()) {
            phase = CallPhase.Speaking
            runCatching { container.voice.speak(text, key, settings.geminiVoice, settings.geminiModel) }
        }
        if (!muted) resumeListening(250)
    }

    fun endWithGoodbye() {
        if (ending || released) return
        ending = true
        job?.cancel()
        watchdogJob?.cancel()
        restartJob?.cancel()
        try { recognizer?.cancel() } catch (_: Exception) {}
        phase = CallPhase.Thinking
        job = scope.launch {
            val settings = container.settings.flow.first()
            val farewell = generateFarewell()
            lines.add(CallLine(false, farewell))
            val key = container.repo.geminiKey()
            if (key.isNotBlank()) {
                phase = CallPhase.Speaking
                runCatching { container.voice.speak(farewell, key, settings.geminiVoice, settings.geminiModel) }
            } else {
                delay(900)
            }
            persistFarewell(farewell)
            saveCallMemory()
            finish()
        }
    }

    private suspend fun generateFarewell(): String {
        val repo = container.repo
        val provider = repo.activeProvider() ?: return "Goodbye. Talk to you soon."
        if (provider.model.isBlank()) return "Goodbye. Talk to you soon."
        val context = lines.takeLast(6).joinToString("\n") {
            (if (it.user) "User" else "Aura") + ": " + it.text
        }
        return runCatching {
            container.api.completeText(
                provider,
                provider.model,
                "You are ending a warm voice call. Reply with exactly one short, natural farewell sentence. " +
                    "No quotes, no markdown, no explanation. Use the language of the conversation.",
                "Conversation so far:\n$context\n\nThe user asked to end the call.",
                0.6f,
            ).trim().take(300)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Goodbye. Talk to you soon."
    }

    private fun finish() {
        if (released) return
        phase = CallPhase.Ended
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        cleanupAudio()
        mainHandler.postDelayed({ if (!released) onEnd() }, 350)
    }

    fun toggleMute() {
        if (muted) {
            muted = false
            note = null
            error = null
            resumeListening(150)
        } else {
            muted = true
            try { recognizer?.cancel() } catch (_: Exception) {}
            phase = CallPhase.Listening
            note = "Microphone muted. Tap the mic to resume."
        }
    }

    fun setRoute(newRoute: CallRoute) {
        route = newRoute
        applyRoute()
    }

    private fun applyRoute() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            sensorManager.unregisterListener(proximityListener)
            when (route) {
                CallRoute.Speaker -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                CallRoute.Earpiece -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
                CallRoute.Bluetooth -> {
                    try {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    } catch (e: SecurityException) {
                        note = "Allow Bluetooth access in phone settings to use a Bluetooth headset."
                    }
                    audioManager.isSpeakerphoneOn = false
                }
                CallRoute.Auto -> {
                    if (proximity != null) {
                        sensorManager.registerListener(proximityListener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
                        audioManager.isSpeakerphoneOn = false
                    } else {
                        audioManager.isSpeakerphoneOn = true
                    }
                }
            }
        } catch (e: Exception) {
            note = "Could not change the audio route: ${e.message}"
        }
    }

    fun toggleCamera(permissionGranted: Boolean) {
        if (cameraOn) {
            cameraOn = false
        } else {
            if (permissionGranted) cameraOn = true else cameraRequestTick++
        }
    }

    fun switchCamera() {
        frontCamera = !frontCamera
        if (!cameraOn) cameraOn = true
    }

    fun onCameraPermission(granted: Boolean) {
        if (granted) {
            cameraOn = true
            error = null
        } else {
            cameraOn = false
            error = "Camera permission is required for video."
        }
    }

    private suspend fun ensureConversation(): String {
        conversationId?.let { return it }
        val id = container.repo.createCallConversation(CALL_PROMPT)
        conversationId = id
        return id
    }

    private suspend fun persist(user: String, reply: String) {
        val id = ensureConversation()
        val repo = container.repo
        val now = System.currentTimeMillis()
        repo.msg.insert(MessageEntity(conversationId = id, role = "user", content = user, createdAt = now))
        repo.msg.insert(MessageEntity(conversationId = id, role = "assistant", content = reply, createdAt = now + 1))
        repo.conv.touch(id, now)
    }

    private suspend fun persistFarewell(farewell: String) {
        val id = conversationId ?: return
        val repo = container.repo
        val now = System.currentTimeMillis()
        repo.msg.insert(MessageEntity(conversationId = id, role = "assistant", content = farewell, createdAt = now))
        repo.conv.touch(id, now)
    }

    private suspend fun saveCallMemory() {
        val id = conversationId ?: return
        if (lines.size < 2) return
        val summary = lines.takeLast(14).joinToString(" | ") {
            (if (it.user) "User" else "Aura") + ": " + it.text
        }.take(1400)
        container.memory.save(
            title = "Voice call " + java.text.SimpleDateFormat("d MMM, HH:mm", Locale.US)
                .format(java.util.Date(System.currentTimeMillis())),
            content = summary,
            category = "fact",
            source = "call",
            conversationId = id,
        )
    }

    fun reportPermissionDenied() {
        error = "Microphone permission is required for a call."
        phase = CallPhase.Listening
    }

    fun release() {
        if (released) return
        released = true
        timerJob?.cancel()
        job?.cancel()
        restartJob?.cancel()
        watchdogJob?.cancel()
        try { container.voice.stop() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        cleanupAudio()
    }

    private fun cleanupAudio() {
        try {
            sensorManager.unregisterListener(proximityListener)
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private fun ended(): Boolean = phase == CallPhase.Ended || released || ending
}

@Composable
fun CallScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val controller = remember { CallController(ctx, scope, container, onBack) }
    var showRoutes by remember { mutableStateOf(false) }
    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        audioGranted = ok
        if (ok) controller.start(settings) else controller.reportPermissionDenied()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraGranted = ok
        controller.onCameraPermission(ok)
    }

    SystemBarIcons(lightBackground = false)
    DisposableEffect(Unit) {
        val window = view.context.findActivityWindow()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (audioGranted) controller.start(settings) else audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.release()
        }
    }
    LaunchedEffect(controller.cameraRequestTick) {
        if (controller.cameraRequestTick > 0) cameraLauncher.launch(Manifest.permission.CAMERA)
    }
    BackHandler {
        if (controller.phase == CallPhase.Ended) onBack() else controller.endWithGoodbye()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(controller.lines.size, controller.lines.lastOrNull()?.text?.length) {
        if (controller.lines.isNotEmpty()) listState.scrollToItem(controller.lines.lastIndex)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(CBg, Color(0xFF120526), Color(0xFF240A3E)))
        )
    ) {
        if (controller.cameraOn && cameraGranted) {
            CallCameraPreview(
                front = controller.frontCamera,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC05040D), Color(0x6605040D), Color(0xE605040D))
                    )
                )
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        .clickable { controller.endWithGoodbye() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowBack, "End call", tint = CText) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Aura", color = CText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (controller.phase == CallPhase.Ended) CDim else Color(0xFF34D399), CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when (controller.phase) {
                                CallPhase.Ended -> "Call ended"
                                CallPhase.Connecting -> "Connecting…"
                                else -> "Live call · ${formatDuration(controller.duration)}"
                            },
                            color = CDim, fontSize = 13.sp,
                        )
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                        .clickable { showRoutes = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (controller.route) {
                                CallRoute.Bluetooth -> Icons.Rounded.Bluetooth
                                CallRoute.Earpiece -> Icons.Rounded.PhoneInTalk
                                else -> Icons.Rounded.VolumeUp
                            },
                            null, tint = CText, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(controller.route.label, color = CText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallLangs.forEach { l ->
                    val selected = l == controller.lang
                    Text(
                        l.label,
                        color = if (selected) Color.White else CDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(CViolet, CPink))
                                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f)))
                            )
                            .clickable { controller.setLang(l) }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }

            // Live transcript
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (controller.lines.isEmpty()) {
                    item {
                        Text(
                            "The call is live. Just speak — Aura listens continuously, answers out loud and keeps " +
                                "the full conversation in memory. Say “end call” whenever you are done.",
                            color = CDim, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        )
                    }
                }
                items(controller.lines.size) { i ->
                    val line = controller.lines[i]
                    if (line.user) {
                        Text(
                            line.text, color = CCyan, fontSize = 15.sp, textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            line.text.ifEmpty { "…" }, color = CText, fontSize = 19.sp, lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium, maxLines = 10, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                CallVisual(level = controller.level, phase = controller.phase)
            }

            val status = when {
                controller.muted -> "Muted · tap the mic to resume"
                controller.phase == CallPhase.Listening -> controller.partial.ifBlank { "Listening… just speak" }
                controller.phase == CallPhase.Thinking -> "Thinking…"
                controller.phase == CallPhase.Speaking -> "Aura is speaking…"
                controller.phase == CallPhase.Ended -> "Call ended"
                else -> "Connecting…"
            }
            Text(
                status, color = CText, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            )
            val msg = controller.error ?: controller.note
            Text(
                msg.orEmpty(),
                color = if (controller.error != null) Color(0xFFFF7A9C) else CDim,
                fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).height(32.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallControl(
                    icon = if (controller.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (controller.muted) "Unmute" else "Mute",
                    active = controller.muted,
                    onClick = { controller.toggleMute() },
                )
                CallControl(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Audio",
                    active = controller.route != CallRoute.Auto,
                    onClick = { showRoutes = true },
                )
                CallControl(
                    icon = if (controller.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = "Camera",
                    active = controller.cameraOn,
                    onClick = { controller.toggleCamera(cameraGranted) },
                )
                CallControl(
                    icon = Icons.Rounded.Cameraswitch,
                    label = "Flip",
                    active = false,
                    onClick = { if (cameraGranted) controller.switchCamera() else cameraLauncher.launch(Manifest.permission.CAMERA) },
                )
                CallControl(
                    icon = Icons.Rounded.CallEnd,
                    label = "End",
                    active = false,
                    danger = true,
                    onClick = { controller.endWithGoodbye() },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ScreenShare, null, tint = CDim.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Screen sharing arrives in the next update",
                    color = CDim.copy(alpha = 0.65f), fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showRoutes) {
        RouteDialog(selected = controller.route, onPick = {
            controller.setRoute(it)
            showRoutes = false
        }, onDismiss = { showRoutes = false })
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (danger) 64.dp else 54.dp)
                .clip(CircleShape)
                .background(
                    when {
                        danger -> Brush.linearGradient(listOf(Color(0xFFE0244B), Color(0xFFB31137)))
                        active -> Brush.linearGradient(listOf(CViolet, CPink))
                        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.07f)))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (danger) 28.dp else 23.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = CDim, fontSize = 11.sp)
    }
}

@Composable
private fun RouteDialog(selected: CallRoute, onPick: (CallRoute) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 28.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF17122B)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Text("Call audio", color = CText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Choose where the call is played.", color = CDim, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            CallRoute.entries.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (r == selected) CViolet.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onPick(r) }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (r) {
                            CallRoute.Bluetooth -> Icons.Rounded.Bluetooth
                            CallRoute.Earpiece -> Icons.Rounded.PhoneInTalk
                            CallRoute.Speaker -> Icons.Rounded.VolumeUp
                            CallRoute.Auto -> Icons.Rounded.Headphones
                        },
                        null, tint = CText, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, color = CText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (r) {
                                CallRoute.Auto -> "Earpiece or speaker, based on the proximity sensor"
                                CallRoute.Speaker -> "Loudspeaker"
                                CallRoute.Earpiece -> "Private earpiece"
                                CallRoute.Bluetooth -> "Bluetooth headset or car"
                            },
                            color = CDim, fontSize = 12.sp,
                        )
                    }
                    if (r == selected) Icon(Icons.Rounded.Check, null, tint = CCyan)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Close", color = CDim, modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

@Composable
private fun CallCameraPreview(front: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { view ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    providerState.value = provider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder()
                            .requireLensFacing(if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                            .build(),
                        preview,
                    )
                }
            }, ContextCompat.getMainExecutor(context))
        },
    )
    DisposableEffect(front) {
        onDispose { runCatching { providerState.value?.unbindAll() } }
    }
}

@Composable
private fun CallVisual(level: Float, phase: CallPhase) {
    val t = rememberInfiniteTransition(label = "call")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (phase == CallPhase.Thinking) 2600 else 18000, easing = LinearEasing)),
        label = "spin",
    )
    val wave by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "wave",
    )
    val smooth by animateFloatAsState(level, tween(120), label = "level")
    Canvas(Modifier.size(190.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val amp = when (phase) {
            CallPhase.Listening -> 0.12f + smooth * 0.85f
            CallPhase.Speaking -> 0.38f + 0.4f * ((sin(wave * 3f) + 1f) / 2f)
            CallPhase.Thinking -> 0.2f
            else -> 0.06f
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(CPink.copy(alpha = 0.30f), CViolet.copy(alpha = 0.10f), Color.Transparent), center = center, radius = r),
            radius = r, center = center,
        )
        val bars = 60
        val inner = r * 0.54f
        rotate(spin) {
            for (i in 0 until bars) {
                val angle = i.toFloat() / bars * 2f * PI.toFloat()
                val len = r * 0.05f + r * 0.36f * amp * (0.5f + 0.5f * sin(wave * 2f + i * 0.5f))
                val start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
                val end = Offset(center.x + cos(angle) * (inner + len), center.y + sin(angle) * (inner + len))
                drawLine(
                    color = lerp(CCyan, CPink, i.toFloat() / bars),
                    start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFB07CFF), CViolet, Color(0xFF2A0F5C)), center = center, radius = inner),
            radius = inner * (0.94f + 0.06f * amp), center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = inner * (0.94f + 0.06f * amp), center = center, style = Stroke(1.5.dp.toPx()),
        )
    }
}

private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

private fun android.content.Context.findActivityWindow(): android.view.Window? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context.window
        context = context.baseContext
    }
    return null
}
