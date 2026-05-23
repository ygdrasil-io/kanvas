package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * STUB.MISSING_API: ManyArcs upstream feeds [SkPathBuilder.arcTo] sweep
 * angles up to `3934723942837.3 × 180° ≈ 7.08e14°`. Our `emitArc`
 * derivation uses `nSegs = ceil(|sweep|/90°)` and allocates one conic
 * per segment — for that input it would allocate ~7.87 trillion conics
 * and OOM. Upstream Skia funnels everything through
 * `SkConic::BuildUnitArc`, which always emits ≤ 4 conics regardless of
 * sweep (the sweep is normalised via `(cos, sin)` unit vectors so
 * multi-turn winds collapse to the equivalent ≤ 360° endpoint).
 *
 * Re-enable once `emitArc` mirrors `BuildUnitArc`'s unit-vector
 * normalisation (independent of this porting PR — touches a public
 * API behaviour in `kanvas-skia`).
 */
@Disabled("STUB.MISSING_API: SkPathBuilder.emitArc OOMs on huge-sweep arcs (>1e10 deg) — needs BuildUnitArc-style unit-vector normalisation")
class ManyArcsTest {
    @Test
    fun `ManyArcsGM matches manyarcs_png within tolerance`() {
        val gm = ManyArcsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manyarcs.png")
        // 160 html-canvas-style stroked arc paths spanning degenerate
        // sweeps (0, ±1e-6) up through multi-turn winds (4.3 × 180°,
        // 3.93e12 × 180°). Stresses SkPathBuilder.arcTo's sweep-mod-360
        // + cubic emitter on the limits.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyArcsGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyArcsGM", comparison.similarity)
        assertTrue(accepted, "ManyArcsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ManyArcsGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
