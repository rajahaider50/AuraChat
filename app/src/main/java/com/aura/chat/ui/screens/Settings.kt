package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.AppSettings
import com.aura.chat.ui.components.AuraOrb
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.components.SettingRow
import com.aura.chat.ui.components.glass
import com.aura.chat.ui.components.rememberHaptic
import com.aura.chat.ui.shareText
import com.aura.chat.ui.theme.Accents
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.launch

@Composable
private fun rememberSettings(): AppSettings {
    val container = LocalContext.current.container
    val s by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    return s
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = LocalAura.current.accent,
    uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
    uncheckedTrackColor = LocalAura.current.textDim.copy(alpha = 0.35f),
    uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = LocalAura.current.accent,
    activeTrackColor = LocalAura.current.accent,
    inactiveTrackColor = LocalAura.current.textDim.copy(alpha = 0.25f),
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProviders: () -> Unit,
    onModels: () -> Unit,
    onAppearance: () -> Unit,
    onPrompts: () -> Unit,
    onAbout: () -> Unit,
    onVoice: () -> Unit,
    onMemory: () -> Unit,
    onSkills: () -> Unit,
    onConnectors: () -> Unit,
) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    val providers by container.repo.providers.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val active = providers.firstOrNull { it.id == s.activeProvider } ?: providers.firstOrNull()

    var temp by remember(s.temperature) { mutableFloatStateOf(s.temperature) }
    var context by remember(s.contextSize) { mutableFloatStateOf(s.contextSize.toFloat()) }
    var name by remember(s.userName) { mutableStateOf(s.userName) }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold("Settings", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            SectionLabel("Profile")
            GlassTextField(
                name,
                { name = it; scope.launch { container.settings.setUserName(it.trim()) } },
                placeholder = "Your name",
                leading = Icons.Rounded.Person,
            )

            SectionLabel("AI connection")
            GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(
                    Icons.Rounded.Dns, "Providers",
                    if (active == null) "No provider yet. Tap to add one" else "${active.name} is active",
                    onProviders,
                )
                SettingRow(
                    Icons.Rounded.Memory, "Model",
                    if (active == null || active.model.isBlank()) "Choose the model used for replies" else active.model,
                    onModels,
                )
            }

            SectionLabel("Chat behavior")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Text("Creativity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("%.1f".format(temp), color = c.textDim)
                }
                Slider(
                    value = temp, onValueChange = { temp = it },
                    onValueChangeFinished = { scope.launch { container.settings.setTemperature((temp * 10).toInt() / 10f) } },
                    valueRange = 0f..1.5f, colors = sliderColors(),
                )
                Text("Lower is focused and precise. Higher is more imaginative.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Text("Memory length", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${context.toInt()} messages", color = c.textDim)
                }
                Slider(
                    value = context, onValueChange = { context = it },
                    onValueChangeFinished = { scope.launch { container.settings.setContextSize(context.toInt()) } },
                    valueRange = 2f..60f, colors = sliderColors(),
                )
                Text("How many recent messages are sent with each request.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
            }

            SectionLabel("Aura intelligence")
            GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(Icons.Rounded.RecordVoiceOver, "Voice & calls", "Gemini voice, audio routing and call controls", onVoice)
                SettingRow(Icons.Rounded.Psychology, "Memory", "See and edit everything Aura remembers", onMemory)
                SettingRow(Icons.Rounded.Extension, "Skills", "Install Claude-style skills from GitHub", onSkills)
                SettingRow(Icons.Rounded.Hub, "Connectors", "MCP servers, APIs and applications", onConnectors)
                SettingRow(
                    Icons.Rounded.Memory, "Long-term memory", "Keep facts and preferences across chats and calls",
                    trailing = {
                        Switch(s.memoryEnabled, { scope.launch { container.settings.setMemoryEnabled(it) } }, colors = switchColors())
                    },
                )
                SettingRow(
                    Icons.Rounded.AutoAwesome, "Learn automatically", "Save useful details while you talk",
                    trailing = {
                        Switch(s.autoMemory, { scope.launch { container.settings.setAutoMemory(it) } }, colors = switchColors())
                    },
                )
            }

            SectionLabel("Personalize")
            GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(Icons.Rounded.Palette, "Appearance", "Theme, accent colour and glass intensity", onAppearance)
                SettingRow(Icons.Rounded.Bolt, "Prompt library", "Reusable prompts for any chat", onPrompts)
                SettingRow(
                    Icons.Rounded.Vibration, "Haptic feedback", "Subtle vibration on actions",
                    trailing = {
                        Switch(s.haptics, { scope.launch { container.settings.setHaptics(it) } }, colors = switchColors())
                    },
                )
            }

            SectionLabel("Your data")
            GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(Icons.Rounded.IosShare, "Export all chats", "Share every conversation as Markdown", {
                    scope.launch {
                        val text = container.repo.exportAll()
                        if (text.isBlank()) toast(ctx, "Nothing to export yet") else shareText(ctx, text)
                    }
                })
                SettingRow(Icons.Rounded.DeleteForever, "Delete all chats", "Permanently remove every conversation", { confirmClear = true })
            }

            SectionLabel("About")
            GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(Icons.Rounded.Info, "About Aura Chat", "Version and credits", onAbout)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        GlassDialog("Delete all chats?", onDismiss = { confirmClear = false }, confirmText = "Delete all", danger = true, onConfirm = {
            scope.launch { container.repo.conv.deleteAll() }
            confirmClear = false
            toast(ctx, "All chats deleted")
        }) { Text("Every conversation and message on this device will be removed. Providers and prompts are kept.", color = c.textDim) }
    }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val c = LocalAura.current
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    var glass by remember(s.glass) { mutableFloatStateOf(s.glass) }
    val haptic = rememberHaptic(s.haptics)

    ScreenScaffold("Appearance", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            SectionLabel("Theme")
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(5.dp)) {
                listOf("System", "Dark", "Light").forEachIndexed { i, label ->
                    val selected = s.theme == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .let { if (selected) it.background(c.gradient) else it }
                            .clickable { haptic(); scope.launch { container.settings.setTheme(i) } }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (selected) Color.White else c.text, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            SectionLabel("Accent colour")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accents.forEachIndexed { i, a ->
                        val selected = s.accent == i
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(a.a, a.b)))
                                .let { if (selected) it.border(3.dp, c.text, CircleShape) else it }
                                .clickable { haptic(); scope.launch { container.settings.setAccent(i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(Accents[s.accent.coerceIn(0, Accents.lastIndex)].name, color = c.textDim)
            }

            SectionLabel("Glass intensity")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Slider(
                    value = glass, onValueChange = { glass = it },
                    onValueChangeFinished = { scope.launch { container.settings.setGlass(glass) } },
                    colors = sliderColors(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtle", color = c.textDim, style = MaterialTheme.typography.labelMedium)
                    Text("Frosted", color = c.textDim, style = MaterialTheme.typography.labelMedium)
                }
            }

            SectionLabel("Preview")
            GlassCard(shape = RoundedCornerShape(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuraOrb(size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Aura", style = MaterialTheme.typography.titleLarge)
                        Text("Changes apply instantly.", color = c.textDim)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val version = remember {
        try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    ScreenScaffold("About", onBack) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AuraOrb(size = 150.dp)
            Spacer(Modifier.height(16.dp))
            Text("Aura Chat", style = MaterialTheme.typography.headlineMedium)
            Text("Version $version", color = c.textDim)
            Spacer(Modifier.height(24.dp))
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "Streaming replies from any OpenAI-compatible API",
                    "Local chat history with search, pinning and export",
                    "Seven built-in personas and a reusable prompt library",
                    "Voice, Compare Lab, Insights, Translate and Writing Studio tools",
                    "Markdown and code blocks with one-tap copy",
                    "Chats and API keys never leave your device except to your chosen provider",
                ).forEach {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent2, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Built with Kotlin, Jetpack Compose, Room and OkHttp.", color = c.textDim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
        }
    }
}
