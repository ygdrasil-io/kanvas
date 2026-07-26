package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasWGSLValidator

class GPUPreparedImageShaderTest {
    @Test
    fun `shader parses and reflects the exact dynamic uniform texture sampler ABI`() {
        val parsed = KanvasWGSLValidator().parse(GPU_PREPARED_IMAGE_WGSL)
        assertTrue(parsed.syntaxErrors.isEmpty(), parsed.syntaxErrors.joinToString())
        val report = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report)

        assertEquals(
            listOf(
                Triple(0, 0, "uniformBuffer"),
                Triple(0, 1, "sampledTexture"),
                Triple(0, 2, "sampler"),
            ),
            report.bindings.map { Triple(it.group, it.binding, it.resourceKind) },
        )
        assertEquals(GPUPreparedImageUniformAbi.BYTE_SIZE, report.bindings[0].minBindingSize)
        assertEquals(PREPARED_IMAGE_BINDING_LAYOUT_HASH, preparedImageShaderContract().bindingLayoutHash)
    }

    @Test
    fun `uniform ABI preserves four independent positions and UVs byte for byte`() {
        val input = GPUPreparedImageUniformInput(
            positions = listOf(1f to 2f, 3f to 4f, 5f to 6f, 7f to 8f),
            uvs = listOf(0.1f to 0.2f, 0.3f to 0.4f, 0.5f to 0.6f, 0.7f to 0.8f),
            tintPremultipliedRgba = listOf(0.1f, 0.2f, 0.3f, 0.5f),
            atlasColorPremultipliedRgba = null,
            alphaOnly = false,
            atlasSourceBlend = null,
        )
        val expected = ByteBuffer.allocate(GPUPreparedImageUniformAbi.BYTE_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        input.positions.zip(input.uvs).forEach { (position, uv) ->
            expected.putFloat(position.first).putFloat(position.second)
            expected.putFloat(uv.first).putFloat(uv.second)
        }
        input.tintPremultipliedRgba.forEach(expected::putFloat)
        repeat(4) { expected.putFloat(0f) }
        expected.putInt(0).putInt(0).putInt(0).putInt(0)

        assertContentEquals(expected.array(), GPUPreparedImageUniformAbi.pack(input))
    }

    @Test
    fun `A8 coverage and all five closed atlas modes match hand-derived CPU oracles`() {
        val coverage = 0.25f
        val tint = listOf(0.2f, 0.1f, 0.05f, 0.5f)
        val atlas = listOf(0.3f, 0.2f, 0.1f, 0.5f)
        val expected = mapOf(
            GPUPreparedAtlasSourceBlend.Src to listOf(0.075f, 0.05f, 0.025f, 0.125f),
            GPUPreparedAtlasSourceBlend.Dst to listOf(0.2f, 0.1f, 0.05f, 0.5f),
            GPUPreparedAtlasSourceBlend.SrcOver to listOf(0.25f, 0.1375f, 0.06875f, 0.5625f),
            GPUPreparedAtlasSourceBlend.Plus to listOf(0.275f, 0.15f, 0.075f, 0.625f),
            GPUPreparedAtlasSourceBlend.Modulate to listOf(0.015f, 0.005f, 0.00125f, 0.0625f),
        )
        expected.forEach { (mode, oracle) ->
            oracle.zip(preparedImageA8AtlasOracle(coverage, tint, atlas, mode))
                .forEachIndexed { channel, (want, actual) ->
                    assertEquals(want, actual, 0.000001f, "mode=$mode channel=$channel")
                }
        }
        assertNull(preparedImageAtlasSourceBlend(GPUBlendMode.SCREEN))
        assertEquals(1, GPU_PREPARED_IMAGE_WGSL.windowed("tint.a".length)
            .count { it == "tint.a" })
    }
}
