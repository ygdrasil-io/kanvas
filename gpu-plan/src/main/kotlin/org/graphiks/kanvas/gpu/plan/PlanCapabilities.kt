package org.graphiks.kanvas.gpu.plan

public enum class PlanLogicalColorFormat(
    public val clampsNormalizedColorWrites: Boolean,
) {
    RGBA8_UNORM_SRGB_LINEAR_PREMUL(true),
}
public enum class PlanDepthStencilFormat { Depth24PlusStencil8 }
public enum class PlanOperationCapability {
    RenderPass,
    CopyUpload,
    UniformBuffer,
    Readback,
    DepthStencilAttachment,
    StencilCover,
    StorageBuffer,
}

/** Exact texture format, sample count, and usage envelope supported by a planning device. */
public class PlanTextureSampleSupport private constructor(
    public val format: PlanTextureFormat,
    public val sampleCountI32: Int,
    usages: Set<PlanResourceUsage>,
) {
    private val storedUsages: Set<PlanResourceUsage> = immutableSet(usages)

    public fun usages(): Set<PlanResourceUsage> = storedUsages

    override fun equals(other: Any?): Boolean = other is PlanTextureSampleSupport &&
        format == other.format &&
        sampleCountI32 == other.sampleCountI32 &&
        storedUsages == other.storedUsages

    override fun hashCode(): Int = listOf(format, sampleCountI32, storedUsages).hashCode()

    public companion object {
        public fun of(
            format: PlanTextureFormat,
            sampleCountI32: Int,
            usages: Set<PlanResourceUsage>,
        ): PlanTextureSampleSupport {
            require(sampleCountI32 == 1 || sampleCountI32 == 4) {
                "Texture sample support must be single-sample or four-sample"
            }
            require(usages.isNotEmpty()) { "Texture sample support usages must not be empty" }
            return PlanTextureSampleSupport(format, sampleCountI32, usages)
        }
    }
}

/** Exact four-sample-to-single-sample resolve support for one texture format. */
public class PlanTextureResolveSupport private constructor(
    public val format: PlanTextureFormat,
    public val sourceSampleCountI32: Int,
    public val destinationSampleCountI32: Int,
) {
    override fun equals(other: Any?): Boolean = other is PlanTextureResolveSupport &&
        format == other.format &&
        sourceSampleCountI32 == other.sourceSampleCountI32 &&
        destinationSampleCountI32 == other.destinationSampleCountI32

    override fun hashCode(): Int = listOf(format, sourceSampleCountI32, destinationSampleCountI32).hashCode()

    public companion object {
        public fun of(
            format: PlanTextureFormat,
            sourceSampleCountI32: Int,
            destinationSampleCountI32: Int,
        ): PlanTextureResolveSupport {
            require(format is PlanTextureFormat.Color || format === PlanTextureFormat.CoverageMask) {
                "Only color and coverage-mask textures may resolve"
            }
            require(sourceSampleCountI32 == 4 && destinationSampleCountI32 == 1) {
                "Texture resolve support must be four-sample to single-sample"
            }
            return PlanTextureResolveSupport(format, sourceSampleCountI32, destinationSampleCountI32)
        }
    }
}

