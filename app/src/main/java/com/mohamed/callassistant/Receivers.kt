package com.mohamed.callassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Prefs.setInCall(c, true)
                val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!num.isNullOrBlank()) {
                    Store.load(c)
                    val prev = Store.lastDone(num)
                    if (prev != null) {
                        Overlay.show(c, prev, Contacts.nameFor(c, num) ?: prev.name ?: ltr(num))
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> Prefs.setInCall(c, true)
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Overlay.hide(c)
                if (Prefs.inCall(c)) {
                    Prefs.setInCall(c, false)
                    Jobs.scan(c, 12)
                }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Store.load(c)
        val r = Store.items.firstOrNull { it.id == id } ?: return
        if (r.apptMs <= 0) return
        val who = r.name ?: r.number.ifBlank { "رقم مجهول" }
        val note = if (r.apptNote.isNotBlank()) r.apptNote + "\n" else ""
        Notif.post(c, ("r" + id).hashCode(), "تذكير: موعد مع $who", note + Fmt.dateShort(r.apptMs) + " " + Fmt.time(r.apptMs))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.load(c)
        val now = System.currentTimeMillis()
        Store.items.filter { it.apptMs > now }.forEach { Remind.scheduleAlarm(c, it) }
    }
}
