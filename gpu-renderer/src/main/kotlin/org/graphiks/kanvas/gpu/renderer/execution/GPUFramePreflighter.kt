package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan

/** Sole transactional join between an immutable semantic frame and materialized resource facts. */
class GPUFramePreflighter(
    private val context: GPUFramePreflightContext,
    capabilities: GPUCapabilities,
    private val resourceProvider: GPUFrameResourcePreflightProvider,
    private val completionProvider: GPUQueueCompletionProvider,
    private val surfaceProvider: GPUSurfaceOutputProvider,
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    private val capabilities: GPUCapabilities = capabilities.preflightSnapshot()

    fun preflight(framePlan: GPUFramePlan): GPUFramePreflightResult {
        pureValidation(framePlan)?.let { return GPUFramePreflightResult.Refused(it) }

        val readbackLayouts = linkedMapOf<GPUFrameResourceRef, GPUReadbackLayoutPlan.Planned>()
        for (step in framePlan.steps) {
            if (step is GPUFrameStep.ReadbackCopyStep) {
                val planned = when (val result = try {
                    readbackLayoutPlanner.plan(step.request, capabilities)
                } catch (failure: Throwable) {
                    return GPUFramePreflightResult.Refused(
                        diagnostic(
                            "failed.preflight.readback_layout",
                            "Readback layout planning failed without a typed result.",
                            mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                        ),
                    )
                }) {
                    is GPUReadbackLayoutPlan.Planned -> result
                    is GPUReadbackLayoutPlan.Refused -> return GPUFramePreflightResult.Refused(result.diagnostic)
                }
                if (readbackLayouts.put(step.staging, planned) != null) {
                    return GPUFramePreflightResult.Refused(
                        diagnostic("invalid.preflight.readback_staging_duplicate", "A staging resource may serve one readback output."),
                    )
                }
            }
        }

        val preparationSteps = framePlan.steps.withIndex()
            .filter { it.value is GPUFrameStep.PrepareResourcesStep }
        val declared = preparationSteps.flatMap { indexed ->
            (indexed.value as GPUFrameStep.PrepareResourcesStep).requests
        }
        val declaredByRef = declared.associateBy { it.resource }
        for (readback in framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()) {
            val preparation = declaredByRef[readback.staging]
            if (preparation == null || preparation.role != GPUFrameResourceRole.ReadbackStaging) {
                return GPUFramePreflightResult.Refused(
                    diagnostic(
                        "invalid.preflight.readback_staging_preparation",
                        "Every readback copy requires exactly one ReadbackStaging preparation.",
                        mapOf("staging" to readback.staging.value),
                    ),
                )
            }
        }
        declared.filter { it.role == GPUFrameResourceRole.ReadbackStaging }.firstOrNull { it.resource !in readbackLayouts }?.let {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.readback_request_missing",
                    "Readback staging cannot be prepared without one logical readback request.",
                    mapOf("staging" to it.resource.value),
                ),
            )
        }
        referencedResources(framePlan).firstOrNull { it !in declaredByRef && it !in context.resourceGenerations }?.let { missing ->
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.resource_undeclared",
                    "Every non-prebound semantic resource must have one preparation request.",
                    mapOf("resource" to missing.value),
                ),
            )
        }

        val ownerScope = try {
            resourceProvider.beginFramePreparation(framePlan.frameId.value, context.deviceGeneration).ownerScope
        } catch (failure: Throwable) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "failed.preflight.resource_session",
                    "Resource provider could not open an isolated preparation journal.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }

        var acquiredAnyResource = false
        val ordinaryResources = mutableListOf<GPUPreparedResourceEvidence>()
        val readbackOutputs = mutableListOf<GPUPreparedReadbackOutput>()
        val preparedGenerationMap = linkedMapOf<GPUFrameResourceRef, Long>()
        val semanticResourceRefs = referencedResources(framePlan) + declared.map { it.resource }
        context.resourceGenerations.forEach { (resource, generation) ->
            if (resource in semanticResourceRefs) preparedGenerationMap[resource] = generation
        }

        for (indexed in preparationSteps) {
            val step = indexed.value as GPUFrameStep.PrepareResourcesStep
            for (preparation in step.requests) {
                val generation = context.resourceGenerations[preparation.resource]
                    ?: return refuseWithRollback(
                        ownerScope,
                        acquiredAnyResource,
                        diagnostic(
                            "stale.preflight.resource_generation_missing",
                            "A current generation is required for every prepared logical resource.",
                            mapOf("resource" to preparation.resource.value),
                        ),
                    )
                val readback = readbackLayouts[preparation.resource]
                val input = try {
                    GPUFrameResourcePreparationInput(
                        preparation = preparation,
                        ownerScope = ownerScope,
                        deviceGeneration = context.deviceGeneration,
                        resourceGeneration = generation,
                        firstStep = indexed.index,
                        lastStepExclusive = lastUseExclusive(framePlan, preparation.resource, indexed.index),
                        budgetPlan = framePlan.memoryBudget,
                        capabilities = capabilities,
                        readbackStagingDescriptor = readback?.stagingDescriptor,
                    )
                } catch (failure: Throwable) {
                    return refuseWithRollback(
                        ownerScope,
                        acquiredAnyResource,
                        diagnostic(
                            "invalid.preflight.resource_preparation_input",
                            "Resource preparation input is inconsistent.",
                            mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                        ),
                    )
                }
                acquiredAnyResource = true
                val decision = try {
                    resourceProvider.prepareFrameResource(input)
                } catch (failure: Throwable) {
                    return refuseWithRollback(
                        ownerScope,
                        acquiredAnyResource,
                        diagnostic(
                            "failed.preflight.resource_provider",
                            "Resource preparation failed without producing a typed decision.",
                            mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                        ),
                    )
                }
                when (decision) {
                    is GPUFrameResourcePreparationDecision.Refused ->
                        return refuseWithRollback(ownerScope, acquiredAnyResource, decision.diagnostic)
                    is GPUFrameResourcePreparationDecision.Prepared -> {
                        validatePreparedResource(preparation, generation, decision)?.let { invalid ->
                            return refuseWithRollback(ownerScope, true, invalid)
                        }
                        preparedGenerationMap[preparation.resource] = decision.resourceGeneration
                        if (preparation.role == GPUFrameResourceRole.ReadbackStaging) {
                            val layout = readback
                                ?: return refuseWithRollback(
                                    ownerScope,
                                    true,
                                    diagnostic(
                                        "invalid.preflight.readback_layout_missing",
                                        "Readback staging was prepared without a matching logical request.",
                                    ),
                                )
                            val lease = decision.outputOwnedReadbackLease
                                ?: return refuseWithRollback(
                                    ownerScope,
                                    true,
                                    diagnostic(
                                        "invalid.preflight.readback_lease_missing",
                                        "Prepared readback staging must transfer its output-owned lease.",
                                    ),
                                )
                            if (decision.concreteResource !is GPUPreparedConcreteResourceRef.Buffer ||
                                lease.resourceRef != decision.concreteResource.ref ||
                                lease.ownerScope != ownerScope ||
                                lease.deviceGeneration != context.deviceGeneration ||
                                lease.logicalMinimumBytes != layout.stagingDescriptor.minimumBufferBytes ||
                                lease.backingBufferBytes < lease.logicalMinimumBytes ||
                                lease.backingBufferBytes != (preparation.descriptor as GPUFrameBufferDescriptor).byteSize ||
                                lease.backingBufferBytes > layout.stagingDescriptor.maxBufferSize ||
                                lease.usages != preparation.usages
                            ) {
                                return refuseWithRollback(
                                    ownerScope,
                                    true,
                                    diagnostic(
                                        "invalid.preflight.readback_lease_evidence",
                                        "Readback lease does not match prepared resource and layout evidence.",
                                    ),
                                )
                            }
                            val request = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
                                .single { it.staging == preparation.resource }.request
                            readbackOutputs += GPUPreparedReadbackOutput(
                                stagingResource = preparation.resource,
                                concreteResource = decision.concreteResource,
                                resourceGeneration = decision.resourceGeneration,
                                request = request,
                                layout = layout.layout,
                                stagingLease = lease,
                            )
                        } else {
                            ordinaryResources += GPUPreparedResourceEvidence(
                                logicalResource = decision.logicalResource,
                                concreteResource = decision.concreteResource,
                                role = decision.role,
                                deviceGeneration = decision.deviceGeneration,
                                resourceGeneration = decision.resourceGeneration,
                            )
                        }
                    }
                }
            }
        }

        acquiredAnyResource = true
        val renderMaterialization = materializeRenderOperands(framePlan, ownerScope)
        val materialized = when (renderMaterialization) {
            is GPUResourceMaterializationDecision.Materialized -> renderMaterialization
            is GPUResourceMaterializationDecision.Refused -> return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(
                    renderMaterialization.diagnostic.code,
                    renderMaterialization.diagnostic.message,
                    mapOf("resource" to renderMaterialization.diagnostic.resourceLabel),
                ),
            )
            is GPUResourceMaterializationDecision.Deferred -> return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(renderMaterialization.reasonCode, "Render operand materialization was deferred."),
            )
        }
        materialized.diagnostics.firstOrNull { it.terminal }?.let { terminal ->
            return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(
                    terminal.code,
                    terminal.message,
                    terminal.facts + mapOf("resource" to terminal.resourceLabel),
                ),
            )
        }
        validateRenderOperands(framePlan, materialized, ownerScope)?.let { invalid ->
            return refuseWithRollback(ownerScope, acquiredAnyResource, invalid)
        }

        val encoderScopes = try {
            lowerEncoderScopes(framePlan, materialized, preparedGenerationMap)
        } catch (failure: Throwable) {
            return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(
                    "invalid.preflight.encoder_lowering",
                    "Semantic steps could not be lowered to a one-to-one encoder plan.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
        val encoderPlan = try {
            GPUCommandEncoderPlan.ordered(
                planId = "frame.${framePlan.frameId.value}",
                contextIdentity = context.targetId,
                deviceGeneration = context.deviceGeneration,
                targetGeneration = context.targetGeneration,
                scopes = encoderScopes,
            )
        } catch (failure: Throwable) {
            return refuseWithRollback(
                ownerScope,
                true,
                diagnostic(
                    "invalid.preflight.encoder_plan",
                    "Ordered encoder plan invariants failed.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }

        val partition = framePlan.steps.mapIndexed { index, step ->
            GPUPreparedStepEvidence(index, step.preparedLane(), step::class.simpleName ?: "GPUFrameStep")
        }
        val dependencies = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.DependencyBarrierStep ->
                    GPUPreparedDependencyEvidence(index, "DependencyBarrier", step.reasonCode)
                is GPUFrameStep.TargetTransitionStep ->
                    GPUPreparedDependencyEvidence(index, "TargetTransition", step.transitionKind.name)
                else -> null
            }
        }
        val hostActions = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.AcquireSurfaceOutput ->
                    GPUFrameHostAction(index, GPUHostActionKind.AcquireSurface, step.descriptor.output)
                is GPUFrameStep.PostSubmitPresentAction ->
                    GPUFrameHostAction(index, GPUHostActionKind.Present, step.output)
                else -> null
            }
        }
        val resources: GPUPreparedResourceSet
        val generationSeal: GPUPreparedGenerationSeal
        try {
            resources = GPUPreparedResourceSet(
                ordinaryResources = ordinaryResources,
                outputOwnedReadbacks = readbackOutputs,
                commandResourceLeases = materialized.resourceLeases,
                commandTextureResources = materialized.resources,
                commandBufferResources = materialized.bufferResources,
                commandDiagnostics = materialized.diagnostics,
            )
            generationSeal = GPUPreparedGenerationSeal(
                deviceGeneration = context.deviceGeneration,
                targetGeneration = context.targetGeneration,
                resourceGenerations = preparedGenerationMap,
                capabilitySealHash = framePlan.capabilitySeal.sealHash,
            )
        } catch (failure: Throwable) {
            return refuseWithRollback(
                ownerScope,
                true,
                diagnostic(
                    "invalid.preflight.prepared_evidence",
                    "Prepared resource and generation evidence failed before late surface acquisition.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }

        val ticketReservation = try {
            completionProvider.reserveTicket(
                GPUQueueCompletionTicketRequest(framePlan.frameId, context.deviceGeneration),
            )
        } catch (failure: Throwable) {
            return refuseWithRollback(
                ownerScope,
                true,
                diagnostic(
                    "failed.preflight.completion_ticket_provider",
                    "Completion ticket provider failed without a typed result.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
        val ticket = when (val reservation = ticketReservation) {
            is GPUQueueCompletionTicketReservation.Reserved -> reservation.ticket
            GPUQueueCompletionTicketReservation.Missing -> return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic("unsupported.preflight.completion_ticket_missing", "Queue completion proof is missing."),
            )
            is GPUQueueCompletionTicketReservation.Failed -> return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                reservation.diagnostic,
            )
            is GPUQueueCompletionTicketReservation.Duplicate -> return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(
                    "unsupported.preflight.completion_ticket_duplicate",
                    "Queue completion ticket identity was already reserved.",
                    mapOf("ticketId" to reservation.ticketId.value),
                ),
            )
        }
        if (ticket.frameId != framePlan.frameId || ticket.deviceGeneration != context.deviceGeneration) {
            return refuseWithRollback(
                ownerScope,
                acquiredAnyResource,
                diagnostic(
                    "unsupported.preflight.completion_ticket_mismatch",
                    "Queue completion ticket does not match the prepared frame generation.",
                ),
            )
        }

        val acquireStep = framePlan.steps.filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>().singleOrNull()
        val acquiredSurface = if (acquireStep == null) {
            null
        } else {
            val acquisition = try {
                surfaceProvider.acquire(
                    GPUSurfaceAcquisitionRequest(acquireStep.descriptor, context.deviceGeneration),
                )
            } catch (failure: Throwable) {
                return refuseWithRollback(
                    ownerScope,
                    true,
                    diagnostic(
                        "failed.preflight.surface_acquisition_provider",
                        "Surface provider failed without a typed result.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    ),
                )
            }
            when (acquisition) {
                is GPUSurfaceAcquisitionResult.Acquired -> acquisition.output
                is GPUSurfaceAcquisitionResult.Unavailable -> return refuseWithRollback(
                    ownerScope,
                    acquiredAnyResource,
                    surfaceDiagnostic(acquisition.status),
                )
            }
        }
        if (acquiredSurface != null &&
            (acquiredSurface.output != acquireStep?.descriptor?.output ||
                acquiredSurface.deviceGeneration != context.deviceGeneration ||
                acquiredSurface.targetGeneration != context.targetGeneration)
        ) {
            val rollback = GPUFrameRollback(ownerScope, resourceProvider, surfaceProvider, acquiredSurface).execute()
            return GPUFramePreflightResult.Refused(
                diagnostic("stale.preflight.surface_generation", "Acquired surface output generation is stale."),
                rollback,
            )
        }

        val rollback = GPUFrameRollback(ownerScope, resourceProvider, surfaceProvider, acquiredSurface)
        return try {
            GPUFramePreflightResult.Prepared(
                PreparedGPUFrame(
                    semanticPlan = framePlan,
                    encoderPlan = encoderPlan,
                    resources = resources,
                    generationSeal = generationSeal,
                    completionTicket = ticket,
                    acquiredSurfaceOutput = acquiredSurface,
                    rollback = rollback,
                    stepPartition = partition,
                    dependencyEvidence = dependencies,
                    hostActions = hostActions,
                ),
            )
        } catch (failure: Throwable) {
            GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.prepared_frame",
                    "Prepared frame invariants failed after late acquisition.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
                rollback.execute(),
            )
        }
    }

    private fun pureValidation(framePlan: GPUFramePlan): GPUDiagnostic? {
        firstUnsafePreparedIdentity(framePlan)?.let { field ->
            return diagnostic(
                "invalid.preflight.dump_unsafe_identity",
                "A semantic identity copied into prepared evidence is not dump-safe.",
                mapOf("field" to field),
            )
        }
        if (framePlan.atomicallyRefused) {
            return diagnostic("unsupported.preflight.frame_atomically_refused", "An atomically refused frame cannot be prepared.")
        }
        if (framePlan.capabilitySeal.deviceGeneration != context.deviceGeneration) {
            return diagnostic("stale.preflight.device_generation", "Frame and current device generations differ.")
        }
        val currentSeal = GPUFrameCapabilitySeal.capture(framePlan.frameId, context.deviceGeneration, capabilities)
        if (currentSeal.sealHash != framePlan.capabilitySeal.sealHash) {
            return diagnostic("stale.preflight.capability_seal", "The current capability snapshot differs from the frame seal.")
        }
        framePlan.memoryBudget.diagnostic?.let { return it }

        val acquires = framePlan.steps.filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
        val blits = framePlan.steps.filterIsInstance<GPUFrameStep.SurfaceBlitRenderPassStep>()
        val presents = framePlan.steps.filterIsInstance<GPUFrameStep.PostSubmitPresentAction>()
        if (listOf(acquires.size, blits.size, presents.size).any { it > 1 }) {
            return diagnostic("invalid.preflight.surface_output_duplicate", "A frame may own at most one surface output chain.")
        }
        if ((acquires.isEmpty() && (blits.isNotEmpty() || presents.isNotEmpty())) ||
            (acquires.isNotEmpty() && (blits.size != 1 || presents.size != 1))
        ) {
            return diagnostic("invalid.preflight.surface_output_incomplete", "Surface acquire, blit and present must form one complete chain.")
        }
        if (acquires.isNotEmpty()) {
            val output = acquires.single().descriptor.output
            if (blits.single().output != output || presents.single().output != output) {
                return diagnostic("invalid.preflight.surface_output_mismatch", "Surface acquire, blit and present must name the same output.")
            }
            if (acquires.single().descriptor.targetGeneration != context.targetGeneration) {
                return diagnostic("stale.preflight.target_generation", "Surface descriptor target generation is stale.")
            }
            val acquireIndex = framePlan.steps.indexOf(acquires.single())
            val blitIndex = framePlan.steps.indexOf(blits.single())
            val presentIndex = framePlan.steps.indexOf(presents.single())
            if (!(acquireIndex < blitIndex && blitIndex < presentIndex)) {
                return diagnostic("invalid.preflight.surface_output_order", "Surface acquire, blit and present order is invalid.")
            }
        }
        for (step in framePlan.steps) {
            when (step) {
                is GPUFrameStep.RenderPassStep -> {
                    val expected = context.resourceGenerations[step.target]
                        ?: return diagnostic("stale.preflight.resource_generation_missing", "Render target generation is unavailable.")
                    if (step.drawPackets.any { it.resourceGeneration != expected }) {
                        return diagnostic("stale.preflight.resource_generation", "A render packet resource generation is stale.")
                    }
                    if (step.drawPackets.any { it.renderPipelineKey == null }) {
                        return diagnostic("invalid.preflight.render_pipeline_key_missing", "Render packets require a pipeline key before materialization.")
                    }
                }
                is GPUFrameStep.CopyDestinationStep -> {
                    if (step.sourceKey.deviceGeneration != context.deviceGeneration ||
                        step.sourceKey.targetGeneration != context.targetGeneration
                    ) return diagnostic("stale.preflight.destination_key_generation", "Destination-copy key generation is stale.")
                    if (step.sourceKey.target.value != step.source.value || step.sourceKey.sourceIntermediate != null) {
                        return diagnostic(
                            "invalid.preflight.destination_key_source",
                            "Destination-copy snapshot key does not identify its direct source target.",
                        )
                    }
                }
                is GPUFrameStep.CopyAsDrawMaterializationStep -> {
                    if (step.sourceKey.deviceGeneration != context.deviceGeneration ||
                        step.sourceKey.targetGeneration != context.targetGeneration ||
                        step.capabilitySealHash != framePlan.capabilitySeal.sealHash
                    ) return diagnostic("stale.preflight.copy_as_draw_seal", "Copy-as-draw evidence is stale.")
                    if (step.sourceKey.sourceIntermediate != step.sourceIntermediate) {
                        return diagnostic(
                            "invalid.preflight.copy_as_draw_source",
                            "Copy-as-draw snapshot key does not identify its source intermediate.",
                        )
                    }
                }
                else -> Unit
            }
        }
        val preparationRefs = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }.map { it.resource }
        if (preparationRefs.distinct().size != preparationRefs.size) {
            return diagnostic("invalid.preflight.resource_preparation_duplicate", "Logical resources must be prepared exactly once.")
        }
        val readbackRequestIds = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .map { it.request.requestId }
        if (readbackRequestIds.distinct().size != readbackRequestIds.size) {
            return diagnostic(
                "invalid.preflight.readback_request_id_duplicate",
                "Readback request identities must be unique within one frame.",
            )
        }
        return null
    }

    private fun materializeRenderOperands(
        framePlan: GPUFramePlan,
        ownerScope: String,
    ): GPUResourceMaterializationDecision {
        val packets = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        if (packets.isEmpty()) {
            return GPUResourceMaterializationDecision.Materialized(resources = emptyList(), targetId = context.targetId)
        }
        return try {
            val operands = packets.flatMap { packet ->
                listOf(
                    operand(packet, "setRenderPipeline", GPUMaterializedCommandOperandKind.RenderPipeline, packet.renderPipelineKey!!.value, ownerScope),
                    operand(packet, "setBindGroup", GPUMaterializedCommandOperandKind.BindGroup, packet.bindingLayoutHash, ownerScope),
                )
            }
            resourceProvider.materializeCommandOperands(
                GPUCommandOperandMaterializationRequest(
                    targetId = context.targetId,
                    taskIds = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                        .flatMap { it.sourceTaskIds }.map { it.value }.distinct(),
                    resourcePlanLabels = operands.map { it.label },
                    operands = operands,
                ),
                GPUTargetPreparationContext(
                    targetId = context.targetId,
                    frameId = framePlan.frameId.value.toString(),
                    deviceGeneration = context.deviceGeneration.value,
                    budgetClass = "frame-preflight",
                ),
            )
        } catch (failure: Throwable) {
            GPUResourceMaterializationDecision.Refused(
                diagnostic = org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic(
                    code = "failed.preflight.command_operand_provider",
                    resourceLabel = ownerScope,
                    message = "Command operand provider failed: ${failure::class.simpleName}",
                    terminal = true,
                ),
            )
        }
    }

    private fun operand(
        packet: GPUDrawPacket,
        command: String,
        kind: GPUMaterializedCommandOperandKind,
        descriptor: String,
        ownerScope: String,
    ): GPUCommandOperandMaterializationPlan = GPUCommandOperandMaterializationPlan(
        packetId = packet.packetId.value,
        commandLabel = command,
        label = "$command.${packet.packetId.value}",
        kind = kind,
        descriptorHash = descriptor,
        deviceGeneration = context.deviceGeneration.value,
        ownerScope = ownerScope,
        requiredUsageLabels = emptySet(),
        availableUsageLabels = emptySet(),
        invalidationPolicy = "device-generation",
        evidenceFacts = mapOf("resourceGeneration" to packet.resourceGeneration.toString()),
    )

    private fun validateRenderOperands(
        framePlan: GPUFramePlan,
        materialized: GPUResourceMaterializationDecision.Materialized,
        ownerScope: String,
    ): GPUDiagnostic? {
        val packets = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        val bridge = materialized.operandBridge
        val expectedTasks = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { it.sourceTaskIds }.map { it.value }.distinct()
        val expectedLabels = packets.flatMap { packet ->
            listOf("setRenderPipeline.${packet.packetId.value}", "setBindGroup.${packet.packetId.value}")
        }
        if (materialized.targetId != context.targetId || materialized.taskIds != expectedTasks ||
            materialized.resourcePlanLabels != expectedLabels || bridge.size != packets.size * 2
        ) {
            return diagnostic("invalid.preflight.render_materialization_scope", "Render materialization scope is not an exact frame match.")
        }
        val leaseIds = materialized.resourceLeases.map { it.leaseId }
        if (leaseIds.distinct().size != leaseIds.size) {
            return diagnostic("invalid.preflight.command_lease_duplicate", "Command resource lease identities must be unique.")
        }
        if (materialized.resourceLeases.any {
                it.deviceGeneration != context.deviceGeneration.value ||
                    it.ownerScope != ownerScope ||
                    it.cacheResult !in setOf(
                        org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult.Create,
                        org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult.Reuse,
                    )
            }
        ) {
            return diagnostic(
                "invalid.preflight.command_lease_evidence",
                "Command resource leases must belong to the current preparation journal and device generation.",
            )
        }
        val bridgedOperands = bridge.map { it.operand }
        if (materialized.operandRefs.any { it !in bridgedOperands }) {
            return diagnostic(
                "invalid.preflight.command_operand_unbridged",
                "Every provider-owned command operand reference must be retained by an encoder bridge.",
            )
        }
        for (packet in packets) {
            val packetBridge = bridge.filter { it.packetId == packet.packetId.value }
            val pipelines = packetBridge.filter { it.commandLabel == "setRenderPipeline" && it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline }
            val bindGroups = packetBridge.filter { it.commandLabel == "setBindGroup" && it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup }
            if (pipelines.size != 1 || bindGroups.size != 1) {
                return diagnostic(
                    "invalid.preflight.render_operand_bijection",
                    "Every render packet requires exactly one pipeline and one bind group.",
                    mapOf("packet" to packet.packetId.value),
                )
            }
            if ((pipelines + bindGroups).any { it.operand.deviceGeneration != context.deviceGeneration.value }) {
                return diagnostic("stale.preflight.render_operand_generation", "A render operand device generation is stale.")
            }
            val expected = listOf(
                operand(packet, "setRenderPipeline", GPUMaterializedCommandOperandKind.RenderPipeline, packet.renderPipelineKey!!.value, ownerScope),
                operand(packet, "setBindGroup", GPUMaterializedCommandOperandKind.BindGroup, packet.bindingLayoutHash, ownerScope),
            )
            val actual = listOf(pipelines.single(), bindGroups.single())
            if (actual.zip(expected).any { (binding, plan) ->
                    binding.commandLabel != plan.commandLabel || binding.operand.label != plan.label ||
                        binding.operand.kind != plan.kind || binding.operand.descriptorHash != plan.descriptorHash ||
                        binding.operand.ownerScope != plan.ownerScope ||
                        binding.operand.usageLabels.toSet() != plan.requiredUsageLabels ||
                        binding.operand.invalidationPolicy != plan.invalidationPolicy ||
                        binding.operand.evidenceFacts != plan.evidenceFacts
                }
            ) {
                return diagnostic("invalid.preflight.render_operand_evidence", "Render operand evidence differs from its request.")
            }
        }
        return null
    }

    private fun lowerEncoderScopes(
        framePlan: GPUFramePlan,
        materialized: GPUResourceMaterializationDecision.Materialized,
        generations: Map<GPUFrameResourceRef, Long>,
    ): List<GPUCommandEncoderScopePlan> = framePlan.steps.mapIndexedNotNull { index, step ->
        val labels = referencedResources(step).map { ref ->
            "${ref::class.simpleName}:${ref.value}@${requireNotNull(generations[ref]) { "generation missing for ${ref.value}" }}"
        }
        when (step) {
            is GPUFrameStep.RenderPassStep -> {
                val submissionCompleteLeaseIds = materialized.resourceLeases
                    .filter { it.releasePolicy == "submission-complete" }
                    .map { it.leaseId }
                val passPlan = GPUPassBatchPlan(
                    streamId = "frame.${framePlan.frameId.value}.step.$index",
                    passId = "frame.${framePlan.frameId.value}.render.$index",
                    batches = step.batches.map { batch ->
                        GPUPassBatch(
                            batchId = batch.batchId,
                            packets = batch.packets,
                            kind = batch.kind,
                            targetStateHash = batch.packets.first().targetStateHash,
                            queueGuard = GPUPassBatchQueueGuard(
                                requiredRetainedRefs = submissionCompleteLeaseIds,
                                retainedRefs = submissionCompleteLeaseIds,
                            ),
                        )
                    },
                    cuts = emptyList(),
                    diagnostics = emptyList(),
                    inputPacketCount = step.drawPackets.size,
                )
                val packetIds = step.drawPackets.map { it.packetId.value }.toSet()
                val stepBridge = materialized.operandBridge.filter { it.packetId in packetIds }
                val stepMaterialized = GPUResourceMaterializationDecision.Materialized(
                    resources = emptyList(),
                    targetId = context.targetId,
                    taskIds = step.sourceTaskIds.map { it.value },
                    operandBridge = stepBridge,
                    resourceLeases = materialized.resourceLeases,
                )
                val stream = GPUPassCommandStream.fromBatchPlan(
                    streamId = "frame.${framePlan.frameId.value}.commands.$index",
                    batchPlan = passPlan,
                    loadStoreLabel = step.loadStore.dumpLabel(),
                    materialization = stepMaterialized,
                )
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = index,
                    operationKind = GPUEncoderOperationKind.Render,
                    sourceTaskIds = step.sourceTaskIds,
                    sourcePacketIds = step.drawPackets.map { it.packetId },
                    facadeOperationClasses = stream.commandLabels,
                    targetGeneration = context.targetGeneration,
                    resourceGenerationLabels = labels,
                    passCommandStream = stream,
                )
            }
            is GPUFrameStep.ComputePassStep -> scope(index, GPUEncoderOperationKind.Compute, step.sourceTaskIds, listOf("beginComputePass") + List(step.dispatches.size) { "dispatchWorkgroups" } + "endComputePass", labels)
            is GPUFrameStep.UploadResourceStep -> scope(index, GPUEncoderOperationKind.Upload, step.sourceTaskIds, listOf("writeBufferOrCopyBuffer"), labels)
            is GPUFrameStep.CopyResourceStep -> scope(index, GPUEncoderOperationKind.Copy, step.sourceTaskIds, List(step.regions.size) { "copyResource" }, labels)
            is GPUFrameStep.CopyDestinationStep -> scope(index, GPUEncoderOperationKind.CopyDestination, step.sourceTaskIds, listOf("copyTextureToTexture"), labels)
            is GPUFrameStep.CopyAsDrawMaterializationStep -> scope(index, GPUEncoderOperationKind.CopyAsDraw, step.sourceTaskIds, listOf("beginRenderPass", "copyAsDraw", "endRenderPass"), labels)
            is GPUFrameStep.ReadbackCopyStep -> scope(index, GPUEncoderOperationKind.Readback, step.sourceTaskIds, listOf("copyTextureToBuffer"), labels)
            is GPUFrameStep.SurfaceBlitRenderPassStep -> scope(index, GPUEncoderOperationKind.SurfaceBlit, step.sourceTaskIds, listOf("beginRenderPass", "surfaceBlit", "endRenderPass"), labels)
            else -> null
        }
    }

    private fun scope(
        index: Int,
        kind: GPUEncoderOperationKind,
        tasks: List<org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID>,
        operations: List<String>,
        resources: List<String>,
    ) = GPUCommandEncoderScopePlan(index, kind, sourceTaskIds = tasks, facadeOperationClasses = operations, targetGeneration = context.targetGeneration, resourceGenerationLabels = resources)

    private fun validatePreparedResource(
        preparation: GPUResourcePreparationRequest,
        generation: Long,
        decision: GPUFrameResourcePreparationDecision.Prepared,
    ): GPUDiagnostic? = when {
        decision.logicalResource != preparation.resource -> diagnostic("invalid.preflight.resource_identity", "Provider returned a different logical resource.")
        decision.role != preparation.role -> diagnostic("invalid.preflight.resource_role", "Provider returned a different resource role.")
        decision.deviceGeneration != context.deviceGeneration -> diagnostic("stale.preflight.resource_device_generation", "Prepared resource device generation is stale.")
        decision.resourceGeneration != generation -> diagnostic("stale.preflight.resource_generation", "Prepared resource generation is stale.")
        preparation.resource is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef && decision.concreteResource !is GPUPreparedConcreteResourceRef.Buffer -> diagnostic("invalid.preflight.resource_kind", "Buffer preparation returned a texture reference.")
        preparation.resource !is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef && decision.concreteResource !is GPUPreparedConcreteResourceRef.Texture -> diagnostic("invalid.preflight.resource_kind", "Texture preparation returned a buffer reference.")
        else -> null
    }

    private fun refuseWithRollback(
        ownerScope: String,
        acquiredAnyResource: Boolean,
        diagnostic: GPUDiagnostic,
    ): GPUFramePreflightResult.Refused {
        if (!acquiredAnyResource) return GPUFramePreflightResult.Refused(diagnostic)
        val rollback = GPUFrameRollback(ownerScope, resourceProvider, surfaceProvider, null).execute()
        return GPUFramePreflightResult.Refused(diagnostic, rollback)
    }
}

