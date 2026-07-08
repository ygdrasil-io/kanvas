package org.graphiks.kanvas.gpu.renderer.intermediates

enum class GPUIntermediatePurpose {
    DestinationCopy,
    ExistingIntermediate,
    LayerTarget,
    FilterIntermediate,
    MsaaResolve,
}

data class GPUIntermediateTextureDescriptor(
    val label: String,
    val purpose: GPUIntermediatePurpose,
    val descriptorHash: String,
    val sourceTargetLabel: String,
    val boundsLabel: String,
    val width: Int,
    val height: Int,
    val formatClass: String,
    val usageLabels: List<String>,
    val sampleCount: Int,
    val generation: Long,
    val lifetimeClass: String,
    val ownerScope: String,
    val byteEstimate: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUIntermediateTextureDescriptor.label must not be blank" }
        require(descriptorHash.isNotBlank()) { "GPUIntermediateTextureDescriptor.descriptorHash must not be blank" }
        require(sourceTargetLabel.isNotBlank()) { "GPUIntermediateTextureDescriptor.sourceTargetLabel must not be blank" }
        require(boundsLabel.isNotBlank()) { "GPUIntermediateTextureDescriptor.boundsLabel must not be blank" }
        require(width > 0) { "GPUIntermediateTextureDescriptor.width must be positive" }
        require(height > 0) { "GPUIntermediateTextureDescriptor.height must be positive" }
        require(formatClass.isNotBlank()) { "GPUIntermediateTextureDescriptor.formatClass must not be blank" }
        require(usageLabels.isNotEmpty()) { "GPUIntermediateTextureDescriptor.usageLabels must not be empty" }
        require(usageLabels.none { it.isBlank() }) { "GPUIntermediateTextureDescriptor.usageLabels must not contain blanks" }
        require(sampleCount > 0) { "GPUIntermediateTextureDescriptor.sampleCount must be positive" }
        require(generation >= 0L) { "GPUIntermediateTextureDescriptor.generation must be non-negative" }
        require(lifetimeClass.isNotBlank()) { "GPUIntermediateTextureDescriptor.lifetimeClass must not be blank" }
        require(ownerScope.isNotBlank()) { "GPUIntermediateTextureDescriptor.ownerScope must not be blank" }
        require(byteEstimate >= 0L) { "GPUIntermediateTextureDescriptor.byteEstimate must be non-negative" }
    }

    val usageLabel: String get() = usageLabels.joinToString(",")
}

sealed interface GPUIntermediatePlanStep {
    data class RenderToTarget(
        val commandId: String,
        val targetLabel: String,
        val routeLabel: String,
        val orderingToken: String,
    ) : GPUIntermediatePlanStep

    data class CreateIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class ReuseIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class CopyDestination(
        val sourceLabel: String,
        val destination: GPUIntermediateTextureDescriptor,
        val boundsLabel: String,
        val tokenLabel: String,
        val passSplitRequired: Boolean,
        val copyBeforeSample: Boolean,
    ) : GPUIntermediatePlanStep

    data class BindIntermediate(
        val descriptor: GPUIntermediateTextureDescriptor,
        val bindingLabel: String,
        val layoutHash: String,
    ) : GPUIntermediatePlanStep

