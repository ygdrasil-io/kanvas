package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkRect

/**
 * R-suivi.8 — verifies that [SkTiledImageUtils.DrawImage] /
 * `DrawImageRect` actually split oversized images into
 * [SkTiledImageUtils.DEFAULT_TILE_SIZE]-sized tiles instead of
 * forwarding the entire image in one call. Small images stay on the
 * direct-forward fast path.
 */
class SkTiledImageUtilsRealTilingTest {

    /**
     * Counting canvas — records every [drawImage] / [drawImageRect] call
     * with the dimensions of the image that hit the device. Used to
     * assert the tiling layout.
     */
    private class CountingCanvas(bm: SkBitmap) : SkCanvas(bm) {
        data class DrawRecord(val w: Int, val h: Int, val x: Float, val y: Float)
        val drawImageCalls: MutableList<DrawRecord> = mutableListOf()
        val drawImageRectCalls: MutableList<DrawRecord> = mutableListOf()

        override fun drawImage(
            image: SkImage,
            x: Float,
            y: Float,
            sampling: SkSamplingOptions,
            paint: SkPaint?,
        ) {
            drawImageCalls += DrawRecord(image.width, image.height, x, y)
            // Don't actually rasterise — the test only cares about dispatch
            // accounting (image.makeSubset is a CPU copy and would dominate
            // the 4096² test cost otherwise).
        }

        override fun drawImageRect(
            image: SkImage,
            src: SkRect,
            dst: SkRect,
            sampling: SkSamplingOptions,
            paint: SkPaint?,
            constraint: SrcRectConstraint,
        ) {
            drawImageRectCalls += DrawRecord(image.width, image.height, dst.left, dst.top)
        }
    }

    /** Build a `w × h` red [SkImage] cheaply (no per-pixel writes). */
    private fun newImage(w: Int, h: Int): SkImage {
        val bm = SkBitmap(w, h)
        bm.eraseColor(SkColorSetARGB(0xFF, 255, 0, 0))
        return SkImage.Make(bm)
    }

    // ─── DrawImage tiling ──────────────────────────────────────────────

