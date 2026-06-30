package org.graphiks.kanvas.skia

enum class RenderFamily {
    PATH,
    GRADIENT,
    BLUR,
    CLIP,
    IMAGE,
    TEXT,
    COMPOSITE,
}

interface SkiaGm {
    val name: String
    val renderFamily: RenderFamily
    val minSimilarity: Double
    val tolerance: Int get() = 2
    val width: Int get() = 800
    val height: Int get() = 600
    fun draw(canvas: GmCanvas, width: Int, height: Int)
}
