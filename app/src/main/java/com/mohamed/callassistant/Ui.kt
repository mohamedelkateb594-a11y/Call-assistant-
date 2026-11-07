package com.mohamed.callassistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Theme ----------
// الباليت: كحلي عميق + نيلي للأسطح والعناوين + مرجاني للأزرار والتنبيهات
// (الاسم Teal فضل زي ما هو عشان باقي الملفات، بس قيمته بقت المرجاني الأساسي)
val Teal = Color(0xFFE8505B)
val Deep = Color(0xFF15173D)

class Pal(
    val bg: Color, val card: Color, val ink: Color, val sub: Color,
    val tint: Color, val accent: Color, val coralTint: Color, val coralInk: Color
)

val LightPal = Pal(
    Color(0xFFF4F5FB), Color.White, Color(0xFF16182F), Color(0xFF5F6285),
    Color(0xFFE6E8FA), Color(0xFF3D42B0), Color(0xFFFFE7E8), Color(0xFFB02F3A)
)
val DarkPal = Pal(
    Color(0xFF0E1026), Color(0xFF181B3D), Color(0xFFE9EAFB), Color(0xFF9EA1C9),
    Color(0xFF262A66), Color(0xFFB4B8FF), Color(0xFF3D2233), Color(0xFFFF9AA2)
)
val LocalPal = compositionLocalOf { LightPal }

val Avatars = listOf(
    listOf(Color(0xFF5B5FE0), Color(0xFF2B2E7E)),
    listOf(Color(0xFFE8505B), Color(0xFF9E2A3C)),
    listOf(Color(0xFF8B5CF6), Color(0xFF4C2A9E)),
    listOf(Color(0xFF3F8EF2), Color(0xFF1E3F8F)),
    listOf(Color(0xFFF2905B), Color(0xFFA04A2A)),
    listOf(Color(0xFF6C74E8), Color(0xFF343A9A))
)

var tick by mutableIntStateOf(0)

// (App / Root / شريط التابات اتنقلوا لملف Shell.kt)

// ---------- Shared pieces ----------
val HeaderBrush = Brush.linearGradient(listOf(Deep, Color(0xFF1F2366), Color(0xFF2F3394)))
val HeaderShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

/** عنوان الصفحة جوه الورقة البيضاء (الهيدر الملون بقى في Shell) */
@Composable
fun Header(title: String, sub: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)) {
        Text(title, color = pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = pal.sub, fontSize = 14.sp)
        content()
    }
}

