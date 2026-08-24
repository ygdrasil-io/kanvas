package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32

sealed interface ColorFilter {
    data class Matrix(val matrix: ColorMatrixF32) : ColorFilter
    data class Blend(val color: ColorARGB, val mode: BlendMode) : ColorFilter
    data class Compose(val outer: ColorFilter, val inner: ColorFilter) : ColorFilter
    data class Table(val table: UByteArray) : ColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Table) return false
            return table.contentEquals(other.table)
        }
        override fun hashCode(): Int = table.contentHashCode()
    }
    data class Lighting(val mul: ColorARGB, val add: ColorARGB) : ColorFilter
    data object SRGBToLinear : ColorFilter
    data object LinearToSRGB : ColorFilter
    data class HSLAMatrix(val values: FloatArray) : ColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HSLAMatrix) return false
            return values.contentEquals(other.values)
        }
        override fun hashCode(): Int = values.contentHashCode()
    }
    data class Lerp(val t: Float, val dst: ColorFilter, val src: ColorFilter) : ColorFilter
    data object HighContrast : ColorFilter
    data object Luma : ColorFilter
    data object Overdraw : ColorFilter
    data class RuntimeEffect(
        val effect: org.graphiks.kanvas.pipeline.RuntimeEffect,
        val uniforms: UniformBlock,
        val children: Map<String, ColorFilter> = emptyMap(),
    ) : ColorFilter
}
