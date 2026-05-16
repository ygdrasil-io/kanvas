package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkCustomTypefaceBuilder
import org.skia.utils.drawCustomTypefaceText
import kotlin.math.roundToInt

/**
 * Port of Skia's
 * [`gm/userfont.cpp`](https://github.com/google/skia/blob/main/gm/userfont.cpp).
 *
 * Builds a user-defined typeface via [SkCustomTypefaceBuilder] by
 * stealing the first 128 glyphs of [ToolUtils.DefaultPortableTypeface].
 * Every other glyph is stored as a path glyph (rendered through
 * [SkCanvas.drawString] / fill) ; the remaining odd-indexed glyphs are
 * stored as drawable glyphs (a [SkPictureRecorder]-recorded
 * [SkDrawable] that paints the path with `0xff008000` green AA).
 *
 * Two waterfalls are drawn side-by-side : the default typeface (left)
 * and the user-built typeface (right). Each waterfall draws "Typeface"
 * at 9, 11.25, 14.06, 17.58, 21.97, 27.47, 34.33, 42.92, 53.65, 67.06,
 * 83.82pt (size *= 1.25, capped at 100), with a stroked text-bound
 * rectangle behind each line and a shared baseline guideline drawn
 * underneath the default-face waterfall.
 *
 * **Adaptations vs upstream** :
 *  - **No `getUnitsPerEm()`** : `:kanvas-skia`'s [SkTypeface] doesn't
 *    expose units-per-em. Upstream rasterises the source font at upem
 *    px, harvests glyph paths, then stores them with a `1/upem` scale
 *    so the output typeface lives at 1-pt units. We achieve the same
 *    1-pt-unit semantics by rasterising at `size = 1f` directly
 *    (Liberation glyphs are exact rational scalars in design units, so
 *    `size = 1f` produces the path outlines at 1-source-unit per em).
 *    The drift vs upstream is the AWT-vs-FreeType outline precision at
 *    sub-pixel sizes, absorbed by the textual tolerance.
 *  - **No `SkTypeface.serialize` / `MakeDeserialize`** : we skip the
 *    `round_trip(tf)` step. Round-trip serialisation is an upstream-
 *    only optimisation tested separately ; it shouldn't affect pixels.
 *  - **Drawable glyphs route through [drawCustomTypefaceText]** : the
 *    extension dispatches to the user-typeface drawable hook when
 *    drawable glyphs are present, falling back to fill otherwise. The
 *    default-typeface waterfall stays on the legacy [SkCanvas.drawString].
 *
 * Reference : `user_typeface.png` (810 × 452).
 */
public class UserFontGM : GM() {

    override fun getName(): String = "user_typeface"
    override fun getISize(): SkISize = SkISize.Make(810, 452)

    private val customTypeface: SkTypeface by lazy { makeTypeface() }

    /**
     * Mirrors `make_drawable(path)` from upstream. Upstream records
     * a `drawPath` op into an [org.skia.core.SkPictureRecorder] and
     * returns the recording as an [SkDrawable]. `:kanvas-skia` doesn't
     * yet expose `finishRecordingAsDrawable`, so we subclass
     * [SkDrawable] inline and emit the same `drawPath` call from
     * [SkDrawable.onDraw] — the result is the same draw-time effect
     * (a 0xff008000-green AA-filled path) without the picture
     * intermediate.
     */
    private fun makeDrawable(path: SkPath): SkDrawable {
        val bounds = path.computeTightBounds()
        val paint = SkPaint().apply {
            color = 0xff008000.toInt()
            isAntiAlias = true
        }
        return object : SkDrawable() {
            override fun onDraw(canvas: SkCanvas) {
                canvas.drawPath(path, paint)
            }
            override fun onGetBounds(): SkRect = bounds
        }
    }

