package com.aura.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

/** One inline image sent to multimodal OpenAI-compatible models. */
data class ApiImage(val mime: String, val base64: String)

data class ApiMessage(
    val role: String,
    val content: String,
    val images: List<ApiImage> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ApiTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class Completion(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
)

/** Minimal client for any OpenAI-compatible `/chat/completions` + `/models` API. */
class ChatApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun request(p: ProviderEntity, path: String): Request.Builder {
        val base = p.baseUrl.trim().trimEnd('/')
        val b = Request.Builder().url(base + path)
        if (p.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${p.apiKey.trim()}")
        return b
    }

    suspend fun listModels(p: ProviderEntity): List<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request(p, "/models").get().build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException(errorMessage(body, r.code), r.code)
                val root = json.parseToJsonElement(body)
                val arr = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: JsonArray(emptyList())
                arr.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
                    .map { it.removePrefix("models/") }
                    .distinct()
                    .sorted()
            }
        } catch (e: IllegalArgumentException) {
            throw ApiException("Invalid base URL. Use a full address such as https://api.openai.com/v1")
        }
    }

    fun stream(p: ProviderEntity, model: String, messages: List<ApiMessage>, temperature: Float): Flow<String> = flow {
        val payload = buildPayload(model, messages, temperature, emptyList(), stream = true).toString()
        val call = try {
            client.newCall(
                request(p, "/chat/completions")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            )
        } catch (e: IllegalArgumentException) {
            throw ApiException("Invalid base URL. Use a full address such as https://api.openai.com/v1")
        }

        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ApiException(errorMessage(resp.body?.string().orEmpty(), resp.code), resp.code)
                }
                val source = resp.body?.source() ?: throw ApiException("Empty response from server")
                var emitted = false
                val raw = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val data = line.substring(5).trim()
                        if (data == "[DONE]") break
                        val delta = parseDelta(data)
                        if (!delta.isNullOrEmpty()) {
                            emitted = true
                            emit(delta)
                        }
                    } else {
                        raw.append(line)
                    }
                }
                // Some servers ignore `stream: true` and return one JSON document.
                if (!emitted && raw.isNotBlank()) {
                    parseFull(raw.toString())?.let { emit(it) }
                }
            }
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** Non-streaming call with optional tool definitions. Used for memory jobs and connectors. */
    suspend fun complete(
        p: ProviderEntity,
        model: String,
        messages: List<ApiMessage>,
        temperature: Float = 0.4f,
        tools: List<ApiTool> = emptyList(),
    ): Completion = withContext(Dispatchers.IO) {
        val payload = buildPayload(model, messages, temperature, tools, stream = false).toString()
        val request = try {
            request(p, "/chat/completions")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: IllegalArgumentException) {
            throw ApiException("Invalid base URL. Use a full address such as https://api.openai.com/v1")
        }
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(errorMessage(body, resp.code), resp.code)
            val choice = runCatching {
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            }.getOrNull() ?: throw ApiException("The provider returned an unreadable response.")
            val message = choice["message"]?.jsonObject
            val content = when (val c = message?.get("content")) {
                is JsonPrimitive -> c.contentOrNull.orEmpty()
                is JsonArray -> c.joinToString("") { part ->
                    (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                }
                else -> ""
            }
            val calls = message?.get("tool_calls")?.jsonArray?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
                val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_${System.nanoTime()}",
                    name = name,
                    arguments = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
                )
            }.orEmpty()
            Completion(content.trim(), calls)
        }
    }

    /** Simple helper for one-off background jobs. */
    suspend fun completeText(
        p: ProviderEntity,
        model: String,
        system: String?,
        user: String,
        temperature: Float = 0.3f,
    ): String {
        val messages = buildList {
            if (!system.isNullOrBlank()) add(ApiMessage("system", system))
            add(ApiMessage("user", user))
        }
        return complete(p, model, messages, temperature).content
    }

    private fun buildPayload(
        model: String,
        messages: List<ApiMessage>,
        temperature: Float,
        tools: List<ApiTool>,
        stream: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", stream)
        put("temperature", temperature.toDouble())
        putJsonArray("messages") {
            messages.forEach { m -> add(messageJson(m)) }
        }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    }
                }
            }
            put("tool_choice", "auto")
        }
    }

    private fun messageJson(m: ApiMessage): JsonObject = buildJsonObject {
        put("role", m.role)
        if (m.toolCallId != null) put("tool_call_id", m.toolCallId)
        when {
            m.toolCalls.isNotEmpty() -> {
                if (m.content.isNotBlank()) put("content", m.content) else put("content", JsonNull)
                putJsonArray("tool_calls") {
                    m.toolCalls.forEach { call ->
                        addJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }
                        }
                    }
                }
            }
            m.images.isNotEmpty() -> {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", m.content)
                    }
                    m.images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:${image.mime};base64,${image.base64}")
                            }
                        }
                    }
                }
            }
            else -> put("content", m.content)
        }
    }

    private fun parseDelta(data: String): String? = try {
        val choice = json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        (choice?.get("delta")?.jsonObject?.get("content") as? JsonPrimitive)?.contentOrNull
    } catch (e: Exception) {
        null
    }

    private fun parseFull(body: String): String? = try {
        val choice = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        (choice?.get("message")?.jsonObject?.get("content") as? JsonPrimitive)?.contentOrNull
    } catch (e: Exception) {
        null
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = try {
            val root = json.parseToJsonElement(body)
            val err = (root as? JsonObject)?.get("error")
            when (err) {
                is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> err.contentOrNull
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        val hint = when (code) {
            401, 403 -> " (check your API key)"
            404 -> " (check the base URL and model name)"
            429 -> " (rate limit reached, try again shortly)"
            else -> ""
        }
        return "HTTP $code$hint" + if (detail.isNullOrBlank()) "" else ": $detail"
    }
}
