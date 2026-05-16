package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/pdf_never_embed.cpp::pdf_never_embed`
 * (`DEF_SIMPLE_GM_CAN_FAIL`, 512 × 512).
 *
 * Originally an end-to-end PDF subsetter exercise: it loads
 * `fonts/Roboto2-Regular_NoEmbed.ttf` (which carries the OS/2
 * "Restricted License Embedding" flag), then renders four sizes /
 * rotations of `"HELLO, WORLD!"` to verify that Skia's PDF backend
 * substitutes the font correctly. On the raster GM pipeline the test
 * collapses to a "draw the same string four ways" visual check.
 *
 * **Resource fallback** — the `Roboto2-Regular_NoEmbed.ttf` blob isn't
 * bundled in `:kanvas-skia`'s test resources; we go through
 * [ToolUtils.DefaultPortableTypeface] (Liberation Sans) directly, the
 * same fallback path upstream takes when the resource is missing
 * (`if (!tf) tf = ToolUtils::DefaultPortableTypeface();`).
 *
 * **Glyph-run substitution** — upstream uses
 * `SkTextBlobBuilder::allocRunPos` to position the glyphs manually
 * via `SkFont::textToGlyphs` + `SkFont::getPos`. Our [SkFont] doesn't
 * expose those measurement helpers yet; we fall through to
 * [SkCanvas.drawString], which threads the same underlying glyph-fill
 * pipeline (per-glyph advances come from the typeface). Visual output
 * is identical for the rotated / scaled cells — the advance widths
 * are the same.
 *
 * Four passes:
 *  1. Black `"HELLO, WORLD!"` at `(30, 90)`.
 *  2. Dark-red `0xF0800000` rotated 45° at `(30, 45)` in rotated frame.
 *  3. Dark-green `0xF0008000` y-scaled 4× at `(15, 70)`.
 *  4. Dark-blue `0xF0000080` y-scaled 0.5× at `(30, 700)`.
 */
public class PdfNeverEmbedGM : GM() {

    override fun getName(): String = "pdf_never_embed"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint()
        val tf = ToolUtils.DefaultPortableTypeface()
        val font = SkFont(tf, 60f)

        val text = "HELLO, WORLD!"

        c.drawColor(SK_ColorWHITE)
        c.drawString(text, 30f, 90f, font, p)

        c.save()
        c.rotate(45f)
        p.color = 0xF0800000.toInt()
        c.drawString(text, 30f, 45f, font, p)
        c.restore()

        c.save()
        c.scale(1f, 4f)
        p.color = 0xF0008000.toInt()
        c.drawString(text, 15f, 70f, font, p)
        c.restore()

        c.scale(1f, 0.5f)
        p.color = 0xF0000080.toInt()
        c.drawString(text, 30f, 700f, font, p)
    }
}
