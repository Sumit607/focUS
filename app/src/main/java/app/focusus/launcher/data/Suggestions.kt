package app.focusus.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/** Starting suggestions for onboarding. Only apps actually installed are offered. */
object Suggestions {
    val knownDistractions = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically",
        "in.mohalla.video",
        "in.mohalla.sharechat",
        "com.eterno.shortvideos",
        "com.roposo.android",
        "com.netflix.mediaclient",
        "in.startv.hotstar",
        "com.amazon.avod.thirdpartyclient",
        "com.jio.media.ondemand",
        "com.linkedin.android",
        "com.pinterest",
        "tv.twitch.android.app",
        "com.discord",
        "com.dts.freefireth",
        "com.pubg.imobile",
        "com.supercell.clashofclans",
        "com.king.candycrushsaga",
    )

    /** Messaging and payment apps people usually want to keep notifications from. */
    val keepNotifications = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.gm",
        "com.google.android.calendar",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping",
        "com.google.android.apps.maps",
        "com.ubercab",
        "com.olacabs.customer",
        "in.swiggy.android",
        "com.application.zomato",
    )

    fun distractions(): List<String> = knownDistractions.filter { Apps.isLaunchable(it) }

    fun notificationAllow(context: Context): Set<String> =
        keepNotifications.filter { Apps.isLaunchable(it) }.toSet() + listOfNotNull(
            try { Telephony.Sms.getDefaultSmsPackage(context) } catch (_: Exception) { null },
        )

    fun favorites(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        try {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it }
        } catch (_: Exception) {
        }
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { out += it }
        } catch (_: Exception) {
        }
        listOf("com.whatsapp", "com.google.android.apps.maps").forEach { out += it }
        try {
            context.packageManager.resolveActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
                ?.activityInfo?.packageName?.let { out += it }
        } catch (_: Exception) {
        }
        listOf("com.google.android.calendar", "com.google.android.gm").forEach { out += it }
        return out.filter { Apps.isLaunchable(it) }.take(5)
    }
}
