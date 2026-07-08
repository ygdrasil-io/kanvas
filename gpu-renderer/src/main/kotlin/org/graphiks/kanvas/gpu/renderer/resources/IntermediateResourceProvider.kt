package org.graphiks.kanvas.gpu.renderer.resources

interface GPUIntermediateTextureMaterializationDescriptor {
    val label: String
    val purposeLabel: String
    val descriptorHash: String
    val sourceTargetLabel: String
    val boundsLabel: String
    val width: Int
    val height: Int
    val formatClass: String
    val usageLabels: List<String>
    val sampleCount: Int
    val generation: Long
    val lifetimeClass: String
    val ownerScope: String
    val byteEstimate: Long
}

data class GPUIntermediateTextureMaterializationRequest(
    val targetId: String,
    val descriptor: GPUIntermediateTextureMaterializationDescriptor,
    val deviceGeneration: Long,
    val actualResourceGeneration: Long,
    val requiredUsageLabels: Set<String>,
    val activeAttachmentSampled: Boolean,
) {
    init {
        require(targetId.isNotBlank()) { "GPUIntermediateTextureMaterializationRequest.targetId must not be blank" }
        require(deviceGeneration >= 0L) {
            "GPUIntermediateTextureMaterializationRequest.deviceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUIntermediateTextureMaterializationRequest.actualResourceGeneration must be non-negative"
        }
        require(requiredUsageLabels.none { it.isBlank() }) {
            "GPUIntermediateTextureMaterializationRequest.requiredUsageLabels must not contain blanks"
        }
    }
}

internal data class GPUIntermediateTextureLeaseCacheKey(
    val targetId: String,
    val descriptorHash: String,
    val boundsLabel: String,
    val formatClass: String,
    val usageLabels: List<String>,
    val sampleCount: Int,
    val generation: Long,
    val lifetimeClass: String,
    val ownerScope: String,
) {
    fun dumpToken(): String =
        "target=$targetId;descriptor=$descriptorHash;bounds=$boundsLabel;format=$formatClass;" +
            "usage=${usageLabels.joinToString("+")};sampleCount=$sampleCount;generation=$generation;" +
            "lifetime=$lifetimeClass;owner=$ownerScope"

    companion object {
        fun from(request: GPUIntermediateTextureMaterializationRequest): GPUIntermediateTextureLeaseCacheKey =
            GPUIntermediateTextureLeaseCacheKey(
                targetId = request.targetId,
                descriptorHash = request.descriptor.descriptorHash,
                boundsLabel = request.descriptor.boundsLabel,
                formatClass = request.descriptor.formatClass,
                usageLabels = request.descriptor.usageLabels.sorted(),
                sampleCount = request.descriptor.sampleCount,
                generation = request.actualResourceGeneration,
                lifetimeClass = request.descriptor.lifetimeClass,
                ownerScope = request.descriptor.ownerScope,
            )
    }
}
