package com.zirakusa.reloj

import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.MapValues
import com.zirakusa.reloj.mcp.Access
import com.zirakusa.reloj.mcp.ClockData
import com.zirakusa.reloj.mcp.HubLink
import com.zirakusa.reloj.mcp.McpHandler
import com.zirakusa.reloj.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reloj's MCP: tools list, good calls, bad arguments, not found, read-only tokens, wrong tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTest {
    private val store = AlarmStore(MapValues())
    private var changes = 0
    private val data = object : ClockData {
        var holidays = false
        override fun alarms() = store.list()
        override fun save(alarm: Alarm) = store.save(alarm).also { changes++ }
        override fun delete(id: Long) = store.delete(id).also { changes++ }
        override fun skipHolidays() = holidays
        override fun now(): ZonedDateTime = ZonedDateTime.of(2026, 11, 21, 6, 36, 40, 0, ZoneId.of("America/Guayaquil"))
    }
    private val writer = Token("rel_" + "w".repeat(40), Access.WRITE)
    private val reader = Token("rel_" + "r".repeat(40), Access.READ)
    private val handler = McpHandler({ listOf(writer, reader) }, data, "0.1.0")

    init {
        store.save(Alarm(hour = 6, minute = 2, days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)))
        store.save(Alarm(hour = 13, minute = 30, label = "Almuerzo"))
    }

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body).getJSONObject("result")
    }
    private fun tool(name: String, args: JSONObject, token: Token = writer) = rpc(token, "tools/call", JSONObject().put("name", name).put("arguments", args))
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject(), token: Token = writer) = tool(name, args, token).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")
    private fun names(token: Token) = rpc(token, "tools/list").getJSONArray("tools").let { t -> (0 until t.length()).map { t.getJSONObject(it).getString("name") } }

    @Test fun listsTheTools() {
        assertEquals(listOf("clock_alarms", "clock_next_alarm", "clock_world_time", "clock_alarm_create", "clock_alarm_enable", "clock_alarm_delete"), names(writer))
        assertEquals(listOf("clock_alarms", "clock_next_alarm", "clock_world_time"), names(reader))
        val init = rpc(writer, "initialize")
        assertEquals("reloj", init.getJSONObject("serverInfo").getString("name"))
        assertEquals("redirect", init.getJSONObject("_meta").getJSONObject(McpHandler.TOKEN_META).getString("kind"))
    }

    @Test fun readsAlarmsAndTime() {
        val alarms = ok("clock_alarms").getJSONArray("alarms")
        assertEquals(2, alarms.length())
        val first = alarms.getJSONObject(0)
        assertEquals("06:02", first.getString("time")); assertEquals("2026-11-23T06:02-05:00", first.getString("next_ring"))
        assertEquals(JSONArray(listOf("mon", "tue", "wed", "thu", "fri")).toString(), first.getJSONArray("days").toString())
        val next = ok("clock_next_alarm")
        assertEquals("Almuerzo", next.getJSONObject("alarm").getString("label")); assertEquals("6 h 54 min", next.getString("in_text"))
        val madrid = ok("clock_world_time", JSONObject().put("city", "madrid"))
        assertEquals("12:36", madrid.getString("time")); assertEquals(360, madrid.getInt("difference_minutes")); assertEquals("GMT +1:00", madrid.getString("utc_offset"))
        assertEquals("Asia/Tokyo", ok("clock_world_time", JSONObject().put("city", "Asia/Tokyo")).getString("zone"))
    }

    @Test fun createsSwitchesAndDeletes() {
        val made = ok("clock_alarm_create", JSONObject().put("time", "7:15").put("days", JSONArray(listOf("sat", "sun"))).put("label", "Correr")).getJSONObject("alarm")
        assertEquals("07:15", made.getString("time")); assertEquals("2026-11-21T07:15-05:00", made.getString("next_ring"))
        val id = made.getLong("id")
        assertFalse(ok("clock_alarm_enable", JSONObject().put("id", id).put("enabled", false)).getJSONObject("alarm").getBoolean("enabled"))
        assertEquals(JSONObject.NULL, ok("clock_alarms").getJSONArray("alarms").getJSONObject(1).get("next_ring"))
        val dated = ok("clock_alarm_create", JSONObject().put("time", "20:00").put("date", "2026-12-24")).getJSONObject("alarm")
        assertEquals("2026-12-24", dated.getString("date"))
        assertEquals(id, ok("clock_alarm_delete", JSONObject().put("id", id)).getLong("deleted"))
        assertEquals(3, store.list().size); assertEquals(4, changes)
    }

    @Test fun badArgumentsAreRefused() {
        assertEquals("BAD_ARGUMENT", code("clock_alarms", JSONObject().put("sql", "DROP TABLE")))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject()))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject().put("time", "25:00")))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject().put("time", "07:00").put("days", JSONArray(listOf("funday")))))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject().put("time", "07:00").put("date", "mañana")))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject().put("time", "06:00").put("date", "2026-11-21")))   // passed
        assertEquals("BAD_ARGUMENT", code("clock_alarm_create", JSONObject().put("time", "07:00").put("label", "x".repeat(61))))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_enable", JSONObject().put("id", "1").put("enabled", true)))
        assertEquals("BAD_ARGUMENT", code("clock_alarm_enable", JSONObject().put("id", 1).put("enabled", "yes")))
        assertEquals("BAD_ARGUMENT", code("clock_world_time", JSONObject().put("city", 5)))
        assertEquals(2, store.list().size)
    }

    @Test fun notFoundUnknownAndReadOnly() {
        assertEquals("NOT_FOUND", code("clock_alarm_delete", JSONObject().put("id", 99)))
        assertEquals("NOT_FOUND", code("clock_alarm_enable", JSONObject().put("id", 99).put("enabled", true)))
        assertEquals("NOT_FOUND", code("clock_world_time", JSONObject().put("city", "Atlántida")))
        assertEquals("UNKNOWN_TOOL", code("clock_shell", JSONObject()))
        assertEquals("READ_ONLY", code("clock_alarm_delete", JSONObject().put("id", 1), reader))
        store.list().forEach { store.save(it.copy(enabled = false)) }
        assertEquals(JSONObject.NULL, ok("clock_next_alarm").get("alarm"))
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer wrong_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer rel_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertEquals(404, handler.handle("POST", "/other", emptyMap(), emptyMap(), body).status)
        assertTrue(Token.secret().startsWith("rel_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        // Only the same developer (Arka) may connect, disconnect, read the status or run a tool.
        listOf(HubLink.disconnect(false), HubLink.status(false)).forEach { assertEquals("NOT_SAME_DEVELOPER", it.getString("error")) }
    }

    /** Lucía runs a tool on the phone through the same handler, with or without the public link; Arka turns that on and off. */
    @Test fun luciaRunsToolsOnThePhone() {
        val relay = com.zirakusa.reloj.mcp.McpMode.relayUrl
        com.zirakusa.reloj.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.reloj.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.reloj.mcp.McpMode.turnRemote(false)
        val list = JSONObject(com.zirakusa.reloj.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        assertEquals("BAD_REQUEST", HubLink.tool(true, "").getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertTrue(HubLink.status(true).containsKey("remote"))
        HubLink.connect(true); assertTrue(com.zirakusa.reloj.mcp.McpMode.remote)                 // Arka › «Activar MCP»
        assertFalse(HubLink.disconnect(true).getBoolean("remote")); assertFalse(com.zirakusa.reloj.mcp.McpMode.remote)
        com.zirakusa.reloj.mcp.McpMode.relayUrl = relay                                          // as the other tests expect
    }

    /** The hub's token from before Arka took over is kept, so the hub's link keeps working. */
    @Test fun theOldHubTokenIsKept() {
        val old = """[{"id":"a","secret":"${"l".repeat(44)}","kind":"llm"},{"id":"b","secret":"${"h".repeat(44)}","kind":"redirect"}]"""
        assertEquals("h".repeat(44), com.zirakusa.reloj.mcp.Legacy.redirectSecret(old))
        assertEquals(null, com.zirakusa.reloj.mcp.Legacy.redirectSecret("[]"))
        assertEquals(null, com.zirakusa.reloj.mcp.Legacy.redirectSecret(""))
    }
}
