package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/inverseclip.cpp::inverseclip` (400 × 400).
 *
 * Reproduces skbug.com/40040760 — clipPath with `kInverseWinding` fill,
 * then `drawRect` covering the whole canvas. The expected output is
 * blue everywhere **except** inside the cubic-approximated circle. The
 * cubic uses 4 cubicTo curves to draw a roughly elliptical shape
 * centred at (195, 197) with x-radius ~177, y-radius ~166.
 */
public class InverseClipGM : GM() {

    override fun getName(): String = "inverseclip"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val clip = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseWinding)
            .moveTo(195.448f, 31f)
            .cubicTo(97.9925f, 31f, 18.99f, 105.23f, 18.99f, 196.797f)
            .cubicTo(18.99f, 288.365f, 97.9925f, 362.595f, 195.448f, 362.595f)
            .cubicTo(292.905f, 362.595f, 371.905f, 288.365f, 371.905f, 196.797f)
            .cubicTo(371.905f, 105.23f, 292.905f, 31f, 195.448f, 31f)
            .close()
            .detach()
        c.clipPath(clip, doAntiAlias = true)

        c.drawRect(SkRect.MakeWH(400f, 400f), SkPaint().apply { color = SK_ColorBLUE })
    }
}
