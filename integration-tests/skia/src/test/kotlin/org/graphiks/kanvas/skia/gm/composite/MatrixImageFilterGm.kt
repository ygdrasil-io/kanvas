package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/matriximagefilter.cpp::matriximagefilter` (420 × 100).
 *
 * Draws a 64x64 checkerboard inside two side-by-side panels with
 * skewed transform — one with nearest sampling, one with linear.
 * @see https://github.com/google/skia/blob/main/gm/matriximagefilter.cpp
 */
class MatrixImageFilterGm : SkiaGm {
    override val name = "matriximagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 420
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val checker = makeCheckerboard()
        val matrix = Matrix33.skew(0.5f, 0.2f)
        val srcRect = Rect.fromXYWH(0f, 0f, 96f, 96f)
        val margin = 10f

        canvas.translate(margin, margin)

        // Nearest sampling
        canvas.save()
        canvas.concat(matrix)
        canvas.drawImage(checker, srcRect)
        canvas.restore()

        canvas.translate(srcRect.width + margin, 0f)

        // Linear sampling
        canvas.save()
        canvas.concat(matrix)
        canvas.drawImage(checker, srcRect)
        canvas.restore()
    }

    private fun makeCheckerboard(): Image {
        val surf = Surface(64, 64)
        val dark = Paint(color = Color.fromRGBA(64f / 255f, 64f / 255f, 64f / 255f))
        val light = Paint(color = Color.fromRGBA(160f / 255f, 160f / 255f, 160f / 255f))
        surf.canvas {
            var y = 0
            while (y < 64) {
                var x = 0
                while (x < 64) {
                    drawRect(Rect.fromXYWH(x.toFloat(), y.toFloat(), 16f, 16f), dark)
                    drawRect(Rect.fromXYWH((x + 16).toFloat(), y.toFloat(), 16f, 16f), light)
                    drawRect(Rect.fromXYWH(x.toFloat(), (y + 16).toFloat(), 16f, 16f), light)
                    drawRect(Rect.fromXYWH((x + 16).toFloat(), (y + 16).toFloat(), 16f, 16f), dark)
                    x += 32
                }
                y += 32
            }
        }
        return surf.makeImageSnapshot()
    }
}
