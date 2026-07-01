package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Combined test class for the sweep-/perlin-shader batch. Pattern
 * follows [org.skia.tests.Round8Test]: run GM → load reference →
 * compare → record → ratchet via [SimilarityTracker] → assert above
 * a per-test floor.
 */
class ShadersBatch1Test {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `sweep_tiling matches reference`() = runGm(SweepTilingGM(), "SweepTilingGM", 40.0)

    @Test
    fun `perlinnoise matches reference`() = runGm(PerlinNoiseGM(), "PerlinNoiseGM", 30.0)

    @Test
    fun `perlinnoise_rotated matches reference`() =
        runGm(PerlinNoiseRotatedGM(), "PerlinNoiseRotatedGM", 30.0)
}
