package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CTMPathEffectGM

@Disabled("STUB.PATH_EFFECT_CTM: needs CTM-aware SkPath1DPathEffect / SkDiscretePathEffect")
class CTMPathEffectWebGpuTest {
    @Test
    fun `CTMPathEffectGM placeholder`() {
        runGpuCrossTest(CTMPathEffectGM(), floor = 0.0)
    }
}
