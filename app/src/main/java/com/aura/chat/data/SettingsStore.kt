package com.aura.chat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aura_settings")

data class AppSettings(
    val theme: Int = 0, // 0 = system, 1 = dark, 2 = light
    val accent: Int = 0,
    val glass: Float = 0.6f,
    val activeProvider: Long = -1L,
    val temperature: Float = 0.7f,
    val contextSize: Int = 20,
    val onboarded: Boolean = false,
    val haptics: Boolean = true,
    val userName: String = "",
    // Gemini native voice (official Gemini TTS, no system voices).
    val geminiKey: String = "",
    val geminiVoice: String = "Kore",
    val geminiModel: String = "gemini-2.5-flash-preview-tts",
    // Calls
    val callRoute: Int = 0, // 0 = auto, 1 = speaker, 2 = earpiece, 3 = bluetooth
    val callCameraFront: Boolean = true,
    // Memory
    val memoryEnabled: Boolean = true,
    val autoMemory: Boolean = true,
    // Optional token used to raise GitHub API limits when installing skills.
    val githubToken: String = "",
)

class SettingsStore(private val context: Context) {
    private object K {
        val THEME = intPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent")
        val GLASS = floatPreferencesKey("glass")
        val PROVIDER = longPreferencesKey("active_provider")
        val TEMP = floatPreferencesKey("temperature")
        val CONTEXT = intPreferencesKey("context_size")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HAPTICS = booleanPreferencesKey("haptics")
        val NAME = stringPreferencesKey("user_name")
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val GEMINI_VOICE = stringPreferencesKey("gemini_voice")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CALL_ROUTE = intPreferencesKey("call_route")
        val CALL_CAMERA_FRONT = booleanPreferencesKey("call_camera_front")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val AUTO_MEMORY = booleanPreferencesKey("auto_memory")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[K.THEME] ?: d.theme,
            accent = p[K.ACCENT] ?: d.accent,
            glass = p[K.GLASS] ?: d.glass,
            activeProvider = p[K.PROVIDER] ?: d.activeProvider,
            temperature = p[K.TEMP] ?: d.temperature,
            contextSize = p[K.CONTEXT] ?: d.contextSize,
            onboarded = p[K.ONBOARDED] ?: d.onboarded,
            haptics = p[K.HAPTICS] ?: d.haptics,
            userName = p[K.NAME] ?: d.userName,
            geminiKey = p[K.GEMINI_KEY] ?: d.geminiKey,
            geminiVoice = p[K.GEMINI_VOICE] ?: d.geminiVoice,
            geminiModel = p[K.GEMINI_MODEL] ?: d.geminiModel,
            callRoute = p[K.CALL_ROUTE] ?: d.callRoute,
            callCameraFront = p[K.CALL_CAMERA_FRONT] ?: d.callCameraFront,
            memoryEnabled = p[K.MEMORY_ENABLED] ?: d.memoryEnabled,
            autoMemory = p[K.AUTO_MEMORY] ?: d.autoMemory,
            githubToken = p[K.GITHUB_TOKEN] ?: d.githubToken,
        )
    }

    suspend fun setTheme(v: Int) { context.dataStore.edit { it[K.THEME] = v } }
    suspend fun setAccent(v: Int) { context.dataStore.edit { it[K.ACCENT] = v } }
    suspend fun setGlass(v: Float) { context.dataStore.edit { it[K.GLASS] = v } }
    suspend fun setActiveProvider(v: Long) { context.dataStore.edit { it[K.PROVIDER] = v } }
    suspend fun setTemperature(v: Float) { context.dataStore.edit { it[K.TEMP] = v } }
    suspend fun setContextSize(v: Int) { context.dataStore.edit { it[K.CONTEXT] = v } }
    suspend fun setOnboarded(v: Boolean) { context.dataStore.edit { it[K.ONBOARDED] = v } }
    suspend fun setHaptics(v: Boolean) { context.dataStore.edit { it[K.HAPTICS] = v } }
    suspend fun setUserName(v: String) { context.dataStore.edit { it[K.NAME] = v } }
    suspend fun setGeminiKey(v: String) { context.dataStore.edit { it[K.GEMINI_KEY] = v.trim() } }
    suspend fun setGeminiVoice(v: String) { context.dataStore.edit { it[K.GEMINI_VOICE] = v } }
    suspend fun setGeminiModel(v: String) { context.dataStore.edit { it[K.GEMINI_MODEL] = v.trim() } }
    suspend fun setCallRoute(v: Int) { context.dataStore.edit { it[K.CALL_ROUTE] = v } }
    suspend fun setCallCameraFront(v: Boolean) { context.dataStore.edit { it[K.CALL_CAMERA_FRONT] = v } }
    suspend fun setMemoryEnabled(v: Boolean) { context.dataStore.edit { it[K.MEMORY_ENABLED] = v } }
    suspend fun setAutoMemory(v: Boolean) { context.dataStore.edit { it[K.AUTO_MEMORY] = v } }
    suspend fun setGithubToken(v: String) { context.dataStore.edit { it[K.GITHUB_TOKEN] = v.trim() } }
}
