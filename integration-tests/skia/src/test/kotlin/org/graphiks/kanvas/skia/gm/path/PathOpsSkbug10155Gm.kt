package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathOp
import org.graphiks.kanvas.geometry.PathOps
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pathopsinverse.cpp::pathops_skbug_10155` (256 × 256).
 * Regression cover for skbug.com/10155.
 *
 * Two cubic-Bézier paths are unioned via [PathOps.op]. The scene zooms
 * into the first path's bounds; the blue union result (nearly) overdraws
 * the red outlines.
 * @see https://github.com/google/skia/blob/main/gm/pathopsinverse.cpp
 */
class PathOpsSkbug10155Gm : SkiaGm {
    override val name = "pathops_skbug_10155"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path0 = path0()
        val path1 = path1()

        val resultPath = PathOps.op(path0, path1, PathOp.UNION) ?: Path { }

        val r = computeBounds(listOf(path0, path1))
        canvas.translate(30f, 30f)
        canvas.scale(200f / r.width, 200f / r.width)
        canvas.translate(-r.left, -r.top)

        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
        )

        canvas.drawPath(path0, paint)
        canvas.drawPath(path1, paint)

        val bluePaint = paint.copy(color = Color.BLUE)
        canvas.drawPath(resultPath, bluePaint)
    }

    private fun path0(): Path = Path {
        moveTo(474.889f, 27.0952f)
        lineTo(479.872f, 27.5019f)
        lineTo(479.889f, 27.0952f)
        close()
    }

    private fun path1(): Path = Path {
        moveTo(474.94f, 26.9405f)
        lineTo(477.689f, 31.1186f)
        lineTo(477.985f, 30.9059f)
        close()
    }

    private fun computeBounds(paths: List<Path>): Rect {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        val allPts = listOf(
            474.889f to 27.0952f, 479.872f to 27.5019f, 479.889f to 27.0952f,
            474.94f to 26.9405f, 477.689f to 31.1186f, 477.985f to 30.9059f,
        )
        for ((x, y) in allPts) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        return Rect(minX, minY, maxX, maxY)
    }
}
