package com.aura.chat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ConnectorTool(
    val key: String,
    val displayName: String,
    val description: String,
    val parameters: JsonObject,
    val connector: ConnectorEntity,
    val remoteName: String,
)

/** Real MCP (JSON-RPC over HTTP), REST API and application connectors. */
class ConnectorManager(
    private val context: Context,
    private val dao: ConnectorDao,
    private val settings: SettingsStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedTools: List<ConnectorTool> = emptyList()

    @Volatile
    private var cachedAt: Long = 0L

    data class TestResult(val ok: Boolean, val message: String, val tools: List<String> = emptyList())

    suspend fun test(connector: ConnectorEntity): TestResult = withContext(Dispatchers.IO) {
        try {
            val result = when (connector.type) {
                "mcp" -> {
                    val tools = listTools(connector)
                    TestResult(true, "Connected. ${tools.size} MCP tools available.", tools.map { it.displayName })
                }
                "api" -> {
                    val builder = Request.Builder().url(connector.url).header("User-Agent", "AuraChat")
                    applyHeaders(builder, connector)
                    client.newCall(builder.get().build()).execute().use { response ->
                        TestResult(response.isSuccessful, "API responded with HTTP ${response.code} ${response.message}.")
                    }
                }
                else -> {
                    val uri = runCatching { Uri.parse(connector.url) }.getOrNull()
                    if (uri == null || uri.scheme.isNullOrBlank()) {
                        TestResult(false, "Enter an app deep link such as myapp://open or a package name.")
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            TestResult(true, "App link is valid and an app can open it.")
                        } else {
                            TestResult(true, "Saved. No installed app currently handles this link.")
                        }
                    }
                }
            }
            dao.setHealth(connector.id, result.message, System.currentTimeMillis())
            result
        } catch (e: Exception) {
            val message = e.message ?: "Connection failed."
            dao.setHealth(connector.id, "Failed: $message", System.currentTimeMillis())
            TestResult(false, message)
        }
    }

    suspend fun openAppConnector(connector: ConnectorEntity): Boolean {
        val uri = runCatching { Uri.parse(connector.url) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }

    /** Tools offered to the model. MCP tools are discovered live and cached briefly. */
    suspend fun toolsForModel(force: Boolean = false): List<ConnectorTool> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - cachedAt < 60_000 && cachedTools.isNotEmpty()) return@withContext cachedTools
        val enabled = dao.all().filter { it.enabled }
        val result = mutableListOf<ConnectorTool>()
        enabled.forEach { connector ->
            when (connector.type) {
                "mcp" -> runCatching {
                    listTools(connector).forEach { tool ->
                        result += tool.copy(
                            key = toolKey(connector, tool.remoteName),
                            parameters = tool.parameters,
                        )
                    }
                }
                "api" -> result += ConnectorTool(
                    key = "c${connector.id}_request",
                    displayName = "${connector.name} request",
                    description = "Make an HTTP request through the ${connector.name} API connector. " +
                        "Use a path relative to ${connector.url}. Returns the raw response body.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("method") {
                                put("type", "string")
                                put("description", "HTTP method: GET, POST, PUT, PATCH or DELETE")
                            }
                            putJsonObject("path") {
                                put("type", "string")
                                put("description", "Path relative to the connector base URL")
                            }
                            putJsonObject("body") {
                                put("type", "string")
                                put("description", "Optional JSON request body")
                            }
                        }
                        put("required", JsonArray(listOf(JsonPrimitive("path"))))
                    }.let { it },
                    connector = connector,
                    remoteName = "",
                )
            }
        }
        cachedTools = result
        cachedAt = now
        result
    }

    suspend fun execute(toolKey: String, argumentsJson: String): String = withContext(Dispatchers.IO) {
        val tool = cachedTools.firstOrNull { it.key == toolKey }
            ?: toolsForModel(force = true).firstOrNull { it.key == toolKey }
            ?: throw ApiException("Unknown connector tool: $toolKey")
        val args = runCatching { json.parseToJsonElement(argumentsJson).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        when (tool.connector.type) {
            "mcp" -> callTool(tool.connector, tool.remoteName, args)
            "api" -> callApi(
                tool.connector,
                args["method"]?.jsonPrimitive?.contentOrNull ?: "GET",
                args["path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                args["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            else -> throw ApiException("This connector cannot be called as a tool.")
        }
    }

    suspend fun listTools(connector: ConnectorEntity): List<ConnectorTool> = withContext(Dispatchers.IO) {
        requirePermission(connector, "tools.list")
        val session = initialize(connector)
        val response = rpc(connector, session, "tools/list", JsonObject(emptyMap()), 2)
        val result = response["result"] as? JsonObject ?: return@withContext emptyList()
        val tools = result["tools"] as? JsonArray ?: return@withContext emptyList()
        tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val schema = obj["inputSchema"] as? JsonObject ?: buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
            ConnectorTool(
                key = toolKey(connector, name),
                displayName = name,
                description = description,
                parameters = schema,
                connector = connector,
                remoteName = name,
            )
        }
    }

    suspend fun callTool(connector: ConnectorEntity, name: String, args: JsonObject): String =
        withContext(Dispatchers.IO) {
            requirePermission(connector, "tools.call")
            val session = initialize(connector)
            val params = buildJsonObject {
                put("name", name)
                put("arguments", args)
            }
            val response = rpc(connector, session, "tools/call", params, 3)
            val error = response["error"] as? JsonObject
            if (error != null) {
                throw ApiException(error["message"]?.jsonPrimitive?.contentOrNull ?: "MCP tool failed.")
            }
            val result = response["result"] as? JsonObject ?: return@withContext response.toString()
            val content = result["content"] as? JsonArray
            if (content != null) {
                content.joinToString("\n") { part ->
                    (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                }.ifBlank { result.toString() }
            } else {
                result.toString().take(20000)
            }
        }

    private data class Session(val id: String?)

    private fun initialize(connector: ConnectorEntity): Session {
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "AuraChat")
                put("version", "2.0")
            }
        }
        val (body, sessionId) = postRpc(connector, null, "initialize", params, 1)
        val parsed = parseRpc(body)
        val error = parsed["error"] as? JsonObject
        if (error != null) {
            throw ApiException(error["message"]?.jsonPrimitive?.contentOrNull ?: "MCP initialize failed.")
        }
        // The initialized notification has no id and may return 202/empty.
        runCatching {
            postRpc(connector, sessionId, "notifications/initialized", null, null)
        }
        return Session(sessionId)
    }

    private fun rpc(
        connector: ConnectorEntity,
        session: Session,
        method: String,
        params: JsonObject?,
        id: Int,
    ): JsonObject {
        val (body, _) = postRpc(connector, session.id, method, params, id)
        val parsed = parseRpc(body)
        val error = parsed["error"] as? JsonObject
        if (error != null) {
            throw ApiException(error["message"]?.jsonPrimitive?.contentOrNull ?: "MCP request failed.")
        }
        return parsed
    }

    private fun postRpc(
        connector: ConnectorEntity,
        sessionId: String?,
        method: String,
        params: JsonObject?,
        id: Int?,
    ): Pair<String, String?> {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }.toString()
        val builder = Request.Builder()
            .url(connector.url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("User-Agent", "AuraChat")
            .post(payload.toRequestBody("application/json".toMediaType()))
        applyHeaders(builder, connector)
        if (!sessionId.isNullOrBlank()) builder.header("Mcp-Session-Id", sessionId)
        val base = connector.url.trimEnd('/')
        val candidates = if (base.substringAfter("://").contains('/')) listOf(base) else listOf(base, "$base/mcp", "$base/sse")
        var last404 = ""
        candidates.distinct().forEach { endpoint ->
            val request = builder.url(endpoint).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val sid = response.header("Mcp-Session-Id") ?: sessionId
                if (response.isSuccessful) return body to sid
                if (response.code == 404) {
                    last404 = "MCP endpoint $endpoint returned HTTP 404 ${response.message}."
                } else {
                    throw ApiException("MCP server returned HTTP ${response.code} ${response.message}. ${body.take(500)}".trim())
                }
            }
        }
        throw ApiException("$last404 Check that the MCP URL includes the server endpoint path, commonly /mcp or /sse.")
    }

    private fun parseRpc(body: String): JsonObject {
        if (body.isBlank()) return JsonObject(emptyMap())
        val trimmed = body.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }
        // Server-sent events: take the last JSON data frame.
        val data = trimmed.lines()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trim() }
            .ifBlank { trimmed.substringAfter("data:").trim() }
        return runCatching {
            val element = json.parseToJsonElement(data)
            element.jsonObject
        }.getOrDefault(JsonObject(emptyMap()))
    }

    private fun callApi(connector: ConnectorEntity, method: String, path: String, body: String): String {
        val base = connector.url.trimEnd('/')
        val url = if (path.isBlank()) base else base + "/" + path.trimStart('/')
        val builder = Request.Builder().url(url).header("User-Agent", "AuraChat")
        applyHeaders(builder, connector)
        val media = "application/json".toMediaType()
        val request = when (method.uppercase()) {
            "POST" -> builder.post(body.ifBlank { "{}" }.toRequestBody(media)).build()
            "PUT" -> builder.put(body.ifBlank { "{}" }.toRequestBody(media)).build()
            "PATCH" -> builder.patch(body.ifBlank { "{}" }.toRequestBody(media)).build()
            "DELETE" -> builder.delete().build()
            else -> builder.get().build()
        }
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException("Connector HTTP ${response.code}: ${text.take(800)}")
            }
            return text.take(20000)
        }
    }

    private fun applyHeaders(builder: Request.Builder, connector: ConnectorEntity) {
        if (connector.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${connector.apiKey.trim()}")
        val extra = runCatching {
            json.parseToJsonElement(connector.headers.ifBlank { "{}" }).jsonObject
        }.getOrNull()
        extra?.forEach { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull
            if (!text.isNullOrBlank()) {
                runCatching { builder.header(key, text) }
            }
        }
    }

    private fun toolKey(connector: ConnectorEntity, remote: String): String {
        val safe = "${connector.id}_${remote}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return safe.take(62)
    }

    private fun requirePermission(connector: ConnectorEntity, permission: String) {
        val allowed = connector.permissions.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (permission !in allowed) throw ApiException("Permission denied by ${connector.name}: $permission")
    }

    suspend fun enabledCount(): Int = withContext(Dispatchers.IO) {
        dao.all().count { it.enabled }
    }

    suspend fun requiredConnectorCount(): Int = withContext(Dispatchers.IO) {
        val settingsNow = settings.flow.first()
        if (!settingsNow.memoryEnabled) 0 else dao.count()
    }
}
