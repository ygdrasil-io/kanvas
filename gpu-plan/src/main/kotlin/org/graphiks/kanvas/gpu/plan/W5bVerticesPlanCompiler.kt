package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.kanvas.render.ir.PreparedVerticesRefusalCodesV1 as Codes
import org.graphiks.math.geometry.*
import org.graphiks.math.matrix.rasterBoundsI32OrNull
import org.graphiks.math.matrix.isFinite
import org.graphiks.math.matrix.toMatrix3x3F64

/** Prepared Vertices/Mesh admission, before the existing frame-wide source publication. */
internal class W5bVerticesPlanCompiler(private val catalog: RuntimeEffectSemanticCatalogSnapshot) : GpuPlanCompiler {
    private class Candidate(val owner: W5bVerticesPlanCompiler, val draw: W5bVerticesDraw,
        val source: MaterialSourceConstructionV4, override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor) : GpuPlanCandidate {
        override val capabilityId: String = CAPABILITY_ID
    }

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val entry = scene.withIndex().filter { it.value is SceneCommand.Draw }.singleOrNull()
            ?: return gap()
        val original = (entry.value as SceneCommand.Draw).node
        val mesh = original.geometry as? GeometryNode.IndexedMesh ?: return gap()
        if (original.origin !in setOf(DrawOrigin.VERTICES, DrawOrigin.MESH)) return gap()
        if (mesh.program != null) return invalid("unsupported.material.runtime_effect.unregistered_semantics")
        if (mesh.meshProgram != null) return invalid(Codes.MeshProgramUnregistered)
        if (!original.transform.toMatrix3x3F64().isFinite() || original.transform.hasPerspective()) return invalid(Codes.Transform)
        if (mesh.vertexCount < 3) return invalid(Codes.PositionCount)
        if (mesh.vertexCount > 1_000_000) return invalid(Codes.Budget)
        val points = FloatArray(mesh.vertexCount * 2) { component -> mesh.vertexAt(component / 2).let { if (component % 2 == 0) it.x else it.y } }
        val uv = mesh.copyTexCoords()?.let { values -> FloatArray(values.size * 2) { component ->
            values[component / 2].let { if (component % 2 == 0) it.x else it.y } } }
        if (points.any { !it.isFinite() } || uv?.any { !it.isFinite() } == true) return invalid(Codes.NonFinite)
        if (uv != null && uv.size != points.size) return invalid(Codes.AttributeCount)
        val indices = mesh.copyIndices()
        if (indices?.any { it !in 0 until mesh.vertexCount } == true) return invalid(Codes.IndexOutOfRange)
        if (indices != null && indices.size > 3_000_000) return invalid(Codes.Budget)
        val geometry = TriangleMeshF32.ofOrNull(when (mesh.primitiveMode) {
            MeshPrimitiveMode.TRIANGLES -> TriangleTopologyI32.List
            MeshPrimitiveMode.TRIANGLE_STRIP -> TriangleTopologyI32.Strip
            MeshPrimitiveMode.TRIANGLE_FAN -> TriangleTopologyI32.Fan
        }, points, uv, indices, 1_000_000, 3_000_000) ?: return invalid(Codes.Topology)
        val colors = mesh.copyColors()
        if (colors != null && colors.size != geometry.vertexCountI32) return invalid(Codes.AttributeCount)
        val rgba = colors?.let { values -> ByteArray(values.size * 4) { index -> values[index / 4].let { color ->
            when (index % 4) { 0 -> color.red; 1 -> color.green; 2 -> color.blue; else -> color.alpha }.toByte()
        } } }
        val targetBounds = RectI32(0, 0, target.extent.width, target.extent.height)
        val bounds = geometry.rasterBoundsI32OrNull(original.transform, targetBounds)
            ?: return invalid(Codes.Transform)
        val scissor = bounds.copy()
        when (val clip = original.clip) {
            ClipStackNode.Empty -> Unit
            is ClipStackNode.DeviceRect -> {
                val rect = clip.copyBounds(); val integer = rect.roundOut()
                if (clip.antiAlias || rect != RectF32(integer.left.toFloat(), integer.top.toFloat(), integer.right.toFloat(), integer.bottom.toFloat()))
                    return invalid(Codes.ClipCoverage)
                if (!scissor.intersect(integer)) return invalid(Codes.ClipCoverage)
            }
            else -> return invalid(Codes.ClipCoverage)
        }
        val capture = try { PreparedSourceCaptureV6.capture(original,
            RectF32(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()),
            CoveragePlan.FullOrScissor, BlendTargetClampV1.Unavailable, catalog) }
        catch (failure: IllegalArgumentException) { return GpuPlanSelection.MaterialOnlyRefusal(CAPABILITY_ID,
            scene.canonicalId, target, listOf(EffectiveMaterialPlanner.Result.Refused(failure.message ?: W5fPlanDiagnostics.Schema))) }
        val draw = W5bVerticesDraw(entry.index, PlanDrawMaterialAuthority.MaterialV5(MaterialPlanRef(0)), geometry,
            rgba, original.transform, bounds, scissor, capture.blend, if (rgba == null) null else requireNotNull(
                FinalBlendPlanner.plan(BlendNode.Mode(original.operationBlendMode ?: BlendMode.SRC_OVER),
                    CoveragePlan.FullOrScissor, SamplePlan.SingleSample, BlendTargetClampV1.Unavailable)))
        return GpuPlanSelection.Candidate(Candidate(this, draw, capture.source, scene.canonicalId, target))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> =
        constructSources(candidate, capabilities, budget).prepareAndPublishSourcesV4()

