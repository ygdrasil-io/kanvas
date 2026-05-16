package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/typeface.cpp::TypefaceStylesGM`.
 *
 * **Variant covered**: `applyKerning = false` (i.e. the
 * `typefacestyles.png` reference). The `_kerning` variant relies on
 * `SkTypeface::getKerningPairAdjustments()`, which is not part of the
 * `:kanvas-skia` `SkTypeface` surface yet — see report skip note.
 *
 * Renders the string `Hamburgefons` four times stacked vertically
 * (size 30) using the four classical font styles
 * (Normal / Bold / Italic / BoldItalic) resolved from the portable
 * Liberation Sans family via [ToolUtils.CreatePortableTypeface].
 *
 * The C++ GM calls `font.getMetrics(nullptr)` purely to read the
 * recommended line-spacing scalar back. Our [SkFont.getMetrics]
 * signature requires a non-null receiver, so we pass a throw-away
 * [SkFontMetrics] and use the returned scalar directly.
 */
public class TypefaceStylesGM : GM() {

    private val styles: Array<SkFontStyle> = arrayOf(
        SkFontStyle.Normal(),
        SkFontStyle.Bold(),
        SkFontStyle.Italic(),
        SkFontStyle.BoldItalic(),
    )

    private lateinit var faces: Array<SkTypeface>

    override fun onOnceBeforeDraw() {
        faces = Array(styles.size) { ToolUtils.CreatePortableTypeface(null, styles[it]) }
    }

    override fun getName(): String = "typefacestyles"

    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont().apply { size = 30f }
        val text = "Hamburgefons"

        val x = 10f
        val metrics = SkFontMetrics()
        val dy = font.getMetrics(metrics)
        var y = dy

        font.isLinearMetrics = true

        val paint = SkPaint()
        for (i in styles.indices) {
            font.typeface = faces[i]
            c.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, x, y, font, paint)
            y += dy
        }
    }
}
