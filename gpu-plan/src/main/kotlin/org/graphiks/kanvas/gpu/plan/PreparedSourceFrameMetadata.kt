package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeI32

/** Source capture after a prepared consumer's real geometry admission; never geometry authority. */
public class PreparedSourceCaptureV6 internal constructor(internal val source: MaterialSourceConstructionV4) {
    public val blend: BlendPlan get() = source.blend

    public companion object {
        public fun capture(draw: DrawNode, deviceBoundsF32: RectF32, coverage: CoveragePlan,
            targetClamp: BlendTargetClampV1, catalog: RuntimeEffectSemanticCatalogSnapshot): PreparedSourceCaptureV6 =
            PreparedSourceAuthenticationV6.authenticate(draw, coverage, targetClamp, catalog).capture(deviceBoundsF32)
    }
}

/**
 * Authenticates every sibling before atomic projection. The opaque value has no material
 * owner, source index, table, packed uniform or lease; only surviving occurrences capture it.
 */
public class PreparedSourceAuthenticationV6 private constructor(
    private val authentication: MaterialSourceConstructionV4.PreparedAuthentication,
    public val blend: BlendPlan,
) {
    public fun capture(deviceBoundsF32: RectF32): PreparedSourceCaptureV6 = PreparedSourceCaptureV6(
        MaterialSourceConstructionV4.captureAuthenticated(authentication, deviceBoundsF32, blend))

    public companion object {
        public fun authenticate(draw: DrawNode, coverage: CoveragePlan, targetClamp: BlendTargetClampV1,
            catalog: RuntimeEffectSemanticCatalogSnapshot): PreparedSourceAuthenticationV6 {
            require(draw.origin in setOf(DrawOrigin.TEXT, DrawOrigin.VERTICES, DrawOrigin.MESH,
                DrawOrigin.RECT, DrawOrigin.RRECT, DrawOrigin.PATH, DrawOrigin.POINT, DrawOrigin.POINTS, DrawOrigin.IMAGE))
            val blend = requireNotNull(FinalBlendPlanner.plan(draw.blend, coverage, SamplePlan.SingleSample, targetClamp,
                if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
                else BlendCoverageApplicationV1.DestinationInterpolation)) {
                W5aPlanDiagnostics.UnsupportedDrawState
            }
            return PreparedSourceAuthenticationV6(MaterialSourceConstructionV4.authenticatePrepared(draw, catalog), blend)
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
public class PreparedSourceFrameV6 private constructor(private val binding: Binding) {
    private sealed interface Binding {
        val table: MaterialPlanTable
        val operations: List<Int>
        fun ref(operation: Int): MaterialPlanRef
        fun blend(operation: Int): BlendPlan
        fun packed(operation: Int): RawMaterialRequirementsV2
    }
    private class Prepared(val metadata: PreparedSourceFrameMetadata, override val table: MaterialPlanTable,
        refs: List<MaterialPlanRef>, val sources: PackedFrameSourcesV4) : Binding {
        private val refs = immutableList(refs)
        override val operations: List<Int> get() = metadata.operationIndicesI32
        private fun index(operation: Int): Int = operations.indexOf(operation).also { require(it >= 0) { W5fPlanDiagnostics.Schema } }
        override fun ref(operation: Int): MaterialPlanRef = refs[index(operation)]
        override fun blend(operation: Int): BlendPlan = metadata.captures[index(operation)].blend
        override fun packed(operation: Int): RawMaterialRequirementsV2 = sources.forPrepared(table,
            ref(operation), metadata.sources[index(operation)].coordinates)
    }
    private class Layered(val graph: RenderGraph, draws: List<W5bVerticesDraw>) : Binding {
        private val draws = draws.associateBy { it.commandIndex }
        override val table: MaterialPlanTable = requireNotNull(graph.materialPlanTableOrNull())
        override val operations: List<Int> = immutableList(draws.map { it.commandIndex })
        override fun ref(operation: Int): MaterialPlanRef = draws.getValue(operation).materialAuthority.materialPlanRef()
        override fun blend(operation: Int): BlendPlan = draws.getValue(operation).blend
        override fun packed(operation: Int): RawMaterialRequirementsV2 = graph.packedMaterialSourceV4(draws.getValue(operation).materialAuthority)
    }
    internal constructor(metadata: PreparedSourceFrameMetadata, table: MaterialPlanTable, refs: List<MaterialPlanRef>,
        packed: PackedFrameSourcesV4) : this(Prepared(metadata, table, refs, packed))
    public val table: MaterialPlanTable get() = binding.table
    public val operationIndicesI32: List<Int> get() = binding.operations
    public fun ref(operationIndexI32: Int): MaterialPlanRef = binding.ref(operationIndexI32)
    public fun blend(operationIndexI32: Int): BlendPlan = binding.blend(operationIndexI32)
    public fun packedSource(operationIndexI32: Int): RawMaterialRequirementsV2 = binding.packed(operationIndexI32)

    public companion object {
        /** A view of the sole frozen graph owner; never an interner, packer or publication. */
        public fun layeredVertices(graph: RenderGraph): PreparedSourceFrameV6 {
            require(graph.verifyW6aLayerCompilerWitness())
            val draws = RenderGraph.visualDraws(graph.passes()).filterIsInstance<W5bVerticesDraw>()
            require(draws.isNotEmpty() && draws.all { it.materialAuthority is PlanDrawMaterialAuthority.MaterialV5 })
            return PreparedSourceFrameV6(Layered(graph, draws))
        }
    }
}
