package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedEvidence
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue

class GPUPreparedMaterialUnsupportedTest {
    @Test
    fun `typed prepared mapper refusals produce canonical compiler diagnostics`() {
        GPUPreparedMaterialUnsupportedReason.entries.forEach { reason ->
            val descriptor = GPUMaterialDescriptor.Unsupported(
                reason = reason,
                originalKind = GPUMaterialKind.ImageDraw,
            )
            val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
                GPUPreparedMaterialProgramCompiler.compile(
                    descriptor = descriptor,
                    paintAlpha = 1f,
                    context = GPUMaterialLoweringContext(
                        capabilityClass = "test",
                        targetFormatClass = "rgba8unorm",
                        dictionaryVersion = "test",
                    ),
                ),
            )
            assertEquals(reason.diagnosticCode, refused.code)
            assertEquals(GPUMaterialSourceKind.ImageShader, refused.sourceKind)
        }
    }

    @Test
    fun `graph refusals and runtime color filter evidence stay fail closed in the compiler`() {
        val evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
            effectId = "must.not.compile",
            uniforms = mapOf(
                "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
            ),
            childIdentities = mapOf(
                "input" to "sha256:${"0".repeat(64)}",
            ),
        )
        val cases = listOf(
            GPUMaterialDescriptor.Unsupported(
                reason = GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
                originalKind = GPUMaterialKind.RuntimeEffect,
            ),
            GPUMaterialDescriptor.Unsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
                originalKind = GPUMaterialKind.SolidColor,
            ),
            GPUMaterialDescriptor.Unsupported(
                reason = GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH,
                originalKind = GPUMaterialKind.RuntimeEffect,
            ),
            GPUMaterialDescriptor.Unsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH,
                originalKind = GPUMaterialKind.SolidColor,
            ),
            GPUMaterialDescriptor.Unsupported(
                reason = GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                originalKind = GPUMaterialKind.SolidColor,
                source = GPUMaterialDescriptor.RuntimeEffect(effectId = "must.not.compile"),
                evidence = evidence,
            ),
        )

        cases.forEach { descriptor ->
            val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
                GPUPreparedMaterialProgramCompiler.compile(
                    descriptor = descriptor,
                    paintAlpha = 1f,
                    context = GPUMaterialLoweringContext(
                        capabilityClass = "test",
                        targetFormatClass = "rgba8unorm",
                        dictionaryVersion = "test",
                    ),
                ),
            )
            assertEquals(descriptor.reason.diagnosticCode, refused.code)
        }
    }
}
