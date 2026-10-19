package com.aura.chat.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.container
import com.aura.chat.data.ConversationWithPreview
import com.aura.chat.data.MessageStat
import com.aura.chat.data.Personas
import com.aura.chat.ui.theme.SystemBarIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val IBg = Color(0xFF03110B)
private val ITile = Color(0xFF0A2218)
private val ILine = Color(0xFF14382A)
private val IEmerald = Color(0xFF2BD98B)
private val ILime = Color(0xFFC6FF3D)
private val IText = Color(0xFFE8FFF3)
private val IDim = Color(0xFF6FA48B)

private const val DAY = 24L * 60 * 60 * 1000

private data class Insights(
    val chats: Int,
    val sent: Int,
    val received: Int,
    val tokens: Int,
    val week: List<Pair<String, Int>>,
    val streak: Int,
    val busiestHour: Int?,
    val personas: List<Pair<String, Int>>,
    val today: Int,
    val avgReply: Int,
)

private fun dayIndex(t: Long): Long = (t + TimeZone.getDefault().getOffset(t)) / DAY

private fun compute(stats: List<MessageStat>, convs: List<ConversationWithPreview>): Insights {
    val now = System.currentTimeMillis()
    val today = dayIndex(now)
    val user = stats.filter { it.role == "user" }
    val bot = stats.filter { it.role != "user" }
    val perDay = user.groupingBy { dayIndex(it.createdAt) }.eachCount()

    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    val week = (6 downTo 0).map { back ->
        val idx = today - back
        fmt.format(Date(now - back * DAY)) to (perDay[idx] ?: 0)
    }

    var streak = 0
    var cursor = if ((perDay[today] ?: 0) > 0) today else today - 1
    while ((perDay[cursor] ?: 0) > 0) {
        streak++
        cursor--
    }

    val hour = user.groupingBy {
        val local = it.createdAt + TimeZone.getDefault().getOffset(it.createdAt)
        ((local / (60 * 60 * 1000L)) % 24).toInt()
    }.eachCount().maxByOrNull { it.value }?.key

    val byConv = convs.associate { it.conversation.id to it.conversation.personaId }
    val personas = user.groupingBy { byConv[it.conversationId] ?: "general" }.eachCount()
        .map { (Personas.find(it.key)?.name ?: "Aura") to it.value }
        .sortedByDescending { it.second }
        .take(5)

    return Insights(
        chats = convs.size,
        sent = user.size,
        received = bot.size,
        tokens = stats.sumOf { it.len } / 4,
        week = week,
        streak = streak,
        busiestHour = hour,
        personas = personas,
        today = perDay[today] ?: 0,
        avgReply = if (bot.isEmpty()) 0 else bot.sumOf { it.len } / bot.size / 5,
    )
}

private fun hourLabel(h: Int): String {
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $suffix"
}

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.container
    SystemBarIcons(lightBackground = false)
    val stats by container.repo.msg.observeStats().collectAsStateWithLifecycle(initialValue = emptyList())
    val convs by container.repo.conv.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val data = remember(stats, convs) { compute(stats, convs) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data.sent, data.received) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900))
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(IBg, Color(0xFF04170F))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(ITile).border(1.dp, ILine, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = IText) }
                Spacer(Modifier.width(14.dp))
                Text("Insights", color = IText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero
                Tile(Modifier.fillMaxWidth()) {
                    Text("Words exchanged, estimated in tokens", color = IDim, fontSize = 13.sp)
                    Text(
                        "%,d".format((data.tokens * progress.value).toInt()),
                        color = ILime, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
                    )
                    Text(
                        if (data.sent == 0) "Start chatting and your numbers appear here." else "${data.today} messages sent today",
                        color = IText, fontSize = 15.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallTile(Icons.Rounded.Forum, "${data.chats}", "chats", Modifier.weight(1f))
                    SmallTile(Icons.Rounded.Today, "${data.sent}", "you sent", Modifier.weight(1f))
                    SmallTile(Icons.Rounded.Forum, "${data.received}", "replies", Modifier.weight(1f))
                }

                // Weekly chart
                Tile(Modifier.fillMaxWidth()) {
                    Text("This week", color = IText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Messages you sent per day", color = IDim, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    val max = (data.week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        val n = data.week.size
                        val gap = 10.dp.toPx()
                        val barW = (size.width - gap * (n - 1)) / n
                        data.week.forEachIndexed { i, (_, v) ->
                            val h = (size.height * 0.92f) * (v.toFloat() / max) * progress.value
                            val x = i * (barW + gap)
                            drawRoundRect(
                                color = ILine, topLeft = Offset(x, 0f), size = Size(barW, size.height),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                            )
                            if (h > 0f) {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(if (i == n - 1) ILime else IEmerald, IEmerald.copy(alpha = 0.55f)),
                                        startY = size.height - h, endY = size.height,
                                    ),
                                    topLeft = Offset(x, size.height - h), size = Size(barW, h),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        data.week.forEach { (label, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(label, color = IDim, fontSize = 11.sp)
                                Text("$v", color = IText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(Modifier.weight(1f)) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = ILime)
                        Spacer(Modifier.height(8.dp))
                        Text("${data.streak}", color = IText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (data.streak == 1) "day streak" else "day streak", color = IDim, fontSize = 13.sp)
                    }
                    Tile(Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null, tint = ILime)
                        Spacer(Modifier.height(8.dp))
                        Text(data.busiestHour?.let { hourLabel(it) } ?: "–", color = IText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text("busiest hour", color = IDim, fontSize = 13.sp)
                    }
                }

                Tile(Modifier.fillMaxWidth()) {
                    Text("Favourite personas", color = IText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    if (data.personas.isEmpty()) {
                        Text("No usage yet.", color = IDim, fontSize = 14.sp)
                    } else {
                        val top = data.personas.first().second.coerceAtLeast(1)
                        data.personas.forEach { (name, count) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = IText, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(ILine)) {
                                    Box(
                                        Modifier.fillMaxWidth(count.toFloat() / top * progress.value).height(10.dp).clip(RoundedCornerShape(50))
                                            .background(Brush.horizontalGradient(listOf(IEmerald, ILime)))
                                    )
                                }
                                Text("$count", color = IDim, fontSize = 13.sp, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                }

                Tile(Modifier.fillMaxWidth()) {
                    Text("Average reply", color = IDim, fontSize = 13.sp)
                    Text(
                        if (data.avgReply == 0) "–" else "~${data.avgReply} words",
                        color = IText, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape).background(ITile).border(1.dp, ILine, shape).padding(18.dp),
        content = content,
    )
}

@Composable
private fun SmallTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Tile(modifier) {
        Icon(icon, null, tint = IEmerald, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = IText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = IDim, fontSize = 12.sp)
    }
}
