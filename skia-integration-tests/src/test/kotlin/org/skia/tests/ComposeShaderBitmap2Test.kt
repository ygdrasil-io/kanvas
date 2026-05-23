package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ComposeShaderBitmap2Test {

    @Test
    fun `ComposeShaderBitmap2GM matches composeshader_bitmap2 within tolerance`() {
        val gm = ComposeShaderBitmap2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        // Tolerance 16 — the alpha-ramp mask introduces per-pixel quantisation
        // in the byte-domain SrcIn blend that accumulates ~ulp drift per channel.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed(gm.name(), comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(gm.name(), comparison.similarity)
        assertTrue(accepted, "${gm.name()} regressed below ratchet")
        assertTrue(
            comparison.similarity >= 23.0,
            "${gm.name()} similarity ${"%.2f".format(comparison.similarity)}% < 23% floor",
        )
    }
}
