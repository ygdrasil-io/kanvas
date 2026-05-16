package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Sk3dSimpleTest {

    @Test
    fun `Sk3dSimpleGM matches sk3d_simple_png within tolerance`() {
        val gm = Sk3dSimpleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image sk3d_simple.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Sk3dSimpleGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Sk3dSimpleGM", comparison.similarity)
        assertTrue(accepted, "Sk3dSimpleGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 5.0,
            "Sk3dSimpleGM similarity ${"%.2f".format(comparison.similarity)}% < 5.0% floor",
        )
    }
}
