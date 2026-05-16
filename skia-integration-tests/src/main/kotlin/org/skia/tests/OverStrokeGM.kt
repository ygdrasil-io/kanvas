package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathMeasure
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import org.skia.utils.SkPathUtils

/**
 * Port of upstream Skia's `gm/overstroke.cpp::OverStroke` GM.
 *
 * Stresses very wide stroke widths ("overstroke") on quad / cubic /
 * oval primitives and compares them to the equivalent
 * [SkPathUtils.FillPathWithPaint]-derived fill. Each cell renders the
 * source path at scale `0.2` plus the rib pattern emitted by
 * [SkPathMeasure.getPosTan] sampling.
 *
 * Now that R2.1 lands a working [SkPathMeasure], the rib-sampling
 * portion is real (not a stub) — we step along the source path at
 * 5-pixel increments and emit a perpendicular line at each step.
 */
public class OverStrokeGM : GM() {

    override fun getName(): String = "OverStroke"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    private companion object {
        const val OVERSTROKE_WIDTH: Float = 500f
        const val NORMALSTROKE_WIDTH: Float = 3f
    }

    private fun normalPaint(): SkPaint = SkPaint().apply {
        isAntiAlias = true
        style = SkPaint.Style.kStroke_Style
        strokeWidth = NORMALSTROKE_WIDTH
        color = SK_ColorBLUE
    }

    private fun overstrokePaint(): SkPaint = SkPaint().apply {
        isAntiAlias = true
        style = SkPaint.Style.kStroke_Style
        strokeWidth = OVERSTROKE_WIDTH
    }

    private fun quadPath(): SkPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .lineTo(100f, 0f)
        .quadTo(50f, -40f, 0f, 0f)
        .close()
        .detach()

    private fun cubicPath(): SkPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .cubicTo(25f, 75f, 75f, -50f, 100f, 0f)
        .detach()

    private fun ovalPath(): SkPath {
        val oval = SkRect.MakeXYWH(0f, -25f, 100f, 50f)
        return SkPathBuilder().arcTo(oval, 0f, 359f, true).close().detach()
    }

    private fun ribsPath(path: SkPath, radius: Float): SkPath {
        val ribs = SkPathBuilder()
        val spacing = 5f
        var accum = 0f
        val meas = SkPathMeasure(path, forceClosed = false)
        val length = meas.getLength()
        val pos = SkPoint()
        val tan = SkVector()
        while (accum < length) {
            if (meas.getPosTan(accum, pos, tan)) {
                // Scale tangent to ±radius, then rotate 90° CCW :
                //   (x, y) → (-y, x)  (Skia's SkPointPriv::RotateCCW).
                val sx = tan.fX * radius
                val sy = tan.fY * radius
                val rx = -sy
                val ry = sx
                ribs.moveTo(pos.fX + rx, pos.fY + ry)
                ribs.lineTo(pos.fX - rx, pos.fY - ry)
            }
            accum += spacing
        }
        return ribs.detach()
    }

    private fun drawRibs(canvas: SkCanvas, path: SkPath) {
        val ribs = ribsPath(path, OVERSTROKE_WIDTH / 2f)
        val p = normalPaint().apply {
            strokeWidth = 1f
            color = SK_ColorGREEN
        }
        canvas.drawPath(ribs, p)
    }

    // ── quads ──────────────────────────────────────────────────────────

    private fun drawSmallQuad(canvas: SkCanvas) {
        val p = normalPaint()
        val path = quadPath()
        drawRibs(canvas, path)
        canvas.drawPath(path, p)
    }

    private fun drawLargeQuad(canvas: SkCanvas) {
        val p = overstrokePaint()
        val path = quadPath()
        canvas.drawPath(path, p)
        drawRibs(canvas, path)
    }

    private fun drawQuadFillpath(canvas: SkCanvas) {
        val path = quadPath()
        val p = overstrokePaint()
        val fillp = normalPaint().apply { color = SK_ColorMAGENTA }
        val builder = SkPathBuilder()
        SkPathUtils.FillPathWithPaint(path, p, builder)
        canvas.drawPath(builder.detach(), fillp)
    }

    private fun drawStrokedQuad(canvas: SkCanvas) {
        canvas.translate(400f, 0f)
        drawLargeQuad(canvas)
        drawQuadFillpath(canvas)
    }

    // ── cubics ─────────────────────────────────────────────────────────

    private fun drawSmallCubic(canvas: SkCanvas) {
        val p = normalPaint()
        val path = cubicPath()
        drawRibs(canvas, path)
        canvas.drawPath(path, p)
    }

    private fun drawLargeCubic(canvas: SkCanvas) {
        val p = overstrokePaint()
        val path = cubicPath()
        canvas.drawPath(path, p)
        drawRibs(canvas, path)
    }

    private fun drawCubicFillpath(canvas: SkCanvas) {
        val path = cubicPath()
        val p = overstrokePaint()
        val fillp = normalPaint().apply { color = SK_ColorMAGENTA }
        val builder = SkPathBuilder()
        SkPathUtils.FillPathWithPaint(path, p, builder)
        canvas.drawPath(builder.detach(), fillp)
    }

    private fun drawStrokedCubic(canvas: SkCanvas) {
        canvas.translate(400f, 0f)
        drawLargeCubic(canvas)
        drawCubicFillpath(canvas)
    }

    // ── ovals ──────────────────────────────────────────────────────────

    private fun drawSmallOval(canvas: SkCanvas) {
        val p = normalPaint()
        val path = ovalPath()
        drawRibs(canvas, path)
        canvas.drawPath(path, p)
    }

    private fun drawLargeOval(canvas: SkCanvas) {
        val p = overstrokePaint()
        val path = ovalPath()
        canvas.drawPath(path, p)
        drawRibs(canvas, path)
    }

    private fun drawOvalFillpath(canvas: SkCanvas) {
        val path = ovalPath()
        val p = overstrokePaint()
        val fillp = normalPaint().apply { color = SK_ColorMAGENTA }
        val builder = SkPathBuilder()
        SkPathUtils.FillPathWithPaint(path, p, builder)
        canvas.drawPath(builder.detach(), fillp)
    }

    private fun drawStrokedOval(canvas: SkCanvas) {
        canvas.translate(400f, 0f)
        drawLargeOval(canvas)
        drawOvalFillpath(canvas)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val examples: List<(SkCanvas) -> Unit> = listOf(
            ::drawSmallQuad, ::drawStrokedQuad,
            ::drawSmallCubic, ::drawStrokedCubic,
            ::drawSmallOval, ::drawStrokedOval,
        )
        val width = 2
        for (i in examples.indices) {
            val x = i % width
            val y = i / width
            c.save()
            c.translate(150f * x, 150f * y)
            c.scale(0.2f, 0.2f)
            c.translate(300f, 400f)
            examples[i](c)
            c.restore()
        }
    }
}
