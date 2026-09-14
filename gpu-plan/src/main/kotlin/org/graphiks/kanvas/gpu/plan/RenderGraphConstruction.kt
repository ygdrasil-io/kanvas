package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32
import org.graphiks.kanvas.render.ir.*

/** Validated compiler topology. It has no packed source payload and cannot be lowered. */
internal class RenderGraphConstruction internal constructor(
    val id: PlanId,
    val capabilityId: String,
    targetExtent: SizeI32,
    val colorFormat: PlanLogicalColorFormat,
    val capabilities: PlanCapabilitySnapshot,
    val budget: PlanBudget,
    val visualCommandCount: Int,
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    val peakFrameLocalBytes: Long,
    val materialTable: MaterialPlanTable?,
    val w4dIssued: Boolean = false,
    val generalIssued: Boolean = false,
    val geometryIssued: Boolean = false,
    geometryLanes: List<GeometryLaneConstruction> = emptyList(),
) {
    private val extent = targetExtent.copy()
    val targetExtent: SizeI32 get() = extent.copy()
    private val resourceValues = immutableList(resources)
    private val passValues = immutableList(passes)
    private val dependencyValues = immutableList(dependencies)
    private val lanes = immutableList(geometryLanes)
    fun w5bGeometryLanes(): List<GeometryLaneConstruction> = lanes
    fun materialPlanTableOrNull(): MaterialPlanTable? = materialTable
    fun verifyW4dCompilerWitness(): Boolean = w4dIssued
    fun verifyW4dGeneralCompilerWitness(): Boolean = generalIssued
    fun verifyW5bGeometryCompilerWitness(): Boolean = geometryIssued
    fun withWitness(w4d: Boolean = w4dIssued, general: Boolean = generalIssued,
        geometry: Boolean = geometryIssued, newLanes: List<GeometryLaneConstruction> = lanes): RenderGraphConstruction =
        RenderGraphConstruction(id,capabilityId,targetExtent,colorFormat,capabilities,budget,visualCommandCount,
            resources(),passes(),dependencies(),peakFrameLocalBytes,materialTable,w4d,general,geometry,newLanes)
    fun resources(): List<PlanResource> = resourceValues
    fun passes(): List<PlanPass> = passValues
    fun dependencies(): List<PlanPassDependency> = dependencyValues

    fun rebindMaterials(table: MaterialPlanTable, remap: (MaterialPlanRef) -> MaterialPlanRef): RenderGraphConstruction {
        val passes = remapSourcePassesV4(passes(),remap=remap)
        val oldStopsI64 = resources().filter { it.role == PlanResourceRole.GradientStopData }.sumOf { it.byteSize }
        val rebound = RenderGraph.construct(id,capabilityId,targetExtent,colorFormat,capabilities,budget,visualCommandCount,
            resources().filterNot { it.role == PlanResourceRole.GradientStopData },passes,dependencies(),
            Math.subtractExact(peakFrameLocalBytes,oldStopsI64),table)
        val reboundLanes = lanes.map {
            GeometryLaneConstruction(it.sourceGraph.rebindMaterials(table,remap),it.commandIndicesI32(),it.drawDataResources,it.depthStencil)
        }
        var authenticated = rebound
        if (w4dIssued) authenticated = RenderGraph.issueW4dCompilerWitness(authenticated)
        if (generalIssued) authenticated = RenderGraph.issueW4dGeneralCompilerWitness(authenticated)
        if (geometryIssued) authenticated = RenderGraph.issueW5bGeometry(authenticated,reboundLanes)
        return authenticated
    }

    fun publish(): RenderGraph {
        val rectScratchI64 = if (capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID &&
            visualSources(passes()).any { it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4 }) {
            val alignmentI64 = capabilities.minUniformBufferOffsetAlignment.toLong()
            val strideI64 = Math.addExact(32L,(alignmentI64-32L%alignmentI64)%alignmentI64)
            listOf(PlanScratchBufferKind.Vertex to 32L,PlanScratchBufferKind.Index to 24L,
                PlanScratchBufferKind.Uniform to strideI64).fold(0L) { bytes,(kind,perDraw) ->
                val reserved = requireNotNull(capabilities.bufferAllocationPolicy.reserve(kind,
                    Math.multiplyExact(visualCommandCount.toLong(),perDraw)))
                require(reserved <= capabilities.maxBufferSizeBytes)
                Math.addExact(bytes,reserved)
            }
        } else 0L
        return RenderGraph.publishConstruction(this,
            packConstructedFrame(listOf(this),materialTable,Math.addExact(peakFrameLocalBytes,rectScratchI64)))
    }
}

