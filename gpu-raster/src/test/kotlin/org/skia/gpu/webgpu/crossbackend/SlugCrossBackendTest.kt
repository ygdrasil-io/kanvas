package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SlugGM

@Disabled("STUB.SLUG: requires sktext::gpu::Slug + SkCanvas.drawSlug")
class SlugCrossBackendTest {
    @Test
    fun `SlugGM placeholder`() {
        runCrossBackendTest(SlugGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
