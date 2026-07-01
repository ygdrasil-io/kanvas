package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color

sealed interface ColorFilter {
    data class Matrix(val values: FloatArray) : ColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Matrix) return false
            return values.contentEquals(other.values)
        }
        override fun hashCode(): Int = values.contentHashCode()
    }
    data class Blend(val color: Color, val mode: BlendMode) : ColorFilter
    data class Compose(val outer: ColorFilter, val inner: ColorFilter) : ColorFilter
    data class Table(val table: UByteArray) : ColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Table) return false
            return table.contentEquals(other.table)
        }
        override fun hashCode(): Int = table.contentHashCode()
    }
    data class Lighting(val mul: Color, val add: Color) : ColorFilter
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
}
