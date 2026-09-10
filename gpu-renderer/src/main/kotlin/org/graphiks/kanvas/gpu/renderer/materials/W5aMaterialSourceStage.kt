package org.graphiks.kanvas.gpu.renderer.materials

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1.Operation

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
) {
    private val ownedUniformBytes = uniformBytes.copyOf()
    val uniformBytes: ByteArray get() = ownedUniformBytes.copyOf()
    val uniformByteCountI64: Long get() = ownedUniformBytes.size.toLong()
    val canonicalIdentity: String = structuralId + ":raw-v2:" + ownedUniformBytes.joinToString(",")
    companion object {
        fun lower(table: MaterialPlanTable, root: MaterialPlanRef): W5aMaterialSourceStage? {
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
            val uniforms = ByteBuffer.allocate(chain.size * 16).order(ByteOrder.LITTLE_ENDIAN)
            val statements = StringBuilder()
            var child: String? = null
            var opaque = true
            var nextValueI32 = 0
            for ((bindingIndexI32, pair) in chain.asReversed().withIndex()) {
                val (source, binding) = pair
                val input = "w5aMaterial.binding$bindingIndexI32"
                when (binding) {
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
                }
                @group(1) @binding(0) var<uniform> w5aMaterial: W5aMaterialBlock;

                $SRGB_TO_LINEAR_WGSL

                fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                $statements
                    return $child;
                }
            """.trimIndent()
            return W5aMaterialSourceStage(table.entry(root).program.structuralId.value,
                declarations, chain.size, uniforms.array(), opaque)
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
            fn w5a_srgb_to_linear(value: vec4<f32>) -> vec4<f32> {
                let low = value.rgb / vec3<f32>(12.92);
                let high = pow((value.rgb + vec3<f32>(0.055)) / vec3<f32>(1.055), vec3<f32>(2.4));
                return vec4<f32>(select(high, low, value.rgb <= vec3<f32>(0.04045)), value.a);
            }
        """.trimIndent()
    }
}
