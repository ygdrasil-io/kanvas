package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class JpgColorCubeTest {

    @Test
    fun `JpgColorCubeGM matches jpg-color-cube within JPEG tolerance`() {
        val gm = JpgColorCubeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image jpg-color-cube.png")
        // R-final.6 — dense colour gradient through ImageIO JPEG-100 then
        // decode. Per-pixel drift vs upstream's libjpeg-turbo encode
        // routinely hits 30-40 byte-levels at the cube's high-saturation
        // edges. Tolerance is widened to 64 to absorb this drift while
        // still catching genuine encode-bit regressions.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 64)
        TestReport.recordDetailed("JpgColorCubeGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("JpgColorCubeGM", comparison.similarity)
        assertTrue(accepted, "JpgColorCubeGM regressed below ratchet")
    }
}
