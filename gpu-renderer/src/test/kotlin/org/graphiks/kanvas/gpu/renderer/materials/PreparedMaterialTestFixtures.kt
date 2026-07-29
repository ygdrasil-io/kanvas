package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialColorContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialCoordinateContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialUniformBinding

internal fun stubPreparedMaterialFragment(
    uniformByteCount: Int = 0,
): GPUPreparedMaterialFragment =
    GPUPreparedMaterialFragment(
        declarationsWgsl = """
            fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                return vec4f(localPosition, 0.0, 1.0);
            }
        """.trimIndent(),
        evaluationFunctionWgsl = """
            fn kanvas_evaluate_material(localPosition: vec2<f32>) -> vec4<f32> {
                return kanvas_material_source(localPosition);
            }
        """.trimIndent(),
        evaluationFunction = "kanvas_evaluate_material",
        uniformBinding = uniformByteCount.takeIf { it > 0 }?.let {
            GPUPreparedMaterialUniformBinding(minBindingSizeBytes = it)
        },
        sampledBindings = emptyList(),
        colorContract = GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
        coordinateContract = GPUPreparedMaterialCoordinateContract.LocalPosition2D,
        fragmentHash = "0".repeat(64),
        abiHash = "abi:test-stub",
    )
