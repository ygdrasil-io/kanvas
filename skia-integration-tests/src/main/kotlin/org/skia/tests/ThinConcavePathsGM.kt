package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/thinconcavepaths.cpp::thinconcavepaths`
 * (DEF_SIMPLE_GM, 550 × 400).
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
public class ThinConcavePathsGM : GM() {

    override fun getName(): String = "thinconcavepaths"
    override fun getISize(): SkISize = SkISize.Make(550, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }

        // Column 0 — thin stroked rect (sweep 0.5..2.0 step 0.25).
        c.save()
        var w = 0.5f
        while (w < 2.05f) {
            drawThinStrokedRect(c, paint, w)
            c.translate(0f, 25f)
            w += 0.25f
        }
        c.restore()
        c.translate(50f, 0f)

        // Column 1 — thin right angle.
        c.save()
        w = 0.5f
        while (w < 2.05f) {
            drawThinRightAngle(c, paint, w)
            c.translate(0f, 25f)
            w += 0.25f
        }
        c.restore()
        c.translate(40f, 0f)

        // Column 2 — golf club.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawGolfClub(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(70f, 0f)

        // Column 3 — thin rect + triangle.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawThinRectAndTriangle(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(30f, 0f)

        // Column 4 — barbell.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawBarbell(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(80f, 0f)

        // Column 5 — hipster pants.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawHipsterPants(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(100f, 0f)

        // Column 6 — skinny snake.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawSkinnySnake(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(30f, 0f)

        // Column 7 — pointy golf club.
        c.save()
        w = 0.2f
        while (w < 2.1f) {
            drawPointyGolfClub(c, paint, w)
            c.translate(0f, 30f)
            w += 0.2f
        }
        c.restore()
        c.translate(100f, 0f)

        // Column 8 — small "i" (sweep 0.0..0.5 step 0.05).
        c.save()
        w = 0.0f
        while (w < 0.5f) {
            drawSmallI(c, paint, w)
            c.translate(0f, 30f)
            w += 0.05f
        }
        c.restore()
        c.translate(100f, 0f)
    }

    private fun drawThinStrokedRect(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(10f + width, 10f + width)
            .lineTo(40f, 10f + width)
            .lineTo(40f, 20f)
            .lineTo(10f + width, 20f)
            .moveTo(10f, 10f)
            .lineTo(10f, 20f + width)
            .lineTo(40f + width, 20f + width)
            .lineTo(40f + width, 10f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawThinRightAngle(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(10f + width, 10f + width)
            .lineTo(40f, 10f + width)
            .lineTo(40f, 20f)
            .lineTo(40f + width, 20f + width)
            .lineTo(40f + width, 10f)
            .lineTo(10f, 10f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawGolfClub(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(20f, 10f)
            .lineTo(80f, 10f)
            .lineTo(80f, 10f + width)
            .lineTo(30f, 10f + width)
            .lineTo(30f, 20f)
            .lineTo(20f, 20f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawBarbell(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val offset = width * 0.5f
        val path: SkPath = SkPathBuilder()
            .moveTo(30f, 5f)
            .lineTo(40f - offset, 15f - offset)
            .lineTo(60f + offset, 15f - offset)
            .lineTo(70f, 5f)
            .lineTo(70f, 25f)
            .lineTo(60f + offset, 15f + offset)
            .lineTo(40f - offset, 15f + offset)
            .lineTo(30f, 25f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawThinRectAndTriangle(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(30f, 5f)
            .lineTo(30f + width, 5f)
            .lineTo(30f + width, 25f)
            .lineTo(30f, 25f)
            .moveTo(40f, 5f)
            .lineTo(40f + width, 5f)
            .lineTo(40f, 25f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawHipsterPants(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(10f, 20f)
            .lineTo(50f, 10f + width)
            .lineTo(90f, 20f)
            .lineTo(90f, 10f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawSkinnySnake(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(20f + width, 10f)
            .lineTo(20f + width, 20f)
            .lineTo(10f + width, 30f)
            .lineTo(10f + width, 40f)
            .lineTo(10f - width, 40f)
            .lineTo(10f - width, 30f)
            .lineTo(20f - width, 20f)
            .lineTo(20f - width, 10f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawPointyGolfClub(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(20f, 10f)
            .lineTo(80f, 10f + width * 0.5f)
            .lineTo(30f, 10f + width)
            .lineTo(30f, 20f)
            .lineTo(20f, 20f)
            .detach()
        canvas.drawPath(path, paint)
    }

    private fun drawSmallI(canvas: SkCanvas, paint: SkPaint, width: Float) {
        val path: SkPath = SkPathBuilder()
            .moveTo(1.25f - width, 18.75f + width)
            .lineTo(1.25f - width, 12.25f - width)
            .lineTo(2.50f + width, 12.25f - width)
            .lineTo(2.50f + width, 18.75f + width)
            .moveTo(1.25f - width, 11.75f + width)
            .lineTo(1.25f - width, 10.25f - width)
            .lineTo(2.50f + width, 10.25f - width)
            .lineTo(2.50f + width, 11.75f + width)
            .detach()
        canvas.drawPath(path, paint)
    }
}
