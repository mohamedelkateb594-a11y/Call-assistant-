package com.mohamed.callassistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * apptState: "" (سجل قديم، كان بيتحفظ تلقائي) | "pending" (مستني تأكيدك) | "saved" | "skipped"
 */
data class CallRecord(
    val id: String,
    val number: String,
    val name: String?,
    val startMs: Long,
    val durationSec: Int,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val transcript: String,
    val apptMs: Long,
    val apptNote: String,
    val file: String,
    val status: String,
    val error: String,
    val apptState: String = ""
)

object Store {
    var items by mutableStateOf(listOf<CallRecord>())
    private fun f(c: Context) = File(c.filesDir, "calls.json")

    private fun toJson(r: CallRecord) = JSONObject()
        .put("id", r.id).put("number", r.number).put("name", r.name ?: "")
        .put("start", r.startMs).put("dur", r.durationSec).put("title", r.title)
        .put("summary", r.summary).put("points", JSONArray(r.keyPoints))
        .put("transcript", r.transcript).put("appt", r.apptMs).put("note", r.apptNote)
        .put("file", r.file).put("status", r.status).put("error", r.error)
        .put("apptState", r.apptState)

    private fun fromJson(j: JSONObject): CallRecord {
        val p = j.optJSONArray("points")
        val pts = ArrayList<String>()
        if (p != null) for (i in 0 until p.length()) pts.add(p.optString(i))
        return CallRecord(
            j.optString("id"), j.optString("number"), j.optString("name").ifBlank { null },
            j.optLong("start"), j.optInt("dur"), j.optString("title"), j.optString("summary"),
            pts, j.optString("transcript"), j.optLong("appt"), j.optString("note"),
            j.optString("file"), j.optString("status", "done"), j.optString("error"),
            j.optString("apptState")
        )
    }

    @Synchronized
    fun load(c: Context) {
        val fl = f(c)
        if (!fl.exists()) { items = emptyList(); return }
        try {
            val arr = JSONArray(fl.readText())
            items = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.startMs }
        } catch (e: Exception) { }
    }

    @Synchronized
    private fun save(c: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        val tmp = File(c.filesDir, "calls.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f(c))
    }

    @Synchronized
    fun upsert(c: Context, r: CallRecord) {
        items = (items.filter { it.id != r.id } + r).sortedByDescending { it.startMs }
        save(c)
    }

    @Synchronized
    fun delete(c: Context, id: String) {
        items = items.filter { it.id != id }
        save(c)
    }

    fun lastDone(number: String): CallRecord? {
        val t = tail(number)
        if (t.isBlank()) return null
        return items.firstOrNull { it.status == "done" && tail(it.number) == t }
    }
}
