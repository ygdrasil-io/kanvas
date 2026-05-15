package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/clipshader.cpp`
 * `DEF_SIMPLE_GM(clipshadermatrix, …, 145, 128)`.
 *
 * Renders a 70 × 60 alpha-only oval into an offscreen surface, then
 * uses that snapshot as a [SkCanvas.clipShader]. Inside that clip the
 * canvas concatenates a perspective-skew-rotate-scale matrix and
 * draws a centred radial gradient in 4 stamped positions
 * (`(0, 0)`, `(68.5, 0)`, `(0, 66.5)`, `(68.5, 66.5)`).
 *
 * Reference image: `clipshadermatrix.png`, 145 × 128.
 */
public class ClipShaderGM : GM() {

    override fun getName(): String = "clipshadermatrix"
    override fun getISize(): SkISize = SkISize.Make(145, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Hard-edged oval clip rendered into an alpha-only surface.
        val clipSurface = SkSurfaces.Raster(SkImageInfo.MakeA8(70, 60))!!
        clipSurface.canvas.drawOval(SkRect.MakeXYWH(0f, 10f, 64f, 44f), SkPaint())
        val clipShader = clipSurface.makeImageSnapshot().makeShader(
            SkTileMode.kDecal,
            SkTileMode.kDecal,
            SkSamplingOptions(SkFilterMode.kLinear),
        )

        c.translate(5f, 0f)
        for (tx in floatArrayOf(0f, 68.5f)) {
            for (ty in floatArrayOf(0f, 66.5f)) {
                c.save()

                c.translate(tx, ty)
                c.clipShader(clipShader)
                c.translate(-tx, -ty)

                // Build m = full perspective + skew + scale + rotate
                // matching upstream's call sequence (which uses the
                // setter then preRotate). Equivalent matrix as a
                // single MakeAll then preRotate.
                var m = SkMatrix.MakeAll(
                    1.2f, 0.03f, 0f,
                    0f, 0.8f, 0f,
                    -0.002f, 0.0007f, 1f,
                )
                m = m.preRotate(30f)
                c.concat(m)

                val center0 = SkPoint.Make(64f, 64f)
                val inv = m.invert() ?: SkMatrix.Identity
                val center = inv.mapXY(center0)
                val colors = intArrayOf(
                    0xFFFFFF00.toInt(), // yellow
                    0xFF00FF00.toInt(), // green
                    0xFF0000FF.toInt(), // blue
                    0xFFFF00FF.toInt(), // magenta
                    0xFF00FFFF.toInt(), // cyan
                    0xFFFFFF00.toInt(), // yellow
                )
                val gradient = SkRadialGradient.Make(
                    center,
                    32f,
                    colors,
                    null,
                    SkTileMode.kMirror,
                )

                val paint = SkPaint().apply { shader = gradient }
                c.drawPaint(paint)

                c.restore()
            }
        }
    }
}
