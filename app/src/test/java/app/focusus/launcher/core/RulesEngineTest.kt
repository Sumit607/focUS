package app.focusus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RulesEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val essentials = setOf("com.android.dialer", "app.focusus.launcher")

    /** 2026-10-08 is a Thursday (ISO day 4). */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val work = Schedule(
        id = "w", name = "Work", apps = setOf("insta"), days = setOf(1, 2, 3, 4, 5),
        startMin = 9 * 60, endMin = 17 * 60,
    )
    private val sleep = Schedule(
        id = "s", name = "Sleep", apps = setOf("yt"), days = setOf(4),
        startMin = 22 * 60, endMin = 7 * 60, friction = Friction.STRICT,
    )

    @Test fun openWhenNoRules() {
        val d = RulesEngine.decide("insta", FocusState(), at(8, 10), zone, essentials)
        assertEquals(Decision.Open, d)
    }

    @Test fun essentialsAreNeverBlocked() {
        val state = FocusState(
            blocks = listOf(ManualBlock("b", setOf("com.android.dialer"), at(9, 0), Friction.STRICT)),
            pauses = mapOf("com.android.dialer" to 10),
        )
        assertEquals(Decision.Open, RulesEngine.decide("com.android.dialer", state, at(8, 10), zone, essentials))
    }

    @Test fun weekdayScheduleBlocksInsideWindowOnly() {
        val state = FocusState(schedules = listOf(work))
        val inside = RulesEngine.decide("insta", state, at(8, 10), zone, essentials)
        assertTrue(inside is Decision.Block)
        assertEquals(at(8, 17), (inside as Decision.Block).until)
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 17), zone, essentials))
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 8, 59), zone, essentials))
        // 2026-10-10 is a Saturday.
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(10, 10), zone, essentials))
        // Other apps are unaffected.
        assertEquals(Decision.Open, RulesEngine.decide("maps", state, at(8, 10), zone, essentials))
    }

    @Test fun overnightScheduleCoversBothSidesOfMidnight() {
        val state = FocusState(schedules = listOf(sleep))
        val late = RulesEngine.decide("yt", state, at(8, 23), zone, essentials)
        assertTrue(late is Decision.Block)
        assertEquals(at(9, 7), (late as Decision.Block).until)
        // Friday 06:30 belongs to Thursday's session.
        assertTrue(RulesEngine.decide("yt", state, at(9, 6, 30), zone, essentials) is Decision.Block)
        // Friday 23:00 is not scheduled (only Thursday starts).
        assertEquals(Decision.Open, RulesEngine.decide("yt", state, at(9, 23), zone, essentials))
        // Thursday 06:30 belongs to Wednesday, which is not scheduled.
        assertEquals(Decision.Open, RulesEngine.decide("yt", state, at(8, 6, 30), zone, essentials))
    }

    @Test fun disabledOrEmptySchedulesDoNothing() {
        val state = FocusState(schedules = listOf(work.copy(enabled = false), work.copy(id = "x", apps = emptySet())))
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 10), zone, essentials))
    }

    @Test fun manualBlockWinsAndPicksStrictest() {
        val state = FocusState(
            blocks = listOf(
                ManualBlock("a", setOf("insta"), at(8, 12), Friction.EASY),
                ManualBlock("b", setOf("insta"), at(8, 11), Friction.STRICT),
                ManualBlock("old", setOf("insta"), at(8, 9), Friction.STRICT),
            ),
            schedules = listOf(work),
        )
        val d = RulesEngine.decide("insta", state, at(8, 10), zone, essentials) as Decision.Block
        assertEquals(Friction.STRICT, d.friction)
        assertTrue(d.reason is BlockReason.Manual)
        assertEquals("b", (d.reason as BlockReason.Manual).block.id)
    }

    @Test fun dailyLimitBlocksOnceReached() {
        val state = FocusState(limits = mapOf("insta" to 30))
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 10), zone, essentials, usedTodayMs = 29 * RulesEngine.MINUTE))
        val d = RulesEngine.decide("insta", state, at(8, 10), zone, essentials, usedTodayMs = 30 * RulesEngine.MINUTE)
        assertTrue(d is Decision.Block)
        assertEquals(at(9, 0), (d as Decision.Block).until)
        // Unknown usage never blocks.
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 10), zone, essentials, usedTodayMs = null))
    }

    @Test fun pauseUnlessSessionGranted() {
        val state = FocusState(pauses = mapOf("insta" to 10))
        assertEquals(Decision.Pause(10), RulesEngine.decide("insta", state, at(8, 10), zone, essentials))
        assertEquals(Decision.Open, RulesEngine.decide("insta", state, at(8, 10), zone, essentials, grantUntil = at(8, 10, 5)))
        assertEquals(Decision.Pause(10), RulesEngine.decide("insta", state, at(8, 10, 6), zone, essentials, grantUntil = at(8, 10, 5)))
    }

    @Test fun unblockScheduleExemptsUntilScheduleEnds() {
        val state = FocusState(schedules = listOf(work))
        val d = RulesEngine.decide("insta", state, at(8, 10), zone, essentials) as Decision.Block
        val after = RulesEngine.unblock(state, "insta", d, at(8, 10))
        assertEquals(Decision.Open, RulesEngine.decide("insta", after, at(8, 16), zone, essentials))
        // The next day the schedule applies again.
        assertTrue(RulesEngine.decide("insta", after, at(9, 10), zone, essentials) is Decision.Block)
    }

    @Test fun unblockManualRemovesOnlyThatApp() {
        val state = FocusState(blocks = listOf(ManualBlock("a", setOf("insta", "yt"), at(8, 12))))
        val d = RulesEngine.decide("insta", state, at(8, 10), zone, essentials) as Decision.Block
        val after = RulesEngine.unblock(state, "insta", d, at(8, 10))
        assertEquals(Decision.Open, RulesEngine.decide("insta", after, at(8, 10), zone, essentials))
        assertTrue(RulesEngine.decide("yt", after, at(8, 10), zone, essentials) is Decision.Block)
        val after2 = RulesEngine.unblock(after, "yt", RulesEngine.decide("yt", after, at(8, 10), zone, essentials) as Decision.Block, at(8, 10))
        assertTrue(after2.blocks.isEmpty())
    }

    @Test fun limitUnblockGivesFifteenMinutes() {
        val state = FocusState(limits = mapOf("insta" to 30))
        val used = 40 * RulesEngine.MINUTE
        val d = RulesEngine.decide("insta", state, at(8, 10), zone, essentials, usedTodayMs = used) as Decision.Block
        val after = RulesEngine.unblock(state, "insta", d, at(8, 10))
        assertEquals(Decision.Open, RulesEngine.decide("insta", after, at(8, 10, 14), zone, essentials, usedTodayMs = used))
        assertTrue(RulesEngine.decide("insta", after, at(8, 10, 16), zone, essentials, usedTodayMs = used) is Decision.Block)
    }

    @Test fun strictScheduleIsLockedOnlyWhileActive() {
        assertTrue(RulesEngine.isScheduleLocked(sleep, at(8, 23), zone))
        assertFalse(RulesEngine.isScheduleLocked(sleep, at(8, 21), zone))
        assertFalse(RulesEngine.isScheduleLocked(work, at(8, 10), zone))
    }

    @Test fun emergencyUnlockOncePerDay() {
        val now = at(8, 10)
        assertTrue(RulesEngine.canUseEmergency(FocusState(), now))
        assertFalse(RulesEngine.canUseEmergency(FocusState(emergencyUsedAt = now - RulesEngine.HOUR), now))
        assertTrue(RulesEngine.canUseEmergency(FocusState(emergencyUsedAt = now - RulesEngine.DAY), now))
    }

    @Test fun pruneDropsExpiredEntries() {
        val now = at(8, 10)
        val state = FocusState(
            blocks = listOf(ManualBlock("a", setOf("x"), now - 1), ManualBlock("b", setOf("y"), now + 1)),
            allowUntil = mapOf("x" to now - 1, "y" to now + 1),
        )
        val p = RulesEngine.prune(state, now)
        assertEquals(listOf("b"), p.blocks.map { it.id })
        assertEquals(setOf("y"), p.allowUntil.keys)
    }
}
