package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.FiddleGM

/**
 * Cross-test : `FiddleGM` on the GPU backend.
 *
 * Empty fiddle placeholder — `onDraw` is intentionally a no-op so the
 * GM renders only the default white background. Tiny but meaningful :
 * exercises the device's "no draws ; flush only the background clear"
 * path through `WebGpuSink`, end-to-end including the G6.0/G6.1
 * sRGB → Rec.2020 present-pass transform on a uniform colour. A baseline
 * sanity check that the post-process colour-space pipeline is round-trip
 * exact on pure background.
 */
class FiddleWebGpuTest {

    @Test
    fun `FiddleGM renders close to reference PNG on the GPU backend`() {
        // Background-only render — every pixel survives the
        // sRGB → Rec.2020 present-pass transform byte-exact, so the
        // floor is at the ceiling.
        runGpuCrossTest(FiddleGM(), floor = 99.99)
    }
}
