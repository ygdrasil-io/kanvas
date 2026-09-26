package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1
import org.graphiks.kanvas.render.ir.MaskFilterNode
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.expandForBlurF64OrNull
import org.graphiks.math.geometry.expandSamplingHaloF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.geometry.translateF64OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.kanvas.gpu.plan.W6bFilterGraphConstruction.ConstructionFailure

/** Reverse propagation over the bound recipe; source-independent leaves never demand the source. */
internal class W6bFilterDemandsV1(
    topology: W6bBoundFilterTopologyV1,
    mask: MaskFilterNode?,
    desired: RectI32,
    mapping: LayerMappingF64?,
) {
    private val outputs = java.util.IdentityHashMap<W6bBoundFilterOperationV1, RectI32>()
    private val inputs = java.util.IdentityHashMap<W6bBoundFilterOperationV1, RectI32>()
    private val positionalInputs = java.util.IdentityHashMap<W6bBoundFilterOperationV1, Array<RectI32?>>()
    private var requiredSource: RectI32? = null
    fun copyRequiredSourceI32(): RectI32? = requiredSource?.copy()
    fun copyOutputI32(operation: W6bBoundFilterOperationV1): RectI32? = outputs[operation]?.copy()
    fun copyInputI32(operation: W6bBoundFilterOperationV1): RectI32? = inputs[operation]?.copy()
    fun copyInputDemandsI32(operation: W6bBoundFilterOperationV1): List<RectI32?> =
        operation.inputs.indices.map { positionalInputs[operation]?.get(it)?.copy() }

    init {
        fun expand(region: RectI32, x: Float, y: Float): RectI32 = RectF64(
            region.left.toDouble(), region.top.toDouble(), region.right.toDouble(), region.bottom.toDouble())
            .expandForBlurF64OrNull(x, y)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds,
                "W6b reverse blur demand cannot be represented in checked I32 texels.",
            ))
        fun spatialDemand(node: CapturedFilterNodeV1, output: RectI32): RectI32? =
            W6cSpatialBoundsPlanner.requiredInputBounds(node, output, mapping ?: throw ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "W6c reverse demand has no sealed local-to-device mapping."),
        ))
        fun record(reference: W6bFilterReferenceV1, region: RectI32): RectI32? {
            when (reference) {
                W6bFilterReferenceV1.Source -> requiredSource = requiredSource?.let { union(it, region) } ?: region.copy()
                is W6bFilterReferenceV1.Transparent -> return null
                is W6bFilterReferenceV1.Result -> {
                    val prior = outputs[reference.operation]
                    outputs[reference.operation] = prior?.let { union(it, region) } ?: region.copy()
                }
            }
            return region
        }
        record(topology.terminal, desired)
        for (bound in topology.operations.asReversed()) {
            val output = outputs[bound] ?: continue
            fun inputDemand(index: Int, region: RectI32): RectI32? = record(bound.inputs[index], region)?.also {
                inputs[bound] = inputs[bound]?.let { prior -> union(prior, it) } ?: it.copy()
                val positions = positionalInputs.getOrPut(bound) { arrayOfNulls(bound.inputs.size) }
                positions[index] = positions[index]?.let { prior -> union(prior, it) } ?: it.copy()
            }
            fun unionInputDemands(region: RectI32): RectI32? {
                var demand: RectI32? = null
                bound.inputs.indices.forEach { index ->
                    inputDemand(index, region)?.let { required -> demand = demand?.let { union(it, required) } ?: required }
                }
                return demand
            }
            when (val node = bound.node) {
            is CapturedFilterNodeV1.Crop -> spatialDemand(node, output)?.let { required -> inputDemand(0, required) }
            is CapturedFilterNodeV1.Offset -> spatialDemand(node, output)?.let { required -> inputDemand(0, required) }
            is CapturedFilterNodeV1.Tile -> spatialDemand(node, output)?.let { required -> inputDemand(0, required) }
            is CapturedFilterNodeV1.Dilate -> inputDemand(0,
                W6cMorphologyPlanner.requiredInputBounds(node.radiusX.toDouble(), node.radiusY.toDouble(), output,
                    mapping ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6c reverse demand has no sealed local-to-device mapping."))))
            is CapturedFilterNodeV1.Erode -> inputDemand(0,
                W6cMorphologyPlanner.requiredInputBounds(node.radiusX.toDouble(), node.radiusY.toDouble(), output,
                    mapping ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6c reverse demand has no sealed local-to-device mapping."))))
            is CapturedFilterNodeV1.Blur -> inputDemand(0, expand(output, node.sigmaX, node.sigmaY))
            is CapturedFilterNodeV1.DropShadow -> {
                val translated = RectF64(output.left.toDouble(), output.top.toDouble(),
                    output.right.toDouble(), output.bottom.toDouble()).translateF64OrNull(-node.dx.toDouble(), -node.dy.toDouble())
                    ?.roundOutToRectI32OrNull() ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b reverse shadow demand cannot be represented in checked I32 texels.",
                ))
                val shadow = expand(translated, node.sigmaX, node.sigmaY)
                inputDemand(0, if (node.mode == CapturedDropShadowModeV1.COMPOSITE) union(output, shadow) else shadow)
            }
            // These nodes do not alter their input's spatial demand.  Compose is contextual:
            // outer consumes the inner result, so its reverse demand must flow into inner.
            is CapturedFilterNodeV1.ColorFilter -> inputDemand(0, output)
            // Keep the captured public order while forming a geometric union.  A Set here would
            // erase repeated inputs before their individual source demand is accounted for.
            is CapturedFilterNodeV1.Merge -> unionInputDemands(output)
            is CapturedFilterNodeV1.Blend -> unionInputDemands(output)
            is CapturedFilterNodeV1.MatrixConvolution -> {
                val width = W6bFilterGraphConstruction.exactPositiveI32(node.kernelSize.width, "matrix kernel width")
                val height = W6bFilterGraphConstruction.exactPositiveI32(node.kernelSize.height, "matrix kernel height")
                val offsetX = node.kernelOffset.x.toDouble()
                val offsetY = node.kernelOffset.y.toDouble()
                val required = RectF64(output.left.toDouble(), output.top.toDouble(), output.right.toDouble(), output.bottom.toDouble())
                    .expandSamplingHaloF64OrNull(offsetX.coerceAtLeast(0.0), offsetY.coerceAtLeast(0.0),
                        (width - 1.0 - offsetX).coerceAtLeast(0.0), (height - 1.0 - offsetY).coerceAtLeast(0.0))
                    ?.roundOutToRectI32OrNull() ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d matrix reverse sampling halo cannot be represented in checked I32 texels."))
                inputDemand(0, required)
            }
            is CapturedFilterNodeV1.DisplacementMap -> {
                val scale = node.scale.toDouble()
                if (!scale.isFinite()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d displacement reverse demand has non-finite scale."))
                val halo = kotlin.math.abs(scale)
                val sourceRequired = RectF64(output.left.toDouble(), output.top.toDouble(), output.right.toDouble(), output.bottom.toDouble())
                    .expandSamplingHaloF64OrNull(halo, halo, halo, halo)?.roundOutToRectI32OrNull()
                    ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d displacement reverse sampling halo cannot be represented in checked I32 texels."))
                inputDemand(0, output)
                inputDemand(1, sourceRequired)
            }
            is CapturedFilterNodeV1.Magnifier -> {
                val lens = node.copySource()
                val mapping = mapping ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d magnifier reverse demand has no sealed local-to-device mapping."))
                val mappedLensF64 = mapping.mapLocalRectToDeviceF64OrNull(RectF64(
                    lens.left.toDouble(), lens.top.toDouble(), lens.right.toDouble(), lens.bottom.toDouble(),
                )) ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d magnifier reverse lens cannot be represented in checked I32 texels."))
                val zoomF64 = node.zoom.toDouble()
                if (!zoomF64.isFinite() || zoomF64 <= 0.0) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d magnifier reverse demand has invalid zoom."))
                val insetF64 = node.inset.toDouble()
                if (!insetF64.isFinite() || insetF64 < 0.0) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d magnifier reverse demand has invalid inset."))
                // The frozen shader samples the inset lens after mapping it to device space,
                // then applies its inverse zoom around the mapped center.  In particular,
                // 0 < zoom < 1 expands that sampled domain beyond the original lens.
                val centerXF64 = (mappedLensF64.left + mappedLensF64.right) / 2.0
                val centerYF64 = (mappedLensF64.top + mappedLensF64.bottom) / 2.0
                val innerLeftF64 = mappedLensF64.left + insetF64
                val innerTopF64 = mappedLensF64.top + insetF64
                val innerRightF64 = mappedLensF64.right - insetF64
                val innerBottomF64 = mappedLensF64.bottom - insetF64
                // WGSL admits every edge of the inner lens. A reversed edge is the only
                // no-sampling case; equal edges still admit a pixel-center line.
                if (innerLeftF64 > innerRightF64 || innerTopF64 > innerBottomF64) {
                    inputDemand(0, output)
                } else {
                    val inverseSampledLensF64 = RectF64(
                        centerXF64 + (innerLeftF64 - centerXF64) / zoomF64,
                        centerYF64 + (innerTopF64 - centerYF64) / zoomF64,
                        centerXF64 + (innerRightF64 - centerXF64) / zoomF64,
                        centerYF64 + (innerBottomF64 - centerYF64) / zoomF64,
                    )
                    if (!inverseSampledLensF64.isFinite()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.InvalidBounds,
                        "W6d magnifier reverse sampled lens is non-finite."))
                    // The shader loads round(sampled - .5).  The F64 half-texel envelope makes
                    // outward I32 rounding include inclusive endpoints and ties-to-even texels,
                    // including a lens collapsed to one admitted coordinate line.
                    val sourceTexelEnvelopeF64 = RectF64(
                        inverseSampledLensF64.left - .5,
                        inverseSampledLensF64.top - .5,
                        inverseSampledLensF64.right + .5,
                        inverseSampledLensF64.bottom + .5,
                    )
                    val inverseSampledLensI32 = sourceTexelEnvelopeF64.roundOutToRectI32OrNull() ?: throw ConstructionFailure(
                        W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                            "W6d magnifier reverse sampled lens cannot be represented in checked I32 texels."),
                    )
                    inputDemand(0, union(output, inverseSampledLensI32))
                }
            }
            is CapturedFilterNodeV1.DistantLitDiffuse -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.PointLitDiffuse -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.SpotLitDiffuse -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.DistantLitSpecular -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.PointLitSpecular -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.SpotLitSpecular -> inputDemand(0, W6bFilterGraphConstruction.sobelRequiredInput(output))
            is CapturedFilterNodeV1.RuntimeEffect -> inputDemand(
                0,
                output,
            )
            is CapturedFilterNodeV1.Picture -> Unit
            is CapturedFilterNodeV1.Compose -> error("Compose must be bound before demand propagation.")
            else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily,
                "Reverse demand requires an admitted W6b filter."))
            }
        }
        requiredSource = requiredSource?.let { imageInput ->
            (mask as? MaskFilterNode.Blur)?.let { expand(imageInput, it.sigma, it.sigma) } ?: imageInput
        }
    }

    private fun union(first: RectI32, second: RectI32): RectI32 = RectI32(
        minOf(first.left, second.left), minOf(first.top, second.top),
        maxOf(first.right, second.right), maxOf(first.bottom, second.bottom))
}
