package app.focusus.launcher.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.DayStats
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Launch
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Inbox
import app.focusus.launcher.data.Usage
import app.focusus.launcher.data.formatDuration
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.components.Divider
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.SectionHeader
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class InsightData(
    val today: Map<String, Long>,
    val unlocks: Int,
    val week: List<Pair<LocalDate, Long>>,
)

@Composable
fun InsightsScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val resume = LocalResumeTick.current
    val hasUsage = remember(resume) { Permissions.hasUsageAccess(ctx) }
    var data by remember { mutableStateOf<InsightData?>(null) }
    LaunchedEffect(resume, hasUsage) {
        if (hasUsage) {
            data = withContext(Dispatchers.IO) {
                InsightData(Usage.today(ctx), Usage.unlocksToday(ctx), Usage.dailyTotals(ctx, 7))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar("Insights", onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (!hasUsage) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FText("Screen time needs usage access", size = 20.sp, weight = FontWeight.Bold)
                    FText("Android keeps a record of which apps you use. focUS reads it on the phone to show your screen time. Nothing is uploaded.", size = 15.sp, color = c.textDim)
                    PillButton("Allow usage access", { Permissions.openUsageAccess(ctx) }, Modifier.fillMaxWidth(), primary = true)
                }
            }
            val d = data
            if (hasUsage && d == null) {
                FText("Counting…", Modifier.padding(24.dp), size = 16.sp, color = c.textDim)
            }
            if (d != null) {
                val total = d.today.values.sum()
                val budget = state.prefs.dailyBudgetMin * RulesEngine.MINUTE
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    FText(formatDuration(total), size = 44.sp, weight = FontWeight.Bold)
                    FText(
                        "screen time today · budget ${formatDuration(budget)}" + if (total > budget) " (over)" else "",
                        size = 15.sp, color = c.textDim,
                    )
                    Spacer(Modifier.height(6.dp))
                    FText("${d.unlocks} unlocks", size = 15.sp, color = c.textDim)
                }

                val stats = state.stats[Store.todayKey()] ?: DayStats()
                SectionHeader("focUS today")
                StatRow("Blocked opens stopped", stats.blocksShown.toString())
                StatRow("Launch pauses", "${stats.pausesShown} (${stats.pausesCancelled} turned back)")
                StatRow("Reminders", "${stats.remindersShown} (${stats.remindersExited} left the app)")

                SectionHeader("Top apps today")
                val top = d.today.entries.filter { it.value >= 60_000L }.sortedByDescending { it.value }.take(6)
                if (top.isEmpty()) FText("Nothing yet.", Modifier.padding(horizontal = 24.dp), size = 15.sp, color = c.textDim)
                val max = top.firstOrNull()?.value ?: 1L
                top.forEach { (pkg, ms) -> BarRow(Apps.label(pkg, state), ms, ms.toFloat() / max) }

                SectionHeader("Last 7 days")
                val weekMax = (d.week.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                d.week.forEach { (date, ms) ->
                    BarRow(date.format(DateTimeFormatter.ofPattern("EEE d")), ms, ms.toFloat() / weekMax)
                }

                val reasons = HashMap<String, Int>()
                state.stats.values.forEach { s -> s.reasons.forEach { (k, v) -> reasons[k] = (reasons[k] ?: 0) + v } }
                if (reasons.isNotEmpty()) {
                    SectionHeader("Why you reached for blocked apps")
                    reasons.entries.sortedByDescending { it.value }.forEach { (r, n) -> StatRow(r, n.toString()) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val c = Focus.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(label, Modifier.weight(1f), size = 16.sp)
        FText(value, size = 15.sp, color = c.textDim)
    }
}

@Composable
private fun BarRow(label: String, ms: Long, fraction: Float) {
    val c = Focus.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(label, Modifier.weight(1f), size = 16.sp, maxLines = 1)
            FText(formatDuration(ms), size = 14.sp, color = c.textDim)
        }
        Spacer(Modifier.height(5.dp))
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val h = size.height
            drawLine(c.outline.copy(alpha = 0.3f), Offset(0f, h / 2), Offset(size.width, h / 2), h)
            val w = size.width * fraction.coerceIn(0f, 1f)
            if (w > 0f) drawLine(c.text, Offset(0f, h / 2), Offset(w, h / 2), h)
        }
    }
}

/** Notifications the filter held back, grouped by app. */
@Composable
fun InboxScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val items by Inbox.items.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val grouped = remember(items) { items.groupBy { it.pkg }.entries.sortedByDescending { e -> e.value.maxOf { it.time } } }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar("Filtered notifications", onBack = { nav.back() }) {
            if (items.isNotEmpty()) TextButton(onClick = { Inbox.clear() }) { FText("Clear", size = 15.sp) }
        }
        if (!state.notif.enabled) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FText("The notification filter is off", size = 20.sp, weight = FontWeight.Bold)
                FText("Turn it on to keep only the notifications that matter. The rest wait here, quietly, for 24 hours.", size = 15.sp, color = c.textDim)
                PillButton("Set up the filter", { nav.go(Screen.NotifSettings) }, Modifier.fillMaxWidth(), primary = true)
            }
        } else if (items.isEmpty()) {
            FText("Nothing filtered right now.", Modifier.padding(24.dp), size = 16.sp, color = c.textDim)
        }
        LazyColumn(Modifier.weight(1f)) {
            grouped.forEach { (pkg, list) ->
                item(key = "h_$pkg") {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        FText("${Apps.label(pkg, state)} (${list.size})", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Bold)
                        TextButton(onClick = { Inbox.removeApp(pkg) }) { FText("Dismiss", size = 14.sp, color = c.textDim) }
                    }
                }
                items(list, key = { it.key + it.time }) { n ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Inbox.remove(n.key)
                                Launch.open(ctx, n.pkg)
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Row {
                            FText(n.title.ifBlank { Apps.label(pkg, state) }, Modifier.weight(1f), size = 16.sp, maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            FText(Instant.ofEpochMilli(n.time).atZone(zone).format(fmt), size = 13.sp, color = c.textDim)
                        }
                        if (n.text.isNotBlank()) FText(n.text, size = 14.sp, color = c.textDim, maxLines = 3)
                    }
                    Divider()
                }
            }
        }
    }
}
