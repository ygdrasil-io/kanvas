package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.BlurStyle

sealed interface MaskFilter {
    data class Blur(val style: BlurStyle, val sigma: Float) : MaskFilter
    /**
     * Shader-based alpha mask.
     *
     * Note: the type name [Shader] conflicts with [org.graphiks.kanvas.paint.Shader].
     * Use a fully-qualified import or avoid star-importing both `MaskFilter.*` and `paint.Shader`.
     */
    data class Shader(val shader: org.graphiks.kanvas.paint.Shader) : MaskFilter
    data class Table(val table: UByteArray) : MaskFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Table) return false
            return table.contentEquals(other.table)
        }
        override fun hashCode(): Int = table.contentHashCode()
    }
}
