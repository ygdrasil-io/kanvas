package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.*
import org.graphiks.math.matrix.preparePointSquaresF32OrNull

/** Pre-publication adapter for the existing W5b point draw and W5h source owner. */
internal class W5bPointPlanCompiler(private val catalog: RuntimeEffectSemanticCatalogSnapshot) : GpuPlanCompiler {
    private class Candidate(val owner: W5bPointPlanCompiler, val commandI32: Int, val original: DrawNode,
        val geometry: PointSquaresF32, val scissor: RectI32, val source: W5hPreparedPointMaterialV6,
        override val sceneCanonicalId: CanonicalId, override val target: RenderTargetDescriptor): GpuPlanCandidate {
        override val capabilityId: String = W5bCorePrimitiveGraph.CAPABILITY_ID
    }

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val entries = scene.withIndex().filter { it.value is SceneCommand.Draw }
        val entry = entries.singleOrNull() ?: return gap("unsupported.w5b.point-frame-geometry")
        val draw = (entry.value as SceneCommand.Draw).node
        val points = draw.geometry as? GeometryNode.Points ?: return gap("unsupported.w5b.point-frame-geometry")
        if (points.mode != PointMode.POINTS || draw.origin !in setOf(DrawOrigin.POINT, DrawOrigin.POINTS))
            return gap("unsupported.w5b.point-frame-geometry")
        val paint = draw.paint ?: return gap("unsupported.w5b.point-frame-geometry")
        if (points.toList().size > 64) return GpuPlanSelection.InvalidScene(listOf(diagnostic("unsupported.w5b.point-frame-geometry")))
        val refusal = when {
            paint.pathEffect != null -> "unsupported.core_primitive.point.path_effect_exact_lowering"
            !paint.strokeWidth.isFinite() || paint.strokeWidth < 0f -> "unsupported.core_primitive.point.invalid_width"
            paint.strokeCap == StrokeCapNode.ROUND -> "unsupported.core_primitive.point.round_cap_exact_lowering"
            paint.antiAlias && paint.strokeWidth != 0f -> "unsupported.w5b.point-coverage"
            else -> null
        }
        if (refusal != null) return GpuPlanSelection.InvalidScene(listOf(diagnostic(refusal)))
        val targetBounds = RectI32(0, 0, target.extent.width, target.extent.height)
        val geometry = draw.transform.preparePointSquaresF32OrNull(points.toList(), paint.strokeWidth, targetBounds)
            ?: return gap("unsupported.w5b.point-frame-geometry")
        val scissor = geometry.copyBoundsI32()
        when (val clip = draw.clip) {
            ClipStackNode.Empty -> Unit
            is ClipStackNode.DeviceRect -> {
                val bounds = clip.copyBounds()
                val integer = bounds.roundOut()
                if (clip.antiAlias || listOf(bounds.left, bounds.top, bounds.right, bounds.bottom) !=
                    listOf(integer.left.toFloat(), integer.top.toFloat(), integer.right.toFloat(), integer.bottom.toFloat()))
                    return gap("unsupported.w5b.point-clip")
                if (!scissor.intersect(integer)) return gap("unsupported.w5b.point-empty")
            }
            else -> return gap("unsupported.w5b.point-clip")
        }
        val source = try { W5hPreparedPointMaterialV6.capture(draw, scissor, BlendTargetClampV1.Unavailable, catalog) }
        catch (failure: IllegalArgumentException) { return GpuPlanSelection.MaterialOnlyRefusal(W5bCorePrimitiveGraph.CAPABILITY_ID,
            scene.canonicalId, target, listOf(EffectiveMaterialPlanner.Result.Refused(failure.message ?: W5fPlanDiagnostics.Schema))) }
        return GpuPlanSelection.Candidate(Candidate(this, entry.index, draw, geometry, scissor, source, scene.canonicalId, target))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> =
        constructSources(candidate, capabilities, budget).prepareAndPublishSourcesV4()

    internal fun constructSources(candidate: GpuPlanCandidate, caps: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) return sourceConstructionRefusalV4(W5fPlanDiagnostics.Schema).failure
        if (selected.source.blend == BlendPlan.NoOpV1) return SourceDeferredRenderConstructionV4.clearOnly(
            PlanId("w5b.point.${selected.sceneCanonicalId.value}"), W5bCorePrimitiveGraph.CAPABILITY_ID,
            SizeI32(selected.target.extent.width, selected.target.extent.height), caps, budget)
        return try {
            val geometry = selected.geometry
            val draw = W5bPointDraw.of(selected.commandI32, MaterialPlanRef(0), geometry.copyVerticesF32(),
                geometry.copyIndicesI32(), geometry.copyContourStartsI32(), geometry.copyBoundsI32(), selected.scissor,
                selected.source.blend, composedV5 = true)
            val resources = listOf(Triple(PlanResourceRole.VertexData, PlanScratchBufferKind.Vertex, geometry.pointCountI32 * 32L),
                Triple(PlanResourceRole.IndexData, PlanScratchBufferKind.Index, geometry.pointCountI32 * 24L),
                Triple(PlanResourceRole.UniformData, PlanScratchBufferKind.Uniform, maxOf(32L, caps.minUniformBufferOffsetAlignment.toLong())))
                .map { (role, kind, useful) ->
                    val capacity = requireNotNull(caps.bufferAllocationPolicy.reserve(kind, useful)) { "resource-limit.w5b.point-buffer" }
                    require(capacity <= caps.maxBufferSizeBytes) { "unsupported.w5b.point-buffer" }
                    PlanResource.of(role, 0, PlanResourceKind.Buffer, null, null, capacity,
                        setOf(when (kind) { PlanScratchBufferKind.Vertex -> PlanResourceUsage.Vertex
                            PlanScratchBufferKind.Index -> PlanResourceUsage.Index; PlanScratchBufferKind.Uniform -> PlanResourceUsage.Uniform },
                            PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 2)
                }
            val data = PlanDrawDataResources(resources[0].id, resources[1].id, resources[2].id)
            when (val result = W5hPreparedPointMaterialV6.constructSources(PlanId("w5b.point.${selected.sceneCanonicalId.value}"),
                SizeI32(selected.target.extent.width, selected.target.extent.height), caps, budget, listOf(draw), listOf(selected.source), resources, data)) {
                is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(result.value)
                is SourceConstructionResultV4.Refused -> result.failure
            }
        } catch (failure: IllegalArgumentException) { sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema).failure }
        catch (_: ArithmeticException) { sourceConstructionRefusalV4("resource-limit.w5b.point-buffer").failure }
    }
    private fun diagnostic(code: String) = RenderDiagnostic(RenderDiagnosticCode(code), RenderDiagnosticDomain.CAPABILITY,
        RenderDiagnosticSeverity.ERROR, code)
    private fun gap(code: String) = GpuPlanSelection.NotCandidate(listOf(diagnostic(code)))
}
