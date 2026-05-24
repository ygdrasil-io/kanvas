package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipShaderPerspTest {

    @Test
    fun `ClipShaderPerspGM matches clip_shader_persp_png within tolerance`() {
        val gm = ClipShaderPerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clip_shader_persp.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ClipShaderPerspGM", comparison)
        if (comparison.similarity < 10.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipShaderPerspGM", comparison.similarity)
        assertTrue(accepted, "ClipShaderPerspGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 10.0,
            "ClipShaderPerspGM similarity ${"%.2f".format(comparison.similarity)}% < 10.0% floor",
        )
    }
}
