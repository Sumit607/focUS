package app.focusus.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import app.focusus.launcher.core.FocusState
import app.focusus.launcher.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

data class AppEntry(
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
)

/** The list of launchable apps, kept fresh as apps are installed, updated or removed. */
object Apps {
    private const val TAG = "FocusApps"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    @Volatile private var byPkg: Map<String, AppEntry> = emptyMap()
    private lateinit var ctx: Context
    @Volatile private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        ctx = context.applicationContext
        val la = ctx.getSystemService(LauncherApps::class.java)
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        }, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        scope.launch {
            val list = try {
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Loading apps failed", e)
                return@launch
            }
            byPkg = list.associateBy { it.pkg }
            _apps.value = list
            Essentials.refresh(ctx)
            cleanUp(list)
        }
    }

    private fun load(): List<AppEntry> {
        val la = ctx.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()
        return la.getActivityList(null, user)
            .asSequence()
            .filter { it.applicationInfo.packageName != ctx.packageName }
            .distinctBy { it.applicationInfo.packageName }
            .map { AppEntry(it.applicationInfo.packageName, it.label?.toString()?.trim().orEmpty().ifEmpty { it.applicationInfo.packageName }, it.componentName, user) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Removes uninstalled apps from favourites, folders and rules. */
    private fun cleanUp(list: List<AppEntry>) {
        if (list.isEmpty()) return
        val installed = list.mapTo(HashSet()) { it.pkg }
        val s = Store.value
        val favOk = s.favorites.all { it.startsWith("folder:") || it in installed }
        val foldersOk = s.folders.all { f -> f.apps.all { it in installed } }
        if (favOk && foldersOk) return
        Store.update { st ->
            st.copy(
                favorites = st.favorites.filter { it.startsWith("folder:") || it in installed },
                folders = st.folders.map { f -> f.copy(apps = f.apps.filter { it in installed }) },
            )
        }
    }

    fun isLaunchable(pkg: String): Boolean = byPkg.containsKey(pkg)

    fun find(pkg: String): AppEntry? = byPkg[pkg]

    fun label(pkg: String, state: FocusState = Store.value): String {
        state.renames[pkg]?.let { return it }
        byPkg[pkg]?.let { return it.label }
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    /** Starts an app directly, without any focUS checks. Use [app.focusus.launcher.core.Launch.open] for normal opens. */
    fun start(context: Context, pkg: String): Boolean {
        val entry = byPkg[pkg]
        if (entry != null) {
            try {
                context.getSystemService(LauncherApps::class.java)
                    .startMainActivity(entry.component, entry.user, null, null)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "LauncherApps start failed for $pkg", e)
            }
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Lower-cased, accent-free text for search. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}
