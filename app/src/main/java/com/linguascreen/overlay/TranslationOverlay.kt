package com.linguascreen.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.linguascreen.domain.Box
import com.linguascreen.domain.ScreenTranslation
import com.linguascreen.util.OverlayLayout
import com.linguascreen.util.ScreenMetrics

/**
 * Full-screen transparent window that draws each English block over its original Chinese text.
 * Tapping anywhere outside the controls dismisses it. A pill at the bottom toggles the original
 * screen, switches to a plain list fallback, or closes.
 */
class TranslationOverlay(
    private val context: Context,
    private val wm: WindowManager,
) {
    private enum class Mode { TRANSLATED, ORIGINAL, LIST }

    private var root: FrameLayout? = null
    val isShowing: Boolean get() = root != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(result: ScreenTranslation, opacity: Float, onDismissed: () -> Unit) {
        dismiss()
        val m = ScreenMetrics.real(context)
        val canvasView = CanvasView(context, result, m.width, m.height, opacity)
        val list = buildList(result)
        var mode = Mode.TRANSLATED

        val frame = FrameLayout(context)
        frame.addView(canvasView, FrameLayout.LayoutParams(-1, -1))

        val navPad = ScreenMetrics.navigationBarHeight(context)
        val listLp = FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = (m.height * 0.3f).toInt()
            bottomMargin = navPad + context.dp(88)
            leftMargin = context.dp(12); rightMargin = context.dp(12)
        }
        list.visibility = View.GONE
        frame.addView(list, listLp)

        val btnOriginal = context.textButton("Show original", Color.WHITE)
        val btnList = context.textButton("List view", Color.WHITE)
        val btnClose = context.textButton("Close", Color.parseColor("#FF8A80"))
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(Color.parseColor("#F2202124"), context.dp(24).toFloat())
            elevation = context.dp(6).toFloat()
            addView(btnOriginal); addView(btnList); addView(btnClose)
        }
        frame.addView(pill, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = navPad + context.dp(24)
        })

        fun render(newMode: Mode) {
            mode = newMode
            canvasView.visibility = if (mode == Mode.TRANSLATED) View.VISIBLE else View.INVISIBLE
            list.visibility = if (mode == Mode.LIST) View.VISIBLE else View.GONE
            btnOriginal.text = if (mode == Mode.ORIGINAL) "Show translation" else "Show original"
            btnList.text = if (mode == Mode.LIST) "Overlay view" else "List view"
        }
        btnOriginal.setOnClickListener { render(if (mode == Mode.ORIGINAL) Mode.TRANSLATED else Mode.ORIGINAL) }
        btnList.setOnClickListener { render(if (mode == Mode.LIST) Mode.TRANSLATED else Mode.LIST) }
        btnClose.setOnClickListener { dismiss(); onDismissed() }
        frame.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) { dismiss(); onDismissed() }
            true
        }

        val lp = WindowManager.LayoutParams(
            m.width, m.height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        wm.addView(frame, lp)
        root = frame
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    private fun buildList(result: ScreenTranslation): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        }
        for (b in result.blocks.sortedBy { it.bounds.top }) {
            col.addView(TextView(context).apply {
                text = "${b.translation}\n${b.original}"
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, context.dp(8), 0, context.dp(8))
                setOnClickListener {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("translation", b.translation))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            })
        }
        return ScrollView(context).apply {
            background = roundedBg(Color.parseColor("#F2202124"), context.dp(16).toFloat())
            addView(col)
        }
    }

    /** Draws the translated labels. Layout is computed once; onDraw only paints. */
    private class CanvasView(
        context: Context,
        result: ScreenTranslation,
        private val screenW: Int,
        private val screenH: Int,
        opacity: Float,
    ) : View(context) {
        private class Item(val box: Box, val layout: StaticLayout)

        private val pad = context.dp(4).toFloat()
        private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E1F24")
            alpha = (opacity.coerceIn(0.5f, 1f) * 255).toInt()
        }
        private val radius = context.dp(4).toFloat()
        private val rect = RectF()
        private val items: List<Item>

        init {
            val minSize = context.sp(10f)
            val maxSize = context.sp(18f)
            val minWidth = context.dp(96).toFloat()
            val built = result.blocks.map { b ->
                val w = maxOf(b.bounds.width() + 2 * pad, minWidth).coerceAtMost(screenW.toFloat())
                val left = (b.bounds.left - pad).coerceIn(0f, screenW - w)
                val boxH = b.bounds.height() + 2 * pad
                var size = maxSize
                var layout = layoutFor(b.translation, size, (w - 2 * pad).toInt())
                while (layout.height + 2 * pad > boxH && size > minSize) {
                    size -= context.sp(1f)
                    layout = layoutFor(b.translation, size, (w - 2 * pad).toInt())
                }
                val top = b.bounds.top - pad
                val h = maxOf(boxH, layout.height + 2 * pad)
                Item(Box(left, top, left + w, top + h), layout)
            }
            val placed = OverlayLayout.resolve(built.map { it.box }, screenH.toFloat(), context.dp(2).toFloat())
            items = built.mapIndexed { i, it -> Item(placed[i], it.layout) }
        }

        private fun layoutFor(text: String, sizePx: Float, width: Int): StaticLayout {
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = sizePx
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        }

        override fun onDraw(canvas: Canvas) {
            for (item in items) {
                rect.set(item.box.left, item.box.top, item.box.right, item.box.bottom)
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                canvas.save()
                canvas.translate(rect.left + pad, rect.top + pad)
                item.layout.draw(canvas)
                canvas.restore()
            }
        }
    }
}
