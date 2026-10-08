package app.focusus.launcher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.ClockStyle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.HomeAlign
import app.focusus.launcher.core.Launch
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Usage
import app.focusus.launcher.data.formatDuration
import app.focusus.launcher.service.FocusAccessibilityService
import app.focusus.launcher.system.Permissions
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.components.AppRow
import app.focusus.launcher.ui.components.ConfirmDialog
import app.focusus.launcher.ui.components.Divider
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.FocusDialog
import app.focusus.launcher.ui.components.MenuItem
import app.focusus.launcher.ui.components.TextInputDialog
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.menu.AppMenuDialog
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val resume = LocalResumeTick.current
    val apps by Apps.apps.collectAsStateWithLifecycle()
    val installed = remember(apps) { apps.mapTo(HashSet()) { it.pkg } }

    var menuFor by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(nav.homeEvents) {
        menuFor = null
        folderMenu = null
        homeMenu = false
        expanded.clear()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resume) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }

    val hasUsage = remember(resume) { Permissions.hasUsageAccess(ctx) }
    var usedMs by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(resume, now / (5 * 60_000L), hasUsage) {
        usedMs = if (hasUsage) withContext(Dispatchers.IO) { Usage.todayTotal(ctx) } else -1L
    }
    val guardNeeded = needsGuard(state)
    val guardOff = remember(resume, guardNeeded) { guardNeeded && !Permissions.isAccessibilityOn(ctx) }
    val isHome = remember(resume) { Permissions.isDefaultHome(ctx) }

    val textAlign = when (state.appearance.align) {
        HomeAlign.LEFT -> TextAlign.Start
        HomeAlign.CENTER -> TextAlign.Center
        HomeAlign.RIGHT -> TextAlign.End
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .pointerInput(state.prefs.swipeDownNotifications) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) {
                            nav.go(Screen.Drawer)
                        } else if (total > 120f && state.prefs.swipeDownNotifications) {
                            if (!FocusAccessibilityService.openNotifications()) expandStatusBar(ctx)
                        }
                    },
                ) { change, dy ->
                    change.consume()
                    total += dy
                }
            }
            .pointerInput(state.prefs.doubleTapLock) {
                detectTapGestures(
                    onDoubleTap = {
                        if (state.prefs.doubleTapLock && !FocusAccessibilityService.lockScreen()) {
                            toast(ctx, "Turn on the focUS app guard to lock with a double tap")
                        }
                    },
                    onLongPress = { homeMenu = true },
                )
            }
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            FText(
                clockText(ctx, now, state.appearance.clock),
                Modifier.clickable { openIntent(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                size = 64.sp,
            )
            FText(
                dateText(now),
                Modifier.clickable { openIntent(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) },
                size = 16.sp,
                color = c.textDim,
            )
            if (state.appearance.showBudget && usedMs >= 0) {
                Spacer(Modifier.height(16.dp))
                val budget = state.prefs.dailyBudgetMin * RulesEngine.MINUTE
                BudgetLine(usedMs.toFloat() / budget.coerceAtLeast(1L), over = usedMs > budget)
                Spacer(Modifier.height(6.dp))
                FText("${formatDuration(usedMs)} of ${formatDuration(budget)} today", Modifier.clickable { nav.go(Screen.Insights) }, size = 13.sp, color = c.textDim)
            }
            statusLine(ctx, state, now)?.let {
                Spacer(Modifier.height(10.dp))
                FText(it, Modifier.clickable { nav.go(Screen.FocusPlan) }, size = 14.sp, color = c.textDim, align = TextAlign.Center)
            }
            if (guardOff) {
                Spacer(Modifier.height(10.dp))
                FText("App guard is off. Tap to turn it on.", Modifier.clickable { nav.go(Screen.Permissions) }, size = 14.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            }
            if (!isHome) {
                Spacer(Modifier.height(10.dp))
                FText(
                    "Make focUS your home screen",
                    Modifier.clickable { ctx.findFragmentActivity()?.let { Permissions.requestHomeRole(it) } },
                    size = 14.sp, weight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth()) {
            if (state.favorites.isEmpty()) {
                FText(
                    "Add your favourite apps",
                    Modifier.fillMaxWidth().clickable { nav.go(Screen.Favorites) }.padding(vertical = 12.dp),
                    size = 18.sp, color = c.textDim, align = textAlign,
                )
            }
            state.favorites.forEach { id ->
                if (id.startsWith("folder:")) {
                    val folder = state.folders.firstOrNull { "folder:${it.id}" == id } ?: return@forEach
                    val open = expanded[folder.id] == true
                    AppRow(
                        label = folder.name,
                        onClick = { expanded[folder.id] = !open },
                        onLongClick = { folderMenu = folder.id },
                        size = 22.sp,
                        align = textAlign,
                        trailing = {
                            Icon(
                                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (open) "Close folder" else "Open folder",
                                tint = c.textDim,
                            )
                        },
                    )
                    if (open) {
                        folder.apps.filter { it in installed }.forEach { pkg ->
                            AppRow(
                                label = Apps.label(pkg, state),
                                onClick = { Launch.open(ctx, pkg) },
                                onLongClick = { menuFor = pkg },
                                modifier = Modifier.padding(start = 20.dp),
                                color = c.textDim,
                                size = 19.sp,
                                align = textAlign,
                            )
                        }
                    }
                } else if (id in installed) {
                    AppRow(
                        label = Apps.label(id, state),
                        onClick = { Launch.open(ctx, id) },
                        onLongClick = { menuFor = id },
                        size = 22.sp,
                        align = textAlign,
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.35f))

        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { openIntent(ctx, Intent(Intent.ACTION_DIAL)) }) {
                Icon(Icons.Default.Call, contentDescription = "Phone", tint = c.text)
            }
            IconButton(onClick = { openIntent(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }) {
                CameraGlyph(c.text)
            }
        }
    }

    menuFor?.let { pkg -> AppMenuDialog(pkg, nav) { menuFor = null } }
    folderMenu?.let { id -> FolderMenuDialog(state, id) { folderMenu = null } }
    if (homeMenu) HomeMenuDialog(nav) { homeMenu = false }
}

fun needsGuard(state: FocusState): Boolean =
    state.blocks.isNotEmpty() || state.schedules.any { it.enabled } || state.pauses.isNotEmpty() ||
        state.limits.isNotEmpty() || state.reminders.isNotEmpty()

@Composable
private fun BudgetLine(fraction: Float, over: Boolean) {
    val c = Focus.colors
    Canvas(Modifier.width(140.dp).height(3.dp)) {
        val h = size.height
        drawLine(c.outline.copy(alpha = 0.35f), Offset(0f, h / 2), Offset(size.width, h / 2), h)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawLine(if (over) c.danger else c.text, Offset(0f, h / 2), Offset(w, h / 2), h)
    }
}

@Composable
private fun CameraGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 2f * s)
        drawRoundRect(color, topLeft = Offset(2f * s, 7f * s), size = Size(20f * s, 14f * s), cornerRadius = CornerRadius(3f * s), style = stroke)
        drawRect(color, topLeft = Offset(8f * s, 4f * s), size = Size(8f * s, 3f * s))
        drawCircle(color, radius = 4f * s, center = Offset(12f * s, 14f * s), style = stroke)
    }
}

