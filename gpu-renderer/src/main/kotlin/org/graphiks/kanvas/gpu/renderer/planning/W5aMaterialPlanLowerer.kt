package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.NumericOperationGraphV1
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction

/**
 * Projects a sealed W5a numeric DAG onto the already-proven CorePrimitive Rect
 * pipeline.  It evaluates only the material/source stage into the existing
 * solid uniform; SrcOver, coverage, sRGB attachment conversion, clamping, and
 * UNORM8 storage are respectively performed by the existing fixed-function
 * blend state, Rect raster coverage, and `rgba8unorm-srgb` attachment.
 */
internal class W5aMaterialPlanLowerer {
    fun lower(table: MaterialPlanTable, root: MaterialPlanRef): ColorF32? = lower(table, root, 0)

    private fun lower(table: MaterialPlanTable, ref: MaterialPlanRef, depth: Int): ColorF32? {
        if (depth > table.sizeI32) return null
        val entry = try {
            table.entry(ref)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val graph = entry.program.copyNumericOperationGraphV1()
        val source = sourceForCorePrimitive(graph) ?: return null
        return evaluateSource(source, entry.bindings) {
            if (ref.indexI32 == 0) null else lower(table, MaterialPlanRef(ref.indexI32 - 1), depth + 1)
        }
    }

    /**
     * Validates the complete pixel tail before handing source to CorePrimitive.
     * This is a stage mapping, not an evaluator that discards destination or
     * coverage: those values are owned by the attachment/raster stages below.
     */
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
        if (blended.inputs.size != 2 || blended.inputs[1] != destination || coverage.type != NumericOperationGraphV1.ValueType.CoverageF32) {
            return null
        }
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
        NumericOperationGraphV1.Operation.CONSTANT_TRANSPARENT -> ColorF32.of(0f, 0f, 0f, 0f)
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
