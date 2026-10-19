package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.ConnectorEntity
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.IconBadge
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.launch

private val ConnectorTypes = listOf(
    Triple("mcp", "MCP URL server", "Model Context Protocol over HTTP. Aura discovers and calls its tools."),
    Triple("api", "API", "Any REST API. Aura can send requests through this connector."),
    Triple("app", "Application", "A deep link or package that Aura can open for you."),
)

@Composable
fun ConnectorsScreen(onBack: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val connectors by container.repo.connectors.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var filter by remember { mutableStateOf("all") }
    var editing by remember { mutableStateOf<ConnectorEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ConnectorEntity?>(null) }
    var testingId by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var tools by remember { mutableStateOf<Triple<ConnectorEntity, List<String>, String?>?>(null) }

    val shown = connectors.filter { filter == "all" || it.type == filter }

    ScreenScaffold(
        "Connectors",
        onBack,
        actions = { GlassIconButton(Icons.Rounded.Add, { creating = true }, contentDescription = "Add connector") },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("all", "mcp", "api", "app").forEach { type ->
                val selected = filter == type
                Text(
                    type.uppercase(),
                    modifier = Modifier
                        .background(
                            if (selected) c.gradient
                            else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.08f)),
                            RoundedCornerShape(50),
                        )
                        .clickable { filter = type }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    color = if (selected) Color.White else c.text,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GlassCard(shape = RoundedCornerShape(22.dp)) {
                    Text("Custom connectors", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nothing is connected by default. Add your own MCP server, REST API or app link here, " +
                            "or simply tell Aura in chat: “add connector <name> <url>”.",
                        color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(Icons.Rounded.Extension, size = 72.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("No connectors yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap + to add one.", color = c.textDim)
                    }
                }
            }
            items(shown, key = { it.id }) { connector ->
                GlassCard(shape = RoundedCornerShape(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            when (connector.type) {
                                "mcp" -> Icons.Rounded.Dns
                                "api" -> Icons.Rounded.Api
                                else -> Icons.Rounded.Apps
                            },
                            size = 42.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(connector.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                ConnectorTypes.firstOrNull { it.first == connector.type }?.second ?: connector.type,
                                color = c.accent2, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Switch(
                            connector.enabled,
                            { enabled -> scope.launch { container.repo.connectors.update(connector.copy(enabled = enabled)) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.accent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                                uncheckedTrackColor = c.textDim.copy(alpha = 0.35f),
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        connector.url, color = c.textDim, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace,
                    )
                    if (connector.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(connector.description, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Health: ${connector.lastStatus}",
                        color = if (connector.lastStatus.startsWith("Connected") || connector.lastStatus.startsWith("API responded")) c.accent2 else c.textDim,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    results[connector.id]?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = if (it.startsWith("Connected") || it.startsWith("API responded")) c.accent2 else c.danger, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            if (testingId == connector.id) "Testing…" else "Test",
                            {
                                testingId = connector.id
                                scope.launch {
                                    val result = container.connectors.test(connector)
                                    results = results + (connector.id to result.message)
                                    testingId = null
                                    if (result.tools.isNotEmpty()) tools = Triple(connector, result.tools, null)
                                }
                            },
                            Modifier.weight(1f),
                            icon = Icons.Rounded.Refresh,
                            enabled = testingId == null,
                        )
                        if (connector.type == "app") {
                            GlassButton("Open", {
                                scope.launch {
                                    if (!container.connectors.openAppConnector(connector)) toast(ctx, "No app handles this link")
                                }
                            }, Modifier.weight(1f), icon = Icons.Rounded.OpenInNew)
                        }
                        GlassIconButton(Icons.Rounded.Delete, { deleting = connector }, contentDescription = "Delete")
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val existing = editing
        ConnectorEditor(existing, onDismiss = { creating = false; editing = null }, onSave = { entity ->
            scope.launch {
                if (existing == null) container.repo.connectors.insert(entity)
                else container.repo.connectors.update(entity.copy(id = existing.id))
            }
            creating = false
            editing = null
        })
    }

    deleting?.let { connector ->
        GlassDialog("Delete connector?", onDismiss = { deleting = null }, confirmText = "Delete", danger = true, onConfirm = {
            scope.launch { container.repo.connectors.delete(connector.id) }
            deleting = null
        }) { Text("${connector.name} will be removed, including its saved key.", color = c.textDim) }
    }

    tools?.let { (connector, list, error) ->
        GlassDialog(
            "${connector.name} tools",
            onDismiss = { tools = null },
            confirmText = "Close",
            onConfirm = { tools = null },
        ) {
            if (error != null) Text(error, color = c.danger)
            Text("${list.size} tools discovered:", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                list.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun ConnectorEditor(
    existing: ConnectorEntity?,
    onDismiss: () -> Unit,
    onSave: (ConnectorEntity) -> Unit,
) {
    val c = LocalAura.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: "mcp") }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var headers by remember { mutableStateOf(existing?.headers ?: "{}") }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var permissions by remember { mutableStateOf(existing?.permissions ?: "tools.list,tools.call") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassDialog(
        if (existing == null) "Add connector" else "Edit connector",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            when {
                name.isBlank() -> error = "Give the connector a name."
                !(url.startsWith("http://") || url.startsWith("https://") || type == "app") ->
                    error = "Enter a full URL starting with http:// or https://"
                else -> onSave(
                    ConnectorEntity(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        type = type,
                        url = url.trim().trimEnd('/'),
                        apiKey = apiKey.trim(),
                        headers = headers.ifBlank { "{}" },
                        description = description.trim(),
                        permissions = permissions.trim().ifBlank { "tools.list,tools.call" },
                        enabled = existing?.enabled ?: true,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    )
                )
            }
        },
    ) {
        Text("Type", color = c.textDim, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectorTypes.forEach { (value, label, hint) ->
                val selected = type == value
                Row(
                    Modifier.fillMaxWidth().background(
                        if (selected) c.accent.copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    ).clickable { type = value }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(hint, color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selected) Icon(Icons.Rounded.Dns, null, tint = c.accent2)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassTextField(name, { name = it }, label = "Name", placeholder = "e.g. Files MCP")
        Spacer(Modifier.height(10.dp))
        GlassTextField(
            url, { url = it },
            label = if (type == "app") "Deep link or package" else "URL",
            placeholder = if (type == "app") "myapp://open" else "https://server.example.com/mcp",
            leading = Icons.Rounded.Link,
        )
        if (type != "app") {
            Spacer(Modifier.height(10.dp))
            GlassTextField(apiKey, { apiKey = it }, label = "API key / token (optional)", placeholder = "Bearer token")
        }
        Spacer(Modifier.height(10.dp))
        GlassTextField(
            headers, { headers = it },
            label = "Extra headers (JSON)", placeholder = "{\"X-Api-Key\":\"…\"}",
            singleLine = false, minLines = 2, maxLines = 4,
        )
        Spacer(Modifier.height(10.dp))
        GlassTextField(description, { description = it }, label = "Notes", placeholder = "What does this connector do?")
        if (type == "mcp") {
            Spacer(Modifier.height(10.dp))
            GlassTextField(
                permissions, { permissions = it },
                label = "Allowed permissions (comma-separated)",
                placeholder = "tools.list,tools.call",
            )
            Text("Remove tools.call to allow discovery without remote execution.", color = c.textDim, style = MaterialTheme.typography.labelSmall)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
