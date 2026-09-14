package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.materialPlanRef
import org.graphiks.kanvas.gpu.plan.colorSourceCoordinatesV4
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1.Operation
import org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV1
import org.graphiks.kanvas.gpu.plan.MaterialCoordinateOperationV2
import org.graphiks.kanvas.gpu.plan.GradientStopSlabPlanV1
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.GradientNumericDomainProofV1

/** Typed placement of the sole admitted child fragment; standalone V1/V2 ABI stays exact. */
internal enum class W5aSourceEmissionLayoutV1(val uniformExpression: String, val sourceFunction: String,
    val declaresUniform: Boolean) {
    Standalone("w5aMaterial", "kanvas_material_source", true),
    ImageChild("w5eImage.child", "w5e_child_source", false),
}

/**
 * Renderer-only lowering of the sealed numeric DAG. No source color is evaluated on the host.
 * The remaining DAG is authenticated to the sRGB, single-sample, premultiplied SRC_OVER
 * attachment partition before any source-stage code or raw binding is published.
 */
internal class W5aMaterialSourceStage private constructor(
    requirements: RawMaterialRequirementsV2,
    val declarationsWgsl: String,
    val bindingCountI32: Int,
    val provenOpaque: Boolean,
    val gradientStopSlab: GradientStopSlabPlanV1?,
    val coordinateFunctionName: String = "w5c_local_point",
    val imageV3: org.graphiks.kanvas.gpu.plan.ImageSampleExecutionPlanV1? = null,
) {
    data class Binding(val bindingI32: Int, val resourceKind: String)
    val imageLayoutV3 = requirements.imageLayoutV3
    val bindingManifest: List<Binding> = listOf(Binding(0, "uniformBuffer")) +
        (if (gradientStopSlab == null) emptyList() else listOf(Binding(imageLayoutV3?.gradientStorageBindingU32?.toInt() ?: 1, "storageBuffer"))) +
        (imageLayoutV3?.let { listOf(Binding(it.imageTextureBindingU32.toInt(), "sampledTexture")) } ?: emptyList())
    val structuralId: String = requirements.structuralId
    private val ownedUniformBytes = requirements.copyUniformBytes()
    val uniformBytes: ByteArray get() = ownedUniformBytes.copyOf()
    val uniformByteCountI64: Long get() = ownedUniformBytes.size.toLong()
    val canonicalIdentity: String = requirements.canonicalIdentity
    companion object {
        fun colorV4(table: MaterialPlanTable, authority: org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority,
            requirements: RawMaterialRequirementsV2): W5aMaterialSourceStage? {
            val ref = authority.materialPlanRef()
            val coordinates = authority.colorSourceCoordinatesV4() ?: return null
            val proof = table.colorSourceProofV4(ref)
            if (!proof.authenticates(table,ref,coordinates) ||
                requirements.structuralId != table.entry(ref).program.structuralId.value ||
                !requirements.canonicalIdentity.endsWith("material-source-footprint-v4:${proof.canonicalIdentity}")) return null
            val wordsI64 = requirements.uniformByteCountI64 / 16L
            if (requirements.uniformByteCountI64 % 16L != 0L || wordsI64 !in 1L..Int.MAX_VALUE.toLong()) return null
            val code = W5fColorOperationEmitterV1.emit(proof.copyOperationGraph(),"vec4<f32>(0.0)",0L)
            val slab = proof.gradientStopSlab
            if (slab != null && slab !== table.gradientStopSlab) return null
            val image = proof.imageExecution
            val imageLayout = proof.imageLayout
            if ((image == null) != (requirements.imageLayoutV3 == null) ||
                requirements.imageLayoutV3?.structuralIdentity != imageLayout?.structuralIdentity) return null
            val stopDeclaration = if (slab == null) "" else """
                struct GradientStopV1 { positionAndReserved: vec4<f32>, straightColor: vec4<f32>, }
                @group(1) @binding(${imageLayout?.gradientStorageBindingU32 ?: 1u}) var<storage, read> w5cStops: array<GradientStopV1>;
            """.trimIndent()
            val imageDeclaration = if (image == null) "" else """
                @group(1) @binding(${requireNotNull(imageLayout).imageTextureBindingU32}) var w5eTexture: texture_2d<f32>;
                ${W5eImageTexelEvaluatorV1.addressDeclarations(image.numericAuthority.graph)}
            """.trimIndent()
            return W5aMaterialSourceStage(requirements,"""
                struct W5fMaterialBlock { words: array<vec4<u32>, ${wordsI64}>, }
                @group(1) @binding(0) var<uniform> w5fMaterial: W5fMaterialBlock;
                $stopDeclaration
                $imageDeclaration
                $W5D_SAFE_DIVIDE_WGSL
                fn w5f_device_point(pixel: vec2<f32>) -> vec2<f32> { return pixel; }
                fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                    $code
                }
            """.trimIndent(),requirements.bindingCountI32,false,slab,"w5f_device_point",image)
        }
        fun imageV3(table: MaterialPlanTable, root: MaterialPlanRef): W5aMaterialSourceStage {
            val execution = (table.entry(root).bindings as org.graphiks.kanvas.gpu.plan.ImageSampleV3).execution
            require(table.authenticatesImage(root, execution)) { org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics.InvalidContract }
            val child = when (val authority = table.imageChildAuthority(root)) {
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV1 ->
                    requireNotNull(lower(table, authority.ref, authority.coordinates, W5aSourceEmissionLayoutV1.ImageChild))
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV2 ->
                    requireNotNull(lower(table, authority.ref, authority.coordinates, W5aSourceEmissionLayoutV1.ImageChild))
                null -> null
                else -> error("Unsupported image child authority")
            }
            val requirements = RawMaterialRequirementsV2.of(table, root)
            return W5aMaterialSourceStage(requirements, W5eImageTexelEvaluatorV1.declarations(execution, child,
                requireNotNull(requirements.imageLayoutV3)),
                requirements.bindingCountI32, false, child?.gradientStopSlab, "w5e_device_point", execution)
        }
        fun lower(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: MaterialCoordinatePlanV1? = null,
            layout: W5aSourceEmissionLayoutV1 = W5aSourceEmissionLayoutV1.Standalone): W5aMaterialSourceStage? {
            if (root.indexI32 !in 0 until table.sizeI32) return null
            val chain = mutableListOf<Pair<NumericOperationGraphV1.Node, MaterialBindingPlan>>()
            var ref = root
            while (true) {
                val entry = table.entry(ref)
                val source = sourceForFixedFunctionTail(entry.program.copyNumericOperationGraphV1()) ?: return null
                chain += source to entry.bindings
                if (entry.bindings !is MaterialBindingPlan.OpacityF32V1) break
                if (ref.indexI32 == 0) return null
                ref = MaterialPlanRef(ref.indexI32 - 1)
            }
            val requirements = RawMaterialRequirementsV2.of(table, root)
            if (chain.size != requirements.bindingCountI32 || requirements.uniformByteCountI64 > Int.MAX_VALUE) return null
            val statements = StringBuilder()
            val gradientBinding = chain.map { it.second }.filterIsInstance<MaterialBindingPlan.GradientV1>().singleOrNull()
            if (gradientBinding != null && coordinates == null) return null
            val gradientGraph = if (gradientBinding == null) null else {
                val numeric = gradientBinding.numericAuthority
                if (!numeric.authenticates(table.entry(ref).program, gradientBinding,
                    requireNotNull(table.gradientStopSlab), requireNotNull(coordinates))) return null
                numeric.graph
            }
            var child: String? = null
            var opaque = true
            var nextValueI32 = 0
            for ((bindingIndexI32, pair) in chain.asReversed().withIndex()) {
                val (source, binding) = pair
                val input = "${layout.uniformExpression}.binding$bindingIndexI32"
                when (binding) {
                    is org.graphiks.kanvas.gpu.plan.ComposedMaterialBindingV5 -> return null
                    is org.graphiks.kanvas.gpu.plan.ColorFilterBindingV4 -> return null
                    is org.graphiks.kanvas.gpu.plan.GradientInterpolationBindingV4 -> return null
                    is org.graphiks.kanvas.gpu.plan.ImageSampleV3 -> return null
                    is MaterialBindingPlan.GradientV2 -> return null
                    is MaterialBindingPlan.GradientV1 -> {
                        opaque = opaque && binding !is MaterialBindingPlan.ConicalGradientV1 &&
                            requireNotNull(table.gradientStopSlab).copyStops().all { it.straightSrgbF32.alpha == 1f }
                    }
                    MaterialBindingPlan.EmptyV1 -> { opaque = false }
                    is MaterialBindingPlan.SolidRgbaF32V1 -> {
                        val color = binding.copyRgbaF32()
                        val values = listOf(color.red, color.green, color.blue, color.alpha)
                        if (values.any { !it.isFinite() || it !in 0f..1f }) return null
                        opaque = opaque && color.alpha == 1f
                    }
                    is MaterialBindingPlan.OpacityF32V1 -> {
                        opaque = opaque && binding.alphaF32 == 1f
                    }
                }
                fun emit(node: NumericOperationGraphV1.Node): String? {
                    val inputs = node.inputs.map { emit(it) ?: return null }
                    val expression = when (node.operation) {
                        Operation.CONSTANT_TRANSPARENT -> "vec4<f32>(0.0)"
                        Operation.INPUT_SOLID_SRGBA_STRAIGHT -> if (binding is MaterialBindingPlan.SolidRgbaF32V1) input else return null
                        Operation.INPUT_GRADIENT_SRGBA_STRAIGHT -> if (binding is MaterialBindingPlan.GradientV1 && gradientGraph != null)
                            "w5c_gradient(localPosition)" else return null
                        Operation.INPUT_MATERIAL_LINEAR_PREMUL -> if (binding is MaterialBindingPlan.OpacityF32V1) child ?: return null else return null
                        Operation.SRGB_TO_LINEAR -> "w5a_srgb_to_linear(${inputs.single()})"
                        Operation.PREMULTIPLY -> "vec4<f32>(${inputs.single()}.rgb * ${inputs.single()}.a, ${inputs.single()}.a)"
                        Operation.OPACITY_F32 -> if (binding is MaterialBindingPlan.OpacityF32V1) "${inputs.single()} * $input.x" else return null
                        else -> return null
                    }
                    val name = "w5aValue${nextValueI32++}"
                    statements.append("    let $name = $expression;\n")
                    return name
                }
                child = emit(source) ?: return null
            }
            val declarations = """
                struct W5aMaterialBlock {
                ${chain.indices.joinToString("\n") { "    binding$it: vec4<f32>," }}
                ${if (gradientBinding == null) "" else "    gradientHeader: vec4<u32>,\n    gradientFlags: vec4<u32>,\n" +
                    (if (gradientBinding is MaterialBindingPlan.LinearGradientV1)
                        "    linearParameters0: vec4<f32>,\n    linearParameters1: vec4<f32>,\n" else "") +
                    (if (gradientBinding is MaterialBindingPlan.SweepGradientV1) "    sweepParameters: vec4<f32>,\n" else "") +
                    (if (gradientBinding is MaterialBindingPlan.ConicalGradientV1)
                        "    conicalParameters0: vec4<f32>,\n    conicalParameters1: vec4<f32>,\n" +
                            "    conicalParameters2: vec4<f32>,\n    conicalParameters3: vec4<f32>,\n" +
                            "    conicalFlags0: vec4<u32>,\n    conicalFlags1: vec4<u32>,\n" else "") +
                    "    inverseRow0: vec4<f32>,\n    inverseRow1: vec4<f32>,\n    inverseRow2: vec4<f32>,"}
                }
                ${if (layout.declaresUniform) "@group(1) @binding(0) var<uniform> w5aMaterial: W5aMaterialBlock;" else ""}

                $SRGB_TO_LINEAR_WGSL
                ${gradientGraph?.let { gradientDeclarationsWgsl(it, layout = layout) }.orEmpty()}

                fn ${layout.sourceFunction}(localPosition: vec2<f32>) -> vec4<f32> {
                $statements
                    return $child;
                }
            """.trimIndent()
            return W5aMaterialSourceStage(requirements,
                declarations, chain.size, opaque, table.gradientStopSlab.takeIf { gradientBinding != null })
        }


        /** V2 is verified directly; the V1 overload never receives a V2 numeric authority. */
        fun lower(table: MaterialPlanTable, root: MaterialPlanRef,
            coordinates: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV2,
            layout: W5aSourceEmissionLayoutV1 = W5aSourceEmissionLayoutV1.Standalone): W5aMaterialSourceStage? {
            if (root.indexI32 !in 0 until table.sizeI32) return null
            val opacities = mutableListOf<MaterialBindingPlan.OpacityF32V1>()
            var ref = root
            while (table.entry(ref).bindings is MaterialBindingPlan.OpacityF32V1) {
                opacities += table.entry(ref).bindings as MaterialBindingPlan.OpacityF32V1
                if (ref.indexI32 == 0) return null
                ref = MaterialPlanRef(ref.indexI32 - 1)
            }
            val entry = table.entry(ref)
            val program = entry.program as? org.graphiks.kanvas.gpu.plan.GradientAddressingProgramV2 ?: return null
            val binding = entry.bindings as? MaterialBindingPlan.GradientV2 ?: return null
            val slab = table.gradientStopSlab ?: return null
            val numeric = binding.numericAuthority
            if (!numeric.authenticates(program, binding, slab, coordinates)) return null
            val requirements = RawMaterialRequirementsV2.of(table, root)
            if (requirements.uniformByteCountI64 > Int.MAX_VALUE) return null
            val operations = coordinates.copyOperations()
            val coordinateFields = StringBuilder()
            val coordinateStatements = StringBuilder("    var state = W5dLocalPointV2(pixel, true);\n")
            for (indexI32 in operations.indices) {
                when (operations[indexI32]) {
                    is MaterialCoordinateOperationV2.ClampRectF32 -> {
                        coordinateFields.append("    coordinate${indexI32}Clamp: vec4<f32>,\n")
                        coordinateStatements.append("    state.pointF32 = clamp(state.pointF32, ${layout.uniformExpression}.coordinate${indexI32}Clamp.xy, ${layout.uniformExpression}.coordinate${indexI32}Clamp.zw);\n")
                    }
                    is MaterialCoordinateOperationV2.InverseMatrixF32 -> {
                        repeat(3) { rowI32 ->
                            coordinateFields.append("    coordinate${indexI32}Row$rowI32: vec4<f32>,\n")
                            coordinateStatements.append("    let h${indexI32}Row$rowI32 = (${layout.uniformExpression}.coordinate${indexI32}Row$rowI32.x * state.pointF32.x + ${layout.uniformExpression}.coordinate${indexI32}Row$rowI32.y * state.pointF32.y) + ${layout.uniformExpression}.coordinate${indexI32}Row$rowI32.z;\n")
                        }
                        coordinateStatements.append("""
                            if (${layout.uniformExpression}.coordinate${indexI32}Row2.w == 1.0) {
                                let affinePointF32 = vec2<f32>(h${indexI32}Row0, h${indexI32}Row1);
                                state.valid = state.valid && all((bitcast<vec2<u32>>(affinePointF32) & vec2<u32>(0x7f800000u)) != vec2<u32>(0x7f800000u));
                                state.pointF32 = select(vec2<f32>(0.0), affinePointF32, state.valid);
                            } else {
                                let projected${indexI32}X = w5dSafeDivideF32(h${indexI32}Row0, h${indexI32}Row2);
                                let projected${indexI32}Y = w5dSafeDivideF32(h${indexI32}Row1, h${indexI32}Row2);
                                state.valid = state.valid && projected${indexI32}X.valid && projected${indexI32}Y.valid;
                                state.pointF32 = select(vec2<f32>(0.0), vec2<f32>(projected${indexI32}X.valueF32, projected${indexI32}Y.valueF32), state.valid);
                            }

                        """.trimIndent())
                    }
                }
            }
            val statements = StringBuilder("    let straight = w5c_gradient(localPosition);\n" +
                "    let linear = w5a_srgb_to_linear(straight);\n" +
                "    let value0 = vec4<f32>(linear.rgb * linear.a, linear.a);\n")
            for (indexI32 in opacities.indices) {
                statements.append("    let value${indexI32 + 1} = value$indexI32 * ${layout.uniformExpression}.binding${indexI32 + 1}.x;\n")
            }
            val declarations = """
                struct W5aMaterialBlock {
                ${(0..opacities.size).joinToString("\n") { "    binding$it: vec4<f32>," }}
                    gradientHeader: vec4<u32>,
                    gradientFlags: vec4<u32>,
                    ${when (binding) {
                        is MaterialBindingPlan.LinearGradientV2 -> "linearParameters0: vec4<f32>,\nlinearParameters1: vec4<f32>,"
                        is MaterialBindingPlan.RadialGradientV2 -> ""
                        is MaterialBindingPlan.SweepGradientV2 -> "sweepParameters: vec4<f32>,"
                        is MaterialBindingPlan.ConicalGradientV2 -> "conicalParameters0: vec4<f32>,\nconicalParameters1: vec4<f32>,\n" +
                            "conicalParameters2: vec4<f32>,\nconicalParameters3: vec4<f32>,\nconicalFlags0: vec4<u32>,\nconicalFlags1: vec4<u32>,"
                    }}
                    ${if (program.consumesDegenerateAverage) "degenerateAverageSrgbaF32: vec4<f32>," else ""}
                    $coordinateFields
                }
                ${if (layout.declaresUniform) "@group(1) @binding(0) var<uniform> w5aMaterial: W5aMaterialBlock;" else ""}
                $SRGB_TO_LINEAR_WGSL
                ${gradientDeclarationsWgsl(numeric.graph, numeric.tileGraph, program.consumesDegenerateAverage, layout)}
                $W5D_SAFE_DIVIDE_WGSL
                struct W5dLocalPointV2 { pointF32: vec2<f32>, valid: bool, }
                fn w5d_local_point(pixel: vec2<f32>) -> W5dLocalPointV2 {
                    $coordinateStatements
                    return state;
                }
                fn ${layout.sourceFunction}(localPoint: W5dLocalPointV2) -> vec4<f32> {
                    if (!localPoint.valid) { return vec4<f32>(0.0); }
                    let localPosition = localPoint.pointF32;
                    $statements
                    return value${opacities.size};
                }
            """.trimIndent()
            return W5aMaterialSourceStage(requirements,
                declarations, requirements.bindingCountI32,
                binding !is MaterialBindingPlan.ConicalGradientV2 &&
                    numeric.tileGraph.effectiveMode != org.graphiks.kanvas.gpu.plan.GradientTileModeV2.DECAL &&
                    opacities.all { it.alphaF32 == 1f } && slab.copyStops().all { it.straightSrgbF32.alpha == 1f } &&
                    operations.filterIsInstance<MaterialCoordinateOperationV2.InverseMatrixF32>().all {
                        it.inverseF32.persp0 == 0f && it.inverseF32.persp1 == 0f && it.inverseF32.persp2 == 1f },
                slab, "w5d_local_point")
        }

        private val W5D_SAFE_DIVIDE_WGSL: String = """
            struct W5dBinaryPartsF32 {
                fractionF32: f32,
                exponentI32: i32,
                valid: bool,
            }
            struct W5dSafeDivideResultF32 { valueF32: f32, valid: bool, }
            fn w5dBinaryPartsF32(valueF32: f32) -> W5dBinaryPartsF32 {
                let bitsU32 = bitcast<u32>(valueF32);
                let absBitsU32 = bitsU32 & 0x7fffffffu;
                let exponentBitsU32 = (absBitsU32 >> 23u) & 0xffu;
                let mantissaBitsU32 = absBitsU32 & 0x007fffffu;
                if (exponentBitsU32 == 0xffu || absBitsU32 == 0u) {
                    return W5dBinaryPartsF32(0.0, 0, exponentBitsU32 != 0xffu);
                }
                var fractionBitsU32 = (bitsU32 & 0x80000000u) | (126u << 23u) | mantissaBitsU32;
                var exponentI32 = i32(exponentBitsU32) - 126;
                if (exponentBitsU32 == 0u) {
                    let leadingI32 = 31 - i32(countLeadingZeros(mantissaBitsU32));
                    let normalizedU32 = mantissaBitsU32 << u32(23 - leadingI32);
                    fractionBitsU32 = (bitsU32 & 0x80000000u) | (126u << 23u) |
                        (normalizedU32 & 0x007fffffu);
                    exponentI32 = leadingI32 - 148;
                }
                return W5dBinaryPartsF32(bitcast<f32>(fractionBitsU32), exponentI32, true);
            }
            fn w5dSafeDivideF32(numeratorF32: f32, denominatorF32: f32) -> W5dSafeDivideResultF32 {
                let numerator = w5dBinaryPartsF32(numeratorF32);
                let denominator = w5dBinaryPartsF32(denominatorF32);
                if (!numerator.valid || !denominator.valid || denominator.fractionF32 == 0.0) {
                    return W5dSafeDivideResultF32(0.0, false);
                }
                if (numerator.fractionF32 == 0.0) {
                    return W5dSafeDivideResultF32(0.0, true);
                }
                let fractionQuotientF32 = numerator.fractionF32 / denominator.fractionF32;
                let normalized = frexp(fractionQuotientF32);
                let resultExponentI32 = numerator.exponentI32 - denominator.exponentI32 + normalized.exp;
                if (resultExponentI32 > 128) {
                    return W5dSafeDivideResultF32(0.0, false);
                }
                return W5dSafeDivideResultF32(ldexp(normalized.fract, resultExponentI32), true);
            }
        """.trimIndent()

        /** Exact partition proof; altered blend/coverage/destination/attachment graphs fail closed. */
        fun sourceForFixedFunctionTail(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node? {
            if (graph.contractId != "WgslFloatEnvelopeV1") return null
            fun NumericOperationGraphV1.Node.input(operation: Operation): NumericOperationGraphV1.Node? =
                takeIf { this.operation == operation }?.inputs?.singleOrNull()
            val covered = graph.root.input(Operation.QUANTIZE_UNORM8)
                ?.input(Operation.CLAMP_01)?.input(Operation.LINEAR_TO_SRGB_ATTACHMENT) ?: return null
            if (covered.operation != Operation.APPLY_COVERAGE_F32) return null
            val (destination, blend, coverage) = covered.inputs
            if (destination != NumericOperationGraphV1.Node(Operation.INPUT_DESTINATION_LINEAR_PREMUL) ||
                coverage != NumericOperationGraphV1.Node(Operation.INPUT_COVERAGE_F32) ||
                blend.operation != Operation.SRC_OVER || blend.inputs[1] != destination) return null
            return blend.inputs[0]
        }

        // One source-stage implementation shared by all renderer consumers of this DAG opcode.
        val SRGB_TO_LINEAR_WGSL: String = """
            fn w5a_srgb_channel_to_linear(value: f32) -> f32 {
                if (value == 0.0) { return 0.0; }
                if (value == 1.0) { return 1.0; }
                if (value <= 0.04045) { return value / 12.92; }
                return pow((value + 0.055) / 1.055, 2.4);
            }
            fn w5a_srgb_to_linear(value: vec4<f32>) -> vec4<f32> {
                return vec4<f32>(w5a_srgb_channel_to_linear(value.r),
                    w5a_srgb_channel_to_linear(value.g), w5a_srgb_channel_to_linear(value.b), value.a);
            }
        """.trimIndent()

        /** Lowers every executed gradient node, including the explicit bounded search body. */
        private fun gradientDeclarationsWgsl(graph: GradientNumericOperationGraphV1,
            tileGraph: org.graphiks.kanvas.gpu.plan.GradientTileOperationGraphV2? = null,
            consumesDegenerateAverage: Boolean = false, layout: W5aSourceEmissionLayoutV1 = W5aSourceEmissionLayoutV1.Standalone): String {
            require(graph.contractId == "WgslFloatEnvelopeV1" && graph.domainProof == GradientNumericDomainProofV1.ProvenFinite)
            val code = StringBuilder()
            val emitted = mutableMapOf<GradientNumericOperationGraphV1.Node, String>()
            var ordinalI32 = 0
            fun emit(node: GradientNumericOperationGraphV1.Node): String = emitted.getOrPut(node) {
                // A control-flow mask dominates tile/search, including every stop load.
                if (node.operation == GradientNumericOperationGraphV1.Operation.VALIDITY_MASK) {
                    val valid = emit(node.inputs[1])
                    code.append("if (!$valid) { return vec4<f32>(0.0); }\n")
                    return@getOrPut emit(node.inputs[0])
                }
                val args = node.inputs.map(::emit)
                val name = "gradientValue${ordinalI32++}"
                val expression = when (node.operation) {
                    GradientNumericOperationGraphV1.Operation.INPUT_LOCAL_POINT_F32 ->
                        if (node.input == GradientNumericOperationGraphV1.Input.X) "localPosition.x" else "localPosition.y"
                    GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_F32 -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.START_X -> "${layout.uniformExpression}.binding0.x"
                        GradientNumericOperationGraphV1.Input.START_Y -> "${layout.uniformExpression}.binding0.y"
                        GradientNumericOperationGraphV1.Input.END_X -> "${layout.uniformExpression}.binding0.z"
                        GradientNumericOperationGraphV1.Input.END_Y -> "${layout.uniformExpression}.binding0.w"
                        GradientNumericOperationGraphV1.Input.LINEAR_DX -> "${layout.uniformExpression}.linearParameters0.x"
                        GradientNumericOperationGraphV1.Input.LINEAR_DY -> "${layout.uniformExpression}.linearParameters0.y"
                        GradientNumericOperationGraphV1.Input.LINEAR_LEN2 -> "${layout.uniformExpression}.linearParameters1.x"
                        GradientNumericOperationGraphV1.Input.CENTER_X -> "${layout.uniformExpression}.binding0.x"
                        GradientNumericOperationGraphV1.Input.CENTER_Y -> "${layout.uniformExpression}.binding0.y"
                        GradientNumericOperationGraphV1.Input.RADIUS -> "${layout.uniformExpression}.binding0.z"
                        GradientNumericOperationGraphV1.Input.START_DEGREES -> "${layout.uniformExpression}.binding0.z"
                        GradientNumericOperationGraphV1.Input.END_DEGREES -> "${layout.uniformExpression}.binding0.w"
                        GradientNumericOperationGraphV1.Input.SPAN_DEGREES -> "${layout.uniformExpression}.sweepParameters.x"
                        GradientNumericOperationGraphV1.Input.MIN_NORMAL -> "1.17549435e-38f"
                        GradientNumericOperationGraphV1.Input.TWO_PI -> "6.2831855f"
                        GradientNumericOperationGraphV1.Input.QUARTER -> "0.25"
                        GradientNumericOperationGraphV1.Input.HALF -> "0.5"
                        GradientNumericOperationGraphV1.Input.THREE_QUARTERS -> "0.75"
                        GradientNumericOperationGraphV1.Input.FULL_TURN_DEGREES -> "360.0"
                        GradientNumericOperationGraphV1.Input.ZERO -> "0.0"
                        GradientNumericOperationGraphV1.Input.ONE -> "1.0"
                        GradientNumericOperationGraphV1.Input.TWO -> "2.0"
                        GradientNumericOperationGraphV1.Input.FOUR -> "4.0"
                        GradientNumericOperationGraphV1.Input.CONICAL_DX -> "${layout.uniformExpression}.conicalParameters0.x"
                        GradientNumericOperationGraphV1.Input.CONICAL_DY -> "${layout.uniformExpression}.conicalParameters0.y"
                        GradientNumericOperationGraphV1.Input.CONICAL_START_RADIUS -> "${layout.uniformExpression}.conicalParameters0.z"
                        GradientNumericOperationGraphV1.Input.CONICAL_END_RADIUS -> "${layout.uniformExpression}.conicalParameters0.w"
                        GradientNumericOperationGraphV1.Input.CONICAL_DR -> "${layout.uniformExpression}.conicalParameters1.x"
                        GradientNumericOperationGraphV1.Input.CONICAL_A -> "${layout.uniformExpression}.conicalParameters2.w"
                        else -> error("Unsupported sealed scalar input")
                    }
                    GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_FLAG -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.DEGENERATE -> "(${layout.uniformExpression}.gradientFlags.x != 0u)"
                        GradientNumericOperationGraphV1.Input.LEADING_SEGMENT -> "(${layout.uniformExpression}.gradientFlags.z != 0u)"
                        GradientNumericOperationGraphV1.Input.CONICAL_FULLY_DEGENERATE -> "(${layout.uniformExpression}.conicalFlags1.z == 0u)"
                        GradientNumericOperationGraphV1.Input.CONICAL_CONCENTRIC -> "(${layout.uniformExpression}.conicalFlags1.z == 1u)"
                        GradientNumericOperationGraphV1.Input.CONICAL_LINEAR_EQUATION -> "(${layout.uniformExpression}.conicalFlags1.z == 2u)"
                        GradientNumericOperationGraphV1.Input.CONICAL_QUADRATIC -> "(${layout.uniformExpression}.conicalFlags1.z == 3u)"
                        GradientNumericOperationGraphV1.Input.CONICAL_SHARED_RADIUS_ABOVE_EPSILON -> "(${layout.uniformExpression}.conicalFlags1.y != 0u)"
                        else -> error("Unsupported sealed flag input")
                    }
                    GradientNumericOperationGraphV1.Operation.INPUT_STOP_RANGE_U32 -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.STOPS -> "${layout.uniformExpression}.gradientHeader.xy"
                        GradientNumericOperationGraphV1.Input.PROBE -> "gradientProbe"
                        GradientNumericOperationGraphV1.Input.ZERO -> "0u"
                        else -> error("Unsupported sealed index input")
                    }
                    GradientNumericOperationGraphV1.Operation.ADD_F32 -> "(${args[0]} + ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.SUB_F32 -> "(${args[0]} - ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.MUL_F32 -> "(${args[0]} * ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.DIV_F32,
                    GradientNumericOperationGraphV1.Operation.ROOT_DIV_F32 -> "(${args[0]} / ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.FINITE_ROOT_OR_ZERO_F32 -> "select(0.0, ${args[0]}, ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.ROOT_RADIUS_MUL_F32 -> "(${args[0]} * ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.ROOT_RADIUS_ADD_F32 -> "(${args[0]} + ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.ROOT_RADIUS_POSITIVE -> "(${args.single()} > 0.0)"
                    GradientNumericOperationGraphV1.Operation.SQRT_F32 -> "sqrt(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.ATAN2_F32 -> "atan2(${args[0]}, ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.FLOOR_F32 -> "floor(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.ABS_F32 -> "abs(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.MAX_F32 -> "max(${args[0]}, ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.COMPARE_F32 -> "(${args[0]} ${if (node.lessOrEqual) "<=" else "<"} ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.SELECT -> "select(${args[0]}, ${args[1]}, ${args[2]})"
                    GradientNumericOperationGraphV1.Operation.FINITE_F32 -> "((bitcast<u32>(${args.single()}) & 0x7f800000u) != 0x7f800000u)"
                    GradientNumericOperationGraphV1.Operation.AND_FLAG -> "(${args[0]} && ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.OR_FLAG -> "(${args[0]} || ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.VALIDITY_MASK -> error("Mask is emitted before its color subtree")
                    GradientNumericOperationGraphV1.Operation.LOAD_STOP_POSITION_F32,
                    GradientNumericOperationGraphV1.Operation.LOAD_STOP_COLOR_SRGBA_F32 -> {
                        val index = if (node.relativeIndexI32 == -1) "max(${args[1]}, 1u) - 1u" else args[1]
                        "w5cStops[${args[0]}.x + min($index, ${args[0]}.y - 1u)]." +
                            if (node.operation == GradientNumericOperationGraphV1.Operation.LOAD_STOP_POSITION_F32) "positionAndReserved.x" else "straightColor"
                    }
                    GradientNumericOperationGraphV1.Operation.UPPER_BOUND_STOPS_V1 -> {
                        val body = requireNotNull(node.loopBody)
                        require(body.countBoundU32 == 65_538u && body.midpointUsesDifference && body.comparisonIsLessOrEqual)
                        code.append("var gradientLow = 0u; var gradientHigh = ${args[0]}.y;\n")
                        code.append("for (var iteration = 0u; iteration < ${args[0]}.y; iteration = iteration + 1u) {\n")
                        code.append("if (gradientLow >= gradientHigh) { break; }\n")
                        code.append("let gradientProbe = gradientLow + (gradientHigh - gradientLow) / 2u;\n")
                        val comparison = emit(body.comparison)
                        code.append("if ($comparison) { gradientLow = gradientProbe + 1u; } else { gradientHigh = gradientProbe; }\n}\n")
                        "gradientLow"
                    }
                    GradientNumericOperationGraphV1.Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> {
                        require(node.clampInterpolationWeightToUnitInterval)
                        "w5c_interpolate(${args.joinToString(", ")})"
                    }
                }
                code.append("let $name = $expression;\n")
                name
            }
            val result = emit(graph.root)
            val coordinatesV1Wgsl = if (tileGraph != null) "" else """
                fn w5c_local_point(pixel: vec2<f32>) -> vec2<f32> {
                    let p = vec3<f32>(pixel, 1.0);
                    let x = (${layout.uniformExpression}.inverseRow0.x * p.x + ${layout.uniformExpression}.inverseRow0.y * p.y) + ${layout.uniformExpression}.inverseRow0.z;
                    let y = (${layout.uniformExpression}.inverseRow1.x * p.x + ${layout.uniformExpression}.inverseRow1.y * p.y) + ${layout.uniformExpression}.inverseRow1.z;
                    let w = (${layout.uniformExpression}.inverseRow2.x * p.x + ${layout.uniformExpression}.inverseRow2.y * p.y) + ${layout.uniformExpression}.inverseRow2.z;
                    if (w == 1.0) { return vec2<f32>(x, y); }
                    return vec2<f32>(x, y) / w;
                }
            """.trimIndent()
            return """
                struct GradientStopV1 { positionAndReserved: vec4<f32>, straightColor: vec4<f32>, }
                @group(1) @binding(1) var<storage, read> w5cStops: array<GradientStopV1>;
                $coordinatesV1Wgsl
                fn w5c_interpolate(left: vec4<f32>, right: vec4<f32>, low: f32, high: f32, t: f32) -> vec4<f32> {
                    if (high <= low) { return right; }
                    if (t == low || all(left == right)) { return left; }
                    let weight = clamp((t - low) / (high - low), 0.0, 1.0);
                    return left + (right - left) * weight;
                }
                fn w5c_gradient(localPosition: vec2<f32>) -> vec4<f32> {
                    ${when {
                        consumesDegenerateAverage -> "if (${layout.uniformExpression}.gradientFlags.x != 0u) { return ${layout.uniformExpression}.degenerateAverageSrgbaF32; }"
                        tileGraph?.effectiveMode == org.graphiks.kanvas.gpu.plan.GradientTileModeV2.DECAL ->
                            "if (${layout.uniformExpression}.gradientFlags.x != 0u) { return vec4<f32>(0.0); }"
                        else -> ""
                    }}
                    $code
                    return $result;
                }
            """.trimIndent()
        }
    }
}
