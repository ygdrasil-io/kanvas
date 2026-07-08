package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/hugepath.cpp` (huge-AA variant).
 *  Tests anti-aliased rendering of huge paths — draws large rounded
 *  rects and paths on a surface.
 *  @see https://github.com/google/skia/blob/main/gm/hugepath.cpp
 */
class PathHugeAaGm : SkiaGm {
    override val name = "path_huge_aa"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawHugePath(canvas, 100, 60)
        canvas.translate(0f, 80f)
        drawHugePath(canvas, LARGE_SURFACE_W, 60)
    }

    private fun drawHugePath(canvas: GmCanvas, w: Int, h: Int) {
        val surface = Surface(w, h)

        val path = Path { }
        path.addRRect(RRect(
            Rect.fromLTRB(4f, 4f, (w - 8).toFloat(), (h - 8).toFloat()),
            12f,
        ))

        canvas.save()
        canvas.clipRect(Rect.fromLTRB(4f, 4f, 68f, 68f))
        surface.canvas { drawPath(path, Paint()) }
        canvas.drawImage(surface.makeImageSnapshot(), Rect(
            (64 - w).toFloat(), 0f,
            (64 - w + w).toFloat(), h.toFloat(),
        ))
        canvas.restore()

        canvas.translate(80f, 0f)
        canvas.save()
        canvas.clipRect(Rect.fromLTRB(4f, 4f, 68f, 68f))
        surface.canvas {
            clear(Color.TRANSPARENT)
            drawPath(path, Paint(antiAlias = true))
        }
        canvas.drawImage(surface.makeImageSnapshot(), Rect(
            (64 - w).toFloat(), 0f,
            (64 - w + w).toFloat(), h.toFloat(),
        ))
        canvas.restore()
    }

    private companion object {
        private const val LARGE_SURFACE_W: Int = 100 * 1024
    }
}
