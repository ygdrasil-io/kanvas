package org.graphiks.kanvas.gpu.renderer.capabilities

/** Implementation identity for wgpu4k, Dawn, or future pure Kotlin facade backends. */
data class GPUImplementationIdentity(
    val facadeName: String,
    val implementationName: String,
    val adapterName: String,
    val deviceName: String,
    val vendorId: String? = null,
    val deviceId: String? = null,
)

/** Single behavior-affecting capability fact. */
data class GPUCapabilityFact(
    val name: String,
    val source: String,
    val value: String,
    val affectsValidity: Boolean,
    val evidenceLabel: String,
)

/** Shader or device feature required by a route. */
data class GPUFeatureRequirement(
    val featureName: String,
    val requiredValue: String,
    val reasonCode: String,
)

/** Adapter/device limit required by a route. */
data class GPULimitRequirement(
    val limitName: String,
    val requiredMinimum: Long,
    val observedValue: Long? = null,
    val unit: String,
    val affectsValidity: Boolean,
)

/** Adapter/device limits that affect backend route validity and resource planning. */
data class GPULimits(
    val maxTextureDimension2D: Long,
    val copyBytesPerRowAlignment: Long,
    val minUniformBufferOffsetAlignment: Long,
    val source: String = "device.limits",
) {
    init {
        require(maxTextureDimension2D > 0L) { "GPULimits.maxTextureDimension2D must be positive" }
        require(copyBytesPerRowAlignment > 0L) { "GPULimits.copyBytesPerRowAlignment must be positive" }
        require(minUniformBufferOffsetAlignment > 0L) {
            "GPULimits.minUniformBufferOffsetAlignment must be positive"
        }
        require(source.isNotBlank()) { "GPULimits.source must not be blank" }
    }

    fun capabilityFacts(evidenceLabel: String): List<GPUCapabilityFact> {
        require(evidenceLabel.isNotBlank()) { "evidenceLabel must not be blank" }
        return listOf(
            GPUCapabilityFact(
                name = "maxTextureDimension2D",
                source = source,
                value = maxTextureDimension2D.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
            GPUCapabilityFact(
                name = "copyBytesPerRowAlignment",
                source = source,
                value = copyBytesPerRowAlignment.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
            GPUCapabilityFact(
                name = "minUniformBufferOffsetAlignment",
                source = source,
                value = minUniformBufferOffsetAlignment.toString(),
                affectsValidity = true,
                evidenceLabel = evidenceLabel,
            ),
        )
    }

    companion object {
        fun conservative(
            maxTextureDimension2D: Long,
            copyBytesPerRowAlignment: Long,
            minUniformBufferOffsetAlignment: Long,
        ): GPULimits =
            GPULimits(
                maxTextureDimension2D = maxTextureDimension2D,
                copyBytesPerRowAlignment = copyBytesPerRowAlignment,
                minUniformBufferOffsetAlignment = minUniformBufferOffsetAlignment,
                source = "runtime.conservative",
            )
    }
}

/** Capability snapshot for the selected GPU facade implementation. */
data class GPUCapabilities(
    val implementation: GPUImplementationIdentity,
    val facts: List<GPUCapabilityFact>,
    val knownUnsupportedFacts: List<GPUCapabilityFact> = emptyList(),
    val snapshotId: String,
    val limits: GPULimits? = null,
)

/** Diagnostic emitted when capability facts block a route. */
data class GPUCapabilityDiagnostic(
    val code: String,
    val severity: String,
    val requirementName: String,
    val required: String,
    val observed: String? = null,
    val isTerminal: Boolean,
)
