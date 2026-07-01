package org.graphiks.kanvas.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PipelineTypesTest {
    @Test fun `UniformSlot data class`() { val s = UniformSlot("t", 0, UniformType.MAT3X3, 48); assertEquals("t", s.name); assertEquals(48, s.size) }
    @Test fun `RenderPipeline built-in constants`() { assertTrue(RenderPipeline.SOLID_COLOR_FILL is RenderPipeline); assertTrue(RenderPipeline.STENCIL_COVER is RenderPipeline) }
    @Test fun `BlendConfig defaults`() { assertEquals(BlendFactor.SRC_ALPHA, BlendConfig.SRC_OVER.colorSrc) }
    @Test fun `RenderPassDescriptor`() { assertEquals(1, RenderPassDescriptor(listOf(ColorAttachment(GPUHandle(1L)))).colorAttachments.size) }
    @Test fun `RuntimeEffect compile fails validation`() { assertTrue(RuntimeEffect.compile("fn main() {}").isFailure) }
    @Test fun `GPUHandle value class`() { assertEquals(42L, GPUHandle(42L).id) }

    @Test fun `UniformBlock builder`() {
        val b = UniformBlock { float2("offset", 10f, 20f); float4("color", 1f, 0f, 0f, 1f) }
        assertEquals(2, b.entries.size)
        assertEquals(10f, (b.entries["offset"] as UniformValue.F2).x)
    }
}