@Composable
fun Avatar(name: String?, number: String, size: Int) {
    val key = tail(number).ifBlank { name ?: "x" }
    val g = Avatars[Math.floorMod(key.hashCode(), Avatars.size)]
    Box(
        Modifier.size(size.dp).background(Brush.linearGradient(g), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val ch = name?.trim()?.firstOrNull()
        if (ch != null) Text(ch.toString(), color = Color.White, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold)
        else Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size((size * 0.46f).dp))
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun DayLabel(t: String) {
    Text(t, color = LocalPal.current.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card).padding(16.dp)) {
        Text(title, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(pal.tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = pal.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = pal.sub, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

fun setupMissing(c: android.content.Context): Boolean =
    Prefs.apiKey(c).isBlank() ||
        !has(c, Manifest.permission.READ_PHONE_STATE) ||
        !has(c, Manifest.permission.READ_CALL_LOG) ||
        !allFiles(c)

// ---------- Home ----------
@Composable
fun Home(onOpen: (String) -> Unit, goSettings: () -> Unit, onAsk: () -> Unit) {
    val c = LocalContext.current
    val pal = LocalPal.current
    val t = tick
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val all = Store.items
    val now = System.currentTimeMillis()
    val upcoming = all.count { it.apptMs > now && it.apptState != "skipped" }
    val needle = q.trim()
    val cats = all.map { it.category }.filter { it.isNotBlank() }.distinct()
    val base = when (filter) {
        "all" -> all
        "star" -> all.filter { it.starred }
        else -> all.filter { it.category == filter }
    }
    val list = if (needle.isEmpty()) base else base.filter { r ->
        listOf(r.name ?: "", r.number, r.summary, r.title, r.transcript).any { it.contains(needle, true) }
    }
    val need = remember(t) { setupMissing(c) }

    Column(Modifier.fillMaxSize()) {
        Header(
            "مكالماتك",
            if (all.isEmpty()) "ملخصات مكالماتك هتظهر هنا"
            else "${all.count { it.status == "done" }} مكالمة متلخصة • $upcoming موعد جاي"
        ) {
            Spacer(Modifier.height(14.dp))
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                placeholder = { Text("دوّر بالاسم أو الرقم أو الكلام", color = pal.sub) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = pal.sub) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = pal.card,
                    unfocusedContainerColor = pal.card,
                    focusedTextColor = pal.ink, unfocusedTextColor = pal.ink,
                    cursorColor = pal.accent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (need) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.coralTint)
                        .clickable { goSettings() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("كمّل الإعداد عشان البرنامج يشتغل", color = pal.coralInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("ناقص مفتاح Gemini أو بعض الأذونات", color = pal.coralInk, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.Settings, null, tint = pal.coralInk)
                }
            }
            if (all.any { it.status == "done" }) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.tint)
                        .clickable { onAsk() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = pal.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("اسأل عن أي مكالمة", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("مثلاً: إيه اللي اتفقنا عليه مع أحمد؟", color = pal.sub, fontSize = 12.sp)
                    }
                }
            }
            if (all.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("الكل", filter == "all") { filter = "all" }
                    FilterPill("★ المميزة", filter == "star") { filter = "star" }
                    cats.forEach { ct -> FilterPill(ct, filter == ct) { filter = ct } }
                }
            }
            if (list.isEmpty()) item {
                if (needle.isEmpty() && filter == "all") EmptyState(
                    "لسه مفيش مكالمات متلخصة",
                    "فعّل التسجيل التلقائي في تطبيق الاتصال. أول ما تخلّص مكالمة هيظهر ملخصها هنا."
                ) else EmptyState("مفيش نتيجة", "جرّب كلمة تانية أو فلتر تاني.")
            }
            list.groupBy { Fmt.dayKey(it.startMs) }.forEach { (_, rows) ->
                item(key = "h" + rows.first().id) { DayLabel(Fmt.dayLabel(rows.first().startMs)) }
                items(rows, key = { r -> r.id }) { r -> CallCard(r) { onOpen(r.id) } }
            }
        }
    }
}

@Composable
fun CallCard(r: CallRecord, onClick: () -> Unit) {
    val pal = LocalPal.current
    val label = r.name ?: r.number.ifBlank { "رقم مجهول" }.let { if (r.number.isBlank()) it else ltr(it) }
    val openTasks = r.tasks.count { !it.done }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(r.name, r.number, 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = pal.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (r.starred) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFB020), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Fmt.time(r.startMs), color = pal.sub, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (r.status == "done") r.summary else r.error,
                color = pal.sub, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.status != "done") {
                    Chip("ما اتلخصتش — افتحها وجرّب تاني", pal.coralTint, pal.coralInk)
                } else {
                    if (r.apptMs > 0 && r.apptState != "skipped") Chip("موعد: " + Fmt.dateShort(r.apptMs) + " " + Fmt.time(r.apptMs), pal.coralTint, pal.coralInk)
                    if (openTasks > 0) Chip(openTasks.toString() + " مهمة", pal.tint, pal.accent)
                    if (r.category.isNotBlank()) Chip(r.category, pal.tint, pal.accent)
                }
            }
        }
    }
}

