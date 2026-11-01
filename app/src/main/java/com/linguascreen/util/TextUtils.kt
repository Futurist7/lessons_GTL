package com.linguascreen.util

/** Pure text helpers (no Android dependencies, unit tested). */
object TextUtils {
    private val han = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
    private val number = Regex("\\d+(?:[.,]\\d+)*")

    fun containsChinese(s: String) = han.containsMatchIn(s)

    /** Joins OCR lines: no space between two CJK characters, a space otherwise. */
    fun joinLines(lines: List<String>): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isNotEmpty()) {
                val prev = sb.last()
                val next = line.first()
                if (!(isCjk(prev) && isCjk(next))) sb.append(' ')
            }
            sb.append(line)
        }
        return sb.toString()
    }

    private fun isCjk(c: Char) = han.matches(c.toString()) || c in '　'..'〿' || c in '＀'..'￯'

    /**
     * Prices, sizes and quantities must never be lost. If a number from [source] is missing in
     * [translated] it is appended in brackets so the user can still see it.
     */
    fun preserveNumbers(source: String, translated: String): String {
        val missing = number.findAll(source).map { it.value }.distinct()
            .filter { !translated.contains(it) }.toList()
        return if (missing.isEmpty()) translated else "$translated [${missing.joinToString(" ")}]"
    }
}
