package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun `one reflected module exposes shared direct cover and stencil entry points`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT to "fragment",
                CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        assertEquals(32, reflection.layouts.single { it.structName == "CorePrimitiveBlock" }.size)
        assertEquals("core-primitive-device-geometry-wgsl-v2", CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY)
    }
}
