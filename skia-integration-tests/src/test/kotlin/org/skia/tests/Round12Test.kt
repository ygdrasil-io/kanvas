package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round12Test {

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
    fun `DistantClipGM matches reference`() = runGm(DistantClipGM(), "DistantClipGM", 80.0)

    @Test
    fun `BlurQuickRejectGM matches reference`() =
        runGm(BlurQuickRejectGM(), "BlurQuickRejectGM", 80.0)

    @Test
    fun `Crbug899512GM matches reference`() = runGm(Crbug899512GM(), "Crbug899512GM", 80.0)

    @Test
    fun `MirrorTileGM matches reference`() = runGm(MirrorTileGM(), "MirrorTileGM", 60.0)
}
