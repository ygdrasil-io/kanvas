package org.graphiks.kanvas.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertFailsWith

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

    @Test
    fun `UniformBlock deeply snapshots mat4 values and exposes an immutable map`() {
        val source = FloatArray(16) { index -> index.toFloat() }
        val block = UniformBlock {
            mat4x4("transform", source)
            float1("alpha", 1f)
        }
        source[0] = 99f

        assertEquals(0f, (block.entries.getValue("transform") as UniformValue.M4).values[0])
        @Suppress("UNCHECKED_CAST")
        val mutableEntries = block.entries as MutableMap<String, UniformValue>
        assertFailsWith<UnsupportedOperationException> {
            mutableEntries["alpha"] = UniformValue.F1(0f)
        }

        val exposed = (block.entries.getValue("transform") as UniformValue.M4).values
        exposed[1] = 99f
        assertEquals(1f, (block.entries.getValue("transform") as UniformValue.M4).values[1])
    }

    @Test
    fun `M4 preserves defensive data class copy and destructuring contracts`() {
        val original = UniformValue.M4(FloatArray(16) { index -> index.toFloat() })

        val (destructured) = original
        destructured[0] = 99f
        assertEquals(0f, original.values[0])

        val defaultCopy = original.copy()
        val defaultCopyValues = defaultCopy.values
        defaultCopyValues[1] = 99f
        assertEquals(1f, original.values[1])

        val replacement = FloatArray(16) { index -> (index + 20).toFloat() }
        val replacementCopy = original.copy(values = replacement)
        replacement[0] = 99f
        assertEquals(20f, replacementCopy.values[0])
    }
}
