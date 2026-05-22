package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * O5 batch -- second factory of the upstream `gm/tilemodes.cpp::TilingGM`
 * (`tilemodes_npot`, 880 x 560). Companion to [TilemodesTest] which
 * exercises the POT (32 x 32 texture) variant ; this variant uses a
 * 21 x 21 (non-power-of-two) texture that upstream regenerates per
 * cell as a long-dead-driver workaround. Same 3x3 tile-mode matrix x
 * 2 colour types x 2 filter modes layout.
 */
class TilemodesNpotTest {

    @Test
    fun `TilemodesGM npot matches tilemodes_npot_png within tolerance`() {
        val gm = TilemodesGM(powerOfTwoSize = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemodes_npot.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TilemodesNpotGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TilemodesNpotGM", comparison.similarity)
        assertTrue(accepted, "TilemodesNpotGM regressed below ratchet")
    }
}
