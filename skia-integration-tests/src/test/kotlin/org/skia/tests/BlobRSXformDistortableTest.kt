package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [BlobRSXformDistortableGM].
 */
class BlobRSXformDistortableTest {
    @Test
    fun `BlobRSXformDistortableGM matches blob_rsxform_distortable_png within tolerance`() {
        val gm = BlobRSXformDistortableGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlobRSXformDistortableGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlobRSXformDistortableGM", comparison.similarity)
        assertTrue(accepted, "BlobRSXformDistortableGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "BlobRSXformDistortableGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
