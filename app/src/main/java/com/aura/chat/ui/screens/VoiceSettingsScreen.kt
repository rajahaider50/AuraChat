package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.AppSettings
import com.aura.chat.data.GeminiVoice
import com.aura.chat.data.GeminiVoices
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.components.SectionLabel
import com.aura.chat.ui.toast
import kotlinx.coroutines.launch

private val RouteLabels = listOf("Auto" to 0, "Speaker" to 1, "Earpiece" to 2, "Bluetooth" to 3)

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val c = com.aura.chat.ui.theme.LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())

    var key by remember(settings.geminiKey) { mutableStateOf(settings.geminiKey) }
    var model by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    var showKey by remember { mutableStateOf(false) }
    var pickVoice by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Voice & calls", onBack) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionLabel("Official Gemini voice")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Text(
                    "Aura speaks with Google's official Gemini TTS voices. The phone's system text-to-speech " +
                        "engine is no longer used anywhere in the app.",
                    color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                GlassTextField(
                    key, { key = it; scope.launch { container.settings.setGeminiKey(it) } },
                    label = "Gemini API key",
                    placeholder = "AIza…",
                    leading = Icons.Rounded.Key,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        Text(
                            if (showKey) "Hide" else "Show",
                            color = c.accent2,
                            modifier = Modifier.clickable { showKey = !showKey }.padding(6.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.RecordVoiceOver, null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Voice", style = MaterialTheme.typography.titleMedium)
                        Text(settings.geminiVoice, color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Change", color = c.accent2, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { pickVoice = true }.padding(6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                GlassTextField(
                    model, { model = it; scope.launch { container.settings.setGeminiModel(it) } },
                    label = "TTS model", placeholder = GeminiVoice.DEFAULT_MODEL,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        if (speaking) "Stop" else "Hear a sample",
                        {
                            if (speaking) {
                                container.voice.stop()
                                speaking = false
                            } else {
                                scope.launch {
                                    speaking = true
                                    status = null
                                    try {
                                        container.voice.speak(
                                            "Hello, I am Aura. This is the official Gemini voice speaking.",
                                            key.ifBlank { container.repo.geminiKey() },
                                            settings.geminiVoice,
                                            model,
                                        )
                                    } catch (e: Exception) {
                                        status = e.message ?: "Could not play the sample."
                                    }
                                    speaking = false
                                }
                            }
                        },
                        Modifier.weight(1f),
                        icon = Icons.Rounded.VolumeUp,
                        enabled = key.isNotBlank(),
                    )
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionLabel("Default call audio")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Text(
                    "Where a call plays by default. You can change this during any call. Auto uses the proximity " +
                        "sensor to switch between earpiece and speaker.",
                    color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                RouteLabels.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { (label, value) ->
                            val selected = settings.callRoute == value
                            Text(
                                label,
                                color = if (selected) Color.White else c.text,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) c.gradient else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                                    .clickable { scope.launch { container.settings.setCallRoute(value) } }
                                    .padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }

            SectionLabel("Long-term memory")
            GlassCard(shape = RoundedCornerShape(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Memory", style = MaterialTheme.typography.titleMedium)
                        Text("Use saved memories in chat and calls", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        settings.memoryEnabled,
                        { scope.launch { container.settings.setMemoryEnabled(it) } },
                        colors = switchColors(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = c.accent2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Save memories automatically", style = MaterialTheme.typography.titleMedium)
                        Text("Extract durable facts after every chat and call", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        settings.autoMemory,
                        { scope.launch { container.settings.setAutoMemory(it) } },
                        colors = switchColors(),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickVoice) {
        GlassDialog("Choose a Gemini voice", onDismiss = { pickVoice = false }) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                GeminiVoices.all.chunked(3).forEach { group ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { voice ->
                            val selected = settings.geminiVoice == voice
                            Text(
                                voice,
                                color = if (selected) Color.White else c.text,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) c.gradient else androidx.compose.ui.graphics.SolidColor(c.text.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                    .clickable {
                                        scope.launch { container.settings.setGeminiVoice(voice) }
                                        pickVoice = false
                                    }
                                    .padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = com.aura.chat.ui.theme.LocalAura.current.accent,
    uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
    uncheckedTrackColor = com.aura.chat.ui.theme.LocalAura.current.textDim.copy(alpha = 0.35f),
)
