package com.linguascreen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.linguascreen.util.ScreenMetrics

/**
 * Draggable round button drawn over other apps, plus the small control panel it opens.
 * Pure Android Views on purpose: lighter than hosting Compose inside a Service.
 */
class FloatingButton(
    private val context: Context,
    private val wm: WindowManager,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onTranslate()
        fun onTogglePause()
        fun onOpenSettings()
        fun onClose()
        fun onMoved(x: Int, y: Int)
    }

    private val size = context.dp(52)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var paused = false
    private var attached = false
    private var panel: View? = null

    private val params = WindowManager.LayoutParams(
        size, size, overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private val button = TextView(context).apply {
        text = "译"
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = roundedBg(OverlayColors.brand, size / 2f)
        elevation = context.dp(4).toFloat()
        alpha = 0.9f
        contentDescription = "LinguaScreen translate button"
    }

    /** Adds the button to the screen. [x]/[y] < 0 means "use the default spot". */
    fun show(x: Int, y: Int) {
        if (attached) return
        val m = ScreenMetrics.real(context)
        params.x = if (x < 0) m.width - size else x
        params.y = if (y < 0) m.height / 3 else y
        clamp()
        wm.addView(button, params)
        attached = true
        installTouch()
    }

    fun setVisible(visible: Boolean) {
        if (!visible) hidePanel()
        button.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun setBusy(busy: Boolean) {
        button.text = if (busy) "…" else "译"
    }

    fun setPaused(value: Boolean) {
        paused = value
        button.alpha = if (value) 0.45f else 0.9f
    }

    /** Re-applies screen bounds, e.g. after rotation. */
    fun reclamp() {
        if (!attached) return
        hidePanel()
        clamp()
        wm.updateViewLayout(button, params)
    }

    fun destroy() {
        hidePanel()
        if (attached) runCatching { wm.removeView(button) }
        attached = false
    }

    private fun clamp() {
        val m = ScreenMetrics.real(context)
        params.x = params.x.coerceIn(0, (m.width - size).coerceAtLeast(0))
        val minY = ScreenMetrics.statusBarHeight(context)
        val maxY = (m.height - size - ScreenMetrics.navigationBarHeight(context)).coerceAtLeast(minY)
        params.y = params.y.coerceIn(minY, maxY)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installTouch() {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        button.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                        dragging = true
                        hidePanel()
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clamp()
                        wm.updateViewLayout(button, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge()
                        callbacks.onMoved(params.x, params.y)
                    } else {
                        if (panel == null) showPanel() else hidePanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val m = ScreenMetrics.real(context)
        params.x = if (params.x + size / 2 < m.width / 2) 0 else m.width - size
        wm.updateViewLayout(button, params)
    }

    private fun showPanel() {
        val night = context.isNight()
        val bg = if (night) OverlayColors.darkSurface else OverlayColors.lightSurface
        val fg = if (night) Color.WHITE else Color.parseColor("#1B1C20")
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(bg, context.dp(12).toFloat())
            elevation = context.dp(8).toFloat()
            setPadding(0, context.dp(6), 0, context.dp(6))
        }
        fun add(label: String, color: Int = fg, action: () -> Unit) {
            container.addView(context.textButton(label, color).apply {
                setOnClickListener { hidePanel(); action() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        add("Translate Screen", OverlayColors.brand) { callbacks.onTranslate() }
        add(if (paused) "Resume Translation" else "Pause Translation") { callbacks.onTogglePause() }
        add("Translation Settings") { callbacks.onOpenSettings() }
        add("Close Floating Button", Color.parseColor("#D93025")) { callbacks.onClose() }

        val width = context.dp(220)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val m = ScreenMetrics.real(context)
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val gap = context.dp(8)
            x = if (params.x + size / 2 < m.width / 2) params.x + size + gap else params.x - width - gap
            y = params.y.coerceAtMost(m.height - container.measuredHeight - ScreenMetrics.navigationBarHeight(context))
                .coerceAtLeast(ScreenMetrics.statusBarHeight(context))
        }
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
            false
        }
        runCatching { wm.addView(container, lp); panel = container }
    }

    fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }
}
