package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnimCodecPlayerExifGM

@Disabled("STUB.ANIM_CODEC_PLAYER: requires SkAnimCodecPlayer + SkCodec frame-by-frame seek")
class AnimCodecPlayerExifWebGpuTest {
    @Test
    fun `AnimCodecPlayerExifGM placeholder`() {
        runGpuCrossTest(AnimCodecPlayerExifGM(), floor = 0.0)
    }
}
