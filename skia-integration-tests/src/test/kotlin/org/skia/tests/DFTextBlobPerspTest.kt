package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [DFTextBlobPerspGM].
 *
 * The GM now uses the portable raster SDF glyph cache/sampler from
 * `cpu-raster`, so this is no longer parked behind the old raster-DF stub.
 */
class DFTextBlobPerspTest {

    @Test
    fun `DFTextBlobPerspGM matches reference`() {
        val gm = DFTextBlobPerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("DFTextBlobPerspGM", comparison)
        if (comparison.similarity < 1.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DFTextBlobPerspGM", comparison.similarity)
        assertTrue(accepted, "DFTextBlobPerspGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "DFTextBlobPerspGM similarity ${"%.2f".format(comparison.similarity)}% < 1.0% floor",
        )
    }
}
