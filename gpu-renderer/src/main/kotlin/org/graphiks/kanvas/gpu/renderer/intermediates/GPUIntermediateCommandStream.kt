package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic
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
        var renderPassOpen = false

        fun openRenderPassIfNeeded() {
            if (renderPassOpen) {
                return
            }
            add(GPUPassCommand.BeginRenderPass(targetStateHash = targetStateHash, loadStoreLabel = loadStoreLabel))
            renderPassOpen = true
        }

        fun closeRenderPassIfOpen() {
            if (!renderPassOpen) {
                return
            }
            add(GPUPassCommand.EndRenderPass(passId = passId))
            renderPassOpen = false
        }

        plan.steps.forEach { step ->
            when (step) {
                is GPUIntermediatePlanStep.CreateIntermediate -> {
                    closeRenderPassIfOpen()
                    add(step.descriptor.prepareCommand())
                }
                is GPUIntermediatePlanStep.ReuseIntermediate -> {
                    closeRenderPassIfOpen()
                    add(step.descriptor.prepareCommand())
                }
                is GPUIntermediatePlanStep.CopyDestination -> {
                    // Destination copies are never encoded inside an active render pass.
                    closeRenderPassIfOpen()
                    add(
                        GPUPassCommand.CopyTexture(
                            sourceLabel = step.sourceLabel,
                            destinationLabel = step.destination.label,
                            boundsLabel = step.boundsLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                }
                is GPUIntermediatePlanStep.BindIntermediate -> Unit
                is GPUIntermediatePlanStep.RenderToTarget -> {
                    openRenderPassIfNeeded()
                    add(
                        GPUPassCommand.Draw(
                            vertexSourceLabel = step.routeLabel,
                            packetId = GPUDrawPacketID(step.commandId),
                        ),
                    )
                }
                is GPUIntermediatePlanStep.RenderLayerChildren -> {
                    openRenderPassIfNeeded()
                    add(
                        GPUPassCommand.RenderLayerChildren(
                            scopeLabel = step.scopeLabel,
                            targetLabel = step.target.label,
                            childrenLabel = step.childrenLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                }
                is GPUIntermediatePlanStep.CompositeIntermediate -> {
                    openRenderPassIfNeeded()
                    add(
                        GPUPassCommand.CompositeLayer(
                            sourceLabel = step.source.label,
                            parentTargetLabel = step.parentTargetLabel,
                            blendModeLabel = step.blendModeLabel,
                            routeLabel = step.routeLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                }
                is GPUIntermediatePlanStep.ResolveMSAA -> {
                    closeRenderPassIfOpen()
                    add(
                        GPUPassCommand.ResolveMSAA(
                            sourceLabel = step.source.label,
                            destinationLabel = step.destination.label,
                            strategyLabel = step.strategyLabel,
                            tokenLabel = step.tokenLabel,
                        ),
                    )
                }
                is GPUIntermediatePlanStep.Refuse -> {
                    closeRenderPassIfOpen()
                    add(GPUPassCommand.RefuseIntermediate(scopeLabel = step.scopeLabel, reasonCode = step.reasonCode))
                }
            }
        }
        closeRenderPassIfOpen()
    }

    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStreamId,
        passId = passId,
        commands = commands,
        diagnostics = plan.diagnostics.map { diagnostic ->
            GPUPassDiagnostic(
                code = diagnostic.code,
                passId = passId,
                invocationId = diagnostic.scopeLabel,
                terminal = diagnostic.terminal,
            )
        },
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
