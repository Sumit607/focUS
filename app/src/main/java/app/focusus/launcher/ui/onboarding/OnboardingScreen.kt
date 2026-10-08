package app.focusus.launcher.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.Schedule
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.data.Suggestions
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.components.CheckDot
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.SurfaceCard
import app.focusus.launcher.ui.components.ToggleRow
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.menu.MAX_FAVORITES
import app.focusus.launcher.ui.settings.AccessibilityDisclosureDialog
import app.focusus.launcher.ui.theme.Focus

private const val STEPS = 6

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = Focus.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    val apps by Apps.apps.collectAsStateWithLifecycle()
    val favs = remember { mutableStateListOf<String>() }
    val distractions = remember { mutableStateListOf<String>() }
    var plan by rememberSaveable { mutableStateOf("focus") }
    var addPause by rememberSaveable { mutableStateOf(true) }
    var seeded by remember { mutableStateOf(false) }

    // Pre-select sensible suggestions once the app list has loaded.
    LaunchedEffect(apps.size) {
        if (!seeded && apps.isNotEmpty()) {
            val st = Store.value
            favs.addAll(st.favorites.ifEmpty { Suggestions.favorites(ctx) })
            distractions.addAll(st.distractions.ifEmpty { Suggestions.distractions().toSet() })
            seeded = true
        }
    }

    BackHandler(enabled = step > 0) { step-- }

    fun applyChoices() {
        val dis = distractions.toSet()
        Store.update { s ->
            val schedules = when {
                dis.isEmpty() || s.schedules.isNotEmpty() -> s.schedules
                plan == "focus" -> listOf(Schedule(Store.newId(), "Focus hours", dis, setOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60))
                plan == "evenings" -> listOf(Schedule(Store.newId(), "Calm evenings", dis, setOf(1, 2, 3, 4, 5, 6, 7), 21 * 60, 7 * 60))
                else -> s.schedules
            }
            s.copy(
                favorites = favs.toList().take(MAX_FAVORITES),
                distractions = dis,
                schedules = schedules,
                pauses = if (addPause) s.pauses + dis.associateWith { 10 } else s.pauses,
            )
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        if (step > 0) {
            FText("Step $step of ${STEPS - 1}", size = 13.sp, color = c.textDim)
        }
        when (step) {
            0 -> Welcome { step = 1 }
            1 -> Chooser(
                title = "Pick your home screen apps",
                subtitle = "Up to $MAX_FAVORITES. Just the ones you truly need. Everything else stays one swipe up.",
                selected = favs,
                suggested = Suggestions.favorites(ctx),
                max = MAX_FAVORITES,
                excludeEssentials = false,
                next = { step = 2 },
            )
            2 -> Chooser(
                title = "Which apps pull you in?",
                subtitle = "These become your distraction list. You decide what happens to them next.",
                selected = distractions,
                suggested = Suggestions.distractions(),
                max = Int.MAX_VALUE,
                excludeEssentials = true,
                next = { step = 3 },
            )
            3 -> Plan(plan, { plan = it }, addPause, { addPause = it }, distractions.size) {
                applyChoices()
                step = 4
            }
            4 -> PermissionsStep { step = 5 }
            else -> HomeStep {
                Store.update { it.copy(onboarded = true) }
                onDone()
            }
        }
    }
}

