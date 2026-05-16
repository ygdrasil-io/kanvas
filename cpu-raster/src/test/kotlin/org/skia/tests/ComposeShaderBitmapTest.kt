package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComposeShaderBitmapTest {

    @Test
    fun `ComposeShaderBitmapGM matches composeshader_bitmap_png within tolerance`() {
        runOne(useLm = false)
    }

    @Test
    fun `ComposeShaderBitmapLmGM matches composeshader_bitmap_lm_png within tolerance`() {
        runOne(useLm = true)
    }

    private fun runOne(useLm: Boolean) {
        val gm = ComposeShaderBitmapGM(useLm)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        // 7×2 grid of 20×20 squares, blend(DstOver, gradient, bitmap).
        // Tolerance 16 — gradient + bitmap composition exercises the
        // float-premul kernel, byte quantisation introduces ~ulp drift
        // per channel that piles up on the alpha-modulated rows.
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
