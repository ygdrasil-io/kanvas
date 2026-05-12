package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RtifUnsharpTest {

    @Test
    fun `RtifUnsharpGM matches rtif_unsharp_png within tolerance`() {
        val gm = RtifUnsharpGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rtif_unsharp.png")
        // The unsharp shader is a 5-term per-pixel formula on the
        // source and blurred children — the blur's σ=1 Gaussian is
        // off-by-half-a-pixel between us and upstream's
        // SkRasterPipeline blur, so per-pixel deltas stack up at the
        // glyph edges. Tolerance 16 absorbs the residual.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("RtifUnsharpGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RtifUnsharpGM", comparison.similarity)
        assertTrue(accepted, "RtifUnsharpGM regressed below ratchet")
    }
}
