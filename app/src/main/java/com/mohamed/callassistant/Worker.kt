package com.mohamed.callassistant

import android.Manifest
import android.content.Context
import android.os.Environment
import android.provider.CallLog
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class CallInfo(val number: String, val startMs: Long, val durSec: Int)

object CallLogs {
    fun match(c: Context, fileMs: Long): CallInfo? {
        if (!has(c, Manifest.permission.READ_CALL_LOG)) return null
        var best: CallInfo? = null
        var bestDiff = Long.MAX_VALUE
        try {
            c.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { cur ->
                var n = 0
                while (cur.moveToNext() && n < 15) {
                    n++
                    val type = cur.getInt(3)
                    val dur = cur.getInt(2)
                    if ((type != CallLog.Calls.INCOMING_TYPE && type != CallLog.Calls.OUTGOING_TYPE) || dur <= 0) continue
                    val date = cur.getLong(1)
                    val diff = Math.abs(date + dur * 1000L - fileMs)
                    if (diff < bestDiff) {
                        bestDiff = diff
                        best = CallInfo(cur.getString(0) ?: "", date, dur)
                    }
                }
            }
        } catch (e: Exception) { }
        return if (bestDiff < 3 * 60_000L) best else null
    }
}

object Recordings {
    private val defaults = listOf(
        "MIUI/sound_recorder/call_rec",
        "Recordings/Call",
        "Recordings/Call recordings",
        "Call",
        "CallRecordings",
        "Music/Recordings/Call Recordings",
        "Sounds/CallRecordings"
    )
    private val exts = setOf("mp3", "m4a", "aac", "wav", "amr", "ogg", "3gp", "mp4")

    fun dirs(c: Context): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val list = ArrayList<File>()
        val custom = Prefs.folder(c)
        if (custom.isNotBlank()) list.add(if (custom.startsWith("/")) File(custom) else File(root, custom))
        defaults.forEach { list.add(File(root, it)) }
        return list.filter { it.isDirectory }
    }

    fun recent(c: Context): List<File> {
        val done = Prefs.processed(c)
        val limit = System.currentTimeMillis() - 45 * 60_000L
        return dirs(c).flatMap { d ->
            d.walkTopDown().maxDepth(2).filter {
                it.isFile && it.extension.lowercase() in exts && it.lastModified() > limit && it.path !in done
            }.toList()
        }.distinct().sortedBy { it.lastModified() }
    }
}

object Pipeline {
    const val DONE = 0
    const val RETRY = 1

    private fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).parse(s.trim().take(16))?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun fail(c: Context, id: String, number: String, start: Long, dur: Int, f: File, msg: String) {
        val old = Store.items.firstOrNull { it.id == id }
        Store.upsert(c, CallRecord(id, number, old?.name, start, dur, "ما اتلخصتش", "", emptyList(), "", 0L, "", f.path, "failed", msg))
        Prefs.markProcessed(c, f.path)
        Notif.post(c, id.hashCode(), "ما قدرتش ألخّص مكالمة", msg)
    }

    fun run(c: Context, f: File, existingId: String?, finalAttempt: Boolean): Int {
        if (System.currentTimeMillis() - f.lastModified() < 4000) {
            try { Thread.sleep(4000) } catch (e: Exception) { }
        }
        val info = CallLogs.match(c, f.lastModified())
        val isCallDir = f.path.lowercase().contains("call")
        if (existingId == null) {
            if (info == null && !isCallDir) { Prefs.markProcessed(c, f.path); return DONE }
            if (f.length() < 8_000 || (info != null && info.durSec < 8)) { Prefs.markProcessed(c, f.path); return DONE }
        }
        val old = existingId?.let { id -> Store.items.firstOrNull { it.id == id } }
        var number = old?.number ?: info?.number ?: ""
        if (number.isBlank()) number = Regex("\\+?\\d{8,15}").find(f.name)?.value ?: ""
        val start = old?.startMs ?: info?.startMs ?: (f.lastModified() - 60_000L)
        val dur = old?.durationSec ?: info?.durSec ?: 0
        val id = existingId ?: UUID.randomUUID().toString()
        try {
            val a = Gemini.analyze(c, f)
            val saved = Contacts.nameFor(c, number)
            val cn = a.callerName
            if (saved == null && cn != null && number.isNotBlank() && has(c, Manifest.permission.WRITE_CONTACTS)) {
                Contacts.save(c, cn, number)
            }
            val name = saved ?: cn ?: old?.name
            val apptRaw = parseIso(a.apptIso)
            val appt = if (apptRaw > System.currentTimeMillis()) apptRaw else 0L
            val rec = CallRecord(id, number, name, start, dur, a.title, a.summary, a.keyPoints, a.transcript, appt, a.apptNote, f.path, "done", "")
            Store.upsert(c, rec)
            Prefs.markProcessed(c, f.path)
            if (appt > 0) Remind.schedule(c, rec)
            Notif.post(c, id.hashCode(), "ملخص مكالمة " + (name ?: number.ifBlank { "رقم مجهول" }), a.summary)
            return DONE
        } catch (e: IOException) {
            if (!finalAttempt) return RETRY
            fail(c, id, number, start, dur, f, "مفيش اتصال بالإنترنت أو الخدمة مشغولة. جرّب تاني.")
        } catch (e: JSONException) {
            fail(c, id, number, start, dur, f, "رد Gemini مش مفهوم. جرّب تاني.")
        } catch (e: Exception) {
            fail(c, id, number, start, dur, f, e.message?.takeIf { it.isNotBlank() } ?: "حصلت مشكلة غير معروفة")
        }
        return DONE
    }
}

class ProcessWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val c = applicationContext
        Store.load(c)
        val retryId = inputData.getString("retry")
        if (retryId != null) {
            val old = Store.items.firstOrNull { it.id == retryId } ?: return Result.success()
            val f = File(old.file)
            if (f.exists()) Pipeline.run(c, f, old.id, true)
            return Result.success()
        }
        if (Prefs.apiKey(c).isBlank() || !allFiles(c)) return Result.success()
        var again = false
        for (f in Recordings.recent(c)) {
            if (Pipeline.run(c, f, null, runAttemptCount >= 2) == Pipeline.RETRY) again = true
        }
        return if (again) Result.retry() else Result.success()
    }
}
