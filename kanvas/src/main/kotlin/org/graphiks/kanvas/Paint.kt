package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidColorPlan

enum class BlendMode(val label: String) {
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

enum class StrokeCap(val label: String) {
    BUTT("butt"),
    ROUND("round"),
    SQUARE("square"),
}

enum class StrokeJoin(val label: String) {
    MITER("miter"),
    ROUND("round"),
    BEVEL("bevel"),
}

/**
 * Paint geometry style. [FILL] is the only style the native pipeline can
 * render; [STROKE] (covering Skia's `kStroke_Style` and
 * `kStrokeAndFill_Style`) is carried so stroke draws REFUSE with
 * `unsupported_stroke` instead of being silently filled. Real stroke
 * rendering is dependency-gated (KGPU-M3-003).
 */
enum class PaintStyle(val label: String) {
    FILL("fill"),
    STROKE("stroke"),
}

sealed interface ColorFilter {
    data class Matrix(val values: FloatArray) : ColorFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Matrix) return false
            return values.contentEquals(other.values)
        }

        override fun hashCode(): Int = values.contentHashCode()
    }

    data object None : ColorFilter
}

class Paint(
    var r: Float = 0f,
    var g: Float = 0f,
    var b: Float = 0f,
    var a: Float = 1f,
    var shader: Shader? = null,
    var blendMode: BlendMode = BlendMode.SRC_OVER,
    var colorFilter: ColorFilter = ColorFilter.None,
    var strokeWidth: Float = 0f,
    var strokeCap: StrokeCap = StrokeCap.BUTT,
    var strokeJoin: StrokeJoin = StrokeJoin.MITER,
    var style: PaintStyle = PaintStyle.FILL,
    var antiAlias: Boolean = true,
) {
    fun color(r: Float, g: Float, b: Float, a: Float = 1f): Paint = apply {
        this.r = r; this.g = g; this.b = b; this.a = a
    }

    fun shader(shader: Shader?): Paint = apply { this.shader = shader }
    fun blendMode(mode: BlendMode): Paint = apply { this.blendMode = mode }
    fun colorFilter(filter: ColorFilter): Paint = apply { this.colorFilter = filter }
    fun strokeWidth(w: Float): Paint = apply { this.strokeWidth = w }
    fun strokeCap(cap: StrokeCap): Paint = apply { this.strokeCap = cap }
    fun strokeJoin(join: StrokeJoin): Paint = apply { this.strokeJoin = join }
    fun style(style: PaintStyle): Paint = apply { this.style = style }

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
