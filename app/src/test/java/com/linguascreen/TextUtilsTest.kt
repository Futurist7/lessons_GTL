package com.linguascreen

import com.linguascreen.util.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsTest {
    @Test fun detectsChinese() {
        assertTrue(TextUtils.containsChinese("全新 iPhone 15"))
        assertFalse(TextUtils.containsChinese("Free shipping 99"))
    }

    @Test fun joinsCjkLinesWithoutSpaces() {
        assertEquals("全新未拆封", TextUtils.joinLines(listOf("全新", "未拆封")))
        assertEquals("iPhone 15 全新", TextUtils.joinLines(listOf("iPhone 15", "全新")))
    }

    @Test fun keepsNumbersThatTranslationDropped() {
        assertEquals("Brand new [128.50]", TextUtils.preserveNumbers("全新 128.50", "Brand new"))
        assertEquals("Price 128.50 yuan", TextUtils.preserveNumbers("价格128.50元", "Price 128.50 yuan"))
    }
}
