package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConicalGradientsGM

@Disabled("ALIAS: upstream ConicalGradientsGM — already ported as ConicalGradients2pt{Inside,Outside,TileMode}CrossBackendTest")
class ConicalGradientsCrossBackendTest {
    @Test
    fun `ConicalGradientsGM placeholder alias`() {
        runCrossBackendTest(ConicalGradientsGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
