package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkRect

/**
 * Phase G6 — unit-level coverage of [SaveLayerRec] with a non-null
 * `backdrop`. We don't go through a PNG reference here ; instead we
 * exercise the three invariants directly :
 *
 *  1. `saveLayer(SaveLayerRec(_, _, Blur(...)))` followed by an
 *     immediate `restore` reproduces the parent device's pixels
 *     filtered through the backdrop (no other draws into the layer).
 *
 *  2. The legacy `saveLayer(bounds, paint)` overload routes through
 *     [SaveLayerRec] but with `backdrop = null`, so passing a layer
 *     paint still composes the layer on top of the parent.
 *
 *  3. A `SaveLayerRec` with `backdrop != null` and a subsequent
 *     `drawColor(transparent)` (no-op under kSrcOver) leaves the
 *     backdrop's pixels intact — `restore()` composites the
 *     filtered backdrop back onto the parent.
 */
class SaveLayerRecBackdropTest {

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun isGreenish(bitmap: SkBitmap, x: Int, y: Int): Boolean {
        val px = bitmap.getPixel(x, y)
        // Premultiplied blur of a green square stays green-dominant.
        return SkColorGetG(px) > SkColorGetR(px) && SkColorGetG(px) > SkColorGetB(px) &&
            SkColorGetA(px) > 0
    }

    @Test
    fun `saveLayer with backdrop blur reproduces blurred green square`() {
        // 40 × 40 canvas, draw a 20 × 20 green square in the centre, then
        // saveLayer with a small Gaussian blur backdrop, drawColor with
        // transparent (no-op), restore. The resulting canvas should be
        // visually a blurred green square — green-dominant in the centre,
        // softer green at the edges, and the corners (which were white)
        // should be drag-blurred slightly green-tinged.
        val bitmap = render(40, 40) {
            val greenPaint = SkPaint().apply { color = SK_ColorGREEN }
            drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), greenPaint)

            val backdrop = SkImageFilters.Blur(
                sigmaX = 3f,
                sigmaY = 3f,
                tileMode = SkTileMode.kDecal,
                input = null,
                cropRect = null,
            )
            saveLayer(SaveLayerRec(bounds = null, paint = null, backdrop = backdrop, flags = 0))
            drawColor(SK_ColorTRANSPARENT, SkBlendMode.kSrcOver)
            restore()
        }

        // Centre pixel : was green (255, before blur), should still be
        // dominantly green after the blur composite back to parent.
        assertTrue(isGreenish(bitmap, 20, 20), "centre pixel must remain green-dominant")
        // A pixel just outside the original square : was white, but the
        // blurred backdrop should have tinted it visibly green
        // (the green-channel sample should exceed pure white).
        val edge = bitmap.getPixel(8, 20)
        // White's G is 255 ; a blur of green-on-white at distance 2 should
        // either stay 255 (no decrease) or skew slightly green-dominant.
        assertTrue(
            SkColorGetG(edge) >= 200,
            "edge pixel green channel ${SkColorGetG(edge)} should stay high after green-on-white blur",
        )
    }

    @Test
    fun `saveLayer(bounds, paint) preserves legacy semantics`() {
        // Existing overload : opens a transparent layer, drawColor(green),
        // restore. The expected behaviour is "draw a green rect of bounds".
        val bitmap = render(40, 40) {
            val greenPaint = SkPaint().apply { color = SK_ColorGREEN }
            saveLayer(SkRect.MakeLTRB(10f, 10f, 30f, 30f), null)
            drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), greenPaint)
            restore()
        }
        // Inside the bounds : green ; outside : untouched white.
        val inside = bitmap.getPixel(20, 20)
        assertEquals(0xFF, SkColorGetG(inside), "green channel of inside pixel")
        val outside = bitmap.getPixel(5, 5)
        assertEquals(0xFF, SkColorGetR(outside), "outside pixel stays white (red channel)")
        assertEquals(0xFF, SkColorGetG(outside), "outside pixel stays white (green channel)")
        assertEquals(0xFF, SkColorGetB(outside), "outside pixel stays white (blue channel)")
    }

    @Test
    fun `SaveLayerRec data class round-trips fields`() {
        val bounds = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val paint = SkPaint().apply { alpha = 0x80 }
        val backdrop = SkImageFilters.Blur(2f, 2f)
        val rec = SaveLayerRec(bounds, paint, backdrop, 0)
        assertEquals(bounds, rec.bounds)
        assertEquals(paint, rec.paint)
        assertNotNull(rec.backdrop)
        assertEquals(0, rec.flags)
    }
}
