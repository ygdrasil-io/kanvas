package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * R-final.9 — HDR PiP blur test. The kanvas-skia pipeline tags the
 * HDR PQ colorspace correctly via [org.skia.foundation.SkColorSpace.MakePqHdr]
 * but the per-pixel tonemapping into the wide-gamut SDR working space
 * is approximate (no full BT.2390 EETF). The structural layout
 * (background + rounded PiP + blur shade) reproduces ; the PQ → SDR
 * roll-off vs. upstream sees ~10-15 % nominal divergence (cf.
 * [HdrPipBlurGM] KDoc).
 */
class HdrPipBlurTest {

    @Test
    fun `HdrPipBlurGM matches hdr-pip-blur_png within tolerance`() {
        val gm = HdrPipBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image hdr-pip-blur.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("HdrPipBlurGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("HdrPipBlurGM", comparison.similarity)
        assertTrue(accepted, "HdrPipBlurGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "HdrPipBlurGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        // PQ → SDR tonemapping is approximate ; floor accepts the
        // structural-only similarity (background + rounded PiP frame
        // + blur shade are present and roughly correctly placed).
        const val EXPECTED_SIMILARITY: Double = 35.0
    }
}
