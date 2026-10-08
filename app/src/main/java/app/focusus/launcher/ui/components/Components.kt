package app.focusus.launcher.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import app.focusus.launcher.ui.theme.Atkinson
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Focus.colors.text,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    scaled: Boolean = true,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = if (scaled) size * Focus.scale else size,
        fontWeight = weight,
        fontFamily = Atkinson,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = lineHeight,
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = Focus.colors
    val shape = RoundedCornerShape(50)
    val fg = when {
        primary -> c.bg
        danger -> c.danger
        else -> c.text
    }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .then(if (primary) Modifier.background(c.text) else Modifier.border(1.dp, if (danger) c.danger else c.outline, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        FText(text, color = fg, size = 15.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Focus.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .then(if (selected) Modifier.background(c.text) else Modifier.border(1.dp, c.outline, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        FText(text, color = if (selected) c.bg else c.text, size = 14.sp, weight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun OutlinedPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Focus.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.bg)
            .border(1.dp, c.outline, shape),
        content = content,
    )
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Focus.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun FocusDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            OutlinedPanel(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, trailing: String? = null) {
    val c = Focus.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(text, Modifier.weight(1f), size = 17.sp, color = if (danger) c.danger else c.text)
        if (trailing != null) FText(trailing, size = 14.sp, color = c.textDim)
    }
}

@Composable
fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = Focus.colors.outline.copy(alpha = 0.35f))
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, action: (@Composable RowScope.() -> Unit)? = null) {
    val c = Focus.colors
    Column {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            FText(title, Modifier.weight(1f), size = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 1)
            if (action != null) action() else Spacer(Modifier.width(48.dp))
        }
        Divider()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    FText(
        text,
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
        size = 13.sp,
        color = Focus.colors.textDim,
        weight = FontWeight.Bold,
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = Focus.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FText(title, size = 17.sp)
            if (subtitle != null) FText(subtitle, size = 14.sp, color = c.textDim)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun FocusSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Focus.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.bg,
            checkedTrackColor = c.text,
            checkedBorderColor = c.text,
            uncheckedThumbColor = c.outline,
            uncheckedTrackColor = c.bg,
            uncheckedBorderColor = c.outline,
        ),
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        FocusSwitch(checked, onChange, enabled)
    }
}

/** A drag-to-confirm control, so that blocking is a deliberate act. */
@Composable
fun SlideToConfirm(text: String, onConfirm: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Focus.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knob = 48.dp
    val padDp = 4.dp
    val knobPx = with(density) { knob.toPx() }
    val pad = with(density) { padDp.toPx() }
    var width by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val max = (width - knobPx - pad * 2).coerceAtLeast(0f)
    val shape = RoundedCornerShape(50)

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(1.dp, c.text, shape)
            .onSizeChanged { width = it.width.toFloat() },
    ) {
        FText(text, Modifier.align(Alignment.Center).padding(start = 40.dp), size = 16.sp, weight = FontWeight.Bold)
        Box(
            Modifier
                .offset { IntOffset((pad + offset.value).roundToInt(), pad.roundToInt()) }
                .size(knob)
                .clip(CircleShape)
                .background(c.text)
                .pointerInput(enabled, max) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (max > 0f && offset.value >= max * 0.85f) {
                                    offset.animateTo(max)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, max)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Slide", tint = c.bg)
        }
    }
}

/** The breathing countdown ring used by the launch pause. */
@Composable
fun BreathingRing(progress: Float, size: Dp = 168.dp, center: @Composable BoxScope.() -> Unit) {
    val c = Focus.colors
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).scale(breath)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.outline.copy(alpha = 0.35f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(c.text, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        center()
    }
}

/** M T W T F S S, bright for scheduled days. */
@Composable
fun DayLetters(days: Set<Int>) {
    val c = Focus.colors
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
            val on = (i + 1) in days
            FText(l, size = 13.sp, color = if (on) c.text else c.textDim.copy(alpha = 0.5f), weight = if (on) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Focus.colors.text,
    size: TextUnit = 20.sp,
    align: TextAlign = TextAlign.Start,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, Modifier.weight(1f), size = size, color = color, align = align, maxLines = 1)
        if (trailing != null) trailing()
    }
}

/** Option list in a dialog; returns the picked value. */
@Composable
fun <T> OptionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    FocusDialog(onDismiss) {
        FText(title, Modifier.padding(20.dp), size = 18.sp, weight = FontWeight.Bold)
        if (note != null) FText(note, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), size = 14.sp, color = Focus.colors.textDim)
        Divider()
        options.forEach { (label, value) ->
            MenuItem(label, { onPick(value) }, trailing = if (value == selected) "Selected" else null)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    cancel: String = "Cancel",
) {
    FocusDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(title, size = 19.sp, weight = FontWeight.Bold)
            FText(message, size = 15.sp, color = Focus.colors.textDim)
            Spacer(Modifier.height(4.dp))
            PillButton(cancel, onDismiss, Modifier.fillMaxWidth(), primary = true)
            PillButton(confirm, onConfirm, Modifier.fillMaxWidth(), danger = danger)
        }
    }
}

fun toast(context: Context, message: String) {
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}
