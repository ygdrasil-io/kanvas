package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComposeShaderAlphaTest {

    @Test
    fun `ComposeShaderAlphaGM matches reference`() {
        val gm = ComposeShaderAlphaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ComposeShaderAlphaGM", comparison)
        if (comparison.similarity < 20.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("ComposeShaderAlphaGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 20.0,
            "ComposeShaderAlphaGM similarity ${"%.2f".format(comparison.similarity)}% < 20.0% floor",
        )
    }
}
