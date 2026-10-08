package app.focusus.launcher.gate

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.BlockReason
import app.focusus.launcher.core.Decision
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Friction
import app.focusus.launcher.core.Launch
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.UsageCache
import app.focusus.launcher.data.formatDuration
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.ChipFlow
import app.focusus.launcher.ui.components.ConfirmDialog
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.FocusDialog
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.theme.Atkinson
import app.focusus.launcher.ui.theme.Focus
import app.focusus.launcher.ui.theme.FocusTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BlockActivity : GateActivity() {

    override fun onShown() {
        Store.bump { it.copy(blocksShown = it.blocksShown + 1) }
    }

    override fun render() {
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            FocusTheme(state.appearance) {
                key(generation, pkg) {
                    BlockScreen(state)
                }
            }
        }
    }

    /** The app is no longer blocked: open it (from the launcher) or step aside (it is underneath). */
    private fun proceed() {
        if (fromLauncher) Launch.open(this, pkg)
        finish()
    }

    @Composable
    private fun BlockScreen(state: FocusState) {
        val c = Focus.colors
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
        val decision = remember(state, now / 5000) { Launch.decide(this@BlockActivity, pkg, now) }
        if (decision !is Decision.Block) {
            LaunchedEffect(decision) { proceed() }
            return
        }
        val label = Apps.label(pkg, state)
        var picked by remember { mutableStateOf<String?>(null) }
        var confirmEasy by remember { mutableStateOf(false) }
        var waitEnds by remember { mutableLongStateOf(0L) }
        var emergency by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            FText(label, size = 32.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            FText(reasonText(decision), size = 17.sp, color = c.textDim, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            val left = (decision.until - now).coerceAtLeast(0)
            FText(
                if (left < 60_000) "Less than a minute left" else "${formatDuration(left)} left",
                size = 17.sp, color = c.textDim, align = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))
            if (picked == null) {
                FText("What did you want to do?", size = 15.sp, color = c.textDim)
                Spacer(Modifier.height(12.dp))
                ChipFlow {
                    listOf("Reply to someone", "Check something", "Bored", "Habit").forEach { r ->
                        Chip(r, selected = false, onClick = {
                            picked = r
                            Store.bump { s -> s.copy(reasons = s.reasons + (r to ((s.reasons[r] ?: 0) + 1))) }
                        })
                    }
                }
            } else {
                FText("Noted. That urge usually passes in a minute or two.", size = 15.sp, color = c.textDim, align = TextAlign.Center)
            }

            Spacer(Modifier.height(56.dp))
            PillButton("Go home", { leaveHome() }, Modifier.fillMaxWidth(), primary = true)
            Spacer(Modifier.height(12.dp))

            when (decision.friction) {
                Friction.EASY -> PillButton("Unblock", { confirmEasy = true }, Modifier.fillMaxWidth())
                Friction.MEDIUM -> {
                    if (waitEnds == 0L) {
                        PillButton("Unblock (wait 30 s)", { waitEnds = now + 30_000 }, Modifier.fillMaxWidth())
                    } else if (now < waitEnds) {
                        PillButton("Unblock in ${((waitEnds - now) / 1000) + 1} s", {}, Modifier.fillMaxWidth(), enabled = false)
                    } else {
                        PillButton("Unblock now", { unblock(decision) }, Modifier.fillMaxWidth())
                    }
                }
                Friction.STRICT -> FText("This block is strict and can't end early.", size = 14.sp, color = c.textDim, align = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { emergency = true }) {
                FText("Emergency unlock", size = 14.sp, color = c.textDim)
            }
        }

        if (confirmEasy) {
            ConfirmDialog(
                title = "Unblock $label?",
                message = "This ends the block early for $label.",
                confirm = "Unblock",
                onConfirm = {
                    confirmEasy = false
                    unblock(decision)
                },
                onDismiss = { confirmEasy = false },
                cancel = "Keep it blocked",
            )
        }
        if (emergency) EmergencyDialog(state, label, now) { emergency = false }
    }

    private fun reasonText(d: Decision.Block): String {
        val zone = ZoneId.systemDefault()
        val until = Instant.ofEpochMilli(d.until).atZone(zone)
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val time = until.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"))
        val whenText = if (until.toLocalDate() == today) time else until.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", " + time
        return when (val r = d.reason) {
            is BlockReason.Manual -> (r.block.label.ifBlank { "Blocked" }) + " until $whenText"
            is BlockReason.ScheduleActive -> "“${r.schedule.name}” is on until $whenText"
            is BlockReason.LimitReached -> "You've used your ${r.limitMin} min for today"
        }
    }

    private fun unblock(decision: Decision.Block) {
        AppLock.guard(this, "Unblock ${Apps.label(pkg)}") {
            val now = System.currentTimeMillis()
            Store.update { RulesEngine.unblock(it, pkg, decision, now) }
            UsageCache.invalidate()
        }
    }

    @Composable
    private fun EmergencyDialog(state: FocusState, label: String, now: Long, onDismiss: () -> Unit) {
        val c = Focus.colors
        val phrase = "I need this now"
        var typed by remember { mutableStateOf("") }
        val strictActive = (Launch.decide(this, pkg, now) as? Decision.Block)?.friction == Friction.STRICT
        val allowed = !strictActive || RulesEngine.canUseEmergency(state, now)
        FocusDialog(onDismiss) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FText("Emergency unlock", size = 19.sp, weight = FontWeight.Bold)
                if (!allowed) {
                    val next = state.emergencyUsedAt + RulesEngine.DAY
                    val t = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEE h:mm a"))
                    FText("You've used the emergency unlock in the last 24 hours. It's available again $t.", size = 15.sp, color = c.textDim)
                    PillButton("OK", onDismiss, Modifier.fillMaxWidth(), primary = true)
                } else {
                    FText("This removes every block on $label right now. Type the sentence below to continue.", size = 15.sp, color = c.textDim)
                    FText(phrase, size = 17.sp, weight = FontWeight.Bold)
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = Atkinson),
                        cursorBrush = SolidColor(c.text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(c.outline, Offset(0f, size.height + 6f), Offset(size.width, size.height + 6f), 2f)
                            }
                            .padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), primary = true)
                    PillButton(
                        "Unlock $label",
                        {
                            AppLock.authenticate(this@BlockActivity, "Emergency unlock", label, failOpen = true) {
                                val at = System.currentTimeMillis()
                                Store.update { s ->
                                    var next = s
                                    repeat(5) {
                                        val d = RulesEngine.decide(pkg, next, at, ZoneId.systemDefault(), emptySet(), 0L, UsageCache.usedToday(this@BlockActivity, pkg, at))
                                        if (d is Decision.Block) next = RulesEngine.unblock(next, pkg, d, at)
                                    }
                                    next.copy(emergencyUsedAt = at)
                                }
                                UsageCache.invalidate()
                                onDismiss()
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = typed.trim().equals(phrase, ignoreCase = true),
                    )
                }
            }
        }
    }
}
