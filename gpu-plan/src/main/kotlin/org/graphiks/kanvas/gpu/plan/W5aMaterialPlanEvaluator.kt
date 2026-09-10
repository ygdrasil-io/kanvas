package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction

/**
 * The single W5a source-stage evaluator shared by sealed plan consumers.
 *
 * It projects the material portion of the numeric graph to the existing fixed-function
 * CorePrimitive tail; destination blending, coverage, attachment conversion, clamping, and
 * UNORM8 quantization remain owned by those existing stages.
 */
public object W5aMaterialPlanEvaluator {
    public fun lower(table: MaterialPlanTable, root: MaterialPlanRef): ColorF32? {
        if (table.sizeI32 !in 1..MaterialPlanTable.MAX_ENTRIES_I32 ||
            root.indexI32 !in 0 until table.sizeI32
        ) return null
        val chain = ArrayList<Pair<NumericOperationGraphV1.Node, MaterialBindingPlan>>()
        var ref = root
        while (true) {
            if (chain.size >= MaterialPlanTable.MAX_ENTRIES_I32) return null
            val entry = try { table.entry(ref) } catch (_: IllegalArgumentException) { return null }
            if (!entry.bindings.hasFiniteW5aBinding()) return null
            val source = sourceForCorePrimitive(entry.program.copyNumericOperationGraphV1()) ?: return null
            chain += source to entry.bindings
            if (entry.bindings !is MaterialBindingPlan.OpacityF32V1) break
            if (ref.indexI32 == 0) return null
            ref = MaterialPlanRef(ref.indexI32 - 1)
        }
        var value: ColorF32? = null
        for ((source, bindings) in chain.asReversed()) {
            value = evaluateSource(source, bindings) { value } ?: return null
        }
        return value?.takeIf { color ->
            listOf(color.red, color.green, color.blue, color.alpha).all(Float::isFinite)
        }
    }

    private fun sourceForCorePrimitive(graph: NumericOperationGraphV1): NumericOperationGraphV1.Node? {
        val quantized = graph.root.takeIf { it.operation == NumericOperationGraphV1.Operation.QUANTIZE_UNORM8 }
            ?: return null
        val clamped = quantized.inputs.singleOrNull()
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.CLAMP_01 }
            ?: return null
        val attachment = clamped.inputs.singleOrNull()
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT }
            ?: return null
        val covered = attachment.inputs.singleOrNull()
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.APPLY_COVERAGE_F32 }
            ?: return null
        val destination = covered.inputs.getOrNull(0)
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.INPUT_DESTINATION_LINEAR_PREMUL }
            ?: return null
        val blended = covered.inputs.getOrNull(1)
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.SRC_OVER }
            ?: return null
        val coverage = covered.inputs.getOrNull(2)
            ?.takeIf { it.operation == NumericOperationGraphV1.Operation.INPUT_COVERAGE_F32 }
            ?: return null
        if (blended.inputs.size != 2 || blended.inputs[1] != destination ||
            coverage.type != NumericOperationGraphV1.ValueType.CoverageF32
        ) return null
        return blended.inputs[0]
    }

    private fun evaluateSource(
        node: NumericOperationGraphV1.Node,
        bindings: MaterialBindingPlan,
        childSource: () -> ColorF32?,
    ): ColorF32? = when (node.operation) {
        NumericOperationGraphV1.Operation.INPUT_SOLID_SRGBA_STRAIGHT ->
            (bindings as? MaterialBindingPlan.SolidRgbaF32V1)?.copyRgbaF32()
        NumericOperationGraphV1.Operation.INPUT_MATERIAL_LINEAR_PREMUL ->
            (bindings as? MaterialBindingPlan.OpacityF32V1)?.let { childSource() }
        NumericOperationGraphV1.Operation.CONSTANT_TRANSPARENT -> ColorF32.Transparent
        NumericOperationGraphV1.Operation.SRGB_TO_LINEAR -> evaluateSource(node.inputs.single(), bindings, childSource)?.let { value ->
            ColorF32.of(
                ColorTransferFunction.sRgb.toLinear(value.red),
                ColorTransferFunction.sRgb.toLinear(value.green),
                ColorTransferFunction.sRgb.toLinear(value.blue),
                value.alpha,
            )
        }
        NumericOperationGraphV1.Operation.PREMULTIPLY -> evaluateSource(node.inputs.single(), bindings, childSource)?.let { value ->
            ColorF32.of(value.red * value.alpha, value.green * value.alpha, value.blue * value.alpha, value.alpha)
        }
        NumericOperationGraphV1.Operation.OPACITY_F32 -> evaluateSource(node.inputs.single(), bindings, childSource)?.let { value ->
            val alpha = (bindings as? MaterialBindingPlan.OpacityF32V1)?.alphaF32 ?: return null
            ColorF32.of(value.red * alpha, value.green * alpha, value.blue * alpha, value.alpha * alpha)
        }
        NumericOperationGraphV1.Operation.INPUT_DESTINATION_LINEAR_PREMUL,
        NumericOperationGraphV1.Operation.INPUT_COVERAGE_F32,
        NumericOperationGraphV1.Operation.SRC_OVER,
        NumericOperationGraphV1.Operation.APPLY_COVERAGE_F32,
        NumericOperationGraphV1.Operation.LINEAR_TO_SRGB_ATTACHMENT,
        NumericOperationGraphV1.Operation.CLAMP_01,
        NumericOperationGraphV1.Operation.QUANTIZE_UNORM8,
        -> null
    }
}

private fun MaterialBindingPlan.hasFiniteW5aBinding(): Boolean = when (this) {
    is MaterialBindingPlan.SolidRgbaF32V1 -> copyRgbaF32().let { color ->
        listOf(color.red, color.green, color.blue, color.alpha).all(Float::isFinite)
    }
    is MaterialBindingPlan.OpacityF32V1 -> alphaF32.isFinite()
    MaterialBindingPlan.EmptyV1 -> true
}
