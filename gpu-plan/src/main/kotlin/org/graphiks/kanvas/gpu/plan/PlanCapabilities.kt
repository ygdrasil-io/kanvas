package org.graphiks.kanvas.gpu.plan

public enum class PlanLogicalColorFormat { RGBA8_UNORM_SRGB_LINEAR_PREMUL }

public class PlanCapabilitySnapshot private constructor(
    public val deviceGeneration: Long,
    public val maxTextureDimension2D: Int,
    public val maxBufferSizeBytes: Long,
    public val copyBytesPerRowAlignment: Int,
    supportedFormats: Set<PlanLogicalColorFormat>,
) {
    private val formats: Set<PlanLogicalColorFormat> = supportedFormats.toSet().let(::immutableSet)

    public fun supportedFormats(): Set<PlanLogicalColorFormat> = formats

    override fun equals(other: Any?): Boolean = other is PlanCapabilitySnapshot &&
        deviceGeneration == other.deviceGeneration &&
        maxTextureDimension2D == other.maxTextureDimension2D &&
        maxBufferSizeBytes == other.maxBufferSizeBytes &&
        copyBytesPerRowAlignment == other.copyBytesPerRowAlignment &&
        formats == other.formats

    override fun hashCode(): Int = listOf(
        deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes, copyBytesPerRowAlignment, formats,
    ).hashCode()

    public companion object {
        public fun of(
            deviceGeneration: Long,
            maxTextureDimension2D: Int,
            maxBufferSizeBytes: Long,
            copyBytesPerRowAlignment: Int,
            supportedFormats: Set<PlanLogicalColorFormat>,
        ): PlanCapabilitySnapshot {
            require(deviceGeneration >= 0) { "Device generation must be non-negative" }
            require(maxTextureDimension2D > 0) { "Maximum texture dimension must be positive" }
            require(maxBufferSizeBytes > 0) { "Maximum buffer size must be positive" }
            require(copyBytesPerRowAlignment > 0) { "Copy row alignment must be positive" }
            return PlanCapabilitySnapshot(deviceGeneration, maxTextureDimension2D, maxBufferSizeBytes,
                copyBytesPerRowAlignment, supportedFormats)
        }
    }
}

internal fun <T> immutableSet(values: Set<T>): Set<T> = java.util.Collections.unmodifiableSet(values.toSet())
