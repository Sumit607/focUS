package app.focusus.launcher.gate

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Gate
import app.focusus.launcher.core.ManualBlock
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Session
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.theme.Focus
import app.focusus.launcher.ui.theme.FocusTheme

/** The in-app reminder sheet: leave, block, or take a little more time. */
class ReminderActivity : GateActivity() {
    private var kind = Gate.KIND_INTERVAL
    private var minutes = 0L

    override fun readExtras(i: Intent) {
        kind = i.getStringExtra(Gate.EXTRA_KIND) ?: Gate.KIND_INTERVAL
        minutes = i.getLongExtra(Gate.EXTRA_MINUTES, 0L)
    }

    override fun onShown() {
        Store.bump { it.copy(remindersShown = it.remindersShown + 1) }
    }

    override fun render() {
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            FocusTheme(state.appearance) {
                key(generation, pkg) { ReminderSheet(state) }
            }
        }
    }

    /** Back gives one more minute rather than silently dismissing. */
    override fun onBack() = moreTime(1)

    private fun takeMeOut() {
        Store.bump { it.copy(remindersExited = it.remindersExited + 1) }
        Session.grants.remove(pkg)
        Session.nextReminderAt.remove(pkg)
        leaveHome()
    }

    private fun blockForAnHour() {
        val now = System.currentTimeMillis()
        val friction = Store.value.prefs.defaultFriction
        Store.update { s ->
            s.copy(blocks = s.blocks + ManualBlock(Store.newId(), setOf(pkg), now + RulesEngine.HOUR, friction))
        }
        Session.grants.remove(pkg)
        Session.nextReminderAt.remove(pkg)
        leaveHome()
    }

    private fun moreTime(m: Int) {
        val until = System.currentTimeMillis() + m * RulesEngine.MINUTE
        if (kind == Gate.KIND_SESSION) Session.grants[pkg] = until else Session.nextReminderAt[pkg] = until
        Session.gatePkg = pkg
        finish()
    }

    @Composable
    private fun ReminderSheet(state: FocusState) {
        val c = Focus.colors
        val label = Apps.label(pkg, state)
        val message = if (kind == Gate.KIND_SESSION) {
            "Your session on $label is over."
        } else {
            val m = minutes.coerceAtLeast(1)
            "You've been on $label for $m min."
        }
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FText(message, size = 16.sp, color = c.textDim)
                Spacer(Modifier.height(16.dp))
                PillButton("Take me out", { takeMeOut() }, Modifier.fillMaxWidth(), primary = true)
                Spacer(Modifier.height(10.dp))
                PillButton("Block $label for 1 hour", { blockForAnHour() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                FText("More time", Modifier.fillMaxWidth(), size = 14.sp, color = c.textDim)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15).forEach { m ->
                        Chip("$m min", selected = false, onClick = { moreTime(m) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline))
            }
        }
    }
}