    @Test
    fun `DrawImage on small image makes a single drawImage call`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(64, 64)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(1, canvas.drawImageCalls.size, "small image → single call")
        assertEquals(64, canvas.drawImageCalls[0].w)
        assertEquals(64, canvas.drawImageCalls[0].h)
    }

    @Test
    fun `DrawImage on 2048x2048 image emits 4 tile calls (2x2)`() {
        // DEFAULT_TILE_SIZE = 1024, so 2048×2048 splits into 4 tiles.
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(2048, 2048)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(4, canvas.drawImageCalls.size, "2048² → 4 tiles")
        // Each tile must be exactly the tile size.
        for (rec in canvas.drawImageCalls) {
            assertEquals(SkTiledImageUtils.DEFAULT_TILE_SIZE, rec.w)
            assertEquals(SkTiledImageUtils.DEFAULT_TILE_SIZE, rec.h)
        }
        // Offsets should cover (0,0), (1024,0), (0,1024), (1024,1024).
        val offsets = canvas.drawImageCalls.map { it.x.toInt() to it.y.toInt() }.toSet()
        assertEquals(
            setOf(0 to 0, 1024 to 0, 0 to 1024, 1024 to 1024),
            offsets,
            "tile offsets should cover the 2×2 grid",
        )
    }

    @Test
    fun `DrawImage on 4096x4096 image emits 16 tile calls (4x4)`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(4096, 4096)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(16, canvas.drawImageCalls.size, "4096² → 16 tiles")
    }

    @Test
    fun `DrawImage on non-multiple dimensions emits trailing partial tile`() {
        // 1500×1024 = 2 tiles wide × 1 tall ; second tile only 476 px wide.
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(1500, 1024)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(2, canvas.drawImageCalls.size)
        // Sort by x : first tile full-size, second tile trailing edge.
        val sorted = canvas.drawImageCalls.sortedBy { it.x }
        assertEquals(SkTiledImageUtils.DEFAULT_TILE_SIZE, sorted[0].w)
        assertEquals(1500 - SkTiledImageUtils.DEFAULT_TILE_SIZE, sorted[1].w, "partial tile width")
        assertEquals(1024f, sorted[0].h.toFloat(), "tile height stays at image height")
    }

    @Test
    fun `DrawImage offsets tiles relative to caller's xy`() {
        // Caller paints at (100, 200) — each tile should land at
        // (100 + tx, 200 + ty).
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(2048, 1024)
        SkTiledImageUtils.DrawImage(canvas, image, 100f, 200f)
        val offsets = canvas.drawImageCalls.map { it.x.toInt() to it.y.toInt() }.toSet()
        assertEquals(setOf(100 to 200, 1124 to 200), offsets)
    }

    // ─── DrawImageRect tiling ──────────────────────────────────────────

    @Test
    fun `DrawImageRect on small image makes a single drawImageRect call`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(64, 64)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            src = SkRect.MakeIWH(64, 64),
            dst = SkRect.MakeXYWH(0f, 0f, 128f, 128f),
        )
        assertEquals(1, canvas.drawImageRectCalls.size)
    }

    @Test
    fun `DrawImageRect on 2048x2048 image emits 4 tile drawImageRect calls`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(2048, 2048)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            src = SkRect.MakeIWH(2048, 2048),
            dst = SkRect.MakeXYWH(0f, 0f, 2048f, 2048f),
        )
        assertEquals(4, canvas.drawImageRectCalls.size)
    }

    @Test
    fun `DrawImageRect skips tiles outside src rect`() {
        // 2048×2048 image, src covers only the top-left 500×500 — only
        // tile (0,0) is needed, not all 4.
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(2048, 2048)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            src = SkRect.MakeXYWH(0f, 0f, 500f, 500f),
            dst = SkRect.MakeXYWH(0f, 0f, 500f, 500f),
        )
        assertEquals(1, canvas.drawImageRectCalls.size, "only 1 tile intersects src")
    }

    // ─── Null + edge ───────────────────────────────────────────────────

    @Test
    fun `DrawImage with null is still a no-op`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        SkTiledImageUtils.DrawImage(canvas, image = null, 0f, 0f)
        assertEquals(0, canvas.drawImageCalls.size)
        assertEquals(0, canvas.drawImageRectCalls.size)
    }

    @Test
    fun `DEFAULT_TILE_SIZE is 1024 matching upstream`() {
        // Sanity check the constant — upstream's
        // `SkTiledImageUtils.cpp:kDefaultTileSize` is 1024.
        assertEquals(1024, SkTiledImageUtils.DEFAULT_TILE_SIZE)
    }

    @Test
    fun `1025 width image emits 2 tiles wide`() {
        // Boundary case : exactly 1 px over the tile size.
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(1025, 100)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(2, canvas.drawImageCalls.size, "1025 > 1024 → 2 tiles")
        val widths = canvas.drawImageCalls.map { it.w }.sorted()
        assertEquals(listOf(1, 1024), widths)
    }

    @Test
    fun `1024 width image stays on fast path (1 tile)`() {
        // Boundary case : exactly the tile size → fast path.
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(1024, 1024)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        assertEquals(1, canvas.drawImageCalls.size, "1024 = tile size → fast path")
    }

    @Test
    fun `tiling preserves dispatch order (rows, then columns)`() {
        val canvas = CountingCanvas(SkBitmap(32, 32))
        val image = newImage(2048, 2048)
        SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        // The 4 tiles should land in row-major order : (0,0), (1024,0),
        // (0,1024), (1024,1024). Order matters for layered transparency.
        val seq = canvas.drawImageCalls.map { it.x.toInt() to it.y.toInt() }
        assertEquals(
            listOf(0 to 0, 1024 to 0, 0 to 1024, 1024 to 1024),
            seq,
            "row-major dispatch order",
        )
    }

    @Test
    fun `tiled output is non-empty (sanity)`() {
        // Run with the default canvas (not the counting subclass) to
        // ensure the standard path actually rasterises something.
        val bm = SkBitmap(32, 32).also { it.eraseColor(0) }
        val canvas = SkCanvas(bm)
        val image = newImage(2048, 2048)
        SkTiledImageUtils.DrawImageRect(
            canvas, image,
            src = SkRect.MakeIWH(2048, 2048),
            dst = SkRect.MakeXYWH(0f, 0f, 32f, 32f),
        )
        // At least one pixel must have flipped from transparent.
        assertTrue(
            (0 until 32).any { y -> (0 until 32).any { x -> bm.getPixel(x, y) != 0 } },
            "at least one pixel should be non-zero after tiled drawImageRect",
        )
        assertNotEquals(0, bm.getPixel(0, 0))
    }
}
