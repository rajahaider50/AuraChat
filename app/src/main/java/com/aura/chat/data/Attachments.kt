package com.aura.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Speech-to-text through the native Gemini API (works for WhatsApp voice notes too). */
class GeminiAudio {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(
        context: Context,
        file: File,
        mime: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ""
        var source = file
        var type = mime.ifBlank { "audio/ogg" }
        if (source.length() > 18L * 1024 * 1024 || type.contains("opus", true) && !type.contains("ogg", true)) {
            val wav = File(AudioFiles.cacheDir(context), source.nameWithoutExtension + "-stt.wav")
            if (AudioFiles.transcodeToWav(source, wav)) {
                source = wav
                type = "audio/wav"
            }
        }
        if (source.length() > 19L * 1024 * 1024) return@withContext ""
        val bytes = source.readBytes()
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "Transcribe this audio exactly as spoken. Keep the original language, " +
                                "do not summarize and do not add commentary. If it is silent, return an empty string.")
                        }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", if (type.startsWith("audio/")) type else "audio/ogg")
                                put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") { put("temperature", 0.0) }
        }.toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey.trim())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext ""
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.joinToString("") { part ->
                        (part as? kotlinx.serialization.json.JsonObject)
                            ?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
            }.getOrNull()?.trim().orEmpty()
        }
    }
}

