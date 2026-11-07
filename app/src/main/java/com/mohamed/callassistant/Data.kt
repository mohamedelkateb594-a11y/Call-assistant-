package com.mohamed.callassistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** مهمة متاخدة من مكالمة. mine=true يعني عليّا، false يعني الشخص التاني اتعهد بيها */
data class TaskItem(val text: String, val mine: Boolean, val dueMs: Long, val done: Boolean = false)

/**
 * apptState: "" (سجل قديم، كان بيتحفظ تلقائي) | "pending" (مستني تأكيدك) | "saved" | "skipped"
 * callCount: كام مكالمة اندمجت في الملخص ده (نفس الرقم بيحدّث ملخصه بدل ما يعمل ملخص جديد)
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
    val apptState: String = "",
    val tasks: List<TaskItem> = emptyList(),
    val facts: List<String> = emptyList(),
    val category: String = "",
    val starred: Boolean = false,
    val callCount: Int = 1
)

object Store {
    var items by mutableStateOf(listOf<CallRecord>())
    private fun f(c: Context) = File(c.filesDir, "calls.json")

    private fun toJson(r: CallRecord): JSONObject {
        val tasks = JSONArray()
        r.tasks.forEach {
            tasks.put(JSONObject().put("t", it.text).put("m", it.mine).put("d", it.dueMs).put("x", it.done))
        }
        return JSONObject()
            .put("id", r.id).put("number", r.number).put("name", r.name ?: "")
            .put("start", r.startMs).put("dur", r.durationSec).put("title", r.title)
            .put("summary", r.summary).put("points", JSONArray(r.keyPoints))
            .put("transcript", r.transcript).put("appt", r.apptMs).put("note", r.apptNote)
            .put("file", r.file).put("status", r.status).put("error", r.error)
            .put("apptState", r.apptState)
            .put("tasks", tasks).put("facts", JSONArray(r.facts))
            .put("cat", r.category).put("star", r.starred).put("cnt", r.callCount)
    }

    private fun fromJson(j: JSONObject): CallRecord {
        val p = j.optJSONArray("points")
        val pts = ArrayList<String>()
        if (p != null) for (i in 0 until p.length()) pts.add(p.optString(i))
        val ta = j.optJSONArray("tasks")
        val tasks = ArrayList<TaskItem>()
        if (ta != null) {
            for (i in 0 until ta.length()) {
                val o = ta.optJSONObject(i) ?: continue
                tasks.add(TaskItem(o.optString("t"), o.optBoolean("m", true), o.optLong("d"), o.optBoolean("x")))
            }
        }
        val fa = j.optJSONArray("facts")
        val facts = ArrayList<String>()
        if (fa != null) for (i in 0 until fa.length()) facts.add(fa.optString(i))
        return CallRecord(
            j.optString("id"), j.optString("number"), j.optString("name").ifBlank { null },
            j.optLong("start"), j.optInt("dur"), j.optString("title"), j.optString("summary"),
            pts, j.optString("transcript"), j.optLong("appt"), j.optString("note"),
            j.optString("file"), j.optString("status", "done"), j.optString("error"),
            j.optString("apptState"),
            tasks, facts, j.optString("cat"), j.optBoolean("star"),
            callCount = j.optInt("cnt", 1).coerceAtLeast(1)
        )
    }

    private fun parse(c: Context, text: String) {
        val arr = JSONArray(text)
        items = (0 until arr.length())
            .map { fromJson(arr.getJSONObject(it)) }
            // المكالمات القديمة بدون اسم تاخد الاسم المسجل جوه البرنامج لنفس الرقم
            .map { r ->
                if (r.name == null && r.number.isNotBlank()) r.copy(name = Prefs.knownName(c, r.number)) else r
            }
            .sortedByDescending { it.startMs }
    }

    @Synchronized
    fun load(c: Context) {
        val fl = f(c)
        if (!fl.exists()) { items = emptyList(); return }
        try {
            val raw = fl.readBytes()
            // الملف القديم كان نص عادي؛ الجديد مشفّر
            val text = if (DataCrypt.isEncrypted(raw)) DataCrypt.dec(raw) else String(raw, Charsets.UTF_8)
            parse(c, text)
        } catch (e: Exception) {
            // منسيش نسخة لو الملف ما اتقراش، عشان ما نكتب فوقيه ونضيع البيانات
            try { fl.copyTo(File(c.filesDir, "calls.bad"), true) } catch (e2: Exception) { }
        }
    }

    private fun pack(s: String): ByteArray =
        try { DataCrypt.enc(s) } catch (e: Exception) { s.toByteArray(Charsets.UTF_8) }

    @Synchronized
    private fun save(c: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        val tmp = File(c.filesDir, "calls.tmp")
        tmp.writeBytes(pack(arr.toString()))
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

    /** نسخة احتياطية مفهومة (JSON عادي) لكل المكالمات */
    fun exportJson(): String {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        return arr.toString(2)
    }

    /** استرجاع نسخة احتياطية: بتتدمج مع الموجود (نفس الـ id يتستبدل) */
    @Synchronized
    fun importJson(c: Context, text: String): Int {
        val arr = JSONArray(text)
        val incoming = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        val ids = incoming.map { it.id }.toSet()
        items = (items.filter { it.id !in ids } + incoming).sortedByDescending { it.startMs }
        save(c)
        return incoming.size
    }

    fun lastDone(number: String): CallRecord? {
        val t = tail(number)
        if (t.isBlank()) return null
        return items.firstOrNull { it.status == "done" && tail(it.number) == t }
    }
}
