package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gammatext.cpp::GammaTextGM` (1024 × 480).
 *
 * Lays a vertical black-to-white linear gradient across the canvas
 * then paints a column of "Hamburgefons" runs in 8 distinct colours
 * (white / yellow / magenta / cyan / red / green / blue / black) at
 * 16pt, [SkFont.Edging.kSubpixelAntiAlias] (silently downgraded to
 * `kAntiAlias` — see [SkFont.Edging] doc — but the visual difference
 * stays within the textual tolerance).
 *
 * The gradient bg validates that text alpha composites correctly
 * over a non-trivial background ; each colour column tests that the
 * paint colour is honoured by the glyph fill.
 */
public class GammatextGM : GM() {

    private companion object {
        private const val HEIGHT = 480
        private const val WIDTH = 1024
    }

    override fun getName(): String = "gammatext"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private fun makeHeatGradient(p0: SkPoint, p1: SkPoint): SkShader = SkLinearGradient.Make(
        p0,
        p1,
        // Black → white vertical gradient. SkColors::kBlack/kWhite in upstream.
        intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        null,
        SkTileMode.kClamp,
    )

    private fun drawGrad(canvas: SkCanvas) {
        canvas.clear(SK_ColorRED)
        val paint = SkPaint().apply {
            shader = makeHeatGradient(SkPoint.Make(0f, 0f), SkPoint.Make(0f, HEIGHT.toFloat()))
        }
        canvas.drawRect(SkRect.MakeLTRB(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawGrad(c)

        val fg = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFF000000.toInt(),
        )

        val text = "Hamburgefons"

        val paint = SkPaint()
        val font = ToolUtils.DefaultPortableFont().apply {
            size = 16f
            edging = SkFont.Edging.kSubpixelAntiAlias
        }

        var x = 10f
        for (i in fg.indices) {
            paint.color = fg[i]

            var y = 40f
            val stopy = HEIGHT.toFloat()
            while (y < stopy) {
                c.drawString(text, x, y, font, paint)
                y += font.size * 2f
            }
            x += WIDTH.toFloat() / fg.size.toFloat()
        }
    }
}
