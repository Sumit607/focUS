package app.focusus.launcher.gate

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import app.focusus.launcher.core.Gate
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps

/** Shared plumbing for the block, pause and reminder screens. */
abstract class GateActivity : FragmentActivity() {
    protected var pkg by mutableStateOf("")
    protected var fromLauncher by mutableStateOf(false)
    /** Changes each time a new intent arrives, so screens reset their local state. */
    protected var generation by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Apps.init(applicationContext)
        if (!readIntent(intent)) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
        onShown()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readIntent(intent)) {
            generation++
            onShown()
        }
    }

    private fun readIntent(i: Intent): Boolean {
        val p = i.getStringExtra(Gate.EXTRA_PKG) ?: return false
        pkg = p
        fromLauncher = i.getBooleanExtra(Gate.EXTRA_FROM_LAUNCHER, false)
        readExtras(i)
        return true
    }

    protected open fun readExtras(i: Intent) {}
    protected open fun onShown() {}
    protected abstract fun render()
    protected open fun onBack() = leaveHome()

    fun leaveHome() {
        Gate.goHome(this)
        finish()
    }
}
