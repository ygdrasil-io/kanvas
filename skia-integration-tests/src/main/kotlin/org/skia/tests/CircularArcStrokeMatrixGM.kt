package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ScalarPI
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::circular_arc_stroke_matrix`
 * (`DEF_SIMPLE_GM(circular_arc_stroke_matrix, canvas, 820, 1090)`).
 *
 * Draws one-radian stroked arc segments under a battery of 2D transforms
 * (reflections, rotations, near-axis perturbations) for each of three
 * stroke-cap styles (`kRound_Cap`, `kButt_Cap`, `kSquare_Cap`). Each cell
 * shows the arc in red (positive sweep) and blue (complementary sweep
 * `sweep − 360`), both at 50 % alpha so cap overlap shows as magenta.
 *
 * The purpose is to verify that the stroker correctly handles degenerate
 * cap directions across the full set of affine axis-reflection symmetries.
 */
public class CircularArcStrokeMatrixGM : GM() {

    override fun getName(): String = "circular_arc_stroke_matrix"
    override fun getISize(): SkISize = SkISize.Make(820, 1090)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kRadius      = 40f
        val kStrokeWidth = 5f
        val kStart       = 89f
        val kSweep       = 180f / SK_ScalarPI   // one radian

        // ── Base matrix set (9 entries) ──────────────────────────────────────
        val baseMatrices = mutableListOf<SkMatrix>()

        // Rotation 45° around (kRadius, kRadius).
        baseMatrices.add(SkMatrix.MakeRotate(kRadius, kRadius, 45f))

        // Identity.
        baseMatrices.add(SkMatrix.I())

        // Flip X around x = 2*kRadius.
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(-1f, 0f, 2*kRadius, 0f, 1f, 0f, 0f, 0f, 1f))
        )
        // Flip Y around y = 2*kRadius.
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(1f, 0f, 0f, 0f, -1f, 2*kRadius, 0f, 0f, 1f))
        )
        // Flip Y around y = 2*kRadius (duplicate — matches upstream exactly).
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(1f, 0f, 0f, 0f, -1f, 2*kRadius, 0f, 0f, 1f))
        )
        // Transpose + flip (90° + reflection).
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(0f, -1f, 2*kRadius, -1f, 0f, 2*kRadius, 0f, 0f, 1f))
        )
        // Anti-transpose.
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(0f, -1f, 2*kRadius, 1f, 0f, 0f, 0f, 0f, 1f))
        )
        // Transpose.
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        )
        // Anti-transpose + flip.
        baseMatrices.add(
            SkMatrix.MakeFrom9(floatArrayOf(0f, 1f, 0f, -1f, 0f, 2*kRadius, 0f, 0f, 1f))
        )

        val baseMatrixCnt = baseMatrices.size

        // ── Augmented set: base × tiny CW, base × tiny CCW, base × CW 45 ─
        val matrices = mutableListOf<SkMatrix>()
        matrices.addAll(baseMatrices)

        val tinyCW  = SkMatrix.MakeRotate(0.001f, kRadius, kRadius)
        val tinyCCW = SkMatrix.MakeRotate(-0.001f, kRadius, kRadius)
        val cw45    = SkMatrix.MakeRotate(45f, kRadius, kRadius)

        for (i in 0 until baseMatrixCnt) {
            matrices.add(SkMatrix.concat(baseMatrices[i], tinyCW))
        }
        for (i in 0 until baseMatrixCnt) {
            matrices.add(SkMatrix.concat(baseMatrices[i], tinyCCW))
        }
        for (i in 0 until baseMatrixCnt) {
            matrices.add(SkMatrix.concat(baseMatrices[i], cw45))
        }

        // ── Render ───────────────────────────────────────────────────────────
        val kPad   = 2f * kStrokeWidth
        val bounds = SkRect.MakeWH(2f * kRadius, 2f * kRadius)

        c.translate(kPad, kPad)

        var x = 0
        var y = 0

        for (cap in listOf(
            SkPaint.Cap.kRound_Cap,
            SkPaint.Cap.kButt_Cap,
            SkPaint.Cap.kSquare_Cap,
        )) {
            for (m in matrices) {
                val paint = SkPaint().apply {
                    strokeCap   = cap
                    isAntiAlias = true
                    style       = SkPaint.Style.kStroke_Style
                    strokeWidth = kStrokeWidth
                }
                c.save()
                c.translate(x * (2f * kRadius + kPad), y * (2f * kRadius + kPad))
                c.concat(m)

                paint.color = SK_ColorRED
                paint.alpha = 0x80
                c.drawArc(bounds, kStart, kSweep, false, paint)

                paint.color = SK_ColorBLUE
                paint.alpha = 0x80
                c.drawArc(bounds, kStart, kSweep - 360f, false, paint)

                c.restore()

                ++x
                if (x == baseMatrixCnt) {
                    x = 0
                    ++y
                }
            }
        }
    }
}
