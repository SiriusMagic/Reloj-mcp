package com.zirakusa.reloj.mcp

import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.Cities
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Holidays
import com.zirakusa.reloj.data.Schedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** What the tools read and change; the app gives the real alarms (and reschedules them), tests a fake. */
interface ClockData {
    fun alarms(): List<Alarm>
    /** Adds ([Alarm.id] 0) or replaces an alarm and reschedules; returns it with its id. */
    fun save(alarm: Alarm): Alarm
    fun delete(id: Long): Boolean
    fun skipHolidays(): Boolean
    fun now(): ZonedDateTime = ZonedDateTime.now()
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/**
 * Reloj's MCP tools: read the alarms and the time in a city; with a read-and-write token also create, switch on or off
 * and delete alarms. Arguments are checked one by one; nothing is ever run as code.
 */
object ClockTools {
    class Tool(val name: String, val description: String, val write: Boolean, val schema: JSONObject)

    private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    private fun schema(required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
    private val id = "id" to prop("integer", "The alarm's id (from clock_alarms).")

    val tools = listOf(
        Tool("clock_alarms", "The phone's alarms: id, time (24 h, phone's local time), enabled, repeat days, date, label, snooze " +
            "and when each rings next.", false, schema(emptyList())),
        Tool("clock_next_alarm", "The alarm that rings next and how long until then (alarm null when none is on).", false, schema(emptyList())),
        Tool("clock_world_time", "The current time and date in a city (e.g. «Madrid», «Tokio») or IANA time zone (e.g. «Europe/Madrid»), " +
            "and the difference with the phone.", false, schema(listOf("city"), "city" to prop("string", "City name or IANA zone id."))),
        Tool("clock_alarm_create", "Creates an alarm that is on. Without days and date it rings once at the next «time».", true,
            schema(listOf("time"), "time" to prop("string", "HH:MM, 24 h, the phone's local time."),
                "days" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string").put("enum", JSONArray(DAYS)))
                    .put("description", "Days it repeats on."),
                "date" to prop("string", "YYYY-MM-DD: rings once on that day (not with days)."),
                "label" to prop("string", "A name, up to ${Alarm.LABEL_MAX} characters."))),
        Tool("clock_alarm_enable", "Switches an alarm on or off.", true,
            schema(listOf("id", "enabled"), id, "enabled" to prop("boolean", "true = on."))),
        Tool("clock_alarm_delete", "Deletes an alarm.", true, schema(listOf("id"), id)),
    )

    fun available(access: Access) = tools.filter { !it.write || access == Access.WRITE }

    fun call(name: String, args: JSONObject, access: Access, data: ClockData): JSONObject {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool called «$name».")
        if (tool.write && access != Access.WRITE) throw ToolFailure("READ_ONLY", "This token can only read. Create a read-and-write token in Reloj.")
        val props = tool.schema.getJSONObject("properties")
        args.keys().forEach { if (!props.has(it)) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        val required = tool.schema.getJSONArray("required")
        (0 until required.length()).map { required.getString(it) }.forEach { if (!args.has(it)) throw ToolFailure("BAD_ARGUMENT", "Missing «$it».") }
        val now = data.now()
        return when (name) {
            "clock_alarms" -> JSONObject().put("alarms", JSONArray(data.alarms().map { describe(it, now, data) }))
            "clock_next_alarm" -> next(now, data)
            "clock_world_time" -> world(args.opt("city") as? String ?: throw ToolFailure("BAD_ARGUMENT", "city must be text."), now)
            "clock_alarm_create" -> JSONObject().put("alarm", describe(data.save(create(args, now)), now, data))
            "clock_alarm_enable" -> {
                val enabled = args.opt("enabled") as? Boolean ?: throw ToolFailure("BAD_ARGUMENT", "enabled must be true or false.")
                val alarm = find(args, data).copy(enabled = enabled, snoozedUntil = 0, snoozed = 0)
                JSONObject().put("alarm", describe(data.save(alarm), now, data))
            }
            else -> find(args, data).let { data.delete(it.id); JSONObject().put("deleted", it.id) }
        }
    }

    private fun find(args: JSONObject, data: ClockData): Alarm {
        val id = (args.opt("id") as? Number)?.takeIf { it is Int || it is Long }?.toLong()
            ?: throw ToolFailure("BAD_ARGUMENT", "id must be an integer.")
        return data.alarms().firstOrNull { it.id == id } ?: throw ToolFailure("NOT_FOUND", "No alarm with id $id.")
    }

    private fun create(args: JSONObject, now: ZonedDateTime): Alarm {
        val time = (args.opt("time") as? String)?.let { Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(it.trim()) }
            ?: throw ToolFailure("BAD_ARGUMENT", "time must be HH:MM (24 h).")
        val days = if (!args.has("days")) emptySet() else (args.opt("days") as? JSONArray)?.let { a ->
            (0 until a.length()).map { i -> DAYS.indexOf(a.opt(i) as? String).takeIf { it >= 0 } ?: throw ToolFailure("BAD_ARGUMENT", "days must be ${DAYS.joinToString()}.") }
                .map { DayOfWeek.of(it + 1) }.toSet()
        } ?: throw ToolFailure("BAD_ARGUMENT", "days must be a list.")
        val date = if (!args.has("date")) null else (args.opt("date") as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: throw ToolFailure("BAD_ARGUMENT", "date must be YYYY-MM-DD.")
        if (date != null && days.isNotEmpty()) throw ToolFailure("BAD_ARGUMENT", "Use days or date, not both.")
        val label = if (!args.has("label")) "" else (args.opt("label") as? String)?.trim()?.takeIf { it.length <= Alarm.LABEL_MAX }
            ?: throw ToolFailure("BAD_ARGUMENT", "label must be text of up to ${Alarm.LABEL_MAX} characters.")
        val alarm = Alarm(hour = time.groupValues[1].toInt(), minute = time.groupValues[2].toInt(), days = days, date = date, label = label)
        if (date != null && Schedule.next(alarm, now) == null) throw ToolFailure("BAD_ARGUMENT", "That date and time have already passed.")
        return alarm
    }

    private fun holiday(data: ClockData): (LocalDate) -> Boolean = if (data.skipHolidays()) Holidays::isEcuador else { _ -> false }

    private fun describe(a: Alarm, now: ZonedDateTime, data: ClockData): JSONObject {
        val next = Schedule.next(a, now, holiday(data))
        return JSONObject().put("id", a.id).put("time", a.time).put("enabled", a.enabled).put("label", a.label)
            .put("days", JSONArray(a.days.sorted().map { DAYS[it.value - 1] })).put("date", a.date?.toString() ?: JSONObject.NULL)
            .put("snooze", if (a.snoozeTimes == 0) JSONObject.NULL else JSONObject().put("minutes", a.snoozeMinutes).put("times", a.snoozeTimes))
            .put("next_ring", next?.toOffsetDateTime()?.toString() ?: JSONObject.NULL)
    }

    private fun next(now: ZonedDateTime, data: ClockData): JSONObject {
        val (alarm, at) = Schedule.soonest(data.alarms(), now, holiday(data)) ?: return JSONObject().put("alarm", JSONObject.NULL)
        val u = Schedule.until(now, at)
        val minutes = java.time.Duration.between(now, at).toMinutes()
        return JSONObject().put("alarm", describe(alarm, now, data)).put("in_minutes", minutes)
            .put("in_text", if (u.days > 0) "${u.days} d" else "${u.hours} h ${u.minutes} min")
    }

    private fun world(text: String, now: ZonedDateTime): JSONObject {
        val city = Cities.find(text) ?: throw ToolFailure("NOT_FOUND", "No city or time zone «$text».")
        val there = now.withZoneSameInstant(java.time.ZoneId.of(city.zone))
        val diff = there.offset.totalSeconds - now.offset.totalSeconds
        return JSONObject().put("city", city.name).put("zone", city.zone).put("time", "%02d:%02d".format(there.hour, there.minute))
            .put("date", there.toLocalDate().toString()).put("utc_offset", Format.gmt(there.offset.totalSeconds))
            .put("difference_minutes", diff / 60)
    }
}
