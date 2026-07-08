package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/filterbug.cpp`.
 *  Regression test — renders a surface-snapshot image shader with skew
 *  and translation to filter-bug scenario.
 *  @see https://github.com/google/skia/blob/main/gm/filterbug.cpp
 */
class FilterBugGm : SkiaGm {
    override val name = "filterbug"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 150

    private val top: Image by lazy { makeImage(0, 5) }
    private val bot: Image by lazy { makeImage(22, 27) }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 0f, 0f)

        val doAA = true

        canvas.drawRect(
            Rect(50f, 0f, 100f, 50f),
            Paint(
                antiAlias = doAA,
                shader = Shader.WithLocalMatrix(
                    Shader.Image(top, TileMode.REPEAT, TileMode.REPEAT),
                    Matrix33.makeAll(2f, 0f, 50f, 0f, 2f, 0f),
                ),
            ),
        )

        canvas.drawRect(
            Rect(50f, 50f, 100f, 86f),
            Paint(color = Color.WHITE, antiAlias = doAA),
        )

        canvas.drawRect(
            Rect(50f, 86f, 100f, 136f),
            Paint(
                antiAlias = doAA,
                shader = Shader.WithLocalMatrix(
                    Shader.Image(bot, TileMode.REPEAT, TileMode.REPEAT),
                    Matrix33.makeAll(2f, 0f, 50f, 0f, 2f, 86f),
                ),
            ),
        )
    }

    private fun makeImage(firstBlackRow: Int, lastBlackRow: Int): Image {
        val surf = Surface(25, 27)
        surf.canvas {
            drawColor(Color.WHITE)
            val black = Paint(color = Color.BLACK)
            for (y in firstBlackRow until lastBlackRow) {
                drawRect(Rect(0f, y.toFloat(), 25f, (y + 1).toFloat()), black)
            }
        }
        return surf.makeImageSnapshot()
    }
}
