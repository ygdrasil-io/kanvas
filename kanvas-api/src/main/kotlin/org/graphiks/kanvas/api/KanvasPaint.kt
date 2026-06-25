package org.graphiks.kanvas.api

import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidColorPlan

enum class KanvasBlendMode(val label: String) {
    CLEAR("clear"),
    SRC("src"),
    DST("dst"),
    SRC_OVER("src_over"),
    DST_OVER("dst_over"),
    SRC_IN("src_in"),
    DST_IN("dst_in"),
    SRC_OUT("src_out"),
    DST_OUT("dst_out"),
    SRC_ATOP("src_atop"),
    DST_ATOP("dst_atop"),
    XOR("xor"),
    PLUS("plus"),
    MODULATE("modulate"),
    MULTIPLY("multiply"),
    SCREEN("screen"),
}

enum class KanvasStrokeCap(val label: String) {
    BUTT("butt"),
    ROUND("round"),
    SQUARE("square"),
}

enum class KanvasStrokeJoin(val label: String) {
    MITER("miter"),
    ROUND("round"),
    BEVEL("bevel"),
}

sealed interface KanvasColorFilter {
    data class Matrix(val values: FloatArray) : KanvasColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Matrix) return false
            return values.contentEquals(other.values)
        }

        override fun hashCode(): Int = values.contentHashCode()
    }

    data object None : KanvasColorFilter
}

class KanvasPaint(
    var r: Float = 0f,
    var g: Float = 0f,
    var b: Float = 0f,
    var a: Float = 1f,
    var shader: KanvasShader? = null,
    var blendMode: KanvasBlendMode = KanvasBlendMode.SRC_OVER,
    var colorFilter: KanvasColorFilter = KanvasColorFilter.None,
    var strokeWidth: Float = 0f,
    var strokeCap: KanvasStrokeCap = KanvasStrokeCap.BUTT,
    var strokeJoin: KanvasStrokeJoin = KanvasStrokeJoin.MITER,
    var antiAlias: Boolean = true,
) {
    fun color(r: Float, g: Float, b: Float, a: Float = 1f): KanvasPaint = apply {
        this.r = r; this.g = g; this.b = b; this.a = a
    }

    fun shader(shader: KanvasShader?): KanvasPaint = apply { this.shader = shader }
    fun blendMode(mode: KanvasBlendMode): KanvasPaint = apply { this.blendMode = mode }
    fun colorFilter(filter: KanvasColorFilter): KanvasPaint = apply { this.colorFilter = filter }
    fun strokeWidth(w: Float): KanvasPaint = apply { this.strokeWidth = w }
    fun strokeCap(cap: KanvasStrokeCap): KanvasPaint = apply { this.strokeCap = cap }
    fun strokeJoin(join: KanvasStrokeJoin): KanvasPaint = apply { this.strokeJoin = join }

    fun lower(): GPUPaintDescriptor {
        val source: GPUMaterialSourceDescriptor = shader?.lower() ?: GPUMaterialSourceDescriptor.Solid(
            plan = GPUSolidColorPlan(r = r, g = g, b = b, a = a, colorSpecLabel = "sRGB"),
        )
        return GPUPaintDescriptor(
            paintId = "kanvas-paint-${System.identityHashCode(this)}",
            source = source,
            blendModeLabel = blendMode.label,
            alpha = a,
            colorSpaceLabel = "sRGB",
        )
    }
}