    data class RenderLayerChildren(
        val scopeLabel: String,
        val target: GPUIntermediateTextureDescriptor,
        val childrenLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class CompositeIntermediate(
        val source: GPUIntermediateTextureDescriptor,
        val parentTargetLabel: String,
        val blendModeLabel: String,
        val routeLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class ResolveMSAA(
        val source: GPUIntermediateTextureDescriptor,
        val destination: GPUIntermediateTextureDescriptor,
        val strategyLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep

    data class Refuse(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUIntermediatePlanStep
}

data class GPUIntermediateDiagnostic(
    val code: String,
    val scopeLabel: String,
    val message: String,
    val terminal: Boolean,
) {
    init {
        require(code.isNotBlank()) { "GPUIntermediateDiagnostic.code must not be blank" }
        require(scopeLabel.isNotBlank()) { "GPUIntermediateDiagnostic.scopeLabel must not be blank" }
        require(message.isNotBlank()) { "GPUIntermediateDiagnostic.message must not be blank" }
    }
}

data class GPUIntermediateTelemetry(
    val destinationReadCopies: Long = 0L,
    val destinationReadIntermediateBinds: Long = 0L,
    val copiedBytes: Long = 0L,
    val passSplits: Long = 0L,
    val intermediatesCreated: Long = 0L,
    val intermediatesReused: Long = 0L,
    val intermediatesRefused: Long = 0L,
    val liveIntermediateBytes: Long = 0L,
    val layerTargets: Long = 0L,
    val layerComposites: Long = 0L,
    val msaaTargets: Long = 0L,
    val msaaResolves: Long = 0L,
) {
    fun dumpLine(): String =
        "intermediate.telemetry destinationReadCopies=$destinationReadCopies " +
            "destinationReadIntermediateBinds=$destinationReadIntermediateBinds copiedBytes=$copiedBytes " +
            "passSplits=$passSplits intermediatesCreated=$intermediatesCreated " +
            "intermediatesReused=$intermediatesReused intermediatesRefused=$intermediatesRefused " +
            "liveIntermediateBytes=$liveIntermediateBytes layerTargets=$layerTargets " +
            "layerComposites=$layerComposites msaaTargets=$msaaTargets msaaResolves=$msaaResolves"
}

data class GPUIntermediatePlan(
    val planId: String,
    val targetId: String,
    val steps: List<GPUIntermediatePlanStep>,
    val diagnostics: List<GPUIntermediateDiagnostic> = emptyList(),
    val telemetry: GPUIntermediateTelemetry = GPUIntermediateTelemetry(),
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlan.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlan.targetId must not be blank" }
        require(steps.isNotEmpty()) { "GPUIntermediatePlan.steps must not be empty" }
        val hasRefusal = steps.any { it is GPUIntermediatePlanStep.Refuse }
        require(!hasRefusal || steps.size == 1) {
            "GPUIntermediatePlan cannot mix terminal refusal with executable steps"
        }
    }
}

fun GPUIntermediatePlan.dumpLines(): List<String> =
    listOf(
        "intermediate.plan id=$planId target=$targetId steps=${steps.size} " +
            "diagnostics=${diagnostics.map { it.code }.ifEmpty { listOf("none") }.joinToString(",")}",
    ) + steps.map { step -> step.dumpLine() } + listOf(telemetry.dumpLine())

private fun GPUIntermediatePlanStep.dumpLine(): String =
    when (this) {
        is GPUIntermediatePlanStep.RenderToTarget ->
            "intermediate.render command=$commandId target=$targetLabel route=$routeLabel ordering=$orderingToken"
        is GPUIntermediatePlanStep.CreateIntermediate ->
            "intermediate.create ${descriptor.dumpFields()}"
        is GPUIntermediatePlanStep.ReuseIntermediate ->
            "intermediate.reuse ${descriptor.dumpFields()}"
        is GPUIntermediatePlanStep.CopyDestination ->
            "intermediate.copy source=$sourceLabel destination=${destination.label} bounds=$boundsLabel " +
                "token=$tokenLabel split=$passSplitRequired copyBeforeSample=$copyBeforeSample"
        is GPUIntermediatePlanStep.BindIntermediate ->
            "intermediate.bind label=${descriptor.label} binding=$bindingLabel layout=$layoutHash"
        is GPUIntermediatePlanStep.RenderLayerChildren ->
            "intermediate.layer-children scope=$scopeLabel target=${target.label} children=$childrenLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.CompositeIntermediate ->
            "intermediate.composite source=${source.label} parent=$parentTargetLabel blend=$blendModeLabel route=$routeLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.ResolveMSAA ->
            "intermediate.msaa-resolve source=${source.label} destination=${destination.label} strategy=$strategyLabel token=$tokenLabel"
        is GPUIntermediatePlanStep.Refuse ->
            "intermediate.refused scope=$scopeLabel reason=$reasonCode"
    }

private fun GPUIntermediateTextureDescriptor.dumpFields(): String =
    "label=$label purpose=$purpose descriptor=$descriptorHash source=$sourceTargetLabel bounds=$boundsLabel " +
        "size=${width}x$height format=$formatClass sampleCount=$sampleCount generation=$generation " +
        "usage=$usageLabel lifetime=$lifetimeClass owner=$ownerScope bytes=$byteEstimate"
