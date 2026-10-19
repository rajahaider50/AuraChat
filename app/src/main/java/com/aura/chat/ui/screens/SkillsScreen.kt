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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
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
import com.aura.chat.data.SkillEntity
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

@Composable
fun SkillsScreen(onBack: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    val skills by container.repo.skills.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = com.aura.chat.data.AppSettings())

    var installing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<SkillEntity?>(null) }
    var deleting by remember { mutableStateOf<SkillEntity?>(null) }
    var token by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }

    ScreenScaffold(
        "Skills",
        onBack,
        actions = { GlassIconButton(Icons.Rounded.Add, { installing = true }, contentDescription = "Install skill") },
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GlassCard(shape = RoundedCornerShape(22.dp)) {
                    Text("Claude-style skills", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Paste a GitHub repository link (or any raw Markdown URL) and Aura installs the skill. " +
                            "Enabled skills are added to the model's instructions for every chat and call.",
                        color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Install from link", { installing = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Link)
                }
            }
            item {
                SectionLabel("GitHub token (optional)")
                GlassTextField(
                    token, { token = it; scope.launch { container.settings.setGithubToken(it) } },
                    placeholder = "ghp_…  raises GitHub API limits",
                    leading = Icons.Rounded.Link,
                )
            }
            item { SectionLabel(if (skills.isEmpty()) "No skills installed" else "Installed skills") }
            items(skills, key = { it.id }) { skill ->
                GlassCard(shape = RoundedCornerShape(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Extension, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                skill.description, color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            skill.enabled,
                            { enabled -> scope.launch { container.skills.setEnabled(skill.id, enabled) } },
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
                        skill.sourceUrl, color = c.accent2, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("View", { viewing = skill }, Modifier.weight(1f))
                        GlassButton("Remove", { deleting = skill }, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (installing) {
        var link by remember { mutableStateOf(url) }
        var busy by remember { mutableStateOf(false) }
        GlassDialog(
            "Install a skill",
            onDismiss = { if (!busy) installing = false },
            confirmText = if (busy) "Installing…" else "Install",
            onConfirm = {
                if (busy) return@GlassDialog
                if (link.isBlank()) {
                    error = "Paste a GitHub link or a raw Markdown URL."
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val skill = container.skills.install(link)
                            url = link
                            toast(ctx, "Installed ${skill.name}")
                            installing = false
                        } catch (e: Exception) {
                            error = e.message ?: "Could not install that skill."
                        }
                        busy = false
                    }
                }
            },
        ) {
            GlassTextField(link, { link = it }, label = "Skill link", placeholder = "https://github.com/user/repo", leading = Icons.Rounded.Link)
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Aura looks for SKILL.md, then skills/*.md, then README.md in the repository.",
                color = c.textDim, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    viewing?.let { skill ->
        GlassDialog(skill.name, onDismiss = { viewing = null }, confirmText = "Enable", onConfirm = {
            scope.launch { container.skills.setEnabled(skill.id, true) }
            viewing = null
        }) {
            Text(
                skill.instructions.take(12000),
                color = c.textDim,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            )
        }
    }

    deleting?.let { skill ->
        GlassDialog("Remove this skill?", onDismiss = { deleting = null }, confirmText = "Remove", danger = true, onConfirm = {
            scope.launch { container.skills.delete(skill.id) }
            deleting = null
        }) { Text(skill.name, color = c.textDim) }
    }
}
