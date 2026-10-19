package com.aura.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.data.ApiException
import com.aura.chat.data.ApiMessage
import com.aura.chat.data.ApiTool
import com.aura.chat.data.AppSettings
import com.aura.chat.data.AttachmentEntity
import com.aura.chat.data.AudioPreview
import com.aura.chat.data.ConnectorEntity
import com.aura.chat.data.ConnectorTool
import com.aura.chat.data.ConversationEntity
import com.aura.chat.data.MessageEntity
import com.aura.chat.data.Personas
import com.aura.chat.data.PromptEntity
import com.aura.chat.data.Repository
import com.aura.chat.ui.components.AuraOrb
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.IconBadge
import com.aura.chat.ui.components.MarkdownText
import com.aura.chat.ui.components.glass
import com.aura.chat.ui.components.rememberHaptic
import com.aura.chat.ui.shareText
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(private val c: AppContainer, val id: String) : ViewModel() {
    private val repo = c.repo

    val conversation: StateFlow<ConversationEntity?> = repo.conv.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val messages: StateFlow<List<MessageEntity>> = repo.msg.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> = c.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val prompts: StateFlow<List<PromptEntity>> = repo.prompts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attachments: StateFlow<List<AttachmentEntity>> = repo.attachments.observeForConversation(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val modelLabel: StateFlow<String> = combine(repo.providers.observeAll(), c.settings.flow, conversation) { ps, s, cv ->
        val p = ps.firstOrNull { it.id == s.activeProvider } ?: ps.firstOrNull()
        val modelOverride = cv?.model.orEmpty()
        when {
            p == null -> NO_PROVIDER
            modelOverride.isNotBlank() -> modelOverride
            p.model.isBlank() -> "Select model"
            else -> p.model
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** null = idle, "" = waiting for first token, otherwise the partial reply. */
    var streaming by mutableStateOf<String?>(null)
        private set
    var toolStatus by mutableStateOf<String?>(null)
        private set
    var attachError by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    fun attach(uri: Uri) {
        viewModelScope.launch {
            try {
                val key = repo.geminiKey()
                c.attachments.import(uri, id, key)
            } catch (e: Exception) {
                attachError = e.message ?: "Could not attach that file."
            }
        }
    }

    fun removeAttachment(attachment: AttachmentEntity) {
        viewModelScope.launch { c.attachments.delete(attachment) }
    }

    fun clearAttachError() { attachError = null }

    fun send(text: String) {
        val t = text.trim()
        val pending = attachments.value.filter { it.messageId == 0L }
        if ((t.isEmpty() && pending.isEmpty()) || streaming != null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val messageId = repo.msg.insert(MessageEntity(conversationId = id, role = "user", content = t, createdAt = now))
            repo.attachments.attachPending(id, messageId)
            val conv = repo.conv.get(id)
            if (conv != null && conv.title == Repository.NEW_TITLE) {
                repo.conv.rename(id, makeTitle(t.ifBlank { pending.firstOrNull()?.name ?: "Attachment" }))
            }
            repo.conv.touch(id, now)
            generate()
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun regenerate() {
        if (streaming != null) return
        viewModelScope.launch {
            val list = repo.msg.list(id)
            val lastUser = list.indexOfLast { it.role == "user" }
            if (lastUser < 0) return@launch
            list.drop(lastUser + 1).forEach { repo.msg.delete(it.id) }
            generate()
        }
    }

    fun setFeedback(messageId: Long, value: Int) {
        viewModelScope.launch { repo.msg.setFeedback(messageId, value) }
    }

    private fun generate() {
        job?.cancel()
        streaming = ""
        toolStatus = null
        job = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                val provider = repo.activeProvider()
                    ?: throw ApiException("No AI provider is connected. Open Settings → Providers and add one.")
                val conv = repo.conv.get(id) ?: return@launch
                val model = conv.model.ifBlank { provider.model }
                if (model.isBlank()) throw ApiException("No model selected. Open Settings → Model and pick one.")
                val s = c.settings.flow.first()
                val all = repo.msg.list(id).filter { !it.isError }
                val lastUser = all.lastOrNull { it.role == "user" }?.content.orEmpty()

                // Local commands: skills, connectors and explicit memory, no model call needed.
                handleLocalCommand(lastUser)?.let { confirmation ->
                    buffer.append(confirmation)
                    streaming = buffer.toString()
                    saveAssistant(confirmation, false)
                    return@launch
                }

                val history = all.takeLast(s.contextSize).dropWhile { it.role != "user" }
                val attachmentsByMessage = repo.attachments.listForConversation(id).groupBy { it.messageId }
                val payload = buildList {
                    val system = buildSystemPrompt(conv, s, lastUser)
                    if (system.isNotBlank()) add(ApiMessage("system", system))
                    history.forEach { message ->
                        val files = attachmentsByMessage[message.id].orEmpty()
                        val text = if (files.isEmpty()) message.content
                        else (message.content + "\n\n" + c.attachments.textFor(files)).trim()
                        val images = if (files.isEmpty()) emptyList() else c.attachments.imagesFor(files)
                        add(ApiMessage(message.role, text, images))
                    }
                }

                val tools = if (all.isEmpty()) emptyList() else c.connectors.toolsForModel()
                if (tools.isEmpty()) {
                    c.api.stream(provider, model, payload, s.temperature).collect { piece ->
                        buffer.append(piece)
                        streaming = buffer.toString()
                    }
                } else {
                    val toolMessages = payload.toMutableList()
                    runAgentLoop(provider, model, toolMessages, tools, s.temperature, buffer)
                }

                if (buffer.isBlank()) throw ApiException("The model returned an empty reply. Try again or pick another model.")
                saveAssistant(buffer.toString(), false)
                scheduleMemory(provider, model, lastUser, buffer.toString())
            } catch (e: CancellationException) {
                if (buffer.isNotBlank()) withContext(NonCancellable) { saveAssistant(buffer.toString(), false) }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    if (buffer.isNotBlank()) saveAssistant(buffer.toString(), false)
                    saveAssistant(e.message ?: "Something went wrong.", true)
                }
            } finally {
                streaming = null
                toolStatus = null
            }
        }
    }

    private suspend fun runAgentLoop(
        provider: com.aura.chat.data.ProviderEntity,
        model: String,
        messages: MutableList<ApiMessage>,
        tools: List<ConnectorTool>,
        temperature: Float,
        buffer: StringBuilder,
    ) {
        val apiTools = tools.map { tool ->
            ApiTool(
                name = tool.key,
                description = tool.description.ifBlank { tool.displayName },
                parameters = tool.parameters,
            )
        }
        var rounds = 0
        while (rounds < 4) {
            toolStatus = if (rounds == 0) "Checking your connectors…" else "Thinking…"
            val completion = c.api.complete(provider, model, messages, temperature, apiTools)
            if (completion.toolCalls.isEmpty()) {
                if (completion.content.isNotBlank()) {
                    buffer.append(completion.content)
                    streaming = buffer.toString()
                }
                return
            }
            messages += ApiMessage("assistant", completion.content, toolCalls = completion.toolCalls)
            completion.toolCalls.forEach { call ->
                val display = tools.firstOrNull { it.key == call.name }?.let { "${it.connector.name} · ${it.displayName}" } ?: call.name
                toolStatus = "Using $display…"
                val result = runCatching { c.connectors.execute(call.name, call.arguments) }
                    .getOrElse { "Tool error: ${it.message ?: "unknown error"}" }
                messages += ApiMessage("tool", result.take(14000), toolCallId = call.id)
            }
            rounds++
        }
        toolStatus = null
    }

    private suspend fun buildSystemPrompt(conv: ConversationEntity, settings: AppSettings, query: String): String {
        val parts = mutableListOf<String>()
        if (conv.systemPrompt.isNotBlank()) parts += conv.systemPrompt
        if (settings.memoryEnabled) {
            val memory = c.memory.contextFor(query)
            if (memory.isNotBlank()) parts += memory
        }
        val skills = c.skills.promptForEnabled()
        if (skills.isNotBlank()) parts += skills
        return parts.joinToString("\n\n")
    }

    private fun scheduleMemory(
        provider: com.aura.chat.data.ProviderEntity,
        model: String,
        userText: String,
        reply: String,
    ) {
        if (userText.isBlank()) return
        c.appScope.launch {
            runCatching { c.memory.extractAndSave(provider, model, userText, reply, "chat", id) }
        }
    }

    /** Handles "install skill …", "add connector …" and "remember …" directly in chat. */
    private suspend fun handleLocalCommand(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        Regex("(?i)(?:install|add|load)\\s+(?:a\\s+)?skill\\s*:?\\s*(https?://\\S+)").find(trimmed)?.let { match ->
            return try {
                val skill = c.skills.install(match.groupValues[1])
                "Installed the skill “${skill.name}”. Open Skills to enable it for every chat and call."
            } catch (e: Exception) {
                "I could not install that skill: ${e.message}"
            }
        }
        if (Regex("(?i)\\b(connector|mcp server)\\b").containsMatchIn(trimmed)) {
            if (Regex("(?i)\\b(test|check|health|status)\\b").containsMatchIn(trimmed)) {
                val requested = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(trimmed)?.value?.trimEnd('.', ',', ')')
                val connector = c.repo.connectors.all().let { list ->
                    requested?.let { url -> list.firstOrNull { it.url.trimEnd('/') == url.trimEnd('/') } }
                        ?: list.firstOrNull { trimmed.contains(it.name, ignoreCase = true) }
                        ?: list.firstOrNull()
                }
                if (connector == null) return "No connector is saved yet. Say: add connector Files https://example.com/mcp"
                val result = c.connectors.test(connector)
                return "${connector.name}: ${result.message}" + if (result.tools.isNotEmpty()) " Tools: ${result.tools.joinToString(", ")}" else ""
            }
            val url = Regex("https?://\\S+").find(trimmed)?.value
            if (url != null && Regex("(?i)\\b(add|connect|create|new)\\b").containsMatchIn(trimmed)) {
                val type = when {
                    Regex("(?i)\\bapi\\b").containsMatchIn(trimmed) -> "api"
                    Regex("(?i)\\bapp\\b|deep link|بھیج|application").containsMatchIn(trimmed) -> "app"
                    else -> "mcp"
                }
                val beforeUrl = trimmed.substringBefore(url)
                val name = beforeUrl.substringAfterLast("connector", "")
                    .replace(Regex("(?i)\\b(add|connect|create|new|a|an|the)\\b"), "")
                    .trim().trim(':','-',' ').ifBlank { url.substringAfter("://").substringBefore('/') }
                return try {
                    val entity = ConnectorEntity(
                        name = name.take(60),
                        type = type,
                        url = url.trim().trimEnd('/'),
                        description = "Added from chat",
                        createdAt = System.currentTimeMillis(),
                    )
                    c.repo.connectors.insert(entity)
                    c.connectors.toolsForModel(force = true)
                    "Added the ${type.uppercase()} connector “${entity.name}”. Tap Test in Connectors to check it."
                } catch (e: Exception) {
                    "I could not add that connector: ${e.message}"
                }
            }
        }
        Regex("(?i)^(?:remember|save to memory|note that|yaad rakho|یاد رکھو|याद रखो|تذكر)\\b[:\\s]*(.+)")
            .find(trimmed)?.let { match ->
                val fact = match.groupValues[1].trim()
                if (fact.isNotBlank()) {
                    c.memory.rememberExplicit(fact, "chat", id)
                    return "Saved to long-term memory: “${fact.take(120)}”."
                }
            }
        if (Regex("(?i)^(?:list|show)\\s+(?:my\\s+)?(?:connectors|skills|memories|memory)\\b").containsMatchIn(trimmed)) {
            return buildString {
                if (Regex("(?i)connector").containsMatchIn(trimmed)) {
                    val list = c.repo.connectors.all()
                    appendLine("Connectors (${list.size}):")
                    if (list.isEmpty()) appendLine("• none yet") else list.forEach { appendLine("• ${it.name} — ${it.type} — ${it.url}") }
                }
                if (Regex("(?i)skill").containsMatchIn(trimmed)) {
                    val list = c.repo.skills.all()
                    appendLine("Skills (${list.size}):")
                    if (list.isEmpty()) appendLine("• none yet") else list.forEach { appendLine("• ${it.name} ${if (it.enabled) "(enabled)" else "(disabled)"}") }
                }
                if (Regex("(?i)memor").containsMatchIn(trimmed)) {
                    val list = c.repo.memories.all()
                    appendLine("Memories (${list.size}):")
                    if (list.isEmpty()) appendLine("• none yet") else list.take(20).forEach { appendLine("• [${it.category}] ${it.title}") }
                }
            }.trim()
        }
        return null
    }

    private suspend fun saveAssistant(text: String, error: Boolean) {
        val now = System.currentTimeMillis()
        repo.msg.insert(MessageEntity(conversationId = id, role = "assistant", content = text, isError = error, createdAt = now))
        repo.conv.touch(id, now)
    }

    fun rename(title: String) {
        if (title.isNotBlank()) viewModelScope.launch { repo.conv.rename(id, title.trim()) }
    }

    fun updateConfig(systemPrompt: String, model: String) {
        viewModelScope.launch { repo.conv.updateConfig(id, systemPrompt.trim(), model.trim()) }
    }

    fun clear() {
        viewModelScope.launch { repo.msg.clear(id) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            job?.cancel()
            repo.conv.delete(id)
            onDone()
        }
    }

    suspend fun export(): String = repo.exportConversation(id)

    private fun makeTitle(text: String): String {
        val one = text.replace('\n', ' ').trim()
        return if (one.length <= 40) one else one.take(40).trimEnd() + "…"
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        const val NO_PROVIDER = "No provider"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    id: String,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenProviders: () -> Unit,
    onCall: () -> Unit,
) {
    val ctx = LocalContext.current
    val container = ctx.container
    val c = LocalAura.current
    val vm: ChatViewModel = viewModel(key = "chat_$id", factory = simpleFactory { ChatViewModel(container, id) })
    val conv by vm.conversation.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prompts by vm.prompts.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val modelLabel by vm.modelLabel.collectAsStateWithLifecycle()
    val generating by remember { derivedStateOf { vm.streaming != null } }

    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptic(settings.haptics)
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var showPrompts by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var speakingId by remember { mutableStateOf<Long?>(null) }
    val audioPreview = remember { AudioPreview(ctx) }
    DisposableEffect(Unit) { onDispose { audioPreview.release() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attach(uri)
    }

    LaunchedEffect(Unit) {
        container.pendingPrompt?.let {
            input = it
            container.pendingPrompt = null
        }
    }
    LaunchedEffect(vm.attachError) {
        vm.attachError?.let {
            toast(ctx, it)
            vm.clearAttachError()
        }
    }
    LaunchedEffect(vm.toolStatus) {
        vm.toolStatus?.let { /* shown in the typing bubble */ }
    }

    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        haptic()
        toast(ctx, "Copied")
    }
    val speak: (Long, String) -> Unit = { messageId, text ->
        scope.launch {
            if (speakingId == messageId) {
                container.voice.stop()
                speakingId = null
            } else {
                speakingId = messageId
                try {
                    val key = container.repo.geminiKey()
                    container.voice.speak(text, key, settings.geminiVoice, settings.geminiModel)
                } catch (e: Exception) {
                    toast(ctx, e.message ?: "Could not play that reply.")
                } finally {
                    speakingId = null
                }
            }
        }
    }

    val persona = Personas.find(conv?.personaId.orEmpty()) ?: Personas.all.first()
    val reversed = remember(messages) { messages.asReversed() }
    val attachmentsByMessage = remember(attachments) { attachments.groupBy { it.messageId } }
    val pending = attachments.filter { it.messageId == 0L }
    val lastId = messages.lastOrNull()?.id
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    conv?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { showRename = true },
                )
                Row(
                    Modifier
                        .padding(top = 3.dp)
                        .glass(RoundedCornerShape(50))
                        .clickable { if (modelLabel == ChatViewModel.NO_PROVIDER) onOpenProviders() else onOpenModels() }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(if (modelLabel == ChatViewModel.NO_PROVIDER) c.danger else c.accent2, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(modelLabel, style = MaterialTheme.typography.labelMedium, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                    Icon(Icons.Rounded.ExpandMore, null, tint = c.textDim, modifier = Modifier.size(14.dp))
                }
            }
            GlassIconButton(Icons.Rounded.Mic, onCall, contentDescription = "Start a voice call")
            Spacer(Modifier.width(8.dp))
            Box {
                GlassIconButton(Icons.Rounded.MoreVert, { menu = true }, contentDescription = "Menu")
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; showRename = true })
                    DropdownMenuItem(text = { Text("Chat settings") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = { menu = false; showConfig = true })
                    DropdownMenuItem(text = { Text("Media & files") }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }, onClick = { menu = false; showMedia = true })
                    DropdownMenuItem(text = { Text("Share chat") }, leadingIcon = { Icon(Icons.Rounded.IosShare, null) }, onClick = {
                        menu = false
                        scope.launch { shareText(ctx, vm.export()) }
                    })
                    DropdownMenuItem(text = { Text("Clear messages") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, onClick = { menu = false; showClear = true })
                    DropdownMenuItem(text = { Text("Delete chat", color = c.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.danger) }, onClick = { menu = false; showDelete = true })
                }
            }
        }

        if (modelLabel == ChatViewModel.NO_PROVIDER) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable(onClick = onOpenProviders).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, null, tint = c.accent2)
                Spacer(Modifier.width(12.dp))
                Text("Connect an AI provider to start chatting", style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (generating) {
                item(key = "streaming") {
                    StreamingBubble(
                        text = vm.streaming,
                        toolStatus = vm.toolStatus,
                        onCopy = copy,
                        onSpeak = speak,
                        speakingId = speakingId,
                    )
                }
            }
            items(reversed, key = { it.id }) { m ->
                val files = attachmentsByMessage[m.id].orEmpty()
                if (m.role == "user") {
                    UserBubble(m.content, files, copy)
                } else {
                    AssistantBubble(
                        id = m.id,
                        text = m.content,
                        feedback = m.feedback,
                        isError = m.isError,
                        canRetry = m.id == lastId && !generating,
                        speaking = speakingId == m.id,
                        onCopy = copy,
                        onSpeak = { speak(m.id, m.content) },
                        onShare = { scope.launch { shareText(ctx, m.content) } },
                        onFeedback = { vm.setFeedback(m.id, if (m.feedback == it) 0 else it) },
                        onRetry = { vm.regenerate() },
                    )
                }
            }
            if (messages.isEmpty() && !generating) {
                item(key = "empty") {
                    EmptyChat(persona.name, persona.tagline, persona.suggestions, persona.icon) { input = it }
                }
            }
        }

        if (pending.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pending.take(3).forEach { file ->
                    Row(
                        Modifier.glass(RoundedCornerShape(50)).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(kindIcon(file.kind), null, tint = c.accent2, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                        Icon(
                            Icons.Rounded.Stop, "Remove",
                            tint = c.textDim,
                            modifier = Modifier.size(16.dp).clickable { vm.removeAttachment(file) }.padding(start = 4.dp),
                        )
                    }
                }
                if (pending.size > 3) Text("+${pending.size - 3}", color = c.textDim, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            GlassIconButton(Icons.Rounded.Bolt, { showPrompts = true }, size = 52.dp, contentDescription = "Prompt library")
            Spacer(Modifier.width(8.dp))
            GlassTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = "Message Aura",
                singleLine = false,
                maxLines = 6,
                shape = RoundedCornerShape(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.AttachFile, { showMedia = true }, size = 52.dp, contentDescription = "Media and files")
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank() || pending.isNotEmpty()
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (canSend || generating) c.gradient else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.10f)))
                    .clickable {
                        if (generating) {
                            vm.stop()
                        } else if (canSend) {
                            vm.send(input)
                            input = ""
                        } else {
                            onCall()
                        }
                        haptic()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        generating -> Icons.Rounded.Stop
                        canSend -> Icons.Rounded.ArrowUpward
                        else -> Icons.Rounded.Mic
                    },
                    if (generating) "Stop" else if (canSend) "Send" else "Call",
                    tint = Color.White,
                )
            }
        }
    }

    if (showMedia) {
        ModalBottomSheet(
            onDismissRequest = { showMedia = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
        ) {
            MediaSheet(
                attachments = attachments,
                onPick = { picker.launch(arrayOf("*/*")) },
                onRemove = { vm.removeAttachment(it) },
                preview = audioPreview,
                onPlay = { file -> audioPreview.toggle(file) { msg -> toast(ctx, msg) } },
            )
        }
    }

    if (showRename) {
        var text by remember { mutableStateOf(conv?.title.orEmpty()) }
        GlassDialog("Rename chat", onDismiss = { showRename = false }, confirmText = "Save", onConfirm = {
            vm.rename(text)
            showRename = false
        }) { GlassTextField(text, { text = it }, placeholder = "Chat title") }
    }
    if (showConfig) {
        var sp by remember { mutableStateOf(conv?.systemPrompt.orEmpty()) }
        var model by remember { mutableStateOf(conv?.model.orEmpty()) }
        GlassDialog("Chat settings", onDismiss = { showConfig = false }, confirmText = "Save", onConfirm = {
            vm.updateConfig(sp, model)
            showConfig = false
        }) {
            GlassTextField(sp, { sp = it }, label = "System prompt", placeholder = "How should Aura behave in this chat?", singleLine = false, minLines = 4, maxLines = 8)
            Spacer(Modifier.height(12.dp))
            GlassTextField(model, { model = it }, label = "Model override (optional)", placeholder = "Leave empty to use the active model")
        }
    }
    if (showPrompts) {
        GlassDialog("Prompt library", onDismiss = { showPrompts = false }) {
            if (prompts.isEmpty()) Text("No saved prompts yet. Add some from Settings → Prompt library.", color = c.textDim)
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prompts.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable {
                            input = p.text
                            showPrompts = false
                        }.padding(12.dp)
                    ) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(p.text.trim(), color = c.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (showClear) {
        GlassDialog("Clear all messages?", onDismiss = { showClear = false }, confirmText = "Clear", danger = true, onConfirm = {
            vm.clear()
            showClear = false
        }) { Text("The conversation stays, but every message in it is removed.", color = c.textDim) }
    }
    if (showDelete) {
        GlassDialog("Delete this chat?", onDismiss = { showDelete = false }, confirmText = "Delete", danger = true, onConfirm = {
            showDelete = false
            vm.delete(onBack)
        }) { Text("This chat and its messages will be permanently removed.", color = c.textDim) }
    }
}

@Composable
private fun StreamingBubble(
    text: String?,
    toolStatus: String?,
    onCopy: (String) -> Unit,
    onSpeak: (Long, String) -> Unit,
    speakingId: Long?,
) {
    val body = text ?: return
    if (body.isEmpty()) {
        TypingBubble(toolStatus)
    } else {
        AssistantBubble(
            id = -1,
            text = body,
            feedback = 0,
            isError = false,
            canRetry = false,
            speaking = speakingId == -1L,
            onCopy = onCopy,
            onSpeak = { onSpeak(-1L, body) },
            onShare = {},
            onFeedback = {},
            onRetry = {},
        )
    }
}

@Composable
private fun UserBubble(text: String, files: List<AttachmentEntity>, onCopy: (String) -> Unit) {
    val c = LocalAura.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            if (files.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    files.take(4).forEach { file -> AttachmentChip(file, alignEnd = true) }
                }
            }
            if (text.isNotBlank()) {
                Box(
                    Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                        .background(c.gradient)
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    SelectionContainer { Text(text, color = Color.White) }
                }
            }
            SmallAction(Icons.Rounded.ContentCopy, "Copy", { onCopy(text) })
        }
    }
}

