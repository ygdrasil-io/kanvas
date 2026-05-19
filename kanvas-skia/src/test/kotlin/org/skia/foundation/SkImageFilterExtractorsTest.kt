package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Phase G-saveLayer-imageFilter -- unit tests for the public extractor
 * functions [SkImageFilter.asBlurImageFilter] and
 * [SkImageFilter.asColorFilterImageFilter].
 *
 * These extractors let GPU backends inspect the two image-filter
 * variants the WebGPU layer-composite scaffolding can route -- Blur
 * (currently throws with a descriptive error) and ColorFilter wrap
 * (routed through the existing colour-filter uniform packing). The
 * tests pin :
 *  - non-Blur / non-ColorFilter variants return `null` (no leak across
 *    `internal` class boundaries).
 *  - Blur extracts `sigmaX` / `sigmaY` / `tileMode` / `input` as the
 *    factory was called.
 *  - ColorFilter wrap extracts the inner [SkColorFilter] + optional
 *    child input as the factory was called.
 *
 * The pattern mirrors [SkColorFilter.asBlendModeFilter] /
 * [SkColorFilter.asMatrixFilter], which provide the GPU-side
 * colour-filter extractors.
 */
class SkImageFilterExtractorsTest {

    private val identityMatrix = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    @Test
    fun `asBlurImageFilter returns null for ColorFilter wrap`() {
        val cf = SkColorFilters.Matrix(identityMatrix)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        assertNull(wrap.asBlurImageFilter())
    }

    @Test
    fun `asBlurImageFilter returns null for Offset filter`() {
        val offset = SkImageFilters.Offset(dx = 4f, dy = 0f, input = null)
        assertNull(offset.asBlurImageFilter())
    }

    @Test
    fun `asBlurImageFilter returns sigmas and tileMode for Blur`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 2.5f, sigmaY = 1.25f,
            tileMode = SkTileMode.kClamp, input = null,
        )
        assertNotNull(blur)
        val params = blur!!.asBlurImageFilter()
        assertNotNull(params)
        assertEquals(2.5f, params!!.sigmaX)
        assertEquals(1.25f, params.sigmaY)
        assertEquals(SkTileMode.kClamp, params.tileMode)
        assertNull(params.input)
    }

    @Test
    fun `asBlurImageFilter preserves non-null child input`() {
        val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f,
            tileMode = SkTileMode.kDecal, input = inner,
        )
        assertNotNull(blur)
        val params = blur!!.asBlurImageFilter()
        assertNotNull(params)
        assertEquals(inner, params!!.input)
    }

    @Test
    fun `asColorFilterImageFilter returns null for Blur`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f,
            tileMode = SkTileMode.kDecal, input = null,
        )
        assertNotNull(blur)
        assertNull(blur!!.asColorFilterImageFilter())
    }

    @Test
    fun `asColorFilterImageFilter returns null for Offset`() {
        val offset = SkImageFilters.Offset(dx = 4f, dy = 0f, input = null)
        assertNull(offset.asColorFilterImageFilter())
    }

    @Test
    fun `asColorFilterImageFilter extracts inner colorFilter and null input`() {
        val cf = SkColorFilters.Matrix(identityMatrix)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        val params = wrap.asColorFilterImageFilter()
        assertNotNull(params)
        assertEquals(cf, params!!.colorFilter)
        assertNull(params.input)
    }

    @Test
    fun `asColorFilterImageFilter extracts non-null child input`() {
        val cf = SkColorFilters.Matrix(identityMatrix)
        val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
        val wrap = SkImageFilters.ColorFilter(cf, input = inner)
        val params = wrap.asColorFilterImageFilter()
        assertNotNull(params)
        assertEquals(cf, params!!.colorFilter)
        assertEquals(inner, params.input)
    }
}
