package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason

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
}
