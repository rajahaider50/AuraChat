package com.aura.chat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class Repository(db: AppDatabase, val settings: SettingsStore, val api: ChatApi) {
    val conv = db.conversations()
    val msg = db.messages()
    val prompts = db.prompts()
    val providers = db.providers()
    val memories = db.memories()
    val skills = db.skills()
    val connectors = db.connectors()
    val attachments = db.attachments()

    suspend fun createConversation(persona: Persona): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conv.upsert(
            ConversationEntity(
                id = id, title = NEW_TITLE, personaId = persona.id, systemPrompt = persona.prompt,
                model = "", pinned = false, createdAt = now, updatedAt = now,
            )
        )
        return id
    }

    /** A real call is persisted as a normal chat titled "Voice call" so it can be reread later. */
    suspend fun createCallConversation(systemPrompt: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conv.upsert(
            ConversationEntity(
                id = id,
                title = "Voice call · " + java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.US).format(java.util.Date(now)),
                personaId = Personas.all.first().id,
                systemPrompt = systemPrompt,
                model = "",
                pinned = false,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun activeProvider(): ProviderEntity? {
        val activeId = settings.flow.first().activeProvider
        val list = providers.all()
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    /** Gemini TTS key: the dedicated voice key, or the active Gemini provider's key. */
    suspend fun geminiKey(): String {
        val s = settings.flow.first()
        if (s.geminiKey.isNotBlank()) return s.geminiKey.trim()
        val active = activeProvider()
        return if (active != null && active.baseUrl.contains("generativelanguage.googleapis.com", true)) {
            active.apiKey.trim()
        } else ""
    }

    /** Active provider + model, or an ApiException telling the user what to set up. */
    suspend fun resolveModel(): Pair<ProviderEntity, String> {
        val p = activeProvider()
            ?: throw ApiException("No AI provider is connected. Open Settings → Providers and add one.")
        if (p.model.isBlank()) throw ApiException("No model selected. Open Settings → Model and pick one.")
        return p to p.model
    }

    /** Streams a single system+user prompt, used by the Studio tools. */
    fun oneShot(provider: ProviderEntity, model: String, system: String?, user: String, temperature: Float): Flow<String> =
        api.stream(
            provider, model,
            buildList {
                if (!system.isNullOrBlank()) add(ApiMessage("system", system))
                add(ApiMessage("user", user))
            },
            temperature,
        )

    suspend fun seedPrompts() {
        if (prompts.count() > 0) return
        val now = System.currentTimeMillis()
        listOf(
            "Summarize" to "Summarize the following text in five clear bullet points and one sentence of takeaway:\n\n",
            "Explain simply" to "Explain the following as if I were a smart beginner, using one everyday analogy:\n\n",
            "Improve my writing" to "Improve the clarity and flow of this text without changing its meaning. Show the result first, then list the main edits:\n\n",
            "Pros and cons" to "Give me an honest pros and cons list, then a recommendation, for this decision:\n\n",
            "Plan my day" to "Turn these tasks into a realistic schedule for today with time blocks and short breaks:\n\n",
        ).forEachIndexed { i, (title, text) ->
            prompts.insert(PromptEntity(title = title, text = text, createdAt = now - i))
        }
    }

    fun toMarkdown(c: ConversationEntity, messages: List<MessageEntity>): String = buildString {
        appendLine("# ${c.title}")
        appendLine()
        messages.filter { !it.isError }.forEach {
            appendLine("**${if (it.role == "user") "You" else "Aura"}**")
            appendLine()
            appendLine(it.content)
            appendLine()
        }
    }

    suspend fun exportConversation(id: String): String {
        val c = conv.get(id) ?: return ""
        return toMarkdown(c, msg.list(id))
    }

    suspend fun exportAll(): String = conv.all().joinToString("\n\n---\n\n") { toMarkdown(it, msg.list(it.id)) }

    companion object {
        const val NEW_TITLE = "New chat"
    }
}