    internal fun constructSources(candidate: GpuPlanCandidate, caps: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) return sourceConstructionRefusalV4(W5fPlanDiagnostics.Schema).failure
        if (selected.draw.blend == BlendPlan.NoOpV1) return SourceDeferredRenderConstructionV4.clearOnly(
            PlanId("w5b.vertices.${selected.sceneCanonicalId.value}"), CAPABILITY_ID,
            SizeI32(selected.target.extent.width, selected.target.extent.height), caps, budget)
        return try {
            val draw = selected.draw
            if (draw.indexElementBytesI32 == 4 && PlanOperationCapability.Uint32Index !in caps.supportedOperations())
                return sourceConstructionRefusalV4(Codes.IndexFormat).failure
            val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
            val resources = listOf(Triple(PlanResourceRole.VertexData, PlanResourceUsage.Vertex,
                Math.multiplyExact(draw.geometryF32.vertexCountI32.toLong(), draw.vertexStrideBytesI32.toLong())),
                Triple(PlanResourceRole.IndexData, PlanResourceUsage.Index,
                    maxOf(4L, Math.multiplyExact(draw.geometryF32.indexCountI32?.toLong() ?: 0L, draw.indexElementBytesI32?.toLong() ?: 0L))),
                Triple(PlanResourceRole.UniformData, PlanResourceUsage.Uniform, 64L)).map { (role, usage, bytes) ->
                require(bytes <= caps.maxBufferSizeBytes) { Codes.Budget }
                PlanResource.of(role, 0, PlanResourceKind.Buffer, null, null,
                    Math.addExact(bytes, (4L - bytes % 4L) % 4L), setOf(usage, PlanResourceUsage.CopyDestination),
                    PlanResourceLifetime.FrameLocal, 0, 2)
            }
            val data = PlanDrawDataResources(resources[0].id, resources[1].id, resources[2].id)
            val row = Math.multiplyExact(extent.width.toLong(), 4L).let { Math.addExact(it,
                (caps.copyBytesPerRowAlignment - it % caps.copyBytesPerRowAlignment) % caps.copyBytesPerRowAlignment) }
            val topology = W5bDestinationGraphSealer.describeSources(CAPABILITY_ID, extent, caps, budget, listOf(draw),
                checkedTextureBytesI64(4, extent.width, extent.height, 1), Math.multiplyExact(row, extent.height.toLong()), row, resources, data)
            val sources = when (val result = MaterialSourceConstructionTableV4.of(listOf(selected.source))) {
                is SourceConstructionResultV4.Built -> result.value
                is SourceConstructionResultV4.Refused -> return result.failure
            }
            when (val result = SourceDeferredRenderConstructionV4.of(PlanId("w5b.vertices.${selected.sceneCanonicalId.value}"),
                CAPABILITY_ID, extent, topology.format, caps, budget, 1, topology.resources, topology.passes,
                topology.dependencies, sources, DeferredLaneTopologyV4.Ordinary, null, emptyList(), emptyMap(), emptyMap())) {
                is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(result.value)
                is SourceConstructionResultV4.Refused -> result.failure
            }
        } catch (failure: IllegalArgumentException) { sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema).failure }
        catch (_: ArithmeticException) { sourceConstructionRefusalV4(Codes.Budget).failure }
    }
    private fun diagnostic(code: String) = RenderDiagnostic(RenderDiagnosticCode(code), RenderDiagnosticDomain.CAPABILITY, RenderDiagnosticSeverity.ERROR, code)
    private fun invalid(code: String) = GpuPlanSelection.InvalidScene(listOf(diagnostic(code)))
    private fun gap() = GpuPlanSelection.NotCandidate(listOf(diagnostic("unsupported.prepared-vertices.frame")))
    internal companion object { const val CAPABILITY_ID = "w5b.vertices.v6" }
}
