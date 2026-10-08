package app.focusus.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager

/** Apps focUS will never block, pause or limit: calls, SMS, Settings and focUS itself. */
object Essentials {
    @Volatile private var cached: Set<String> = emptySet()

    private val fixed = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.vivo.settings",
        "com.android.incallui",
    )

    fun get(context: Context): Set<String> {
        if (cached.isEmpty()) refresh(context)
        return cached
    }

    fun refresh(context: Context) {
        val set = HashSet(fixed)
        set += context.packageName
        try {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        } catch (_: Exception) {
        }
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { set += it }
        } catch (_: Exception) {
        }
        try {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
                .forEach { set += it.activityInfo.packageName }
        } catch (_: Exception) {
        }
        cached = set
    }
}