@Composable
private fun AttachmentChip(file: AttachmentEntity, alignEnd: Boolean) {
    val c = LocalAura.current
    Row(
        Modifier.glass(RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(file.kind), null, tint = c.accent2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
    }
}

@Composable
private fun AssistantBubble(
    id: Long,
    text: String,
    feedback: Int,
    isError: Boolean,
    canRetry: Boolean,
    speaking: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit,
    onFeedback: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val c = LocalAura.current
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 4.dp)) { IconBadge(Icons.Rounded.AutoAwesome, size = 30.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                    .let { if (isError) it.background(c.danger.copy(alpha = 0.14f)) else it.glass(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isError) {
                    Text(text, color = c.danger)
                } else {
                    SelectionContainer { MarkdownText(text, onCopy) }
                }
            }
            if (!isError) {
                PrivateLinks(text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isError) {
                    SmallAction(if (speaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (speaking) "Stop" else "Listen", onSpeak)
                    SmallAction(Icons.Rounded.ContentCopy, "Copy", { onCopy(text) })
                    SmallAction(Icons.Rounded.IosShare, "Share", onShare)
                    SmallAction(Icons.Rounded.ThumbUp, null, { onFeedback(1) }, tint = if (feedback == 1) c.accent2 else null)
                    SmallAction(Icons.Rounded.ThumbDown, null, { onFeedback(-1) }, tint = if (feedback == -1) c.danger else null)
                }
                if (canRetry) SmallAction(Icons.Rounded.Refresh, if (isError) "Retry" else "Regenerate", onRetry)
            }
        }
    }
}

