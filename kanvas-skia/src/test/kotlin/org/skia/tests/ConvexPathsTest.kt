package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ConvexPathsTest {

    @Test
    fun `ConvexPathsGM matches convexpaths_png within tolerance`() {
        val gm = ConvexPathsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image convexpaths.png")
        // Heaviest path GM yet: 35+ paths spanning every verb (line, quad,
        // conic, cubic, arc) plus all factories (Rect/Circle/Oval/RRect/
        // Line/Polygon) and a 4096-point polyline. AA fill via 4×4 SS scanline.
        // Random colours come from a bit-compatible SkRandom so the per-cell
        // hue matches the reference.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ConvexPathsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ConvexPathsGM", comparison.similarity)
        assertTrue(accepted, "ConvexPathsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "ConvexPathsGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
