package app.focusus.launcher.core

import kotlinx.serialization.Serializable

/** How hard it is to undo a block before it ends. */
@Serializable
enum class Friction { EASY, MEDIUM, STRICT }

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
enum class HomeAlign { LEFT, CENTER, RIGHT }

@Serializable
enum class ClockStyle { AUTO, H24, H12 }

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
)

/** A block the user started by hand (or a focus session). */
@Serializable
data class ManualBlock(
    val id: String,
    val apps: Set<String>,
    val until: Long,
    val friction: Friction = Friction.MEDIUM,
    val label: String = "",
)

/** A recurring block. Times are minutes after midnight; days are ISO (1 = Monday ... 7 = Sunday). */
@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val apps: Set<String> = emptySet(),
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startMin: Int = 9 * 60,
    val endMin: Int = 17 * 60,
    val enabled: Boolean = true,
    val friction: Friction = Friction.MEDIUM,
)

@Serializable
data class NotifSettings(
    val enabled: Boolean = false,
    val allow: Set<String> = emptySet(),
)

@Serializable
data class Appearance(
    val theme: ThemeMode = ThemeMode.DARK,
    val textScale: Float = 1.0f,
    val align: HomeAlign = HomeAlign.LEFT,
    val clock: ClockStyle = ClockStyle.AUTO,
    val showBudget: Boolean = true,
)

@Serializable
data class Prefs(
    val dailyBudgetMin: Int = 120,
    val autoLaunchSingleMatch: Boolean = false,
    val keyboardOnDrawer: Boolean = true,
    val doubleTapLock: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val appLock: Boolean = false,
    val defaultFriction: Friction = Friction.MEDIUM,
    val searchEngine: String = "google",
)

@Serializable
data class DayStats(
    val blocksShown: Int = 0,
    val pausesShown: Int = 0,
    val pausesCancelled: Int = 0,
    val remindersShown: Int = 0,
    val remindersExited: Int = 0,
    val reasons: Map<String, Int> = emptyMap(),
)

/** Everything focUS remembers. Stored as one JSON file in the app's private storage. */
@Serializable
data class FocusState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    /** Package names, or "folder:<id>" for a folder. */
    val favorites: List<String> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    /** Seconds of mindful pause before the app opens. */
    val pauses: Map<String, Int> = emptyMap(),
    /** Minutes between in-app reminders. */
    val reminders: Map<String, Int> = emptyMap(),
    /** Daily limit in minutes. */
    val limits: Map<String, Int> = emptyMap(),
    val blocks: List<ManualBlock> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    /** Apps the user considers distracting; used for focus sessions and suggestions. */
    val distractions: Set<String> = emptySet(),
    /** Temporary exemptions from schedules and limits (package -> until millis). */
    val allowUntil: Map<String, Long> = emptyMap(),
    val notif: NotifSettings = NotifSettings(),
    val appearance: Appearance = Appearance(),
    val prefs: Prefs = Prefs(),
    /** Keyed by ISO date, e.g. 2026-10-08. */
    val stats: Map<String, DayStats> = emptyMap(),
    val emergencyUsedAt: Long = 0,
)
