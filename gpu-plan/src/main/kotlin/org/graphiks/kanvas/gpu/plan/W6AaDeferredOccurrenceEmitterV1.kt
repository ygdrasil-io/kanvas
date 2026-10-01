package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.LayerMappingF64

/** Private two-phase seam shared by root and layer assembly; it never selects a compiler. */
internal object W6AaDeferredOccurrenceEmitterV1 {
    internal data class Selected(
        val commandIndexI32: Int,
        val sourceDraw: PlanDraw,
        val blend: BlendPlan,
    ) {
        init {
            require(commandIndexI32 == sourceDraw.commandIndex)
            require(sourceDraw.coverage == CoveragePlan.StencilAA4 && sourceDraw.sample == SamplePlan.Multisample4)
            require(blend.isW7AaDeferredBlendV1())
        }
    }

    internal data class Target(
        val resource: PlanResourceId,
        val extentI32: SizeI32,
        val originDeviceI32: Point2I32,
        val sourceBoundsTargetI32: RectI32,
        val mapping: LayerMappingF64?,
    ) {
        init {
            require(extentI32.width > 0 && extentI32.height > 0 && !sourceBoundsTargetI32.isEmpty)
            require(sourceBoundsTargetI32.left >= 0 && sourceBoundsTargetI32.top >= 0 &&
                sourceBoundsTargetI32.right <= extentI32.width && sourceBoundsTargetI32.bottom <= extentI32.height)
        }
    }

    internal class Draft internal constructor(
        val selected: Selected,
        val target: Target,
        val destinationVersionBefore: DestinationVersionI64,
        val destinationVersionAfter: DestinationVersionI64,
        val snapshot: PlanResourceId?,
    ) {
        fun bindAndSeal(coverage: PlanW4dAaCoverageSourceBindingV1): PlanAaDeferredCompositeV1 =
            PlanAaDeferredCompositeV1(selected.commandIndexI32, coverage, selected.sourceDraw, target.resource,
                snapshot, selected.blend, destinationVersionBefore, destinationVersionAfter, target.extentI32,
                target.sourceBoundsTargetI32, target.originDeviceI32, target.mapping)
    }

    fun emit(selected: Selected, target: Target, destinationVersionBefore: DestinationVersionI64,
        snapshot: PlanResourceId?): Draft {
        val blend = if (selected.blend is BlendPlan.DestinationReadV1) {
            requireNotNull(snapshot)
            selected.blend.bindDestinationReadV1(destinationVersionBefore, snapshot)
        } else {
            require(snapshot == null)
            selected.blend
        }
        val bound = selected.copy(sourceDraw = selected.sourceDraw.withFinalBlendV1(blend), blend = blend)
        val writes = blend.compositionFacts.writesParentDevice
        return Draft(bound, target, destinationVersionBefore,
            DestinationVersionI64(Math.addExact(destinationVersionBefore.valueI64, if (writes) 1L else 0L)), snapshot)
    }
}
