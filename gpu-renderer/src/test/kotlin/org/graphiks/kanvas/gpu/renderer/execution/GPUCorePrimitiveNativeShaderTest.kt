package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class GPUCorePrimitiveNativeShaderTest {
    @Test
    fun `shader is parser validated and converts device coordinates with y inversion`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveNativeShader(),
        )

        assertContains(ready.plan.wgslSource, "device_position.x / core.target_size.x * 2.0 - 1.0")
        assertContains(ready.plan.wgslSource, "1.0 - device_position.y / core.target_size.y * 2.0")
        assertContains(ready.plan.wgslSource, "return core.premul_rgba;")
    }
}
