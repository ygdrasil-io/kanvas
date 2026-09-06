package org.graphiks.kanvas.gpu.plan

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
                        }
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
            validateStencilAtomicContracts(passes, dependencies, resources, resourcesById, capabilities, targetExtent)
            validateVisualCommandOrder(passes)
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
                resources, passes, dependencies, peakFrameLocalBytes)
        }

        private fun referencedResources(pass: PlanPass): List<PlanResourceId> = when (pass) {
            is PlanPass.RenderPass -> buildList {
                add(pass.target)
                pass.drawDataResources?.let { addAll(listOf(it.vertex, it.index, it.uniform)) }
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
                        pass.draws().filterIsInstance<PathFillDraw>().forEach { draw ->
                            require(draw.strategy == PathFillStrategy.DirectTriangle) {
                                "Stencil path fills require atomic stencil passes"
                            }
                        }
                        ColorAttachment(pass.target, pass.load, pass.store)
                    }
                    is PlanPass.StencilProducer -> ColorAttachment(pass.target, pass.load, pass.store)
                    is PlanPass.StencilCover -> ColorAttachment(pass.target, pass.load, pass.store)
                    else -> null
                }
            }
            colorPasses.forEachIndexed { index, pass ->
                validateColorTarget(pass.target, resourcesById, targetExtent, colorFormat)
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
        ) {
            val target = requireNotNull(resourcesById[targetId])
            require(target.role == PlanResourceRole.LogicalTarget) { "Render target must be the logical target" }
            require(target.kind == PlanResourceKind.Texture2D) { "Render target must be a texture" }
            require(target.format == PlanTextureFormat.Color(colorFormat)) {
                "Render target format must match the graph color format"
            }
            require(target.copyExtent() == targetExtent) { "Render target extent must match the graph target" }
            require(PlanResourceUsage.RenderAttachment in target.usages()) {
                "Render target must allow render attachment usage"
            }
        }

        private fun validatePassCapabilities(
            passes: List<PlanPass>,
            capabilities: PlanCapabilitySnapshot,
        ) {
            if (passes.any {
                    it is PlanPass.RenderPass || it is PlanPass.StencilProducer || it is PlanPass.StencilCover
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

            val depthStencil = depthStencilResources.single()
            require(depthStencil.kind == PlanResourceKind.Texture2D) { "Depth-stencil must be a texture" }
            require(
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            ) { "Stencil graphs require Depth24PlusStencil8" }
            require(depthStencil.copyExtent() == targetExtent) { "Depth-stencil extent must match the target" }
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
            listOf(
                Triple(producer.drawDataResources.vertex, PlanResourceRole.VertexData, PlanResourceUsage.Vertex),
                Triple(producer.drawDataResources.index, PlanResourceRole.IndexData, PlanResourceUsage.Index),
                Triple(producer.drawDataResources.uniform, PlanResourceRole.UniformData, PlanResourceUsage.Uniform),
            ).forEach { (resourceId, requiredRole, requiredUsage) ->
                val resource = requireNotNull(resourcesById[resourceId])
                require(resource.role == requiredRole) { "Stencil draw data resource has the wrong role" }
                require(requiredUsage in resource.usages()) { "Stencil draw data resource has the wrong usage" }
            }
        }

        private fun validateVisualCommandOrder(passes: List<PlanPass>) {
            val draws = buildList {
                passes.forEach { pass ->
                    when (pass) {
                        is PlanPass.RenderPass -> addAll(pass.draws())
                        is PlanPass.StencilProducer -> add(pass.draw)
                        else -> Unit
                    }
                }
            }
            draws.zipWithNext().forEach { (before, after) ->
                require(before.commandIndex < after.commandIndex) { "Visual commands must be strictly ascending" }
            }
        }

        private data class ColorAttachment(
            val target: PlanResourceId,
            val load: AttachmentLoadPlan,
            val store: AttachmentStorePlan,
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
}
