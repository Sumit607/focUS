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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Friction
import app.focusus.launcher.core.Schedule
import app.focusus.launcher.core.Store
import app.focusus.launcher.system.AppLock
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.block.frictionName
import app.focusus.launcher.ui.block.frictionText
import app.focusus.launcher.ui.components.AppPickerDialog
import app.focusus.launcher.ui.components.Chip
import app.focusus.launcher.ui.components.ChipFlow
import app.focusus.launcher.ui.components.ConfirmDialog
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.PillButton
import app.focusus.launcher.ui.components.SectionHeader
import app.focusus.launcher.ui.components.SettingRow
import app.focusus.launcher.ui.components.TopBar
import app.focusus.launcher.ui.components.UnderlineField
import app.focusus.launcher.ui.components.findFragmentActivity
import app.focusus.launcher.ui.components.toast
import app.focusus.launcher.ui.theme.Focus

private data class Preset(val name: String, val days: Set<Int>, val start: Int, val end: Int)

private val PRESETS = listOf(
    Preset("Work", setOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60),
    Preset("Study", setOf(1, 2, 3, 4, 5, 6), 17 * 60, 20 * 60),
    Preset("Sleep", setOf(1, 2, 3, 4, 5, 6, 7), 22 * 60, 7 * 60),
    Preset("Morning", setOf(1, 2, 3, 4, 5, 6, 7), 6 * 60, 9 * 60),
)

@Composable
fun ScheduleEditScreen(nav: Nav, state: FocusState, id: String?) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val c = Focus.colors
    val existing = remember(id) { state.schedules.firstOrNull { it.id == id } }
    var draft by remember(id) {
        mutableStateOf(existing ?: Schedule(id = Store.newId(), name = "Work", apps = state.distractions, friction = state.prefs.defaultFriction))
    }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var pickApps by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        when {
            draft.name.isBlank() -> toast(ctx, "Give the schedule a name")
            draft.apps.isEmpty() -> toast(ctx, "Choose at least one app to block")
            draft.days.isEmpty() -> toast(ctx, "Choose at least one day")
            draft.startMin == draft.endMin -> toast(ctx, "Start and end can't be the same time")
            else -> {
                val d = draft.copy(name = draft.name.trim())
                Store.update { s ->
                    if (s.schedules.any { it.id == d.id }) s.copy(schedules = s.schedules.map { if (it.id == d.id) d else it })
                    else s.copy(schedules = s.schedules + d)
                }
                nav.back()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        TopBar(if (existing == null) "New schedule" else "Edit schedule", onBack = { nav.back() }) {
            TextButton(onClick = { save() }) { FText("Save", size = 16.sp) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (existing == null) {
                SectionHeader("Start from")
                ChipFlow(Modifier.padding(horizontal = 24.dp)) {
                    PRESETS.forEach { p ->
                        Chip(p.name, draft.name == p.name && draft.startMin == p.start, {
                            draft = draft.copy(name = p.name, days = p.days, startMin = p.start, endMin = p.end)
                        })
                    }
                }
            }
            SectionHeader("Name")
            UnderlineField(draft.name, { draft = draft.copy(name = it.take(30)) }, "Schedule name", Modifier.fillMaxWidth().padding(horizontal = 24.dp))

            SectionHeader("Time")
            SettingRow("Starts", minuteText(draft.startMin), onClick = { picking = "start" })
            SettingRow(
                "Ends",
                minuteText(draft.endMin) + if (draft.endMin < draft.startMin) " (next day)" else "",
                onClick = { picking = "end" },
            )

            SectionHeader("Days")
            ChipFlow(Modifier.padding(horizontal = 24.dp)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d ->
                    val day = i + 1
                    val on = day in draft.days
                    Chip(d, on, { draft = draft.copy(days = if (on) draft.days - day else draft.days + day) })
                }
            }

            SectionHeader("Apps to block")
            SettingRow(
                if (draft.apps.isEmpty()) "Choose apps" else "${draft.apps.size} apps",
                "Tap to change",
                onClick = { pickApps = true },
            )

            SectionHeader("How strict?")
            ChipFlow(Modifier.padding(horizontal = 24.dp)) {
                Friction.entries.forEach { f -> Chip(frictionName(f), draft.friction == f, { draft = draft.copy(friction = f) }) }
            }
            FText(frictionText(draft.friction) + if (draft.friction == Friction.STRICT) " A strict schedule also can't be edited while it runs." else "",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), size = 14.sp, color = c.textDim)

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Save schedule", { save() }, Modifier.fillMaxWidth(), primary = true)
                if (existing != null) PillButton("Delete schedule", { confirmDelete = true }, Modifier.fillMaxWidth(), danger = true)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { which ->
        TimeDialog(
            initialMin = if (which == "start") draft.startMin else draft.endMin,
            is24 = DateFormat.is24HourFormat(ctx),
            onPick = { m ->
                draft = if (which == "start") draft.copy(startMin = m) else draft.copy(endMin = m)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (pickApps) {
        AppPickerDialog(
            title = "Apps to block",
            initial = draft.apps,
            suggested = state.distractions.toList(),
            onDone = {
                draft = draft.copy(apps = it)
                pickApps = false
            },
            onDismiss = { pickApps = false },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete “${existing.name}”?",
            message = "Its apps will no longer be blocked at these times.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                confirmDelete = false
                AppLock.guard(activity, "Delete ${existing.name}") {
                    Store.update { s -> s.copy(schedules = s.schedules.filter { it.id != existing.id }) }
                    nav.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMin: Int, is24: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Focus.colors
    val st = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
        confirmButton = {
            TextButton(onClick = { onPick(st.hour * 60 + st.minute) }) { FText("OK", size = 16.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { FText("Cancel", size = 16.sp, color = c.textDim) }
        },
        text = {
            TimePicker(
                state = st,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surface,
                    selectorColor = c.text,
                    containerColor = c.bg,
                    clockDialSelectedContentColor = c.bg,
                    clockDialUnselectedContentColor = c.text,
                    periodSelectorSelectedContainerColor = c.text,
                    periodSelectorSelectedContentColor = c.bg,
                    periodSelectorUnselectedContentColor = c.text,
                    timeSelectorSelectedContainerColor = c.text,
                    timeSelectorSelectedContentColor = c.bg,
                    timeSelectorUnselectedContainerColor = c.surface,
                    timeSelectorUnselectedContentColor = c.text,
                ),
            )
        },
    )
}
