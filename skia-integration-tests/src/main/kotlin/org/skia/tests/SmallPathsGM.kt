package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/smallpaths.cpp::SmallPathsGM` (640 × 512).
 *
 * 11 small paths (triangle, rect, oval, 5-star, 13-star, three-line,
 * arrow, conic curve, battery #1, battery #2, ring) drawn in 4 columns :
 *   1. Filled.
 *   2. Stroked with `gWidths[i]` (per-path).
 *   3. Stroked with `gWidths[i] + 2`.
 *   4. Stroke-and-fill with `gWidths[i]`.
 *
 * Each path provides its own `(SkPath, dy)` advance ; some paths inject
 * an `xOffset` reset (`gXTranslate[i]`) so the column doesn't drift
 * after the battery glyphs (which live at `xOffset = 225` in source
 * space).
 *
 * This GM stresses the path subsystem broadly : conicTo, cubicTo,
 * close, addCircle, near-zero stroke widths (hairlines), and per-path
 * miter limits.
 */
public class SmallPathsGM : GM() {

    override fun getName(): String = "smallpaths"
    override fun getISize(): SkISize = SkISize.Make(640, 512)

    private val paths: Array<SkPath>
    private val dy: FloatArray
    private val widths: FloatArray = floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 7f, 14f, 0f, 0f, 0f)
    private val miters: FloatArray = floatArrayOf(2f, 3f, 3f, 3f, 4f, 4f, 4f, 4f, 4f, 4f, 4f)
    private val xTranslate: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -220.625f, 0f, 0f)

    init {
        val pairs = listOf(
            ::makeTriangle, ::makeRect, ::makeOval,
            { makeStar(5) }, { makeStar(13) },
            ::makeThreeLine, ::makeArrow, ::makeCurve,
            ::makeBattery, ::makeBattery2, ::makeRing,
        ).map { it() }
        paths = Array(pairs.size) { pairs[it].first }
        dy = FloatArray(pairs.size) { pairs[it].second }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }

        // Column 1 — filled.
        c.save()
        for (i in paths.indices) {
            c.drawPath(paths[i], paint)
            c.translate(xTranslate[i], dy[i])
        }
        c.restore()
        c.translate(120f, 0f)

        // Column 2 — stroked.
        c.save()
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeCap = SkPaint.Cap.kButt_Cap
        for (i in paths.indices) {
            paint.strokeWidth = widths[i]
            paint.strokeMiter = miters[i]
            c.drawPath(paths[i], paint)
            c.translate(xTranslate[i], dy[i])
        }
        c.restore()
        c.translate(120f, 0f)

        // Column 3 — stroked at width+2.
        c.save()
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeCap = SkPaint.Cap.kButt_Cap
        for (i in paths.indices) {
            paint.strokeWidth = widths[i] + 2f
            paint.strokeMiter = miters[i]
            c.drawPath(paths[i], paint)
            c.translate(xTranslate[i], dy[i])
        }
        c.restore()
        c.translate(120f, 0f)

        // Column 4 — stroke + fill.
        paint.style = SkPaint.Style.kStrokeAndFill_Style
        paint.strokeCap = SkPaint.Cap.kButt_Cap
        for (i in paths.indices) {
            paint.strokeWidth = widths[i]
            paint.strokeMiter = miters[i]
            c.drawPath(paths[i], paint)
            c.translate(xTranslate[i], dy[i])
        }
    }

    // ─── Path makers (`MakePathProc` upstream) ──────────────────────────

    private fun makeTriangle(): Pair<SkPath, Float> {
        val pts = intArrayOf(10, 20, 15, 5, 30, 30)
        return SkPathBuilder()
            .moveTo(pts[0].toFloat(), pts[1].toFloat())
            .lineTo(pts[2].toFloat(), pts[3].toFloat())
            .lineTo(pts[4].toFloat(), pts[5].toFloat())
            .close()
            .detach()
            .makeOffset(10f, 0f) to 30f
    }

    private fun makeRect(): Pair<SkPath, Float> {
        val r = SkRect.MakeLTRB(10f, 10f, 30f, 30f).makeOffset(10f, 0f)
        return SkPath.Rect(r) to 30f
    }

    private fun makeOval(): Pair<SkPath, Float> {
        val r = SkRect.MakeLTRB(10f, 10f, 30f, 30f).makeOffset(10f, 0f)
        return SkPath.Oval(r) to 30f
    }

    private fun makeStar(n: Int): Pair<SkPath, Float> {
        val cx = 45f
        val r = 20f
        var rad = -PI.toFloat() / 2f
        val drad = (n shr 1) * PI.toFloat() * 2f / n
        val b = SkPathBuilder()
        b.moveTo(cx, cx - r)
        for (i in 1 until n) {
            rad += drad
            b.lineTo(cx + cos(rad) * r, cx + sin(rad) * r)
        }
        b.close()
        return b.detach() to (r * 2 * 6 / 5)
    }

    private fun makeThreeLine(): Pair<SkPath, Float> {
        val xOffset = 34f
        val yOffset = 50f
        val b = SkPathBuilder()
        b.moveTo(-32.5f + xOffset, 0f + yOffset)
        b.lineTo(32.5f + xOffset, 0f + yOffset)

        b.moveTo(-32.5f + xOffset, 19f + yOffset)
        b.lineTo(32.5f + xOffset, 19f + yOffset)

        b.moveTo(-32.5f + xOffset, -19f + yOffset)
        b.lineTo(32.5f + xOffset, -19f + yOffset)
        b.lineTo(-32.5f + xOffset, -19f + yOffset)

        b.close()
        return b.detach() to 70f
    }

    private fun makeArrow(): Pair<SkPath, Float> {
        val xOffset = 34f
        val yOffset = 40f
        val b = SkPathBuilder()
        b.moveTo(-26f + xOffset, 0f + yOffset)
        b.lineTo(26f + xOffset, 0f + yOffset)

        b.moveTo(-28f + xOffset, -2.4748745f + yOffset)
        b.lineTo(0f + xOffset, 25.525126f + yOffset)

        b.moveTo(-28f + xOffset, 2.4748745f + yOffset)
        b.lineTo(0f + xOffset, -25.525126f + yOffset)
        b.lineTo(-28f + xOffset, 2.4748745f + yOffset)

        b.close()
        return b.detach() to 70f
    }

    private fun makeCurve(): Pair<SkPath, Float> {
        val xOffset = -382f
        val yOffset = -50f
        val b = SkPathBuilder()
        b.moveTo(491f + xOffset, 56f + yOffset)
        b.conicTo(
            435.93292f + xOffset, 56.000031f + yOffset,
            382.61078f + xOffset, 69.752716f + yOffset,
            0.9920463f,
        )
        return b.detach() to 40f
    }

    private fun makeBattery(): Pair<SkPath, Float> {
        val xOffset = 5f
        val b = SkPathBuilder()
        b.moveTo(24.67f + xOffset, 0.33000004f)
        b.lineTo(8.3299999f + xOffset, 0.33000004f)
        b.lineTo(8.3299999f + xOffset, 5.3299999f)
        b.lineTo(0.33000004f + xOffset, 5.3299999f)
        b.lineTo(0.33000004f + xOffset, 50.669998f)
        b.lineTo(32.669998f + xOffset, 50.669998f)
        b.lineTo(32.669998f + xOffset, 5.3299999f)
        b.lineTo(24.67f + xOffset, 5.3299999f)
        b.lineTo(24.67f + xOffset, 0.33000004f)
        b.close()

        b.moveTo(25.727224f + xOffset, 12.886665f)
        b.lineTo(10.907918f + xOffset, 12.886665f)
        b.lineTo(7.5166659f + xOffset, 28.683645f)
        b.lineTo(14.810181f + xOffset, 28.683645f)
        b.lineTo(7.7024879f + xOffset, 46.135998f)
        b.lineTo(28.049999f + xOffset, 25.136419f)
        b.lineTo(16.854223f + xOffset, 25.136419f)
        b.lineTo(25.727224f + xOffset, 12.886665f)
        b.close()
        return b.detach() to 50f
    }

    private fun makeBattery2(): Pair<SkPath, Float> {
        val xOffset = 225.625f
        val b = SkPathBuilder()
        b.moveTo(32.669998f + xOffset, 9.8640003f)
        b.lineTo(0.33000004f + xOffset, 9.8640003f)
        b.lineTo(0.33000004f + xOffset, 50.669998f)
        b.lineTo(32.669998f + xOffset, 50.669998f)
        b.lineTo(32.669998f + xOffset, 9.8640003f)
        b.close()

        b.moveTo(10.907918f + xOffset, 12.886665f)
        b.lineTo(25.727224f + xOffset, 12.886665f)
        b.lineTo(16.854223f + xOffset, 25.136419f)
        b.lineTo(28.049999f + xOffset, 25.136419f)
        b.lineTo(7.7024879f + xOffset, 46.135998f)
        b.lineTo(14.810181f + xOffset, 28.683645f)
        b.lineTo(7.5166659f + xOffset, 28.683645f)
        b.lineTo(10.907918f + xOffset, 12.886665f)
        b.close()
        return b.detach() to 60f
    }

    private fun makeRing(): Pair<SkPath, Float> {
        val xOffset = 120f
        val yOffset = -270f
        val b = SkPathBuilder()
        b.setFillType(org.skia.foundation.SkPathFillType.kWinding)

        b.moveTo(xOffset + 144.859f, yOffset + 285.172f)
        b.lineTo(xOffset + 144.859f, yOffset + 285.172f)
        b.lineTo(xOffset + 144.859f, yOffset + 285.172f)
        b.lineTo(xOffset + 143.132f, yOffset + 284.617f)
        b.lineTo(xOffset + 144.859f, yOffset + 285.172f)
        b.close()

        b.moveTo(xOffset + 135.922f, yOffset + 286.844f)
        b.lineTo(xOffset + 135.922f, yOffset + 286.844f)
        b.lineTo(xOffset + 135.922f, yOffset + 286.844f)
        b.lineTo(xOffset + 135.367f, yOffset + 288.571f)
        b.lineTo(xOffset + 135.922f, yOffset + 286.844f)
        b.close()

        b.moveTo(xOffset + 135.922f, yOffset + 286.844f)
        b.cubicTo(
            xOffset + 137.07f, yOffset + 287.219f,
            xOffset + 138.242f, yOffset + 287.086f,
            xOffset + 139.242f, yOffset + 286.578f,
        )
        b.cubicTo(
            xOffset + 140.234f, yOffset + 286.078f,
            xOffset + 141.031f, yOffset + 285.203f,
            xOffset + 141.406f, yOffset + 284.055f,
        )
        b.lineTo(xOffset + 144.859f, yOffset + 285.172f)
        b.cubicTo(
            xOffset + 143.492f, yOffset + 289.375f,
            xOffset + 138.992f, yOffset + 291.656f,
            xOffset + 134.797f, yOffset + 290.297f,
        )
        b.lineTo(xOffset + 135.922f, yOffset + 286.844f)
        b.close()

        b.moveTo(xOffset + 129.68f, yOffset + 280.242f)
        b.lineTo(xOffset + 129.68f, yOffset + 280.242f)
        b.lineTo(xOffset + 129.68f, yOffset + 280.242f)
        b.lineTo(xOffset + 131.407f, yOffset + 280.804f)
        b.lineTo(xOffset + 129.68f, yOffset + 280.242f)
        b.close()

        b.moveTo(xOffset + 133.133f, yOffset + 281.367f)
        b.cubicTo(
            xOffset + 132.758f, yOffset + 282.508f,
            xOffset + 132.883f, yOffset + 283.687f,
            xOffset + 133.391f, yOffset + 284.679f,
        )
        b.cubicTo(
            xOffset + 133.907f, yOffset + 285.679f,
            xOffset + 134.774f, yOffset + 286.468f,
            xOffset + 135.922f, yOffset + 286.843f,
        )
        b.lineTo(xOffset + 134.797f, yOffset + 290.296f)
        b.cubicTo(
            xOffset + 130.602f, yOffset + 288.929f,
            xOffset + 128.313f, yOffset + 284.437f,
            xOffset + 129.68f, yOffset + 280.241f,
        )
        b.lineTo(xOffset + 133.133f, yOffset + 281.367f)
        b.close()

        b.moveTo(xOffset + 139.742f, yOffset + 275.117f)
        b.lineTo(xOffset + 139.742f, yOffset + 275.117f)
        b.lineTo(xOffset + 139.18f, yOffset + 276.844f)
        b.lineTo(xOffset + 139.742f, yOffset + 275.117f)
        b.close()

        b.moveTo(xOffset + 138.609f, yOffset + 278.57f)
        b.cubicTo(
            xOffset + 137.461f, yOffset + 278.203f,
            xOffset + 136.297f, yOffset + 278.328f,
            xOffset + 135.297f, yOffset + 278.836f,
        )
        b.cubicTo(
            xOffset + 134.297f, yOffset + 279.344f,
            xOffset + 133.508f, yOffset + 280.219f,
            xOffset + 133.133f, yOffset + 281.367f,
        )
        b.lineTo(xOffset + 129.68f, yOffset + 280.242f)
        b.cubicTo(
            xOffset + 131.047f, yOffset + 276.039f,
            xOffset + 135.539f, yOffset + 273.758f,
            xOffset + 139.742f, yOffset + 275.117f,
        )
        b.lineTo(xOffset + 138.609f, yOffset + 278.57f)
        b.close()

        b.moveTo(xOffset + 141.406f, yOffset + 284.055f)
        b.cubicTo(
            xOffset + 141.773f, yOffset + 282.907f,
            xOffset + 141.648f, yOffset + 281.735f,
            xOffset + 141.148f, yOffset + 280.735f,
        )
        b.cubicTo(
            xOffset + 140.625f, yOffset + 279.735f,
            xOffset + 139.757f, yOffset + 278.946f,
            xOffset + 138.609f, yOffset + 278.571f,
        )
        b.lineTo(xOffset + 139.742f, yOffset + 275.118f)
        b.cubicTo(
            xOffset + 143.937f, yOffset + 276.493f,
            xOffset + 146.219f, yOffset + 280.977f,
            xOffset + 144.859f, yOffset + 285.173f,
        )
        b.lineTo(xOffset + 141.406f, yOffset + 284.055f)
        b.close()

        return b.detach() to 15f
    }
}
