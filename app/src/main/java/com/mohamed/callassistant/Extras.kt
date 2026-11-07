package com.mohamed.callassistant

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs

// ---------- إعدادات إضافية ----------
object XPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("xprefs", Context.MODE_PRIVATE)
    fun lock(c: Context) = sp(c).getBoolean("lock", false)
    fun setLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("lock", v).apply()
    fun morning(c: Context) = sp(c).getBoolean("morning", false)
    fun setMorning(c: Context, v: Boolean) = sp(c).edit().putBoolean("morning", v).apply()
    fun autoDelete(c: Context) = sp(c).getBoolean("autoDelete", false)
    fun setAutoDelete(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoDelete", v).apply()

    // ملاحظات الأشخاص (مشفّرة)
    private fun notes(c: Context): JSONObject {
        val s = sp(c).getString("notes_enc", null) ?: return JSONObject()
        return try { JSONObject(DataCrypt.decStr(s)) } catch (e: Exception) { JSONObject() }
    }
    fun note(c: Context, key: String): String = notes(c).optString(key, "")
    fun setNote(c: Context, key: String, text: String) {
        val j = notes(c)
        j.put(key, text)
        try {
            sp(c).edit().putString("notes_enc", DataCrypt.encStr(j.toString())).apply()
        } catch (e: Exception) { }
    }
}

// ---------- تشفير البيانات (AES-GCM بمفتاح جوه Android Keystore) ----------
object DataCrypt {
    private const val ALIAS = "call_assistant_key"
    private val MAGIC = byteArrayOf(67, 65, 69, 49) // "CAE1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val ex = ks.getKey(ALIAS, null)
        if (ex is SecretKey) return ex
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun isEncrypted(b: ByteArray): Boolean =
        b.size > 20 && b[0] == MAGIC[0] && b[1] == MAGIC[1] && b[2] == MAGIC[2] && b[3] == MAGIC[3]

    fun enc(plain: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MAGIC + c.iv + ct
    }

    fun dec(b: ByteArray): String {
        val iv = b.copyOfRange(4, 16)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(b, 16, b.size - 16), Charsets.UTF_8)
    }

    fun encStr(s: String): String = Base64.encodeToString(enc(s), Base64.NO_WRAP)
    fun decStr(s: String): String = dec(Base64.decode(s, Base64.NO_WRAP))
}

// ---------- المهام وتذكيراتها ----------
object TaskOps {
    private fun pending(c: Context, id: String, i: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, (id + "#" + i).hashCode(),
            Intent(c, TaskReminderReceiver::class.java).putExtra("id", id).putExtra("i", i),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(c: Context, r: CallRecord) {
        val now = System.currentTimeMillis()
        val am = c.getSystemService(AlarmManager::class.java)
        r.tasks.forEachIndexed { i, t ->
            val pi = pending(c, r.id, i)
            if (t.done || t.dueMs <= now) am.cancel(pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.dueMs, pi)
        }
    }

    fun toggle(c: Context, id: String, i: Int) {
        val r = Store.items.firstOrNull { it.id == id } ?: return
        if (i !in r.tasks.indices) return
        val l = r.tasks.toMutableList()
        l[i] = l[i].copy(done = !l[i].done)
        val nr = r.copy(tasks = l)
        Store.upsert(c, nr)
        schedule(c, nr)
    }

    fun rescheduleAll(c: Context) {
        Store.items.forEach { if (it.tasks.isNotEmpty()) schedule(c, it) }
    }
}

class TaskRef(val rec: CallRecord, val idx: Int, val t: TaskItem) {
    val key: String = rec.id + "#" + idx
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val idx = i.getIntExtra("i", -1)
        Store.load(c)
        val r = Store.items.firstOrNull { it.id == id } ?: return
        val t = r.tasks.getOrNull(idx) ?: return
        if (t.done) return
        val who = r.name ?: r.number.ifBlank { "رقم مجهول" }
        val title = if (t.mine) "تذكير: مهمة عليك مع $who" else "متابعة: $who اتعهد بحاجة"
        Notif.post(c, ("t" + id + "#" + idx).hashCode(), title, t.text)
    }
}

class ExtrasBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.load(c)
        TaskOps.rescheduleAll(c)
        if (XPrefs.morning(c)) MorningJob.schedule(c)
    }
}

// ---------- ملخص الصبح ----------
object MorningJob {
    fun schedule(c: Context) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 60_000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        val delay = cal.timeInMillis - System.currentTimeMillis()
        val req = OneTimeWorkRequestBuilder<MorningWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("morning", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork("morning")
    }
}

class MorningWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val c = applicationContext
        if (!XPrefs.morning(c)) return Result.success()
        Store.load(c)
        val now = System.currentTimeMillis()
        val today = Fmt.dayKey(now)
        val appts = Store.items
            .filter { it.apptMs > now - 3_600_000L && it.apptState != "skipped" && Fmt.dayKey(it.apptMs) == today }
            .sortedBy { it.apptMs }
        val mine = Store.items.sumOf { r -> r.tasks.count { !it.done && it.mine } }
        val theirs = Store.items.sumOf { r -> r.tasks.count { !it.done && !it.mine } }
        val lines = ArrayList<String>()
        appts.forEach { r ->
            lines.add("موعد " + Fmt.time(r.apptMs) + " مع " + (r.name ?: r.number.ifBlank { "رقم مجهول" }))
        }
        if (mine > 0) lines.add("عليك $mine مهمة مفتوحة")
        if (theirs > 0) lines.add("لسه مستني $theirs حاجة من الناس")
        if (lines.isNotEmpty()) Notif.post(c, 8008, "صباح الخير — ملخص يومك", lines.joinToString("\n"))
        MorningJob.schedule(c)
        return Result.success()
    }
}

