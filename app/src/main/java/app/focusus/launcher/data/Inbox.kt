package app.focusus.launcher.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable
data class InboxItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
)

/** Notifications the filter held back. Private to the phone and kept for 24 hours at most. */
object Inbox {
    private const val KEEP_MS = 24 * 60 * 60 * 1000L
    private const val MAX_ITEMS = 300
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(InboxItem.serializer())
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()
    private var file: AtomicFile? = null
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "focus-inbox").apply { isDaemon = true } }

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = AtomicFile(File(context.filesDir, "inbox.json"))
        file = f
        _items.value = try {
            json.decodeFromString(serializer, f.readFully().decodeToString()).filter(::fresh)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fresh(i: InboxItem) = System.currentTimeMillis() - i.time < KEEP_MS

    fun add(item: InboxItem) {
        _items.update { list ->
            (listOf(item) + list.filter { it.key != item.key }).filter(::fresh).take(MAX_ITEMS)
        }
        save()
    }

    fun remove(key: String) {
        _items.update { list -> list.filter { it.key != key } }
        save()
    }

    fun removeApp(pkg: String) {
        _items.update { list -> list.filter { it.pkg != pkg } }
        save()
    }

    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = _items.value
        io.execute {
            val out = try {
                f.startWrite()
            } catch (_: Exception) {
                return@execute
            }
            try {
                out.write(json.encodeToString(serializer, snapshot).encodeToByteArray())
                f.finishWrite(out)
            } catch (_: Exception) {
                f.failWrite(out)
            }
        }
    }
}
