package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/roundrects.cpp` (`RoundRectGM`).
 *
 * A 5 × 8 grid of stroked / filled circle-corner rrects under all
 * combinations of 5 paints (no-AA / AA / AA+stroke=5 / AA+stroke=0 /
 * AA+stroke-and-fill=3) and 8 matrices (identity / scales / rotate / skew),
 * mirroring [OvalGM]'s structure but with `setRectXY(rect, 5, 5)` rrects
 * instead of ovals. Plus the same special-case rows (tall / wide /
 * super-skinny / super-short / radial-gradient / strokes-and-radii / OOO /
 * stroke-bigger-than-radius).
 *
 * Reference image: `roundrects.png`, 1200 × 900, BG = black.
 *
 * **Caveat — radial-gradient row dropped.** Same as in [OvalGM]: Phase 4b
 * doesn't yet expose `SkPaint.setShader` / `SkShader`, so we render solid
 * colours where upstream had a 3-stop radial gradient. Visual diverges in
 * the gradient column.
 */
public class RoundRectGM : GM() {

    init { setBGColor(0xFF000000.toInt()) }

    override fun getName(): String = "roundrects"
    override fun getISize(): SkISize = SkISize.Make(1200, 900)

    private val paints: List<() -> SkPaint> = listOf(
        { SkPaint() },                                                       // 0: no AA
        { SkPaint().apply { isAntiAlias = true } },                          // 1: AA
        { SkPaint().apply {                                                  // 2: AA + stroke=5
            isAntiAlias = true; style = SkPaint.Style.kStroke_Style; strokeWidth = 5f
        } },
        { SkPaint().apply {                                                  // 3: AA + hairline
            isAntiAlias = true; style = SkPaint.Style.kStroke_Style
        } },
        { SkPaint().apply {                                                  // 4: AA + stroke-and-fill=3
            isAntiAlias = true; style = SkPaint.Style.kStrokeAndFill_Style; strokeWidth = 3f
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
        val rand = SkRandom(1)
        c.translate(20f, 20f)
        val kRect = SkRect.MakeLTRB(-20f, -30f, 20f, 30f)
        val circleRRect = SkRRect.MakeRectXY(kRect, 5f, 5f)

        val kXStart = 60f
        val kYStart = 80f
        val kXStep = 150
        val kYStep = 160
        val maxX = matrices.size

        val rectPaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            color = 0xFFCCCCCC.toInt()    // SK_ColorLTGRAY
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

                c.drawRect(kRect, rectPaint)
                c.drawRRect(circleRRect, paint)

                c.restore()
                testCount += 1
            }
        }

        // Tall and skinny rrect (column 2.55, rectXY(5, 10)).
        for (i in paints.indices) {
            val rect = SkRect.MakeLTRB(-20f, -60f, 20f, 60f)
            val ellipseRect = SkRRect.MakeRectXY(rect, 5f, 10f)
            c.save()
            c.translate(kXStart + kXStep * 2.55f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(rect, rectPaint)
            c.drawRRect(ellipseRect, paint)
            c.restore()
        }

        // Wide and short rrect (column 4, half-row offset, rectXY(20, 5)).
        for (i in paints.indices) {
            val rect = SkRect.MakeLTRB(-80f, -30f, 80f, 30f)
            val ellipseRect = SkRRect.MakeRectXY(rect, 20f, 5f)
            c.save()
            c.translate(
                kXStart + kXStep * 4 + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(rect, rectPaint)
            c.drawRRect(ellipseRect, paint)
            c.restore()
        }

        // Super-skinny rrect (column 3.25, rectXY(5, 5), no rect outline).
        for (i in paints.indices) {
            val rect = SkRect.MakeLTRB(0f, -60f, 1f, 60f)
            val circleRect = SkRRect.MakeRectXY(rect, 5f, 5f)
            c.save()
            c.translate(kXStart + kXStep * 3.25f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRRect(circleRect, paint)
            c.restore()
        }

        // Super-short rrect (column 2.5, half-row offset, rectXY(5, 5), no rect outline).
        for (i in paints.indices) {
            val rect = SkRect.MakeLTRB(-80f, -1f, 80f, 0f)
            val circleRect = SkRRect.MakeRectXY(rect, 5f, 5f)
            c.save()
            c.translate(
                kXStart + kXStep * 2.5f + 0.25f,
                kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
            )
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRRect(circleRect, paint)
            c.restore()
        }

        // Radial-gradient row (column 0, half-row offset). Solid colour
        // fallback — shader infrastructure not yet exposed.
        for (i in paints.indices) {
            c.save()
            c.translate(kXStart + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i]().apply { color = genColor(rand) }
            c.drawRect(kRect, rectPaint)
            c.drawRRect(circleRRect, paint)
            c.restore()
        }

        // Strokes-and-radii column (column 5, rows 0..3, half-row offset).
        run {
            val radii = arrayOf(
                Pair(10f, 10f),
                Pair(5f, 15f),
                Pair(5f, 15f),
                Pair(5f, 15f),
            )
            val strokeWidths = floatArrayOf(20f, 10f, 20f, 40f)
            for (i in 0 until 4) {
                val circleRect = SkRRect.MakeRectXY(kRect, radii[i].first, radii[i].second)
                c.save()
                c.translate(
                    kXStart + kXStep * 5 + 0.25f,
                    kYStart + kYStep * i + 0.75f + 0.5f * kYStep,
                )
                val color = genColor(rand)
                val p = SkPaint().apply {
                    isAntiAlias = true
                    style = SkPaint.Style.kStroke_Style
                    strokeWidth = strokeWidths[i]
                    this.color = color
                }
                c.drawRRect(circleRect, p)
                c.restore()
            }
        }

        // OOO rect via drawRoundRect (skbug.com/40034920). Intentionally
        // out-of-order rect (left > right, top > bottom). drawRoundRect
        // sorts these via SkRRect.initializeRect.
        run {
            c.save()
            c.translate(
                kXStart + kXStep * 5 + 0.25f,
                kYStart + kYStep * 4 + 0.25f + 0.5f * kYStep,
            )
            val color = genColor(rand)
            val p = SkPaint().apply { this.color = color }
            val oooRect = SkRect.MakeLTRB(20f, 30f, -20f, -30f)   // intentionally out of order
            c.drawRoundRect(oooRect, 10f, 10f, p)
            c.restore()
        }

        // RRect with stroke > radius/2 (column 5, special row).
        run {
            val smallRect = SkRect.MakeLTRB(-30f, -20f, 30f, 20f)
            val circleRect = SkRRect.MakeRectXY(smallRect, 5f, 5f)
            c.save()
            c.translate(
                kXStart + kXStep * 5 + 0.25f,
                kYStart - kYStep + 73f / 4f + 0.5f * kYStep,
            )
            val color = genColor(rand)
            val p = SkPaint().apply {
                isAntiAlias = true
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 25f
                this.color = color
            }
            c.drawRRect(circleRect, p)
            c.restore()
        }
    }

    private fun genColor(rand: SkRandom): Int {
        val hsv = floatArrayOf(
            rand.nextRangeF(0.0f, 360.0f),
            rand.nextRangeF(0.75f, 1.0f),
            rand.nextRangeF(0.75f, 1.0f),
        )
        return ToolUtils.colorTo565(ToolUtils.skHSVToColor(hsv))
    }
}
