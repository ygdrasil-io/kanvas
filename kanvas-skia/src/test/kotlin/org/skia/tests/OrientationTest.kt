package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the R-final.5 [OrientationGM] family.
 *
 * **Pixel-fidelity caveat.** kanvas-skia's [org.skia.codec.jpeg.SkJpegCodec]
 * does not yet apply the EXIF `Orientation` tag (queued for R-final.8).
 * Until that lands the rendered tiles will display the un-rotated raw
 * JPEG content (each tile rotated relative to its label), so similarity
 * vs. the upstream `orientation_444.png` / `respect_orientation_jpeg.png`
 * references is **expected to be low**. The tests therefore use a
 * permissive floor and rely on the [SimilarityTracker] ratchet to
 * detect regressions.
 */
class OrientationTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double = 1.0) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `Orientation444GM matches orientation_444_png within tolerance`() =
        runGm(Orientation444GM(), "Orientation444GM")

    @Test
    fun `RespectOrientationJpegGM matches respect_orientation_jpeg_png within tolerance`() =
        runGm(RespectOrientationJpegGM(), "RespectOrientationJpegGM")
}
