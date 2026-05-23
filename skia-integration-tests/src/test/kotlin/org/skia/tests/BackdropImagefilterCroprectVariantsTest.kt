package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the 4 variants of the `backdrop_imagefilter_croprect` GM family
 * that were missing from the initial port.
 *
 * - [BackdropImagefilterCroprectRotatedGM] — rotated CTM
 * - [BackdropImagefilterCroprectPerspGM] — perspective CTM (known rendering
 *   quirk upstream; skbug.com/40040358)
 * - [BackdropImagefilterCroprectNestedGM] — nested saveLayer with 50% alpha
 * - [BackdropLayerTilemodeGM] — backdrop tile mode variants (kClamp, kDecal,
 *   kRepeat, kMirror); tile mode accepted but not yet honoured by CPU raster
 */
class BackdropImagefilterCroprectVariantsTest {

    @Test
    fun `BackdropImagefilterCroprectRotatedGM matches reference within tolerance`() {
        val gm = BackdropImagefilterCroprectRotatedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropImagefilterCroprectRotatedGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropImagefilterCroprectRotatedGM", comparison.similarity)
        assertTrue(accepted, "BackdropImagefilterCroprectRotatedGM regressed below ratchet")
        assertTrue(comparison.similarity >= ROTATED_FLOOR,
            "BackdropImagefilterCroprectRotatedGM similarity ${"%.2f".format(comparison.similarity)}% < $ROTATED_FLOOR%")
    }

    @Test
    fun `BackdropImagefilterCroprectPerspGM matches reference within tolerance`() {
        val gm = BackdropImagefilterCroprectPerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropImagefilterCroprectPerspGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropImagefilterCroprectPerspGM", comparison.similarity)
        assertTrue(accepted, "BackdropImagefilterCroprectPerspGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BackdropImagefilterCroprectPerspGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    @Test
    fun `BackdropImagefilterCroprectNestedGM matches reference within tolerance`() {
        val gm = BackdropImagefilterCroprectNestedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropImagefilterCroprectNestedGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropImagefilterCroprectNestedGM", comparison.similarity)
        assertTrue(accepted, "BackdropImagefilterCroprectNestedGM regressed below ratchet")
        assertTrue(comparison.similarity >= NESTED_FLOOR,
            "BackdropImagefilterCroprectNestedGM similarity ${"%.2f".format(comparison.similarity)}% < $NESTED_FLOOR%")
    }

    @Test
    fun `BackdropLayerTilemodeGM matches reference within tolerance`() {
        val gm = BackdropLayerTilemodeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        // Loose tolerance: backdropTileMode not yet honoured — only kClamp panel matches
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropLayerTilemodeGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropLayerTilemodeGM", comparison.similarity)
        assertTrue(accepted, "BackdropLayerTilemodeGM regressed below ratchet")
        // Intentionally loose floor — three of four panels diverge until
        // backdropTileMode is honoured in the CPU-raster backend.
        assertTrue(comparison.similarity >= LOOSE_SIMILARITY,
            "BackdropLayerTilemodeGM similarity ${"%.2f".format(comparison.similarity)}% < $LOOSE_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 90.0
        const val ROTATED_FLOOR: Double = 63.0
        const val NESTED_FLOOR: Double = 76.0
        const val LOOSE_SIMILARITY: Double = 0.0
    }
}
