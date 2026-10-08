package app.focusus.launcher.core

import android.content.Context
import android.content.Intent
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.data.UsageCache
import app.focusus.launcher.gate.BlockActivity
import app.focusus.launcher.gate.PauseActivity
import app.focusus.launcher.gate.ReminderActivity
import java.time.ZoneId

/** Every app open from focUS goes through here, so the rules apply no matter where the tap came from. */
object Launch {

    fun decide(context: Context, pkg: String, now: Long = System.currentTimeMillis()): Decision {
        val state = Store.value
        val used = if ((state.limits[pkg] ?: 0) > 0) UsageCache.usedToday(context, pkg, now) else null
        return RulesEngine.decide(
            pkg = pkg,
            state = state,
            now = now,
            zone = ZoneId.systemDefault(),
            essentials = Essentials.get(context),
            grantUntil = Session.grants[pkg] ?: 0L,
            usedTodayMs = used,
        )
    }

    /** Opens an app, or shows the pause / block screen instead. */
    fun open(context: Context, pkg: String) {
        when (val d = decide(context, pkg)) {
            is Decision.Open -> Apps.start(context, pkg)
            is Decision.Pause -> Gate.showPause(context, pkg, d.seconds, fromLauncher = true)
            is Decision.Block -> Gate.showBlock(context, pkg, fromLauncher = true)
        }
    }
}

/** Starts the full-screen gate screens. Only one gate shows at a time. */
object Gate {
    const val EXTRA_PKG = "pkg"
    const val EXTRA_FROM_LAUNCHER = "fromLauncher"
    const val EXTRA_SECONDS = "seconds"
    const val EXTRA_KIND = "kind"
    const val EXTRA_MINUTES = "minutes"
    const val KIND_INTERVAL = "interval"
    const val KIND_SESSION = "session"

    private fun flags(i: Intent) = i.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
    )

    fun showBlock(context: Context, pkg: String, fromLauncher: Boolean) {
        context.startActivity(
            flags(Intent(context, BlockActivity::class.java))
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_FROM_LAUNCHER, fromLauncher)
        )
    }

    fun showPause(context: Context, pkg: String, seconds: Int, fromLauncher: Boolean) {
        context.startActivity(
            flags(Intent(context, PauseActivity::class.java))
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_FROM_LAUNCHER, fromLauncher)
        )
    }

    fun showReminder(context: Context, pkg: String, kind: String, minutes: Long) {
        context.startActivity(
            flags(Intent(context, ReminderActivity::class.java))
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_MINUTES, minutes)
        )
    }

    fun goHome(context: Context) {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(home)
        } catch (_: Exception) {
        }
    }
}