private fun GPUCapabilities.preflightSnapshot(): GPUCapabilities = copy(
    facts = immutableList(facts),
    knownUnsupportedFacts = immutableList(knownUnsupportedFacts),
    supportedTextureFormats = immutableSet(supportedTextureFormats),
    rendererFeatures = immutableSet(rendererFeatures),
)

private fun GPULoadStorePlan.dumpLabel(): String =
    "$loadOp:${storePlan.name}:${clearColorLabel ?: "none"}"

private fun GPUFrameStep.preparedLane(): GPUPreparedStepLane = when (executionKind) {
    org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Preflight ->
        if (this is GPUFrameStep.AcquireSurfaceOutput) GPUPreparedStepLane.HostAction else GPUPreparedStepLane.ResourcePreflight
    org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Encoder -> GPUPreparedStepLane.Encoder
    org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.DependencyOnly -> GPUPreparedStepLane.Dependency
    org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.PostSubmitHost -> GPUPreparedStepLane.HostAction
    org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.RefusalEvidence -> GPUPreparedStepLane.RefusalEvidence
}

private fun surfaceDiagnostic(status: GPUSurfaceAcquisitionStatus): GPUDiagnostic = when (status) {
    GPUSurfaceAcquisitionStatus.Lost, GPUSurfaceAcquisitionStatus.Outdated -> diagnostic(
        "unsupported.preflight.surface_reconfigure",
        "Surface must be reconfigured before retry.",
        mapOf("status" to status.name, "recovery" to "reconfigure"),
    )
    GPUSurfaceAcquisitionStatus.Timeout -> diagnostic(
        "unsupported.preflight.surface_timeout",
        "Surface acquisition timed out; retry without submit.",
        mapOf("status" to status.name, "recovery" to "retry"),
    )
    GPUSurfaceAcquisitionStatus.OutOfMemory, GPUSurfaceAcquisitionStatus.DeviceLost -> diagnostic(
        "failed.preflight.surface_terminal",
        "Surface acquisition failed terminally.",
        mapOf("status" to status.name, "recovery" to "terminal"),
    )
}

