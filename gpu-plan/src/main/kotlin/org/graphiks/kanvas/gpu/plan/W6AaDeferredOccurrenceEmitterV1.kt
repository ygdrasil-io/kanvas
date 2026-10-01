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

    /** Complete ordered occurrence; callers reserve IDs and only publish these sealed passes. */
    internal class Emission internal constructor(
        val phases: List<PlanPass.PathRenderPass>, val producer: PlanPass.AaCoverageSourcePass,
        val snapshotCopy: PlanPass.TextureCopy?, val consumer: PlanPass.AaDeferredComposite, val draft: Draft,
    )

    fun emitOccurrence(selected: Selected, target: Target, destinationVersionBefore: DestinationVersionI64,
        sourcePhases: List<PlanPass>, remapping: Map<PlanResourceId, PlanResourceId>,
        sourceResources: List<PlanResource>, domainDeviceI32: RectI32,
        firstOrdinalI32: Int, snapshot: PlanResourceId?, sourcePhaseMapping: LayerMappingF64? = target.mapping): Emission {
        val phases = sourcePhases.mapIndexed { index, original ->
            val path = original as? PlanPass.PathRenderPass ?: error("W7 deferred AA source lost its path phase.")
            val rebound = path.rebindW4eV6(firstOrdinalI32 + index, remapping::getValue, sourcePhaseMapping, domainDeviceI32) as PlanPass.PathRenderPass
            PlanPass.PathRenderPass(rebound.ordinal, rebound.target, rebound.draw, rebound.phase, rebound.drawDataResources,
                rebound.atomicGroup, rebound.depthStencil, path.load, path.store, rebound.depthStencilAccess,
                rebound.depthStencilLoadStore, rebound.resolveTarget, rebound.scanSpansDeviceI32)
        }
        val coverage = remapping.getValue(sourceResources.single { it.role == PlanResourceRole.CoverageSource }.id)
        val producer = PlanPass.AaCoverageSourcePass(firstOrdinalI32, coverage)
        val draft = emit(selected, target, destinationVersionBefore, snapshot)
        val copy = snapshot?.let { PlanPass.TextureCopy(firstOrdinalI32 + 1, target.resource, it, destinationVersionBefore,
            RectI32(0, 0, target.extentI32.width, target.extentI32.height), Point2I32.Origin,
            Math.multiplyExact(target.extentI32.width.toLong(), 4L)) }
        val consumer = PlanPass.AaDeferredComposite(firstOrdinalI32 + if (copy == null) 1 else 2, target.resource)
        return Emission(phases, producer, copy, consumer, draft)
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