    /**
     * Mirrors `make_tf()` from upstream — assembles a typeface whose
     * first 128 code-points carry path outlines harvested from the
     * default Liberation typeface. Even-indexed code-points become
     * path glyphs ; odd-indexed become drawable glyphs (so the test
     * exercises both code paths in [SkCustomTypefaceBuilder]).
     */
    private fun makeTypeface(): SkTypeface {
        val builder = SkCustomTypefaceBuilder()
        // Source font at size = 1f — glyph outlines come back in
        // 1-source-unit-per-em coords directly (no upem scale needed).
        val srcFont = SkFont(ToolUtils.DefaultPortableTypeface(), /* size = */ 1f)
        srcFont.hinting = org.skia.foundation.SkFontHinting.kNone

        val metrics = SkFontMetrics()
        srcFont.getMetrics(metrics)
        builder.setMetrics(metrics, /* scale = */ 1f)
        builder.setFontStyle(srcFont.typeface.fontStyle)

        val codepoints = IntArray(1)
        val glyphIds = ShortArray(1)
        for (index in 0..127) {
            codepoints[0] = index
            srcFont.unicharsToGlyphs(codepoints, 1, glyphIds)
            val glyphId = glyphIds[0].toInt() and 0xFFFF
            val width = srcFont.getWidth(glyphId)
            val path = srcFont.getPath(glyphId) ?: SkPathBuilder().detach()
            if (index % 2 == 1) {
                builder.setGlyph(index, width, makeDrawable(path), path.computeTightBounds())
            } else {
                builder.setGlyph(index, width, path)
            }
        }
        return builder.detach()
    }

    /**
     * Mirrors `make_blob(tf, size, &spacing)` — builds a single-run
     * text blob carrying "Typeface" at the requested [size], populates
     * `spacing[0]` with the font's recommended line spacing.
     */
    private fun makeBlob(tf: SkTypeface, size: Float, spacing: FloatArray): SkTextBlob? {
        val font = SkFont(tf, size).apply { edging = SkFont.Edging.kAntiAlias }
        spacing[0] = font.getMetrics(SkFontMetrics())
        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, "Typeface", font, 0f, 0f)
        return builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        waterfall(c, ToolUtils.DefaultPortableTypeface(), defaultFace = true)
        c.translate(400f, 0f)
        waterfall(c, customTypeface, defaultFace = false)
    }

    /**
     * Mirrors the upstream lambda — draws successive sizes of
     * "Typeface" stacked vertically with a stroked bounding rect per
     * line and (for the default face only) a 1-px shared baseline
     * guideline.
     */
    private fun waterfall(canvas: SkCanvas, tf: SkTypeface, defaultFace: Boolean) {
        val paint = SkPaint().apply { isAntiAlias = true }
        val spacing = FloatArray(1)
        var x = 20f
        var y = 16f
        var size = 9f
        while (size <= 100f) {
            val blob = makeBlob(tf, size, spacing) ?: return

            // Shared baseline (default face only).
            if (defaultFace) {
                paint.color = 0xFFDDDDDD.toInt()
                canvas.drawRect(SkRect.MakeLTRB(0f, y, 810f, y + 1f), paint)
            }

            // Stroked bounds rect.
            paint.color = 0xFFCCCCCC.toInt()
            paint.style = SkPaint.Style.kStroke_Style
            canvas.drawRect(blob.bounds().makeOffset(x, y), paint)

            // The text itself.
            paint.style = SkPaint.Style.kFill_Style
            paint.color = SK_ColorBLACK
            // The custom typeface carries drawable glyphs ; we route
            // through the [drawCustomTypefaceText] extension which
            // honours them. For the default face the extension falls
            // through to drawString-equivalent (no drawable glyphs).
            val font = SkFont(tf, size).apply { edging = SkFont.Edging.kAntiAlias }
            canvas.drawCustomTypefaceText("Typeface", x, y, font, paint)

            y += (spacing[0] * 1.25f + 2f).roundToInt().toFloat()
            size *= 1.25f
        }
    }
}