public class PlanCapabilitySnapshot private constructor(
    public val deviceGeneration: Long,
    public val maxTextureDimension2D: Int,
    public val maxBufferSizeBytes: Long,
    public val copyBytesPerRowAlignment: Int,
    supportedFormats: Set<PlanLogicalColorFormat>,
    public val minUniformBufferOffsetAlignment: Int,
    public val maxDynamicUniformBuffersPerPipelineLayout: Int,
    supportedOperations: Set<PlanOperationCapability>,
    public val bufferAllocationPolicy: PlanBufferAllocationPolicy,
    supportedDepthStencilFormats: Set<PlanDepthStencilFormat>,
    supportedTextureSampleSupports: Set<PlanTextureSampleSupport>,
    supportedTextureResolveSupports: Set<PlanTextureResolveSupport>,
    public val maxUniformBufferBindingSizeBytesI64: Long?,
    public val maxStorageBufferBindingSizeBytesI64: Long?,
    public val maxStorageBuffersPerShaderStageI32: Int?,
    public val maxUniformBuffersPerShaderStageI32: Int?,
    public val maxSampledTexturesPerShaderStageI32: Int?,
    public val maxSamplersPerShaderStageI32: Int?,
    public val maxBindingsPerBindGroupI32: Int?,
    public val maxBindGroupsI32: Int?,
) {
    private val formats: Set<PlanLogicalColorFormat> = supportedFormats.toSet().let(::immutableSet)
    private val operations: Set<PlanOperationCapability> = supportedOperations.toSet().let(::immutableSet)
    private val depthStencilFormats: Set<PlanDepthStencilFormat> =
        supportedDepthStencilFormats.toSet().let(::immutableSet)
    private val textureSampleSupports: Set<PlanTextureSampleSupport> =
        supportedTextureSampleSupports.toSet().let(::immutableSet)
    private val textureResolveSupports: Set<PlanTextureResolveSupport> =
        supportedTextureResolveSupports.toSet().let(::immutableSet)

    public fun supportedFormats(): Set<PlanLogicalColorFormat> = formats
    public fun supportedOperations(): Set<PlanOperationCapability> = operations
    public fun supportedDepthStencilFormats(): Set<PlanDepthStencilFormat> = depthStencilFormats
    public fun supportedTextureSampleSupports(): Set<PlanTextureSampleSupport> = textureSampleSupports
    public fun supportedTextureResolveSupports(): Set<PlanTextureResolveSupport> = textureResolveSupports

    public fun supportsTexture(
        format: PlanTextureFormat,
        sampleCountI32: Int,
        usages: Set<PlanResourceUsage>,
    ): Boolean = textureSampleSupports.any { support ->
        support.format == format &&
            support.sampleCountI32 == sampleCountI32 &&
            support.usages().containsAll(usages)
    }

    public fun supportsResolve(
        format: PlanTextureFormat,
        sourceSampleCountI32: Int,
        destinationSampleCountI32: Int,
    ): Boolean = (format is PlanTextureFormat.Color || format === PlanTextureFormat.CoverageMask) &&
        sourceSampleCountI32 == 4 &&
        destinationSampleCountI32 == 1 &&
        textureResolveSupports.any { support ->
            support.format == format &&
                support.sourceSampleCountI32 == sourceSampleCountI32 &&
                support.destinationSampleCountI32 == destinationSampleCountI32
        }

    override fun equals(other: Any?): Boolean = other is PlanCapabilitySnapshot &&
        deviceGeneration == other.deviceGeneration &&
        maxTextureDimension2D == other.maxTextureDimension2D &&
        maxBufferSizeBytes == other.maxBufferSizeBytes &&
        copyBytesPerRowAlignment == other.copyBytesPerRowAlignment &&
        formats == other.formats &&
        minUniformBufferOffsetAlignment == other.minUniformBufferOffsetAlignment &&
        maxDynamicUniformBuffersPerPipelineLayout == other.maxDynamicUniformBuffersPerPipelineLayout &&
        operations == other.operations &&
        bufferAllocationPolicy == other.bufferAllocationPolicy &&
        depthStencilFormats == other.depthStencilFormats &&
        textureSampleSupports == other.textureSampleSupports &&
        textureResolveSupports == other.textureResolveSupports &&
        maxUniformBufferBindingSizeBytesI64 == other.maxUniformBufferBindingSizeBytesI64 &&
        maxStorageBufferBindingSizeBytesI64 == other.maxStorageBufferBindingSizeBytesI64 &&
        maxStorageBuffersPerShaderStageI32 == other.maxStorageBuffersPerShaderStageI32 &&
        maxUniformBuffersPerShaderStageI32 == other.maxUniformBuffersPerShaderStageI32 &&
        maxSampledTexturesPerShaderStageI32 == other.maxSampledTexturesPerShaderStageI32 &&
        maxSamplersPerShaderStageI32 == other.maxSamplersPerShaderStageI32 &&
        maxBindingsPerBindGroupI32 == other.maxBindingsPerBindGroupI32 &&
        maxBindGroupsI32 == other.maxBindGroupsI32

    override fun hashCode(): Int = listOf(
        deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes, copyBytesPerRowAlignment, formats,
        minUniformBufferOffsetAlignment, maxDynamicUniformBuffersPerPipelineLayout, operations, bufferAllocationPolicy,
        depthStencilFormats, textureSampleSupports, textureResolveSupports,
        maxUniformBufferBindingSizeBytesI64, maxStorageBufferBindingSizeBytesI64,
        maxStorageBuffersPerShaderStageI32, maxUniformBuffersPerShaderStageI32,
        maxSampledTexturesPerShaderStageI32, maxSamplersPerShaderStageI32,
        maxBindingsPerBindGroupI32, maxBindGroupsI32,
    ).hashCode()

    public companion object {
        public fun of(
            deviceGeneration: Long,
            maxTextureDimension2D: Int,
            maxBufferSizeBytes: Long,
            copyBytesPerRowAlignment: Int,
            supportedFormats: Set<PlanLogicalColorFormat>,
            minUniformBufferOffsetAlignment: Int,
            maxDynamicUniformBuffersPerPipelineLayout: Int,
            supportedOperations: Set<PlanOperationCapability>,
            bufferAllocationPolicy: PlanBufferAllocationPolicy,
            supportedDepthStencilFormats: Set<PlanDepthStencilFormat> = emptySet(),
            supportedTextureSampleSupports: Set<PlanTextureSampleSupport> = defaultTextureSampleSupports(
                supportedFormats,
                supportedDepthStencilFormats,
            ),
            supportedTextureResolveSupports: Set<PlanTextureResolveSupport> = emptySet(),
            maxUniformBufferBindingSizeBytesI64: Long? = null,
            maxStorageBufferBindingSizeBytesI64: Long? = null,
            maxStorageBuffersPerShaderStageI32: Int? = null,
            maxUniformBuffersPerShaderStageI32: Int? = null,
            maxSampledTexturesPerShaderStageI32: Int? = null,
            maxSamplersPerShaderStageI32: Int? = null,
            maxBindingsPerBindGroupI32: Int? = null,
            maxBindGroupsI32: Int? = null,
        ): PlanCapabilitySnapshot {
            require(deviceGeneration >= 0) { "Device generation must be non-negative" }
            require(maxTextureDimension2D > 0) { "Maximum texture dimension must be positive" }
            require(maxBufferSizeBytes > 0) { "Maximum buffer size must be positive" }
            require(copyBytesPerRowAlignment > 0) { "Copy row alignment must be positive" }
            require(minUniformBufferOffsetAlignment > 0) { "Minimum uniform alignment must be positive" }
            require(maxDynamicUniformBuffersPerPipelineLayout >= 0) { "Maximum dynamic uniform buffers must be non-negative" }
            supportedTextureSampleSupports.forEach { support ->
                when (val format = support.format) {
                    is PlanTextureFormat.ImageV1 -> require(support.sampleCountI32 == 1 &&
                        support.usages() == setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination))
                    is PlanTextureFormat.Color -> require(format.value in supportedFormats) {
                        "Texture sample support declares an unsupported color format"
                    }
                    is PlanTextureFormat.DepthStencil -> require(format.value in supportedDepthStencilFormats) {
                        "Texture sample support declares an unsupported depth-stencil format"
                    }
                    PlanTextureFormat.CoverageMask -> Unit
                }
            }
            supportedTextureResolveSupports.forEach { support ->
                when (val format = support.format) {
                    is PlanTextureFormat.ImageV1 -> error("Decoded image resolve support is impossible")
                    is PlanTextureFormat.Color -> require(format.value in supportedFormats) {
                        "Texture resolve support declares an unsupported color format"
                    }
                    PlanTextureFormat.CoverageMask -> require(PlanTextureFormat.CoverageMask.value == PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR) {
                        "Texture resolve support declares an unsupported coverage-mask format"
                    }
                    is PlanTextureFormat.DepthStencil -> error("Depth-stencil resolve support is impossible")
                }
            }
            return PlanCapabilitySnapshot(deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes,
                copyBytesPerRowAlignment, supportedFormats, minUniformBufferOffsetAlignment,
                maxDynamicUniformBuffersPerPipelineLayout, supportedOperations, bufferAllocationPolicy,
                supportedDepthStencilFormats, supportedTextureSampleSupports, supportedTextureResolveSupports,
                maxUniformBufferBindingSizeBytesI64, maxStorageBufferBindingSizeBytesI64,
                maxStorageBuffersPerShaderStageI32, maxUniformBuffersPerShaderStageI32,
                maxSampledTexturesPerShaderStageI32, maxSamplersPerShaderStageI32,
                maxBindingsPerBindGroupI32, maxBindGroupsI32)
        }

        private fun defaultTextureSampleSupports(
            supportedFormats: Set<PlanLogicalColorFormat>,
            supportedDepthStencilFormats: Set<PlanDepthStencilFormat>,
        ): Set<PlanTextureSampleSupport> = buildSet {
            supportedFormats.forEach { format ->
                add(
                    PlanTextureSampleSupport.of(
                        PlanTextureFormat.Color(format),
                        1,
                        setOf(
                            PlanResourceUsage.RenderAttachment,
                            PlanResourceUsage.CopySource,
                            PlanResourceUsage.CopyDestination,
                            PlanResourceUsage.Sampled,
                        ),
                    ),
                )
            }
            supportedDepthStencilFormats.forEach { format ->
                add(
                    PlanTextureSampleSupport.of(
                        PlanTextureFormat.DepthStencil(format),
                        1,
                        setOf(PlanResourceUsage.DepthStencilAttachment),
                    ),
                )
            }
        }
    }
}

internal fun <T> immutableSet(values: Set<T>): Set<T> = java.util.Collections.unmodifiableSet(values.toSet())
