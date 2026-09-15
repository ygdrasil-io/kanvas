package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** Captured source metadata, not a material table, packing permit or geometry admission. */
public class W5hPreparedPointMaterialV6 private constructor(
    internal val source: MaterialSourceConstructionV4,
) {
    public val sourceRef: MaterialPlanRef = MaterialPlanRef(0)
    public val blend: BlendPlan get() = source.blend

    public companion object {
        public fun capture(draw: DrawNode, bounds: RectI32, targetClamp: BlendTargetClampV1,
            runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): W5hPreparedPointMaterialV6 {
            require(draw.origin in setOf(DrawOrigin.POINT, DrawOrigin.POINTS, DrawOrigin.RECT))
            val blend = requireNotNull(FinalBlendPlanner.plan(draw.blend, CoveragePlan.FullOrScissor,
                SamplePlan.SingleSample, targetClamp)) { W5aPlanDiagnostics.UnsupportedDrawState }
            val captured = MaterialSourceConstructionV4.capture(draw, SourceCoordinatesV4.None,
                RectF32.ofLTRB(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()),
                blend, runtimeCatalog = runtimeCatalog, composedV6 = true)
            return when (captured) {
                is SourceConstructionResultV4.Built -> W5hPreparedPointMaterialV6(captured.value)
                is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(captured.diagnosticCode)
            }
        }

        /** Geometry has already been admitted. Bind every sibling through Task 3's one frame owner. */
        public fun seal(id: PlanId, extent: SizeI32, capabilities: PlanCapabilitySnapshot, budget: PlanBudget,
            draws: List<PlanDraw>, sources: List<W5hPreparedPointMaterialV6>): RenderGraph {
            require(draws.size == sources.size && draws.any { it is W5bPointDraw } &&
                draws.all { it is W5bPointDraw || it is SolidRectDraw })
            require(draws.indices.all { draws[it].materialAuthority.materialPlanRef() == MaterialPlanRef(it) })
            val visibleIndices = draws.indices.filter { W5bCorePrimitiveGraph.isVisible(draws[it], extent) }
            val visible = visibleIndices.mapIndexed { index, originalIndex ->
                val ref = MaterialPlanRef(index)
                when (val draw = draws[originalIndex]) {
                    is W5bPointDraw -> draw.withMaterialRef(ref)
                    is SolidRectDraw -> draw.withMaterialRef(ref)
                    else -> error(W5aPlanDiagnostics.UnsupportedDrawState)
                }
            }
            val targetBytes = Math.multiplyExact(Math.multiplyExact(extent.width.toLong(), extent.height.toLong()), 4L)
            val widthBytes = Math.multiplyExact(extent.width.toLong(), 4L)
            val alignment = capabilities.copyBytesPerRowAlignment.toLong()
            val rowBytes = Math.addExact(widthBytes, (alignment - widthBytes % alignment) % alignment)
            val topology = W5bDestinationGraphSealer.describeSources(W5bCorePrimitiveGraph.CAPABILITY_ID, extent,
                capabilities, budget, visible, targetBytes, Math.multiplyExact(rowBytes, extent.height.toLong()), rowBytes)
            val metadata = when (val result = MaterialSourceConstructionTableV4.of(visibleIndices.map { sources[it].source })) {
                is SourceConstructionResultV4.Built -> result.value
                is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(result.diagnosticCode)
            }
            val construction = SourceDeferredRenderConstructionV4.of(id, W5bCorePrimitiveGraph.CAPABILITY_ID, extent,
                topology.format, capabilities, budget, visible.size, topology.resources, topology.passes,
                topology.dependencies, metadata, DeferredLaneTopologyV4.Ordinary, null, emptyList(), emptyMap(), emptyMap())
            val result = when (construction) {
                is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(construction.value).prepareAndPublishSourcesV4()
                is SourceConstructionResultV4.Refused -> construction.failure
            }
            val diagnostics = when (result) {
                is RenderPlanResult.Ready -> return result.plan
                is RenderPlanResult.InvalidScene -> result.diagnostics
                is RenderPlanResult.GapNotMigrated -> result.diagnostics
                is RenderPlanResult.GapOnPromotedScope -> result.diagnostics
                is RenderPlanResult.ResourceLimitExceeded -> result.diagnostics
            }
            throw IllegalArgumentException(diagnostics.joinToString { "${it.code.value}: ${it.message}" })
        }
    }
}
