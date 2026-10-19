package com.aura.chat

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.aura.chat.data.AppDatabase
import com.aura.chat.data.AttachmentManager
import com.aura.chat.data.ChatApi
import com.aura.chat.data.ConnectorManager
import com.aura.chat.data.DocumentReader
import com.aura.chat.data.GeminiAudio
import com.aura.chat.data.GeminiVoice
import com.aura.chat.data.MemoryManager
import com.aura.chat.data.Repository
import com.aura.chat.data.SettingsStore
import com.aura.chat.data.SkillsManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AuraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "aura.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    /** Outlives screens: memory extraction and call writes keep working after a disconnect. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = SettingsStore(context)
    val api = ChatApi()
    val repo = Repository(db, settings, api)
    val memory = MemoryManager(context, repo.memories, api, settings)
    val skills = SkillsManager(context, repo.skills, settings)
    val connectors = ConnectorManager(context, repo.connectors, settings)
    val geminiAudio = GeminiAudio()
    val documents = DocumentReader(context, geminiAudio)
    val attachments = AttachmentManager(context, repo.attachments, documents)
    val voice = GeminiVoice(context)

    /** Text handed from the prompt library to the next opened chat. */
    @Volatile
    var pendingPrompt: String? = null
}

val Context.container: AppContainer
    get() = (applicationContext as AuraApp).container
