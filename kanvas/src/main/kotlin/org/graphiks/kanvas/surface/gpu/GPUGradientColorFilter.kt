package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

internal fun GPUMaterialDescriptor.withGradientColorFilter(filter: ColorFilter): GPUMaterialDescriptor? = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> filteredColors(filter)?.let { colors ->
        copy(
            startR = colors.start.r, startG = colors.start.g, startB = colors.start.b, startA = colors.start.a,
            endR = colors.end.r, endG = colors.end.g, endB = colors.end.b, endA = colors.end.a,
            allStopColors = colors.stops,
        )
    }
    is GPUMaterialDescriptor.RadialGradient -> filteredColors(filter)?.let { colors ->
        copy(
            startR = colors.start.r, startG = colors.start.g, startB = colors.start.b, startA = colors.start.a,
            endR = colors.end.r, endG = colors.end.g, endB = colors.end.b, endA = colors.end.a,
            allStopColors = colors.stops,
        )
    }
    is GPUMaterialDescriptor.SweepGradient -> filteredColors(filter)?.let { colors ->
        copy(
            startR = colors.start.r, startG = colors.start.g, startB = colors.start.b, startA = colors.start.a,
            endR = colors.end.r, endG = colors.end.g, endB = colors.end.b, endA = colors.end.a,
            allStopColors = colors.stops,
        )
    }
    is GPUMaterialDescriptor.ConicalGradient -> filteredColors(filter)?.let { colors ->
        copy(
            startR = colors.start.r, startG = colors.start.g, startB = colors.start.b, startA = colors.start.a,
            endR = colors.end.r, endG = colors.end.g, endB = colors.end.b, endA = colors.end.a,
            allStopColors = colors.stops,
        )
    }
    else -> null
}

private data class GradientRgba(val r: Float, val g: Float, val b: Float, val a: Float) {
    fun clamped(): GradientRgba = GradientRgba(
        r.coerceIn(0f, 1f),
        g.coerceIn(0f, 1f),
        b.coerceIn(0f, 1f),
        a.coerceIn(0f, 1f),
    )
}

private data class FilteredGradientColors(
    val start: GradientRgba,
    val end: GradientRgba,
    val stops: FloatArray?,
)

private fun GPUMaterialDescriptor.LinearGradient.filteredColors(filter: ColorFilter): FilteredGradientColors? =
    filteredGradientColors(startR, startG, startB, startA, endR, endG, endB, endA, allStopColors, tileMode, filter)

private fun GPUMaterialDescriptor.RadialGradient.filteredColors(filter: ColorFilter): FilteredGradientColors? =
    filteredGradientColors(startR, startG, startB, startA, endR, endG, endB, endA, allStopColors, tileMode, filter)

private fun GPUMaterialDescriptor.SweepGradient.filteredColors(filter: ColorFilter): FilteredGradientColors? =
    filteredGradientColors(startR, startG, startB, startA, endR, endG, endB, endA, allStopColors, tileMode, filter)

private fun GPUMaterialDescriptor.ConicalGradient.filteredColors(filter: ColorFilter): FilteredGradientColors? =
    filteredGradientColors(startR, startG, startB, startA, endR, endG, endB, endA, allStopColors, tileMode, filter)

private fun filteredGradientColors(
    startR: Float,
    startG: Float,
    startB: Float,
    startA: Float,
    endR: Float,
    endG: Float,
    endB: Float,
    endA: Float,
    allStopColors: FloatArray?,
    tileMode: String,
    filter: ColorFilter,
): FilteredGradientColors? {
    if (filter is ColorFilter.Blend && filter.mode == BlendMode.SRC && tileMode == "decal") return null
    val start = filter.applyTo(GradientRgba(startR, startG, startB, startA), requireNoClamp = true) ?: return null
    val end = filter.applyTo(GradientRgba(endR, endG, endB, endA), requireNoClamp = true) ?: return null
    val stops = allStopColors?.filteredBy(filter) ?: if (allStopColors == null) null else return null
    return FilteredGradientColors(start, end, stops)
}

private fun FloatArray.filteredBy(filter: ColorFilter): FloatArray? {
    if (size % 4 != 0) return null
    val result = copyOf()
    var offset = 0
    while (offset < size) {
        val filtered = filter.applyTo(
            GradientRgba(this[offset], this[offset + 1], this[offset + 2], this[offset + 3]),
            requireNoClamp = true,
        )
            ?: return null
        result[offset] = filtered.r
        result[offset + 1] = filtered.g
        result[offset + 2] = filtered.b
        result[offset + 3] = filtered.a
        offset += 4
    }
    return result
}

private fun ColorFilter.applyTo(input: GradientRgba, requireNoClamp: Boolean): GradientRgba? = when (this) {
    is ColorFilter.Matrix -> values.applyMatrix(input, requireNoClamp)
    is ColorFilter.Lighting -> GradientRgba(
        r = input.r * mul.r + add.r,
        g = input.g * mul.g + add.g,
        b = input.b * mul.b + add.b,
        a = input.a,
    ).clampedOrNull(requireNoClamp)
    is ColorFilter.Blend -> if (mode == BlendMode.SRC) GradientRgba(color.r, color.g, color.b, color.a) else null
    else -> null
}

private fun FloatArray.applyMatrix(input: GradientRgba, requireNoClamp: Boolean): GradientRgba? {
    if (size != 20) return null
    return GradientRgba(
        r = this[0] * input.r + this[1] * input.g + this[2] * input.b + this[3] * input.a + this[4],
        g = this[5] * input.r + this[6] * input.g + this[7] * input.b + this[8] * input.a + this[9],
        b = this[10] * input.r + this[11] * input.g + this[12] * input.b + this[13] * input.a + this[14],
        a = this[15] * input.r + this[16] * input.g + this[17] * input.b + this[18] * input.a + this[19],
    ).clampedOrNull(requireNoClamp)
}

private fun GradientRgba.clampedOrNull(requireNoClamp: Boolean): GradientRgba? {
    val clamped = clamped()
    if (requireNoClamp && clamped != this) return null
    return clamped
}
