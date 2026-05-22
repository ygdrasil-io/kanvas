package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ConicalGradientsGM

@Disabled("ALIAS: upstream ConicalGradientsGM — already ported as ConicalGradients2pt{Inside,Outside,TileMode}WebGpuTest")
class ConicalGradientsWebGpuTest {
    @Test
    fun `ConicalGradientsGM placeholder alias`() {
        runGpuCrossTest(ConicalGradientsGM(), floor = 0.0)
    }
}
