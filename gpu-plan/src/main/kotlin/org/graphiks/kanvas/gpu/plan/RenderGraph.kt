package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.SizeI32

public class RenderGraph private constructor(
    public val id: PlanId,
    public val capabilityId: String,
    targetExtent: SizeI32,
    public val colorFormat: PlanLogicalColorFormat,
    public val capabilities: PlanCapabilitySnapshot,
    public val budget: PlanBudget,
    public val visualCommandCount: Int,
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    public val peakFrameLocalBytes: Long,
    private val w4dCompilerWitness: W4dCompilerWitness?,
    private val w4dGeneralCompilerWitness: W4dGeneralCompilerWitness?,
    private val w4eNativePayloadPlan: W4eNativePayloadPlan?,
    private val w4eCompilerWitness: W4eCompilerWitness?,
    private val materialPlanTable: MaterialPlanTable?,
    private val w5aCompositePlan: W5aCompositePlanV1? = null,
    private val w5bGeometryIssued: Boolean = false,
    w5bGeometryLanes: List<W5bGeometryLanePlanV3> = emptyList(),
    private val w5eImageConstruction: W5eImageConstructionPlanV1? = null,
) {
    private val storedTargetExtent: SizeI32 = targetExtent.copy()
    public val targetExtent: SizeI32
        get() = storedTargetExtent.copy()
    private val storedResources = immutableList(resources)
    private val storedPasses = immutableList(passes)
    private val storedDependencies = immutableList(dependencies)
    private val storedW5bGeometryLanes = immutableList(w5bGeometryLanes)

    public fun resources(): List<PlanResource> = storedResources
    public fun passes(): List<PlanPass> = storedPasses
    public fun dependencies(): List<PlanPassDependency> = storedDependencies
    public fun w5eImageConstructionOrNull(): W5eImageConstructionPlanV1? = w5eImageConstruction

    /** Immutable W5 material authority, present only on material-plan graphs. */
    public fun materialPlanTableOrNull(): MaterialPlanTable? = materialPlanTable

    public fun w5aCompositePlanOrNull(): W5aCompositePlanV1? = w5aCompositePlan

    public fun verifyW5bGeometryCompilerWitness(): Boolean = w5bGeometryIssued
    public fun w5bGeometryLanes(): List<W5bGeometryLanePlanV3> = storedW5bGeometryLanes

    /** Verifies that this exact immutable graph snapshot was issued by the W4d compiler. */
    public fun verifyW4dCompilerWitness(): Boolean =
        w4dCompilerWitness?.matches(this) == true

    /** Verifies that this exact immutable graph snapshot was issued by the W4d.2 compiler. */
    public fun verifyW4dGeneralCompilerWitness(): Boolean =
        w4dGeneralCompilerWitness?.matches(this) == true

    /** Verifies that this exact immutable graph snapshot was issued by the W4e compiler. */
    public fun verifyW4eCompilerWitness(): Boolean =
        w4eCompilerWitness?.matches(this) == true

    /**
     * Returns the immutable W4e V/I/U payload issued with this graph, when the graph is W4e.
     * The payload owns defensive snapshots, so callers cannot mutate bytes later consumed by lowering.
     */
    public fun w4eNativePayloadOrNull(): W4eNativePayloadPlan? = w4eNativePayloadPlan

    public companion object {
        internal fun issueW5e(bridge: W5eImageConstructionPlanV1): RenderGraph {
            val geometry = bridge.constructionGraph
            val images = bridge.imageDraws().associateBy { it.commandIndex }
            val cachedImages = bridge.imageDraws().map { it.execution.cacheRequest }
                .distinctBy { it.canonicalPhysicalIdentity }.mapIndexed { ordinalI32, request ->
                    PlanResource.of(PlanResourceRole.DecodedImageV1, ordinalI32, request.kind,
                        PlanTextureFormat.ImageV1(request.format), SizeI32(request.widthI32, request.heightI32),
                        request.byteSizeI64, request.usages(), request.lifetime, 0, geometry.passes().size)
                }
            val passes = geometry.passes().map { pass -> if (pass is PlanPass.RenderPass)
                PlanPass.RenderPass(pass.ordinal, pass.target, pass.draws().map { images.getValue(it.commandIndex) },
                    pass.load, pass.store, pass.drawDataResources, destinationVersionAfter = pass.destinationVersionAfter)
                else pass }
            return RenderGraph(PlanId(bridge.canonicalIdentity), W5eImagePlanCompiler.CAPABILITY_ID,
                geometry.targetExtent, geometry.colorFormat, geometry.capabilities, geometry.budget, images.size,
                geometry.resources() + cachedImages, passes, geometry.dependencies(), bridge.peakBytesI64,
                null, null, null, null, bridge.materialTable, w5eImageConstruction = bridge)
        }
        internal fun issueW5bGeometry(graph: RenderGraph, lanes: List<W5bGeometryLanePlanV3> = emptyList()): RenderGraph {
            require(graph.capabilityId in setOf(W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID,
                W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID, W4cPathFillPlanCompiler.W5B_CAPABILITY_ID,
                W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID,
                W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID, W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID))
            require(graph.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }.all {
                (it is SolidRectDraw || it is AnalyticRectDraw || it is AnalyticRRectDraw || it is PathFillDraw || it is PathStrokeDraw || it is GeneralPathDraw || it is W5bW4ePathDraw) &&
                    (it.materialAuthority is PlanDrawMaterialAuthority.MaterialV1 ||
                        (it is SolidRectDraw || it is AnalyticRectDraw || it is AnalyticRRectDraw ||
                            it is PathFillDraw || it is PathStrokeDraw || it is GeneralPathDraw) && it.materialAuthority is PlanDrawMaterialAuthority.MaterialV2)
            })
            return RenderGraph(graph.id, graph.capabilityId, graph.targetExtent, graph.colorFormat, graph.capabilities,
                graph.budget, graph.visualCommandCount, graph.resources(), graph.passes(), graph.dependencies(),
                graph.peakFrameLocalBytes, null, null, null, null, graph.materialPlanTable, w5bGeometryIssued = true,
                w5bGeometryLanes = if (lanes.isEmpty() && graph.capabilityId in setOf(W4cPathFillPlanCompiler.W5B_CAPABILITY_ID, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID) && graph.visualCommandCount > 0) {
                    val colors = visualDraws(graph.passes())
                    listOf(W5bGeometryLanePlanV3(graph, colors.map { it.commandIndex },
                        PlanDrawDataResources(graph.resources().single { it.role == PlanResourceRole.VertexData }.id,
                            graph.resources().single { it.role == PlanResourceRole.IndexData }.id,
                            graph.resources().single { it.role == PlanResourceRole.UniformData }.id),
                        graph.resources().singleOrNull { it.role == PlanResourceRole.DepthStencil }?.id))
                } else lanes)
        }
        /** Only the composite compiler can issue this distinct, lane-owned graph representation. */
        internal fun issueW5aComposite(composite: W5aCompositePlanV1): RenderGraph {
            val lanes = composite.lanes()
            val first = lanes.first()
            val identity = MessageDigest.getInstance("SHA-256").digest(
                lanes.joinToString("|") { it.id.value }.encodeToByteArray(),
            ).joinToString("") { "%02x".format(it) }
            // Composite resources/passes live in typed lanes; no standalone topology is forged.
            return RenderGraph(PlanId("w5a.composite.$identity"), W5aCompositePlanCompiler.CAPABILITY_ID,
                first.targetExtent, first.colorFormat, first.capabilities, first.budget,
                lanes.sumOf { it.visualCommandCount }, emptyList(), emptyList(), emptyList(),
                composite.peakFrameLocalBytesI64, null, null, null, null, composite.materialTable, composite)
        }

        public fun of(
            id: PlanId,
            capabilityId: String,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
            visualCommandCount: Int,
            resources: List<PlanResource>,
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            peakFrameLocalBytes: Long,
            materialPlanTable: MaterialPlanTable? = null,
            w5bW4eSource: RenderGraph? = null,
        ): RenderGraph {
            val stopSlab = materialPlanTable?.gradientStopSlab
            if (stopSlab != null) {
                visualDraws(passes).forEach { draw ->
                    val authority = draw.materialAuthority
                    var indexI32 = authority.materialPlanRef().indexI32
                    while (materialPlanTable.entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) indexI32--
                    val entry = materialPlanTable.entry(MaterialPlanRef(indexI32))
                    if (entry.bindings is MaterialBindingPlan.GradientV2) {
                        require((draw is SolidRectDraw || draw is AnalyticRectDraw || draw is AnalyticRRectDraw ||
                            draw is PathFillDraw || draw is PathStrokeDraw || draw is GeneralPathDraw ||
                            draw is BinaryMaskedPathDraw) && authority is PlanDrawMaterialAuthority.MaterialV2) {
                            W5dPlanDiagnostics.CoordinatePlanSchema
                        }
                        require(entry.program is GradientAddressingProgramV2 && entry.bindings.numericAuthority.authenticates(
                            entry.program, entry.bindings, stopSlab, authority.coordinates)) { W5dPlanDiagnostics.CoordinatePlanSchema }
                    }
                    if (entry.bindings is MaterialBindingPlan.GradientV1) {
                        require(authority is PlanDrawMaterialAuthority.MaterialV1) { W5cPlanDiagnostics.CoordinatesUnavailable }
                        require((draw is SolidRectDraw || draw is AnalyticRectDraw || draw is AnalyticRRectDraw ||
                            draw is PathFillDraw || draw is PathStrokeDraw || draw is GeneralPathDraw ||
                            draw is ClippedGeneralPathDraw || draw is BinaryMaskedPathDraw || draw is W5bW4ePathDraw) && authority.coordinates != null) {
                            W5cPlanDiagnostics.CoordinatesUnavailable
                        }
                        require(entry.bindings.numericAuthority.authenticates(entry.program, entry.bindings,
                            stopSlab, requireNotNull(authority.coordinates))) { W5cPlanDiagnostics.NumericDomainUnbounded }
                    }
                }
            }
            if (stopSlab != null && resources.none { it.role == PlanResourceRole.GradientStopData }) {
                stopSlab.requireStorageCapabilities(capabilities)
                val sourceRequirements = visualDraws(passes).map { draw ->
                    val authority = draw.materialAuthority
                    val source = RawMaterialRequirementsV2.of(materialPlanTable, authority.materialPlanRef())
                    require(source.fitsUniformBinding(capabilities)) {
                        if (authority is PlanDrawMaterialAuthority.MaterialV2) W5dPlanDiagnostics.CoordinateUniformBudget else W5cPlanDiagnostics.StorageUnavailable
                    }
                    source
                }
                val peakI64 = Math.addExact(peakFrameLocalBytes, stopSlab.byteSizeI64)
                RawMaterialRequirementsV2.requireFrameBudget(sourceRequirements, peakI64, budget, W5cPlanDiagnostics.StopBudget)
                val stopResource = PlanResource.of(PlanResourceRole.GradientStopData, 0, PlanResourceKind.Buffer,
                    null, null, stopSlab.byteSizeI64, setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination),
                    PlanResourceLifetime.FrameLocal, 0, passes.size)
                return of(id, capabilityId, targetExtent, colorFormat, capabilities, budget, visualCommandCount,
                    resources + stopResource, passes, dependencies, peakI64, materialPlanTable, w5bW4eSource)
            }
            require(w5bW4eSource == null || capabilityId == W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID &&
                w5bW4eSource.verifyW4eCompilerWitness() && w5bW4eSource.capabilityId == W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID)
            require(capabilityId != W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID || visualCommandCount == 0 || w5bW4eSource != null)
            require(capabilityId.isNotBlank()) { "Capability ID must not be blank" }
            require(!targetExtent.isEmpty()) { "Target extent must be non-empty" }
            require(colorFormat in capabilities.supportedFormats()) { "Target format is unsupported" }
            require(targetExtent.width <= capabilities.maxTextureDimension2D &&
                targetExtent.height <= capabilities.maxTextureDimension2D) { "Target extent exceeds capabilities" }
            require(visualCommandCount >= 0) { "Visual command count must be non-negative" }
            require(peakFrameLocalBytes >= 0) { "Peak memory must be non-negative" }
            val resourceIds = resources.map { it.id }
            require(resourceIds.distinct().size == resourceIds.size) { "Resource IDs must be unique" }
            val passIds = passes.map { it.id }
            require(passIds.distinct().size == passIds.size) { "Pass IDs must be unique" }
            require(passes.map { it.role to it.ordinal }.distinct().size == passes.size) { "Pass role ordinals must be unique" }
            require(passes.none { it is PlanPass.ResolvePass }) {
                "Standalone resolve passes are forbidden; resolve only from an explicit path color pass"
            }
            resources.forEach { resource ->
                require(resource.lastPassIndexExclusive <= passes.size) { "Resource lifetime exceeds pass count" }
                when (resource.kind) {
                    PlanResourceKind.Texture2D -> {
                        val extent = requireNotNull(resource.copyExtent())
                        require(extent.width <= capabilities.maxTextureDimension2D &&
                            extent.height <= capabilities.maxTextureDimension2D) { "Texture extent exceeds capabilities" }
                        when (val format = requireNotNull(resource.format)) {
                            is PlanTextureFormat.ImageV1 -> require(resource.lifetime == PlanResourceLifetime.DeviceSessionCache)
                            is PlanTextureFormat.Color -> require(format.value in capabilities.supportedFormats()) {
                                "Color texture format is unsupported"
                            }
                            is PlanTextureFormat.DepthStencil -> require(
                                format.value in capabilities.supportedDepthStencilFormats(),
                            ) { "Depth-stencil texture format is unsupported" }
                            PlanTextureFormat.CoverageMask -> Unit
                        }
                        require(
                            capabilities.supportsTexture(
                                requireNotNull(resource.format),
                                resource.sampleCountI32,
                                resource.usages(),
                            ),
                        ) { "Texture format, sample count, or usage is unsupported" }
                    }
                    PlanResourceKind.Buffer -> require(resource.byteSize <= capabilities.maxBufferSizeBytes) {
                        "Buffer size exceeds capabilities"
                    }
                }
            }
            val resourcesById = resources.associateBy { it.id }
            passes.forEachIndexed { passIndex, pass -> referencedResources(pass).forEach { reference ->
                val resource = requireNotNull(resourcesById[reference]) { "Pass references an unknown resource" }
                require(resource.firstPassIndex <= passIndex && passIndex < resource.lastPassIndexExclusive) {
                    "Pass references a resource outside its lifetime"
                }
            } }
            val passIndex = passIds.withIndex().associate { it.value to it.index }
            dependencies.forEach { dependency ->
                val before = requireNotNull(passIndex[dependency.before]) { "Dependency source is unknown" }
                val after = requireNotNull(passIndex[dependency.after]) { "Dependency target is unknown" }
                require(before < after) { "Dependencies must point forward" }
            }
            require(dependencies.distinct().size == dependencies.size) { "Dependencies must be unique" }
            validatePassCapabilities(passes, capabilities)
            validateW5bDestinationVersions(passes)
            validateColorPasses(passes, resourcesById, targetExtent, colorFormat, capabilityId)
            val usesExplicitAa4PathPasses = passes.any {
                it is PlanPass.PathMaskClearPass || it is PlanPass.PathRenderPass
            }
            val usesClipMasks = passes.any {
                it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold
            }
            if (usesClipMasks && w5bW4eSource == null) {
                validateClipMaskContracts(passes, dependencies, resources, resourcesById, capabilities, targetExtent)
            }
            if (w5bW4eSource == null) validateClipConsumers(passes, dependencies, resourcesById, targetExtent, usesClipMasks)
            if (w5bW4eSource != null) {
                validateW5bW4eGeometrySource(w5bW4eSource, passes, resources, targetExtent, capabilities, budget)
                validateW5bGeometryPasses(passes, resourcesById, visualCommandCount, w5bW4eSource)
            } else if (usesExplicitAa4PathPasses) {
                validateExplicitAa4PathContracts(
                    passes,
                    dependencies,
                    resources,
                    resourcesById,
                    capabilities,
                    targetExtent,
                    colorFormat,
                    visualCommandCount,
                )
            } else if (capabilityId in setOf(W4cPathFillPlanCompiler.W5B_CAPABILITY_ID, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID)) {
                validateW5bGeometryPasses(passes, resourcesById, visualCommandCount)
            } else {
                validateStencilAtomicContracts(passes, dependencies, resources, resourcesById, capabilities, targetExtent)
            }
            validateVisualCommandOrder(passes)
            if (capabilityId !in setOf(W4cPathFillPlanCompiler.W5B_CAPABILITY_ID, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID, W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID)) validatePathDrawContracts(
                passes,
                dependencies,
                resources,
                resourcesById,
                capabilities,
                visualCommandCount,
            )
            passes.filterIsInstance<PlanPass.ReadbackPass>().forEach { pass ->
                require(pass.bytesPerRow % capabilities.copyBytesPerRowAlignment == 0L) {
                    "Readback row bytes do not satisfy alignment"
                }
                validateReadback(pass, resourcesById, targetExtent)
            }
            val calculatedPeak = peak(resources, passes.size)
            require(calculatedPeak == peakFrameLocalBytes) { "Peak memory does not match resource lifetimes" }
            require(calculatedPeak <= budget.maxFrameLocalBytes) { "Peak memory exceeds budget" }
            return RenderGraph(id, capabilityId, targetExtent, colorFormat, capabilities, budget, visualCommandCount,
                resources, passes, dependencies, peakFrameLocalBytes, null, null, null, null, materialPlanTable)
        }

        /** Trust-boundary factory available only to the W4d compiler after public validation. */
        @JvmSynthetic
        internal fun issueW4dCompilerWitness(graph: RenderGraph): RenderGraph {
            require(
                W4dPathStrokePlanCompiler.isHistoricalCapabilityId(graph.capabilityId) ||
                    W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId),
            ) {
                "Only a W4d graph may receive a W4d compiler witness"
            }
            require(
                if (W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
                    graph.hasW5aPathDrawMaterialContract()
                } else {
                    graph.hasLegacyPathDrawColorContract()
                },
            ) { "W4d graph material authority does not match its capability version" }
            require(graph.w4dCompilerWitness == null) { "A W4d compiler witness may be issued only once" }
            return RenderGraph(
                graph.id,
                graph.capabilityId,
                graph.storedTargetExtent,
                graph.colorFormat,
                graph.capabilities,
                graph.budget,
                graph.visualCommandCount,
                graph.storedResources,
                graph.storedPasses,
                graph.storedDependencies,
                graph.peakFrameLocalBytes,
                W4dCompilerWitness.issue(graph),
                null,
                null,
                null,
                graph.materialPlanTable,
            )
        }

        /** Trust-boundary factory available only to the W4d.2 compiler after public validation. */
        @JvmSynthetic
        internal fun issueW4dGeneralCompilerWitness(graph: RenderGraph): RenderGraph {
            require(
                W4dGeneralPathPlanCompiler.isLegacyCapabilityId(graph.capabilityId) ||
                    W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId),
            ) { "Only a W4d.2 graph may receive a W4d.2 compiler witness" }
            require(
                if (W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
                    graph.hasW5aMaterialPathContract()
                } else {
                    graph.hasLegacyPathColorContract()
                },
            ) { "W4d.2 graph material authority does not match its capability version" }
            require(graph.w4dGeneralCompilerWitness == null) {
                "A W4d.2 compiler witness may be issued only once"
            }
            return RenderGraph(
                graph.id,
                graph.capabilityId,
                graph.storedTargetExtent,
                graph.colorFormat,
                graph.capabilities,
                graph.budget,
                graph.visualCommandCount,
                graph.storedResources,
                graph.storedPasses,
                graph.storedDependencies,
                graph.peakFrameLocalBytes,
                null,
                W4dGeneralCompilerWitness.issue(graph),
                null,
                null,
                graph.materialPlanTable,
            )
        }

        /** Trust-boundary factory available only to the W4e complex clip compiler. */
        @JvmSynthetic
        internal fun issueW4eCompilerWitness(
            graph: RenderGraph,
            nativePayload: W4eNativePayloadPlan,
        ): RenderGraph {
            require(
                W4eClipPlanCompiler.isLegacyCapabilityId(graph.capabilityId) ||
                    W4eClipPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId),
            ) { "Only a W4e graph may receive a W4e compiler witness" }
            require(
                if (W4eClipPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
                    graph.hasW5aMaterialPathContract()
                } else {
                    graph.hasLegacyPathColorContract()
                },
            ) { "W4e graph material authority does not match its capability version" }
            require(graph.w4dCompilerWitness == null && graph.w4dGeneralCompilerWitness == null &&
                graph.w4eNativePayloadPlan == null && graph.w4eCompilerWitness == null) {
                "A W4e graph must be sealed exactly once"
            }
            require(nativePayload.matchesDeclaredResources(graph.storedResources)) {
                "W4e graph V/I/U resources differ from the compiler-native payload"
            }
            val payloadGraph = RenderGraph(
                graph.id,
                graph.capabilityId,
                graph.storedTargetExtent,
                graph.colorFormat,
                graph.capabilities,
                graph.budget,
                graph.visualCommandCount,
                graph.storedResources,
                graph.storedPasses,
                graph.storedDependencies,
                graph.peakFrameLocalBytes,
                null,
                null,
                nativePayload,
                null,
                graph.materialPlanTable,
            )
            return RenderGraph(
                payloadGraph.id,
                payloadGraph.capabilityId,
                payloadGraph.storedTargetExtent,
                payloadGraph.colorFormat,
                payloadGraph.capabilities,
                payloadGraph.budget,
                payloadGraph.visualCommandCount,
                payloadGraph.storedResources,
                payloadGraph.storedPasses,
                payloadGraph.storedDependencies,
                payloadGraph.peakFrameLocalBytes,
                null,
                null,
                nativePayload,
                W4eCompilerWitness.issue(payloadGraph),
                payloadGraph.materialPlanTable,
            )
        }

        private fun referencedResources(pass: PlanPass): List<PlanResourceId> = when (pass) {
            is PlanPass.StencilGeometryProducerV3 -> listOf(pass.target, pass.depthStencil,
                pass.drawDataResources.vertex, pass.drawDataResources.index, pass.drawDataResources.uniform)
            is PlanPass.RenderPass -> buildList {
                add(pass.target)
                pass.draws().mapNotNull { (it.blend as? BlendPlan.DestinationReadV1)?.snapshotResource }.forEach(::add)
                pass.drawDataResources?.let { addAll(listOf(it.vertex, it.index, it.uniform)) }
                pass.draws().flatMap { it.clipStrategies() }.forEach { strategy ->
                    strategy.resourceReferences().forEach(::add)
                }
            }
            is PlanPass.PathMaskClearPass -> listOf(pass.target)
            is PlanPass.PathRenderPass -> buildList {
                add(pass.target)
                add(pass.drawDataResources.vertex)
                add(pass.drawDataResources.index)
                add(pass.drawDataResources.uniform)
                pass.depthStencil?.let(::add)
                pass.resolveTarget?.let(::add)
                pass.draw.binaryMaskedSourceOrNull()?.let { add(it.mask) }
                pass.draw.clipStrategyOrNull()?.resourceReferences()?.forEach(::add)
            }
            is PlanPass.StencilProducer -> listOf(
                pass.target,
                pass.depthStencil,
                pass.drawDataResources.vertex,
                pass.drawDataResources.index,
                pass.drawDataResources.uniform,
            )
            is PlanPass.StencilCover -> listOfNotNull(
                pass.target,
                pass.depthStencil,
                pass.drawDataResources.vertex,
                pass.drawDataResources.index,
                pass.drawDataResources.uniform,
                (pass.draw.blend as? BlendPlan.DestinationReadV1)?.snapshotResource,
            )
            is PlanPass.TextureCopy -> listOf(pass.source, pass.destination)
            is PlanPass.FilterPass -> pass.inputs() + pass.output
            is PlanPass.ResolvePass -> listOf(pass.source, pass.destination)
            is PlanPass.ReadbackPass -> listOf(pass.source, pass.staging)
            is PlanPass.ClipMaskInitialize -> listOf(pass.output)
            is PlanPass.ClipMaskProducer -> buildList {
                add(pass.target)
                pass.resolveTarget?.let(::add)
                pass.depthStencil?.let(::add)
            }
            is PlanPass.ClipMaskFold -> listOf(pass.previous, pass.source, pass.output)
        }

        private fun validateColorPasses(
            passes: List<PlanPass>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            capabilityId: String,
        ) {
            val colorPasses = passes.mapNotNull { pass ->
                when (pass) {
                    is PlanPass.StencilGeometryProducerV3 -> ColorAttachment(pass.target, pass.load, pass.store, SamplePlan.SingleSample)
                    is PlanPass.RenderPass -> {
                        require(pass.draws().all { it.sample == SamplePlan.SingleSample }) {
                            "Legacy render passes require single-sample draws"
                        }
                        require(pass.draws().none { it.unwrapClippedSource().let { source -> source is PathRenderDraw &&
                            !(source is GeneralPathDraw && capabilityId in setOf(W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID,
                                W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID)) } }) {
                            "General and binary masked path draws require explicit path render passes"
                        }
                        pass.draws().map { it.unwrapClippedSource() }.filterIsInstance<PathDraw>().forEach { draw ->
                            require(draw.strategy == PathFillStrategy.DirectTriangle) {
                                "Stencil path draws require atomic stencil passes"
                            }
                        }
                        ColorAttachment(pass.target, pass.load, pass.store, SamplePlan.SingleSample)
                    }
                    is PlanPass.StencilProducer -> ColorAttachment(
                        pass.target,
                        pass.load,
                        pass.store,
                        SamplePlan.SingleSample,
                    )
                    is PlanPass.StencilCover -> ColorAttachment(
                        pass.target,
                        pass.load,
                        pass.store,
                        SamplePlan.SingleSample,
                    )
                    is PlanPass.PathRenderPass -> when (pass.phase) {
                        PathRenderPhase.SingleSampleDirectColor,
                        PathRenderPhase.SingleSampleStencilProducer,
                        PathRenderPhase.SingleSampleStencilColorCover,
                        PathRenderPhase.MultisampleDirectColor,
                        PathRenderPhase.MultisampleStencilProducer,
                        PathRenderPhase.MultisampleStencilColorCover,
                        PathRenderPhase.HardEdgeBinaryColorCover,
                        -> ColorAttachment(pass.target, pass.load, pass.store, pass.draw.sample)
                        PathRenderPhase.HardEdgeMaskProducer,
                        PathRenderPhase.HardEdgeMaskStencilProducer,
                        PathRenderPhase.HardEdgeMaskStencilCover,
                        -> null
                    }
                    else -> null
                }
            }
            colorPasses.forEachIndexed { index, pass ->
                validateColorTarget(pass.target, resourcesById, targetExtent, colorFormat, pass.sample)
                val expectedLoad = if (index == 0) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
                require(pass.load == expectedLoad) { "Color attachment load does not preserve paint order" }
                require(pass.store == AttachmentStorePlan.Store) { "Color attachments must store every pass" }
            }
        }

        private fun validateColorTarget(
            targetId: PlanResourceId,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            sample: SamplePlan,
        ) {
            val target = requireNotNull(resourcesById[targetId])
            val expectedRole = when (sample) {
                SamplePlan.SingleSample -> PlanResourceRole.LogicalTarget
                SamplePlan.Multisample4 -> PlanResourceRole.MultisampleColorTarget
            }
            val expectedSampleCountI32 = when (sample) {
                SamplePlan.SingleSample -> 1
                SamplePlan.Multisample4 -> 4
            }
            require(target.role == expectedRole) { "Render target role does not match draw samples" }
            require(target.kind == PlanResourceKind.Texture2D) { "Render target must be a texture" }
            require(target.format == PlanTextureFormat.Color(colorFormat)) {
                "Render target format must match the graph color format"
            }
            require(target.copyExtent() == targetExtent) { "Render target extent must match the graph target" }
            require(target.sampleCountI32 == expectedSampleCountI32) { "Render target sample count does not match draw" }
            require(PlanResourceUsage.RenderAttachment in target.usages()) {
                "Render target must allow render attachment usage"
            }
        }

        private fun validatePassCapabilities(
            passes: List<PlanPass>,
            capabilities: PlanCapabilitySnapshot,
        ) {
            if (passes.any {
                    it is PlanPass.RenderPass ||
                        it is PlanPass.PathMaskClearPass ||
                        it is PlanPass.PathRenderPass ||
                        it is PlanPass.StencilProducer ||
                        it is PlanPass.StencilCover ||
                        it is PlanPass.ClipMaskInitialize ||
                        it is PlanPass.ClipMaskProducer ||
                        it is PlanPass.ClipMaskFold
                }
            ) {
                require(PlanOperationCapability.RenderPass in capabilities.supportedOperations()) {
                    "Render passes are unsupported"
                }
            }
            if (passes.any { it is PlanPass.ReadbackPass }) {
                require(PlanOperationCapability.Readback in capabilities.supportedOperations()) {
                    "Readback passes are unsupported"
                }
            }
        }

        private fun validateClipMaskContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            targetExtent: SizeI32,
        ) {
            val clipPasses = passes.filter {
                it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold
            }
            require(clipPasses.isNotEmpty()) { "Clip-mask validation requires clip passes" }
            val accumulators = resources.filter { it.role == PlanResourceRole.CoverageMaskAccumulator }
            require(accumulators.size >= 1) { "Clip-mask graphs require an accumulator" }
            fun coverage(id: PlanResourceId, role: PlanResourceRole, samples: Int): PlanResource {
                val resource = requireNotNull(resourcesById[id])
                require(resource.role == role && resource.kind == PlanResourceKind.Texture2D &&
                    resource.format == PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR) &&
                    resource.copyExtent() == targetExtent && resource.sampleCountI32 == samples &&
                    PlanResourceUsage.RenderAttachment in resource.usages()) {
                    "Clip-mask resource does not have the required linear coverage contract"
                }
                if (samples == 1) require(PlanResourceUsage.Sampled in resource.usages()) {
                    "Single-sample clip masks must be sampleable"
                }
                return resource
            }
            val dependencySet = dependencies.toSet()
            var index = 0
            var groups = 0
            while (index < passes.size && passes[index] is PlanPass.ClipMaskInitialize) {
                val initializePass = passes[index] as PlanPass.ClipMaskInitialize
                require(initializePass.clearCoverageF32 == 1f) {
                    "Clip-mask graphs must initialize coverage to one"
                }
                validateClipDomain(initializePass.copyDomainI32(), targetExtent)
                coverage(initializePass.output, PlanResourceRole.CoverageMaskAccumulator, 1)
                var currentAccumulator = initializePass.output
                index += 1
                while (index < passes.size && passes[index] is PlanPass.ClipMaskProducer) {
                    val producer = passes[index] as PlanPass.ClipMaskProducer
                    val fold = passes.getOrNull(index + 1) as? PlanPass.ClipMaskFold
                        ?: throw IllegalArgumentException("Each clip-mask producer requires an adjacent fold")
                    require(producer.atomicGroup == initializePass.atomicGroup && fold.atomicGroup == initializePass.atomicGroup) {
                        "Clip-mask groups must not interleave"
                    }
                    require(PlanPassDependency(producer.id, fold.id) in dependencySet) {
                        "Clip-mask producer and fold require a direct dependency"
                    }
                    validateClipMaskProducer(producer, resourcesById, capabilities, targetExtent)
                    validateClipMaskFold(fold, targetExtent)
                    require(fold.previous == currentAccumulator && fold.source == producer.resolveTargetOrTarget()) {
                        "Clip-mask folds must consume the current accumulator and preceding producer"
                    }
                    coverage(fold.previous, PlanResourceRole.CoverageMaskAccumulator, 1)
                    coverage(fold.source, PlanResourceRole.CoverageMaskScratch, 1)
                    coverage(fold.output, PlanResourceRole.CoverageMaskAccumulator, 1)
                    currentAccumulator = fold.output
                    index += 2
                }
                groups += 1
            }
            require(groups > 0) { "Clip-mask graphs must begin with an initializer" }
            require(passes.drop(index).none {
                it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold
            }) { "Clip-mask passes must form ordered initialization/producer/fold prefixes" }

            resources.filter { it.role in setOf(
                PlanResourceRole.CoverageMaskAccumulator,
                PlanResourceRole.CoverageMaskScratch,
                PlanResourceRole.CoverageMaskMultisampleScratch,
                PlanResourceRole.CoverageMaskDepthStencil,
            ) }.forEach { resource ->
                val lastUse = passes.indices.lastOrNull { resource.id in referencedResources(passes[it]) }
                require(lastUse != null && resource.lastPassIndexExclusive == lastUse + 1) {
                    "Clip-mask resource lifetimes must end at their final consumer"
                }
            }
        }

        private fun validateClipConsumers(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
            usesClipMasks: Boolean,
        ) {
            val consumers = passes.flatMapIndexed { index, pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws().flatMap { draw ->
                    draw.clipStrategies().flatMap { it.maskStrategies() }.map { Triple(index, it.maskResource(), it) }
                }
                is PlanPass.PathRenderPass -> pass.draw.clipStrategyOrNull()?.maskStrategies().orEmpty().map {
                    Triple(index, it.maskResource(), it)
                }
                else -> emptyList()
            } }
            passes.forEach { pass -> pass.clipStrategies().flatMap { it.allStrategies() }
                .filterIsInstance<ClipPlanStrategy.Stencil>().forEach { stencil ->
                when (pass) {
                    is PlanPass.RenderPass -> require(pass.target != stencil.depthStencil) {
                        "Clip stencil cannot alias color target"
                    }
                    is PlanPass.PathRenderPass -> require(pass.target != stencil.depthStencil) {
                        "Clip stencil cannot alias color target"
                    }
                    else -> Unit
                }
                val resource = requireNotNull(resourcesById[stencil.depthStencil]) { "Clip stencil resource is unknown" }
                val expectedSampleCount = when (pass) {
                    is PlanPass.RenderPass -> 1
                    is PlanPass.PathRenderPass -> if (pass.draw.sample == SamplePlan.Multisample4) 4 else 1
                    else -> error("Only draw passes may carry a clip strategy")
                }
                require(resource.role == PlanResourceRole.DepthStencil &&
                    resource.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                    resource.copyExtent() == targetExtent &&
                    resource.sampleCountI32 == expectedSampleCount &&
                    PlanResourceUsage.DepthStencilAttachment in resource.usages()) {
                    "Clip stencil requires a matching D24S8 depth-stencil resource"
                }
            } }
            if (!usesClipMasks) {
                require(consumers.isEmpty()) { "Mask clip consumers require a clip-mask graph" }
                return
            }
            require(consumers.isNotEmpty()) { "Clip-mask producers require a consumer" }
            val writers = mutableMapOf<PlanResourceId, PlanPassId>()
            val finalMasks = mutableMapOf<PlanResourceId, PlanPass.ClipMaskInitialize>()
            var index = 0
            while (index < passes.size && passes[index] is PlanPass.ClipMaskInitialize) {
                val initialize = passes[index] as PlanPass.ClipMaskInitialize
                writers[initialize.output] = initialize.id
                var finalMask = initialize.output
                index += 1
                while (index < passes.size && passes[index] is PlanPass.ClipMaskProducer) {
                    val producer = passes[index] as PlanPass.ClipMaskProducer
                    val fold = passes[index + 1] as PlanPass.ClipMaskFold
                    require(producer.atomicGroup == initialize.atomicGroup && fold.atomicGroup == initialize.atomicGroup) {
                        "Clip-mask consumers require non-interleaved groups"
                    }
                    finalMask = fold.output
                    writers[fold.output] = fold.id
                    index += 2
                }
                finalMasks[finalMask] = initialize
            }
            consumers.forEach { (index, mask, strategy) ->
                val initialize = requireNotNull(finalMasks[mask]) { "Clip consumers must sample their final accumulator" }
                val writer = requireNotNull(writers[mask]) { "Clip consumer mask has no producer" }
                require(pathExists(writer, passes[index].id, dependencies)) {
                    "Clip consumers require a dependency from their mask producer"
                }
                require(resourcesById[mask]?.role == PlanResourceRole.CoverageMaskAccumulator) {
                    "Clip consumers must sample a coverage-mask accumulator"
                }
                if (strategy is ClipPlanStrategy.InverseMask) {
                    require(strategy.geometryF32.copyDomainI32() == initialize.copyDomainI32()) {
                        "Inverse clip domains must match their initialized mask domain"
                    }
                }
            }
        }

        private fun validateClipMaskProducer(
            pass: PlanPass.ClipMaskProducer,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            targetExtent: SizeI32,
        ) {
            require(pass.sampleCountI32 == 1 || pass.sampleCountI32 == 4) {
                "Clip-mask producers must be single-sample or AA4"
            }
            require(pass.copyGeometryF32() != ClipGeometryF32.Empty) {
                "Zero inverse interiors must not allocate a clip-mask producer"
            }
            if (pass.antiAlias && pass.copyGeometryF32() !is ClipGeometryF32.Rect) {
                require(pass.sampleCountI32 == 4) {
                    "Antialiased Path/RRect clip producers require AA4 scratch, resolve, and D24S8"
                }
            }
            if (pass.sampleCountI32 == 4) {
                require(pass.antiAlias) {
                    "AA4 clip producers must preserve their antialiasing fact"
                }
            }
            val target = requireNotNull(resourcesById[pass.target])
            if (pass.sampleCountI32 == 1) {
                require(pass.resolveTarget == null && target.role == PlanResourceRole.CoverageMaskScratch &&
                    target.sampleCountI32 == 1) { "Hard clip producers require a single-sample scratch target" }
                pass.depthStencil?.let { id ->
                    validateClipMaskDepthStencil(requireNotNull(resourcesById[id]), 1, targetExtent)
                }
            } else {
                require(target.role == PlanResourceRole.CoverageMaskMultisampleScratch && target.sampleCountI32 == 4) {
                    "AA4 clip producers require a multisample scratch target"
                }
                val resolve = requireNotNull(pass.resolveTarget) { "AA4 clip producers require a resolve target" }
                val resolved = requireNotNull(resourcesById[resolve])
                require(resolved.role == PlanResourceRole.CoverageMaskScratch && resolved.sampleCountI32 == 1 &&
                    capabilities.supportsResolve(target.format!!, 4, 1)) {
                    "AA4 clip producers require supported linear coverage resolve"
                }
                val depth = requireNotNull(pass.depthStencil) { "AA4 clip producers require D24S8" }
                validateClipMaskDepthStencil(requireNotNull(resourcesById[depth]), 4, targetExtent)
            }
        }

        private fun validateClipMaskDepthStencil(resource: PlanResource, samples: Int, targetExtent: SizeI32) {
            require(resource.role == PlanResourceRole.CoverageMaskDepthStencil &&
                resource.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                resource.copyExtent() == targetExtent && resource.sampleCountI32 == samples &&
                PlanResourceUsage.DepthStencilAttachment in resource.usages()) {
                "Clip-mask depth-stencil must be matching D24S8"
            }
        }

        private fun validateClipMaskFold(pass: PlanPass.ClipMaskFold, targetExtent: SizeI32) {
            require(pass.previous != pass.output) { "Clip mask fold cannot sample its output attachment" }
            require(pass.source != pass.output) { "Clip mask producer cannot alias fold output" }
            require(pass.previous != pass.source) { "Clip mask fold inputs must be distinct" }
            validateClipDomain(pass.copyDomainI32(), targetExtent)
        }

        private fun validateClipDomain(domain: org.graphiks.math.geometry.RectI32, targetExtent: SizeI32) {
            require(!domain.isEmpty && domain.left >= 0 && domain.top >= 0 &&
                domain.right <= targetExtent.width && domain.bottom <= targetExtent.height) {
                "Clip-mask domain must be a non-empty target-domain rectangle"
            }
        }

        private fun PlanPass.ClipMaskProducer.resolveTargetOrTarget(): PlanResourceId = resolveTarget ?: target

        private fun ClipPlanStrategy.maskStrategies(): List<ClipPlanStrategy> = when (this) {
            is ClipPlanStrategy.Mask -> listOf(this)
            is ClipPlanStrategy.InverseMask -> listOf(this)
            is ClipPlanStrategy.InverseDomain -> emptyList()
            is ClipPlanStrategy.Scissor -> child?.maskStrategies().orEmpty()
            is ClipPlanStrategy.Stencil -> child?.maskStrategies().orEmpty()
        }

        private fun ClipPlanStrategy.allStrategies(): List<ClipPlanStrategy> = when (this) {
            is ClipPlanStrategy.Mask, is ClipPlanStrategy.InverseMask -> listOf(this)
            is ClipPlanStrategy.InverseDomain -> listOf(this)
            is ClipPlanStrategy.Scissor -> listOf(this) + child?.allStrategies().orEmpty()
            is ClipPlanStrategy.Stencil -> listOf(this) + child?.allStrategies().orEmpty()
        }

        private fun ClipPlanStrategy.maskResource(): PlanResourceId = when (this) {
            is ClipPlanStrategy.Mask -> resource
            is ClipPlanStrategy.InverseMask -> resource
            is ClipPlanStrategy.InverseDomain,
            is ClipPlanStrategy.Scissor,
            is ClipPlanStrategy.Stencil,
            -> error("Only mask leaves have a resource")
        }

        private fun ClipPlanStrategy.resourceReferences(): List<PlanResourceId> = when (this) {
            is ClipPlanStrategy.Mask -> listOf(resource)
            is ClipPlanStrategy.InverseMask -> listOf(resource)
            is ClipPlanStrategy.InverseDomain -> emptyList()
            is ClipPlanStrategy.Scissor -> child?.resourceReferences().orEmpty()
            is ClipPlanStrategy.Stencil -> listOf(depthStencil) + child?.resourceReferences().orEmpty()
        }

        private fun PlanPass.clipStrategies(): List<ClipPlanStrategy> = when (this) {
            is PlanPass.RenderPass -> draws().flatMap { it.clipStrategies() }
            is PlanPass.PathRenderPass -> listOfNotNull(draw.clipStrategyOrNull())
            else -> emptyList()
        }

        private fun PlanDraw.clipStrategies(): List<ClipPlanStrategy> = when (this) {
            is ClippedPlanDraw -> listOf(strategy) + source.clipStrategies()
            is W5bPointDraw -> listOfNotNull(clipOnly?.let { ClipPlanStrategy.Mask(it.maskResource) })
            else -> emptyList()
        }

        private fun PlanDraw.unwrapClippedSource(): PlanDraw {
            var source = this
            while (source is ClippedPlanDraw) source = source.source
            return source
        }

        private fun PathRenderDraw.binaryMaskedSourceOrNull(): BinaryMaskedPathDraw? = when (this) {
            is BinaryMaskedPathDraw -> this
            is ClippedBinaryMaskedPathDraw -> source
            is GeneralPathDraw, is ClippedGeneralPathDraw -> null
        }

        private fun PathRenderDraw.clipStrategyOrNull(): ClipPlanStrategy? = when (this) {
            is ClippedBinaryMaskedPathDraw -> clip
            is ClippedGeneralPathDraw -> clip
            is BinaryMaskedPathDraw, is GeneralPathDraw -> null
        }

        private fun PathRenderDraw.generalSourceOrNull(): GeneralPathDraw? = when (this) {
            is GeneralPathDraw -> this
            is ClippedGeneralPathDraw -> source
            is BinaryMaskedPathDraw, is ClippedBinaryMaskedPathDraw -> null
        }

        private fun pathExists(
            before: PlanPassId,
            after: PlanPassId,
            dependencies: List<PlanPassDependency>,
        ): Boolean {
            val outgoing = dependencies.groupBy({ it.before }, { it.after })
            val pending = ArrayDeque<PlanPassId>()
            val visited = mutableSetOf<PlanPassId>()
            pending += before
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!visited.add(current)) continue
                if (current == after) return true
                outgoing[current].orEmpty().forEach { pending += it }
            }
            return false
        }

        private fun validateStencilAtomicContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            targetExtent: SizeI32,
        ) {
            val producers = passes.filterIsInstance<PlanPass.StencilProducer>()
            val covers = passes.filterIsInstance<PlanPass.StencilCover>()
            val depthStencilResources = resources.filter { it.role == PlanResourceRole.DepthStencil }
            if (producers.isEmpty() && covers.isEmpty()) {
                require(depthStencilResources.isEmpty()) {
                    "Depth-stencil resources require stencil producer and cover passes"
                }
                return
            }

            require(PlanOperationCapability.DepthStencilAttachment in capabilities.supportedOperations()) {
                "Depth-stencil attachments are unsupported"
            }
            require(PlanOperationCapability.StencilCover in capabilities.supportedOperations()) {
                "Stencil cover passes are unsupported"
            }
            require(depthStencilResources.size == 1) { "Stencil graphs require one depth-stencil texture" }
            require(producers.size == covers.size) { "Stencil producer and cover counts must match" }
            require(producers.map { it.atomicGroup }.distinct().size == producers.size) {
                "Stencil atomic groups must be unique"
            }

            val depthStencil = depthStencilResources.single()
            require(depthStencil.kind == PlanResourceKind.Texture2D) { "Depth-stencil must be a texture" }
            require(
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            ) { "Stencil graphs require Depth24PlusStencil8" }
            require(depthStencil.copyExtent() == targetExtent) { "Depth-stencil extent must match the target" }
            require(depthStencil.sampleCountI32 == 1) { "W4c D24S8 must be single-sample" }
            require(PlanResourceUsage.DepthStencilAttachment in depthStencil.usages()) {
                "Depth-stencil texture must allow depth-stencil attachment usage"
            }

            val firstProducerIndex = passes.indexOfFirst { it is PlanPass.StencilProducer }
            require(depthStencil.firstPassIndex == firstProducerIndex) {
                "Depth-stencil lifetime must begin at the first stencil producer"
            }
            val readbackIndices = passes.indices.filter { passes[it] is PlanPass.ReadbackPass }
            require(readbackIndices.isNotEmpty()) { "Stencil graphs require a readback" }
            require(depthStencil.lastPassIndexExclusive > readbackIndices.last()) {
                "Depth-stencil physical lifetime must include readback"
            }

            val stencilTarget = producers.first().target
            require(producers.all { it.target == stencilTarget }) { "Stencil producers must share one target" }
            require(covers.all { it.target == stencilTarget }) { "Stencil covers must share one target" }
            require(readbackIndices.any { (passes[it] as PlanPass.ReadbackPass).source == stencilTarget }) {
                "Stencil target must be read back"
            }

            val dependencySet = dependencies.toSet()
            passes.zipWithNext().forEach { (before, after) ->
                require(PlanPassDependency(before.id, after.id) in dependencySet) {
                    "Stencil graphs require linear pass dependencies"
                }
            }
            passes.forEachIndexed { index, pass ->
                when (pass) {
                    is PlanPass.StencilProducer -> {
                        val cover = passes.getOrNull(index + 1) as? PlanPass.StencilCover
                        requireNotNull(cover) { "Stencil covers must be adjacent to their producers" }
                        require(PlanPassDependency(pass.id, cover.id) in dependencySet) {
                            "Stencil producer and cover require a direct dependency"
                        }
                        validateAtomicStencilPair(pass, cover, depthStencil.id, resourcesById)
                    }
                    is PlanPass.StencilCover -> {
                        require(passes.getOrNull(index - 1) is PlanPass.StencilProducer) {
                            "Stencil covers must immediately follow a producer"
                        }
                    }
                    else -> Unit
                }
            }
        }

        private fun validateAtomicStencilPair(
            producer: PlanPass.StencilProducer,
            cover: PlanPass.StencilCover,
            depthStencilId: PlanResourceId,
            resourcesById: Map<PlanResourceId, PlanResource>,
        ) {
            require(producer.target == cover.target) { "Stencil pair targets must match" }
            require(producer.depthStencil == depthStencilId && cover.depthStencil == depthStencilId) {
                "Stencil pairs must use the graph depth-stencil texture"
            }
            require(producer.draw === cover.draw) { "Stencil pairs must share one immutable path draw" }
            require(producer.draw.commandIndex == cover.draw.commandIndex) { "Stencil pair commands must match" }
            require(producer.draw.strategy == PathFillStrategy.StencilCover) {
                "Stencil producers require stencil-cover path geometry"
            }
            require(cover.draw.strategy == PathFillStrategy.StencilCover) {
                "Stencil covers require stencil-cover path geometry"
            }
            require(producer.drawDataResources == cover.drawDataResources) {
                "Stencil pairs must share vertex, index, and uniform resources"
            }
            require(producer.atomicGroup == cover.atomicGroup) { "Stencil pairs must share an atomic group" }
            val expectedAtomicGroup = canonicalPathAtomicGroup(producer.draw)
            require(producer.atomicGroup == expectedAtomicGroup && cover.atomicGroup == expectedAtomicGroup) {
                "Stencil pairs require the canonical command atomic group"
            }
            require(producer.depthStencilAccess == PlanDepthStencilAccess.Write) {
                "Stencil producers require write access"
            }
            require(cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite) {
                "Stencil covers require read-write access"
            }
            require(producer.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore) {
                "Stencil producers require clear-zero store"
            }
            require(cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset) {
                "Stencil covers require load-store test-reset"
            }
            validatePathDrawDataShape(producer.drawDataResources, resourcesById)
        }

        private fun validateExplicitAa4PathContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            visualCommandCount: Int,
        ) {
            require(passes.any { it is PlanPass.PathRenderPass }) { "Explicit path graphs require path render passes" }
            require(passes.all {
                it is PlanPass.PathMaskClearPass ||
                    it is PlanPass.PathRenderPass ||
                    it is PlanPass.ClipMaskInitialize ||
                    it is PlanPass.ClipMaskProducer ||
                    it is PlanPass.ClipMaskFold ||
                    it is PlanPass.ReadbackPass
            }) { "Explicit path graphs may contain only path, mask, and readback passes" }
            require(PlanOperationCapability.CopyUpload in capabilities.supportedOperations()) {
                "Explicit path draws require copy upload support"
            }
            require(PlanOperationCapability.UniformBuffer in capabilities.supportedOperations()) {
                "Explicit path draws require uniform buffer support"
            }
            val pathPasses = passes.mapIndexedNotNull { index, pass ->
                (pass as? PlanPass.PathRenderPass)?.let { index to it }
            }
            val usesAa4 = pathPasses.any { (_, pass) -> pass.draw.sample == SamplePlan.Multisample4 } ||
                resources.any { it.role == PlanResourceRole.MultisampleColorTarget }
            if (usesAa4) {
                validateFourSampleExplicitPathContracts(
                    passes, dependencies, resources, resourcesById, capabilities, targetExtent, colorFormat,
                    visualCommandCount, pathPasses,
                )
            } else {
                validateSingleSampleExplicitPathContracts(
                    passes, dependencies, resources, resourcesById, targetExtent, colorFormat,
                    visualCommandCount, pathPasses,
                )
            }
        }

        private fun validateSingleSampleExplicitPathContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            visualCommandCount: Int,
            pathPasses: List<Pair<Int, PlanPass.PathRenderPass>>,
        ) {
            require(passes.none { it is PlanPass.PathMaskClearPass }) {
                "Single-sample explicit paths may not allocate hard-edge masks"
            }
            require(resources.none {
                it.role == PlanResourceRole.MultisampleColorTarget ||
                    it.role == PlanResourceRole.PathHardEdgeMask ||
                    it.role == PlanResourceRole.PathHardEdgeDepthStencil
            }) { "Single-sample explicit paths may not declare AA4 resources" }
            require(resources.all {
                it.role in setOf(
                    PlanResourceRole.LogicalTarget,
                    PlanResourceRole.ReadbackStaging,
                    PlanResourceRole.VertexData,
                    PlanResourceRole.IndexData,
                    PlanResourceRole.UniformData,
                    PlanResourceRole.DepthStencil,
                    PlanResourceRole.CoverageMaskAccumulator,
                    PlanResourceRole.CoverageMaskScratch,
                    PlanResourceRole.CoverageMaskDepthStencil,
                    PlanResourceRole.GradientStopData,
                )
            }) { "Single-sample explicit paths may declare only their direct resource inventory" }
            val referencedResourceIds = passes.flatMap(::referencedResources).toSet()
            // The shared material source consumes the sealed stop slab separately from W4
            // geometry references; producer-only passes still carry no material binding.
            require(resources.all { it.id in referencedResourceIds || it.role == PlanResourceRole.GradientStopData }) {
                "Single-sample explicit path resources must be consumed by a pass"
            }
            require(pathPasses.all { (_, pass) -> pass.draw.sample == SamplePlan.SingleSample }) {
                "Single-sample explicit paths may not contain four-sample draws"
            }
            val target = requireExplicitLogicalTarget(resources, targetExtent, colorFormat)
            val readback = requireExplicitTerminalReadback(passes, target.id)
            requireLinearDependencies(passes, dependencies, "Explicit path graphs")
            val drawData = requireExplicitDrawData(resources, resourcesById, pathPasses)
            require(pathPasses.all { (_, pass) -> pass.resolveTarget == null }) {
                "Single-sample explicit paths must not resolve"
            }

            val stencilPairs = mutableListOf<Pair<Int, Int>>()
            pathPasses.forEach { (index, pass) ->
                when (pass.phase) {
                    PathRenderPhase.SingleSampleDirectColor -> {
                        val inverseDomainInterior = (pass.draw.clipStrategyOrNull() as? ClipPlanStrategy.InverseDomain)
                            ?.geometryF32?.interiorCoverageF32 is
                            org.graphiks.math.geometry.InverseInteriorCoverageF32.Geometry
                        val declaredInverseDepth = pass.depthStencil?.let(resourcesById::get)
                        require((pass.draw is GeneralPathDraw || pass.draw is ClippedGeneralPathDraw) &&
                            pass.draw.coverage == CoveragePlan.FullOrScissor &&
                            pass.draw.strategy == PathFillStrategy.DirectTriangle &&
                            pass.target == target.id && pass.atomicGroup == null &&
                            pass.depthStencilAccess == null && pass.depthStencilLoadStore == null &&
                            if (inverseDomainInterior) {
                                declaredInverseDepth != null &&
                                    declaredInverseDepth.role == PlanResourceRole.DepthStencil &&
                                    declaredInverseDepth.kind == PlanResourceKind.Texture2D &&
                                    declaredInverseDepth.format == PlanTextureFormat.DepthStencil(
                                        PlanDepthStencilFormat.Depth24PlusStencil8,
                                    ) &&
                                    declaredInverseDepth.copyExtent() == targetExtent &&
                                    declaredInverseDepth.sampleCountI32 == 1 &&
                                    PlanResourceUsage.DepthStencilAttachment in declaredInverseDepth.usages() &&
                                    declaredInverseDepth.firstPassIndex <= index &&
                                    declaredInverseDepth.lastPassIndexExclusive > index
                            } else {
                                pass.depthStencil == null
                            }) {
                            "Single-sample direct color passes require a direct hard draw and logical target"
                        }
                    }
                    PathRenderPhase.SingleSampleStencilProducer -> {
                        val cover = passes.getOrNull(index + 1) as? PlanPass.PathRenderPass
                            ?: throw IllegalArgumentException("Single-sample stencil producers require an adjacent cover")
                        validateExplicitStencilPair(
                            pass, index, cover, index + 1, target.id, PlanResourceRole.DepthStencil,
                            SamplePlan.SingleSample, CoveragePlan.FullOrScissor, resourcesById, targetExtent,
                        )
                        stencilPairs += index to (index + 1)
                    }
                    PathRenderPhase.SingleSampleStencilColorCover -> require(
                        passes.getOrNull(index - 1) is PlanPass.PathRenderPass &&
                            (passes[index - 1] as PlanPass.PathRenderPass).phase ==
                                PathRenderPhase.SingleSampleStencilProducer,
                    ) { "Single-sample stencil covers must immediately follow their producer" }
                    else -> throw IllegalArgumentException("Single-sample explicit paths use only single-sample phases")
                }
            }
            val usesStencil = stencilPairs.isNotEmpty()
            val depthResources = resources.filter { it.role == PlanResourceRole.DepthStencil }
            val declaredDepthUses = pathPasses.mapNotNull { (index, pass) ->
                pass.depthStencil?.let { depth -> index to depth }
            }.groupBy({ (_, depth) -> depth }, { (index, _) -> index })
            require(depthResources.map(PlanResource::id).toSet() == declaredDepthUses.keys) {
                "Single-sample D24S8 inventory must be declared by exactly one or more sealed path uses"
            }
            if (usesStencil) {
                require(stencilPairs.flatMap { (producer, cover) ->
                    listOf(
                        requireNotNull((passes[producer] as PlanPass.PathRenderPass).depthStencil),
                        requireNotNull((passes[cover] as PlanPass.PathRenderPass).depthStencil),
                    )
                }.distinct().size == 1) {
                    "Single-sample stencil producer and cover must share one declared D24S8 resource"
                }
            }
            declaredDepthUses.forEach { (depthId, indices) ->
                val depth = requireNotNull(resourcesById[depthId])
                require(depth.firstPassIndex <= indices.min() &&
                    depth.lastPassIndexExclusive > indices.max()) {
                    "Single-sample D24S8 lifetime must cover every sealed path use"
                }
            }
            validateExplicitVisualDraws(
                pathPasses.filter { (_, pass) ->
                    pass.phase == PathRenderPhase.SingleSampleDirectColor ||
                        pass.phase == PathRenderPhase.SingleSampleStencilColorCover
                },
                visualCommandCount,
            )
            require(target.lastPassIndexExclusive > passes.lastIndex &&
                target.firstPassIndex <= pathPasses.first().first) {
                "Single-sample logical target must remain alive through readback"
            }
            require(readback.source == target.id) { "Single-sample readback must consume the logical target" }
        }

        private fun validateFourSampleExplicitPathContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            visualCommandCount: Int,
            pathPasses: List<Pair<Int, PlanPass.PathRenderPass>>,
        ) {
            require(resources.any { it.role == PlanResourceRole.MultisampleColorTarget } &&
                pathPasses.any { (_, pass) -> pass.draw.sample == SamplePlan.Multisample4 }) {
                "AA4 resources and four-sample path draws must appear together"
            }
            val usesDepthStencilAttachment = pathPasses.any { (_, pass) ->
                pass.depthStencil != null
            }
            if (usesDepthStencilAttachment) {
                require(PlanOperationCapability.DepthStencilAttachment in capabilities.supportedOperations()) {
                    "AA4 depth-stencil attachments require depth-stencil attachment support"
                }
            }
            val usesStencilCover = pathPasses.any { (_, pass) ->
                pass.phase == PathRenderPhase.MultisampleStencilProducer ||
                    pass.phase == PathRenderPhase.MultisampleStencilColorCover ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilProducer ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilCover
            }
            if (usesStencilCover) {
                require(PlanOperationCapability.StencilCover in capabilities.supportedOperations()) {
                    "AA4 stencil paths require stencil cover support"
                }
            }
            val explicitRoles = setOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.MultisampleColorTarget,
                PlanResourceRole.PathHardEdgeMask,
                PlanResourceRole.PathHardEdgeDepthStencil,
                PlanResourceRole.GradientStopData,
                PlanResourceRole.CoverageMaskAccumulator,
                PlanResourceRole.CoverageMaskScratch,
                PlanResourceRole.CoverageMaskMultisampleScratch,
                PlanResourceRole.CoverageMaskDepthStencil,
                PlanResourceRole.ReadbackStaging,
                PlanResourceRole.VertexData,
                PlanResourceRole.IndexData,
                PlanResourceRole.UniformData,
                PlanResourceRole.DepthStencil,
            )
            require(resources.all { it.role in explicitRoles }) {
                "AA4 graphs may declare only explicit path graph resources"
            }
            val referencedResourceIds = passes.flatMap(::referencedResources).toSet()
            val hasAaColorPath = pathPasses.any { (_, pass) ->
                pass.phase == PathRenderPhase.MultisampleDirectColor ||
                    pass.phase == PathRenderPhase.MultisampleStencilProducer ||
                    pass.phase == PathRenderPhase.MultisampleStencilColorCover
            }
            require(resources.all { resource ->
                resource.id in referencedResourceIds || resource.role == PlanResourceRole.GradientStopData ||
                    (!hasAaColorPath && resource.role == PlanResourceRole.DepthStencil && resource.sampleCountI32 == 4)
            }) {
                "AA4 graph resources must be consumed by an explicit pass"
            }

            val multisampleTargets = resources.filter { it.role == PlanResourceRole.MultisampleColorTarget }
            require(multisampleTargets.size == 1) { "AA4 graphs require one multisample color target" }
            val multisampleTarget = multisampleTargets.single()
            require(multisampleTarget.kind == PlanResourceKind.Texture2D) {
                "AA4 multisample color target must be a texture"
            }
            require(multisampleTarget.format == PlanTextureFormat.Color(colorFormat)) {
                "AA4 multisample color target format must match the graph color format"
            }
            require(multisampleTarget.copyExtent() == targetExtent && multisampleTarget.sampleCountI32 == 4) {
                "AA4 multisample color target must have the graph extent and four samples"
            }
            require(PlanResourceUsage.RenderAttachment in multisampleTarget.usages()) {
                "AA4 multisample color target must be renderable"
            }

            val resolvedTargets = resources.filter { it.role == PlanResourceRole.LogicalTarget }
            require(resolvedTargets.size == 1) { "AA4 graphs require one resolved logical target" }
            val resolvedTarget = resolvedTargets.single()
            require(resolvedTarget.kind == PlanResourceKind.Texture2D) { "AA4 resolved target must be a texture" }
            require(resolvedTarget.format == PlanTextureFormat.Color(colorFormat)) {
                "AA4 resolved target format must match the graph color format"
            }
            require(resolvedTarget.copyExtent() == targetExtent && resolvedTarget.sampleCountI32 == 1) {
                "AA4 resolved target must have the graph extent and one sample"
            }
            require(PlanResourceUsage.CopySource in resolvedTarget.usages()) {
                "AA4 resolved target must support readback copies"
            }
            require(PlanResourceUsage.RenderAttachment in resolvedTarget.usages()) {
                "AA4 resolved target must support render attachment usage"
            }

            val readbacks = passes.filterIsInstance<PlanPass.ReadbackPass>()
            require(readbacks.size == 1 && passes.last() === readbacks.single()) {
                "AA4 graphs require one terminal readback"
            }
            require(readbacks.single().source == resolvedTarget.id) {
                "AA4 readback must consume the resolved logical target"
            }
            requireLinearDependencies(passes, dependencies, "AA4 graphs")

            val vertex = requireSinglePathResource(resources, PlanResourceRole.VertexData)
            val index = requireSinglePathResource(resources, PlanResourceRole.IndexData)
            val uniform = requireSinglePathResource(resources, PlanResourceRole.UniformData)
            val drawDataResources = PlanDrawDataResources(vertex.id, index.id, uniform.id)
            validatePathDrawDataShape(drawDataResources, resourcesById)
            val pathPassIndices = passes.indices.filter { passes[it] is PlanPass.PathRenderPass }
            val lastPathPassIndex = pathPassIndices.maxOrNull()
                ?: throw IllegalArgumentException("AA4 graphs require path render passes")
            listOf(vertex, index, uniform).forEach { resource ->
                require(resource.lastPassIndexExclusive > lastPathPassIndex) {
                    "AA4 path draw data must remain alive through the consuming color draw"
                }
            }
            pathPasses.forEach { (_, pass) ->
                require(pass.drawDataResources == drawDataResources) {
                    "AA4 path passes must share one vertex, index, and uniform triplet"
                }
                require(pass.store == AttachmentStorePlan.Store) { "AA4 path attachments must store" }
            }

            val colorPasses = pathPasses.filter { (_, pass) ->
                pass.phase == PathRenderPhase.MultisampleDirectColor ||
                    pass.phase == PathRenderPhase.MultisampleStencilColorCover ||
                    pass.phase == PathRenderPhase.HardEdgeBinaryColorCover
            }
            require(colorPasses.isNotEmpty()) { "AA4 graphs require a color-producing path pass" }
            require(pathPasses.filter { (_, pass) -> pass !in colorPasses.map { it.second } }.all { (_, pass) ->
                pass.resolveTarget == null
            }) { "Only AA4 color-producing passes may resolve" }
            colorPasses.forEach { (_, pass) ->
                require(pass.target == multisampleTarget.id) {
                    "AA4 color-producing passes must target the multisample color target"
                }
                require(pass.draw.sample == SamplePlan.Multisample4) {
                    "AA4 color-producing passes must use four-sample draws"
                }
            }
            val visualDraws = colorPasses.map { (_, pass) -> pass.draw }
            require(visualCommandCount == visualDraws.map { it.commandIndex }.distinct().size) {
                "AA4 visual command count must match unique color draws"
            }
            visualDraws.zipWithNext().forEach { (before, after) ->
                require(before.commandIndex < after.commandIndex) {
                    "AA4 visual commands must be strictly ascending"
                }
            }
            val (finalColorIndex, finalColorPass) = colorPasses.last()
            require(finalColorPass.resolveTarget == resolvedTarget.id) {
                "Only the final AA4 color-producing pass must resolve to the logical target"
            }
            require(colorPasses.dropLast(1).all { (_, pass) -> pass.resolveTarget == null }) {
                "Only the final AA4 color-producing pass may resolve"
            }
            require(
                capabilities.supportsResolve(
                    PlanTextureFormat.Color(colorFormat),
                    multisampleTarget.sampleCountI32,
                    resolvedTarget.sampleCountI32,
                ),
            ) { "AA4 color resolve is unsupported" }
            require(multisampleTarget.lastPassIndexExclusive > finalColorIndex) {
                "AA4 multisample target must remain alive through resolve"
            }
            require(resolvedTarget.firstPassIndex <= finalColorIndex &&
                resolvedTarget.lastPassIndexExclusive > passes.lastIndex) {
                "AA4 resolved target must remain alive through resolve and readback"
            }

            val coveredHardPassIndices = mutableSetOf<Int>()
            val coveredMaskClearIndices = mutableSetOf<Int>()
            pathPasses.forEach { (passIndex, pass) ->
                when (pass.phase) {
                    PathRenderPhase.MultisampleDirectColor -> {
                        val depthStencil = pass.depthStencil?.let { resourceId ->
                            requireNotNull(resourcesById[resourceId])
                        }
                        require((pass.draw is GeneralPathDraw || pass.draw is ClippedGeneralPathDraw) &&
                            pass.draw.coverage == CoveragePlan.StencilAA4 &&
                            pass.draw.strategy == PathFillStrategy.DirectTriangle &&
                            pass.target == multisampleTarget.id && pass.atomicGroup == null &&
                            pass.depthStencilAccess == null && pass.depthStencilLoadStore == null &&
                            (depthStencil == null ||
                                depthStencil.role == PlanResourceRole.DepthStencil &&
                                    depthStencil.format == PlanTextureFormat.DepthStencil(
                                        PlanDepthStencilFormat.Depth24PlusStencil8,
                                    ) &&
                                    depthStencil.copyExtent() == targetExtent &&
                                    depthStencil.sampleCountI32 == 4 &&
                                    PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())) {
                        "AA4 direct color passes require a four-sample direct path draw"
                    }
                    }
                    PathRenderPhase.MultisampleStencilProducer -> {
                        val cover = passes.getOrNull(passIndex + 1) as? PlanPass.PathRenderPass
                            ?: throw IllegalArgumentException("AA4 stencil producers require an adjacent cover")
                        validateExplicitStencilPair(
                            pass, passIndex, cover, passIndex + 1, multisampleTarget.id,
                            PlanResourceRole.DepthStencil, SamplePlan.Multisample4, CoveragePlan.StencilAA4,
                            resourcesById, targetExtent,
                        )
                    }
                    PathRenderPhase.MultisampleStencilColorCover -> require(
                        passes.getOrNull(passIndex - 1) is PlanPass.PathRenderPass &&
                            (passes[passIndex - 1] as PlanPass.PathRenderPass).phase ==
                                PathRenderPhase.MultisampleStencilProducer,
                    ) { "AA4 stencil covers must immediately follow their producer" }
                    PathRenderPhase.HardEdgeBinaryColorCover -> {
                        val binaryDraw = pass.draw.binaryMaskedSourceOrNull()
                            ?: throw IllegalArgumentException("AA4 hard color covers require binary masked draws")
                        require(pass.depthStencil == null && pass.depthStencilAccess == null &&
                            pass.depthStencilLoadStore == null) {
                            "AA4 binary color covers must not attach depth-stencil"
                        }
                        val atomicGroup = requireNotNull(pass.atomicGroup) {
                            "AA4 binary color covers require an atomic group"
                        }
                        require(atomicGroup == canonicalGeneralPathAtomicGroup(binaryDraw.producer)) {
                            "AA4 binary color covers require the canonical hard-edge group"
                        }
                        val previous = passes.getOrNull(passIndex - 1) as? PlanPass.PathRenderPass
                        val producer: PlanPass.PathRenderPass
                        val clearIndex: Int
                        when (previous?.phase) {
                            PathRenderPhase.HardEdgeMaskProducer -> {
                                producer = previous
                                clearIndex = passIndex - 2
                                require(producer.draw is GeneralPathDraw &&
                                    producer.draw.coverage == CoveragePlan.FullOrScissor &&
                                    producer.draw.strategy == PathFillStrategy.DirectTriangle &&
                                    producer.depthStencil == null && producer.depthStencilAccess == null &&
                                    producer.depthStencilLoadStore == null &&
                                    producer.load == AttachmentLoadPlan.Load) {
                                    "AA4 direct hard mask producers require a typed single-sample direct mask pass"
                                }
                                coveredHardPassIndices += passIndex - 1
                            }
                            PathRenderPhase.HardEdgeMaskStencilCover -> {
                                val stencilProducer = passes.getOrNull(passIndex - 2) as? PlanPass.PathRenderPass
                                requireNotNull(stencilProducer) {
                                    "AA4 hard stencil covers require an adjacent producer"
                                }
                                require(stencilProducer.phase == PathRenderPhase.HardEdgeMaskStencilProducer) {
                                    "AA4 hard stencil covers require a stencil producer"
                                }
                                require(stencilProducer.draw === previous.draw && previous.draw === binaryDraw.producer) {
                                    "AA4 hard stencil mask passes must share one immutable producer draw"
                                }
                                require(stencilProducer.atomicGroup == atomicGroup && previous.atomicGroup == atomicGroup) {
                                    "AA4 hard stencil mask passes must share the binary cover group"
                                }
                                validateHardEdgeStencilPair(
                                    stencilProducer,
                                    passIndex - 2,
                                    previous,
                                    passIndex - 1,
                                    resourcesById,
                                    targetExtent,
                                )
                                producer = stencilProducer
                                clearIndex = passIndex - 3
                                coveredHardPassIndices += passIndex - 2
                                coveredHardPassIndices += passIndex - 1
                            }
                            else -> throw IllegalArgumentException("AA4 hard color covers require an adjacent mask producer")
                        }
                        val clear = passes.getOrNull(clearIndex) as? PlanPass.PathMaskClearPass
                            ?: throw IllegalArgumentException("AA4 hard color covers require a preceding mask clear")
                        require(clear.target == binaryDraw.mask && clear.atomicGroup == atomicGroup) {
                            "AA4 hard mask clear must use the binary cover mask and group"
                        }
                        require(producer.target == binaryDraw.mask && producer.draw === binaryDraw.producer &&
                            producer.atomicGroup == atomicGroup) {
                            "AA4 hard mask producer must feed the binary color cover"
                        }
                        validateHardEdgeMask(binaryDraw.mask, clearIndex, passIndex, resourcesById, targetExtent)
                        coveredMaskClearIndices += clearIndex
                    }
                    PathRenderPhase.HardEdgeMaskProducer,
                    PathRenderPhase.HardEdgeMaskStencilProducer,
                    PathRenderPhase.HardEdgeMaskStencilCover,
                    -> Unit
                    PathRenderPhase.SingleSampleDirectColor,
                    PathRenderPhase.SingleSampleStencilProducer,
                    PathRenderPhase.SingleSampleStencilColorCover,
                    -> throw IllegalArgumentException("AA4 paths may not use single-sample color phases")
                }
            }
            val allHardPassIndices = pathPasses.filter { (_, pass) ->
                pass.phase == PathRenderPhase.HardEdgeMaskProducer ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilProducer ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilCover
            }.map { (index, _) -> index }.toSet()
            require(coveredHardPassIndices == allHardPassIndices) {
                "Every AA4 hard mask pass must feed one ordered binary color cover"
            }
            val allMaskClearIndices = passes.indices.filter { passes[it] is PlanPass.PathMaskClearPass }.toSet()
            require(coveredMaskClearIndices == allMaskClearIndices) {
                "Every AA4 hard mask clear must feed one ordered binary color cover"
            }
        }

        private fun requireExplicitLogicalTarget(
            resources: List<PlanResource>,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
        ): PlanResource {
            val targets = resources.filter { it.role == PlanResourceRole.LogicalTarget }
            require(targets.size == 1) { "Explicit path graphs require one logical target" }
            val target = targets.single()
            require(target.kind == PlanResourceKind.Texture2D &&
                target.format == PlanTextureFormat.Color(colorFormat) &&
                target.copyExtent() == targetExtent && target.sampleCountI32 == 1 &&
                PlanResourceUsage.RenderAttachment in target.usages() &&
                PlanResourceUsage.CopySource in target.usages()) {
                "Explicit logical targets require one-sample render-attachment and copy-source usage"
            }
            return target
        }

        private fun requireExplicitTerminalReadback(
            passes: List<PlanPass>,
            target: PlanResourceId,
        ): PlanPass.ReadbackPass {
            val readbacks = passes.filterIsInstance<PlanPass.ReadbackPass>()
            require(readbacks.size == 1 && passes.last() === readbacks.single()) {
                "Explicit path graphs require one terminal readback"
            }
            return readbacks.single().also { readback ->
                require(readback.source == target) { "Explicit path readback must consume the logical target" }
            }
        }

        private fun requireLinearDependencies(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            label: String,
        ) {
            val expected = passes.zipWithNext().map { (before, after) ->
                PlanPassDependency(before.id, after.id)
            }.toSet()
            require(dependencies.toSet() == expected) { "$label require consecutive linear dependencies" }
        }

        private fun requireExplicitDrawData(
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            pathPasses: List<Pair<Int, PlanPass.PathRenderPass>>,
        ): PlanDrawDataResources {
            val vertex = requireSinglePathResource(resources, PlanResourceRole.VertexData)
            val index = requireSinglePathResource(resources, PlanResourceRole.IndexData)
            val uniform = requireSinglePathResource(resources, PlanResourceRole.UniformData)
            val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
            validatePathDrawDataShape(drawData, resourcesById)
            val lastPathPassIndex = pathPasses.last().first
            listOf(vertex, index, uniform).forEach { resource ->
                require(resource.lastPassIndexExclusive > lastPathPassIndex) {
                    "Explicit path draw data must remain alive through the consuming path pass"
                }
            }
            require(pathPasses.all { (_, pass) -> pass.drawDataResources == drawData }) {
                "Explicit path passes must share one vertex, index, and uniform triplet"
            }
            return drawData
        }

        private fun validateExplicitVisualDraws(
            colorPasses: List<Pair<Int, PlanPass.PathRenderPass>>,
            visualCommandCount: Int,
        ) {
            require(colorPasses.isNotEmpty()) { "Explicit path graphs require a color-producing path pass" }
            val draws = colorPasses.map { (_, pass) -> pass.draw }
            require(visualCommandCount == draws.map { it.commandIndex }.distinct().size) {
                "Explicit path visual command count must match unique color draws"
            }
            draws.zipWithNext().forEach { (before, after) ->
                require(before.commandIndex < after.commandIndex) {
                    "Explicit path visual commands must be strictly ascending"
                }
            }
        }

        private fun validateExplicitStencilPair(
            producer: PlanPass.PathRenderPass,
            producerIndex: Int,
            cover: PlanPass.PathRenderPass,
            coverIndex: Int,
            target: PlanResourceId,
            depthRole: PlanResourceRole,
            sample: SamplePlan,
            coverage: CoveragePlan,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
        ) {
            val producerPhase = when (sample) {
                SamplePlan.SingleSample -> PathRenderPhase.SingleSampleStencilProducer
                SamplePlan.Multisample4 -> PathRenderPhase.MultisampleStencilProducer
            }
            val coverPhase = when (sample) {
                SamplePlan.SingleSample -> PathRenderPhase.SingleSampleStencilColorCover
                SamplePlan.Multisample4 -> PathRenderPhase.MultisampleStencilColorCover
            }
            require(producer.phase == producerPhase && cover.phase == coverPhase) {
                "Explicit stencil pairs require matching producer and color-cover phases"
            }
            require(producer.target == target && cover.target == target &&
                producer.draw === cover.draw && producer.drawDataResources == cover.drawDataResources &&
                producer.depthStencil == cover.depthStencil && producer.atomicGroup == cover.atomicGroup) {
                "Explicit stencil producer and cover must share draw, data, target, depth, and group"
            }
            val draw = producer.draw.generalSourceOrNull()
                ?: throw IllegalArgumentException("Explicit stencil producers require general path draws")
            require(draw.coverage == coverage && draw.sample == sample &&
                draw.strategy == PathFillStrategy.StencilCover) {
                "Explicit stencil pairs require a typed stencil path draw"
            }
            val group = requireNotNull(producer.atomicGroup) {
                "Explicit stencil pairs require an atomic group"
            }
            require(group == canonicalGeneralPathAtomicGroup(draw)) {
                "Explicit stencil pairs require the canonical atomic group"
            }
            require(producer.resolveTarget == null &&
                producer.depthStencilAccess == PlanDepthStencilAccess.Write &&
                producer.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore &&
                cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset) {
                "Explicit stencil pairs require clear/write then read-write/reset stencil access"
            }
            val depth = requireNotNull(producer.depthStencil).let { resourcesById[it] }
                ?: throw IllegalArgumentException("Explicit stencil pairs require a depth-stencil resource")
            val sampleCount = when (sample) {
                SamplePlan.SingleSample -> 1
                SamplePlan.Multisample4 -> 4
            }
            require(depth.role == depthRole && depth.kind == PlanResourceKind.Texture2D &&
                depth.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depth.copyExtent() == targetExtent && depth.sampleCountI32 == sampleCount &&
                PlanResourceUsage.DepthStencilAttachment in depth.usages() &&
                depth.firstPassIndex <= producerIndex && depth.lastPassIndexExclusive > coverIndex) {
                "Explicit stencil pairs require a live matching D24S8 attachment"
            }
        }

        private fun validateMultisampleDepthStencil(
            pass: PlanPass.PathRenderPass,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
        ) {
            val depthStencil = requireNotNull(pass.depthStencil) { "AA4 stencil color passes require depth-stencil" }
            val resource = requireNotNull(resourcesById[depthStencil])
            require(resource.role == PlanResourceRole.DepthStencil &&
                resource.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                resource.copyExtent() == targetExtent && resource.sampleCountI32 == 4 &&
                PlanResourceUsage.DepthStencilAttachment in resource.usages()) {
                "AA4 stencil color passes require a four-sample D24S8 attachment"
            }
            require(pass.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                pass.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset) {
                "AA4 stencil color passes require typed stencil read-write reset access"
            }
        }

        private fun validateHardEdgeStencilPair(
            producer: PlanPass.PathRenderPass,
            producerIndex: Int,
            cover: PlanPass.PathRenderPass,
            coverIndex: Int,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
        ) {
            require(producer.target == cover.target && producer.depthStencil == cover.depthStencil) {
                "AA4 hard stencil mask pairs must share target and depth-stencil"
            }
            require(producer.draw is GeneralPathDraw && producer.draw.strategy == PathFillStrategy.StencilCover) {
                "AA4 hard stencil producer requires a stencil path draw"
            }
            require(producer.draw.coverage == CoveragePlan.FullOrScissor &&
                producer.draw.sample == SamplePlan.SingleSample) {
                "AA4 hard stencil producer requires a typed single-sample hard draw"
            }
            require(producer.load == AttachmentLoadPlan.Load && cover.load == AttachmentLoadPlan.Load) {
                "AA4 hard stencil mask passes must preserve the cleared mask"
            }
            require(producer.depthStencilAccess == PlanDepthStencilAccess.Write &&
                producer.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore &&
                cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset) {
                "AA4 hard stencil mask passes require typed stencil access"
            }
            val depthStencil = requireNotNull(resourcesById[requireNotNull(producer.depthStencil)])
            require(depthStencil.role == PlanResourceRole.PathHardEdgeDepthStencil &&
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depthStencil.copyExtent() == targetExtent && depthStencil.sampleCountI32 == 1 &&
                PlanResourceUsage.DepthStencilAttachment in depthStencil.usages()) {
                "AA4 hard stencil masks require a single-sample D24S8 attachment"
            }
            require(depthStencil.firstPassIndex == producerIndex && depthStencil.lastPassIndexExclusive == coverIndex + 1) {
                "AA4 hard stencil depth-stencil lifetime must be bounded by its atomic group"
            }
        }

        private fun validateHardEdgeMask(
            maskId: PlanResourceId,
            clearIndex: Int,
            binaryColorIndex: Int,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
        ) {
            val mask = requireNotNull(resourcesById[maskId])
            require(mask.role == PlanResourceRole.PathHardEdgeMask &&
                mask.format == PlanTextureFormat.CoverageMask &&
                mask.copyExtent() == targetExtent && mask.sampleCountI32 == 1 &&
                mask.usages() == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)) {
                "AA4 hard masks require the typed single-sample sampled coverage texture"
            }
            require(mask.firstPassIndex == clearIndex && mask.lastPassIndexExclusive == binaryColorIndex + 1) {
                "AA4 hard mask lifetime must extend from clear through consuming binary color draw"
            }
        }

        private fun validatePathDrawContracts(
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            resources: List<PlanResource>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            capabilities: PlanCapabilitySnapshot,
            visualCommandCount: Int,
        ) {
            val visualDraws = visualDraws(passes)
            if (visualDraws.none { it.unwrapClippedSource().let { source -> source is PathFillDraw || source is PathStrokeDraw } }) return

            require(passes.all {
                it is PlanPass.RenderPass ||
                    it is PlanPass.StencilProducer ||
                    it is PlanPass.StencilCover ||
                    it is PlanPass.ClipMaskInitialize ||
                    it is PlanPass.ClipMaskProducer ||
                    it is PlanPass.ClipMaskFold ||
                    it is PlanPass.ReadbackPass
            }) { "Path graphs may contain only W4c, clip-mask, and readback passes" }
            require(PlanOperationCapability.CopyUpload in capabilities.supportedOperations()) {
                "Path draws require copy upload support"
            }
            require(PlanOperationCapability.UniformBuffer in capabilities.supportedOperations()) {
                "Path draws require uniform buffer support"
            }
            val targetResource = requireSinglePathResource(resources, PlanResourceRole.LogicalTarget)
            val stagingResource = requireSinglePathResource(resources, PlanResourceRole.ReadbackStaging)
            val vertexResource = requireSinglePathResource(resources, PlanResourceRole.VertexData)
            val indexResource = requireSinglePathResource(resources, PlanResourceRole.IndexData)
            val uniformResource = requireSinglePathResource(resources, PlanResourceRole.UniformData)
            val usesStencil = passes.any {
                it is PlanPass.StencilProducer || it is PlanPass.StencilCover
            }
            val depthStencilResources = resources.filter { it.role == PlanResourceRole.DepthStencil }
            require(depthStencilResources.size == if (usesStencil) 1 else 0) {
                "Path graphs require a depth-stencil resource only for stencil pairs"
            }
            val inventory = buildList {
                add(targetResource)
                add(stagingResource)
                add(vertexResource)
                add(indexResource)
                add(uniformResource)
                depthStencilResources.singleOrNull()?.let(::add)
                addAll(resources.filter {
                    it.role in setOf(
                        PlanResourceRole.CoverageMaskAccumulator,
                        PlanResourceRole.CoverageMaskScratch,
                        PlanResourceRole.CoverageMaskMultisampleScratch,
                        PlanResourceRole.CoverageMaskDepthStencil,
                    )
                })
            }
            require(inventory.map { it.id }.distinct().size == inventory.size) {
                "Path draw resources must have distinct identities"
            }
            require(resources.map { it.id }.toSet() == inventory.map { it.id }.toSet()) {
                "Path graphs must declare only the path draw resource inventory"
            }
            val target = targetResource.id
            passes.forEach { pass ->
                val colorTarget = when (pass) {
                    is PlanPass.RenderPass -> pass.target
                    is PlanPass.StencilProducer -> pass.target
                    is PlanPass.StencilCover -> pass.target
                    else -> null
                }
                if (colorTarget != null) {
                    require(colorTarget == target) { "Path draws must use one color target" }
                }
            }
            val readbacks = passes.filterIsInstance<PlanPass.ReadbackPass>()
            require(readbacks.size == 1) { "Path draws require one readback" }
            val terminalReadback = readbacks.single()
            require(passes.last() === terminalReadback) { "Path draw readback must be terminal" }
            require(terminalReadback.source == target) { "Path draws must read back their color target" }
            require(terminalReadback.staging == stagingResource.id) {
                "Path readback must use the readback staging resource"
            }
            val expectedDependencies = passes.zipWithNext().map { (before, after) ->
                PlanPassDependency(before.id, after.id)
            }.toSet()
            require(dependencies.toSet() == expectedDependencies) {
                "Path draws require consecutive linear dependencies"
            }
            require(visualCommandCount == visualDraws.map { it.commandIndex }.distinct().size) {
                "Path visual command count must match unique draws"
            }
            val sharedDrawDataResources = PlanDrawDataResources(
                vertexResource.id,
                indexResource.id,
                uniformResource.id,
            )
            val terminalReadbackIndex = passes.lastIndex
            passes.forEach { pass ->
                when (pass) {
                    is PlanPass.RenderPass -> {
                        require(pass.draws().size == 1) {
                            "Path render passes require exactly one draw"
                        }
                        val draw = pass.draws().single().unwrapClippedSource()
                        require(draw is PathDraw && draw.strategy == PathFillStrategy.DirectTriangle) {
                            "Path render passes require one direct-triangle draw"
                        }
                        val drawDataResources = requireNotNull(pass.drawDataResources) {
                            "Path render passes require vertex, index, and uniform resources"
                        }
                        require(drawDataResources == sharedDrawDataResources) {
                            "Path passes must share one vertex, index, and uniform triplet"
                        }
                        validatePathDrawDataShape(drawDataResources, resourcesById)
                        validatePathDrawDataLifetime(
                            drawDataResources,
                            resourcesById,
                            terminalReadbackIndex,
                        )
                    }
                    is PlanPass.StencilProducer -> {
                        require(pass.drawDataResources == sharedDrawDataResources) {
                            "Path passes must share one vertex, index, and uniform triplet"
                        }
                        validatePathDrawDataShape(pass.drawDataResources, resourcesById)
                        validatePathDrawDataLifetime(
                            pass.drawDataResources,
                            resourcesById,
                            terminalReadbackIndex,
                        )
                    }
                    is PlanPass.StencilCover -> {
                        require(pass.drawDataResources == sharedDrawDataResources) {
                            "Path passes must share one vertex, index, and uniform triplet"
                        }
                        validatePathDrawDataShape(pass.drawDataResources, resourcesById)
                        validatePathDrawDataLifetime(
                            pass.drawDataResources,
                            resourcesById,
                            terminalReadbackIndex,
                        )
                    }
                    else -> Unit
                }
            }
        }

        private fun requireSinglePathResource(
            resources: List<PlanResource>,
            role: PlanResourceRole,
        ): PlanResource {
            val matches = resources.filter { it.role == role }
            require(matches.size == 1) { "Path graphs require one $role resource" }
            return matches.single()
        }

        private fun validatePathDrawDataShape(
            drawDataResources: PlanDrawDataResources,
            resourcesById: Map<PlanResourceId, PlanResource>,
        ) {
            listOf(
                Triple(drawDataResources.vertex, PlanResourceRole.VertexData, PlanResourceUsage.Vertex),
                Triple(drawDataResources.index, PlanResourceRole.IndexData, PlanResourceUsage.Index),
                Triple(drawDataResources.uniform, PlanResourceRole.UniformData, PlanResourceUsage.Uniform),
            ).forEach { (resourceId, requiredRole, requiredUsage) ->
                val resource = requireNotNull(resourcesById[resourceId])
                require(resource.role == requiredRole) { "Path draw data resource has the wrong role" }
                require(resource.kind == PlanResourceKind.Buffer) { "Path draw data resources must be buffers" }
                require(requiredUsage in resource.usages()) { "Path draw data resource has the wrong usage" }
                require(PlanResourceUsage.CopyDestination in resource.usages()) {
                    "Path draw data resources require copy destination usage"
                }
            }
        }

        private fun validatePathDrawDataLifetime(
            drawDataResources: PlanDrawDataResources,
            resourcesById: Map<PlanResourceId, PlanResource>,
            terminalReadbackIndex: Int,
        ) {
            listOf(drawDataResources.vertex, drawDataResources.index, drawDataResources.uniform).forEach { resourceId ->
                val resource = requireNotNull(resourcesById[resourceId])
                require(resource.lastPassIndexExclusive > terminalReadbackIndex) {
                    "Path draw data resources must remain alive through readback"
                }
            }
        }

        private fun validateVisualCommandOrder(passes: List<PlanPass>) {
            visualDraws(passes).zipWithNext().forEach { (before, after) ->
                require(before.commandIndex < after.commandIndex) { "Visual commands must be strictly ascending" }
            }
        }

        private fun visualDraws(passes: List<PlanPass>): List<PlanDraw> = buildList {
            passes.forEachIndexed { index, pass ->
                when (pass) {
                    is PlanPass.RenderPass -> addAll(pass.draws())
                    is PlanPass.StencilProducer -> add(pass.draw)
                    is PlanPass.StencilCover -> if (passes.getOrNull(index - 1) is PlanPass.StencilGeometryProducerV3) add(pass.draw)
                    is PlanPass.PathRenderPass -> when (pass.phase) {
                        PathRenderPhase.SingleSampleDirectColor,
                        PathRenderPhase.SingleSampleStencilColorCover,
                        PathRenderPhase.MultisampleDirectColor,
                        PathRenderPhase.MultisampleStencilColorCover,
                        PathRenderPhase.HardEdgeBinaryColorCover,
                        -> add(pass.draw)
                        PathRenderPhase.SingleSampleStencilProducer,
                        PathRenderPhase.MultisampleStencilProducer,
                        PathRenderPhase.HardEdgeMaskProducer,
                        PathRenderPhase.HardEdgeMaskStencilProducer,
                        PathRenderPhase.HardEdgeMaskStencilCover,
                        -> Unit
                    }
                    else -> Unit
                }
            }
        }

        private data class ColorAttachment(
            val target: PlanResourceId,
            val load: AttachmentLoadPlan,
            val store: AttachmentStorePlan,
            val sample: SamplePlan,
        )

        private fun validateReadback(
            pass: PlanPass.ReadbackPass,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
        ) {
            val source = requireNotNull(resourcesById[pass.source])
            require(source.kind == PlanResourceKind.Texture2D) { "Readback source must be a texture" }
            require(PlanResourceUsage.CopySource in source.usages()) { "Readback source must allow copies" }
            require(source.copyExtent() == targetExtent) { "Readback source extent must match the target" }

            val staging = requireNotNull(resourcesById[pass.staging])
            require(staging.kind == PlanResourceKind.Buffer) { "Readback staging must be a buffer" }
            require(PlanResourceUsage.CopyDestination in staging.usages()) { "Readback staging must allow copy destinations" }
            require(PlanResourceUsage.MapRead in staging.usages()) { "Readback staging must allow read mapping" }

            val minimumBytesPerRow = Math.multiplyExact(targetExtent.width.toLong(), LOGICAL_PIXEL_BYTES)
            require(pass.bytesPerRow >= minimumBytesPerRow) { "Readback row bytes are too small" }
            val expectedStagingBytes = try {
                Math.multiplyExact(pass.bytesPerRow, targetExtent.height.toLong())
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Readback staging size overflows", error)
            }
            require(staging.byteSize == expectedStagingBytes) { "Readback staging size does not match layout" }
        }

        private fun peak(resources: List<PlanResource>, passCount: Int): Long = (0 until passCount).maxOfOrNull { index ->
            resources.filter { it.firstPassIndex <= index && index < it.lastPassIndexExclusive }
                .fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }
        } ?: 0L

        private const val LOGICAL_PIXEL_BYTES: Long = 4L
    }

    /** Opaque proof whose digest never crosses the public API. */
    private class W4dCompilerWitness private constructor(digest: ByteArray) {
        private val digestSnapshot: ByteArray = digest.copyOf()

        fun matches(graph: RenderGraph): Boolean = try {
            MessageDigest.isEqual(digestSnapshot, canonicalW4dGraphDigest(graph))
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }

        companion object {
            fun issue(graph: RenderGraph): W4dCompilerWitness =
                W4dCompilerWitness(canonicalW4dGraphDigest(graph))
        }
    }

    /** Opaque proof for the W4d.2 general-path graph; its version is sealed into the digest. */
    private class W4dGeneralCompilerWitness private constructor(digest: ByteArray) {
        private val digestSnapshot: ByteArray = digest.copyOf()

        fun matches(graph: RenderGraph): Boolean = try {
            MessageDigest.isEqual(digestSnapshot, canonicalW4dGeneralGraphDigest(graph))
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }

        companion object {
            fun issue(graph: RenderGraph): W4dGeneralCompilerWitness =
                W4dGeneralCompilerWitness(canonicalW4dGeneralGraphDigest(graph))
        }
    }

    /** Opaque proof for the W4e clip graph, including its pooled mask inventory. */
    private class W4eCompilerWitness private constructor(
        private val nativePayload: W4eNativePayloadPlan,
        digest: ByteArray,
    ) {
        private val digestSnapshot: ByteArray = digest.copyOf()

        fun matches(graph: RenderGraph): Boolean = try {
            graph.w4eNativePayloadPlan === nativePayload &&
                nativePayload.matchesDeclaredResources(graph.storedResources) &&
                MessageDigest.isEqual(digestSnapshot, canonicalW4eGraphDigest(graph))
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }

        companion object {
            fun issue(graph: RenderGraph): W4eCompilerWitness {
                val nativePayload = requireNotNull(graph.w4eNativePayloadPlan) {
                    "W4e compiler witness requires the graph-native V/I/U payload"
                }
                return W4eCompilerWitness(nativePayload, canonicalW4eGraphDigest(graph))
            }
        }
    }
}
