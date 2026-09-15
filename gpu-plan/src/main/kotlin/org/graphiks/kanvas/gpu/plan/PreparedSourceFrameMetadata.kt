package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeI32

/** Source capture after a prepared consumer's real geometry admission; never geometry authority. */
public class PreparedSourceCaptureV6 private constructor(internal val source: MaterialSourceConstructionV4) {
    public val blend: BlendPlan get() = source.blend

    public companion object {
        public fun capture(draw: DrawNode, deviceBoundsF32: RectF32, coverage: CoveragePlan,
            targetClamp: BlendTargetClampV1, catalog: RuntimeEffectSemanticCatalogSnapshot): PreparedSourceCaptureV6 {
            require(draw.origin in setOf(DrawOrigin.TEXT, DrawOrigin.VERTICES, DrawOrigin.MESH, DrawOrigin.RECT))
            val blend = requireNotNull(FinalBlendPlanner.plan(draw.blend, coverage, SamplePlan.SingleSample, targetClamp,
                if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
                else BlendCoverageApplicationV1.DestinationInterpolation)) {
                W5aPlanDiagnostics.UnsupportedDrawState
            }
            return when (val captured = MaterialSourceConstructionV4.capture(draw, SourceCoordinatesV4.None,
                deviceBoundsF32, blend, runtimeCatalog = catalog, composedV6 = true)) {
                is SourceConstructionResultV4.Built -> PreparedSourceCaptureV6(captured.value)
                is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(captured.diagnosticCode)
            }
        }
    }
}

/**
 * Backend-neutral input adapter, not a graph, renderer descriptor, or geometry admission.
 * Every sibling has passed the prepared consumer's real admission before this value exists.
 * Its only publication delegates immediately to the existing final-frame source owner.
 */
public class PreparedSourceFrameMetadata private constructor(
    extent: SizeI32,
    public val capabilities: PlanCapabilitySnapshot,
    public val budget: PlanBudget,
    operationIndicesI32: List<Int>,
    captures: List<PreparedSourceCaptureV6>,
    public val nonUniformBytesI64: Long,
) {
    private val extent = extent.copy()
    public val targetExtent: SizeI32 get() = extent.copy()
    internal val operationIndicesI32 = immutableList(operationIndicesI32)
    internal val captures = immutableList(captures)
    internal val sources = immutableList(captures.map { it.source })

    public fun prepare(): PreparedSourceFrameV6 = when (val layout = FrameSourceLayoutV4.prepared(this)) {
        is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(layout.diagnosticCode)
        is SourceConstructionResultV4.Built -> when (val prepared = layout.value.preparePreparedFrame(this)) {
            is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(prepared.diagnosticCode)
            is SourceConstructionResultV4.Built -> prepared.value
        }
    }

    public companion object {
        public fun of(extent: SizeI32, capabilities: PlanCapabilitySnapshot, budget: PlanBudget,
            operationIndicesI32: List<Int>, captures: List<PreparedSourceCaptureV6>, nonUniformBytesI64: Long): PreparedSourceFrameMetadata {
            require(extent.width > 0 && extent.height > 0 && nonUniformBytesI64 >= 0L &&
                nonUniformBytesI64 <= budget.maxFrameLocalBytes && operationIndicesI32.size == captures.size &&
                captures.isNotEmpty() && operationIndicesI32.all { it >= 0 } &&
                operationIndicesI32.zipWithNext().all { (before, after) -> before < after }) { W5fPlanDiagnostics.Schema }
            return PreparedSourceFrameMetadata(extent, capabilities, budget, operationIndicesI32, captures, nonUniformBytesI64)
        }
    }
}

/** Exact common table/ref/packed-owner handoff; no source re-interning or publication API. */
public class PreparedSourceFrameV6 internal constructor(
    internal val metadata: PreparedSourceFrameMetadata,
    public val table: MaterialPlanTable,
    refs: List<MaterialPlanRef>,
    private val packed: PackedFrameSourcesV4,
) {
    private val refs = immutableList(refs)
    public val operationIndicesI32: List<Int> get() = metadata.operationIndicesI32
    public fun ref(operationIndexI32: Int): MaterialPlanRef = refs[index(operationIndexI32)]
    public fun blend(operationIndexI32: Int): BlendPlan = metadata.captures[index(operationIndexI32)].blend
    public fun packedSource(operationIndexI32: Int): RawMaterialRequirementsV2 = packed.forPrepared(table,
        ref(operationIndexI32), metadata.sources[index(operationIndexI32)].coordinates)
    private fun index(operationIndexI32: Int): Int = metadata.operationIndicesI32.indexOf(operationIndexI32).also {
        require(it >= 0) { W5fPlanDiagnostics.Schema }
    }
}
