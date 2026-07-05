package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/windowrectangles.cpp`.
 * Builds a stack of kDifference clips (rects, round rects) on a checkerboard BG.
 * @see https://github.com/google/skia/blob/main/gm/windowrectangles.cpp
 */
class WindowRectanglesGm : SkiaGm {
    override val name = "windowrectangles"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)

        canvas.clipPath(
            Path { }.apply { addRect(Rect(370.75f, 80.25f, 519.75f, 180.25f)) },
            ClipOp.DIFFERENCE, antiAlias = false,
        )
        canvas.clipPath(
            Path { }.apply { addRect(Rect(80.25f, 420.75f, 230.25f, 520.75f)) },
            ClipOp.DIFFERENCE, antiAlias = true,
        )
        canvas.clipRRect(
            RRect(Rect(200f, 200f, 400f, 400f), CornerRadii(60f, 45f)),
            ClipOp.DIFFERENCE, antiAlias = true,
        )

        canvas.clipRRect(
            RRect(
                Rect(419.75f, 370.75f, 519.75f, 520.75f),
                CornerRadii(12f, 35f), CornerRadii(23f, 20f),
                CornerRadii(12f, 35f), CornerRadii(23f, 20f),
            ),
            ClipOp.DIFFERENCE, antiAlias = true,
        )

        canvas.clipRRect(
            RRect(
                Rect(80.25f, 80.75f, 180.25f, 229.75f),
                CornerRadii(6f, 4f), CornerRadii(8f, 12f),
                CornerRadii(16f, 24f), CornerRadii(48f, 32f),
            ),
            ClipOp.DIFFERENCE, antiAlias = false,
        )

        canvas.drawRect(
            Rect(50f, 50f, 550f, 550f),
            Paint(color = Color(0xFF00AA80u)),
        )
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val size = 25
        val colors = listOf(
            Color(0xFFFFFFFFu),
            Color(0xFFC6C3C6u),
        )
        for (y in 0 until (canvas.height + size - 1) / size) {
            for (x in 0 until (canvas.width + size - 1) / size) {
                val color = colors[(x + y) % 2]
                canvas.drawRect(
                    Rect((x * size).toFloat(), (y * size).toFloat(), ((x + 1) * size).toFloat(), ((y + 1) * size).toFloat()),
                    Paint(color = color),
                )
            }
        }
    }
}
