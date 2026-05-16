package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase G5 GM port — see [Crbug1174186GM] for source-spec mapping.
 *
 * The 150 thin-quad iterations almost never deposit pixels (the AA-off
 * shortcut makes the quads invisible by design) — so the canvas is
 * dominated by the white BG and reference / port should match with very
 * high similarity.
 */
class Crbug1174186Test {

    @Test
    fun `Crbug1174186GM matches crbug_1174186_png within tolerance`() {
        val gm = Crbug1174186GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1174186.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Crbug1174186GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1174186GM", comparison.similarity)
        assertTrue(accepted, "Crbug1174186GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "Crbug1174186GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
