package app.focusus.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import app.focusus.launcher.core.Decision
import app.focusus.launcher.core.Gate
import app.focusus.launcher.core.Launch
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.core.Session
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps

/**
 * The "app guard". Listens only for which app comes to the front (window-state events),
 * never reads screen content, and shows the pause, block or reminder screen when a rule says so.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusGuard"
        private const val TICK_MS = 10_000L

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set

        fun lockScreen(): Boolean {
            val s = instance ?: return false
            return if (Build.VERSION.SDK_INT >= 28) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else false
        }

        fun openNotifications(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private var ignored: Set<String> = emptySet()
    private var homePackages: Set<String> = emptySet()
    /** Package of the most recent real window, so timed checks only act while the user is inside that app. */
    @Volatile private var lastWindowPkg: String? = null
    private var receiverRegistered = false

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (e: Exception) {
                Log.w(TAG, "tick failed", e)
            }
            if (screenOn) handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(ticker)
                    // Continuous use is broken by locking the phone, so reminders start over.
                    Session.nextReminderAt.clear()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val now = System.currentTimeMillis()
                    if (!screenOn) {
                        screenOn = true
                        Session.sessionStart = now
                        Session.foregroundPkg?.let { armReminder(it, now) }
                    }
                    handler.removeCallbacks(ticker)
                    handler.postDelayed(ticker, 1_000L)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Store.init(applicationContext)
        Apps.init(applicationContext)
        ignored = computeIgnored()
        homePackages = computeHomes()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MS)
    }

    private fun computeIgnored(): Set<String> {
        val set = hashSetOf("android", "com.android.systemui")
        try {
            getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { set += it.packageName }
        } catch (_: Exception) {
        }
        return set
    }

    /** Every installed home-screen app (vivo's own launcher included), so leaving to it ends the app session. */
    private fun computeHomes(): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
    } catch (_: Exception) {
        emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        lastWindowPkg = pkg
        try {
            if (pkg == packageName) {
                onOwnWindow(event.className?.toString().orEmpty())
                return
            }
            if (pkg in homePackages) {
                onOtherHome(pkg)
                return
            }
            if (!Apps.isLaunchable(pkg)) return
            if (pkg == Session.foregroundPkg) return
            onAppForeground(pkg)
        } catch (e: Exception) {
            Log.w(TAG, "event handling failed", e)
        }
    }

    private fun onOwnWindow(className: String) {
        val now = System.currentTimeMillis()
        if (".gate." in className) {
            // A gate is on top of the app; the app's session continues afterwards.
            Session.foregroundPkg = packageName
            Session.foregroundSince = now
            return
        }
        // The user is back on the focUS home screen: any app session has ended.
        Session.foregroundPkg = packageName
        Session.foregroundSince = now
        Session.gatePkg = null
        Session.nextReminderAt.clear()
    }

    private fun onOtherHome(pkg: String) {
        Session.foregroundPkg = pkg
        Session.foregroundSince = System.currentTimeMillis()
        Session.gatePkg = null
        Session.nextReminderAt.clear()
    }

    /**
     * Sends the user Home first, then shows the gate from there. Going Home always works, and with
     * focUS in front Android (and vivo's pop-up restriction) allows the gate screen to open.
     * Because the app is no longer underneath, the gate re-opens it if the user chooses to continue.
     */
    private fun showGate(pkg: String, d: Decision) {
        Session.gatePkg = pkg
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            try {
                when (d) {
                    is Decision.Block -> Gate.showBlock(this, pkg, fromLauncher = true)
                    is Decision.Pause -> Gate.showPause(this, pkg, d.seconds, fromLauncher = true)
                    Decision.Open -> {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not show gate", e)
            }
        }, 150L)
    }

    private fun onAppForeground(pkg: String) {
        val now = System.currentTimeMillis()
        val returningFromGate = Session.gatePkg == pkg
        if (!returningFromGate) {
            Session.sessionStart = now
            Session.nextReminderAt.keys.filter { it != pkg }.forEach { Session.nextReminderAt.remove(it) }
            Session.nextReminderAt.remove(pkg)
        }
        Session.foregroundPkg = pkg
        Session.foregroundSince = now
        Session.gatePkg = null

        when (val d = Launch.decide(this, pkg, now)) {
            is Decision.Block, is Decision.Pause -> showGate(pkg, d)
            Decision.Open -> armReminder(pkg, now)
        }
    }

    private fun armReminder(pkg: String, now: Long) {
        val minutes = Store.value.reminders[pkg] ?: 0
        if (minutes <= 0) {
            Session.nextReminderAt.remove(pkg)
            return
        }
        val existing = Session.nextReminderAt[pkg]
        if (existing == null || existing <= now) {
            Session.nextReminderAt[pkg] = now + minutes * RulesEngine.MINUTE
        }
    }

    private fun tick() {
        val pkg = Session.foregroundPkg ?: return
        if (pkg == packageName || pkg in homePackages) return
        // Only act while the user is actually inside this app (not in a system screen or another launcher).
        if (lastWindowPkg != pkg) return
        val now = System.currentTimeMillis()
        val state = Store.value

        // A launch-pause session the user chose has run out.
        val grant = Session.grants[pkg]
        if (grant != null && grant <= now && (state.pauses[pkg] ?: 0) > 0) {
            Session.grants.remove(pkg)
            Session.gatePkg = pkg
            Gate.showReminder(this, pkg, Gate.KIND_SESSION, (now - Session.sessionStart) / RulesEngine.MINUTE)
            return
        }

        // A schedule started or a daily limit was reached while the app was open.
        val d = Launch.decide(this, pkg, now)
        if (d is Decision.Block) {
            showGate(pkg, d)
            return
        }

        // In-app reminder.
        val next = Session.nextReminderAt[pkg]
        if ((state.reminders[pkg] ?: 0) > 0) {
            if (next == null) {
                armReminder(pkg, now)
            } else if (now >= next) {
                Session.nextReminderAt[pkg] = Long.MAX_VALUE
                Session.gatePkg = pkg
                Gate.showReminder(this, pkg, Gate.KIND_INTERVAL, (now - Session.sessionStart) / RulesEngine.MINUTE)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacks(ticker)
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        if (instance === this) instance = null
    }
}
