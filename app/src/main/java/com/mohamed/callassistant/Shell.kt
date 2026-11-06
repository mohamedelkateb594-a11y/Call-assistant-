@file:OptIn(ExperimentalFoundationApi::class)

package com.mohamed.callassistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TABS = 3

/** موضع الصفحة الحالي كرقم عشري (بيتحرك مع السحب لحظة بلحظة) */
private fun PagerState.pos(): Float =
    (currentPage + currentPageOffsetFraction).coerceIn(0f, (TABS - 1).toFloat())

// ---------- App ----------
@Composable
fun CallAssistantApp() {
    val dark = isSystemInDarkTheme()
    val pal = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(primary = Teal, background = pal.bg, surface = pal.card)
    else lightColorScheme(primary = Teal, background = pal.bg, surface = pal.card)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme) { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    val pal = LocalPal.current
    val pager = rememberPagerState { TABS }
    val scope = rememberCoroutineScope()
    var openId by remember { mutableStateOf<String?>(null) }
    val open = openId?.let { id -> Store.items.firstOrNull { it.id == id } }
    BackHandler(enabled = open != null) { openId = null }
    BackHandler(enabled = open == null && pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        if (open != null) {
            Detail(open) { openId = null }
        } else {
            val done = Store.items.count { it.status == "done" }
            Column(Modifier.fillMaxSize().background(HeaderBrush)) {
                TopBar(if (done == 0) "ملخصات مكالماتك بالذكاء الاصطناعي" else "$done مكالمة متلخصة")
                TabStrip(pager) { i -> scope.launch { pager.animateScrollToPage(i) } }
                // الورقة البيضاء: حافتها العليا فيها «نتوء» بينزل تحت التاب المختار ويتحرك معاه
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val side = 16.dp.toPx()
                            val tabW = (size.width - 2 * side) / TABS
                            val cx = side + (TABS - 0.5f - pager.pos()) * tabW
                            shape = NotchShape(cx, tabW * 0.28f, 12.dp.toPx(), 28.dp.toPx(), 12.dp.toPx())
                            clip = true
                        }
                        .background(pal.bg)
                ) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().navigationBarsPadding()) { page ->
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                // الصفحة بتنزل وتبهت وهي خارجة، وبتطلع وهي داخلة
                                val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                                alpha = 1f - off * 0.55f
                                translationY = off * 28.dp.toPx()
                            }
                        ) {
                            when (page) {
                                0 -> Home(onOpen = { openId = it }, goSettings = { scope.launch { pager.animateScrollToPage(2) } })
                                1 -> Appts { openId = it }
                                else -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Top bar ----------
@Composable
fun TopBar(sub: String) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Icon(
                Icons.Rounded.AutoAwesome, null, tint = Color(0xFFFFB199),
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(12.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Call Assistant", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
        }
    }
}

// ---------- Tabs (الشريط اللي فوق + المؤشر المتحرك) ----------
@Composable
fun TabStrip(pager: PagerState, onTab: (Int) -> Unit) {
    val items = listOf(
        "المكالمات" to Icons.Rounded.Call,
        "المواعيد" to Icons.Rounded.Event,
        "الإعدادات" to Icons.Rounded.Settings
    )
    // الحساب كله بإحداثيات فعلية (شمال→يمين) عشان المؤشر والنتوء يتطابقوا بالظبط
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).height(48.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        ) {
            val tabW = maxWidth / TABS
            // المؤشر: بيتبع الصفحة وإنت بتسحب، وبيتمطّ شوية وهو بيتحرك
            Box(
                Modifier
                    .offset { IntOffset(((TABS - 1 - pager.pos()) * (constraints.maxWidth / TABS.toFloat())).roundToInt(), 0) }
                    .width(tabW).fillMaxHeight()
                    .graphicsLayer {
                        val p = pager.pos()
                        val frac = abs(p - p.roundToInt())
                        scaleX = 1f + 0.35f * frac
                    }
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.30f), CircleShape)
            )
            Row(Modifier.fillMaxSize()) {
                // الترتيب معكوس لأن الواجهة عربي (أول تاب على اليمين)
                for (i in TABS - 1 downTo 0) {
                    val sel = (1f - abs(pager.pos() - i)).coerceIn(0f, 1f)
                    val tint = lerp(Color.White.copy(alpha = 0.62f), Color.White, sel)
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { onTab(i) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(items[i].second, null, tint = tint, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(items[i].first, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** شكل الورقة: ركنين مدوّرين + نتوء ناعم بينزل تحت التاب المختار */
class NotchShape(
    private val cx: Float, private val hw: Float, private val depth: Float,
    private val r: Float, private val s: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val left = (cx - hw).coerceAtLeast(r + s)
        val right = (cx + hw).coerceAtMost(w - r - s)
        val k = r * 0.4477f
        val p = Path()
        p.moveTo(0f, r)
        p.cubicTo(0f, r - k, r - k, 0f, r, 0f)
        p.lineTo(left - s, 0f)
        p.cubicTo(left, 0f, left, depth, left + s, depth)
        p.lineTo(right - s, depth)
        p.cubicTo(right, depth, right, 0f, right + s, 0f)
        p.lineTo(w - r, 0f)
        p.cubicTo(w - r + k, 0f, w, r - k, w, r)
        p.lineTo(w, h)
        p.lineTo(0f, h)
        p.close()
        return Outline.Generic(p)
    }
}
