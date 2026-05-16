package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.math.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkRect

/**
 * Unit tests for [SkTiledImageUtils]. Verifies that the helpers
 * forward to [SkCanvas.drawImage] / [SkCanvas.drawImageRect] (R1 :
 * no actual tiling — just a delegating shim).
 *
 * Null-image paths exercise the upstream-faithful no-op contract.
 */
class SkTiledImageUtilsTest {

    private val opaqueRed: SkColor = (0xFFFF0000).toInt()
    private val transparent: SkColor = 0

    private fun newCanvas(w: Int = 16, h: Int = 16): SkCanvas =
        SkCanvas(SkBitmap(w, h).also { it.eraseColor(transparent) })

    private fun newRedImage(w: Int = 4, h: Int = 4): SkImage {
        val b = SkBitmap(w, h)
        b.eraseColor(opaqueRed)
        return SkImage.Make(b)
    }

    @Test
    fun `DrawImage forwards to canvas drawImage and paints opaque pixels`() {
        val canvas = newCanvas()
        val image = newRedImage()
        SkTiledImageUtils.DrawImage(canvas, image, 2f, 2f, SkSamplingOptions.Default)
        // The destination bitmap should now have opaque-red pixels at the
        // image's destination position.
        val dst = canvas.bitmap.getPixel(3, 3)
        assertNotEquals(transparent, dst)
    }

    @Test
    fun `DrawImage with null image is a no-op`() {
        val canvas = newCanvas()
        SkTiledImageUtils.DrawImage(canvas, image = null, x = 0f, y = 0f)
        // Bitmap is untouched.
        assertEquals(transparent, canvas.bitmap.getPixel(0, 0))
    }

    @Test
    fun `DrawImageRect with explicit src and dst forwards to canvas drawImageRect`() {
        val canvas = newCanvas()
        val image = newRedImage(w = 4, h = 4)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            src = SkRect.MakeIWH(4, 4),
            dst = SkRect.MakeXYWH(0f, 0f, 8f, 8f),
            sampling = SkSamplingOptions.Default,
            paint = null,
            constraint = SrcRectConstraint.kFast,
        )
        // Upscaled image should paint into the (0,0)-(8,8) region.
        assertNotEquals(transparent, canvas.bitmap.getPixel(1, 1))
    }

    @Test
    fun `DrawImageRect dst-only overload uses image bounds as src`() {
        val canvas = newCanvas()
        val image = newRedImage(w = 4, h = 4)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            dst = SkRect.MakeXYWH(0f, 0f, 4f, 4f),
        )
        // Pixels inside the dst rect should be painted.
        assertNotEquals(transparent, canvas.bitmap.getPixel(2, 2))
        // Pixels outside the dst rect remain transparent.
        assertEquals(transparent, canvas.bitmap.getPixel(10, 10))
    }

    @Test
    fun `DrawImageRect with null image is a no-op`() {
        val canvas = newCanvas()
        SkTiledImageUtils.DrawImageRect(
            canvas, image = null,
            dst = SkRect.MakeXYWH(0f, 0f, 4f, 4f),
        )
        assertEquals(transparent, canvas.bitmap.getPixel(2, 2))
    }
}