// ---------- قفل التطبيق (بصمة أو قفل الشاشة) ----------
object AppLock {
    fun available(c: Context): Boolean =
        c.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun prompt(a: Activity, onOk: () -> Unit, onFail: (Int) -> Unit) {
        val b = BiometricPrompt.Builder(a)
            .setTitle("Call Assistant")
            .setSubtitle("تأكد إنك صاحب الموبايل")
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            b.setDeviceCredentialAllowed(true)
        }
        val p = b.build()
        p.authenticate(CancellationSignal(), a.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onFail(errorCode)
            }
        })
    }
}

// ---------- أدوات متفرقة ----------
object CaTools {
    /** مواعيد بالقرب من بعض (أقل من ساعة) */
    fun conflicts(r: CallRecord): List<CallRecord> {
        if (r.apptMs <= 0 || r.apptState == "skipped") return emptyList()
        return Store.items.filter {
            it.id != r.id && it.apptMs > 0 && it.apptState != "skipped" && abs(it.apptMs - r.apptMs) < 3_600_000L
        }
    }

    /** رقم دولي لواتساب (افتراضياً مصر لو الرقم بيبدأ بصفر) */
    fun intl(number: String): String {
        val d = number.filter { it.isDigit() }
        return when {
            number.trim().startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.startsWith("0") -> "20" + d.drop(1)
            else -> d
        }
    }

    fun who(r: CallRecord): String = r.name ?: r.number.ifBlank { "رقم مجهول" }

    fun shareText(r: CallRecord): String {
        val sb = StringBuilder()
        sb.append("ملخص مكالمة مع ").append(who(r)).append('\n')
        sb.append(Fmt.dateShort(r.startMs)).append(' ').append(Fmt.time(r.startMs)).append("\n\n")
        sb.append(r.summary)
        if (r.keyPoints.isNotEmpty()) {
            sb.append("\n\nأهم النقط:")
            r.keyPoints.forEach { sb.append("\n- ").append(it) }
        }
        if (r.tasks.isNotEmpty()) {
            sb.append("\n\nالمهام:")
            r.tasks.forEach { sb.append("\n- ").append(if (it.mine) "[عليّا] " else "[عليه] ").append(it.text) }
        }
        if (r.apptMs > 0) {
            sb.append("\n\nالموعد: ").append(Fmt.dateShort(r.apptMs)).append(' ').append(Fmt.time(r.apptMs))
            if (r.apptNote.isNotBlank()) sb.append(" - ").append(r.apptNote)
        }
        return sb.toString()
    }

    /** رسالة تأكيد جاهزة للشخص اللي كلمته */
    fun confirmText(r: CallRecord): String {
        val nm = r.name ?: ""
        val sb = StringBuilder(if (nm.isBlank()) "أهلاً، " else "أهلاً أستاذ $nm، ")
        sb.append("تأكيداً لكلامنا في المكالمة.")
        if (r.apptMs > 0) {
            sb.append("\nالميعاد: ").append(Fmt.dateShort(r.apptMs)).append(" الساعة ").append(Fmt.time(r.apptMs))
            if (r.apptNote.isNotBlank()) sb.append(" (").append(r.apptNote).append(')')
        }
        val theirs = r.tasks.filter { !it.mine && !it.done }
        if (theirs.isNotEmpty()) {
            sb.append("\nالمطلوب منك:")
            theirs.forEach { sb.append("\n- ").append(it.text) }
        }
        val mine = r.tasks.filter { it.mine && !it.done }
        if (mine.isNotEmpty()) {
            sb.append("\nهعمله من ناحيتي:")
            mine.forEach { sb.append("\n- ").append(it.text) }
        }
        sb.append("\nشكراً ليك.")
        return sb.toString()
    }

    fun openWhatsApp(c: Context, number: String, text: String) {
        try {
            val n = intl(number)
            val url = if (n.isBlank()) "https://wa.me/?text=" + Uri.encode(text)
            else "https://wa.me/" + n + (if (text.isBlank()) "" else "?text=" + Uri.encode(text))
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(c, "ما قدرتش أفتح واتساب", Toast.LENGTH_SHORT).show()
        }
    }

    /** يحوّل مجلد اختاره المستخدم (Uri) لمسار فعلي على التخزين */
    fun treeToPath(uri: Uri): String? {
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { return null }
        val i = id.indexOf(':')
        if (i < 0) return null
        val vol = id.substring(0, i)
        val rel = id.substring(i + 1)
        val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/" + vol
        return if (rel.isBlank()) base else base + "/" + rel
    }

    /** استيراد ملف صوتي (مثلاً صوتية واتساب) وتلخيصه */
    fun importAudio(c: Context, uri: Uri) {
        val app = c.applicationContext
        val ui = Handler(Looper.getMainLooper())
        ui.post { Toast.makeText(app, "بحلّل التسجيل… هيظهر في المكالمات لما يخلص", Toast.LENGTH_LONG).show() }
        Thread {
            try {
                var name = "audio.m4a"
                app.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                    val ix = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ix >= 0 && cur.moveToFirst()) name = cur.getString(ix) ?: name
                }
                val ext = name.substringAfterLast('.', "m4a").lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "m4a" }
                val f = File(app.cacheDir, "call_import_" + System.currentTimeMillis() + "." + ext)
                app.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
                Store.load(app)
                Pipeline.run(app, f, null, true)
            } catch (e: Exception) {
                ui.post { Toast.makeText(app, "ما قدرتش أقرا الملف", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}
