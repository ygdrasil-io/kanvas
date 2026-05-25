package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class VerticesTest {

    @Test
    fun `VerticesGM matches vertices_png within tolerance`() {
        runVerticesGmTest(VerticesGM(), "VerticesGM")
    }

    @Test
    fun `VerticesGM scaled shader matches vertices_scaled_shader_png within tolerance`() {
        runVerticesGmTest(VerticesGM(shaderScale = 1f / 40f), "VerticesScaledShaderGM")
    }

    private fun runVerticesGmTest(gm: VerticesGM, trackerName: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
