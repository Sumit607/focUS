package app.focusus.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.focusus.launcher.core.Store
import app.focusus.launcher.data.Essentials
import app.focusus.launcher.data.Inbox
import app.focusus.launcher.data.InboxItem

/**
 * Hides notifications from apps not on the allowed list and keeps them in the focUS inbox for 24 hours.
 * Calls, alarms, navigation, media and anything ongoing always pass.
 */
class NotificationFilterService : NotificationListenerService() {

    companion object {
        private const val TAG = "FocusNotif"

        @Volatile
        var instance: NotificationFilterService? = null
            private set

        private val ALWAYS_PASS = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_EVENT,
            "missed_call",
            "stopwatch",
        )

        /** Applies the filter to notifications already in the shade (after turning it on). */
        fun refilter() {
            instance?.filterExisting()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Store.init(applicationContext)
        Inbox.init(applicationContext)
        filterExisting()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.w(TAG, "filter failed", e)
        }
    }

    fun filterExisting() {
        if (!Store.value.notif.enabled) return
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
            Log.w(TAG, "refilter failed", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val settings = Store.value.notif
        if (!settings.enabled) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg in settings.allow || pkg in Essentials.get(this)) return
        if (sbn.isOngoing || !sbn.isClearable) return
        val n = sbn.notification ?: return
        if (n.category in ALWAYS_PASS) return
        if ((n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (!isSummary && (title.isNotBlank() || text.isNotBlank())) {
            Inbox.add(InboxItem(sbn.key, pkg, title.take(200), text.take(600), sbn.postTime))
        }
        cancelNotification(sbn.key)
    }
}
