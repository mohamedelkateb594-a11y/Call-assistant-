package com.mohamed.callassistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Analysis(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val transcript: String,
    val callerName: String?,
    val apptIso: String?,
    val apptNote: String,
    val tasks: List<TaskItem> = emptyList(),
    val facts: List<String> = emptyList(),
    val category: String = ""
)

class ApiError(val code: Int, msg: String) : Exception(msg)

fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "").trim()

object Gemini {
    fun analyze(c: Context, f: File): Analysis {
        val key = Prefs.apiKey(c)
        if (key.isBlank()) throw IllegalStateException("مفيش مفتاح Gemini — ضيفه من الإعدادات")
        if (f.length() > 14_000_000) throw IllegalStateException("ملف التسجيل كبير أوي")
        val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        val mimes = when (f.extension.lowercase()) {
            "mp3" -> listOf("audio/mp3")
            "m4a", "mp4" -> listOf("audio/mp4", "audio/aac")
            "aac" -> listOf("audio/aac")
            "wav" -> listOf("audio/wav")
            "ogg", "opus", "oga" -> listOf("audio/ogg")
            else -> listOf("audio/mp3", "audio/aac")
        }
        var last: ApiError? = null
        for (m in mimes) {
            try {
                return call(c, key, m, b64)
            } catch (e: ApiError) {
                if (e.code == 400) last = e else throw e
            }
        }
        throw last ?: IllegalStateException("فشل غير معروف")
    }

