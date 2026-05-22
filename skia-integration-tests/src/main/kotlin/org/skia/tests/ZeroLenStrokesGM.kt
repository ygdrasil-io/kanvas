package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp::ZeroLenStrokesGM` (`zeroPath`,
 * 400 × 800).
 *
 * Regression GM for crbug.com/422974 (and the related jsfiddle) :
 * stroking degenerate / zero-length paths and verifying they round-
 * trip through the stroker as the equivalent of multiple stroke-cap
 * dots (round or square caps, two stroke widths in two columns).
 *
 * Upstream uses [SkParsePath::FromSVGString] to build the source
 * paths from SVG strings ; our :math has no SVG parser, so we
 * construct the same six paths via [SkPathBuilder] :
 *  - `fMoveHfPath` = three coincident `moveTo + lineTo` at (0,0), (10,0), (20,0)
 *  - `fMoveZfPath` = three coincident `moveTo + close`
 *  - `fDashedfPath` = `moveTo(0,0) + lineTo(25,0)`
 *  - `fCubicPath`, `fQuadPath`, `fLinePath` = single-vertex curves
 *  - `fRefPath[0..3]` = filled "expected" circles / rects per column.
 *
 * Both columns iterate the same body : ten rows of dot-like primitives
 * at progressively shifted Y, with the stroker output expected to
 * land within the reference "blob" pattern.
 */
public class ZeroLenStrokesGM : GM() {

    private lateinit var fMoveHfPath: SkPath
    private lateinit var fMoveZfPath: SkPath
    private lateinit var fDashedfPath: SkPath
    private val fRefPath: Array<SkPath?> = arrayOfNulls(4)
    private lateinit var fCubicPath: SkPath
    private lateinit var fQuadPath: SkPath
    private lateinit var fLinePath: SkPath

    override fun onOnceBeforeDraw() {
        // "M0,0h0M10,0h0M20,0h0" : three moveTo + zero-length horizontal lines.
        fMoveHfPath = SkPathBuilder().apply {
            moveTo(0f, 0f); lineTo(0f, 0f)
            moveTo(10f, 0f); lineTo(10f, 0f)
            moveTo(20f, 0f); lineTo(20f, 0f)
        }.detach()

        // "M0,0zM10,0zM20,0z" : three moveTo + close (no implicit lineTo).
        fMoveZfPath = SkPathBuilder().apply {
            moveTo(0f, 0f); close()
            moveTo(10f, 0f); close()
            moveTo(20f, 0f); close()
        }.detach()

        // "M0,0h25" : moveTo + horizontal line of length 25.
        fDashedfPath = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(25f, 0f)
            .detach()

        // "M 0 0 C 0 0 0 0 0 0" : cubic with all control points at origin.
        fCubicPath = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, 0f, 0f, 0f, 0f, 0f)
            .detach()

        // "M 0 0 Q 0 0 0 0"
        fQuadPath = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(0f, 0f, 0f, 0f)
            .detach()

        // "M 0 0 L 0 0"
        fLinePath = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(0f, 0f)
            .detach()

        // Reference filled-circle / rect paths (4 entries — one per column).
        val builders = Array(4) { SkPathBuilder() }
        for (i in 0 until 3) {
            builders[0].addCircle(i * 10f, 0f, 5f)
            builders[1].addCircle(i * 10f, 0f, 10f)
            builders[2].addRect(org.graphiks.math.SkRect.MakeLTRB(i * 10f - 4f, -2f, i * 10f + 4f, 6f))
            builders[3].addRect(org.graphiks.math.SkRect.MakeLTRB(i * 10f - 10f, -10f, i * 10f + 10f, 10f))
        }
        for (i in 0 until 4) {
            fRefPath[i] = builders[i].detach()
        }
    }

    override fun getName(): String = "zeroPath"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val fillPaint = SkPaint().apply { isAntiAlias = true }
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }

        for (i in 0 until 2) {
            fillPaint.alphaf = 1f
            strokePaint.alphaf = 1f
            strokePaint.strokeWidth = if (i != 0) 8f else 10f
            strokePaint.strokeCap = if (i != 0) SkPaint.Cap.kSquare_Cap else SkPaint.Cap.kRound_Cap

            c.save()
            c.translate(10f + i * 100f, 10f)
            c.drawPath(fMoveHfPath, strokePaint)
            c.translate(0f, 20f)
            c.drawPath(fMoveZfPath, strokePaint)

            // Dash : Make(intervals={0,10}, phase=0).
            val dashPaint = SkPaint().apply {
                isAntiAlias = true
                style = SkPaint.Style.kStroke_Style
                strokeWidth = strokePaint.strokeWidth
                strokeCap = strokePaint.strokeCap
                pathEffect = SkDashPathEffect.Make(floatArrayOf(0f, 10f), 0f)
            }
            c.translate(0f, 20f)
            c.drawPath(fDashedfPath, dashPaint)

            c.translate(0f, 20f)
            c.drawPath(fRefPath[i * 2]!!, fillPaint)

            strokePaint.strokeWidth = 20f
            strokePaint.alphaf = 0.5f
            c.translate(0f, 50f)
            c.drawPath(fMoveHfPath, strokePaint)
            c.translate(0f, 30f)
            c.drawPath(fMoveZfPath, strokePaint)
            c.translate(0f, 30f)
            fillPaint.alphaf = 0.5f
            c.drawPath(fRefPath[1 + i * 2]!!, fillPaint)
            c.translate(0f, 30f)
            c.drawPath(fCubicPath, strokePaint)
            c.translate(0f, 30f)
            c.drawPath(fQuadPath, strokePaint)
            c.translate(0f, 30f)
            c.drawPath(fLinePath, strokePaint)
            c.restore()
        }
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
    }
}
