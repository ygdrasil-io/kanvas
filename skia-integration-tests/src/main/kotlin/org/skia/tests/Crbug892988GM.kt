package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/crbug_892988.cpp::crbug_892988` (256 × 256).
 *
 * Reproduces a clipping bug : a 1-px AA stroke at half-pixel boundaries
 * (`(11.5, 0.5, 245.5, 245.5)`) followed by `clipRect(12, 1, 244, 244)`
 * with AA, then a `drawRect` over the same region with paint
 * `(color=0xF0FFFFFF, kSrc, AA)`. The non-opaque kSrc paint forces the
 * blend through the slow path; the AA-bloated stroke must not leak
 * outside the AA-clipped region. The expected result is the stroked
 * frame visible in the corners and the rect interior covered by the
 * 0xF0FFFFFF wash.
 */
public class Crbug892988GM : GM() {

    override fun getName(): String = "crbug_892988"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint1 = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
            isAntiAlias = true
        }
        c.drawRect(SkRect.MakeLTRB(11.5f, 0.5f, 245.5f, 245.5f), paint1)

        c.clipRect(SkRect.MakeLTRB(12f, 1f, 244f, 244f), doAntiAlias = true)
        val paint2 = SkPaint().apply {
            color = 0xF0FFFFFF.toInt()
            blendMode = SkBlendMode.kSrc
            isAntiAlias = true
        }
        c.drawRect(SkRect.MakeLTRB(12f, 1f, 244f, 244f), paint2)
    }
}
