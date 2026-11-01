package com.linguascreen

import com.linguascreen.domain.Box
import com.linguascreen.util.OverlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayLayoutTest {
    @Test fun separatesOverlappingBoxes() {
        val a = Box(0f, 100f, 200f, 160f)
        val b = Box(0f, 140f, 200f, 200f)
        val out = OverlayLayout.resolve(listOf(a, b), 2000f, 2f)
        assertFalse(out[0].intersects(out[1]))
        assertEquals(a, out[0]) // first box never moves
    }

    @Test fun leavesDisjointBoxesAlone() {
        val boxes = listOf(Box(0f, 0f, 100f, 50f), Box(0f, 200f, 100f, 250f))
        assertEquals(boxes, OverlayLayout.resolve(boxes, 2000f, 2f))
    }
}
