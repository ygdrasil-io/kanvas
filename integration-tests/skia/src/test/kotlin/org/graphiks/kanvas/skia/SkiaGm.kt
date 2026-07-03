package org.graphiks.kanvas.skia

enum class RenderFamily {
    PATH,
    GRADIENT,
    BLUR,
    CLIP,
    IMAGE,
    TEXT,
    COMPOSITE,
    IMAGE_FILTERS,
    RUNTIME_EFFECT,
    SHADER,
    MESH,
    SURFACE,
    COLOR,
}

interface SkiaGm {
    val name: String
    val renderFamily: RenderFamily
    val minSimilarity: Double
    val tolerance: Int get() = 2
    val width: Int get() = 800
    val height: Int get() = 600

    fun onOnceBeforeDraw(canvas: GmCanvas) {}

    fun onAnimate(deltaMs: Long): Boolean = false

    fun draw(canvas: GmCanvas, width: Int, height: Int)
}
