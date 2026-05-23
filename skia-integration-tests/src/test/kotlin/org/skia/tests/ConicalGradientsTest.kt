package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for the upstream `gm/gradients_2pt_conical.cpp::ConicalGradientsGM`
 * class alias.
 *
 * The 12 upstream `DEF_GM` instantiations of `ConicalGradientsGM`
 * (3 case types × 4 dither/tileMode combos) are each covered by a
 * dedicated sibling GM in the [ConicalGradients2ptVariantGM] family
 * — exercised by [ConicalGradients2ptTest].
 *
 * [ConicalGradientsGM] itself is a `typealias` for the canonical
 * default-registration variant ([ConicalGradients2ptInsideDitherGM])
 * — the C++ class's first `DEF_GM` registers
 * `gradients_2pt_conical_inside` (kInside, dither=true, kClamp).
 *
 * This test smoke-checks that the typealias resolves at runtime and
 * still renders against the shared
 * `gradients_2pt_conical_inside.png` reference.
 */
class ConicalGradientsTest {

    @Test
    fun `gradients_2pt_conical_inside via ConicalGradientsGM alias`() {
        val gm = ConicalGradientsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ConicalGradientsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ConicalGradientsGM", comparison.similarity)
        assertTrue(accepted, "ConicalGradientsGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 82.2,
            "ConicalGradientsGM similarity ${"%.2f".format(comparison.similarity)}% < 82.2% floor",
        )
    }
}
