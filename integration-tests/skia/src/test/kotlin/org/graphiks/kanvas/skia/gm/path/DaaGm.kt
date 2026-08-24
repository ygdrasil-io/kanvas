package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/daa.cpp`.
 * Five sub-tests probing delta-based AA rasteriser handling of adjacent/wound polygons.
 * @see https://github.com/google/skia/blob/main/gm/daa.cpp
 */
class DaaGm : SkiaGm {
    override val name = "daa"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 76.8
    override val width = 399
    override val height = 245

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val k = 49f

        canvas.drawRect(RectF32(0f, 0f, k, k), Paint(color = ColorARGB.Red))
        val path1 = Path {
            moveTo(0f, 0f); lineTo(k, k); lineTo(0f, k); lineTo(0f, 0f)
            moveTo(0f, 0f); lineTo(k, k); lineTo(k, 0f); lineTo(0f, 0f)
        }
        canvas.drawPath(path1, Paint(color = ColorARGB.Green))

        canvas.translate(0f, k)
        canvas.drawRect(RectF32(0f, 0f, k, k), Paint(color = ColorARGB.Red))
        val path2a = Path { moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f) }
        canvas.drawPath(path2a, Paint(color = ColorARGB.Blue))
        val path2b = Path { moveTo(k * 0.5f, 0f); lineTo(k * 0.5f, k); lineTo(k, k); lineTo(k, 0f) }
        canvas.drawPath(path2b, Paint(color = ColorARGB.Green))

        canvas.translate(0f, k)
        canvas.drawRect(RectF32(0f, 0f, k, k), Paint(color = ColorARGB.Red))
        val path3 = Path {
            moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f)
            moveTo(k * 0.5f, 0f); lineTo(k * 0.5f, k); lineTo(k, k); lineTo(k, 0f)
        }
        canvas.drawPath(path3, Paint(color = ColorARGB.Green))

        canvas.translate(0f, k)
        canvas.drawRect(RectF32(0f, 0f, k, k), Paint(color = ColorARGB.Red))
        val path4 = Path {
            moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f)
            moveTo(k * 0.5f, 0f); lineTo(k, 0f); lineTo(k, k); lineTo(k * 0.5f, k)
        }
        canvas.drawPath(path4, Paint(color = ColorARGB.Green))

        canvas.translate(0f, k)
        canvas.drawRect(RectF32(0f, 0f, k, k), Paint(color = ColorARGB.Red))
        val path5 = Path {
            moveTo(k * 0.5f, 0f); lineTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k)
            lineTo(k * 0.5f, 0f); lineTo(k, 0f); lineTo(k, k); lineTo(k * 0.5f, k)
        }
        canvas.drawPath(path5, Paint(color = ColorARGB.Green))
    }
}
