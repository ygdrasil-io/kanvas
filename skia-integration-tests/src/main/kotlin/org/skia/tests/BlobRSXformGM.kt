package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkTextBlob
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.sin

/**
 * Port of `gm/drawatlas.cpp::blob_rsxform` (DEF_SIMPLE_GM, 500 × 100).
 *
 * Draws the string `"CrazyXform"` with per-glyph [SkRSXform] transforms
 * where the scale of each glyph oscillates sinusoidally, giving a
 * wave-height effect. The bounding-box rect is filled in grey before
 * drawing the black text.
 *
 * **STUB.RSXBLOB** — [SkTextBlob.MakeFromRSXform] is not yet implemented
 * end-to-end. The GM body calls it; the test is
 * `@Disabled("STUB.RSXBLOB")`.
 */
public class BlobRSXformGM : GM() {

    override fun getName(): String = "blob_rsxform"
    override fun getISize(): SkISize = SkISize.Make(500, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont(50f)

        val text = "CrazyXform"
        val len = text.length

        // Build xforms iteratively (mirrors the C++ accumulating-x loop).
        var xAccum = 0f
        val xforms = Array(len) { i ->
            val scale = (sin(i * PI / (len - 1).toDouble()) * 0.75 + 0.5).toFloat()
            val xform = SkRSXform.Make(scale, 0f, xAccum, 0f)
            xAccum += 50f * scale
            xform
        }

        // STUB.RSXBLOB — will throw NotImplementedError at runtime.
        val blob = SkTextBlob.MakeFromRSXform(text, xforms, font)

        val offset = org.graphiks.math.SkPoint(20f, 70f)

        val bgPaint = SkPaint().apply { color = 0xFFCCCCCC.toInt() }
        c.drawRect(blob.bounds().makeOffset(offset.fX, offset.fY), bgPaint)

        val fgPaint = SkPaint().apply { color = 0xFF000000.toInt() }
        c.drawTextBlob(blob, offset.fX, offset.fY, fgPaint)
    }
}
