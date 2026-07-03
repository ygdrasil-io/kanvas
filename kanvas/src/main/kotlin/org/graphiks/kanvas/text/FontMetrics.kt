package org.graphiks.kanvas.text

data class FontMetrics(
    val ascent: Float,
    val descent: Float,
    val leading: Float,
    val xHeight: Float = 0f,
    val capHeight: Float = 0f,
)
