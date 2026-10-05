package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32

/** Occurrence identity: equal layer descriptors never share an identifier. */
@JvmInline
public value class LayerScopeIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) { "Layer scope ID must not be negative" } }
}

public sealed interface LayerInitializationPlanV1 {
    public data object TransparentBlack : LayerInitializationPlanV1

    public class PreviousCopy internal constructor(
        public val parentTarget: PlanResourceId,
        public val layerTarget: PlanResourceId,
        public val capturedParentVersion: DestinationVersionI64,
        sourceBoundsParentI32: RectI32,
        destinationOriginLayerI32: Point2I32,
    ) : LayerInitializationPlanV1 {
        private val sourceBoundsParentI32 = sourceBoundsParentI32.copy()
        private val destinationOriginLayerI32 = Point2I32(destinationOriginLayerI32.x, destinationOriginLayerI32.y)
        public fun copySourceBoundsParentI32(): RectI32 = sourceBoundsParentI32.copy()
        public fun copyDestinationOriginLayerI32(): Point2I32 =
            Point2I32(destinationOriginLayerI32.x, destinationOriginLayerI32.y)
    }

    /** A save-time snapshot of the immediate parent, filtered before any child work starts. */
    public class Backdrop(public val plan: BackdropInitializationPlanV1) : LayerInitializationPlanV1
}

/** Semantic witness for the existing copy/filter/composite sequence used to initialize a backdrop layer. */
public class BackdropInitializationPlanV1 internal constructor(
    public val parentTarget: PlanResourceId,
    public val snapshotTarget: PlanResourceId,
    public val filteredTarget: PlanResourceId,
    public val capturedParentVersion: DestinationVersionI64,
    public val filterRoot: CapturedFilterNodeIdI32,
)

public class LayerBoundsPlanV1 internal constructor(
    requestedHintDeviceF64: RectF64?,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    requiredInputDeviceI32: RectI32,
    producedOutputDeviceI32: RectI32?,
    compositeDomainDeviceI32: RectI32,
) {
    private val requestedHintDeviceF64 = requestedHintDeviceF64?.copyF64()
    private val knownContentDeviceI32 = knownContentDeviceI32?.copy()
    private val desiredOutputDeviceI32 = desiredOutputDeviceI32.copy()
    private val requiredInputDeviceI32 = requiredInputDeviceI32.copy()
    private val producedOutputDeviceI32 = producedOutputDeviceI32?.copy()
    private val compositeDomainDeviceI32 = compositeDomainDeviceI32.copy()

    public fun copyRequestedHintDeviceF64(): RectF64? = requestedHintDeviceF64?.copyF64()
    public fun copyKnownContentDeviceI32(): RectI32? = knownContentDeviceI32?.copy()
    public fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutputDeviceI32.copy()
    public fun copyRequiredInputDeviceI32(): RectI32 = requiredInputDeviceI32.copy()
    public fun copyProducedOutputDeviceI32(): RectI32? = producedOutputDeviceI32?.copy()
    public fun copyCompositeDomainDeviceI32(): RectI32 = compositeDomainDeviceI32.copy()
}

public class LayerRestorePlanV1 internal constructor(
    public val alphaF32: Float,
    public val colorFilter: ColorFilterExecutionPlanV1?,
    public val blend: BlendPlan,
    public val readsPriorDevice: Boolean,
    public val writesParentDevice: Boolean,
    public val restoreAffectsTransparentBlack: Boolean,
    public val parentVersionBefore: DestinationVersionI64,
    public val parentVersionAfter: DestinationVersionI64,
    /** Byte offset in the one W6 frame uniform allocation, when W5 color data is consumed. */
    public val colorFilterUniformOffsetI64: Long? = null,
) {
    init {
        require(alphaF32.isFinite()) { "Layer restore alpha must be finite" }
        require(colorFilter == null || colorFilterUniformOffsetI64 != null) {
            "A restore color filter requires its sealed W6 uniform placement"
        }
        require(colorFilter != null || colorFilterUniformOffsetI64 == null)
    }
}

public class LayerScopePlanV1 internal constructor(
    public val id: LayerScopeIdI32,
    public val parentId: LayerScopeIdI32?,
    public val beginCommandIndexI32: Int,
    public val endCommandIndexI32: Int,
    childIds: List<LayerScopeIdI32>,
    public val mapping: LayerMappingF64,
    public val bounds: LayerBoundsPlanV1,
    public val initialization: LayerInitializationPlanV1,
    public val restore: LayerRestorePlanV1,
    public val targetResource: PlanResourceId,
    /**
     * A Picture-stream layer may be parented by an isolated Picture aggregate rather than by a
     * top-level W6a layer scope.  This is an already-selected target identity, never a renderer
     * lookup or a late routing decision.
     */
    public val parentTargetResource: PlanResourceId? = null,
) {
    private val childIds = immutableList(childIds)
    init {
        require(beginCommandIndexI32 >= 0 && endCommandIndexI32 > beginCommandIndexI32)
        require(parentId == null || parentTargetResource == null) {
            "A W6a scope uses either a scope parent or an explicit Picture-stream target parent."
        }
        require(childIds.distinct().size == childIds.size) { "Layer child IDs must be distinct" }
    }
    public fun childIds(): List<LayerScopeIdI32> = childIds
}

public sealed interface LayerExecutionStepV1 {
    public val scopeId: LayerScopeIdI32
    public val passId: PlanPassId

    public data class Initialize(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1

    public data class RenderChildren(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1

    public data class Restore(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1
}

/** Semantic W6a metadata; physical resources and ordering remain in RenderGraph. */
public class LayerFramePlanV1 internal constructor(
    scopes: List<LayerScopePlanV1>,
    executionSteps: List<LayerExecutionStepV1>,
    pictureStreamAggregates: List<PictureStreamAggregateV1> = emptyList(),
    /** The compiler-published total pass order consumed verbatim by the lowerer. */
    frozenPassSchedule: List<PlanPassId> = emptyList(),
) {
    private val scopes = immutableList(scopes)
    private val executionSteps = immutableList(executionSteps)
    private val pictureStreamAggregates = immutableList(pictureStreamAggregates)
    private val frozenPassSchedule = immutableList(frozenPassSchedule)
    init { require(frozenPassSchedule.distinct().size == frozenPassSchedule.size) }
    public fun scopes(): List<LayerScopePlanV1> = scopes
    public fun executionSteps(): List<LayerExecutionStepV1> = executionSteps
    /** Frozen ordered Picture scopes. Renderer code may only materialize these facts. */
    public fun pictureStreamAggregates(): List<PictureStreamAggregateV1> = pictureStreamAggregates
    public fun frozenPassSchedule(): List<PlanPassId> = frozenPassSchedule
}
