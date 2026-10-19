package com.aura.chat.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.MemoryEntity
import com.aura.chat.data.MemoryFile
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.components.glass
import com.aura.chat.ui.shareText
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.launch

private val MemoryCategories = listOf("profile", "preference", "project", "fact", "other")

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val memories by container.repo.memories.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MemoryEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var openFile by remember { mutableStateOf<MemoryFile?>(null) }
    var files by remember { mutableStateOf(container.memory.readFiles()) }

    fun refreshFiles() {
        files = container.memory.readFiles()
    }

    ScreenScaffold(
        "Memory",
        onBack,
        actions = {
            GlassIconButton(Icons.Rounded.Add, { creating = true }, contentDescription = "Add memory")
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.IosShare, {
                scope.launch {
                    val text = container.memory.exportMarkdown()
                    if (text.isBlank()) toast(ctx, "No memory to export yet") else shareText(ctx, text)
                }
            }, contentDescription = "Export memory")
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Memories", "Files").forEachIndexed { index, label ->
                val selected = tab == index
                Text(
                    label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) c.gradient else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.08f)))
                        .clickable { tab = index; if (index == 1) refreshFiles() }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    color = if (selected) Color.White else c.text,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (tab == 0) {
            GlassTextField(
                query, { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Search memory",
                leading = Icons.Rounded.Search,
            )
            val shown = memories.filter {
                query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true)
            }
            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Icon(Icons.Rounded.Description, null, tint = c.textDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (memories.isEmpty()) "No memories yet. Aura saves durable facts here automatically, and you can add your own."
                        else "Nothing matches “$query”.",
                        color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { memory ->
                    GlassCard(shape = RoundedCornerShape(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(memory.category, color = c.accent2, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                memory.source, color = c.textDim, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (memory.pinned) Icon(Icons.Rounded.PushPin, null, tint = c.accent2, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(memory.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(memory.content, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MemoryAction(Icons.Rounded.PushPin, if (memory.pinned) "Unpin" else "Pin") {
                                scope.launch { container.memory.setPinned(memory.id, !memory.pinned) }
                            }
                            MemoryAction(Icons.Rounded.Edit, "Edit") { editing = memory }
                            MemoryAction(Icons.Rounded.ContentCopy, "Copy") {
                                clipboard.setText(AnnotatedString(memory.content))
                                toast(ctx, "Copied")
                            }
                            MemoryAction(Icons.Rounded.Delete, "Delete", danger = true) { deleting = memory }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    GlassButton("Delete all memory", { confirmClear = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteSweep)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GlassCard(shape = RoundedCornerShape(22.dp)) {
                        Text("Memory files", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Every memory is also written to real Markdown files in the app's private storage. " +
                                "They survive restarts, disconnects and calls, exactly like a long-term memory store.",
                            color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            container.memory.readFiles().firstOrNull()?.path?.substringBeforeLast('/').orEmpty(),
                            color = c.accent2, style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                items(files, key = { it.name }) { file ->
                    GlassCard(onClick = { openFile = file }, shape = RoundedCornerShape(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FolderOpen, null, tint = c.accent2)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${file.content.lines().size} lines · ${file.size / 1024} KB",
                                    color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        MemoryEditor(null, onDismiss = { creating = false }) { title, content, category ->
            scope.launch {
                container.memory.save(title, content, category, "manual")
                refreshFiles()
            }
            creating = false
        }
    }
    editing?.let { memory ->
        MemoryEditor(memory, onDismiss = { editing = null }) { title, content, category ->
            scope.launch {
                container.memory.update(memory.copy(title = title, content = content, category = category))
                refreshFiles()
            }
            editing = null
        }
    }
    deleting?.let { memory ->
        GlassDialog("Delete this memory?", onDismiss = { deleting = null }, confirmText = "Delete", danger = true, onConfirm = {
            scope.launch { container.memory.delete(memory.id); refreshFiles() }
            deleting = null
        }) { Text(memory.title, color = c.textDim) }
    }
    if (confirmClear) {
        GlassDialog("Delete all memory?", onDismiss = { confirmClear = false }, confirmText = "Delete all", danger = true, onConfirm = {
            scope.launch { container.memory.clear(); refreshFiles() }
            confirmClear = false
        }) { Text("Every saved memory and every memory file will be removed.", color = c.textDim) }
    }
    openFile?.let { file ->
        GlassDialog(
            file.name,
            onDismiss = { openFile = null },
            confirmText = "Copy",
            onConfirm = {
                clipboard.setText(AnnotatedString(file.content))
                toast(ctx, "File copied")
            },
        ) {
            Text(
                file.content,
                color = c.textDim,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun MemoryEditor(
    existing: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val c = LocalAura.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember(existing?.id) { mutableStateOf(existing?.content.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "fact") }
    GlassDialog(
        if (existing == null) "New memory" else "Edit memory",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = { if (content.isNotBlank()) onSave(title, content, category) },
    ) {
        GlassTextField(title, { title = it }, label = "Title", placeholder = "Short label")
        Spacer(Modifier.height(12.dp))
        GlassTextField(content, { content = it }, label = "Memory", placeholder = "What should Aura remember?", singleLine = false, minLines = 3, maxLines = 7)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Category")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryCategories.forEach { item ->
                val selected = category == item
                Text(
                    item,
                    color = if (selected) Color.White else c.text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) c.gradient else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.08f)))
                        .clickable { category = item }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun MemoryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalAura.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (danger) c.danger else c.textDim, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (danger) c.danger else c.textDim, style = MaterialTheme.typography.labelMedium)
    }
}
