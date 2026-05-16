package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/color4f.cpp::DEF_SIMPLE_GM(color4f, …)`
 * (1024 × 260).
 *
 * Draws a row of `(shader, colorFilter)` combinations into two
 * intermediate sRGB raster surfaces — one tagged with `null`
 * colour space, one tagged with `SkColorSpace::MakeSRGB()` — and
 * blits each surface back onto the outer canvas vertically
 * stacked. The shaders cover {opaque red, half-alpha red} and the
 * colour filters cover {none, 0.75-saturation matrix,
 * 0.75-saturation matrix composed with a non-mergeable scale
 * matrix, srcATop blend with `#8044CC88`}.
 *
 * Upstream uses `SkShaders::Color(c)` ; `:kanvas-skia` does not
 * expose a `SkColorShader` yet, so we fall through to
 * `paint.color = c` — the on-screen pixels are identical because
 * the paint's colour drives the rasterizer in the same way when
 * no shader is set, and the only "shader" cases here are solid
 * colours.
 */
public class Color4fGM : GM() {

    override fun getName(): String = "color4f"
    override fun getISize(): SkISize = SkISize.Make(1024, 260)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        val bg = SkPaint().apply { color = SK_ColorWHITE }

        // `null` colour space and sRGB — upstream draws the same
        // pixels into both because the source paint is sRGB-encoded
        // already (the DM working CS is what the outer canvas tags
        // the offscreen surface with).
        val colorSpaces: Array<SkColorSpace?> = arrayOf(null, SkColorSpace.makeSRGB())
        for (cs in colorSpaces) {
            val info = SkImageInfo.MakeN32Premul(1024, 100, cs ?: SkColorSpace.makeSRGB())
            val surface = SkSurface.MakeRaster(info)
            surface.canvas.drawPaint(bg)
            drawIntoCanvas(surface.canvas)
            surface.draw(c, 0f, 0f)
            c.translate(0f, 120f)
        }
    }

    private fun drawIntoCanvas(target: SkCanvas) {
        val r = SkRect.MakeWH(50f, 100f)
        val shaderColors = intArrayOf(0xFFFF0000.toInt(), 0x80FF0000.toInt())
        val filters: Array<SkColorFilter?> = arrayOf(
            null,
            makeCf0(),
            makeCf1(),
            makeCf2(),
        )
        for (col in shaderColors) {
            val paint = SkPaint()
            paint.color = col
            for (cf in filters) {
                paint.colorFilter = cf
                target.drawRect(r, paint)
                target.translate(60f, 0f)
            }
        }
    }

    private fun makeCf0(): SkColorFilter = SkColorFilters.Matrix(saturationMatrix(0.75f))

    /**
     * Compose of `setSaturation(0.75) ∘ setScale(1.1, 0.9, 1)`.
     * Upstream's comment notes the inner scale matrix exists
     * specifically to keep the optimiser from collapsing the two
     * matrices into one — exercising the real compose pipeline.
     */
    private fun makeCf1(): SkColorFilter {
        val outer = SkColorFilters.Matrix(saturationMatrix(0.75f))
        val inner = SkColorFilters.Matrix(scaleMatrix(1.1f, 0.9f, 1f))
        return outer.makeComposed(inner)
    }

    private fun makeCf2(): SkColorFilter =
        SkColorFilters.Blend(0x8044CC88.toInt(), SkBlendMode.kSrcATop)

    private fun saturationMatrix(s: Float): FloatArray {
        val r = 0.213f * (1f - s)
        val g = 0.715f * (1f - s)
        val b = 0.072f * (1f - s)
        return floatArrayOf(
            s + r, g,     b,     0f, 0f,
            r,     s + g, b,     0f, 0f,
            r,     g,     s + b, 0f, 0f,
            0f,    0f,    0f,    1f, 0f,
        )
    }

    /** Mirrors `SkColorMatrix::setScale(rs, gs, bs, 1)` — diagonal RGB scale, alpha pass-through. */
    private fun scaleMatrix(rs: Float, gs: Float, bs: Float): FloatArray = floatArrayOf(
        rs, 0f, 0f, 0f, 0f,
        0f, gs, 0f, 0f, 0f,
        0f, 0f, bs, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
