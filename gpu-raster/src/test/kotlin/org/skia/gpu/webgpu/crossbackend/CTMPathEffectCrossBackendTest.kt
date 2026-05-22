package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CTMPathEffectGM

@Disabled("STUB.PATH_EFFECT_CTM: needs CTM-aware SkPath1DPathEffect / SkDiscretePathEffect")
class CTMPathEffectCrossBackendTest {
    @Test
    fun `CTMPathEffectGM placeholder`() {
        runCrossBackendTest(CTMPathEffectGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
