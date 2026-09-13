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
        require(graph.contractId == "WgslFloatEnvelopeV1" && graph.colorAlpha == execution.colorAlpha &&
            graph.sampling == execution.sampling && graph.tileModes == execution.tileModes)
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
        val evaluateEncodedTexel = if (TexelOperation.RETURN_SCALAR_MASK in texelOperations) {
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
        fun address(axis: String, index: String, dimension: String, mode: ImageTileAxisModePlanV1): String = when (mode) {
            ImageTileAxisModePlanV1.CLAMP -> "let a$axis = clamp($index, 0, $dimension - 1);"
            ImageTileAxisModePlanV1.REPEAT -> "let a$axis = (($index % $dimension) + $dimension) % $dimension;"
            ImageTileAxisModePlanV1.MIRROR -> "let p$axis = (($index % ($dimension * 2)) + ($dimension * 2)) % ($dimension * 2);\n" +
                "let a$axis = min(p$axis, ($dimension * 2) - 1 - p$axis);"
            ImageTileAxisModePlanV1.DECAL -> "if ($index < 0 || $index >= $dimension) { return $zero; }\nlet a$axis = $index;"
        }
        val addressX = address("x", "ix", "i32(w5eImage.parameters.x)", execution.tileModes.x)
        val addressY = address("y", "iy", "i32(w5eImage.parameters.y)", execution.tileModes.y)
        val sample = when (execution.sampling) {
            ImageSamplingPlanV1.Nearest -> """
                let tapX = $x;
                let tapY = $y;
                if (!w5e_finite(tapX) || !w5e_finite(tapY) || tapX < -2147483648.0 || tapX >= 2147483648.0 ||
                    tapY < -2147483648.0 || tapY >= 2147483648.0) { return $zero; }
                return w5e_texel(i32(floor(tapX)), i32(floor(tapY)));
            """.trimIndent()
            ImageSamplingPlanV1.Linear -> """
                let tapX = $x - 0.5;
                let tapY = $y - 0.5;
                if (!w5e_finite(tapX) || !w5e_finite(tapY) || tapX < -2147483648.0 || tapX >= 2147483647.0 ||
                    tapY < -2147483648.0 || tapY >= 2147483647.0) { return $zero; }
                let baseX = i32(floor(tapX));
                let baseY = i32(floor(tapY));
                let fractionX = tapX - f32(baseX);
                let fractionY = tapY - f32(baseY);
                let tap00 = w5e_texel(baseX, baseY);
                let tap10 = w5e_texel(baseX + 1, baseY);
                let tap01 = w5e_texel(baseX, baseY + 1);
                let tap11 = w5e_texel(baseX + 1, baseY + 1);
                return tap00 * (1.0 - fractionX) * (1.0 - fractionY) +
                    tap10 * fractionX * (1.0 - fractionY) + tap01 * (1.0 - fractionX) * fractionY +
                    tap11 * fractionX * fractionY;
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
            fn w5e_texel(ix: i32, iy: i32) -> $returnType {
                $addressX
                $addressY
                let encoded: vec4<f32> = textureLoad(w5eTexture, vec2<i32>(ax, ay), 0);
                $evaluateEncodedTexel
            }
            fn w5e_image_sample(pixel: vec2<f32>) -> $returnType {
                $statements
                // Validity dominates floor and every signed integer conversion, including dynamic faults.
                $sample
            }
            fn kanvas_material_source(pixel: vec2<f32>) -> vec4<f32> {
                ${if (child == null) "return w5e_image_sample(pixel) * w5eImage.parameters.z;" else
                    "return w5e_child_source(" + (if (child.gradientStopSlab == null) "pixel" else child.coordinateFunctionName + "(pixel)") + ") * w5e_image_sample(pixel);"}
            }
        """.trimIndent()
    }
}
