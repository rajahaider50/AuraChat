package com.aura.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One entry in the Studio carousel on the home screen. */
data class ToolInfo(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
)

val Tools = listOf(
    ToolInfo("call", "Live Call", "Continuous voice call with memory", Icons.Rounded.Call,
        listOf(Color(0xFF16062E), Color(0xFF7A3CFF), Color(0xFFFF2E93))),
    ToolInfo("memory", "Memory", "See everything Aura remembers", Icons.Rounded.Psychology,
        listOf(Color(0xFF0A1128), Color(0xFF2B4C9B), Color(0xFF63B3ED))),
    ToolInfo("compare", "Compare Lab", "Race two models on one prompt", Icons.Rounded.Compare,
        listOf(Color(0xFF14151A), Color(0xFF4A3510), Color(0xFFFFB020))),
    ToolInfo("insights", "Insights", "Your usage, streaks and habits", Icons.Rounded.QueryStats,
        listOf(Color(0xFF03110B), Color(0xFF0B4A33), Color(0xFF2BD98B))),
    ToolInfo("translate", "Translate", "Natural translation, 18 languages", Icons.Rounded.Translate,
        listOf(Color(0xFF2A0E3A), Color(0xFF9B2A7A), Color(0xFFFF5C8A))),
    ToolInfo("studio", "Writing Studio", "Rewrite in exactly the tone you need", Icons.Rounded.AutoFixHigh,
        listOf(Color(0xFF0B1B3A), Color(0xFF2347C8), Color(0xFF2F5BFF))),
)

/** Plain text field that takes its colours from the tool's own palette. */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textColor: Color,
    hintColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 8,
    serif: Boolean = false,
    textSize: TextUnit = 16.sp,
) {
    Column(modifier) {
        androidx.compose.foundation.layout.Box {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = hintColor,
                    fontSize = textSize,
                    fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = if (singleLine) 1 else maxLines,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = textSize,
                    lineHeight = textSize * 1.45f,
                    fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Searchable list dialog styled with the calling tool's palette. */
@Composable
fun ToolPickerDialog(
    title: String,
    items: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    bg: Color,
    text: Color,
    dim: Color,
    accent: Color,
    searchable: Boolean = false,
    allowCustom: Boolean = false,
    display: (String) -> String = { it },
) {
    var query by remember { mutableStateOf("") }
    val shown = items.filter { display(it).contains(query.trim(), ignoreCase = true) || it.contains(query.trim(), ignoreCase = true) }
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(shape).background(bg).padding(20.dp),
        ) {
            Text(title, color = text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            if (searchable || allowCustom) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(text.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolTextField(
                        query, { query = it },
                        placeholder = if (allowCustom) "Filter, or type a custom id" else "Filter",
                        textColor = text, hintColor = dim, accent = accent,
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    if (allowCustom && query.isNotBlank() && shown.none { it == query.trim() }) {
                        Text(
                            "Use",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onPick(query.trim()) }.padding(start = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { it }) { item ->
                    val isSel = item == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { onPick(item) }.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(display(item), color = text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSel) Icon(Icons.Rounded.Check, null, tint = accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Close",
                color = dim,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}
