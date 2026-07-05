package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseLocalMatrixGM` (640 × 480).
 *
 * Exercises [Shader.WithLocalMatrix] over the fractal-noise shader.
 * Three rows: default scale, canvas-scale 2x, shader local-matrix 2x.
 * @see https://github.com/google/skia/blob/main/gm/perlinnoise.cpp
 */
class PerlinNoiseLocalMatrixGm : SkiaGm {
    override val name = "perlinnoise_localmatrix"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        val baseShader = Shader.FractalNoise(0.1f, 0.1f, 2, 0, null)
        val paint = Paint(shader = baseShader)

        val w = 80f
        val h = 80f
        var r = Rect.fromXYWH(0f, 0f, w, h)

        // Row 1: default scale
        canvas.drawRect(r, paint)

        canvas.save()
        canvas.translate(w * 5f / 4f, 0f)
        canvas.drawRect(r, paint)
        canvas.restore()

        // Row 2: canvas-scale 2x
        canvas.save()
        canvas.translate(0f, h + 10f)
        canvas.scale(2f, 2f)
        canvas.drawRect(r, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(w + 100f, h + 10f)
        canvas.scale(2f, 2f)
        canvas.drawRect(r, paint)
        canvas.restore()

        // Row 3: shader local-matrix 2x (should match row 2)
        canvas.translate(0f, h * 2f + 10f)

        val lm = Matrix33.scale(2f, 2f)
        val lmPaint = Paint(shader = Shader.WithLocalMatrix(baseShader, lm))
        r = Rect.fromXYWH(0f, 0f, w * 2f, h * 2f)

        canvas.save()
        canvas.translate(0f, h + 10f)
        canvas.drawRect(r, lmPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(w + 100f, h + 10f)
        canvas.drawRect(r, lmPaint)
        canvas.restore()
    }
}
