package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/complexclip.cpp::ComplexClipGM`.
 *
 * Each variant stamps a base path (rect with rounded corners + inner
 * U-shaped hole), two polygon clips (clipA / clipB), operator combinations
 * of [ClipOp.INTERSECT] and [ClipOp.DIFFERENCE], and all four invert /
 * non-invert combos for clipA and clipB. 8 variants selected by AA-clip,
 * save-layer, and invert-draw flags.
 * @see https://github.com/google/skia/blob/main/gm/complexclip.cpp
 */
abstract class ComplexClipGm(
    override val name: String,
    private val doAAClip: Boolean,
    private val doSaveLayer: Boolean,
    private val invertDraw: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 388
    override val height = 780

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0xDE / 255f, 0xDF / 255f, 0xDE / 255f)

        val basePath = Path {
            moveTo(0f, 50f)
            quadTo(0f, 0f, 50f, 0f)
            lineTo(175f, 0f)
            quadTo(200f, 0f, 200f, 25f)
            lineTo(200f, 150f)
            quadTo(200f, 200f, 150f, 200f)
            lineTo(0f, 200f)
            close()
            moveTo(50f, 50f)
            lineTo(150f, 50f)
            lineTo(150f, 125f)
            quadTo(150f, 150f, 125f, 150f)
            lineTo(50f, 150f)
            close()
        }

        val pathFill = if (invertDraw) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
        val path = Path { }.apply {
            addPath(basePath)
            fillType = pathFill
        }

        val pathPaint = Paint(color = PATH_COLOR)

        val clipABase = Path {
            moveTo(10f, 20f)
            lineTo(165f, 22f)
            lineTo(70f, 105f)
            lineTo(165f, 177f)
            lineTo(-5f, 180f)
            close()
        }

        val clipBBase = Path {
            moveTo(40f, 10f)
            lineTo(190f, 15f)
            lineTo(195f, 190f)
            lineTo(40f, 185f)
            lineTo(155f, 100f)
            close()
        }

        val font = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = 20f,
        )

        canvas.translate(20f, 20f)
        canvas.scale(3f / 4f, 3f / 4f)

        if (doSaveLayer) {
            val bounds = Rect.fromLTRB(
                4f / 3f * -20f,
                4f / 3f * -20f,
                4f / 3f * (388f - 20f),
                4f / 3f * (780f - 20f),
            ).let { Rect.fromLTRB(it.left + 100f, it.top + 100f, it.right - 100f, it.bottom - 100f) }

            canvas.drawRect(bounds, Paint(color = Color.RED, style = PaintStyle.STROKE))
            canvas.clipRect(bounds)
            canvas.saveLayer(bounds, null)
        }

        for (invBits in 0 until 4) {
            canvas.save()
            for (opIdx in 0 until 2) {
                drawHairlines(canvas, path, clipABase, clipBBase)

                val doInvA = (invBits and 1) != 0
                val doInvB = (invBits and 2) != 0
                canvas.save()
                run {
                    val clipA = Path { }.apply {
                        addPath(clipABase)
                        fillType = if (doInvA) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
                    }
                    val clipB = Path { }.apply {
                        addPath(clipBBase)
                        fillType = if (doInvB) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
                    }
                    val op = if (opIdx == 0) ClipOp.INTERSECT else ClipOp.DIFFERENCE

                    canvas.clipPath(clipA, ClipOp.INTERSECT, doAAClip)
                    canvas.clipPath(clipB, op, doAAClip)

                    if (invertDraw) {
                        val clipBounds = Rect.fromLTRB(-10f, -5f, 205f, 205f)
                        canvas.clipRect(clipBounds)
                    }
                    canvas.drawPath(path, pathPaint)
                }
                canvas.restore()

                var txtX = 45f
                val aTxt = if (doInvA) "InvA " else "A "
                canvas.drawString(aTxt, txtX, 220f, font, Paint(color = CLIP_A_COLOR))
                txtX += font.measureText(aTxt)
                val opName = if (opIdx == 0) "Isect " else "Diff "
                canvas.drawString(opName, txtX, 220f, font, Paint(color = Color.BLACK))
                txtX += font.measureText(opName)
                val bTxt = if (doInvB) "InvB " else "B "
                canvas.drawString(bTxt, txtX, 220f, font, Paint(color = CLIP_B_COLOR))

                canvas.translate(250f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 250f)
        }

        if (doSaveLayer) {
            canvas.restore()
        }
    }

    private fun drawHairlines(canvas: GmCanvas, path: Path, clipA: Path, clipB: Path) {
        val paint = Paint(style = PaintStyle.STROKE, strokeWidth = 0f)
        val fade = 0x33
        val alphaFade = fade.toFloat() / 255f

        canvas.drawPath(path, paint.copy(color = Color.fromRGBA(
            ((PATH_COLOR.packed shr 16) and 0xFFu).toFloat() / 255f,
            ((PATH_COLOR.packed shr 8) and 0xFFu).toFloat() / 255f,
            (PATH_COLOR.packed and 0xFFu).toFloat() / 255f,
            alphaFade,
        )))

        canvas.drawPath(clipA, paint.copy(color = Color.fromRGBA(
            ((CLIP_A_COLOR.packed shr 16) and 0xFFu).toFloat() / 255f,
            ((CLIP_A_COLOR.packed shr 8) and 0xFFu).toFloat() / 255f,
            (CLIP_A_COLOR.packed and 0xFFu).toFloat() / 255f,
            alphaFade,
        )))

        canvas.drawPath(clipB, paint.copy(color = Color.fromRGBA(
            ((CLIP_B_COLOR.packed shr 16) and 0xFFu).toFloat() / 255f,
            ((CLIP_B_COLOR.packed shr 8) and 0xFFu).toFloat() / 255f,
            (CLIP_B_COLOR.packed and 0xFFu).toFloat() / 255f,
            alphaFade,
        )))
    }

    private companion object {
        private val PATH_COLOR = Color.BLACK
        private val CLIP_A_COLOR = Color(0xFF0000FFu)
        private val CLIP_B_COLOR = Color(0xFFFF0000u)
    }
}

class ComplexClipBwGm : ComplexClipGm("complexclip_bw", false, false, false)
class ComplexClipBwInvertGm : ComplexClipGm("complexclip_bw_invert", false, false, true)
class ComplexClipBwLayerGm : ComplexClipGm("complexclip_bw_layer", false, true, false)
class ComplexClipBwLayerInvertGm : ComplexClipGm("complexclip_bw_layer_invert", false, true, true)
class ComplexClipAaGm : ComplexClipGm("complexclip_aa", true, false, false)
class ComplexClipAaInvertGm : ComplexClipGm("complexclip_aa_invert", true, false, true)
class ComplexClipAaLayerGm : ComplexClipGm("complexclip_aa_layer", true, true, false)
class ComplexClipAaLayerInvertGm : ComplexClipGm("complexclip_aa_layer_invert", true, true, true)
