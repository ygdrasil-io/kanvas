package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32

/**
 * Plan-time bounds authority for W6c Merge and Blend.  It deliberately accepts an ordered List
 * of source bindings: source equality is neither queried nor used as a reuse key here.
 */
internal object W6cMultiInputPlanner {
    internal fun bounds(inputs: List<W6bFilterGraphConstruction.SourceBinding>): FilterBoundsPlanV1 {
        require(inputs.isNotEmpty())
        val desired = inputs.map { it.copyDesiredOutputDeviceI32() ?: it.copyDeviceBoundsI32() }.reduce(::union)
        val required = inputs.map { it.copyRequiredInputDeviceI32() ?: it.copyDeviceBoundsI32() }.reduce(::union)
        val known = inputs.mapNotNull(W6bFilterGraphConstruction.SourceBinding::copyKnownContentDeviceI32).reduceOrNull(::union)
        val produced = inputs.mapNotNull(W6bFilterGraphConstruction.SourceBinding::copyProducedOutputDeviceI32).reduceOrNull(::union)
        return FilterBoundsPlanV1(known, desired, required, produced, Point2I32(desired.left, desired.top))
    }

    private fun union(first: RectI32, second: RectI32): RectI32 = RectI32(
        minOf(first.left, second.left), minOf(first.top, second.top),
        maxOf(first.right, second.right), maxOf(first.bottom, second.bottom),
    )
}
