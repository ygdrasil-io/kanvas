package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gradtext.cpp::gradtext` (DEF_SIMPLE_GM,
 * 500 × 480).
 *
 * "When in the course of human events" rendered 18 times :
 * 2 (paints) × 3 (font edging) × 2 (top half / bottom half — top half
 * has a black BG drawRect to expose the transparent-stop gradient,
 * bottom has a white BG so the opaque-stop gradient shows the same).
 *
 * `paint[0]` is a 3-stop linear gradient (red, transparent green, blue)
 * with `kMirror` tile mode at 80-px period — exercises gradient
 * compositing on text. `paint[1]` is a 3-stop opaque (red, green,
 * blue) variant.
 */
public class GradTextGM : GM() {

    override fun getName(): String = "gradtext"
    override fun getISize(): SkISize = SkISize.Make(500, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val textSize = 26f
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), textSize)

        c.drawRect(SkRect.MakeLTRB(0f, 0f, 500f, 240f), SkPaint())
        c.translate(20f, textSize)

        val paints = arrayOf(
            SkPaint().apply { shader = makeGrad(80f) },
            SkPaint().apply { shader = makeGrad2(80f) },
        )
        val edgings = arrayOf(
            SkFont.Edging.kAlias,
            SkFont.Edging.kAntiAlias,
            SkFont.Edging.kSubpixelAntiAlias,
        )
        for (i in 0..1) {
            for (paint in paints) {
                for (edging in edgings) {
                    font.edging = edging
                    c.drawString("When in the course of human events", 0f, 0f, font, paint)
                    c.translate(0f, textSize * 4f / 3f)
                }
                c.translate(0f, textSize * 2f / 3f)
            }
        }
    }

    private fun makeGrad(width: Float): SkShader = SkLinearGradient.Make(
        SkPoint.Make(0f, 0f),
        SkPoint.Make(width, 0f),
        // Red — transparent green (alpha 0) — blue.
        intArrayOf(SK_ColorRED, SkColorSetARGB(0, 0, 255, 0), SK_ColorBLUE),
        null,
        SkTileMode.kMirror,
    )

    private fun makeGrad2(width: Float): SkShader = SkLinearGradient.Make(
        SkPoint.Make(0f, 0f),
        SkPoint.Make(width, 0f),
        intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE),
        null,
        SkTileMode.kMirror,
    )
}
