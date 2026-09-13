package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelOperation

/** Sole W5e sampling/color emitter. Task 3 extends this graph consumer, not a parallel sampler. */
internal object W5eImageTexelEvaluatorV1 {
    fun declarations(execution: ImageSampleExecutionPlanV1, child: W5aMaterialSourceStage?, layout: ImageSourceLayoutV3): String {
        val graph = execution.numericAuthority.graph
        val maskSource = execution.colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA
        require(maskSource == (child != null))
        require(layout.hasChildGradientStorage == (child?.gradientStopSlab != null))
        val returnType = if (maskSource) "f32" else "vec4<f32>"
        val zero = if (maskSource) "0.0" else "vec4<f32>(0.0)"
        require(graph.contractId == "WgslFloatEnvelopeV1" && graph.colorAlpha == execution.colorAlpha)
        val texelOperations = graph.texelOperations()
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
        statements.append("if (!w5e_finite($denominator) || abs($denominator) < 1.17549435e-38f) { return $zero; }\n")
        val x = emit(graph.sourceX)
        val y = emit(graph.sourceY)
        val evaluateTexel = if (TexelOperation.RETURN_SCALAR_MASK in texelOperations) {
            val mask = if (TexelOperation.ALPHA_OPAQUE in texelOperations) "1.0" else "encoded.r"
            "return $mask;"
        } else {
            val rgb = if (TexelOperation.SWIZZLE_BGRA in texelOperations) "vec3<f32>(encoded.b, encoded.g, encoded.r)" else "encoded.rgb"
            val alpha = if (TexelOperation.ALPHA_OPAQUE in texelOperations) "1.0" else "encoded.a"
            val straight = if (TexelOperation.UNPREMULTIPLY_SOURCE in texelOperations)
                "let straightRgb = $rgb / sourceAlpha;"
            else "let straightRgb = $rgb;"
            val transfer = if (TexelOperation.SRGB_TO_LINEAR in texelOperations)
                "let linearRgb = w5a_srgb_to_linear(vec4<f32>(straightRgb, sourceAlpha)).rgb;" else "let linearRgb = straightRgb;"
            val gamut = if (TexelOperation.DISPLAY_P3_TO_LINEAR_SRGB in texelOperations)
                "let workingRgb = vec3<f32>(1.2247455 * linearRgb.r - 0.2249044 * linearRgb.g, " +
                    "-0.0420581 * linearRgb.r + 1.0420810 * linearRgb.g, " +
                    "-0.0196423 * linearRgb.r - 0.0786549 * linearRgb.g + 1.0985372 * linearRgb.b);"
            else "let workingRgb = linearRgb;"
            """
                let sourceAlpha = $alpha;
                if (sourceAlpha == 0.0) { return vec4<f32>(0.0); }
                $straight
                $transfer
                $gamut
                return vec4<f32>(workingRgb * sourceAlpha, sourceAlpha);
            """.trimIndent()
        }
        return """
            ${child?.declarationsWgsl.orEmpty()}
            struct W5eImageBlock {
                values0: vec4<f32>, values1: vec4<f32>, values2: vec4<f32>,
                values3: vec4<f32>, values4: vec4<f32>, parameters: vec4<f32>,
                ${if (child != null) "child: W5aMaterialBlock," else ""}
            }
            @group(1) @binding(${layout.uniformBindingU32}) var<uniform> w5eImage: W5eImageBlock;
            @group(1) @binding(${layout.imageTextureBindingU32}) var w5eTexture: texture_2d<f32>;
            ${if (child == null) W5aMaterialSourceStage.SRGB_TO_LINEAR_WGSL else ""}
            fn w5e_finite(value: f32) -> bool { return (bitcast<u32>(value) & 0x7f800000u) != 0x7f800000u; }
            fn w5e_device_point(pixel: vec2<f32>) -> vec2<f32> { return pixel; }
            fn w5e_image_sample(pixel: vec2<f32>) -> $returnType {
                $statements
                // Validity dominates floor and signed integer conversion, including dynamic faults.
                if (!w5e_finite($x) || !w5e_finite($y) || $x < -2147483648.0 || $x >= 2147483648.0 ||
                    $y < -2147483648.0 || $y >= 2147483648.0) { return $zero; }
                let ix = clamp(i32(floor($x)), 0, i32(w5eImage.parameters.x) - 1);
                let iy = clamp(i32(floor($y)), 0, i32(w5eImage.parameters.y) - 1);
                let encoded: vec4<f32> = textureLoad(w5eTexture, vec2<i32>(ix, iy), 0);
                $evaluateTexel
            }
            fn kanvas_material_source(pixel: vec2<f32>) -> vec4<f32> {
                ${if (child == null) "return w5e_image_sample(pixel) * w5eImage.parameters.z;" else
                    "return w5e_child_source(" + (if (child.gradientStopSlab == null) "pixel" else child.coordinateFunctionName + "(pixel)") + ") * w5e_image_sample(pixel);"}
            }
        """.trimIndent()
    }
}
