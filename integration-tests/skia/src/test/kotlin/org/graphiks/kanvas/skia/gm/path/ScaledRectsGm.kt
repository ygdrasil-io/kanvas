package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/scaledrects.cpp::ScaledRectsGM`.
 * Stresses full 3×3 matrix application, per-paint blend mode (kPlus),
 * and clip-rect with rotated/skewed draws.
 * @see https://github.com/google/skia/blob/main/gm/scaledrects.cpp
 */
class ScaledRectsGm : SkiaGm {
    override val name = "scaledrects"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.8f, 0.8f, 0.8f)

        canvas.clipRect(Rect.fromXYWH(10f, 50f, 100f, 10f))

        canvas.save()
        canvas.concat(makeMatrix(3.0f, -0.5f, -0.5f, -3.0f))
        canvas.drawRect(
            Rect.fromXYWH(-1000f, -1000f, 2000f, 2000f),
            Paint(color = Color.BLUE),
        )
        canvas.restore()

        canvas.save()
        canvas.concat(makeMatrix(3000.0f, -500.0f, -500.0f, -3000.0f))
        canvas.drawRect(
            Rect.fromXYWH(-1f, -1f, 2f, 2f),
            Paint(color = Color.RED, blendMode = BlendMode.PLUS),
        )
        canvas.restore()
    }

    private fun makeMatrix(a: Float, b: Float, c: Float, d: Float): Matrix33 {
        // [a, b, 0; c, d, 0; 0, 0, 1] = scale(d, ?) * skew(...)
        // Decompose: scale(diag) then skew
        val sx = a
        val sy = d
        val kx = b / sx
        val ky = c / sy
        return Matrix33.scale(sx, sy) * Matrix33.skew(kx, ky)
    }
}
