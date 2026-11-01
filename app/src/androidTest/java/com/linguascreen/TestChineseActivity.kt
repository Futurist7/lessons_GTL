package com.linguascreen

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Stand-in for a Chinese shopping app: shows real Chinese product text on screen. */
class TestChineseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(48, 260, 48, 48)
        }
        listOf("全新未拆封 苹果手机", "价格 1288 元", "包邮 当天发货", "卖家信誉良好").forEach {
            col.addView(TextView(this).apply {
                text = it; textSize = 30f; setTextColor(Color.BLACK); setPadding(0, 36, 0, 36)
            })
        }
        setContentView(col)
    }
}
