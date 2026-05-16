package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkClipOp
import org.graphiks.math.SkColorSetRGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/complexclip2.cpp::ComplexClip2GM`.
 *
 * 5 × 5 grid of clip-stack experiments. Each cell stacks five clips
 * (rect / rrect / path variant, with random `kDifference` /
 * `kIntersect` op per slot), then fills with light green to reveal
 * the resulting region. The five clip shapes are pre-positioned with
 * `0.65` sub-pixel offsets so AA matters, and the same 5×5 random op
 * grid is reused across all 6 instantiated variants.
 *
 * 6 DEF_GM entries : (rect|rrect|path) × (bw|aa). The reference PNGs
 * `complexclip2(_<clip>_<aa>)?.png` carry the canonical seed-based
 * random op grid that this port reproduces via [SkRandom] seeded at 0.
 */
public class ComplexClip2GM(
    private val clip: Clip,
    private val antiAlias: Boolean,
) : GM() {

    public enum class Clip { kRect_Clip, kRRect_Clip, kPath_Clip }

    private val rects = Array(5) { SkRect.MakeLTRB(0f, 0f, 0f, 0f) }
    private val rrects = Array(5) { SkRRect.MakeEmpty() }
    private val paths = arrayOfNulls<SkPath>(5)
    private val rectColors = IntArray(5)
    private val ops = Array(kRows * kCols) { Array(5) { SkClipOp.kIntersect } }

    private val width: Float
    private val height: Float
    private val totalWidth: Float
    private val totalHeight: Float

    init {
        setBGColor(SkColorSetRGB(0xDD, 0xA0, 0xDD))
        val xA = 0.65f; val xF = 50.65f
        val yA = 0.65f; val yF = 50.65f
        width = xF - xA
        height = yF - yA
        totalWidth = kCols * width + (kCols + 1) * kPadX
        totalHeight = kRows * height + (kRows + 1) * kPadY
    }

    override fun getName(): String {
        if (clip == Clip.kRect_Clip && !antiAlias) return "complexclip2"
        val clipStr = when (clip) {
            Clip.kRect_Clip -> "rect"
            Clip.kRRect_Clip -> "rrect"
            Clip.kPath_Clip -> "path"
        }
        return "complexclip2_${clipStr}_${if (antiAlias) "aa" else "bw"}"
    }

    override fun getISize(): SkISize =
        SkISize.Make(kotlin.math.floor(totalWidth + 0.5f).toInt(),
            kotlin.math.floor(totalHeight + 0.5f).toInt())

    override fun onOnceBeforeDraw() {
        val xA = 0.65f; val xB = 10.65f; val xC = 20.65f
        val xD = 30.65f; val xE = 40.65f; val xF = 50.65f
        val yA = 0.65f; val yB = 10.65f; val yC = 20.65f
        val yD = 30.65f; val yE = 40.65f; val yF = 50.65f

        rects[0].setLTRB(xB, yB, xE, yE)
        rrects[0] = SkRRect.MakeRectXY(rects[0], 7f, 7f)
        paths[0] = SkPath.RRect(SkRRect.MakeRectXY(rects[0], 5f, 5f))
        rectColors[0] = SK_ColorRED

        rects[1].setLTRB(xA, yA, xD, yD)
        rrects[1] = SkRRect.MakeRectXY(rects[1], 7f, 7f)
        paths[1] = SkPath.RRect(SkRRect.MakeRectXY(rects[1], 5f, 5f))
        rectColors[1] = SK_ColorGREEN

        rects[2].setLTRB(xC, yA, xF, yD)
        rrects[2] = SkRRect.MakeRectXY(rects[2], 7f, 7f)
        paths[2] = SkPath.RRect(SkRRect.MakeRectXY(rects[2], 5f, 5f))
        rectColors[2] = SK_ColorBLUE

        rects[3].setLTRB(xA, yC, xD, yF)
        rrects[3] = SkRRect.MakeRectXY(rects[3], 7f, 7f)
        paths[3] = SkPath.RRect(SkRRect.MakeRectXY(rects[3], 5f, 5f))
        rectColors[3] = SK_ColorYELLOW

        rects[4].setLTRB(xC, yC, xF, yF)
        rrects[4] = SkRRect.MakeRectXY(rects[4], 7f, 7f)
        paths[4] = SkPath.RRect(SkRRect.MakeRectXY(rects[4], 5f, 5f))
        rectColors[4] = SK_ColorCYAN

        val opChoices = arrayOf(SkClipOp.kDifference, SkClipOp.kIntersect)
        val r = SkRandom()
        for (i in 0 until kRows) {
            for (j in 0 until kCols) {
                for (k in 0 until 5) {
                    val idx = ((r.nextU().toLong() and 0xFFFFFFFFL) % opChoices.size).toInt()
                    ops[j * kRows + i][k] = opChoices[idx]
                }
            }
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val rectPaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            // Upstream uses `setStrokeWidth(-1)` which Skia rounds up to
            // 0 (hairline). Match that here.
            strokeWidth = 0f
        }
        val fillPaint = SkPaint().apply {
            color = SkColorSetRGB(0xA0, 0xDD, 0xA0)
        }

        for (i in 0 until kRows) {
            for (j in 0 until kCols) {
                c.save()
                c.translate(
                    kPadX + (width + kPadX) * j,
                    kPadY + (height + kPadY) * i,
                )

                // Outline pass : draw the clip shapes as strokes so we
                // can see the AA on the clipped region.
                for (k in 0 until 5) {
                    rectPaint.color = rectColors[k]
                    when (clip) {
                        Clip.kRect_Clip -> c.drawRect(rects[k], rectPaint)
                        Clip.kRRect_Clip -> c.drawRRect(rrects[k], rectPaint)
                        Clip.kPath_Clip -> c.drawPath(paths[k]!!, rectPaint)
                    }
                }

                // Clip stack.
                for (k in 0 until 5) {
                    val op = ops[j * kRows + i][k]
                    when (clip) {
                        Clip.kRect_Clip -> c.clipRect(rects[k], op, antiAlias)
                        Clip.kRRect_Clip -> c.clipRRect(rrects[k], op, antiAlias)
                        Clip.kPath_Clip -> c.clipPath(paths[k]!!, op, antiAlias)
                    }
                }
                c.drawRect(SkRect.MakeWH(width, height), fillPaint)
                c.restore()
            }
        }
    }

    public companion object {
        private const val kRows = 5
        private const val kCols = 5
        private const val kPadX = 20f
        private const val kPadY = 20f
    }
}
