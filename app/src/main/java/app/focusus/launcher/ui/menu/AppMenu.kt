package app.focusus.launcher.ui.menu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.Folder
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.components.Divider
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.FocusDialog
import app.focusus.launcher.ui.components.MenuItem
import app.focusus.launcher.ui.components.OptionDialog
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.TextInputDialog
import app.focusus.launcher.ui.components.ToggleRow
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.theme.Focus
import androidx.compose.foundation.layout.fillMaxWidth

const val MAX_FAVORITES = 8

private enum class Sub { NONE, REMINDER, PAUSE, LIMIT, RENAME, FOLDER, NEW_FOLDER }

/** Long-press menu for any app, on the home screen or in the app list. */
@Composable
fun AppMenuDialog(pkg: String, nav: Nav, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val c = Focus.colors
    val state by Store.state.collectAsStateWithLifecycle()
    val label = Apps.label(pkg, state)
    val essential = remember(pkg) { pkg in Essentials.get(ctx) }
    var sub by remember { mutableStateOf(Sub.NONE) }

    fun minutes(v: Int) = if (v <= 0) "Off" else if (v % 60 == 0) "${v / 60} h" else "$v min"

    when (sub) {
        Sub.REMINDER -> OptionDialog(
            title = "Time reminder",
            note = "A gentle sheet appears after this much continuous use of $label.",
            options = listOf("Off" to 0, "Every 5 min" to 5, "Every 10 min" to 10, "Every 15 min" to 15, "Every 30 min" to 30),
            selected = state.reminders[pkg] ?: 0,
            onPick = { v ->
                val apply = { Store.update { s -> s.copy(reminders = if (v > 0) s.reminders + (pkg to v) else s.reminders - pkg) } }
                if (v == 0) AppLock.guard(activity, "Turn off reminder for $label", apply) else apply()
                onDismiss()
            },
            onDismiss = { sub = Sub.NONE },
        )
        Sub.PAUSE -> OptionDialog(
            title = "Launch pause",
            note = "Wait a few breaths before $label opens, then choose how long to use it.",
            options = listOf("Off" to 0, "5 seconds" to 5, "10 seconds" to 10, "15 seconds" to 15),
            selected = state.pauses[pkg] ?: 0,
            onPick = { v ->
                val apply = { Store.update { s -> s.copy(pauses = if (v > 0) s.pauses + (pkg to v) else s.pauses - pkg) } }
                if (v == 0) AppLock.guard(activity, "Turn off pause for $label", apply) else apply()
                onDismiss()
            },
            onDismiss = { sub = Sub.NONE },
        )
        Sub.LIMIT -> OptionDialog(
            title = "Daily limit",
            note = if (Permissions.hasUsageAccess(ctx)) "$label is blocked until midnight once you reach this." else "Needs usage access (Settings → Permissions) to count your time.",
            options = listOf("Off" to 0, "15 min" to 15, "30 min" to 30, "45 min" to 45, "1 hour" to 60, "1.5 hours" to 90, "2 hours" to 120),
            selected = state.limits[pkg] ?: 0,
            onPick = { v ->
                val apply = { Store.update { s -> s.copy(limits = if (v > 0) s.limits + (pkg to v) else s.limits - pkg) } }
                if (v == 0) AppLock.guard(activity, "Remove limit for $label", apply) else apply()
                onDismiss()
            },
            onDismiss = { sub = Sub.NONE },
        )
        Sub.RENAME -> TextInputDialog(
            title = "Rename $label",
            initial = state.renames[pkg] ?: label,
            confirm = "Save",
            onConfirm = { name ->
                Store.update { s -> s.copy(renames = s.renames + (pkg to name)) }
                onDismiss()
            },
            onDismiss = { sub = Sub.NONE },
            extra = {
                if (state.renames.containsKey(pkg)) {
                    PillButton("Use original name", {
                        Store.update { s -> s.copy(renames = s.renames - pkg) }
                        onDismiss()
                    }, Modifier.fillMaxWidth())
                }
            },
        )
        Sub.FOLDER -> FocusDialog({ sub = Sub.NONE }) {
            FText("Move $label to", Modifier.padding(20.dp), size = 18.sp, weight = FontWeight.Bold)
            Divider()
            state.folders.forEach { f ->
                MenuItem(f.name, {
                    moveToFolder(pkg, f.id)
                    onDismiss()
                }, trailing = if (pkg in f.apps) "Here now" else null)
            }
            MenuItem("New folder…", { sub = Sub.NEW_FOLDER })
            if (state.folders.any { pkg in it.apps }) {
                MenuItem("Take out of folder", {
                    Store.update { s -> s.copy(folders = s.folders.map { it.copy(apps = it.apps - pkg) }) }
                    onDismiss()
                })
            }
            Spacer(Modifier.height(8.dp))
        }
        Sub.NEW_FOLDER -> TextInputDialog(
            title = "New folder",
            initial = "",
            placeholder = "Folder name",
            confirm = "Create",
            onConfirm = { name ->
                val id = Store.newId()
                Store.update { s ->
                    s.copy(
                        folders = s.folders + Folder(id, name),
                        favorites = if (s.favorites.size < MAX_FAVORITES) s.favorites + "folder:$id" else s.favorites,
                    )
                }
                moveToFolder(pkg, id)
                onDismiss()
            },
            onDismiss = { sub = Sub.NONE },
        )
        Sub.NONE -> FocusDialog(onDismiss) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FText(label, Modifier.padding(20.dp), size = 19.sp, weight = FontWeight.Bold)
                Divider()
                if (!essential) {
                    ToggleRow(
                        title = "Time reminder",
                        subtitle = state.reminders[pkg]?.let { "Every $it min" } ?: "Off",
                        checked = state.reminders.containsKey(pkg),
                    ) { on ->
                        if (on) sub = Sub.REMINDER
                        else AppLock.guard(activity, "Turn off reminder for $label") {
                            Store.update { s -> s.copy(reminders = s.reminders - pkg) }
                        }
                    }
                }
                val isFav = pkg in state.favorites
                MenuItem(if (isFav) "Remove from favourites" else "Add to favourites", {
                    if (isFav) {
                        Store.update { s -> s.copy(favorites = s.favorites - pkg) }
                    } else if (state.favorites.size >= MAX_FAVORITES) {
                        toast(ctx, "Up to $MAX_FAVORITES favourites keeps home calm")
                    } else {
                        Store.update { s -> s.copy(favorites = s.favorites + pkg) }
                    }
                    onDismiss()
                })
                if (!essential) {
                    MenuItem("Block…", {
                        onDismiss()
                        nav.go(Screen.BlockSetup(pkg))
                    })
                    MenuItem("Launch pause", { sub = Sub.PAUSE }, trailing = state.pauses[pkg]?.let { "$it s" } ?: "Off")
                    MenuItem("Daily limit", { sub = Sub.LIMIT }, trailing = minutes(state.limits[pkg] ?: 0))
                }
                MenuItem("Rename", { sub = Sub.RENAME })
                val hidden = pkg in state.hidden
                MenuItem(if (hidden) "Unhide" else "Hide", {
                    Store.update { s ->
                        if (hidden) s.copy(hidden = s.hidden - pkg)
                        else s.copy(hidden = s.hidden + pkg, favorites = s.favorites - pkg)
                    }
                    onDismiss()
                })
                MenuItem("Move to folder", { sub = Sub.FOLDER })
                MenuItem("Uninstall", {
                    onDismiss()
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        Permissions.openAppInfo(ctx, pkg)
                    }
                })
                MenuItem("App info", {
                    onDismiss()
                    Permissions.openAppInfo(ctx, pkg)
                })
                if (essential) {
                    FText(
                        "Always allowed: focUS never blocks calls, messages or Settings.",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        size = 13.sp, color = c.textDim,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun moveToFolder(pkg: String, folderId: String) {
    Store.update { s ->
        s.copy(
            folders = s.folders.map { f ->
                if (f.id == folderId) {
                    if (pkg in f.apps) f else f.copy(apps = f.apps + pkg)
                } else {
                    f.copy(apps = f.apps - pkg)
                }
            },
            favorites = s.favorites - pkg,
        )
    }
}
