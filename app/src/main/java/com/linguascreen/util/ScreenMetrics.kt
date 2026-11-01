package com.linguascreen.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object ScreenMetrics {
    data class Size(val width: Int, val height: Int, val dpi: Int)

    /** Full display size in pixels, including status and navigation bars. */
    fun real(context: Context): Size {
        val wm = context.getSystemService(WindowManager::class.java)
        val dpi = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            Size(b.width(), b.height(), dpi)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Size(dm.widthPixels, dm.heightPixels, dpi)
        }
    }

    fun statusBarHeight(context: Context): Int = dimen(context, "status_bar_height")
    fun navigationBarHeight(context: Context): Int = dimen(context, "navigation_bar_height")

    private fun dimen(context: Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
