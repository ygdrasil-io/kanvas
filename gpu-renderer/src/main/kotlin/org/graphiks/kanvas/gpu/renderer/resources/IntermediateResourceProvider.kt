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
            val label = descriptor.label
            val purposeLabel = descriptor.purposeLabel
            val descriptorHash = descriptor.descriptorHash
            val sourceTargetLabel = descriptor.sourceTargetLabel
            val boundsLabel = descriptor.boundsLabel
            val width = descriptor.width
            val height = descriptor.height
            val formatClass = descriptor.formatClass
            val usageLabels = descriptor.usageLabels.toList()
            val sampleCount = descriptor.sampleCount
            val generation = descriptor.generation
            val lifetimeClass = descriptor.lifetimeClass
            val ownerScope = descriptor.ownerScope
            val byteEstimate = descriptor.byteEstimate

            require(label.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.label must not be blank"
            }
            require(purposeLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.purposeLabel must not be blank"
            }
            require(descriptorHash.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.descriptorHash must not be blank"
            }
            require(sourceTargetLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.sourceTargetLabel must not be blank"
            }
            require(boundsLabel.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.boundsLabel must not be blank"
            }
            require(width > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.width must be positive"
            }
            require(height > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.height must be positive"
            }
            require(formatClass.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.formatClass must not be blank"
            }
            require(usageLabels.isNotEmpty()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels must not be empty"
            }
            require(usageLabels.none { it.isBlank() }) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels must not contain blanks"
            }
            require(sampleCount > 0) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.sampleCount must be positive"
            }
            require(generation >= 0L) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.generation must be non-negative"
            }
            require(lifetimeClass.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.lifetimeClass must not be blank"
            }
            require(ownerScope.isNotBlank()) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.ownerScope must not be blank"
            }
            require(byteEstimate >= 0L) {
                "GPUIntermediateTextureMaterializationRequest.descriptor.byteEstimate must be non-negative"
            }
            listOf(
                "GPUIntermediateTextureMaterializationRequest.descriptor.label" to label,
                "GPUIntermediateTextureMaterializationRequest.descriptor.purposeLabel" to purposeLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.descriptorHash" to descriptorHash,
                "GPUIntermediateTextureMaterializationRequest.descriptor.sourceTargetLabel" to sourceTargetLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.boundsLabel" to boundsLabel,
                "GPUIntermediateTextureMaterializationRequest.descriptor.formatClass" to formatClass,
                "GPUIntermediateTextureMaterializationRequest.descriptor.lifetimeClass" to lifetimeClass,
                "GPUIntermediateTextureMaterializationRequest.descriptor.ownerScope" to ownerScope,
            ).forEach { (fieldName, value) ->
                requireResourceDumpSafe(fieldName, value)
            }
            usageLabels.forEachIndexed { index, usageLabel ->
                requireResourceDumpSafe(
                    fieldName = "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels[$index]",
                    value = usageLabel,
                )
            }
            return GPUValidatedIntermediateTextureMaterializationDescriptor(
                label = label,
                purposeLabel = purposeLabel,
                descriptorHash = descriptorHash,
                sourceTargetLabel = sourceTargetLabel,
                boundsLabel = boundsLabel,
                width = width,
                height = height,
                formatClass = formatClass,
                usageLabels = usageLabels,
                sampleCount = sampleCount,
                generation = generation,
                lifetimeClass = lifetimeClass,
                ownerScope = ownerScope,
                byteEstimate = byteEstimate,
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
