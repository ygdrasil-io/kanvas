package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelOperation

/** Sole W5e sampling/color emitter. Task 3 extends this graph consumer, not a parallel sampler. */
internal object W5eImageTexelEvaluatorV1 {
    fun declarations(execution: ImageSampleExecutionPlanV1, child: W5aMaterialSourceStage?, layout: ImageSourceLayoutV3): String {
        val graph = execution.numericAuthority.graph
        val selection = execution.cellSelection
        require(layout.selectsCells == (selection != null))
        val maskSource = execution.colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA
        require(maskSource == (child != null))
        require(layout.hasChildGradientStorage == (child?.gradientStopSlab != null))
        val returnType = if (maskSource) "f32" else "vec4<f32>"
        val zero = if (maskSource) "0.0" else "vec4<f32>(0.0)"
        require(graph.contractId == "WgslFloatEnvelopeV1" && graph.colorAlpha == execution.colorAlpha &&
            graph.sampling == execution.sampling && graph.tileModes == execution.tileModes)
        val texelOperations = graph.texelOperations()
        val statements = StringBuilder()
        val afterValidityStatements = StringBuilder()
        val afterFetchStatements = StringBuilder()
        val emitted = mutableMapOf<ImageNumericOperationGraphV1.Node, String>()
        fun expression(node: ImageNumericOperationGraphV1.Node, inputs: List<String>): String = when (node.operation) {
            ImageNumericOperationGraphV1.Operation.DEVICE_X_F32 -> "pixel.x"
            ImageNumericOperationGraphV1.Operation.DEVICE_Y_F32 -> "pixel.y"
            ImageNumericOperationGraphV1.Operation.UNIFORM_F32 -> when {
                selection != null && node.uniformIndexI32 in 12..15 -> "cellSource[${node.uniformIndexI32 % 4}]"
                selection != null && node.uniformIndexI32 in 16..19 -> "cellDestination[${node.uniformIndexI32 % 4}]"
                node.uniformIndexI32 < 20 -> "w5eImage.values${node.uniformIndexI32 / 4}[${node.uniformIndexI32 % 4}]"
                node.uniformIndexI32 < 24 -> "w5eImage.parameters[${node.uniformIndexI32 % 4}]"
                else -> "w5eImage.cubicParameters[${node.uniformIndexI32 % 4}]"
            }
            ImageNumericOperationGraphV1.Operation.CONSTANT_HALF_F32 -> "0.5"
            ImageNumericOperationGraphV1.Operation.CONSTANT_ONE_F32 -> "1.0"
            ImageNumericOperationGraphV1.Operation.CONSTANT_F32 -> "${Float.fromBits(node.constantBitsI32)}f"
            ImageNumericOperationGraphV1.Operation.KERNEL_DISTANCE_F32 -> "distance"
            ImageNumericOperationGraphV1.Operation.ABS_F32 -> "abs(${inputs.single()})"
            ImageNumericOperationGraphV1.Operation.TAP_INDEX_F32 -> "f32(i32(${inputs.single()}) + ${node.uniformIndexI32})"
            ImageNumericOperationGraphV1.Operation.CUBIC_KERNEL_F32 -> "w5e_cubic_weight(${inputs.single()})"
            ImageNumericOperationGraphV1.Operation.TEXEL_COMPONENT_F32 -> "tap${node.uniformIndexI32 / 4}${node.uniformIndexI32 % 4}"
            ImageNumericOperationGraphV1.Operation.ADD_F32 -> "(${inputs[0]} + ${inputs[1]})"
            ImageNumericOperationGraphV1.Operation.SUB_F32 -> "(${inputs[0]} - ${inputs[1]})"
            ImageNumericOperationGraphV1.Operation.MUL_F32 -> "(${inputs[0]} * ${inputs[1]})"
            ImageNumericOperationGraphV1.Operation.DIV_F32 -> "(${inputs[0]} / ${inputs[1]})"
            ImageNumericOperationGraphV1.Operation.FLOOR_F32 -> "floor(${inputs[0]})"
        }
        fun emit(node: ImageNumericOperationGraphV1.Node, afterValidity: Boolean = false, afterFetch: Boolean = false): String = emitted.getOrPut(node) {
            val inputs = node.inputs.map { emit(it, afterValidity, afterFetch) }
            val value = expression(node, inputs)
            val name = "imageValue${emitted.size}"
            (if (afterFetch) afterFetchStatements else if (afterValidity) afterValidityStatements else statements).append("let $name = $value;\n")
            name
        }
        val cubicKernelDeclarations = graph.cubicKernel?.let { kernel ->
            fun branch(root: ImageNumericOperationGraphV1.Node, prefix: String): Pair<String, String> {
                val body = StringBuilder()
                val names = mutableMapOf(kernel.distance to "distance", kernel.absoluteDistance to "kernelAbsolute")
                fun emitKernel(node: ImageNumericOperationGraphV1.Node): String = names.getOrPut(node) {
                    val inputs = node.inputs.map(::emitKernel)
                    val name = "$prefix${names.size}"
                    body.append("let $name = ${expression(node, inputs)};\n")
                    name
                }
                val result = emitKernel(root)
                return body.toString() to result
            }
            val (innerBody, innerResult) = branch(kernel.innerResult, "innerKernelValue")
            val (outerBody, outerResult) = branch(kernel.outerResult, "outerKernelValue")
            """
                fn w5e_cubic_weight(distance: f32) -> f32 {
                    let kernelAbsolute = ${expression(kernel.absoluteDistance, listOf("distance"))};
                    if (kernelAbsolute < ${kernel.innerLimitF32}f) {
                        $innerBody
                        return $innerResult;
                    }
                    if (kernelAbsolute < ${kernel.outerLimitF32}f) {
                        $outerBody
                        return $outerResult;
                    }
                    return ${expression(kernel.outsideResult, emptyList())};
                }
            """.trimIndent()
        }.orEmpty()
        val selectorDeclarations = selection?.let { selector ->
            require(selector.capacityI32 == 9 && selector.samples.size <= selector.capacityI32 &&
                selector.startInclusive && !selector.endInclusive && selector.firstHit && selector.discardOnMiss)
            val body = StringBuilder()
            val names = mutableMapOf<ImageNumericOperationGraphV1.Node, String>()
            fun scalar(node: ImageNumericOperationGraphV1.Node): String = names.getOrPut(node) {
                val args = node.inputs.map(::scalar)
                val name = "selectorValue${names.size}"
                body.append("let $name = ${expression(node, args)};\n")
                name
            }
            val selectorDenominator = scalar(graph.denominator)
            body.append("if (!w5e_finite($selectorDenominator) || abs($selectorDenominator) < 1.17549435e-38f) { return $zero; }\n")
            val localX = scalar(graph.localXF32)
            val localY = scalar(graph.localYF32)
            val checks = (0..1).map { axisI32 ->
                val value = "pixelLocal[$axisI32]"
                val increasing = "w5eImage.cellDirections[$axisI32] == ${ImageCellAxisDirectionV1.Increasing.flagF32}f"
                "(outerEdges[$axisI32] == 1.0 || select($value <= cellBounds[$axisI32], $value >= cellBounds[$axisI32], $increasing)) && " +
                    "(outerEdges[${axisI32 + 2}] == 1.0 || select($value > cellBounds[${axisI32 + 2}], $value < cellBounds[${axisI32 + 2}], $increasing))"
            }.joinToString(" && ")
            val dispatch = (0 until selector.capacityI32).joinToString("\n") { indexI32 ->
                """
                    if (w5eImage.parameters.w > ${indexI32.toFloat()}f) {
                        let candidate = w5eImage.cells[$indexI32];
                        if (w5e_cell_contains(cellLocal, candidate.bounds, candidate.outerEdges)) {
                            return w5e_sample_cell(pixel, candidate.source, candidate.destination);
                        }
                    }
                """.trimIndent()
            }
            """
                fn w5e_cell_contains(pixelLocal: vec2<f32>, cellBounds: vec4<f32>, outerEdges: vec4<f32>) -> bool {
                    return $checks;
                }
                fn w5e_image_sample(pixel: vec2<f32>) -> $returnType {
                    $body
                    if (!w5e_finite($localX) || !w5e_finite($localY)) { return $zero; }
                    let cellLocal = vec2<f32>($localX, $localY);
                    $dispatch
                    discard;
                    return $zero;
                }
            """.trimIndent()
        }.orEmpty()
        val denominator = emit(graph.denominator)
        statements.append("if (!w5e_finite($denominator) || abs($denominator) < 1.17549435e-38f) { return $zero; }\n")
        val x = emit(graph.sourceX)
        val y = emit(graph.sourceY)
        val tapX = emit(graph.tapXF32)
        val tapY = emit(graph.tapYF32)
        val baseX = emit(graph.baseXF32, afterValidity = true)
        val baseY = emit(graph.baseYF32, afterValidity = true)
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
                if (!w5e_finite($tapX) || !w5e_finite($tapY) || $tapX < -2147483648.0 || $tapX >= 2147483648.0 ||
                    $tapY < -2147483648.0 || $tapY >= 2147483648.0) { return $zero; }
                $afterValidityStatements
                return w5e_texel(i32($baseX), i32($baseY));
            """.trimIndent()
            ImageSamplingPlanV1.Linear -> {
                val fractionX = emit(requireNotNull(graph.fractionXF32), afterValidity = true)
                val fractionY = emit(requireNotNull(graph.fractionYF32), afterValidity = true)
                val weight00 = emit(requireNotNull(graph.weight00F32), afterValidity = true)
                val weight10 = emit(requireNotNull(graph.weight10F32), afterValidity = true)
                val weight01 = emit(requireNotNull(graph.weight01F32), afterValidity = true)
                val weight11 = emit(requireNotNull(graph.weight11F32), afterValidity = true)
                """
                    if (!w5e_finite($tapX) || !w5e_finite($tapY) || $tapX < -2147483648.0 || $tapX >= 2147483647.0 ||
                        $tapY < -2147483648.0 || $tapY >= 2147483647.0) { return $zero; }
                    $afterValidityStatements
                    let baseXi32 = i32($baseX);
                    let baseYi32 = i32($baseY);
                    let tap00 = w5e_texel(baseXi32, baseYi32);
                    let tap10 = w5e_texel(baseXi32 + 1, baseYi32);
                    let tap01 = w5e_texel(baseXi32, baseYi32 + 1);
                    let tap11 = w5e_texel(baseXi32 + 1, baseYi32 + 1);
                    return tap00 * $weight00 + tap10 * $weight10 + tap01 * $weight01 + tap11 * $weight11;
                """.trimIndent()
            }
            is ImageSamplingPlanV1.Cubic -> {
                graph.cubicWeightsF32.forEach { emit(it, afterValidity = true) }
                val accumulated = emit(requireNotNull(graph.cubicAccumulationF32), afterFetch = true)
                val taps = buildString {
                    for (row in -1..2) for (column in -1..2) {
                        append("let tap${row + 1}${column + 1} = w5e_texel(baseXi32 + $column, baseYi32 + $row);\n")
                    }
                }
                """
                    if (!w5e_finite($tapX) || !w5e_finite($tapY) || $tapX < -2147483647.0 || $tapX >= 2147483646.0 ||
                        $tapY < -2147483647.0 || $tapY >= 2147483646.0) { return $zero; }
                    $afterValidityStatements
                    let baseXi32 = i32($baseX);
                    let baseYi32 = i32($baseY);
                    $taps
                    $afterFetchStatements
                    return $accumulated;
                """.trimIndent()
            }
        }
        return """
            ${child?.declarationsWgsl.orEmpty()}
            ${if (selection != null) "struct W5eImageCell { source: vec4<f32>, destination: vec4<f32>, outerEdges: vec4<f32>, bounds: vec4<f32>, }" else ""}
            struct W5eImageBlock {
                values0: vec4<f32>, values1: vec4<f32>, values2: vec4<f32>,
                values3: vec4<f32>, values4: vec4<f32>, parameters: vec4<f32>, cubicParameters: vec4<f32>,
                ${if (selection != null) "cellDirections: vec4<f32>, cells: array<W5eImageCell, ${selection.capacityI32}>," else ""}
                ${if (child != null) "child: W5aMaterialBlock," else ""}
            }
            @group(1) @binding(${layout.uniformBindingU32}) var<uniform> w5eImage: W5eImageBlock;
            @group(1) @binding(${layout.imageTextureBindingU32}) var w5eTexture: texture_2d<f32>;
            ${if (child == null) W5aMaterialSourceStage.SRGB_TO_LINEAR_WGSL else ""}
            fn w5e_finite(value: f32) -> bool { return (bitcast<u32>(value) & 0x7f800000u) != 0x7f800000u; }
            fn w5e_device_point(pixel: vec2<f32>) -> vec2<f32> { return pixel; }
            $cubicKernelDeclarations
            fn w5e_texel(ix: i32, iy: i32) -> $returnType {
                $addressX
                $addressY
                let encoded: vec4<f32> = textureLoad(w5eTexture, vec2<i32>(ax, ay), 0);
                $evaluateEncodedTexel
            }
            fn ${if (selection == null) "w5e_image_sample(pixel: vec2<f32>)" else "w5e_sample_cell(pixel: vec2<f32>, cellSource: vec4<f32>, cellDestination: vec4<f32>)"} -> $returnType {
                $statements
                // Validity dominates floor and every signed integer conversion, including dynamic faults.
                $sample
            }
            $selectorDeclarations
            fn kanvas_material_source(pixel: vec2<f32>) -> vec4<f32> {
                ${if (child == null) "return w5e_image_sample(pixel) * w5eImage.parameters.z;" else
                    "return w5e_child_source(" + (if (child.gradientStopSlab == null) "pixel" else child.coordinateFunctionName + "(pixel)") + ") * w5e_image_sample(pixel);"}
            }
        """.trimIndent()
    }
}
