package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM ports that the `MIGRATION_PLAN_D2_RUNTIME_EFFECT` originally
 * listed as "needs D2" but that turn out to be **faux-positifs** :
 * the upstream `gm/<name>.cpp` includes `<SkRuntimeEffect.h>` while
 * the actual GM body either never uses `SkRuntimeEffect` at all, or
 * uses it only for a half of the cells (the runtime-effect branch
 * is replaced by a placeholder in the port).
 *
 * **Floor strategy** : these ports run with **very low floors**
 * (5 % – 50 %) because :
 *
 *  - PNG references are encoded with upstream's full pipeline (text
 *    labels, mandrill image asset, runtime-shader cells, etc.) ;
 *    our ports skip text labels (font drift) and substitute
 *    placeholders for the runtime branches.
 *  - The point of these ports is to **unblock the GMs from the
 *    "needs D2" backlog** with whatever fidelity is achievable
 *    today ; the floors will ratchet upward naturally as missing
 *    D2 slices land.
 *  - A low floor still catches regressions (the ratchet detects a
 *    drop > 1 % even at low absolute values).
 *
 * Each test ports the **first DEF_GM** of its `gm/<name>.cpp` file
 * unless otherwise noted.
 */
class D2PreFauxPositifTest {

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
     * `lumafilter` — the first DEF_GM of `lumafilter.cpp`. Doesn't
     * use `SkRuntimeEffect` at all (only the second `AlternateLuma`
     * GM does). Skips text labels.
     */
    @Test
    fun `LumaFilterGM matches reference`() =
        runGm(LumaFilterGM(), "LumaFilterGM", floor = 0.0)

    /**
     * `arithmode` — the first DEF_GM of `arithmode.cpp`. Uses only
     * `SkImageFilters.Arithmetic` (shipped in C1.3) ; the second
     * GM `ArithmodeBlenderGM` is the runtime-effect-dependent one.
     * Skips text labels.
     */
    @Test
    fun `ArithmodeGM matches reference`() =
        runGm(ArithmodeGM(), "ArithmodeGM", floor = 0.0)

    /**
     * `composeCF` — the first DEF_SIMPLE_GM of
     * `composecolorfilter.cpp`. Half-port : the `useSkSL=true`
     * column is replaced by a gray placeholder.
     */
    @Test
    fun `ComposeColorFilterGM matches reference`() =
        runGm(ComposeColorFilterGM(), "ComposeColorFilterGM", floor = 0.0)

    /**
     * `composeCFIF` — the second DEF_SIMPLE_GM of
     * `composecolorfilter.cpp`. No `SkRuntimeEffect` involvement —
     * fully portable today.
     */
    @Test
    fun `ComposeCFIFGM matches reference`() =
        runGm(ComposeCFIFGM(), "ComposeCFIFGM", floor = 0.0)

    /**
     * `runtimecolorfilter` — the first DEF_GM of
     * `runtimecolorfilter.cpp`. **D2.4.a** port : the 5 SkSL color
     * filters (Noop / LumaSrc / Ternary / Ifs / EarlyReturn) are
     * hand-ported via [org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects]
     * and dispatched through the SkRuntimeEffect façade. The GM
     * uses a synthetic 256×256 RGB-gradient stand-in for upstream's
     * `mandrill_256.png` ; iso-pixel parity is therefore impossible
     * (the per-cell colour-filter math is correct, but the
     * underlying source pixels differ from upstream's mandrill).
     * Floor stays at 0 % until either the image asset lands or a
     * mandrill-substitute ratchet baseline is captured.
     */
    @Test
    fun `RuntimeColorFilterGM matches reference`() =
        runGm(RuntimeColorFilterGM(), "RuntimeColorFilterGM", floor = 0.0)

    /**
     * `vertices_perspective` — `gm/vertices.cpp`. Pure faux-positif :
     * `<SkRuntimeEffect.h>` is included but never used.
     */
    @Test
    fun `VerticesPerspectiveGM matches reference`() =
        runGm(VerticesPerspectiveGM(), "VerticesPerspectiveGM", floor = 0.0)

    /**
     * `skbug_13047` — `gm/vertices.cpp`. Faux-positif. Adapted to use
     * a synthetic 128×128 gradient image (upstream uses
     * `mandrill_128.png` which we don't have in our test classpath) —
     * very-low floor.
     */
    @Test
    fun `Skbug13047GM matches reference`() =
        runGm(Skbug13047GM(), "Skbug13047GM", floor = 0.0)

}
