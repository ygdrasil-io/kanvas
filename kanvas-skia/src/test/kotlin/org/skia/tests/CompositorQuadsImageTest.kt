package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for the R-final.8 [CompositorQuadsImageGM] — exercises the
 * pure-Kotlin [org.skia.gpu.YUVUtils] helper that materialises an
 * RGBA bitmap from JPEG-derived Y/U/V planes (BT.601 4:2:0).
 *
 * Pixel-fidelity vs. the upstream `compositor_quads_image.png` is
 * expected to start very low — the upstream GM is GPU-only and uses
 * the EdgeAA image-set + perspective compositor pipeline neither of
 * which kanvas-skia ships. The test mirrors the AnimatedGifTest
 * pattern : record + ratchet, no hard floor beyond the
 * [SimilarityTracker]'s monotonic guard.
 */
class CompositorQuadsImageTest {

    @Test
    fun `CompositorQuadsImageGM ratchets against compositor_quads_image_png`() {
        val gm = CompositorQuadsImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("CompositorQuadsImageGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CompositorQuadsImageGM", comparison.similarity)
        assertTrue(accepted, "CompositorQuadsImageGM regressed below ratchet")
    }
}
