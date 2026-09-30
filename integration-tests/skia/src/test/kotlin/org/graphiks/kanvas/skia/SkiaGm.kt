package org.graphiks.kanvas.skia

import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.RenderConfig

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

data class ReferenceStatusEntry(
    val status: String = "trusted",
    val reason: String? = null,
) {
    val untrustable: Boolean
        get() = status == "untrustable"
}

interface SkiaGm {
    val name: String
    val referenceName: String get() = name
    val renderFamily: RenderFamily
    val renderCost: RenderCost
    val minSimilarity: Double
    /** A promoted port has no allowed GPU refusal route. */
    val requiresZeroRefusals: Boolean get() = false
    val referenceStatus: ReferenceStatusEntry get() = ReferenceStatusEntry()
    val tolerance: Int get() = 2
    val width: Int get() = 800
    val height: Int get() = 600
    val compositionDomain: CompositionDomain get() = CompositionDomain.LINEAR

    fun onOnceBeforeDraw(canvas: GmCanvas) {}

    fun onAnimate(deltaMs: Long): Boolean = false

    fun draw(canvas: GmCanvas, width: Int, height: Int)
}

internal fun SkiaGm.compositionConfig(base: RenderConfig = RenderConfig.DEFAULT): RenderConfig =
    base.copy(compositionDomain = compositionDomain)
