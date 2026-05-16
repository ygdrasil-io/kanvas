package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/ovals.cpp` (`OvalGM`).
 *
 * A 5 × 8 grid of stroked / filled ovals under all combinations of
 * 5 paints (no-AA / AA / AA+stroke=5 / AA+stroke=0 / AA+stroke-and-fill=3)
 * and 8 matrices (identity / scale (3,2)/(2,2)/(1,2)/(4,1) / rotate 90° /
 * skew (2,3) / rotate 60°), plus four special-case rows (tall / wide /
 * super-skinny / super-short ovals) and a reflected-oval row.
 *
 * Each cell first draws an LTGRAY rectangle outline (the oval's bounding
 * rect, hairline-stroked) then the oval itself. Cells use deterministic
 * 565-quantised HSV colours from a bit-compatible [SkRandom].
 *
 * The radial-gradient row at column 0 (half-row offset) uses the new
 * Phase 5a [SkRadialGradient] (3-stop blue / red / green, radius 20,
 * `kClamp` tile mode), matching upstream.
 *
 * Reference image: `ovals.png`, 1200 × 900, BG = black.
 */
public class OvalGM : GM() {

    init { setBGColor(0xFF000000.toInt()) }

    override fun getName(): String = "ovals"
    override fun getISize(): SkISize = SkISize.Make(1200, 900)

    private val paints: List<() -> SkPaint> = listOf(
        // 0: no AA.
        { SkPaint() },
        // 1: AA.
        { SkPaint().apply { isAntiAlias = true } },
        // 2: AA + stroke, width = 5.
        { SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 5f
        } },
        // 3: AA + stroke, width = 0 (hairline → falls back to 1 px in our pipeline).
        { SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        } },
        // 4: AA + stroke-and-fill, width = 3.
        { SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStrokeAndFill_Style
            strokeWidth = 3f
        } },
    )

    private val matrices: List<SkMatrix> = listOf(
        SkMatrix.Identity,
        SkMatrix.MakeScale(3f, 2f),
        SkMatrix.MakeScale(2f, 2f),
        SkMatrix.MakeScale(1f, 2f),
        SkMatrix.MakeScale(4f, 1f),
        SkMatrix.MakeRotate(90f),
        SkMatrix.MakeSkew(2f, 3f),
        SkMatrix.MakeRotate(60f),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Rec.2020 transforms black to black, so eraseColor is fine — but
        // ArcOfZorroGM-style drawPaint also works. We rely on `setBGColor`
        // (white) being overridden by `init { setBGColor(black) }` above and
        // the harness eraseColor at start.
        val rand = SkRandom(1)
        c.translate(20f, 20f)
        val kOval = SkRect.MakeLTRB(-20f, -30f, 20f, 30f)

        val kXStart = 60f
        val kYStart = 80f
        val kXStep = 150
        val kYStep = 160
        val maxX = matrices.size

        val rectPaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            color = 0xFFCCCCCC.toInt()    // SK_ColorLTGRAY
            // strokeWidth = 0 → hairline (falls back to 1 in our pipeline).
        }

        var testCount = 0
        for (i in paints.indices) {
            for (j in matrices.indices) {
                c.save()
                val dx = kXStart + kXStep * (testCount % maxX) + 0.25f
                val dy = kYStart + kYStep * (testCount / maxX) + 0.75f
                c.translate(dx, dy)
                c.concat(matrices[j])

                val color = genColor(rand)
                val paint = paints[i]().apply { this.color = color }

                c.drawRect(kOval, rectPaint)
                c.drawOval(kOval, paint)

                c.restore()
                testCount += 1
            }
        }

        // Special cases — each loops over the 5 paints, drawing one oval
        // per paint at a deterministic position outside the main grid.
        // Order matters: rand consumption must match upstream so the colours
        // line up with the reference.

        // Tall and skinny oval (column 2.55).
        for (i in paints.indices) {
            val oval = SkRect.MakeLTRB(-20f, -60f, 20f, 60f)
            c.save()
            c.translate(kXStart + kXStep * 2.55f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(oval, rectPaint)
            c.drawOval(oval, paint)
            c.restore()
        }

        // Wide and short oval (column 4, half-row offset).
        for (i in paints.indices) {
            val oval = SkRect.MakeLTRB(-80f, -30f, 80f, 30f)
            c.save()
            c.translate(
                kXStart + kXStep * 4 + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(oval, rectPaint)
            c.drawOval(oval, paint)
            c.restore()
        }

        // Super-skinny oval (column 3.25, no rect outline).
        for (i in paints.indices) {
            val oval = SkRect.MakeLTRB(0f, -60f, 1f, 60f)
            c.save()
            c.translate(kXStart + kXStep * 3.25f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawOval(oval, paint)
            c.restore()
        }

        // Super-short oval (column 2.5, half-row offset, no rect outline).
        for (i in paints.indices) {
            val oval = SkRect.MakeLTRB(-80f, -1f, 80f, 0f)
            c.save()
            c.translate(
                kXStart + kXStep * 2.5f + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawOval(oval, paint)
            c.restore()
        }

        // Radial-gradient row (column 0, half-row offset). Phase 5a — 3-stop
        // blue / red / green radial gradient at the local origin, radius 20,
        // kClamp. The gradient is built once and shared across all 5 paints
        // (the shader's per-draw `setupForDraw` re-prepares it for each).
        val gradient = org.skia.foundation.SkRadialGradient.Make(
            org.graphiks.math.SkPoint(0f, 0f), 20f,
            intArrayOf(0xFF0000FF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            org.skia.foundation.SkTileMode.kClamp,
        )
        for (i in paints.indices) {
            c.save()
            c.translate(
                kXStart + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            // genColor(rand) is still consumed even when we don't use it for
            // the paint colour — keeps rand in lockstep with upstream for
            // the rows after this one (reflected-oval).
            val paint = paints[i]().apply {
                color = genColor(rand)
                shader = gradient
            }
            c.drawRect(kOval, rectPaint)
            c.drawOval(kOval, paint)
            c.restore()
        }

        // Reflected oval (column 5, half-row offset, rotate(90)+scale(1,-1)+scale(1,0.66)).
        for (i in paints.indices) {
            val oval = SkRect.MakeLTRB(-30f, -30f, 30f, 30f)
            c.save()
            c.translate(
                kXStart + kXStep * 5 + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            c.rotate(90f)
            c.scale(1f, -1f)
            c.scale(1f, 0.66f)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(oval, rectPaint)
            c.drawOval(oval, paint)
            c.restore()
        }
    }

    /** Mirrors upstream's `genColor` — same HSV ranges as `roundrects.cpp`. */
    private fun genColor(rand: SkRandom): Int {
        val hsv = floatArrayOf(
            rand.nextRangeF(0.0f, 360.0f),
            rand.nextRangeF(0.75f, 1.0f),
            rand.nextRangeF(0.75f, 1.0f),
        )
        return ToolUtils.colorTo565(ToolUtils.skHSVToColor(hsv))
    }
}