@Composable
private fun ColumnScope.Welcome(next: () -> Unit) {
    val c = Focus.colors
    Spacer(Modifier.weight(1f))
    FText("focUS", size = 52.sp, weight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    FText("Use your phone on purpose.", size = 22.sp)
    Spacer(Modifier.height(32.dp))
    listOf(
        "A calm home screen with only the apps you need.",
        "Pauses, blocks and schedules for the apps that pull you in.",
        "Everything stays on this phone. focUS has no internet access.",
    ).forEach {
        FText(it, Modifier.padding(vertical = 6.dp), size = 17.sp, color = c.textDim)
    }
    Spacer(Modifier.weight(1f))
    PillButton("Get started", next, Modifier.fillMaxWidth(), primary = true)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.Chooser(
    title: String,
    subtitle: String,
    selected: SnapshotStateList<String>,
    suggested: List<String>,
    max: Int,
    excludeEssentials: Boolean,
    next: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val apps by Apps.apps.collectAsStateWithLifecycle()
    val essentials = remember { Essentials.get(ctx) }
    val rows = remember(apps) {
        apps.filter { !excludeEssentials || it.pkg !in essentials }
            .sortedWith(compareBy<app.focusus.launcher.data.AppEntry>({ if (it.pkg in suggested) 0 else 1 }, { it.label.lowercase() }))
    }
    Spacer(Modifier.height(8.dp))
    FText(title, size = 26.sp, weight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    FText(subtitle, size = 15.sp, color = c.textDim)
    Spacer(Modifier.height(12.dp))
    if (rows.isEmpty()) FText("Loading your apps…", size = 15.sp, color = c.textDim)
    LazyColumn(Modifier.weight(1f)) {
        items(rows, key = { it.pkg }) { app ->
            val on = app.pkg in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable {
                        if (on) selected.remove(app.pkg)
                        else if (selected.size < max) selected.add(app.pkg)
                        else toast(ctx, "Up to $max keeps it calm")
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(app.label, Modifier.weight(1f), size = 18.sp, maxLines = 1)
                CheckDot(on)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PillButton("Continue (${selected.size} chosen)", next, Modifier.fillMaxWidth(), primary = true)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.Plan(
    plan: String,
    setPlan: (String) -> Unit,
    addPause: Boolean,
    setPause: (Boolean) -> Unit,
    count: Int,
    next: () -> Unit,
) {
    val c = Focus.colors
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        FText("Choose a starting plan", size = 26.sp, weight = FontWeight.Bold)
        FText("You can change any of this later in Settings.", size = 15.sp, color = c.textDim)
        Spacer(Modifier.height(4.dp))
        listOf(
            Triple("focus", "Focus hours", "Block your $count distracting apps Monday to Friday, 9:00 to 17:00."),
            Triple("evenings", "Calm evenings", "Block them every day from 21:00 to 07:00 to protect your sleep."),
            Triple("launcher", "Just the launcher", "No schedule for now. Use pauses and blocks when you need them."),
        ).forEach { (key, name, desc) ->
            SurfaceCard(Modifier.fillMaxWidth(), onClick = { setPlan(key) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText(name, size = 19.sp, weight = FontWeight.Bold)
                        FText(desc, size = 14.sp, color = c.textDim)
                    }
                    CheckDot(plan == key)
                }
            }
        }
        ToggleRow(
            "Add a 10-second pause",
            "Before any distracting app opens, take a few breaths and choose how long to use it.",
            checked = addPause,
        ) { setPause(it) }
    }
    Spacer(Modifier.height(12.dp))
    PillButton("Continue", next, Modifier.fillMaxWidth(), primary = true)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.PermissionsStep(next: () -> Unit) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val resume = LocalResumeTick.current
    var disclosure by remember { mutableStateOf(false) }
    val usage = remember(resume) { Permissions.hasUsageAccess(ctx) }
    val guard = remember(resume) { Permissions.isAccessibilityOn(ctx) }
    val notif = remember(resume) { Permissions.hasNotificationAccess(ctx) }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        FText("Let focUS do its job", size = 26.sp, weight = FontWeight.Bold)
        FText("Each permission is used only on this phone. Skip any you don't want; the related feature just stays off.", size = 15.sp, color = c.textDim)
        StepRow("1. Usage access", "Counts screen time and daily limits.", usage, "Allow") { Permissions.openUsageAccess(ctx) }
        StepRow("2. App guard", "Shows pauses, blocks and reminders for your chosen apps.", guard, "Turn on") { disclosure = true }
        StepRow("3. Notification access (optional)", "Only for the notification filter.", notif, "Allow") { Permissions.openNotificationAccess(ctx) }
        SurfaceCard(Modifier.fillMaxWidth()) {
            FText("Seeing “Restricted setting”?", size = 16.sp, weight = FontWeight.Bold)
            FText("Android adds one step for apps installed from a file. Open App info, tap ⋮ at the top right, choose Allow restricted settings, then come back and try again.", size = 14.sp, color = c.textDim)
            Spacer(Modifier.height(8.dp))
            PillButton("Open App info", { Permissions.openAppInfo(ctx) }, Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(12.dp))
    PillButton(if (usage && guard) "Continue" else "Continue for now", next, Modifier.fillMaxWidth(), primary = true)
    Spacer(Modifier.height(20.dp))

    if (disclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                disclosure = false
                Permissions.openAccessibility(ctx)
            },
            onDismiss = { disclosure = false },
        )
    }
}

@Composable
private fun StepRow(title: String, detail: String, ok: Boolean, action: String, onAction: () -> Unit) {
    val c = Focus.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(title, Modifier.weight(1f), size = 18.sp, weight = FontWeight.Bold)
            if (ok) FText("Done", size = 14.sp, color = c.textDim)
        }
        FText(detail, size = 14.sp, color = c.textDim)
        if (!ok) {
            Spacer(Modifier.height(8.dp))
            PillButton(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ColumnScope.HomeStep(done: () -> Unit) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val resume = LocalResumeTick.current
    val isHome = remember(resume) { Permissions.isDefaultHome(ctx) }
    Spacer(Modifier.weight(1f))
    FText("Make focUS your home screen", size = 26.sp, weight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    FText(
        if (isHome) "Done. Pressing Home now brings you here."
        else "Choose focUS as your Home app on the next screen. You can switch back any time from focUS Settings or your phone's Settings → Apps → Default apps.",
        size = 16.sp, color = c.textDim,
    )
    Spacer(Modifier.weight(1f))
    if (!isHome) {
        PillButton("Set as home screen", { ctx.findFragmentActivity()?.let { Permissions.requestHomeRole(it) } }, Modifier.fillMaxWidth(), primary = true)
        Spacer(Modifier.height(12.dp))
    }
    PillButton(if (isHome) "Start using focUS" else "Maybe later", done, Modifier.fillMaxWidth(), primary = isHome)
    Spacer(Modifier.height(24.dp))
}
