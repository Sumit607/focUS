package app.focusus.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focusus.launcher.BuildConfig
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Friction
import app.focusus.launcher.core.Session
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Inbox
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.block.frictionName
import app.focusus.launcher.ui.block.frictionText
import app.focusus.launcher.ui.components.ConfirmDialog
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.OptionDialog
import app.focusus.launcher.ui.components.SectionHeader
import app.focusus.launcher.ui.components.SettingRow
import app.focusus.launcher.ui.components.ToggleRow
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.theme.Focus

private enum class Pick { NONE, FRICTION, BUDGET, SEARCH, RESET }

@Composable
fun SettingsScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val c = Focus.colors
    val resume = LocalResumeTick.current
    var pick by remember { mutableStateOf(Pick.NONE) }
    val missing = remember(resume) {
        listOf(
            Permissions.isDefaultHome(ctx),
            Permissions.hasUsageAccess(ctx),
            Permissions.isAccessibilityOn(ctx),
        ).count { !it }
    }
    val now = System.currentTimeMillis()
    val activeBlocks = state.blocks.count { it.until > now }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().encodeToByteArray()) }
                toast(ctx, "Settings exported")
            } catch (_: Exception) {
                toast(ctx, "Couldn't save the file")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                text != null && Store.importJson(text)
            } catch (_: Exception) {
                false
            }
            toast(ctx, if (ok) "Settings imported" else "That file isn't a focUS settings file")
        }
    }

    fun setPrefs(change: (app.focusus.launcher.core.Prefs) -> app.focusus.launcher.core.Prefs) =
        Store.update { it.copy(prefs = change(it.prefs)) }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar("Settings", onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (missing > 0) {
                SettingRow(
                    "Finish setup",
                    if (missing == 1) "1 permission needs your attention" else "$missing permissions need your attention",
                    onClick = { nav.go(Screen.Permissions) },
                )
            }

            SectionHeader("Focus")
            SettingRow("Focus sessions & schedules", "${state.schedules.size} schedules", onClick = { nav.go(Screen.FocusPlan) })
            SettingRow("Blocked right now", if (activeBlocks == 0) "Nothing blocked" else "$activeBlocks active blocks", onClick = { nav.go(Screen.ActiveBlocks) })
            SettingRow("Distraction list", "${state.distractions.size} apps", onClick = { nav.go(Screen.Distractions) })
            SettingRow("Default strictness", frictionName(state.prefs.defaultFriction), onClick = { pick = Pick.FRICTION })
            ToggleRow(
                "App lock",
                if (AppLock.available(ctx)) "Ask for fingerprint or PIN before unblocking or loosening rules" else "Set a screen lock on your phone to use this",
                checked = state.prefs.appLock,
                enabled = AppLock.available(ctx),
            ) { on ->
                val a = activity ?: return@ToggleRow
                AppLock.authenticate(a, if (on) "Turn on app lock" else "Turn off app lock", failOpen = false) {
                    setPrefs { it.copy(appLock = on) }
                }
            }

            SectionHeader("Home screen")
            SettingRow("Favourites", "${state.favorites.size} of 8", onClick = { nav.go(Screen.Favorites) })
            SettingRow("Hidden apps", "${state.hidden.size} hidden", onClick = { nav.go(Screen.HiddenApps) })
            SettingRow("Daily screen-time budget", budgetText(state.prefs.dailyBudgetMin), onClick = { pick = Pick.BUDGET })
            ToggleRow("Double-tap to lock", "Needs the app guard", checked = state.prefs.doubleTapLock) { on -> setPrefs { it.copy(doubleTapLock = on) } }
            ToggleRow("Swipe down for notifications", checked = state.prefs.swipeDownNotifications) { on -> setPrefs { it.copy(swipeDownNotifications = on) } }
            ToggleRow("Open keyboard in app list", checked = state.prefs.keyboardOnDrawer) { on -> setPrefs { it.copy(keyboardOnDrawer = on) } }
            ToggleRow("Open single search match", "Launch an app as soon as it's the only match", checked = state.prefs.autoLaunchSingleMatch) { on -> setPrefs { it.copy(autoLaunchSingleMatch = on) } }
            SettingRow("Web search", state.prefs.searchEngine.replaceFirstChar { it.uppercase() }, onClick = { pick = Pick.SEARCH })
            SettingRow("Appearance", "Theme, text size, alignment, clock", onClick = { nav.go(Screen.Appearance) })

            SectionHeader("Notifications")
            SettingRow("Notification filter", if (state.notif.enabled) "On, ${state.notif.allow.size} apps allowed" else "Off", onClick = { nav.go(Screen.NotifSettings) })
            SettingRow("Filtered notifications", "Held back for 24 hours", onClick = { nav.go(Screen.Inbox) })

            SectionHeader("Insights")
            SettingRow("Screen time and focus stats", onClick = { nav.go(Screen.Insights) })

            SectionHeader("Phone setup")
            SettingRow("Permissions & battery", if (missing == 0) "All set" else "Needs attention", onClick = { nav.go(Screen.Permissions) })
            SettingRow("Use a different home app", "Switch back to your previous launcher at any time", onClick = { Permissions.openHomeSettings(ctx) })

            SectionHeader("Your data")
            SettingRow("Export settings", "Save a backup file", onClick = { exportLauncher.launch("focus-settings.json") })
            SettingRow("Import settings", "Restore from a backup file", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
            SettingRow("Delete all focUS data", "Start over from the beginning", onClick = { pick = Pick.RESET })

            SectionHeader("About")
            SettingRow("focUS ${BuildConfig.VERSION_NAME}", "No internet permission: nothing you do here leaves this phone.")
            Spacer(Modifier.height(32.dp))
        }
    }

    when (pick) {
        Pick.FRICTION -> OptionDialog(
            title = "Default strictness",
            options = Friction.entries.map { "${frictionName(it)}: ${frictionText(it)}" to it },
            selected = state.prefs.defaultFriction,
            onPick = { f ->
                pick = Pick.NONE
                val apply = { setPrefs { it.copy(defaultFriction = f) } }
                if (f.ordinal < state.prefs.defaultFriction.ordinal) AppLock.guard(activity, "Lower strictness", apply) else apply()
            },
            onDismiss = { pick = Pick.NONE },
        )
        Pick.BUDGET -> OptionDialog(
            title = "Daily screen-time budget",
            note = "Shown as a thin line under the clock. It's a goal, not a block.",
            options = listOf(30, 60, 90, 120, 180, 240, 360).map { budgetText(it) to it },
            selected = state.prefs.dailyBudgetMin,
            onPick = { v ->
                setPrefs { it.copy(dailyBudgetMin = v) }
                pick = Pick.NONE
            },
            onDismiss = { pick = Pick.NONE },
        )
        Pick.SEARCH -> OptionDialog(
            title = "Web search",
            options = listOf("Google" to "google", "DuckDuckGo" to "duckduckgo", "Bing" to "bing", "Brave" to "brave"),
            selected = state.prefs.searchEngine,
            onPick = { v ->
                setPrefs { it.copy(searchEngine = v) }
                pick = Pick.NONE
            },
            onDismiss = { pick = Pick.NONE },
        )
        Pick.RESET -> ConfirmDialog(
            title = "Delete all focUS data?",
            message = "Favourites, folders, blocks, schedules and stats are erased and setup starts again. Your apps are not touched.",
            confirm = "Delete everything",
            danger = true,
            onConfirm = {
                pick = Pick.NONE
                AppLock.guard(activity, "Delete all focUS data") {
                    Store.reset()
                    Inbox.clear()
                    Session.grants.clear()
                    Session.nextReminderAt.clear()
                    nav.home()
                }
            },
            onDismiss = { pick = Pick.NONE },
        )
        Pick.NONE -> {}
    }
}

private fun budgetText(min: Int): String = if (min % 60 == 0) "${min / 60} h" else if (min > 60) "${min / 60} h ${min % 60} min" else "$min min"

@Composable
fun PlainNote(text: String) {
    FText(text, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), size = 14.sp, color = Focus.colors.textDim)
}
