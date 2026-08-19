package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

class GPUPreparedImageShaderTest {
    @Test
    fun `invalid prepared image WGSL returns the canonical production refusal`() {
        val refused = assertIs<GPUPreparedImageShaderValidationResult.Refused>(
            validatePreparedImageShader("@fragment fn broken("),
        )

        assertEquals(GPUPreparedImageRefusalCodes.WGSL_VALIDATION, refused.code)
        assertEquals("wgsl-validation", refused.facts["boundary"])
        assertTrue(refused.facts.getValue("reason").isNotBlank())
    }

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
        val ready = assertIs<GPUPreparedImageShaderValidationResult.Ready>(
            validatePreparedImageShader(GPU_PREPARED_IMAGE_WGSL),
        )
        val contract = ready.bindingLayout
        assertEquals(0, contract.group)
        assertEquals(0, contract.uniformBinding)
        assertEquals(1, contract.textureBinding)
        assertEquals(2, contract.samplerBinding)
        assertEquals(112L, contract.uniformMinBindingSize)
        assertEquals(contract.identity, ready.shaderContract.bindingLayoutHash)
        assertEquals(
            contract.reflectedBindingsHash,
            ready.shaderContract.reflectedBindingsHash,
        )
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
    fun `A8 atlas modes apply nontrivial paint tint and coverage exactly once`() {
        val tint = listOf(0.2f, 0.1f, 0.05f, 0.5f)
        val atlas = listOf(0.3f, 0.2f, 0.1f, 0.5f)
        val expected = mapOf(
            0f to mapOf(
                GPUPreparedAtlasSourceBlend.Src to listOf(0f, 0f, 0f, 0f),
                GPUPreparedAtlasSourceBlend.Dst to listOf(0f, 0f, 0f, 0f),
                GPUPreparedAtlasSourceBlend.SrcOver to listOf(0f, 0f, 0f, 0f),
                GPUPreparedAtlasSourceBlend.Plus to listOf(0f, 0f, 0f, 0f),
                GPUPreparedAtlasSourceBlend.Modulate to listOf(0f, 0f, 0f, 0f),
            ),
            0.5f to mapOf(
                GPUPreparedAtlasSourceBlend.Src to listOf(0.03f, 0.01f, 0.0025f, 0.125f),
                GPUPreparedAtlasSourceBlend.Dst to listOf(0.1f, 0.05f, 0.025f, 0.25f),
                GPUPreparedAtlasSourceBlend.SrcOver to listOf(0.08f, 0.035f, 0.015f, 0.25f),
                GPUPreparedAtlasSourceBlend.Plus to listOf(0.1f, 0.05f, 0.025f, 0.25f),
                GPUPreparedAtlasSourceBlend.Modulate to
                    listOf(0.03f, 0.01f, 0.0025f, 0.125f),
            ),
            1f to mapOf(
                GPUPreparedAtlasSourceBlend.Src to listOf(0.06f, 0.02f, 0.005f, 0.25f),
                GPUPreparedAtlasSourceBlend.Dst to listOf(0.2f, 0.1f, 0.05f, 0.5f),
                GPUPreparedAtlasSourceBlend.SrcOver to listOf(0.16f, 0.07f, 0.03f, 0.5f),
                GPUPreparedAtlasSourceBlend.Plus to listOf(0.2f, 0.1f, 0.05f, 0.5f),
                GPUPreparedAtlasSourceBlend.Modulate to listOf(0.06f, 0.02f, 0.005f, 0.25f),
            ),
        )
        expected.forEach { (coverage, modes) ->
            modes.forEach { (mode, oracle) ->
                oracle.zip(preparedImageA8AtlasOracle(coverage, tint, atlas, mode))
                    .forEachIndexed { channel, (want, actual) ->
                        assertEquals(
                            want,
                            actual,
                            0.000001f,
                            "coverage=$coverage mode=$mode channel=$channel",
                        )
                    }
                }
        }
        assertNull(preparedImageAtlasSourceBlend(GPUBlendMode.SCREEN))
    }
}