/** Extracts readable content from PDF, Office, ZIP, text and audio attachments. */
class DocumentReader(
    private val context: Context,
    private val geminiAudio: GeminiAudio,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun process(
        file: File,
        mime: String,
        kind: String,
        attachmentId: Long,
        geminiKey: String,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            when {
                kind == "pdf" -> extractPdf(file, attachmentId)
                kind == "audio" -> {
                    val text = geminiAudio.transcribe(context, file, mime, geminiKey)
                    (text.ifBlank { "[Voice recording attached: ${file.name}. No transcript available.]" }) to "audio"
                }
                kind == "image" -> "[Image attached: ${file.name}]" to "image"
                kind == "zip" -> extractZip(file) to "zip"
                kind == "text" -> {
                    val ext = file.extension.lowercase()
                    if (ext in setOf("docx", "pptx", "xlsx")) extractOffice(file) to "text"
                    else file.readTextLimited() to "text"
                }
                else -> {
                    val text = file.readTextLimitedOrEmpty()
                    if (text.isNotBlank()) text to "text" else "[File attached: ${file.name}]" to kind
                }
            }
        }.getOrElse { "[File attached: ${file.name}. Could not read its contents automatically.]" to kind }
    }

    private fun extractPdf(file: File, attachmentId: Long): Pair<String, String> {
        PDFBoxResourceLoader.init(context)
        val text = runCatching {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.getText(doc)
            }
        }.getOrDefault("")
        if (text.trim().length >= 30) return text.take(30000) to "pdf"
        // Scanned / image-only PDF: render pages so vision models can read them.
        val pages = renderPdfPages(file, attachmentId)
        return if (pages > 0) {
            "[Scanned PDF attached: ${file.name}. $pages page image(s) are included for vision. " +
                "Read the pages and answer the user's request.]" to "pdf_scan"
        } else {
            (text.ifBlank { "[PDF attached: ${file.name}. No extractable text.]" }) to "pdf"
        }
    }

    private fun renderPdfPages(file: File, attachmentId: Long): Int {
        val dir = File(context.filesDir, "attachments/pages").apply { mkdirs() }
        var rendered = 0
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = minOf(4, renderer.pageCount)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val width = 1240
                            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val out = File(dir, "${attachmentId}_$i.jpg")
                            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                            bitmap.recycle()
                            rendered++
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return rendered
        }
        return rendered
    }

    private fun extractOffice(file: File): String {
        val out = StringBuilder()
        runCatching {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(".xml") &&
                        (name.startsWith("word/") || name.startsWith("ppt/") || name.startsWith("xl/"))) {
                        val raw = runCatching { zip.readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
                        if (raw.isNotBlank() && out.length < 30000) {
                            val readable = raw
                                .replace(Regex("</(w:p|a:p|row)>"), "\n")
                                .replace(Regex("<[^>]+>"), " ")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&apos;", "'")
                                .replace(Regex("[ \\t]+"), " ")
                                .replace(Regex("\\n\\s*\\n+"), "\n")
                            out.appendLine(readable.trim())
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return out.toString().trim().take(30000).ifBlank { "[Office file: ${file.name}. No readable text found.]" }
    }

    private fun extractZip(file: File): String {
        val out = StringBuilder()
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (count >= 60) break
                if (!entry.isDirectory) {
                    out.appendLine("• ${entry.name} (${entry.size} bytes)")
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (entry.size in 1L..200_000L && ext in TEXT_EXTENSIONS) {
                        val bytes = zip.readBytes()
                        val text = String(bytes, Charsets.UTF_8).take(1500)
                        out.appendLine(text)
                        out.appendLine()
                    }
                }
                zip.closeEntry()
                count++
            }
        }
        return out.toString().take(30000)
    }

    private fun File.readTextLimited(): String {
        val bytes = readBytes()
        val probe = bytes.take(4096)
        if (probe.contains(0.toByte())) return "[Binary file: $name]"
        return String(bytes, Charsets.UTF_8).take(30000)
    }

    private fun File.readTextLimitedOrEmpty(): String = runCatching { readTextLimited() }.getOrDefault("")

    fun pageImages(attachment: AttachmentEntity): List<File> {
        val dir = File(context.filesDir, "attachments/pages")
        return dir.listFiles { f -> f.name.startsWith("${attachment.id}_") }
            ?.sortedBy { it.name }?.take(4).orEmpty()
    }

    fun imageDataUrls(attachment: AttachmentEntity): List<ApiImage> {
        val files = if (attachment.kind == "pdf_scan") pageImages(attachment) else listOf(File(attachment.localPath))
        val mime = if (attachment.kind == "pdf_scan") "image/jpeg" else attachment.mime.ifBlank { "image/jpeg" }
        var total = 0L
        return files.mapNotNull { f ->
            if (!f.exists()) return@mapNotNull null
            val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@mapNotNull null
            total += bytes.size
            if (total > 7L * 1024 * 1024) return@mapNotNull null
            ApiImage(mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "csv", "tsv", "xml", "html", "htm", "yaml", "yml", "ini", "log",
            "kt", "java", "py", "js", "ts", "css", "sql", "sh", "c", "cpp", "h", "rs", "go", "php", "rb",
        )
    }
}

/** Copies any Android document into private storage and prepares it for the model. */
class AttachmentManager(
    private val context: Context,
    private val dao: AttachmentDao,
    private val reader: DocumentReader,
) {
    suspend fun import(uri: Uri, conversationId: String, geminiKey: String): AttachmentEntity? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = queryName(uri) ?: "attachment-${System.currentTimeMillis()}"
            val size = querySize(uri)
            if (size > 120L * 1024 * 1024) throw ApiException("That file is larger than the 120 MB limit.")
            val mime = resolver.getType(uri)
                ?: mimeFromName(name)
                ?: "application/octet-stream"
            val kind = kindFor(mime, name)
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val safeName = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)
            val local = File(dir, "${UUID.randomUUID()}-$safeName")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(local).use { output -> input.copyTo(output, 256 * 1024) }
            } ?: throw ApiException("Could not open that file.")
            val now = System.currentTimeMillis()
            val entity = AttachmentEntity(
                messageId = 0,
                conversationId = conversationId,
                name = name.take(160),
                mime = mime,
                localPath = local.absolutePath,
                size = local.length(),
                kind = kind,
                extractedText = "",
                createdAt = now,
            )
            val id = dao.insert(entity)
            val withId = entity.copy(id = id)
            val (text, resolvedKind) = reader.process(local, mime, kind, id, geminiKey)
            dao.setExtracted(id, text, resolvedKind)
            withId.copy(extractedText = text, kind = resolvedKind)
        }

    suspend fun delete(attachment: AttachmentEntity) = withContext(Dispatchers.IO) {
        runCatching { File(attachment.localPath).delete() }
        reader.pageImages(attachment).forEach { runCatching { it.delete() } }
        dao.delete(attachment.id)
    }

    fun file(attachment: AttachmentEntity): File = File(attachment.localPath)

    suspend fun textFor(attachments: List<AttachmentEntity>): String = withContext(Dispatchers.IO) {
        buildString {
            attachments.forEach { a ->
                when (a.kind) {
                    "text", "pdf" -> {
                        appendLine()
                        appendLine("[Attached file: ${a.name}]")
                        appendLine(a.extractedText.take(14000))
                    }
                    "pdf_scan" -> {
                        appendLine()
                        appendLine("[Scanned PDF: ${a.name}. Page images are attached above for vision.]")
                    }
                    "audio" -> {
                        appendLine()
                        appendLine("[Voice recording: ${a.name}]")
                        appendLine("Transcript: ${a.extractedText.take(12000)}")
                    }
                    "zip" -> {
                        appendLine()
                        appendLine("[ZIP archive: ${a.name}]")
                        appendLine(a.extractedText.take(8000))
                    }
                    "image" -> appendLine("\n[Image attached: ${a.name}]")
                    else -> appendLine("\n[File attached: ${a.name} (${a.mime})]")
                }
            }
        }.trim().take(26000)
    }

    suspend fun imagesFor(attachments: List<AttachmentEntity>): List<ApiImage> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ApiImage>()
        attachments.take(5).forEach { a ->
            if (a.kind == "image" || a.kind == "pdf_scan") {
                out += reader.imageDataUrls(a).take(3)
            }
        }
        out.take(5)
    }

    fun kindFor(mime: String, name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "amr", "flac", "3gp") -> "audio"
            mime == "application/pdf" || ext == "pdf" -> "pdf"
            mime.contains("zip") || ext in setOf("zip", "rar", "7z", "tar", "gz") -> "zip"
            mime.startsWith("text/") || ext in setOf(
                "txt", "md", "json", "csv", "xml", "html", "kt", "java", "py", "js", "ts", "css", "sql",
                "doc", "docx", "ppt", "pptx", "xls", "xlsx", "log", "yaml", "yml", "ini",
            ) -> "text"
            else -> "file"
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } else -1L
        }
    }.getOrDefault(-1L)

    private fun mimeFromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "amr" -> "audio/amr"
            "flac" -> "audio/flac"
            "zip" -> "application/zip"
            "txt", "md", "csv", "json", "xml", "html", "kt", "java", "py", "js", "ts" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> null
        }
    }
}
