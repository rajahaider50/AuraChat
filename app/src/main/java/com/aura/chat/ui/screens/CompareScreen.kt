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
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.data.ProviderEntity
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.SystemBarIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val CBg = Color(0xFF0E0F12)
private val CPanel = Color(0xFF181A1F)
private val CLine = Color(0xFF2A2D35)
private val CText = Color(0xFFEDEAE3)
private val CDim = Color(0xFF8A8F98)
private val CAmber = Color(0xFFFFB020)
private val CIce = Color(0xFF7AA2FF)

class ComparePane(val tag: String, val color: Color) {
    var model by mutableStateOf("")
    var text by mutableStateOf("")
    var running by mutableStateOf(false)
    var firstMs by mutableLongStateOf(-1L)
    var totalMs by mutableLongStateOf(-1L)
    var error by mutableStateOf<String?>(null)

    val approxTokens: Int get() = text.length / 4
    val tokensPerSecond: Double
        get() = if (totalMs > 0) approxTokens / (totalMs / 1000.0) else 0.0
}

class CompareViewModel(private val c: AppContainer) : ViewModel() {
    val a = ComparePane("A", CAmber)
    val b = ComparePane("B", CIce)
    var models by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    private var provider: ProviderEntity? = null
    private val jobs = mutableListOf<Job>()

    val running: Boolean get() = a.running || b.running

    init {
        viewModelScope.launch {
            try {
                val p = c.repo.activeProvider()
                provider = p
                if (p == null) {
                    loadError = "No AI provider is connected. Add one in Settings → Providers."
                } else {
                    a.model = p.model
                    val list = try {
                        c.api.listModels(p)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loadError = "Could not list models (${e.message}). You can still type model ids."
                        emptyList()
                    }
                    models = list
                    if (a.model.isBlank()) a.model = list.firstOrNull().orEmpty()
                    b.model = list.firstOrNull { it != a.model }.orEmpty()
                }
            } finally {
                loading = false
            }
        }
    }

    fun run(prompt: String) {
        if (prompt.isBlank() || running) return
        jobs.clear()
        jobs += launchPane(a, prompt.trim())
        jobs += launchPane(b, prompt.trim())
    }

    fun stop() {
        jobs.forEach { it.cancel() }
    }

    private fun launchPane(pane: ComparePane, prompt: String): Job = viewModelScope.launch {
        val p = provider
        pane.text = ""
        pane.error = null
        pane.firstMs = -1L
        pane.totalMs = -1L
        if (p == null) {
            pane.error = "No AI provider is connected."
            return@launch
        }
        if (pane.model.isBlank()) {
            pane.error = "Pick a model for ${pane.tag}."
            return@launch
        }
        pane.running = true
        val start = System.nanoTime()
        try {
            val temp = c.settings.flow.first().temperature
            c.repo.oneShot(p, pane.model, null, prompt, temp).collect { piece ->
                if (pane.firstMs < 0) pane.firstMs = (System.nanoTime() - start) / 1_000_000
                pane.text += piece
            }
            pane.totalMs = (System.nanoTime() - start) / 1_000_000
        } catch (e: CancellationException) {
            pane.totalMs = (System.nanoTime() - start) / 1_000_000
            throw e
        } catch (e: Exception) {
            pane.error = e.message ?: "Request failed."
        } finally {
            pane.running = false
        }
    }
}

@Composable
fun CompareScreen(onBack: () -> Unit, onProviders: () -> Unit) {
    val container = LocalContext.current.container
    val vm: CompareViewModel = viewModel(factory = simpleFactory { CompareViewModel(container) })
    SystemBarIcons(lightBackground = false)
    var prompt by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<ComparePane?>(null) }

    Box(Modifier.fillMaxSize().background(CBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(CPanel).border(1.dp, CLine, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = CText) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("COMPARE LAB", color = CAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Two models, one prompt", color = CText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Prompt panel
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CPanel)
                    .border(1.dp, CLine, RoundedCornerShape(18.dp)).padding(14.dp),
            ) {
                ToolTextField(
                    prompt, { prompt = it },
                    placeholder = "Write one prompt to send to both models",
                    textColor = CText, hintColor = CDim, accent = CAmber,
                    maxLines = 4, minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.running) "Streaming both…" else "Same prompt, same settings",
                        color = CDim, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    val enabled = vm.running || (prompt.isNotBlank() && !vm.loading)
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (vm.running) CLine else CAmber)
                            .clickable(enabled = enabled) { if (vm.running) vm.stop() else vm.run(prompt) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (vm.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (vm.running) CText else Color(0xFF1A1200))
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.running) "Stop" else "Run", color = if (vm.running) CText else Color(0xFF1A1200), fontWeight = FontWeight.Bold)
                    }
                }
            }

            vm.loadError?.let {
                Text(
                    it, color = Color(0xFFFF8A7A), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable(onClick = onProviders),
                )
            }

            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val aFaster = vm.a.totalMs > 0 && vm.b.totalMs > 0 && vm.a.totalMs < vm.b.totalMs
                val bFaster = vm.a.totalMs > 0 && vm.b.totalMs > 0 && vm.b.totalMs < vm.a.totalMs
                PanePanel(vm.a, aFaster, Modifier.weight(1f)) { picking = vm.a }
                PanePanel(vm.b, bFaster, Modifier.weight(1f)) { picking = vm.b }
            }
        }
    }

    picking?.let { pane ->
        ToolPickerDialog(
            title = "Model for ${pane.tag}",
            items = vm.models,
            selected = pane.model,
            onPick = { pane.model = it; picking = null },
            onDismiss = { picking = null },
            bg = CPanel, text = CText, dim = CDim, accent = pane.color,
            searchable = true, allowCustom = true,
        )
    }
}

@Composable
private fun PanePanel(pane: ComparePane, faster: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(CPanel)
            .border(1.dp, if (faster) pane.color.copy(alpha = 0.8f) else CLine, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(pane.color.copy(alpha = 0.16f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(pane.color), contentAlignment = Alignment.Center) {
                Text(pane.tag, color = Color(0xFF1A1200), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pane.model.ifBlank { "Choose model" }, color = CText, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = CDim)
            }
            if (faster) {
                Text("FASTER", color = pane.color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat("first", if (pane.firstMs >= 0) "${pane.firstMs} ms" else "–")
            Stat("total", if (pane.totalMs >= 0) "%.1f s".format(pane.totalMs / 1000.0) else if (pane.running) "…" else "–")
            Stat("speed", if (pane.totalMs > 0) "%.0f tok/s".format(pane.tokensPerSecond) else "–")
            Stat("≈tokens", if (pane.text.isNotEmpty()) "${pane.approxTokens}" else "–")
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            val err = pane.error
            when {
                err != null -> Text(err, color = Color(0xFFFF8A7A), fontSize = 14.sp)
                pane.text.isEmpty() -> Text(if (pane.running) "Waiting for the first token…" else "Output appears here.", color = CDim, fontSize = 14.sp)
                else -> Text(pane.text, color = CText, fontSize = 14.5.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = CDim, fontSize = 10.sp)
        Text(value, color = CText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}
