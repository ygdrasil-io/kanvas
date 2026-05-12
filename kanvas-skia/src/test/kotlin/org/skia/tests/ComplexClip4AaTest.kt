package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComplexClip4AaTest {
    @Test
    fun `ComplexClip4 aa variant matches reference`() {
        val gm = ComplexClip4AaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ComplexClip4AaGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ComplexClip4AaGM", comparison.similarity)
        assertTrue(accepted, "ComplexClip4AaGM regressed below ratchet")
    }
}
