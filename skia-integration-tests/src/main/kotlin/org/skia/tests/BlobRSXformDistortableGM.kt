package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkTextBlob
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.sin

/**
 * Port of `gm/drawatlas.cpp::blob_rsxform_distortable`
 * (DEF_SIMPLE_GM, 500 × 100).
 *
 * Like [BlobRSXformGM] but uses the `fonts/Distortable.ttf` variable font
 * with `wght=1.618…` when available, falling back to [ToolUtils.DefaultPortableTypeface].
 *
 * Draws `"abcabcabc"` with the same sinusoidal per-glyph scale transform.
 *
 * **Note on `SkFontMgr.makeFromStream(stream, SkFontArguments)`**: upstream
 * loads the variable font via `fm->makeFromStream(distortable, params)`.
 * The Kotlin [org.skia.foundation.SkFontMgr] does not yet expose an overload
 * that accepts [org.skia.foundation.SkFontArguments], so this port falls
 * back to [ToolUtils.DefaultPortableTypeface] unconditionally (same visual
 * path as when the resource is missing.
 */
public class BlobRSXformDistortableGM : GM() {

    override fun getName(): String = "blob_rsxform_distortable"
    override fun getISize(): SkISize = SkISize.Make(500, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Try to load Distortable.ttf; fall back to portable typeface.
        // Upstream applies SkFontArguments with wght=1.618 — that
        // variation API is not yet bridged (makeFromStream has no
        // SkFontArguments overload), so we always take the fallback branch.
        val typeface = ToolUtils.CreateTypefaceFromResource("fonts/Distortable.ttf")
            ?: ToolUtils.DefaultPortableTypeface()

        val font = SkFont(typeface, 50f)

        val text = "abcabcabc"
        val len = text.length

        val xforms = buildXforms(len)

        val blob = SkTextBlob.MakeFromRSXform(text, xforms, font)

        val offset = org.graphiks.math.SkPoint(20f, 70f)

        val bgPaint = SkPaint().apply { color = 0xFFCCCCCC.toInt() }
        c.drawRect(blob.bounds().makeOffset(offset.fX, offset.fY), bgPaint)

        val fgPaint = SkPaint().apply { color = 0xFF000000.toInt() }
        c.drawTextBlob(blob, offset.fX, offset.fY, fgPaint)
    }

    /** Sinusoidal per-glyph xform — mirrors the C++ helper loop. */
    private fun buildXforms(len: Int): Array<SkRSXform> {
        var x = 0f
        return Array(len) { i ->
            val scale = (sin(i * PI / (len - 1).toDouble()) * 0.75 + 0.5).toFloat()
            val xform = SkRSXform.Make(scale, 0f, x, 0f)
            x += 50f * scale
            xform
        }
    }
}
