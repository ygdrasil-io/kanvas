package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnimCodecPlayerExifGM

@Disabled("STUB.ANIM_CODEC_PLAYER: requires SkAnimCodecPlayer + SkCodec frame-by-frame seek")
class AnimCodecPlayerExifCrossBackendTest {
    @Test
    fun `AnimCodecPlayerExifGM placeholder`() {
        runCrossBackendTest(AnimCodecPlayerExifGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
