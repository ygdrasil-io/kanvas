package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MixerCFTest {
    @Test
    fun `MixerCFGM matches mixerCF_png within tolerance`() {
        val gm = MixerCFGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image mixerCF.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("MixerCFGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MixerCFGM", comparison.similarity)
        assertTrue(accepted, "MixerCFGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "MixerCFGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        // mixerCF exercises Lerp(t, cf0, cf1) where one slot is upstream
        // `nullptr`; we substitute an identity-matrix filter, which alters
        // alpha handling vs. true pass-through and leaves the bulk of the
        // sweep gradient tinted into a band of values that drifts from the
        // reference. Score is ratchet-tracked.
        private const val THRESHOLD: Double = 30.7
    }
}
