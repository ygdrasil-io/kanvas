package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.B119394958GM

/**
 * Cross-test : `B119394958GM` on the GPU backend.
 *
 * Three layered draws : blue filled circle, green stroked circle,
 * red round-cap stroked arc on `(30, 30, 70, 70)` sweeping 110 deg.
 * First GM in scope to combine `drawArc(useCenter = false)` with
 * `kRound_Cap` -- the round-cap arc endpoints are emitted as half
 * circles by `SkStroker`, exercising the round-cap dispatch on a
 * short-sweep curve through G3.4.1.
 */
class B119394958WebGpuTest {

    @Test
    fun `B119394958GM renders close to reference PNG on the GPU backend`() {
        // G6.2 re-ratchet : moving the intermediate render target
        // to RGBA16Float introduced sub-byte precision in the
        // gradient lerp / coverage products, which shifted one
        // edge pixel by 1 LSB and dropped similarity by 0.01 %.
        runGpuCrossTest(B119394958GM(), floor = 93.74, logTag = "B119394958WebGpu")
    }
}
