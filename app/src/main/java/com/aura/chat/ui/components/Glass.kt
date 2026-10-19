package com.aura.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.theme.LocalGlass

/** Slowly drifting colour fields behind every screen. */
@Composable
fun AuraBackground(modifier: Modifier = Modifier) {
    val c = LocalAura.current
    val t = rememberInfiniteTransition(label = "bg")
    val p1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "p1")
    val p2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "p2")
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg, c.bgAlt)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strong = if (c.dark) 0.42f else 0.30f
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            blob(c.accent.copy(alpha = strong), w * (0.10f + 0.55f * p1), h * (0.10f + 0.12f * p2), w * 0.95f)
            blob(c.accent2.copy(alpha = strong * 0.85f), w * (0.95f - 0.55f * p2), h * (0.55f + 0.15f * p1), w * 1.0f)
            blob(c.accent.copy(alpha = strong * 0.6f), w * (0.2f + 0.3f * p2), h * (0.95f - 0.1f * p1), w * 0.8f)
        }
    }
}

@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val c = LocalAura.current
    val g = LocalGlass.current
    val top = if (c.dark) 0.07f + 0.14f * g else 0.55f + 0.30f * g
    val bottom = if (c.dark) 0.03f + 0.07f * g else 0.35f + 0.25f * g
    val edgeStart = if (c.dark) 0.34f else 0.28f
    val edgeEnd = if (c.dark) 0.04f else 0.06f
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(c.glassTint.copy(alpha = top), c.glassTint.copy(alpha = bottom))))
        .border(1.dp, Brush.linearGradient(listOf(c.border.copy(alpha = edgeStart), c.border.copy(alpha = edgeEnd))), shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.glass(shape)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalAura.current.text,
    contentDescription: String? = null,
) {
    Box(
        modifier.size(size).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = c.accent, spotColor = c.accent)
            .clip(shape)
            .background(c.gradient)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .glass(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        content()
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val c = LocalAura.current
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.textDim, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().glass(shape).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (leading != null) {
                Icon(leading, null, tint = c.textDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = c.textDim.copy(alpha = 0.7f))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalAura.current.textDim,
        modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val c = LocalAura.current
    Box(modifier.size(size).clip(RoundedCornerShape(size / 3)).background(c.gradient), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalAura.current
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = c.textDim)
    }
}

@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.border.copy(alpha = 0.2f), shape)
                .padding(22.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(dismissText, color = c.textDim) }
                if (confirmText != null && onConfirm != null) {
                    TextButton(onClick = onConfirm) {
                        Text(confirmText, color = if (danger) c.danger else c.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberHaptic(enabled: Boolean): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(enabled, h) { { if (enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}
