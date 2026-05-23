package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Disabled placeholder for `gm/pathmeasure.cpp::PathMeasure_explosion`.
 *
 * The upstream GM is guarded by `#if 0` and deliberately never compiled
 * into the Skia test suite.  It is an intentional UB / extreme-RAM stress
 * test for skbug.com/40038934 (signed integer overflow in SkTDArray when
 * a path approaches INT_MAX points).  There is no reference image and the
 * test cannot be run in a normal CI environment.
 *
 * See [PathMeasureExplosionGM] for the full rationale.
 */
@Disabled(
    "STUB.PATH_MEASURE_EXPLOSION: upstream GM unconditionally disabled via #if 0 — " +
        "intentional INT_MAX path stress-test that triggers UB / requires multi-GB heap; " +
        "unportable by design (skbug.com/40038934)"
)
class PathMeasureExplosionTest {

    @Test
    fun `PathMeasureExplosionGM is disabled upstream — INT_MAX path stress-test triggers UB`() {
        val gm = PathMeasureExplosionGM()
        gm.draw(null) // would throw NotImplementedError — guarded by @Disabled
    }
}
