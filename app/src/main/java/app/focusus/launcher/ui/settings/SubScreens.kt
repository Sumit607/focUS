package app.focusus.launcher.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focusus.launcher.core.Appearance
import app.focusus.launcher.core.ClockStyle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Folder
import app.focusus.launcher.core.Friction
import app.focusus.launcher.core.HomeAlign
import app.focusus.launcher.core.ManualBlock
import app.focusus.launcher.core.Store
import app.focusus.launcher.core.ThemeMode
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Suggestions
import app.focusus.launcher.service.NotificationFilterService
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.block.frictionName
import app.focusus.launcher.ui.components.AppPickerDialog
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.ChipFlow
import app.focusus.launcher.ui.components.ConfirmDialog
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.FocusDialog
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.SectionHeader
import app.focusus.launcher.ui.components.SettingRow
import app.focusus.launcher.ui.components.TextInputDialog
import app.focusus.launcher.ui.components.ToggleRow
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.menu.MAX_FAVORITES
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun Page(title: String, nav: Nav, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Focus.colors.bg).systemBarsPadding()) {
        TopBar(title, onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------- Appearance ----------------

@Composable
fun AppearanceScreen(nav: Nav, state: FocusState) {
    val a = state.appearance
    fun set(change: (Appearance) -> Appearance) = Store.update { it.copy(appearance = change(it.appearance)) }
    Page("Appearance", nav) {
        SectionHeader("Theme")
        ChipFlow(Modifier.padding(horizontal = 24.dp)) {
            listOf("Dark" to ThemeMode.DARK, "Light" to ThemeMode.LIGHT, "Follow phone" to ThemeMode.SYSTEM).forEach { (l, v) ->
                Chip(l, a.theme == v, { set { it.copy(theme = v) } })
            }
        }
        PlainNote("Dark uses true black, which saves battery on your phone's AMOLED screen.")
        SectionHeader("Text size")
        ChipFlow(Modifier.padding(horizontal = 24.dp)) {
            listOf("Small" to 0.9f, "Medium" to 1.0f, "Large" to 1.15f, "Extra large" to 1.3f).forEach { (l, v) ->
                Chip(l, kotlin.math.abs(a.textScale - v) < 0.01f, { set { it.copy(textScale = v) } })
            }
        }
        SectionHeader("Home alignment")
        ChipFlow(Modifier.padding(horizontal = 24.dp)) {
            listOf("Left" to HomeAlign.LEFT, "Centre" to HomeAlign.CENTER, "Right" to HomeAlign.RIGHT).forEach { (l, v) ->
                Chip(l, a.align == v, { set { it.copy(align = v) } })
            }
        }
        SectionHeader("Clock")
        ChipFlow(Modifier.padding(horizontal = 24.dp)) {
            listOf("Phone setting" to ClockStyle.AUTO, "24-hour" to ClockStyle.H24, "12-hour" to ClockStyle.H12).forEach { (l, v) ->
                Chip(l, a.clock == v, { set { it.copy(clock = v) } })
            }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Show day budget line", "A thin line under the clock showing today's screen time", checked = a.showBudget) { on -> set { it.copy(showBudget = on) } }
    }
}

// ---------------- Favourites ----------------

@Composable
fun FavoritesScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val c = Focus.colors
    var picking by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        Store.update { s ->
            if (j !in s.favorites.indices) s
            else s.copy(favorites = s.favorites.toMutableList().apply { add(j, removeAt(i)) })
        }
    }

    Page("Favourites", nav) {
        PlainNote("Up to $MAX_FAVORITES apps or folders on your home screen. Fewer is calmer.")
        state.favorites.forEachIndexed { i, id ->
            val name = if (id.startsWith("folder:")) {
                state.folders.firstOrNull { "folder:${it.id}" == id }?.let { "${it.name} (folder)" } ?: id
            } else Apps.label(id, state)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(name, Modifier.weight(1f), size = 18.sp, maxLines = 1)
                IconButton(onClick = { move(i, -1) }, enabled = i > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = c.textDim)
                }
                IconButton(onClick = { move(i, 1) }, enabled = i < state.favorites.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = c.textDim)
                }
                IconButton(onClick = { Store.update { s -> s.copy(favorites = s.favorites - id) } }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = c.textDim)
                }
            }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Choose apps", { picking = true }, Modifier.fillMaxWidth(), primary = true)
            PillButton("New folder", {
                if (state.favorites.size >= MAX_FAVORITES) toast(ctx, "Remove a favourite first") else newFolder = true
            }, Modifier.fillMaxWidth())
        }
    }
    if (picking) {
        val folderIds = state.favorites.filter { it.startsWith("folder:") }
        AppPickerDialog(
            title = "Favourite apps",
            initial = state.favorites.filterNot { it.startsWith("folder:") }.toSet(),
            max = MAX_FAVORITES - folderIds.size,
            excludeEssentials = false,
            onDone = { chosen ->
                Store.update { s ->
                    val keep = s.favorites.filter { it.startsWith("folder:") || it in chosen }
                    s.copy(favorites = keep + chosen.filter { it !in keep })
                }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    if (newFolder) {
        TextInputDialog(
            title = "New folder",
            initial = "",
            placeholder = "Folder name",
            confirm = "Create",
            onConfirm = { name ->
                val id = Store.newId()
                Store.update { s -> s.copy(folders = s.folders + Folder(id, name), favorites = s.favorites + "folder:$id") }
                newFolder = false
                toast(ctx, "Long-press any app and choose Move to folder")
            },
            onDismiss = { newFolder = false },
        )
    }
}

// ---------------- Hidden apps ----------------

@Composable
fun HiddenAppsScreen(nav: Nav, state: FocusState) {
    var picking by remember { mutableStateOf(false) }
    Page("Hidden apps", nav) {
        PlainNote("Hidden apps don't appear in your app list or search. They still work if something else opens them, unless you also block them.")
        if (state.hidden.isEmpty()) PlainNote("No hidden apps.")
        state.hidden.sortedBy { Apps.label(it, state).lowercase() }.forEach { pkg ->
            SettingRow(Apps.label(pkg, state), trailing = {
                TextButton(onClick = { Store.update { s -> s.copy(hidden = s.hidden - pkg) } }) { FText("Unhide", size = 15.sp) }
            })
        }
        Column(Modifier.padding(24.dp)) {
            PillButton("Choose apps to hide", { picking = true }, Modifier.fillMaxWidth(), primary = true)
        }
    }
    if (picking) {
        AppPickerDialog(
            title = "Hide apps",
            initial = state.hidden,
            excludeEssentials = true,
            onDone = { chosen ->
                Store.update { s -> s.copy(hidden = chosen, favorites = s.favorites.filter { it !in chosen }) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

// ---------------- Distraction list ----------------

@Composable
fun DistractionsScreen(nav: Nav, state: FocusState) {
    var picking by remember { mutableStateOf(false) }
    Page("Distraction list", nav) {
        PlainNote("The apps that pull you in. Focus sessions block these, and new schedules start with them.")
        if (state.distractions.isEmpty()) PlainNote("Your list is empty.")
        state.distractions.sortedBy { Apps.label(it, state).lowercase() }.forEach { pkg ->
            SettingRow(Apps.label(pkg, state), trailing = {
                TextButton(onClick = { Store.update { s -> s.copy(distractions = s.distractions - pkg) } }) { FText("Remove", size = 15.sp) }
            })
        }
        Column(Modifier.padding(24.dp)) {
            PillButton("Choose apps", { picking = true }, Modifier.fillMaxWidth(), primary = true)
        }
    }
    if (picking) {
        AppPickerDialog(
            title = "Distraction list",
            initial = state.distractions,
            suggested = Suggestions.distractions(),
            onDone = { chosen ->
                Store.update { s -> s.copy(distractions = chosen) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

// ---------------- Active blocks ----------------

@Composable
fun ActiveBlocksScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val c = Focus.colors
    var ending by remember { mutableStateOf<ManualBlock?>(null) }
    val now = System.currentTimeMillis()
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a")
    val active = state.blocks.filter { it.until > now }.sortedBy { it.until }

    Page("Blocked right now", nav) {
        if (active.isEmpty()) PlainNote("Nothing is blocked right now. Long-press any app and choose Block to start.")
        active.forEach { b ->
            val names = b.apps.map { Apps.label(it, state) }.sorted().joinToString(", ")
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                FText(b.label.ifBlank { names }, size = 18.sp, weight = FontWeight.Bold, maxLines = 2)
                if (b.label.isNotBlank()) FText(names, size = 14.sp, color = c.textDim, maxLines = 3)
                FText(
                    "Until ${Instant.ofEpochMilli(b.until).atZone(ZoneId.systemDefault()).format(fmt)} · ${frictionName(b.friction)}",
                    size = 14.sp, color = c.textDim,
                )
                Spacer(Modifier.height(8.dp))
                if (b.friction == Friction.STRICT) {
                    FText("Strict blocks can't end early. Emergency unlock is on the block screen.", size = 13.sp, color = c.textDim)
                } else {
                    PillButton("End early", { ending = b }, Modifier.fillMaxWidth())
                }
            }
        }
    }

    ending?.let { b ->
        val waitMs = if (b.friction == Friction.MEDIUM) 30_000L else 0L
        WaitConfirmDialog(
            title = "End this block early?",
            message = "Everything in it becomes available again.",
            waitMs = waitMs,
            confirm = "End block",
            onConfirm = {
                ending = null
                AppLock.guard(activity, "End block early") {
                    Store.update { s -> s.copy(blocks = s.blocks.filter { it.id != b.id }) }
                }
            },
            onDismiss = { ending = null },
        )
    }
}

@Composable
fun WaitConfirmDialog(title: String, message: String, waitMs: Long, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val start = remember { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(start) }
    LaunchedEffect(Unit) {
        while (now - start < waitMs) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val left = ((waitMs - (now - start)) / 1000 + 1).coerceAtLeast(0)
    val ready = now - start >= waitMs
    FocusDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(title, size = 19.sp, weight = FontWeight.Bold)
            FText(message, size = 15.sp, color = Focus.colors.textDim)
            PillButton("Keep it", onDismiss, Modifier.fillMaxWidth(), primary = true)
            PillButton(if (ready) confirm else "$confirm in $left s", onConfirm, Modifier.fillMaxWidth(), enabled = ready)
        }
    }
}

// ---------------- Notification filter ----------------

@Composable
fun NotifSettingsScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val resume = LocalResumeTick.current
    val hasAccess = remember(resume) { Permissions.hasNotificationAccess(ctx) }
    var picking by remember { mutableStateOf(false) }

    Page("Notification filter", nav) {
        PlainNote("Only apps you allow can notify you. Everything else waits quietly in Filtered notifications for 24 hours. Calls, SMS, alarms and anything ongoing (music, navigation, downloads) always come through.")
        if (!hasAccess) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FText("Step 1: allow focUS to see notifications", size = 17.sp, weight = FontWeight.Bold)
                PillButton("Allow notification access", { Permissions.openNotificationAccess(ctx) }, Modifier.fillMaxWidth(), primary = true)
                PlainNote("If Android says this is a restricted setting, open App info → ⋮ → Allow restricted settings, then try again.")
                PillButton("Open App info", { Permissions.openAppInfo(ctx) }, Modifier.fillMaxWidth())
            }
        }
        ToggleRow("Filter notifications", if (state.notif.enabled) "On" else "Off", checked = state.notif.enabled, enabled = hasAccess) { on ->
            val apply = {
                Store.update { s ->
                    val allow = if (on && s.notif.allow.isEmpty()) Suggestions.notificationAllow(ctx) else s.notif.allow
                    s.copy(notif = s.notif.copy(enabled = on, allow = allow))
                }
                if (on) NotificationFilterService.refilter()
            }
            if (!on) AppLock.guard(activity, "Turn off notification filter", apply) else apply()
        }
        SettingRow("Allowed apps", "${state.notif.allow.size} apps can notify you", onClick = { picking = true })
        SettingRow("Filtered notifications", onClick = { nav.go(Screen.Inbox) })
    }
    if (picking) {
        AppPickerDialog(
            title = "Allowed to notify",
            initial = state.notif.allow,
            suggested = Suggestions.keepNotifications,
            note = "Calls, SMS and alarms always come through.",
            onDone = { chosen ->
                Store.update { s -> s.copy(notif = s.notif.copy(allow = chosen)) }
                NotificationFilterService.refilter()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

// ---------------- Permissions ----------------

@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    val c = Focus.colors
    FocusDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText("Turn on the focUS app guard", size = 19.sp, weight = FontWeight.Bold)
            FText(
                "focUS uses Android's Accessibility service to notice which app is opening, so it can show your pause, block or reminder screen for the apps you chose. It also lets double-tap lock the screen.",
                size = 15.sp,
            )
            FText(
                "It does not read what's on screen, what you type, passwords or messages. focUS has no internet permission, so nothing ever leaves your phone.",
                size = 15.sp, color = c.textDim,
            )
            FText("On the next screen, open focUS app guard and switch it on.", size = 15.sp, color = c.textDim)
            PillButton("Agree and continue", onAgree, Modifier.fillMaxWidth(), primary = true)
            PillButton("Not now", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, ok: Boolean, optional: Boolean = false, action: String, onAction: () -> Unit) {
    val c = Focus.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(title, Modifier.weight(1f), size = 18.sp, weight = FontWeight.Bold)
            FText(if (ok) "On" else if (optional) "Optional" else "Needed", size = 14.sp, color = if (ok) c.textDim else c.text, weight = if (ok) FontWeight.Normal else FontWeight.Bold)
        }
        FText(detail, size = 14.sp, color = c.textDim)
        if (!ok) {
            Spacer(Modifier.height(8.dp))
            PillButton(action, onAction, Modifier.fillMaxWidth(), primary = !optional)
        }
    }
}

@Composable
fun PermissionsScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val resume = LocalResumeTick.current
    var disclosure by remember { mutableStateOf(false) }
    val isHome = remember(resume) { Permissions.isDefaultHome(ctx) }
    val usage = remember(resume) { Permissions.hasUsageAccess(ctx) }
    val guard = remember(resume) { Permissions.isAccessibilityOn(ctx) }
    val stalled = remember(resume) { Permissions.isAccessibilityStalled(ctx) }
    val notif = remember(resume) { Permissions.hasNotificationAccess(ctx) }
    val battery = remember(resume) { Permissions.isIgnoringBatteryOptimizations(ctx) }

    Page("Permissions & battery", nav) {
        PermissionRow("Home screen", "Makes focUS the screen you see when you press Home.", isHome, action = "Make focUS my home screen") {
            activity?.let { Permissions.requestHomeRole(it) }
        }
        PermissionRow("Usage access", "Lets focUS count screen time and daily limits, on the phone only.", usage, action = "Allow usage access") {
            Permissions.openUsageAccess(ctx)
        }
        PermissionRow("App guard", "Shows the pause, block and reminder screens when you open a chosen app.", guard, action = "Turn on app guard") {
            disclosure = true
        }
        if (stalled) {
            PlainNote("The app guard is switched on but Android has stopped it, usually to save battery. Turn it off and on again in Accessibility, and allow focUS to run in the background below.")
        }
        PermissionRow("Notification access", "Needed only for the notification filter.", notif, optional = true, action = "Allow notification access") {
            Permissions.openNotificationAccess(ctx)
        }
        PermissionRow("Run in background", "Stops the phone's battery saver from switching off the app guard.", battery, action = "Allow background activity") {
            Permissions.openBatterySettings(ctx)
        }

        SectionHeader("If Android says “Restricted setting”")
        PlainNote("Because focUS was installed from a file rather than a store, Android asks for one extra step the first time: open App info, tap ⋮ in the top-right corner, choose Allow restricted settings and confirm with your PIN. Then come back and switch the permission on.")
        Column(Modifier.padding(horizontal = 24.dp)) {
            PillButton("Open focUS App info", { Permissions.openAppInfo(ctx) }, Modifier.fillMaxWidth())
        }

        if (Permissions.isVivo()) {
            SectionHeader("vivo phones")
            PlainNote("vivo's battery manager closes background apps aggressively. For a reliable app guard:")
            PlainNote("1. Settings → Battery → Background power consumption management → focUS → Allow.")
            PlainNote("2. Settings → Apps → Autostart (or i Manager → App management → Autostart) → turn on focUS.")
            PlainNote("3. Settings → Apps → focUS → Permissions → allow “Display pop-up windows” and “Display pop-up windows while running in the background” (names vary slightly by version).")
            PlainNote("4. In Recents, pull down on focUS (or tap the lock) so it isn't cleared.")
            PlainNote("5. Accessibility lives in Settings → Shortcuts & accessibility → Accessibility → focUS app guard.")
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                PillButton("Open vivo background settings", { Permissions.openVivoBackgroundSettings(ctx) }, Modifier.fillMaxWidth())
            }
        }
    }

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
