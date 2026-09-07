package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
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
) {
    private val storedTargetExtent: SizeI32 = targetExtent.copy()
    public val targetExtent: SizeI32
        get() = storedTargetExtent.copy()
    private val storedResources = immutableList(resources)
    private val storedPasses = immutableList(passes)
    private val storedDependencies = immutableList(dependencies)

    public fun resources(): List<PlanResource> = storedResources
    public fun passes(): List<PlanPass> = storedPasses
    public fun dependencies(): List<PlanPassDependency> = storedDependencies

    /** Verifies that this exact immutable graph snapshot was issued by the W4d compiler. */
    public fun verifyW4dCompilerWitness(): Boolean =
        w4dCompilerWitness?.matches(this) == true

    public companion object {
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
        ): RenderGraph {
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
            validateColorPasses(passes, resourcesById, targetExtent, colorFormat)
            val usesExplicitAa4PathPasses = passes.any {
                it is PlanPass.PathMaskClearPass || it is PlanPass.PathRenderPass
            }
            if (usesExplicitAa4PathPasses) {
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
            } else {
                validateStencilAtomicContracts(passes, dependencies, resources, resourcesById, capabilities, targetExtent)
            }
            validateVisualCommandOrder(passes)
            validatePathDrawContracts(
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
                resources, passes, dependencies, peakFrameLocalBytes, null)
        }

        /** Trust-boundary factory available only to the W4d compiler after public validation. */
        @JvmSynthetic
        internal fun issueW4dCompilerWitness(graph: RenderGraph): RenderGraph {
            require(graph.capabilityId == W4dPathStrokePlanCompiler.CAPABILITY_ID) {
                "Only a W4d graph may receive a W4d compiler witness"
            }
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
            )
        }

        private fun referencedResources(pass: PlanPass): List<PlanResourceId> = when (pass) {
            is PlanPass.RenderPass -> buildList {
                add(pass.target)
                pass.drawDataResources?.let { addAll(listOf(it.vertex, it.index, it.uniform)) }
            }
            is PlanPass.PathMaskClearPass -> listOf(pass.target)
            is PlanPass.PathRenderPass -> buildList {
                add(pass.target)
                add(pass.drawDataResources.vertex)
                add(pass.drawDataResources.index)
                add(pass.drawDataResources.uniform)
                pass.depthStencil?.let(::add)
                pass.resolveTarget?.let(::add)
                (pass.draw as? BinaryMaskedPathDraw)?.let { add(it.mask) }
            }
            is PlanPass.StencilProducer -> listOf(
                pass.target,
                pass.depthStencil,
                pass.drawDataResources.vertex,
                pass.drawDataResources.index,
                pass.drawDataResources.uniform,
            )
            is PlanPass.StencilCover -> listOf(
                pass.target,
                pass.depthStencil,
                pass.drawDataResources.vertex,
                pass.drawDataResources.index,
                pass.drawDataResources.uniform,
            )
            is PlanPass.TextureCopy -> listOf(pass.source, pass.destination)
            is PlanPass.FilterPass -> pass.inputs() + pass.output
            is PlanPass.ResolvePass -> listOf(pass.source, pass.destination)
            is PlanPass.ReadbackPass -> listOf(pass.source, pass.staging)
        }

        private fun validateColorPasses(
            passes: List<PlanPass>,
            resourcesById: Map<PlanResourceId, PlanResource>,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
        ) {
            val colorPasses = passes.mapNotNull { pass ->
                when (pass) {
                    is PlanPass.RenderPass -> {
                        require(pass.draws().all { it.sample == SamplePlan.SingleSample }) {
                            "Legacy render passes require single-sample draws"
                        }
                        require(pass.draws().none { it is PathRenderDraw }) {
                            "General and binary masked path draws require explicit path render passes"
                        }
                        pass.draws().filterIsInstance<PathDraw>().forEach { draw ->
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
                        it is PlanPass.StencilCover
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
                )
            }) { "Single-sample explicit paths may declare only their direct resource inventory" }
            val referencedResourceIds = passes.flatMap(::referencedResources).toSet()
            require(resources.all { it.id in referencedResourceIds }) {
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
                        require(pass.draw is GeneralPathDraw &&
                            pass.draw.coverage == CoveragePlan.FullOrScissor &&
                            pass.draw.strategy == PathFillStrategy.DirectTriangle &&
                            pass.target == target.id && pass.atomicGroup == null && pass.depthStencil == null &&
                            pass.depthStencilAccess == null && pass.depthStencilLoadStore == null) {
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
            require(depthResources.size == if (usesStencil) 1 else 0) {
                "Single-sample explicit paths require one D24S8 resource exactly when using stencil"
            }
            if (usesStencil) {
                val depth = depthResources.single()
                require(depth.firstPassIndex <= stencilPairs.first().first &&
                    depth.lastPassIndexExclusive > stencilPairs.last().second) {
                    "Single-sample D24S8 lifetime must cover its stencil pairs"
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
            val usesStencil = pathPasses.any { (_, pass) ->
                pass.phase == PathRenderPhase.MultisampleStencilProducer ||
                    pass.phase == PathRenderPhase.MultisampleStencilColorCover ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilProducer ||
                    pass.phase == PathRenderPhase.HardEdgeMaskStencilCover
            }
            if (usesStencil) {
                require(PlanOperationCapability.DepthStencilAttachment in capabilities.supportedOperations()) {
                    "AA4 stencil paths require depth-stencil attachment support"
                }
                require(PlanOperationCapability.StencilCover in capabilities.supportedOperations()) {
                    "AA4 stencil paths require stencil cover support"
                }
            }
            val explicitRoles = setOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.MultisampleColorTarget,
                PlanResourceRole.PathHardEdgeMask,
                PlanResourceRole.PathHardEdgeDepthStencil,
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
            require(resources.all { it.id in referencedResourceIds }) {
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
                    PathRenderPhase.MultisampleDirectColor -> require(pass.draw is GeneralPathDraw &&
                        pass.draw.coverage == CoveragePlan.StencilAA4 &&
                        pass.draw.strategy == PathFillStrategy.DirectTriangle &&
                        pass.target == multisampleTarget.id && pass.atomicGroup == null &&
                        pass.depthStencil == null && pass.depthStencilAccess == null &&
                        pass.depthStencilLoadStore == null) {
                        "AA4 direct color passes require a four-sample direct path draw"
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
                        val binaryDraw = pass.draw as? BinaryMaskedPathDraw
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
            val draw = producer.draw as? GeneralPathDraw
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
            if (visualDraws.none { it is PathDraw }) return

            require(passes.all {
                it is PlanPass.RenderPass ||
                    it is PlanPass.StencilProducer ||
                    it is PlanPass.StencilCover ||
                    it is PlanPass.ReadbackPass
            }) { "Path graphs may contain only W4c passes" }
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
                        val draw = pass.draws().single()
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
            passes.forEach { pass ->
                when (pass) {
                    is PlanPass.RenderPass -> addAll(pass.draws())
                    is PlanPass.StencilProducer -> add(pass.draw)
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
}
