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

internal class GPUValidatedIntermediateTextureMaterializationDescriptor private constructor(
    override val label: String,
    override val purposeLabel: String,
    override val descriptorHash: String,
    override val sourceTargetLabel: String,
    override val boundsLabel: String,
    override val width: Int,
    override val height: Int,
    override val formatClass: String,
    override val usageLabels: List<String>,
    override val sampleCount: Int,
    override val generation: Long,
    override val lifetimeClass: String,
    override val ownerScope: String,
    override val byteEstimate: Long,
) : GPUIntermediateTextureMaterializationDescriptor {
    companion object {
        fun from(descriptor: GPUIntermediateTextureMaterializationDescriptor): GPUValidatedIntermediateTextureMaterializationDescriptor {
            require(descriptor.label.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.label must not be blank"
            }
            require(descriptor.purposeLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.purposeLabel must not be blank"
            }
            require(descriptor.descriptorHash.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.descriptorHash must not be blank"
            }
            require(descriptor.sourceTargetLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.sourceTargetLabel must not be blank"
            }
            require(descriptor.boundsLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.boundsLabel must not be blank"
            }
            require(descriptor.width > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.width must be positive"
            }
            require(descriptor.height > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.height must be positive"
            }
            require(descriptor.formatClass.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.formatClass must not be blank"
            }
            require(descriptor.usageLabels.isNotEmpty()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels must not be empty"
            }
            require(descriptor.usageLabels.none { it.isBlank() }) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels must not contain blanks"
            }
            require(descriptor.sampleCount > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.sampleCount must be positive"
            }
            require(descriptor.generation >= 0L) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.generation must be non-negative"
            }
            require(descriptor.lifetimeClass.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.lifetimeClass must not be blank"
            }
            require(descriptor.ownerScope.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.ownerScope must not be blank"
            }
            require(descriptor.byteEstimate >= 0L) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.byteEstimate must be non-negative"
            }
            listOf(
                "GPUIntermediateTextureMaterializationRequest.descriptor.label" to descriptor.label,
                "GPUIntermediateTextureMaterializationRequest.descriptor.purposeLabel" to descriptor.purposeLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.descriptorHash" to descriptor.descriptorHash,
                "GPUIntermediateTextureMaterializationRequest.descriptor.sourceTargetLabel" to descriptor.sourceTargetLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.boundsLabel" to descriptor.boundsLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.formatClass" to descriptor.formatClass,
                "GPUIntermediateTextureMaterializationRequest.descriptor.lifetimeClass" to descriptor.lifetimeClass,
                "GPUIntermediateTextureMaterializationRequest.descriptor.ownerScope" to descriptor.ownerScope,
            ).forEach { (fieldName, value) ->
                requireResourceDumpSafe(fieldName, value)
            }
            descriptor.usageLabels.forEachIndexed { index, usageLabel ->
                requireResourceDumpSafe(
                    fieldName = "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels[$index]",
                    value = usageLabel,
                )
            }
            return GPUValidatedIntermediateTextureMaterializationDescriptor(
                label = descriptor.label,
                purposeLabel = descriptor.purposeLabel,
                descriptorHash = descriptor.descriptorHash,
                sourceTargetLabel = descriptor.sourceTargetLabel,
                boundsLabel = descriptor.boundsLabel,
                width = descriptor.width,
                height = descriptor.height,
                formatClass = descriptor.formatClass,
                usageLabels = descriptor.usageLabels.toList(),
                sampleCount = descriptor.sampleCount,
                generation = descriptor.generation,
                lifetimeClass = descriptor.lifetimeClass,
                ownerScope = descriptor.ownerScope,
                byteEstimate = descriptor.byteEstimate,
            )
        }
    }
}

data class GPUIntermediateTextureMaterializationRequest(
    val targetId: String,
    val descriptor: GPUIntermediateTextureMaterializationDescriptor,
    val deviceGeneration: Long,
    val actualResourceGeneration: Long,
    val requiredUsageLabels: Set<String>,
    val activeAttachmentSampled: Boolean,
) {
    internal val validatedDescriptor: GPUValidatedIntermediateTextureMaterializationDescriptor =
        GPUValidatedIntermediateTextureMaterializationDescriptor.from(descriptor)

    init {
        require(targetId.isNotBlank()) { "GPUIntermediateTextureMaterializationRequest.targetId must not be blank" }
        requireResourceDumpSafe("GPUIntermediateTextureMaterializationRequest.targetId", targetId)
        require(deviceGeneration >= 0L) {
            "GPUIntermediateTextureMaterializationRequest.deviceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUIntermediateTextureMaterializationRequest.actualResourceGeneration must be non-negative"
        }
        require(requiredUsageLabels.none { it.isBlank() }) {
            "GPUIntermediateTextureMaterializationRequest.requiredUsageLabels must not contain blanks"
        }
        requiredUsageLabels.forEachIndexed { index, usageLabel ->
            requireResourceDumpSafe(
                fieldName = "GPUIntermediateTextureMaterializationRequest.requiredUsageLabels[$index]",
                value = usageLabel,
            )
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
                descriptorHash = request.validatedDescriptor.descriptorHash,
                boundsLabel = request.validatedDescriptor.boundsLabel,
                formatClass = request.validatedDescriptor.formatClass,
                usageLabels = request.validatedDescriptor.usageLabels.sorted(),
                sampleCount = request.validatedDescriptor.sampleCount,
                generation = request.actualResourceGeneration,
                lifetimeClass = request.validatedDescriptor.lifetimeClass,
                ownerScope = request.validatedDescriptor.ownerScope,
            )
    }
}
