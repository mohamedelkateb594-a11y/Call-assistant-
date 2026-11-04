package com.mohamed.callassistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

fun has(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
fun tail(n: String?): String = (n ?: "").filter { it.isDigit() }.takeLast(9)
fun ltr(s: String) = "\u202A$s\u202C"
fun allFiles(c: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else has(c, Manifest.permission.READ_EXTERNAL_STORAGE)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun apiKey(c: Context) = sp(c).getString("key", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("key", v.trim()).apply()
    fun model(c: Context): String = (sp(c).getString("model", "") ?: "").ifBlank { "gemini-2.5-flash" }
    fun setModel(c: Context, v: String) = sp(c).edit().putString("model", v.trim()).apply()
    fun folder(c: Context) = sp(c).getString("folder", "") ?: ""
    fun setFolder(c: Context, v: String) = sp(c).edit().putString("folder", v.trim()).apply()
    fun inCall(c: Context) = sp(c).getBoolean("inCall", false)
    fun setInCall(c: Context, v: Boolean) = sp(c).edit().putBoolean("inCall", v).apply()
    private fun doneList(c: Context): List<String> =
        (sp(c).getString("done_v2", "") ?: "").split('\n').filter { it.isNotBlank() }
    fun processed(c: Context): Set<String> = doneList(c).toSet()
    fun markProcessed(c: Context, path: String) {
        val l = doneList(c).toMutableList()
        l.remove(path)
        l.add(path)
        sp(c).edit().putString("done_v2", l.takeLast(300).joinToString("\n")).apply()
    }
}

object Fmt {
    private val days = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val months = arrayOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

    fun time(ms: Long): String {
        val c = cal(ms)
        val h = c.get(Calendar.HOUR)
        val hh = if (h == 0) 12 else h
        val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$hh:$m " + (if (c.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م")
    }
    fun dayNum(ms: Long) = cal(ms).get(Calendar.DAY_OF_MONTH).toString()
    fun month(ms: Long) = months[cal(ms).get(Calendar.MONTH)]
    fun dateShort(ms: Long): String {
        val c = cal(ms)
        return "${days[c.get(Calendar.DAY_OF_WEEK) - 1]} ${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}"
    }
    fun dayKey(ms: Long): Int {
        val c = cal(ms)
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
    fun dayLabel(ms: Long): String {
        val now = System.currentTimeMillis()
        val k = dayKey(ms)
        if (k == dayKey(now)) return "النهارده"
        if (k == dayKey(now - 86_400_000L)) return "امبارح"
        val y = cal(ms).get(Calendar.YEAR)
        return dateShort(ms) + if (y != cal(now).get(Calendar.YEAR)) " $y" else ""
    }
    fun dur(s: Int): String =
        if (s < 60) "$s ث" else "${s / 60} د" + (if (s % 60 > 0) " ${s % 60} ث" else "")
}

object Notif {
    const val CH = "calls"
    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "ملخصات وتذكيرات", NotificationManager.IMPORTANCE_HIGH))
    }
    fun post(c: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && !has(c, Manifest.permission.POST_NOTIFICATIONS)) return
        channels(c)
        val pi = PendingIntent.getActivity(
            c, id,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF0E7C86.toInt())
            .build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (e: SecurityException) { }
    }
}

object Jobs {
    private fun net() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun scan(c: Context, delaySec: Long) {
        val req = OneTimeWorkRequestBuilder<ProcessWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .setConstraints(net())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("scan", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
    fun retry(c: Context, id: String) {
        val req = OneTimeWorkRequestBuilder<ProcessWorker>()
            .setInputData(workDataOf("retry" to id))
            .setConstraints(net())
            .build()
        WorkManager.getInstance(c).enqueue(req)
    }
}
