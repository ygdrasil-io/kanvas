package org.skia.foundation


import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix

/**
 * Unit tests for [SkImageFilters] — Phase 7d.1.
 *
 * Coverage :
 *  - **Offset** : zero offset is identity, positive offset shifts
 *    result, scales with CTM max-scale.
 *  - **ColorFilter** : every pixel is mapped through the wrapped
 *    [SkColorFilter] ; image dimensions preserved.
 *  - **Compose** : null-handling (both / first / second null), offset
 *    accumulation, output is `outer(inner(src))`.
 */
class SkImageFiltersTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image with distinct pixel values for verification. */
    private val sampleImage: SkImage = run {
        val pixels = IntArray(16) { i ->
            // Each pixel : opaque, R = column*64, G = row*64, B = 0.
            val col = i % 4
            val row = i / 4
            (0xFF shl 24) or ((col * 64) shl 16) or ((row * 64) shl 8)
        }
        SkImage(4, 4, pixels)
    }

    // -- Offset ---------------------------------------------------------------

    @Test
    fun `Offset with zero displacement returns input image unchanged`() {
        val filter = SkImageFilters.Offset(0f, 0f)
        val out = filter.filterImage(sampleImage, identity)
        assertSame(sampleImage, out.image)
        assertEquals(0, out.offsetX)
        assertEquals(0, out.offsetY)
    }

    @Test
    fun `Offset with positive displacement returns input + offset`() {
        val filter = SkImageFilters.Offset(10f, 5f)
        val out = filter.filterImage(sampleImage, identity)
        assertSame(sampleImage, out.image)
        assertEquals(10, out.offsetX)
        assertEquals(5, out.offsetY)
    }

    @Test
    fun `Offset scales with CTM max-scale`() {
        // Under a 2x scale, an Offset(10, 5) should become offset by 20, 10.
        val filter = SkImageFilters.Offset(10f, 5f)
        val ctm = SkMatrix.MakeScale(2f, 2f)
        val out = filter.filterImage(sampleImage, ctm)
        assertEquals(20, out.offsetX)
        assertEquals(10, out.offsetY)
    }

    // -- ColorFilter ---------------------------------------------------------

    @Test
    fun `ColorFilter applies cf to every pixel preserving dimensions`() {
        // Identity matrix colour filter — output should equal input.
        val identityCf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val filter = SkImageFilters.ColorFilter(identityCf)
        val out = filter.filterImage(sampleImage, identity)
        assertEquals(sampleImage.width, out.image.width)
        assertEquals(sampleImage.height, out.image.height)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    sampleImage.peekPixel(x, y),
                    out.image.peekPixel(x, y),
                    "identity colour filter should preserve pixel ($x, $y)",
                )
            }
        }
    }

    @Test
    fun `ColorFilter swap RB produces a swapped image`() {
        val swapRB = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val filter = SkImageFilters.ColorFilter(swapRB)
        val out = filter.filterImage(sampleImage, identity)
        // Sample a pixel with known R, B and verify they swapped.
        val srcPx = sampleImage.peekPixel(2, 1)  // R=128, G=64, B=0
        val outPx = out.image.peekPixel(2, 1)
        assertEquals(SkColorGetR(srcPx), SkColorGetB(outPx),
            "R should land in B after RB swap")
        assertEquals(SkColorGetB(srcPx), SkColorGetR(outPx),
            "B should land in R after RB swap")
    }

    // -- Compose --------------------------------------------------------------

    @Test
    fun `Compose with both nulls returns null`() {
        assertNull(SkImageFilters.Compose(null, null))
    }

    @Test
    fun `Compose with null outer returns inner unchanged`() {
        val inner = SkImageFilters.Offset(5f, 5f)
        assertSame(inner, SkImageFilters.Compose(outer = null, inner = inner))
    }

    @Test
    fun `Compose with null inner returns outer unchanged`() {
        val outer = SkImageFilters.Offset(5f, 5f)
        assertSame(outer, SkImageFilters.Compose(outer = outer, inner = null))
    }

    @Test
    fun `Compose stacks the offsets of two Offset filters`() {
        val outer = SkImageFilters.Offset(10f, 0f)
        val inner = SkImageFilters.Offset(0f, 5f)
        val composed = SkImageFilters.Compose(outer, inner)!!
        val out = composed.filterImage(sampleImage, identity)
        assertEquals(10, out.offsetX, "outer's dx should stack")
        assertEquals(5, out.offsetY, "inner's dy should stack")
    }

    @Test
    fun `Compose evaluates inner first then outer for image transformation`() {
        // inner = identity colour filter ; outer = swap-RB
        val identityCf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val swapRB = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val inner = SkImageFilters.ColorFilter(identityCf)
        val outer = SkImageFilters.ColorFilter(swapRB)
        val composed = SkImageFilters.Compose(outer, inner)!!
        val out = composed.filterImage(sampleImage, identity)
        // Inner = identity (no change), outer = swap RB. Final should
        // equal swap-RB applied directly to source.
        val swapDirect = SkImageFilters.ColorFilter(swapRB).filterImage(sampleImage, identity)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    swapDirect.image.peekPixel(x, y),
                    out.image.peekPixel(x, y),
                    "compose output should equal direct swap at ($x, $y)",
                )
            }
        }
    }

    @Test
    fun `Offset with input chain accumulates upstream offset`() {
        val inner = SkImageFilters.Offset(10f, 0f)
        val outer = SkImageFilters.Offset(0f, 5f, input = inner)
        val out = outer.filterImage(sampleImage, identity)
        assertEquals(10, out.offsetX, "inner's dx should propagate via input chain")
        assertEquals(5, out.offsetY, "outer's dy adds")
    }

    // -- J3 — Compose with non-null inner that physically shifts the image
    //         (so outer must see inner's spatial position, not just its
    //         pixel buffer). Mirrors upstream Skia's
    //         `ctx.withNewSource(innerResult)` semantics in
    //         `SkComposeImageFilter::onFilterImage`.

    @Test
    fun `Compose outer ColorFilter inner Offset sees inner spatial position`() {
        // Inner = Offset(2, 0) shifts a 4x4 image right by 2px (so the
        // logical image now occupies columns [2, 6) at y in [0, 4)).
        // Outer = an identity ColorFilter that just copies its input
        // pixel-for-pixel. The composed result must contain inner's
        // shifted pixels — i.e. the materialized image must be 6 wide,
        // with columns 0..1 transparent and columns 2..5 carrying the
        // original sampleImage rows.
        val identityCf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val inner = SkImageFilters.Offset(2f, 0f)
        val outer = SkImageFilters.ColorFilter(identityCf)
        val composed = SkImageFilters.Compose(outer, inner)!!
        val out = composed.filterImage(sampleImage, identity)
        // The materialized image should be 6 wide x 4 tall (covers
        // both the origin (0,0) and the shifted inner at (2,0)).
        assertEquals(6, out.image.width, "materialized width covers origin + shifted inner")
        assertEquals(4, out.image.height, "materialized height matches inner")
        // Final offset is (left, top) = (0, 0) — origin is contained.
        assertEquals(0, out.offsetX, "left of materialized footprint")
        assertEquals(0, out.offsetY, "top of materialized footprint")
        // The shifted region (columns 2..5) carries sampleImage pixels.
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    sampleImage.peekPixel(x, y),
                    out.image.peekPixel(x + 2, y),
                    "shifted pixel at ($x, $y) -> materialized ($x+2, $y)",
                )
            }
        }
        // The padding region (columns 0..1) is transparent black.
        for (y in 0 until 4) {
            for (x in 0 until 2) {
                assertEquals(0, out.image.peekPixel(x, y),
                    "padding pixel at ($x, $y) should be transparent")
            }
        }
    }

    @Test
    fun `Compose outer Offset inner Offset keeps fast offset-stacking path`() {
        // When outer is a pure Offset, the materialization fast-path
        // kicks in : inner's image is forwarded as-is and the offsets
        // simply stack. This preserves the original test
        // `Compose stacks the offsets of two Offset filters`.
        val outer = SkImageFilters.Offset(10f, 0f)
        val inner = SkImageFilters.Offset(0f, 5f)
        val composed = SkImageFilters.Compose(outer, inner)!!
        val out = composed.filterImage(sampleImage, identity)
        // Fast path : image unchanged, offsets accumulated.
        assertSame(sampleImage, out.image, "fast path forwards inner's image")
        assertEquals(10, out.offsetX)
        assertEquals(5, out.offsetY)
    }

    @Test
    fun `Compose outer ColorFilter inner Offset negative dx materializes with left padding`() {
        // Inner = Offset(-2.5, 0) which the implementation rounds to
        // (sx = (-2.5 + 0.5).toInt() = -2) shifts the image LEFT by 2
        // px. The materialized footprint covers BOTH the origin (0, 0)
        // and the shifted image at (-2, 0). Since the image is 4 wide,
        // it spans [-2, 2) in layer space, which already contains the
        // origin -- so the footprint is just the image's own range :
        // left = -2, right = 2, width = 4. Final offset becomes (-2, 0).
        val identityCf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val inner = SkImageFilters.Offset(-2.5f, 0f)
        val outer = SkImageFilters.ColorFilter(identityCf)
        val composed = SkImageFilters.Compose(outer, inner)!!
        val out = composed.filterImage(sampleImage, identity)
        assertEquals(4, out.image.width, "materialized width = image range, origin already inside")
        assertEquals(4, out.image.height)
        assertEquals(-2, out.offsetX, "left of materialized footprint")
        assertEquals(0, out.offsetY)
        // The image content lives in columns [0, 4) of the materialized
        // image (which corresponds to layer columns [-2, 2)).
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    sampleImage.peekPixel(x, y),
                    out.image.peekPixel(x, y),
                    "shifted pixel at ($x, $y) -> materialized ($x, $y)",
                )
            }
        }
    }
}
