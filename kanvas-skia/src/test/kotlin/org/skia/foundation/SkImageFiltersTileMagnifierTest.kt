package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * C1.2 verification suite — Tile + Magnifier.
 *
 * Covers :
 *  - **Tile** : src → dst replication with implicit kRepeat ; dst
 *    larger than src tiles ; dst smaller crops ; degenerate empty
 *    src yields transparent black ; offsets honoured.
 *  - **Magnifier** : pixels outside lensBounds pass through ; pixels
 *    well inside the lens (`distance > inset`) magnify by `zoom` ;
 *    pixels within `inset` of the edge blend smoothly via the
 *    distance / inset factor ; degenerate `zoom <= 0` is a no-op.
 */
class SkImageFiltersTileMagnifierTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image with 16 distinct pixel values. */
    private val sample4x4: SkImage = run {
        val pixels = IntArray(16) { i ->
            val col = i % 4
            val row = i / 4
            (0xFF shl 24) or ((col * 60) shl 16) or ((row * 60) shl 8)
        }
        SkImage(4, 4, pixels)
    }

    /** Driver image — only its dimensions matter for filters that ignore src. */
    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    // ─── Tile ─────────────────────────────────────────────────────────

    @Test
    fun `Tile replicates src across a 2x-wider dst`() {
        // src = full 4x4 ; dst = 8x4. Output should tile src twice
        // horizontally.
        val filter = SkImageFilters.Tile(
            srcRect = SkRect.MakeWH(4f, 4f),
            dstRect = SkRect.MakeWH(8f, 4f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(8, result.image.width)
        assertEquals(4, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
        for (y in 0 until 4) for (x in 0 until 8) {
            assertEquals(
                sample4x4.peekPixel(x % 4, y),
                result.image.peekPixel(x, y),
                "tile ($x, $y)",
            )
        }
    }

    @Test
    fun `Tile to a smaller dst crops src to dst dims (pure repeat)`() {
        // Pure-repeat semantic (matches upstream Skia) : dst smaller
        // than src crops to src's top-left ; no scaling.
        val filter = SkImageFilters.Tile(
            srcRect = SkRect.MakeWH(4f, 4f),
            dstRect = SkRect.MakeWH(2f, 2f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(2, result.image.width)
        assertEquals(2, result.image.height)
        // dst (0, 0) ↔ src (0, 0) ; dst (1, 1) ↔ src (1, 1).
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(1, 1), result.image.peekPixel(1, 1))
    }

    @Test
    fun `Tile with empty src returns transparent black at dst dims`() {
        val filter = SkImageFilters.Tile(
            srcRect = SkRect.MakeWH(0f, 4f), // zero-width src
            dstRect = SkRect.MakeWH(4f, 4f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(0, result.image.peekPixel(x, y), "transparent black at ($x, $y)")
        }
    }

    @Test
    fun `Tile with non-zero dst origin records the offset`() {
        val filter = SkImageFilters.Tile(
            srcRect = SkRect.MakeWH(4f, 4f),
            dstRect = SkRect.MakeXYWH(20f, 30f, 4f, 4f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(20, result.offsetX)
        assertEquals(30, result.offsetY)
    }

    @Test
    fun `Tile with null input tiles the rasterised src directly`() {
        val filter = SkImageFilters.Tile(
            srcRect = SkRect.MakeWH(4f, 4f),
            dstRect = SkRect.MakeWH(8f, 4f),
            input = null,
        )
        val result = filter.filterImage(sample4x4, identity)
        assertEquals(8, result.image.width)
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(4, 0)) // wrap
    }

    // ─── Magnifier ────────────────────────────────────────────────────

    @Test
    fun `Magnifier outside lensBounds passes pixels through`() {
        // Lens covers (1,1)-(3,3) of a 4x4 image. Corner pixels (0,0)
        // and (3,0) are outside ⇒ pass through.
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeLTRB(1f, 1f, 3f, 3f),
            zoomAmount = 2f,
            inset = 0.5f,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        // (0, 0) is outside the lens — same as src.
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(3, 0), result.image.peekPixel(3, 0))
    }

    @Test
    fun `Magnifier centre pixel always maps to itself regardless of zoom`() {
        // The lens centre is the magnification fixed point — sampling
        // there yields `centre + 0` regardless of zoom. Confirm with
        // a centred lens on the 4x4 source.
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeWH(4f, 4f),
            zoomAmount = 2f,
            inset = 0.01f, // tiny — full magnification away from edges
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Centre of the lens is (2, 2). Pixel (2, 2) has minEdgeDist=2
        // ⇒ t=1 ⇒ full magnification. magX = cx + (devX - cx)/zoom
        // = 2 + 0/2 = 2 ⇒ samples upstream at (2, 2).
        assertEquals(sample4x4.peekPixel(2, 2), result.image.peekPixel(2, 2))
    }

    @Test
    fun `Magnifier at lens edge (t = 0) passes through`() {
        // At the lens corner (dx = 0, dy = 0), t collapses to 0
        // regardless of inset ⇒ the sampler picks up the source pixel
        // verbatim. This is the "lens edge meets pass-through"
        // semantic ; pixel (0, 0) is at the top-left corner of the
        // lens spanning (0, 0)-(4, 4), so dLeft = dTop = 0.
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeWH(4f, 4f),
            zoomAmount = 2f,
            inset = 0.01f,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Magnifier away from edges magnifies (pulls samples toward centre)`() {
        // Pixel (1, 1) on a 4x4 lens has minEdgeDist=1, with
        // inset=0.01 → t=1 → full mag. magX = 2 + (1-2)/2 = 1.5,
        // similarly magY = 1.5. The sampler rounds via
        // `(sampleX + 0.5).toInt()` ⇒ floor(2.0) = 2. So (1, 1)
        // pulls in to source (2, 2).
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeWH(4f, 4f),
            zoomAmount = 2f,
            inset = 0.01f,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(sample4x4.peekPixel(2, 2), result.image.peekPixel(1, 1))
    }

    @Test
    fun `Magnifier with non-positive zoom is a no-op`() {
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeWH(4f, 4f),
            zoomAmount = 0f,
            inset = 1f,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriver, identity)
        // No-op : output equals upstream pixels exactly.
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(
                sample4x4.peekPixel(x, y),
                result.image.peekPixel(x, y),
                "zoom <= 0 must pass through ($x, $y)",
            )
        }
    }

    @Test
    fun `Magnifier blends from pass-through at the very edge to full mag inside`() {
        // 8x8 source with a vertical hot-stripe at x=4 to make the
        // magnification visible. Lens (2..6, 2..6) ; inset = 2 so
        // the blend band reaches the lens centre.
        val pixels = IntArray(64) { i ->
            val col = i % 8
            (0xFF shl 24) or (if (col == 4) 0xFF0000 else (col * 8))
        }
        val src = SkImage(8, 8, pixels)
        val filter = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeLTRB(2f, 2f, 6f, 6f),
            zoomAmount = 2f,
            inset = 2f,
            input = SkImageFilters.Image(src),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Pixel (2, 2) sits exactly at the top-left corner of the
        // lens : dLeft=dTop=0 ⇒ t=0 ⇒ pure pass-through.
        assertEquals(src.peekPixel(2, 2), result.image.peekPixel(2, 2))
        // Pixel (4, 4) is the lens centre : dLeft=dRight=dTop=dBottom=2
        // ⇒ minEdgeDist=2 ⇒ t=1 ⇒ full magnification ; sampleX/Y =
        // lensCentre = (4, 4) ⇒ samples src(4, 4) — the bright red
        // stripe.
        assertEquals(src.peekPixel(4, 4), result.image.peekPixel(4, 4))
        assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(4, 4))
    }
}
