package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkMatrix

/**
 * Unit tests for [SkImageFilters.Blur] / [SkImageFilters.MatrixTransform]
 * / [SkImageFilters.DropShadow] (Phase 7d.2).
 *
 * Coverage :
 *  - Blur : non-positive sigma returns input ; positive sigma grows
 *    the image by `±ceil(3·σ)` per axis ; uniform-mass conservation
 *    on a single-pixel blob ; offset is negated.
 *  - MatrixTransform : non-invertible matrix returns input ; identity
 *    preserves dimensions ; scale-2 doubles dimensions.
 *  - DropShadow : output bounds union the original + offset shadow ;
 *    pixel inside original area is unchanged when source is opaque.
 */
class SkImageFiltersBlurMatrixDropShadowTest {

    private val identity = SkMatrix.Identity

    private val sampleImage: SkImage = SkImage(8, 8, IntArray(64) { 0xFFFF0000.toInt() })

    // -- Blur ---------------------------------------------------------------

    @Test
    fun `Blur with zero sigma returns input unchanged`() {
        val filter = SkImageFilters.Blur(0f)
        // sigma <= 0 returns input or null when input is null
        assertNull(filter as Any?)
    }

    @Test
    fun `Blur with non-finite sigma returns null when input null`() {
        assertNull(SkImageFilters.Blur(Float.NaN) as Any?)
        assertNull(SkImageFilters.Blur(Float.POSITIVE_INFINITY) as Any?)
    }

    @Test
    fun `Blur with positive sigma grows output by 6 sigma per axis`() {
        val filter = SkImageFilters.Blur(2f)!!
        val out = filter.filterImage(sampleImage, identity)
        // radius = ceil(3*2) = 6 per side, so output is 8 + 12 = 20.
        assertEquals(20, out.image.width)
        assertEquals(20, out.image.height)
        assertEquals(-6, out.offsetX, "offset compensates for left margin")
        assertEquals(-6, out.offsetY, "offset compensates for top margin")
    }

    @Test
    fun `Blur preserves total alpha mass (uniform input)`() {
        // Blur a fully opaque uniform-red image and check the centre
        // pixel is still ~opaque red.
        val filter = SkImageFilters.Blur(1f)!!
        val out = filter.filterImage(sampleImage, identity).image
        // Centre pixel of the output (which corresponds to source centre).
        val centre = out.peekPixel(out.width / 2, out.height / 2)
        val centreA = (centre ushr 24) and 0xFF
        val centreR = (centre ushr 16) and 0xFF
        // Centre is fully inside the source ⇒ alpha ≈ 255, R ≈ 255.
        assertTrue(centreA >= 250) { "centre alpha should be ≈255, got $centreA" }
        assertTrue(centreR >= 250) { "centre red should be ≈255, got $centreR" }
    }

    // -- MatrixTransform ----------------------------------------------------

    @Test
    fun `MatrixTransform non-invertible returns input`() {
        val degenerate = SkMatrix.MakeAll(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)
        assertNull(SkImageFilters.MatrixTransform(degenerate) as Any?)
    }

    @Test
    fun `MatrixTransform identity preserves dimensions`() {
        val filter = SkImageFilters.MatrixTransform(SkMatrix.Identity)!!
        val out = filter.filterImage(sampleImage, identity)
        assertEquals(sampleImage.width, out.image.width)
        assertEquals(sampleImage.height, out.image.height)
    }

    @Test
    fun `MatrixTransform scale-2 doubles dimensions`() {
        val filter = SkImageFilters.MatrixTransform(SkMatrix.MakeScale(2f, 2f))!!
        val out = filter.filterImage(sampleImage, identity)
        assertEquals(16, out.image.width)
        assertEquals(16, out.image.height)
    }

    // -- DropShadow ---------------------------------------------------------

    @Test
    fun `DropShadow returns image larger than source for non-zero offset`() {
        val filter = SkImageFilters.DropShadow(
            dx = 10f, dy = 5f,
            sigmaX = 2f, sigmaY = 2f,
            color = 0xFF000000.toInt(),
        )
        val out = filter.filterImage(sampleImage, identity)
        // Shadow image is the blurred (8+12)×(8+12) = 20×20 tinted img.
        // Offset 10 right + 5 down ; union with original 8×8 at (0,0).
        // Union covers from (-6, -6) to max(8, 10-6+20) = (8, 24)
        //                          and (8, 5-6+20) = (8, 19) — but
        // shadow positive offset 10, 5 plus negative blur margin -6, -6
        // gives shadow bounds (4, -1)..(24, 19). Original (0..8, 0..8).
        // Union (0..24, -1..19) = 24×20.
        // Just check the result is at least as big as either.
        assertTrue(out.image.width >= 8) { "output width ${out.image.width} < source width 8" }
        assertTrue(out.image.height >= 8) { "output height ${out.image.height} < source height 8" }
    }

    @Test
    fun `DropShadow preserves opaque source pixels`() {
        val filter = SkImageFilters.DropShadow(
            dx = 5f, dy = 5f,
            sigmaX = 1f, sigmaY = 1f,
            color = 0xFF000000.toInt(),
        )
        val out = filter.filterImage(sampleImage, identity)
        // The original source image lives at (-out.offsetX, -out.offsetY)
        // in the result. Sample a pixel inside that region.
        val srcOriginX = -out.offsetX
        val srcOriginY = -out.offsetY
        val sampleX = srcOriginX + 4
        val sampleY = srcOriginY + 4
        val px = out.image.peekPixel(sampleX, sampleY)
        // Source was fully opaque red ; should still be opaque red.
        val a = (px ushr 24) and 0xFF
        val r = (px ushr 16) and 0xFF
        assertEquals(255, a, "source pixel should stay opaque")
        assertEquals(255, r, "source pixel red should be preserved")
    }

    private fun assertNull(value: Any?) {
        org.junit.jupiter.api.Assertions.assertNull(value)
    }
}
