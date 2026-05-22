package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnimatedImageGM

@Disabled("STUB.ANIMATED_IMAGE: requires SkAnimatedImage + SkCodec frame decode pipeline")
class AnimatedImageWebGpuTest {
    @Test
    fun `AnimatedImageGM placeholder`() {
        runGpuCrossTest(AnimatedImageGM(), floor = 0.0)
    }
}
