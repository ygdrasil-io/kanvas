package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PointsGM

/**
 * Cross-test : `PointsGM` on the GPU backend.
 *
 * 99 pseudo-random points rendered four times :
 *  - `kPolygon` red 4-px polyline,
 *  - `kLines` green hairline pairs,
 *  - `kPoints` blue 6-px round-cap stamps,
 *  - `kPoints` white hairline butt-cap dots overlay.
 *
 * Stresses the full point-mode dispatch and confirms `drawPoints`
 * routing through [SkCanvas.drawCircle] / [SkCanvas.drawLine] reaches
 * the WebGPU rasteriser correctly.
 */
class PointsWebGpuTest {

    @Test
    fun `PointsGM renders close to reference PNG on the GPU backend`() {
        // Floor : PointsGM lands at ~99.45 % on the GPU backend
        // (matching the CPU ratchet at 99.44 %), so the floor sits
        // a small margin below to ride out hairline-rounding wobble
        // between runs.
        runGpuCrossTest(PointsGM(), floor = 99.0)
    }
}
