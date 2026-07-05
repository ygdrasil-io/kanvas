package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/circulararcs.cpp::circular_arc_stroke_matrix`
 * (`DEF_SIMPLE_GM(circular_arc_stroke_matrix, canvas, 820, 1090)`).
 *
 * Draws one-radian stroked arc segments under a battery of 2D transforms
 * (reflections, rotations, near-axis perturbations) for each of three
 * stroke-cap styles (`ROUND`, `BUTT`, `SQUARE`). Each cell shows the arc
 * in red (positive sweep) and blue (complementary sweep `sweep - 360`),
 * both at 50 % alpha so cap overlap shows as magenta.
 */
class CircularArcStrokeMatrixGm : SkiaGm {
    override val name = "circular_arc_stroke_matrix"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 820
    override val height = 1090

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kRadius = 40f
        val kStrokeWidth = 5f
        val kStart = 89f
        val kSweep = 180f / kotlin.math.PI.toFloat()

        val baseMatrices = mutableListOf<Matrix33>()

        val rotateAroundCenter = { deg: Float ->
            Matrix33.translate(kRadius, kRadius) *
                Matrix33.rotate(deg) *
                Matrix33.translate(-kRadius, -kRadius)
        }

        baseMatrices.add(rotateAroundCenter(45f))
        baseMatrices.add(Matrix33.identity())

        baseMatrices.add(
            Matrix33.translate(2f * kRadius, 0f) * Matrix33.scale(-1f, 1f)
        )
        baseMatrices.add(
            Matrix33.translate(0f, 2f * kRadius) * Matrix33.scale(1f, -1f)
        )
        baseMatrices.add(
            Matrix33.translate(0f, 2f * kRadius) * Matrix33.scale(1f, -1f)
        )
        baseMatrices.add(
            Matrix33.translate(2f * kRadius, 2f * kRadius) *
                Matrix33.scale(1f, -1f) * Matrix33.rotate(90f)
        )
        baseMatrices.add(
            Matrix33.translate(2f * kRadius, 0f) * Matrix33.rotate(90f)
        )
        baseMatrices.add(
            Matrix33.rotate(45f) * Matrix33.scale(1f, -1f) * Matrix33.rotate(-45f)
        )
        baseMatrices.add(
            Matrix33.translate(0f, 2f * kRadius) * Matrix33.rotate(-90f)
        )

        val baseMatrixCnt = baseMatrices.size

        val matrices = mutableListOf<Matrix33>()
        matrices.addAll(baseMatrices)

        val tinyCW = rotateAroundCenter(0.001f)
        val tinyCCW = rotateAroundCenter(-0.001f)
        val cw45 = rotateAroundCenter(45f)

        for (i in 0 until baseMatrixCnt) {
            matrices.add(baseMatrices[i] * tinyCW)
        }
        for (i in 0 until baseMatrixCnt) {
            matrices.add(baseMatrices[i] * tinyCCW)
        }
        for (i in 0 until baseMatrixCnt) {
            matrices.add(baseMatrices[i] * cw45)
        }

        val kPad = 2f * kStrokeWidth
        val bounds = Rect(0f, 0f, 2f * kRadius, 2f * kRadius)

        canvas.translate(kPad, kPad)

        var x = 0
        var y = 0

        val red = Color.fromRGBA(1f, 0f, 0f, 0.5f)
        val blue = Color.fromRGBA(0f, 0f, 1f, 0.5f)

        for (cap in listOf(StrokeCap.ROUND, StrokeCap.BUTT, StrokeCap.SQUARE)) {
            for (m in matrices) {
                canvas.save()
                canvas.translate(
                    x * (2f * kRadius + kPad),
                    y * (2f * kRadius + kPad),
                )
                canvas.concat(m)

                val p = Paint(
                    strokeCap = cap,
                    antiAlias = true,
                    style = PaintStyle.STROKE,
                    strokeWidth = kStrokeWidth,
                )
                canvas.drawArc(bounds, kStart, kSweep, false, p.copy(color = red))
                canvas.drawArc(bounds, kStart, kSweep - 360f, false, p.copy(color = blue))

                canvas.restore()

                ++x
                if (x == baseMatrixCnt) {
                    x = 0
                    ++y
                }
            }
        }
    }
}
