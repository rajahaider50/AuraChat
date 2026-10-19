package com.aura.chat.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.chat.ui.components.AuraOrb
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.GradientButton
import com.aura.chat.ui.components.IconBadge
import com.aura.chat.ui.theme.LocalAura
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0f) }
    val c = LocalAura.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 110f)) }
        launch { alpha.animateTo(1f, tween(800)) }
        delay(1700)
        onDone()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AuraOrb(Modifier.scale(scale.value), size = 170.dp, active = true)
        Spacer(Modifier.height(28.dp))
        Text("Aura", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(alpha.value))
        Spacer(Modifier.height(4.dp))
        Text("Your intelligent companion", color = c.textDim, modifier = Modifier.alpha(alpha.value))
    }
}

private data class IntroPage(val icon: ImageVector?, val title: String, val body: String)

@Composable
fun OnboardingScreen(onFinish: (String) -> Unit) {
    val c = LocalAura.current
    val pages = remember {
        listOf(
            IntroPage(null, "Meet Aura", "A calm, fast chat companion that thinks with you, in whatever language you write."),
            IntroPage(Icons.Rounded.Dns, "Bring your own AI", "Connect any OpenAI-compatible provider: OpenAI, OpenRouter, Groq, Gemini, DeepSeek or your own server."),
            IntroPage(Icons.Rounded.Lock, "Private by design", "Chats and API keys stay on this device. Nothing is sent anywhere except to the provider you choose."),
        )
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Skip",
                color = c.textDim,
                modifier = Modifier.clickable { onFinish(name) }.padding(8.dp).alpha(if (last) 0f else 1f),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val p = pages[page]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (p.icon == null) AuraOrb(size = 200.dp) else IconBadge(p.icon, size = 110.dp)
                Spacer(Modifier.height(36.dp))
                Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                if (page == pages.lastIndex) {
                    Spacer(Modifier.height(28.dp))
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "What should Aura call you?",
                        leading = Icons.Rounded.Person,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = pager.currentPage == i
                Box(
                    Modifier
                        .padding(4.dp)
                        .height(8.dp)
                        .width(if (selected) 26.dp else 8.dp)
                        .background(if (selected) c.accent else c.textDim.copy(alpha = 0.35f), RoundedCornerShape(50))
                )
            }
        }
        GradientButton(
            text = if (last) "Get started" else "Continue",
            onClick = {
                if (last) onFinish(name) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
