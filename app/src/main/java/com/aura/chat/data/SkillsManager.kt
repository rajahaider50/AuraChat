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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Installs Claude-style skills from a GitHub repository (or any raw Markdown
 * URL). A skill is a folder-less Markdown instruction file stored locally and
 * injected into the model's system prompt when enabled.
 */
class SkillsManager(
    private val context: Context,
    private val dao: SkillDao,
    private val settings: SettingsStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun skillsDir(): File = File(context.filesDir, "skills").apply { mkdirs() }

    suspend fun install(rawUrl: String): SkillEntity = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw ApiException("Enter a full skill URL, for example https://github.com/user/repo")
        }
        val token = settings.flow.first().githubToken
        val resolved = if (url.contains("github.com")) resolveGitHub(url, token) else resolveRaw(url)
        val now = System.currentTimeMillis()
        val existing = dao.all().firstOrNull { it.sourceUrl.equals(resolved.sourceUrl, ignoreCase = true) }
        val skill = if (existing != null) {
            existing.copy(
                name = resolved.name,
                description = resolved.description,
                instructions = resolved.instructions,
                installedAt = now,
            ).also { dao.update(it) }
        } else {
            SkillEntity(
                name = resolved.name,
                description = resolved.description,
                sourceUrl = resolved.sourceUrl,
                instructions = resolved.instructions,
                enabled = false,
                installedAt = now,
            ).let { it.copy(id = dao.insert(it)) }
        }
        saveToDisk(skill)
        skill
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { dao.update(it.copy(enabled = enabled)) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val skill = dao.get(id)
        dao.delete(id)
        skill?.let { File(skillsDir(), "${it.id}-${slug(it.name)}.md").delete() }
    }

    /** Enabled skills, capped so the prompt never becomes enormous. */
    suspend fun promptForEnabled(maxChars: Int = 14000): String = withContext(Dispatchers.IO) {
        val enabled = dao.all().filter { it.enabled }
        if (enabled.isEmpty()) return@withContext ""
        val builder = StringBuilder()
        builder.appendLine("You have the following installed skills. Follow their instructions when the task matches:")
        enabled.forEach { skill ->
            val block = "\n## Skill: ${skill.name}\n${skill.instructions.trim()}\n"
            if (builder.length + block.length <= maxChars) builder.append(block)
        }
        builder.toString().trim()
    }

    fun fileFor(skill: SkillEntity): File = File(skillsDir(), "${skill.id}-${slug(skill.name)}.md")

    private fun saveToDisk(skill: SkillEntity) {
        fileFor(skill).writeText(
            "# ${skill.name}\n\n${skill.description}\n\nSource: ${skill.sourceUrl}\n\n${skill.instructions}"
        )
    }

    private data class Resolved(
        val name: String,
        val description: String,
        val instructions: String,
        val sourceUrl: String,
    )

    private fun resolveGitHub(url: String, token: String): Resolved {
        val match = Regex("github\\.com/([^/]+)/([^/?#]+)").find(url)
            ?: throw ApiException("That GitHub link is not a repository URL.")
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        val apiHeaders = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "AuraChat",
        )
        if (token.isNotBlank()) apiHeaders["Authorization"] = "Bearer $token"

        val repoJson = getJson("https://api.github.com/repos/$owner/$repo", apiHeaders)
        val branch = repoJson["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main"
        val repoDescription = repoJson["description"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val paths = mutableListOf<String>()
        val treeUrl = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
        runCatching {
            val tree = getJson(treeUrl, apiHeaders)
            (tree["tree"] as? JsonArray)?.forEach { node ->
                val obj = node as? JsonObject ?: return@forEach
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                if (type == "blob" && path.endsWith(".md", ignoreCase = true)) paths += path
            }
        }
        if (paths.isEmpty()) {
            runCatching {
                val contentsBody = fetchText("https://api.github.com/repos/$owner/$repo/contents", apiHeaders)
                val contents = runCatching { json.parseToJsonElement(contentsBody).jsonArray }.getOrNull() ?: return@runCatching
                contents.forEach { node ->
                    val obj = node as? JsonObject ?: return@forEach
                    val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (path.endsWith(".md", ignoreCase = true)) paths += path
                }
            }
        }
        val chosen = paths.firstOrNull { it.substringAfterLast('/').equals("SKILL.md", true) }
            ?: paths.firstOrNull { it.startsWith("skills/", true) && it.endsWith(".md", true) }
            ?: paths.firstOrNull { it.substringAfterLast('/').equals("README.md", true) }
            ?: throw ApiException("No SKILL.md or README.md found in $owner/$repo.")

        val raw = fetchText(
            "https://raw.githubusercontent.com/$owner/$repo/$branch/${encodePath(chosen)}",
            emptyMap(),
        )
        val meta = parseFrontMatter(raw)
        val name = meta.first.ifBlank { repo.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() } }
        val description = meta.second.ifBlank { repoDescription.ifBlank { "Installed from $owner/$repo" } }
        return Resolved(name, description, raw.take(60000), url)
    }

    private fun resolveRaw(url: String): Resolved {
        val raw = fetchText(url, emptyMap())
        val meta = parseFrontMatter(raw)
        val name = meta.first.ifBlank {
            url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').ifBlank { "Installed skill" }
        }
        return Resolved(
            name = name.take(80),
            description = meta.second.ifBlank { "Installed from ${url.take(120)}" },
            instructions = raw.take(60000),
            sourceUrl = url,
        )
    }

    private fun parseFrontMatter(raw: String): Pair<String, String> {
        if (!raw.trimStart().startsWith("---")) return "" to ""
        val body = raw.trimStart().removePrefix("---")
        val end = body.indexOf("\n---")
        if (end < 0) return "" to ""
        val header = body.substring(0, end)
        var name = ""
        var description = ""
        header.lines().forEach { line ->
            val key = line.substringBefore(':', "").trim().lowercase()
            val value = line.substringAfter(':', "").trim().trim('"', '\'')
            when (key) {
                "name" -> name = value
                "description" -> description = value
            }
        }
        return name to description
    }

    private fun getJson(url: String, headers: Map<String, String>): JsonObject {
        val body = fetchText(url, headers)
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw ApiException(githubError(body) ?: "GitHub returned an unreadable response.")
    }

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url).header("User-Agent", "AuraChat")
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(
                    when (response.code) {
                        401, 403 -> "GitHub rejected the request. Add a GitHub token in Skills, or try again later."
                        404 -> "That skill link was not found. Check the repository is public."
                        429 -> "GitHub rate limit reached. Add a GitHub token in Skills and retry."
                        else -> githubError(body) ?: "Could not download the skill (HTTP ${response.code})."
                    }
                )
            }
            return body
        }
    }

    private fun githubError(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment -> URLEncoder.encode(segment, "UTF-8").replace("+", "%20") }

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "skill" }.take(50)
}
