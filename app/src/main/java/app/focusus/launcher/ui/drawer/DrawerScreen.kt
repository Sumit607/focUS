package app.focusus.launcher.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Launch
import app.focusus.launcher.data.Apps
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.components.AppRow
import app.focusus.launcher.ui.components.FText
import app.focusus.launcher.ui.components.UnderlineField
import app.focusus.launcher.ui.home.openIntent
import app.focusus.launcher.ui.menu.AppMenuDialog
import app.focusus.launcher.ui.theme.Focus
import kotlinx.coroutines.delay

/** All apps as a quiet, dimmed text list with search at the top. */
@Composable
fun DrawerScreen(nav: Nav, state: FocusState) {
    val ctx = LocalContext.current
    val c = Focus.colors
    val apps by Apps.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val visible = remember(apps, state.hidden, state.renames, query) {
        val q = Apps.normalize(query.trim())
        apps.asSequence()
            .filter { it.pkg !in state.hidden }
            .map { it.pkg to (state.renames[it.pkg] ?: it.label) }
            .filter { q.isEmpty() || Apps.normalize(it.second).contains(q) }
            .sortedWith(compareBy<Pair<String, String>>({ if (q.isNotEmpty() && Apps.normalize(it.second).startsWith(q)) 0 else 1 }, { it.second.lowercase() }))
            .toList()
    }

    fun close() {
        keyboard?.hide()
        focusManager.clearFocus()
        nav.back()
    }

    fun open(pkg: String) {
        keyboard?.hide()
        Launch.open(ctx, pkg)
        nav.home()
    }

    BackHandler {
        if (query.isNotEmpty()) query = "" else close()
    }

    LaunchedEffect(Unit) {
        if (state.prefs.keyboardOnDrawer) {
            delay(150)
            try {
                focus.requestFocus()
                keyboard?.show()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(visible, query) {
        if (state.prefs.autoLaunchSingleMatch && query.trim().length >= 2 && visible.size == 1) {
            delay(250)
            open(visible.first().first)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnderlineField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search apps",
                modifier = Modifier.weight(1f),
                focusRequester = focus,
                imeAction = ImeAction.Go,
                onAction = { visible.firstOrNull()?.let { open(it.first) } },
            )
            IconButton(onClick = {
                keyboard?.hide()
                nav.go(Screen.Settings)
            }) {
                Icon(Icons.Default.Settings, contentDescription = "focUS settings", tint = c.textDim)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 28.dp), state = listState) {
            items(visible, key = { it.first }) { (pkg, label) ->
                AppRow(
                    label = label,
                    onClick = { open(pkg) },
                    onLongClick = { menuFor = pkg },
                    color = c.textDim,
                    size = 20.sp,
                )
            }
            if (query.isNotBlank()) {
                item(key = "web") {
                    FText(
                        "Search the web for “${query.trim()}”",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboard?.hide()
                                openIntent(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl(state.prefs.searchEngine, query.trim()))))
                                nav.home()
                            }
                            .padding(vertical = 18.dp),
                        size = 17.sp,
                        color = c.text,
                    )
                }
            }
            if (visible.isEmpty() && query.isBlank()) {
                item(key = "empty") {
                    FText("Loading your apps…", Modifier.padding(vertical = 18.dp), size = 16.sp, color = c.textDim)
                }
            }
        }
    }

    menuFor?.let { pkg -> AppMenuDialog(pkg, nav) { menuFor = null } }
}

fun searchUrl(engine: String, q: String): String {
    val e = Uri.encode(q)
    return when (engine) {
        "duckduckgo" -> "https://duckduckgo.com/?q=$e"
        "bing" -> "https://www.bing.com/search?q=$e"
        "brave" -> "https://search.brave.com/search?q=$e"
        else -> "https://www.google.com/search?q=$e"
    }
}
