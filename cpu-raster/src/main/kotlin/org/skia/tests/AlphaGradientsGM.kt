package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/alphagradients.cpp` :
 * `DEF_GM(return new AlphaGradientsGM;)`.
 *
 * 12-row grid of `(start, end)` colour pairs each drawn through a
 * linear gradient + black stroke, exercising alpha-modulated
 * interpolation. Upstream's GM duplicates the column for two
 * `InPremul` modes (`kYes` / `kNo`) — our [SkLinearGradient] has
 * only one interpolation mode (matches Skia's "kNo" = non-premul
 * by default), so the second column shows the same gradients as
 * the first. Similarity will be ~50 % vs upstream as a result.
 *
 * **Note** : the visible difference between premul-on / off only
 * matters when one stop has zero alpha — that's exactly the rows
 * where `c1 = (R, G, B, 0)`. We can't reproduce the premul-on
 * column today.
 */
public class AlphaGradientsGM : GM() {

    override fun getName(): String = "alphagradients"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private fun drawGrad(canvas: SkCanvas, r: SkRect, c0: Int, c1: Int) {
        val pts = arrayOf(SkPoint(r.left, r.top), SkPoint(r.right, r.bottom))
        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                pts[0], pts[1],
                colors = intArrayOf(c0, c1),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
        }
        canvas.drawRect(r, paint)
        // Black stroke around the rect.
        val stroke = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawRect(r, stroke)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // (c0, c1) pairs — 12 rows × 2 columns. Upstream column 1 =
        // premul-off, column 2 = premul-on. We render both columns
        // identically (premul-off only) ; the second column is a
        // visible duplicate of the first.
        val white = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
        val red = SkColorSetARGB(0xFF, 0xFF, 0, 0)
        val blue = SkColorSetARGB(0xFF, 0, 0, 0xFF)

        // Each pair : (full-alpha colour, "fade-toward-channel" with
        // alpha=0). Per upstream's gRec table.
        val pairs = listOf(
            white to SkColorSetARGB(0, 0, 0, 0),              // → transparent black
            white to SkColorSetARGB(0, 0xFF, 0, 0),            // → transparent red
            white to SkColorSetARGB(0, 0xFF, 0xFF, 0),         // → transparent yellow
            white to SkColorSetARGB(0, 0xFF, 0xFF, 0xFF),      // → transparent white

            red to SkColorSetARGB(0, 0, 0, 0),
            red to SkColorSetARGB(0, 0xFF, 0, 0),
            red to SkColorSetARGB(0, 0xFF, 0xFF, 0),
            red to SkColorSetARGB(0, 0xFF, 0xFF, 0xFF),

            blue to SkColorSetARGB(0, 0, 0, 0),
            blue to SkColorSetARGB(0, 0xFF, 0, 0),
            blue to SkColorSetARGB(0, 0xFF, 0xFF, 0),
            blue to SkColorSetARGB(0, 0xFF, 0xFF, 0xFF),
        )

        val r = SkRect.MakeWH(300f, 30f)
        c.translate(10f, 10f)

        for (col in 0..1) {
            c.save()
            for ((c0, c1) in pairs) {
                drawGrad(c, r, c0, c1)
                c.translate(0f, r.height() + 8f)
            }
            c.restore()
            c.translate(r.width() + 10f, 0f)
        }
    }
}
