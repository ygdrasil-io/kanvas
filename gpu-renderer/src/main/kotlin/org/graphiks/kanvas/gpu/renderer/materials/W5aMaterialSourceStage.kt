package org.graphiks.kanvas.gpu.renderer.materials

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1.Operation
import org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV1
import org.graphiks.kanvas.gpu.plan.GradientStopSlabPlanV1
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.GradientNumericDomainProofV1

/**
 * Renderer-only lowering of the sealed numeric DAG. No source color is evaluated on the host.
 * The remaining DAG is authenticated to the sRGB, single-sample, premultiplied SRC_OVER
 * attachment partition before any source-stage code or raw binding is published.
 */
internal class W5aMaterialSourceStage private constructor(
    val structuralId: String,
    val declarationsWgsl: String,
    val bindingCountI32: Int,
    uniformBytes: ByteArray,
    val provenOpaque: Boolean,
    val gradientStopSlab: GradientStopSlabPlanV1?,
) {
    data class Binding(val bindingI32: Int, val resourceKind: String)
    val bindingManifest: List<Binding> = listOf(Binding(0, "uniformBuffer")) +
        if (gradientStopSlab == null) emptyList() else listOf(Binding(1, "storageBuffer"))
    private val ownedUniformBytes = uniformBytes.copyOf()
    val uniformBytes: ByteArray get() = ownedUniformBytes.copyOf()
    val uniformByteCountI64: Long get() = ownedUniformBytes.size.toLong()
    val canonicalIdentity: String = structuralId + ":raw-v2:" + ownedUniformBytes.joinToString(",") +
        (gradientStopSlab?.canonicalIdentity ?: "")
    companion object {
        fun lower(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: MaterialCoordinatePlanV1? = null): W5aMaterialSourceStage? {
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
            val uniforms = ByteBuffer.allocate(requirements.uniformByteCountI64.toInt()).order(ByteOrder.LITTLE_ENDIAN)
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
                val input = "w5aMaterial.binding$bindingIndexI32"
                when (binding) {
                    is MaterialBindingPlan.GradientV1 -> {
                        binding.copyUniformValuesF32().forEach(uniforms::putFloat)
                        opaque = opaque && requireNotNull(table.gradientStopSlab).copyStops().all { it.straightSrgbF32.alpha == 1f }
                    }
                    MaterialBindingPlan.EmptyV1 -> { repeat(4) { uniforms.putFloat(0f) }; opaque = false }
                    is MaterialBindingPlan.SolidRgbaF32V1 -> {
                        val color = binding.copyRgbaF32()
                        val values = listOf(color.red, color.green, color.blue, color.alpha)
                        if (values.any { !it.isFinite() || it !in 0f..1f }) return null
                        values.forEach(uniforms::putFloat)
                        opaque = opaque && color.alpha == 1f
                    }
                    is MaterialBindingPlan.OpacityF32V1 -> {
                        uniforms.putFloat(binding.alphaF32)
                        repeat(3) { uniforms.putFloat(0f) }
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
            if (gradientBinding != null) {
                uniforms.putInt(gradientBinding.stopRange.baseIndexU32.toInt()).putInt(gradientBinding.stopRange.countU32.toInt())
                    .putInt(0).putInt(0)
                val sweep = (gradientBinding as? MaterialBindingPlan.SweepGradientV1)?.degeneracy
                uniforms.putInt(if (gradientBinding.gradientDegenerate) 1 else 0)
                    .putInt(if (sweep?.sweepOrderingInvalid == true) 1 else 0)
                    .putInt(if (sweep?.sweepClampLeadingSegment == true) 1 else 0)
                    .putInt(if (sweep?.sweepFullCoverage == true) 1 else 0)
                if (sweep != null) {
                    uniforms.putFloat(sweep.sweepSpanDegreesF32)
                    repeat(3) { uniforms.putFloat(0f) }
                }
                val inverseF32 = requireNotNull(coordinates).copyInverseCtmF32()
                listOf(inverseF32.sx, inverseF32.kx, inverseF32.tx, 0f, inverseF32.ky, inverseF32.sy, inverseF32.ty, 0f,
                    inverseF32.persp0, inverseF32.persp1, inverseF32.persp2, 0f).forEach(uniforms::putFloat)
            }
            val declarations = """
                struct W5aMaterialBlock {
                ${chain.indices.joinToString("\n") { "    binding$it: vec4<f32>," }}
                ${if (gradientBinding == null) "" else "    gradientHeader: vec4<u32>,\n    gradientFlags: vec4<u32>,\n" +
                    (if (gradientBinding is MaterialBindingPlan.SweepGradientV1) "    sweepParameters: vec4<f32>,\n" else "") +
                    "    inverseRow0: vec4<f32>,\n    inverseRow1: vec4<f32>,\n    inverseRow2: vec4<f32>,"}
                }
                @group(1) @binding(0) var<uniform> w5aMaterial: W5aMaterialBlock;

                $SRGB_TO_LINEAR_WGSL
                ${gradientGraph?.let(::gradientDeclarationsWgsl).orEmpty()}

                fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                $statements
                    return $child;
                }
            """.trimIndent()
            return W5aMaterialSourceStage(table.entry(root).program.structuralId.value + ":srgb-endpoints-v1",
                declarations, chain.size, uniforms.array(), opaque, table.gradientStopSlab.takeIf { gradientBinding != null })
        }

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
        private fun gradientDeclarationsWgsl(graph: GradientNumericOperationGraphV1): String {
            require(graph.contractId == "WgslFloatEnvelopeV1" && graph.domainProof == GradientNumericDomainProofV1.ProvenFinite)
            val code = StringBuilder()
            val emitted = mutableMapOf<GradientNumericOperationGraphV1.Node, String>()
            var ordinalI32 = 0
            fun emit(node: GradientNumericOperationGraphV1.Node): String = emitted.getOrPut(node) {
                val args = node.inputs.map(::emit)
                val name = "gradientValue${ordinalI32++}"
                val expression = when (node.operation) {
                    GradientNumericOperationGraphV1.Operation.INPUT_LOCAL_POINT_F32 ->
                        if (node.input == GradientNumericOperationGraphV1.Input.X) "localPosition.x" else "localPosition.y"
                    GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_F32 -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.START_X -> "w5aMaterial.binding0.x"
                        GradientNumericOperationGraphV1.Input.START_Y -> "w5aMaterial.binding0.y"
                        GradientNumericOperationGraphV1.Input.END_X -> "w5aMaterial.binding0.z"
                        GradientNumericOperationGraphV1.Input.END_Y -> "w5aMaterial.binding0.w"
                        GradientNumericOperationGraphV1.Input.CENTER_X -> "w5aMaterial.binding0.x"
                        GradientNumericOperationGraphV1.Input.CENTER_Y -> "w5aMaterial.binding0.y"
                        GradientNumericOperationGraphV1.Input.RADIUS -> "w5aMaterial.binding0.z"
                        GradientNumericOperationGraphV1.Input.START_DEGREES -> "w5aMaterial.binding0.z"
                        GradientNumericOperationGraphV1.Input.END_DEGREES -> "w5aMaterial.binding0.w"
                        GradientNumericOperationGraphV1.Input.SPAN_DEGREES -> "w5aMaterial.sweepParameters.x"
                        GradientNumericOperationGraphV1.Input.MIN_NORMAL -> "1.17549435e-38f"
                        GradientNumericOperationGraphV1.Input.TWO_PI -> "6.2831855f"
                        GradientNumericOperationGraphV1.Input.QUARTER -> "0.25"
                        GradientNumericOperationGraphV1.Input.HALF -> "0.5"
                        GradientNumericOperationGraphV1.Input.THREE_QUARTERS -> "0.75"
                        GradientNumericOperationGraphV1.Input.FULL_TURN_DEGREES -> "360.0"
                        GradientNumericOperationGraphV1.Input.ZERO -> "0.0"
                        GradientNumericOperationGraphV1.Input.ONE -> "1.0"
                        else -> error("Unsupported sealed scalar input")
                    }
                    GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_FLAG -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.DEGENERATE -> "(w5aMaterial.gradientFlags.x != 0u)"
                        GradientNumericOperationGraphV1.Input.LEADING_SEGMENT -> "(w5aMaterial.gradientFlags.z != 0u)"
                        else -> error("Unsupported sealed flag input")
                    }
                    GradientNumericOperationGraphV1.Operation.INPUT_STOP_RANGE_U32 -> when (node.input) {
                        GradientNumericOperationGraphV1.Input.STOPS -> "w5aMaterial.gradientHeader.xy"
                        GradientNumericOperationGraphV1.Input.PROBE -> "gradientProbe"
                        GradientNumericOperationGraphV1.Input.ZERO -> "0u"
                        else -> error("Unsupported sealed index input")
                    }
                    GradientNumericOperationGraphV1.Operation.ADD_F32 -> "(${args[0]} + ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.SUB_F32 -> "(${args[0]} - ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.MUL_F32 -> "(${args[0]} * ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.DIV_F32 -> "(${args[0]} / ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.SQRT_F32 -> "sqrt(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.ATAN2_F32 -> "atan2(${args[0]}, ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.FLOOR_F32 -> "floor(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.ABS_F32 -> "abs(${args.single()})"
                    GradientNumericOperationGraphV1.Operation.MAX_F32 -> "max(${args[0]}, ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.COMPARE_F32 -> "(${args[0]} ${if (node.lessOrEqual) "<=" else "<"} ${args[1]})"
                    GradientNumericOperationGraphV1.Operation.SELECT -> "select(${args[0]}, ${args[1]}, ${args[2]})"
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
            return """
                struct GradientStopV1 { positionAndReserved: vec4<f32>, straightColor: vec4<f32>, }
                @group(1) @binding(1) var<storage, read> w5cStops: array<GradientStopV1>;
                fn w5c_local_point(pixel: vec2<f32>) -> vec2<f32> {
                    let p = vec3<f32>(pixel, 1.0);
                    let x = (w5aMaterial.inverseRow0.x * p.x + w5aMaterial.inverseRow0.y * p.y) + w5aMaterial.inverseRow0.z;
                    let y = (w5aMaterial.inverseRow1.x * p.x + w5aMaterial.inverseRow1.y * p.y) + w5aMaterial.inverseRow1.z;
                    let w = (w5aMaterial.inverseRow2.x * p.x + w5aMaterial.inverseRow2.y * p.y) + w5aMaterial.inverseRow2.z;
                    if (w == 1.0) { return vec2<f32>(x, y); }
                    return vec2<f32>(x, y) / w;
                }
                fn w5c_interpolate(left: vec4<f32>, right: vec4<f32>, low: f32, high: f32, t: f32) -> vec4<f32> {
                    if (high <= low) { return right; }
                    if (t == low || all(left == right)) { return left; }
                    let weight = clamp((t - low) / (high - low), 0.0, 1.0);
                    return left + (right - left) * weight;
                }
                fn w5c_gradient(localPosition: vec2<f32>) -> vec4<f32> {
                    $code
                    return $result;
                }
            """.trimIndent()
        }
    }
}
