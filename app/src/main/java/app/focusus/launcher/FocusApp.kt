package app.focusus.launcher

import android.app.Application
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Apps
import app.focusus.launcher.data.Inbox

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Inbox.init(this)
        Apps.init(this)
    }
}
