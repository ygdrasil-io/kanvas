package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/strokerect.cpp::strokerect_anisotropic_5408`
 * (200 × 50).
 *
 * Stroke-6 rect `(5, 20, 15, 30)` drawn under `scale(10, 1)` — a 10:1
 * non-uniform CTM. Reproduces crbug.com/skia/5408 : the rect-stroker's
 * specialised path was not handling non-square stroke widths correctly.
 * Expected output is a horizontally-stretched stroke frame.
 */
public class StrokerectAnisotropic5408GM : GM() {

    override fun getName(): String = "strokerect_anisotropic_5408"
    override fun getISize(): SkISize = SkISize.Make(200, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 6f
        }
        c.scale(10f, 1f)
        c.drawRect(SkRect.MakeXYWH(5f, 20f, 10f, 10f), p)
    }
}
