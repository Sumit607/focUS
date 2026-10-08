package app.focusus.launcher.ui.block

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Friction
import app.focusus.launcher.core.ManualBlock
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.data.Usage
import app.focusus.launcher.data.formatDuration
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.ChipFlow
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.SlideToConfirm
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DURATIONS = listOf(
    15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours",
    480 to "8 hours", 1440 to "24 hours", 4320 to "3 days", 10080 to "7 days", 43200 to "30 days",
)

fun frictionText(f: Friction): String = when (f) {
    Friction.EASY -> "You can unblock with one confirmation."
    Friction.MEDIUM -> "Unblocking makes you wait 30 seconds first."
    Friction.STRICT -> "No early unblock. Emergency unlock works once a day."
}

fun frictionName(f: Friction): String = when (f) {
    Friction.EASY -> "Easy"
    Friction.MEDIUM -> "Medium"
    Friction.STRICT -> "Strict"
}

@Composable
fun BlockSetupScreen(nav: Nav, state: FocusState, pkg: String) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val label = Apps.label(pkg, state)
    var idx by remember { mutableIntStateOf(6) }
    var friction by remember { mutableStateOf(state.prefs.defaultFriction) }
    val selected = remember { mutableStateListOf(pkg) }
    var week by remember { mutableStateOf<Map<String, Long>?>(null) }
    LaunchedEffect(Unit) {
        week = withContext(Dispatchers.IO) { Usage.lastDays(ctx, 7) }
    }
    val essentials = remember { Essentials.get(ctx) }
    val others = remember(week) {
        week.orEmpty().entries
            .filter { it.key != pkg && it.key !in essentials && Apps.isLaunchable(it.key) && it.value >= 60_000L }
            .sortedByDescending { it.value }
            .take(6)
    }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar("Block $label", onBack = { nav.back() })
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            val spent = week?.get(pkg)
            FText(
                if (spent != null && spent >= 60_000L) {
                    "You spent ${formatDuration(spent)} on $label in the last 7 days. Need a break without uninstalling it? Block it for up to 30 days."
                } else {
                    "Need a break from $label without uninstalling it? Block it for up to 30 days."
                },
                size = 18.sp, align = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            FText("Block for", size = 15.sp, color = c.textDim)
            Spacer(Modifier.height(6.dp))
            FText(DURATIONS[idx].second, size = 26.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Slider(
                value = idx.toFloat(),
                onValueChange = { idx = kotlin.math.round(it).toInt().coerceIn(0, DURATIONS.lastIndex) },
                valueRange = 0f..DURATIONS.lastIndex.toFloat(),
                steps = DURATIONS.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = c.text,
                    activeTrackColor = c.text,
                    inactiveTrackColor = c.outline.copy(alpha = 0.4f),
                    activeTickColor = c.bg,
                    inactiveTickColor = c.outline,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                FText("15 min", Modifier.weight(1f), size = 13.sp, color = c.textDim)
                FText("30 days", size = 13.sp, color = c.textDim)
            }
            val until = System.currentTimeMillis() + DURATIONS[idx].first * RulesEngine.MINUTE
            val untilText = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a"))
            Spacer(Modifier.height(6.dp))
            FText("Until $untilText", size = 14.sp, color = c.textDim)

            Spacer(Modifier.height(28.dp))
            FText("How strict?", Modifier.fillMaxWidth(), size = 15.sp, color = c.textDim)
            Spacer(Modifier.height(10.dp))
            ChipFlow(Modifier.fillMaxWidth()) {
                Friction.entries.forEach { f -> Chip(frictionName(f), friction == f, { friction = f }) }
            }
            Spacer(Modifier.height(8.dp))
            FText(frictionText(friction), Modifier.fillMaxWidth(), size = 14.sp, color = c.textDim)

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                FText("Also block (screen time in the last 7 days)", Modifier.fillMaxWidth(), size = 15.sp, color = c.textDim)
                Spacer(Modifier.height(10.dp))
                ChipFlow(Modifier.fillMaxWidth()) {
                    others.forEach { (p, ms) ->
                        val on = p in selected
                        Chip("${Apps.label(p, state)}  ${formatDuration(ms)}", on, {
                            if (on) selected.remove(p) else selected.add(p)
                        })
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            SlideToConfirm(
                text = if (selected.size > 1) "Slide to block ${selected.size} apps" else "Slide to block",
                onConfirm = {
                    val end = System.currentTimeMillis() + DURATIONS[idx].first * RulesEngine.MINUTE
                    Store.update { s ->
                        s.copy(blocks = s.blocks + ManualBlock(Store.newId(), selected.toSet(), end, friction))
                    }
                    toast(ctx, "Blocked until $untilText")
                    nav.back()
                },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
