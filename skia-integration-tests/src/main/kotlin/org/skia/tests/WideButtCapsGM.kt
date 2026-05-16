package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/widebuttcaps.cpp::widebuttcaps` (DEF_SIMPLE_GM,
 * 480 × 500).
 *
 * Stress test for wide stroke (`strokeWidth = 100`) with bevel / round /
 * miter joins and degenerate / near-degenerate paths. Four "row" cases
 * cover : pure-line corners, vertical-line back-and-forth (zero-extent
 * caps), 4-vertex L-shape, and a 4-vertex closed quad. Each row redraws
 * the same path 4 times — bevel join, round join, miter join (default
 * limit), and the equivalent cubic-only path under the miter join — to
 * make sure the cubic flattener emits the same outline as the line
 * sequence.
 *
 * Colours come from `SkRandom::nextU() | 0xFF808080` so they reproduce
 * upstream's RNG sequence exactly (each call OR's the alpha to 0xFF and
 * forces every RGB high-bit to ensure visibility on the black BG).
 */
public class WideButtCapsGM : GM() {

    override fun getName(): String = "widebuttcaps"
    override fun getISize(): SkISize = SkISize.Make(kTestWidth, kTestHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        drawTest(c)
    }

    private fun drawTest(canvas: SkCanvas) {
        val rand = SkRandom()
        canvas.clear(SK_ColorBLACK)

        // Iso with upstream `SkAutoCanvasRestore arc(canvas, true);`.
        canvas.withSave {
            translate(60f, 60f)

            drawStrokes(
                this, rand,
                SkPathBuilder().lineTo(10f, 0f).lineTo(10f, 10f).detach(),
                SkPathBuilder().cubicTo(10f, 0f, 10f, 0f, 10f, 10f).detach(),
            )
            translate(0f, 120f)

            drawStrokes(
                this, rand,
                SkPathBuilder().lineTo(0f, -10f).lineTo(0f, 10f).detach(),
                SkPathBuilder().cubicTo(0f, -10f, 0f, -10f, 0f, 10f).detach(),
            )
            translate(0f, 120f)

            drawStrokes(
                this, rand,
                SkPathBuilder().lineTo(0f, -10f).lineTo(10f, -10f).lineTo(10f, 10f).lineTo(0f, 10f).detach(),
                SkPathBuilder().cubicTo(0f, -10f, 10f, 10f, 0f, 10f).detach(),
            )
            translate(0f, 140f)

            drawStrokes(
                this, rand,
                SkPathBuilder().lineTo(0f, -10f).lineTo(10f, -10f).lineTo(10f, 0f).lineTo(0f, 0f).detach(),
                SkPathBuilder().cubicTo(0f, -10f, 10f, 0f, 0f, 0f).detach(),
            )
            translate(0f, 120f)
        }
    }

    private fun drawStrokes(canvas: SkCanvas, rand: SkRandom, path: SkPath, cubic: SkPath) {
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = kStrokeWidth
            style = SkPaint.Style.kStroke_Style
        }

        // Iso with upstream `SkAutoCanvasRestore arc(canvas, true);`.
        canvas.withSave {
            strokePaint.strokeJoin = SkPaint.Join.kBevel_Join
            strokePaint.color = rand.nextU() or 0xFF808080.toInt()
            drawPath(path, strokePaint)

            translate(120f, 0f)
            strokePaint.strokeJoin = SkPaint.Join.kRound_Join
            strokePaint.color = rand.nextU() or 0xFF808080.toInt()
            drawPath(path, strokePaint)

            translate(120f, 0f)
            strokePaint.strokeJoin = SkPaint.Join.kMiter_Join
            strokePaint.color = rand.nextU() or 0xFF808080.toInt()
            drawPath(path, strokePaint)

            translate(120f, 0f)
            strokePaint.color = rand.nextU() or 0xFF808080.toInt()
            drawPath(cubic, strokePaint)
        }
    }

    private companion object {
        const val kStrokeWidth: Float = 100f
        const val kTestWidth: Int = 120 * 4
        const val kTestHeight: Int = 120 * 3 + 140
    }
}
