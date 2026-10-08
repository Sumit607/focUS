package app.focusus.launcher.ui.focus

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.ManualBlock
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Schedule
import app.focusus.launcher.core.Store
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.ChipFlow
import app.focusus.launcher.ui.components.DayLetters
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.FocusSwitch
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.SectionHeader
import app.focusus.launcher.ui.components.SurfaceCard
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val FOCUS_SESSION_LABEL = "Focus session"

fun minuteText(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** Focus sessions (Pomodoro-style) and recurring blocking schedules. */
@Composable
fun FocusPlanScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val c = Focus.colors
    val zone = ZoneId.systemDefault()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a")
    val session = state.blocks.filter { it.label == FOCUS_SESSION_LABEL && it.until > now }.maxByOrNull { it.until }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar("Focus", onBack = { nav.back() }) {
            IconButton(onClick = { nav.go(Screen.ScheduleEdit(null)) }) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule", tint = c.text)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Focus session")
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (session != null) {
                    val end = Instant.ofEpochMilli(session.until).atZone(zone).format(fmt)
                    val left = ((session.until - now) / RulesEngine.MINUTE).coerceAtLeast(0) + 1
                    FText("Focusing until $end ($left min left).", size = 17.sp)
                    FText("${session.apps.size} distracting apps are blocked.", size = 14.sp, color = c.textDim)
                } else {
                    FText("Block your distraction list right now, Pomodoro style.", size = 16.sp, color = c.textDim)
                    if (state.distractions.isEmpty()) {
                        PillButton("Choose your distraction list", { nav.go(Screen.Distractions) }, Modifier.fillMaxWidth())
                    } else {
                        ChipFlow {
                            listOf(25, 50, 90).forEach { m ->
                                Chip("$m min", false, {
                                    val until = System.currentTimeMillis() + m * RulesEngine.MINUTE
                                    Store.update { s ->
                                        s.copy(blocks = s.blocks + ManualBlock(Store.newId(), s.distractions, until, s.prefs.defaultFriction, FOCUS_SESSION_LABEL))
                                    }
                                    toast(ctx, "Focus session started")
                                })
                            }
                        }
                        FText("Blocks ${state.distractions.size} apps from your distraction list.", size = 13.sp, color = c.textDim)
                    }
                }
            }

            SectionHeader("Schedules")
            if (state.schedules.isEmpty()) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FText("No schedules yet. Add one to block apps automatically at set times, like work hours or bedtime.", size = 16.sp, color = c.textDim)
                    PillButton("Add a schedule", { nav.go(Screen.ScheduleEdit(null)) }, Modifier.fillMaxWidth(), primary = true)
                }
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.schedules.forEach { s -> ScheduleCard(s, now, zone, onOpen = {
                    if (RulesEngine.isScheduleLocked(s, now, zone)) toast(ctx, "“${s.name}” is strict and running, so it can't be changed until it ends")
                    else nav.go(Screen.ScheduleEdit(s.id))
                }, onToggle = { on ->
                    if (!on && RulesEngine.isScheduleLocked(s, now, zone)) {
                        toast(ctx, "Strict schedules can't be turned off while they run")
                    } else {
                        val apply = { Store.update { st -> st.copy(schedules = st.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) } }
                        if (!on) AppLock.guard(activity, "Turn off ${s.name}", apply) else apply()
                    }
                }) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScheduleCard(s: Schedule, now: Long, zone: ZoneId, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = Focus.colors
    val active = RulesEngine.isScheduleActive(s, now, zone)
    SurfaceCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        FText(
            "${minuteText(s.startMin)} – ${minuteText(s.endMin)}" + if (active) "  ·  on now" else "",
            size = 13.sp, color = c.textDim,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText(s.name, size = 22.sp, weight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayLetters(s.days)
                    FText("   ${s.apps.size} apps", size = 13.sp, color = c.textDim)
                }
            }
            FocusSwitch(s.enabled, onToggle)
        }
    }
}
