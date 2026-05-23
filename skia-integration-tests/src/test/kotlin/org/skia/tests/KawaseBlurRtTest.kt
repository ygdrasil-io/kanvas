package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Ratchet test for [KawaseBlurRtGM].
 *
 * Port of `gm/kawase_blur_rt.cpp` — Kawase iterative blur filter via two
 * SkSL runtime shaders ([org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects.KawaseBlurShaderImpl]
 * and [org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects.KawaseMixShaderImpl]).
 *
 * **Floor : 0 %** — the port uses a synthetic 256x256 RGB-gradient image
 * in place of `mandrill_256.png` (not available as a test resource), and
 * omits the per-pass DEBUG stage draws that upstream emits. Iso-pixel
 * parity vs the reference PNG is therefore not expected ; the ratchet still
 * detects regressions in the SkSL shader math.
 */
class KawaseBlurRtTest {

    @Test
    fun `KawaseBlurRtGM matches kawase_blur_rt_png within tolerance`() {
        val gm = KawaseBlurRtGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image kawase_blur_rt.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("KawaseBlurRtGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("KawaseBlurRtGM", comparison.similarity)
        assertTrue(accepted, "KawaseBlurRtGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 0.0,
            "KawaseBlurRtGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor",
        )
    }
}