private fun diagnostic(code: String, message: String, facts: Map<String, String> = emptyMap()): GPUDiagnostic =
    preflightDiagnostic(code, message, facts)

private fun referencedResources(framePlan: GPUFramePlan): Set<GPUFrameResourceRef> =
    framePlan.steps.flatMap(::referencedResources).toSet()

private fun referencedResources(step: GPUFrameStep): List<GPUFrameResourceRef> = when (step) {
    is GPUFrameStep.RenderPassStep -> listOf(step.target)
    is GPUFrameStep.ComputePassStep -> listOf(step.target) + step.resourceUses.map { it.resource }
    is GPUFrameStep.PrepareResourcesStep -> emptyList()
    is GPUFrameStep.UploadResourceStep -> listOf(step.staging, step.destination)
    is GPUFrameStep.CopyResourceStep -> listOf(step.source, step.destination)
    is GPUFrameStep.DependencyBarrierStep -> emptyList()
    is GPUFrameStep.CopyDestinationStep -> listOf(step.source, step.snapshot)
    is GPUFrameStep.CopyAsDrawMaterializationStep -> listOf(step.source, step.snapshot)
    is GPUFrameStep.TargetTransitionStep -> listOf(step.parent, step.child)
    is GPUFrameStep.ReadbackCopyStep -> listOf(step.source, step.staging)
    is GPUFrameStep.AcquireSurfaceOutput -> emptyList()
    is GPUFrameStep.SurfaceBlitRenderPassStep -> listOf(step.scene)
    is GPUFrameStep.PostSubmitPresentAction -> emptyList()
    is GPUFrameStep.RefusedLeafDrawStep -> emptyList()
    is GPUFrameStep.RefusedCompositeCommandStep -> emptyList()
}

