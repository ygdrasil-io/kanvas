package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AlternateLumaTest {

    @Test
    fun `AlternateLumaGM matches reference`() {
        val gm = AlternateLumaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AlternateLumaGM", comparison)
        if (comparison.similarity < 4.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AlternateLumaGM", comparison.similarity)
        assertTrue(accepted, "AlternateLumaGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 4.0,
            "AlternateLumaGM similarity ${"%.2f".format(comparison.similarity)}% < 4.0% floor",
        )
    }
}
