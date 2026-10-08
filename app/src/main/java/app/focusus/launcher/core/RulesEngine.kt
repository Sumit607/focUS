package app.focusus.launcher.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Why an app is blocked right now. */
sealed interface BlockReason {
    data class Manual(val block: ManualBlock) : BlockReason
    data class ScheduleActive(val schedule: Schedule, val endsAt: Long) : BlockReason
    data class LimitReached(val limitMin: Int, val usedMin: Long) : BlockReason
}

/** What should happen when an app is about to open or is in front. */
sealed interface Decision {
    data object Open : Decision
    data class Pause(val seconds: Int) : Decision
    data class Block(val reason: BlockReason, val until: Long, val friction: Friction) : Decision
}

/**
 * The single place that decides whether an app may open. Pure Kotlin (only java.time),
 * so every rule is covered by plain JVM unit tests.
 */
object RulesEngine {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
    const val LIMIT_EXTENSION_MIN = 15

    fun decide(
        pkg: String,
        state: FocusState,
        now: Long,
        zone: ZoneId,
        essentials: Set<String>,
        grantUntil: Long = 0L,
        usedTodayMs: Long? = null,
    ): Decision {
        // Calls, SMS, Settings and focUS itself are never blocked.
        if (pkg in essentials) return Decision.Open

        // 1. Blocks started by hand (and focus sessions).
        val manual = state.blocks
            .filter { it.until > now && pkg in it.apps }
            .maxWithOrNull(compareBy<ManualBlock>({ it.friction.ordinal }, { it.until }))
        if (manual != null) {
            return Decision.Block(BlockReason.Manual(manual), manual.until, manual.friction)
        }

        val exempt = (state.allowUntil[pkg] ?: 0L) > now
        if (!exempt) {
            // 2. Recurring schedules.
            val active = activeSchedules(state.schedules, now, zone).filter { pkg in it.first.apps }
            if (active.isNotEmpty()) {
                val (schedule, endsAt) = active.maxWith(
                    compareBy<Pair<Schedule, Long>>({ it.first.friction.ordinal }, { it.second })
                )
                return Decision.Block(BlockReason.ScheduleActive(schedule, endsAt), endsAt, schedule.friction)
            }

            // 3. Daily limits.
            val limit = state.limits[pkg] ?: 0
            if (limit > 0 && usedTodayMs != null && usedTodayMs >= limit * MINUTE) {
                return Decision.Block(
                    BlockReason.LimitReached(limit, usedTodayMs / MINUTE),
                    endOfDay(now, zone),
                    state.prefs.defaultFriction,
                )
            }
        }

        // 4. Mindful launch pause, unless a session was already granted.
        val pause = state.pauses[pkg] ?: 0
        if (pause > 0 && grantUntil <= now) return Decision.Pause(pause)

        return Decision.Open
    }

    /** Schedules running at [now], each paired with the moment it ends. */
    fun activeSchedules(schedules: List<Schedule>, now: Long, zone: ZoneId): List<Pair<Schedule, Long>> {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val minute = t.hour * 60 + t.minute
        val dow = t.dayOfWeek.value
        val prevDow = if (dow == 1) 7 else dow - 1
        val today = t.toLocalDate()
        val result = mutableListOf<Pair<Schedule, Long>>()
        for (s in schedules) {
            if (!s.enabled || s.apps.isEmpty() || s.days.isEmpty() || s.startMin == s.endMin) continue
            if (s.startMin < s.endMin) {
                if (dow in s.days && minute >= s.startMin && minute < s.endMin) {
                    result += s to today.atStartOfDay(zone).plusMinutes(s.endMin.toLong()).toInstant().toEpochMilli()
                }
            } else {
                // Overnight, e.g. 22:00 -> 07:00.
                if (dow in s.days && minute >= s.startMin) {
                    result += s to today.plusDays(1).atStartOfDay(zone).plusMinutes(s.endMin.toLong()).toInstant().toEpochMilli()
                } else if (prevDow in s.days && minute < s.endMin) {
                    result += s to today.atStartOfDay(zone).plusMinutes(s.endMin.toLong()).toInstant().toEpochMilli()
                }
            }
        }
        return result
    }

    fun isScheduleActive(s: Schedule, now: Long, zone: ZoneId): Boolean =
        activeSchedules(listOf(s), now, zone).isNotEmpty()

    /** A strict schedule cannot be edited, paused or deleted while it runs. */
    fun isScheduleLocked(s: Schedule, now: Long, zone: ZoneId): Boolean =
        s.friction == Friction.STRICT && isScheduleActive(s, now, zone)

    fun canUseEmergency(state: FocusState, now: Long): Boolean = now - state.emergencyUsedAt >= DAY

    fun startOfDay(now: Long, zone: ZoneId): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(now: Long, zone: ZoneId): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate().plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Returns the state after unblocking [pkg] for the given block decision.
     * Manual blocks drop the app; schedules and limits get a temporary exemption.
     */
    fun unblock(state: FocusState, pkg: String, block: Decision.Block, now: Long): FocusState =
        when (val r = block.reason) {
            is BlockReason.Manual -> state.copy(
                blocks = state.blocks.mapNotNull { b ->
                    if (b.id != r.block.id) b
                    else {
                        val left = b.apps - pkg
                        if (left.isEmpty()) null else b.copy(apps = left)
                    }
                }
            )
            is BlockReason.ScheduleActive -> state.copy(allowUntil = state.allowUntil + (pkg to r.endsAt))
            is BlockReason.LimitReached -> state.copy(
                allowUntil = state.allowUntil + (pkg to now + LIMIT_EXTENSION_MIN * MINUTE)
            )
        }

    /** Drops finished blocks and expired exemptions so the saved state stays small. */
    fun prune(state: FocusState, now: Long): FocusState {
        val blocks = state.blocks.filter { it.until > now }
        val allow = state.allowUntil.filterValues { it > now }
        return if (blocks.size == state.blocks.size && allow.size == state.allowUntil.size) state
        else state.copy(blocks = blocks, allowUntil = allow)
    }
}