    private fun call(c: Context, key: String, mime: String, b64: String): Analysis {
        val now = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
        val prompt = """
You process a recorded phone call. The audio is a phone call (mostly Egyptian Arabic, sometimes mixed with English) between the phone owner and one other person. Current local date/time: $now.
Return ONLY a JSON object with exactly these keys:
{
  "transcript": "full transcript; label speakers 'أنا:' (phone owner) and 'هو:' (other person), each speaker turn on its own line",
  "title": "very short title in Arabic, max 6 words",
  "summary": "2-4 sentences in Arabic: what was discussed and what was decided",
  "key_points": ["short Arabic bullet", "..."],
  "caller_name": "the OTHER person's own name, ONLY if they clearly say it during the call, otherwise null",
  "appointment": {"datetime": "yyyy-MM-ddTHH:mm", "note": "short Arabic description"} or null,
  "tasks": [{"text": "short Arabic description of the action", "owner": "me" or "them", "due": "yyyy-MM-ddTHH:mm" or null}],
  "facts": ["important concrete details said in the call such as money amounts, addresses, phone numbers, account or reference numbers, written in short Arabic, e.g. 'المبلغ: 5000 جنيه'"],
  "category": "one of: عمل, مالي, شخصي, أخرى"
}
Rules:
- Set appointment only if a future meeting, visit, call-back or deadline was actually agreed. Resolve relative words (بكرة، الخميس، بعد أسبوع) using the current date. If only a day is given, use 10:00.
- tasks: only real commitments or requests made in the call. owner "me" = something the phone owner must do. owner "them" = something the other person promised or must do. Use due only when a time or deadline was mentioned, otherwise null. If there are none, use [].
- facts: only details that were actually said. If there are none, use [].
- If the audio has no real conversation, set summary to "مفيش كلام واضح في المكالمة", key_points, tasks and facts to [], caller_name and appointment to null.
- Do not invent anything that was not said.
""".trimIndent()

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", mime).put("data", b64)))
                .put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2))

        return parse(postJson(c, key, body))
    }

    /** سؤال بلغة طبيعية عن كل المكالمات المتلخصة */
    fun ask(c: Context, question: String): String {
        val key = Prefs.apiKey(c)
        if (key.isBlank()) throw IllegalStateException("مفيش مفتاح Gemini — ضيفه من الإعدادات")
        val sb = StringBuilder()
        for (r in Store.items.filter { it.status == "done" }.take(120)) {
            sb.append("- ").append(Fmt.dateShort(r.startMs)).append(' ').append(Fmt.time(r.startMs))
                .append(" | ").append(r.name ?: r.number.ifBlank { "رقم مجهول" })
                .append(" | ").append(r.title).append(": ").append(r.summary)
            if (r.keyPoints.isNotEmpty()) sb.append(" | نقاط: ").append(r.keyPoints.joinToString("؛ "))
            if (r.apptMs > 0) sb.append(" | موعد: ").append(Fmt.dateShort(r.apptMs)).append(' ').append(Fmt.time(r.apptMs))
            for (t in r.tasks) {
                sb.append(" | مهمة (").append(if (t.mine) "عليّا" else "عليه").append(if (t.done) " - خلصت" else "").append("): ").append(t.text)
            }
            if (r.facts.isNotEmpty()) sb.append(" | بيانات: ").append(r.facts.joinToString("؛ "))
            sb.append('\n')
            if (sb.length > 60_000) break
        }
        if (sb.isEmpty()) return "مفيش مكالمات متلخصة لسه أجاوب منها."
        val now = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
        val prompt = "You answer questions about the phone owner's calls using ONLY the call notes below. " +
            "Answer in short Egyptian Arabic. Mention who and when. If the answer is not in the notes, say so clearly. " +
            "Current local date/time: " + now + "\n\nCall notes (newest first):\n" + sb.toString() +
            "\nQuestion: " + question
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        return joinText(postJson(c, key, body)).trim()
    }

    private fun postJson(c: Context, key: String, body: JSONObject): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${Prefs.model(c)}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 180000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 429 || code >= 500) throw IOException("الخدمة مشغولة ($code)")
            if (code !in 200..299) {
                val m = try {
                    JSONObject(txt).getJSONObject("error").getString("message")
                } catch (e: Exception) { txt.take(120) }
                throw ApiError(code, if (m.contains("API key", true)) "مفتاح Gemini مش صحيح" else m)
            }
            return txt
        } finally {
            conn.disconnect()
        }
    }

    private fun joinText(resp: String): String {
        val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        var t = ""
        for (i in 0 until parts.length()) t += parts.getJSONObject(i).optString("text", "")
        return t
    }

    private fun isoMs(s: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).parse(s.trim().take(16))?.time ?: 0L
    } catch (e: Exception) { 0L }

    private fun parse(resp: String): Analysis {
        var t = joinText(resp)
        t = t.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val j = JSONObject(t)
        val pts = ArrayList<String>()
        val kp = j.optJSONArray("key_points")
        if (kp != null) {
            for (i in 0 until kp.length()) {
                val s = kp.optString(i).trim()
                if (s.isNotEmpty() && s != "null") pts.add(s)
            }
        }
        val ap = j.optJSONObject("appointment")
        val name = j.s("caller_name").takeIf { it.isNotBlank() && !it.equals("null", true) && it.length <= 40 }

        val tasks = ArrayList<TaskItem>()
        val ta = j.optJSONArray("tasks")
        if (ta != null) {
            for (i in 0 until ta.length()) {
                val o = ta.optJSONObject(i) ?: continue
                val tx = o.s("text")
                if (tx.isBlank() || tx == "null") continue
                val mine = !o.s("owner").equals("them", true)
                val due = isoMs(o.s("due"))
                tasks.add(TaskItem(tx, mine, if (due > System.currentTimeMillis()) due else 0L))
            }
        }
        val facts = ArrayList<String>()
        val fa = j.optJSONArray("facts")
        if (fa != null) {
            for (i in 0 until fa.length()) {
                val s = fa.optString(i).trim()
                if (s.isNotEmpty() && s != "null") facts.add(s)
            }
        }
        val cat = j.s("category").let { if (it in listOf("عمل", "مالي", "شخصي", "أخرى")) it else "" }

        return Analysis(
            j.s("title").ifBlank { "مكالمة" },
            j.s("summary").ifBlank { "مفيش كلام واضح في المكالمة" },
            pts,
            j.s("transcript"),
            name,
            ap?.s("datetime"),
            ap?.s("note") ?: "",
            tasks, facts, cat
        )
    }
}
