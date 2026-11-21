package com.zirakusa.reloj.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** «Sonido de la alarma»: silence, the phone's default alarm tone, another tone of the phone, or one the user added. */
enum class SoundKind(val key: String) {
    SILENT("silent"), DEFAULT("default"), SYSTEM("system"), CUSTOM("custom");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT }
}

/** A sound: [uri] is a content:// tone of the phone or a file:// copy in the app's own folder; [name] is shown. */
data class Sound(val kind: SoundKind, val uri: String = "", val name: String = "") {
    fun json(): JSONObject = JSONObject().put("kind", kind.key).put("uri", uri).put("name", name)
    companion object {
        val DEFAULT = Sound(SoundKind.DEFAULT)
        val SILENT = Sound(SoundKind.SILENT)
        fun parse(o: JSONObject?): Sound = if (o == null) DEFAULT else
            Sound(SoundKind.from(o.optString("kind")), o.optString("uri"), o.optString("name")).let {
                if (it.kind in setOf(SoundKind.SYSTEM, SoundKind.CUSTOM) && it.uri.isBlank()) DEFAULT else it
            }
    }
}

/** «Vibrar mientras suena»: our own patterns (milliseconds off, on, off, on…; repeated while ringing). */
enum class Vibration(val key: String, val pattern: LongArray) {
    STANDARD("standard", longArrayOf(0, 800, 800)),
    PULSE("pulse", longArrayOf(0, 200, 200, 200, 900)),
    FAST("fast", longArrayOf(0, 150, 150)),
    HEARTBEAT("heartbeat", longArrayOf(0, 120, 140, 120, 800)),
    WALTZ("waltz", longArrayOf(0, 450, 250, 150, 250, 150, 700)),
    WAVES("waves", longArrayOf(0, 300, 120, 600, 120, 900, 800)),
    URGENT("urgent", longArrayOf(0, 70, 70)),
    NONE("none", longArrayOf());
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: STANDARD }
}

/**
 * One alarm. [days] empty = once (or on [date] when set). [offAfterRing] switches it off once it has rung, even when it
 * repeats. [snoozeTimes] 0 = no «Posponer». [snoozedUntil] (epoch ms) and [snoozed] are the running snooze.
 */
data class Alarm(
    val id: Long = 0, val hour: Int = 7, val minute: Int = 0, val enabled: Boolean = true,
    val days: Set<DayOfWeek> = emptySet(), val date: LocalDate? = null, val offAfterRing: Boolean = false,
    val sound: Sound = Sound.DEFAULT, val label: String = "", val snoozeMinutes: Int = 10, val snoozeTimes: Int = 3,
    val vibration: Vibration = Vibration.STANDARD, val snoozedUntil: Long = 0, val snoozed: Int = 0,
) {
    val repeats get() = days.isNotEmpty() && date == null
    val time get() = "%02d:%02d".format(hour, minute)
    val canSnooze get() = snoozeTimes > 0 && snoozed < snoozeTimes

    fun json(): JSONObject = JSONObject().put("id", id).put("hour", hour).put("minute", minute).put("enabled", enabled)
        .put("days", JSONArray(days.sorted().map { it.value })).put("date", date?.toString() ?: "")
        .put("offAfterRing", offAfterRing).put("sound", sound.json()).put("label", label)
        .put("snoozeMinutes", snoozeMinutes).put("snoozeTimes", snoozeTimes).put("vibration", vibration.key)
        .put("snoozedUntil", snoozedUntil).put("snoozed", snoozed)

    companion object {
        const val LABEL_MAX = 60
        val SNOOZE_MINUTES = listOf(5, 10, 15, 20, 30)
        val SNOOZE_TIMES = listOf(1, 3, 5, 10)

        fun parse(o: JSONObject): Alarm? = runCatching {
            val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it).takeIf { d -> d in 1..7 } } }
                .orEmpty().map(DayOfWeek::of).toSet()
            Alarm(o.getLong("id"), o.getInt("hour").coerceIn(0, 23), o.getInt("minute").coerceIn(0, 59), o.optBoolean("enabled", true),
                days, o.optString("date").takeIf { it.isNotBlank() }?.let(LocalDate::parse), o.optBoolean("offAfterRing"),
                Sound.parse(o.optJSONObject("sound")), o.optString("label").take(LABEL_MAX),
                o.optInt("snoozeMinutes", 10).takeIf { it in 1..60 } ?: 10, o.optInt("snoozeTimes", 3).coerceIn(0, 10),
                Vibration.from(o.optString("vibration")), o.optLong("snoozedUntil"), o.optInt("snoozed"))
        }.getOrNull()
    }
}

/** The alarms, as JSON in the preferences (newest id last). */
class AlarmStore(private val kv: KeyValues) {
    fun list(): List<Alarm> = runCatching {
        val a = JSONArray(kv.get(KEY) ?: "[]")
        (0 until a.length()).mapNotNull { Alarm.parse(a.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))

    fun get(id: Long) = list().firstOrNull { it.id == id }

    /** Adds ([Alarm.id] 0 gets a new id) or replaces it. */
    fun save(alarm: Alarm): Alarm {
        val all = list()
        val saved = if (alarm.id != 0L) alarm else alarm.copy(id = (all.maxOfOrNull { it.id } ?: 0) + 1)
        write(all.filterNot { it.id == saved.id } + saved)
        return saved
    }

    fun delete(id: Long): Boolean {
        val all = list()
        if (all.none { it.id == id }) return false
        write(all.filterNot { it.id == id }); return true
    }

    private fun write(list: List<Alarm>) = kv.put(KEY, JSONArray(list.map { it.json() }).toString())

    private companion object { const val KEY = "alarms" }
}

/** Names of the sounds the user adds (pure, with tests). */
object SoundFiles {
    const val MAX_BYTES = 50L * 1024 * 1024
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "mid", "midi", "3gp", "mka", "webm")

    /** A safe file name: no folders or odd characters, at most 60 characters, an audio extension kept. */
    fun clean(shown: String): String {
        val base = shown.substringAfterLast('/').substringAfterLast('\\')
        val ext = base.substringAfterLast('.', "").lowercase().takeIf { it in AUDIO }
        val stem = (if (ext != null) base.dropLast(ext.length + 1) else base)
            .replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").trim(' ', '.').take(60).ifBlank { "sonido" }
        return if (ext != null) "$stem.$ext" else stem
    }

    /** «tono.mp3», then «tono (1).mp3»… while [taken]. */
    fun unique(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        return (1..999).map { "${name.substring(0, dot)} ($it)${name.substring(dot)}" }.firstOrNull { !taken(it) } ?: name
    }
}
