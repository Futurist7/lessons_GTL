package com.linguascreen.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.WindowManager
import android.widget.TextView

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
internal fun Context.sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

internal fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

internal fun roundedBg(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokeColor != null) setStroke(strokePx, strokeColor)
    }

@Suppress("DEPRECATION")
internal fun overlayType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else WindowManager.LayoutParams.TYPE_PHONE

internal fun Context.textButton(label: String, textColor: Int): TextView = TextView(this).apply {
    text = label
    setTextColor(textColor)
    textSize = 15f
    setPadding(dp(16), dp(12), dp(16), dp(12))
    isClickable = true
    minHeight = dp(44)
    gravity = android.view.Gravity.CENTER_VERTICAL
}

internal object OverlayColors {
    val brand = Color.parseColor("#1B6EF3")
    val darkSurface = Color.parseColor("#26282E")
    val lightSurface = Color.parseColor("#FFFFFF")
}
