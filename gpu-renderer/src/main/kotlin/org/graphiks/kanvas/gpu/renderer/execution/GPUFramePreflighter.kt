package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationSequenceRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectStage
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectRenderStepId
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

private const val solidRectRenderStepIdentity = "rect.fill.coverage"

/** Sole transactional join between an immutable semantic frame and materialized resource facts. */
internal class GPUFramePreflighter(
    private val context: GPUFramePreflightContext,
    capabilities: GPUCapabilities,
    private val resourceProvider: GPUFrameResourcePreflightProvider,
    private val completionProvider: GPUQueueCompletionProvider,
    private val surfaceProvider: GPUSurfaceOutputProvider,
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
    private val nativeBoundary: GPUPreparedNativeFrameBoundary? = null,
) {
    private val capabilities: GPUCapabilities = capabilities.preflightSnapshot()

    fun preflight(framePlan: GPUFramePlan): GPUFramePreflightResult {
        pureValidation(framePlan)?.let { return GPUFramePreflightResult.Refused(it) }
        if (nativeBoundary != null && nativeBoundary.resourceProvider !== resourceProvider) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.native_payload_provider_mismatch",
                    "Native payload boundary does not own the exact resource provider used by preflight.",
                ),
            )
        }

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
        val rollback = GPUFrameRollback(
            ownerScope = ownerScope,
            resourceProvider = resourceProvider,
            surfaceProvider = surfaceProvider,
            completionProvider = completionProvider,
        )

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
                        rollback,
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
                        rollback,
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
                        rollback,
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
                        return refuseWithRollback(rollback, acquiredAnyResource, decision.diagnostic)
                    is GPUFrameResourcePreparationDecision.Prepared -> {
                        validatePreparedResource(preparation, generation, decision)?.let { invalid ->
                            return refuseWithRollback(rollback, true, invalid)
                        }
                        preparedGenerationMap[preparation.resource] = decision.resourceGeneration
                        if (preparation.role == GPUFrameResourceRole.ReadbackStaging) {
                            val layout = readback
                                ?: return refuseWithRollback(
                                    rollback,
                                    true,
                                    diagnostic(
                                        "invalid.preflight.readback_layout_missing",
                                        "Readback staging was prepared without a matching logical request.",
                                    ),
                                )
                            val lease = decision.outputOwnedReadbackLease
                                ?: return refuseWithRollback(
                                    rollback,
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
                                    rollback,
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
                                textureAllocation = decision.textureAllocation,
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
                rollback,
                acquiredAnyResource,
                diagnostic(
                    renderMaterialization.diagnostic.code,
                    renderMaterialization.diagnostic.message,
                    mapOf("resource" to renderMaterialization.diagnostic.resourceLabel),
                ),
            )
            is GPUResourceMaterializationDecision.Deferred -> return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(renderMaterialization.reasonCode, "Render operand materialization was deferred."),
            )
        }
        materialized.diagnostics.firstOrNull { it.terminal }?.let { terminal ->
            return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(
                    terminal.code,
                    terminal.message,
                    terminal.facts + mapOf("resource" to terminal.resourceLabel),
                ),
            )
        }
        validateRenderOperands(framePlan, materialized, ownerScope)?.let { invalid ->
            return refuseWithRollback(rollback, acquiredAnyResource, invalid)
        }

        val encoderScopes = try {
            lowerEncoderScopes(framePlan, materialized, preparedGenerationMap)
        } catch (failure: Throwable) {
            return refuseWithRollback(
                rollback,
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
                rollback,
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
                rollback,
                true,
                diagnostic(
                    "invalid.preflight.prepared_evidence",
                    "Prepared resource and generation evidence failed before late surface acquisition.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }

        var nativeDraft: GPUPreparedNativeFrameDraft? = null
        var nativeOwnership: GPUPreparedNativeFrameOwnership? = null
        nativeBoundary?.let { boundary ->
            val materialization = try {
                boundary.materializeReusable(framePlan, encoderPlan, resources, generationSeal)
            } catch (failure: Throwable) {
                return refuseWithRollback(
                    rollback,
                    true,
                    diagnostic(
                        "failed.preflight.native_payload_materialization",
                        "Reusable native payload materialization failed without a typed result.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    ),
                )
            }
            nativeDraft = when (materialization) {
                is GPUPreparedNativeFramePayloadMaterialization.Materialized -> materialization.draft
                is GPUPreparedNativeFramePayloadMaterialization.Refused -> {
                    materialization.retainedDraft?.let(boundary::terminalizeCallerRetainedDraft)
                    return refuseWithRollback(
                        rollback,
                        true,
                        diagnostic(materialization.code, materialization.message),
                    )
                }
            }
            validateNativeRenderSemanticPayloads(framePlan, requireNotNull(nativeDraft))?.let { invalid ->
                boundary.terminalizeCallerRetainedDraft(requireNotNull(nativeDraft))
                return refuseWithRollback(rollback, true, invalid)
            }
            val registration = try {
                boundary.register(requireNotNull(nativeDraft))
            } catch (failure: Throwable) {
                boundary.terminalizeCallerRetainedDraft(requireNotNull(nativeDraft))
                return refuseWithRollback(
                    rollback,
                    true,
                    diagnostic(
                        "failed.preflight.native_payload_registration",
                        "Native payload registration failed without a typed result.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    ),
                )
            }
            when (registration) {
                is GPUPreparedNativeFrameRegistration.Registered -> {
                    nativeOwnership = registration.ownership
                    if (!rollback.adoptNativePayload(registration.ownership)) {
                        registration.ownership.rollback()
                        return refuseWithRollback(
                            rollback,
                            true,
                            diagnostic(
                                "failed.preflight.native_payload_ownership",
                                "Native payload ownership could not be transferred to rollback.",
                            ),
                        )
                    }
                }
                is GPUPreparedNativeFrameRegistration.Refused -> {
                    if (registration.ownership ==
                        GPUPreparedNativeFrameRegistration.RefusalOwnership.CallerRetained
                    ) {
                        boundary.terminalizeCallerRetainedDraft(requireNotNull(nativeDraft))
                    }
                    return refuseWithRollback(
                        rollback,
                        true,
                        diagnostic(registration.code, "Native payload registry refused the reusable draft."),
                    )
                }
            }
        }

        val ticketReservation = try {
            completionProvider.reserveTicket(
                GPUQueueCompletionTicketRequest(framePlan.frameId, context.deviceGeneration),
            )
        } catch (failure: Throwable) {
            return refuseWithRollback(
                rollback,
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
                rollback,
                acquiredAnyResource,
                diagnostic("unsupported.preflight.completion_ticket_missing", "Queue completion proof is missing."),
            )
            is GPUQueueCompletionTicketReservation.Failed -> return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                reservation.diagnostic,
            )
            is GPUQueueCompletionTicketReservation.Duplicate -> return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(
                    "unsupported.preflight.completion_ticket_duplicate",
                    "Queue completion ticket identity was already reserved.",
                    mapOf("ticketId" to reservation.ticketId.value),
                ),
            )
        }
        if (!rollback.adoptCompletionTicket(ticket)) {
            completionProvider.abandonReservedTicket(ticket)
            return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(
                    "failed.preflight.completion_ticket_ownership",
                    "Reserved completion ticket could not be transferred to rollback.",
                ),
            )
        }
        if (ticket.frameId != framePlan.frameId || ticket.deviceGeneration != context.deviceGeneration) {
            return refuseWithRollback(
                rollback,
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
                    rollback,
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
                    rollback,
                    acquiredAnyResource,
                    gpuSurfaceAcquisitionDiagnostic(acquisition.status),
                )
            }
        }
        if (acquiredSurface != null && !rollback.adoptSurface(acquiredSurface)) {
            runCatching { surfaceProvider.release(acquiredSurface) }
            return refuseWithRollback(
                rollback,
                true,
                diagnostic(
                    "failed.preflight.surface_ownership",
                    "Acquired surface could not be transferred to rollback ownership.",
                ),
            )
        }
        if (acquiredSurface != null &&
            (acquiredSurface.output != acquireStep?.descriptor?.output ||
                acquiredSurface.deviceGeneration != context.deviceGeneration ||
                acquiredSurface.targetGeneration != acquireStep.descriptor.targetGeneration)
        ) {
            return refuseWithRollback(
                rollback,
                true,
                diagnostic("stale.preflight.surface_generation", "Acquired surface output generation is stale."),
            )
        }

        if (nativeBoundary != null) {
            val binding = try {
                nativeBoundary.bindLateSurface(
                    requireNotNull(nativeOwnership),
                    requireNotNull(nativeDraft),
                    acquiredSurface,
                )
            } catch (failure: Throwable) {
                return refuseWithRollback(
                    rollback,
                    true,
                    diagnostic(
                        "failed.preflight.native_payload_surface_binding",
                        "Late native surface binding failed without a typed result.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    ),
                )
            }
            if (binding is GPUPreparedNativeFrameBindingResult.Refused) {
                return refuseWithRollback(
                    rollback,
                    true,
                    diagnostic(binding.code, binding.message),
                )
            }
        }

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
        validateMsaaContinuation(framePlan)?.let { return it }

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
            if (acquires.single().descriptor.targetGeneration != context.surfaceGeneration) {
                return diagnostic("stale.preflight.surface_generation", "Surface descriptor generation is stale.")
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
                    if (step.drawPackets.any { packet ->
                            val colorGlyph = packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph
                            val preparedLateBound =
                                packet.semanticPayload is GPUDrawSemanticPayload.SolidRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.RegisteredUniformRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.SeparableBlurRect
                            val acceptedGeneration = when {
                                colorGlyph != null -> colorGlyph.planArtifactKey.generation.value.toLong()
                                preparedLateBound &&
                                    packet.resourceGeneration == PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ->
                                    PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
                                else -> expected
                            }
                            packet.resourceGeneration != acceptedGeneration
                        }
                    ) {
                        return diagnostic("stale.preflight.resource_generation", "A render packet resource generation is stale.")
                    }
                    if (step.drawPackets.any { it.renderPipelineKey == null }) {
                        return diagnostic("invalid.preflight.render_pipeline_key_missing", "Render packets require a pipeline key before materialization.")
                    }
                    step.drawPackets.forEach { packet ->
                        validateSemanticPayload(framePlan, step, packet)?.let { return it }
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

    private fun validateMsaaContinuation(framePlan: GPUFramePlan): GPUDiagnostic? {
        val renderSteps = framePlan.steps.withIndex()
            .filter { it.value is GPUFrameStep.RenderPassStep }
            .map { indexed -> indexed.index to indexed.value as GPUFrameStep.RenderPassStep }

        framePlan.steps.forEach { step ->
            val consumers = when (step) {
                is GPUFrameStep.CopyDestinationStep -> step.consumers
                is GPUFrameStep.CopyAsDrawMaterializationStep -> step.consumers
                else -> return@forEach
            }
            if (consumers.isEmpty()) return@forEach
            val consumerUsesMsaa = consumers.any { consumer ->
                renderSteps.any { (_, render) ->
                    render.sourceTaskIds.contains(consumer.renderTaskId) &&
                        render.drawPackets.any { it.packetId == consumer.packetId } &&
                        render.samplePlan is GPUSamplePlan.MultisampleFrame
                }
            }
            if (consumerUsesMsaa) {
                return diagnostic(
                    "unsupported.blend.msaa_destination_read_exactness",
                    "Destination-reading draws require a typed exact single-sample geometry and clip lowering before MSAA may be removed.",
                )
            }
        }

        val msaaTargets = renderSteps
            .filter { (_, render) -> render.samplePlan is GPUSamplePlan.MultisampleFrame }
            .groupBy { (_, render) -> render.target }
        msaaTargets.forEach { (target, indexedRenders) ->
            val firstIndex = indexedRenders.minOf { it.first }
            val lastIndex = indexedRenders.maxOf { it.first }
            for (index in firstIndex..lastIndex) {
                when (val step = framePlan.steps[index]) {
                    is GPUFrameStep.RenderPassStep -> if (
                        step.target == target && step.samplePlan !is GPUSamplePlan.MultisampleFrame
                    ) {
                        return diagnostic(
                            "unsupported.msaa.continuation_canonical_write",
                            "A direct canonical-target write invalidates retained MSAA attachment authority.",
                        )
                    }
                    is GPUFrameStep.CopyResourceStep -> if (step.destination == target) {
                        return diagnostic(
                            "unsupported.msaa.continuation_canonical_write",
                            "A copy into the canonical target invalidates retained MSAA attachment authority.",
                        )
                    }
                    is GPUFrameStep.UploadResourceStep -> if (step.destination == target) {
                        return diagnostic(
                            "unsupported.msaa.continuation_canonical_write",
                            "An upload into the canonical target invalidates retained MSAA attachment authority.",
                        )
                    }
                    is GPUFrameStep.ComputePassStep -> if (
                        step.target == target || step.resourceUses.any { use ->
                            use.write && use.resource == target
                        }
                    ) {
                        return diagnostic(
                            "unsupported.msaa.continuation_canonical_write",
                            "Compute writes cannot preserve MSAA attachment authority in this slice.",
                        )
                    }
                    else -> Unit
                }
            }

            val requests = indexedRenders.map { (_, render) ->
                val request = render.sampleContinuation ?: return diagnostic(
                    "unsupported.msaa.continuation_attachment_not_stored",
                    "Every MSAA render segment requires an explicit retained attachment proof.",
                )
                if (request.key.target.value != target.value) {
                    return diagnostic(
                        "unsupported.msaa.continuation_target_identity",
                        "The MSAA continuation target does not match the render target.",
                    )
                }
                if (request.key.deviceGeneration != context.deviceGeneration) {
                    return diagnostic(
                        "unsupported.msaa.continuation_device_generation",
                        "The MSAA continuation device generation is stale.",
                    )
                }
                if (request.key.targetGeneration != context.targetGeneration) {
                    return diagnostic(
                        "unsupported.msaa.continuation_target_generation",
                        "The MSAA continuation target generation is stale.",
                    )
                }
                if (request.key.samplePlan != render.samplePlan) {
                    return diagnostic(
                        "unsupported.msaa.continuation_sample_plan",
                        "The MSAA continuation sample plan does not match the render pass.",
                    )
                }
                if (request.key.depthStencilAttachment != null) {
                    return diagnostic(
                        "unsupported.msaa.continuation_depth_stencil_unavailable",
                        "The first native MSAA continuation slice is color-only; depth/stencil authority is unproven.",
                    )
                }
                val expectedLoad = when (render.loadStore.loadOp) {
                    "clear" -> GPUSampleLoadTransition.FreshClear
                    "load" -> GPUSampleLoadTransition.RetainedLoad
                    else -> return diagnostic(
                        "unsupported.msaa.continuation_load_operation",
                        "The MSAA continuation load operation is unsupported.",
                    )
                }
                if (request.loadTransition != expectedLoad) {
                    return diagnostic(
                        "unsupported.msaa.continuation_load_operation",
                        "The MSAA continuation transition does not match the render load operation.",
                    )
                }
                if (request.storeAction != GPUSampleStoreAction.Store ||
                    request.resolveAction != GPUSampleResolveAction.ResolveCanonical
                ) {
                    return diagnostic(
                        "unsupported.msaa.continuation_store_resolve",
                        "Every producing MSAA pass must store the retained attachment and resolve the canonical target.",
                    )
                }
                if (render.loadStore.storePlan != GPUStorePlan.Store) {
                    return diagnostic(
                        "unsupported.msaa.continuation_store_operation",
                        "A stored MSAA continuation requires the render pass to declare Store.",
                    )
                }
                request
            }
            if (requests.first().loadTransition != GPUSampleLoadTransition.FreshClear) {
                return diagnostic(
                    "unsupported.msaa.continuation_retained_target_unavailable",
                    "Inter-frame retained-target MSAA loading is not promoted by the frame-local color-only slice.",
                )
            }
            when (
                val planned = GPUSampleContinuationPlanner().plan(
                    GPUSampleContinuationSequenceRequest(requests),
                )
            ) {
                is GPUSampleContinuationResult.Accepted -> Unit
                is GPUSampleContinuationResult.Refused -> return planned.diagnostic
            }
        }
        return null
    }

    private fun validateSemanticPayload(
        framePlan: GPUFramePlan,
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
    ): GPUDiagnostic? {
        val semantic = packet.semanticPayload
        if (semantic == null) {
            return when (packet.renderStepId.value) {
                solidRectRenderStepIdentity -> diagnostic(
                    "invalid.preflight.solid_semantic_payload_missing",
                    "Executable solid FillRect packets require their gathered semantic payload.",
                )
                COLOR_GLYPH_RENDER_STEP_IDENTITY -> diagnostic(
                    "invalid.preflight.color_glyph_semantic_payload_missing",
                    "Executable color-glyph packets require their gathered typed semantic payload.",
                )
                REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY -> diagnostic(
                    "invalid.preflight.registered_uniform_semantic_payload_missing",
                    "Executable registered uniform packets require their typed semantic payload.",
                )
                else -> null
            }
        }
        return when (semantic) {
            is GPUDrawSemanticPayload.SolidRect -> validateSolidRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.RegisteredUniformRect ->
                validateRegisteredUniformRectSemanticPayload(render, packet, semantic)
            is GPUDrawSemanticPayload.SeparableBlurRect ->
                validateSeparableBlurRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.ColorGlyph ->
                validateColorGlyphSemanticPayload(framePlan, render, packet, semantic)
        }
    }

    private fun validateSeparableBlurRectSemanticPayload(
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.SeparableBlurRect,
    ): GPUDiagnostic? {
        fun refuse(code: String, message: String) = diagnostic(code, message)
        val stage = GPUSeparableBlurRectStage.entries.singleOrNull {
            packet.renderStepId.value == separableBlurRectRenderStepId(it)
        } ?: return refuse(
            "invalid.preflight.separable_blur_semantic_route",
            "Separable blur packets require one closed source, horizontal, or vertical stage.",
        )
        val expectedScissor = when (stage) {
            GPUSeparableBlurRectStage.Source -> semantic.sourceBounds
            GPUSeparableBlurRectStage.Horizontal,
            GPUSeparableBlurRectStage.Vertical,
            -> semantic.targetBounds
        }
        val expectedLayout = when (stage) {
            GPUSeparableBlurRectStage.Source -> SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
            GPUSeparableBlurRectStage.Horizontal,
            GPUSeparableBlurRectStage.Vertical,
            -> SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
        }
        if (!semantic.hasCanonicalHashIntegrity() ||
            packet.commandIdValue != semantic.payloadRef.commandIdValue ||
            packet.uniformSlot != semantic.payloadRef.uniformSlot ||
            packet.bindingLayoutHash != expectedLayout ||
            packet.vertexSourceLabel != SEPARABLE_BLUR_VERTEX_SOURCE_LABEL ||
            packet.targetStateHash != SEPARABLE_BLUR_TARGET_STATE_HASH ||
            packet.scissorBoundsHash != separableBlurRectScissorAuthority(expectedScissor) ||
            !packet.blendPlan.isCanonicalSolidRectSrcOver()
        ) {
            return refuse(
                "invalid.preflight.separable_blur_semantic_integrity",
                "Separable blur packet authority contradicts its immutable semantic input.",
            )
        }
        return null
    }

    private fun validateRegisteredUniformRectSemanticPayload(
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.RegisteredUniformRect,
    ): GPUDiagnostic? {
        fun refuse(code: String, message: String) = diagnostic(code, message)
        val ref = semantic.payloadRef
        if (packet.renderStepId.value != REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY ||
            ref.renderStepIdentity != REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY
        ) {
            return refuse(
                "invalid.preflight.registered_uniform_semantic_route",
                "Registered uniform payloads require the exact canonical render step.",
            )
        }
        if (ref.commandIdValue != packet.commandIdValue || packet.uniformSlot != ref.uniformSlot) {
            return refuse(
                "invalid.preflight.registered_uniform_packet_identity",
                "Registered uniform command or slot identity differs from its packet.",
            )
        }
        if (!semantic.hasCanonicalHashIntegrity()) {
            return refuse(
                "invalid.preflight.registered_uniform_canonical_hash",
                "Registered uniform bytes do not match their immutable hash and ABI evidence.",
            )
        }
        if (packet.renderPipelineKey != registeredUniformRectPipelineKey(semantic.program) ||
            packet.bindingLayoutHash != REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH ||
            packet.vertexSourceLabel != REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL ||
            packet.targetStateHash != REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH ||
            packet.scissorBoundsHash != registeredUniformRectScissorAuthority(semantic.scissorBounds)
        ) {
            return refuse(
                "invalid.preflight.registered_uniform_packet_authority",
                "Registered uniform pipeline, binding, vertex, target, or scissor authority differs.",
            )
        }
        if (!packet.blendPlan.isCanonicalSolidRectSrcOver()) {
            return refuse(
                "unsupported.preflight.registered_uniform_blend",
                "Registered uniform rectangles require canonical premultiplied SrcOver.",
            )
        }
        if (render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
            render.loadStore.loadOp != "clear" || render.loadStore.storePlan != GPUStorePlan.Store
        ) {
            return refuse(
                "unsupported.preflight.registered_uniform_pass_state",
                "Registered uniform rectangles require one single-sample clear-and-store pass.",
            )
        }
        if (semantic.targetBounds.left != 0 || semantic.targetBounds.top != 0 ||
            semantic.scissorBounds.left < semantic.targetBounds.left ||
            semantic.scissorBounds.top < semantic.targetBounds.top ||
            semantic.scissorBounds.right > semantic.targetBounds.right ||
            semantic.scissorBounds.bottom > semantic.targetBounds.bottom
        ) {
            return refuse(
                "invalid.preflight.registered_uniform_bounds",
                "Registered uniform scissor must be contained by a zero-origin target.",
            )
        }
        return null
    }

    private fun validateColorGlyphSemanticPayload(
        framePlan: GPUFramePlan,
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.ColorGlyph,
    ): GPUDiagnostic? {
        fun refuse(code: String, message: String) = diagnostic(code, message)
        if (packet.renderStepId.value != COLOR_GLYPH_RENDER_STEP_IDENTITY ||
            semantic.payloadRef.renderStepIdentity != COLOR_GLYPH_RENDER_STEP_IDENTITY
        ) return refuse("invalid.preflight.color_glyph_semantic_route", "ColorGlyph requires the exact canonical render step.")
        if (semantic.payloadRef.commandIdValue != packet.commandIdValue) {
            return refuse("invalid.preflight.color_glyph_semantic_command_mismatch", "ColorGlyph command identity differs from its packet.")
        }
        if (packet.uniformSlot != semantic.payloadRef.uniformSlot) {
            return refuse("invalid.preflight.color_glyph_semantic_packet_slot_mismatch", "ColorGlyph uniform slot differs from its packet evidence.")
        }
        if (!semantic.hasCanonicalHashIntegrity()) {
            return refuse("invalid.preflight.color_glyph_canonical_hash_mismatch", "ColorGlyph canonical hash does not match its immutable payload fields.")
        }
        if (packet.renderPipelineKey != COLOR_GLYPH_RENDER_PIPELINE_KEY ||
            packet.bindingLayoutHash != COLOR_GLYPH_BINDING_LAYOUT_HASH ||
            packet.vertexSourceLabel != COLOR_GLYPH_VERTEX_SOURCE_LABEL ||
            packet.targetStateHash != COLOR_GLYPH_TARGET_STATE_HASH ||
            packet.scissorBoundsHash != colorGlyphScissorAuthority(semantic.scissorBounds) ||
            render.loadStore.loadOp != "clear" ||
            render.loadStore.storePlan != GPUStorePlan.Store ||
            render.loadStore.clearColorLabel != "opaque-black"
        ) {
            return refuse(
                "invalid.preflight.color_glyph_packet_authority",
                "ColorGlyph packet and pass state must match the exact prepared native route authority.",
            )
        }
        if (semantic.planArtifactKey.generation.value < 0 ||
            semantic.atlasArtifactKey.generation.value.toLong() != semantic.atlasGeneration
        ) return refuse("stale.preflight.color_glyph_artifact_generation", "ColorGlyph plan or atlas generation evidence is inconsistent.")
        if (render.samplePlan != GPUSamplePlan.SingleSampleFrame) {
            return refuse("unsupported.preflight.color_glyph_sample_plan", "ColorGlyph first slice requires exact single-sample rendering.")
        }
        val blend = packet.blendPlan as? GPUBlendPlan.FixedFunctionBlend
        if (blend == null || blend.mode != GPUBlendMode.SRC_OVER || blend.state.stateId != "one_isa" ||
            blend.state.color.sourceFactor != "one" || blend.state.color.destinationFactor != "one-minus-src-alpha" ||
            blend.state.alpha.sourceFactor != "one" || blend.state.alpha.destinationFactor != "one-minus-src-alpha" ||
            blend.state.color.operation != "add" || blend.state.alpha.operation != "add" ||
            blend.state.writeMask != "rgba"
        ) return refuse("unsupported.preflight.color_glyph_blend", "ColorGlyph requires canonical fixed-function SRC_OVER.")
        if (GPUTextureFormat.R8Unorm !in capabilities.supportedTextureFormats) {
            return refuse("unsupported.preflight.color_glyph_r8unorm", "ColorGlyph requires an explicitly observed r8unorm texture capability.")
        }
        if (semantic.scissorBounds.left < semantic.targetBounds.left ||
            semantic.scissorBounds.top < semantic.targetBounds.top ||
            semantic.scissorBounds.right > semantic.targetBounds.right ||
            semantic.scissorBounds.bottom > semantic.targetBounds.bottom
        ) return refuse("invalid.preflight.color_glyph_scissor_bounds", "ColorGlyph scissor must be contained by its target bounds.")

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().flatMap { it.requests }
        val readbacks = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val expectedResourceCount = if (readbacks.isEmpty()) 5 else 6
        if (preparations.size != expectedResourceCount) {
            return refuse("invalid.preflight.color_glyph_resource_count", "ColorGlyph requires exactly five resources, plus staging only when readback is requested.")
        }
        fun exact(role: GPUFrameResourceRole): GPUResourcePreparationRequest? =
            preparations.filter { it.role == role }.singleOrNull()
        val targets = exact(GPUFrameResourceRole.SceneTarget)
        val atlases = exact(GPUFrameResourceRole.GlyphAtlas)
        val vertices = exact(GPUFrameResourceRole.VertexData)
        val indices = exact(GPUFrameResourceRole.IndexData)
        val uniforms = exact(GPUFrameResourceRole.UniformData)
        val stagings = exact(GPUFrameResourceRole.ReadbackStaging)
        if (listOf(targets, atlases, vertices, indices, uniforms).any { it == null } ||
            (readbacks.isEmpty() != (stagings == null))
        ) {
            return refuse("invalid.preflight.color_glyph_resource_roles", "ColorGlyph requires one target, atlas, vertex, index, uniform, and staging exactly when readback exists.")
        }
        val target = requireNotNull(targets)
        val atlas = requireNotNull(atlases)
        val vertex = requireNotNull(vertices)
        val index = requireNotNull(indices)
        val uniform = requireNotNull(uniforms)
        val exactRenderUses = listOf(
            GPUFrameResourceUse(
                atlas.resource,
                GPUFrameResourceRole.GlyphAtlas,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.SharedCache,
                write = false,
            ),
            GPUFrameResourceUse(
                vertex.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            GPUFrameResourceUse(
                index.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            GPUFrameResourceUse(
                uniform.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        )
        if (render.resourceUses.size != exactRenderUses.size || render.resourceUses.toSet() != exactRenderUses.toSet()) {
            return refuse(
                "invalid.preflight.color_glyph_render_resource_uses",
                "ColorGlyph requires exact typed atlas, vertex, index, and uniform render uses.",
            )
        }
        val targetDescriptor = target.descriptor as? GPUFrameTextureDescriptor
        if (target.resource != render.target || targetDescriptor?.logicalBounds != semantic.targetBounds ||
            targetDescriptor.format.value != "rgba8unorm" || targetDescriptor.sampleCount != 1 ||
            target.usages != setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) ||
            target.lifetime != GPUFrameResourceLifetime.FrameLocal || target.byteSize != semantic.targetBounds.width.toLong() * semantic.targetBounds.height * 4L
        ) return refuse("invalid.preflight.color_glyph_target_resource", "ColorGlyph target declaration does not match its semantic bounds and format.")
        val atlasDescriptor = atlas.descriptor as? GPUFrameTextureDescriptor
        if (atlasDescriptor?.logicalBounds != org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, semantic.atlasWidth, semantic.atlasHeight) ||
            atlasDescriptor.format.value != "r8unorm" || atlasDescriptor.sampleCount != 1 ||
            atlas.usages != setOf(GPUFrameResourceUsage.TextureBinding, GPUFrameResourceUsage.CopyDestination) ||
            atlas.lifetime != GPUFrameResourceLifetime.SharedCache || atlas.byteSize != semantic.atlasA8Bytes.size.toLong() ||
            context.resourceGenerations[atlas.resource] != semantic.atlasGeneration
        ) return refuse("invalid.preflight.color_glyph_atlas_resource", "ColorGlyph atlas declaration or generation does not match its payload.")
        fun exactBuffer(
            request: GPUResourcePreparationRequest,
            roleUsage: GPUFrameResourceUsage,
            bytes: Long,
            alignment: Long,
        ): Boolean {
            val descriptor = request.descriptor as? GPUFrameBufferDescriptor ?: return false
            return descriptor.byteSize == bytes && descriptor.alignmentBytes == alignment && request.byteSize == bytes &&
                request.usages == setOf(roleUsage, GPUFrameResourceUsage.CopyDestination) &&
                request.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactBuffer(vertex, GPUFrameResourceUsage.Vertex, semantic.vertexData.size * 4L, 4L)) {
            return refuse("invalid.preflight.color_glyph_vertex_resource", "ColorGlyph vertex declaration does not match its payload.")
        }
        if (!exactBuffer(index, GPUFrameResourceUsage.Index, semantic.indexData.size * 4L, 4L)) {
            return refuse("invalid.preflight.color_glyph_index_resource", "ColorGlyph index declaration does not match its payload.")
        }
        if (!exactBuffer(uniform, GPUFrameResourceUsage.Uniform, semantic.uniformBytes.size.toLong(), 16L)) {
            return refuse("invalid.preflight.color_glyph_uniform_resource", "ColorGlyph uniform declaration does not match its payload.")
        }
        val planGeneration = semantic.planArtifactKey.generation.value.toLong()
        if (packet.resourceGeneration != planGeneration || context.resourceGenerations[render.target] != context.targetGeneration ||
            listOf(vertex, index, uniform).any { context.resourceGenerations[it.resource] != planGeneration } ||
            preparations.any { context.resourceGenerations[it.resource] == null }
        ) {
            return refuse("stale.preflight.color_glyph_resource_generation_missing", "Every ColorGlyph prepared resource requires generation evidence.")
        }
        if (readbacks.isEmpty()) return null
        val staging = requireNotNull(stagings)
        val readback = readbacks.singleOrNull()
        val plannedReadback = readback?.let { readbackLayoutPlanner.plan(it.request, capabilities) as? GPUReadbackLayoutPlan.Planned }
        if (readback == null || readback.source != render.target || readback.staging != staging.resource ||
            readback.request.sourceBounds != semantic.targetBounds ||
            staging.usages != setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead) ||
            staging.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            (staging.descriptor as? GPUFrameBufferDescriptor)?.byteSize != plannedReadback?.stagingDescriptor?.minimumBufferBytes ||
            staging.byteSize != plannedReadback?.stagingDescriptor?.minimumBufferBytes
        ) return refuse("invalid.preflight.color_glyph_readback_resource", "ColorGlyph readback declaration does not match the exact target layout.")

        return null
    }

    private fun validateSolidRectSemanticPayload(
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.SolidRect,
    ): GPUDiagnostic? {
        if (packet.renderStepId.value != solidRectRenderStepIdentity) {
            return diagnostic(
                "invalid.preflight.solid_semantic_route",
                "SolidRect semantic payloads are valid only for the exact solid FillRect render step.",
            )
        }
        val ref = semantic.payloadRef
        if (ref.commandIdValue != packet.commandIdValue) {
            return diagnostic(
                "invalid.preflight.solid_semantic_command_mismatch",
                "Solid semantic command identity differs from its packet.",
            )
        }
        if (ref.renderStepIdentity != packet.renderStepId.value) {
            return diagnostic(
                "invalid.preflight.solid_semantic_render_step_mismatch",
                "Solid semantic render-step identity differs from its selected packet step.",
            )
        }
        if (packet.uniformSlot != ref.uniformSlot) {
            return diagnostic(
                "invalid.preflight.solid_semantic_packet_slot_mismatch",
                "Solid semantic slot facts differ from their packet slot evidence.",
            )
        }
        GPUSolidPayloadGatherer.semanticValidationFailure(ref)?.let { code ->
            return diagnostic(code, "Solid semantic payload failed the gatherer ABI validation.")
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
            val operands = packets.flatMap { packet -> plannedRenderOperands(packet, ownerScope) }
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

    private fun plannedRenderOperands(
        packet: GPUDrawPacket,
        ownerScope: String,
    ): List<GPUCommandOperandMaterializationPlan> = buildList {
        add(
            operand(
                packet,
                "setRenderPipeline",
                GPUMaterializedCommandOperandKind.RenderPipeline,
                packet.renderPipelineKey!!.value,
                ownerScope,
            ),
        )
        add(
            operand(
                packet,
                "setBindGroup",
                GPUMaterializedCommandOperandKind.BindGroup,
                packet.bindingLayoutHash,
                ownerScope,
            ),
        )
        val colorGlyph = packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph
        if (colorGlyph != null) {
            add(
                operand(
                    packet,
                    "setVertexBuffer",
                    GPUMaterializedCommandOperandKind.VertexBuffer,
                    "${colorGlyph.canonicalHash}.vertices.${colorGlyph.vertexData.size}",
                    ownerScope,
                ),
            )
            add(
                operand(
                    packet,
                    "setIndexBuffer",
                    GPUMaterializedCommandOperandKind.IndexBuffer,
                    "${colorGlyph.canonicalHash}.indices.${colorGlyph.indexData.size}",
                    ownerScope,
                ),
            )
        }
    }

    private fun validateRenderOperands(
        framePlan: GPUFramePlan,
        materialized: GPUResourceMaterializationDecision.Materialized,
        ownerScope: String,
    ): GPUDiagnostic? {
        val packets = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        val bridge = materialized.operandBridge
        val expectedTasks = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { it.sourceTaskIds }.map { it.value }.distinct()
        val expectedPlans = packets.flatMap { packet -> plannedRenderOperands(packet, ownerScope) }
        val expectedLabels = expectedPlans.map { it.label }
        if (materialized.targetId != context.targetId || materialized.taskIds != expectedTasks ||
            materialized.resourcePlanLabels != expectedLabels || bridge.size != expectedPlans.size
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
            val vertices = packetBridge.filter { it.commandLabel == "setVertexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.VertexBuffer }
            val indices = packetBridge.filter { it.commandLabel == "setIndexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.IndexBuffer }
            val colorGlyph = packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
            if (pipelines.size != 1 || bindGroups.size != 1 ||
                (colorGlyph && (vertices.size != 1 || indices.size != 1)) ||
                (!colorGlyph && (vertices.isNotEmpty() || indices.isNotEmpty()))
            ) {
                return diagnostic(
                    "invalid.preflight.render_operand_bijection",
                    "Every render packet requires one pipeline and bind group, plus exact indexed buffers for ColorGlyph.",
                    mapOf("packet" to packet.packetId.value),
                )
            }
            if ((pipelines + bindGroups + vertices + indices).any { it.operand.deviceGeneration != context.deviceGeneration.value }) {
                return diagnostic("stale.preflight.render_operand_generation", "A render operand device generation is stale.")
            }
            val expected = plannedRenderOperands(packet, ownerScope)
            val actual = pipelines + bindGroups + vertices + indices
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

    private fun validateNativeRenderSemanticPayloads(
        framePlan: GPUFramePlan,
        draft: GPUPreparedNativeFrameDraft,
    ): GPUDiagnostic? {
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (step !is GPUFrameStep.RenderPassStep) return@forEachIndexed
            val renderOperand = draft.payload.scopeOperands
                .singleOrNull { it.sourceStepIndex == sourceStepIndex } as? GPUPreparedNativeScopeOperand.Render
                ?: return diagnostic(
                    "invalid.preflight.native_render_semantic_payloads",
                    "Native render scope is missing for semantic payload validation.",
                )
            val expected = step.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload)
            if (renderOperand.semanticPayloads.size != expected.size ||
                renderOperand.semanticPayloads.zip(expected).any { (actual, semantic) -> actual !== semantic }
            ) {
                return diagnostic(
                    "invalid.preflight.native_render_semantic_payloads",
                    "Native render operand must retain the exact semantic payload instances in packet order.",
                )
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
                ).attachNativeOperandKeys(nativeOperandKeys(step, labels, stream))
            }
            is GPUFrameStep.ComputePassStep -> scope(index, GPUEncoderOperationKind.Compute, step.sourceTaskIds, listOf("beginComputePass") + List(step.dispatches.size) { "dispatchWorkgroups" } + "endComputePass", labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.UploadResourceStep -> scope(index, GPUEncoderOperationKind.Upload, step.sourceTaskIds, listOf("writeBufferOrCopyBuffer"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.CopyResourceStep -> scope(index, GPUEncoderOperationKind.Copy, step.sourceTaskIds, List(step.regions.size) { "copyResource" }, labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.CopyDestinationStep -> scope(index, GPUEncoderOperationKind.CopyDestination, step.sourceTaskIds, listOf("copyTextureToTexture"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.CopyAsDrawMaterializationStep -> scope(index, GPUEncoderOperationKind.CopyAsDraw, step.sourceTaskIds, listOf("beginRenderPass", "copyAsDraw", "endRenderPass"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.ReadbackCopyStep -> scope(index, GPUEncoderOperationKind.Readback, step.sourceTaskIds, listOf("copyTextureToBuffer"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.SurfaceBlitRenderPassStep -> scope(index, GPUEncoderOperationKind.SurfaceBlit, step.sourceTaskIds, listOf("beginRenderPass", "surfaceBlit", "endRenderPass"), labels, nativeOperandKeys(step, labels))
            else -> null
        }
    }

    private fun scope(
        index: Int,
        kind: GPUEncoderOperationKind,
        tasks: List<org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID>,
        operations: List<String>,
        resources: List<String>,
        nativeOperandKeys: List<GPUPreparedNativeOperandKey>,
    ) = GPUCommandEncoderScopePlan(index, kind, sourceTaskIds = tasks, facadeOperationClasses = operations, targetGeneration = context.targetGeneration, resourceGenerationLabels = resources)
        .attachNativeOperandKeys(nativeOperandKeys)

    private fun nativeOperandKeys(
        step: GPUFrameStep,
        resources: List<String>,
        stream: GPUPassCommandStream? = null,
    ): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
            ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            ownership,
        )
        return when (step) {
            is GPUFrameStep.RenderPassStep -> {
                val targetResourceLabel = resources.first()
                val colorGlyph = step.drawPackets.all { it.semanticPayload is GPUDrawSemanticPayload.ColorGlyph }
                val drawOperandOwnership = if (colorGlyph) {
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
                } else {
                    GPUPreparedNativeOperandOwnership.Borrowed
                }
                (step.sampleContinuation?.let { continuation ->
                listOf(
                    key(
                        GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                        GPUPreparedNativeOperandKind.TextureView,
                        "msaa:${continuation.key.colorAttachment.value}",
                    ),
                    key(
                        GPUPreparedNativeOperandRole.RenderResolveTarget,
                        GPUPreparedNativeOperandKind.TextureView,
                        targetResourceLabel,
                    ),
                )
            } ?: listOf(
                key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel),
            )) + requireNotNull(stream).operandBridge.map { bridge ->
                when (bridge.operand.kind) {
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline ->
                        key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            if (colorGlyph) GPUPreparedNativeOperandOwnership.Borrowed else drawOperandOwnership,
                        )
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup ->
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            drawOperandOwnership,
                        )
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer ->
                        key(
                            GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            drawOperandOwnership,
                        )
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer ->
                        key(
                            GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            drawOperandOwnership,
                        )
                    else -> error("Render native operand bridge contains an unsupported operand kind")
                }
            }
            }
            is GPUFrameStep.ComputePassStep -> step.dispatches.mapIndexed { index, dispatch ->
                key(GPUPreparedNativeOperandRole.ComputePipeline, GPUPreparedNativeOperandKind.ComputePipeline, "dispatch.$index:${dispatch.programKey.value}")
            }
            is GPUFrameStep.UploadResourceStep -> listOf(
                key(GPUPreparedNativeOperandRole.UploadSource, GPUPreparedNativeOperandKind.Buffer, resources[0]),
                key(GPUPreparedNativeOperandRole.UploadDestination, GPUPreparedNativeOperandKind.Buffer, resources[1]),
            )
            is GPUFrameStep.CopyResourceStep,
            is GPUFrameStep.CopyDestinationStep -> listOf(
                key(GPUPreparedNativeOperandRole.CopySource, GPUPreparedNativeOperandKind.Texture, resources[0]),
                key(GPUPreparedNativeOperandRole.CopyDestination, GPUPreparedNativeOperandKind.Texture, resources[1]),
            )
            is GPUFrameStep.CopyAsDrawMaterializationStep -> listOf(
                key(GPUPreparedNativeOperandRole.CopyAsDrawSource, GPUPreparedNativeOperandKind.TextureView, resources[0]),
                key(GPUPreparedNativeOperandRole.CopyAsDrawTarget, GPUPreparedNativeOperandKind.TextureView, resources[1]),
                key(GPUPreparedNativeOperandRole.CopyAsDrawPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "copy-as-draw:pipeline"),
                key(GPUPreparedNativeOperandRole.CopyAsDrawBindGroup, GPUPreparedNativeOperandKind.BindGroup, "copy-as-draw:bind-group"),
            )
            is GPUFrameStep.ReadbackCopyStep -> listOf(
                key(GPUPreparedNativeOperandRole.ReadbackSource, GPUPreparedNativeOperandKind.Texture, resources[0]),
                key(GPUPreparedNativeOperandRole.ReadbackDestination, GPUPreparedNativeOperandKind.Buffer, resources[1], GPUPreparedNativeOperandOwnership.OutputOwnedReadback),
            )
            is GPUFrameStep.SurfaceBlitRenderPassStep -> listOf(
                key(GPUPreparedNativeOperandRole.SurfaceSource, GPUPreparedNativeOperandKind.TextureView, resources.single()),
                key(GPUPreparedNativeOperandRole.SurfaceTarget, GPUPreparedNativeOperandKind.TextureView, "surface:${step.output.value}:target"),
                key(GPUPreparedNativeOperandRole.SurfacePipeline, GPUPreparedNativeOperandKind.RenderPipeline, "surface:${step.output.value}:pipeline"),
                key(GPUPreparedNativeOperandRole.SurfaceBindGroup, GPUPreparedNativeOperandKind.BindGroup, "surface:${step.output.value}:bind-group"),
            )
            else -> emptyList()
        }
    }

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
        decision.textureAllocation != null && preparation.descriptor is GPUFrameTextureDescriptor &&
            (decision.textureAllocation.logicalBounds != preparation.descriptor.logicalBounds ||
                decision.textureAllocation.format != preparation.descriptor.format ||
                decision.textureAllocation.sampleCount != preparation.descriptor.sampleCount ||
                decision.textureAllocation.usages != preparation.usages) ->
            diagnostic(
                "invalid.preflight.texture_allocation_evidence",
                "Prepared texture allocation does not match its logical declaration.",
            )
        else -> null
    }

    private fun refuseWithRollback(
        rollback: GPUFrameRollback,
        @Suppress("UNUSED_PARAMETER") acquiredAnyResource: Boolean,
        diagnostic: GPUDiagnostic,
    ): GPUFramePreflightResult.Refused {
        return GPUFramePreflightResult.Refused(diagnostic, rollback.execute())
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

internal fun gpuSurfaceAcquisitionDiagnostic(status: GPUSurfaceAcquisitionStatus): GPUDiagnostic = when (status) {
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
    GPUSurfaceAcquisitionStatus.DependencyUnavailable -> diagnostic(
        "unsupported.wgpu4k.surface-status-v29",
        "Surface acquisition is dependency-gated until wgpu4k exposes wgpu-native v29 statuses.",
        mapOf("status" to status.name, "recovery" to "upgrade-wgpu4k"),
    )
}

private fun diagnostic(code: String, message: String, facts: Map<String, String> = emptyMap()): GPUDiagnostic =
    preflightDiagnostic(code, message, facts)

private fun referencedResources(framePlan: GPUFramePlan): Set<GPUFrameResourceRef> =
    framePlan.steps.flatMap(::referencedResources).toSet()

private fun referencedResources(step: GPUFrameStep): List<GPUFrameResourceRef> = when (step) {
    is GPUFrameStep.RenderPassStep -> listOf(step.target) + step.resourceUses.map { it.resource }
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

/** A first conflict pass detaches identities already owned elsewhere; the second owns the remaining draft. */
private fun GPUPreparedNativeFrameBoundary.terminalizeCallerRetainedDraft(
    draft: GPUPreparedNativeFrameDraft,
) {
    if (releaseOrQuarantineBeforeRegistration(draft) ==
        GPUPreparedNativeOwnerTerminalization.CallerRetained
    ) {
        releaseOrQuarantineBeforeRegistration(draft)
    }
}
