package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.isCorePrimitiveNoClipOrScissorExecution
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
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
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticClipPacketAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticClipUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionPacketAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveDirectClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.validateCorePrimitiveClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveClipStencilPreparedCandidateValidation
import org.graphiks.kanvas.gpu.renderer.recording.validateCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDepthStencilByteSize
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalCorePrimitiveTargetPreparation
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
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
        val pureValidation = pureValidation(framePlan)
        pureValidation.diagnostic?.let { return GPUFramePreflightResult.Refused(it) }
        val corePrimitiveDirectRoutes = pureValidation.corePrimitiveDirectRoutes
        val corePrimitivePathStencilRoutes = pureValidation.corePrimitivePathStencilRoutes
        val corePrimitiveNativeScopeRoutes = pureValidation.corePrimitiveNativeScopeRoutes
        val corePrimitiveClipStencilPreparedRoutes =
            pureValidation.corePrimitiveClipStencilPreparedRoutes
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
        val renderMaterialization = materializeRenderOperands(framePlan, ownerScope, corePrimitiveDirectRoutes)
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
        validateRenderOperands(framePlan, materialized, ownerScope, corePrimitiveDirectRoutes)?.let { invalid ->
            return refuseWithRollback(rollback, acquiredAnyResource, invalid)
        }

        val encoderScopes = try {
            lowerEncoderScopes(
                framePlan,
                materialized,
                preparedGenerationMap,
                corePrimitiveDirectRoutes,
                corePrimitivePathStencilRoutes,
                corePrimitiveNativeScopeRoutes,
                corePrimitiveClipStencilPreparedRoutes,
            )
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

    private data class CorePrimitiveClipStencilPreparedValidation(
        val routeSeal: GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        val diagnostic: GPUDiagnostic? = null,
    )

    private fun validateCorePrimitiveClipStencilPreparedRoutes(
        framePlan: GPUFramePlan,
    ): CorePrimitiveClipStencilPreparedValidation {
        data class Location(
            val sourceStepIndex: Int,
            val render: GPUFrameStep.RenderPassStep,
            val packet: GPUDrawPacket,
        )

        fun refuse(message: String): CorePrimitiveClipStencilPreparedValidation =
            CorePrimitiveClipStencilPreparedValidation(
                GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty,
                diagnostic(
                    "invalid.preflight.core_primitive_clip_stencil_prepared_route",
                    message,
                ),
            )

        val locations = framePlan.steps.flatMapIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@flatMapIndexed emptyList()
            render.drawPackets.map { packet -> Location(sourceStepIndex, render, packet) }
        }
        val candidateLocations = locations.filter {
            it.packet.corePrimitiveClipStencilPreparedCandidate != null
        }
        if (candidateLocations.isEmpty()) {
            return CorePrimitiveClipStencilPreparedValidation(
                GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty,
            )
        }

        val candidate = requireNotNull(candidateLocations.first().packet
            .corePrimitiveClipStencilPreparedCandidate)
        if (candidateLocations.any {
                it.packet.corePrimitiveClipStencilPreparedCandidate !== candidate
            }
        ) return refuse("All prepared clip-stencil packets must share one exact candidate instance.")
        val expectedPacketIds = listOf(candidate.producerPacketId) +
            candidate.consumers.map { it.packetId }
        if (candidateLocations.map { it.packet.packetId } != expectedPacketIds ||
            candidateLocations.size != expectedPacketIds.size
        ) return refuse("The prepared clip-stencil candidate packet set or order was substituted.")
        val allCorePacketIds = locations.filter { location ->
            location.packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                location.packet.role == GPUDrawPacketRole.StencilProducer ||
                location.packet.role == GPUDrawPacketRole.ClipProducer
        }.map { it.packet.packetId }
        if (allCorePacketIds != expectedPacketIds) {
            return refuse("Prepared clip-stencil must cover every core packet in the bounded frame.")
        }
        if (candidateLocations.any { it.render.drawPackets != listOf(it.packet) }) {
            return refuse("Prepared clip-stencil geometry must own one exact packet per render scope.")
        }

        val producerLocation = candidateLocations.first()
        val consumerLocations = candidateLocations.drop(1)
        if (producerLocation.packet.role != GPUDrawPacketRole.StencilProducer ||
            producerLocation.packet.packetId != candidate.producerPacketId ||
            producerLocation.packet.commandIdValue != candidate.producerCommandId ||
            consumerLocations.size != candidate.consumers.size ||
            consumerLocations.zip(candidate.consumers).any { (location, consumer) ->
                location.packet.role != GPUDrawPacketRole.Shading ||
                    location.packet.packetId != consumer.packetId ||
                    location.packet.commandIdValue != consumer.commandId ||
                    location.packet.originalPaintOrder != consumer.sourceOrder
            } || consumerLocations.any { it.sourceStepIndex <= producerLocation.sourceStepIndex } ||
            !consumerLocations.zipWithNext().all { (left, right) ->
                left.sourceStepIndex < right.sourceStepIndex
            }
        ) return refuse("Producer and consumers do not retain exact frame and source order.")

        if (candidateLocations.any {
                it.packet.clipExecutionPlan?.canonicalIdentity() != candidate.planCanonicalIdentity
            }
        ) return refuse("Prepared clip-stencil content or canonical plan identity was substituted.")

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val producerUses = producerLocation.render.resourceUses
        val vertexUse = producerUses.singleOrNull { it.role == GPUFrameResourceRole.VertexData }
            ?: return refuse("Prepared clip-stencil producer is missing its exact vertex slab use.")
        val indexUse = producerUses.singleOrNull { it.role == GPUFrameResourceRole.IndexData }
            ?: return refuse("Prepared clip-stencil producer is missing its exact index slab use.")
        val depthStencilUse = producerUses.singleOrNull {
            it.role == GPUFrameResourceRole.ClipDepthStencil
        } ?: return refuse("Prepared clip-stencil producer is missing its exact D24S8 use.")
        val expectedVertexUse = GPUFrameResourceUse(
            vertexUse.resource,
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceUsage.Vertex,
            GPUFrameResourceLifetime.FrameLocal,
            false,
        )
        val expectedIndexUse = GPUFrameResourceUse(
            indexUse.resource,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceUsage.Index,
            GPUFrameResourceLifetime.FrameLocal,
            false,
        )
        val expectedDepthStencilProducerUse = GPUFrameResourceUse(
            depthStencilUse.resource,
            GPUFrameResourceRole.ClipDepthStencil,
            GPUFrameResourceUsage.RenderAttachment,
            GPUFrameResourceLifetime.FrameLocal,
            true,
        )
        if (producerUses.toSet() != setOf(
                expectedVertexUse,
                expectedIndexUse,
                expectedDepthStencilProducerUse,
            ) || producerUses.size != 3 ||
            candidate.attachmentLogicalReference != depthStencilUse.resource.value
        ) return refuse("Prepared clip-stencil producer resource uses were substituted.")

        val uniformUses = mutableListOf<GPUFrameResourceUse>()
        consumerLocations.forEachIndexed { consumerIndex, location ->
            val uniformUse = location.render.resourceUses.singleOrNull {
                it.role == GPUFrameResourceRole.UniformData
            } ?: return refuse("Prepared clip-stencil consumer is missing its uniform slab use.")
            val expectedUniformUse = GPUFrameResourceUse(
                uniformUse.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            )
            val expectedDepthStencilConsumerUse = expectedDepthStencilProducerUse.copy(write = false)
            if (location.render.resourceUses.toSet() != setOf(
                    expectedVertexUse,
                    expectedIndexUse,
                    expectedUniformUse,
                    expectedDepthStencilConsumerUse,
                ) || location.render.resourceUses.size != 4 ||
                location.render.depthStencilLoadStore !=
                org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.ReadOnlyKeep ||
                location.render.target != producerLocation.render.target ||
                location.render.loadStore != GPULoadStorePlan(
                    if (consumerIndex == 0) "clear" else "load",
                    GPUStorePlan.Store,
                )
            ) return refuse("Prepared clip-stencil consumer resource uses were substituted.")
            uniformUses += uniformUse
        }
        if (uniformUses.map { it.resource }.distinct().size != 1) {
            return refuse("Prepared clip-stencil consumers must share one exact uniform slab.")
        }
        val uniformResource = uniformUses.first().resource
        val boundedPreparationRoles = setOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.ClipDepthStencil,
            GPUFrameResourceRole.ClipMask,
            GPUFrameResourceRole.PathDepthStencil,
        )
        val boundedPreparations = preparations.filter {
            it.role in boundedPreparationRoles
        }
        val expectedBoundedPreparations = setOf(
            vertexUse.resource to GPUFrameResourceRole.VertexData,
            indexUse.resource to GPUFrameResourceRole.IndexData,
            uniformResource to GPUFrameResourceRole.UniformData,
            depthStencilUse.resource to GPUFrameResourceRole.ClipDepthStencil,
        )
        if (boundedPreparations.size != 4 ||
            boundedPreparations.map { it.resource to it.role }.toSet() !=
            expectedBoundedPreparations
        ) return refuse("Prepared clip-stencil has a foreign geometry or clip preparation.")
        val uniformPreparation = preparations.singleOrNull {
            it.resource == uniformResource
        } ?: return refuse("Prepared clip-stencil uniform slab preparation is missing.")
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refuse("Prepared clip-stencil uniform slab is not a buffer.")
        val uniformSeals = consumerLocations.map { location ->
            location.packet.corePrimitivePreparedAuthority?.uniformSlabSeal
                ?: return refuse("Prepared clip-stencil uniform slab seal is missing.")
        }
        val uniformSeal = uniformSeals.first()
        val uniformPayloads = consumerLocations.map { location ->
            (location.packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)
                ?.payloadRef?.uniformBlock?.bytes
                ?: return refuse("Prepared clip-stencil uniform payload bytes are missing.")
        }
        val limits = capabilities.limits
            ?: return refuse("Prepared clip-stencil requires observed device limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return refuse("Prepared clip-stencil requires observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?: return refuse("Prepared clip-stencil requires an observed dynamic-uniform limit.")
        val exactUniformPayloads = candidate.consumers.zip(uniformPayloads).map {
                (consumer, bytes) ->
            GPUUniformSlabPayload("draw-${consumer.commandId}", bytes.map(Int::toByte).toByteArray())
        }
        if (uniformSeals.any { it !== uniformSeal } ||
            uniformSeal.commandIds != candidate.consumers.map { it.commandId } ||
            !uniformSeal.hasExactPayloads(
                candidate.consumers.map { it.commandId },
                uniformPayloads,
            ) || !uniformSeal.plan.hasExactPayloads(
                "core-primitive-uniform-pass",
                context.deviceGeneration.value,
                limits.minUniformBufferOffsetAlignment,
                exactUniformPayloads,
            ) || uniformSeal.plan.slots.any { slot ->
                slot.payloadBytes != 32L || slot.alignedOffset > UInt.MAX_VALUE.toLong()
            } || uniformSeal.plan.sourceLabel != "core-primitive-uniform-pass" ||
            uniformSeal.plan.deviceGeneration != context.deviceGeneration.value ||
            uniformSeal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformSeal.plan.totalBytes > maxBufferSize || maxDynamicUniformBuffers < 1L ||
            uniformSeal.plan.slots.size != candidate.consumers.size ||
            uniformPreparation.role != GPUFrameResourceRole.UniformData ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformPreparation.byteSize != uniformSeal.plan.totalBytes ||
            uniformDescriptor.byteSize != uniformSeal.plan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSeal.plan.alignmentBytes
        ) return refuse("Prepared clip-stencil uniform slab authority was substituted.")

        val sealedResources = setOf(
            vertexUse.resource,
            indexUse.resource,
            uniformResource,
            depthStencilUse.resource,
        )
        val exactRenderUses = (listOf(producerLocation) + consumerLocations)
            .flatMap { it.render.resourceUses }
            .filter { it.resource in sealedResources }
        val allRenderUses = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::resourceUses)
            .filter { it.resource in sealedResources }
        if (allRenderUses != exactRenderUses) {
            return refuse("A foreign render scope uses a sealed clip-stencil slab or attachment.")
        }
        val sealedRenderStepIndices = candidateLocations.map { it.sourceStepIndex }.toSet()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (step is GPUFrameStep.PrepareResourcesStep ||
                sourceStepIndex in sealedRenderStepIndices
            ) return@forEachIndexed
            if (referencedResources(step).any { it in sealedResources }) {
                return refuse("A foreign frame step references a sealed clip-stencil resource.")
            }
        }

        val targetPreparation = preparations.singleOrNull {
            it.resource == producerLocation.render.target &&
                it.role == GPUFrameResourceRole.SceneTarget
        } ?: return refuse("Prepared clip-stencil target preparation is missing.")
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refuse("Prepared clip-stencil target is not a texture.")
        val targetBounds = targetDescriptor.logicalBounds
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            candidate.attachmentWidth != targetBounds.width ||
            candidate.attachmentHeight != targetBounds.height ||
            candidate.attachmentSampleCount != 1 ||
            !isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                producerLocation.render.target,
                targetBounds,
            )
        ) return refuse("Prepared clip-stencil target dimensions or origin were substituted.")

        val depthStencilPreparation = preparations.singleOrNull {
            it.resource == depthStencilUse.resource
        } ?: return refuse("Prepared clip-stencil D24S8 preparation is missing.")
        val depthStencilDescriptor = depthStencilPreparation.descriptor as?
            GPUFrameTextureDescriptor
            ?: return refuse("Prepared clip-stencil D24S8 authority is not a texture.")
        val expectedDepthStencilBytes = try {
            corePrimitiveDepthStencilByteSize(targetBounds, 1)
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil D24S8 byte size overflowed.")
        }
        if (depthStencilPreparation.role != GPUFrameResourceRole.ClipDepthStencil ||
            depthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencilDescriptor.logicalBounds != targetBounds ||
            depthStencilDescriptor.format.value != "depth24plus-stencil8" ||
            depthStencilDescriptor.sampleCount != 1 ||
            depthStencilPreparation.byteSize != expectedDepthStencilBytes
        ) return refuse("Prepared clip-stencil D24S8 preparation was substituted.")
        val depthStencilGeneration = context.resourceGenerations[depthStencilUse.resource]
            ?: return refuse("Prepared clip-stencil D24S8 generation is missing.")
        val depthStencilResource = depthStencilUse.resource as? GPUFrameTextureRef
            ?: return refuse("Prepared clip-stencil D24S8 resource is not a texture reference.")
        val attachment = GPUCorePrimitiveClipStencilAttachmentAuthority(
            logicalReference = depthStencilResource.value,
            width = targetBounds.width,
            height = targetBounds.height,
            format = GPUCorePrimitiveClipStencilAttachmentFormat.Depth24PlusStencil8,
            sampleCount = 1,
            deviceGeneration = context.deviceGeneration,
            resourceGeneration = depthStencilGeneration,
        )
        val preparedAttachmentAuthority =
            GPUCorePrimitiveClipStencilPreparedAttachmentAuthority(
                depthStencilResource,
                depthStencilGeneration,
            )

        val semanticValidation = when (val validation =
            validateCorePrimitiveClipStencilPreparedCandidate(
                candidate,
                producerLocation.packet,
                consumerLocations.map { it.packet },
                attachment,
            )) {
            is GPUCorePrimitiveClipStencilPreparedCandidateValidation.Accepted -> validation
            is GPUCorePrimitiveClipStencilPreparedCandidateValidation.Refused ->
                return refuse(validation.message)
        }
        val accepted = semanticValidation.route
        if (accepted.producer.structuralKey != candidate.producerStructuralKey ||
            producerLocation.packet.renderPipelineKey != accepted.producer.structuralKey
                .stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY) ||
            accepted.consumers.map { it.structuralKey } !=
            candidate.consumers.map { it.structuralKey }
        ) return refuse("Prepared clip-stencil structural pipeline keys were substituted.")

        val vertexPreparation = preparations.singleOrNull { it.resource == vertexUse.resource }
            ?: return refuse("Prepared clip-stencil vertex slab preparation is missing.")
        val indexPreparation = preparations.singleOrNull { it.resource == indexUse.resource }
            ?: return refuse("Prepared clip-stencil index slab preparation is missing.")
        val vertexResource = vertexUse.resource as? GPUFrameBufferRef
            ?: return refuse("Prepared clip-stencil vertex slab is not a buffer reference.")
        val indexResource = indexUse.resource as? GPUFrameBufferRef
            ?: return refuse("Prepared clip-stencil index slab is not a buffer reference.")
        val uniformBufferResource = uniformResource as? GPUFrameBufferRef
            ?: return refuse("Prepared clip-stencil uniform slab is not a buffer reference.")
        val vertexGeneration = context.resourceGenerations[vertexResource]
            ?: return refuse("Prepared clip-stencil vertex generation is missing.")
        val indexGeneration = context.resourceGenerations[indexResource]
            ?: return refuse("Prepared clip-stencil index generation is missing.")
        val uniformGeneration = context.resourceGenerations[uniformBufferResource]
            ?: return refuse("Prepared clip-stencil uniform generation is missing.")
        val slabAuthority = try {
            GPUCorePrimitiveClipStencilPreparedSlabAuthority(
                vertexResource,
                vertexGeneration,
                vertexPreparation.byteSize,
                indexResource,
                indexGeneration,
                indexPreparation.byteSize,
                uniformBufferResource,
                uniformGeneration,
                uniformPreparation.byteSize,
                uniformDescriptor.alignmentBytes,
                uniformSeal,
            )
        } catch (_: IllegalArgumentException) {
            return refuse("Prepared clip-stencil slab authority is invalid.")
        }

        val consumerScopeLocations = consumerLocations.zip(candidate.consumers).map {
                (location, consumer) ->
            GPUCorePrimitiveClipStencilPreparedConsumerLocation(
                location.sourceStepIndex,
                location.packet.packetId,
                location.packet.commandIdValue,
                consumer.sourceOrder,
                consumer.dependencyFromPreviousConsumerToken,
            )
        }
        val routeSeal = try {
            sealGPUCorePrimitiveClipStencilPreparedFrameRoute(
                accepted,
                semanticValidation.producerFanVertices,
                semanticValidation.producerFanIndices,
                slabAuthority,
                preparedAttachmentAuthority,
                producerLocation.sourceStepIndex,
                producerLocation.packet.packetId,
                producerLocation.packet.commandIdValue,
                consumerScopeLocations,
            )
        } catch (_: IllegalArgumentException) {
            return refuse("Prepared clip-stencil frame scope order is invalid.")
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil frame scope order is invalid.")
        }

        consumerLocations.zipWithNext().forEachIndexed { index, (from, to) ->
            val fromTask = from.render.sourceTaskIds.singleOrNull()
                ?: return refuse("Prepared clip-stencil consumer task identity is ambiguous.")
            val toTask = to.render.sourceTaskIds.singleOrNull()
                ?: return refuse("Prepared clip-stencil consumer task identity is ambiguous.")
            val pairEdges = framePlan.dependencies.filter { dependency ->
                dependency.fromTaskId == fromTask && dependency.toTaskId == toTask
            }
            if (pairEdges.size != 1 || pairEdges.single().dependencyKind != "prepared-scene-order" ||
                pairEdges.single().reasonCode != "preserve.prepared-scene.order" ||
                pairEdges.single().useToken?.value !=
                    candidate.consumers[index + 1].dependencyFromPreviousConsumerToken ||
                framePlan.dependencies.any { dependency ->
                    dependency.fromTaskId == toTask && dependency.toTaskId == fromTask
                }
            ) return refuse("Prepared clip-stencil consumer order dependency was substituted.")
        }

        fun exactGeometryPreparation(
            request: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            expectedBytes: Long,
        ): Boolean {
            val descriptor = request.descriptor as? GPUFrameBufferDescriptor ?: return false
            return request.role == role &&
                request.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                request.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                request.byteSize == expectedBytes && descriptor.byteSize == expectedBytes &&
                descriptor.alignmentBytes == 4L
        }
        val vertexBytes = try {
            Math.multiplyExact(routeSeal.geometryArena.vertexFloatCount.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil vertex slab size overflowed.")
        }
        val indexBytes = try {
            Math.multiplyExact(routeSeal.geometryArena.indexCount.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil index slab size overflowed.")
        }
        if (!exactGeometryPreparation(
                vertexPreparation,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                vertexBytes,
            ) || !exactGeometryPreparation(
                indexPreparation,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                indexBytes,
            )
        ) return refuse("Prepared clip-stencil geometry slab bytes, alignment, or usages were substituted.")
        val lastConsumerStepIndex = consumerLocations.last().sourceStepIndex
        val allowedRenderSteps = (listOf(producerLocation) + consumerLocations)
            .map { it.sourceStepIndex }.toSet()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (sourceStepIndex !in producerLocation.sourceStepIndex..lastConsumerStepIndex) {
                return@forEachIndexed
            }
            when (step) {
                is GPUFrameStep.RenderPassStep -> if (sourceStepIndex !in allowedRenderSteps) {
                    return refuse("Foreign rendering splits the prepared clip-stencil atomic interval.")
                }
                is GPUFrameStep.DependencyBarrierStep -> Unit
                else -> return refuse("A foreign encoder or host step splits the prepared clip-stencil atomic interval.")
            }
        }
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (sourceStepIndex <= lastConsumerStepIndex &&
                (step is GPUFrameStep.ReadbackCopyStep ||
                    step is GPUFrameStep.SurfaceBlitRenderPassStep ||
                    step is GPUFrameStep.PostSubmitPresentAction)
            ) return refuse("Readback, surface blit, and present must follow the final clip-stencil consumer.")
        }
        return CorePrimitiveClipStencilPreparedValidation(routeSeal)
    }

    private data class PureValidationResult(
        val diagnostic: GPUDiagnostic?,
        val corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        val corePrimitivePathStencilRoutes: GPUCorePrimitivePathStencilNativeFrameRouteSeal,
        val corePrimitiveNativeScopeRoutes: GPUCorePrimitiveNativeScopeFrameRouteSeal,
        val corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
    )

    private fun pureValidation(framePlan: GPUFramePlan): PureValidationResult {
        var corePrimitiveDirectRoutes = GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty
        var corePrimitivePathStencilRoutes = GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty
        var corePrimitiveNativeScopeRoutes = GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty
        var corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal =
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty
        val diagnostic = pureValidationDiagnostic(
            framePlan,
            retainCorePrimitiveRoutes = { validation ->
                corePrimitiveDirectRoutes = validation.directRouteSeal
                corePrimitivePathStencilRoutes = validation.pathRouteSeal
                corePrimitiveNativeScopeRoutes = validation.unifiedRouteSeal
            },
            retainCorePrimitiveClipStencilPreparedRoutes = { validation ->
                corePrimitiveClipStencilPreparedRoutes = validation.routeSeal
            },
        )
        return PureValidationResult(
            diagnostic,
            corePrimitiveDirectRoutes,
            corePrimitivePathStencilRoutes,
            corePrimitiveNativeScopeRoutes,
            corePrimitiveClipStencilPreparedRoutes,
        )
    }

    private fun pureValidationDiagnostic(
        framePlan: GPUFramePlan,
        retainCorePrimitiveRoutes: (CorePrimitiveGeometryValidation) -> Unit,
        retainCorePrimitiveClipStencilPreparedRoutes:
            (CorePrimitiveClipStencilPreparedValidation) -> Unit,
    ): GPUDiagnostic? {
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
        val hasClipStencilPreparedCandidate = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .any { it.corePrimitiveClipStencilPreparedCandidate != null }
        if (nativeBoundary != null && !hasClipStencilPreparedCandidate) {
            val validation = validateCorePrimitiveGeometryResources(
                framePlan,
                strictNativeRoute = true,
            )
            validation.diagnostic?.let { return it }
            retainCorePrimitiveRoutes(validation)
        }
        validateCorePrimitiveRenderAuthority(framePlan)?.let { return it }
        val clipProducerValidation = validateCorePrimitiveClipProducerAuthority(framePlan)
        clipProducerValidation.diagnostic?.let { return it }
        val clipStencilPreparedValidation =
            validateCorePrimitiveClipStencilPreparedRoutes(framePlan)
        clipStencilPreparedValidation.diagnostic?.let { return it }
        retainCorePrimitiveClipStencilPreparedRoutes(clipStencilPreparedValidation)
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
                                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.RegisteredUniformRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.SeparableBlurRect
                            val acceptedGeneration = when {
                                colorGlyph != null -> colorGlyph.planArtifactKey.generation.value.toLong()
                                (preparedLateBound || packet.packetId in clipProducerValidation.sealedProducerPacketIds) &&
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
                        validateSemanticPayload(
                            framePlan,
                            step,
                            packet,
                            clipStencilPreparedValidation.routeSeal,
                        )?.let { return it }
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
        if (nativeBoundary == null &&
            clipStencilPreparedValidation.routeSeal ===
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty
        ) {
            val validation = validateCorePrimitiveGeometryResources(
                framePlan,
                strictNativeRoute = false,
            )
            validation.diagnostic?.let { return it }
            retainCorePrimitiveRoutes(validation)
        }
        return null
    }

    private data class CorePrimitiveDirectGeometryValidation(
        val diagnostic: GPUDiagnostic?,
        val routeSeal: GPUCorePrimitiveDirectNativeFrameRouteSeal,
    )

    private data class CorePrimitiveGeometryValidation(
        val diagnostic: GPUDiagnostic?,
        val directRouteSeal: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        val pathRouteSeal: GPUCorePrimitivePathStencilNativeFrameRouteSeal,
        val unifiedRouteSeal: GPUCorePrimitiveNativeScopeFrameRouteSeal,
    )

    private fun classifyCorePrimitiveDirectNativeRoute(
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        clipAuthority: GPUCorePrimitiveDirectClipAuthority,
        blendPlan: GPUBlendPlan?,
        samplePlan: GPUSamplePlan,
        targetFormat: String,
    ): GPUCorePrimitiveDirectNativeRoute = validateCorePrimitiveDirectNativeRoute(
        semantic,
        clipAuthority,
        blendPlan,
        samplePlan,
        targetFormat,
    )

    private fun validateCorePrimitiveGeometryResources(
        framePlan: GPUFramePlan,
        strictNativeRoute: Boolean,
    ): CorePrimitiveGeometryValidation {
        val hasPathPackets = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .any { packet ->
                packet.role == GPUDrawPacketRole.PathStencilProducer ||
                    packet.role == GPUDrawPacketRole.PathStencilCover
            }
        if (hasPathPackets) {
            return validateCorePrimitivePathGeometryResources(framePlan)
        }
        val direct = validateCorePrimitiveDirectGeometryResources(framePlan, strictNativeRoute)
        if (direct.diagnostic != null) {
            return CorePrimitiveGeometryValidation(
                direct.diagnostic,
                direct.routeSeal,
                GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty,
                GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty,
            )
        }
        val unifiedByKey = linkedMapOf<
            GPUCorePrimitiveNativeScopeFrameRouteKey,
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
            >()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
            val corePackets = render.drawPackets.filter { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
            if (corePackets.isEmpty()) return@forEachIndexed
            val units = corePackets.mapNotNull { packet ->
                val route = direct.routeSeal.routeOrNull(sourceStepIndex, packet.packetId)
                    ?: return@mapNotNull null
                val authority = packet.corePrimitivePreparedAuthority ?: return@mapNotNull null
                GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                    packet.commandIdValue,
                    packet.packetId,
                    route,
                    authority.structuralPipelineKey,
                )
            }
            if (units.size == corePackets.size) {
                val slab = corePackets.first().corePrimitivePreparedAuthority?.uniformSlabSeal
                    ?: return@forEachIndexed
                val routes = GPUCorePrimitiveNativeScopeRouteSeal.Routes(units, slab)
                unifiedByKey[GPUCorePrimitiveNativeScopeFrameRouteKey(
                    sourceStepIndex,
                    routes.flattenedPacketIds.first(),
                )] = routes
            }
        }
        return CorePrimitiveGeometryValidation(
            null,
            direct.routeSeal,
            GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty,
            GPUCorePrimitiveNativeScopeFrameRouteSeal(unifiedByKey),
        )
    }

    private fun validateCorePrimitivePathGeometryResources(
        framePlan: GPUFramePlan,
    ): CorePrimitiveGeometryValidation {
        fun refused(message: String): CorePrimitiveGeometryValidation = CorePrimitiveGeometryValidation(
            diagnostic(
                "invalid.preflight.core_primitive_path_stencil",
                message,
            ),
            GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty,
            GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty,
            GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty,
        )

        val indexedCoreRenders = framePlan.steps.withIndex().mapNotNull { indexed ->
            val render = indexed.value as? GPUFrameStep.RenderPassStep ?: return@mapNotNull null
            if (render.drawPackets.any { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }) {
                indexed.index to render
            } else {
                null
            }
        }
        if (indexedCoreRenders.size != 1) {
            return refused("Path stencil CorePrimitive requires exactly one prepared render pass.")
        }
        val (sourceStepIndex, render) = indexedCoreRenders.single()
        if (render.drawPackets.any { it.semanticPayload !is GPUDrawSemanticPayload.CorePrimitive }) {
            return refused("Path stencil CorePrimitive requires one all-CorePrimitive render pass.")
        }
        if (render.loadStore != GPULoadStorePlan("clear", GPUStorePlan.Store) ||
            render.depthStencilLoadStore != org.graphiks.kanvas.gpu.renderer.recording
                .GPUDepthStencilLoadStorePlan.WritableStencil(
                    org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Discard,
                    0u,
                )
        ) {
            return refused("Path stencil requires exact color clear/store and stencil clear/discard authority.")
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val targetPreparation = preparations.singleOrNull { request -> request.resource == render.target }
            ?: return refused("Path stencil requires one exact target preparation.")
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path stencil target preparation must be a texture.")
        val semanticTargetBounds = render.drawPackets.map { packet ->
            (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive).targetBounds
        }.distinct()
        if (semanticTargetBounds.size != 1 ||
            !isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                render.target,
                semanticTargetBounds.single(),
            )
        ) {
            return refused(
                "Path stencil requires one exact SceneTarget matching every semantic target bound.",
            )
        }

        val geometryRoles = setOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.PathDepthStencil,
        )
        val geometryPreparations = preparations.filter { it.role in geometryRoles }
        val vertex = geometryPreparations.singleOrNull { it.role == GPUFrameResourceRole.VertexData }
            ?: return refused("Path stencil requires one shared vertex slab.")
        val index = geometryPreparations.singleOrNull { it.role == GPUFrameResourceRole.IndexData }
            ?: return refused("Path stencil requires one shared index slab.")
        val uniform = geometryPreparations.singleOrNull { it.role == GPUFrameResourceRole.UniformData }
            ?: return refused("Path stencil requires one shared uniform slab.")
        val depthStencil = geometryPreparations.singleOrNull {
            it.role == GPUFrameResourceRole.PathDepthStencil
        } ?: return refused("Path stencil requires one full-target depth/stencil attachment.")
        if (geometryPreparations.size != 4 ||
            setOf(vertex.resource, index.resource, uniform.resource, depthStencil.resource).size != 4
        ) {
            return refused("Path stencil shared resources must be unique and exact.")
        }

        val directRoutes = linkedMapOf<
            GPUCorePrimitiveDirectNativeFrameRouteKey,
            GPUCorePrimitiveDirectNativeRoute.Accepted
            >()
        val pathRoutes = linkedMapOf<
            GPUCorePrimitivePathStencilNativeFrameRouteKey,
            GPUCorePrimitivePathStencilNativeRoute.AcceptedPair
            >()
        val unifiedUnits = mutableListOf<GPUCorePrimitiveNativeScopeRouteUnit>()
        val preparedPathPairs = mutableListOf<GPUCorePrimitivePathStencilPreparedPairSeal>()
        val directStructuralKeys = mutableListOf<GPUCorePrimitiveRenderPipelineStructuralKey>()
        var sharedUniformSeal: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal? = null
        var packetIndex = 0
        var uniformSlotIndex = 0

        fun exactAuthority(
            packet: GPUDrawPacket,
            semantic: GPUDrawSemanticPayload.CorePrimitive,
            role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
        ): Pair<
            org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority,
            GPUCorePrimitiveRenderPipelineStructuralKey
            >? {
            val clip = packet.clipExecutionPlan ?: return null
            val blend = packet.blendPlan ?: return null
            val expected = when (role) {
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading ->
                    corePrimitiveRenderPipelineStructuralKey(semantic, clip, blend)
                        .copy(depthStencil = corePrimitiveDirectPathDepthStencilState())
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                -> corePrimitivePathStencilRenderPipelineStructuralKey(semantic, role, clip, blend)
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer,
                -> return null
            }
            val authority = packet.corePrimitivePreparedAuthority ?: return null
            if (authority.structuralPipelineKey != expected ||
                packet.renderPipelineKey != authority.renderPipelineKey ||
                authority.renderPipelineKey !=
                expected.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
            ) {
                return null
            }
            val slab = authority.uniformSlabSeal ?: return null
            if (sharedUniformSeal == null) sharedUniformSeal = slab
            if (sharedUniformSeal !== slab) return null
            return authority to expected
        }

        while (packetIndex < render.drawPackets.size) {
            val packet = render.drawPackets[packetIndex]
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
            when (packet.role) {
                GPUDrawPacketRole.Shading -> {
                    val authority = exactAuthority(
                        packet,
                        semantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading,
                    ) ?: return refused("A mixed direct packet has corrupt prepared pipeline or uniform authority.")
                    val route = classifyCorePrimitiveDirectNativeRoute(
                        semantic,
                        corePrimitiveDirectClipAuthority(
                            packet.clipExecutionPlan ?: return refused("A direct packet is missing clip authority."),
                            semantic.targetBounds,
                        ),
                        packet.blendPlan,
                        render.samplePlan,
                        targetDescriptor.format.value,
                    )
                    if (route !is GPUCorePrimitiveDirectNativeRoute.Accepted) {
                        route as GPUCorePrimitiveDirectNativeRoute.Refused
                        return CorePrimitiveGeometryValidation(
                            diagnostic(route.code, route.message),
                            GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty,
                            GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty,
                            GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty,
                        )
                    }
                    directRoutes[
                        GPUCorePrimitiveDirectNativeFrameRouteKey(sourceStepIndex, packet.packetId)
                    ] = route
                    directStructuralKeys += authority.second
                    unifiedUnits += GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                        packet.commandIdValue,
                        packet.packetId,
                        route,
                        authority.second,
                    )
                    packetIndex += 1
                    uniformSlotIndex += 1
                }
                GPUDrawPacketRole.PathStencilProducer -> {
                    val cover = render.drawPackets.getOrNull(packetIndex + 1)
                        ?: return refused("Every path producer must be followed by one cover packet.")
                    if (cover.role != GPUDrawPacketRole.PathStencilCover ||
                        cover.commandIdValue != packet.commandIdValue
                    ) {
                        return refused("Path producer and cover order, role, or command identity is corrupt.")
                    }
                    val coverSemantic = cover.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                        ?: return refused("Path cover is missing its CorePrimitive semantic payload.")
                    val producerGeometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
                        ?: return refused("Path producer requires triangulated path geometry.")
                    val coverGeometry = coverSemantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
                        ?: return refused("Path cover requires triangulated path geometry.")
                    if (semantic.coverageMode != GPUCorePrimitiveCoverageMode.Stencil1x ||
                        coverSemantic.coverageMode != GPUCorePrimitiveCoverageMode.Stencil1x ||
                        producerGeometry.geometryMode != GPUCorePrimitiveGeometryMode.StencilEdgeFan ||
                        producerGeometry != coverGeometry ||
                        semantic.targetBounds != coverSemantic.targetBounds ||
                        semantic.scissorBounds != coverSemantic.scissorBounds ||
                        packet.scissorBoundsHash != cover.scissorBoundsHash ||
                        packet.uniformSlot != cover.uniformSlot ||
                        semantic.payloadRef.uniformBlock?.bytes != coverSemantic.payloadRef.uniformBlock?.bytes ||
                        packet.clipExecutionPlan != cover.clipExecutionPlan
                    ) {
                        return refused("Path producer and cover semantic, scissor, geometry, or uniform authority differs.")
                    }
                    val pathClip = packet.clipExecutionPlan
                    if (pathClip?.isCorePrimitiveNoClipOrScissorExecution() != true) {
                        return refused("Path stencil currently accepts only no clip or dynamic scissor.")
                    }
                    val producerAuthority = exactAuthority(
                        packet,
                        semantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                    ) ?: return refused("Path producer prepared pipeline or uniform authority is corrupt.")
                    val coverAuthority = exactAuthority(
                        cover,
                        coverSemantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                    ) ?: return refused("Path cover prepared pipeline or uniform authority is corrupt.")
                    val pair = try {
                        GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
                            packet.packetId,
                            cover.packetId,
                            FloatArray(producerGeometry.vertices.size) { producerGeometry.vertices[it] },
                            IntArray(producerGeometry.indices.size) { producerGeometry.indices[it] },
                            producerGeometry.coverBounds,
                            semantic.targetBounds,
                            producerGeometry.inverseFill,
                        )
                    } catch (_: IllegalArgumentException) {
                        return refused("Path producer or cover geometry is not a valid immutable pair.")
                    }
                    pathRoutes[
                        GPUCorePrimitivePathStencilNativeFrameRouteKey(
                            sourceStepIndex,
                            packet.packetId,
                            cover.packetId,
                        )
                    ] = pair
                    preparedPathPairs += GPUCorePrimitivePathStencilPreparedPairSeal(
                        packet.commandIdValue,
                        uniformSlotIndex,
                        packet.packetId,
                        cover.packetId,
                        producerAuthority.second,
                        coverAuthority.second,
                    )
                    unifiedUnits += GPUCorePrimitiveNativeScopeRouteUnit.PathPair(
                        packet.commandIdValue,
                        pair,
                        producerAuthority.second,
                        coverAuthority.second,
                    )
                    packetIndex += 2
                    uniformSlotIndex += 1
                }
                GPUDrawPacketRole.PathStencilCover ->
                    return refused("A path cover cannot appear without its immediately preceding producer.")
                else -> return refused("Path stencil render contains an unsupported packet role.")
            }
        }

        val uniformSeal = sharedUniformSeal
            ?: return refused("Path stencil packets are missing their shared uniform slab seal.")
        if (uniformSeal.drawCount != unifiedUnits.size ||
            unifiedUnits.indices.any { unitIndex ->
                val unit = unifiedUnits[unitIndex]
                val packet = when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct ->
                        render.drawPackets.single { it.packetId == unit.packetId }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ->
                        render.drawPackets.single { it.packetId == unit.pair.producerPacketId }
                }
                val bytes = (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive)
                    .payloadRef.uniformBlock?.bytes ?: return refused(
                    "CorePrimitive uniform semantic bytes are missing.",
                )
                !uniformSeal.hasExactPayload(unitIndex, unit.commandIdValue, bytes)
            }
        ) {
            return refused("The shared uniform slab does not exactly match original command order and bytes.")
        }

        val geometrySizing = try {
            val arena = packCorePrimitiveNativeScopeGeometry(
                GPUCorePrimitiveNativeScopeRouteSeal.Routes(unifiedUnits, uniformSeal),
            )
            Triple(
                arena,
                Math.multiplyExact(arena.vertexFloatCount.toLong(), Float.SIZE_BYTES.toLong()),
                Math.multiplyExact(arena.indexCount.toLong(), Int.SIZE_BYTES.toLong()),
            )
        } catch (_: IllegalArgumentException) {
            return refused(
                "Unified path geometry cannot be sized or packed into exact immutable slabs.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "Unified path geometry cannot be sized or packed into exact immutable slabs.",
            )
        }
        val expectedVertexBytes = geometrySizing.second
        val expectedIndexBytes = geometrySizing.third
        fun exactBuffer(
            request: GPUResourcePreparationRequest,
            bytes: Long,
            usage: GPUFrameResourceUsage,
        ): Boolean {
            val descriptor = request.descriptor as? GPUFrameBufferDescriptor ?: return false
            return bytes > 0L && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                request.byteSize == bytes &&
                request.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                request.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactBuffer(vertex, expectedVertexBytes, GPUFrameResourceUsage.Vertex) ||
            expectedVertexBytes % 8L != 0L ||
            !exactBuffer(index, expectedIndexBytes, GPUFrameResourceUsage.Index)
        ) {
            return refused("Path stencil vertex or index slab topology is not exact.")
        }
        val uniformDescriptor = uniform.descriptor as? GPUFrameBufferDescriptor
            ?: return refused("Path stencil uniform slab descriptor is missing.")
        val limits = capabilities.limits
            ?: return refused("Path stencil requires observed backend limits.")
        if (uniformDescriptor.byteSize != uniformSeal.plan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSeal.plan.alignmentBytes ||
            uniform.byteSize != uniformSeal.plan.totalBytes ||
            uniform.usages != setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform) ||
            uniform.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformSeal.plan.deviceGeneration != context.deviceGeneration.value ||
            uniformSeal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformSeal.plan.totalBytes > (limits.maxBufferSize ?: return refused(
                "Path stencil requires observed maxBufferSize.",
            )) ||
            (limits.maxDynamicUniformBuffersPerPipelineLayout ?: 0) < 1
        ) {
            return refused("Path stencil uniform slab topology or device-limit authority is not exact.")
        }
        val depthDescriptor = depthStencil.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path depth/stencil preparation must be a texture.")
        val targetBounds = targetDescriptor.logicalBounds
        val depthBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused("Path depth/stencil byte size overflows signed 64-bit arithmetic.")
        }
        if (depthDescriptor.logicalBounds != targetBounds ||
            depthDescriptor.format.value != "depth24plus-stencil8" ||
            depthDescriptor.sampleCount != 1 ||
            depthStencil.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencil.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencil.byteSize != depthBytes
        ) {
            return refused("Path depth/stencil descriptor, extent, format, usage, lifetime, or size is not exact.")
        }
        val exactUses = setOf(
            GPUFrameResourceUse(
                vertex.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            GPUFrameResourceUse(
                index.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            GPUFrameResourceUse(
                uniform.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            GPUFrameResourceUse(
                depthStencil.resource,
                GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceLifetime.FrameLocal,
                true,
            ),
        )
        if (render.resourceUses.toSet() != exactUses || render.resourceUses.size != exactUses.size
        ) {
            return refused("Path stencil render must retain exactly the four shared resource uses.")
        }
        val exclusiveRefs = exactUses.map(GPUFrameResourceUse::resource).toSet()
        val foreignGeometryUse = framePlan.steps.withIndex()
            .filter { indexed -> indexed.index != sourceStepIndex }
            .any { indexed ->
                val typedUses = when (val step = indexed.value) {
                    is GPUFrameStep.RenderPassStep -> step.resourceUses
                    is GPUFrameStep.ComputePassStep -> step.resourceUses
                    else -> emptyList()
                }
                typedUses.any { use -> use.role in geometryRoles } ||
                    referencedResources(indexed.value).any { resource -> resource in exclusiveRefs }
            }
        if (foreignGeometryUse) {
            return refused(
                "Path stencil shared geometry roles and resources are exclusive to its unique render scope.",
            )
        }
        if (listOf(vertex, index, uniform, depthStencil).any {
                context.resourceGenerations[it.resource] == null
            }
        ) {
            return refused("Path stencil shared resources require current generation evidence.")
        }

        val directPasses = if (directRoutes.isEmpty()) {
            emptyMap()
        } else {
            if (directStructuralKeys.distinct().size != 1) {
                return refused("Mixed direct packets must share one neutral depth/stencil structural key.")
            }
            mapOf(
                sourceStepIndex to GPUCorePrimitiveDirectPreparedPassSeal(
                    directStructuralKeys.first(),
                    uniformSeal,
                ),
            )
        }
        val pathPass = GPUCorePrimitivePathStencilPreparedPassSeal(preparedPathPairs, uniformSeal)
        val unifiedRoutes = GPUCorePrimitiveNativeScopeRouteSeal.Routes(unifiedUnits, uniformSeal)
        return CorePrimitiveGeometryValidation(
            null,
            GPUCorePrimitiveDirectNativeFrameRouteSeal(directRoutes, directPasses),
            GPUCorePrimitivePathStencilNativeFrameRouteSeal(
                pathRoutes,
                mapOf(sourceStepIndex to pathPass),
            ),
            GPUCorePrimitiveNativeScopeFrameRouteSeal(
                mapOf(
                    GPUCorePrimitiveNativeScopeFrameRouteKey(
                        sourceStepIndex,
                        unifiedRoutes.flattenedPacketIds.first(),
                    ) to unifiedRoutes,
                ),
            ),
        )
    }

    private fun validateCorePrimitiveDirectGeometryResources(
        framePlan: GPUFramePlan,
        strictNativeRoute: Boolean,
    ): CorePrimitiveDirectGeometryValidation {
        var routeSeal = GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty
        val diagnostic = validateCorePrimitiveDirectGeometryResourcesDiagnostic(
            framePlan,
            strictNativeRoute,
        ) { routes, preparedPasses ->
            routeSeal = GPUCorePrimitiveDirectNativeFrameRouteSeal(routes, preparedPasses)
        }
        return CorePrimitiveDirectGeometryValidation(diagnostic, routeSeal)
    }

    private fun validateCorePrimitiveDirectGeometryResourcesDiagnostic(
        framePlan: GPUFramePlan,
        strictNativeRoute: Boolean,
        retainAcceptedRoutes: (
            Map<GPUCorePrimitiveDirectNativeFrameRouteKey, GPUCorePrimitiveDirectNativeRoute.Accepted>,
            Map<Int, GPUCorePrimitiveDirectPreparedPassSeal>,
        ) -> Unit,
    ): GPUDiagnostic? {
        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val coreRenders = renders.filter { render ->
            render.drawPackets.any { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
        }
        if (coreRenders.isEmpty()) return null
        data class Direct(
            val sourceStepIndex: Int,
            val render: GPUFrameStep.RenderPassStep,
            val packet: GPUDrawPacket,
            val semantic: GPUDrawSemanticPayload.CorePrimitive,
            val route: GPUCorePrimitiveDirectNativeRoute.Accepted,
        )
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val directRoles = setOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
        )
        val directPreparations = preparations.filter { it.role in directRoles }
        val directUsesByRender = renders.associateWith { render ->
            render.resourceUses.filter { it.role in directRoles }
        }
        fun refuse(message: String) = diagnostic(
            "invalid.preflight.core_primitive_direct_geometry_resources",
            message,
        )
        fun refuseAnalytic(message: String) = diagnostic(
            "invalid.preflight.core_primitive_analytic_clip_uniform_seal",
            message,
        )
        val targetPreparations = preparations.filter { it.role == GPUFrameResourceRole.SceneTarget }
            .associateBy(GPUResourcePreparationRequest::resource)
        val routeResults = coreRenders.flatMap { render ->
            render.drawPackets.mapNotNull { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    ?: return@mapNotNull null
                val targetFormat = (
                    targetPreparations[render.target]?.descriptor as? GPUFrameTextureDescriptor
                    )?.format?.value ?: if (strictNativeRoute) {
                    return diagnostic(
                        "unsupported.native-core-primitive.target-format",
                        "Direct CorePrimitive native geometry requires an exact prepared target format.",
                    )
                } else {
                    return@mapNotNull null
                }
                val clipExecutionPlan = packet.clipExecutionPlan ?: if (strictNativeRoute) {
                    return diagnostic(
                        "unsupported.native-core-primitive.clip",
                        "Direct CorePrimitive native geometry requires classified clip authority.",
                    )
                } else {
                    return@mapNotNull null
                }
                Triple(
                    render,
                    packet to semantic,
                    classifyCorePrimitiveDirectNativeRoute(
                        semantic,
                        corePrimitiveDirectClipAuthority(
                            clipExecutionPlan,
                            semantic.targetBounds,
                        ),
                        packet.blendPlan,
                        render.samplePlan,
                        targetFormat,
                    ),
                )
            }
        }
        val accepted = routeResults.mapNotNull { (render, packetAndSemantic, route) ->
            (route as? GPUCorePrimitiveDirectNativeRoute.Accepted)?.let {
                Direct(
                    framePlan.steps.indexOfFirst { step -> step === render },
                    render,
                    packetAndSemantic.first,
                    packetAndSemantic.second,
                    it,
                )
            }
        }
        val directUniformLayouts = accepted.mapNotNull { entry ->
            entry.packet.corePrimitivePreparedAuthority?.structuralPipelineKey?.uniformLayout
        }.distinct()
        if (directUniformLayouts.size > 1) {
            return diagnostic(
                "unsupported.preflight.core_primitive_mixed_uniform_layouts",
                "One direct CorePrimitive pass cannot mix uniform32, uniform64, and uniform160 layouts.",
            )
        }
        if (accepted.any { entry ->
                val block = entry.semantic.payloadRef.uniformBlock
                block == null || entry.semantic.payloadRef.uniformSlot?.fingerprint != block.fingerprint ||
                    block.byteSize != 32L || block.bytes.size != 32
            }
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_semantic_integrity",
                "Core primitive packet authority contradicts its immutable semantic input.",
            )
        }
        if (accepted.isNotEmpty()) {
            val forbiddenDirectRoles = setOf(
                GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceRole.ClipDepthStencil,
                GPUFrameResourceRole.ClipMask,
            )
            val forbiddenPreparations = preparations.filter {
                it.role in forbiddenDirectRoles
            }
            val forbiddenRefs = forbiddenPreparations.map {
                it.resource
            }.toSet()
            val hasForbiddenUseOrAlias = renders.any { render ->
                render.resourceUses.any { use ->
                    use.role in forbiddenDirectRoles || use.resource in forbiddenRefs
                }
            }
            if (forbiddenPreparations.isNotEmpty() ||
                hasForbiddenUseOrAlias ||
                accepted.any { entry -> entry.render.depthStencilLoadStore != null }
            ) {
                return refuse(
                    "A direct-only CorePrimitive frame cannot declare, reference, use, or load mask or depth/stencil artifacts.",
                )
            }
        }
        val declaresDirectBoundary = directPreparations.isNotEmpty() ||
            directUsesByRender.values.any(List<GPUFrameResourceUse>::isNotEmpty) || accepted.isNotEmpty()
        if (!declaresDirectBoundary && !strictNativeRoute) return null
        routeResults.firstOrNull { it.third is GPUCorePrimitiveDirectNativeRoute.Refused }?.let { (_, _, route) ->
            route as GPUCorePrimitiveDirectNativeRoute.Refused
            return diagnostic(route.code, route.message)
        }
        if (!declaresDirectBoundary) return null
        if (coreRenders.size != 1 || coreRenders.single().drawPackets.size != accepted.size ||
            renders.any { it !in coreRenders && directUsesByRender.getValue(it).isNotEmpty() }
        ) {
            return refuse("Direct CorePrimitive requires one all-direct multi-packet render pass.")
        }
        val directRender = coreRenders.single()
        if (directRender.loadStore.loadOp != "clear" ||
            directRender.loadStore.storePlan != GPUStorePlan.Store ||
            directRender.loadStore.clearColorLabel != null
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_direct_load_store",
                "Direct CorePrimitive requires exactly one clear/store pass.",
            )
        }
        val vertex = directPreparations.filter { it.role == GPUFrameResourceRole.VertexData }.singleOrNull()
            ?: return refuse("Direct CorePrimitive requires exactly one shared vertex slab.")
        val index = directPreparations.filter { it.role == GPUFrameResourceRole.IndexData }.singleOrNull()
            ?: return refuse("Direct CorePrimitive requires exactly one shared index slab.")
        val uniform = directPreparations.filter { it.role == GPUFrameResourceRole.UniformData }.singleOrNull()
            ?: return refuse("Direct CorePrimitive requires exactly one shared uniform slab.")
        if (directPreparations.size != 3 ||
            setOf(vertex.resource, index.resource, uniform.resource).size != 3
        ) {
            return refuse("Direct CorePrimitive vertex, index, and uniform slabs must be unique and distinct.")
        }
        val vertexBytes = try {
            accepted.fold(0L) { total, entry ->
                Math.addExact(
                    total,
                    Math.multiplyExact(entry.route.vertexCount.toLong(), 2L * Float.SIZE_BYTES),
                )
            }
        } catch (_: ArithmeticException) {
            return refuse("Direct CorePrimitive vertex slab size overflows signed 64-bit arithmetic.")
        }
        val indexBytes = try {
            accepted.fold(0L) { total, entry ->
                Math.addExact(
                    total,
                    Math.multiplyExact(entry.route.indexCount.toLong(), Int.SIZE_BYTES.toLong()),
                )
            }
        } catch (_: ArithmeticException) {
            return refuse("Direct CorePrimitive index slab size overflows signed 64-bit arithmetic.")
        }
        fun exactBuffer(
            request: GPUResourcePreparationRequest,
            bytes: Long,
            usage: GPUFrameResourceUsage,
        ): Boolean {
            val descriptor = request.descriptor as? GPUFrameBufferDescriptor ?: return false
            return bytes > 0L && bytes % 4L == 0L && descriptor.byteSize == bytes &&
                descriptor.alignmentBytes == 4L && request.byteSize == bytes &&
                request.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                request.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactBuffer(vertex, vertexBytes, GPUFrameResourceUsage.Vertex) ||
            vertexBytes % 8L != 0L || !exactBuffer(index, indexBytes, GPUFrameResourceUsage.Index)
        ) {
            return refuse("Direct CorePrimitive shared slab descriptors, sizes, alignment, usages, or lifetime are not exact.")
        }
        val limits = capabilities.limits
            ?: return diagnostic(
                "unsupported.native-core-primitive.limits-unavailable",
                "Direct CorePrimitive requires observed backend limits.",
            )
        val uniformDescriptor = uniform.descriptor as? GPUFrameBufferDescriptor
            ?: return refuse("Direct CorePrimitive uniform slab descriptor is missing.")
        val packetAuthorities = accepted.map { entry ->
            entry.packet.corePrimitivePreparedAuthority
                ?: return refuse("Direct CorePrimitive packet is missing its builder authority seal.")
        }
        accepted.indices.forEach { acceptedIndex ->
            val entry = accepted[acceptedIndex]
            val clipExecutionPlan = entry.packet.clipExecutionPlan
                ?: return refuse("Direct CorePrimitive packet is missing its builder clip authority.")
            val blendPlan = entry.packet.blendPlan
                ?: return refuse("Direct CorePrimitive packet is missing its builder blend authority.")
            val expectedStructuralKey = corePrimitiveRenderPipelineStructuralKey(
                entry.semantic,
                clipExecutionPlan,
                blendPlan,
            )
            val authority = packetAuthorities[acceptedIndex]
            if (authority.structuralPipelineKey != expectedStructuralKey ||
                authority.structuralPipelineKey.role !=
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading ||
                authority.renderPipelineKey !=
                expectedStructuralKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY) ||
                entry.packet.renderPipelineKey != authority.renderPipelineKey
            ) {
                return refuse(
                    "Direct CorePrimitive packet has non-canonical shading pipeline authority.",
                )
            }
        }
        val structuralPipelineKey = packetAuthorities.first().structuralPipelineKey
        if (packetAuthorities.any { authority -> authority.structuralPipelineKey != structuralPipelineKey }) {
            return refuse("Direct CorePrimitive packets must share one structural pipeline authority.")
        }
        fun refuseIntersection(message: String) = diagnostic(
            "invalid.preflight.core_primitive_analytic_intersection_uniform_seal",
            message,
        )
        val uniformLayout = structuralPipelineKey.uniformLayout
        when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                return diagnostic(
                    "unsupported.native-core-primitive.no-bindings-direct-route",
                    "The no-bindings clip-stencil producer is not a direct CorePrimitive route.",
                )
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 -> {
                if (packetAuthorities.any { authority ->
                        authority.analyticClipUniformSeal != null ||
                            authority.analyticIntersectionUniformSeal != null
                    }
                ) return refuse("Uniform32 packets cannot retain uniform64 or uniform160 seals.")
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 -> {
                if (packetAuthorities.any { authority ->
                        authority.uniformSlabSeal != null || authority.analyticIntersectionUniformSeal != null
                    }
                ) return refuseAnalytic("Uniform64 packets cannot retain uniform32 or uniform160 seals.")
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 -> {
                if (packetAuthorities.any { authority ->
                        authority.uniformSlabSeal != null || authority.analyticClipUniformSeal != null
                    }
                ) return refuseIntersection("Uniform160 packets cannot retain uniform32 or uniform64 seals.")
            }
        }
        val uniformSeal = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                packetAuthorities.first().uniformSlabSeal
                    ?: return refuse("Direct CorePrimitive packet is missing its builder uniform32 slab seal.")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
            -> null
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct uniform authority selection")
        }
        if (uniformSeal != null && packetAuthorities.any { authority -> authority.uniformSlabSeal !== uniformSeal }) {
            return refuse("Direct CorePrimitive packets must share one exact builder uniform32 slab seal.")
        }
        val analyticSeals = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                packetAuthorities.map { authority ->
                    authority.analyticClipUniformSeal
                        ?: return refuseAnalytic("Analytic direct CorePrimitive packet is missing its uniform64 seal.")
                }
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
            -> emptyList()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before analytic uniform authority selection")
        }
        val analyticIntersectionSeals = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                packetAuthorities.map { authority ->
                    authority.analyticIntersectionUniformSeal
                        ?: return refuseIntersection("Analytic intersection packet is missing its uniform160 seal.")
                }
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1,
            -> emptyList()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before intersection uniform authority selection")
        }
        val uniformPlan = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                requireNotNull(uniformSeal).plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                analyticSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                analyticIntersectionSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct uniform plan selection")
        }
        val maxBufferSize = limits.maxBufferSize ?: return diagnostic(
            "unsupported.native-core-primitive.max-buffer-size-unavailable",
            "Direct CorePrimitive requires observed maxBufferSize.",
        )
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return diagnostic(
            "unsupported.native-core-primitive.dynamic-uniform-limit-unavailable",
            "Direct CorePrimitive requires the observed dynamic-uniform limit.",
        )
        if (uniformPlan.deviceGeneration != context.deviceGeneration.value ||
            uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformPlan.totalBytes > maxBufferSize || maxDynamicUniformBuffers < 1L ||
            when (uniformLayout) {
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                    requireNotNull(uniformSeal).drawCount != accepted.size || accepted.indices.any { indexAt ->
                        val entry = accepted[indexAt]
                        val bytes = entry.semantic.payloadRef.uniformBlock?.bytes
                            ?: return diagnostic(
                                "invalid.preflight.core_primitive_semantic_integrity",
                                "Core primitive packet authority contradicts its immutable semantic input.",
                            )
                        !uniformSeal.hasExactPayload(indexAt, entry.packet.commandIdValue, bytes)
                    }
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
                -> false
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                    error("NoBindingsV1 was refused before direct uniform validation")
            }
        ) {
            return refuse("Direct CorePrimitive builder uniform slab seal contradicts current packet or limit authority.")
        }
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1) {
            if (uniformPlan.sourceLabel != "core-primitive-analytic-clip-uniform-pass" ||
                uniformPlan.slots.size != accepted.size ||
                analyticSeals.any { seal -> seal.plan !== uniformPlan }
            ) {
                return refuseAnalytic("Analytic direct CorePrimitive packets must share one exact uniform64 slab plan.")
            }
            val exactPayloads = mutableListOf<GPUUniformSlabPayload>()
            accepted.indices.forEach { indexAt ->
                val entry = accepted[indexAt]
                val packetClip = corePrimitiveAnalyticClipPacketAuthority(
                    entry.packet,
                    entry.semantic.targetBounds,
                )
                    ?: return refuseAnalytic("Analytic direct CorePrimitive clip authority is no longer canonical.")
                val expectedClip = packetClip.clip
                val seal = analyticSeals[indexAt]
                val slot = uniformPlan.slots[indexAt]
                val expectedBytes = corePrimitiveAnalyticClipUniformBytes(entry.semantic, expectedClip)
                val exactRange = try {
                    Math.addExact(slot.alignedOffset, 64L) <= uniformPlan.totalBytes
                } catch (_: ArithmeticException) {
                    false
                }
                if (seal.slotIndex != indexAt || seal.commandId != entry.packet.commandIdValue ||
                    seal.packetId != entry.packet.packetId ||
                    seal.clipCanonicalIdentity != packetClip.canonicalIdentity ||
                    seal.clipType != expectedClip.clipType || seal.clipBounds != expectedClip.bounds ||
                    seal.clipRadii != expectedClip.radii || seal.antiAlias != expectedClip.antiAlias ||
                    seal.conservativeScissor != expectedClip.conservativeScissor ||
                    entry.semantic.scissorBounds != expectedClip.conservativeScissor ||
                    seal.structuralPipelineKey != packetAuthorities[indexAt].structuralPipelineKey ||
                    seal.renderPipelineKey != packetAuthorities[indexAt].renderPipelineKey ||
                    seal.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH ||
                    entry.packet.bindingLayoutHash != seal.bindingLayoutHash ||
                    seal.resourceGeneration != entry.packet.resourceGeneration ||
                    seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                    seal.deviceGeneration != context.deviceGeneration.value ||
                    seal.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                    seal.payloadBytes != 64L || slot.payloadBytes != 64L ||
                    slot.slotLabel != "analytic-clip-draw-${entry.packet.commandIdValue}" ||
                    seal.alignedOffset != slot.alignedOffset || slot.alignedOffset > UInt.MAX_VALUE.toLong() ||
                    !exactRange || !seal.hasExactPayload(expectedBytes)
                ) {
                    return refuseAnalytic(
                        "Analytic direct CorePrimitive uniform64 seal contradicts packet, clip, layout, or generation authority.",
                    )
                }
                exactPayloads += GPUUniformSlabPayload(slot.slotLabel, expectedBytes)
            }
            if (!uniformPlan.hasExactPayloads(
                    "core-primitive-analytic-clip-uniform-pass",
                    context.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    exactPayloads,
                )
            ) {
                return refuseAnalytic(
                    "Analytic direct CorePrimitive uniform64 slab plan, slots, offsets, or hashes are not exact.",
                )
            }
        }
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1) {
            if (uniformPlan.sourceLabel != "core-primitive-analytic-intersection-uniform-pass" ||
                uniformPlan.slots.size != accepted.size ||
                analyticIntersectionSeals.any { seal -> seal.plan !== uniformPlan }
            ) {
                return refuseIntersection("Analytic intersection packets must share one exact uniform160 slab plan.")
            }
            val exactPayloads = mutableListOf<GPUUniformSlabPayload>()
            accepted.indices.forEach { indexAt ->
                val entry = accepted[indexAt]
                val packetClip = corePrimitiveAnalyticIntersectionPacketAuthority(
                    entry.packet,
                    entry.semantic.targetBounds,
                ) ?: return refuseIntersection("Analytic intersection authority is no longer canonical.")
                val expectedClip = packetClip.clip
                val seal = analyticIntersectionSeals[indexAt]
                val slot = uniformPlan.slots[indexAt]
                val expectedBytes = corePrimitiveAnalyticIntersectionUniformBytes(entry.semantic, expectedClip)
                val exactRange = try {
                    Math.addExact(slot.alignedOffset, 160L) <= uniformPlan.totalBytes
                } catch (_: ArithmeticException) {
                    false
                }
                val exactElements = seal.elements.size == expectedClip.elements.size &&
                    seal.elements.indices.all { elementIndex ->
                        val actual = seal.elements[elementIndex]
                        val expected = expectedClip.elements[elementIndex]
                        actual.clipType == expected.clipType && actual.clipBounds == expected.bounds &&
                            actual.clipRadii == expected.packedRadii && actual.antiAlias == expected.antiAlias
                    }
                if (seal.slotIndex != indexAt || seal.commandId != entry.packet.commandIdValue ||
                    seal.packetId != entry.packet.packetId ||
                    seal.clipCanonicalIdentity != packetClip.canonicalIdentity || !exactElements ||
                    seal.conservativeScissor != expectedClip.conservativeScissor ||
                    entry.semantic.scissorBounds != expectedClip.conservativeScissor ||
                    seal.structuralPipelineKey != packetAuthorities[indexAt].structuralPipelineKey ||
                    seal.renderPipelineKey != packetAuthorities[indexAt].renderPipelineKey ||
                    seal.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH ||
                    entry.packet.bindingLayoutHash != seal.bindingLayoutHash ||
                    seal.resourceGeneration != entry.packet.resourceGeneration ||
                    seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                    seal.deviceGeneration != context.deviceGeneration.value ||
                    seal.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                    seal.payloadBytes != 160L || slot.payloadBytes != 160L ||
                    slot.slotLabel != "analytic-intersection-draw-${entry.packet.commandIdValue}" ||
                    seal.alignedOffset != slot.alignedOffset || slot.alignedOffset > UInt.MAX_VALUE.toLong() ||
                    !exactRange || !seal.hasExactPayload(expectedBytes)
                ) {
                    return refuseIntersection(
                        "Analytic intersection uniform160 seal contradicts packet, clip, layout, or generation authority.",
                    )
                }
                exactPayloads += GPUUniformSlabPayload(slot.slotLabel, expectedBytes)
            }
            if (!uniformPlan.hasExactPayloads(
                    "core-primitive-analytic-intersection-uniform-pass",
                    context.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    exactPayloads,
                )
            ) {
                return refuseIntersection(
                    "Analytic intersection uniform160 slab plan, slots, offsets, or hashes are not exact.",
                )
            }
        }
        val analyticPackedBytes = if (
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
        ) {
            if (uniformPlan.totalBytes > Int.MAX_VALUE.toLong()) {
                return refuseAnalytic(
                    "Analytic direct CorePrimitive uniform64 slab exceeds the host-addressable packed size.",
                )
            }
            try {
                ByteArray(uniformPlan.totalBytes.toInt()).also { packed ->
                    analyticSeals.forEach { seal ->
                        seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
                    }
                }
            } catch (_: Throwable) {
                return refuseAnalytic(
                    "Analytic direct CorePrimitive uniform64 packet ranges cannot form one exact packed slab.",
                )
            }
        } else {
            null
        }
        val analyticIntersectionPackedBytes = if (
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1
        ) {
            if (uniformPlan.totalBytes > Int.MAX_VALUE.toLong()) {
                return refuseIntersection("Analytic intersection uniform160 slab exceeds the host-addressable packed size.")
            }
            try {
                ByteArray(uniformPlan.totalBytes.toInt()).also { packed ->
                    analyticIntersectionSeals.forEach { seal ->
                        seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
                    }
                }
            } catch (_: Throwable) {
                return refuseIntersection(
                    "Analytic intersection uniform160 packet ranges cannot form one exact packed slab.",
                )
            }
        } else {
            null
        }
        if (uniformDescriptor.byteSize != uniformPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformPlan.alignmentBytes ||
            uniform.byteSize != uniformPlan.totalBytes ||
            uniform.usages != setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform) ||
            uniform.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refuse("Direct CorePrimitive uniform slab descriptor, size, alignment, usages, or lifetime is not exact.")
        }
        if (context.resourceGenerations[vertex.resource] == null ||
            context.resourceGenerations[index.resource] == null ||
            context.resourceGenerations[uniform.resource] == null
        ) {
            return refuse("Direct CorePrimitive shared slabs require current resource-generation evidence.")
        }
        val exactUses = setOf(
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
        if (directUsesByRender.any { (render, uses) ->
                if (render == directRender) uses.size != 3 || uses.toSet() != exactUses else uses.isNotEmpty()
            }
        ) {
            return refuse("The direct pass must read exactly all three shared slabs; non-direct draws may read none.")
        }
        retainAcceptedRoutes(
            accepted.associate { entry ->
                GPUCorePrimitiveDirectNativeFrameRouteKey(entry.sourceStepIndex, entry.packet.packetId) to
                    entry.route
            },
            mapOf(
                accepted.first().sourceStepIndex to GPUCorePrimitiveDirectPreparedPassSeal(
                    structuralPipelineKey = structuralPipelineKey,
                    uniformSlabSeal = uniformSeal,
                    analyticClipUniformSeals = analyticSeals,
                    analyticClipPackedBytes = analyticPackedBytes,
                    analyticIntersectionUniformSeals = analyticIntersectionSeals,
                    analyticIntersectionPackedBytes = analyticIntersectionPackedBytes,
                ),
            ),
        )
        return null
    }

    private fun validateCorePrimitiveRenderAuthority(framePlan: GPUFramePlan): GPUDiagnostic? {
        val invalidRender = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .firstOrNull { render ->
                render.samplePlan != GPUSamplePlan.SingleSampleFrame &&
                    render.drawPackets.any { packet ->
                        packet.renderStepId.value == CORE_PRIMITIVE_RENDER_STEP_IDENTITY
                    }
            }
        return invalidRender?.let {
            diagnostic(
                "invalid.preflight.core_primitive_render_authority",
                "Core primitive render passes require the canonical single-sample plan.",
            )
        }
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
        clipStencilPreparedRouteSeal:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
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
                CORE_PRIMITIVE_RENDER_STEP_IDENTITY -> diagnostic(
                    "invalid.preflight.core_primitive_semantic_payload_missing",
                    "Executable core primitive packets require their gathered semantic payload.",
                )
                else -> null
            }
        }
        return when (semantic) {
            is GPUDrawSemanticPayload.SolidRect -> validateSolidRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.CorePrimitive ->
                validateCorePrimitiveSemanticPayload(
                    framePlan,
                    render,
                    packet,
                    semantic,
                    clipStencilPreparedRouteSeal,
                )
            is GPUDrawSemanticPayload.RegisteredUniformRect ->
                validateRegisteredUniformRectSemanticPayload(render, packet, semantic)
            is GPUDrawSemanticPayload.SeparableBlurRect ->
                validateSeparableBlurRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.ColorGlyph ->
                validateColorGlyphSemanticPayload(framePlan, render, packet, semantic)
        }
    }

    private fun validateCorePrimitiveSemanticPayload(
        framePlan: GPUFramePlan,
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        clipStencilPreparedRouteSeal:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
    ): GPUDiagnostic? {
        val clipExecutionPlan = packet.clipExecutionPlan
        if (packet.renderStepId.value != CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
            semantic.payloadRef.renderStepIdentity != CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
            packet.commandIdValue != semantic.payloadRef.commandIdValue ||
            packet.uniformSlot != semantic.payloadRef.uniformSlot ||
            packet.clipCoveragePlan != semantic.clipCoveragePlan ||
            packet.blendPlan?.canonicalIdentity() != semantic.blendPlanIdentity ||
            packet.frameProvenance != semantic.frameProvenance ||
            clipExecutionPlan == null ||
            semantic.clipExecutionPlanIdentity != clipExecutionPlan.canonicalIdentity() ||
            !semantic.hasStructuralIntegrity()
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_semantic_integrity",
                "Core primitive packet authority contradicts its immutable semantic input.",
            )
        }
        if ((packet.role == GPUDrawPacketRole.PathStencilProducer ||
                packet.role == GPUDrawPacketRole.PathStencilCover) &&
            !clipExecutionPlan.isCorePrimitiveNoClipOrScissorExecution()
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_path_stencil",
                "Path stencil currently accepts only no clip or dynamic scissor.",
            )
        }
        val blendPlan = requireNotNull(packet.blendPlan)
        val preparedAuthority = packet.corePrimitivePreparedAuthority
        val expectedStructuralPipelineKey = when (packet.role) {
            GPUDrawPacketRole.Shading -> {
                val clipStencilConsumerSeal =
                    (clipStencilPreparedRouteSeal as?
                        GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route)
                        ?.retainedFor(
                            framePlan.steps.indexOf(render),
                            listOf(packet.packetId),
                        ) as? GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                if (clipStencilConsumerSeal != null) {
                    clipStencilConsumerSeal.route.consumers.single { consumer ->
                        consumer.commandId == packet.commandIdValue &&
                            consumer.sourceOrder == packet.originalPaintOrder
                    }.structuralKey
                } else {
                    corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        clipExecutionPlan,
                        blendPlan,
                    ).let { structuralKey ->
                        if (render.resourceUses.any {
                                it.role == GPUFrameResourceRole.PathDepthStencil
                            }
                        ) {
                            structuralKey.copy(
                                depthStencil = corePrimitiveDirectPathDepthStencilState(),
                            )
                        } else {
                            structuralKey
                        }
                    }
                }
            }
            GPUDrawPacketRole.PathStencilProducer ->
                corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                    clipExecutionPlan,
                    blendPlan,
                )
            GPUDrawPacketRole.PathStencilCover ->
                corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                    clipExecutionPlan,
                    blendPlan,
                )
            else -> return diagnostic(
                "invalid.preflight.core_primitive_packet_authority",
                "Core primitive executable packet role is not a sealed native route role.",
            )
        }
        val expectedBindingLayoutHash = when (expectedStructuralPipelineKey.uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                CORE_PRIMITIVE_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 -> return diagnostic(
                "unsupported.native-core-primitive.no-bindings-direct-route",
                "The no-bindings clip-stencil producer is not an executable direct packet role.",
            )
        }
        if (preparedAuthority == null ||
            preparedAuthority.structuralPipelineKey != expectedStructuralPipelineKey ||
            packet.renderPipelineKey != preparedAuthority.renderPipelineKey ||
            preparedAuthority.renderPipelineKey !=
            expectedStructuralPipelineKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY) ||
            packet.renderStepVersion != 1 ||
            packet.bindingLayoutHash != expectedBindingLayoutHash ||
            packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
            packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
            packet.scissorBoundsHash != corePrimitiveScissorAuthority(semantic.scissorBounds)
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_packet_authority",
                "Core primitive executable packet fields contradict the canonical route authority.",
            )
        }
        val targetPreparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.resource == render.target }
        val targetPreparation = targetPreparations.singleOrNull()
        if (targetPreparation == null ||
            !isCanonicalCorePrimitiveTargetPreparation(targetPreparation, render.target, semantic.targetBounds)
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_target_authority",
                "Core primitive target preparation must match the complete canonical target authority.",
            )
        }
        return null
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
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
    ): GPUResourceMaterializationDecision {
        val packets = framePlan.steps.mapIndexedNotNull { sourceStepIndex, step ->
            (step as? GPUFrameStep.RenderPassStep)?.let { render ->
                render.drawPackets.map { packet -> sourceStepIndex to packet }
            }
        }.flatten()
        if (packets.isEmpty()) {
            return GPUResourceMaterializationDecision.Materialized(resources = emptyList(), targetId = context.targetId)
        }
        return try {
            val operands = packets.flatMap { (sourceStepIndex, packet) ->
                plannedRenderOperands(sourceStepIndex, packet, ownerScope, corePrimitiveDirectRoutes)
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

    private fun plannedRenderOperands(
        sourceStepIndex: Int,
        packet: GPUDrawPacket,
        ownerScope: String,
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
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
        val directCore = corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId)
        if (colorGlyph != null || directCore != null) {
            add(
                operand(
                    packet,
                    "setVertexBuffer",
                    GPUMaterializedCommandOperandKind.VertexBuffer,
                    if (colorGlyph != null) {
                        "${colorGlyph.canonicalHash}.vertices.${colorGlyph.vertexData.size}"
                    } else {
                        "core-direct.$sourceStepIndex.${packet.packetId.value}.vertices." +
                            requireNotNull(directCore).vertexCount * 2
                    },
                    ownerScope,
                ),
            )
            add(
                operand(
                    packet,
                    "setIndexBuffer",
                    GPUMaterializedCommandOperandKind.IndexBuffer,
                    if (colorGlyph != null) {
                        "${colorGlyph.canonicalHash}.indices.${colorGlyph.indexData.size}"
                    } else {
                        "core-direct.$sourceStepIndex.${packet.packetId.value}.indices." +
                            requireNotNull(directCore).indexCount
                    },
                    ownerScope,
                ),
            )
        }
    }

    private fun validateRenderOperands(
        framePlan: GPUFramePlan,
        materialized: GPUResourceMaterializationDecision.Materialized,
        ownerScope: String,
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
    ): GPUDiagnostic? {
        val packets = framePlan.steps.mapIndexedNotNull { sourceStepIndex, step ->
            (step as? GPUFrameStep.RenderPassStep)?.let { render ->
                render.drawPackets.map { packet -> sourceStepIndex to packet }
            }
        }.flatten()
        val bridge = materialized.operandBridge
        val expectedTasks = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { it.sourceTaskIds }.map { it.value }.distinct()
        val expectedPlans = packets.flatMap { (sourceStepIndex, packet) ->
            plannedRenderOperands(sourceStepIndex, packet, ownerScope, corePrimitiveDirectRoutes)
        }
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
        var bridgeOffset = 0
        for ((sourceStepIndex, packet) in packets) {
            val expected = plannedRenderOperands(
                sourceStepIndex,
                packet,
                ownerScope,
                corePrimitiveDirectRoutes,
            )
            val packetBridge = bridge.subList(bridgeOffset, bridgeOffset + expected.size)
            bridgeOffset += expected.size
            val pipelines = packetBridge.filter { it.commandLabel == "setRenderPipeline" && it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline }
            val bindGroups = packetBridge.filter { it.commandLabel == "setBindGroup" && it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup }
            val vertices = packetBridge.filter { it.commandLabel == "setVertexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.VertexBuffer }
            val indices = packetBridge.filter { it.commandLabel == "setIndexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.IndexBuffer }
            val indexedPayload = packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId) != null
            if (pipelines.size != 1 || bindGroups.size != 1 ||
                (indexedPayload && (vertices.size != 1 || indices.size != 1)) ||
                (!indexedPayload && (vertices.isNotEmpty() || indices.isNotEmpty()))
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
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        corePrimitivePathStencilRoutes: GPUCorePrimitivePathStencilNativeFrameRouteSeal,
        corePrimitiveNativeScopeRoutes: GPUCorePrimitiveNativeScopeFrameRouteSeal,
        corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
    ): List<GPUCommandEncoderScopePlan> {
        val renderBridgesByStepIndex = buildMap {
            var bridgeOffset = 0
            framePlan.steps.forEachIndexed { sourceStepIndex, step ->
                val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
                val operandCount = render.drawPackets.sumOf { packet ->
                    2 + if (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                        corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId) != null
                    ) {
                        2
                    } else {
                        0
                    }
                }
                put(
                    sourceStepIndex,
                    materialized.operandBridge.subList(bridgeOffset, bridgeOffset + operandCount),
                )
                bridgeOffset += operandCount
            }
            check(bridgeOffset == materialized.operandBridge.size) {
                "Render operand bridge partition does not cover the exact frame"
            }
        }
        return framePlan.steps.mapIndexedNotNull { index, step ->
        val labels = referencedResources(step).map { ref ->
            "${ref::class.simpleName}:${ref.value}@${requireNotNull(generations[ref]) { "generation missing for ${ref.value}" }}"
        }
        when (step) {
            is GPUFrameStep.RenderPassStep -> {
                val stepCorePrimitiveDirectRoutes = corePrimitiveDirectRoutes.retainedFor(
                    index,
                    step.drawPackets
                        .filter { packet ->
                            corePrimitiveDirectRoutes.routeOrNull(index, packet.packetId) != null
                        }
                        .map { it.packetId },
                )
                val corePacketIds = step.drawPackets
                    .filter { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
                    .map { it.packetId }
                val stepCorePrimitivePathStencilRoutes = corePrimitivePathStencilRoutes.retainedFor(
                    index,
                    step.drawPackets
                        .filter { packet ->
                            packet.role == GPUDrawPacketRole.PathStencilProducer ||
                                packet.role == GPUDrawPacketRole.PathStencilCover
                        }
                        .map { it.packetId },
                )
                val stepCorePrimitiveNativeScopeRoutes = corePrimitiveNativeScopeRoutes.retainedFor(
                    index,
                    if (corePrimitiveNativeScopeRoutes.hasRouteForStep(index)) {
                        corePacketIds
                    } else {
                        emptyList()
                    },
                )
                val stepCorePrimitiveClipStencilPreparedRoutes =
                    when (corePrimitiveClipStencilPreparedRoutes) {
                        GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty ->
                            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
                        is GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route ->
                            corePrimitiveClipStencilPreparedRoutes.retainedFor(
                                index,
                                step.drawPackets.map(GPUDrawPacket::packetId),
                            )
                    }
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
                val stepBridge = renderBridgesByStepIndex.getValue(index)
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
                    scopeLabel = "step.$index",
                    sourceTaskIds = step.sourceTaskIds,
                    sourcePacketIds = step.drawPackets.map { it.packetId },
                    facadeOperationClasses = stream.commandLabels,
                    targetGeneration = context.targetGeneration,
                    resourceGenerationLabels = labels,
                    passCommandStream = stream,
                    corePrimitiveDirectNativeRouteSeal = stepCorePrimitiveDirectRoutes,
                    corePrimitivePathStencilNativeRouteSeal = stepCorePrimitivePathStencilRoutes,
                    corePrimitiveNativeScopeRouteSeal = stepCorePrimitiveNativeScopeRoutes,
                    corePrimitiveClipStencilPreparedRouteSeal =
                        stepCorePrimitiveClipStencilPreparedRoutes,
                ).attachNativeOperandKeys(
                    nativeOperandKeys(
                        step,
                        labels,
                        stream,
                        stepCorePrimitiveDirectRoutes,
                        stepCorePrimitiveNativeScopeRoutes,
                    ),
                )
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
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeRouteSeal =
            GPUCorePrimitiveDirectNativeRouteSeal.Empty,
        corePrimitiveNativeScopeRoutes: GPUCorePrimitiveNativeScopeRouteSeal =
            GPUCorePrimitiveNativeScopeRouteSeal.Empty,
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
                val directCore = corePrimitiveDirectRoutes is GPUCorePrimitiveDirectNativeRouteSeal.Routes
                val indexedCore = corePrimitiveNativeScopeRoutes is GPUCorePrimitiveNativeScopeRouteSeal.Routes
                val pathCore = indexedCore && step.drawPackets.any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilProducer ||
                        packet.role == GPUDrawPacketRole.PathStencilCover
                }
                val drawOperandOwnership = if (colorGlyph) {
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
                } else {
                    GPUPreparedNativeOperandOwnership.Borrowed
                }
                val streamBridges = requireNotNull(stream).operandBridge
                val nativeBridges = if (pathCore) {
                    val pipelineBridges = streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
                    }
                    check(pipelineBridges.size == step.drawPackets.size) {
                        "Indexed CorePrimitive pipelines must retain exact packet-order evidence"
                    }
                    pipelineBridges.zip(step.drawPackets)
                        .distinctBy { (_, packet) -> packet.renderPipelineKey }
                        .map { (bridge, _) -> bridge } + streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                    }
                } else if (directCore) {
                    listOfNotNull(
                        streamBridges.firstOrNull {
                            it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
                        },
                        streamBridges.firstOrNull {
                            it.operand.kind == GPUMaterializedCommandOperandKind.VertexBuffer
                        },
                        streamBridges.firstOrNull {
                            it.operand.kind == GPUMaterializedCommandOperandKind.IndexBuffer
                        },
                    ) + streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                    }
                } else {
                    streamBridges
                }
                val targetKeys = step.sampleContinuation?.let { continuation ->
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
            )
                val depthStencilKeys = if (pathCore) {
                    step.resourceUses.withIndex().singleOrNull { (_, use) ->
                        use.role == GPUFrameResourceRole.PathDepthStencil
                    }?.let { (resourceIndex, _) ->
                        listOf(
                            key(
                                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                resources[resourceIndex + 1],
                            ),
                        )
                    } ?: emptyList()
                } else {
                    emptyList()
                }
                fun bridgeKey(
                    bridge: org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge,
                ): GPUPreparedNativeOperandKey = when (bridge.operand.kind) {
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline ->
                        key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            GPUPreparedNativeOperandOwnership.Borrowed,
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
                if (pathCore) {
                    val pipelineKeys = nativeBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
                    }.map(::bridgeKey)
                    fun sharedGeometryKey(
                        resourceRole: GPUFrameResourceRole,
                        operandRole: GPUPreparedNativeOperandRole,
                    ): GPUPreparedNativeOperandKey {
                        val resourceIndex = step.resourceUses.indexOfFirst { it.role == resourceRole }
                        check(resourceIndex >= 0) {
                            "Indexed CorePrimitive scope is missing its shared $resourceRole resource"
                        }
                        return key(
                            operandRole,
                            GPUPreparedNativeOperandKind.Buffer,
                            resources[resourceIndex + 1],
                        )
                    }
                    val geometryKeys = listOf(
                        sharedGeometryKey(
                            GPUFrameResourceRole.VertexData,
                            GPUPreparedNativeOperandRole.RenderVertexBuffer,
                        ),
                        sharedGeometryKey(
                            GPUFrameResourceRole.IndexData,
                            GPUPreparedNativeOperandRole.RenderIndexBuffer,
                        ),
                    )
                    val bindGroupKeys = nativeBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                    }.map(::bridgeKey)
                    targetKeys + depthStencilKeys + pipelineKeys + geometryKeys + bindGroupKeys
                } else {
                    targetKeys + nativeBridges.map { bridge ->
                when (bridge.operand.kind) {
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline ->
                        key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "${bridge.commandLabel}:${bridge.operand.label}",
                            GPUPreparedNativeOperandOwnership.Borrowed,
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
