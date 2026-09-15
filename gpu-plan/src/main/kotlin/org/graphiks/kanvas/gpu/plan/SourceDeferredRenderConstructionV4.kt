package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32
import org.graphiks.kanvas.render.ir.RenderPlanResult

/** Only the checked final layout may cross from captured metadata to issued sources. */
internal fun RenderPlanResult<SourceDeferredRenderConstructionV4>.prepareAndPublishSourcesV4(): RenderPlanResult<RenderGraph> =
    when (this) {
        is RenderPlanResult.Ready -> when (val layout = FrameSourceLayoutV4.standalone(plan)) {
            is SourceConstructionResultV4.Refused -> layout.failure
            is SourceConstructionResultV4.Built -> when (val constructed = layout.value.prepareAndConstruct()) {
                is SourceConstructionResultV4.Refused -> constructed.failure
                is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(constructed.value).publishConstructionResult()
            }
        }
        is RenderPlanResult.GapNotMigrated -> this
        is RenderPlanResult.GapOnPromotedScope -> this
        is RenderPlanResult.InvalidScene -> this
        is RenderPlanResult.ResourceLimitExceeded -> this
    }

/** Compiler-owned topology selection, not a claim to a material or geometry witness. */
internal enum class DeferredLaneTopologyV4 { Ordinary, GeometryBridge, GeneralGeometryAndColor }

