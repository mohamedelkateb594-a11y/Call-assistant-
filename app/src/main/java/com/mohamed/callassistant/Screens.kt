package com.mohamed.callassistant

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- أجزاء مشتركة ----------
@Composable
fun FilterPill(text: String, sel: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Text(
        text, color = if (sel) Color.White else pal.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(if (sel) Teal else pal.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = pal.sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- شاشة القفل ----------
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HeaderBrush), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Call Assistant", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("التطبيق مقفول", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onUnlock, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) { Text("افتح بالبصمة أو القفل") }
        }
    }
}

// ---------- المهام ----------
@Composable
fun TaskCard(ref: TaskRef, onOpen: (String) -> Unit) {
    val c = LocalContext.current
    val pal = LocalPal.current
    val t = ref.t
    val r = ref.rec
    val who = r.name ?: r.number.ifBlank { "رقم مجهول" }.let { if (r.number.isBlank()) it else ltr(it) }
    val late = !t.done && t.dueMs > 0 && t.dueMs < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card)
            .clickable { onOpen(r.id) }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = t.done, onCheckedChange = { TaskOps.toggle(c, r.id, ref.idx) })
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                t.text, color = if (t.done) pal.sub else pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                who + (if (t.dueMs > 0) " • " + Fmt.dateShort(t.dueMs) + " " + Fmt.time(t.dueMs) else ""),
                color = pal.sub, fontSize = 12.sp
            )
            if (late) Text("فات ميعادها", color = pal.coralInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TasksScreen(onOpen: (String) -> Unit) {
    val all = Store.items.flatMap { r -> r.tasks.mapIndexed { i, t -> TaskRef(r, i, t) } }
    val mine = all.filter { !it.t.done && it.t.mine }.sortedBy { if (it.t.dueMs > 0) it.t.dueMs else Long.MAX_VALUE }
    val theirs = all.filter { !it.t.done && !it.t.mine }.sortedBy { if (it.t.dueMs > 0) it.t.dueMs else Long.MAX_VALUE }
    val done = all.filter { it.t.done }.take(10)
    Column(Modifier.fillMaxSize()) {
        Header(
            "المهام",
            if (mine.isEmpty() && theirs.isEmpty()) "مفيش مهام مفتوحة" else "${mine.size} عليك • ${theirs.size} مستنيها من الناس"
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (all.isEmpty()) item {
                EmptyState("مفيش مهام", "أي حاجة تتفق عليها في المكالمة (منك أو من الشخص التاني) هتلاقيها هنا وهيجيلك تذكير لو فيها معاد.")
            }
            if (mine.isNotEmpty()) {
                item { DayLabel("عليّا") }
                items(mine, key = { it.key }) { TaskCard(it, onOpen) }
            }
            if (theirs.isNotEmpty()) {
                item { DayLabel("متابعة (عليهم)") }
                items(theirs, key = { it.key }) { TaskCard(it, onOpen) }
            }
            if (done.isNotEmpty()) {
                item { DayLabel("اتنفذت") }
                items(done, key = { it.key }) { TaskCard(it, onOpen) }
            }
        }
    }
}

// ---------- ملف الشخص ----------
@Composable
fun PersonScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalContext.current
    val pal = LocalPal.current
    val recs = Store.items.filter { tail(it.number) == key }
    val number = recs.firstOrNull()?.number ?: ""
    val name = recs.firstOrNull { it.name != null }?.name
    val label = name ?: (if (number.isBlank()) "رقم مجهول" else ltr(number))
    var note by remember(key) { mutableStateOf(XPrefs.note(c, key)) }
    val now = System.currentTimeMillis()
    val openTasks = recs.flatMap { r -> r.tasks.mapIndexed { i, t -> TaskRef(r, i, t) } }.filter { !it.t.done }
    val appts = recs.filter { it.apptMs > now && it.apptState != "skipped" }.sortedBy { it.apptMs }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(HeaderBrush, HeaderShape).statusBarsPadding()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Avatar(name, number, 60)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (name != null && number.isNotBlank()) Text(ltr(number), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    Text(
                        recs.size.toString() + " مكالمة • " + openTasks.size + " مهمة مفتوحة • " + appts.size + " موعد جاي",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recs.isEmpty()) EmptyState("مفيش مكالمات مع الشخص ده", "ما لقيتش مكالمات متلخصة مع الرقم ده.")
            if (number.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(8.dp)); Text("اتصل") }
                OutlinedButton(
                    onClick = { CaTools.openWhatsApp(c, number, "") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Rounded.Chat, null); Spacer(Modifier.width(8.dp)); Text("واتساب") }
            }
            if (openTasks.isNotEmpty()) Section("مهام مفتوحة") {
                openTasks.forEach { ref ->
                    TaskCard(ref, onOpen)
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (appts.isNotEmpty()) Section("مواعيد جاية") {
                appts.forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 6.dp)) {
                        Text(Fmt.dateShort(r.apptMs) + " • " + Fmt.time(r.apptMs), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (r.apptNote.isNotBlank()) Text(r.apptNote, color = pal.sub, fontSize = 13.sp)
                    }
                }
            }
            Section("ملاحظاتك") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; XPrefs.setNote(c, key, it) },
                    label = { Text("أي حاجة عايز تفكرها عن الشخص ده") },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (recs.isNotEmpty()) Section("المكالمات (" + recs.size + ")") {
                recs.forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 8.dp)) {
                        Text(r.title, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(Fmt.dateShort(r.startMs) + " • " + Fmt.time(r.startMs), color = pal.sub, fontSize = 12.sp)
                        if (r.status == "done") Text(r.summary, color = pal.sub, fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ---------- اسأل عن مكالماتك ----------
@Composable
fun AskScreen(onBack: () -> Unit) {
    val c = LocalContext.current
    val pal = LocalPal.current
    var q by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val samples = listOf("إيه المواعيد اللي جاية؟", "مين طلب مني حاجة لسه معملتها؟", "كلّمت مين النهارده؟", "إيه المبالغ اللي اتكلمنا عليها؟")

    fun go(text: String) {
        if (text.isBlank() || loading) return
        loading = true
        answer = ""
        val app = c.applicationContext
        Thread {
            val res = try {
                Gemini.ask(app, text)
            } catch (e: Exception) {
                "ما قدرتش أجاوب: " + (e.message ?: "مشكلة في الاتصال")
            }
            Handler(Looper.getMainLooper()).post {
                answer = res
                loading = false
            }
        }.start()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(HeaderBrush, HeaderShape).statusBarsPadding()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFFFB199), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("اسأل عن مكالماتك", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("أجاوب من ملخصات كل مكالماتك", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                label = { Text("اسأل سؤالك") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { go(q) }, enabled = !loading && q.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "بفكر…" else "اسأل") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                samples.forEach { s ->
                    Text(
                        s, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(pal.tint)
                            .clickable { q = s; go(s) }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
            if (answer.isNotBlank()) Section("الإجابة") {
                Text(answer, color = pal.ink, fontSize = 15.sp, lineHeight = 24.sp)
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
