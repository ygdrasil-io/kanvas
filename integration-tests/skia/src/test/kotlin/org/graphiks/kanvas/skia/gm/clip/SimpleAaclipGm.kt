package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

private val font = Font(
    typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
    size = 24f,
)

/**
 * Port of Skia's `gm/simpleaaclip.cpp::SimpleClipGM`.
 * Tests clipRect and clipPath with Difference and Intersect ops.
 * @see https://github.com/google/skia/blob/main/gm/simpleaaclip.cpp
 */
sealed class SimpleAaclipBaseGm(
    override val name: String,
    private val usePath: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 240

    private val referenceBackground = 0xDD / 255f
    private val fBase = Rect.fromLTRB(100.65f, 100.65f, 150.65f, 150.65f)
    private val fRect = Rect.fromLTRB(130.65f, 130.65f, 170.65f, 170.65f)
    private val fBasePath = Path { }.apply { addRRect(RRect(fBase, 5f)) }
    private val fRectPath = Path { }.apply { addRRect(RRect(fRect, 5f)) }
    private val fBaseRectPath = Path { }.apply { addRect(fBase) }
    private val fRectRectPath = Path { }.apply { addRect(fRect) }

    private fun drawOrig(canvas: GmCanvas) {
        val paint = Paint(color = Color.BLACK, style = PaintStyle.STROKE)
        canvas.drawRect(fBase, paint)
        canvas.drawRect(fRect, paint)
    }

    private fun drawPathsOped(canvas: GmCanvas, op: ClipOp, color: Color) {
        drawOrig(canvas)
        canvas.save()
        if (usePath) {
            canvas.clipPath(fBasePath, ClipOp.INTERSECT, true)
            canvas.clipPath(fRectPath, op, true)
        } else {
            canvas.clipPath(fBaseRectPath, ClipOp.INTERSECT, true)
            canvas.clipPath(fRectRectPath, op, true)
        }
        val paint = Paint(color = color)
        canvas.drawRect(Rect.fromLTRB(90f, 90f, 180f, 180f), paint)
        canvas.restore()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(referenceBackground, referenceBackground, referenceBackground, 1f)

        val gOps = listOf(
            Triple("Difference", ClipOp.DIFFERENCE, Color.BLACK),
            Triple("Intersect", ClipOp.INTERSECT, Color.RED),
        )

        var xOff = 0
        for ((label, op, color) in gOps) {
            canvas.drawString(label, 75f, 50f, font, Paint())
            drawPathsOped(canvas, op, color)

            if (xOff >= 400) {
                canvas.translate(-400f, 250f)
                xOff = 0
            } else {
                canvas.translate(200f, 0f)
                xOff += 200
            }
        }
    }
}

class SimpleAaclipRectGm : SimpleAaclipBaseGm("simpleaaclip_rect", usePath = false)
class SimpleAaclipPathGm : SimpleAaclipBaseGm("simpleaaclip_path", usePath = true)
