package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the R-final.8 [AnimatedImageOrientationGM] family
 * (`flight_animated_image` + `stoplight_animated_image`).
 *
 * Upstream resources (`images/flightAnim.gif`,
 * `images/stoplight_h.webp`) are vendored. If decode is unavailable
 * for a fixture (notably animated-WebP feature gaps), the GM still
 * uses a deterministic checker fallback at the upstream PNG dimensions.
 *
 * The triptych-save side-effect path (which builds a 3-up rendered /
 * reference / diff image, used when similarity < 30 %) is gated by
 * the `KANVAS_SAVE_LOW_SIM` env var here — at the upstream PNG sizes
 * (3216 × 3216 for flight) the triple-buffered raw-RGBA tripleych
 * exceeds the JVM default heap and OOMs. Re-enable locally with
 * `KANVAS_SAVE_LOW_SIM=1 ./gradlew :kanvas-skia:test --tests …` if
 * you need to inspect the diff.
 */
class AnimatedImageOrientationTest {

    private fun runGm(gm: GM, trackerName: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 30.0 && System.getenv("KANVAS_SAVE_LOW_SIM") != null) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
    }

    @Test
    fun `FlightAnimatedImageGM ratchets against flight_animated_image_png`() =
        runGm(FlightAnimatedImageGM(), "FlightAnimatedImageGM")

    @Test
    fun `StoplightAnimatedImageGM ratchets against stoplight_animated_image_png`() =
        runGm(StoplightAnimatedImageGM(), "StoplightAnimatedImageGM")
}
