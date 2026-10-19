package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.ui.shareText
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.SystemBarIcons
import com.aura.chat.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val TPlum = Color(0xFF2A0E3A)
private val TMagenta = Color(0xFF9B2A7A)
private val TRose = Color(0xFFFF5C8A)
private val TCoral = Color(0xFFFF9A7A)
private val TCard = Color(0xFFFFFBFD)
private val TInk = Color(0xFF2A0E3A)
private val TDim = Color(0xFF8B6B96)
private val TAccent = Color(0xFFE0306E)

data class TLang(val name: String, val native: String, val tag: String)

private val TLangs = listOf(
    TLang("English", "English", "en"),
    TLang("Urdu", "اردو", "ur"),
    TLang("Arabic", "العربية", "ar"),
    TLang("Hindi", "हिन्दी", "hi"),
    TLang("Persian", "فارسی", "fa"),
    TLang("Pashto", "پښتو", "ps"),
    TLang("Punjabi", "ਪੰਜਾਬੀ", "pa"),
    TLang("Spanish", "Español", "es"),
    TLang("French", "Français", "fr"),
    TLang("German", "Deutsch", "de"),
    TLang("Turkish", "Türkçe", "tr"),
    TLang("Chinese", "中文", "zh"),
    TLang("Japanese", "日本語", "ja"),
    TLang("Korean", "한국어", "ko"),
    TLang("Russian", "Русский", "ru"),
    TLang("Portuguese", "Português", "pt"),
    TLang("Italian", "Italiano", "it"),
    TLang("Indonesian", "Bahasa Indonesia", "id"),
)

