package app.focusus.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.data.UsageCache
import app.focusus.launcher.ui.LocalResumeTick
import app.focusus.launcher.ui.Nav
import app.focusus.launcher.ui.Screen
import app.focusus.launcher.ui.block.BlockSetupScreen
import app.focusus.launcher.ui.drawer.DrawerScreen
import app.focusus.launcher.ui.focus.FocusPlanScreen
import app.focusus.launcher.ui.focus.ScheduleEditScreen
import app.focusus.launcher.ui.home.HomeScreen
import app.focusus.launcher.ui.insights.InboxScreen
import app.focusus.launcher.ui.insights.InsightsScreen
import app.focusus.launcher.ui.onboarding.OnboardingScreen
import app.focusus.launcher.ui.settings.ActiveBlocksScreen
import app.focusus.launcher.ui.settings.AppearanceScreen
import app.focusus.launcher.ui.settings.DistractionsScreen
import app.focusus.launcher.ui.settings.FavoritesScreen
import app.focusus.launcher.ui.settings.HiddenAppsScreen
import app.focusus.launcher.ui.settings.NotifSettingsScreen
import app.focusus.launcher.ui.settings.PermissionsScreen
import app.focusus.launcher.ui.settings.SettingsScreen
import app.focusus.launcher.ui.theme.Focus
import app.focusus.launcher.ui.theme.FocusTheme
import kotlin.concurrent.thread

class MainActivity : FragmentActivity() {
    private val nav = Nav()
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Apps.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                nav.back()
            }
        })
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            FocusTheme(state.appearance) {
                CompositionLocalProvider(LocalResumeTick provides resumeTick) {
                    Box(Modifier.fillMaxSize().background(Focus.colors.bg)) {
                        if (!state.onboarded) {
                            OnboardingScreen(onDone = { nav.home() })
                        } else {
                            Navigation(nav, state)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        UsageCache.invalidate()
        val ctx = applicationContext
        thread(name = "focus-refresh") { Essentials.refresh(ctx) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) nav.home()
    }
}

@Composable
private fun Navigation(nav: Nav, state: FocusState) {
    when (val screen = nav.current) {
        Screen.Home -> HomeScreen(nav, state)
        Screen.Drawer -> DrawerScreen(nav, state)
        Screen.Settings -> SettingsScreen(nav, state)
        Screen.FocusPlan -> FocusPlanScreen(nav, state)
        is Screen.ScheduleEdit -> ScheduleEditScreen(nav, state, screen.id)
        is Screen.BlockSetup -> BlockSetupScreen(nav, state, screen.pkg)
        Screen.Insights -> InsightsScreen(nav, state)
        Screen.Inbox -> InboxScreen(nav, state)
        Screen.NotifSettings -> NotifSettingsScreen(nav, state)
        Screen.Permissions -> PermissionsScreen(nav, state)
        Screen.Appearance -> AppearanceScreen(nav, state)
        Screen.Favorites -> FavoritesScreen(nav, state)
        Screen.HiddenApps -> HiddenAppsScreen(nav, state)
        Screen.Distractions -> DistractionsScreen(nav, state)
        Screen.ActiveBlocks -> ActiveBlocksScreen(nav, state)
    }
}
