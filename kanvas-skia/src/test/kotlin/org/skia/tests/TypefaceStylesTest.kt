package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test driver for [TypefaceStylesGM] (no-kerning variant — `typefacestyles.png`).
 *
 * Tolerance + floor follow the textual GM convention
 * (`TestUtils.TEXTUAL_GM_TOLERANCE`); the floor is set well below the
 * empirical similarity to leave ample buffer for AWT-vs-FreeType edge
 * AA drift across JVM versions.
 */
class TypefaceStylesTest {

    @Test
    fun `TypefaceStylesGM matches typefacestyles_png within tolerance`() {
        val gm = TypefaceStylesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image typefacestyles.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("TypefaceStylesGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TypefaceStylesGM", comparison.similarity)
        assertTrue(accepted, "TypefaceStylesGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "TypefaceStylesGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
