package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

internal fun stubPreparedMaterialProgram(
    red: Float = 1f,
    paintAlpha: Float = 1f,
): GPUPreparedMaterialProgram {
    val result = GPUPreparedMaterialProgramCompiler.compile(
        descriptor = GPUMaterialDescriptor.SolidColor(
            r = red,
            g = 1f,
            b = 1f,
            a = 1f,
        ),
        paintAlpha = paintAlpha,
        context = GPUMaterialLoweringContext(
            capabilityClass = "test-prepared-material",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = "material-dictionary:test:v1",
        ),
    )
    return checkNotNull((result as? GPUPreparedMaterialProgramResult.Ready)?.program) {
        "The admitted prepared-material fixture must compile: $result"
    }
}
