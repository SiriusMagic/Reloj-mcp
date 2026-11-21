package com.zirakusa.reloj.data

import org.json.JSONArray
import org.json.JSONObject

/** «Temporizador»: [total] chosen; while running it ends at [endsAt]; paused it keeps [remaining]. Idle when total is 0. */
data class CountDown(val total: Long = 0, val endsAt: Long = 0, val remaining: Long = 0, val running: Boolean = false) {
    val idle get() = total == 0L
    fun left(now: Long) = if (running) (endsAt - now).coerceAtLeast(0) else remaining
    /** 0 at the start, 1 when finished. */
    fun progress(now: Long) = if (total == 0L) 0f else (1f - left(now).toFloat() / total).coerceIn(0f, 1f)
    fun pause(now: Long) = if (!running) this else copy(running = false, remaining = left(now))
    fun resume(now: Long) = if (running || idle) this else copy(running = true, endsAt = now + remaining)
    /** «+1:00» while it runs or is paused. */
    fun plus(ms: Long) = if (idle) this else copy(total = total + ms, endsAt = endsAt + ms, remaining = remaining + ms)

    fun json(): JSONObject = JSONObject().put("total", total).put("endsAt", endsAt).put("remaining", remaining).put("running", running)

    companion object {
        const val MAX = 99 * 3_600_000L + 59 * 60_000L + 59_000L
        fun start(total: Long, now: Long) = if (total <= 0) CountDown() else CountDown(total.coerceAtMost(MAX), now + total.coerceAtMost(MAX), total.coerceAtMost(MAX), true)
        fun parse(text: String?): CountDown = runCatching {
            val o = JSONObject(text!!)
            CountDown(o.getLong("total"), o.getLong("endsAt"), o.getLong("remaining"), o.getBoolean("running"))
        }.getOrDefault(CountDown())
    }
}

/** «Cronómetro»: time adds up while running; [laps] are the marks (total time at each «Vuelta»). */
data class Stopwatch(val running: Boolean = false, val startedAt: Long = 0, val accumulated: Long = 0, val laps: List<Long> = emptyList()) {
    val idle get() = !running && accumulated == 0L
    fun elapsed(now: Long) = accumulated + if (running) (now - startedAt).coerceAtLeast(0) else 0
    fun start(now: Long) = if (running) this else copy(running = true, startedAt = now)
    fun pause(now: Long) = if (!running) this else copy(running = false, accumulated = elapsed(now))
    fun lap(now: Long) = if (!running) this else copy(laps = laps + elapsed(now))
    /** Each lap's own time, newest first, with its number: (n, lap time, total). */
    fun splits(): List<Triple<Int, Long, Long>> = laps.mapIndexed { i, t -> Triple(i + 1, t - (laps.getOrNull(i - 1) ?: 0L), t) }.reversed()

    fun json(): JSONObject = JSONObject().put("running", running).put("startedAt", startedAt).put("accumulated", accumulated)
        .put("laps", JSONArray(laps))

    companion object {
        fun parse(text: String?): Stopwatch = runCatching {
            val o = JSONObject(text!!)
            val l = o.optJSONArray("laps") ?: JSONArray()
            Stopwatch(o.getBoolean("running"), o.getLong("startedAt"), o.getLong("accumulated"), (0 until l.length()).map { l.getLong(it) })
        }.getOrDefault(Stopwatch())
    }
}

/** How times are written. */
object Format {
    /** Timer: «05:00», «1:05:00»; seconds rounded up so it shows 00:01 until the very end. */
    fun countdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        val h = s / 3600; val m = s / 60 % 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** Stopwatch: «00:12.34», «1:02:03.45». */
    fun stopwatch(ms: Long): String {
        val cs = ms.coerceAtLeast(0) / 10
        val h = cs / 360_000; val m = cs / 6000 % 60; val s = cs / 100 % 60; val c = cs % 100
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, c) else "%02d:%02d.%02d".format(m, s, c)
    }

    /** «GMT +5:30», «GMT -7:00», «GMT +0:00». */
    fun gmt(offsetSeconds: Int): String {
        val sign = if (offsetSeconds < 0) "-" else "+"
        val a = kotlin.math.abs(offsetSeconds) / 60
        return "GMT $sign${a / 60}:%02d".format(a % 60)
    }

    /** Hours to 12 h: 0 → 12, 13 → 1. */
    fun hour12(hour: Int) = (hour % 12).let { if (it == 0) 12 else it }
}

/** A saved timer of «Temporizador». */
data class Preset(val name: String, val ms: Long) {
    companion object {
        const val NAME_MAX = 30
        val DEFAULTS = listOf(Preset("Reunión", 20 * 60_000L), Preset("Sueño", 10 * 60_000L), Preset("Ejercicio", 15 * 60_000L))
        fun parse(text: String): List<Preset> = runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { a.getJSONObject(it) }.map { Preset(it.getString("name").take(NAME_MAX), it.getLong("ms")) }
                .filter { it.ms in 1000..CountDown.MAX }
        }.getOrDefault(DEFAULTS)
        fun serialize(list: List<Preset>) = JSONArray(list.map { JSONObject().put("name", it.name).put("ms", it.ms) }).toString()
    }
}
