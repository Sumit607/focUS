package app.focusus.launcher.gate

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Gate
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Session
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.ui.components.BreathingRing
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.OutlinedPanel
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.theme.Focus
import app.focusus.launcher.ui.theme.FocusTheme
import kotlinx.coroutines.delay

/** The mindful launch pause: wait a few breaths, then choose how long to use the app. */
class PauseActivity : GateActivity() {
    private var seconds = 10

    override fun readExtras(i: Intent) {
        seconds = i.getIntExtra(Gate.EXTRA_SECONDS, 10).coerceIn(1, 60)
    }

    override fun onShown() {
        Store.bump { it.copy(pausesShown = it.pausesShown + 1) }
    }

    override fun render() {
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            FocusTheme(state.appearance) {
                key(generation, pkg) { PauseScreen(state) }
            }
        }
    }

    override fun onBack() = goBack()

    private fun goBack() {
        Store.bump { it.copy(pausesCancelled = it.pausesCancelled + 1) }
        leaveHome()
    }

    private fun grant(minutes: Int) {
        val now = System.currentTimeMillis()
        Session.grants[pkg] = now + minutes * RulesEngine.MINUTE
        Session.gatePkg = pkg
        if (fromLauncher) Apps.start(this, pkg)
        finish()
    }

    @Composable
    private fun PauseScreen(state: FocusState) {
        val c = Focus.colors
        val label = Apps.label(pkg, state)
        val start = remember { System.currentTimeMillis() }
        var elapsed by remember { mutableLongStateOf(0L) }
        val total = seconds * 1000L
        LaunchedEffect(Unit) {
            while (elapsed < total) {
                delay(100)
                elapsed = System.currentTimeMillis() - start
            }
        }
        val done = elapsed >= total
        val remaining = ((total - elapsed + 999) / 1000).coerceAtLeast(0)
        val breathingIn = (elapsed / 4000) % 2 == 0L

        Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            OutlinedPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FText("Open $label?", size = 21.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    BreathingRing(progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FText(if (done) "Ready" else remaining.toString(), size = if (done) 22.sp else 40.sp, weight = FontWeight.Bold)
                            FText(if (done) "your choice" else if (breathingIn) "breathe in" else "breathe out", size = 13.sp, color = c.textDim)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    FText("How long?", size = 15.sp, color = c.textDim)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 15).forEach { m ->
                            Chip("$m min", selected = false, onClick = { grant(m) }, enabled = done)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    PillButton("Go back", { goBack() }, Modifier.fillMaxWidth(), primary = true)
                }
            }
        }
    }
}
