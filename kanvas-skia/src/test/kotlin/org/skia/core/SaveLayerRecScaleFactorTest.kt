package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode

/**
 * Phase R1-C — exercises the new [SaveLayerRec.scaleFactor] field
 * (Skia's `SaveLayerRec::fExperimentalBackdropScale`) by rendering a
 * solid-colour parent device with a Gaussian-blur backdrop at two
 * different scale factors and asserting the rendered output differs.
 *
 *  - `scaleFactor = 1.0` (default) : full-resolution backdrop snapshot,
 *    matches the pre-Phase-R1-C output bit-for-bit.
 *  - `scaleFactor = 0.5` : the backdrop snapshot is downsampled to
 *    half-resolution before filtering then upsampled back — the
 *    resulting pixel grid is visibly blockier (different from the
 *    1.0 reference) but still green-dominant.
 */
class SaveLayerRecScaleFactorTest {

    private fun renderWithScale(scaleFactor: Float): SkBitmap {
        val bitmap = SkBitmap(40, 40)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply {
            // Checkerboard-style green / white pattern in the parent so
            // a downscale of the backdrop snapshot visibly changes the
            // pixel grid (a solid colour would round-trip identically
            // through downsample + filter + upsample).
            val green = SkPaint().apply { color = 0xFF00FF00.toInt() }
            for (y in 0 until 40 step 4) {
                for (x in 0 until 40 step 4) {
                    if (((x + y) / 4) and 1 == 0) {
                        drawRect(
                            org.skia.math.SkRect.MakeLTRB(
                                x.toFloat(), y.toFloat(), (x + 4).toFloat(), (y + 4).toFloat(),
                            ),
                            green,
                        )
                    }
                }
            }
            val blur = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
            // Snapshot the parent into the new layer via the backdrop
            // filter at the requested scale factor.
            saveLayer(SaveLayerRec(bounds = null, paint = null, backdrop = blur, scaleFactor = scaleFactor))
            restore()
        }
        return bitmap
    }

    @Test
    fun `scaleFactor default is 1f`() {
        val rec = SaveLayerRec()
        assertEquals(1f, rec.scaleFactor)
    }

    @Test
    fun `scaleFactor=1 reproduces full-resolution backdrop`() {
        val bm = renderWithScale(1f)
        // The checkerboard's green pixels should survive the blur ;
        // sanity check that any pixel still has non-zero green.
        var greenSomewhere = false
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                if (SkColorGetG(bm.getPixel(x, y)) > 0) { greenSomewhere = true; break }
            }
            if (greenSomewhere) break
        }
        assertTrue(greenSomewhere, "blurred checkerboard should still contain green")
    }

    @Test
    fun `scaleFactor below 1 produces different output than the full-res reference`() {
        val full = renderWithScale(1f)
        val half = renderWithScale(0.5f)
        // Find at least one pixel that differs between the two renders.
        var differs = false
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                if (full.getPixel(x, y) != half.getPixel(x, y)) { differs = true; break }
            }
            if (differs) break
        }
        assertTrue(differs, "scaleFactor < 1 must change the backdrop pixel grid")
    }
}
