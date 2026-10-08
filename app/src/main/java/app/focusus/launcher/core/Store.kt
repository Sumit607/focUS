package app.focusus.launcher.core

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single source of truth for everything focUS remembers.
 * Held in memory as a StateFlow and written to one private JSON file shortly after each change.
 */
object Store {
    private const val TAG = "FocusStore"
    private const val STATS_DAYS = 35

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    val value: FocusState get() = _state.value

    private lateinit var file: AtomicFile
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "focus-store").apply { isDaemon = true } }
    private val saveQueued = AtomicBoolean(false)
    @Volatile private var ready = false

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        file = AtomicFile(File(context.filesDir, "focus_state.json"))
        _state.value = load()
        ready = true
    }

    private fun load(): FocusState = try {
        val text = file.readFully().decodeToString()
        json.decodeFromString(FocusState.serializer(), text)
    } catch (e: FileNotFoundException) {
        FocusState()
    } catch (e: Exception) {
        Log.w(TAG, "State unreadable, starting fresh", e)
        try {
            file.baseFile.copyTo(File(file.baseFile.parentFile, "focus_state.broken.json"), overwrite = true)
        } catch (_: Exception) {
        }
        FocusState()
    }

    fun update(transform: (FocusState) -> FocusState) {
        val now = System.currentTimeMillis()
        _state.update { RulesEngine.prune(transform(it), now) }
        queueSave()
    }

    private fun queueSave() {
        if (!ready) return
        if (saveQueued.compareAndSet(false, true)) {
            io.execute {
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                }
                saveQueued.set(false)
                write(_state.value)
            }
        }
    }

    private fun write(s: FocusState) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open state file", e)
            return
        }
        try {
            out.write(json.encodeToString(FocusState.serializer(), s).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            Log.e(TAG, "Saving state failed", e)
            file.failWrite(out)
        }
    }

    fun exportJson(): String = json.encodeToString(FocusState.serializer(), value)

    fun importJson(text: String): Boolean = try {
        val imported = json.decodeFromString(FocusState.serializer(), text)
        update { imported.copy(onboarded = true) }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Import failed", e)
        false
    }

    fun reset() {
        update { FocusState() }
    }

    fun todayKey(): String = LocalDate.now().toString()

    /** Adds to today's counters and keeps only the last few weeks. */
    fun bump(change: (DayStats) -> DayStats) {
        val key = todayKey()
        update { s ->
            val m = s.stats.toMutableMap()
            m[key] = change(m[key] ?: DayStats())
            if (m.size > STATS_DAYS) {
                m.keys.sorted().take(m.size - STATS_DAYS).forEach { m.remove(it) }
            }
            s.copy(stats = m)
        }
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
}

/** Runtime-only state shared by the service and the gate screens. Lost on restart by design. */
object Session {
    /** Launch-pause sessions: package -> allowed until (millis). */
    val grants = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Next in-app reminder per package (millis). */
    val nextReminderAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile var foregroundPkg: String? = null
    @Volatile var foregroundSince: Long = 0L
}
