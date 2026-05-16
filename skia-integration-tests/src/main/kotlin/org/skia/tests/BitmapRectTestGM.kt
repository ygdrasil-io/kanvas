package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bitmaprecttest.cpp::bitmaprecttest` (320 × 240).
 *
 * Reproduces a precision bug where `drawImageRect` with a non-trivial
 * dst-rect under a CTM scale would take a fast-path that drew the
 * rightmost column of source pixels twice. The fix forced the slow
 * path; this GM verifies that fix.
 *
 * Source bitmap : 60 × 60 transparent N32, with a filled triangle
 * `(6,6)-(6,54)-(30,54)` and a 1-px-stroked `(0.5, 0.5, 59.5, 59.5)`
 * outline.
 *
 * Three draws :
 *  1. `drawImage(image, 150, 45)` — 1:1 placement.
 *  2. Under `scale(0.472560018)` : `drawImageRect(src=image, dst=(100,
 *     100, 228, 228))` — the bug-trigger.
 *  3. After `scale(-1, 1)` : `drawImage(image, -310, 45)` — flipped
 *     copy on the left.
 */
public class BitmapRectTestGM : GM() {

    override fun getName(): String = "bitmaprecttest"
    override fun getISize(): SkISize = SkISize.Make(320, 240)

    private fun makeBm(): SkImage {
        val bm = SkBitmap(60, 60).also { it.eraseColor(0) }
        val canv = org.skia.core.SkCanvas(bm)
        val paint = SkPaint()
        canv.drawPath(
            SkPath.Polygon(arrayOf(6f to 6f, 6f to 54f, 30f to 54f), isClosed = false),
            paint,
        )

        paint.style = SkPaint.Style.kStroke_Style
        canv.drawRect(SkRect.MakeLTRB(0.5f, 0.5f, 59.5f, 59.5f), paint)
        return bm.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = makeBm()

        c.drawImage(image, 150f, 45f)

        val scale = 0.472560018f
        c.save()
        c.scale(scale, scale)
        c.drawImageRect(
            image,
            SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()),
            SkRect.MakeXYWH(100f, 100f, 128f, 128f),
            SkSamplingOptions.Default,
            paint = null,
        )
        c.restore()

        c.scale(-1f, 1f)
        c.drawImage(image, -310f, 45f)
    }
}
