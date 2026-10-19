package com.aura.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.chat.ui.theme.LocalAura

sealed interface Block {
    data class Code(val lang: String, val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val marker: String, val text: String) : Block
    data class Para(val text: String) : Block
}

private val numbered = Regex("^\\d+\\.\\s+.*")

private fun headingLevel(t: String): Int {
    val l = t.takeWhile { it == '#' }.length
    return if (l in 1..4 && t.length > l && t[l] == ' ') l else 0
}

fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = src.lines()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotEmpty()) {
            out += Block.Para(para.toString().trimEnd())
            para.clear()
        }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trimStart()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += Block.Code(lang, code.toString())
            }
            headingLevel(t) > 0 -> {
                flush()
                val l = headingLevel(t)
                out += Block.Heading(l, t.substring(l + 1).trim())
            }
            t.startsWith("- ") || t.startsWith("* ") -> {
                flush()
                out += Block.Bullet("•", t.substring(2))
            }
            numbered.matches(t) -> {
                flush()
                out += Block.Bullet(t.substringBefore('.') + ".", t.substringAfter('.').trim())
            }
            t.isBlank() -> flush()
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

fun inlineMarkdown(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val e = text.indexOf("**", i + 2)
            if (e > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, e)) }
                i = e + 2
                continue
            }
        }
        if (text[i] == '`') {
            val e = text.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) {
                    append(text.substring(i + 1, e))
                }
                i = e + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}

@Composable
fun MarkdownText(text: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAura.current
    val blocks = remember(text) { parseBlocks(text) }
    val codeBg = c.text.copy(alpha = 0.12f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Code -> CodeBlock(b, onCopy)
                is Block.Heading -> Text(
                    inlineMarkdown(b.text, codeBg),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                is Block.Bullet -> Row {
                    Text(b.marker, color = c.accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(inlineMarkdown(b.text, codeBg), modifier = Modifier.weight(1f))
                }
                is Block.Para -> Text(inlineMarkdown(b.text, codeBg))
            }
        }
    }
}

@Composable
private fun CodeBlock(b: Block.Code, onCopy: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0C0C18))) {
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                b.lang.ifBlank { "code" },
                color = Color(0xFF9A9AB8),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Row(Modifier.clickable { onCopy(b.text) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ContentCopy, "Copy code", tint = Color(0xFF9A9AB8), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy", color = Color(0xFF9A9AB8), fontSize = 12.sp)
            }
        }
        Text(
            b.text,
            color = Color(0xFFE8E8F6),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
        )
    }
}
