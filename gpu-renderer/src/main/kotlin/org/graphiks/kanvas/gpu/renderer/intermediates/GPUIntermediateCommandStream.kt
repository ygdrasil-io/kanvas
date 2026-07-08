package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream

fun GPUPassCommandStream.Companion.fromIntermediatePlan(
    streamId: String,
    packetStreamId: String,
    passId: String,
    targetStateHash: String,
    loadStoreLabel: String,
    plan: GPUIntermediatePlan,
): GPUPassCommandStream {
    require(targetStateHash.isNotBlank()) { "fromIntermediatePlan targetStateHash must not be blank" }
    require(loadStoreLabel.isNotBlank()) { "fromIntermediatePlan loadStoreLabel must not be blank" }

    val commands = buildList {
        add(GPUPassCommand.BeginRenderPass(targetStateHash = targetStateHash, loadStoreLabel = loadStoreLabel))
        plan.steps.forEach { step ->
            when (step) {
                is GPUIntermediatePlanStep.CreateIntermediate ->
                    add(step.descriptor.prepareCommand())
                is GPUIntermediatePlanStep.ReuseIntermediate ->
                    add(step.descriptor.prepareCommand())
                is GPUIntermediatePlanStep.CopyDestination ->
                    add(
                        GPUPassCommand.CopyTexture(
                            sourceLabel = step.sourceLabel,
                            destinationLabel = step.destination.label,
                            boundsLabel = step.boundsLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.BindIntermediate -> Unit
                is GPUIntermediatePlanStep.RenderToTarget ->
                    add(
                        GPUPassCommand.Draw(
                            vertexSourceLabel = step.routeLabel,
                            packetId = GPUDrawPacketID(step.commandId),
                        ),
                    )
                is GPUIntermediatePlanStep.RenderLayerChildren ->
                    add(
                        GPUPassCommand.RenderLayerChildren(
                            scopeLabel = step.scopeLabel,
                            targetLabel = step.target.label,
                            childrenLabel = step.childrenLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.CompositeIntermediate ->
                    add(
                        GPUPassCommand.CompositeLayer(
                            sourceLabel = step.source.label,
                            parentTargetLabel = step.parentTargetLabel,
                            blendModeLabel = step.blendModeLabel,
                            routeLabel = step.routeLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.ResolveMSAA ->
                    add(
                        GPUPassCommand.ResolveMSAA(
                            sourceLabel = step.source.label,
                            destinationLabel = step.destination.label,
                            strategyLabel = step.strategyLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                is GPUIntermediatePlanStep.Refuse ->
                    add(GPUPassCommand.RefuseIntermediate(scopeLabel = step.scopeLabel, reasonCode = step.reasonCode))
            }
        }
        add(GPUPassCommand.EndRenderPass(passId = passId))
    }

    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStreamId,
        passId = passId,
        commands = commands,
        diagnostics = emptyList(),
        operandBridge = emptyList(),
    )
}

private fun GPUIntermediateTextureDescriptor.prepareCommand(): GPUPassCommand.PrepareIntermediateTexture =
    GPUPassCommand.PrepareIntermediateTexture(
        textureLabel = label,
        purposeLabel = purpose.name,
        descriptorHash = descriptorHash,
        usageLabel = usageLabel,
        sampleCount = sampleCount,
        byteEstimate = byteEstimate,
    )
