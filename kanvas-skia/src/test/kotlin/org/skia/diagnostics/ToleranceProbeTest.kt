package org.skia.diagnostics

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.BigRectGM
import org.skia.tests.ClipStrokeRectGM
import org.skia.tests.SimpleRectGM
import org.skia.tests.ThinRectsGM

/**
 * Phase 5 probe: with the colorspace pipeline now wired, what tolerance
 * budget do BigRect / SimpleRect actually need? Run once, read the numbers
 * from the test report; this informs how aggressively we can ratchet the
 * GM test thresholds.
 */
class ToleranceProbeTest {

    @Test
    fun `BigRectGM tolerance budget`() {
        probe("BigRectGM", BigRectGM(), "bigrect")
    }

    @Test
    fun `SimpleRectGM tolerance budget`() {
        probe("SimpleRectGM", SimpleRectGM(), "simplerect")
    }

    @Test
    fun `ThinRectsGM tolerance budget`() {
        probe("ThinRectsGM", ThinRectsGM(), "thinrects")
    }

    @Test
    fun `ClipStrokeRectGM tolerance budget`() {
        probe("ClipStrokeRectGM", ClipStrokeRectGM(), "clip_strokerect")
    }

    private fun probe(label: String, gm: org.skia.tests.GM, refName: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(refName)
            ?: error("missing reference $refName.png")
        println("=== $label ===")
        for (t in listOf(0, 1, 2, 4, 8, 16, 32, 64, 128, 160)) {
            val sim = TestUtils.compareBitmaps(rendered, reference, tolerance = t)
            println("  tolerance=%-3d similarity=%6.3f%%".format(t, sim))
        }
    }
}
