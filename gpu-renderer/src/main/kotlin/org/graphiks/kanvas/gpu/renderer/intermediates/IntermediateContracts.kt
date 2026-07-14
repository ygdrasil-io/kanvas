package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateTextureMaterializationDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement

/** Stable handle-free identity for an intermediate attachment or texture. */
@JvmInline
value class GPUIntermediateIdentity(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUIntermediateIdentity.value must not be blank" }
    }
}

enum class GPUIntermediatePurpose {
    DestinationCopy,
    ReadbackSnapshot,
    BlendSource,
    ExistingIntermediate,
    LayerTarget,
    FilterIntermediate,
    MsaaResolve,
}

data class GPUIntermediateTextureDescriptor(
    override val label: String,
    val purpose: GPUIntermediatePurpose,
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
    override val purposeLabel: String get() = purpose.name
}

sealed interface GPUIntermediatePlanStep {
    data class RenderToTarget(
        val commandId: String,
        val targetLabel: String,
        val routeLabel: String,
        val orderingToken: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(commandId.isNotBlank()) { "GPUIntermediatePlanStep.RenderToTarget.commandId must not be blank" }
            require(targetLabel.isNotBlank()) { "GPUIntermediatePlanStep.RenderToTarget.targetLabel must not be blank" }
            require(routeLabel.isNotBlank()) { "GPUIntermediatePlanStep.RenderToTarget.routeLabel must not be blank" }
            require(orderingToken.isNotBlank()) { "GPUIntermediatePlanStep.RenderToTarget.orderingToken must not be blank" }
        }
    }

    data class CreateIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class ReuseIntermediate(val descriptor: GPUIntermediateTextureDescriptor) : GPUIntermediatePlanStep

    data class CopyDestination(
        val sourceLabel: String,
        val destination: GPUIntermediateTextureDescriptor,
        val boundsLabel: String,
        val tokenLabel: String,
        val passSplitRequired: Boolean,
        val copyBeforeSample: Boolean,
    ) : GPUIntermediatePlanStep {
        init {
            require(sourceLabel.isNotBlank()) { "GPUIntermediatePlanStep.CopyDestination.sourceLabel must not be blank" }
            require(boundsLabel.isNotBlank()) { "GPUIntermediatePlanStep.CopyDestination.boundsLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUIntermediatePlanStep.CopyDestination.tokenLabel must not be blank" }
        }
    }

    data class BindIntermediate(
        val descriptor: GPUIntermediateTextureDescriptor,
        val bindingLabel: String,
        val layoutHash: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(bindingLabel.isNotBlank()) { "GPUIntermediatePlanStep.BindIntermediate.bindingLabel must not be blank" }
            require(layoutHash.isNotBlank()) { "GPUIntermediatePlanStep.BindIntermediate.layoutHash must not be blank" }
        }
    }

    data class RenderLayerChildren(
        val scopeLabel: String,
        val target: GPUIntermediateTextureDescriptor,
        val childrenLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(scopeLabel.isNotBlank()) { "GPUIntermediatePlanStep.RenderLayerChildren.scopeLabel must not be blank" }
            require(childrenLabel.isNotBlank()) { "GPUIntermediatePlanStep.RenderLayerChildren.childrenLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUIntermediatePlanStep.RenderLayerChildren.tokenLabel must not be blank" }
        }
    }

    data class CompositeIntermediate(
        val source: GPUIntermediateTextureDescriptor,
        val parentTargetLabel: String,
        val blendModeLabel: String,
        val routeLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(parentTargetLabel.isNotBlank()) { "GPUIntermediatePlanStep.CompositeIntermediate.parentTargetLabel must not be blank" }
            require(blendModeLabel.isNotBlank()) { "GPUIntermediatePlanStep.CompositeIntermediate.blendModeLabel must not be blank" }
            require(routeLabel.isNotBlank()) { "GPUIntermediatePlanStep.CompositeIntermediate.routeLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUIntermediatePlanStep.CompositeIntermediate.tokenLabel must not be blank" }
        }
    }

    data class ResolveMSAA(
        val source: GPUIntermediateTextureDescriptor,
        val destination: GPUIntermediateTextureDescriptor,
        val strategyLabel: String,
        val tokenLabel: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(strategyLabel.isNotBlank()) { "GPUIntermediatePlanStep.ResolveMSAA.strategyLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUIntermediatePlanStep.ResolveMSAA.tokenLabel must not be blank" }
        }
    }

    data class Refuse(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUIntermediatePlanStep {
        init {
            require(scopeLabel.isNotBlank()) { "GPUIntermediatePlanStep.Refuse.scopeLabel must not be blank" }
            require(reasonCode.isNotBlank()) { "GPUIntermediatePlanStep.Refuse.reasonCode must not be blank" }
        }
    }
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
    init {
        require(destinationReadCopies >= 0L) { "GPUIntermediateTelemetry.destinationReadCopies must be non-negative" }
        require(destinationReadIntermediateBinds >= 0L) { "GPUIntermediateTelemetry.destinationReadIntermediateBinds must be non-negative" }
        require(copiedBytes >= 0L) { "GPUIntermediateTelemetry.copiedBytes must be non-negative" }
        require(passSplits >= 0L) { "GPUIntermediateTelemetry.passSplits must be non-negative" }
        require(intermediatesCreated >= 0L) { "GPUIntermediateTelemetry.intermediatesCreated must be non-negative" }
        require(intermediatesReused >= 0L) { "GPUIntermediateTelemetry.intermediatesReused must be non-negative" }
        require(intermediatesRefused >= 0L) { "GPUIntermediateTelemetry.intermediatesRefused must be non-negative" }
        require(liveIntermediateBytes >= 0L) { "GPUIntermediateTelemetry.liveIntermediateBytes must be non-negative" }
        require(layerTargets >= 0L) { "GPUIntermediateTelemetry.layerTargets must be non-negative" }
        require(layerComposites >= 0L) { "GPUIntermediateTelemetry.layerComposites must be non-negative" }
        require(msaaTargets >= 0L) { "GPUIntermediateTelemetry.msaaTargets must be non-negative" }
        require(msaaResolves >= 0L) { "GPUIntermediateTelemetry.msaaResolves must be non-negative" }
    }

    fun dumpLine(): String =
        "intermediate.telemetry destinationReadCopies=$destinationReadCopies " +
            "destinationReadIntermediateBinds=$destinationReadIntermediateBinds copiedBytes=$copiedBytes " +
            "passSplits=$passSplits intermediatesCreated=$intermediatesCreated " +
            "intermediatesReused=$intermediatesReused intermediatesRefused=$intermediatesRefused " +
            "liveIntermediateBytes=$liveIntermediateBytes layerTargets=$layerTargets " +
            "layerComposites=$layerComposites msaaTargets=$msaaTargets msaaResolves=$msaaResolves"
}

