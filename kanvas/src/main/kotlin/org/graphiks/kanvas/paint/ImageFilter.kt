package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color

sealed interface ImageFilter {
    data class Blur(
        val sigmaX: Float, val sigmaY: Float,
        val tileMode: TileMode = TileMode.CLAMP,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class DropShadow(
        val dx: Float, val dy: Float,
        val sigmaX: Float, val sigmaY: Float,
        val color: Color,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class ColorFilter(val filter: org.graphiks.kanvas.paint.ColorFilter, val input: ImageFilter? = null) : ImageFilter
    data class Compose(val outer: ImageFilter, val inner: ImageFilter) : ImageFilter
    data class Blend(
        val mode: BlendMode,
        val background: ImageFilter, val foreground: ImageFilter,
    ) : ImageFilter
}
