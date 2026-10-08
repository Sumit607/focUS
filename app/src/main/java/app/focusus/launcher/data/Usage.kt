package app.focusus.launcher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.focusus.launcher.core.RulesEngine
import app.focusus.launcher.system.Permissions
import java.time.LocalDate
import java.time.ZoneId

/** Screen-time numbers computed on the phone from Android's own usage events. */
object Usage {
    private const val RESUMED = 1        // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2         // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val STOPPED = 23       // ACTIVITY_STOPPED
    private const val SCREEN_OFF = 16    // SCREEN_NON_INTERACTIVE
    private const val KEYGUARD_HIDDEN = 18
    private const val SHUTDOWN = 26

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Foreground milliseconds per package between [start] and [end]. Excludes focUS itself. */
    fun foregroundTimes(context: Context, start: Long, end: Long): Map<String, Long> {
        if (!Permissions.hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val events = try {
            usm.queryEvents(start, end)
        } catch (_: Exception) {
            return emptyMap()
        } ?: return emptyMap()

        val totals = HashMap<String, Long>()
        val open = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val seen = HashSet<String>()
        val e = UsageEvents.Event()

        fun close(pkg: String, at: Long) {
            val s = since.remove(pkg) ?: return
            if (at > s) totals[pkg] = (totals[pkg] ?: 0L) + (at - s)
            open.remove(pkg)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val ts = e.timeStamp
            when (e.eventType) {
                RESUMED -> {
                    val set = open.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) since[pkg] = ts
                    set += e.className ?: ""
                    seen += pkg
                }
                PAUSED, STOPPED -> {
                    val set = open[pkg]
                    if (set == null || set.isEmpty()) {
                        // Opened before the window started.
                        if (pkg !in seen && e.eventType == PAUSED) {
                            totals[pkg] = (totals[pkg] ?: 0L) + (ts - start).coerceAtLeast(0)
                        }
                    } else {
                        set.remove(e.className ?: "")
                        if (set.isEmpty()) close(pkg, ts)
                    }
                    seen += pkg
                }
                SCREEN_OFF, SHUTDOWN -> {
                    open.keys.toList().forEach { close(it, ts) }
                }
            }
        }
        open.keys.toList().forEach { close(it, end) }
        totals.remove(context.packageName)
        return totals
    }

    fun today(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> =
        foregroundTimes(context, RulesEngine.startOfDay(now, zone), now)

    fun todayTotal(context: Context): Long = today(context).values.sum()

    fun unlocksToday(context: Context, now: Long = System.currentTimeMillis()): Int {
        if (!Permissions.hasUsageAccess(context)) return 0
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return 0
        val events = try {
            usm.queryEvents(RulesEngine.startOfDay(now, zone), now)
        } catch (_: Exception) {
            return 0
        } ?: return 0
        val e = UsageEvents.Event()
        var n = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == KEYGUARD_HIDDEN) n++
        }
        return n
    }

    /** Total screen time per day for the last [days] days, oldest first. */
    fun dailyTotals(context: Context, days: Int = 7): List<Pair<LocalDate, Long>> {
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            d to foregroundTimes(context, start, end).values.sum()
        }
    }

    fun lastDays(context: Context, days: Int = 7): Map<String, Long> {
        val now = System.currentTimeMillis()
        return foregroundTimes(context, now - days * RulesEngine.DAY, now)
    }
}

/** Short-lived cache of today's per-app usage, used when checking daily limits. */
object UsageCache {
    private const val TTL = 60_000L
    @Volatile private var at = 0L
    @Volatile private var map: Map<String, Long> = emptyMap()

    fun usedToday(context: Context, pkg: String, now: Long = System.currentTimeMillis()): Long {
        if (now - at > TTL || RulesEngine.startOfDay(now, ZoneId.systemDefault()) > at) {
            map = Usage.today(context, now)
            at = now
        }
        return map[pkg] ?: 0L
    }

    fun invalidate() {
        at = 0L
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h h $m m"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}