/** Unissued source refs are confined to this metadata type until full material binding. */
internal class SourceDeferredRenderConstructionV4 private constructor(
    val id: PlanId,
    val capabilityId: String,
    extent: SizeI32,
    val colorFormat: PlanLogicalColorFormat,
    val capabilities: PlanCapabilitySnapshot,
    val budget: PlanBudget,
    val visualCommandCount: Int,
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    private val sourceMetadata: MaterialSourceConstructionTableV4,
    val topology: DeferredLaneTopologyV4,
    val geometrySource: SourceDeferredRenderConstructionV4?,
    geometryCommandsI32: List<Int>,
    data: Map<Int, PlanDrawDataResources>,
    depth: Map<Int, PlanResourceId>,
) {
    private val extent = extent.copy()
    val targetExtent: SizeI32 get() = extent.copy()
    private val resourceValues = immutableList(resources)
    private val passValues = immutableList(passes)
    private val dependencyValues = immutableList(dependencies)
    private val commandValues = immutableList(geometryCommandsI32)
    private val dataValues = java.util.Collections.unmodifiableMap(LinkedHashMap(data))
    private val depthValues = java.util.Collections.unmodifiableMap(LinkedHashMap(depth))
    val peakFrameLocalBytesI64: Long = RenderGraph.peak(resourceValues, passValues.size)
    fun resources(): List<PlanResource> = resourceValues
    fun passes(): List<PlanPass> = passValues
    fun dependencies(): List<PlanPassDependency> = dependencyValues
    fun sourceTable(): MaterialSourceConstructionTableV4 = sourceMetadata
    fun geometryCommandsI32(): List<Int> = commandValues
    fun drawDataByCommandI32(): Map<Int, PlanDrawDataResources> = dataValues
    fun depthStencilByCommandI32(): Map<Int, PlanResourceId> = depthValues

    /** Replace only real compiler-issued color-source occurrences, keeping every geometry fact. */
    fun overlayImageSources(capture: (PlanDraw)->MaterialSourceConstructionV4?): SourceConstructionResultV4<SourceDeferredRenderConstructionV4> { return try {
        val originalDraws = RenderGraph.visualDraws(passValues)
        val captured = originalDraws.map { draw -> capture(draw) ?: sourceMetadata.source(draw.materialAuthority.materialPlanRef()) }
        val byCommand = originalDraws.mapIndexed { index,draw -> draw.commandIndex to MaterialPlanRef(index) }.toMap()
        require(byCommand.size == originalDraws.size) { W5fPlanDiagnostics.Schema }
        val sources = when (val result = MaterialSourceConstructionTableV4.of(captured)) {
            is SourceConstructionResultV4.Built -> result.value
            is SourceConstructionResultV4.Refused -> return result
        }
        fun rebuild(original: SourceDeferredRenderConstructionV4,
            geometry: SourceDeferredRenderConstructionV4?): SourceConstructionResultV4<SourceDeferredRenderConstructionV4> {
            // Ref identity can be shared by different original draws. First reindex each
            // occurrence by command; the checked structural interner runs only afterwards.
            val passes = remapSourcePassesV4(original.passes(),overlayCoordinates = { draw ->
                captured[byCommand.getValue(draw.commandIndex).indexI32].takeIf { it.image != null }?.coordinates
            },remap = { it },overlayReference = { draw -> byCommand.getValue(draw.commandIndex) })
            return of(original.id,original.capabilityId,original.targetExtent,original.colorFormat,original.capabilities,
                original.budget,original.visualCommandCount,original.resources(),passes,original.dependencies(),sources,
                original.topology,geometry,original.geometryCommandsI32(),original.drawDataByCommandI32(),original.depthStencilByCommandI32())
        }
        val geometry = geometrySource?.let { original -> when (val result = rebuild(original,null)) {
            is SourceConstructionResultV4.Built -> result.value
            is SourceConstructionResultV4.Refused -> return result
        } }
        rebuild(this,geometry)
    } catch (failure: IllegalArgumentException) {
        sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
    } }

    companion object {
        fun clearOnly(id: PlanId,capabilityId: String,extent: SizeI32,caps: PlanCapabilitySnapshot,
            budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> {
            val topology = W5bGeometryLanePlanV3.describeClearOnly(capabilityId,extent,caps,budget)
            RawMaterialRequirementsV2.requireFrameBudget(emptyList(),topology.peakI64,budget,
                "resource-limit.w5b.destination-budget")
            val sources = when (val value = MaterialSourceConstructionTableV4.of(emptyList())) {
                is SourceConstructionResultV4.Built -> value.value
                is SourceConstructionResultV4.Refused -> return value.failure
            }
            return when (val value = of(id,capabilityId,extent,topology.format,caps,budget,0,
                topology.resources,topology.passes,topology.dependencies,sources,DeferredLaneTopologyV4.GeometryBridge,
                null,emptyList(),emptyMap(),emptyMap())) {
                is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(value.value)
                is SourceConstructionResultV4.Refused -> value.failure
            }
        }

        fun of(id: PlanId, capabilityId: String, extent: SizeI32, format: PlanLogicalColorFormat,
            caps: PlanCapabilitySnapshot, budget: PlanBudget, visualCommandCount: Int,
            resources: List<PlanResource>, passes: List<PlanPass>, dependencies: List<PlanPassDependency>,
            sources: MaterialSourceConstructionTableV4, topology: DeferredLaneTopologyV4,
            geometrySource: SourceDeferredRenderConstructionV4?, geometryCommandsI32: List<Int>,
            data: Map<Int, PlanDrawDataResources>, depth: Map<Int, PlanResourceId>,
        ): SourceConstructionResultV4<SourceDeferredRenderConstructionV4> = try {
            // This is the very same resource, lifetime, dependency and geometry validator used
            // by RenderGraph.construct. No material table or source certificate is fabricated.
            RenderGraph.validateConstructionTopology(capabilityId, extent, format, caps, budget,
                visualCommandCount, resources, passes, dependencies, RenderGraph.peak(resources, passes.size))
            val allDraws = passes.flatMap { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws()
                is PlanPass.StencilProducer -> listOf(pass.draw)
                is PlanPass.StencilCover -> listOf(pass.draw)
                is PlanPass.PathRenderPass -> listOf(pass.draw)
                else -> emptyList()
            } }
            allDraws.forEach { draw ->
                val source = sources.source(draw.materialAuthority.materialPlanRef())
                val coordinates = when (val authority = draw.materialAuthority) {
                    is PlanDrawMaterialAuthority.MaterialV5 -> SourceCoordinatesV4.None
                    is PlanDrawMaterialAuthority.MaterialV4 -> authority.coordinates
                    is PlanDrawMaterialAuthority.MaterialV3 -> SourceCoordinatesV4.V3(authority.imageCoordinates)
                    is PlanDrawMaterialAuthority.MaterialV2 -> SourceCoordinatesV4.V2(authority.coordinates)
                    is PlanDrawMaterialAuthority.MaterialV1 -> authority.coordinates?.let(SourceCoordinatesV4::V1) ?: SourceCoordinatesV4.None
                    is PlanDrawMaterialAuthority.LegacyColorV1 -> error(W5fPlanDiagnostics.Schema)
                }
                require(coordinates == source.coordinates && (!source.pending ||
                    draw.materialAuthority.colorSourceCoordinatesV4() != null)) { W5fPlanDiagnostics.Schema }
            }
            val commands = RenderGraph.visualDraws(passes).map { it.commandIndex }
            require(commands.size == visualCommandCount) { W5fPlanDiagnostics.Schema }
            if (topology == DeferredLaneTopologyV4.GeneralGeometryAndColor) {
                requireNotNull(geometrySource) { W5fPlanDiagnostics.Schema }
                require(geometrySource.topology != DeferredLaneTopologyV4.GeneralGeometryAndColor &&
                    geometrySource.geometrySource == null && geometrySource.targetExtent == extent &&
                    geometrySource.colorFormat == format && geometrySource.capabilities == caps &&
                    geometrySource.budget == budget && geometrySource.sourceTable() === sources) { W5fPlanDiagnostics.Schema }
                require(geometryCommandsI32.distinct() == geometryCommandsI32 &&
                    geometryCommandsI32 == commands &&
                    RenderGraph.visualDraws(geometrySource.passes()).map { it.commandIndex } == commands &&
                    data.keys == commands.toSet() && commands.containsAll(depth.keys)) { W5fPlanDiagnostics.Schema }
                val geometryResources = geometrySource.resources().associateBy { it.id }
                data.values.forEach { value ->
                    require(geometryResources[value.vertex]?.role == PlanResourceRole.VertexData &&
                        geometryResources[value.index]?.role == PlanResourceRole.IndexData &&
                        geometryResources[value.uniform]?.role == PlanResourceRole.UniformData) { W5fPlanDiagnostics.Schema }
                }
                depth.values.forEach { require(geometryResources[it]?.role == PlanResourceRole.DepthStencil) { W5fPlanDiagnostics.Schema } }
            } else require(geometrySource == null && geometryCommandsI32.isEmpty() && data.isEmpty() && depth.isEmpty()) {
                W5fPlanDiagnostics.Schema
            }
            SourceConstructionResultV4.Built(SourceDeferredRenderConstructionV4(id, capabilityId, extent,
                format, caps, budget, visualCommandCount, resources, passes, dependencies, sources,
                topology, geometrySource, geometryCommandsI32, data, depth))
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (_: ArithmeticException) {
            sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget)
        }
    }
}
