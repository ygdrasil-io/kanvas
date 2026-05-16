package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/complexclip.cpp::ComplexClipGM` (8 GMs registered :
 * `complexclip_(bw|aa)(_layer)?(_invert)?`).
 *
 * Each GM stamps :
 *  - a base path (rect with rounded corners + inner U-shaped hole),
 *  - 2 polygon clips (clipA / clipB),
 *  - operator combinations of `clipPath(clipA)` then
 *    `clipPath(clipB, op)` with `op ∈ {kIntersect, kDifference}` ;
 *  - all four invert / non-invert combos for `clipA` and `clipB` ;
 *  - hairline overlay of `(path, clipA, clipB)` for legibility.
 *
 * Selected by 3 boolean flags : AA-clip, save-layer, invert-draw.
 *
 * Reference images: `complexclip_bw.png`, `complexclip_aa.png`, plus
 * the `_invert`, `_layer`, `_layer_invert` variants — all 388 × 780.
 */
public open class ComplexClipGM(
    private val fDoAAClip: Boolean,
    private val fDoSaveLayer: Boolean,
    private val fInvertDraw: Boolean,
) : GM() {

    init { setBGColor(0xFFDEDFDE.toInt()) }

    override fun getName(): String {
        val sb = StringBuilder("complexclip_")
        sb.append(if (fDoAAClip) "aa" else "bw")
        if (fDoSaveLayer) sb.append("_layer")
        if (fInvertDraw) sb.append("_invert")
        return sb.toString()
    }

    override fun getISize(): SkISize = SkISize.Make(388, 780)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val basePath: SkPath = SkPathBuilder()
            .moveTo(0f, 50f)
            .quadTo(0f, 0f, 50f, 0f)
            .lineTo(175f, 0f)
            .quadTo(200f, 0f, 200f, 25f)
            .lineTo(200f, 150f)
            .quadTo(200f, 200f, 150f, 200f)
            .lineTo(0f, 200f)
            .close()
            .moveTo(50f, 50f)
            .lineTo(150f, 50f)
            .lineTo(150f, 125f)
            .quadTo(150f, 150f, 125f, 150f)
            .lineTo(50f, 150f)
            .close()
            .detach()

        val pathFill = if (fInvertDraw) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd
        val path = basePath.makeFillType(pathFill)

        val pathPaint = SkPaint().apply {
            isAntiAlias = true
            color = gPathColor
        }

        val clipABase: SkPath = SkPath.Polygon(
            arrayOf(
                10f to 20f, 165f to 22f, 70f to 105f, 165f to 177f, -5f to 180f,
            ),
            isClosed = true,
        )
        val clipBBase: SkPath = SkPath.Polygon(
            arrayOf(
                40f to 10f, 190f to 15f, 195f to 190f, 40f to 185f, 155f to 100f,
            ),
            isClosed = true,
        )

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 20f)

        c.translate(20f, 20f)
        c.scale(3f / 4f, 3f / 4f)

        if (fDoSaveLayer) {
            // Want the layer symmetric in device coords — undo the translate+scale.
            var bounds = SkRect.MakeLTRB(
                4f / 3f * -20f,
                4f / 3f * -20f,
                4f / 3f * (getISize().width - 20f),
                4f / 3f * (getISize().height - 20f),
            )
            bounds = bounds.makeInset(100f, 100f)
            val boundPaint = SkPaint().apply {
                color = SK_ColorRED
                style = SkPaint.Style.kStroke_Style
            }
            c.drawRect(bounds, boundPaint)
            c.clipRect(bounds)
            c.saveLayer(bounds, null)
        }

        for (invBits in 0 until 4) {
            c.save()
            for (op in 0 until gOps.size) {
                drawHairlines(c, path, clipABase, clipBBase)

                val doInvA = (invBits and 1) != 0
                val doInvB = (invBits and 2) != 0
                c.save()
                run {
                    val clipA = clipABase.makeFillType(
                        if (doInvA) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd,
                    )
                    val clipB = clipBBase.makeFillType(
                        if (doInvB) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd,
                    )
                    c.clipPath(clipA, fDoAAClip)
                    c.clipPath(clipB, gOps[op].fOp, fDoAAClip)

                    if (fInvertDraw) {
                        var rectClip = clipABase.computeBounds()
                        rectClip = unionRect(rectClip, basePath.computeBounds())
                        rectClip = rectClip.makeOutset(5f, 5f)
                        c.clipRect(rectClip)
                    }
                    c.drawPath(path, pathPaint)
                }
                c.restore()

                val paint = SkPaint()
                var txtX = 45f
                paint.color = gClipAColor
                val aTxt = if (doInvA) "InvA " else "A "
                c.drawString(aTxt, txtX, 220f, font, paint)
                txtX += font.measureText(aTxt)
                paint.color = SK_ColorBLACK
                c.drawString(gOps[op].fName, txtX, 220f, font, paint)
                txtX += font.measureText(gOps[op].fName)
                paint.color = gClipBColor
                val bTxt = if (doInvB) "InvB " else "B "
                c.drawString(bTxt, txtX, 220f, font, paint)

                c.translate(250f, 0f)
            }
            c.restore()
            c.translate(0f, 250f)
        }

        if (fDoSaveLayer) {
            c.restore()
        }
    }

    private fun drawHairlines(
        canvas: SkCanvas,
        path: SkPath,
        clipA: SkPath,
        clipB: SkPath,
    ) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        val fade = 0x33

        paint.color = (gPathColor and 0x00FFFFFF) or (fade shl 24)
        canvas.drawPath(path, paint)

        paint.color = (gClipAColor and 0x00FFFFFF) or (fade shl 24)
        canvas.drawPath(clipA, paint)
        paint.color = (gClipBColor and 0x00FFFFFF) or (fade shl 24)
        canvas.drawPath(clipB, paint)
    }

    private data class OpEntry(val fOp: SkClipOp, val fName: String)

    private companion object {
        private val gPathColor: Int = SK_ColorBLACK
        private val gClipAColor: Int = SK_ColorBLUE
        private val gClipBColor: Int = SK_ColorRED
        private val gOps = arrayOf(
            OpEntry(SkClipOp.kIntersect, "Isect "),
            OpEntry(SkClipOp.kDifference, "Diff "),
        )

        private fun unionRect(a: SkRect, b: SkRect): SkRect = SkRect.MakeLTRB(
            minOf(a.left, b.left),
            minOf(a.top, b.top),
            maxOf(a.right, b.right),
            maxOf(a.bottom, b.bottom),
        )
    }
}

// `complexclip_bw_invert` etc. — concrete subclasses give each
// 8 variants its own no-arg constructor so the test harness can
// instantiate them by class.
public class ComplexClipBwGM : ComplexClipGM(false, false, false)
public class ComplexClipBwInvertGM : ComplexClipGM(false, false, true)
public class ComplexClipBwLayerGM : ComplexClipGM(false, true, false)
public class ComplexClipBwLayerInvertGM : ComplexClipGM(false, true, true)
public class ComplexClipAaGM : ComplexClipGM(true, false, false)
public class ComplexClipAaInvertGM : ComplexClipGM(true, false, true)
public class ComplexClipAaLayerGM : ComplexClipGM(true, true, false)
public class ComplexClipAaLayerInvertGM : ComplexClipGM(true, true, true)
