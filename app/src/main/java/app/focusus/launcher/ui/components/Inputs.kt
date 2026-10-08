package app.focusus.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.ui.theme.Atkinson
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.delay

/** A text field with only a thin underline, in the focUS style. */
@Composable
fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    sizeSp: Int = 18,
) {
    val c = Focus.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = c.text, fontSize = (sizeSp * Focus.scale).sp, fontFamily = Atkinson),
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, capitalization = KeyboardCapitalization.Sentences),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onAny = { onAction() }),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .drawBehind {
                val y = size.height + 6.dp.toPx()
                drawLine(c.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) FText(placeholder, size = sizeSp.sp, color = c.textDim, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
    extra: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        try {
            fr.requestFocus()
        } catch (_: Exception) {
        }
    }
    FocusDialog(onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FText(title, size = 19.sp, weight = FontWeight.Bold)
            UnderlineField(text, { text = it.take(40) }, placeholder, Modifier.fillMaxWidth(), fr, onAction = {
                if (text.isNotBlank()) onConfirm(text.trim())
            })
            Spacer(Modifier.height(4.dp))
            PillButton(confirm, { onConfirm(text.trim()) }, Modifier.fillMaxWidth(), primary = true, enabled = text.isNotBlank())
            if (extra != null) extra()
            PillButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CheckDot(checked: Boolean) {
    val c = Focus.colors
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(c.text) else Modifier.border(1.5.dp, c.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = c.bg, modifier = Modifier.size(16.dp))
    }
}

/** Full-screen multi-select list of installed apps with search. */
@Composable
fun AppPickerDialog(
    title: String,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    max: Int = Int.MAX_VALUE,
    excludeEssentials: Boolean = true,
    suggested: List<String> = emptyList(),
    note: String? = null,
) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val apps by Apps.apps.collectAsStateWithLifecycle()
    val state by Store.state.collectAsStateWithLifecycle()
    val essentials = remember { Essentials.get(ctx) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var query by remember { mutableStateOf("") }

    val rows = remember(apps, query, state.renames) {
        val q = Apps.normalize(query.trim())
        apps.asSequence()
            .filter { !excludeEssentials || it.pkg !in essentials }
            .map { it.pkg to (state.renames[it.pkg] ?: it.label) }
            .filter { q.isEmpty() || Apps.normalize(it.second).contains(q) }
            .sortedWith(compareBy<Pair<String, String>>({ if (it.first in suggested) 0 else 1 }, { it.second.lowercase() }))
            .toList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().imePadding()) {
            TopBar(title, onBack = onDismiss) {
                TextButton(onClick = { onDone(selected.toSet()) }) { FText("Done", size = 16.sp, weight = FontWeight.Bold) }
            }
            if (note != null) FText(note, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), size = 14.sp, color = c.textDim)
            UnderlineField(query, { query = it }, "Search apps", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), imeAction = ImeAction.Search)
            if (max != Int.MAX_VALUE) {
                FText("${selected.size} of $max chosen", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), size = 13.sp, color = c.textDim)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.first }) { (pkg, label) ->
                    val on = pkg in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                if (on) selected.remove(pkg)
                                else if (selected.size < max) selected.add(pkg)
                                else toast(ctx, "You can choose up to $max")
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FText(label, Modifier.weight(1f), size = 18.sp, maxLines = 1)
                        CheckDot(on)
                    }
                }
            }
        }
    }
}
