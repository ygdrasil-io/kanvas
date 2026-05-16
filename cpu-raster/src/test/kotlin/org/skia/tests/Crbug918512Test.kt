package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug918512Test {

    @Test
    fun `Crbug918512GM matches crbug_918512_png within tolerance`() {
        val gm = Crbug918512GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_918512.png")
        // Two nested saveLayers: inner one carries a kDstIn blend + luma
        // colour filter. Phase G7 wired `compositeFrom` to apply
        // `paint.colorFilter` at layer-restore time, which is what makes
        // the inner layer's grey rectangle correctly mask the outer cyan
        // layer through the luma filter. Without that wiring the result
        // is uniformly cyan or fully yellow depending on which side of
        // the broken pipeline you hit.
        // Tolerance 32 absorbs the wide-gamut Rec.2020 encoding drift on
        // the half-cyan / half-yellow output (max per-channel diff ~31
        // in current runs; structural correctness is what we're after).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 32)
        TestReport.recordDetailed("Crbug918512GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug918512GM", comparison.similarity)
        assertTrue(accepted, "Crbug918512GM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "Crbug918512GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=32 floor)")
    }
}
