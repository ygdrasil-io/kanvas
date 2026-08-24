package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/windowrectangles.cpp`.
 * Builds a stack of kDifference clips (rects, round rects) on a checkerboard BG.
 * @see https://github.com/google/skia/blob/main/gm/windowrectangles.cpp
 */
class WindowRectanglesGm : SkiaGm {
    override val name = "windowrectangles"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)

        canvas.clipPath(
            Path { }.apply { addRect(RectF32(370.75f, 80.25f, 519.75f, 180.25f)) },
            ClipOp.DIFFERENCE, antiAlias = false,
        )
        canvas.clipPath(
            Path { }.apply { addRect(RectF32(80.25f, 420.75f, 230.25f, 520.75f)) },
            ClipOp.DIFFERENCE, antiAlias = true,
        )
        canvas.clipRRect(
            RRectF32.of(RectF32(200f, 200f, 400f, 400f), CornerRadiiF32.of(60f, 45f)),
            ClipOp.DIFFERENCE, antiAlias = true,
        )

        canvas.clipRRect(
            RRectF32.of(
                RectF32(419.75f, 370.75f, 519.75f, 520.75f),
                CornerRadiiF32.of(12f, 35f), CornerRadiiF32.of(23f, 20f),
                CornerRadiiF32.of(12f, 35f), CornerRadiiF32.of(23f, 20f),
            ),
            ClipOp.DIFFERENCE, antiAlias = true,
        )

        canvas.clipRRect(
            RRectF32.of(
                RectF32(80.25f, 80.75f, 180.25f, 229.75f),
                CornerRadiiF32.of(6f, 4f), CornerRadiiF32.of(8f, 12f),
                CornerRadiiF32.of(16f, 24f), CornerRadiiF32.of(48f, 32f),
            ),
            ClipOp.DIFFERENCE, antiAlias = false,
        )

        canvas.drawRect(
            RectF32(50f, 50f, 550f, 550f),
            Paint(color = ColorARGB.fromPackedUInt(0xFF00AA80u)),
        )
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val size = 25
        val colors = listOf(
            ColorARGB.fromPackedUInt(0xFFFFFFFFu),
            ColorARGB.fromPackedUInt(0xFFC6C3C6u),
        )
        for (y in 0 until (canvas.height + size - 1) / size) {
            for (x in 0 until (canvas.width + size - 1) / size) {
                val color = colors[(x + y) % 2]
                canvas.drawRect(
                    RectF32((x * size).toFloat(), (y * size).toFloat(), ((x + 1) * size).toFloat(), ((y + 1) * size).toFloat()),
                    Paint(color = color),
                )
            }
        }
    }
}
