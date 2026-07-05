package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/circularclips.cpp` (800 × 200).
 *
 * Stress test for circular clipPath stacks under both
 * [ClipOp.INTERSECT] and [ClipOp.DIFFERENCE] semantics, plus
 * inverse-fill toggling.
 * @see https://github.com/google/skia/blob/main/gm/circularclips.cpp
 */
class CircularClipsGm : SkiaGm {
    override val name = "circular-clips"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 200

    private val fX1 = 80f
    private val fX2 = 120f
    private val fY = 50f
    private val fR = 40f
    private var fCircle1 = Path { }.apply { addCircle(fX1, fY, fR) }
    private var fCircle2 = Path { }.apply { addCircle(fX2, fY, fR) }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val ops = arrayOf(ClipOp.DIFFERENCE, ClipOp.INTERSECT)
        val rect = Rect.fromLTRB(fX1 - fR, fY - fR, fX2 + fR, fY + fR)
        var fillPaint = Paint(color = Color(0x80808080u))

        canvas.save()
        canvas.scale(10f, 10f)
        canvas.translate(-((fX1 + fX2) / 2 - fR), -(fY - 2 * fR / 3))
        canvas.clipPath(fCircle1, antiAlias = true)
        canvas.clipPath(fCircle2, antiAlias = true)
        canvas.drawRect(rect, fillPaint)
        canvas.restore()

        fillPaint = fillPaint.copy(color = Color.BLACK)

        for (i in 0 until 4) {
            fCircle1 = toggleInverse(fCircle1)
            if (i % 2 == 0) {
                fCircle2 = toggleInverse(fCircle2)
            }

            canvas.save()
            for (op in ops.indices) {
                canvas.save()
                canvas.clipPath(fCircle1)
                canvas.clipPath(fCircle2, ops[op])
                canvas.drawRect(rect, fillPaint)
                canvas.restore()
                canvas.translate(0f, 2 * fY)
            }
            canvas.restore()
            canvas.translate(fX1 + fX2, 0f)
        }
    }

    private fun toggleInverse(path: Path): Path {
        val result = Path { }
        result.addPath(path)
        result.fillType = when (path.fillType) {
            FillType.WINDING -> FillType.INVERSE_WINDING
            FillType.INVERSE_WINDING -> FillType.WINDING
            FillType.EVEN_ODD -> FillType.INVERSE_EVEN_ODD
            FillType.INVERSE_EVEN_ODD -> FillType.EVEN_ODD
        }
        return result
    }
}
