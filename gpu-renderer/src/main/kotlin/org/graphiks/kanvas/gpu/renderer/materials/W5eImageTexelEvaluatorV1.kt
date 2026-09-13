package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Sole W5e sampling/color emitter. Task 3 extends this graph consumer, not a parallel sampler. */
internal object W5eImageTexelEvaluatorV1 {
    fun uniformBytes(execution: ImageSampleExecutionPlanV1): ByteArray = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN).apply {
        execution.coordinates.uniformValuesF32().forEach(::putFloat)
        putFloat(execution.upload.widthI32.toFloat()).putFloat(execution.upload.heightI32.toFloat())
        putFloat(execution.paintAlphaF32).putFloat(0f)
    }.array()

    fun declarations(execution: ImageSampleExecutionPlanV1): String {
        val graph = execution.numericAuthority.graph
        require(graph.contractId == "WgslFloatEnvelopeV1" && graph.texelOperations() == ImageNumericOperationGraphV1.TexelOperation.entries)
        val statements = StringBuilder()
        val emitted = mutableMapOf<ImageNumericOperationGraphV1.Node, String>()
        fun emit(node: ImageNumericOperationGraphV1.Node): String = emitted.getOrPut(node) {
            val inputs = node.inputs.map(::emit)
            val value = when (node.operation) {
                ImageNumericOperationGraphV1.Operation.DEVICE_X_F32 -> "pixel.x"
                ImageNumericOperationGraphV1.Operation.DEVICE_Y_F32 -> "pixel.y"
                ImageNumericOperationGraphV1.Operation.UNIFORM_F32 -> "w5eImage.values${node.uniformIndexI32 / 4}[${node.uniformIndexI32 % 4}]"
                ImageNumericOperationGraphV1.Operation.ADD_F32 -> "(${inputs[0]} + ${inputs[1]})"
                ImageNumericOperationGraphV1.Operation.SUB_F32 -> "(${inputs[0]} - ${inputs[1]})"
                ImageNumericOperationGraphV1.Operation.MUL_F32 -> "(${inputs[0]} * ${inputs[1]})"
                ImageNumericOperationGraphV1.Operation.DIV_F32 -> "(${inputs[0]} / ${inputs[1]})"
            }
            val name = "imageValue${emitted.size}"
            statements.append("let $name = $value;\n")
            name
        }
        val denominator = emit(graph.denominator)
        statements.append("if (!w5e_finite($denominator) || abs($denominator) < 1.17549435e-38f) { return vec4<f32>(0.0); }\n")
        val x = emit(graph.sourceX)
        val y = emit(graph.sourceY)
        return """
            struct W5eImageBlock {
                values0: vec4<f32>, values1: vec4<f32>, values2: vec4<f32>,
                values3: vec4<f32>, values4: vec4<f32>, parameters: vec4<f32>,
            }
            @group(1) @binding(0) var<uniform> w5eImage: W5eImageBlock;
            @group(1) @binding(1) var w5eTexture: texture_2d<f32>;
            ${W5aMaterialSourceStage.SRGB_TO_LINEAR_WGSL}
            fn w5e_finite(value: f32) -> bool { return (bitcast<u32>(value) & 0x7f800000u) != 0x7f800000u; }
            fn w5e_device_point(pixel: vec2<f32>) -> vec2<f32> { return pixel; }
            fn kanvas_material_source(pixel: vec2<f32>) -> vec4<f32> {
                $statements
                // Validity dominates floor and signed integer conversion, including dynamic faults.
                if (!w5e_finite($x) || !w5e_finite($y) || $x < -2147483648.0 || $x >= 2147483648.0 ||
                    $y < -2147483648.0 || $y >= 2147483648.0) { return vec4<f32>(0.0); }
                let ix = clamp(i32(floor($x)), 0, i32(w5eImage.parameters.x) - 1);
                let iy = clamp(i32(floor($y)), 0, i32(w5eImage.parameters.y) - 1);
                let encoded: vec4<f32> = textureLoad(w5eTexture, vec2<i32>(ix, iy), 0);
                if (encoded.a == 0.0) { return vec4<f32>(0.0); }
                let straight = vec4<f32>(encoded.rgb / encoded.a, encoded.a);
                let linear = w5a_srgb_to_linear(straight);
                return vec4<f32>(linear.rgb * encoded.a, encoded.a) * w5eImage.parameters.z;
            }
        """.trimIndent()
    }
}
