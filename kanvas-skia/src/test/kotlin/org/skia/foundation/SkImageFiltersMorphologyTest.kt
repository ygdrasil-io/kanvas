package org.skia.foundation


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix

/**
 * C1.4 verification suite — morphology family (Erode + Dilate).
 *
 * Covers :
 *  - **Erode** : per-channel min over a `(2·rx + 1) × (2·ry + 1)`
 *    rectangular kernel. Edges shrink because OOB samples are
 *    treated as transparent black (= 0).
 *  - **Dilate** : per-channel max over the same kernel. Output bbox
 *    expands by `(rx, ry)` on each side, since dilation grows the
 *    region.
 *  - Zero-radius is a pass-through (no-op) for both ops.
 */
class SkImageFiltersMorphologyTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image, all pixels opaque red. */
    private val redImg: SkImage = SkImage(4, 4, IntArray(16) { 0xFFFF0000.toInt() })

    /** A 5×5 image, transparent black except a single opaque-red pixel at (2, 2). */
    private val singleDotImg: SkImage = SkImage(
        5, 5,
        IntArray(25) { i -> if (i == 12) 0xFFFF0000.toInt() else 0 },
    )

    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    // ─── Erode ──────────────────────────────────────────────────────────

    @Test
    fun `Erode with (0, 0) is a no-op`() {
        val filter = SkImageFilters.Erode(0, 0, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(x, y))
        }
    }

    @Test
    fun `Erode with (1, 0) wipes left and right columns`() {
        // Horizontal kernel of size 3 (rx=1, ry=0). Output is same
        // 4×4 size, but columns 0 and 3 see an OOB sample at the edge
        // → min = 0 (transparent). Inner columns 1 and 2 stay opaque.
        val filter = SkImageFilters.Erode(1, 0, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        for (y in 0 until 4) {
            assertEquals(0, result.image.peekPixel(0, y), "col 0 row $y should be transparent")
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(1, y), "col 1 row $y")
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(2, y), "col 2 row $y")
            assertEquals(0, result.image.peekPixel(3, y), "col 3 row $y should be transparent")
        }
    }

    @Test
    fun `Erode with (0, 1) wipes top and bottom rows`() {
        val filter = SkImageFilters.Erode(0, 1, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        for (x in 0 until 4) {
            assertEquals(0, result.image.peekPixel(x, 0), "row 0 col $x")
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(x, 1), "row 1 col $x")
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(x, 2), "row 2 col $x")
            assertEquals(0, result.image.peekPixel(x, 3), "row 3 col $x")
        }
    }

    @Test
    fun `Erode of a single dot to nothing`() {
        // Single opaque pixel at (2, 2) on transparent. Erode by (1, 1)
        // → every output sample needs the centre of the 3×3 kernel
        // covered by an in-bounds pixel ; only the pixel ITSELF is
        // opaque → kernel always sees ≥ 1 transparent neighbour →
        // min = 0 everywhere.
        val filter = SkImageFilters.Erode(1, 1, SkImageFilters.Image(singleDotImg))
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until result.image.height) for (x in 0 until result.image.width) {
            assertEquals(0, result.image.peekPixel(x, y), "expected transparent at ($x, $y)")
        }
    }

    // ─── Dilate ─────────────────────────────────────────────────────────

    @Test
    fun `Dilate with (0, 0) is a no-op`() {
        val filter = SkImageFilters.Dilate(0, 0, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
    }

    @Test
    fun `Dilate of a single dot grows to a 3x3 square`() {
        // Single opaque-red pixel at (2, 2). Dilate by (1, 1) →
        // output is 7×7 (input 5×5 + 2 padding on each side). The
        // dilated region is a 3×3 opaque-red square centred at (3, 3)
        // in output coords (since padding shifts everything by 1).
        val filter = SkImageFilters.Dilate(1, 1, SkImageFilters.Image(singleDotImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(7, result.image.width)
        assertEquals(7, result.image.height)
        // The padding offsets the dot from input (2, 2) to output
        // (2 + 1, 2 + 1) = (3, 3). The 3×3 kernel makes the dilated
        // region span output cols/rows 2..4.
        for (y in 0 until 7) for (x in 0 until 7) {
            val expectInside = x in 2..4 && y in 2..4
            val expected = if (expectInside) 0xFFFF0000.toInt() else 0
            assertEquals(expected, result.image.peekPixel(x, y), "pixel ($x, $y)")
        }
    }

    @Test
    fun `Dilate output offset shifts by minus radius`() {
        // Output bbox grows outward, so the device-space offset of
        // the result must shift by (-rx, -ry).
        val filter = SkImageFilters.Dilate(1, 1, SkImageFilters.Image(singleDotImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(-1, result.offsetX)
        assertEquals(-1, result.offsetY)
    }

    @Test
    fun `Dilate of opaque rect is unchanged interior`() {
        // 4×4 opaque red dilated by (1, 1) → 6×6, all opaque red
        // (interior was already saturated, edges grow into transparent
        // padding which max-stacks with the bordering reds).
        val filter = SkImageFilters.Dilate(1, 1, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(6, result.image.width)
        assertEquals(6, result.image.height)
        for (y in 0 until 6) for (x in 0 until 6) {
            assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(x, y), "pixel ($x, $y)")
        }
    }

    // ─── Negative-radius coercion ───────────────────────────────────────

    @Test
    fun `Erode with negative radii is treated as zero (no-op)`() {
        val filter = SkImageFilters.Erode(-3, -2, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(2, 2))
    }

    @Test
    fun `Dilate with negative radii is treated as zero (no-op)`() {
        val filter = SkImageFilters.Dilate(-1, -1, SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
    }
}
