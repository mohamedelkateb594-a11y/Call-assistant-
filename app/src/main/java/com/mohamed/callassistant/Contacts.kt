package com.mohamed.callassistant

import android.Manifest
import android.app.PendingIntent
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat

object Contacts {
    fun nameFor(c: Context, number: String): String? {
        if (number.isBlank() || !has(c, Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    /** شاشة إضافة جهة اتصال جاهزة بالرقم (والاسم لو معروف) — مش محتاجة إذن كتابة */
    fun insertIntent(name: String?, number: String): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            if (!name.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * في آخر المكالمة: لو الرقم مش في جهات الاتصال، اسأل هل أحفظه ولا لأ.
     * اسم المتصل (لو قاله) بيتسجل في البرنامج في الحالتين، وده بيتم قبل الاستدعاء.
     */
    fun ask(c: Context, r: CallRecord) {
        if (r.number.isBlank() || !has(c, Manifest.permission.READ_CONTACTS)) return
        if (nameFor(c, r.number) != null || Prefs.isDeclined(c, r.number)) return
        val nid = ("ct" + tail(r.number)).hashCode()
        val who = r.name
        val title = if (who != null) "تحفظ $who في جهات الاتصال؟" else "رقم غير محفوظ"
        val text = if (who != null)
            ltr(r.number) + " مش في جهات الاتصال. اسمه اتسجّل في البرنامج بس."
        else
            ltr(r.number) + " مش في جهات الاتصال. تحب تحفظه؟"
        val yes: NotificationCompat.Action =
            if (who != null && has(c, Manifest.permission.WRITE_CONTACTS)) {
                Notif.receiverAction(c, "احفظه", ActionReceiver.SAVE_CONTACT, r.id, nid)
            } else {
                val pi = PendingIntent.getActivity(
                    c, nid, insertIntent(who, r.number),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                NotificationCompat.Action.Builder(0, "احفظه", pi).build()
            }
        val no = Notif.receiverAction(c, "لأ", ActionReceiver.SKIP_CONTACT, r.id, nid)
        Notif.post(c, nid, title, text, listOf(yes, no))
    }

    fun save(c: Context, name: String, number: String): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
            c.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) { false }
    }
}
