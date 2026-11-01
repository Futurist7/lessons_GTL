package com.linguascreen.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort name of the app currently on screen, used only to label history entries.
 * Needs the optional "Usage access" permission; without it the source app is simply left blank.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(@ApplicationContext private val context: Context) {

    private val knownApps = mapOf(
        "com.taobao.idlefish" to "Xianyu",
        "com.taobao.taobao" to "Taobao",
        "com.alibaba.wireless" to "1688",
        "com.xunmeng.pinduoduo" to "Pinduoduo",
        "com.tencent.mm" to "WeChat",
        "com.jingdong.app.mall" to "JD.com",
        "com.eg.android.AlipayGphone" to "Alipay",
        "com.sankuai.meituan" to "Meituan",
        "com.xingin.xhs" to "Xiaohongshu",
    )

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Returns a readable app name, or null when unknown / not permitted. */
    fun currentApp(): String? {
        if (!hasAccess()) return null
        return try {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 60_000, now)
            val event = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    event.packageName != context.packageName
                ) last = event.packageName
            }
            last?.let { knownApps[it] ?: it }
        } catch (e: Exception) {
            null
        }
    }
}