/** Links are shown in a private, non-clickable box with a copy button only. */
@Composable
private fun PrivateLinks(text: String) {
    val urls = remember(text) {
        Regex("https?://[^\\s<>()\\[\\]]+").findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().take(3).toList()
    }
    if (urls.isEmpty()) return
    val c = LocalAura.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        urls.forEach { url ->
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Link, null, tint = c.accent2, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    Text(url, color = c.textDim, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Copy",
                    color = c.accent2,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(url))
                    }.padding(4.dp),
                )
            }
        }
        Text("Links are kept private — nothing opens automatically.", color = c.textDim.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MediaSheet(
    attachments: List<AttachmentEntity>,
    onPick: () -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
    preview: AudioPreview,
    onPlay: (java.io.File) -> Unit,
) {
    val c = LocalAura.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Media & files", style = MaterialTheme.typography.titleLarge)
        Text(
            "Attach any file: PDFs, Word and Excel documents, ZIP archives, images, code and voice notes " +
                "from WhatsApp or any other app. Unsupported audio is converted automatically for playback.",
            color = c.textDim, style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onPick).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AttachFile, null, tint = c.accent2)
                Spacer(Modifier.width(10.dp))
                Text("Choose any file to upload", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (attachments.isEmpty()) {
            Text("No files in this chat yet.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { file ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(kindIcon(file.kind), null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${file.kind} · ${file.size / 1024} KB · ${if (file.messageId == 0L) "pending" else "sent"}",
                            color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                        )
                        if (file.extractedText.isNotBlank() && file.kind == "audio") {
                            Text(
                                file.extractedText.take(140),
                                color = c.textDim.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (file.kind == "audio") {
                        val playing = preview.playingPath == java.io.File(file.localPath).absolutePath
                        Icon(
                            if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            "Play",
                            tint = c.accent2,
                            modifier = Modifier.size(26.dp).clickable {
                                onPlay(java.io.File(file.localPath))
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(
                        Icons.Rounded.Delete, "Remove",
                        tint = c.danger,
                        modifier = Modifier.size(20.dp).clickable { onRemove(file) },
                    )
                }
            }
        }
    }
}

private fun kindIcon(kind: String) = when (kind) {
    "image" -> Icons.Rounded.Image
    "audio" -> Icons.Rounded.GraphicEq
    "pdf", "pdf_scan" -> Icons.Rounded.Description
    "zip" -> Icons.Rounded.InsertDriveFile
    else -> Icons.Rounded.Description
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit = {},
    tint: Color? = null,
) {
    val c = LocalAura.current
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint ?: c.textDim, modifier = Modifier.size(15.dp))
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.width(5.dp))
            Text(label, color = tint ?: c.textDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TypingBubble(status: String?) {
    val c = LocalAura.current
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 4.dp)) { IconBadge(Icons.Rounded.AutoAwesome, size = 30.dp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(
                Modifier.glass(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    val a by t.animateFloat(
                        0.25f, 1f,
                        infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse),
                        label = "dot$i",
                    )
                    Box(Modifier.size(8.dp).alpha(a).background(c.accent2, CircleShape))
                }
            }
            if (!status.isNullOrBlank()) {
                Text(status, color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun EmptyChat(name: String, tagline: String, suggestions: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, onPick: (String) -> Unit) {
    val c = LocalAura.current
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AuraOrb(size = 130.dp)
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(tagline, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Spacer(Modifier.height(18.dp))
        suggestions.forEach { s ->
            Row(
                Modifier.padding(vertical = 4.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { onPick(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = c.accent2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
