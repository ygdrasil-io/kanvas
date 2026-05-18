package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ShallowAnglePathArcToGM

/**
 * Cross-test : `ShallowAnglePathArcToGM` on the GPU backend.
 *
 * Port of upstream `gm/patharcto.cpp::shallow_angle_path_arcto` (regression
 * for crbug.com/982968). A curvy-triangle path whose three corners are
 * extremely shallow-angle tangent arcs at huge radii (up to ~697 000 px),
 * built via `SkPathBuilder.arcTo(p1, p2, radius)` and stroked under a
 * negative translate(-200, -50).
 *
 * Not strictly `drawArc`, but exercises the same arc-emitter machinery
 * (the inner `cosh / sinh / d` math kept in `Double` to survive the
 * extreme-radius precision drop). 300×300, paint stroke, AA off.
 */
class ShallowAnglePathArcToWebGpuTest {

    @Test
    fun `ShallowAnglePathArcToGM renders close to reference PNG on the GPU backend`() {
        // 300 × 300 ; observed 99.88 % on Apple M2 Max. Confirms
        // the `Double`-precision arc emitter survives the
        // extreme-radius case end-to-end through the GPU stroker.
        runGpuCrossTest(ShallowAnglePathArcToGM(), floor = 99.8)
    }
}
