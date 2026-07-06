package org.graphiks.kanvas.gpu.renderer.capabilities

/** Implementation identity for native or future pure Kotlin GPU facade backends. */
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

    /** Converts these limits to deterministic capability facts for diagnostics and evidence dumps. */
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
        /** Builds a limits snapshot from known conservative runtime assumptions. */
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
    val supportedTextureFormats: Set<String> = emptySet(),
    val supportedTextureUsageLabels: Set<String> = emptySet(),
    val featureLabels: Set<String> = emptySet(),
) {
    init {
        require(snapshotId.isNotBlank()) { "GPUCapabilities.snapshotId must not be blank" }
        require(supportedTextureFormats.none { it.isBlank() }) {
            "GPUCapabilities.supportedTextureFormats must not contain blank labels"
        }
        require(supportedTextureUsageLabels.none { it.isBlank() }) {
            "GPUCapabilities.supportedTextureUsageLabels must not contain blank labels"
        }
        require(featureLabels.none { it.isBlank() }) {
            "GPUCapabilities.featureLabels must not contain blank labels"
        }
    }
}

/** Validates a texture allocation request against known format, usage, and size capabilities. */
fun GPUCapabilities.validateTextureRequest(
    format: String,
    width: Int,
    height: Int,
    usageLabels: Set<String>,
): GPUCapabilityDiagnostic? {
    require(format.isNotBlank()) { "format must not be blank" }
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(usageLabels.none { it.isBlank() }) { "usageLabels must not contain blank labels" }

    if (supportedTextureFormats.isNotEmpty() && format !in supportedTextureFormats) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_format",
            severity = "error",
            requirementName = "texture.format",
            required = format,
            observed = supportedTextureFormats.sorted().joinToString(","),
            isTerminal = true,
        )
    }

    val missingUsageLabels = usageLabels.subtract(supportedTextureUsageLabels)
    if (supportedTextureUsageLabels.isNotEmpty() && missingUsageLabels.isNotEmpty()) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_usage",
            severity = "error",
            requirementName = "texture.usage",
            required = missingUsageLabels.sorted().joinToString(","),
            observed = supportedTextureUsageLabels.sorted().joinToString(","),
            isTerminal = true,
        )
    }

    val maxTextureDimension2D = limits?.maxTextureDimension2D
    if (maxTextureDimension2D != null && (width.toLong() > maxTextureDimension2D || height.toLong() > maxTextureDimension2D)) {
        return GPUCapabilityDiagnostic(
            code = "unsupported.capability.texture_size",
            severity = "error",
            requirementName = "texture.maxTextureDimension2D",
            required = maxOf(width, height).toString(),
            observed = maxTextureDimension2D.toString(),
            isTerminal = true,
        )
    }

    return null
}

/** Validates a dynamic uniform-buffer alignment request against known device limits. */
fun GPUCapabilities.validateUniformAlignment(alignmentBytes: Long): GPUCapabilityDiagnostic? {
    require(alignmentBytes > 0L) { "alignmentBytes must be positive" }

    val required = limits?.minUniformBufferOffsetAlignment ?: return null
    if (alignmentBytes >= required && alignmentBytes % required == 0L) {
        return null
    }

    return GPUCapabilityDiagnostic(
        code = "unsupported.capability.uniform_alignment",
        severity = "error",
        requirementName = "limits.minUniformBufferOffsetAlignment",
        required = required.toString(),
        observed = alignmentBytes.toString(),
        isTerminal = true,
    )
}

/** Validates that a named optional GPU feature is present when the snapshot has feature evidence. */
fun GPUCapabilities.validateFeature(featureLabel: String): GPUCapabilityDiagnostic? {
    require(featureLabel.isNotBlank()) { "featureLabel must not be blank" }

    if (featureLabels.isEmpty() || featureLabel in featureLabels) {
        return null
    }

    return GPUCapabilityDiagnostic(
        code = "unsupported.capability.feature",
        severity = "error",
        requirementName = "feature",
        required = featureLabel,
        observed = featureLabels.sorted().joinToString(","),
        isTerminal = true,
    )
}

/** Diagnostic emitted when capability facts block a route. */
data class GPUCapabilityDiagnostic(
    val code: String,
    val severity: String,
    val requirementName: String,
    val required: String,
    val observed: String? = null,
    val isTerminal: Boolean,
)
