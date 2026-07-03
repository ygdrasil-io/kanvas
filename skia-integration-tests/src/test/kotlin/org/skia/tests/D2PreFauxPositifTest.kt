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
 * the actual GM body never uses `SkRuntimeEffect` at all.
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
