package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/daa.cpp`.
 * Five sub-tests probing delta-based AA rasteriser handling of adjacent/wound polygons.
 * @see https://github.com/google/skia/blob/main/gm/daa.cpp
 */
class DaaGm : SkiaGm {
    override val name = "daa"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 399
    override val height = 245

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val k = 49f

        canvas.drawRect(Rect(0f, 0f, k, k), Paint(color = Color.RED))
        val path1 = Path {
            moveTo(0f, 0f); lineTo(k, k); lineTo(0f, k); lineTo(0f, 0f)
            moveTo(0f, 0f); lineTo(k, k); lineTo(k, 0f); lineTo(0f, 0f)
        }
        canvas.drawPath(path1, Paint(color = Color.GREEN))

        canvas.translate(0f, k)
        canvas.drawRect(Rect(0f, 0f, k, k), Paint(color = Color.RED))
        val path2a = Path { moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f) }
        canvas.drawPath(path2a, Paint(color = Color.BLUE))
        val path2b = Path { moveTo(k * 0.5f, 0f); lineTo(k * 0.5f, k); lineTo(k, k); lineTo(k, 0f) }
        canvas.drawPath(path2b, Paint(color = Color.GREEN))

        canvas.translate(0f, k)
        canvas.drawRect(Rect(0f, 0f, k, k), Paint(color = Color.RED))
        val path3 = Path {
            moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f)
            moveTo(k * 0.5f, 0f); lineTo(k * 0.5f, k); lineTo(k, k); lineTo(k, 0f)
        }
        canvas.drawPath(path3, Paint(color = Color.GREEN))

        canvas.translate(0f, k)
        canvas.drawRect(Rect(0f, 0f, k, k), Paint(color = Color.RED))
        val path4 = Path {
            moveTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k); lineTo(k * 0.5f, 0f)
            moveTo(k * 0.5f, 0f); lineTo(k, 0f); lineTo(k, k); lineTo(k * 0.5f, k)
        }
        canvas.drawPath(path4, Paint(color = Color.GREEN))

        canvas.translate(0f, k)
        canvas.drawRect(Rect(0f, 0f, k, k), Paint(color = Color.RED))
        val path5 = Path {
            moveTo(k * 0.5f, 0f); lineTo(0f, 0f); lineTo(0f, k); lineTo(k * 0.5f, k)
            lineTo(k * 0.5f, 0f); lineTo(k, 0f); lineTo(k, k); lineTo(k * 0.5f, k)
        }
        canvas.drawPath(path5, Paint(color = Color.GREEN))
    }
}
