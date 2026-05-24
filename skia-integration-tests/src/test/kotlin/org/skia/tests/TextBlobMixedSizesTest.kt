package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [TextBlobMixedSizesGM] (non-DFT variant,
 * name `textblobmixedsizes`).
 *
 * The GM renders a multi-size text blob (sizes 262 / 162 / 72 / 32 / 14 / 0)
 * four times with random rotations and blur-shadow passes. This exercises
 * [org.skia.foundation.SkTextBlobBuilder], [org.skia.foundation.SkMaskFilter]
 * (Gaussian blur), and [org.skia.tools.SkRandom].
 *
 * **Font resource gap** : upstream uses `fonts/HangingS.ttf`; our resource
 * root does not ship it, so the GM transparently falls back to
 * [org.skia.tools.ToolUtils.DefaultPortableTypeface]. The rendered glyph
 * shapes will differ from the upstream reference (different font), so the
 * similarity ratchet guards against regressions in our own baseline rather
 * than comparing pixel-for-pixel with the Skia reference.
 */
class TextBlobMixedSizesTest {

    @Test
    fun `TextBlobMixedSizesGM matches reference`() {
        val gm = TextBlobMixedSizesGM(useDFT = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TextBlobMixedSizesGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("TextBlobMixedSizesGM", comparison.similarity)
        assertTrue(accepted, "TextBlobMixedSizesGM regressed below ratchet")
    }
}
