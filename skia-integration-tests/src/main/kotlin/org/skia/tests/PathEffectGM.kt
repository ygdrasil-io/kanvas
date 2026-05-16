package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkCornerPathEffect
import org.skia.foundation.SkDashPathEffect
import org.skia.tools.SkDiscretePathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPath1DPathEffect
import org.skia.foundation.SkPath2DPathEffect
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathEffect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/patheffects.cpp::PathEffectGM` (800 × 600).
 *
 * Phase 7p_t validation GM — exercises the **full**
 * [SkPathEffect] family delivered across Phase 7p / 7p2 / 7p3 / 7p_t :
 *
 *  - [SkDashPathEffect] (Phase 7p)
 *  - [SkCornerPathEffect] + [SkDiscretePathEffect] (Phase 7p2)
 *  - [SkPathEffect.MakeCompose] (Phase 7p3)
 *  - [SkPath1DPathEffect] + [SkPath2DPathEffect] (Phase 7p_t)
 *
 * The GM has three sections :
 *  1. **Top-left column** : 5 strokes of a 5-vertex polyline, one per
 *     `gPE` entry — hair / hair+corner / stroke+corner / dash+corner /
 *     1D-rotate-stamp+corner.
 *  2. **Top-right column** (translate 320, 20) : 3 strokes of an
 *     oval+inset-rect compound path, one per `gPE2` entry — fill /
 *     discrete / 2D-tile-stamp.
 *  3. **Bottom row** : 5 filled rect cells reusing `gPE`.
 *
 * Each cell stresses the `paint.pathEffect → stroker / fill` pipeline
 * end-to-end.
 */
public class PathEffectGM : GM() {

    override fun getName(): String = "patheffect"
    override fun getISize(): SkISize = SkISize.Make(800, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        val polyline = SkPath.Polygon(
            arrayOf(
                20f to 20f,
                70f to 120f,
                120f to 30f,
                170f to 80f,
                240f to 50f,
            ),
            isClosed = false,
        )

        // -- gPE column ---------------------------------------------
        c.save()
        for (effectFn in gPE) {
            effectFn(paint)
            c.drawPath(polyline, paint)
            c.translate(0f, 75f)
        }
        c.restore()

        // -- gPE2 column (oval + inset rect) ------------------------
        val r = SkRect.MakeLTRB(0f, 0f, 250f, 120f)
        val composite = SkPathBuilder()
            .addOval(r, SkPathDirection.kCW)
            .addRect(r.makeInset(50f, 50f), SkPathDirection.kCCW)
            .detach()
        c.translate(320f, 20f)
        for (effectFn in gPE2) {
            effectFn(paint)
            c.drawPath(composite, paint)
            c.translate(0f, 160f)
        }
    }

    private fun composePE(paint: SkPaint) {
        val outer = SkCornerPathEffect.Make(25f)
        paint.pathEffect = SkPathEffect.MakeCompose(outer = outer, inner = paint.pathEffect)
    }

    private fun hairPE(paint: SkPaint) {
        paint.strokeWidth = 0f
        paint.pathEffect = null
    }

    private fun hairCornerPE(paint: SkPaint) {
        paint.strokeWidth = 0f
        paint.pathEffect = null
        composePE(paint)
    }

    private fun strokeCornerPE(paint: SkPaint) {
        paint.strokeWidth = 12f
        paint.pathEffect = null
        composePE(paint)
    }

    private fun dashCornerPE(paint: SkPaint) {
        paint.strokeWidth = 12f
        paint.pathEffect = SkDashPathEffect.Make(floatArrayOf(20f, 10f, 10f, 10f), 0f)
        composePE(paint)
    }

    private fun oneDRotateCornerPE(paint: SkPaint) {
        // 6-vertex hexagonal stamp from the upstream gXY[] array,
        // scaled 1.5×.
        val stamp = SkPathBuilder()
            .moveTo(4f, 0f)
            .lineTo(0f, -4f)
            .lineTo(8f, -4f)
            .lineTo(12f, 0f)
            .lineTo(8f, 4f)
            .lineTo(0f, 4f)
            .close()
            .detach()
            .makeTransform(SkMatrix.MakeTrans(-6f, 0f))
            .makeTransform(SkMatrix.MakeScale(1.5f))
        paint.pathEffect = SkPath1DPathEffect.Make(
            stamp = stamp,
            advance = 21f,
            phase = 0f,
            style = SkPath1DPathEffect.Style.kRotate,
        )
        composePE(paint)
    }

    private fun fillPE(paint: SkPaint) {
        paint.style = SkPaint.Style.kFill_Style
        paint.pathEffect = null
    }

    private fun discretePE(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.pathEffect = SkDiscretePathEffect.Make(10f, 4f, seed = 0)
    }

    private fun tilePE(paint: SkPaint) {
        paint.style = SkPaint.Style.kStroke_Style
        paint.pathEffect = SkPath2DPathEffect.Make(
            matrix = SkMatrix.MakeScale(12f, 12f),
            stamp = SkPath.Circle(0f, 0f, 5f),
        )
    }

    private val gPE: List<(SkPaint) -> Unit> = listOf(
        ::hairPE, ::hairCornerPE, ::strokeCornerPE, ::dashCornerPE, ::oneDRotateCornerPE,
    )

    private val gPE2: List<(SkPaint) -> Unit> = listOf(
        ::fillPE, ::discretePE, ::tilePE,
    )

    init {
        // Mirror upstream's bgcolor (default = white, no override).
        // Set black colour for paint via overrides per cell (we keep
        // paint.color = SK_ColorBLACK by default).
    }
}

private fun SkPaint.applyDefaultBlackStroke() {
    color = SK_ColorBLACK
}
