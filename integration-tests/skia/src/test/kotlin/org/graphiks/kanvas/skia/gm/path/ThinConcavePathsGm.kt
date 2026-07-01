package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/thinconcavepaths.cpp::thinconcavepaths`
 * (DEF_SIMPLE_GM, 550 x 400).
 *
 * Stress test for AA fill of thin concave path families. Nine families
 * (thin stroked rect, thin right angle, golf club, thin rect + triangle,
 * barbell, hipster pants, skinny snake, pointy golf club, small "i") are
 * each swept across multiple sub-pixel widths (0.05..2.0 px). Each
 * family is drawn vertically stacked in its own column.
 *
 * Originally a regression test for triangulator inverted-edge
 * compensation when the inner edges of a thin path collapse on
 * themselves.
 */
class ThinConcavePathsGm : SkiaGm {
    override val name = "thinconcavepaths"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 550
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, 1f),
            style = PaintStyle.FILL,
            antiAlias = true,
        )

        // Column 0 — thin stroked rect (sweep 0.5..2.0 step 0.25).
        canvas.save()
        var w = 0.5f
        while (w < 2.05f) {
            drawThinStrokedRect(canvas, paint, w)
            canvas.translate(0f, 25f)
            w += 0.25f
        }
        canvas.restore()
        canvas.translate(50f, 0f)

        // Column 1 — thin right angle.
        canvas.save()
        w = 0.5f
        while (w < 2.05f) {
            drawThinRightAngle(canvas, paint, w)
            canvas.translate(0f, 25f)
            w += 0.25f
        }
        canvas.restore()
        canvas.translate(40f, 0f)

        // Column 2 — golf club.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawGolfClub(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(70f, 0f)

        // Column 3 — thin rect + triangle.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawThinRectAndTriangle(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(30f, 0f)

        // Column 4 — barbell.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawBarbell(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(80f, 0f)

        // Column 5 — hipster pants.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawHipsterPants(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(100f, 0f)

        // Column 6 — skinny snake.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawSkinnySnake(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(30f, 0f)

        // Column 7 — pointy golf club.
        canvas.save()
        w = 0.2f
        while (w < 2.1f) {
            drawPointyGolfClub(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.2f
        }
        canvas.restore()
        canvas.translate(100f, 0f)

        // Column 8 — small "i" (sweep 0.0..0.5 step 0.05).
        canvas.save()
        w = 0.0f
        while (w < 0.5f) {
            drawSmallI(canvas, paint, w)
            canvas.translate(0f, 30f)
            w += 0.05f
        }
        canvas.restore()
        canvas.translate(100f, 0f)
    }

    private fun drawThinStrokedRect(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(10f + width, 10f + width)
            lineTo(40f, 10f + width)
            lineTo(40f, 20f)
            lineTo(10f + width, 20f)
            moveTo(10f, 10f)
            lineTo(10f, 20f + width)
            lineTo(40f + width, 20f + width)
            lineTo(40f + width, 10f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawThinRightAngle(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(10f + width, 10f + width)
            lineTo(40f, 10f + width)
            lineTo(40f, 20f)
            lineTo(40f + width, 20f + width)
            lineTo(40f + width, 10f)
            lineTo(10f, 10f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawGolfClub(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(20f, 10f)
            lineTo(80f, 10f)
            lineTo(80f, 10f + width)
            lineTo(30f, 10f + width)
            lineTo(30f, 20f)
            lineTo(20f, 20f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawBarbell(canvas: GmCanvas, paint: Paint, width: Float) {
        val offset = width * 0.5f
        val path = Path {
            moveTo(30f, 5f)
            lineTo(40f - offset, 15f - offset)
            lineTo(60f + offset, 15f - offset)
            lineTo(70f, 5f)
            lineTo(70f, 25f)
            lineTo(60f + offset, 15f + offset)
            lineTo(40f - offset, 15f + offset)
            lineTo(30f, 25f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawThinRectAndTriangle(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(30f, 5f)
            lineTo(30f + width, 5f)
            lineTo(30f + width, 25f)
            lineTo(30f, 25f)
            moveTo(40f, 5f)
            lineTo(40f + width, 5f)
            lineTo(40f, 25f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawHipsterPants(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(10f, 10f)
            lineTo(10f, 20f)
            lineTo(50f, 10f + width)
            lineTo(90f, 20f)
            lineTo(90f, 10f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSkinnySnake(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(20f + width, 10f)
            lineTo(20f + width, 20f)
            lineTo(10f + width, 30f)
            lineTo(10f + width, 40f)
            lineTo(10f - width, 40f)
            lineTo(10f - width, 30f)
            lineTo(20f - width, 20f)
            lineTo(20f - width, 10f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPointyGolfClub(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(20f, 10f)
            lineTo(80f, 10f + width * 0.5f)
            lineTo(30f, 10f + width)
            lineTo(30f, 20f)
            lineTo(20f, 20f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSmallI(canvas: GmCanvas, paint: Paint, width: Float) {
        val path = Path {
            moveTo(1.25f - width, 18.75f + width)
            lineTo(1.25f - width, 12.25f - width)
            lineTo(2.50f + width, 12.25f - width)
            lineTo(2.50f + width, 18.75f + width)
            moveTo(1.25f - width, 11.75f + width)
            lineTo(1.25f - width, 10.25f - width)
            lineTo(2.50f + width, 10.25f - width)
            lineTo(2.50f + width, 11.75f + width)
        }
        canvas.drawPath(path, paint)
    }
}