/** Same immutable draw/resource-map rebind for resolved and source-deferred construction. */
internal fun remapSourcePassesV4(sourcePasses: List<PlanPass>,
    overlayCoordinates: ((PlanDraw)->SourceCoordinatesV4?)? = null,
    overlayReference: ((PlanDraw)->MaterialPlanRef)? = null,
    remap: (MaterialPlanRef) -> MaterialPlanRef): List<PlanPass> {
        val copied = java.util.IdentityHashMap<PlanDraw, PlanDraw>()
        fun draw(source: PlanDraw): PlanDraw = copied.getOrPut(source) {
            val ref = overlayReference?.invoke(source) ?: remap(source.materialAuthority.materialPlanRef())
            val imageCoordinates = overlayCoordinates?.invoke(source)
            if (imageCoordinates != null) return@getOrPut when (source) {
                is SolidRectDraw -> SolidRectDraw.ofMaterial(source.commandIndex,ref,source.copyVisibleBounds(),
                    source.copyScissor(),source.coverage,source.sample,source.blend,coordinatesV4=imageCoordinates)
                is AnalyticRectDraw -> AnalyticRectDraw.ofMaterial(source.commandIndex,ref,source.copyDeviceBounds(),
                    source.copyRasterBounds(),source.copyScissor(),source.blend,coordinatesV4=imageCoordinates)
                is PathFillDraw -> PathFillDraw.ofMaterial(source.commandIndex,ref,source.copyGeometryF32(),source.strategy,
                    source.copyScissorI32(),source.blend,coordinatesV4=imageCoordinates)
                is GeneralPathDraw -> GeneralPathDraw.ofMaterial(source.commandIndex,ref,source.copyPathGeometry(),
                    source.strategy,source.copyScissorI32(),source.coverage,source.sample,source.blend,coordinatesV4=imageCoordinates)
                else -> error(W5fPlanDiagnostics.Unpromoted)
            }
            when (source) {
                is SolidRectDraw -> source.withMaterialRef(ref)
                is AnalyticRectDraw -> source.withMaterialRef(ref)
                is AnalyticRRectDraw -> source.withMaterialRef(ref)
                is PathFillDraw -> source.withMaterialRef(ref)
                is PathStrokeDraw -> source.withMaterialRef(ref)
                is GeneralPathDraw -> GeneralPathDraw.ofMaterial(source.commandIndex,ref,source.copyPathGeometry(),
                    source.strategy,source.copyScissorI32(),source.coverage,source.sample,source.blend,
                    source.materialCoordinates,source.materialCoordinatesV2,
                    (source.materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates)
                else -> error("Unsupported composite construction draw")
            }
        }
        return sourcePasses.map { pass -> when (pass) {
            is PlanPass.RenderPass -> PlanPass.RenderPass(pass.ordinal,pass.target,pass.draws().map(::draw),
                pass.load,pass.store,pass.drawDataResources,pass.destinationVersionAfter)
            is PlanPass.StencilProducer -> PlanPass.StencilProducer(pass.ordinal,pass.target,pass.depthStencil,
                draw(pass.draw) as PathDraw,pass.drawDataResources,pass.atomicGroup,pass.load,pass.store,
                pass.depthStencilAccess,pass.depthStencilLoadStore)
            is PlanPass.StencilCover -> PlanPass.StencilCover(pass.ordinal,pass.target,pass.depthStencil,
                draw(pass.draw) as PathDraw,pass.drawDataResources,pass.atomicGroup,pass.load,pass.store,
                pass.depthStencilAccess,pass.depthStencilLoadStore,pass.destinationVersionAfter)
            is PlanPass.PathRenderPass -> PlanPass.PathRenderPass(pass.ordinal,pass.target,draw(pass.draw) as PathRenderDraw,
                pass.phase,pass.drawDataResources,pass.atomicGroup,pass.depthStencil,pass.load,pass.store,
                pass.depthStencilAccess,pass.depthStencilLoadStore,pass.resolveTarget)
            else -> pass
        } }
}


internal fun packConstructedFrame(constructions: List<RenderGraphConstruction>, materialTable: MaterialPlanTable?,
    peakFrameLocalBytes: Long): PackedFrameSourcesV4 = PackedFrameSourcesV4.issue(constructions,materialTable,peakFrameLocalBytes)

/** Opaque final-frame packing handoff; callers cannot supply or replace its payload map. */
internal class PackedFrameSourcesV4 private constructor(private val table: MaterialPlanTable?,
    private val capabilities: PlanCapabilitySnapshot, private val budget: PlanBudget,
    sources: Map<String, RawMaterialRequirementsV2>) {
    private val sources = java.util.Collections.unmodifiableMap(LinkedHashMap(sources))
    fun forConstruction(graph: RenderGraphConstruction): Map<String, RawMaterialRequirementsV2> {
        require(graph.materialTable === table && graph.capabilities == capabilities && graph.budget == budget) {
            W5fPlanDiagnostics.Schema
        }
        val selected = linkedMapOf<String, RawMaterialRequirementsV2>()
        visualSources(graph.passes()).forEach { draw ->
            (draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.let { authority ->
                val footprint = RawMaterialRequirementsV2.measureV4(requireNotNull(table),authority.ref)
                require(footprint.proof.authenticates(table,authority.ref,authority.coordinates) &&
                    sources[footprint.canonicalIdentity]?.canonicalIdentity?.endsWith(footprint.canonicalIdentity) == true) {
                    W5fPlanDiagnostics.Schema
                }
                selected[footprint.canonicalIdentity] = requireNotNull(sources[footprint.canonicalIdentity])
            }
        }
        return java.util.Collections.unmodifiableMap(selected)
    }
    companion object {
        fun issue(constructions: List<RenderGraphConstruction>, table: MaterialPlanTable?,
            nonUniformBytesI64: Long): PackedFrameSourcesV4 {
            val first = constructions.first()
            require(constructions.all { it.materialTable === table && it.capabilities == first.capabilities && it.budget == first.budget })
            val draws = constructions.flatMap { visualSources(it.passes()) }
            val footprints = draws.mapNotNull { draw ->
                (draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.let { authority ->
                    require(draw is SolidRectDraw || draw is AnalyticRectDraw || draw is PathFillDraw ||
                        draw is GeneralPathDraw && draw.copyPathGeometry() is PathDrawGeometry.Fill ||
                        (draw is AnalyticRRectDraw || draw is PathStrokeDraw || draw is GeneralPathDraw) &&
                            table?.isUnfilteredGradientV4(authority.ref) == true) { W5fPlanDiagnostics.Unpromoted }
                    RawMaterialRequirementsV2.measureV4(requireNotNull(table),authority.ref).also {
                        require(it.proof.authenticates(table,authority.ref,authority.coordinates)) { W5fPlanDiagnostics.Schema }
                    }
                }
            }
            // Legacy standalone/native issuers already own their historical budget
            // checks. Do not add a new refusal boundary to the V1–V3 public wrapper.
            if (footprints.isEmpty() && constructions.size == 1)
                return PackedFrameSourcesV4(table,first.capabilities,first.budget,emptyMap())
            val legacy = draws.filter { it.materialAuthority !is PlanDrawMaterialAuthority.MaterialV4 &&
                it.materialAuthority !is PlanDrawMaterialAuthority.LegacyColorV1 }
                .map { RawMaterialRequirementsV2.of(requireNotNull(table),it.materialAuthority.materialPlanRef()) }
                .distinctBy { it.canonicalIdentity }
            RawMaterialRequirementsV2.requireFrameBudget(legacy,nonUniformBytesI64,first.budget,"w5a.composite.unsupported")
            val packed = if (footprints.isEmpty()) emptyMap() else {
                val base = legacy.fold(nonUniformBytesI64) { bytes, source -> Math.addExact(bytes,source.uniformByteCountI64) }
                val permit = RawMaterialRequirementsV2.requireFrameBudgetV4(footprints,base,first.budget,first.capabilities,
                    "resource-limit.w5b.source-budget")
                footprints.distinctBy { it.canonicalIdentity }.associate {
                    it.canonicalIdentity to RawMaterialRequirementsV2.packV4(it,permit)
                }
            }
            return PackedFrameSourcesV4(table,first.capabilities,first.budget,packed)
        }
    }
}

private fun visualSources(passes: List<PlanPass>): List<PlanDraw> = passes.flatMap { pass -> when (pass) {
    is PlanPass.RenderPass -> pass.draws()
    is PlanPass.StencilCover -> listOf(pass.draw)
    is PlanPass.PathRenderPass -> if (pass.phase in setOf(PathRenderPhase.SingleSampleDirectColor,
        PathRenderPhase.SingleSampleStencilColorCover,PathRenderPhase.MultisampleDirectColor,
        PathRenderPhase.MultisampleStencilColorCover,PathRenderPhase.HardEdgeBinaryColorCover)) listOf(pass.draw) else emptyList()
    else -> emptyList()
} }
internal class GeometryLaneConstruction(val sourceGraph: RenderGraphConstruction, commands: List<Int>,
    val drawDataResources: PlanDrawDataResources?, val depthStencil: PlanResourceId?) {
    private val indices = immutableList(commands)
    fun commandIndicesI32(): List<Int> = indices
    val capabilityId: String get() = sourceGraph.capabilityId
}

/** Read-only canonical serialization input; never used to republish a graph or defer its budget. */
internal fun RenderGraph.canonicalConstruction(): RenderGraphConstruction = RenderGraphConstruction(
    id,capabilityId,targetExtent,colorFormat,capabilities,budget,visualCommandCount,resources(),passes(),
    dependencies(),peakFrameLocalBytes,materialPlanTableOrNull())

internal fun RenderPlanResult<RenderGraphConstruction>.publishConstructionResult(): RenderPlanResult<RenderGraph> = when (this) {
    is RenderPlanResult.Ready -> try { RenderPlanResult.Ready(plan.publish()) }
        catch (failure: RawMaterialRequirementsV2.Refusal) {
            RenderPlanResult.ResourceLimitExceeded(listOf(RenderDiagnostic(RenderDiagnosticCode(failure.code),
                RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, failure.code)))
        }
    is RenderPlanResult.GapNotMigrated -> this
    is RenderPlanResult.GapOnPromotedScope -> this
    is RenderPlanResult.InvalidScene -> this
    is RenderPlanResult.ResourceLimitExceeded -> this
}
