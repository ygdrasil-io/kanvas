package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AddArcTest {
    @Test
    fun `AddArcGM matches addarc_png within tolerance`() {
        val gm = AddArcGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image addarc.png")
        // Concentric 345° stroked arcs with random starts/colours. Heavy
        // stress on SkPathBuilder.addArc + cubic Bézier flattening.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("AddArcGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AddArcGM", comparison.similarity)
        assertTrue(accepted, "AddArcGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "AddArcGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
