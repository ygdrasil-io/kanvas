package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkPaint
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Phase 4 micro-tests: verify that drawing into a Rec.2020 bitmap goes
 * through the colorspace xform we built, and that drawing into the default
 * sRGB bitmap stays a fast-path no-op.
 */
class SkBitmapDeviceColorSpaceTest {

    private val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!

    @Test
    fun `sRGB bitmap stays bit-identical to paint color`() {
        val bm = SkBitmap(2, 2)
        val device = SkBitmapDevice(bm)
        val paint = SkPaint().apply { color = SK_ColorBLUE; style = SkPaint.Style.kFill_Style }
        device.drawRect(SkRect.MakeWH(2f, 2f), SkIRect.MakeWH(2, 2), paint)
        assertEquals(SK_ColorBLUE, bm.getPixel(0, 0))
    }

    @Test
    fun `Rec_2020 bitmap blue paint produces (43, 13, 241) at the pixel`() {
        val bm = SkBitmap(2, 2, rec2020)
        val device = SkBitmapDevice(bm)
        val paint = SkPaint().apply { color = SK_ColorBLUE; style = SkPaint.Style.kFill_Style }
        device.drawRect(SkRect.MakeWH(2f, 2f), SkIRect.MakeWH(2, 2), paint)
        val px = bm.getPixel(0, 0)
        assertEquals(43, SkColorGetR(px), "R must match bigrect.png")
        assertEquals(13, SkColorGetG(px), "G must match bigrect.png")
        val b = SkColorGetB(px)
        assert(b in 240..242) { "B must be ~241; got $b" }
    }

    @Test
    fun `Rec_2020 bitmap red paint produces (202, 59, 19)`() {
        val bm = SkBitmap(2, 2, rec2020)
        val device = SkBitmapDevice(bm)
        val paint = SkPaint().apply { color = SK_ColorRED; style = SkPaint.Style.kFill_Style }
        device.drawRect(SkRect.MakeWH(2f, 2f), SkIRect.MakeWH(2, 2), paint)
        val px = bm.getPixel(0, 0)
        assertEquals(202, SkColorGetR(px))
        assertEquals(59, SkColorGetG(px))
        val b = SkColorGetB(px)
        assert(b in 18..20) { "B = $b" }
    }

    @Test
    fun `Default bitmap colorspace is sRGB`() {
        val bm = SkBitmap(1, 1)
        assertEquals(SkColorSpace.makeSRGB(), bm.colorSpace)
    }
}
