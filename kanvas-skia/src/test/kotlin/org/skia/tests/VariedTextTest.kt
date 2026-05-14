package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class VariedTextTest {

    @Test
    fun `VariedTextGM clipped no_lcd matches varied_text_clipped_no_lcd_png within tolerance`() {
        val gm = VariedTextGM(fEffectiveClip = true, fLCD = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image varied_text_clipped_no_lcd.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("VariedTextClippedNoLcdGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("VariedTextClippedNoLcdGM", comparison.similarity)
        assertTrue(accepted, "VariedTextClippedNoLcdGM regressed below ratchet")
    }

    @Test
    fun `VariedTextGM ignorable_clip no_lcd matches varied_text_ignorable_clip_no_lcd_png within tolerance`() {
        val gm = VariedTextGM(fEffectiveClip = false, fLCD = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image varied_text_ignorable_clip_no_lcd.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("VariedTextIgnorableClipNoLcdGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("VariedTextIgnorableClipNoLcdGM", comparison.similarity)
        assertTrue(accepted, "VariedTextIgnorableClipNoLcdGM regressed below ratchet")
    }

    @Test
    fun `VariedTextGM clipped lcd matches varied_text_clipped_lcd_png within tolerance`() {
        val gm = VariedTextGM(fEffectiveClip = true, fLCD = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image varied_text_clipped_lcd.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("VariedTextClippedLcdGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("VariedTextClippedLcdGM", comparison.similarity)
        assertTrue(accepted, "VariedTextClippedLcdGM regressed below ratchet")
    }

    @Test
    fun `VariedTextGM ignorable_clip lcd matches varied_text_ignorable_clip_lcd_png within tolerance`() {
        val gm = VariedTextGM(fEffectiveClip = false, fLCD = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image varied_text_ignorable_clip_lcd.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("VariedTextIgnorableClipLcdGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("VariedTextIgnorableClipLcdGM", comparison.similarity)
        assertTrue(accepted, "VariedTextIgnorableClipLcdGM regressed below ratchet")
    }
}
