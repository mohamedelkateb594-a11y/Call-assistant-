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
    fun schedule(c: Context, r: CallRecord) {
        scheduleAlarm(c, r)
        if (has(c, Manifest.permission.WRITE_CALENDAR) && has(c, Manifest.permission.READ_CALENDAR)) addCalendar(c, r)
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

    private fun addCalendar(c: Context, r: CallRecord) {
        try {
            var calId = -1L
            c.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=? AND ${CalendarContract.Calendars.VISIBLE}=1",
                arrayOf("500"),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { if (it.moveToFirst()) calId = it.getLong(0) }
            if (calId < 0) return
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "موعد: " + (r.name ?: r.number))
                put(CalendarContract.Events.DESCRIPTION, r.apptNote)
                put(CalendarContract.Events.DTSTART, r.apptMs)
                put(CalendarContract.Events.DTEND, r.apptMs + 30 * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return
            val eid = uri.lastPathSegment?.toLongOrNull() ?: return
            c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eid)
                put(CalendarContract.Reminders.MINUTES, 30)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        } catch (e: Exception) { }
    }
}
