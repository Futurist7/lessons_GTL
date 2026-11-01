package com.linguascreen.domain

import com.google.mlkit.nl.translate.TranslateLanguage

/** Target languages offered in the UI. The source language is always Chinese. */
object Languages {
    const val DEFAULT_TARGET = TranslateLanguage.ENGLISH

    val targets = listOf(
        Language(TranslateLanguage.ENGLISH, "English"),
        Language(TranslateLanguage.SPANISH, "Spanish"),
        Language(TranslateLanguage.FRENCH, "French"),
        Language(TranslateLanguage.GERMAN, "German"),
        Language(TranslateLanguage.PORTUGUESE, "Portuguese"),
        Language(TranslateLanguage.ITALIAN, "Italian"),
        Language(TranslateLanguage.RUSSIAN, "Russian"),
        Language(TranslateLanguage.JAPANESE, "Japanese"),
        Language(TranslateLanguage.KOREAN, "Korean"),
        Language(TranslateLanguage.ARABIC, "Arabic"),
    )

    fun nameOf(tag: String) = targets.firstOrNull { it.tag == tag }?.displayName ?: tag
}
