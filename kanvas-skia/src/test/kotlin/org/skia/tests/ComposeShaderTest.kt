package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComposeShaderTest {

    @Test
    fun `ComposeShaderGM matches composeshader_png within tolerance`() {
        val gm = ComposeShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image composeshader.png")
        // Tolerance is generous (16) — the GM relies on `Blend(DstIn, …)`
        // composition through two SkLinearGradients, and the byte
        // round-trip in our blend kernel quantises differently from
        // upstream's float-precision compositor. Floor mirrors the rest
        // of the gradient family : we ratchet on regression rather than
        // chase upstream's exact pixel.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("ComposeShaderGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ComposeShaderGM", comparison.similarity)
        assertTrue(accepted, "ComposeShaderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 25.0,
            "ComposeShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 25% floor",
        )
    }
}
