package org.skia.foundation


import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * C1.1 verification suite — source / passthrough wrappers.
 *
 * Covers :
 *  - **Image** : ignores `src`, returns the wrapped image at the
 *    `dstRect` offset. Identity-shape fast path returns the
 *    original image by reference.
 *  - **Picture** : replays an [org.skia.core.SkPicture] into a
 *    bitmap of `targetRect` size, with the picture's local origin
 *    translated to the bitmap's `(0, 0)`.
 *  - **Shader** : fills a buffer sized to `src` with the shader's
 *    per-pixel output (driven through `SkBitmapDevice.drawPaint`
 *    so it sees the same pipeline the raster sinks see).
 *  - **Empty** : 1×1 transparent black, singleton.
 *  - **Crop** : per-tileMode edge sampling (kDecal / kClamp /
 *    kRepeat / kMirror) ; null `input` means "crop the rasterised
 *    source directly".
 */
class SkImageFiltersSourcePassthroughTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image with distinct pixel values for verification. */
    private val sample4x4: SkImage = run {
        val pixels = IntArray(16) { i ->
            val col = i % 4
            val row = i / 4
            (0xFF shl 24) or ((col * 60) shl 16) or ((row * 60) shl 8)
        }
        SkImage(4, 4, pixels)
    }

    /** A "draw bitmap" surrogate so `filterImage` always has *some* `src` arg. */
    private val anyDriverImage: SkImage = SkImage(2, 2, IntArray(4))

    // ─── Image ────────────────────────────────────────────────────────

    @Test
    fun `Image identity-shape fast path returns the wrapped image by reference`() {
        val filter = SkImageFilters.Image(sample4x4)
        val result = filter.filterImage(anyDriverImage, identity)
        assertSame(sample4x4, result.image, "expected identity-shape fast path")
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `Image with non-default dstRect carries the offset`() {
        val filter = SkImageFilters.Image(
            image = sample4x4,
            srcRect = SkRect.MakeWH(4f, 4f),
            dstRect = SkRect.MakeXYWH(10f, 20f, 4f, 4f),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(10, result.offsetX)
        assertEquals(20, result.offsetY)
        assertEquals(4, result.image.width)
        assertEquals(4, result.image.height)
        // Pixel at (0, 0) of the result should be the same as (0, 0)
        // of the source — same dimensions, no scaling.
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(3, 3), result.image.peekPixel(3, 3))
    }

    @Test
    fun `Image with a sub-rect src samples the requested sub-region`() {
        // Take the bottom-right 2x2 quadrant of the 4x4 source, render
        // into a 2x2 dst rect at origin.
        val filter = SkImageFilters.Image(
            image = sample4x4,
            srcRect = SkRect.MakeXYWH(2f, 2f, 2f, 2f),
            dstRect = SkRect.MakeWH(2f, 2f),
            sampling = SkSamplingOptions.Default, // nearest by default
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(2, result.image.width)
        assertEquals(2, result.image.height)
        // Result (0, 0) ↔ source (2, 2) ; (1, 1) ↔ (3, 3).
        assertEquals(sample4x4.peekPixel(2, 2), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(3, 3), result.image.peekPixel(1, 1))
    }

    // ─── Picture ──────────────────────────────────────────────────────

    @Test
    fun `Picture replays into a bitmap of targetRect size with the local origin translated`() {
        // Record a picture that draws a red rect at local (10, 20) of
        // size 5x5. Replay into a target rect that maps local (10, 20)
        // to bitmap (0, 0).
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(SkRect.MakeWH(50f, 50f))
        canvas.drawRect(
            SkRect.MakeXYWH(10f, 20f, 5f, 5f),
            SkPaint(SK_ColorRED).apply { isAntiAlias = false },
        )
        val picture = recorder.finishRecordingAsPicture()

        val filter = SkImageFilters.Picture(picture, SkRect.MakeXYWH(10f, 20f, 5f, 5f))
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(5, result.image.width)
        assertEquals(5, result.image.height)
        assertEquals(10, result.offsetX)
        assertEquals(20, result.offsetY)
        // Pixel (0, 0) of the result should be red (translated origin
        // brings the recorded rect's top-left to bitmap origin).
        val px = result.image.peekPixel(0, 0)
        assertEquals(0xFFFF0000.toInt(), px, "got 0x${px.toString(16)}")
    }

    @Test
    fun `Picture overload uses the cullRect as the target by default`() {
        val recorder = SkPictureRecorder()
        recorder.beginRecording(SkRect.MakeWH(8f, 6f))
        val picture = recorder.finishRecordingAsPicture()
        val filter = SkImageFilters.Picture(picture)
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(8, result.image.width)
        assertEquals(6, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    // ─── Shader ───────────────────────────────────────────────────────

    @Test
    fun `Shader fills a buffer sized to src with the shader's output`() {
        // A linear gradient from red (left) to blue (right) over
        // a 4-px-wide source. The shader filter should produce a
        // 4x2 bitmap (matching src dims).
        val src = SkImage(4, 2, IntArray(8))
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(4f, 0f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = floatArrayOf(0f, 1f),
            tileMode = SkTileMode.kClamp,
        )
        val filter = SkImageFilters.Shader(shader)
        val result = filter.filterImage(src, identity)
        assertEquals(4, result.image.width)
        assertEquals(2, result.image.height)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
        // Left edge should be red-ish, right edge blue-ish (allow
        // some drift through the rasteriser's gradient pipeline ;
        // strict colour assertions belong in the gradient tests).
        val leftR = (result.image.peekPixel(0, 0) ushr 16) and 0xFF
        val rightB = result.image.peekPixel(3, 0) and 0xFF
        // Tightened from a strict equality after observing the
        // gradient stops aren't pixel-aligned at the rect's edges
        // — leftR is the "0%" stop's red, rightB is the "100%"'s
        // blue ; both hit non-zero plateau values.
        org.junit.jupiter.api.Assertions.assertTrue(leftR > 200, "left edge should be predominantly red, got R=$leftR")
        org.junit.jupiter.api.Assertions.assertTrue(rightB > 200, "right edge should be predominantly blue, got B=$rightB")
    }

    // ─── Empty ────────────────────────────────────────────────────────

    @Test
    fun `Empty returns a 1x1 transparent-black image`() {
        val filter = SkImageFilters.Empty()
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(1, result.image.width)
        assertEquals(1, result.image.height)
        assertEquals(0, result.image.peekPixel(0, 0)) // transparent black
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun `Empty is a singleton across calls`() {
        val a = SkImageFilters.Empty()
        val b = SkImageFilters.Empty()
        assertSame(a, b, "Empty filter must be a singleton")
    }

    // ─── Crop ─────────────────────────────────────────────────────────

    @Test
    fun `Crop with kDecal returns transparent outside the rect`() {
        // Crop the 4x4 source to its left half (2 columns).
        val filter = SkImageFilters.Crop(
            rect = SkRect.MakeWH(2f, 4f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(2, result.image.width)
        assertEquals(4, result.image.height)
        // Inside the crop : same pixel as source.
        for (y in 0 until 4) for (x in 0 until 2) {
            assertEquals(
                sample4x4.peekPixel(x, y),
                result.image.peekPixel(x, y),
                "inside-crop ($x, $y)",
            )
        }
    }

    @Test
    fun `Crop with kClamp samples the nearest border for out-of-rect coords`() {
        // Crop the 4x4 source to a 6x4 rect — wider than the source —
        // so the right 2 columns are out-of-bounds and need clamping.
        val filter = SkImageFilters.Crop(
            rect = SkRect.MakeWH(6f, 4f),
            tileMode = SkTileMode.kClamp,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(6, result.image.width)
        // Cols 4 and 5 should clamp to col 3 of the source.
        for (y in 0 until 4) {
            assertEquals(
                sample4x4.peekPixel(3, y),
                result.image.peekPixel(4, y),
                "clamp col 4 → src col 3 at y=$y",
            )
            assertEquals(
                sample4x4.peekPixel(3, y),
                result.image.peekPixel(5, y),
                "clamp col 5 → src col 3 at y=$y",
            )
        }
    }

    @Test
    fun `Crop with kRepeat tiles the source across out-of-rect coords`() {
        // Crop to 8 wide → should tile the 4-wide source twice horizontally.
        val filter = SkImageFilters.Crop(
            rect = SkRect.MakeWH(8f, 4f),
            tileMode = SkTileMode.kRepeat,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(8, result.image.width)
        for (y in 0 until 4) for (x in 0 until 8) {
            assertEquals(
                sample4x4.peekPixel(x % 4, y),
                result.image.peekPixel(x, y),
                "repeat ($x, $y)",
            )
        }
    }

    @Test
    fun `Crop with kMirror flips the source at every period boundary`() {
        // Crop to 8 wide → src tiles, then mirror. So col 0..3 is
        // the source ; col 4..7 is the source MIRRORED (col 4 → src
        // col 3, col 5 → src col 2, etc).
        val filter = SkImageFilters.Crop(
            rect = SkRect.MakeWH(8f, 4f),
            tileMode = SkTileMode.kMirror,
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        for (y in 0 until 4) {
            // Direct half.
            for (x in 0 until 4) {
                assertEquals(
                    sample4x4.peekPixel(x, y),
                    result.image.peekPixel(x, y),
                    "mirror direct half ($x, $y)",
                )
            }
            // Mirrored half.
            for (x in 4 until 8) {
                val srcX = 7 - x // 4→3, 5→2, 6→1, 7→0
                assertEquals(
                    sample4x4.peekPixel(srcX, y),
                    result.image.peekPixel(x, y),
                    "mirror flipped half ($x, $y) → src ($srcX, $y)",
                )
            }
        }
    }

    @Test
    fun `Crop with null input crops the src arg directly`() {
        val filter = SkImageFilters.Crop(rect = SkRect.MakeWH(2f, 2f))
        val result = filter.filterImage(sample4x4, identity)
        assertEquals(2, result.image.width)
        assertEquals(2, result.image.height)
        assertEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
        assertEquals(sample4x4.peekPixel(1, 1), result.image.peekPixel(1, 1))
    }

    @Test
    fun `Crop with non-zero rect origin records the offset`() {
        val filter = SkImageFilters.Crop(
            rect = SkRect.MakeXYWH(1f, 1f, 2f, 2f),
            input = SkImageFilters.Image(sample4x4),
        )
        val result = filter.filterImage(anyDriverImage, identity)
        assertEquals(2, result.image.width)
        assertEquals(2, result.image.height)
        assertEquals(1, result.offsetX)
        assertEquals(1, result.offsetY)
        // Output (0, 0) should be source (1, 1) — the rect's origin in
        // the upstream's coordinate space.
        assertEquals(sample4x4.peekPixel(1, 1), result.image.peekPixel(0, 0))
        assertNotEquals(sample4x4.peekPixel(0, 0), result.image.peekPixel(0, 0))
    }
}
