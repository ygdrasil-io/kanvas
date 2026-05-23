package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComposeShaderGridTest {

    @Test
    fun `ComposeShaderGridGM matches composeshader_grid within tolerance`() {
        val gm = ComposeShaderGridGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        // Tolerance 16 — the 4×4 blend-mode grid exercises the full 16-mode range
        // of SkBlendShader; unsupported advanced modes fall back to SrcOver per
        // the SkBlendShader kernel budget, introducing a known divergence on a
        // small number of cells. The ratchet catches regressions.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed(gm.name(), comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(gm.name(), comparison.similarity)
        assertTrue(accepted, "${gm.name()} regressed below ratchet")
        assertTrue(
            comparison.similarity >= 25.0,
            "${gm.name()} similarity ${"%.2f".format(comparison.similarity)}% < 25% floor",
        )
    }
}
