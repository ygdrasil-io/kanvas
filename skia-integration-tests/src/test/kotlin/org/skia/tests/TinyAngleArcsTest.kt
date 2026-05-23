package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TinyAngleArcsTest {
    @Test
    fun `TinyAngleArcsGM matches tinyanglearcs_png within tolerance`() {
        val gm = TinyAngleArcsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tinyanglearcs.png")
        // Two annular wedges built from huge-radius (1e5) + tiny-sweep
        // (1e-4 rad) arcs. The chromium#640031 repro for arc rasterisation
        // on the degenerate limit — Skia expected to fall back to a
        // chord-line.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TinyAngleArcsGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TinyAngleArcsGM", comparison.similarity)
        assertTrue(accepted, "TinyAngleArcsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "TinyAngleArcsGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
