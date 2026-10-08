package app.focusus.launcher.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data object Drawer : Screen
    data object Settings : Screen
    data object FocusPlan : Screen
    data class ScheduleEdit(val id: String?) : Screen
    data class BlockSetup(val pkg: String) : Screen
    data object Insights : Screen
    data object Inbox : Screen
    data object NotifSettings : Screen
    data object Permissions : Screen
    data object Appearance : Screen
    data object Favorites : Screen
    data object HiddenApps : Screen
    data object Distractions : Screen
    data object ActiveBlocks : Screen
}

/** A tiny back stack; the launcher always starts and ends on Home. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    /** Increments when the user presses Home, so screens can close menus. */
    var homeEvents by mutableIntStateOf(0)

    val current: Screen get() = stack.last()

    fun go(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        stack.clear()
        stack.add(Screen.Home)
        homeEvents++
    }
}

/** Bumps every time the launcher resumes, so permission and usage checks refresh. */
val LocalResumeTick = compositionLocalOf { 0 }