private fun lastUseExclusive(framePlan: GPUFramePlan, resource: GPUFrameResourceRef, preparationStep: Int): Int {
    val last = framePlan.steps.indices.lastOrNull { resource in referencedResources(framePlan.steps[it]) } ?: preparationStep
    return maxOf(preparationStep + 1, last + 1)
}

private fun firstUnsafePreparedIdentity(framePlan: GPUFramePlan): String? {
    framePlan.steps.forEach { step ->
        if (step.sourceTaskIds.any { !it.value.isExecutionDumpSafe() }) return "sourceTaskId"
        if (referencedResources(step).any { !it.value.isExecutionDumpSafe() }) return "resourceRef"
        when (step) {
            is GPUFrameStep.PrepareResourcesStep -> if (
                step.requests.any { !it.resource.value.isExecutionDumpSafe() }
            ) return "preparedResourceRef"
            is GPUFrameStep.RenderPassStep -> {
                if (step.drawPackets.any { !it.packetId.value.isExecutionDumpSafe() }) return "packetId"
                if (step.drawPackets.any { !it.passId.isExecutionDumpSafe() }) return "sourcePassId"
                if (step.drawPackets.any { it.renderPipelineKey?.value?.isExecutionDumpSafe() == false }) {
                    return "renderPipelineKey"
                }
                if (step.drawPackets.any { !it.bindingLayoutHash.isExecutionDumpSafe() }) return "bindingLayoutHash"
                if (step.drawPackets.any { !it.vertexSourceLabel.isExecutionDumpSafe() }) return "vertexSourceLabel"
                if (step.drawPackets.any { !it.targetStateHash.isExecutionDumpSafe() }) return "targetStateHash"
                if (step.drawPackets.any { it.scissorBoundsHash?.isExecutionDumpSafe() == false }) {
                    return "scissorBoundsHash"
                }
                if (!step.loadStore.loadOp.isExecutionDumpSafe()) return "loadOp"
                if (step.loadStore.clearColorLabel?.isExecutionDumpSafe() == false) return "clearColorLabel"
            }
            is GPUFrameStep.DependencyBarrierStep ->
                if (!step.reasonCode.isExecutionDumpSafe()) return "dependencyReasonCode"
            is GPUFrameStep.AcquireSurfaceOutput ->
                if (!step.descriptor.output.value.isExecutionDumpSafe()) return "surfaceOutput"
            is GPUFrameStep.SurfaceBlitRenderPassStep ->
                if (!step.output.value.isExecutionDumpSafe()) return "surfaceOutput"
            is GPUFrameStep.PostSubmitPresentAction ->
                if (!step.output.value.isExecutionDumpSafe()) return "surfaceOutput"
            is GPUFrameStep.ReadbackCopyStep ->
                if (!step.request.requestId.value.isExecutionDumpSafe()) return "readbackRequestId"
            else -> Unit
        }
    }
    return null
}
