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
    val apptNote: String
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
            "ogg" -> listOf("audio/ogg")
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
  "transcript": "full transcript; label speakers 'أنا:' (phone owner) and 'هو:' (other person)",
  "title": "very short title in Arabic, max 6 words",
  "summary": "2-4 sentences in Arabic: what was discussed and what was decided",
  "key_points": ["short Arabic bullet", "..."],
  "caller_name": "the OTHER person's own name, ONLY if they clearly say it during the call, otherwise null",
  "appointment": {"datetime": "yyyy-MM-ddTHH:mm", "note": "short Arabic description"} or null
}
Rules:
- Set appointment only if a future meeting, visit, call-back or deadline was actually agreed. Resolve relative words (بكرة، الخميس، بعد أسبوع) using the current date. If only a day is given, use 10:00.
- If the audio has no real conversation, set summary to "مفيش كلام واضح في المكالمة", key_points to [], caller_name and appointment to null.
- Do not invent anything that was not said.
""".trimIndent()

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", mime).put("data", b64)))
                .put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2))

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
            return parse(txt)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(resp: String): Analysis {
        val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        var t = ""
        for (i in 0 until parts.length()) t += parts.getJSONObject(i).optString("text", "")
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
        return Analysis(
            j.s("title").ifBlank { "مكالمة" },
            j.s("summary").ifBlank { "مفيش كلام واضح في المكالمة" },
            pts,
            j.s("transcript"),
            name,
            ap?.s("datetime"),
            ap?.s("note") ?: ""
        )
    }
}
