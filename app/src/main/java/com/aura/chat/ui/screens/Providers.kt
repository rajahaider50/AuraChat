package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.AppSettings
import com.aura.chat.data.ProviderEntity
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.GradientButton
import com.aura.chat.ui.components.IconBadge
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.components.glass
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Preset(val name: String, val baseUrl: String)

private val Presets = listOf(
    Preset("OpenAI", "https://api.openai.com/v1"),
    Preset("OpenRouter", "https://openrouter.ai/api/v1"),
    Preset("Groq", "https://api.groq.com/openai/v1"),
    Preset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    Preset("DeepSeek", "https://api.deepseek.com/v1"),
    Preset("Together", "https://api.together.xyz/v1"),
    Preset("Ollama (LAN)", "http://192.168.1.10:11434/v1"),
)

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').ifBlank { url }

// ---------- Providers list ----------

@Composable
fun ProvidersScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val c = LocalAura.current
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val providers by container.repo.providers.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val activeId = (providers.firstOrNull { it.id == settings.activeProvider } ?: providers.firstOrNull())?.id

    ScreenScaffold(
        "Providers",
        onBack,
        actions = { GlassIconButton(Icons.Rounded.Add, { onEdit("new") }, contentDescription = "Add provider") },
    ) {
        if (providers.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                IconBadge(Icons.Rounded.Dns, size = 84.dp)
                Spacer(Modifier.height(20.dp))
                Text("Connect your first provider", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Aura works with any OpenAI-compatible API. Add a base URL and API key to begin.",
                    color = c.textDim,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                GradientButton("Add provider", { onEdit("new") }, icon = Icons.Rounded.Add)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(providers, key = { it.id }) { p ->
                    val isActive = p.id == activeId
                    GlassCard(onClick = { onEdit(p.id.toString()) }, shape = RoundedCornerShape(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Dns, size = 46.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(hostOf(p.baseUrl), color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isActive) {
                                Text("Active", color = c.accent2, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (p.model.isBlank()) "No model selected" else p.model,
                            color = c.textDim,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!isActive) {
                            Spacer(Modifier.height(12.dp))
                            GlassButton(
                                "Use this provider",
                                { scope.launch { container.settings.setActiveProvider(p.id) } },
                                Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.Check,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Add / edit provider ----------

@Composable
fun ProviderEditScreen(idArg: String, onBack: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val repo = container.repo
    val scope = rememberCoroutineScope()
    val isNew = idArg == "new"

    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var existing by remember { mutableStateOf<ProviderEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(idArg) {
        if (!isNew) {
            repo.providers.get(idArg.toLongOrNull() ?: -1L)?.let {
                existing = it
                name = it.name
                baseUrl = it.baseUrl
                apiKey = it.apiKey
                model = it.model
            }
        }
    }

    fun draft() = ProviderEntity(
        id = existing?.id ?: 0L,
        name = name.trim(),
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiKey = apiKey.trim(),
        model = model.trim(),
    )

    ScreenScaffold(if (isNew) "Add provider" else "Edit provider", onBack, modifier = Modifier.imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("Quick presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Presets) { p ->
                    Text(
                        p.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .glass(RoundedCornerShape(50))
                            .clickable {
                                baseUrl = p.baseUrl
                                if (name.isBlank()) name = p.name.substringBefore(" (")
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            GlassTextField(name, { name = it }, label = "Name", placeholder = "e.g. OpenRouter", leading = Icons.Rounded.Edit)
            Spacer(Modifier.height(14.dp))
            GlassTextField(
                baseUrl, { baseUrl = it }, label = "Base URL", placeholder = "https://api.example.com/v1",
                leading = Icons.Rounded.Link,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(14.dp))
            GlassTextField(
                apiKey, { apiKey = it }, label = "API key", placeholder = "sk-…  (leave empty for local servers)",
                leading = Icons.Rounded.Key,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailing = {
                    IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(24.dp)) {
                        Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle key visibility", tint = c.textDim)
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            GlassTextField(model, { model = it }, label = "Default model (optional)", placeholder = "e.g. gpt-4o-mini", leading = Icons.Rounded.Memory)

            Spacer(Modifier.height(20.dp))
            GlassButton(
                if (testing) "Testing…" else "Test connection",
                {
                    scope.launch {
                        testing = true
                        testResult = try {
                            val models = repo.api.listModels(draft())
                            true to "Connected. ${models.size} models available."
                        } catch (e: Exception) {
                            false to (e.message ?: "Could not connect.")
                        }
                        testing = false
                    }
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Refresh,
                enabled = !testing && baseUrl.isNotBlank(),
            )
            testResult?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                Text(msg, color = if (ok) c.accent2 else c.danger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!isNew) {
                GlassButton("Delete", { confirmDelete = true }, Modifier.weight(1f))
            }
            GradientButton(
                "Save",
                {
                    val d = draft()
                    when {
                        d.name.isBlank() -> toast(ctx, "Give the provider a name")
                        !(d.baseUrl.startsWith("http://") || d.baseUrl.startsWith("https://")) ->
                            toast(ctx, "Base URL must start with http:// or https://")
                        else -> scope.launch {
                            if (isNew) {
                                val newId = repo.providers.insert(d)
                                // Make the new provider active when no valid active provider exists yet.
                                val activeId = container.settings.flow.first().activeProvider
                                val hasActive = repo.providers.all().any { it.id == activeId && it.id != newId }
                                if (!hasActive) container.settings.setActiveProvider(newId)
                            } else {
                                repo.providers.update(d)
                            }
                            onBack()
                        }
                    }
                },
                Modifier.weight(if (isNew) 1f else 1.4f),
            )
        }
    }

    if (confirmDelete) {
        GlassDialog("Delete provider?", onDismiss = { confirmDelete = false }, confirmText = "Delete", danger = true, onConfirm = {
            confirmDelete = false
            scope.launch {
                existing?.let { repo.providers.delete(it.id) }
                onBack()
            }
        }) { Text("The saved API key for “${existing?.name.orEmpty()}” will be removed from this device.", color = c.textDim) }
    }
}

// ---------- Model picker ----------

@Composable
fun ModelPickerScreen(onBack: () -> Unit, onProviders: () -> Unit) {
    val c = LocalAura.current
    val container = LocalContext.current.container
    val repo = container.repo
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf<ProviderEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }

    suspend fun load() {
        val p = provider ?: return
        loading = true
        error = null
        try {
            models = repo.api.listModels(p)
        } catch (e: Exception) {
            error = e.message ?: "Could not load models."
        }
        loading = false
    }

    suspend fun select(m: String) {
        val p = provider ?: return
        val updated = p.copy(model = m)
        repo.providers.update(updated)
        container.settings.setActiveProvider(updated.id)
        provider = updated
    }

    LaunchedEffect(Unit) {
        provider = repo.activeProvider()
        loaded = true
        load()
    }

    ScreenScaffold(
        "Choose model",
        onBack,
        actions = {
            if (provider != null) GlassIconButton(Icons.Rounded.Refresh, { scope.launch { load() } }, contentDescription = "Reload models")
        },
    ) {
        if (loaded && provider == null) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No provider connected yet.", color = c.textDim)
                Spacer(Modifier.height(16.dp))
                GradientButton("Add provider", onProviders)
            }
            return@ScreenScaffold
        }
        val p = provider
        if (p != null) {
            Text(
                "Provider: ${p.name}",
                color = c.textDim,
                modifier = Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            GlassTextField(
                query, { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Filter models",
                leading = Icons.Rounded.Search,
            )
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
                error != null -> GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Text(error.orEmpty(), color = c.danger, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Some providers do not list models. Enter a model id below instead.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val shown = models.filter { it.contains(query.trim(), ignoreCase = true) }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it }) { m ->
                    val selected = m == p.model
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(18.dp))
                            .let { if (selected) it.border(1.5.dp, c.accent, RoundedCornerShape(18.dp)) else it }
                            .clickable { scope.launch { select(m) } }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected) Icon(Icons.Rounded.Check, null, tint = c.accent2)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(custom, { custom = it }, modifier = Modifier.weight(1f), placeholder = "Or type a model id")
                Spacer(Modifier.width(10.dp))
                GradientButton(
                    "Use",
                    { scope.launch { select(custom.trim()); custom = "" } },
                    enabled = custom.isNotBlank(),
                )
            }
        }
    }
}
