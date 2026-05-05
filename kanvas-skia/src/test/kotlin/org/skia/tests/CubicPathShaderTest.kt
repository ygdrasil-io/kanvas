package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CubicPathShaderTest {

    @Test
    fun `CubicPathShaderGM matches cubicpath_shader_png within tolerance`() {
        val gm = CubicPathShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image cubicpath_shader.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CubicPathShaderGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CubicPathShaderGM", comparison.similarity)
        assertTrue(accepted, "CubicPathShaderGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 78.0,
            "CubicPathShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 78.0% floor",
        )
    }
}
