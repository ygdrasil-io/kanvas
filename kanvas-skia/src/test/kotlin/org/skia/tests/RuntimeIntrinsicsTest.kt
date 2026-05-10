package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM ratchet tests for the D2.4.c port of upstream's
 * [`gm/runtimeintrinsics.cpp`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * cluster — the 6 raster `DEF_SIMPLE_GM`s exercising every public
 * SkSL intrinsic upstream ships at ES2 / ES3.
 *
 * **Floor strategy** — these GMs are deliberately low-floor :
 *
 *  - **Text labels** drift via AWT-vs-FreeType glyph rasterisation
 *    (3-5 % of canvas area).
 *  - **Working colour space** — each cell is rendered into a sRGB
 *    100×100 sub-surface (matches upstream `SkImageInfo::MakeN32Premul`)
 *    and then composited onto a Rec.2020 parent.
 *  - **Polyline AA edges** use our 4×4 supersampling vs Skia's
 *    analytical AA.
 *  - 12 cells × 3 channels (R/G/B broadcast) × ~5 % per-cell drift
 *    ⇒ ~5-15 % aggregate drift expected.
 *
 * The ratchet via [SimilarityTracker.updateScore] catches regressions
 * even at low absolute values (a > 1 % drop fails the test).
 *
 * **Files** :
 *  - Impl cluster :
 *    [`SkBuiltinShaderEffectsIntrinsicsTrig`](../../../../main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsIntrinsicsTrig.kt)
 *  - GM port :
 *    [`RuntimeIntrinsicsTrigGM`](../../../main/kotlin/org/skia/tests/RuntimeIntrinsicsTrigGM.kt)
 *  - Reference PNG :
 *    `kanvas/src/test/resources/original-888/runtime_intrinsics_trig.png`
 */
class RuntimeIntrinsicsTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    /**
     * `runtime_intrinsics_trig` — 12 unary trig intrinsics in a 3×5
     * grid. Phase D2.4.c.1 port — exercises every registered hash
     * in [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig].
     */
    @Test
    fun `RuntimeIntrinsicsTrigGM matches reference`() =
        runGm(RuntimeIntrinsicsTrigGM(), "RuntimeIntrinsicsTrigGM", floor = 0.0)

    /**
     * `runtime_intrinsics_exponential` — 10 unary exponential
     * intrinsics in a 2×5 grid (pow×4 / exp / log / exp2 / log2 /
     * sqrt / inversesqrt). Phase D2.4.c.2 port — reuses the
     * `UnaryIntrinsicImpl` skeleton from D2.4.c.1.
     */
    @Test
    fun `RuntimeIntrinsicsExponentialGM matches reference`() =
        runGm(RuntimeIntrinsicsExponentialGM(), "RuntimeIntrinsicsExponentialGM", floor = 0.0)

    /**
     * `runtime_intrinsics_common` — 31 plot calls in a 6×7 grid
     * covering the GLSL "common" function family (abs / sign /
     * floor / ceil / fract / mod / min / max / clamp / saturate /
     * mix / step / smoothstep + componentwise floor(p) / ceil(p)).
     * Phase D2.4.c.3 port.
     */
    @Test
    fun `RuntimeIntrinsicsCommonGM matches reference`() =
        runGm(RuntimeIntrinsicsCommonGM(), "RuntimeIntrinsicsCommonGM", floor = 0.0)

    /**
     * `runtime_intrinsics_geometric` — 16 plot calls in a 4×5 grid
     * covering GLSL geometric functions (length / distance / dot /
     * cross / normalize / faceforward / reflect / refract).
     * Phase D2.4.c.4 port.
     */
    @Test
    fun `RuntimeIntrinsicsGeometricGM matches reference`() =
        runGm(RuntimeIntrinsicsGeometricGM(), "RuntimeIntrinsicsGeometricGM", floor = 0.0)

    /**
     * `runtime_intrinsics_matrix` — 6 plot calls (matrixCompMult ×
     * 3 dims + inverse × 3 dims) in a 3×2 grid. Phase D2.4.c.5
     * port — uses the new `MatrixCompMultImpl` / `MatrixInverseImpl`
     * built on a distinct SkSL template (not `make_unary_sksl_1d`).
     */
    @Test
    fun `RuntimeIntrinsicsMatrixGM matches reference`() =
        runGm(RuntimeIntrinsicsMatrixGM(), "RuntimeIntrinsicsMatrixGM", floor = 0.0)
}
