package com.aura.chat.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.data.AppSettings
import com.aura.chat.data.ConversationWithPreview
import com.aura.chat.data.Personas
import com.aura.chat.ui.components.AuraOrb
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.GradientButton
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.components.glass
import com.aura.chat.ui.relativeTime
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.LocalAura
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val conversations: StateFlow<List<ConversationWithPreview>> = repo.conv.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val providerCount: StateFlow<Int> = repo.providers.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)
    val settings: StateFlow<AppSettings> = c.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun newChat(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repo.createConversation(Personas.all.first())) }
    }

    fun rename(id: String, title: String) {
        if (title.isNotBlank()) viewModelScope.launch { repo.conv.rename(id, title.trim()) }
    }

    fun pin(id: String, pinned: Boolean) {
        viewModelScope.launch { repo.conv.pin(id, pinned) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.conv.delete(id) }
    }
}

@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onPersonas: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProviders: () -> Unit,
    onTool: (String) -> Unit,
    onCall: () -> Unit,
    onMemory: () -> Unit,
    onSkills: () -> Unit,
    onConnectors: () -> Unit,
) {
    val c = LocalAura.current
    val context = LocalContext.current
    val container = context.container
    val vm: HomeViewModel = viewModel(factory = simpleFactory { HomeViewModel(container) })
    val items by vm.conversations.collectAsStateWithLifecycle()
    val providerCount by vm.providerCount.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }

    BackHandler {
        (context as? Activity)?.moveTaskToBack(true)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.userName.isBlank()) "Hello" else "Hello, ${settings.userName}",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("What shall we explore today?", color = c.textDim)
                    }
                    GlassIconButton(Icons.Rounded.Search, onSearch, contentDescription = "Search")
                    Spacer(Modifier.width(10.dp))
                    GlassIconButton(Icons.Rounded.Settings, onSettings, contentDescription = "Settings")
                }
            }
            item {
                GlassCard(shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AuraOrb(size = 88.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Start a conversation", style = MaterialTheme.typography.titleLarge)
                            Text("Ask anything, or pick a specialist.", color = c.textDim)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton("New chat", { vm.newChat(onOpenChat) }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                        GlassButton("Personas", onPersonas, Modifier.weight(1f), icon = Icons.Rounded.Widgets)
                    }
                }
            }
            item { SectionLabel("Studio") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(Tools, key = { it.route }) { tool -> ToolCard(tool) { onTool(tool.route) } }
                }
            }
            if (providerCount == 0) {
                item {
                    GlassCard(onClick = onProviders, shape = RoundedCornerShape(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Dns, null, tint = c.accent2)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Connect an AI provider", style = MaterialTheme.typography.titleMedium)
                                Text("Add a base URL and API key to start chatting.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item { SectionLabel(if (items.isEmpty()) "No conversations yet" else "Recent") }
            items(items, key = { it.conversation.id }) { row ->
                ConversationRow(
                    row = row,
                    onClick = { onOpenChat(row.conversation.id) },
                    onPin = { vm.pin(row.conversation.id, !row.conversation.pinned) },
                    onRename = { renameTarget = row },
                    onDelete = { deleteTarget = row },
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 104.dp)
                .size(64.dp)
                .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = c.accent, spotColor = c.accent)
                .clip(RoundedCornerShape(22.dp))
                .background(c.gradient)
                .clickable { vm.newChat(onOpenChat) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, "New chat", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        HomeDock(
            Modifier.align(Alignment.BottomCenter),
            onChat = {},
            onCall = onCall,
            onMemory = onMemory,
            onSkills = onSkills,
            onConnectors = onConnectors,
        )
    }

    renameTarget?.let { row ->
        var text by remember(row.conversation.id) { mutableStateOf(row.conversation.title) }
        GlassDialog("Rename chat", onDismiss = { renameTarget = null }, confirmText = "Save", onConfirm = {
            vm.rename(row.conversation.id, text)
            renameTarget = null
        }) { GlassTextField(text, { text = it }, placeholder = "Chat title") }
    }
    deleteTarget?.let { row ->
        GlassDialog("Delete this chat?", onDismiss = { deleteTarget = null }, confirmText = "Delete", danger = true, onConfirm = {
            vm.delete(row.conversation.id)
            deleteTarget = null
        }) { Text("“${row.conversation.title}” and all its messages will be removed.", color = c.textDim) }
    }
}

@Composable
private fun ConversationRow(
    row: ConversationWithPreview,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAura.current
    val conv = row.conversation
    val persona = Personas.find(conv.personaId)
    var menu by remember { mutableStateOf(false) }
    GlassCard(onClick = onClick, shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.aura.chat.ui.components.IconBadge(persona?.icon ?: Icons.Rounded.AutoAwesome, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conv.pinned) {
                        Icon(Icons.Rounded.PushPin, null, tint = c.accent2, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(conv.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    row.preview?.replace('\n', ' ') ?: "No messages yet",
                    color = c.textDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(relativeTime(conv.updatedAt), color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                Box {
                    androidx.compose.material3.IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, "More", tint = c.textDim)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (conv.pinned) "Unpin" else "Pin to top") },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                            onClick = { menu = false; onPin() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = c.danger) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolInfo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .width(158.dp)
            .height(190.dp)
            .clip(shape)
            .background(Brush.linearGradient(tool.colors, start = Offset(0f, 0f), end = Offset(420f, 640f)))
            .clickable(onClick = onClick)
    ) {
        Icon(
            tool.icon, null, tint = Color.White.copy(alpha = 0.13f),
            modifier = Modifier.size(130.dp).align(Alignment.BottomEnd).offset(x = 28.dp, y = 28.dp),
        )
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = Color.White)
            }
            Column {
                Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(tool.subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3)
            }
        }
    }
}

private data class DockEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun HomeDock(
    modifier: Modifier = Modifier,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onMemory: () -> Unit,
    onSkills: () -> Unit,
    onConnectors: () -> Unit,
) {
    val c = LocalAura.current
    val entries = listOf(
        DockEntry("Chats", Icons.Rounded.AutoAwesome, true, onChat),
        DockEntry("Call", Icons.Rounded.Call, false, onCall),
        DockEntry("Memory", Icons.Rounded.Psychology, false, onMemory),
        DockEntry("Skills", Icons.Rounded.Extension, false, onSkills),
        DockEntry("Links", Icons.Rounded.Dns, false, onConnectors),
    )
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                20.dp, RoundedCornerShape(24.dp),
                ambientColor = c.accent.copy(alpha = 0.35f),
                spotColor = c.accent.copy(alpha = 0.30f),
            )
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .let { if (entry.selected) it.background(c.accent.copy(alpha = 0.16f)) else it }
                    .clickable(onClick = entry.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    entry.icon, entry.label,
                    tint = if (entry.selected) c.accent else c.textDim,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.label,
                    color = if (entry.selected) c.accent else c.textDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
