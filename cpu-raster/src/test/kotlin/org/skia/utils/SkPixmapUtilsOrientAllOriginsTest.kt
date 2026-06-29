package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkBitmap

/**
 * R-suivi.9 — exhaustive coverage of [PixmapUtils.Orient] for all
 * eight [SkEncodedOrigin] values on a 4×4 bitmap that encodes each
 * pixel as `(sx * 100 + sy) | 0xFF000000` so per-pixel mismatches are
 * trivially diagnosable.
 *
 * Each per-origin test :
 *  1. Allocates `dst` with the dimensions expected for that origin
 *     ([PixmapUtils.SwapWidthHeight]-shaped for 90°-style rotations,
 *     same-shape otherwise).
 *  2. Calls [PixmapUtils.Orient] and asserts `true`.
 *  3. Verifies `dst.width` / `dst.height` against the expected post-
 *     orientation dimensions.
 *  4. Verifies every destination pixel against the closed-form
 *     `(sx, sy) → (dx, dy)` mapping derived from
 *     [SkEncodedOrigin.toMatrix] (see [PixmapUtils] doc).
 */
class PixmapUtilsOrientAllOriginsTest {

    private val W = 4
    private val H = 4

    /** Build a 4×4 bitmap where pixel (sx, sy) carries the sentinel value `0xFF000000 | (sx * 100 + sy)`. */
    private fun sentinelBitmap(): SkBitmap {
        val b = SkBitmap(W, H)
        for (sy in 0 until H) {
            for (sx in 0 until W) {
                b.setPixel(sx, sy, (0xFF000000.toInt()) or (sx * 100 + sy))
            }
        }
        return b
    }

    private fun expectedColor(sx: Int, sy: Int): Int = (0xFF000000.toInt()) or (sx * 100 + sy)

    @Test
    fun `kTopLeft is identity copy`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(W, H)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kTopLeft))
        assertEquals(W, dst.width); assertEquals(H, dst.height)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(sx, sy), "kTopLeft (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kTopRight is horizontal flip`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(W, H)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kTopRight))
        assertEquals(W, dst.width); assertEquals(H, dst.height)
        // (sx, sy) -> (W-1-sx, sy)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(W - 1 - sx, sy), "kTopRight (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kBottomRight is 180 degree rotation`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(W, H)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kBottomRight))
        assertEquals(W, dst.width); assertEquals(H, dst.height)
        // (sx, sy) -> (W-1-sx, H-1-sy)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(W - 1 - sx, H - 1 - sy), "kBottomRight (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kBottomLeft is vertical flip`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(W, H)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kBottomLeft))
        assertEquals(W, dst.width); assertEquals(H, dst.height)
        // (sx, sy) -> (sx, H-1-sy)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(sx, H - 1 - sy), "kBottomLeft (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kLeftTop is transpose`() {
        val src = sentinelBitmap()
        // Swap-shaped destination: dst is (H, W).
        val dst = SkBitmap(H, W)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kLeftTop))
        assertEquals(H, dst.width); assertEquals(W, dst.height)
        // (sx, sy) -> (sy, sx)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(sy, sx), "kLeftTop (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kRightTop is 90 degree CW rotation`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(H, W)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kRightTop))
        assertEquals(H, dst.width); assertEquals(W, dst.height)
        // (sx, sy) -> (H-1-sy, sx)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(H - 1 - sy, sx), "kRightTop (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kRightBottom is anti-transpose`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(H, W)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kRightBottom))
        assertEquals(H, dst.width); assertEquals(W, dst.height)
        // (sx, sy) -> (H-1-sy, W-1-sx)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(H - 1 - sy, W - 1 - sx), "kRightBottom (sx=$sx, sy=$sy)")
        }
    }

    @Test
    fun `kLeftBottom is 90 degree CCW rotation`() {
        val src = sentinelBitmap()
        val dst = SkBitmap(H, W)
        assertTrue(PixmapUtils.Orient(dst, src, SkEncodedOrigin.kLeftBottom))
        assertEquals(H, dst.width); assertEquals(W, dst.height)
        // (sx, sy) -> (sy, W-1-sx)
        for (sy in 0 until H) for (sx in 0 until W) {
            assertEquals(expectedColor(sx, sy), dst.getPixel(sy, W - 1 - sx), "kLeftBottom (sx=$sx, sy=$sy)")
        }
    }

    // ─── Negative-path tests ────────────────────────────────────────

    @Test
    fun `swap-style origin with non-swapped dst returns false`() {
        // Use a non-square 3×5 source so swap-style dimensions (5×3)
        // differ from same-shape (3×5) and the contract is observable.
        val src = SkBitmap(3, 5).also { b ->
            for (sy in 0 until 5) for (sx in 0 until 3) {
                b.setPixel(sx, sy, (0xFF000000.toInt()) or (sx * 100 + sy))
            }
        }
        // dst keeps the source's shape (3, 5) → mismatches swap-style.
        val dst = SkBitmap(3, 5)
        assertEquals(false, PixmapUtils.Orient(dst, src, SkEncodedOrigin.kLeftTop))
        assertEquals(false, PixmapUtils.Orient(dst, src, SkEncodedOrigin.kRightTop))
        assertEquals(false, PixmapUtils.Orient(dst, src, SkEncodedOrigin.kRightBottom))
        assertEquals(false, PixmapUtils.Orient(dst, src, SkEncodedOrigin.kLeftBottom))
    }
}
