package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.LayerMappingF64

/**
 * Fully sealed W6 AA consumer.  Its coverage producer is geometric white only; [sourceDraw]
 * retains the independently selected material and final blend authority.
 */
public class PlanAaDeferredCompositeV1 internal constructor(
    public val commandIndexI32: Int,
    public val coverage: PlanW4dAaCoverageSourceBindingV1,
    public val sourceDraw: PlanDraw,
    public val target: PlanResourceId,
    public val destinationSnapshot: PlanResourceId?,
    public val blend: BlendPlan,
    public val destinationVersionBefore: DestinationVersionI64,
    public val destinationVersionAfter: DestinationVersionI64,
    targetExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32,
    targetOriginDeviceI32: Point2I32,
    public val mapping: LayerMappingF64?,
) {
    private val targetExtentSnapshot = targetExtentI32.copy()
    private val sourceBoundsSnapshot = sourceBoundsTargetI32.copy()
    private val targetOriginSnapshot = Point2I32(targetOriginDeviceI32.x, targetOriginDeviceI32.y)

    public fun copyTargetExtentI32(): SizeI32 = targetExtentSnapshot.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = sourceBoundsSnapshot.copy()
    public fun copyTargetOriginDeviceI32(): Point2I32 = Point2I32(targetOriginSnapshot.x, targetOriginSnapshot.y)

    init {
        require(commandIndexI32 >= 0 && sourceDraw.commandIndex == commandIndexI32) { "W7 deferred command identity diverged" }
        require(targetExtentSnapshot.width > 0 && targetExtentSnapshot.height > 0 && !sourceBoundsSnapshot.isEmpty) { "W7 deferred target domain is empty" }
        require(sourceBoundsSnapshot.left >= 0 && sourceBoundsSnapshot.top >= 0 &&
            sourceBoundsSnapshot.right <= targetExtentSnapshot.width && sourceBoundsSnapshot.bottom <= targetExtentSnapshot.height) { "W7 deferred bounds escape target" }
        require(sourceDraw.coverage == CoveragePlan.StencilAA4 && sourceDraw.sample == SamplePlan.Multisample4) { "W7 deferred source is not AA4" }
        require(coverage.commandIndexI32 == commandIndexI32 && coverage.copyExtentI32() == targetExtentSnapshot) { "W7 deferred coverage does not match target" }
        require(blend.isW7AaDeferredBlendV1()) { "W7 deferred final blend is not selected" }
        require(sourceDraw.materialAuthority !is PlanDrawMaterialAuthority.LegacyColorV1) { "W7 deferred source lost material authority" }
        if (blend is BlendPlan.DestinationReadV1) {
            require(destinationSnapshot != null && destinationSnapshot != target && destinationSnapshot != coverage.resources()
                .single { it.role == PlanResourceRole.CoverageSource }.id) { "W7 deferred snapshot aliases an operand" }
            require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == destinationVersionBefore) { "W7 deferred snapshot version diverged" }
        } else require(destinationSnapshot == null) { "W7 fixed consumer cannot own a snapshot" }
        require(destinationVersionAfter.valueI64 == Math.addExact(destinationVersionBefore.valueI64,
            if (blend.compositionFacts.writesParentDevice) 1L else 0L))
    }
}

/**
 * Task-2's selected consumer vocabulary.  This is an assertion over the final planner output,
 * rather than a backend classification: SrcOver has the fixed scalar-coverage form and Plus
 * has the authenticated destination-read/source-pre-scale form.
 */
internal fun BlendPlan.isW7AaDeferredBlendV1(): Boolean = when (this) {
    BlendPlan.NoOpV1 -> true
    is BlendPlan.FixedFunctionV1 ->
        mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER &&
            coverage == BlendCoverageEncodingV1.ScalarCoverageInShader
    is BlendPlan.DestinationReadV1 ->
        mode in W7_PORTER_DUFF_BLEND_MODES && coverage == BlendCoverageEncodingV1.ScalarCoverageInShader &&
            when (mode) {
                org.graphiks.kanvas.render.ir.BlendMode.PLUS -> coverageLaw == BlendCoverageLawV1.SourcePreScale
                else -> coverageLaw == BlendCoverageLawV1.DestinationInterpolation
            }
    else -> false
}

/** Closed W7 solid LINEAR blend vocabulary; selection never broadens this set implicitly. */
internal val W7_PORTER_DUFF_BLEND_MODES: Set<org.graphiks.kanvas.render.ir.BlendMode> = setOf(
    org.graphiks.kanvas.render.ir.BlendMode.CLEAR,
    org.graphiks.kanvas.render.ir.BlendMode.SRC,
    org.graphiks.kanvas.render.ir.BlendMode.DST,
    org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER,
    org.graphiks.kanvas.render.ir.BlendMode.DST_OVER,
    org.graphiks.kanvas.render.ir.BlendMode.SRC_IN,
    org.graphiks.kanvas.render.ir.BlendMode.DST_IN,
    org.graphiks.kanvas.render.ir.BlendMode.SRC_OUT,
    org.graphiks.kanvas.render.ir.BlendMode.DST_OUT,
    org.graphiks.kanvas.render.ir.BlendMode.SRC_ATOP,
    org.graphiks.kanvas.render.ir.BlendMode.DST_ATOP,
    org.graphiks.kanvas.render.ir.BlendMode.XOR,
    org.graphiks.kanvas.render.ir.BlendMode.PLUS,
)
