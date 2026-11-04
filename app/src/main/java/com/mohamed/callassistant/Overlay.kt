package com.mohamed.callassistant

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object Overlay {
    private var v: View? = null
    private var key = ""

    fun visible(): Boolean = v != null
    fun isShowing(number: String): Boolean = v != null && key.isNotEmpty() && key == tail(number)

    fun hide(c: Context) {
        val x = v ?: return
        v = null
        key = ""
        try {
            (c.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(x)
        } catch (e: Exception) { }
    }

    fun show(c: Context, r: CallRecord, who: String, autoHideMs: Long = 0, number: String = "") {
        val app = c.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        hide(app)
        val d = app.resources.displayMetrics.density
        fun px(n: Int) = (n * d).toInt()
        fun tv(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(app).apply {
            text = t
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        val card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(18), px(16), px(18), px(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0B3C49.toInt(), 0xFF0E7C86.toInt())
            ).apply { cornerRadius = px(22).toFloat() }
            elevation = px(8).toFloat()
        }
        card.addView(tv(who, 18f, 0xFFFFFFFF.toInt(), true))
        card.addView(tv("آخر مكالمة: ${Fmt.dayLabel(r.startMs)} ${Fmt.time(r.startMs)}", 12f, 0xB3FFFFFF.toInt()).apply {
            setPadding(0, px(2), 0, px(8))
        })
        card.addView(tv(r.summary, 14f, 0xFFFFFFFF.toInt()).apply {
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(px(3).toFloat(), 1f)
        })
        if (r.apptMs > System.currentTimeMillis()) {
            card.addView(tv("موعد: ${Fmt.dateShort(r.apptMs)} ${Fmt.time(r.apptMs)}", 13f, 0xFFFFB199.toInt(), true).apply {
                setPadding(0, px(8), 0, 0)
            })
        }
        card.addView(tv("تمام", 14f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.END
            setPadding(px(16), px(10), px(4), px(6))
            setOnClickListener { hide(app) }
        })

        val box = FrameLayout(app).apply {
            setPadding(px(12), px(0), px(12), px(0))
            addView(card)
            alpha = 0f
            translationY = -px(30).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = px(28)
        }
        try {
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(box, lp)
            v = box
            key = tail(number)
            box.animate().alpha(1f).translationY(0f).setDuration(260).start()
            if (autoHideMs > 0) box.postDelayed({ hide(app) }, autoHideMs)
        } catch (e: Exception) { }
    }
}
