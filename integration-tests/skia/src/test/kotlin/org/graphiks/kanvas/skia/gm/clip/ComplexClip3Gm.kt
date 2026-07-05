package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/complexclip3.cpp::ComplexClip3GM`.
 * Simple clip first variant (name = "complexclip3_simple").
 * @see https://github.com/google/skia/blob/main/gm/complexclip3.cpp
 */
class ComplexClip3SimpleGm : SkiaGm {
    override val name = "complexclip3_simple"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 950

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 14f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)

        val clipSimple = Path { }.apply { addCircle(70f, 50f, 20f) }
        val clipComplex = Path {
            moveTo(40f, 50f)
            lineTo(10f, 80f)
            lineTo(10f, 20f)
            close()
        }
        val clipComplexInv = Path {
            moveTo(40f, 50f)
            lineTo(10f, 80f)
            lineTo(10f, 20f)
            close()
        }.apply { fillType = FillType.INVERSE_EVEN_ODD }
        val clipSimpleInv = Path { }.apply { addCircle(70f, 50f, 20f) }
        clipSimpleInv.fillType = FillType.INVERSE_EVEN_ODD

        drawCells(canvas, clipSimple, clipComplex, clipSimpleInv, clipComplexInv)
    }

    private fun drawCells(canvas: GmCanvas, firstBase: Path, secondBase: Path, firstInv: Path, secondInv: Path) {
        val gOps = listOf(
            ClipOp.INTERSECT to "I",
            ClipOp.DIFFERENCE to "D",
        )

        canvas.translate(20f, 20f)
        canvas.scale(3f / 4f, 3f / 4f)

        val pathPaint = Paint(antiAlias = true, color = Color(0xFFFFFF00u))

        for (invA in 0 until 2) {
            for (aaBits in 0 until 4) {
                canvas.save()
                for ((op, opName) in gOps) {
                    for (invB in 0 until 2) {
                        val doAAA = (aaBits and 1) != 0
                        val doAAB = (aaBits and 2) != 0
                        val doInvA = invA != 0
                        val doInvB = invB != 0

                        canvas.save()
                        val first = if (doInvA) firstInv else firstBase
                        val second = if (doInvB) secondInv else secondBase
                        canvas.clipPath(first, antiAlias = doAAA)
                        canvas.clipPath(second, op, doAAB)

                        canvas.drawRect(Rect.fromLTRB(0f, 0f, 100f, 100f), pathPaint)
                        canvas.restore()

                        val str = "${if (doAAA) "A" else "B"}${if (doInvA) "I" else "N"} $opName ${if (doAAB) "A" else "B"}${if (doInvB) "I" else "N"}"
                        canvas.drawString(str, 10f, 130f, font, Paint(color = Color.BLACK))

                        if (doInvB) canvas.translate(150f, 0f) else canvas.translate(120f, 0f)
                    }
                }
                canvas.restore()
                canvas.translate(0f, 150f)
            }
        }
    }
}

/**
 * Port of Skia's `gm/complexclip3.cpp::ComplexClip3ComplexGM`.
 * Complex clip first variant (name = "complexclip3_complex").
 * @see https://github.com/google/skia/blob/main/gm/complexclip3.cpp
 */
class ComplexClip3ComplexGm : SkiaGm {
    override val name = "complexclip3_complex"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 950

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 14f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)

        val clipSimple = Path { }.apply { addCircle(70f, 50f, 20f) }
        val clipComplex = Path {
            moveTo(40f, 50f)
            lineTo(10f, 80f)
            lineTo(10f, 20f)
            close()
        }
        val clipComplexInv = Path {
            moveTo(40f, 50f)
            lineTo(10f, 80f)
            lineTo(10f, 20f)
            close()
        }.apply { fillType = FillType.INVERSE_EVEN_ODD }
        val clipSimpleInv = Path { }.apply { addCircle(70f, 50f, 20f) }
        clipSimpleInv.fillType = FillType.INVERSE_EVEN_ODD

        drawCells(canvas, clipComplex, clipSimple, clipComplexInv, clipSimpleInv)
    }

    private fun drawCells(canvas: GmCanvas, firstBase: Path, secondBase: Path, firstInv: Path, secondInv: Path) {
        val gOps = listOf(
            ClipOp.INTERSECT to "I",
            ClipOp.DIFFERENCE to "D",
        )

        canvas.translate(20f, 20f)
        canvas.scale(3f / 4f, 3f / 4f)

        val pathPaint = Paint(antiAlias = true, color = Color(0xFFFFFF00u))

        for (invA in 0 until 2) {
            for (aaBits in 0 until 4) {
                canvas.save()
                for ((op, opName) in gOps) {
                    for (invB in 0 until 2) {
                        val doAAA = (aaBits and 1) != 0
                        val doAAB = (aaBits and 2) != 0
                        val doInvA = invA != 0
                        val doInvB = invB != 0

                        canvas.save()
                        val first = if (doInvA) firstInv else firstBase
                        val second = if (doInvB) secondInv else secondBase
                        canvas.clipPath(first, antiAlias = doAAA)
                        canvas.clipPath(second, op, doAAB)

                        canvas.drawRect(Rect.fromLTRB(0f, 0f, 100f, 100f), pathPaint)
                        canvas.restore()

                        val str = "${if (doAAA) "A" else "B"}${if (doInvA) "I" else "N"} $opName ${if (doAAB) "A" else "B"}${if (doInvB) "I" else "N"}"
                        canvas.drawString(str, 10f, 130f, font, Paint(color = Color.BLACK))

                        if (doInvB) canvas.translate(150f, 0f) else canvas.translate(120f, 0f)
                    }
                }
                canvas.restore()
                canvas.translate(0f, 150f)
            }
        }
    }
}
