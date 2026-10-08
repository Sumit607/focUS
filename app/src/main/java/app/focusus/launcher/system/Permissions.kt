package app.focusus.launcher.system

import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.focusus.launcher.service.FocusAccessibilityService

/** Checks and shortcuts for every permission focUS can use. All are optional except being the home app. */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityOn(context: Context): Boolean {
        if (FocusAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** The service is switched on in Settings but Android is not running it (often a battery manager). */
    fun isAccessibilityStalled(context: Context): Boolean =
        FocusAccessibilityService.instance == null && isAccessibilityOn(context)

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    // ---- Shortcuts into Settings ----

    fun openUsageAccess(context: Context) =
        open(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openAccessibility(context: Context) =
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationAccess(context: Context) =
        open(context, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    fun openAppInfo(context: Context, pkg: String = context.packageName) =
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun openBatterySettings(context: Context) =
        open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /** vivo / iQOO keep their background-start list in their own security app; try the known screens. */
    fun openVivoBackgroundSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        for (c in candidates) {
            try {
                context.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        openAppInfo(context)
        return false
    }

    fun isVivo(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return "vivo" in m || "iqoo" in m
    }

    /** Opens the system "choose home app" flow. */
    fun requestHomeRole(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7)
                    return
                } catch (_: Exception) {
                }
            }
        }
        openHomeSettings(activity)
    }

    fun openHomeSettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_HOME_SETTINGS))) {
            if (!tryStart(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
                tryStart(context, Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    fun open(context: Context, intent: Intent) {
        if (!tryStart(context, intent)) tryStart(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun tryStart(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
