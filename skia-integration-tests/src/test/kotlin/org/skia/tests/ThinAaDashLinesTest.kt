package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ThinAaDashLinesTest {
    @Test
    fun `ThinAaDashLinesGM matches thin_aa_dash_lines_png within tolerance`() {
        val gm = ThinAaDashLinesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ThinAaDashLinesGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ThinAaDashLinesGM", comparison.similarity)
        assertTrue(accepted, "ThinAaDashLinesGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "ThinAaDashLinesGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
