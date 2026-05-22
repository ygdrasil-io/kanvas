package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnimatedImageGM

@Disabled("STUB.ANIMATED_IMAGE: requires SkAnimatedImage + SkCodec frame decode pipeline")
class AnimatedImageCrossBackendTest {
    @Test
    fun `AnimatedImageGM placeholder`() {
        runCrossBackendTest(AnimatedImageGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
