package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.OneBadArcGM

/**
 * Cross-test : `OneBadArcGM` on the GPU backend.
 *
 * Port of upstream `gm/circulararcs.cpp::onebadarc`. Two semi-translucent
 * (alpha=100) red `kStroke_Style` arcs, strokeWidth=15, on a 100×100
 * canvas :
 *  - A hand-built `moveTo + lineTo + conicTo + quadTo + lineTo + close`
 *    path mirroring Skia's `arcTo` decomposition.
 *  - An `SkCanvas.drawArc(rect, 45°, 90°, useCenter = true, paint)`
 *    pie-wedge.
 *
 * Verifies that the canvas-level `drawArc` routing (build an
 * `SkPath` via `SkPathBuilder.arcTo` + close, then delegate to
 * `drawPath`) produces the same outline as the hand-built decomposition
 * on the GPU device. No GPU-side `drawArc` override exists — the
 * `SkCanvas` default implementation routes both arms through
 * `SkWebGpuDevice.drawPath` unmodified.
 */
class OneBadArcWebGpuTest {

    @Test
    fun `OneBadArcGM renders close to reference PNG on the GPU backend`() {
        // 100 × 100 ; observed 98.69 % on Apple M2 Max. Drift dominated
        // by AA stroke edges on the two translucent-red overlapping
        // arcs.
        runGpuCrossTest(OneBadArcGM(), floor = 98.6)
    }
}
