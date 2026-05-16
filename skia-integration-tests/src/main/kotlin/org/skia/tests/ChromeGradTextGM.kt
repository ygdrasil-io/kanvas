package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gradtext.cpp::ChromeGradTextGM1` (500 × 480).
 *
 * Replicates a Chrome layout test : 100 × 100 clipRect + giant red
 * rect filling the clip + giant `"I"` text (`size = 500`, `kAlias`)
 * drawn with a 2-stop solid-green linear gradient shader. Tests
 * gradient-shaded text rasterization in a clipped region.
 */
public class ChromeGradText1GM : GM() {

    override fun getName(): String = "chrome_gradtext1"
    override fun getISize(): SkISize = SkISize.Make(500, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val r = SkRect.MakeWH(100f, 100f)
        c.clipRect(r)

        val paint = SkPaint().apply { color = SK_ColorRED }
        c.drawRect(r, paint)

        paint.shader = makeChromeSolid()
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 500f).apply {
            edging = SkFont.Edging.kAlias
        }
        c.drawString("I", 0f, 100f, font, paint)
    }

    private fun makeChromeSolid(): SkLinearGradient = SkLinearGradient.Make(
        SkPoint.Make(0f, 0f),
        SkPoint.Make(1f, 0f),
        intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
        null,
        SkTileMode.kClamp,
    )
}

/**
 * Port of Skia's `gm/gradtext.cpp::ChromeGradTextGM2` (500 × 480).
 *
 * 4 lines : `Normal Fill Text`, `Normal Stroke Text`, `Gradient Fill
 * Text`, `Gradient Stroke Text`. The first two use solid black ; the
 * last two switch in a green linear-gradient shader. Tests switching
 * between solid and gradient text shaders mid-frame.
 */
public class ChromeGradText2GM : GM() {

    override fun getName(): String = "chrome_gradtext2"
    override fun getISize(): SkISize = SkISize.Make(500, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val font = ToolUtils.DefaultPortableFont().apply { edging = SkFont.Edging.kAlias }

        val paint = SkPaint().apply { style = SkPaint.Style.kFill_Style }
        c.drawString("Normal Fill Text", 0f, 50f, font, paint)
        paint.style = SkPaint.Style.kStroke_Style
        c.drawString("Normal Stroke Text", 0f, 100f, font, paint)

        paint.shader = SkLinearGradient.Make(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(1f, 0f),
            intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
            null,
            SkTileMode.kClamp,
        )

        paint.style = SkPaint.Style.kFill_Style
        c.drawString("Gradient Fill Text", 0f, 150f, font, paint)
        paint.style = SkPaint.Style.kStroke_Style
        c.drawString("Gradient Stroke Text", 0f, 200f, font, paint)
    }
}
