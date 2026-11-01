package com.linguascreen.util

import com.linguascreen.domain.Box

/** Pure layout logic that keeps translated labels from overlapping each other. */
object OverlayLayout {
    /**
     * Places boxes top-to-bottom; a box that collides with an already placed one is pushed below it.
     * The result has the same order as [input]. Boxes are kept inside [0, screenHeight] when possible.
     */
    fun resolve(input: List<Box>, screenHeight: Float, gap: Float): List<Box> {
        val order = input.indices.sortedWith(compareBy({ input[it].top }, { input[it].left }))
        val placed = ArrayList<Box>(input.size)
        val out = arrayOfNulls<Box>(input.size)
        for (i in order) {
            var box = input[i]
            var guard = 0
            while (guard++ < 40) {
                val hit = placed.firstOrNull { it.intersects(box) } ?: break
                box = box.offset(0f, hit.bottom + gap - box.top)
            }
            if (box.bottom > screenHeight && box.height <= screenHeight) {
                box = box.offset(0f, screenHeight - box.bottom)
            }
            placed += box
            out[i] = box
        }
        return out.map { it!! }
    }
}
