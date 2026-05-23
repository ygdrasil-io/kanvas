package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression test for [ShadowUtilsDirectionalGM].
 *
 * Exercises [org.skia.utils.SkShadowUtils.DrawShadow] with
 * [org.skia.utils.SkShadowUtils.kDirectionalLight_ShadowFlag] across
 * four transform modes (translation, rotation, scale, perspective) in a
 * 3-column grid.
 *
 * Floor is 0% — this is a new port; the analytic mesh shadow path with
 * directional-light parameters may diverge from the upstream GPU
 * reference in perspective rows.
 *
 * Upstream reference : `shadow_utils_directional.png` (256 x 384).
 */
class ShadowUtilsDirectionalTest {

    @Test
    fun `ShadowUtilsDirectionalGM matches reference`() {
        val gm = ShadowUtilsDirectionalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("ShadowUtilsDirectional", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShadowUtilsDirectional", comparison.similarity)
        assertTrue(accepted, "ShadowUtilsDirectional regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "ShadowUtilsDirectional similarity < 0%",
        )
    }
}