class TranslateViewModel(private val c: AppContainer) : ViewModel() {
    var source by mutableStateOf("")
    var output by mutableStateOf("")
        private set
    var from by mutableStateOf<TLang?>(null)
    var to by mutableStateOf(TLangs.first())
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun translate() {
        val text = source.trim()
        if (text.isEmpty() || running) return
        job = viewModelScope.launch {
            output = ""
            error = null
            running = true
            try {
                val (provider, model) = c.repo.resolveModel()
                val fromPart = from?.let { "from ${it.name} " }.orEmpty()
                val system = "You are a professional translator. Translate the user's text ${fromPart}into ${to.name}. " +
                    "Output only the translation, with no quotes, notes or explanations. " +
                    "Preserve line breaks, names, numbers and the original tone."
                c.repo.oneShot(provider, model, system, text, 0.2f).collect { output += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Translation failed."
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun swap() {
        if (running) return
        val oldFrom = from ?: TLangs.first { it != to }
        val oldTo = to
        from = oldTo
        to = oldFrom
        if (output.isNotBlank()) {
            source = output
            output = ""
        }
    }

    fun clear() {
        source = ""
        output = ""
        error = null
    }
}

@Composable
fun TranslateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm: TranslateViewModel = viewModel(factory = simpleFactory { TranslateViewModel(ctx.container) })
    val clipboard = LocalClipboardManager.current
    SystemBarIcons(lightBackground = false)

    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    val container = ctx.container
    val scope = rememberCoroutineScope()
    var speaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { container.voice.stop() } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(TPlum, TMagenta, TRose, TCoral))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
            }
            Text(
                "Say it in\nany language.", color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                "18 languages · natural, not word-for-word", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(22.dp))

            // Source card
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LangPill("From", vm.from?.name ?: "Auto-detect") { pickFrom = true }
                    Spacer(Modifier.weight(1f))
                    if (vm.source.isEmpty()) {
                        IconTap(Icons.Rounded.ContentPaste, "Paste") {
                            clipboard.getText()?.text?.let { vm.source = it }
                        }
                    } else {
                        IconTap(Icons.Rounded.Close, "Clear") { vm.clear() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToolTextField(
                    vm.source, { if (it.length <= 5000) vm.source = it },
                    placeholder = "Type or paste text",
                    textColor = TInk, hintColor = TDim.copy(alpha = 0.6f), accent = TAccent,
                    minLines = 4, maxLines = 10, textSize = 20.sp,
                )
                Text("${vm.source.length} / 5000", color = TDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.End).padding(top = 8.dp))
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(48.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(TMagenta, TRose))).clickable { vm.swap() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.SwapVert, "Swap languages", tint = Color.White) }
            }

            // Output card
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LangPill("To", vm.to.name) { pickTo = true }
                    Spacer(Modifier.weight(1f))
                    if (vm.output.isNotBlank()) {
                        IconTap(if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp, "Listen") {
                            if (speaking) {
                                container.voice.stop()
                                speaking = false
                            } else {
                                scope.launch {
                                    speaking = true
                                    try {
                                        val key = container.repo.geminiKey()
                                        if (key.isBlank()) {
                                            toast(ctx, "Add a Gemini API key in Settings → Voice & calls")
                                        } else {
                                            val s = container.settings.flow.first()
                                            container.voice.speak(vm.output, key, s.geminiVoice, s.geminiModel)
                                        }
                                    } catch (e: Exception) {
                                        toast(ctx, e.message ?: "Could not play the translation.")
                                    } finally {
                                        speaking = false
                                    }
                                }
                            }
                        }
                        IconTap(Icons.Rounded.ContentCopy, "Copy") {
                            clipboard.setText(AnnotatedString(vm.output))
                            toast(ctx, "Copied")
                        }
                        IconTap(Icons.Rounded.IosShare, "Share") { shareText(ctx, vm.output) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val err = vm.error
                when {
                    err != null -> Text(err, color = TAccent, fontSize = 15.sp)
                    vm.output.isEmpty() -> Text(
                        if (vm.running) "Translating…" else "Your translation appears here.",
                        color = TDim.copy(alpha = 0.7f), fontSize = 20.sp,
                    )
                    else -> Text(
                        vm.output,
                        style = TextStyle(color = TInk, fontSize = 20.sp, lineHeight = 29.sp, textDirection = TextDirection.Content),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(58.dp).shadow(14.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(TPlum, TMagenta)))
                    .clickable(enabled = vm.running || vm.source.isNotBlank()) { if (vm.running) vm.stop() else vm.translate() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(if (vm.running) Icons.Rounded.Stop else Icons.Rounded.Translate, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(if (vm.running) "Stop" else "Translate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (pickFrom) {
        ToolPickerDialog(
            title = "Translate from",
            items = listOf("Auto-detect") + TLangs.map { it.name },
            selected = vm.from?.name ?: "Auto-detect",
            onPick = { name -> vm.from = TLangs.firstOrNull { it.name == name }; pickFrom = false },
            onDismiss = { pickFrom = false },
            bg = TCard, text = TInk, dim = TDim, accent = TAccent,
            display = { name -> TLangs.firstOrNull { it.name == name }?.let { "${it.name}  ·  ${it.native}" } ?: name },
        )
    }
    if (pickTo) {
        ToolPickerDialog(
            title = "Translate to",
            items = TLangs.map { it.name },
            selected = vm.to.name,
            onPick = { name -> TLangs.firstOrNull { it.name == name }?.let { vm.to = it }; pickTo = false },
            onDismiss = { pickTo = false },
            bg = TCard, text = TInk, dim = TDim, accent = TAccent,
            display = { name -> TLangs.firstOrNull { it.name == name }?.let { "${it.name}  ·  ${it.native}" } ?: name },
        )
    }
}

@Composable
private fun SheetCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(18.dp, shape, ambientColor = TPlum, spotColor = TPlum)
            .clip(shape).background(TCard).padding(20.dp),
        content = content,
    )
}

@Composable
private fun LangPill(prefix: String, name: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(TRose.copy(alpha = 0.14f)).clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, color = TDim, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(name, color = TAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.ExpandMore, null, tint = TAccent)
    }
}

@Composable
private fun IconTap(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = TDim, modifier = Modifier.size(22.dp))
    }
}