private fun clockText(ctx: Context, now: Long, style: ClockStyle): String {
    val h24 = when (style) {
        ClockStyle.AUTO -> DateFormat.is24HourFormat(ctx)
        ClockStyle.H24 -> true
        ClockStyle.H12 -> false
    }
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofPattern(if (h24) "H:mm" else "h:mm"))
}

private fun dateText(now: Long): String =
    Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

private fun statusLine(ctx: Context, state: FocusState, now: Long): String? {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm a")
    fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(fmt)
    val session = state.blocks.filter { it.until > now && it.label.isNotBlank() }.maxByOrNull { it.until }
    if (session != null) return "${session.label} until ${at(session.until)}"
    val active = RulesEngine.activeSchedules(state.schedules, now, zone)
    if (active.isNotEmpty()) {
        val (s, end) = active.first()
        return "“${s.name}” on until ${at(end)}"
    }
    val blocked = state.blocks.filter { it.until > now }.flatMap { it.apps }.toSet().size
    if (blocked > 0) return if (blocked == 1) "1 app blocked" else "$blocked apps blocked"
    return null
}

fun openIntent(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        toast(ctx, "No app found for that")
    }
}

@SuppressLint("WrongConstant", "PrivateApi")
private fun expandStatusBar(ctx: Context) {
    try {
        val service = ctx.getSystemService("statusbar") ?: return
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    } catch (_: Exception) {
    }
}

@Composable
private fun HomeMenuDialog(nav: Nav, onDismiss: () -> Unit) {
    FocusDialog(onDismiss) {
        FText("focUS", Modifier.padding(20.dp), size = 19.sp, weight = FontWeight.Bold)
        Divider()
        MenuItem("Start a focus session", { onDismiss(); nav.go(Screen.FocusPlan) })
        MenuItem("Schedules", { onDismiss(); nav.go(Screen.FocusPlan) })
        MenuItem("Insights", { onDismiss(); nav.go(Screen.Insights) })
        MenuItem("Filtered notifications", { onDismiss(); nav.go(Screen.Inbox) })
        MenuItem("Edit favourites", { onDismiss(); nav.go(Screen.Favorites) })
        MenuItem("Settings", { onDismiss(); nav.go(Screen.Settings) })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FolderMenuDialog(state: FocusState, folderId: String, onDismiss: () -> Unit) {
    val folder = state.folders.firstOrNull { it.id == folderId }
    if (folder == null) {
        LaunchedEffect(folderId) { onDismiss() }
        return
    }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    when {
        rename -> TextInputDialog(
            title = "Rename folder",
            initial = folder.name,
            confirm = "Save",
            onConfirm = { name ->
                Store.update { s -> s.copy(folders = s.folders.map { if (it.id == folderId) it.copy(name = name) else it }) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        delete -> ConfirmDialog(
            title = "Remove “${folder.name}”?",
            message = "The apps stay installed and remain in your app list.",
            confirm = "Remove folder",
            danger = true,
            onConfirm = {
                Store.update { s ->
                    s.copy(
                        folders = s.folders.filter { it.id != folderId },
                        favorites = s.favorites.filter { it != "folder:$folderId" },
                    )
                }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        else -> FocusDialog(onDismiss) {
            FText(folder.name, Modifier.padding(20.dp), size = 19.sp, weight = FontWeight.Bold)
            Divider()
            MenuItem("Rename", { rename = true })
            MenuItem("Remove folder", { delete = true }, danger = true)
            Spacer(Modifier.height(8.dp))
        }
    }
}
