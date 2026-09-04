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
                        require(resource.format in capabilities.supportedFormats()) { "Texture format is unsupported" }
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
            passes.filterIsInstance<PlanPass.RenderPass>().forEach { pass ->
                validateRenderPass(pass, resourcesById)
            }
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
            is PlanPass.TextureCopy -> listOf(pass.source, pass.destination)
            is PlanPass.FilterPass -> pass.inputs() + pass.output
            is PlanPass.ResolvePass -> listOf(pass.source, pass.destination)
            is PlanPass.ReadbackPass -> listOf(pass.source, pass.staging)
        }

        private fun validateRenderPass(
            pass: PlanPass.RenderPass,
            resourcesById: Map<PlanResourceId, PlanResource>,
        ) {
            val target = requireNotNull(resourcesById[pass.target])
            require(target.kind == PlanResourceKind.Texture2D) { "Render target must be a texture" }
            require(PlanResourceUsage.RenderAttachment in target.usages()) {
                "Render target must allow render attachment usage"
            }
        }

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
