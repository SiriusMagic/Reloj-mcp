package com.zirakusa.reloj.data

import androidx.core.content.edit

/** Where the settings live: the phone's preferences in the app, a map in the tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapValues(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValues {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Every setting of Reloj, typed. Unknown or broken values fall back to the defaults. */
class Settings(private val kv: KeyValues) {
    private fun flag(key: String, default: Boolean) = kv.get(key)?.let { it == "1" } ?: default
    private fun setFlag(key: String, on: Boolean) = kv.put(key, if (on) "1" else "0")

    /** «Días festivos»: repeating alarms do not ring on Ecuador's holidays. */
    var skipHolidays: Boolean get() = flag("holidays", false); set(v) = setFlag("holidays", v)
    /** «Subir el volumen poco a poco». */
    var rising: Boolean get() = flag("rising", true); set(v) = setFlag("rising", v)
    /** «Silenciar tras»: minutes an alarm rings before it snoozes (or stops) by itself. */
    var ringMinutes: Int get() = kv.get("ring_minutes")?.toIntOrNull()?.takeIf { it in RING_MINUTES } ?: 10
        set(v) = kv.put("ring_minutes", v.toString())
    var timerSound: Boolean get() = flag("timer_sound", true); set(v) = setFlag("timer_sound", v)
    var stopwatchSound: Boolean get() = flag("stopwatch_sound", true); set(v) = setFlag("stopwatch_sound", v)
    /** «Reloj mundial»: analog face or digital time with seconds (tap the clock to switch). */
    var analog: Boolean get() = flag("analog", true); set(v) = setFlag("analog", v)
    /** The cities of «Reloj mundial», in the order added. */
    var cities: List<String> get() = kv.get("cities")?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(v) = kv.put("cities", v.joinToString("\n"))
    var timer: CountDown get() = CountDown.parse(kv.get("timer")); set(v) = kv.put("timer", v.json().toString())
    /** The last duration started, so «Temporizador» opens on it. */
    var lastTimer: Long get() = kv.get("last_timer")?.toLongOrNull() ?: 300_000L; set(v) = kv.put("last_timer", v.toString())
    /** «Temporizador»: the saved timers with a name (Reunión 20 min…), shown under the wheels. */
    var presets: List<Preset> get() = kv.get("presets")?.let(Preset::parse) ?: Preset.DEFAULTS
        set(v) = kv.put("presets", Preset.serialize(v))
    /** The timer's sound (the bell button of «Temporizador»). */
    var timerTone: Sound get() = runCatching { Sound.parse(org.json.JSONObject(kv.get("timer_tone")!!)) }.getOrDefault(Sound.DEFAULT)
        set(v) = kv.put("timer_tone", v.json().toString())
    /** The label of the timer that is running («Reunión»), or blank. */
    var timerName: String get() = kv.get("timer_name").orEmpty(); set(v) = kv.put("timer_name", v)
    var stopwatch: Stopwatch get() = Stopwatch.parse(kv.get("stopwatch")); set(v) = kv.put("stopwatch", v.json().toString())
    var theme: String? get() = kv.get("theme"); set(v) = kv.put("theme", v)
    var accent: String? get() = kv.get("accent"); set(v) = kv.put("accent", v)

    companion object { val RING_MINUTES = listOf(1, 5, 10, 15, 30) }
}

/** The phone's preferences file «reloj» (kept out of backups). The alarm receiver and the MCP tools read the same. */
class SharedValues(context: android.content.Context) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences("reloj", android.content.Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }
}
