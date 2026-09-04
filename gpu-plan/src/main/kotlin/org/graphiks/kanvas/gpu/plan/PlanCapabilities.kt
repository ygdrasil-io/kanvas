package org.graphiks.kanvas.gpu.plan

public enum class PlanLogicalColorFormat { RGBA8_UNORM_SRGB_LINEAR_PREMUL }
public enum class PlanOperationCapability { RenderPass, CopyUpload, UniformBuffer, Readback }

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
) {
    private val formats: Set<PlanLogicalColorFormat> = supportedFormats.toSet().let(::immutableSet)
    private val operations: Set<PlanOperationCapability> = supportedOperations.toSet().let(::immutableSet)

    public fun supportedFormats(): Set<PlanLogicalColorFormat> = formats
    public fun supportedOperations(): Set<PlanOperationCapability> = operations

    override fun equals(other: Any?): Boolean = other is PlanCapabilitySnapshot &&
        deviceGeneration == other.deviceGeneration &&
        maxTextureDimension2D == other.maxTextureDimension2D &&
        maxBufferSizeBytes == other.maxBufferSizeBytes &&
        copyBytesPerRowAlignment == other.copyBytesPerRowAlignment &&
        formats == other.formats &&
        minUniformBufferOffsetAlignment == other.minUniformBufferOffsetAlignment &&
        maxDynamicUniformBuffersPerPipelineLayout == other.maxDynamicUniformBuffersPerPipelineLayout &&
        operations == other.operations &&
        bufferAllocationPolicy == other.bufferAllocationPolicy

    override fun hashCode(): Int = listOf(
        deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes, copyBytesPerRowAlignment, formats,
        minUniformBufferOffsetAlignment, maxDynamicUniformBuffersPerPipelineLayout, operations, bufferAllocationPolicy,
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
        ): PlanCapabilitySnapshot {
            require(deviceGeneration >= 0) { "Device generation must be non-negative" }
            require(maxTextureDimension2D > 0) { "Maximum texture dimension must be positive" }
            require(maxBufferSizeBytes > 0) { "Maximum buffer size must be positive" }
            require(copyBytesPerRowAlignment > 0) { "Copy row alignment must be positive" }
            require(minUniformBufferOffsetAlignment > 0) { "Minimum uniform alignment must be positive" }
            require(maxDynamicUniformBuffersPerPipelineLayout >= 0) { "Maximum dynamic uniform buffers must be non-negative" }
            return PlanCapabilitySnapshot(deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes,
                copyBytesPerRowAlignment, supportedFormats, minUniformBufferOffsetAlignment,
                maxDynamicUniformBuffersPerPipelineLayout, supportedOperations, bufferAllocationPolicy)
        }
    }
}

internal fun <T> immutableSet(values: Set<T>): Set<T> = java.util.Collections.unmodifiableSet(values.toSet())
