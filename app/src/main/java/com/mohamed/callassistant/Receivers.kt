package com.mohamed.callassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                Prefs.setIncoming(c, false)
                val num = i.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                if (!num.isNullOrBlank()) {
                    Prefs.setLastOut(c, num)
                    showFor(c, num)
                }
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> onState(c, i)
        }
    }

    private fun onState(c: Context, i: Intent) {
        when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Prefs.setInCall(c, true)
                Prefs.setIncoming(c, true)
                val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!num.isNullOrBlank()) showFor(c, num)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Prefs.setInCall(c, true)
                // مكالمة صادرة: لو لسه ما ظهرتش نافذة، دوّر على الرقم
                if (!Prefs.incoming(c) && !Overlay.visible()) findOutgoing(c)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Overlay.hide(c)
                Notif.clearLive(c)
                Prefs.setIncoming(c, false)
                Prefs.setLastOut(c, "")
                if (Prefs.inCall(c)) {
                    Prefs.setInCall(c, false)
                    Jobs.scan(c, 12)
                }
            }
        }
    }

    /** لو بث الاتصال الصادر ما وصلش (الإذن ناقص)، نقرا الرقم من سجل المكالمات */
    private fun findOutgoing(c: Context) {
        val app = c.applicationContext
        val known = Prefs.lastOut(app)
        if (known != null) { showFor(app, known); return }
        val started = System.currentTimeMillis()
        val pending = goAsync()
        Thread {
            try {
                for (n in 1..5) {
                    Thread.sleep(1200)
                    if (!Prefs.inCall(app)) break
                    val num = CallLogs.recentOutgoing(app, started - 30_000L)
                    if (num != null) {
                        Handler(Looper.getMainLooper()).post { showFor(app, num) }
                        break
                    }
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** النافذة العائمة + إشعار احتياطي بآخر تفاصيل مكالمة مع نفس الرقم */
    private fun showFor(c: Context, num: String) {
        val app = c.applicationContext
        if (Overlay.isShowing(num)) return
        Store.load(app)
        val prev = Store.lastDone(num) ?: return
        val who = Contacts.nameFor(app, num) ?: Prefs.knownName(app, num) ?: prev.name ?: ltr(num)
        Overlay.show(app, prev, who, 0, num)
        Notif.live(app, prev, who)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Store.load(c)
        val r = Store.items.firstOrNull { it.id == id } ?: return
        if (r.apptMs <= 0) return
        if (r.apptState == "pending" || r.apptState == "skipped") return
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
        Store.items
            .filter { it.apptMs > now && (it.apptState == "saved" || it.apptState == "") }
            .forEach { Remind.scheduleAlarm(c, it) }
    }
}

/** أزرار «احفظه / لأ» اللي في إشعارات آخر المكالمة */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val SAVE_CONTACT = "com.mohamed.callassistant.SAVE_CONTACT"
        const val SKIP_CONTACT = "com.mohamed.callassistant.SKIP_CONTACT"
        const val SAVE_APPT = "com.mohamed.callassistant.SAVE_APPT"
        const val SKIP_APPT = "com.mohamed.callassistant.SKIP_APPT"
    }

    private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val nid = i.getIntExtra("nid", 0)
        Store.load(c)
        val r = Store.items.firstOrNull { it.id == id }
        var done = true
        if (r != null) {
            when (i.action) {
                SAVE_CONTACT -> {
                    val nm = r.name
                    if (nm != null && r.number.isNotBlank() && Contacts.save(c, nm, r.number)) {
                        toast(c, "اتحفظ في جهات الاتصال")
                    } else {
                        toast(c, "ما قدرتش أحفظ الرقم، جرّب من داخل البرنامج")
                        done = false
                    }
                }
                SKIP_CONTACT -> Prefs.markDeclined(c, r.number)
                SAVE_APPT -> {
                    if (Remind.confirm(c, r)) {
                        toast(c, "اتحفظ الموعد في التقويم")
                    } else {
                        toast(c, "ما اتحفظش. فعّل إذن التقويم أو احفظه من داخل البرنامج")
                        done = false
                    }
                }
                SKIP_APPT -> Store.upsert(c, r.copy(apptState = "skipped"))
            }
        }
        if (done && nid != 0) NotificationManagerCompat.from(c).cancel(nid)
    }
}
