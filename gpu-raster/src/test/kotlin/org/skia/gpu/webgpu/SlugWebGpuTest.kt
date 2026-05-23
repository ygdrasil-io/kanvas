package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SlugGM

@Disabled("STUB.SLUG: requires sktext::gpu::Slug + SkCanvas.drawSlug")
class SlugWebGpuTest {
    @Test
    fun `SlugGM placeholder`() {
        runGpuCrossTest(SlugGM(), floor = 0.0)
    }
}