// ---------- Detail ----------
@Composable
fun Detail(r: CallRecord, onBack: () -> Unit, onNav: (String) -> Unit) {
    val c = LocalContext.current
    val pal = LocalPal.current
    var showT by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var contactSaved by remember { mutableStateOf(false) }
    val label = r.name ?: r.number.ifBlank { "رقم مجهول" }.let { if (r.number.isBlank()) it else ltr(it) }
    val canSaveContact = remember(r.id, contactSaved) {
        !contactSaved && r.number.isNotBlank() &&
            has(c, Manifest.permission.READ_CONTACTS) && Contacts.nameFor(c, r.number) == null
    }
    val clash = CaTools.conflicts(r)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(HeaderBrush, HeaderShape).statusBarsPadding()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { Store.upsert(c, r.copy(starred = !r.starred)) }) {
                    Icon(
                        if (r.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "نجمة",
                        tint = if (r.starred) Color(0xFFFFC53D) else Color.White
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp).clickable {
                    if (r.number.isNotBlank()) onNav("p:" + tail(r.number))
                }
            ) {
                Avatar(r.name, r.number, 60)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (r.name != null && r.number.isNotBlank()) Text(ltr(r.number), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    Text(
                        Fmt.dateShort(r.startMs) + " • " + Fmt.time(r.startMs) + (if (r.durationSec > 0) " • " + Fmt.dur(r.durationSec) else ""),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r.status == "done") {
                Section("الملخص") {
                    Text(r.title, color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(r.summary, color = pal.ink, fontSize = 15.sp, lineHeight = 23.sp)
                }
                if (r.keyPoints.isNotEmpty()) Section("أهم النقط") {
                    r.keyPoints.forEach { p ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.padding(top = 9.dp).size(6.dp).background(Teal, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(p, color = pal.ink, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                if (r.apptMs > 0) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.coralTint).padding(16.dp)
                ) {
                    Text("الموعد", color = pal.coralInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(Fmt.dateShort(r.apptMs) + " • " + Fmt.time(r.apptMs), color = pal.coralInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (r.apptNote.isNotBlank()) Text(r.apptNote, color = pal.coralInk, fontSize = 14.sp)
                    if (clash.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "⚠ تعارض مع موعد مع " + CaTools.who(clash.first()) + " الساعة " + Fmt.time(clash.first().apptMs),
                            color = pal.coralInk, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (r.apptState == "pending" || r.apptState == "skipped") {
                        Text(
                            if (r.apptState == "pending") "لسه ما اتحفظش في التقويم" else "مش محفوظ في التقويم",
                            color = pal.coralInk, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (Remind.canCalendar(c)) {
                                        val ok = Remind.confirm(c, r)
                                        Toast.makeText(c, if (ok) "اتحفظ في التقويم" else "ما قدرتش أحفظ الموعد", Toast.LENGTH_SHORT).show()
                                    } else {
                                        c.startActivity(Remind.insertIntent(r))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal)
                            ) { Text("احفظه في التقويم") }
                            if (r.apptState == "pending") TextButton(onClick = { Store.upsert(c, r.copy(apptState = "skipped")) }) {
                                Text("لأ", color = pal.coralInk)
                            }
                        }
                    } else {
                        Text("اتحفظ في التقويم وهيجيلك تذكير قبلها بنص ساعة", color = pal.coralInk, fontSize = 12.sp)
                    }
                }
                if (r.tasks.isNotEmpty()) Section("المهام") {
                    r.tasks.forEachIndexed { i, tk ->
                        Row(
                            Modifier.fillMaxWidth().clickable { TaskOps.toggle(c, r.id, i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = tk.done, onCheckedChange = { TaskOps.toggle(c, r.id, i) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tk.text, color = if (tk.done) pal.sub else pal.ink, fontSize = 15.sp,
                                    textDecoration = if (tk.done) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Text(
                                    (if (tk.mine) "عليّا" else "عليه") +
                                        (if (tk.dueMs > 0) " • " + Fmt.dateShort(tk.dueMs) + " " + Fmt.time(tk.dueMs) else ""),
                                    color = pal.sub, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                if (r.facts.isNotEmpty()) Section("بيانات مهمة (مبالغ وعناوين)") {
                    r.facts.forEach { f ->
                        Text("• " + f, color = pal.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                if (r.transcript.isNotBlank()) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card).animateContentSize()
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showT = !showT }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نص المكالمة", color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (showT) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = pal.sub)
                    }
                    if (showT) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        r.transcript.lines().filter { it.isNotBlank() }.forEach { ln ->
                            val mine = ln.trimStart().startsWith("أنا")
                            Text(
                                ln, color = if (mine) pal.accent else pal.ink, fontSize = 14.sp, lineHeight = 22.sp,
                                fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                Section("حصلت مشكلة") { Text(r.error, color = pal.ink, fontSize = 15.sp) }
                Button(
                    onClick = {
                        Jobs.retry(c, r.id)
                        Toast.makeText(c, "بحاول تاني…", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("حاول تاني") }
            }
            if (canSaveContact) OutlinedButton(
                onClick = {
                    val nm = r.name
                    if (nm != null && has(c, Manifest.permission.WRITE_CONTACTS)) {
                        val ok = Contacts.save(c, nm, r.number)
                        Toast.makeText(c, if (ok) "اتحفظ في جهات الاتصال" else "ما قدرتش أحفظ الرقم", Toast.LENGTH_SHORT).show()
                        if (ok) contactSaved = true
                    } else {
                        c.startActivity(Contacts.insertIntent(nm, r.number))
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("احفظ الرقم في جهات الاتصال") }
            if (r.status == "done") Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, CaTools.shareText(r))
                        c.startActivity(Intent.createChooser(i, "شارك الملخص"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("شارك الملخص") }
                OutlinedButton(
                    onClick = { CaTools.openWhatsApp(c, r.number, CaTools.confirmText(r)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Rounded.Chat, null); Spacer(Modifier.width(8.dp)); Text("تأكيد واتساب") }
            }
            if (r.number.isNotBlank()) OutlinedButton(
                onClick = { onNav("p:" + tail(r.number)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.Person, null); Spacer(Modifier.width(8.dp)); Text("ملف الشخص (كل مكالماته ومهامه)") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.number.isNotBlank()) Button(
                    onClick = { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(r.number)))) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(8.dp)); Text("اتصل") }
                TextButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, null, tint = pal.coralInk)
                    Spacer(Modifier.width(6.dp))
                    Text("مسح", color = pal.coralInk)
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("تمسح الملخص ده؟") },
        text = { Text("هيتمسح من البرنامج بس. ملف التسجيل هيفضل زي ما هو.") },
        confirmButton = { TextButton(onClick = { Store.delete(c, r.id); confirm = false; onBack() }) { Text("امسح", color = pal.coralInk) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } }
    )
}

// ---------- Appointments ----------
@Composable
fun Appts(onOpen: (String) -> Unit) {
    val pal = LocalPal.current
    val now = System.currentTimeMillis()
    val up = Store.items.filter { it.apptMs > now && it.apptState != "skipped" }.sortedBy { it.apptMs }
    val past = Store.items.filter { it.apptMs in 1..now && it.apptState != "skipped" }.sortedByDescending { it.apptMs }.take(10)
    Column(Modifier.fillMaxSize()) {
        Header("مواعيدك", if (up.isEmpty()) "مفيش مواعيد جاية" else "${up.size} موعد جاي")
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (up.isEmpty() && past.isEmpty()) item {
                EmptyState("مفيش مواعيد", "لما تتفق مع حد على ميعاد في المكالمة هسألك تحفظه في التقويم ولا لأ.")
            }
            items(up, key = { r -> "u" + r.id }) { r -> ApptCard(r, false) { onOpen(r.id) } }
            if (past.isNotEmpty()) {
                item { DayLabel("فاتت") }
                items(past, key = { r -> "p" + r.id }) { r -> ApptCard(r, true) { onOpen(r.id) } }
            }
        }
    }
}

@Composable
fun ApptCard(r: CallRecord, past: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val label = r.name ?: r.number.ifBlank { "رقم مجهول" }.let { if (r.number.isBlank()) it else ltr(it) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.size(58.dp).background(if (past) pal.tint else pal.coralTint, RoundedCornerShape(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            val fg = if (past) pal.accent else pal.coralInk
            Text(Fmt.dayNum(r.apptMs), color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(Fmt.month(r.apptMs), color = fg, fontSize = 11.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = pal.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.apptNote.isNotBlank()) Text(r.apptNote, color = pal.sub, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Fmt.dateShort(r.apptMs) + " • " + Fmt.time(r.apptMs), color = pal.sub, fontSize = 12.sp)
            if (r.apptState == "pending") Text("لسه ما اتحفش في التقويم", color = pal.coralInk, fontSize = 12.sp)
            if (!past && CaTools.conflicts(r).isNotEmpty()) {
                Text("⚠ فيه تعارض مع موعد تاني", color = pal.coralInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------- Settings ----------
@Composable
fun PermRow(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(if (ok) Teal else pal.coralTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null,
                tint = if (ok) Color.White else pal.coralInk, modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = pal.sub, fontSize = 12.sp)
        }
        if (!ok) Button(
            onClick = onClick, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("فعّل", fontSize = 13.sp) }
    }
}

@Composable
fun SettingsScreen() {
    val c = LocalContext.current
    val pal = LocalPal.current
    val t = tick
    var key by remember { mutableStateOf(Prefs.apiKey(c)) }
    var model by remember { mutableStateOf(Prefs.model(c)) }
    var folder by remember { mutableStateOf(Prefs.folder(c)) }
    var show by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(XPrefs.lock(c)) }
    var morningOn by remember { mutableStateOf(XPrefs.morning(c)) }
    var delOn by remember { mutableStateOf(XPrefs.autoDelete(c)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val p = CaTools.treeToPath(uri)
            if (p != null) {
                folder = p
                Prefs.setFolder(c, p)
                Toast.makeText(c, "اتحدد مجلد التسجيلات", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(c, "المجلد ده مش مدعوم، اكتب المسار بإيدك", Toast.LENGTH_LONG).show()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) CaTools.importAudio(c, uri)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                c.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(c, "اتحفظت النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(c, "ما قدرتش أحفظ النسخة", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val txt = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val n = Store.importJson(c, txt)
                TaskOps.rescheduleAll(c)
                Toast.makeText(c, "اتسترجعت $n مكالمة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(c, "الملف ده مش نسخة من البرنامج", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val p1 = listOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.PROCESS_OUTGOING_CALLS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS
    ).all { has(c, it) }
    val p2 = has(c, Manifest.permission.WRITE_CALENDAR) && has(c, Manifest.permission.READ_CALENDAR) &&
        (Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS))
    val p3 = allFiles(c)
    val p4 = Settings.canDrawOverlays(c)

    fun appSettings() {
        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)))
    }

    Column(Modifier.fillMaxSize()) {
        Header("الإعدادات", "جهّز البرنامج مرة واحدة")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("التلخيص بالذكاء الاصطناعي") {
                OutlinedTextField(
                    value = key, onValueChange = { key = it; Prefs.setApiKey(c, it) },
                    label = { Text("مفتاح Gemini") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "إخفاء" else "إظهار", fontSize = 12.sp) } },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("المفتاح مجاني من Google AI Studio (aistudio.google.com).", color = pal.sub, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it; Prefs.setModel(c, it) },
                    label = { Text("اسم الموديل") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
            }
            Section("الأذونات") {
                PermRow("الاتصال والسجل وجهات الاتصال", "عشان أعرف الرقم (وارد وصادر) وأحفظ الاسم", p1) {
                    launcher.launch(arrayOf(
                        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.PROCESS_OUTGOING_CALLS,
                        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS
                    ))
                }
                PermRow("الإشعارات والتقويم", "للملخصات والتذكيرات", p2) {
                    val l = arrayListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
                    launcher.launch(l.toTypedArray())
                }
                PermRow("الوصول لملفات التسجيل", "عشان أقرا تسجيل المكالمة بعد ما تخلص", p3) {
                    try {
                        c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + c.packageName)))
                    } catch (e: Exception) {
                        c.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
                PermRow("الظهور فوق التطبيقات", "عشان نافذة آخر ملخص تظهر وقت المكالمة", p4) {
                    c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))
                }
            }
            Section("الخصوصية والأمان") {
                SwitchRow("قفل التطبيق", "بالبصمة أو قفل الشاشة عند فتح البرنامج", lockOn) { on ->
                    if (on && !AppLock.available(c)) {
                        Toast.makeText(c, "فعّل قفل الشاشة (بصمة أو رقم سري) في إعدادات الموبايل الأول", Toast.LENGTH_LONG).show()
                    } else {
                        lockOn = on
                        XPrefs.setLock(c, on)
                    }
                }
                SwitchRow("مسح التسجيل بعد التلخيص", "ملف الصوت بياتمسح من الموبايل بعد ما الملخص يجهز", delOn) { on ->
                    delOn = on
                    XPrefs.setAutoDelete(c, on)
                }
                SwitchRow("ملخص الصبح", "إشعار الساعة 8 بمواعيد اليوم والمهام المفتوحة", morningOn) { on ->
                    morningOn = on
                    XPrefs.setMorning(c, on)
                    if (on) MorningJob.schedule(c) else MorningJob.cancel(c)
                }
                Spacer(Modifier.height(4.dp))
                Text("بيانات المكالمات متخزّنة مشفّرة على الموبايل.", color = pal.sub, fontSize = 12.sp)
            }
            Section("شغل التسجيلات") {
                Text(
                    "1) فعّل تسجيل المكالمات التلقائي من تطبيق الاتصال → الإعدادات → تسجيل المكالمات.\n" +
                        "2) من إعدادات التطبيق → أذونات أخرى: فعّل «عرض النوافذ المنبثقة أثناء التشغيل في الخلفية».\n" +
                        "3) فعّل «التشغيل التلقائي» وخلّي توفير البطارية «بدون قيود».",
                    color = pal.ink, fontSize = 14.sp, lineHeight = 22.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { appSettings() }, shape = RoundedCornerShape(14.dp)) { Text("افتح إعدادات التطبيق") }
            }
            Section("مجلد التسجيلات") {
                Text(
                    if (folder.isBlank()) "لسه ما اخترتش مجلد. البرنامج هيدوّر في الأماكن المعروفة." else folder,
                    color = pal.ink, fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { treePicker.launch(null) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) { Text("اختار المجلد") }
                    if (folder.isNotBlank()) OutlinedButton(
                        onClick = { folder = ""; Prefs.setFolder(c, "") },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("مسح الاختيار") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it; Prefs.setFolder(c, it) },
                    label = { Text("أو اكتب المسار بإيدك (مثال: MIUI/sound_recorder/call_rec)") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*", "video/*")) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                ) { Text("استيراد ملف صوتي (مثلاً صوتية واتساب)") }
            }
            Section("نسخ احتياطي") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exporter.launch("call-assistant-backup.json") },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("حفظ نسخة") }
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("استرجاع نسخة") }
                }
                Spacer(Modifier.height(4.dp))
                Text("النسخة بتتحفظ كملف عادي (غير مشفّر) في المكان اللي تختاره، فحافظ عليها في مكان آمن.", color = pal.sub, fontSize = 12.sp)
            }
            Section("تجربة") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!Settings.canDrawOverlays(c)) {
                                Toast.makeText(c, "فعّل «الظهور فوق التطبيقات» الأول", Toast.LENGTH_SHORT).show()
                            } else {
                                val now = System.currentTimeMillis()
                                Overlay.show(
                                    c,
                                    CallRecord("test", "", null, now - 86_400_000L, 120, "تجربة", "ده مثال لنافذة آخر ملخص. هتظهر كده لما نفس الرقم يكلمك تاني.", emptyList(), "", now + 86_400_000L, "", "", "done", ""),
                                    "اسم تجريبي", 8000
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("جرّب النافذة") }
                    OutlinedButton(
                        onClick = {
                            Jobs.scan(c, 0)
                            Toast.makeText(c, "بدأ فحص التسجيلات الجديدة", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("افحص تسجيلات جديدة") }
                }
            }
            Text(
                "ملاحظة: صوت المكالمة بيتبعت لخدمة Gemini عشان يتحوّل لنص وملخص. اتأكد إن ده مسموح في شغلك ومع الناس اللي بتكلمهم.",
                color = pal.sub, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
