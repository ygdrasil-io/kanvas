package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TextBlobInterceptsTest {

    @Test
    fun `TextBlobInterceptsGM matches textblob_intercepts_png within tolerance`() {
        val gm = TextBlobInterceptsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image textblob_intercepts.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TextBlobInterceptsGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TextBlobInterceptsGM", comparison.similarity)
        assertTrue(accepted, "TextBlobInterceptsGM regressed below ratchet")
    }
}
