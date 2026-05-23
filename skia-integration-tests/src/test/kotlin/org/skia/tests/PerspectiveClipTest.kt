package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [PerspectiveClipGM] (`perspective_clip`, 800 × 800).
 *
 * Draws a deterministic random path twice: once with a flat grey fill, then
 * under a crazy perspective matrix with a `mandrill_128.png` image shader.
 * The second draw exercises half-plane clipping where part of the geometry
 * is "behind" the viewer.
 */
class PerspectiveClipTest {

    @Test
    fun `PerspectiveClipGM matches perspective_clip_png within tolerance`() {
        val gm = PerspectiveClipGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("PerspectiveClipGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("PerspectiveClipGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("PerspectiveClipGM", comparison.similarity)
        assertTrue(accepted, "PerspectiveClipGM regressed below ratchet")
    }
}
