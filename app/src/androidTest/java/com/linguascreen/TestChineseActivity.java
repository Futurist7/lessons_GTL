package com.linguascreen;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Stand-in for a Chinese shopping app: shows real Chinese product text on screen (plain Java: no Kotlin runtime in the test APK). */
public class TestChineseActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.WHITE);
        col.setPadding(48, 260, 48, 48);
        String[] lines = {"全新未拆封 苹果手机", "价格 1288 元", "包邮 当天发货", "卖家信誉良好"};
        for (String line : lines) {
            TextView t = new TextView(this);
            t.setText(line);
            t.setTextSize(30f);
            t.setTextColor(Color.BLACK);
            t.setPadding(0, 36, 0, 36);
            col.addView(t);
        }
        setContentView(col);
    }
}
