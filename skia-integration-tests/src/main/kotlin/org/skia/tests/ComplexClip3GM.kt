package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkClipOp
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/complexclip3.cpp::ComplexClip3GM` (400 × 950).
 *
 * For each (`invA`, `aaBits`, `op`, `invB`) combination it draws a yellow
 * 100×100 rect clipped by two paths (a simple circle and a complex arc),
 * one of which is the first clip and the other the second. The first clip
 * is set via `clipPath(path, doAntiAlias)`, the second via
 * `clipPath(path, op, doAntiAlias)` where `op` cycles between Intersect
 * and Difference. Fill type of each clip path is toggled between
 * `kEvenOdd` and `kInverseEvenOdd`. Below each cell a tag like `BI I AN`
 * is drawn describing the parameters.
 *
 * Two GMs are emitted by the C++ side : `complexclip3_simple` (simple
 * clip first) and `complexclip3_complex` (complex clip first) ; see
 * [ComplexClip3ComplexGM] for the latter.
 */
public open class ComplexClip3GM(
    private val doSimpleClipFirst: Boolean = true,
) : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String =
        if (doSimpleClipFirst) "complexclip3_simple" else "complexclip3_complex"

    override fun getISize(): SkISize = SkISize.Make(400, 950)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Simple clip — a circle.
        val clipSimple: SkPath = SkPath.Circle(70f, 50f, 20f)
        // Complex clip — an open arc that closes back to its start.
        val r1 = SkRect.MakeLTRB(10f, 20f, 70f, 80f)
        val clipComplex: SkPath = SkPathBuilder()
            .moveTo(40f, 50f)
            .arcTo(r1, 30f, 300f, false)
            .close()
            .detach()

        val firstBase = if (doSimpleClipFirst) clipSimple else clipComplex
        val secondBase = if (doSimpleClipFirst) clipComplex else clipSimple

        val paint = SkPaint().apply { isAntiAlias = true }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 20f)

        // (op, name)
        val gOps = listOf(
            SkClipOp.kIntersect to "I",
            SkClipOp.kDifference to "D",
        )

        c.translate(20f, 20f)
        c.scale(3f / 4f, 3f / 4f)

        val pathPaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorYELLOW
        }

        for (invA in 0 until 2) {
            for (aaBits in 0 until 4) {
                c.save()
                for ((op, opName) in gOps) {
                    for (invB in 0 until 2) {
                        val doAAA = (aaBits and 1) != 0
                        val doAAB = (aaBits and 2) != 0
                        val doInvA = invA != 0
                        val doInvB = invB != 0

                        c.save()
                        val first = firstBase.makeFillType(
                            if (doInvA) SkPathFillType.kInverseEvenOdd
                            else SkPathFillType.kEvenOdd,
                        )
                        val second = secondBase.makeFillType(
                            if (doInvB) SkPathFillType.kInverseEvenOdd
                            else SkPathFillType.kEvenOdd,
                        )
                        c.clipPath(first, doAAA)
                        c.clipPath(second, op, doAAB)

                        val r = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
                        c.drawRect(r, pathPaint)
                        c.restore()

                        paint.color = SK_ColorBLACK
                        val str = "${if (doAAA) "A" else "B"}${if (doInvA) "I" else "N"} " +
                            "$opName " +
                            "${if (doAAB) "A" else "B"}${if (doInvB) "I" else "N"}"
                        c.drawString(str, 10f, 130f, font, paint)

                        if (doInvB) c.translate(150f, 0f) else c.translate(120f, 0f)
                    }
                }
                c.restore()
                c.translate(0f, 150f)
            }
        }
    }
}

/** Sibling GM — complex clip is applied first. */
public class ComplexClip3ComplexGM : ComplexClip3GM(doSimpleClipFirst = false)
