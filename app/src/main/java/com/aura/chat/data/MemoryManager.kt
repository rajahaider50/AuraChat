package com.aura.chat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MemoryFile(
    val name: String,
    val path: String,
    val content: String,
    val updatedAt: Long,
    val size: Long,
)

/**
 * Claude/Gemini-style long-term memory.
 *
 * Every memory is indexed in SQLite for fast search and mirrored to real
 * Markdown files under `files/memory/`. Creating, editing or deleting a memory
 * rewrites the affected file, so the files always match what the app shows.
 */
class MemoryManager(
    private val context: Context,
    private val dao: MemoryDao,
    private val api: ChatApi,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val root: File get() = File(context.filesDir, "memory").apply { mkdirs() }

    private val categories = listOf("profile", "preference", "project", "fact", "other")

    private fun fileFor(category: String): File {
        val safe = if (category in categories) category else "other"
        return File(root, "$safe.md")
    }

    suspend fun save(
        title: String,
        content: String,
        category: String = "fact",
        source: String = "manual",
        conversationId: String? = null,
        pinned: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { content.take(60) }
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return@withContext -1L
        if (dao.countContent(cleanContent) > 0) return@withContext -2L
        val now = System.currentTimeMillis()
        val id = dao.insert(
            MemoryEntity(
                title = cleanTitle.take(120),
                content = cleanContent.take(4000),
                category = normalizeCategory(category),
                source = source,
                conversationId = conversationId,
                pinned = pinned,
                createdAt = now,
                updatedAt = now,
            )
        )
        rewriteAll()
        id
    }

    suspend fun update(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        dao.update(memory.copy(updatedAt = System.currentTimeMillis()))
        rewriteAll()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
        rewriteAll()
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        val all = dao.all()
        all.firstOrNull { it.id == id }?.let { dao.update(it.copy(pinned = pinned, updatedAt = System.currentTimeMillis())) }
        rewriteAll()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        root.listFiles()?.forEach { it.delete() }
        rewriteAll()
    }

    /**
     * Facts injected into the system prompt for chat and calls. Only durable,
     * relevant memories are included to keep the context small.
     */
    suspend fun contextFor(query: String, limit: Int = 8): String = withContext(Dispatchers.IO) {
        val s = settings.flow.first()
        if (!s.memoryEnabled) return@withContext ""
        val words = query.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .distinct()
            .take(6)
        val picked = LinkedHashMap<Long, MemoryEntity>()
        words.forEach { word ->
            runCatching { dao.search(word, 4) }.getOrDefault(emptyList()).forEach { picked[it.id] = it }
        }
        dao.pinned(4).forEach { picked[it.id] = it }
        if (picked.size < limit) {
            dao.all().take(limit).forEach { picked.putIfAbsent(it.id, it) }
        }
        val list = picked.values.take(limit)
        if (list.isEmpty()) return@withContext ""
        buildString {
            appendLine("Long-term memory about the user (use only when relevant, never list it back unless asked):")
            list.forEach { m ->
                append("- [")
                append(m.category)
                append("] ")
                append(m.title)
                append(": ")
                append(m.content.take(320))
                appendLine()
            }
        }.trim()
    }

    fun readFiles(): List<MemoryFile> = (listOf("index") + categories).mapNotNull { name ->
        val f = File(root, "$name.md")
        if (!f.exists()) null else MemoryFile(
            name = f.name,
            path = f.absolutePath,
            content = runCatching { f.readText() }.getOrDefault(""),
            updatedAt = f.lastModified(),
            size = f.length(),
        )
    }

    suspend fun exportMarkdown(): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("# Aura Chat memory")
            appendLine()
            readFiles().forEach { f ->
                appendLine("## ${f.name}")
                appendLine()
                appendLine(f.content)
                appendLine()
            }
        }
    }

    suspend fun extractAndSave(
        provider: ProviderEntity,
        model: String,
        userText: String,
        replyText: String,
        source: String,
        conversationId: String?,
    ) = withContext(Dispatchers.IO) {
        val s = settings.flow.first()
        if (!s.autoMemory || !s.memoryEnabled) return@withContext
        if (userText.trim().length < 6 || replyText.trim().length < 6) return@withContext
        val prompt = """
            You maintain the long-term memory of a personal AI assistant.
            Read the exchange and extract ONLY durable facts that will still matter weeks from now:
            names, identities, language, preferences, ongoing projects, important people, goals,
            constraints, and explicit "remember this" requests.
            Never store small talk, questions, temporary moods, or the assistant's own words.
            Return a strict JSON array with 0 to 3 objects. Each object has:
            "title" (short label), "content" (one clear sentence), "category"
            (one of: profile, preference, project, fact).
            If there is nothing durable, return [].
        """.trimIndent()
        val exchange = "User: ${userText.take(4000)}\nAssistant: ${replyText.take(4000)}"
        val raw = runCatching {
            api.completeText(provider, model, prompt, exchange, 0.1f)
        }.getOrNull().orEmpty()
        val saved = parseMemories(raw)
        if (saved.isNotEmpty()) {
            saved.take(3).forEach { (title, content, category) ->
                save(title, content, category, source, conversationId)
            }
        } else if (raw.isBlank()) {
            heuristic(userText, source, conversationId)
        }
    }

    /** Save an explicit "remember …" command immediately. */
    suspend fun rememberExplicit(text: String, source: String, conversationId: String?): Long {
        val clean = text.trim()
        if (clean.isBlank()) return -1L
        val category = when {
            Regex("(?i)\\b(my name|i am|i'm|call me)\\b").containsMatchIn(clean) -> "profile"
            Regex("(?i)\\b(i like|i love|i hate|i prefer|favourite|favorite)\\b").containsMatchIn(clean) -> "preference"
            Regex("(?i)\\b(i am (building|working on)|my project|we are building)\\b").containsMatchIn(clean) -> "project"
            else -> "fact"
        }
        return save(clean.take(80), clean, category, source, conversationId)
    }

    private fun parseMemories(raw: String): List<Triple<String, String, String>> {
        if (raw.isBlank()) return emptyList()
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonArray ?: return emptyList()
            arr.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val content = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (content.length < 4) return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { content.take(60) }
                val category = normalizeCategory(obj["category"]?.jsonPrimitive?.contentOrNull.orEmpty())
                Triple(title.take(100), content.take(1000), category)
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun heuristic(text: String, source: String, conversationId: String?) {
        val line = text.replace('\n', ' ').trim()
        val patterns = listOf(
            Regex("(?i)\\bmy name is\\s+([^.,!?]{2,60})") to "profile",
            Regex("(?i)\\bi prefer\\s+([^.!?]{4,140})") to "preference",
            Regex("(?i)\\bi am (?:building|working on)\\s+([^.!?]{4,140})") to "project",
            Regex("(?i)\\bremember (?:that )?(.{6,200})") to "fact",
        )
        patterns.forEach { (regex, category) ->
            regex.find(line)?.let { match ->
                val fact = match.value.trim()
                save(fact.take(80), fact, category, source, conversationId)
            }
        }
    }

    private fun normalizeCategory(value: String): String {
        val v = value.trim().lowercase()
        return when {
            v.startsWith("profile") || v.contains("identity") -> "profile"
            v.startsWith("prefer") -> "preference"
            v.startsWith("project") -> "project"
            v.startsWith("fact") -> "fact"
            else -> "other"
        }
    }

    private suspend fun rewriteAll() {
        val all = dao.all()
        categories.forEach { category ->
            val items = all.filter { it.category == category }
            val body = buildString {
                appendLine("# Aura memory · ${category.replaceFirstChar { it.uppercase() }}")
                appendLine()
                appendLine("_Automatically maintained by Aura Chat. Every line below is a saved long-term memory._")
                appendLine()
                if (items.isEmpty()) {
                    appendLine("_No memories in this file yet._")
                } else {
                    items.forEach { m ->
                        append("- **")
                        append(escape(m.title))
                        append("** — ")
                        append(escape(m.content))
                        append("  ")
                        appendLine()
                        append("  <sub>")
                        append(m.source)
                        append(" · ")
                        append(date(m.updatedAt))
                        if (m.pinned) append(" · pinned")
                        append("</sub>")
                        appendLine()
                    }
                }
            }
            fileFor(category).writeText(body)
        }
        val index = buildString {
            appendLine("# Aura memory index")
            appendLine()
            appendLine("Long-term memory is stored in separate Markdown files inside the app's private storage.")
            appendLine("These files survive app restarts, disconnects and calls.")
            appendLine()
            appendLine("| File | Memories | Updated |")
            appendLine("| --- | --- | --- |")
            val now = date(System.currentTimeMillis())
            categories.forEach { category ->
                val count = all.count { it.category == category }
                val f = fileFor(category)
                appendLine("| ${f.name} | $count | ${if (f.exists()) date(f.lastModified()) else now} |")
            }
            appendLine()
            appendLine("Total: ${all.size} memories · ${all.count { it.pinned }} pinned")
            appendLine()
            if (all.isNotEmpty()) {
                appendLine("## Most recent")
                all.take(8).forEach { appendLine("- ${escape(it.title)} (${it.category})") }
            }
            appendLine()
            appendLine("_Updated ${date(System.currentTimeMillis())}_")
        }
        File(root, "index.md").writeText(index)
    }

    private fun escape(text: String): String = text.replace("\n", " ").trim()

    private fun date(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}
