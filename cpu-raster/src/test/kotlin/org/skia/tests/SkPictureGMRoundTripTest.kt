package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.core.SkSurface
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.testing.TestUtils

/**
 * End-to-end integration tests that exercise the full
 * record-once-replay-many pipeline against real GM ports — the exact
 * pattern Skia DM uses to drive its sink architecture.
 *
 * Each test :
 *  1. Runs the GM directly into an F16 [SkBitmap] (the "ground-truth"
 *     render the rest of the test suite already validates against the
 *     upstream reference PNGs).
 *  2. Records the same GM into an [org.skia.core.SkPicture] via
 *     [SkPictureRecorder].
 *  3. Plays the picture back into a fresh [SkSurface] of the same
 *     dimensions and snapshots an [SkImage].
 *  4. Asserts the picture-snapshot is bit-identical to the direct
 *     render.
 *
 * If this passes for a GM, that GM is *DM-ready* — DM can record it
 * once and play it into any compatible raster sink.
 *
 * GMs picked here are intentionally small + diverse :
 *  - [SimpleRectGM] — 1 op, smoke test.
 *  - [ConvexPathsGM] — many fill paths, AA + non-AA.
 *  - [BeziersGM] — strokes + cubics, exercises stroker.
 */
class SkPictureGMRoundTripTest {

    /**
     * Render `gm` directly + via record/playback into matching F16
     * surfaces, asserting pixel-identical output.
     */
    private fun assertGMRoundTrips(gm: GM) {
        val size = gm.size()
        val info = SkImageInfo.Make(
            size.width, size.height,
            SkColorType.kRGBA_F16Norm,
            org.skia.foundation.SkAlphaType.kPremul,
            TestUtils.DM_REFERENCE_COLOR_SPACE,
        )

        // -- Direct render -----------------------------------------------
        val direct = TestUtils.runGmTest(gm)

        // -- Record ------------------------------------------------------
        val recorder = SkPictureRecorder()
        val recCanvas = recorder.beginRecording(size.width.toFloat(), size.height.toFloat())
        gm.draw(recCanvas)
        val picture = recorder.finishRecordingAsPicture()
        assertTrue(picture.opCount > 0, "${gm.name()} should record at least one op")

        // -- Playback into a fresh surface -------------------------------
        // Use MakeRasterDirect over an explicitly bg-filled bitmap so the
        // playback target matches `runGmTest`'s setup byte-for-byte.
        val replayBitmap = SkBitmap(
            size.width, size.height,
            TestUtils.DM_REFERENCE_COLOR_SPACE,
            SkColorType.kRGBA_F16Norm,
        ).also { it.eraseColor(gm.bgColor()) }
        val surface = SkSurface.MakeRasterDirect(replayBitmap)
        picture.playback(surface.canvas)
        val snapshot = surface.makeImageSnapshot()

        // -- Compare -----------------------------------------------------
        assertImagesEqual(direct, replayBitmap, snapshot, gm.name())
    }

    /**
     * Compare three buffers : [directBitmap] (direct render),
     * [replayedBitmap] (the live surface backing) and [snapshot] (the
     * immutable snapshot taken from the surface). All three must agree
     * pixel-by-pixel — if the snapshot diverges from the live bitmap,
     * the snapshot's copy contract is broken; if the replayed bitmap
     * diverges from the direct one, recording or playback is dropping
     * / mis-recording an op.
     */
    private fun assertImagesEqual(
        directBitmap: SkBitmap,
        replayedBitmap: SkBitmap,
        snapshot: SkImage,
        tag: String,
    ) {
        assertEquals(directBitmap.width, replayedBitmap.width, "$tag width")
        assertEquals(directBitmap.height, replayedBitmap.height, "$tag height")

        // The bitmaps are F16; compare the float backing arrays directly.
        // F16 produces deterministic float output for deterministic ops, so
        // bit-identical comparison is the right call here.
        val a = directBitmap.pixelsF16
        val b = replayedBitmap.pixelsF16
        assertEquals(a.size, b.size, "$tag F16 buffer length")
        var firstDiff = -1
        for (i in a.indices) {
            if (a[i] != b[i]) { firstDiff = i; break }
        }
        assertEquals(
            -1, firstDiff,
            "$tag: F16 pixel ${firstDiff / 4} channel ${firstDiff % 4} diverged " +
                "(direct=${a.getOrNull(firstDiff)}, replay=${b.getOrNull(firstDiff)})",
        )

        // Snapshot must agree with the live surface contents at snapshot time.
        for (y in 0 until directBitmap.height) {
            for (x in 0 until directBitmap.width) {
                assertEquals(
                    directBitmap.getPixel(x, y),
                    snapshot.peekPixel(x, y),
                    "$tag snapshot ($x,$y)",
                )
            }
        }
    }

    @Test
    fun `SimpleRectGM round-trips through Picture and Surface pixel-identical`() {
        assertGMRoundTrips(SimpleRectGM())
    }

    @Test
    fun `ConvexPathsGM round-trips through Picture and Surface pixel-identical`() {
        assertGMRoundTrips(ConvexPathsGM())
    }

    @Test
    fun `BeziersGM round-trips through Picture and Surface pixel-identical`() {
        assertGMRoundTrips(BeziersGM())
    }
}
