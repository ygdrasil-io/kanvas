package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Raster smoke-test for [TilemodeDecalGM] (`tilemode_decal`, 720×1100).
 *
 * Disabled by default: `SkTileMode.kDecal` on image shaders and gradient
 * shaders requires a clamp-to-transparent implementation path that is not
 * yet fully wired in `:kanvas-skia`'s raster backend. The GM is registered
 * and the class is in production, but pixel-accurate results are not
 * guaranteed until the decal-shader pass is complete.
 *
 * TODO("STUB.DECAL_SHADER") — track decal image/gradient shader support.
 */
@Disabled("tilemode_decal: decal image/gradient shader not yet implemented — TODO(STUB.DECAL_SHADER)")
class TilemodeDecalTest {

    @Test
    fun `TilemodeDecalGM matches tilemode_decal_png within tolerance`() {
        val gm = TilemodeDecalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemode_decal.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TilemodeDecalGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TilemodeDecalGM", comparison.similarity)
        assertTrue(accepted, "TilemodeDecalGM regressed below ratchet")
    }
}