/** Destination-read identity and optional exact-intermediate eligibility emitted without selecting a strategy. */
data class GPUIntermediateDestinationReadEligibility(
    val commandId: String,
    val requirement: GPUBlendDestinationReadRequirement,
    val eligibleIntermediate: GPUIntermediateTextureDescriptor?,
) {
    init {
        require(commandId.isNotBlank()) {
            "GPUIntermediateDestinationReadEligibility.commandId must not be blank"
        }
    }
}

data class GPUIntermediatePlan(
    val planId: String,
    val targetId: String,
    val steps: List<GPUIntermediatePlanStep>,
    val diagnostics: List<GPUIntermediateDiagnostic> = emptyList(),
    val telemetry: GPUIntermediateTelemetry = GPUIntermediateTelemetry(),
    val destinationReadEligibilities: List<GPUIntermediateDestinationReadEligibility> = emptyList(),
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlan.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlan.targetId must not be blank" }
        require(steps.isNotEmpty()) { "GPUIntermediatePlan.steps must not be empty" }
        val refusalSteps = steps.filterIsInstance<GPUIntermediatePlanStep.Refuse>()
        val terminalDiagnostics = diagnostics.filter { it.terminal }
        require(refusalSteps.size <= 1) {
            "GPUIntermediatePlan must not contain multiple terminal refusal steps"
        }
        require(refusalSteps.size != 1 || steps.size == 1) {
            "GPUIntermediatePlan cannot mix terminal refusal with executable steps"
        }
        require(refusalSteps.isEmpty() || terminalDiagnostics.isEmpty()) {
            "GPUIntermediatePlan refusal-only plans must not duplicate terminal diagnostics"
        }
        require(terminalDiagnostics.size <= 1) {
            "GPUIntermediatePlan must not contain more than one terminal diagnostic"
        }
    }
}

fun GPUIntermediatePlan.dumpLines(): List<String> =
    listOf(
        "intermediate.plan id=$planId target=$targetId steps=${steps.size} " +
            "diagnostics=${headerDiagnostics().ifEmpty { listOf("none") }.joinToString(",")}",
    ) + steps.map { step -> step.dumpLine() } +
        destinationReadEligibilities.map { eligibility -> eligibility.dumpLine() } +
        listOf(telemetry.dumpLine())

private fun GPUIntermediateDestinationReadEligibility.dumpLine(): String =
    "intermediate.destination-read-eligibility command=$commandId requirement=$requirement " +
        "eligible=${eligibleIntermediate?.label ?: "none"} " +
        "descriptor=${eligibleIntermediate?.descriptorHash ?: "none"}"

private fun GPUIntermediatePlan.headerDiagnostics(): List<String> {
    val refusalDiagnostic = steps.firstOrNull { it is GPUIntermediatePlanStep.Refuse } as? GPUIntermediatePlanStep.Refuse
    if (refusalDiagnostic != null) {
        return listOf(refusalDiagnostic.reasonCode)
    }

    val terminalDiagnostic = diagnostics.singleOrNull { it.terminal }
    if (terminalDiagnostic != null) {
        return listOf(terminalDiagnostic.code)
    }
    return diagnostics.map { it.code }
}

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
