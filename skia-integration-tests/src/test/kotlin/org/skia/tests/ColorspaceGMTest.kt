package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Integration test for [ColorspaceGM] — the `colorspace` GM from
 * `gm/colorspace.cpp`.
 *
 * **Classification: partial-complete.** The GM renders correctly (7×5 grid
 * of mandrill_128 images converted through intermediate colour spaces via
 * [org.skia.foundation.SkImage.makeColorSpace]), but per-pixel divergence
 * from the upstream reference is expected due to the 8-bit quantization
 * incurred at each double-conversion step (our pipeline always quantises
 * through 8888 between `imgCS → midCS` and `midCS → dstCS`, whereas
 * upstream's GPU path may accumulate in float). Tolerance is set to 8
 * channels to absorb this drift.
 */
class ColorspaceGMTest {

    @Test
    fun `ColorspaceGM matches colorspace_png within tolerance`() {
        val gm = ColorspaceGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorspace.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ColorspaceGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorspaceGM", comparison.similarity)
        assertTrue(accepted, "ColorspaceGM regressed below ratchet")
    }
}
