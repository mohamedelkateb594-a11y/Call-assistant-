package com.mohamed.callassistant

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.TimeZone

object Remind {
    fun canCalendar(c: Context): Boolean =
        has(c, Manifest.permission.WRITE_CALENDAR) && has(c, Manifest.permission.READ_CALENDAR)

    /** في آخر المكالمة: لو فيه ميعاد، اسأل هل أحفظه في التقويم ولا لأ */
    fun ask(c: Context, r: CallRecord) {
        if (r.apptMs <= System.currentTimeMillis()) return
        val nid = ("ap" + r.id).hashCode()
        val who = r.name ?: r.number.ifBlank { "رقم مجهول" }
        val body = Fmt.dateShort(r.apptMs) + " " + Fmt.time(r.apptMs) +
            (if (r.apptNote.isNotBlank()) "\n" + r.apptNote else "")
        Notif.post(
            c, nid, "تحفظ الموعد في التقويم؟ (مع $who)", body,
            listOf(
                Notif.receiverAction(c, "احفظه", ActionReceiver.SAVE_APPT, r.id, nid),
                Notif.receiverAction(c, "لأ", ActionReceiver.SKIP_APPT, r.id, nid)
            )
        )
    }

    /** بيحفظ الموعد في التقويم ويضبط التذكير. بيرجع true لو اتحفظ فعلاً. */
    fun confirm(c: Context, r: CallRecord): Boolean {
        if (r.apptState == "saved") return true
        if (!canCalendar(c)) return false
        if (!addCalendar(c, r)) return false
        scheduleAlarm(c, r)
        Store.upsert(c, r.copy(apptState = "saved"))
        return true
    }

    /** شاشة إضافة حدث في تطبيق التقويم — مش محتاجة إذن */
    fun insertIntent(r: CallRecord): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "موعد: " + (r.name ?: r.number))
            putExtra(CalendarContract.Events.DESCRIPTION, r.apptNote)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, r.apptMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, r.apptMs + 30 * 60_000L)
        }

    fun scheduleAlarm(c: Context, r: CallRecord) {
        val now = System.currentTimeMillis()
        if (r.apptMs <= now) return
        val at = if (r.apptMs - 30 * 60_000L > now) r.apptMs - 30 * 60_000L else r.apptMs
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, r.id.hashCode(),
            Intent(c, ReminderReceiver::class.java).putExtra("id", r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun addCalendar(c: Context, r: CallRecord): Boolean {
        return try {
            var calId = -1L
            c.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=? AND ${CalendarContract.Calendars.VISIBLE}=1",
                arrayOf("500"),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { if (it.moveToFirst()) calId = it.getLong(0) }
            if (calId < 0) return false
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "موعد: " + (r.name ?: r.number))
                put(CalendarContract.Events.DESCRIPTION, r.apptNote)
                put(CalendarContract.Events.DTSTART, r.apptMs)
                put(CalendarContract.Events.DTEND, r.apptMs + 30 * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return false
            val eid = uri.lastPathSegment?.toLongOrNull() ?: return false
            c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eid)
                put(CalendarContract.Reminders.MINUTES, 30)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
            true
        } catch (e: Exception) { false }
    }
}
