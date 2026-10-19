package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.SystemBarIcons
import com.aura.chat.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SPaper = Color(0xFFF3F7FB)
private val SSheet = Color(0xFFFFFFFF)
private val SInk = Color(0xFF0B1B3A)
private val SDim = Color(0xFF6B7A99)
private val SBlue = Color(0xFF2F5BFF)
private val SRule = Color(0xFFD9E2F0)

private data class RewriteMode(val label: String, val icon: ImageVector, val instruction: String)

private val Modes = listOf(
    RewriteMode("Fix", Icons.Rounded.Spellcheck, "Correct grammar, spelling and punctuation. Keep the wording and voice exactly as they are otherwise."),
    RewriteMode("Polish", Icons.Rounded.AutoFixHigh, "Improve clarity and flow while keeping the meaning and the author's voice."),
    RewriteMode("Professional", Icons.Rounded.BusinessCenter, "Rewrite in a confident, professional tone suitable for work communication."),
    RewriteMode("Friendly", Icons.Rounded.Favorite, "Rewrite in a warm, friendly, conversational tone."),
    RewriteMode("Shorter", Icons.Rounded.UnfoldLess, "Make it about half as long while keeping every key point."),
    RewriteMode("Longer", Icons.Rounded.UnfoldMore, "Expand it with relevant detail and smoother transitions, to roughly twice the length."),
    RewriteMode("Simpler", Icons.Rounded.School, "Rewrite in plain, simple language that a twelve-year-old would understand."),
    RewriteMode("Persuasive", Icons.Rounded.Campaign, "Rewrite to be more persuasive and compelling without exaggerating or inventing facts."),
)

private fun words(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

class StudioViewModel(private val c: AppContainer) : ViewModel() {
    var draft by mutableStateOf("")
    var result by mutableStateOf("")
        private set
    var mode by mutableStateOf(1)
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun rewrite() {
        val text = draft.trim()
        if (text.isEmpty() || running) return
        job = viewModelScope.launch {
            result = ""
            error = null
            running = true
            try {
                val (provider, model) = c.repo.resolveModel()
                val system = "You are an expert editor. ${Modes[mode].instruction} " +
                    "Reply in the same language as the input. Return only the rewritten text, with no preface, labels or commentary."
                c.repo.oneShot(provider, model, system, text, 0.5f).collect { result += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Rewrite failed."
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun useResult() {
        if (result.isNotBlank()) {
            draft = result
            result = ""
        }
    }
}

@Composable
fun StudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm: StudioViewModel = viewModel(factory = simpleFactory { StudioViewModel(ctx.container) })
    val clipboard = LocalClipboardManager.current
    SystemBarIcons(lightBackground = true)

    Box(Modifier.fillMaxSize().background(SPaper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SSheet).border(1.dp, SRule, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = SInk) }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(
                    "Writing Studio", color = SInk, fontSize = 36.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                )
                Text(
                    "Paste a draft, choose the voice, and watch it come back better.",
                    color = SDim, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Modes.forEachIndexed { i, m ->
                        val sel = vm.mode == i
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (sel) SInk else SSheet)
                                .border(1.dp, if (sel) SInk else SRule, RoundedCornerShape(50))
                                .clickable { vm.mode = i }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(m.icon, null, tint = if (sel) Color.White else SBlue, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(m.label, color = if (sel) Color.White else SInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                // Draft sheet
                Paper(title = "Draft", right = {
                    Text("${words(vm.draft).let { if (vm.draft.isBlank()) 0 else it }} words", color = SDim, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    if (vm.draft.isEmpty()) {
                        Tap(Icons.Rounded.ContentPaste, "Paste") { clipboard.getText()?.text?.let { vm.draft = it } }
                    } else {
                        Tap(Icons.Rounded.Close, "Clear") { vm.draft = "" }
                    }
                }) {
                    ToolTextField(
                        vm.draft, { vm.draft = it },
                        placeholder = "Start with a rough draft…",
                        textColor = SInk, hintColor = SDim.copy(alpha = 0.6f), accent = SBlue,
                        minLines = 5, maxLines = 12, serif = true, textSize = 18.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(if (vm.running) SDim else SBlue)
                        .clickable(enabled = vm.running || vm.draft.isNotBlank()) { if (vm.running) vm.stop() else vm.rewrite() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(if (vm.running) Icons.Rounded.Stop else Icons.Rounded.AutoFixHigh, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (vm.running) "Stop" else "Rewrite as ${Modes[vm.mode].label}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))

                // Result sheet with an ink rule on the left
                val shape = RoundedCornerShape(20.dp)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(SSheet).border(1.dp, SRule, shape)) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(SBlue))
                    Column(Modifier.padding(18.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Result", color = SBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (vm.result.isNotBlank() && !vm.running) {
                                val before = words(vm.draft)
                                val after = words(vm.result)
                                if (before > 0) {
                                    val delta = (after - before) * 100 / before
                                    Text("$before → $after words (${if (delta >= 0) "+" else ""}$delta%)", color = SDim, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val err = vm.error
                        when {
                            err != null -> Text(err, color = Color(0xFFD92D45), fontSize = 15.sp)
                            vm.result.isEmpty() -> Text(
                                if (vm.running) "Writing…" else "The rewrite appears here.",
                                color = SDim.copy(alpha = 0.7f), fontSize = 18.sp, fontFamily = FontFamily.Serif,
                            )
                            else -> Text(
                                vm.result,
                                style = TextStyle(
                                    color = SInk, fontSize = 18.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif,
                                    textDirection = TextDirection.Content,
                                ),
                            )
                        }
                        if (vm.result.isNotBlank() && !vm.running) {
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionChip("Copy", Icons.Rounded.ContentCopy) {
                                    clipboard.setText(AnnotatedString(vm.result))
                                    toast(ctx, "Copied")
                                }
                                ActionChip("Use as draft", Icons.Rounded.ArrowDownward) { vm.useResult() }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Paper(title: String, right: @Composable () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(SSheet).border(1.dp, SRule, shape).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = SInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            right()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Tap(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = SDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(SBlue.copy(alpha = 0.1f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SBlue, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = SBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
