package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.materials.w5aMaterialAllocationsV2
import org.graphiks.kanvas.gpu.renderer.materials.w5aCombinedMemoryBudgetV2

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.isW5bW3Blend
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageSampleAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.W3SessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.W4aSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.W4bSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.W5aAnalyticRRectSessionScratchV2
import org.graphiks.kanvas.gpu.renderer.passes.W5aAnalyticRectSessionScratchV2
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.w4dGeneralCoverageMaskConsumerUniform64
import org.graphiks.kanvas.gpu.renderer.passes.GPUPreparedImageClipAuthorityValidation
import org.graphiks.kanvas.gpu.renderer.passes.validateGPUCorePrimitiveCoverageMaskPreparedAuthority
import org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveCoverageSampleAuthority
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveGradientAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.isCorePrimitiveNoClipOrScissorExecution
import org.graphiks.kanvas.gpu.renderer.passes.hasExactCorePrimitivePathClipPair
import org.graphiks.kanvas.gpu.renderer.passes.hasAnalyticCorePrimitivePathClipPair
import org.graphiks.kanvas.gpu.renderer.passes.hasSupportedCorePrimitivePathClip
import org.graphiks.kanvas.gpu.renderer.passes.validatePreparedImageClipAuthority
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
import org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformByteSize
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformBytes
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_PACKET_PASS_AUTHORITY_CODE
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_PACKET_PASS_AUTHORITY_MESSAGE
import org.graphiks.kanvas.gpu.renderer.recording.preparedColorGlyphPacketAuthorityRefusal
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveGradientBindingLayoutHash
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL
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
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalCorePrimitiveTargetPreparation
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectStage
import org.graphiks.kanvas.gpu.renderer.passes.validateCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.hasExactNoClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectRenderStepId
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.preparedTextNativeBlendDomainRefusal
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.math.geometry.RectI32
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.render.ir.DrawOrigin
import kotlin.math.ceil
import kotlin.math.floor

private const val solidRectRenderStepIdentity = "rect.fill.coverage"

private fun GPUColorFormat.corePrimitiveInterpretationOrNull(): GPUColorInterpretation? = when (this) {
    GPUColorFormat.RGBA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    GPUColorFormat.RGBA8UnormSrgb -> GPUColorInterpretation.LinearPremul
    GPUColorFormat.BGRA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    else -> null
}

/**
 * A W4a seal is authoritative even when a forged envelope drops its scratch reference.
 * This must remain independent of generic CorePrimitive route classification.
 */
internal fun GPUFramePlan.hasSealedW4aSessionMarker(): Boolean =
    recordingSeals.any { seal ->
        seal.compatibilityKeyHash.startsWith("w4a:") || seal.replayKeyHash.startsWith("w4a:")
    } ||
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().any { render ->
            render.drawPackets.any { packet ->
                packet.packetId.value.startsWith("packet.w4a.") ||
                    packet.passId == "pass.w4a.main" ||
                    packet.bindingListId.startsWith("binding.w4a.") ||
                    packet.insertionReasonCode == "w4a-analytic-rect" ||
                    packet.corePrimitivePreparedAuthority?.w4aSessionScratch != null
            }
        }

/** W4b is an independent sealed RRect lane and must be checked before generic classification. */
internal fun GPUFramePlan.hasSealedW4bSessionMarker(): Boolean =
    recordingSeals.any { seal ->
        seal.compatibilityKeyHash.startsWith("w4b:") || seal.replayKeyHash.startsWith("w4b:")
    } ||
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().any { render ->
            render.drawPackets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4bSessionScratch != null
            }
        }

/** W4c owns a closed multi-render path-fill envelope and must bypass generic path classification. */
internal fun GPUFramePlan.hasSealedW4cSessionMarker(): Boolean =
    recordingSeals.any { seal ->
        seal.compatibilityKeyHash.startsWith("w4c:") || seal.replayKeyHash.startsWith("w4c:")
    } ||
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().any { render ->
            render.drawPackets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4cSessionScratch != null
            }
        }

/** W4d owns the same closed native path ABI with authenticated stroke geometry authority. */
internal fun GPUFramePlan.hasSealedW4dSessionMarker(): Boolean =
    recordingSeals.any { seal ->
        seal.compatibilityKeyHash.startsWith("w4d:") || seal.replayKeyHash.startsWith("w4d:")
    } ||
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().any { render ->
            render.drawPackets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4dSessionScratch != null
            }
        }

/** Sole transactional join between an immutable semantic frame and materialized resource facts. */
internal class GPUFramePreflighter(
    private val context: GPUFramePreflightContext,
    capabilities: GPUCapabilities,
    private val resourceProvider: GPUFrameResourcePreflightProvider,
    private val completionProvider: GPUQueueCompletionProvider,
    private val surfaceProvider: GPUSurfaceOutputProvider,
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
    private val nativeBoundary: GPUPreparedNativeFrameBoundary? = null,
    private val nominalEncoderScopeObserver: ((List<GPUCommandEncoderScopePlan>) -> Unit)? = null,
) {
    private val capabilities: GPUCapabilities = capabilities.preflightSnapshot()

    private fun GPUFrameResourceUse.referencesW4eLogicalResource(resourceId: String): Boolean =
        resource.value == resourceId || resource.value.endsWith(".$resourceId")

    private fun GPUFrameResourceRef.referencesW4eLogicalResource(resourceId: String): Boolean =
        value == resourceId || value.endsWith(".$resourceId")

    fun preflight(framePlan: GPUFramePlan): GPUFramePreflightResult {
        val w5b = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .mapNotNull { it.w5bFinalFrameWitnessV3 }.firstOrNull()
        if (w5b != null) {
            val limits = capabilities.limits
            val materialBytes = framePlan.w5aMaterialAllocationsV2()
            val readsDestination = w5b.graph.passes().any { it is org.graphiks.kanvas.gpu.plan.PlanPass.TextureCopy }
            val hasClip = w5b.clipPrefixV4 != null
            if (limits == null || limits.maxBindGroupsI32?.let { it >= if (hasClip) 4 else if (readsDestination) 3 else 2 } != true ||
                limits.maxBindingsPerBindGroupI32?.let { it >= if (readsDestination) 2 else 1 } != true ||
                (readsDestination && limits.maxSamplersPerShaderStageI32?.let { it >= 1 } != true) ||
                (readsDestination && limits.maxSampledTexturesPerShaderStageI32?.let { it >= if (hasClip) 2 else 1 } != true) ||
                limits.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } != true ||
                limits.maxUniformBufferBindingSizeBytesI64 == null ||
                materialBytes.any { it.bytes > limits.maxUniformBufferBindingSizeBytesI64 } || !w5b.validates(framePlan)) {
                return GPUFramePreflightResult.Refused(diagnostic("unsupported.preflight.w5b-abi", "W5b source/destination ABI facts are unavailable or stale."))
            }
            val physicalBytes = try {
                val capacities = w5b.scratch.poolCapacities
                (listOf(w5b.graph.peakFrameLocalBytes) +
                    (if (w5b.graph.w5bGeometryLanes().isEmpty() && w5b.scratch is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.Direct)
                        listOf(capacities.vertexBytes, capacities.indexBytes, capacities.uniformBytes) else emptyList()) +
                    materialBytes.map { it.bytes }).fold(0L, Math::addExact)
            } catch (_: ArithmeticException) {
                return GPUFramePreflightResult.Refused(diagnostic("resource.preflight.w5b-overflow", "W5b native inventory arithmetic overflow."))
            }
            if (physicalBytes > w5b.graph.budget.maxFrameLocalBytes) return GPUFramePreflightResult.Refused(
                diagnostic("resource.preflight.w5b-budget", "W5b native snapshot/source/scratch inventory exceeds the frame budget."))
        }
        framePlan.w5aMaterialAllocationsV2().takeIf { it.isNotEmpty() }?.let { allocations ->
            val limits = capabilities.limits ?: return GPUFramePreflightResult.Refused(
                diagnostic("unsupported.preflight.w5a-source-limits", "W5a source bindings require observed device limits."))
            // 16 KiB is the portable WebGPU uniform-binding floor. W5a stays inside it.
            if (allocations.any { it.bytes > minOf(limits.maxBufferSize ?: 0L, 16_384L) }) {
                return GPUFramePreflightResult.Refused(diagnostic(
                    "resource.material.w5a.binding-limit", "W5a raw source binding exceeds its bounded uniform ABI."))
            }
            framePlan.w5aCombinedMemoryBudgetV2(limits).diagnostic?.let {
                return GPUFramePreflightResult.Refused(it)
            }
        }
        framePlan.steps.filterIsInstance<GPUFrameStep.RefusedLeafDrawStep>()
            .firstOrNull { step ->
                step.diagnostic.code.value ==
                    PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE
            }
            ?.let { step -> return GPUFramePreflightResult.Refused(step.diagnostic) }
        if (nativeBoundary != null && nativeBoundary.resourceProvider !== resourceProvider) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.native_payload_provider_mismatch",
                    "Native payload boundary does not own the exact resource provider used by preflight.",
                ),
            )
        }
        val allW4eCandidateRenders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val pointComposite = allW4eCandidateRenders.flatMap { it.drawPackets }
            .mapNotNull { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 }.firstOrNull()?.takeIf { it.clipPrefixV4 != null }
        if (pointComposite != null && !pointComposite.validates(framePlan)) return GPUFramePreflightResult.Refused(
            diagnostic("invalid.preflight.w5b_clip_v4", "W5b clip-only composition does not match its complete sealed graph."))
        val w4eRenders = when {
            w5b?.w4eLane != null -> allW4eCandidateRenders.filter { step -> step.drawPackets.any(w5b.w4eLane::owns) }
            pointComposite != null -> allW4eCandidateRenders.take(requireNotNull(pointComposite.clipPrefixV4).renders.size)
            else -> allW4eCandidateRenders
        }
        val w4ePackets = w4eRenders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .filter { packet -> packet.role == GPUDrawPacketRole.W4ePrepared }
        if (w4ePackets.isNotEmpty()) {
            val authority = w4ePackets.first().w4ePreparedFrameAuthority
            val texturePreparations = framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .associate { request -> request.resource to (request.descriptor as? GPUFrameTextureDescriptor) }
            if (w4ePackets.size != w4eRenders.size || authority == null ||
                !authority.validatesRenderSteps(
                    framePlan.frameId.value,
                    framePlan.capabilitySeal.sealHash,
                    w4eRenders,
                )
            ) {
                return GPUFramePreflightResult.Refused(
                    diagnostic(
                        "invalid.preflight.w4e_frame_authority",
                        "W4e preflight requires one sealed graph, frame, target, resource-use, order, and atomic-group authority.",
                    ),
                )
            }
            w4eRenders.firstOrNull { render ->
                val continuation = render.w4eMaskContinuation ?: return@firstOrNull false
                val producer = render.drawPackets.single().w4ePreparedClipPass as?
                    org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipPassAuthority.Producer
                    ?: return@firstOrNull true
                producer.sampleCount != 4 ||
                    producer.targetResourceId != continuation.maskTargetResourceId ||
                    producer.resolveTargetResourceId != continuation.resolveMaskResourceId ||
                    render.resourceUses.singleOrNull { use ->
                        use.referencesW4eLogicalResource(continuation.maskTargetResourceId) &&
                            use.role == GPUFrameResourceRole.ClipMask &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                    }?.let { use ->
                        texturePreparations[use.resource]?.let { descriptor ->
                            descriptor.format == GPUColorFormat.RGBA8Unorm && descriptor.sampleCount == 4
                        } == true
                    } != true || (continuation.resolveMaskResourceId != null &&
                    render.resourceUses.singleOrNull { use ->
                        use.referencesW4eLogicalResource(continuation.resolveMaskResourceId) &&
                            use.role == GPUFrameResourceRole.ClipMask &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                    }?.let { use ->
                        texturePreparations[use.resource]?.let { descriptor ->
                            descriptor.format == GPUColorFormat.RGBA8Unorm && descriptor.sampleCount == 1
                        } == true
                    } != true)
            }?.let {
                return GPUFramePreflightResult.Refused(
                    diagnostic(
                        "invalid.preflight.w4e_mask_continuation",
                        "W4e mask continuation must retain its sealed linear RGBA8 scratch target and optional resolve mask.",
                    ),
                )
            }
            w4eRenders.firstOrNull { render ->
                val continuation = render.w4eSceneContinuation ?: return@firstOrNull false
                val path = render.drawPackets.single().w4ePreparedPath ?: return@firstOrNull true
                val sealedSceneTarget = render.resourceUses.singleOrNull { use ->
                    use.referencesW4eLogicalResource(continuation.sceneTargetResourceId) &&
                        use.role == GPUFrameResourceRole.LayerTarget &&
                        use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                }?.let { use -> texturePreparations[use.resource]?.let { descriptor ->
                        descriptor.format == GPUColorFormat.RGBA8UnormSrgb && descriptor.sampleCount == 4
                    } == true
                } == true
                val sealedResolve = continuation.resolveSceneResourceId == null ||
                    render.resourceUses.singleOrNull { use ->
                        use.referencesW4eLogicalResource(continuation.resolveSceneResourceId) &&
                            use.role == GPUFrameResourceRole.SceneTarget &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                    }?.let { use ->
                        texturePreparations[use.resource]?.let { descriptor ->
                            descriptor.format == GPUColorFormat.RGBA8UnormSrgb && descriptor.sampleCount == 1
                        } == true
                    } == true
                path.sample != org.graphiks.kanvas.gpu.plan.SamplePlan.Multisample4 ||
                    path.targetResourceId != continuation.sceneTargetResourceId ||
                    path.resolveTargetResourceId != continuation.resolveSceneResourceId ||
                    render.sampleContinuation != null || !sealedSceneTarget || !sealedResolve
            }?.let {
                return GPUFramePreflightResult.Refused(
                    diagnostic(
                        "invalid.preflight.w4e_scene_continuation",
                        "W4e scene MSAA scopes must retain their sealed 4x target and only the sealed final canonical resolve.",
                    ),
                )
            }
            w4eRenders.firstOrNull { render ->
                val path = render.drawPackets.single().w4ePreparedPath ?: return@firstOrNull false
                val inverse = render.drawPackets.single().w4ePreparedClipConsumer as?
                    org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority.InverseDomain
                    ?: return@firstOrNull false
                when (inverse.interiorCoverage) {
                    org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage.Zero -> {
                        // A zero finite interior is always D24-free.  The two legal sealed forms
                        // distinguish an actual empty source from its exact non-empty source fact.
                        path.copyGeometry() !is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Empty &&
                            path.copyGeometry() !is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.InverseDomainSource ||
                            path.depthStencilResourceId != null ||
                            render.resourceUses.any { use -> use.role == GPUFrameResourceRole.PathDepthStencil }
                    }
                    is org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage.Geometry -> {
                        val depthId = path.depthStencilResourceId ?: return@firstOrNull true
                        val expectedSampleCount = if (path.sample == org.graphiks.kanvas.gpu.plan.SamplePlan.Multisample4) 4 else 1
                        render.resourceUses.singleOrNull { use ->
                            use.referencesW4eLogicalResource(depthId) &&
                                use.role == GPUFrameResourceRole.PathDepthStencil &&
                                use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                        }?.let { use ->
                            texturePreparations[use.resource]?.let { descriptor ->
                                descriptor.format == GPUColorFormat("depth24plus-stencil8") &&
                                    descriptor.sampleCount == expectedSampleCount
                            } == true
                        } != true
                    }
                }
            }?.let {
                return GPUFramePreflightResult.Refused(
                    diagnostic(
                        "invalid.preflight.w4e_inverse_domain_depth",
                        "W4e inverse-domain zero must retain its sealed source form without a D24S8 attachment; finite interior geometry must declare its exact scene D24S8 attachment.",
                    ),
                )
            }
        }
        preparedTextNativeBlendDomainRefusal(
            framePlan.steps
                .filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                .filter { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                }
                .map(GPUDrawPacket::blendPlan),
        )?.let { refused ->
            return GPUFramePreflightResult.Refused(
                diagnostic(refused.code, refused.message),
            )
        }
        val hasExactPreparedSurfaceMixedBoundary =
            hasExactPreparedSurfaceMixedNativeBoundary(framePlan)
        if (!hasExactPreparedSurfaceMixedBoundary &&
            framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                .any { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.SampledImage
                }
        ) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "unsupported.preflight.sampled_image_unmaterialized",
                    "Prepared sampled-image semantics have no executable native materialization route.",
                ),
            )
        }
        if (hasExactPreparedSurfaceMixedBoundary) {
            GPUPreparedSurfaceNativePreflight()
                .validateFramePlan(framePlan, context, capabilities)
                ?.let { refused ->
                    return GPUFramePreflightResult.Refused(
                        diagnostic(refused.code, refused.message),
                    )
                }
        }
        val renderPackets = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        val compositeAuthority = renderPackets.mapNotNull { it.w5aCompositeFrameAuthority }.firstOrNull()
        val compositeValidation = compositeAuthority?.let { validateW5aComposite(framePlan, it) }
        if (compositeAuthority != null && compositeValidation == null) return GPUFramePreflightResult.Refused(
            diagnostic("invalid.preflight.w5a_composite", "Composite native lanes fail their exact preflight envelopes."))
        val hasW4aSessionMarker = compositeAuthority == null && framePlan.hasSealedW4aSessionMarker()
        val hasW4bSessionMarker = compositeAuthority == null && framePlan.hasSealedW4bSessionMarker()
        val hasW4cSessionMarker = compositeAuthority == null && framePlan.hasSealedW4cSessionMarker()
        val hasW4dSessionMarker = compositeAuthority == null && framePlan.hasSealedW4dSessionMarker()
        val w5aRectScratch = renderPackets
            .mapNotNull { it.corePrimitivePreparedAuthority?.w5aAnalyticRectSessionScratch }
            .firstOrNull()
        if (w5aRectScratch != null &&
            (renderPackets.any { it.corePrimitivePreparedAuthority?.w5aAnalyticRectSessionScratch !== w5aRectScratch } ||
                !w5aRectScratch.validatesMaterialPlanVersion() ||
                !hasExactW4aSessionScratch(framePlan, w5aRectScratch.payloadFacts, w5aRectScratch))
        ) return GPUFramePreflightResult.Refused(
            diagnostic("invalid.preflight.w5a_rect_material", "W5a Rect material-plan V2 authority is invalid."),
        )
        val w5aRRectScratch = renderPackets
            .mapNotNull { it.corePrimitivePreparedAuthority?.w5aAnalyticRRectSessionScratch }
            .firstOrNull()
        if (compositeAuthority == null && w5aRRectScratch != null &&
            (renderPackets.any { it.corePrimitivePreparedAuthority?.w5aAnalyticRRectSessionScratch !== w5aRRectScratch } ||
                !w5aRRectScratch.validatesMaterialPlanVersion() ||
                !hasExactW4bSessionScratch(framePlan, w5aRRectScratch.payloadFacts, w5aRRectScratch))
        ) return GPUFramePreflightResult.Refused(
            diagnostic("invalid.preflight.w5a_rrect_material", "W5a RRect material-plan V2 authority is invalid."),
        )
        val w4aScratch = renderPackets
            .mapNotNull { packet -> packet.corePrimitivePreparedAuthority?.w4aSessionScratch }
            .firstOrNull()
        if (hasW4aSessionMarker && w5aRectScratch == null &&
            (w4aScratch == null ||
                renderPackets.any { packet ->
                    packet.corePrimitivePreparedAuthority?.w4aSessionScratch !== w4aScratch
                } ||
                !hasExactW4aSessionScratch(framePlan, w4aScratch))
        ) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.w4a_session_scratch",
                    "W4a encoder scratch authority is absent, stale, or contradicts the closed frame envelope.",
                ),
            )
        }
        val w4bScratch = renderPackets
            .mapNotNull { packet -> packet.corePrimitivePreparedAuthority?.w4bSessionScratch }
            .firstOrNull()
        if (hasW4bSessionMarker && w5aRRectScratch == null &&
            (w4bScratch == null ||
                renderPackets.any { packet ->
                    packet.corePrimitivePreparedAuthority?.w4bSessionScratch !== w4bScratch
                } ||
                !hasExactW4bSessionScratch(framePlan, w4bScratch))
        ) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.w4b_session_scratch",
                    "W4b encoder scratch authority is absent, stale, or contradicts the closed frame envelope.",
                ),
            )
        }
        val w4cScratch = renderPackets
            .mapNotNull { packet -> packet.corePrimitivePreparedAuthority?.w4cSessionScratch }
            .firstOrNull()
        val w4cValidation = if (hasW4cSessionMarker) {
            if (hasW4aSessionMarker || hasW4bSessionMarker || w4cScratch == null ||
                renderPackets.any { packet ->
                    packet.corePrimitivePreparedAuthority?.w4cSessionScratch !== w4cScratch
                }
            ) {
                null
            } else {
                validateW4cSessionScratch(framePlan, w4cScratch)
            }
        } else {
            null
        }
        if (hasW4cSessionMarker && w4cValidation == null) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.w4c_session_scratch",
                    "W4c encoder scratch is absent, stale, or contradicts the closed frame envelope.",
                ),
            )
        }
        val w4dScratch = renderPackets
            .mapNotNull { packet -> packet.corePrimitivePreparedAuthority?.w4dSessionScratch }
            .firstOrNull()
        val w4dValidation = if (hasW4dSessionMarker) {
            if (hasW4aSessionMarker || hasW4bSessionMarker || hasW4cSessionMarker || w4dScratch == null ||
                renderPackets.any { packet ->
                    packet.corePrimitivePreparedAuthority?.w4dSessionScratch !== w4dScratch
                }
            ) {
                null
            } else {
                validateW4dSessionScratch(framePlan, w4dScratch)
            }
        } else {
            null
        }
        if (hasW4dSessionMarker && w4dValidation == null) {
            return GPUFramePreflightResult.Refused(
                diagnostic(
                    "invalid.preflight.w4d_session_scratch",
                    "W4d encoder scratch is absent, stale, or contradicts the closed frame envelope.",
                ),
            )
        }
        val w5bPathValidation = w5b?.takeIf { witness -> witness.geometryLanes.any {
            it is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.NativePath
        } }?.let { validateW5bPathGeometry(framePlan, it) }
        val pureValidation = pureValidation(
            framePlan,
            skipNativeCorePrimitiveClassification =
                hasW4aSessionMarker || hasW4bSessionMarker || hasW4cSessionMarker ||
                    hasW4dSessionMarker || compositeAuthority != null || w5bPathValidation != null,
        )
        pureValidation.diagnostic?.let { return GPUFramePreflightResult.Refused(it) }
        val plannedPathValidation = w5bPathValidation ?: compositeValidation ?: w4dValidation ?: w4cValidation
        val corePrimitiveDirectRoutes = plannedPathValidation?.directRouteSeal
            ?: pureValidation.corePrimitiveDirectRoutes
        val corePrimitivePathStencilRoutes = plannedPathValidation?.pathRouteSeal
            ?: pureValidation.corePrimitivePathStencilRoutes
        val corePrimitiveNativeScopeRoutes = plannedPathValidation?.unifiedRouteSeal
            ?: pureValidation.corePrimitiveNativeScopeRoutes
        val corePrimitiveClipStencilPreparedRoutes =
            pureValidation.corePrimitiveClipStencilPreparedRoutes
        val corePrimitiveCoverageMaskPreparedRoutes =
            pureValidation.corePrimitiveCoverageMaskPreparedRoutes

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
        val plannedPathSessionResources = if (plannedPathValidation == null) {
            // Task 8 owns these W4d.2 graph-local inputs.  The authenticated Task 7 snapshot
            // has already bound their typed identities; they are not ordinary caller-provided
            // preparation requests.  Keep the logical target and staging out of this set so
            // their declared provider generations remain authoritative.
            framePlan.w4dGeneralRenderSteps().let { renders ->
                buildSet {
                    addAll(renders.flatMap(GPUFrameStep.RenderPassStep::resourceUses)
                        .map(GPUFrameResourceUse::resource))
                    addAll(renders.map(GPUFrameStep.RenderPassStep::target)
                        .filter { target -> target !in declaredByRef })
                }
            }
        } else {
            framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::resourceUses)
                .filter { use -> use.role == GPUFrameResourceRole.PathDepthStencil }
                .map(GPUFrameResourceUse::resource)
                .toSet()
        }
        referencedResources(framePlan).firstOrNull { resource ->
            resource !in declaredByRef && resource !in context.resourceGenerations &&
                resource !in plannedPathSessionResources
        }?.let { missing ->
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
                    mapOf(
                        "failureClass" to failure::class.simpleName.orEmpty(),
                        "failureMessage" to failure.message.orEmpty(),
                    ),
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
        plannedPathSessionResources.forEach { resource ->
            preparedGenerationMap[resource] = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
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
        val renderMaterialization = materializeRenderOperands(
            framePlan,
            ownerScope,
            corePrimitiveDirectRoutes,
            corePrimitiveClipStencilPreparedRoutes,
            corePrimitiveCoverageMaskPreparedRoutes,
        )
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
        validateRenderOperands(
            framePlan,
            materialized,
            ownerScope,
            corePrimitiveDirectRoutes,
            corePrimitiveClipStencilPreparedRoutes,
            corePrimitiveCoverageMaskPreparedRoutes,
        )?.let { invalid ->
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
                corePrimitiveCoverageMaskPreparedRoutes,
            )
        } catch (failure: Throwable) {
            return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(
                    "invalid.preflight.encoder_lowering",
                "Semantic steps could not be lowered to a one-to-one encoder plan: " +
                    failure.message.orEmpty(),
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
        nominalEncoderScopeObserver?.invoke(encoderScopes)
        if (!hasExactPreparedSurfaceMixedNativeBoundary(framePlan) &&
            framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { it.drawPackets }
                .any { it.semanticPayload is GPUDrawSemanticPayload.SampledImage }
        ) {
            return refuseWithRollback(
                rollback,
                acquiredAnyResource,
                diagnostic(
                    "unsupported.preflight.sampled_image_unmaterialized",
                    "Prepared sampled-image semantics have no executable native materialization route.",
                )
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
        val invalidPreparedSurfaceScopes = if (hasExactPreparedSurfaceMixedBoundary) {
            encoderScopes.filter { scope ->
                !GPUPreparedSurfaceEncoderScopeAuthority.matches(
                    framePlan,
                    framePlan.steps[scope.sourceStepIndex],
                    scope,
                    generationSeal,
                )
            }
        } else {
            emptyList()
        }
        if (invalidPreparedSurfaceScopes.isNotEmpty()) {
            return refuseWithRollback(
                rollback,
                true,
                diagnostic(
                    "invalid.preflight.prepared_surface_encoder_authority",
                    "Mixed prepared-surface scopes diverged from their canonical frame authority.",
                    mapOf(
                        "scopeIndices" to invalidPreparedSurfaceScopes
                            .joinToString(",") { it.sourceStepIndex.toString() },
                        "scopeKinds" to invalidPreparedSurfaceScopes
                            .joinToString(",") { it.operationKind.name },
                    ),
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
                        "Reusable native payload materialization failed without a typed result: " +
                            "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
                        mapOf(
                            "failureClass" to failure::class.simpleName.orEmpty(),
                            "failureMessage" to failure.message.orEmpty(),
                        ),
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
            if (!validatesW5aSourcePartitionV2(framePlan, requireNotNull(nativeDraft).payload)) {
                boundary.terminalizeCallerRetainedDraft(requireNotNull(nativeDraft))
                return refuseWithRollback(rollback, true, diagnostic(
                    "invalid.preflight.w5a-source-partition-v2",
                    "Native W5a source bindings do not exactly match the sealed color-writing packet inventory."))
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
                    mapOf(
                        "failureClass" to failure::class.simpleName.orEmpty(),
                        "failureMessage" to (failure.message.orEmpty()),
                    ),
                ),
                rollback.execute(),
            )
        }
    }

    private data class CorePrimitiveClipStencilPreparedValidation(
        val routeSeal: GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        val diagnostic: GPUDiagnostic? = null,
    )

    private data class CorePrimitiveCoverageMaskPreparedValidation(
        val routeSeal: GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
        val diagnostic: GPUDiagnostic? = null,
    )

    private fun validateCorePrimitiveCoverageMaskPreparedRoutes(
        framePlan: GPUFramePlan,
    ): CorePrimitiveCoverageMaskPreparedValidation {
        data class Location(
            val sourceStepIndex: Int,
            val render: GPUFrameStep.RenderPassStep,
            val packet: GPUDrawPacket,
        )

        fun refuse(message: String) = CorePrimitiveCoverageMaskPreparedValidation(
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty,
            diagnostic(
                "invalid.preflight.core_primitive_coverage_mask_prepared_route",
                message,
            ),
        )

        val allLocations = framePlan.steps.flatMapIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@flatMapIndexed emptyList()
            render.drawPackets.map { Location(sourceStepIndex, render, it) }
        }
        val locations = allLocations.filter {
            it.packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal != null
        }
        if (locations.isEmpty()) {
            return CorePrimitiveCoverageMaskPreparedValidation(
                GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty,
            )
        }
        val slabSeal = requireNotNull(
            locations.first().packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal,
        )
        if (locations.any {
                it.packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal !== slabSeal
            }
        ) return refuse("Every prepared coverage-mask packet must share one exact uniform slab seal.")
        val liveAuthority = when (val validation =
            validateGPUCorePrimitiveCoverageMaskPreparedAuthority(
                locations.map(Location::packet),
                slabSeal,
            )
        ) {
            is GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Accepted -> validation
            is GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Refused ->
                return refuse(validation.message)
        }
        val accepted = liveAuthority.route
        val expectedPacketIds = slabSeal.producerPacketIds + slabSeal.consumerPacketIds
        val boundedCoreLocations = allLocations.filter { location ->
            location.packet.role == GPUDrawPacketRole.ClipProducer ||
                location.packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        }
        if (boundedCoreLocations.map { it.packet.packetId } != expectedPacketIds) {
            return refuse("Prepared coverage-mask must own every CorePrimitive packet in the bounded frame.")
        }
        val invalidPartition = locations.groupBy(Location::sourceStepIndex).entries.firstOrNull { (_, scopedLocations) ->
                val render = scopedLocations.first().render
                val batch = render.batches.singleOrNull()
                render.drawPackets.map(GPUDrawPacket::packetId) !=
                    scopedLocations.map { location -> location.packet.packetId } ||
                    render.sourceTaskIds.size != 1 || batch == null ||
                    batch.packets != render.drawPackets ||
                    batch.sourceTaskIds != render.sourceTaskIds
            }
        if (invalidPartition != null) return refuse(
            "Prepared coverage-mask requires one exact ordered packet partition, task, and batch per render scope: " +
                "step=${invalidPartition.key} tasks=${invalidPartition.value.first().render.sourceTaskIds.size} " +
                "batches=${invalidPartition.value.first().render.batches.size}.",
        )

        val firstPreparedStepIndex = locations.first().sourceStepIndex
        val lastPreparedStepIndex = locations.last().sourceStepIndex
        val preparedRenderStepIndices = locations.map { it.sourceStepIndex }.toSet()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (sourceStepIndex !in firstPreparedStepIndex..lastPreparedStepIndex) {
                return@forEachIndexed
            }
            when (step) {
                is GPUFrameStep.RenderPassStep -> if (sourceStepIndex !in preparedRenderStepIndices) {
                    return refuse("Foreign rendering splits the prepared coverage-mask atomic interval.")
                }
                is GPUFrameStep.DependencyBarrierStep -> Unit
                else -> return refuse(
                    "A foreign encoder or host step splits the prepared coverage-mask atomic interval.",
                )
            }
        }
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (sourceStepIndex <= lastPreparedStepIndex &&
                (step is GPUFrameStep.ReadbackCopyStep ||
                    step is GPUFrameStep.SurfaceBlitRenderPassStep ||
                    step is GPUFrameStep.PostSubmitPresentAction)
            ) return refuse(
                "Readback, surface blit, and present must follow the final coverage-mask consumer.",
            )
        }

        val producerLocations = locations.take(slabSeal.producerSlots.size)
        val consumerLocations = locations.drop(slabSeal.producerSlots.size)
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val maskResource = slabSeal.maskResource
        if (producerLocations.any { it.render.target != maskResource } ||
            consumerLocations.map { it.render.target }.distinct().size != 1
        ) return refuse("Prepared coverage-mask render targets were substituted.")
        val sceneTarget = consumerLocations.first().render.target
        val sceneTargetPreparation = preparations.singleOrNull {
            it.resource == sceneTarget && it.role == GPUFrameResourceRole.SceneTarget
        } ?: return refuse("Prepared coverage-mask scene target preparation is missing.")
        val sceneDescriptor = sceneTargetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refuse("Prepared coverage-mask scene target is not a texture.")
        if (!isCanonicalCorePrimitiveTargetPreparation(
                sceneTargetPreparation,
                sceneTarget,
                sceneDescriptor.logicalBounds,
                sceneDescriptor.format,
            ) || sceneDescriptor.logicalBounds != slabSeal.maskBounds
        ) return refuse("Prepared coverage-mask scene target authority was substituted.")
        val maskPreparation = preparations.singleOrNull {
            it.resource == maskResource && it.role == GPUFrameResourceRole.ClipMask
        } ?: return refuse("Prepared coverage-mask color attachment preparation is missing.")
        val maskDescriptor = maskPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refuse("Prepared coverage-mask color attachment is not a texture.")
        if (maskPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.TextureBinding,
            ) || maskPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            maskPreparation.byteSize != liveAuthority.resolvedMaskBytes ||
            maskDescriptor.logicalBounds != slabSeal.maskBounds ||
            maskDescriptor.format.value != "rgba8unorm" || maskDescriptor.sampleCount != 1
        ) return refuse("Prepared coverage-mask attachment format, bounds, samples, usages, or bytes were substituted.")

        val producerScopes = producerLocations.groupBy(Location::sourceStepIndex).values.toList()
        val consumerScopes = consumerLocations.groupBy(Location::sourceStepIndex).values.toList()
        val consumerSlotsByPacketId = slabSeal.consumerSlots.associateBy { it.packetId }
        val producerUniformUses = producerScopes.mapIndexed { producerScopeIndex, scopeLocations ->
            val render = scopeLocations.first().render
            val maskUse = render.resourceUses.singleOrNull {
                it.resource == maskResource && it.role == GPUFrameResourceRole.ClipMask &&
                    it.usage == GPUFrameResourceUsage.RenderAttachment && it.write &&
                    it.lifetime == GPUFrameResourceLifetime.FrameLocal
            } ?: return refuse("Prepared coverage-mask producer mask use was substituted.")
            val uniformUse = render.resourceUses.singleOrNull {
                it.role == GPUFrameResourceRole.UniformData &&
                    it.usage == GPUFrameResourceUsage.Uniform && !it.write &&
                    it.lifetime == GPUFrameResourceLifetime.FrameLocal
            } ?: return refuse("Prepared coverage-mask producer uniform use was substituted.")
            if (render.drawPackets.map(GPUDrawPacket::packetId) != scopeLocations.map { it.packet.packetId } ||
                render.resourceUses.toSet() != setOf(maskUse, uniformUse) ||
                render.resourceUses.size != 2 || render.depthStencilLoadStore != null ||
                render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                render.loadStore != GPULoadStorePlan(
                    if (producerScopeIndex == 0) "clear" else "load",
                    GPUStorePlan.Store,
                    if (producerScopeIndex == 0) CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL else null,
                )
            ) return refuse("Prepared coverage-mask producer uses must be mask plus uniform only.")
            uniformUse
        }
        val vertexUses = mutableListOf<GPUFrameResourceUse>()
        val indexUses = mutableListOf<GPUFrameResourceUse>()
        val consumerUniformUses = mutableListOf<GPUFrameResourceUse>()
        consumerScopes.forEachIndexed { consumerScopeIndex, scopeLocations ->
            val render = scopeLocations.first().render
            val vertexUse = render.resourceUses.singleOrNull {
                it.role == GPUFrameResourceRole.VertexData &&
                    it.usage == GPUFrameResourceUsage.Vertex && !it.write &&
                    it.lifetime == GPUFrameResourceLifetime.FrameLocal
            } ?: return refuse("Prepared coverage-mask consumer vertex use is missing.")
            val indexUse = render.resourceUses.singleOrNull {
                it.role == GPUFrameResourceRole.IndexData &&
                    it.usage == GPUFrameResourceUsage.Index && !it.write &&
                    it.lifetime == GPUFrameResourceLifetime.FrameLocal
            } ?: return refuse("Prepared coverage-mask consumer index use is missing.")
            val uniformUse = render.resourceUses.singleOrNull {
                it.role == GPUFrameResourceRole.UniformData &&
                    it.usage == GPUFrameResourceUsage.Uniform && !it.write &&
                    it.lifetime == GPUFrameResourceLifetime.FrameLocal
            } ?: return refuse("Prepared coverage-mask consumer uniform use is missing.")
            val maskUse = render.resourceUses.singleOrNull {
                it == GPUFrameResourceUse(
                    maskResource,
                    GPUFrameResourceRole.ClipMask,
                    GPUFrameResourceUsage.TextureBinding,
                    GPUFrameResourceLifetime.FrameLocal,
                    false,
                )
            } ?: return refuse("Prepared coverage-mask consumer texture binding was substituted.")
            if (render.drawPackets.map(GPUDrawPacket::packetId) != scopeLocations.map { it.packet.packetId } ||
                render.resourceUses.toSet() != setOf(
                    vertexUse,
                    indexUse,
                    uniformUse,
                    maskUse,
                ) || render.resourceUses.size != 4 ||
                render.depthStencilLoadStore != null ||
                render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                render.loadStore != GPULoadStorePlan(
                    if (consumerScopeIndex == 0) "clear" else "load",
                    GPUStorePlan.Store,
                )
            ) return refuse(
                "Prepared coverage-mask consumer scope state was substituted: " +
                    "scope=$consumerScopeIndex loadStore=${render.loadStore} uses=${render.resourceUses.map { it.role }}.",
            )
            vertexUses += vertexUse
            indexUses += indexUse
            consumerUniformUses += uniformUse
        }
        val allUniformUses = producerUniformUses + consumerUniformUses
        if (allUniformUses.map { it.resource }.distinct().size != 1 ||
            vertexUses.map { it.resource }.distinct().size != 1 ||
            indexUses.map { it.resource }.distinct().size != 1
        ) return refuse("Prepared coverage-mask scopes must share exact uniform and geometry slabs.")
        val uniformResource = allUniformUses.first().resource as? GPUFrameBufferRef
            ?: return refuse("Prepared coverage-mask uniform slab is not a buffer.")
        val vertexResource = vertexUses.first().resource as? GPUFrameBufferRef
            ?: return refuse("Prepared coverage-mask vertex slab is not a buffer.")
        val indexResource = indexUses.first().resource as? GPUFrameBufferRef
            ?: return refuse("Prepared coverage-mask index slab is not a buffer.")
        val sealedResources = setOf(
            maskResource,
            sceneTarget,
            vertexResource,
            indexResource,
            uniformResource,
        )
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (sourceStepIndex !in firstPreparedStepIndex..lastPreparedStepIndex &&
                step is GPUFrameStep.RenderPassStep &&
                referencedResources(step).any(sealedResources::contains)
            ) return refuse(
                "Foreign rendering outside the coverage-mask interval aliases a sealed resource.",
            )
        }
        val uniformPreparation = preparations.singleOrNull { it.resource == uniformResource }
            ?: return refuse("Prepared coverage-mask uniform preparation is missing.")
        val vertexPreparation = preparations.singleOrNull { it.resource == vertexResource }
            ?: return refuse("Prepared coverage-mask vertex preparation is missing.")
        val indexPreparation = preparations.singleOrNull { it.resource == indexResource }
            ?: return refuse("Prepared coverage-mask index preparation is missing.")
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refuse("Prepared coverage-mask uniform descriptor is missing.")
        val vertexDescriptor = vertexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refuse("Prepared coverage-mask vertex descriptor is missing.")
        val indexDescriptor = indexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refuse("Prepared coverage-mask index descriptor is missing.")
        val limits = capabilities.limits
            ?: return refuse("Prepared coverage-mask requires observed device limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return refuse("Prepared coverage-mask requires observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?: return refuse("Prepared coverage-mask requires an observed dynamic-uniform limit.")
        val consumerSemantics = consumerLocations.map { location ->
            location.packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
        }
        if (slabSeal.plan.sourceLabel != "core-primitive-coverage-mask-uniform-pass" ||
            slabSeal.plan.deviceGeneration != context.deviceGeneration.value ||
            slabSeal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            slabSeal.plan.slots.size !=
            slabSeal.producerSlots.size + slabSeal.consumerSlots.size ||
            slabSeal.plan.slots.any {
                it.payloadBytes != 64L || it.alignedOffset > UInt.MAX_VALUE.toLong()
            } || slabSeal.plan.totalBytes > maxBufferSize ||
            maxDynamicUniformBuffers < 1L ||
            !slabSeal.hasZeroPadding() ||
            uniformPreparation.role != GPUFrameResourceRole.UniformData ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformPreparation.byteSize != slabSeal.plan.totalBytes ||
            uniformDescriptor.byteSize != slabSeal.plan.totalBytes ||
            uniformDescriptor.alignmentBytes != slabSeal.plan.alignmentBytes
        ) return refuse("Prepared coverage-mask uniform64 plan or bytes were substituted.")

        fun exactGeometryPreparation(
            request: GPUResourcePreparationRequest,
            descriptor: GPUFrameBufferDescriptor,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
        ): Boolean = request.role == role &&
            request.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
            request.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            request.byteSize == descriptor.byteSize && descriptor.alignmentBytes == 4L
        if (!exactGeometryPreparation(
                vertexPreparation,
                vertexDescriptor,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
            ) || !exactGeometryPreparation(
                indexPreparation,
                indexDescriptor,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
            )
        ) return refuse("Prepared coverage-mask geometry slab descriptors were substituted.")
        val geometrySlices = mutableListOf<GPUCorePrimitiveCoverageMaskPreparedGeometrySlice>()
        var firstIndex = 0
        var baseVertex = 0
        consumerSemantics.forEach { semantic ->
            val (vertexCount, indexCount) = when (val geometry = semantic.geometry) {
                is GPUCorePrimitiveGeometry.Rect -> 4 to 6
                is GPUCorePrimitiveGeometry.TriangulatedPath ->
                    geometry.vertices.size / 2 to geometry.indices.size
                is GPUCorePrimitiveGeometry.RRect ->
                    return refuse("Prepared coverage-mask consumer RRect geometry is unsupported.")
                is GPUCorePrimitiveGeometry.DRRect ->
                    return refuse("Prepared coverage-mask consumer DRRect geometry is unsupported.")
            }
            geometrySlices += GPUCorePrimitiveCoverageMaskPreparedGeometrySlice(
                firstIndex,
                indexCount,
                baseVertex,
                vertexCount,
            )
            firstIndex = try { Math.addExact(firstIndex, indexCount) } catch (_: ArithmeticException) {
                return refuse("Prepared coverage-mask geometry index offsets overflowed.")
            }
            baseVertex = try { Math.addExact(baseVertex, vertexCount) } catch (_: ArithmeticException) {
                return refuse("Prepared coverage-mask geometry vertex offsets overflowed.")
            }
        }
        if (vertexDescriptor.byteSize != baseVertex.toLong() * 2L * Float.SIZE_BYTES ||
            indexDescriptor.byteSize != firstIndex.toLong() * Int.SIZE_BYTES
        ) return refuse("Prepared coverage-mask geometry slab byte sizes were substituted.")

        val maskGeneration = context.resourceGenerations[maskResource]
            ?: return refuse("Prepared coverage-mask attachment generation is missing.")
        val sceneGeneration = context.resourceGenerations[sceneTarget]
            ?: return refuse("Prepared coverage-mask scene generation is missing.")
        val vertexGeneration = context.resourceGenerations[vertexResource]
            ?: return refuse("Prepared coverage-mask vertex generation is missing.")
        val indexGeneration = context.resourceGenerations[indexResource]
            ?: return refuse("Prepared coverage-mask index generation is missing.")
        val uniformGeneration = context.resourceGenerations[uniformResource]
            ?: return refuse("Prepared coverage-mask uniform generation is missing.")
        if (accepted.producers.map { it.structuralKey } !=
            slabSeal.producerSlots.map { it.structuralPipelineKey } ||
            accepted.consumers.map { it.structuralKey } !=
            slabSeal.consumerSlots.map { it.structuralPipelineKey }
        ) return refuse("Prepared coverage-mask pure structural route was substituted.")

        val orderedScopes = producerScopes + consumerScopes
        val taskIds = orderedScopes.map { locationsInScope ->
            locationsInScope.first().render.sourceTaskIds.single()
        }
        if (taskIds.distinct().size != taskIds.size) {
            return refuse("Prepared coverage-mask scopes must retain distinct task identities; self-edges are forbidden.")
        }
        val expectedPairs = taskIds.zipWithNext()
        val taskIdSet = taskIds.toSet()
        val routeDependencies = framePlan.dependencies.filter {
            it.fromTaskId in taskIdSet && it.toTaskId in taskIdSet
        }
        if (routeDependencies.size != expectedPairs.size ||
            routeDependencies.map { it.fromTaskId to it.toTaskId } != expectedPairs
        ) return refuse("Prepared coverage-mask dependency graph was substituted.")
        routeDependencies.forEachIndexed { index, dependency ->
            val destinationScopeIndex = index + 1
            val isConsumerOrder = destinationScopeIndex > producerScopes.size
            if (isConsumerOrder) {
                val targetConsumerScope = consumerScopes[destinationScopeIndex - producerScopes.size]
                val targetPacketId = targetConsumerScope.first().packet.packetId
                val expectedToken = consumerSlotsByPacketId[targetPacketId]
                    ?.dependencyFromPreviousConsumerToken
                    ?: return refuse("Prepared coverage-mask consumer slot authority is missing.")
                if (dependency.dependencyKind != "prepared-scene-order" ||
                    dependency.useToken?.value != expectedToken ||
                    dependency.reasonCode != "preserve.prepared-scene.order" ||
                    dependency.atomicGroupId != null
                ) return refuse("Prepared coverage-mask consumer dependency authority was substituted.")
            } else if (dependency.dependencyKind != "clip-producer-consumer" ||
                dependency.useToken?.value != slabSeal.orderingToken ||
                dependency.atomicGroupId != null ||
                dependency.reasonCode != if (index < producerScopes.lastIndex) {
                    "preserve.core-primitive.clip.mask-producer.$index"
                } else {
                    "preserve.core-primitive.clip.producer-before-consumer"
                }
            ) return refuse("Prepared coverage-mask producer dependency authority was substituted.")
        }
        val slabAuthority = try {
            GPUCorePrimitiveCoverageMaskPreparedSlabAuthority(
                vertexResource,
                vertexGeneration,
                vertexPreparation.byteSize,
                indexResource,
                indexGeneration,
                indexPreparation.byteSize,
                uniformResource,
                uniformGeneration,
                uniformPreparation.byteSize,
                uniformDescriptor.alignmentBytes,
                slabSeal,
            )
        } catch (_: IllegalArgumentException) {
            return refuse("Prepared coverage-mask generated slab authority is invalid.")
        }
        val routeSeal = try {
            sealGPUCorePrimitiveCoverageMaskPreparedFrameRoute(
                accepted,
                slabAuthority,
                GPUCorePrimitiveCoverageMaskPreparedAttachmentAuthority(
                    maskResource,
                    maskGeneration,
                ),
                sceneTarget,
                sceneGeneration,
                producerLocations.zip(slabSeal.producerSlots).map { (location, slot) ->
                    GPUCorePrimitiveCoverageMaskPreparedProducerLocation(
                        location.sourceStepIndex,
                        location.packet.packetId,
                        location.packet.commandIdValue,
                        slot.sourceOrder,
                    )
                },
                consumerLocations.zip(geometrySlices).zip(slabSeal.consumerSlots).map {
                    (locationAndSlice, slot) ->
                    val (location, slice) = locationAndSlice
                    GPUCorePrimitiveCoverageMaskPreparedConsumerLocation(
                        location.sourceStepIndex,
                        location.packet.packetId,
                        location.packet.commandIdValue,
                        location.packet.originalPaintOrder,
                        slot.dependencyFromPreviousConsumerToken,
                        slice,
                    )
                },
            )
        } catch (_: IllegalArgumentException) {
            return refuse("Prepared coverage-mask frame scope seal is invalid.")
        }
        return CorePrimitiveCoverageMaskPreparedValidation(routeSeal)
    }

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
        val allCoreLocations = locations.filter { location ->
            location.packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                location.packet.role == GPUDrawPacketRole.StencilProducer ||
                location.packet.role == GPUDrawPacketRole.ClipProducer
        }
        val prefixLocations = allCoreLocations.filter { location ->
            location.packet.packetId !in expectedPacketIds
        }
        if (prefixLocations.size > 1) {
            return refuse("Prepared clip-stencil permits at most one direct background prefix packet.")
        }
        val hasBackgroundPrefix = prefixLocations.isNotEmpty()
        prefixLocations.singleOrNull()?.let { prefix ->
            val semantic = prefix.packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            val geometry = semantic?.geometry as? GPUCorePrimitiveGeometry.Rect
            val material = semantic?.material as? GPUCorePrimitiveMaterialPayload.SolidColor
            val opaque = material?.premultipliedRgba?.getOrNull(3) == 1f
            if (prefix.packet.role != GPUDrawPacketRole.Shading ||
                semantic == null ||
                semantic.sourceFamily != GPUCorePrimitiveSourceFamily.Rect ||
                geometry == null ||
                geometry.left != semantic.targetBounds.left.toFloat() ||
                geometry.top != semantic.targetBounds.top.toFloat() ||
                geometry.right != semantic.targetBounds.right.toFloat() ||
                geometry.bottom != semantic.targetBounds.bottom.toFloat() ||
                material == null ||
                !opaque ||
                !hasExactNoClipAuthority(semantic, prefix.packet.clipExecutionPlan) ||
                !prefix.packet.blendPlan.isCanonicalSolidRectSrcOver() ||
                prefix.render.drawPackets != listOf(prefix.packet) ||
                prefix.sourceStepIndex >= candidateLocations.first().sourceStepIndex
            ) return refuse("Prepared clip-stencil background prefix is not one direct opaque FillRect before the stencil scope.")
        }
        val unclaimedCorePacketIds = prefixLocations.map { it.packet.packetId }.toSet()
        val allClipScopePacketIds = allCoreLocations
            .filter { it.packet.packetId !in unclaimedCorePacketIds }
            .map { it.packet.packetId }
        if (allClipScopePacketIds != expectedPacketIds) {
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
        if (
            prefixLocations.isNotEmpty() &&
            consumerLocations.singleOrNull()?.packet?.corePrimitivePreparedAuthority
                ?.let { authority ->
                    authority.analyticShapeUniformSeal != null &&
                        authority.structuralPipelineKey.shader ==
                        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect
                } == true
        ) return refuse("Prepared analytic DRRect clip-stencil accepts exactly one consumer without a prefix.")
        val attachmentSampleCount = candidate.attachmentSampleCount
        val expectedSamplePlan = when (attachmentSampleCount) {
            1 -> GPUSamplePlan.SingleSampleFrame
            4 -> GPUSamplePlan.MultisampleFrame(4)
            else -> return refuse("Prepared clip-stencil accepts only exact 1x or 4x attachment authority.")
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val sceneTargetPreparation = preparations.singleOrNull {
            it.resource == producerLocation.render.target &&
                it.role == GPUFrameResourceRole.SceneTarget
        } ?: return refuse("Prepared clip-stencil target preparation is missing.")
        val sceneTargetDescriptor = sceneTargetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refuse("Prepared clip-stencil target is not a texture.")
        val sceneTargetInterpretation =
            sceneTargetDescriptor.format.corePrimitiveInterpretationOrNull()
                ?: return refuse("Prepared clip-stencil target format is unsupported.")
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
        val producerContinuation = producerLocation.render.sampleContinuation
        if (producerLocation.render.samplePlan != expectedSamplePlan ||
            producerLocation.render.loadStore != GPULoadStorePlan(
                if (attachmentSampleCount == 4) "clear" else "load",
                GPUStorePlan.Store,
            ) ||
            (attachmentSampleCount == 1 && producerContinuation != null) ||
            (attachmentSampleCount == 4 &&
                (producerContinuation == null ||
                producerContinuation.key.target.value != producerLocation.render.target.value ||
                producerContinuation.key.targetGeneration != context.targetGeneration ||
                producerContinuation.key.deviceGeneration != context.deviceGeneration ||
                producerContinuation.key.colorFormat != sceneTargetDescriptor.format ||
                producerContinuation.key.colorInterpretation != sceneTargetInterpretation ||
                producerContinuation.key.samplePlan != expectedSamplePlan ||
                producerContinuation.key.attachmentAuthority !=
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUSampleAttachmentAuthority.PreparedFramePayload ||
                producerContinuation.key.colorAttachment.value !=
                "msaa-color:${producerLocation.render.target.value}:${context.targetGeneration}" ||
                producerContinuation.key.depthStencilAttachment?.value != depthStencilUse.resource.value ||
                producerContinuation.loadTransition != GPUSampleLoadTransition.FreshClear ||
                producerContinuation.storeAction != GPUSampleStoreAction.Store ||
                producerContinuation.resolveAction != GPUSampleResolveAction.ResolveCanonical))
        ) return refuse("Prepared clip-stencil producer sample continuation was substituted.")

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
                    if (attachmentSampleCount == 4) "load"
                    else if (consumerIndex == 0 && !hasBackgroundPrefix) "clear"
                    else "load",
                    GPUStorePlan.Store,
                ) ||
                location.render.samplePlan != expectedSamplePlan ||
                (attachmentSampleCount == 1 && location.render.sampleContinuation != null) ||
                (attachmentSampleCount == 4 &&
                    location.render.sampleContinuation?.let { continuation ->
                        continuation.key == producerContinuation?.key &&
                            continuation.loadTransition == GPUSampleLoadTransition.RetainedLoad &&
                            continuation.storeAction == GPUSampleStoreAction.Store &&
                            continuation.resolveAction == GPUSampleResolveAction.ResolveCanonical
                    } != true)
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
        val analyticShapeUniformSeals = consumerLocations.map { location ->
            location.packet.corePrimitivePreparedAuthority?.analyticShapeUniformSeal
        }
        val analyticShapeUniformSeal = analyticShapeUniformSeals.firstOrNull()
        val usesAnalyticClipConsumerUniform = analyticShapeUniformSeal != null
        if (usesAnalyticClipConsumerUniform &&
            (prefixLocations.isNotEmpty() || consumerLocations.size != 1 ||
                analyticShapeUniformSeals.any { it !== analyticShapeUniformSeal } ||
                consumerLocations.single().packet.corePrimitivePreparedAuthority
                    ?.structuralPipelineKey?.shader !in setOf(
                        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect,
                    ))
        ) return refuse("Prepared analytic shape clip-stencil accepts exactly one consumer without a prefix.")
        val uniformSeals = if (usesAnalyticClipConsumerUniform) {
            emptyList()
        } else {
            consumerLocations.map { location ->
                location.packet.corePrimitivePreparedAuthority?.uniformSlabSeal
                    ?: return refuse("Prepared clip-stencil uniform slab seal is missing.")
            }
        }
        val uniformSeal = uniformSeals.firstOrNull()
        val limits = capabilities.limits
            ?: return refuse("Prepared clip-stencil requires observed device limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return refuse("Prepared clip-stencil requires observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?: return refuse("Prepared clip-stencil requires an observed dynamic-uniform limit.")
        val uniformScopeLocations = prefixLocations + consumerLocations
        val uniformScopeCommandIds = uniformScopeLocations.map { it.packet.commandIdValue }
        val uniformScopeKeys = uniformScopeLocations.map { location ->
            location.packet.corePrimitivePreparedAuthority?.structuralPipelineKey
                ?: return refuse("Prepared clip-stencil uniform structural key is missing.")
        }
        val uniformScopePayloads = uniformScopeLocations.map { location ->
            (location.packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)
                ?.payloadRef?.uniformBlock?.bytes
                ?: return refuse("Prepared clip-stencil uniform payload bytes are missing.")
        }
        val exactUniformPayloads = uniformScopeCommandIds.zip(uniformScopePayloads).map {
                (commandId, bytes) ->
            GPUUniformSlabPayload("draw-$commandId", bytes.map(Int::toByte).toByteArray())
        }
        val analyticUniformValid = analyticShapeUniformSeal?.let { seal ->
            val consumer = consumerLocations.single().packet
            val semantic = consumer.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            val payload = semantic?.let { preparedSemantic ->
                when (
                    val rebuilt = buildCorePrimitiveAnalyticShapeUniform(
                        preparedSemantic,
                        GPUCorePrimitivePreparedSemanticAuthority.capture(preparedSemantic),
                    )
                ) {
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> rebuilt.bytes
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> null
                }
            }
            seal.commandId == consumer.commandIdValue &&
                seal.packetId == consumer.packetId &&
                semantic != null &&
                payload != null &&
                seal.hasExactSemantic(semantic) &&
                seal.hasExactPayload(payload) &&
                seal.structuralPipelineKey == consumer.corePrimitivePreparedAuthority?.structuralPipelineKey &&
                seal.structuralPipelineKey.uniformLayout in setOf(
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1,
                ) &&
                seal.plan.sourceLabel == (if (semantic.geometry is GPUCorePrimitiveGeometry.DRRect)
                    "core-primitive-analytic-drrect-uniform-pass" else "core-primitive-analytic-shape-uniform-pass") &&
                seal.plan.deviceGeneration == context.deviceGeneration.value &&
                seal.plan.alignmentBytes == limits.minUniformBufferOffsetAlignment &&
                seal.plan.slots.size == 1 && seal.plan.slots.single().payloadBytes ==
                    (if (semantic.geometry is GPUCorePrimitiveGeometry.DRRect) 128L else 80L) &&
                seal.plan.hasExactPayloads(
                    if (semantic.geometry is GPUCorePrimitiveGeometry.DRRect)
                        "core-primitive-analytic-drrect-uniform-pass"
                    else "core-primitive-analytic-shape-uniform-pass",
                    context.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    listOf(
                        GPUUniformSlabPayload(
                            if (semantic.geometry is GPUCorePrimitiveGeometry.DRRect)
                                "analytic-drrect-draw-${consumer.commandIdValue}"
                            else "analytic-shape-draw-${consumer.commandIdValue}",
                            payload,
                        ),
                    ),
                ) &&
                seal.plan.slots.single().alignedOffset <= UInt.MAX_VALUE.toLong() &&
                seal.plan.totalBytes <= maxBufferSize && maxDynamicUniformBuffers >= 1L &&
                uniformPreparation.role == GPUFrameResourceRole.UniformData &&
                uniformPreparation.usages == setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.Uniform,
                ) && uniformPreparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                uniformPreparation.byteSize == seal.plan.totalBytes &&
                uniformDescriptor.byteSize == seal.plan.totalBytes &&
                uniformDescriptor.alignmentBytes == seal.plan.alignmentBytes
        } ?: false
        if ((!usesAnalyticClipConsumerUniform && (uniformSeals.any { it !== uniformSeal } ||
            uniformScopeLocations.any {
                it.packet.corePrimitivePreparedAuthority?.uniformSlabSeal !== uniformSeal
            } ||
            requireNotNull(uniformSeal).commandIds != uniformScopeCommandIds ||
            !requireNotNull(uniformSeal).hasExactPayloads(
                uniformScopeCommandIds,
                uniformScopePayloads,
            ) || !requireNotNull(uniformSeal).plan.hasExactPayloads(
                "core-primitive-uniform-pass",
                context.deviceGeneration.value,
                limits.minUniformBufferOffsetAlignment,
                exactUniformPayloads,
            ) || requireNotNull(uniformSeal).plan.slots.zip(uniformScopeKeys).any { (slot, key) ->
                val expectedBytes = when (key.uniformLayout) {
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 -> 32L
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 -> 592L
                    else -> -1L
                }
                slot.payloadBytes != expectedBytes || slot.alignedOffset > UInt.MAX_VALUE.toLong()
            } || requireNotNull(uniformSeal).plan.sourceLabel != "core-primitive-uniform-pass" ||
            requireNotNull(uniformSeal).plan.deviceGeneration != context.deviceGeneration.value ||
            requireNotNull(uniformSeal).plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            requireNotNull(uniformSeal).plan.totalBytes > maxBufferSize || maxDynamicUniformBuffers < 1L ||
            requireNotNull(uniformSeal).plan.slots.size != uniformScopeLocations.size ||
            uniformPreparation.role != GPUFrameResourceRole.UniformData ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformPreparation.byteSize != requireNotNull(uniformSeal).plan.totalBytes ||
            uniformDescriptor.byteSize != requireNotNull(uniformSeal).plan.totalBytes ||
            uniformDescriptor.alignmentBytes != requireNotNull(uniformSeal).plan.alignmentBytes
        )) || (usesAnalyticClipConsumerUniform && !analyticUniformValid)) {
            return refuse("Prepared clip-stencil uniform slab authority was substituted.")
        }

        val sealedResources = setOf(
            vertexUse.resource,
            indexUse.resource,
            uniformResource,
            depthStencilUse.resource,
        )
        val sealedLocations = (candidateLocations + prefixLocations).sortedBy { it.sourceStepIndex }
        val exactRenderUses = sealedLocations
            .flatMap { it.render.resourceUses }
            .filter { it.resource in sealedResources }
        val allRenderUses = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::resourceUses)
            .filter { it.resource in sealedResources }
        if (allRenderUses != exactRenderUses) {
            return refuse(
                "A foreign render scope uses a sealed clip-stencil slab or attachment: " +
                    "all=$allRenderUses exact=$exactRenderUses",
            )
        }
        val sealedRenderStepIndices = (candidateLocations + prefixLocations).map { it.sourceStepIndex }.toSet()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            if (step is GPUFrameStep.PrepareResourcesStep ||
                sourceStepIndex in sealedRenderStepIndices
            ) return@forEachIndexed
            if (referencedResources(step).any { it in sealedResources }) {
                return refuse("A foreign frame step references a sealed clip-stencil resource.")
            }
        }

        val targetPreparation = sceneTargetPreparation
        val targetDescriptor = sceneTargetDescriptor
        val targetBounds = targetDescriptor.logicalBounds
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            candidate.attachmentWidth != targetBounds.width ||
            candidate.attachmentHeight != targetBounds.height ||
            !isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                producerLocation.render.target,
                targetBounds,
                targetDescriptor.format,
            )
        ) return refuse("Prepared clip-stencil target dimensions or origin were substituted.")

        val depthStencilPreparation = preparations.singleOrNull {
            it.resource == depthStencilUse.resource
        } ?: return refuse("Prepared clip-stencil D24S8 preparation is missing.")
        val depthStencilDescriptor = depthStencilPreparation.descriptor as?
            GPUFrameTextureDescriptor
            ?: return refuse("Prepared clip-stencil D24S8 authority is not a texture.")
        val expectedDepthStencilBytes = try {
            corePrimitiveDepthStencilByteSize(targetBounds, attachmentSampleCount)
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil D24S8 byte size overflowed.")
        }
        if (depthStencilPreparation.role != GPUFrameResourceRole.ClipDepthStencil ||
            depthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencilDescriptor.logicalBounds != targetBounds ||
            depthStencilDescriptor.format.value != "depth24plus-stencil8" ||
            depthStencilDescriptor.sampleCount != attachmentSampleCount ||
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
            sampleCount = attachmentSampleCount,
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
                sceneTargetDescriptor.format,
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
                analyticShapeUniformSeal,
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
        val prefixGeometry = prefixLocations.singleOrNull()?.packet?.semanticPayload
            ?.let { it as? GPUDrawSemanticPayload.CorePrimitive }
            ?.geometry
            ?.let { it as? GPUCorePrimitiveGeometry.Rect }
        val prefixVertexBytes = if (prefixGeometry == null) 0L else 8L * Float.SIZE_BYTES
        val prefixIndexBytes = if (prefixGeometry == null) 0L else 6L * Int.SIZE_BYTES
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
                prefixCommandIds = prefixLocations.map { it.packet.commandIdValue },
                prefixPacketIdsBySourceStepIndex = prefixLocations.associate { location ->
                    location.sourceStepIndex to location.packet.packetId
                },
                prefixVertexBytes = prefixVertexBytes,
                prefixIndexBytes = prefixIndexBytes,
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
            Math.addExact(
                Math.multiplyExact(routeSeal.geometryArena.vertexFloatCount.toLong(), 4L),
                prefixVertexBytes,
            )
        } catch (_: ArithmeticException) {
            return refuse("Prepared clip-stencil vertex slab size overflowed.")
        }
        val indexBytes = try {
            Math.addExact(
                Math.multiplyExact(routeSeal.geometryArena.indexCount.toLong(), 4L),
                prefixIndexBytes,
            )
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

    private data class PlannedPathSessionValidation(
        val directRouteSeal: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        val pathRouteSeal: GPUCorePrimitivePathStencilNativeFrameRouteSeal,
        val unifiedRouteSeal: GPUCorePrimitiveNativeScopeFrameRouteSeal,
    )

    /** Reuses strict standalone preflight, then relocates only its already sealed packet ranges. */
    private fun validateW5aComposite(
        frame: GPUFramePlan,
        authority: org.graphiks.kanvas.gpu.renderer.planning.W5aCompositeFrameAuthorityV1,
    ): PlannedPathSessionValidation? {
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (!authority.validates(frame, renders)) return null
        var direct = GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty
        var path = GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty
        var unified = GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty
        var cursor = 0
        for (lane in authority.lanes) {
            val laneFrame = org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner.plan(lane.standaloneTaskList)
            if (laneFrame.atomicallyRefused) return null
            val laneRenders = laneFrame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            val indices = laneRenders.mapIndexed { index, render ->
                laneFrame.steps.indexOf(render) to frame.steps.indexOf(renders[cursor + index])
            }.toMap()
            cursor += laneRenders.size
            val scratch = lane.packets.first().corePrimitivePreparedAuthority ?: return null
            val validation = when (lane.capabilityId) {
                org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler.W5A_CAPABILITY_ID -> {
                    if (!hasExactW3SessionScratch(laneFrame, laneRenders, scratch.w3SessionScratch ?: return null)) return null
                    val pure = pureValidation(laneFrame)
                    if (pure.diagnostic != null) return null
                    PlannedPathSessionValidation(pure.corePrimitiveDirectRoutes, pure.corePrimitivePathStencilRoutes, pure.corePrimitiveNativeScopeRoutes)
                }
                org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler.CAPABILITY_ID -> {
                    val material = scratch.w5aAnalyticRRectSessionScratch ?: return null
                    if (!material.validatesMaterialPlanVersion() || !hasExactW4bSessionScratch(laneFrame, material.payloadFacts, material, authority.sessionIdentity)) return null
                    val pure = pureValidation(laneFrame, skipNativeCorePrimitiveClassification = true)
                    if (pure.diagnostic != null) return null
                    PlannedPathSessionValidation(pure.corePrimitiveDirectRoutes, pure.corePrimitivePathStencilRoutes, pure.corePrimitiveNativeScopeRoutes)
                }
                org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler.CAPABILITY_ID ->
                    validateW4cSessionScratch(laneFrame, scratch.w4cSessionScratch ?: return null) ?: return null
                org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler.CAPABILITY_ID ->
                    validateW4dSessionScratch(laneFrame, scratch.w4dSessionScratch ?: return null, authority.sessionIdentity) ?: return null
                else -> return null
            }
            direct = direct.appended(validation.directRouteSeal.reindexed(indices))
            path = path.appended(validation.pathRouteSeal.reindexed(indices))
            unified = unified.appended(validation.unifiedRouteSeal.reindexed(indices))
        }
        return PlannedPathSessionValidation(direct, path, unified)
    }

    /**
     * Re-authenticates a sealed W4c/W4d envelope mechanically before generic path classification.
     * The retained routes copy only already-planned math geometry; no tessellation or fallback
     * enters this lane.
     */
    private fun validateW4cSessionScratch(
        framePlan: GPUFramePlan,
        scratch: W4cSessionScratchV1,
    ): PlannedPathSessionValidation? = validatePlannedPathSessionScratch(
        framePlan,
        GPUPlannedPathSessionScratch.from(scratch),
    )

    private fun validateW4dSessionScratch(
        framePlan: GPUFramePlan,
        scratch: W4dSessionScratchV1,
        compositeSessionIdentity: String? = null,
    ): PlannedPathSessionValidation? = validatePlannedPathSessionScratch(
        framePlan,
        GPUPlannedPathSessionScratch.from(scratch),
        compositeSessionIdentity,
    )

    private fun validatePlannedPathSessionScratch(
        framePlan: GPUFramePlan,
        scratch: GPUPlannedPathSessionScratch,
        compositeSessionIdentity: String? = null,
    ): PlannedPathSessionValidation? = try {
        val limits = capabilities.limits ?: return null
        val lane = scratch.lane.label
        val renders = framePlan.steps.withIndex().mapNotNull { indexed ->
            (indexed.value as? GPUFrameStep.RenderPassStep)?.let { indexed.index to it }
        }
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .singleOrNull() ?: return null
        val taskPrefix = "task.$lane.${scratch.planId}"
        val expectedReadbackId = "$lane.${scratch.planId}.readback"
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val targetPreparation = preparations.singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
            ?: return null
        val stagingPreparation = preparations.singleOrNull { it.role == GPUFrameResourceRole.ReadbackStaging }
            ?: return null
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor ?: return null
        val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor ?: return null
        val targetBytes = Math.multiplyExact(
            Math.multiplyExact(scratch.targetBounds.width.toLong(), scratch.targetBounds.height.toLong()),
            4L,
        )
        val logicalRowBytes = Math.multiplyExact(scratch.targetBounds.width.toLong(), 4L)
        val rowAlignment = limits.copyBytesPerRowAlignment
        if (rowAlignment <= 0L) return null
        val paddedRowBytes = Math.addExact(
            logicalRowBytes,
            (rowAlignment - logicalRowBytes % rowAlignment) % rowAlignment,
        )
        val stagingBytes = Math.multiplyExact(paddedRowBytes, scratch.targetBounds.height.toLong())
        if (framePlan.steps.size != renders.size + 2 ||
            framePlan.steps.firstOrNull() !is GPUFrameStep.PrepareResourcesStep ||
            framePlan.steps.lastOrNull() !is GPUFrameStep.ReadbackCopyStep ||
            framePlan.steps.drop(1).dropLast(1).any { step -> step !is GPUFrameStep.RenderPassStep } ||
            framePlan.steps.first().sourceTaskIds.singleOrNull()?.value != "$taskPrefix.prepare" ||
            readback.sourceTaskIds.singleOrNull()?.value != "$taskPrefix.readback.Readback:0" ||
            framePlan.recordingSeals.size != 1 ||
            framePlan.recordingSeals.single().compatibilityKeyHash != "$lane:${scratch.planId}" ||
            framePlan.recordingSeals.single().replayKeyHash != "$lane:${scratch.planId}" ||
            framePlan.recordingSeals.single().capabilitySealHash != framePlan.capabilitySeal.sealHash ||
            readback.request.requestId.value != expectedReadbackId ||
            readback.staging != scratch.staging || readback.source != scratch.target ||
            readback.request.sourceBounds != scratch.targetBounds ||
            targetPreparation.resource != scratch.target ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) ||
            targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            targetPreparation.byteSize != targetBytes ||
            targetDescriptor.logicalBounds != scratch.targetBounds ||
            targetDescriptor.format != GPUColorFormat.RGBA8UnormSrgb || targetDescriptor.sampleCount != 1 ||
            stagingPreparation.resource != scratch.staging ||
            stagingPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.MapRead,
            ) ||
            stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            stagingPreparation.byteSize != stagingBytes ||
            stagingDescriptor.byteSize != stagingBytes ||
            stagingDescriptor.alignmentBytes != limits.copyBytesPerRowAlignment ||
            preparations.any { request ->
                request.role in setOf(
                    GPUFrameResourceRole.VertexData,
                    GPUFrameResourceRole.IndexData,
                    GPUFrameResourceRole.UniformData,
                    GPUFrameResourceRole.PathDepthStencil,
                )
            } ||
            scratch.deviceGeneration != context.deviceGeneration.value ||
            scratch.maxBufferSize != limits.maxBufferSize ||
            scratch.maxDynamicUniformBuffersPerPipelineLayout !=
            limits.maxDynamicUniformBuffersPerPipelineLayout ||
            scratch.uniformStrideBytes != limits.minUniformBufferOffsetAlignment ||
            scratch.vertexCapacityBytes != scratch.poolCapacities.vertexBytes ||
            scratch.indexCapacityBytes != scratch.poolCapacities.indexBytes ||
            scratch.uniformCapacityBytes != scratch.poolCapacities.uniformBytes ||
            scratch.vertexUsefulBytes !in 1L..Int.MAX_VALUE.toLong() ||
            scratch.indexUsefulBytes !in 1L..Int.MAX_VALUE.toLong() ||
            scratch.uniformPlan.totalBytes !in 1L..Int.MAX_VALUE.toLong() ||
            scratch.vertexUsefulBytes % Float.SIZE_BYTES != 0L ||
            scratch.indexUsefulBytes % Int.SIZE_BYTES != 0L ||
            !scratch.matches(
                scratch.planId,
                framePlan.capabilitySeal.sealHash,
                context.deviceGeneration.value,
                scratch.target,
                scratch.staging,
                scratch.targetBounds,
            )
        ) return null

        if (scratch.lane == GPUPlannedPathSessionScratch.Lane.W4d &&
            (preparations.size != 2 ||
                (scratch.capabilityId != W4dPathStrokePlanCompiler.HISTORICAL_CAPABILITY_ID &&
                    scratch.capabilityId != W4dPathStrokePlanCompiler.CAPABILITY_ID) ||
                scratch.targetBytes != targetBytes || scratch.stagingBytes != stagingBytes ||
                scratch.renderPassIds != renders.map { (_, render) ->
                PlanPassId(render.drawPackets.singleOrNull()?.passId ?: return null)
            } ||
                scratch.readbackPassId != PlanPassId("Readback:0") ||
                scratch.resourceLastPassIndexExclusive != renders.size + 1)
        ) return null


        val usesStencil = scratch.draws.any { draw -> draw.strategy == PathFillStrategy.StencilCover }
        val expectedDepthStencilBytes = if (usesStencil) {
            Math.multiplyExact(
                Math.multiplyExact(scratch.targetBounds.width.toLong(), scratch.targetBounds.height.toLong()),
                4L,
            )
        } else {
            0L
        }
        val expectedDepthStencilFirstPassIndex = if (usesStencil) {
            var renderPassIndex = 0
            scratch.draws.firstNotNullOfOrNull { draw ->
                if (draw.strategy == PathFillStrategy.StencilCover) {
                    renderPassIndex
                } else {
                    renderPassIndex += 1
                    null
                }
            }
        } else {
            null
        }
        val nativeByteRanges = buildList {
            add(
                GPUPlannedPathNativeByteRange(
                    GPUPlannedPathNativeByteRange.Resource.Vertex,
                    0L,
                    scratch.vertexUsefulBytes,
                    scratch.vertexCapacityBytes,
                    Float.SIZE_BYTES.toLong(),
                    scratch.maxBufferSize,
                ),
            )
            add(
                GPUPlannedPathNativeByteRange(
                    GPUPlannedPathNativeByteRange.Resource.Index,
                    0L,
                    scratch.indexUsefulBytes,
                    scratch.indexCapacityBytes,
                    Int.SIZE_BYTES.toLong(),
                    scratch.maxBufferSize,
                ),
            )
            add(
                GPUPlannedPathNativeByteRange(
                    GPUPlannedPathNativeByteRange.Resource.Uniform,
                    0L,
                    scratch.uniformPlan.totalBytes,
                    scratch.uniformCapacityBytes,
                    1L,
                    scratch.maxBufferSize,
                ),
            )
            add(
                GPUPlannedPathNativeByteRange(
                    GPUPlannedPathNativeByteRange.Resource.Readback,
                    0L,
                    stagingBytes,
                    stagingPreparation.byteSize,
                    1L,
                    scratch.maxBufferSize,
                ),
            )
            if (usesStencil) {
                add(
                    GPUPlannedPathNativeByteRange(
                        GPUPlannedPathNativeByteRange.Resource.DepthStencil,
                        0L,
                        expectedDepthStencilBytes,
                        scratch.depthStencilBytes,
                        4L,
                    ),
                )
            }
            scratch.draws.forEach { draw ->
                add(
                    GPUPlannedPathNativeByteRange(
                        GPUPlannedPathNativeByteRange.Resource.Vertex,
                        draw.vertexOffsetBytes,
                        draw.vertexRangeBytes,
                        scratch.vertexUsefulBytes,
                        Float.SIZE_BYTES.toLong(),
                    ),
                )
                add(
                    GPUPlannedPathNativeByteRange(
                        GPUPlannedPathNativeByteRange.Resource.Index,
                        draw.indexOffsetBytes,
                        draw.indexRangeBytes,
                        scratch.indexUsefulBytes,
                        Int.SIZE_BYTES.toLong(),
                    ),
                )
            }
        }
        if (validatePlannedPathNativeByteRanges(nativeByteRanges) !is
            GPUPlannedPathNativeByteRangeValidation.Accepted
        ) return null
        if (
            scratch.vertexResourceId != PlanResourceId("VertexData:0") ||
                scratch.indexResourceId != PlanResourceId("IndexData:0") ||
                scratch.uniformResourceId != PlanResourceId("UniformData:0") ||
                scratch.depthStencilResourceId !=
                (if (usesStencil) PlanResourceId("DepthStencil:0") else null) ||
                scratch.depthStencilBytes != expectedDepthStencilBytes ||
                scratch.lane == GPUPlannedPathSessionScratch.Lane.W4d &&
                scratch.depthStencilFirstPassIndex != expectedDepthStencilFirstPassIndex
        ) return null

        if (scratch.lane == GPUPlannedPathSessionScratch.Lane.W4d) {
            val identity = compositeSessionIdentity ?: ("w4d.session.${scratch.deviceGeneration}." +
                "${scratch.targetBounds.width}x${scratch.targetBounds.height}.rgba8unorm-srgb")
            val expectedAllocations = buildList {
                add(
                    GPUFrameMemoryAllocation(
                        "$identity.target",
                        GPUFrameMemoryCategory.CanonicalTarget,
                        targetBytes,
                        GPUFrameMemoryResourceKind.Texture2D,
                        scratch.targetBounds,
                    ),
                )
                add(
                    GPUFrameMemoryAllocation(
                        "$identity.staging",
                        GPUFrameMemoryCategory.ReadbackStaging,
                        stagingBytes,
                        GPUFrameMemoryResourceKind.Buffer,
                        null,
                    ),
                )
                listOf(
                    "vertex" to scratch.vertexCapacityBytes,
                    "index" to scratch.indexCapacityBytes,
                    "uniform" to scratch.uniformCapacityBytes,
                ).forEach { (label, bytes) ->
                    add(
                        GPUFrameMemoryAllocation(
                            "$identity.$label",
                            GPUFrameMemoryCategory.ReusableScratch,
                            bytes,
                            GPUFrameMemoryResourceKind.Buffer,
                            null,
                        ),
                    )
                }
                if (usesStencil) {
                    add(
                        GPUFrameMemoryAllocation(
                            "$identity.depth-stencil",
                            GPUFrameMemoryCategory.ReusableScratch,
                            expectedDepthStencilBytes,
                            GPUFrameMemoryResourceKind.Texture2D,
                            scratch.targetBounds,
                        ),
                    )
                }
            }
            val transientBytes = listOf(
                stagingBytes,
                scratch.vertexCapacityBytes,
                scratch.indexCapacityBytes,
                scratch.uniformCapacityBytes,
                expectedDepthStencilBytes,
            ).fold(0L, Math::addExact)
            val expectedCategoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
                expectedAllocations.filter { allocation -> allocation.category == category }
                    .fold(0L) { total, allocation -> Math.addExact(total, allocation.bytes) }
            }
            if (framePlan.memoryBudget.diagnostic != null ||
                framePlan.memoryBudget.allocations != expectedAllocations ||
                framePlan.memoryBudget.categoryTotals != expectedCategoryTotals ||
                framePlan.memoryBudget.targetResidentBytes != targetBytes ||
                framePlan.memoryBudget.peakFrameTransientBytes != transientBytes ||
                Math.addExact(targetBytes, transientBytes) >
                framePlan.memoryBudget.configuredAggregateBudgetBytes
            ) return null
        }

        scratch.draws.forEach { draw ->
            val vertexEnd = Math.addExact(draw.vertexOffsetBytes, draw.vertexRangeBytes)
            val indexEnd = Math.addExact(draw.indexOffsetBytes, draw.indexRangeBytes)
            if (draw.vertexOffsetBytes % Float.SIZE_BYTES != 0L ||
                draw.vertexRangeBytes % Float.SIZE_BYTES != 0L ||
                draw.indexOffsetBytes % Int.SIZE_BYTES != 0L ||
                draw.indexRangeBytes % Int.SIZE_BYTES != 0L ||
                vertexEnd > scratch.vertexUsefulBytes || indexEnd > scratch.indexUsefulBytes ||
                draw.vertexOffsetBytes > Int.MAX_VALUE.toLong() ||
                draw.indexOffsetBytes > Int.MAX_VALUE.toLong()
            ) return null
        }

        val locations = renders.flatMap { (stepIndex, render) ->
            render.drawPackets.map { packet -> Triple(stepIndex, render, packet) }
        }
        if (locations.size != scratch.draws.sumOf { draw ->
                if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2
            } ||
            locations.map { it.third.packetId }.distinct().size != locations.size ||
            renders.any { (_, render) ->
                render.drawPackets.size != 1 || render.sourceTaskIds.size != 1 ||
                    render.batches.singleOrNull()?.packets != render.drawPackets ||
                    render.batches.singleOrNull()?.sourceTaskIds != render.sourceTaskIds ||
                    render.samplePlan != GPUSamplePlan.SingleSampleFrame || render.target != scratch.target
            }
        ) return null

        val uniformBytes = ByteArray(scratch.uniformPlan.totalBytes.toInt())
        val uniformBytesBySlot = ArrayList<List<Int>>(scratch.uniformPlan.slots.size)
        val uniformCommandIds = ArrayList<Int>(scratch.uniformPlan.slots.size)
        val directRoutes = linkedMapOf<
            GPUCorePrimitiveDirectNativeFrameRouteKey,
            GPUCorePrimitiveDirectNativeRoute.Accepted,
        >()
        val pathRoutes = linkedMapOf<
            GPUCorePrimitivePathStencilNativeFrameRouteKey,
            GPUCorePrimitivePathStencilNativeRoute.AcceptedPair,
        >()
        val unifiedRoutes = linkedMapOf<
            GPUCorePrimitiveNativeScopeFrameRouteKey,
            GPUCorePrimitiveNativeScopeRouteSeal.Routes,
        >()
        var locationCursor = 0
        var directPassOrdinal = 0
        var producerPassOrdinal = 0
        var coverPassOrdinal = 0
        val expectedAtomicGroupByFromTaskId = linkedMapOf<String, String?>()
        scratch.draws.forEachIndexed { drawIndex, draw ->
            val expectedPacketCount = if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2
            val scopedLocations = locations.subList(locationCursor, locationCursor + expectedPacketCount)
            locationCursor += expectedPacketCount
            val packets = scopedLocations.map(Triple<Int, GPUFrameStep.RenderPassStep, GPUDrawPacket>::third)
            val semantics = packets.map { it.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive }
            val uniformSlots = when (draw.strategy) {
                PathFillStrategy.DirectTriangle -> listOf(draw.uniformSlotIndex)
                PathFillStrategy.StencilCover -> listOf(draw.producerUniformSlotIndex ?: return null, draw.uniformSlotIndex)
            }
            if (semantics.size != uniformSlots.size) return null
            semantics.zip(uniformSlots).forEachIndexed { uniformIndex, (semantic, slotIndex) ->
                val uniform = semantic?.payloadRef?.uniformBlock?.bytes ?: return null
                val slot = scratch.uniformPlan.slots.getOrNull(slotIndex) ?: return null
                if (uniform.size.toLong() != scratch.lane.uniformPayloadBytes ||
                    slot.payloadBytes != scratch.lane.uniformPayloadBytes ||
                    slot.alignedOffset != slotIndex.toLong() * scratch.uniformStrideBytes ||
                    slot.alignedOffset > Int.MAX_VALUE.toLong() ||
                    slot.alignedOffset + uniform.size > uniformBytes.size
                ) return null
                uniform.forEachIndexed { index, value -> uniformBytes[slot.alignedOffset.toInt() + index] = value.toByte() }
                uniformBytesBySlot += uniform
                uniformCommandIds += draw.commandId
            }
            if (packets.any { packet ->
                    val authority = packet.corePrimitivePreparedAuthority ?: return@any true
                    !scratch.owns(authority) ||
                        authority.uniformSlabSeal != null || authority.analyticShapeUniformSeal != null ||
                        authority.analyticClipUniformSeal != null || authority.analyticIntersectionUniformSeal != null ||
                        authority.coverageMaskUniformSlabSeal != null ||
                        !scratch.matchesPreparedPacket(
                            packet,
                            authority.structuralPipelineKey,
                            authority.renderPipelineKey,
                        )
                }
            ) return null

            val expectedLoad = if (drawIndex == 0) "clear" else "load"
            when (draw.strategy) {
                PathFillStrategy.DirectTriangle -> {
                    val (stepIndex, render, packet) = scopedLocations.single()
                    val semantic = requireNotNull(semantics.single())
                    val direct = draw.copyGeometryF32().copyDirectTriangleF32OrNull() ?: return null
                    val authority = requireNotNull(packet.corePrimitivePreparedAuthority)
                    val expectedPassId = "MainRender:${directPassOrdinal++}"
                    val sourceTaskId = render.sourceTaskIds.singleOrNull()?.value ?: return null
                    if (draw.atomicGroupId != null ||
                        packet.packetId.value != "packet.$lane.${draw.commandId}.direct" ||
                        packet.passId != expectedPassId ||
                        sourceTaskId != "$taskPrefix.render.$expectedPassId" ||
                        packet.role != GPUDrawPacketRole.Shading ||
                        render.loadStore != GPULoadStorePlan(expectedLoad, GPUStorePlan.Store) ||
                        render.depthStencilLoadStore != null || render.resourceUses.isNotEmpty() ||
                        semantic.scissorBounds != draw.copyScissorBounds()
                    ) return null
                    expectedAtomicGroupByFromTaskId[sourceTaskId] = null
                    val route = GPUCorePrimitiveDirectNativeRoute.Accepted(
                        direct.copyVerticesF32(),
                        direct.copyIndicesI32(),
                        GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry,
                        draw.copyScissorBounds(),
                    )
                    directRoutes[GPUCorePrimitiveDirectNativeFrameRouteKey(stepIndex, packet.packetId)] = route
                    unifiedRoutes[GPUCorePrimitiveNativeScopeFrameRouteKey(stepIndex, packet.packetId)] =
                        GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                            listOf(
                                GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                                    draw.commandId,
                                    packet.packetId,
                                    route,
                                    authority.structuralPipelineKey,
                                ),
                            ),
                            scratchUniformSeal(scratch, uniformBytes),
                            GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(draw.uniformSlotIndex, 1),
                        )
                }
                PathFillStrategy.StencilCover -> {
                    val producerLocation = scopedLocations[0]
                    val coverLocation = scopedLocations[1]
                    val producer = producerLocation.third
                    val cover = coverLocation.third
                    val producerAuthority = requireNotNull(producer.corePrimitivePreparedAuthority)
                    val coverAuthority = requireNotNull(cover.corePrimitivePreparedAuthority)
                    val fan = draw.copyGeometryF32().copyStencilEdgeFanF32OrNull() ?: return null
                    val expectedAtomicGroupId = draw.atomicGroupId ?: return null
                    val expectedProducerPassId = "StencilProducer:${producerPassOrdinal++}"
                    val expectedCoverPassId = "StencilCover:${coverPassOrdinal++}"
                    val producerTaskId = producerLocation.second.sourceTaskIds.singleOrNull()?.value ?: return null
                    val coverTaskId = coverLocation.second.sourceTaskIds.singleOrNull()?.value ?: return null
                    val expectedDepthStencilResource = scratch.target.value
                        .removeSuffix(".target")
                        .plus(".depth-stencil")
                    val expectedUse = { render: GPUFrameStep.RenderPassStep ->
                        render.resourceUses.singleOrNull { use ->
                            use.role == GPUFrameResourceRole.PathDepthStencil &&
                                use.usage == GPUFrameResourceUsage.RenderAttachment &&
                                use.lifetime == GPUFrameResourceLifetime.FrameLocal && use.write &&
                                use.resource.value == expectedDepthStencilResource
                        } != null && render.resourceUses.size == 1
                    }
                    if (scratch.depthStencilResourceId == null ||
                        draw.atomicGroupId != expectedAtomicGroupId ||
                        producer.packetId.value != "packet.$lane.${draw.commandId}.producer" ||
                        cover.packetId.value != "packet.$lane.${draw.commandId}.cover" ||
                        producer.passId != expectedProducerPassId || cover.passId != expectedCoverPassId ||
                        producerTaskId != "$taskPrefix.render.$expectedProducerPassId" ||
                        coverTaskId != "$taskPrefix.render.$expectedCoverPassId" ||
                        producer.role != GPUDrawPacketRole.PathStencilProducer ||
                        cover.role != GPUDrawPacketRole.PathStencilCover ||
                        producer.commandIdValue != draw.commandId || cover.commandIdValue != draw.commandId ||
                        producerLocation.second.loadStore != GPULoadStorePlan(expectedLoad, GPUStorePlan.Store) ||
                        coverLocation.second.loadStore != GPULoadStorePlan("load", GPUStorePlan.Store) ||
                        producerLocation.second.depthStencilLoadStore !=
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                            org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                            GPUStorePlan.Store,
                            0u,
                        ) ||
                        coverLocation.second.depthStencilLoadStore !=
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                            org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Load,
                            GPUStorePlan.Store,
                            null,
                        ) || !expectedUse(producerLocation.second) || !expectedUse(coverLocation.second)
                    ) return null
                    expectedAtomicGroupByFromTaskId[producerTaskId] = expectedAtomicGroupId
                    expectedAtomicGroupByFromTaskId[coverTaskId] = null
                    val pair = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
                        producer.packetId,
                        cover.packetId,
                        fan.copyVerticesF32(),
                        fan.copyIndicesI32(),
                        draw.copyScissorBounds(),
                        scratch.targetBounds,
                        inverseFill = false,
                    )
                    pathRoutes[
                        GPUCorePrimitivePathStencilNativeFrameRouteKey(
                            producerLocation.first,
                            producer.packetId,
                            cover.packetId,
                        )
                    ] = pair
                    val uniformSeal = scratchUniformSeal(scratch, uniformBytes)
                    unifiedRoutes[
                        GPUCorePrimitiveNativeScopeFrameRouteKey(producerLocation.first, producer.packetId)
                    ] = GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        listOf(
                            GPUCorePrimitiveNativeScopeRouteUnit.PathProducer(
                                draw.commandId,
                                producer.packetId,
                                producerAuthority.structuralPipelineKey,
                                pair.producer,
                            ),
                        ),
                        uniformSeal,
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(
                            requireNotNull(draw.producerUniformSlotIndex), 1,
                        ),
                    )
                    unifiedRoutes[
                        GPUCorePrimitiveNativeScopeFrameRouteKey(coverLocation.first, cover.packetId)
                    ] = GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        listOf(
                            GPUCorePrimitiveNativeScopeRouteUnit.PathCover(
                                draw.commandId,
                                cover.packetId,
                                coverAuthority.structuralPipelineKey,
                                pair.cover,
                            ),
                        ),
                        uniformSeal,
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(draw.uniformSlotIndex, 1),
                    )
                }
            }
        }
        if (locationCursor != locations.size ||
            !GPUCorePrimitiveUniformSlabSeal(
                scratch.uniformPlan,
                uniformCommandIds,
                uniformBytes,
            ).hasExactPayloads(uniformCommandIds, uniformBytesBySlot) ||
            scratch.depthStencilResourceId == null && pathRoutes.isNotEmpty() ||
            scratch.depthStencilResourceId != null && pathRoutes.isEmpty()
        ) return null
        val taskIds = framePlan.steps.map { step -> step.sourceTaskIds.singleOrNull() ?: return null }
        if (framePlan.dependencies.size != taskIds.size - 1 ||
            framePlan.dependencies.map { dependency -> dependency.fromTaskId to dependency.toTaskId } !=
            taskIds.zipWithNext() ||
            framePlan.dependencies.any { dependency ->
                dependency.atomicGroupId?.value !=
                    expectedAtomicGroupByFromTaskId[dependency.fromTaskId.value]
            }
        ) return null
        val directSeal = GPUCorePrimitiveDirectNativeFrameRouteSeal(directRoutes)
        val pathSeal = GPUCorePrimitivePathStencilNativeFrameRouteSeal(pathRoutes)
        val unifiedSeal = GPUCorePrimitiveNativeScopeFrameRouteSeal(unifiedRoutes)
        PlannedPathSessionValidation(directSeal, pathSeal, unifiedSeal)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    /** Projects immutable W4c geometry after the complete W5b frame has been authenticated. */
    private fun validateW5bPathGeometry(frame: GPUFramePlan,
        witness: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3): PlannedPathSessionValidation {
        require(witness.validates(frame))
        val directRoutes = linkedMapOf<GPUCorePrimitiveDirectNativeFrameRouteKey, GPUCorePrimitiveDirectNativeRoute.Accepted>()
        val pathRoutes = linkedMapOf<GPUCorePrimitivePathStencilNativeFrameRouteKey, GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>()
        val unifiedRoutes = linkedMapOf<GPUCorePrimitiveNativeScopeFrameRouteKey, GPUCorePrimitiveNativeScopeRouteSeal.Routes>()
        witness.geometryLanes.filterIsInstance<org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.NativePath>().forEach { lane ->
            val scratch = GPUPlannedPathSessionScratch.from(lane, witness)
            val packets = witness.packetsFor(lane)
            require(scratch.uniformPlan.totalBytes in 1L..Int.MAX_VALUE.toLong())
            val bytes = ByteArray(scratch.uniformPlan.totalBytes.toInt())
            packets.forEach { packet ->
                val draw = scratch.draws.single { it.commandId == packet.commandIdValue }
                val slot = scratch.uniformPlan.slots[if (packet.role == GPUDrawPacketRole.PathStencilProducer)
                    requireNotNull(draw.producerUniformSlotIndex) else draw.uniformSlotIndex]
                val block = (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive).payloadRef.uniformBlock!!
                block.bytes.map(Int::toByte).toByteArray().copyInto(bytes, slot.alignedOffset.toInt())
            }
            val uniformSeal = scratchUniformSeal(scratch, bytes)
            fun location(packet: GPUDrawPacket) = frame.steps.indexOfFirst { step ->
                step is GPUFrameStep.RenderPassStep && step.drawPackets.singleOrNull() === packet
            }.also { require(it >= 0) }
            scratch.draws.forEach { draw ->
                val selected = packets.filter { it.commandIdValue == draw.commandId }
                val direct = draw.copyGeometryF32().copyDirectTriangleF32OrNull()
                if (direct != null) {
                    val packet = selected.single()
                    val index = location(packet)
                    val route = GPUCorePrimitiveDirectNativeRoute.Accepted(direct.copyVerticesF32(), direct.copyIndicesI32(),
                        GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry, draw.copyScissorBounds())
                    directRoutes[GPUCorePrimitiveDirectNativeFrameRouteKey(index, packet.packetId)] = route
                    unifiedRoutes[GPUCorePrimitiveNativeScopeFrameRouteKey(index, packet.packetId)] = GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        listOf(GPUCorePrimitiveNativeScopeRouteUnit.Direct(draw.commandId, packet.packetId, route,
                            requireNotNull(packet.corePrimitivePreparedAuthority).structuralPipelineKey)), uniformSeal,
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(draw.uniformSlotIndex, 1))
                } else {
                    val producer = selected[0]
                    val cover = selected[1]
                    val index = location(producer)
                    require(location(cover) == index + 1)
                    val fan = requireNotNull(draw.copyGeometryF32().copyStencilEdgeFanF32OrNull())
                    val pair = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(producer.packetId, cover.packetId,
                        fan.copyVerticesF32(), fan.copyIndicesI32(), draw.copyScissorBounds(), scratch.targetBounds, inverseFill = false)
                    pathRoutes[GPUCorePrimitivePathStencilNativeFrameRouteKey(index, producer.packetId, cover.packetId)] = pair
                    unifiedRoutes[GPUCorePrimitiveNativeScopeFrameRouteKey(index, producer.packetId)] = GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        listOf(GPUCorePrimitiveNativeScopeRouteUnit.PathProducer(draw.commandId, producer.packetId,
                            requireNotNull(producer.corePrimitivePreparedAuthority).structuralPipelineKey, pair.producer)), uniformSeal,
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(requireNotNull(draw.producerUniformSlotIndex), 1))
                    unifiedRoutes[GPUCorePrimitiveNativeScopeFrameRouteKey(index + 1, cover.packetId)] = GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        listOf(GPUCorePrimitiveNativeScopeRouteUnit.PathCover(draw.commandId, cover.packetId,
                            requireNotNull(cover.corePrimitivePreparedAuthority).structuralPipelineKey, pair.cover)), uniformSeal,
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(draw.uniformSlotIndex, 1))
                }
            }
        }
        return PlannedPathSessionValidation(GPUCorePrimitiveDirectNativeFrameRouteSeal(directRoutes),
            GPUCorePrimitivePathStencilNativeFrameRouteSeal(pathRoutes), GPUCorePrimitiveNativeScopeFrameRouteSeal(unifiedRoutes))
    }

    private fun scratchUniformSeal(
        scratch: GPUPlannedPathSessionScratch,
        packedBytes: ByteArray,
    ): GPUCorePrimitiveUniformSlabSeal =
        GPUCorePrimitiveUniformSlabSeal(
            scratch.uniformPlan,
            scratch.draws.flatMap { draw ->
                if (draw.strategy == PathFillStrategy.StencilCover) {
                    listOf(draw.commandId, draw.commandId)
                } else listOf(draw.commandId)
            },
            packedBytes,
        )

    private data class PureValidationResult(
        val diagnostic: GPUDiagnostic?,
        val corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        val corePrimitivePathStencilRoutes: GPUCorePrimitivePathStencilNativeFrameRouteSeal,
        val corePrimitiveNativeScopeRoutes: GPUCorePrimitiveNativeScopeFrameRouteSeal,
        val corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        val corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    )

    private fun pureValidation(
        framePlan: GPUFramePlan,
        skipNativeCorePrimitiveClassification: Boolean = false,
    ): PureValidationResult {
        var corePrimitiveDirectRoutes = GPUCorePrimitiveDirectNativeFrameRouteSeal.Empty
        var corePrimitivePathStencilRoutes = GPUCorePrimitivePathStencilNativeFrameRouteSeal.Empty
        var corePrimitiveNativeScopeRoutes = GPUCorePrimitiveNativeScopeFrameRouteSeal.Empty
        var corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal =
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty
        var corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal =
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty
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
            retainCorePrimitiveCoverageMaskPreparedRoutes = { validation ->
                corePrimitiveCoverageMaskPreparedRoutes = validation.routeSeal
            },
            skipNativeCorePrimitiveClassification = skipNativeCorePrimitiveClassification,
        )
        return PureValidationResult(
            diagnostic,
            corePrimitiveDirectRoutes,
            corePrimitivePathStencilRoutes,
            corePrimitiveNativeScopeRoutes,
            corePrimitiveClipStencilPreparedRoutes,
            corePrimitiveCoverageMaskPreparedRoutes,
        )
    }

    private fun pureValidationDiagnostic(
        framePlan: GPUFramePlan,
        retainCorePrimitiveRoutes: (CorePrimitiveGeometryValidation) -> Unit,
        retainCorePrimitiveClipStencilPreparedRoutes:
            (CorePrimitiveClipStencilPreparedValidation) -> Unit,
        retainCorePrimitiveCoverageMaskPreparedRoutes:
            (CorePrimitiveCoverageMaskPreparedValidation) -> Unit,
        skipNativeCorePrimitiveClassification: Boolean = false,
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
        if (framePlan.w4eRenderSteps().isNotEmpty()) {
            return validateW4eSceneMsaaContinuation(framePlan)
        }
        if (framePlan.w4dGeneralRenderSteps().isNotEmpty()) {
            return validateW4dGeneralMsaaContinuation(framePlan)
        }
        validateCorePrimitivePathMsaaAuthority(framePlan)?.let { return it }
        validateCorePrimitiveSemanticEnvelopes(framePlan)?.let { return it }
        val semanticEnvelopeAuthority = captureCorePrimitiveSemanticEnvelopeAuthority(framePlan)
        validateCorePrimitiveCoverageSampleMatrix(framePlan, semanticEnvelopeAuthority)?.let { return it }
        val coverageMaskPreparedValidation =
            validateCorePrimitiveCoverageMaskPreparedRoutes(framePlan)
        coverageMaskPreparedValidation.diagnostic?.let { return it }
        retainCorePrimitiveCoverageMaskPreparedRoutes(coverageMaskPreparedValidation)
        val clipStencilPreparedValidation =
            validateCorePrimitiveClipStencilPreparedRoutes(framePlan)
        val hasClipStencilPreparedRoute = clipStencilPreparedValidation.routeSeal is
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route &&
            clipStencilPreparedValidation.diagnostic == null
        val hasCoverageMaskPreparedRoute = coverageMaskPreparedValidation.routeSeal is
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route
        val coreSealedCoverageMaskProducerPacketIds =
            (coverageMaskPreparedValidation.routeSeal as?
                GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route)
                ?.slabAuthority?.uniformSlabSeal?.producerPacketIds?.toSet().orEmpty()
        val coverageMaskProducerLocations = framePlan.steps.flatMap { step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@flatMap emptyList()
            render.drawPackets.mapNotNull { packet ->
                packet.coverageMaskProducerUniformSlabSeal?.let { seal ->
                    Triple(render, packet, seal)
                }
            }
        }
        coverageMaskProducerLocations.groupBy { location -> location.third }.forEach { (seal, locations) ->
            val producerPackets = locations.map { location -> location.second }
            if (locations.any { location -> location.first.target != seal.maskResource } ||
                !validateCoverageMaskProducerUniformSlabSeal(producerPackets, seal)
            ) {
                return diagnostic(
                    "invalid.preflight.coverage_mask_producer_slab",
                    "CoverageMask producer packets contradict their common passive slab authority.",
                )
            }
        }
        val alreadySealedCoverageMaskProducerPacketIds =
            coreSealedCoverageMaskProducerPacketIds
        val alreadySealedCoverageMaskConsumerPacketIds =
            (coverageMaskPreparedValidation.routeSeal as?
                GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route)
                ?.slabAuthority?.uniformSlabSeal?.consumerPacketIds?.toSet().orEmpty()
        clipStencilPreparedValidation.diagnostic?.let { preparedDiagnostic ->
            validateCorePrimitiveRenderAuthority(framePlan)?.let { return it }
            validateCorePrimitiveClipProducerAuthority(
                framePlan,
                alreadySealedCoverageMaskProducerPacketIds,
                alreadySealedCoverageMaskConsumerPacketIds,
            ).diagnostic?.let { return it }
            return preparedDiagnostic
        }
        retainCorePrimitiveClipStencilPreparedRoutes(clipStencilPreparedValidation)
        if (!skipNativeCorePrimitiveClassification && nativeBoundary != null && !hasClipStencilPreparedRoute &&
            !hasCoverageMaskPreparedRoute
        ) {
            val validation = validateCorePrimitiveGeometryResources(
                framePlan,
                strictNativeRoute = true,
            )
            validation.diagnostic?.let { return it }
            retainCorePrimitiveRoutes(validation)
        }
        validateCorePrimitiveRenderAuthority(framePlan)?.let { return it }
        val clipProducerValidation = validateCorePrimitiveClipProducerAuthority(
            framePlan,
            alreadySealedCoverageMaskProducerPacketIds,
            alreadySealedCoverageMaskConsumerPacketIds,
        )
        clipProducerValidation.diagnostic?.let { return it }
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
        for ((sourceStepIndex, step) in framePlan.steps.withIndex()) {
            when (step) {
                is GPUFrameStep.RenderPassStep -> {
                    val expected = context.resourceGenerations[step.target]
                        ?: return diagnostic("stale.preflight.resource_generation_missing", "Render target generation is unavailable.")
                    val staleResourceGenerationPacket = step.drawPackets.firstOrNull(
                        predicate = { packet ->
                            val preparedLateBound =
                                packet.semanticPayload is GPUDrawSemanticPayload.SolidRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.SampledImage ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.Vertices ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.RegisteredUniformRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.SeparableBlurRect ||
                                    packet.semanticPayload is GPUDrawSemanticPayload.MaskBlur ||
                                    packet.renderPipelineKey == PREPARED_VERTICES_RENDER_PIPELINE_KEY ||
                                    (
                                        packet.bindingLayoutHash == "preflight.pending" &&
                                            packet.vertexSourceLabel == "preflight.pending" &&
                                            packet.renderPipelineKey?.value?.startsWith("pending.pipeline.") == true
                                    )
                            val acceptedGeneration = when {
                                (preparedLateBound ||
                                    packet.role == GPUDrawPacketRole.W4ePrepared ||
                                    packet.packetId in clipProducerValidation.sealedProducerPacketIds) &&
                                    packet.resourceGeneration == PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ->
                                    PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
                                else -> expected
                            }
                            packet.resourceGeneration != acceptedGeneration
                        },
                    )
                    if (staleResourceGenerationPacket != null) {
                        return diagnostic(
                            "stale.preflight.resource_generation",
                            "A render packet resource generation is stale.",
                            mapOf(
                                "packet" to staleResourceGenerationPacket.packetId.value,
                                "semantic" to staleResourceGenerationPacket.semanticPayload
                                    ?.let { it::class.simpleName }
                                    .orEmpty(),
                                "target" to step.target.value,
                                "packetGeneration" to staleResourceGenerationPacket.resourceGeneration.toString(),
                                "targetGeneration" to expected.toString(),
                                "renderStep" to staleResourceGenerationPacket.renderStepId.value,
                                "pipeline" to staleResourceGenerationPacket.renderPipelineKey?.value.orEmpty(),
                                "bindingLayout" to staleResourceGenerationPacket.bindingLayoutHash,
                                "vertexSource" to staleResourceGenerationPacket.vertexSourceLabel,
                            ),
                        )
                    }
                    if (step.drawPackets.any {
                            it.renderPipelineKey == null && it.role != GPUDrawPacketRole.W4ePrepared
                        }
                    ) {
                        return diagnostic("invalid.preflight.render_pipeline_key_missing", "Render packets require a pipeline key before materialization.")
                    }
                    step.drawPackets.forEach { packet ->
                        validateSemanticPayload(
                            framePlan,
                            semanticEnvelopeAuthority,
                            sourceStepIndex,
                            step,
                            packet,
                            clipStencilPreparedValidation.routeSeal,
                            coverageMaskPreparedValidation.routeSeal,
                        )?.let { return it }
                    }
                }
                is GPUFrameStep.CopyDestinationStep -> {
                    if (step.sourceKey.deviceGeneration != context.deviceGeneration ||
                        (step.sourceKey.targetGeneration != context.targetGeneration &&
                            step.sourceKey.targetGeneration !=
                            PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION)
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
                        (step.sourceKey.targetGeneration != context.targetGeneration &&
                            step.sourceKey.targetGeneration !=
                            PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION) ||
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
        if (!skipNativeCorePrimitiveClassification && nativeBoundary == null &&
            clipStencilPreparedValidation.routeSeal ===
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty &&
            coverageMaskPreparedValidation.routeSeal ===
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty
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
        semantic = semantic,
        exactClipScissor = (clipAuthority as? GPUCorePrimitiveDirectClipAuthority.Accepted)?.scissor,
        blendPlan = blendPlan ?: return GPUCorePrimitiveDirectNativeRoute.Refused(
            "unsupported.native-core-primitive.blend",
            "Direct CorePrimitive native geometry requires one exact classified blend plan.",
        ),
        samplePlan = samplePlan,
        targetFormat = targetFormat,
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
                val preparedPass = (
                    direct.routeSeal.retainedFor(
                        sourceStepIndex,
                        corePackets.map(GPUDrawPacket::packetId),
                    ) as? GPUCorePrimitiveDirectNativeRouteSeal.Routes
                    )?.preparedPassSeal ?: return@forEachIndexed
                val uniformCoverage = if (hasExactPreparedSurfaceMixedNativeBoundary(framePlan)) {
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(
                            startIndex = preparedPass.commandIds.indexOf(
                                units.first().commandIdValue,
                            ),
                            commandCount = units.size,
                        )
                    } else {
                        GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope
                    }
                val routes = preparedPass.uniformSlabSeal?.let { slab ->
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        units,
                        slab,
                        uniformCoverage,
                    )
                } ?: GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                    units,
                    preparedPass,
                    uniformCoverage,
                )
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

    private fun validateCorePrimitivePathMsaaAuthority(framePlan: GPUFramePlan): GPUDiagnostic? {
        fun refused(message: String) = diagnostic(
            "invalid.preflight.core_primitive_path_stencil_msaa_authority",
            message,
        )

        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val coreRenders = renderSteps
            .filter { render ->
                render.drawPackets.any { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                }
            }
        val pathMsaaRenders = coreRenders.filter { render ->
            render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                render.drawPackets.any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilProducer ||
                        packet.role == GPUDrawPacketRole.PathStencilCover
                }
        }
        if (pathMsaaRenders.isEmpty()) return null
        if (renderSteps.size != 1 || coreRenders.size != 1 || pathMsaaRenders.size != 1) {
            return refused("Path stencil MSAA authority requires one unique path-bearing render scope.")
        }
        val render = pathMsaaRenders.single()
        if (render.drawPackets.any { packet ->
                packet.semanticPayload !is GPUDrawSemanticPayload.CorePrimitive
            }
        ) {
            return refused("Path stencil MSAA authority requires one all-CorePrimitive render scope.")
        }
        var packetIndex = 0
        while (packetIndex < render.drawPackets.size) {
            val packet = render.drawPackets[packetIndex]
            when (packet.role) {
                GPUDrawPacketRole.Shading -> packetIndex += 1
                GPUDrawPacketRole.PathStencilProducer -> {
                    val cover = render.drawPackets.getOrNull(packetIndex + 1)
                    if (cover?.role != GPUDrawPacketRole.PathStencilCover ||
                        cover.commandIdValue != packet.commandIdValue
                    ) {
                        return refused("Path stencil MSAA authority requires exact producer/cover pairs in order.")
                    }
                    packetIndex += 2
                }
                GPUDrawPacketRole.PathStencilCover ->
                    return refused("Path stencil MSAA authority refuses an unpaired cover packet.")
                else -> return refused("Path stencil MSAA authority contains an unsupported packet role.")
            }
        }
        val semantics = render.drawPackets.map { packet ->
            packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
        }
        val targetBounds = semantics.map(GPUDrawSemanticPayload.CorePrimitive::targetBounds)
            .distinct().singleOrNull()
            ?: return refused("Path stencil MSAA semantics require one exact target extent.")
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val targetPreparation = preparations.singleOrNull { it.resource == render.target }
            ?: return refused("Path stencil MSAA requires one exact canonical target preparation.")
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path stencil MSAA target preparation must be a texture.")
        val targetInterpretation = targetDescriptor.format.corePrimitiveInterpretationOrNull()
            ?: return refused("Path stencil MSAA target format is unsupported.")
        if (render.drawPackets.zip(semantics).any { (packet, semantic) ->
                packet.role != GPUDrawPacketRole.Shading &&
                    (
                semantic.coverageMode != GPUCorePrimitiveCoverageMode.StencilAA ||
                    (semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath)
                        ?.geometryMode !in setOf(
                        GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                        GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
                    )
                    )
            }
        ) {
            return refused("Path stencil MSAA semantics require StencilAA edge-fan authority.")
        }
        val pathUses = render.resourceUses.filter { it.role == GPUFrameResourceRole.PathDepthStencil }
        if (pathUses.singleOrNull()?.let { use ->
                use.usage == GPUFrameResourceUsage.RenderAttachment &&
                    use.lifetime == GPUFrameResourceLifetime.FrameLocal && use.write
            } != true || render.resourceUses.any { it.role == GPUFrameResourceRole.ClipDepthStencil }
        ) {
            return refused("Path stencil MSAA requires one unique writable Path D24S8 use and no clip D24S8.")
        }
        val pathUse = pathUses.single()
        val continuation = render.sampleContinuation
            ?: return refused("Path stencil MSAA requires one payload-owned color and D24S8 continuation.")
        val key = continuation.key
        val exactTargetGeneration = key.targetGeneration == context.targetGeneration ||
            key.targetGeneration == PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
        if (key.target.value != render.target.value ||
            !exactTargetGeneration ||
            key.deviceGeneration != context.deviceGeneration ||
            key.colorFormat != targetDescriptor.format ||
            key.colorInterpretation != targetInterpretation ||
            key.samplePlan != GPUSamplePlan.MultisampleFrame(4) ||
            key.attachmentAuthority != org.graphiks.kanvas.gpu.renderer.passes
                .GPUSampleAttachmentAuthority.PreparedFramePayload ||
            key.colorAttachment.value != "msaa-color:${render.target.value}:${key.targetGeneration}" ||
            key.depthStencilAttachment?.value != pathUse.resource.value ||
            continuation.loadTransition != GPUSampleLoadTransition.FreshClear ||
            continuation.storeAction != GPUSampleStoreAction.Store ||
            continuation.resolveAction != GPUSampleResolveAction.ResolveCanonical
        ) {
            return refused(
                "Path stencil MSAA continuation identity, generation, or transition authority is not exact: " +
                    "key=$key, load=${continuation.loadTransition}, store=${continuation.storeAction}, " +
                    "resolve=${continuation.resolveAction}, targetGeneration=${context.targetGeneration}, " +
                    "deviceGeneration=${context.deviceGeneration}, path=${pathUse.resource.value}.",
            )
        }
        if (render.loadStore != GPULoadStorePlan("clear", GPUStorePlan.Store) ||
            render.depthStencilLoadStore != org.graphiks.kanvas.gpu.renderer.recording
                .GPUDepthStencilLoadStorePlan.WritableStencil(
                    org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Discard,
                    0u,
                )
        ) {
            return refused("Path stencil MSAA requires color Clear+Store+Resolve and stencil Clear0+Discard.")
        }
        val depthPreparation = preparations.singleOrNull { request ->
            request.role == GPUFrameResourceRole.PathDepthStencil
        } ?: return refused("Path stencil MSAA requires one exact Path D24S8 preparation.")
        val depthDescriptor = depthPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path stencil MSAA Path D24S8 preparation must be a texture.")
        val attachmentBytes = try {
            corePrimitiveDepthStencilByteSize(targetBounds, 4)
        } catch (_: ArithmeticException) {
            return refused("Path stencil MSAA attachment byte authority overflows.")
        }
        if (!isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                render.target,
                targetBounds,
                targetDescriptor.format,
            ) ||
            depthPreparation.resource != pathUse.resource ||
            depthDescriptor.logicalBounds != targetBounds ||
            depthDescriptor.format.value != "depth24plus-stencil8" ||
            depthDescriptor.sampleCount != 4 ||
            depthPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthPreparation.byteSize != attachmentBytes ||
            context.resourceGenerations[depthPreparation.resource] == null ||
            framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaColor] !=
            attachmentBytes ||
            framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil] !=
            attachmentBytes
        ) {
            return refused("Path stencil MSAA target, D24S8 descriptor, generation, or budget authority is not exact.")
        }
        render.drawPackets.forEach { packet ->
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
            val clip = packet.clipExecutionPlan
                ?: return refused("Path stencil MSAA clip authority is missing.")
            val blend = packet.blendPlan
                ?: return refused("Path stencil MSAA blend authority is missing.")
            val structural = try {
                when (packet.role) {
                    GPUDrawPacketRole.Shading -> corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        clip,
                        blend,
                        4,
                        targetDescriptor.format.corePrimitiveStructuralColorFormat(),
                    ).copy(depthStencil = corePrimitiveDirectPathDepthStencilState())
                    GPUDrawPacketRole.PathStencilProducer ->
                        corePrimitivePathStencilRenderPipelineStructuralKey(
                            semantic,
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                            clip,
                            blend,
                            4,
                            targetDescriptor.format.corePrimitiveStructuralColorFormat(),
                        )
                    GPUDrawPacketRole.PathStencilCover ->
                        corePrimitivePathStencilRenderPipelineStructuralKey(
                            semantic,
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                            clip,
                            blend,
                            4,
                            targetDescriptor.format.corePrimitiveStructuralColorFormat(),
                        )
                    else -> error("Validated above")
                }
            } catch (_: IllegalArgumentException) {
                return refused("Path stencil MSAA structural pipeline authority is invalid.")
            }
            val authority = packet.corePrimitivePreparedAuthority
            if (authority?.structuralPipelineKey != structural ||
                authority.renderPipelineKey != packet.renderPipelineKey ||
                packet.renderPipelineKey != structural.stableRenderPipelineKey(
                    CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                ) || packet.targetStateHash != corePrimitiveTargetStateHash(4, targetDescriptor.format)
            ) {
                return refused("Path stencil MSAA structural key, pipeline key, or target state is not exact.")
            }
        }
        return null
    }

    /** W4e's scene continuation is a closed Task 7 ABI, not a generic or W4d.2 exception. */
    private fun validateW4eSceneMsaaContinuation(framePlan: GPUFramePlan): GPUDiagnostic? {
        fun refused(message: String) = diagnostic("invalid.preflight.w4e_scene_msaa_authority", message)
        val pointWitness = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .mapNotNull { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 }.firstOrNull()?.takeIf { it.clipPrefixV4 != null }
        if (pointWitness != null) return if (pointWitness.validates(framePlan) &&
            framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().all { it.w4eSceneContinuation == null && it.sampleContinuation == null }) null
            else refused("W5b clip-only frame cannot carry a Path scene continuation.")
        val w4eFinal = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .mapNotNull { it.w5bFinalFrameWitnessV3 }.firstOrNull()?.takeIf { it.w4eLane != null }
        if (w4eFinal != null) return if (w4eFinal.validates(framePlan) &&
            framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().all {
                it.w4eSceneContinuation == null && it.sampleContinuation == null && it.samplePlan == GPUSamplePlan.SingleSampleFrame
            }) null else refused("W5b W4e native frame must retain its complete single-sample authority.")
        val renders = framePlan.w4eRenderSteps()
        if (renders.isEmpty() || framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size != renders.size) {
            return refused("W4e requires one closed prepared render sequence.")
        }
        if (renders.any { render ->
                render.drawPackets.singleOrNull()?.w4ePreparedFrameAuthority == null
            }
        ) return refused("W4e render scopes require one sealed prepared frame authority.")

        // W4e has two independent AA4 attachment families: clip-mask producers and scene paths.
        // Only a sealed scene path owns the typed scene continuation; requiring it on an AA4
        // mask producer rejects an otherwise exact producer/fold/scene sequence before it can
        // allocate its declared attachments.
        val msaa = renders.filter { render ->
            render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                render.drawPackets.singleOrNull()?.w4ePreparedPath != null
        }
        if (renders.filter { render -> render.samplePlan == GPUSamplePlan.SingleSampleFrame }
                .any { render -> render.w4eSceneContinuation != null || render.sampleContinuation != null }
        ) return refused("W4e single-sample scopes cannot carry scene MSAA authority.")
        if (msaa.isEmpty()) return null

        val continuations = msaa.map { render ->
            render.w4eSceneContinuation ?: return refused("Every W4e 4x scene scope requires typed scene continuation authority.")
        }
        val sceneTarget = continuations.first().sceneTargetResourceId
        if (msaa.zip(continuations).any { (render, continuation) ->
                render.sampleContinuation != null ||
                    !render.target.referencesW4eLogicalResource(continuation.sceneTargetResourceId) ||
                    continuation.sceneTargetResourceId != sceneTarget ||
                    render.resourceUses.singleOrNull { use ->
                        use.referencesW4eLogicalResource(continuation.sceneTargetResourceId) &&
                            use.role == GPUFrameResourceRole.LayerTarget &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
                    } == null ||
                    render.loadStore.storePlan != GPUStorePlan.Store
            }
        ) return refused("W4e scene continuation target, generic authority, or store state was substituted.")
        if (msaa.withIndex().any { (index, render) ->
                render.loadStore.loadOp != if (index == 0) "clear" else "load"
            }
        ) return refused("W4e scene continuation must clear once then load the retained 4x target.")
        if (continuations.dropLast(1).any { continuation ->
                continuation.resolveAction != org.graphiks.kanvas.gpu.renderer.passes.GPUW4eSceneResolveAction.Skip ||
                    continuation.resolveSceneResourceId != null
            } || continuations.last().resolveAction !=
            org.graphiks.kanvas.gpu.renderer.passes.GPUW4eSceneResolveAction.ResolveCanonical ||
            continuations.last().resolveSceneResourceId == null
        ) return refused("Only the final sealed W4e scene scope may resolve the canonical target.")
        val final = msaa.last()
        val finalResolve = requireNotNull(continuations.last().resolveSceneResourceId)
        if (final.resourceUses.singleOrNull { use ->
                use.referencesW4eLogicalResource(finalResolve) &&
                    use.role == GPUFrameResourceRole.SceneTarget &&
                    use.usage == GPUFrameResourceUsage.RenderAttachment && use.write
            } == null ||
            msaa.dropLast(1).any { render -> render.resourceUses.any { use ->
                use.role == GPUFrameResourceRole.SceneTarget && use.write &&
                    use.usage == GPUFrameResourceUsage.RenderAttachment
            } }
        ) return refused("W4e scene continuation must retain one LayerTarget and resolve only its final scope to SceneTarget.")
        return null
    }

    /**
     * Validates the only W4d.2 continuation sequence which is allowed to omit intermediate resolves.
     * Its `Skip` values originate exclusively in the sealed Task 7 W4d.2 authority; generic
     * MSAA frames keep using [GPUSampleContinuationPlanner] and therefore still reject `Skip`.
     */
    private fun validateW4dGeneralMsaaContinuation(framePlan: GPUFramePlan): GPUDiagnostic? {
        fun refused(message: String) = diagnostic(
            "invalid.preflight.w4d_general_msaa_authority",
            message,
        )

        val renders = framePlan.w4dGeneralRenderSteps()
        val msaaRenders = renders.filter { render ->
            render.samplePlan == GPUSamplePlan.MultisampleFrame(4)
        }
        val w5b = renders.firstOrNull()?.drawPackets?.singleOrNull()?.corePrimitivePreparedAuthority?.w5bFrameWitnessV3
        if (renders.isEmpty() || (w5b == null && framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size != renders.size) ||
            (w5b != null && (!w5b.validates(framePlan) || msaaRenders.isNotEmpty()))) {
            return refused("W4d.2 requires its original closed frame or a complete sealed W5b single-sample envelope.")
        }
        val packets = renders.map { render -> render.drawPackets.singleOrNull() ?: return refused(
            "W4d.2 MSAA render scopes must retain exactly one sealed path packet.",
        ) }
        val authority = packets.first().corePrimitivePreparedAuthority
            ?.w4dGeneralPreparedAuthority
            ?: return refused("W4d.2 MSAA packets require one attached prepared authority.")
        if (packets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4dGeneralPreparedAuthority !== authority ||
                    packet.semanticPayload !is GPUDrawSemanticPayload.CorePrimitive
            }
        ) {
            return refused("W4d.2 MSAA packets must retain one common sealed authority and semantic payload.")
        }
        validateW4dGeneralNativeMaterializationAuthority(framePlan, renders, packets)?.let { return it }
        val transitions = authority.sampleContinuation?.transitions.orEmpty()
        if (transitions.size != msaaRenders.size || msaaRenders.zip(transitions).any { (render, transition) ->
                val packet = render.drawPackets.single()
                val continuation = render.sampleContinuation
                render.samplePlan != GPUSamplePlan.MultisampleFrame(4) ||
                    packet.passId != transition.pathPassId ||
                    packet.commandIdValue != transition.commandIdValue ||
                    continuation == null ||
                    continuation.loadTransition != transition.loadTransition ||
                    continuation.storeAction != transition.storeAction ||
                    continuation.resolveAction != transition.resolveAction
            }
        ) {
            return refused("W4d.2 MSAA packets, transitions, and pass order must match exactly.")
        }
        if (renders.filter { render -> render.samplePlan == GPUSamplePlan.SingleSampleFrame }
                .any { render -> render.sampleContinuation != null }
        ) {
            return refused("W4d.2 hard path passes cannot carry an MSAA continuation.")
        }
        if (msaaRenders.isEmpty()) return null
        val target = msaaRenders.first().target
        if (msaaRenders.any { render ->
                render.target != target || render.loadStore.storePlan != GPUStorePlan.Store
            }
        ) {
            return refused("W4d.2 MSAA transitions must retain one stored color target.")
        }
        val key = requireNotNull(msaaRenders.first().sampleContinuation).key
        if (key.target.value != target.value ||
            key.targetGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
            key.deviceGeneration != context.deviceGeneration ||
            key.colorFormat != GPUColorFormat.RGBA8UnormSrgb ||
            key.colorInterpretation != GPUColorInterpretation.LinearPremul ||
            key.samplePlan != GPUSamplePlan.MultisampleFrame(4) ||
            key.attachmentAuthority != org.graphiks.kanvas.gpu.renderer.passes
                .GPUSampleAttachmentAuthority.PreparedFramePayload ||
            key.colorAttachment.value != "msaa-color:${target.value}:$PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION" ||
            key.depthStencilAttachment != null ||
            msaaRenders.any { render -> render.sampleContinuation?.key != key }
        ) {
            return refused("W4d.2 MSAA attachment identity or format was substituted.")
        }
        if (msaaRenders.withIndex().any { (index, render) ->
                render.loadStore != GPULoadStorePlan(
                    if (index == 0) "clear" else "load",
                    GPUStorePlan.Store,
                )
            }
        ) {
            return refused("W4d.2 MSAA load/store transitions must remain sealed.")
        }
        if (transitions.dropLast(1).any { it.resolveAction != GPUSampleResolveAction.Skip } ||
            transitions.last().resolveAction != GPUSampleResolveAction.ResolveCanonical
        ) {
            return refused("Only the final sealed W4d.2 color pass may resolve the canonical target.")
        }
        return null
    }

    /**
     * Rechecks the Task 7 frame binding before resource preflight can acquire a native object.
     * This reads only the opaque scalar snapshot attached to the authenticated packets; it does
     * not rebuild target names, coverage, samples, or resolve placement from render roles.
     */
    private fun validateW4dGeneralNativeMaterializationAuthority(
        framePlan: GPUFramePlan,
        renders: List<GPUFrameStep.RenderPassStep>,
        packets: List<GPUDrawPacket>,
    ): GPUDiagnostic? {
        fun refused(message: String) = diagnostic(
            "invalid.preflight.w4d_general_native_authority",
            message,
        )
        val native = packets.firstOrNull()?.corePrimitivePreparedAuthority
            ?.w4dGeneralFrameMaterializationAuthority
            ?: return refused("W4d.2 packets require one sealed native materialization table.")
        val w5b = packets.first().corePrimitivePreparedAuthority?.w5bFrameWitnessV3
        val w5bGeneral = w5b?.scratchFor(packets.first()) as? org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.General
        if (w5b != null && (w5bGeneral?.native !== native || !w5b.validates(framePlan) ||
            packets.any { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 !== w5b ||
                w5b.scratchFor(it) !== w5bGeneral }))
            return refused("General native rows lost their complete W5b frame and lane identity.")
        if (packets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4dGeneralFrameMaterializationAuthority !== native
            } || native.deviceGeneration != context.deviceGeneration ||
            native.capabilitySealHash != framePlan.capabilitySeal.sealHash ||
            native.targetBounds.isEmpty ||
            framePlan.recordingSeals.size != 1 ||
            (w5b == null && (framePlan.recordingSeals.single().compatibilityKeyHash != "w4d-general:${native.planId}" ||
            framePlan.recordingSeals.single().replayKeyHash != "w4d-general:${native.planId}"))
        ) {
            return refused("W4d.2 native materialization table is stale or not common to the frame.")
        }
        if (native.pathPassFacts.size != renders.size || renders.zip(native.pathPassFacts).any {
                (render, fact) ->
                val packet = render.drawPackets.singleOrNull()
                val expectedResourceUses = buildList {
                    fun resource(resourceId: String) = native.resource(resourceId)
                    add(GPUFrameResourceUse(
                        requireNotNull(resource(fact.vertexResourceId)),
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceUsage.Vertex,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ))
                    add(GPUFrameResourceUse(
                        requireNotNull(resource(fact.indexResourceId)),
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceUsage.Index,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ))
                    add(GPUFrameResourceUse(
                        requireNotNull(resource(fact.uniformResourceId)),
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceUsage.Uniform,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ))
                    fact.depthStencilResourceId?.let { depth ->
                        add(GPUFrameResourceUse(
                            requireNotNull(resource(depth)),
                            GPUFrameResourceRole.PathDepthStencil,
                            GPUFrameResourceUsage.RenderAttachment,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = true,
                        ))
                    }
                    fact.maskResourceId?.let { mask ->
                        add(GPUFrameResourceUse(
                            requireNotNull(resource(mask)),
                            GPUFrameResourceRole.ClipMask,
                            GPUFrameResourceUsage.TextureBinding,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ))
                    }
                }
                val expectedDepthStencilLoadStore = when (fact.depthStencilLoadStore) {
                    null -> null
                    org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore.ClearZeroStore ->
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
                            .WritableStencil(
                                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                                GPUStorePlan.Store,
                                0u,
                            )
                    org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore.LoadStoreTestReset ->
                        org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
                            .WritableStencil(
                                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Load,
                                GPUStorePlan.Store,
                                null,
                            )
                }
                val hasExactConsumerUniform64 = when (val maskResourceId = fact.maskResourceId) {
                    null -> fact.coverageMaskConsumerUniform64 == null
                    else -> {
                        val semantic = packet?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                        val mask = native.resourceFact(maskResourceId)
                        val sealed = fact.coverageMaskConsumerUniform64
                        semantic != null && mask?.width != null && mask.height != null &&
                            sealed != null && sealed.size == 64 &&
                            sealed == w4dGeneralCoverageMaskConsumerUniform64(
                                native.targetBounds,
                                mask.width,
                                mask.height,
                                semantic.premultipliedRgba,
                        )
                    }
                }
                val foldedMaskClear = native.maskClearFacts.any { clear ->
                    clear.targetResourceId == fact.targetResourceId &&
                        clear.atomicGroupId == fact.atomicGroupId &&
                        clear.followingPathPassId == fact.pathPassId
                }
                packet == null ||
                    packet.passId != fact.pathPassId ||
                    packet.commandIdValue != fact.commandIdValue ||
                    packet.corePrimitivePreparedAuthority?.structuralPipelineKey !=
                        native.structuralPipelineKey(fact.pathPassId) ||
                    render.target != native.resource(fact.targetResourceId) ||
                    render.samplePlan.sampleCount != fact.sampleCountI32 ||
                    (w5b == null && render.resourceUses != expectedResourceUses) ||
                    render.depthStencilLoadStore != expectedDepthStencilLoadStore ||
                    !hasExactConsumerUniform64 ||
                    (w5b == null && render.loadStore.loadOp != (if (foldedMaskClear) {
                        "clear"
                    } else when (fact.load) {
                        org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent -> "clear"
                        org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.Load -> "load"
                    })) ||
                    render.loadStore.storePlan != GPUStorePlan.Store
            }
        ) {
            return refused("W4d.2 render target, pass order, sample, or load/store facts differ from the sealed table.")
        }
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()
            ?: return refused("W4d.2 native materialization requires one terminal readback.")
        val preparedSceneTarget = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?: return refused("W4d.2 native materialization requires one sealed logical target preparation.")
        if (readback.source != native.resource(native.readbackSourceResourceId) ||
            readback.staging != native.resource(native.readbackStagingResourceId) ||
            preparedSceneTarget.resource != native.resource(native.readbackSourceResourceId)
        ) {
            return refused("W4d.2 resolve/readback resources differ from the sealed native table.")
        }
        return null
    }

    private fun GPUFramePlan.w4eRenderSteps(): List<GPUFrameStep.RenderPassStep> =
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().filter { render ->
            render.drawPackets.any { packet -> packet.role == GPUDrawPacketRole.W4ePrepared }
        }

    private fun GPUFramePlan.w4dGeneralRenderSteps(): List<GPUFrameStep.RenderPassStep> =
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().filter { render ->
            render.drawPackets.any { packet ->
                packet.corePrimitivePreparedAuthority?.w4dGeneralPreparedAuthority != null
            }
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
        val mixedPreparedSurface = hasExactPreparedSurfaceMixedNativeBoundary(framePlan)
        val preparedImageResourceRefs = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .flatMap { step -> step.imageResourcePlan?.preparationRequests.orEmpty() }
            .map(GPUResourcePreparationRequest::resource)
            .toSet()
        fun corePackets(render: GPUFrameStep.RenderPassStep): List<GPUDrawPacket> =
            if (mixedPreparedSurface) {
                render.drawPackets.filter { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                }
            } else {
                render.drawPackets
            }
        val coreResourceRoles = setOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.PathDepthStencil,
            GPUFrameResourceRole.DestinationSnapshot,
        )
        fun coreResourceUses(render: GPUFrameStep.RenderPassStep): List<GPUFrameResourceUse> =
            if (mixedPreparedSurface) {
                render.resourceUses.filter { use ->
                    use.role in coreResourceRoles && use.resource !in preparedImageResourceRefs
                }
            } else {
                render.resourceUses
            }
        // The direct pass splits by uniform layout, so a path-bearing frame may
        // legitimately carry exactly one path pass plus direct-only split passes (each direct
        // pass owns its own uniform slab). The path pass itself retains the producer/cover
        // pair in one scope.
        val pathCoreRenders = indexedCoreRenders.filter { (_, render) ->
            corePackets(render).any { packet ->
                packet.role == GPUDrawPacketRole.PathStencilProducer ||
                    packet.role == GPUDrawPacketRole.PathStencilCover
            }
        }
        val splitPathAdmission = !mixedPreparedSurface &&
            pathCoreRenders.size == 1 &&
            indexedCoreRenders.all { (_, render) ->
                corePackets(render).all { packet ->
                    packet.role == GPUDrawPacketRole.Shading ||
                        packet.role == GPUDrawPacketRole.PathStencilProducer ||
                        packet.role == GPUDrawPacketRole.PathStencilCover
                }
            }
        // A continued destination-read path splits the producer (fan Store) from
        // the cover (fan read-only) into two path renders, with the ordered destination snapshot
        // copy between them. The two path renders are exactly one producer-only render and one
        // cover-only render, and the frame is exactly three render scopes total (one background
        // render + producer + cover) so the continued lane's three-render materialization is the
        // exact admitted shape — an extra direct render refuses here instead of later.
        val continuedDstReadPathAdmission = !mixedPreparedSurface &&
            pathCoreRenders.size == 2 &&
            framePlan.steps.count { it is GPUFrameStep.RenderPassStep } == 3 &&
            framePlan.steps.any { it is GPUFrameStep.CopyDestinationStep } &&
            indexedCoreRenders.all { (_, render) ->
                corePackets(render).all { packet ->
                    packet.role == GPUDrawPacketRole.Shading ||
                        packet.role == GPUDrawPacketRole.PathStencilProducer ||
                        packet.role == GPUDrawPacketRole.PathStencilCover
                }
            } &&
            pathCoreRenders.map { (_, render) ->
                render.drawPackets.map { packet -> packet.role }.toSet()
            }.toSet() == setOf(
                setOf(GPUDrawPacketRole.PathStencilProducer),
                setOf(GPUDrawPacketRole.PathStencilCover),
            )
        if (indexedCoreRenders.isEmpty() ||
            (!mixedPreparedSurface && !splitPathAdmission && !continuedDstReadPathAdmission)
        ) {
            return refused("Path stencil CorePrimitive requires exactly one prepared render pass.")
        }
        if (indexedCoreRenders.any { (_, render) ->
                corePackets(render).any {
                    it.semanticPayload !is GPUDrawSemanticPayload.CorePrimitive
                }
            }
        ) {
            return refused("Path stencil CorePrimitive requires one all-CorePrimitive render pass.")
        }
        val allRenderSteps = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (indexedCoreRenders.any { (_, render) ->
                val hasProducer = corePackets(render).any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilProducer
                }
                val hasCover = corePackets(render).any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilCover
                }
                val expectedDepthStencil = when {
                    hasProducer && hasCover ->
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUDepthStencilLoadStorePlan.WritableStencil(
                                org.graphiks.kanvas.gpu.renderer.recording
                                    .GPUStencilLoadOperation.Clear,
                                GPUStorePlan.Discard,
                                0u,
                            )
                    hasProducer ->
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUDepthStencilLoadStorePlan.WritableStencil(
                                org.graphiks.kanvas.gpu.renderer.recording
                                    .GPUStencilLoadOperation.Clear,
                                GPUStorePlan.Store,
                                0u,
                            )
                    hasCover ->
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUDepthStencilLoadStorePlan.ReadOnlyKeep
                    else -> null
                }
                render.loadStore != GPULoadStorePlan(
                    if (allRenderSteps.indexOf(render) == 0) "clear" else "load",
                    GPUStorePlan.Store,
                ) ||
                    render.depthStencilLoadStore != expectedDepthStencil
            }
        ) {
            return refused("Path stencil requires exact color clear/store and stencil clear/store/keep authority.")
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val target = indexedCoreRenders.first().second.target
        if (indexedCoreRenders.any { (_, render) -> render.target != target }) {
            return refused("Path stencil CorePrimitive renders must share one exact scene target.")
        }
        val targetPreparation = preparations.singleOrNull { request -> request.resource == target }
            ?: return refused("Path stencil requires one exact target preparation.")
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path stencil target preparation must be a texture.")
        val semanticTargetBounds = indexedCoreRenders.flatMap { (_, render) ->
            corePackets(render).map { packet ->
                (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive).targetBounds
            }
        }.distinct()
        if (semanticTargetBounds.size != 1 ||
            !isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                target,
                semanticTargetBounds.single(),
                targetDescriptor.format,
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
        val coreGeometryRefs = indexedCoreRenders
            .flatMap { (_, render) -> coreResourceUses(render) }
            .filter { use -> use.role in geometryRoles }
            .map(GPUFrameResourceUse::resource)
            .toSet()
        val geometryPreparations = preparations.filter { request ->
            request.resource in coreGeometryRefs
        }
        val vertex = geometryPreparations.singleOrNull { it.role == GPUFrameResourceRole.VertexData }
            ?: return refused("Path stencil requires one shared vertex slab.")
        val index = geometryPreparations.singleOrNull { it.role == GPUFrameResourceRole.IndexData }
            ?: return refused("Path stencil requires one shared index slab.")
        // The layout split owns one uniform slab per layout group, so a
        // path-bearing frame may declare one uniform preparation per present layout.
        val uniformSlabs = geometryPreparations.filter { it.role == GPUFrameResourceRole.UniformData }
        if (uniformSlabs.isEmpty()) {
            return refused("Path stencil requires at least one shared uniform slab.")
        }
        val depthStencil = geometryPreparations.singleOrNull {
            it.role == GPUFrameResourceRole.PathDepthStencil
        } ?: return refused("Path stencil requires one full-target depth/stencil attachment.")
        if (geometryPreparations.size != 2 + uniformSlabs.size + 1 ||
            setOf(vertex.resource, index.resource, depthStencil.resource).size != 3 ||
            uniformSlabs.map { it.resource }.distinct().size != uniformSlabs.size
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
        val unifiedUnitsByStep = linkedMapOf<
            Int,
            MutableList<GPUCorePrimitiveNativeScopeRouteUnit>,
            >()
        val preparedPathPairsByStep = linkedMapOf<
            Int,
            MutableList<GPUCorePrimitivePathStencilPreparedPairSeal>,
            >()
        val preparedPathAnalyticSealsByStep = linkedMapOf<
            Int,
            MutableList<org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal>,
            >()
        val directStructuralKeysByStep = linkedMapOf<
            Int,
            MutableList<GPUCorePrimitiveRenderPipelineStructuralKey>,
            >()
        val allUnifiedUnits = mutableListOf<GPUCorePrimitiveNativeScopeRouteUnit>()
        val corePacketById = indexedCoreRenders
            .flatMap { (_, render) -> corePackets(render) }
            .associateBy(GPUDrawPacket::packetId)
        var sharedUniformSeal: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal? = null
        var sharedAnalyticPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan? = null
        var uniformSlotIndex = 0
        // One render scope may retain exactly one uniform authority kind (the
        // uniform32 slab or the analytic uniform64 plan). Direct-only split passes own the
        // uniform32 slab while the path pass owns its own authority, so the mix refusal is
        // per render scope instead of per frame.
        val stepUniformAuthorityKinds = mutableMapOf<Int, String>()

        fun requireStepUniformAuthority(stepIndex: Int, kind: String): Boolean {
            val previous = stepUniformAuthorityKinds[stepIndex]
            if (previous != null && previous != kind) return false
            stepUniformAuthorityKinds[stepIndex] = kind
            return true
        }

        fun exactAuthority(
            render: GPUFrameStep.RenderPassStep,
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
                    corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        clip,
                        blend,
                        render.samplePlan.sampleCount,
                        targetDescriptor.format.corePrimitiveStructuralColorFormat(),
                    ).let { structural ->
                        if (render.resourceUses.any {
                                it.role == GPUFrameResourceRole.PathDepthStencil
                            }
                        ) {
                            structural.copy(
                                depthStencil = corePrimitiveDirectPathDepthStencilState(),
                            )
                        } else {
                            structural
                        }
                    }
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                -> corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    role,
                    clip,
                    blend,
                    render.samplePlan.sampleCount,
                    targetDescriptor.format.corePrimitiveStructuralColorFormat(),
                )
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer,
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
            return authority to expected
        }

        val renderByStepIndex = indexedCoreRenders.associate { (index, render) -> index to render }
        val flatEntries = indexedCoreRenders.flatMap { (stepIndex, render) ->
            corePackets(render).map { packet -> stepIndex to packet }
        }
        var flatIndex = 0
        while (flatIndex < flatEntries.size) {
            val (sourceStepIndex, packet) = flatEntries[flatIndex]
            val render = renderByStepIndex.getValue(sourceStepIndex)
            val unifiedUnits = unifiedUnitsByStep.getOrPut(sourceStepIndex) { mutableListOf() }
            val preparedPathPairs =
                preparedPathPairsByStep.getOrPut(sourceStepIndex) { mutableListOf() }
            val preparedPathAnalyticSeals =
                preparedPathAnalyticSealsByStep.getOrPut(sourceStepIndex) { mutableListOf() }
            val directStructuralKeys =
                directStructuralKeysByStep.getOrPut(sourceStepIndex) { mutableListOf() }
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
            when (packet.role) {
                GPUDrawPacketRole.Shading -> {
                    val authority = exactAuthority(
                        render,
                        packet,
                        semantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading,
                    ) ?: return refused("A mixed direct packet has corrupt prepared pipeline or uniform authority.")
                    val directUniformSeal = authority.first.uniformSlabSeal
                        ?: return refused("A mixed direct packet is missing its exact uniform32 slab authority.")
                    if (!requireStepUniformAuthority(sourceStepIndex, "uniform32")) {
                        return refused("A path render cannot mix uniform32 and analytic uniform64 authority.")
                    }
                    if (sharedUniformSeal == null) sharedUniformSeal = directUniformSeal
                    if (sharedUniformSeal !== directUniformSeal) {
                        return refused("Mixed direct packets substituted their shared uniform32 slab authority.")
                    }
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
                    val directUnit = GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                        packet.commandIdValue,
                        packet.packetId,
                        route,
                        authority.second,
                    )
                    unifiedUnits += directUnit
                    allUnifiedUnits += directUnit
                    flatIndex += 1
                    uniformSlotIndex += 1
                }
                GPUDrawPacketRole.PathStencilProducer -> {
                    val (coverStepIndex, cover) = flatEntries.getOrNull(flatIndex + 1)
                        ?: return refused("Every path producer must be followed by one cover packet.")
                    if (cover.role != GPUDrawPacketRole.PathStencilCover ||
                        cover.commandIdValue != packet.commandIdValue
                    ) {
                        return refused("Path producer and cover order, role, or command identity is corrupt.")
                    }
                    val coverRender = renderByStepIndex.getValue(coverStepIndex)
                    val coverUnifiedUnits = unifiedUnitsByStep.getOrPut(coverStepIndex) { mutableListOf() }
                    // The cover step must own an (empty) entry in every per-step map so the
                    // per-step seal construction below addresses it exactly.
                    preparedPathPairsByStep.getOrPut(coverStepIndex) { mutableListOf() }
                    preparedPathAnalyticSealsByStep.getOrPut(coverStepIndex) { mutableListOf() }
                    directStructuralKeysByStep.getOrPut(coverStepIndex) { mutableListOf() }
                    val coverSemantic = cover.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                        ?: return refused("Path cover is missing its CorePrimitive semantic payload.")
                    val producerGeometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
                        ?: return refused("Path producer requires triangulated path geometry.")
                    val coverGeometry = coverSemantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
                        ?: return refused("Path cover requires triangulated path geometry.")
                    val expectedCoverageMode = if (render.samplePlan == GPUSamplePlan.MultisampleFrame(4)) {
                        GPUCorePrimitiveCoverageMode.StencilAA
                    } else {
                        GPUCorePrimitiveCoverageMode.Stencil1x
                    }
                    if (semantic.coverageMode != expectedCoverageMode ||
                        coverSemantic.coverageMode != expectedCoverageMode ||
                        producerGeometry.geometryMode !in setOf(
                            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
                        ) ||
                        producerGeometry != coverGeometry ||
                        semantic.targetBounds != coverSemantic.targetBounds ||
                        semantic.scissorBounds != coverSemantic.scissorBounds ||
                        packet.scissorBoundsHash != cover.scissorBoundsHash ||
                        packet.uniformSlot != cover.uniformSlot ||
                        semantic.payloadRef.uniformBlock?.bytes != coverSemantic.payloadRef.uniformBlock?.bytes
                    ) {
                        return refused("Path producer and cover semantic, scissor, geometry, or uniform authority differs.")
                    }
                    if (!hasExactCorePrimitivePathClipPair(packet, cover)) {
                        return refused(
                            "Path producer/cover clip authority must be exact legacy identity or " +
                                "NoClip producer plus AnalyticCoverage cover.",
                        )
                    }
                    val producerAuthority = exactAuthority(
                        render,
                        packet,
                        semantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                    ) ?: return refused("Path producer prepared pipeline or uniform authority is corrupt.")
                    val coverAuthority = exactAuthority(
                        coverRender,
                        cover,
                        coverSemantic,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                    ) ?: return refused("Path cover prepared pipeline or uniform authority is corrupt.")
                    val continued = sourceStepIndex != coverStepIndex
                    if (continued && hasAnalyticCorePrimitivePathClipPair(packet, cover)) {
                        // An analytic-clip path cover cannot continue its fan
                        // across the ordered snapshot copy yet; the analytic path pair stays on
                        // the path-stencil preflight authority.
                        return refused(
                            "Analytic path pair cannot continue its fan across the destination snapshot yet.",
                        )
                    }
                    if (hasAnalyticCorePrimitivePathClipPair(packet, cover)) {
                        if (!requireStepUniformAuthority(sourceStepIndex, "analytic64")) {
                            return refused("A path render cannot mix uniform32 and analytic uniform64 authority.")
                        }
                        val seal = coverAuthority.first.analyticClipUniformSeal
                            ?: return refused("Analytic path cover is missing its existing uniform64 seal.")
                        val packetClip = corePrimitiveAnalyticClipPacketAuthority(
                            cover,
                            coverSemantic.targetBounds,
                        ) ?: return refused("Analytic path cover clip authority is no longer canonical.")
                        val expectedClip = packetClip.clip
                        val expectedBytes = corePrimitiveAnalyticClipUniformBytes(
                            coverSemantic,
                            expectedClip,
                        )
                        val producerPrefix = semantic.payloadRef.uniformBlock?.bytes
                            ?: return refused("Analytic path producer is missing its uniform32 prefix.")
                        val plan = seal.plan
                        // The pair addresses its cover's uniform64 plan slot; with a split
                        // frame the frame-wide unit counter includes the direct steps, so the
                        // authority derives the slot from the cover seal itself.
                        val coverSlotIndex = seal.slotIndex
                        val slot = plan.slots.getOrNull(coverSlotIndex)
                            ?: return refused("Analytic path cover slot is outside its uniform64 plan.")
                        val exactRange = try {
                            Math.addExact(slot.alignedOffset, 64L) <= plan.totalBytes
                        } catch (_: ArithmeticException) {
                            false
                        }
                        if (producerAuthority.first.uniformSlabSeal != null ||
                            producerAuthority.first.analyticShapeUniformSeal != null ||
                            producerAuthority.first.analyticClipUniformSeal != null ||
                            producerAuthority.first.analyticIntersectionUniformSeal != null ||
                            coverAuthority.first.uniformSlabSeal != null ||
                            coverAuthority.first.analyticShapeUniformSeal != null ||
                            coverAuthority.first.analyticIntersectionUniformSeal != null ||
                            seal.slotIndex != coverSlotIndex ||
                            seal.commandId != cover.commandIdValue ||
                            seal.packetId != cover.packetId ||
                            seal.clipCanonicalIdentity != packetClip.canonicalIdentity ||
                            seal.clipType != expectedClip.clipType ||
                            seal.clipBounds != expectedClip.bounds ||
                            seal.clipRadii != expectedClip.radii ||
                            seal.antiAlias != expectedClip.antiAlias ||
                            seal.conservativeScissor != expectedClip.conservativeScissor ||
                            seal.structuralPipelineKey != coverAuthority.second ||
                            seal.renderPipelineKey != coverAuthority.first.renderPipelineKey ||
                            packet.bindingLayoutHash != CORE_PRIMITIVE_BINDING_LAYOUT_HASH ||
                            cover.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH ||
                            seal.bindingLayoutHash != cover.bindingLayoutHash ||
                            seal.resourceGeneration != cover.resourceGeneration ||
                            seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                            seal.deviceGeneration != context.deviceGeneration.value ||
                            seal.alignmentBytes != capabilities.limits?.minUniformBufferOffsetAlignment ||
                            seal.payloadBytes != 64L || slot.payloadBytes != 64L ||
                            slot.slotLabel != "analytic-clip-draw-${cover.commandIdValue}" ||
                            seal.alignedOffset != slot.alignedOffset ||
                            slot.alignedOffset > UInt.MAX_VALUE.toLong() || !exactRange ||
                            producerPrefix.size != 32 ||
                            expectedBytes.take(8).map { byte -> byte.toInt() and 0xff } !=
                            producerPrefix.take(8) ||
                            expectedBytes.copyOfRange(16, 32)
                                .map { byte -> byte.toInt() and 0xff } != producerPrefix.drop(16) ||
                            !seal.hasExactPayload(expectedBytes)
                        ) {
                            return refused(
                                "Analytic path pair contradicts command, geometry prefix, clip seal, slot, " +
                                    "offset, layout, or generation authority.",
                            )
                        }
                        if (sharedAnalyticPlan == null) sharedAnalyticPlan = plan
                        if (sharedAnalyticPlan !== plan) {
                            return refused("Analytic path covers substituted their shared uniform64 plan.")
                        }
                        preparedPathAnalyticSeals += seal
                    } else {
                        if (!requireStepUniformAuthority(sourceStepIndex, "uniform32") ||
                            (continued && !requireStepUniformAuthority(coverStepIndex, "uniform32"))
                        ) {
                            return refused("A path render cannot mix analytic uniform64 and uniform32 authority.")
                        }
                        val producerSlab = producerAuthority.first.uniformSlabSeal
                            ?: return refused("Legacy path producer is missing its uniform32 slab.")
                        val coverSlab = coverAuthority.first.uniformSlabSeal
                            ?: return refused("Legacy path cover is missing its uniform32 slab.")
                        if (producerSlab !== coverSlab) {
                            return refused("Legacy path pair substituted its shared uniform32 slab.")
                        }
                        if (sharedUniformSeal == null) sharedUniformSeal = producerSlab
                        if (sharedUniformSeal !== producerSlab) {
                            return refused("Legacy path pairs substituted their pass uniform32 slab.")
                        }
                    }
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
                        if (hasAnalyticCorePrimitivePathClipPair(packet, cover)) {
                            // Analytic pairs address their cover's uniform64 plan slot; the
                            // pair's pass authority derives its command range from the analytic
                            // seals, which are rebased per render scope for split frames.
                            requireNotNull(coverAuthority.first.analyticClipUniformSeal).slotIndex
                        } else {
                            uniformSlotIndex
                        },
                        packet.packetId,
                        cover.packetId,
                        producerAuthority.second,
                        coverAuthority.second,
                    )
                    if (continued) {
                        // The producer stores the fan in its own render, and the
                        // cover loads it read-only in a second render with the snapshot bound.
                        val producerUnit = GPUCorePrimitiveNativeScopeRouteUnit.PathProducer(
                            packet.commandIdValue,
                            packet.packetId,
                            producerAuthority.second,
                            pair.producer,
                        )
                        val coverUnit = GPUCorePrimitiveNativeScopeRouteUnit.PathCover(
                            cover.commandIdValue,
                            cover.packetId,
                            coverAuthority.second,
                            pair.cover,
                        )
                        unifiedUnits += producerUnit
                        allUnifiedUnits += producerUnit
                        coverUnifiedUnits += coverUnit
                        allUnifiedUnits += coverUnit
                    } else {
                        val pathPairUnit = GPUCorePrimitiveNativeScopeRouteUnit.PathPair(
                            packet.commandIdValue,
                            pair,
                            producerAuthority.second,
                            coverAuthority.second,
                        )
                        unifiedUnits += pathPairUnit
                        allUnifiedUnits += pathPairUnit
                    }
                    flatIndex += 2
                    uniformSlotIndex += 1
                }
                GPUDrawPacketRole.PathStencilCover ->
                    return refused("A path cover cannot appear without its immediately preceding producer.")
                else -> return refused("Path stencil render contains an unsupported packet role.")
            }
        }

        val uniformSeal = sharedUniformSeal
        val analyticSeals = preparedPathAnalyticSealsByStep.values.flatten()
        val limits = capabilities.limits
            ?: return refused("Path stencil requires observed backend limits.")
        // The uniform32 slab covers the direct units and the legacy path pairs,
        // while the analytic uniform64 plan covers the analytic covers. Both authorities may
        // coexist when they belong to different render scopes (the split path frame: the
        // direct pass owns the slab, the path pass owns the analytic plan).
        val slabUnitCommands = allUnifiedUnits.mapNotNull { unit ->
            when (unit) {
                is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> unit.commandIdValue
                is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> unit.commandIdValue.takeIf {
                    unit.coverStructuralPipelineKey.uniformLayout ==
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2
                }
                is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> unit.commandIdValue.takeIf {
                    unit.structuralPipelineKey.uniformLayout ==
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2
                }
                is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> null
            }
        }
        if (uniformSeal != null) {
            val unitByCommandId = allUnifiedUnits
                .filterNot { it is GPUCorePrimitiveNativeScopeRouteUnit.PathCover }
                .associateBy { it.commandIdValue }
            val slabExact = uniformSeal.commandIds == slabUnitCommands &&
                uniformSeal.drawCount == slabUnitCommands.size &&
                slabUnitCommands.withIndex().all { (index, commandId) ->
                    val unit = unitByCommandId.getValue(commandId)
                    val packet = when (unit) {
                        is GPUCorePrimitiveNativeScopeRouteUnit.Direct ->
                            corePacketById.getValue(unit.packetId)
                        is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ->
                            corePacketById.getValue(unit.pair.producerPacketId)
                        is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ->
                            corePacketById.getValue(unit.packetId)
                        is GPUCorePrimitiveNativeScopeRouteUnit.PathCover ->
                            error("A path cover must not address the shared uniform slab.")
                    }
                    val bytes = (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive)
                        .payloadRef.uniformBlock?.bytes ?: return refused(
                        "CorePrimitive uniform semantic bytes are missing.",
                    )
                    uniformSeal.hasExactPayload(index, commandId, bytes)
                }
            if (!slabExact) {
                return refused("The shared uniform slab does not exactly match original command order and bytes.")
            }
        }
        val analyticPairUnits = allUnifiedUnits.filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.PathPair>()
            .filter { pair ->
                pair.coverStructuralPipelineKey.uniformLayout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
            }
        if (sharedAnalyticPlan != null) {
            if (analyticSeals.size != analyticPairUnits.size ||
                analyticSeals.any { seal -> seal.plan !== sharedAnalyticPlan }
            ) {
                return refused("Analytic path authority requires one exact uniform64 seal per ordered path pair.")
            }
            val exactPayloads = analyticSeals.map { seal ->
                val packet = corePacketById.getValue(seal.packetId)
                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                val clip = corePrimitiveAnalyticClipPacketAuthority(packet, semantic.targetBounds)
                    ?: return refused("Analytic path cover clip authority is no longer canonical.")
                GPUUniformSlabPayload(
                    "analytic-clip-draw-${packet.commandIdValue}",
                    corePrimitiveAnalyticClipUniformBytes(semantic, clip.clip),
                )
            }
            if (!sharedAnalyticPlan.hasExactPayloads(
                    "core-primitive-analytic-clip-uniform-pass",
                    context.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    exactPayloads,
                )
            ) {
                return refused("Analytic path uniform64 plan, slots, offsets, or hashes are not exact.")
            }
        }

        val pathPasses = preparedPathPairsByStep
            .filterValues(List<GPUCorePrimitivePathStencilPreparedPairSeal>::isNotEmpty)
            .mapValues { (sourceStepIndex, preparedPathPairs) ->
                val analyticSeals = preparedPathAnalyticSealsByStep.getValue(sourceStepIndex)
                if (analyticSeals.isEmpty()) {
                    GPUCorePrimitivePathStencilPreparedPassSeal(
                        preparedPathPairs,
                        requireNotNull(uniformSeal),
                    )
                } else {
                    GPUCorePrimitivePathStencilPreparedPassSeal(preparedPathPairs, analyticSeals)
                }
            }

        val geometrySizing = try {
            var vertexFloatCount = 0L
            var indexCount = 0L
            allUnifiedUnits.forEach { unit ->
                when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> {
                        vertexFloatCount = Math.addExact(
                            vertexFloatCount,
                            Math.multiplyExact(unit.route.vertexCount.toLong(), 2L),
                        )
                        indexCount = Math.addExact(indexCount, unit.route.indexCount.toLong())
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> {
                        vertexFloatCount = Math.addExact(
                            vertexFloatCount,
                            Math.multiplyExact(unit.pair.producer.vertexCount.toLong(), 2L),
                        )
                        vertexFloatCount = Math.addExact(
                            vertexFloatCount,
                            Math.multiplyExact(unit.pair.cover.vertexCount.toLong(), 2L),
                        )
                        indexCount = Math.addExact(indexCount, unit.pair.producer.indexCount.toLong())
                        indexCount = Math.addExact(indexCount, unit.pair.cover.indexCount.toLong())
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> {
                        vertexFloatCount = Math.addExact(
                            vertexFloatCount,
                            Math.multiplyExact(unit.geometry.vertexCount.toLong(), 2L),
                        )
                        indexCount = Math.addExact(indexCount, unit.geometry.indexCount.toLong())
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> {
                        vertexFloatCount = Math.addExact(
                            vertexFloatCount,
                            Math.multiplyExact(unit.geometry.vertexCount.toLong(), 2L),
                        )
                        indexCount = Math.addExact(indexCount, unit.geometry.indexCount.toLong())
                    }
                }
            }
            Pair(
                Math.multiplyExact(vertexFloatCount, Float.SIZE_BYTES.toLong()),
                Math.multiplyExact(indexCount, Int.SIZE_BYTES.toLong()),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "Unified path geometry cannot be sized or packed into exact immutable slabs.",
            )
        }
        val expectedVertexBytes = geometrySizing.first
        val expectedIndexBytes = geometrySizing.second
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
        val uniformPreparationByResource = uniformSlabs.associateBy { it.resource }
        val stepUniformResourceByStepIndex = indexedCoreRenders.associate { (stepIndex, render) ->
            val uniformUse = coreResourceUses(render).singleOrNull {
                it.role == GPUFrameResourceRole.UniformData
            } ?: return refused("Path stencil render must retain its exact uniform slab use.")
            stepIndex to uniformUse.resource
        }
        fun stepUniformPlan(stepIndex: Int): org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan =
            when (stepUniformAuthorityKinds.getValue(stepIndex)) {
                "analytic64" -> requireNotNull(sharedAnalyticPlan)
                else -> requireNotNull(uniformSeal).plan
            }
        indexedCoreRenders.forEach { (stepIndex, render) ->
            val stepPreparation = uniformPreparationByResource[
                stepUniformResourceByStepIndex.getValue(stepIndex)
            ] ?: return refused("Path stencil render references an undeclared uniform slab.")
            val descriptor = stepPreparation.descriptor as? GPUFrameBufferDescriptor
                ?: return refused("Path stencil uniform slab descriptor is missing.")
            val plan = stepUniformPlan(stepIndex)
            if (descriptor.byteSize != plan.totalBytes ||
                descriptor.alignmentBytes != plan.alignmentBytes ||
                stepPreparation.byteSize != plan.totalBytes ||
                stepPreparation.usages != setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform) ||
                stepPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                plan.deviceGeneration != context.deviceGeneration.value ||
                plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                plan.totalBytes > (limits.maxBufferSize ?: return refused(
                    "Path stencil requires observed maxBufferSize.",
                )) ||
                (limits.maxDynamicUniformBuffersPerPipelineLayout ?: 0) < 1
            ) {
                return refused("Path stencil uniform slab topology or device-limit authority is not exact.")
            }
        }
        val depthDescriptor = depthStencil.descriptor as? GPUFrameTextureDescriptor
            ?: return refused("Path depth/stencil preparation must be a texture.")
        val targetBounds = targetDescriptor.logicalBounds
        val coreSampleCount = indexedCoreRenders
            .map { (_, render) -> render.samplePlan.sampleCount }
            .distinct()
            .singleOrNull()
            ?: return refused("Path stencil CorePrimitive runs must share one sample count.")
        val depthBytes = try {
            corePrimitiveDepthStencilByteSize(targetBounds, coreSampleCount)
        } catch (_: ArithmeticException) {
            return refused("Path depth/stencil byte size overflows signed 64-bit arithmetic.")
        }
        if (depthDescriptor.logicalBounds != targetBounds ||
            depthDescriptor.format.value != "depth24plus-stencil8" ||
            depthDescriptor.sampleCount != coreSampleCount ||
            depthStencil.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencil.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencil.byteSize != depthBytes
        ) {
            return refused("Path depth/stencil descriptor, extent, format, usage, lifetime, or size is not exact.")
        }
        val exactVertexUse = GPUFrameResourceUse(
            vertex.resource,
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceUsage.Vertex,
            GPUFrameResourceLifetime.FrameLocal,
            false,
        )
        val exactIndexUse = GPUFrameResourceUse(
            index.resource,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceUsage.Index,
            GPUFrameResourceLifetime.FrameLocal,
            false,
        )
        if (indexedCoreRenders.any { (stepIndex, render) ->
                val hasProducer = corePackets(render).any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilProducer
                }
                val hasCover = corePackets(render).any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilCover
                }
                val hasPath = hasProducer || hasCover
                val exactDepthUse = GPUFrameResourceUse(
                    depthStencil.resource,
                    GPUFrameResourceRole.PathDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    // The continued cover only tests the fan (read-only); the producer writes it.
                    write = hasProducer,
                )
                val expectedUniformUse = GPUFrameResourceUse(
                    stepUniformResourceByStepIndex.getValue(stepIndex),
                    GPUFrameResourceRole.UniformData,
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceLifetime.FrameLocal,
                    false,
                )
                // A continued destination-read path cover carries one extra
                // DestinationSnapshot TextureBinding use (the ordered snapshot the cover samples).
                val destinationSnapshotUses = render.resourceUses.filter { use ->
                    use.role == GPUFrameResourceRole.DestinationSnapshot
                }
                if (destinationSnapshotUses.any { use ->
                        use.usage != GPUFrameResourceUsage.TextureBinding ||
                            use.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                            use.write
                    } || destinationSnapshotUses.distinctBy { it.resource }.size !=
                    destinationSnapshotUses.size
                ) {
                    return refused(
                        "Path stencil destination snapshot uses must be exact read-only frame-local bindings.",
                    )
                }
                val expectedUses = if (hasPath) {
                    setOf(exactVertexUse, exactIndexUse, exactDepthUse, expectedUniformUse) +
                        destinationSnapshotUses
                } else {
                    setOf(exactVertexUse, exactIndexUse, expectedUniformUse)
                }
                val retainedCoreResourceUses = coreResourceUses(render)
                retainedCoreResourceUses.toSet() != expectedUses ||
                    retainedCoreResourceUses.size != expectedUses.size
            }
        ) {
            return refused("Path stencil render must retain exactly the shared resource uses.")
        }
        val exclusiveRefs = (uniformSlabs.map { it.resource } +
            listOf(vertex.resource, index.resource, depthStencil.resource)).toSet()
        val coreStepIndices = indexedCoreRenders.map { (index, _) -> index }.toSet()
        val foreignGeometryUse = framePlan.steps.withIndex()
            .filter { indexed -> indexed.index !in coreStepIndices }
            .any { indexed ->
                val typedUses = when (val step = indexed.value) {
                    is GPUFrameStep.RenderPassStep -> step.resourceUses
                    is GPUFrameStep.ComputePassStep -> step.resourceUses
                    else -> emptyList()
                }
                typedUses.any { use -> use.resource in exclusiveRefs } ||
                    referencedResources(indexed.value).any { resource -> resource in exclusiveRefs }
            }
        if (foreignGeometryUse) {
            return refused(
                "Path stencil shared geometry roles and resources are exclusive to its unique render scope.",
            )
        }
        if ((listOf(vertex, index, depthStencil) + uniformSlabs).any {
                context.resourceGenerations[it.resource] == null
            }
        ) {
            return refused("Path stencil shared resources require current generation evidence.")
        }

        val directPasses = linkedMapOf<Int, GPUCorePrimitiveDirectPreparedPassAuthority>()
        directStructuralKeysByStep.forEach { (sourceStepIndex, directStructuralKeys) ->
            if (directStructuralKeys.isNotEmpty()) {
                val exactUniform32 = uniformSeal
                    ?: return refused("Mixed direct path packets require the exact uniform32 authority.")
                if (directStructuralKeys.distinct().size != 1) {
                    return refused("Mixed direct packets must share one neutral depth/stencil structural key.")
                }
                directPasses[sourceStepIndex] = GPUCorePrimitiveDirectPreparedPassSeal(
                    directStructuralKeys.first(),
                    if (indexedCoreRenders.size == 1) {
                        exactUniform32
                    } else {
                        sliceUniformSlabSealToCommands(
                            exactUniform32,
                            unifiedUnitsByStep.getValue(sourceStepIndex).map { it.commandIdValue },
                        )
                    },
                )
            }
        }
        val unifiedRoutesByKey = linkedMapOf<
            GPUCorePrimitiveNativeScopeFrameRouteKey,
            GPUCorePrimitiveNativeScopeRouteSeal.Routes,
            >()
        var firstUniformIndex = 0
        unifiedUnitsByStep.forEach { (sourceStepIndex, unifiedUnits) ->
            val uniformCoverage = if (mixedPreparedSurface) {
                GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange(
                    firstUniformIndex,
                    unifiedUnits.size,
                )
            } else {
                GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope
            }
            val routes = when {
                preparedPathAnalyticSealsByStep.getValue(sourceStepIndex).isNotEmpty() ->
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        unifiedUnits,
                        pathPasses.getValue(sourceStepIndex),
                        uniformCoverage,
                    )
                mixedPreparedSurface ->
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        unifiedUnits,
                        requireNotNull(uniformSeal),
                        uniformCoverage,
                    )
                indexedCoreRenders.size == 1 ->
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        unifiedUnits,
                        requireNotNull(uniformSeal),
                        uniformCoverage,
                    )
                else -> {
                    val stepSlab = sliceUniformSlabSealToCommands(
                        requireNotNull(uniformSeal),
                        unifiedUnits.map { it.commandIdValue },
                    )
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes(
                        unifiedUnits,
                        stepSlab,
                        uniformCoverage,
                    )
                }
            }
            firstUniformIndex += unifiedUnits.size
            unifiedRoutesByKey[
                GPUCorePrimitiveNativeScopeFrameRouteKey(
                    sourceStepIndex,
                    routes.flattenedPacketIds.first(),
                )
            ] = routes
        }
        return CorePrimitiveGeometryValidation(
            null,
            GPUCorePrimitiveDirectNativeFrameRouteSeal(directRoutes, directPasses),
            GPUCorePrimitivePathStencilNativeFrameRouteSeal(
                pathRoutes,
                pathPasses,
            ),
            GPUCorePrimitiveNativeScopeFrameRouteSeal(unifiedRoutesByKey),
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

    /** W3 is the sole direct lane whose physical V/I/U slabs are sealed encoder scratch. */
    private fun hasExactW3SessionScratch(
        framePlan: GPUFramePlan,
        renders: List<GPUFrameStep.RenderPassStep>,
        scratch: W3SessionScratchV1,
    ): Boolean {
        val render = renders.singleOrNull() ?: return false
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .singleOrNull() ?: return false
        val expectedPlanId = readback.request.requestId.value
            .takeIf { it.startsWith("w3.") && it.endsWith(".readback") }
            ?.removePrefix("w3.")
            ?.removeSuffix(".readback")
            ?.takeIf(String::isNotBlank)
            ?: return false
        val limits = capabilities.limits ?: return false
        val maxBufferSize = limits.maxBufferSize ?: return false
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return false
        val packets = render.drawPackets
        val firstSemantic = packets.firstOrNull()?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return false
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val target = preparations.singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
        val staging = preparations.singleOrNull { it.role == GPUFrameResourceRole.ReadbackStaging }
        val targetDescriptor = target?.descriptor as? GPUFrameTextureDescriptor
        val stagingDescriptor = staging?.descriptor as? GPUFrameBufferDescriptor
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(firstSemantic.targetBounds.width.toLong(), firstSemantic.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val stagingBytes = try {
            val unpaddedBytesPerRow = Math.multiplyExact(firstSemantic.targetBounds.width.toLong(), 4L)
            val alignment = limits.copyBytesPerRowAlignment
            val paddedBytesPerRow = Math.addExact(
                unpaddedBytesPerRow,
                (alignment - unpaddedBytesPerRow % alignment) % alignment,
            )
            Math.multiplyExact(paddedBytesPerRow, firstSemantic.targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        return framePlan.steps.size == 3 &&
            framePlan.steps[0] is GPUFrameStep.PrepareResourcesStep &&
            framePlan.steps[1] === render && framePlan.steps[2] === readback &&
            framePlan.recordingSeals.size == 1 &&
            framePlan.recordingSeals.single().compatibilityKeyHash == "w3:$expectedPlanId" &&
            framePlan.recordingSeals.single().replayKeyHash == "w3:$expectedPlanId" &&
            packets.size in 1..512 &&
            scratch.matches(
                expectedPlanId,
                framePlan.capabilitySeal.sealHash,
                framePlan.capabilitySeal.deviceGeneration.value,
                render.target,
                readback.staging,
                firstSemantic.targetBounds,
                packets,
            ) &&
            scratch.hasExactUniformPayloads(limits.minUniformBufferOffsetAlignment, packets) &&
            scratch.fitsDeviceLimits(maxBufferSize, maxDynamicUniformBuffers) &&
            packets.all { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                val authority = packet.corePrimitivePreparedAuthority
                semantic != null &&
                    authority?.w3SessionScratch === scratch &&
                    authority.uniformSlabSeal == null &&
                    scratch.packetStructuralPipelineKeys == packets.map { candidate ->
                        val candidateSemantic = candidate.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                            ?: return false
                        corePrimitiveRenderPipelineStructuralKey(
                            candidateSemantic,
                            requireNotNull(candidate.clipExecutionPlan),
                            requireNotNull(candidate.blendPlan),
                            sampleCount = 1,
                            colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                        )
                    } &&
                    packet.analysisRecordId == semantic.analysisRecordId &&
                    packet.blendPlan?.isW5bW3Blend() == true &&
                    packet.clipExecutionPlan == if (semantic.scissorBounds == firstSemantic.targetBounds) {
                        org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip
                    } else {
                        org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.ScissorOnly(
                            semantic.scissorBounds,
                        )
                    } &&
                    authority.structuralPipelineKey == corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        requireNotNull(packet.clipExecutionPlan),
                        requireNotNull(packet.blendPlan),
                        sampleCount = 1,
                        colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                    ) &&
                    packet.renderPipelineKey == authority.structuralPipelineKey.stableRenderPipelineKey(
                        CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                    ) &&
                    semantic.sourceFamily == GPUCorePrimitiveSourceFamily.Rect &&
                    semantic.targetBounds == firstSemantic.targetBounds &&
                    semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor &&
                    semantic.material is GPUCorePrimitiveMaterialPayload.SolidColor &&
                    semantic.geometry is GPUCorePrimitiveGeometry.Rect
            } &&
            render.samplePlan == GPUSamplePlan.SingleSampleFrame &&
            render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.loadStore.clearColorLabel == null && render.resourceUses.isEmpty() &&
            render.sampleContinuation == null && render.depthStencilLoadStore == null &&
            readback.source == render.target &&
            readback.request.sourceBounds == firstSemantic.targetBounds &&
            readback.request.pixelFormat == GPUReadbackPixelFormat.Rgba8Unorm &&
            readback.request.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            readback.request.bufferOffsetBytes == 0L &&
            preparations.size == 2 && target?.resource == scratch.target &&
            target?.usages == setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) &&
            target.lifetime == GPUFrameResourceLifetime.FrameLocal && target.byteSize == targetBytes &&
            targetDescriptor?.logicalBounds == firstSemantic.targetBounds &&
            targetDescriptor.format == GPUColorFormat.RGBA8UnormSrgb && targetDescriptor.sampleCount == 1 &&
            staging?.resource == scratch.staging &&
            staging.usages == setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead) &&
            staging.lifetime == GPUFrameResourceLifetime.FrameLocal && staging.byteSize == stagingBytes &&
            stagingDescriptor?.byteSize == stagingBytes &&
            stagingDescriptor.alignmentBytes == limits.copyBytesPerRowAlignment &&
            framePlan.memoryBudget.allocations.size == 2 &&
            framePlan.memoryBudget.allocations.map { it.category } == listOf(
                GPUFrameMemoryCategory.CanonicalTarget,
                GPUFrameMemoryCategory.ReadbackStaging,
            ) && framePlan.memoryBudget.allocations[0].bytes == targetBytes &&
            framePlan.memoryBudget.allocations[0].extent == firstSemantic.targetBounds &&
            framePlan.memoryBudget.allocations[1].bytes == stagingBytes &&
            framePlan.memoryBudget.allocations[1].extent == null &&
            framePlan.memoryBudget.targetResidentBytes == targetBytes &&
            framePlan.memoryBudget.peakFrameTransientBytes == stagingBytes &&
            targetBytes <= Long.MAX_VALUE - stagingBytes &&
            targetBytes + stagingBytes <= framePlan.memoryBudget.configuredAggregateBudgetBytes &&
            framePlan.memoryBudget.allocations.none { it.category == GPUFrameMemoryCategory.ReusableScratch }
    }

    /** W4b seals the full ScalarAA RRect envelope before generic native-route classification. */
    private fun hasExactW4bSessionScratch(
        framePlan: GPUFramePlan,
        scratch: W4bSessionScratchV1,
        expectedW5aScratch: W5aAnalyticRRectSessionScratchV2? = null,
        compositeSessionIdentity: String? = null,
    ): Boolean {
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().singleOrNull() ?: return false
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull() ?: return false
        val expectedPlanId = readback.request.requestId.value
            .takeIf { it.startsWith("w4b.") && it.endsWith(".readback") }
            ?.removePrefix("w4b.")?.removeSuffix(".readback")?.takeIf(String::isNotBlank) ?: return false
        val limits = capabilities.limits ?: return false
        val maxBufferSize = limits.maxBufferSize ?: return false
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return false
        val expectedUniformStride = try {
            val alignment = limits.minUniformBufferOffsetAlignment
            if (alignment <= 0L) null else {
                val remainder = W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES % alignment
                if (remainder == 0L) W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES else Math.addExact(
                    W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES,
                    alignment - remainder,
                )
            }
        } catch (_: ArithmeticException) {
            null
        } ?: return false
        val packets = render.drawPackets
        val firstSemantic = packets.firstOrNull()?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return false
        val targetBounds = firstSemantic.targetBounds
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val target = preparations.singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
        val staging = preparations.singleOrNull { it.role == GPUFrameResourceRole.ReadbackStaging }
        val targetDescriptor = target?.descriptor as? GPUFrameTextureDescriptor
        val stagingDescriptor = staging?.descriptor as? GPUFrameBufferDescriptor
        val targetBytes = try {
            Math.multiplyExact(Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()), 4L)
        } catch (_: ArithmeticException) {
            return false
        }
        val stagingBytes = try {
            val row = Math.multiplyExact(targetBounds.width.toLong(), 4L)
            val alignment = limits.copyBytesPerRowAlignment
            Math.multiplyExact(Math.addExact(row, (alignment - row % alignment) % alignment), targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        val transientBytes = try {
            Math.addExact(
                Math.addExact(stagingBytes, scratch.vertexCapacityBytes),
                Math.addExact(scratch.indexCapacityBytes, scratch.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val scratchBytes = try {
            Math.addExact(
                scratch.vertexCapacityBytes,
                Math.addExact(scratch.indexCapacityBytes, scratch.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val requiredAggregateBudgetBytes = try {
            Math.addExact(targetBytes, transientBytes)
        } catch (_: ArithmeticException) {
            return false
        }
        val identity = compositeSessionIdentity ?: ("w4b.session.${scratch.deviceGeneration}." +
            "${targetBounds.width}x${targetBounds.height}.rgba8unorm-srgb")
        val expectedAllocations = listOf(
            GPUFrameMemoryAllocation(
                "$identity.target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                targetBounds,
            ),
            GPUFrameMemoryAllocation(
                "$identity.staging",
                GPUFrameMemoryCategory.ReadbackStaging,
                stagingBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.vertex",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.vertexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.index",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.indexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.uniform",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.uniformCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
        )
        val expectedCategoryTotals = try {
            GPUFrameMemoryCategory.entries.associateWith { category ->
                expectedAllocations
                    .filter { allocation -> allocation.category == category }
                    .fold(0L) { total, allocation -> Math.addExact(total, allocation.bytes) }
            }
        } catch (_: ArithmeticException) {
            return false
        }
        if (scratch.uniformPlan.totalBytes !in 1L..Int.MAX_VALUE.toLong()) return false
        val packedUniforms = ByteArray(scratch.uniformPlan.totalBytes.toInt())
        val payloads = packets.mapIndexed { index, packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
            val authority = packet.corePrimitivePreparedAuthority ?: return false
            val shape = authority.analyticShapeUniformSeal ?: return false
            val draw = scratch.draws.getOrNull(index) ?: return false
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.RRect ?: return false
            val device = draw.copyDeviceShape()
            val raster = draw.copyRasterBounds()
            val scissor = draw.copyScissorBounds()
            val expectedClip = if (scissor == targetBounds) {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip
            } else {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.ScissorOnly(scissor)
            }
            val expectedRadii = listOf(
                device.topLeft.x, device.topLeft.y, device.topRight.x, device.topRight.y,
                device.bottomRight.x, device.bottomRight.y, device.bottomLeft.x, device.bottomLeft.y,
            )
            val expectedRaster = w4bRasterBounds(geometry) ?: return false
            val exactClipCoverage = packet.clipCoveragePlan == semantic.clipCoveragePlan && when (val coverage = packet.clipCoveragePlan) {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip -> scissor == targetBounds
                is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor ->
                    coverage.bounds.left == scissor.left.toFloat() && coverage.bounds.top == scissor.top.toFloat() &&
                        coverage.bounds.right == scissor.right.toFloat() && coverage.bounds.bottom == scissor.bottom.toFloat()
                else -> false
            }
            if ((if (expectedW5aScratch == null) {
                    authority.w4bSessionScratch !== scratch || authority.w5aAnalyticRRectSessionScratch != null
                } else {
                    authority.w4bSessionScratch != null || authority.w5aAnalyticRRectSessionScratch !== expectedW5aScratch
                }) || authority.w3SessionScratch != null ||
                authority.w4aSessionScratch != null || authority.uniformSlabSeal != null ||
                authority.analyticClipUniformSeal != null || authority.analyticIntersectionUniformSeal != null ||
                authority.coverageMaskUniformSlabSeal != null ||
                authority.structuralPipelineKey != scratch.structuralPipelineKey ||
                authority.renderPipelineKey != packet.renderPipelineKey || shape.plan !== scratch.uniformPlan ||
                shape.slotIndex != index || shape.commandId != packet.commandIdValue || shape.packetId != packet.packetId ||
                shape.renderScissor != scissor || shape.structuralPipelineKey != scratch.structuralPipelineKey ||
                shape.renderPipelineKey != packet.renderPipelineKey ||
                shape.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH ||
                shape.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                shape.payloadBytes != W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES ||
                shape.alignedOffset != index.toLong() * scratch.uniformStrideBytes || !shape.hasExactSemantic(semantic) ||
                packet.renderPipelineKey != scratch.structuralPipelineKey.stableRenderPipelineKey(
                    CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                ) || packet.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH ||
                packet.renderStepId.value != CORE_PRIMITIVE_RENDER_STEP_IDENTITY || packet.renderStepVersion != 1 ||
                packet.role != GPUDrawPacketRole.Shading || packet.packetId.value != "packet.w4b.${packet.commandIdValue}" ||
                packet.analysisRecordId != "analysis.fill_rrect.${packet.commandIdValue}" || packet.passId != "pass.w4b.main" ||
                packet.layerId != "root" || packet.bindingListId != "binding.w4b.${packet.commandIdValue}" ||
                packet.insertionReasonCode != "w4b-analytic-rrect" || packet.originalPaintOrder != index ||
                packet.sortKey != index.toLong() || packet.sortKeyPreimage != "paint-order:$index" ||
                packet.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                !packet.blendPlan.isCanonicalSolidRectSrcOver() || packet.clipExecutionPlan != expectedClip ||
                !exactClipCoverage || semantic.clipExecutionPlanIdentity != expectedClip.canonicalIdentity() ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(scissor) ||
                packet.targetStateHash != corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb) ||
                semantic.sourceFamily != GPUCorePrimitiveSourceFamily.RRect || semantic.rectRouteAuthority != null ||
                semantic.rectGeometryAuthority != null || semantic.rrectGeometryAuthority == null ||
                semantic.material !is GPUCorePrimitiveMaterialPayload.SolidColor ||
                semantic.coverageMode != GPUCorePrimitiveCoverageMode.ScalarAA || semantic.targetBounds != targetBounds ||
                semantic.scissorBounds != scissor || geometry.left != device.rect.left || geometry.top != device.rect.top ||
                geometry.right != device.rect.right || geometry.bottom != device.rect.bottom || geometry.radii != expectedRadii ||
                raster != expectedRaster || scissor.left < maxOf(targetBounds.left, raster.left) ||
                scissor.top < maxOf(targetBounds.top, raster.top) ||
                scissor.right > minOf(targetBounds.right, raster.right) ||
                scissor.bottom > minOf(targetBounds.bottom, raster.bottom)
            ) return false
            shape.copyPayloadInto(packedUniforms, shape.alignedOffset.toInt())
            if (!shape.hasExactPayloadAt(packedUniforms, shape.alignedOffset.toInt())) return false
            GPUUniformSlabPayload("analytic-shape-draw-${packet.commandIdValue}", shape.payloadBytesSnapshot())
        }
        return framePlan.steps.size == 3 && framePlan.steps[0] is GPUFrameStep.PrepareResourcesStep &&
            framePlan.steps[1] === render && framePlan.steps[2] === readback && framePlan.recordingSeals.size == 1 &&
            framePlan.recordingSeals.single().compatibilityKeyHash == "w4b:$expectedPlanId" &&
            framePlan.recordingSeals.single().replayKeyHash == "w4b:$expectedPlanId" && packets.size in 1..512 &&
            scratch.matches(expectedPlanId, framePlan.capabilitySeal.sealHash, framePlan.capabilitySeal.deviceGeneration.value,
                render.target, readback.staging, targetBounds, packets) &&
            scratch.draws.any { it.origin == DrawOrigin.RRECT } &&
            scratch.draws.filter { it.origin == DrawOrigin.RECT }.all { it.isPositiveZeroRect() } &&
            scratch.maxBufferSize == maxBufferSize &&
            scratch.maxDynamicUniformBuffersPerPipelineLayout == maxDynamicUniformBuffers &&
            scratch.vertexResourceId.value == "VertexData:0" && scratch.indexResourceId.value == "IndexData:0" &&
            scratch.uniformResourceId.value == "UniformData:0" &&
            scratch.vertexUsefulBytes == packets.size.toLong() * 32L && scratch.indexUsefulBytes == packets.size.toLong() * 24L &&
            scratch.uniformStrideBytes == expectedUniformStride && scratch.uniformPlan.alignmentBytes == expectedUniformStride &&
            scratch.uniformUsefulBytes == packets.size.toLong() * scratch.uniformStrideBytes &&
            scratch.uniformPlan.hasExactPayloads(W4bSessionScratchV1.SOURCE_LABEL, scratch.deviceGeneration,
                scratch.uniformStrideBytes, payloads) && scratch.uniformPlan.uploadBudgetBytes == scratch.uniformCapacityBytes &&
            scratch.vertexCapacityBytes == scratch.poolCapacities.vertexBytes &&
            scratch.indexCapacityBytes == scratch.poolCapacities.indexBytes &&
            scratch.uniformCapacityBytes == scratch.poolCapacities.uniformBytes &&
            render.samplePlan == GPUSamplePlan.SingleSampleFrame && render.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            render.resourceUses.isEmpty() && render.sampleContinuation == null && render.depthStencilLoadStore == null &&
            readback.source == render.target && readback.request.sourceBounds == targetBounds &&
            readback.request.pixelFormat == GPUReadbackPixelFormat.Rgba8Unorm &&
            readback.request.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            readback.request.bufferOffsetBytes == 0L && preparations.size == 2 && target?.resource == scratch.target &&
            target.diagnosticLabel == "$identity.target" &&
            target.usages == setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) &&
            target.lifetime == GPUFrameResourceLifetime.FrameLocal && target.byteSize == targetBytes &&
            targetDescriptor?.logicalBounds == targetBounds && targetDescriptor.format == GPUColorFormat.RGBA8UnormSrgb &&
            targetDescriptor.sampleCount == 1 && staging?.resource == scratch.staging &&
            staging.diagnosticLabel == "$identity.staging" &&
            staging.usages == setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead) &&
            staging.lifetime == GPUFrameResourceLifetime.FrameLocal && staging.byteSize == stagingBytes &&
            stagingDescriptor?.byteSize == stagingBytes && stagingDescriptor.alignmentBytes == limits.copyBytesPerRowAlignment &&
            framePlan.memoryBudget.diagnostic == null &&
            framePlan.memoryBudget.targetResidentBytes == targetBytes &&
            framePlan.memoryBudget.peakFrameTransientBytes == transientBytes &&
            framePlan.memoryBudget.categoryTotals == expectedCategoryTotals &&
            framePlan.memoryBudget.configuredAggregateBudgetBytes >= requiredAggregateBudgetBytes &&
            framePlan.memoryBudget.allocations == expectedAllocations
    }

    private fun w4bRasterBounds(geometry: GPUCorePrimitiveGeometry.RRect): RectI32? {
        val edges = listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
        if (edges.any { !it.isFinite() } || geometry.left >= geometry.right || geometry.top >= geometry.bottom) return null
        val raster = listOf(
            floor(geometry.left.toDouble()), floor(geometry.top.toDouble()),
            ceil(geometry.right.toDouble()), ceil(geometry.bottom.toDouble()),
        )
        if (raster.any { it < Int.MIN_VALUE.toDouble() || it > Int.MAX_VALUE.toDouble() }) return null
        return RectI32(raster[0].toInt(), raster[1].toInt(), raster[2].toInt(), raster[3].toInt())
            .takeUnless(RectI32::isEmpty64)
    }

    /** W4a seals the full ScalarAA Rect envelope before generic native-route classification. */
    private fun hasExactW4aSessionScratch(
        framePlan: GPUFramePlan,
        scratch: W4aSessionScratchV1,
        expectedW5aScratch: W5aAnalyticRectSessionScratchV2? = null,
    ): Boolean {
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .singleOrNull() ?: return false
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .singleOrNull() ?: return false
        val expectedPlanId = readback.request.requestId.value
            .takeIf { it.startsWith("w4a.") && it.endsWith(".readback") }
            ?.removePrefix("w4a.")
            ?.removeSuffix(".readback")
            ?.takeIf(String::isNotBlank)
            ?: return false
        val limits = capabilities.limits ?: return false
        val maxBufferSize = limits.maxBufferSize ?: return false
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return false
        val expectedUniformStride = W4aSessionScratchV1.canonicalUniformStrideOrNull(
            limits.minUniformBufferOffsetAlignment,
        ) ?: return false
        val packets = render.drawPackets
        val firstSemantic = packets.firstOrNull()?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return false
        val targetBounds = firstSemantic.targetBounds
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val target = preparations.singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
        val staging = preparations.singleOrNull { it.role == GPUFrameResourceRole.ReadbackStaging }
        val targetDescriptor = target?.descriptor as? GPUFrameTextureDescriptor
        val stagingDescriptor = staging?.descriptor as? GPUFrameBufferDescriptor
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val stagingBytes = try {
            val rowBytes = Math.multiplyExact(targetBounds.width.toLong(), 4L)
            val alignment = limits.copyBytesPerRowAlignment
            val paddedRowBytes = Math.addExact(rowBytes, (alignment - rowBytes % alignment) % alignment)
            Math.multiplyExact(paddedRowBytes, targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        val transientBytes = try {
            Math.addExact(
                Math.addExact(stagingBytes, scratch.vertexCapacityBytes),
                Math.addExact(scratch.indexCapacityBytes, scratch.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val scratchBytes = try {
            Math.addExact(
                scratch.vertexCapacityBytes,
                Math.addExact(scratch.indexCapacityBytes, scratch.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val identity = "w4a.session.${scratch.deviceGeneration}." +
            "${targetBounds.width}x${targetBounds.height}.rgba8unorm-srgb"
        val expectedAllocations = listOf(
            GPUFrameMemoryAllocation(
                "$identity.target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                targetBounds,
            ),
            GPUFrameMemoryAllocation(
                "$identity.staging",
                GPUFrameMemoryCategory.ReadbackStaging,
                stagingBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.vertex",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.vertexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.index",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.indexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.uniform",
                GPUFrameMemoryCategory.ReusableScratch,
                scratch.uniformCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
        )
        if (scratch.uniformPlan.totalBytes !in 1L..Int.MAX_VALUE.toLong()) return false
        val packedUniforms = ByteArray(scratch.uniformPlan.totalBytes.toInt())
        val slabPayloads = packets.mapIndexed { index, packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return false
            val authority = packet.corePrimitivePreparedAuthority ?: return false
            val shape = authority.analyticShapeUniformSeal ?: return false
            val snapshot = scratch.draws.getOrNull(index) ?: return false
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.Rect ?: return false
            val device = snapshot.copyDeviceBounds()
            val raster = snapshot.copyRasterBounds()
            val scissor = snapshot.copyScissorBounds()
            val expectedRaster = w4aRasterBounds(geometry) ?: return false
            val expectedClip = if (scissor == targetBounds) {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip
            } else {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.ScissorOnly(scissor)
            }
            val expectedStructural = corePrimitiveRenderPipelineStructuralKey(
                semantic,
                expectedClip,
                packet.blendPlan ?: return false,
                sampleCount = 1,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
            )
            val exactClipCoverage = packet.clipCoveragePlan == semantic.clipCoveragePlan && when (
                val coverage = packet.clipCoveragePlan
            ) {
                org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip ->
                    scissor == targetBounds
                is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor ->
                    coverage.bounds.left == scissor.left.toFloat() &&
                        coverage.bounds.top == scissor.top.toFloat() &&
                        coverage.bounds.right == scissor.right.toFloat() &&
                        coverage.bounds.bottom == scissor.bottom.toFloat()
                else -> false
            }
            if ((if (expectedW5aScratch == null) {
                    authority.w4aSessionScratch !== scratch || authority.w5aAnalyticRectSessionScratch != null
                } else {
                    authority.w4aSessionScratch != null || authority.w5aAnalyticRectSessionScratch !== expectedW5aScratch
                }) || authority.w3SessionScratch != null ||
                authority.uniformSlabSeal != null || authority.analyticClipUniformSeal != null ||
                authority.analyticIntersectionUniformSeal != null || authority.coverageMaskUniformSlabSeal != null ||
                authority.structuralPipelineKey != scratch.structuralPipelineKey ||
                authority.renderPipelineKey != packet.renderPipelineKey ||
                shape.plan !== scratch.uniformPlan || shape.slotIndex != index ||
                shape.commandId != packet.commandIdValue || shape.packetId != packet.packetId ||
                shape.renderScissor != scissor || shape.structuralPipelineKey != scratch.structuralPipelineKey ||
                shape.renderPipelineKey != packet.renderPipelineKey ||
                shape.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH ||
                shape.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                shape.payloadBytes != W4aSessionScratchV1.UNIFORM_PAYLOAD_BYTES ||
                shape.alignedOffset != index.toLong() * scratch.uniformStrideBytes ||
                !shape.hasExactSemantic(semantic) ||
                packet.renderPipelineKey != scratch.structuralPipelineKey.stableRenderPipelineKey(
                    CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                ) ||
                packet.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH ||
                packet.renderStepId.value != CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
                packet.renderStepVersion != 1 || packet.role != GPUDrawPacketRole.Shading ||
                packet.packetId.value != "packet.w4a.${packet.commandIdValue}" ||
                packet.analysisRecordId != "analysis.fill_rect.${packet.commandIdValue}" ||
                packet.passId != "pass.w4a.main" || packet.layerId != "root" ||
                packet.bindingListId != "binding.w4a.${packet.commandIdValue}" ||
                packet.insertionReasonCode != "w4a-analytic-rect" ||
                packet.originalPaintOrder != index || packet.sortKey != index.toLong() ||
                packet.sortKeyPreimage != "paint-order:$index" ||
                packet.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                !packet.blendPlan.isCanonicalSolidRectSrcOver() ||
                packet.clipExecutionPlan != expectedClip ||
                !exactClipCoverage ||
                semantic.clipExecutionPlanIdentity != expectedClip.canonicalIdentity() ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(scissor) ||
                packet.targetStateHash != corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb) ||
                semantic.sourceFamily != GPUCorePrimitiveSourceFamily.Rect ||
                semantic.rectRouteAuthority !=
                org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority.RectAxisAligned ||
                semantic.rectGeometryAuthority == null || semantic.rrectGeometryAuthority != null ||
                semantic.material !is GPUCorePrimitiveMaterialPayload.SolidColor ||
                semantic.coverageMode != GPUCorePrimitiveCoverageMode.ScalarAA ||
                semantic.targetBounds != targetBounds || semantic.scissorBounds != scissor ||
                geometry.left != device.left || geometry.top != device.top ||
                geometry.right != device.right || geometry.bottom != device.bottom ||
                raster != expectedRaster ||
                scissor.left < maxOf(targetBounds.left, raster.left) ||
                scissor.top < maxOf(targetBounds.top, raster.top) ||
                scissor.right > minOf(targetBounds.right, raster.right) ||
                scissor.bottom > minOf(targetBounds.bottom, raster.bottom) ||
                expectedStructural != scratch.structuralPipelineKey
            ) return false
            shape.copyPayloadInto(packedUniforms, shape.alignedOffset.toInt())
            if (!shape.hasExactPayloadAt(packedUniforms, shape.alignedOffset.toInt())) return false
            GPUUniformSlabPayload("analytic-shape-draw-${packet.commandIdValue}", shape.payloadBytesSnapshot())
        }
        val requiredAggregateBudgetBytes = try {
            Math.addExact(
                framePlan.memoryBudget.targetResidentBytes,
                framePlan.memoryBudget.peakFrameTransientBytes,
            )
        } catch (_: ArithmeticException) {
            return false
        }
        return framePlan.steps.size == 3 &&
            framePlan.steps[0] is GPUFrameStep.PrepareResourcesStep &&
            framePlan.steps[1] === render && framePlan.steps[2] === readback &&
            framePlan.recordingSeals.size == 1 &&
            framePlan.recordingSeals.single().compatibilityKeyHash == "w4a:$expectedPlanId" &&
            framePlan.recordingSeals.single().replayKeyHash == "w4a:$expectedPlanId" &&
            packets.size in 1..512 &&
            scratch.matches(
                expectedPlanId,
                framePlan.capabilitySeal.sealHash,
                framePlan.capabilitySeal.deviceGeneration.value,
                render.target,
                readback.staging,
                targetBounds,
                packets,
            ) &&
            scratch.targetBounds == targetBounds &&
            scratch.maxBufferSize == maxBufferSize &&
            scratch.maxDynamicUniformBuffersPerPipelineLayout == maxDynamicUniformBuffers &&
            scratch.vertexResourceId.value == "VertexData:0" &&
            scratch.indexResourceId.value == "IndexData:0" &&
            scratch.uniformResourceId.value == "UniformData:0" &&
            scratch.structuralPipelineKey.shader ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
            scratch.structuralPipelineKey.topology ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
            scratch.structuralPipelineKey.sampleCount == 1 &&
            scratch.structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 &&
            scratch.vertexUsefulBytes == packets.size.toLong() * 32L &&
            scratch.indexUsefulBytes == packets.size.toLong() * 24L &&
            scratch.uniformStrideBytes == expectedUniformStride &&
            scratch.uniformPlan.alignmentBytes == expectedUniformStride &&
            scratch.uniformUsefulBytes == packets.size.toLong() * scratch.uniformStrideBytes &&
            scratch.uniformPlan.hasExactPayloads(
                W4aSessionScratchV1.SOURCE_LABEL,
                scratch.deviceGeneration,
                scratch.uniformStrideBytes,
                slabPayloads,
            ) &&
            scratch.uniformPlan.uploadBudgetBytes == scratch.uniformCapacityBytes &&
            scratch.vertexCapacityBytes == scratch.poolCapacities.vertexBytes &&
            scratch.indexCapacityBytes == scratch.poolCapacities.indexBytes &&
            scratch.uniformCapacityBytes == scratch.poolCapacities.uniformBytes &&
            render.samplePlan == GPUSamplePlan.SingleSampleFrame &&
            render.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            render.resourceUses.isEmpty() && render.sampleContinuation == null &&
            render.depthStencilLoadStore == null &&
            readback.source == render.target &&
            readback.request.sourceBounds == targetBounds &&
            readback.request.pixelFormat == GPUReadbackPixelFormat.Rgba8Unorm &&
            readback.request.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            readback.request.bufferOffsetBytes == 0L &&
            preparations.size == 2 && target?.resource == scratch.target &&
            target.diagnosticLabel == "$identity.target" &&
            target.usages == setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) &&
            target.lifetime == GPUFrameResourceLifetime.FrameLocal && target.byteSize == targetBytes &&
            targetDescriptor?.logicalBounds == targetBounds &&
            targetDescriptor.format == GPUColorFormat.RGBA8UnormSrgb && targetDescriptor.sampleCount == 1 &&
            staging?.resource == scratch.staging && staging.diagnosticLabel == "$identity.staging" &&
            staging.usages == setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead) &&
            staging.lifetime == GPUFrameResourceLifetime.FrameLocal && staging.byteSize == stagingBytes &&
            stagingDescriptor?.byteSize == stagingBytes &&
            stagingDescriptor.alignmentBytes == limits.copyBytesPerRowAlignment &&
            framePlan.memoryBudget.diagnostic == null &&
            framePlan.memoryBudget.targetResidentBytes == targetBytes &&
            framePlan.memoryBudget.peakFrameTransientBytes == transientBytes &&
            framePlan.memoryBudget.categoryTotals.keys == GPUFrameMemoryCategory.entries.toSet() &&
            framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.CanonicalTarget] == targetBytes &&
            framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReadbackStaging] == stagingBytes &&
            framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReusableScratch] == scratchBytes &&
            framePlan.memoryBudget.configuredAggregateBudgetBytes >= requiredAggregateBudgetBytes &&
            framePlan.memoryBudget.allocations == expectedAllocations
    }

    private fun w4aRasterBounds(geometry: GPUCorePrimitiveGeometry.Rect): RectI32? {
        val values = listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
        if (values.any { value -> !value.isFinite() } || geometry.left >= geometry.right ||
            geometry.top >= geometry.bottom
        ) return null
        val edges = listOf(
            floor(geometry.left.toDouble()),
            floor(geometry.top.toDouble()),
            ceil(geometry.right.toDouble()),
            ceil(geometry.bottom.toDouble()),
        )
        if (edges.any { edge -> edge < Int.MIN_VALUE.toDouble() || edge > Int.MAX_VALUE.toDouble() }) {
            return null
        }
        return RectI32(edges[0].toInt(), edges[1].toInt(), edges[2].toInt(), edges[3].toInt())
            .takeUnless(RectI32::isEmpty64)
    }

    private fun validateCorePrimitiveDirectGeometryResourcesDiagnostic(
        framePlan: GPUFramePlan,
        strictNativeRoute: Boolean,
        retainAcceptedRoutes: (
            Map<GPUCorePrimitiveDirectNativeFrameRouteKey, GPUCorePrimitiveDirectNativeRoute.Accepted>,
            Map<Int, GPUCorePrimitiveDirectPreparedPassAuthority>,
        ) -> Unit,
    ): GPUDiagnostic? {
        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val coreRenders = renders.filter { render ->
            render.drawPackets.any { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
        }
        renders.mapNotNull { it.w5bInitialClearV3?.clearOnly }.firstOrNull()?.let { witness ->
            return if (witness.validates(framePlan)) null else diagnostic(
                "invalid.preflight.w5b-clear-only", "W5b clear-only graph changed before native allocation.")
        }
        if (coreRenders.isEmpty()) return null
        if (coreRenders.any { render ->
                render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                    render.sampleContinuation?.key?.attachmentAuthority !=
                    org.graphiks.kanvas.gpu.renderer.passes
                        .GPUSampleAttachmentAuthority.PreparedFramePayload
            }
        ) {
            return diagnostic(
                "unsupported.preflight.core_primitive_msaa_attachment_authority",
                "The B3.5c CorePrimitive 4x route requires payload-owned MSAA attachment authority.",
            )
        }
        if (coreRenders.any { render ->
                render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                    (render.depthStencilLoadStore != null || render.resourceUses.any { use ->
                        use.role == GPUFrameResourceRole.PathDepthStencil ||
                            use.role == GPUFrameResourceRole.ClipDepthStencil
                    })
            }
        ) {
            return diagnostic(
                "unsupported.preflight.core_primitive_msaa_depth_stencil",
                "The B3.5c CorePrimitive 4x route is color-only and refuses depth/stencil state.",
            )
        }
        data class Direct(
            val sourceStepIndex: Int,
            val render: GPUFrameStep.RenderPassStep,
            val packet: GPUDrawPacket,
            val semantic: GPUDrawSemanticPayload.CorePrimitive,
            val route: GPUCorePrimitiveDirectNativeRoute.Accepted,
        )
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val preparedImageResourceRefs = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .flatMap { step -> step.imageResourcePlan?.preparationRequests.orEmpty() }
            .map(GPUResourcePreparationRequest::resource)
            .toSet()
        val directRoles = setOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
        )
        val exactPreparedSurfaceMixedBoundary =
            hasExactPreparedSurfaceMixedNativeBoundary(framePlan)
        val coreDirectResourceRefs = coreRenders
            .flatMap { render ->
                render.resourceUses.filter { use ->
                    use.resource !in preparedImageResourceRefs
                }
            }
            .filter { use -> use.role in directRoles }
            .map(GPUFrameResourceUse::resource)
            .toSet()
        val directPreparations = preparations.filter { preparation ->
            preparation.role in directRoles &&
                (!exactPreparedSurfaceMixedBoundary ||
                    preparation.resource in coreDirectResourceRefs)
        }
        val directUsesByRender = renders.associateWith { render ->
            render.resourceUses.filter { use ->
                use.resource !in preparedImageResourceRefs && use.role in directRoles
            }
        }
        fun refuse(message: String) = diagnostic(
            "invalid.preflight.core_primitive_direct_geometry_resources",
            message,
        )
        fun refuseAnalytic(message: String) = diagnostic(
            "invalid.preflight.core_primitive_analytic_clip_uniform_seal",
            message,
        )
        fun refuseShape(message: String) = diagnostic(
            "invalid.preflight.core_primitive_analytic_shape_uniform_seal",
            message,
        )
        val targetPreparations = buildMap<GPUFrameResourceRef, GPUFrameTextureDescriptor> {
            preparations.filter { it.role == GPUFrameResourceRole.SceneTarget }
                .forEach { request ->
                    (request.descriptor as? GPUFrameTextureDescriptor)?.let { descriptor ->
                        put(request.resource, descriptor)
                    }
                }
            // Layer targets materialize from the pooled RGBA8 attachment; the direct route
            // authority only needs their exact pooled format for structural-key resolution.
            framePlan.steps
                .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
                .map { step -> GPUFrameTargetRef(step.targetLabel) }
                .forEach { layer ->
                    put(
                        layer,
                        GPUFrameTextureDescriptor(
                            GPUPixelBounds(0, 0, 1, 1),
                            GPUColorFormat("rgba8unorm"),
                            1,
                        ),
                    )
                }
        }
        val routeResults = coreRenders.flatMap { render ->
            render.drawPackets.mapNotNull { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    ?: return@mapNotNull null
                val targetFormat = (
                    targetPreparations[render.target]
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
        if (accepted.any { entry ->
                val block = entry.semantic.payloadRef.uniformBlock
                block == null || entry.semantic.payloadRef.uniformSlot?.fingerprint != block.fingerprint ||
                    block.byteSize != corePrimitiveUniformByteSize(entry.semantic.material).toLong() ||
                    block.bytes.size != corePrimitiveUniformByteSize(entry.semantic.material)
            }
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_semantic_integrity",
                "Core primitive packet authority contradicts its immutable semantic input.",
            )
        }
        val w5bWitness = coreRenders.flatMap { it.drawPackets }
            .mapNotNull { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 }.firstOrNull()
        if (w5bWitness != null) {
            return if (w5bWitness.validates(framePlan)) null else diagnostic("invalid.preflight.w5b-witness", "W5b prepared graph changed before native allocation.")
        }
        val w3Scratch = coreRenders.flatMap { it.drawPackets }
            .mapNotNull { it.corePrimitivePreparedAuthority?.w3SessionScratch }
            .firstOrNull()
        if (w3Scratch != null) {
            if (!hasExactW3SessionScratch(framePlan, coreRenders, w3Scratch)) {
                return diagnostic(
                    "invalid.preflight.w3_session_scratch",
                    "W3 encoder scratch authority is absent, stale, or contradicts the closed frame envelope.",
                )
            }
            return null
        }
        // A frame that exactly matches the prepared-surface mixed boundary owns mask/depth
        // artifacts for its non-core members (text, image, vertices); only an all-direct-core
        // frame is forbidden from carrying them.
        if (!exactPreparedSurfaceMixedBoundary && accepted.isNotEmpty()) {
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
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val dstCopyConsumerPacketId = copySteps.singleOrNull()?.consumers?.singleOrNull()?.packetId
        // A destination-reading core frame uses ordered-copy admission, with
        // the ordered snapshot copy between them (Graphite DrawContext.cpp recipe: the consuming
        // pass runs after the copy in the same encoder). The shape is admitted when the ordered
        // CopyDestinationStep consumer resolves to one packet of the second core render, both core
        // renders share the exact same scene target, and every core packet of both renders
        // classified Accepted (a malformed frame carrying a non-accepted packet in a core render
        // must refuse at preflight instead of failing later at materialization).
        val twoRenderDstReadShape = copySteps.isNotEmpty() &&
            coreRenders.size == 2 &&
            coreRenders.sumOf { render -> render.drawPackets.size } == accepted.size &&
            coreRenders.map { it.target }.distinct().size == 1 &&
            dstCopyConsumerPacketId != null &&
            coreRenders.any { render ->
                render.drawPackets.any { packet -> packet.packetId == dstCopyConsumerPacketId }
            }
        val multiRenderDstReadShape = copySteps.isNotEmpty() &&
            coreRenders.size > 1 &&
            coreRenders.sumOf { render -> render.drawPackets.size } == accepted.size &&
            coreRenders.map { it.target }.distinct().size == 1 &&
            copySteps.all { copy ->
                val consumerPacketId = copy.consumers.singleOrNull()?.packetId
                consumerPacketId != null && coreRenders.any { render ->
                    render.drawPackets.any { packet -> packet.packetId == consumerPacketId }
                }
            }
        // The direct pass may split into N render passes when every render's
        // packets retain exactly one uniform layout (each split pass owns its slab). The
        // per-render seals are validated below; the render-count admission accepts the split
        // shape without the old single-pass requirement, while a destination-copy frame keeps
        // its two-render admission (the ordered snapshot copy must sit between exactly two
        // passes).
        val singleLayoutPerRender = accepted.groupBy(Direct::sourceStepIndex).all { (_, entries) ->
            entries.map { it.packet.corePrimitivePreparedAuthority?.structuralPipelineKey?.uniformLayout }
                .distinct().size == 1
        }
        val corePacketCount = coreRenders.sumOf { render ->
            render.drawPackets.count { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }
        }
        if ((!exactPreparedSurfaceMixedBoundary && coreRenders.size != 1 &&
                !(singleLayoutPerRender && copySteps.isEmpty())) ||
            corePacketCount != accepted.size ||
            (!exactPreparedSurfaceMixedBoundary &&
                renders.any {
                    it !in coreRenders && directUsesByRender.getValue(it).isNotEmpty()
                })
        ) {
            if (!twoRenderDstReadShape && !multiRenderDstReadShape) {
                return refuse("Direct CorePrimitive requires one all-direct render pass per uniform layout.")
            }
        }
        val dstConsumerRender = if (twoRenderDstReadShape) {
            coreRenders.first { render ->
                render.drawPackets.any { packet -> packet.packetId == dstCopyConsumerPacketId }
            }
        } else {
            null
        }
        val directRender = coreRenders.first()
        val directTargetDescriptor =
            targetPreparations[directRender.target]
                ?: return diagnostic(
                    "unsupported.native-core-primitive.target-format",
                    "Direct CorePrimitive native geometry requires an exact prepared target format.",
                )
        val directStructuralColorFormat = try {
            directTargetDescriptor.format.corePrimitiveStructuralColorFormat()
        } catch (_: IllegalArgumentException) {
            return diagnostic(
                "unsupported.native-core-primitive.target-format",
                "Direct CorePrimitive native geometry requires an exact prepared target format.",
            )
        }
        if (!exactPreparedSurfaceMixedBoundary) {
            if (dstConsumerRender != null) {
                // The admitted two-render dst-copy shape owns the exact clear/store producer
                // pass followed by the load/store consuming pass (the recording orders the
                // ordered snapshot copy between them).
                val producerRender = coreRenders.first { it !== dstConsumerRender }
                val producerIndex = framePlan.steps.indexOf(producerRender)
                val consumerIndex = framePlan.steps.indexOf(dstConsumerRender)
                val copyIndex = copySteps.single().let(framePlan.steps::indexOf)
                val exactLoadStore = producerRender.loadStore.loadOp == "clear" &&
                    producerRender.loadStore.storePlan == GPUStorePlan.Store &&
                    producerRender.loadStore.clearColorLabel == null &&
                    dstConsumerRender.loadStore.loadOp == "load" &&
                    dstConsumerRender.loadStore.storePlan == GPUStorePlan.Store &&
                    dstConsumerRender.loadStore.clearColorLabel == null &&
                    producerIndex < copyIndex && copyIndex < consumerIndex
                if (!exactLoadStore) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_direct_load_store",
                        "The two-render dst-copy shape requires the clear/store producer, the ordered " +
                            "snapshot copy, then the load/store consuming pass.",
                    )
                }
            } else if (coreRenders.indices.any { index ->
                    val expectedLoad = if (index == 0) "clear" else "load"
                    coreRenders[index].loadStore.loadOp != expectedLoad ||
                        coreRenders[index].loadStore.storePlan != GPUStorePlan.Store ||
                        coreRenders[index].loadStore.clearColorLabel != null
                }
            ) {
                return diagnostic(
                    "invalid.preflight.core_primitive_direct_load_store",
                    "Direct CorePrimitive requires the clear/store first pass followed by load/store split passes.",
                )
            }
        }
        val vertex = directPreparations.filter { it.role == GPUFrameResourceRole.VertexData }.singleOrNull()
            ?: return refuse("Direct CorePrimitive requires exactly one shared vertex slab.")
        val index = directPreparations.filter { it.role == GPUFrameResourceRole.IndexData }.singleOrNull()
            ?: return refuse("Direct CorePrimitive requires exactly one shared index slab.")
        // The layout split owns one uniform slab per layout group, so a direct
        // frame may declare one uniform preparation per present layout instead of one slab.
        val uniformSlabs = directPreparations.filter { it.role == GPUFrameResourceRole.UniformData }
        if (uniformSlabs.isEmpty()) {
            return refuse("Direct CorePrimitive requires at least one shared uniform slab.")
        }
        if (directPreparations.size != 2 + uniformSlabs.size ||
            setOf(vertex.resource, index.resource).size != 2 ||
            uniformSlabs.map { it.resource }.distinct().size != uniformSlabs.size
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
        val packetAuthorities = accepted.map { entry ->
            entry.packet.corePrimitivePreparedAuthority
                ?: return refuse("Direct CorePrimitive packet is missing its builder authority seal.")
        }
        val declaredLayerTargetLabels = framePlan.steps
            .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
            .map(GPUFrameStep.LayerTargetPrepareStep::targetLabel)
            .toSet()
        accepted.indices.forEach { acceptedIndex ->
            val entry = accepted[acceptedIndex]
            if (entry.render.target.value in declaredLayerTargetLabels) {
                // Layer-target renders route through the pooled RGBA8 attachment; their
                // structural authority resolves against the pooled format, not the scene target.
                return@forEach
            }
            val clipExecutionPlan = entry.packet.clipExecutionPlan
                ?: return refuse("Direct CorePrimitive packet is missing its builder clip authority.")
            val blendPlan = entry.packet.blendPlan
                ?: return refuse("Direct CorePrimitive packet is missing its builder blend authority.")
            val expectedStructuralKey = corePrimitiveRenderPipelineStructuralKey(
                entry.semantic,
                clipExecutionPlan,
                blendPlan,
                entry.render.samplePlan.sampleCount,
                directStructuralColorFormat,
            )
            val authority = packetAuthorities[acceptedIndex]
            if (authority.structuralPipelineKey != expectedStructuralKey ||
                authority.structuralPipelineKey.role !=
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading
            ) {
                val message = "Direct CorePrimitive packet has non-canonical shading pipeline authority."
                return if (expectedStructuralKey.uniformLayout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                    expectedStructuralKey.uniformLayout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
                ) {
                    refuseShape(message)
                } else {
                    refuse(message)
                }
            }
        }
        val sceneAcceptedIndices = accepted.indices.filter { acceptedIndex ->
            accepted[acceptedIndex].render.target.value !in declaredLayerTargetLabels
        }
        // The direct pass admits per-key structural pipelines (Graphite DrawPass fFullPipelines):
        // every packet's render pipeline key must match its own structural key's stable authority,
        // and the pass shares one exact uniform layout. The stable key is derived once per
        // distinct structural key, never once per draw.
        val sceneStructuralKeys = sceneAcceptedIndices.map { acceptedIndex ->
            packetAuthorities[acceptedIndex].structuralPipelineKey
        }
        val structuralPipelineKey = sceneStructuralKeys.first()
        val stableRenderPipelineKeys = sceneStructuralKeys.distinct().associateWith { key ->
            key.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        }
        if (sceneAcceptedIndices.any { acceptedIndex ->
                packetAuthorities[acceptedIndex].renderPipelineKey !=
                    stableRenderPipelineKeys.getValue(
                        packetAuthorities[acceptedIndex].structuralPipelineKey,
                    ) ||
                    accepted[acceptedIndex].packet.renderPipelineKey !=
                    packetAuthorities[acceptedIndex].renderPipelineKey
            }
        ) {
            val message = "Direct CorePrimitive packet has non-canonical shading pipeline authority."
            return if (structuralPipelineKey.uniformLayout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                structuralPipelineKey.uniformLayout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                refuseShape(message)
            } else {
                refuse(message)
            }
        }
        fun refuseIntersection(message: String) = diagnostic(
            "invalid.preflight.core_primitive_analytic_intersection_uniform_seal",
            message,
        )
        val acceptedStepIndexes = accepted.map(Direct::sourceStepIndex).distinct()
        val singleStepFrame = acceptedStepIndexes.size == 1
        val stepCommandIdsByIndex = acceptedStepIndexes.associateWith { stepIndex ->
            accepted.filter { it.sourceStepIndex == stepIndex }.map { it.packet.commandIdValue }
        }
        val stepAcceptedIndicesByIndex = acceptedStepIndexes.associateWith { stepIndex ->
            accepted.indices.filter { accepted[it].sourceStepIndex == stepIndex }
        }
        // Per-render-scope uniform authority. Every direct render pass retains
        // exactly one uniform layout, and each layout owns its own slab (the recording emits
        // one split pass per layout group). The step derives its seals from the packet
        // authorities of its own scope.
        data class StepUniformAuthority(
            val layout: GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout,
            val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
            val analyticShapeSeals: List<GPUCorePrimitiveAnalyticShapeUniformSeal>,
            val analyticClipSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal>,
            val analyticIntersectionSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal>,
            val uniformPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan,
        )
        val stepUniformAuthorities = linkedMapOf<Int, StepUniformAuthority>()
        acceptedStepIndexes.forEach { stepIndex ->
            val stepAcceptedIndices = stepAcceptedIndicesByIndex.getValue(stepIndex)
            val stepAuthorities = stepAcceptedIndices.map { packetAuthorities[it] }
            val stepLayouts = stepAuthorities
                .map { it.structuralPipelineKey.uniformLayout }
                .distinct()
            if (stepLayouts.size != 1) {
                return refuse("Every direct CorePrimitive render pass must retain exactly one uniform layout.")
            }
            val stepLayout = stepLayouts.single()
            when (stepLayout) {
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
                -> return diagnostic(
                    "unsupported.native-core-primitive.coverage-mask-direct-route",
                    "Coverage-mask programs require their dedicated prepared multi-pass route.",
                )
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
                -> {
                    if (stepAuthorities.any { authority ->
                            authority.analyticShapeUniformSeal != null ||
                                authority.analyticClipUniformSeal != null ||
                                authority.analyticIntersectionUniformSeal != null
                        }
                    ) return refuse("Gradient packets cannot retain a conflicting analytic uniform seal.")
                    val stepSeal = stepAuthorities.first().uniformSlabSeal
                        ?: return refuse("Gradient packet is missing its builder uniform slab seal.")
                    if (stepAuthorities.any { authority -> authority.uniformSlabSeal !== stepSeal }) {
                        return refuse("Gradient packets must share one exact builder uniform slab seal.")
                    }
                    stepUniformAuthorities[stepIndex] = StepUniformAuthority(
                        stepLayout,
                        stepSeal,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        stepSeal.plan,
                    )
                }
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                    return diagnostic(
                        "unsupported.native-core-primitive.no-bindings-direct-route",
                        "The no-bindings clip-stencil producer is not a direct CorePrimitive route.",
                    )
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 -> {
                    if (stepAuthorities.any { authority ->
                            authority.analyticShapeUniformSeal != null ||
                                authority.analyticClipUniformSeal != null ||
                                authority.analyticIntersectionUniformSeal != null
                        }
                    ) return refuse("Uniform32 packets cannot retain uniform64, uniform80, or uniform160 seals.")
                    val stepSeal = stepAuthorities.first().uniformSlabSeal
                        ?: return refuse("Direct CorePrimitive packet is missing its builder uniform32 slab seal.")
                    if (stepAuthorities.any { authority -> authority.uniformSlabSeal !== stepSeal }) {
                        return refuse("Direct CorePrimitive packets must share one exact builder uniform32 slab seal.")
                    }
                    stepUniformAuthorities[stepIndex] = StepUniformAuthority(
                        stepLayout,
                        stepSeal,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        stepSeal.plan,
                    )
                }
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 -> {
                    if (stepAuthorities.any { authority ->
                            authority.uniformSlabSeal != null ||
                                authority.analyticClipUniformSeal != null ||
                                authority.analyticIntersectionUniformSeal != null
                        }
                    ) return refuseShape("Uniform80 packets cannot retain uniform32, uniform64, or uniform160 seals.")
                    val seals = stepAuthorities.map { authority ->
                        authority.analyticShapeUniformSeal
                            ?: return refuseShape("Analytic shape packet is missing its uniform80 seal.")
                    }
                    if (seals.any { seal -> seal.plan !== seals.first().plan }) {
                        return refuseShape("Analytic shape packets must share one exact uniform80 slab plan.")
                    }
                    stepUniformAuthorities[stepIndex] = StepUniformAuthority(
                        stepLayout,
                        null,
                        seals,
                        emptyList(),
                        emptyList(),
                        seals.first().plan,
                    )
                }
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 -> {
                    if (stepAuthorities.any { authority ->
                            authority.uniformSlabSeal != null ||
                                authority.analyticShapeUniformSeal != null ||
                                authority.analyticIntersectionUniformSeal != null
                        }
                    ) return refuseAnalytic("Uniform64 packets cannot retain uniform32, uniform80, or uniform160 seals.")
                    val seals = stepAuthorities.map { authority ->
                        authority.analyticClipUniformSeal
                            ?: return refuseAnalytic("Analytic direct CorePrimitive packet is missing its uniform64 seal.")
                    }
                    if (seals.any { seal -> seal.plan !== seals.first().plan }) {
                        return refuseAnalytic("Analytic direct CorePrimitive packets must share one exact uniform64 slab plan.")
                    }
                    stepUniformAuthorities[stepIndex] = StepUniformAuthority(
                        stepLayout,
                        null,
                        emptyList(),
                        seals,
                        emptyList(),
                        seals.first().plan,
                    )
                }
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 -> {
                    if (stepAuthorities.any { authority ->
                            authority.uniformSlabSeal != null ||
                                authority.analyticShapeUniformSeal != null ||
                                authority.analyticClipUniformSeal != null
                        }
                    ) return refuseIntersection("Uniform160 packets cannot retain uniform32, uniform64, or uniform80 seals.")
                    val seals = stepAuthorities.map { authority ->
                        authority.analyticIntersectionUniformSeal
                            ?: return refuseIntersection("Analytic intersection packet is missing its uniform160 seal.")
                    }
                    if (seals.any { seal -> seal.plan !== seals.first().plan }) {
                        return refuseIntersection("Analytic intersection packets must share one exact uniform160 slab plan.")
                    }
                    stepUniformAuthorities[stepIndex] = StepUniformAuthority(
                        stepLayout,
                        null,
                        emptyList(),
                        emptyList(),
                        seals,
                        seals.first().plan,
                    )
                }
            }
        }
        val maxBufferSize = limits.maxBufferSize ?: return diagnostic(
            "unsupported.native-core-primitive.max-buffer-size-unavailable",
            "Direct CorePrimitive requires observed maxBufferSize.",
        )
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return diagnostic(
            "unsupported.native-core-primitive.dynamic-uniform-limit-unavailable",
            "Direct CorePrimitive requires the observed dynamic-uniform limit.",
        )
        fun layoutAcceptedIndices(
            layout: GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout,
        ): List<Int> = accepted.indices.filter { acceptedIndex ->
            packetAuthorities[acceptedIndex].structuralPipelineKey.uniformLayout == layout
        }
        val legacyUniformLayouts = setOf(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
        )
        val legacyUniformAcceptedIndices = accepted.indices.filter { acceptedIndex ->
            packetAuthorities[acceptedIndex].structuralPipelineKey.uniformLayout in legacyUniformLayouts
        }
        var hasDynamicLegacyUniformLayout = false
        var hasGradientLegacyUniformLayout = false
        var missingLegacyUniformSeal = false
        var firstLegacyUniformSeal: GPUCorePrimitiveUniformSlabSeal? = null
        var hasMultipleLegacyUniformSeals = false
        legacyUniformAcceptedIndices.forEach { acceptedIndex ->
            when (packetAuthorities[acceptedIndex].structuralPipelineKey.uniformLayout) {
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                    hasDynamicLegacyUniformLayout = true
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 ->
                    hasGradientLegacyUniformLayout = true
                else -> Unit
            }
            val seal = packetAuthorities[acceptedIndex].uniformSlabSeal
            if (seal == null) {
                missingLegacyUniformSeal = true
            } else if (firstLegacyUniformSeal == null) {
                firstLegacyUniformSeal = seal
            } else if (firstLegacyUniformSeal !== seal) {
                hasMultipleLegacyUniformSeals = true
            }
        }
        if (
            hasDynamicLegacyUniformLayout &&
            hasGradientLegacyUniformLayout &&
            !missingLegacyUniformSeal &&
            hasMultipleLegacyUniformSeals
        ) {
            return diagnostic(
                "unsupported.core_primitive.mixed_legacy_uniform_layouts",
                "Mixed legacy uniform layouts must share one exact frame slab seal.",
            )
        }
        val legacyUniformSlotByAcceptedIndex = IntArray(accepted.size) { -1 }
        val legacyUniformSeal = legacyUniformAcceptedIndices.firstOrNull()?.let { acceptedIndex ->
            packetAuthorities[acceptedIndex].uniformSlabSeal
        }
        if (legacyUniformAcceptedIndices.isNotEmpty()) {
            val seal = legacyUniformSeal ?: return refuse(
                "Direct CorePrimitive builder uniform slab seal contradicts current packet or limit authority.",
            )
            if (seal.plan.sourceLabel != "core-primitive-uniform-pass" ||
                seal.plan.deviceGeneration != context.deviceGeneration.value ||
                seal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                seal.plan.totalBytes > maxBufferSize || maxDynamicUniformBuffers < 1L ||
                seal.plan.slots.size != legacyUniformAcceptedIndices.size ||
                seal.drawCount != legacyUniformAcceptedIndices.size
            ) {
                return refuse("Direct CorePrimitive builder uniform slab seal contradicts current packet or limit authority.")
            }
            legacyUniformAcceptedIndices.forEachIndexed { slotIndex, acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val uniformBlock = entry.semantic.payloadRef.uniformBlock ?: return diagnostic(
                    "invalid.preflight.core_primitive_semantic_integrity",
                    "Core primitive packet authority contradicts its immutable semantic input.",
                )
                if (packetAuthorities[acceptedIndex].uniformSlabSeal !== seal ||
                    seal.commandIds[slotIndex] != entry.packet.commandIdValue ||
                    seal.plan.slots[slotIndex].slotLabel != "draw-${entry.packet.commandIdValue}" ||
                    !seal.hasExactPayload(slotIndex, entry.packet.commandIdValue, uniformBlock.bytes)
                ) {
                    return refuse("Direct CorePrimitive builder uniform slab seal contradicts current packet or limit authority.")
                }
                legacyUniformSlotByAcceptedIndex[acceptedIndex] = slotIndex
            }
        }
        val gradientAcceptedIndices = accepted.indices.filter { acceptedIndex ->
            packetAuthorities[acceptedIndex].structuralPipelineKey.uniformLayout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1
        }
        if (gradientAcceptedIndices.isNotEmpty()) {
            val seal = legacyUniformSeal ?: return diagnostic(
                "invalid.preflight.core_primitive_gradient_uniform_seal",
                "Gradient packets must retain one exact uniform slab seal.",
            )
            gradientAcceptedIndices.forEach { acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val authority = packetAuthorities[acceptedIndex]
                val indexAt = legacyUniformSlotByAcceptedIndex[acceptedIndex]
                val expectedHash = corePrimitiveGradientBindingLayoutHash(
                    authority.structuralPipelineKey.shader,
                ) ?: return diagnostic(
                    "invalid.preflight.core_primitive_gradient_uniform_seal",
                    "Gradient structural authority has no exact binding-layout identity.",
                )
                if (entry.packet.bindingLayoutHash != expectedHash ||
                    entry.route.lane != GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry ||
                    indexAt < 0 ||
                    entry.semantic.payloadRef.uniformBlock?.byteSize !=
                    corePrimitiveUniformByteSize(entry.semantic.material).toLong()
                ) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_gradient_uniform_seal",
                        "Gradient uniform slab payload contradicts the current packet or material ABI.",
                    )
                }
            }
        }
        val analyticGradientSteps = stepUniformAuthorities.filterValues { authority ->
            authority.layout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1
        }
        analyticGradientSteps.forEach { (stepIndex, authority) ->
            val seal = authority.uniformSlabSeal ?: return diagnostic(
                "invalid.preflight.core_primitive_gradient_uniform_seal",
                "Gradient packets must retain one exact uniform slab seal.",
            )
            val stepAcceptedIndices = stepAcceptedIndicesByIndex.getValue(stepIndex)
            if (authority.uniformPlan.sourceLabel != "core-primitive-uniform-pass" ||
                authority.uniformPlan.deviceGeneration != context.deviceGeneration.value ||
                authority.uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                authority.uniformPlan.totalBytes > maxBufferSize ||
                authority.uniformPlan.slots.size != stepAcceptedIndices.size ||
                seal.commandIds != stepAcceptedIndices.map { acceptedIndex -> accepted[acceptedIndex].packet.commandIdValue }
            ) {
                return diagnostic(
                    "invalid.preflight.core_primitive_gradient_uniform_seal",
                    "Gradient uniform slab plan contradicts the current packet or limit authority.",
                )
            }
            stepAcceptedIndices.forEachIndexed { indexAt, acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val expectedBytes = when (val built = buildCorePrimitiveGradientAnalyticShapeUniform(
                    entry.semantic,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(entry.semantic),
                )) {
                    is GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult.Accepted ->
                        built.bytes.map { byte -> byte.toInt() and 0xff }
                    is GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult.Refused ->
                        return diagnostic(built.code, built.message)
                }
                val expectedHash = corePrimitiveGradientBindingLayoutHash(
                    packetAuthorities[acceptedIndex].structuralPipelineKey.shader,
                ) ?: return diagnostic(
                    "invalid.preflight.core_primitive_gradient_uniform_seal",
                    "Gradient structural authority has no exact binding-layout identity.",
                )
                if (entry.packet.bindingLayoutHash != expectedHash ||
                    entry.route.lane != GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape ||
                    entry.semantic.payloadRef.uniformBlock?.byteSize !=
                    corePrimitiveUniformByteSize(entry.semantic.material).toLong() ||
                    entry.semantic.payloadRef.uniformBlock.bytes !=
                    corePrimitiveUniformBytes(entry.semantic.targetBounds, entry.semantic.material) ||
                    !seal.hasExactPayload(indexAt, entry.packet.commandIdValue, expectedBytes)
                ) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_gradient_uniform_seal",
                        "Gradient uniform slab payload contradicts the current packet or material ABI.",
                    )
                }
            }
        }
        val analyticShapeSteps = stepUniformAuthorities.values.filter {
            it.layout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                it.layout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
        }
        analyticShapeSteps.groupBy(StepUniformAuthority::layout).forEach { (analyticLayout, stepsForLayout) ->
            val uniform80AcceptedIndices = layoutAcceptedIndices(analyticLayout)
            val frame80Plan = stepsForLayout.first().uniformPlan
            val analyticUniformBytes = if (
                analyticLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) 128L else 80L
            val expectedSourceLabel = if (analyticUniformBytes == 128L)
                "core-primitive-analytic-drrect-uniform-pass" else "core-primitive-analytic-shape-uniform-pass"
            if (frame80Plan.sourceLabel != expectedSourceLabel ||
                frame80Plan.slots.size != uniform80AcceptedIndices.size ||
                stepsForLayout.flatMap { it.analyticShapeSeals }.any { seal -> seal.plan !== frame80Plan }
            ) {
                return refuseShape("Analytic shape packets must share one exact uniform80 slab plan.")
            }
            fun GPUPixelBounds.isContainedBy(outer: GPUPixelBounds): Boolean =
                left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom
            val shapeSeals = stepsForLayout.flatMap { it.analyticShapeSeals }
            uniform80AcceptedIndices.forEachIndexed { indexAt, acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val seal = shapeSeals[indexAt]
                val slot = frame80Plan.slots[indexAt]
                val rebuilt = buildCorePrimitiveAnalyticShapeUniform(
                    entry.semantic,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(entry.semantic),
                )
                val expectedBytes = when (rebuilt) {
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> rebuilt.bytes
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> return refuseShape(
                        "Analytic shape semantic can no longer be recomposed into the sealed uniform80 ABI.",
                    )
                }
                val renderScissor = entry.route.renderScissor ?: return refuseShape(
                    "Analytic shape route is missing its exact non-empty render scissor.",
                )
                val exactRange = try {
                    Math.addExact(slot.alignedOffset, analyticUniformBytes) <= frame80Plan.totalBytes
                } catch (_: ArithmeticException) {
                    false
                }
                if (entry.route.lane != GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape ||
                    renderScissor.isEmpty || seal.renderScissor != renderScissor ||
                    !renderScissor.isContainedBy(entry.semantic.targetBounds) ||
                    !renderScissor.isContainedBy(entry.semantic.scissorBounds) ||
                    !seal.hasExactSemantic(entry.semantic) ||
                    seal.slotIndex != indexAt || seal.commandId != entry.packet.commandIdValue ||
                    seal.packetId != entry.packet.packetId ||
                    seal.structuralPipelineKey != packetAuthorities[acceptedIndex].structuralPipelineKey ||
                    seal.structuralPipelineKey.shader != (if (analyticUniformBytes == 128L)
                        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect
                    else GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape) ||
                    seal.renderPipelineKey != packetAuthorities[acceptedIndex].renderPipelineKey ||
                    seal.bindingLayoutHash != (if (analyticUniformBytes == 128L)
                        CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
                    else CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH) ||
                    entry.packet.bindingLayoutHash != seal.bindingLayoutHash ||
                    seal.resourceGeneration != entry.packet.resourceGeneration ||
                    seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                    seal.deviceGeneration != context.deviceGeneration.value ||
                    seal.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                    seal.payloadBytes != analyticUniformBytes || slot.payloadBytes != analyticUniformBytes ||
                    slot.slotLabel != (if (analyticUniformBytes == 128L)
                        "analytic-drrect-draw-${entry.packet.commandIdValue}"
                    else "analytic-shape-draw-${entry.packet.commandIdValue}") ||
                    seal.alignedOffset != slot.alignedOffset || slot.alignedOffset > UInt.MAX_VALUE.toLong() ||
                    !exactRange || !seal.hasExactPayload(expectedBytes)
                ) {
                    return refuseShape(
                        "Analytic shape uniform80 seal contradicts packet, semantic, route, layout, or generation authority.",
                    )
                }
            }
        }
        val analyticClipSteps = stepUniformAuthorities.values.filter {
            it.layout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
        }
        val uniform64AcceptedIndices = layoutAcceptedIndices(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1,
        )
        val analytic64PackedBytes = if (analyticClipSteps.isNotEmpty()) {
            val frame64Plan = analyticClipSteps.first().uniformPlan
            if (frame64Plan.sourceLabel != "core-primitive-analytic-clip-uniform-pass" ||
                frame64Plan.slots.size != uniform64AcceptedIndices.size ||
                analyticClipSteps.flatMap { it.analyticClipSeals }.any { seal -> seal.plan !== frame64Plan }
            ) {
                return refuseAnalytic("Analytic direct CorePrimitive packets must share one exact uniform64 slab plan.")
            }
            val exactPayloads = mutableListOf<GPUUniformSlabPayload>()
            val clipSeals = analyticClipSteps.flatMap { it.analyticClipSeals }
            uniform64AcceptedIndices.forEachIndexed { indexAt, acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val packetClip = corePrimitiveAnalyticClipPacketAuthority(
                    entry.packet,
                    entry.semantic.targetBounds,
                )
                    ?: return refuseAnalytic("Analytic direct CorePrimitive clip authority is no longer canonical.")
                val expectedClip = packetClip.clip
                val seal = clipSeals[indexAt]
                val slot = frame64Plan.slots[indexAt]
                val expectedBytes = corePrimitiveAnalyticClipUniformBytes(entry.semantic, expectedClip)
                val exactRange = try {
                    Math.addExact(slot.alignedOffset, 64L) <= frame64Plan.totalBytes
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
                    seal.structuralPipelineKey != packetAuthorities[acceptedIndex].structuralPipelineKey ||
                    seal.renderPipelineKey != packetAuthorities[acceptedIndex].renderPipelineKey ||
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
            if (!frame64Plan.hasExactPayloads(
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
            if (frame64Plan.totalBytes > Int.MAX_VALUE.toLong()) {
                return refuseAnalytic(
                    "Analytic direct CorePrimitive uniform64 slab exceeds the host-addressable packed size.",
                )
            }
            try {
                ByteArray(frame64Plan.totalBytes.toInt()).also { packed ->
                    clipSeals.forEach { seal ->
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
        val analyticIntersectionSteps = stepUniformAuthorities.values.filter {
            it.layout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1
        }
        val uniform160AcceptedIndices = layoutAcceptedIndices(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
        )
        val analytic160PackedBytes = if (analyticIntersectionSteps.isNotEmpty()) {
            val frame160Plan = analyticIntersectionSteps.first().uniformPlan
            if (frame160Plan.sourceLabel != "core-primitive-analytic-intersection-uniform-pass" ||
                frame160Plan.slots.size != uniform160AcceptedIndices.size ||
                analyticIntersectionSteps.flatMap { it.analyticIntersectionSeals }
                    .any { seal -> seal.plan !== frame160Plan }
            ) {
                return refuseIntersection("Analytic intersection packets must share one exact uniform160 slab plan.")
            }
            val exactPayloads = mutableListOf<GPUUniformSlabPayload>()
            val intersectionSeals = analyticIntersectionSteps.flatMap { it.analyticIntersectionSeals }
            uniform160AcceptedIndices.forEachIndexed { indexAt, acceptedIndex ->
                val entry = accepted[acceptedIndex]
                val packetClip = corePrimitiveAnalyticIntersectionPacketAuthority(
                    entry.packet,
                    entry.semantic.targetBounds,
                ) ?: return refuseIntersection("Analytic intersection authority is no longer canonical.")
                val expectedClip = packetClip.clip
                val seal = intersectionSeals[indexAt]
                val slot = frame160Plan.slots[indexAt]
                val expectedBytes = corePrimitiveAnalyticIntersectionUniformBytes(entry.semantic, expectedClip)
                val exactRange = try {
                    Math.addExact(slot.alignedOffset, 160L) <= frame160Plan.totalBytes
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
                    seal.structuralPipelineKey != packetAuthorities[acceptedIndex].structuralPipelineKey ||
                    seal.renderPipelineKey != packetAuthorities[acceptedIndex].renderPipelineKey ||
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
            if (!frame160Plan.hasExactPayloads(
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
            if (frame160Plan.totalBytes > Int.MAX_VALUE.toLong()) {
                return refuseIntersection("Analytic intersection uniform160 slab exceeds the host-addressable packed size.")
            }
            try {
                ByteArray(frame160Plan.totalBytes.toInt()).also { packed ->
                    intersectionSeals.forEach { seal ->
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
        val uniformPreparationByResource = uniformSlabs.associateBy { it.resource }
        val stepUniformResourceByStepIndex = acceptedStepIndexes.associateWith { stepIndex ->
            val entry = accepted.firstOrNull { it.sourceStepIndex == stepIndex }
                ?: return refuse("Direct CorePrimitive step is missing its accepted packet authority.")
            val uniformUse = entry.render.resourceUses.filter { use ->
                use.resource !in preparedImageResourceRefs
            }.singleOrNull {
                it.role == GPUFrameResourceRole.UniformData
            } ?: return refuse("Direct CorePrimitive render must retain its exact uniform slab use.")
            uniformUse.resource
        }
        acceptedStepIndexes.forEach { stepIndex ->
            val stepAuthority = stepUniformAuthorities.getValue(stepIndex)
            val stepPreparation = uniformPreparationByResource[
                stepUniformResourceByStepIndex.getValue(stepIndex)
            ] ?: return refuse("Direct CorePrimitive render references an undeclared uniform slab.")
            val descriptor = stepPreparation.descriptor as? GPUFrameBufferDescriptor
                ?: return refuse("Direct CorePrimitive uniform slab descriptor is missing.")
            if (descriptor.byteSize != stepAuthority.uniformPlan.totalBytes ||
                descriptor.alignmentBytes != stepAuthority.uniformPlan.alignmentBytes ||
                stepPreparation.byteSize != stepAuthority.uniformPlan.totalBytes ||
                stepPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.Uniform,
                ) || stepPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
            ) {
                val message = "Direct CorePrimitive uniform slab descriptor, size, alignment, usages, or lifetime is not exact."
                return if (stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                    stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
                ) {
                    refuseShape(message)
                } else {
                    refuse(message)
                }
            }
            if (context.resourceGenerations[stepPreparation.resource] == null) {
                return refuse("Direct CorePrimitive shared slabs require current resource-generation evidence.")
            }
        }
        val exactVertexUse = GPUFrameResourceUse(
            vertex.resource,
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceUsage.Vertex,
            GPUFrameResourceLifetime.FrameLocal,
            write = false,
        )
        val exactIndexUse = GPUFrameResourceUse(
            index.resource,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceUsage.Index,
            GPUFrameResourceLifetime.FrameLocal,
            write = false,
        )
        if (directUsesByRender.any { (render, uses) ->
                if (render in coreRenders) {
                    val stepIndex = accepted.first { it.render === render }.sourceStepIndex
                    val expectedUniformUse = GPUFrameResourceUse(
                        stepUniformResourceByStepIndex.getValue(stepIndex),
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceUsage.Uniform,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    )
                    uses.toSet() != setOf(exactVertexUse, exactIndexUse, expectedUniformUse)
                } else {
                    uses.isNotEmpty() && !exactPreparedSurfaceMixedBoundary
                }
            }
        ) {
            return refuse(
                "Every direct pass must read exactly the shared vertex/index slabs and its own uniform slab; " +
                    "non-direct draws may read none.",
            )
        }
        // The direct lane admits per-key structural pipelines per render scope (Graphite
        // DrawPass fFullPipelines): every packet's render pipeline key must match its own
        // structural key's stable authority, and every scope owns one exact uniform layout.
        // The stable key is derived once per distinct structural key, never once per draw.
        // A two-render dst-copy frame retains one prepared pass seal per render scope (the
        // producer and consuming passes each seal over their own packets' structural keys).
        val uniformSlabSealByStep: Map<Int, GPUCorePrimitiveUniformSlabSeal?> =
            acceptedStepIndexes.associateWith { stepIndex ->
                val stepAuthority = stepUniformAuthorities.getValue(stepIndex)
                if (stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ||
                    stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 ||
                    stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1
                ) {
                    if (singleStepFrame || exactPreparedSurfaceMixedBoundary) {
                        stepAuthority.uniformSlabSeal
                    } else {
                        stepAuthority.uniformSlabSeal?.let { frameSeal ->
                            sliceUniformSlabSealToCommands(
                                frameSeal,
                                stepCommandIdsByIndex.getValue(stepIndex),
                            )
                        }
                    }
                } else {
                    null
                }
            }
        val preparedPassSealsByStep = buildMap<Int, GPUCorePrimitiveDirectPreparedPassAuthority> {
            acceptedStepIndexes.forEach { stepIndex ->
                val stepAcceptedIndices = stepAcceptedIndicesByIndex.getValue(stepIndex)
                val stepStructuralKeys = stepAcceptedIndices.map { packetAuthorities[it].structuralPipelineKey }
                val stepMultiKey = stepStructuralKeys.distinct().size > 1
                val stepAuthority = stepUniformAuthorities.getValue(stepIndex)
                val stepAnalyticShapeSeals = when {
                    stepAuthority.layout !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 &&
                        stepAuthority.layout !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                        emptyList()
                    analyticShapeSteps.count { it.layout == stepAuthority.layout } <= 1 ->
                        stepAuthority.analyticShapeSeals
                    else -> sliceAnalyticShapeUniformSealsToCommands(
                        stepAuthority.analyticShapeSeals,
                        stepCommandIdsByIndex.getValue(stepIndex),
                        stepAcceptedIndices.associate { accepted[it].packet.commandIdValue to accepted[it].semantic },
                    )
                }
                // When a frame owns multiple uniform64/uniform160 steps (the
                // analytic-clip split), each step's seals are rebased to a zero-based sliced slab
                // so the per-render-scope run materializer binds exact offsets. A single-step
                // frame retains its frame-level seals unchanged.
                val stepAnalyticClipSeals = when {
                    stepAuthority.layout !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                        emptyList()
                    analyticClipSteps.size <= 1 -> stepAuthority.analyticClipSeals
                    else -> sliceAnalyticClipUniformSealsToCommands(
                        stepAuthority.analyticClipSeals,
                        stepCommandIdsByIndex.getValue(stepIndex),
                    )
                }
                val stepAnalyticIntersectionSeals = when {
                    stepAuthority.layout !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                        emptyList()
                    analyticIntersectionSteps.size <= 1 -> stepAuthority.analyticIntersectionSeals
                    else -> sliceAnalyticIntersectionUniformSealsToCommands(
                        stepAuthority.analyticIntersectionSeals,
                        stepCommandIdsByIndex.getValue(stepIndex),
                    )
                }
                val stepSeal = if (stepMultiKey) {
                    when (stepAuthority.layout) {
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                            GPUCorePrimitiveMultiKeyDirectPreparedPassSeal(
                                structuralPipelineKeys = stepStructuralKeys.distinct(),
                                uniformSlabSeal = requireNotNull(uniformSlabSealByStep.getValue(stepIndex)),
                            )
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                        GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                            try {
                                GPUCorePrimitiveMultiKeyDirectPreparedPassSeal.analyticShape(
                                    structuralPipelineKeys = stepStructuralKeys.distinct(),
                                    analyticShapeUniformSeals = stepAnalyticShapeSeals,
                                )
                            } catch (_: Throwable) {
                                return refuseShape(
                                    "Analytic shape packet ranges cannot form one exact packed multi-key uniform80 slab.",
                                )
                            }
                        else -> return refuse(
                            "Multi-key direct CorePrimitive passes require one exact uniform32 or analytic-shape uniform80 layout.",
                        )
                    }
                } else if (
                    stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                    stepAuthority.layout ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
                ) {
                    try {
                        GPUCorePrimitiveDirectPreparedPassSeal.analyticShape(
                            structuralPipelineKey = stepStructuralKeys.first(),
                            analyticShapeUniformSeals = stepAnalyticShapeSeals,
                        )
                    } catch (_: Throwable) {
                        return refuseShape("Analytic shape packet ranges cannot form one exact packed uniform80 slab.")
                    }
                } else {
                    GPUCorePrimitiveDirectPreparedPassSeal(
                        structuralPipelineKey = stepStructuralKeys.first(),
                        uniformSlabSeal = uniformSlabSealByStep.getValue(stepIndex),
                        analyticClipUniformSeals = stepAnalyticClipSeals,
                        analyticClipPackedBytes = if (stepAnalyticClipSeals.isEmpty()) {
                            null
                        } else if (analyticClipSteps.size <= 1) {
                            analytic64PackedBytes
                        } else {
                            try {
                                ByteArray(stepAnalyticClipSeals.first().plan.totalBytes.toInt()).also { packed ->
                                    stepAnalyticClipSeals.forEach { seal ->
                                        seal.copyPayloadInto(packed, seal.alignedOffset.toInt())
                                    }
                                }
                            } catch (_: Throwable) {
                                return refuseAnalytic(
                                    "Analytic direct CorePrimitive uniform64 packet ranges cannot form one exact packed slab.",
                                )
                            }
                        },
                        analyticIntersectionUniformSeals = stepAnalyticIntersectionSeals,
                        analyticIntersectionPackedBytes = if (stepAnalyticIntersectionSeals.isEmpty()) {
                            null
                        } else if (analyticIntersectionSteps.size <= 1) {
                            analytic160PackedBytes
                        } else {
                            try {
                                ByteArray(stepAnalyticIntersectionSeals.first().plan.totalBytes.toInt()).also { packed ->
                                    stepAnalyticIntersectionSeals.forEach { seal ->
                                        seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
                                    }
                                }
                            } catch (_: Throwable) {
                                return refuseIntersection(
                                    "Analytic intersection uniform160 packet ranges cannot form one exact packed slab.",
                                )
                            }
                        },
                    )
                }
                put(stepIndex, stepSeal)
            }
        }

        retainAcceptedRoutes(
            accepted.associate { entry ->
                GPUCorePrimitiveDirectNativeFrameRouteKey(entry.sourceStepIndex, entry.packet.packetId) to
                    entry.route
            },
            preparedPassSealsByStep,
        )
        return null
    }

    private fun validateCorePrimitiveRenderAuthority(framePlan: GPUFramePlan): GPUDiagnostic? {
        val invalidRender = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .firstOrNull { render ->
                render.samplePlan != GPUSamplePlan.SingleSampleFrame &&
                    render.samplePlan != GPUSamplePlan.MultisampleFrame(4) &&
                    render.drawPackets.any { packet ->
                        packet.renderStepId.value == CORE_PRIMITIVE_RENDER_STEP_IDENTITY
                    }
            }
        return invalidRender?.let {
            diagnostic(
                "invalid.preflight.core_primitive_render_authority",
                "Core primitive render passes require the canonical single-sample or exact 4x plan.",
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
                if (request.key.targetGeneration != context.targetGeneration &&
                    request.key.targetGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
                ) {
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
                val pathDepthUse = render.resourceUses.singleOrNull { use ->
                    use.role == GPUFrameResourceRole.PathDepthStencil
                }
                val exactPathDepthAuthority = render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                    pathDepthUse != null &&
                    request.key.depthStencilAttachment?.value == pathDepthUse.resource.value &&
                    render.drawPackets.all { packet ->
                        packet.role == GPUDrawPacketRole.PathStencilProducer ||
                            packet.role == GPUDrawPacketRole.PathStencilCover ||
                            packet.role == GPUDrawPacketRole.Shading &&
                            packet.corePrimitivePreparedAuthority?.structuralPipelineKey?.let { structural ->
                                structural.sampleCount == 4 &&
                                    structural.depthStencil == corePrimitiveDirectPathDepthStencilState()
                            } == true
                    }
                val clipDepthUse = render.resourceUses.singleOrNull { use ->
                    use.role == GPUFrameResourceRole.ClipDepthStencil
                }
                val clipPacket = render.drawPackets.singleOrNull()
                val clipCandidate = clipPacket?.corePrimitiveClipStencilPreparedCandidate
                val exactClipDepthAuthority =
                    render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
                        clipDepthUse != null && clipCandidate?.attachmentSampleCount == 4 &&
                        clipCandidate.attachmentLogicalReference == clipDepthUse.resource.value &&
                        request.key.depthStencilAttachment?.value == clipDepthUse.resource.value &&
                        when (clipPacket.role) {
                            GPUDrawPacketRole.StencilProducer ->
                                clipDepthUse.write && render.depthStencilLoadStore is
                                    org.graphiks.kanvas.gpu.renderer.recording
                                        .GPUDepthStencilLoadStorePlan.WritableStencil
                            GPUDrawPacketRole.Shading ->
                                !clipDepthUse.write && render.depthStencilLoadStore ==
                                    org.graphiks.kanvas.gpu.renderer.recording
                                        .GPUDepthStencilLoadStorePlan.ReadOnlyKeep
                            else -> false
                        }
                if (request.key.depthStencilAttachment != null &&
                    !exactPathDepthAuthority && !exactClipDepthAuthority
                ) {
                    return diagnostic(
                        "unsupported.msaa.continuation_depth_stencil_unavailable",
                        "A prepared MSAA depth/stencil continuation requires an exact sealed Path or clip-stencil 4x scope.",
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

    private data class CorePrimitiveSemanticEnvelopeEntry(
        val packet: GPUDrawPacket,
        val semantic: GPUDrawSemanticPayload.CorePrimitive,
    )

    private class CorePrimitiveSemanticEnvelopeAuthority(
        entriesByPacket: Map<GPUDrawPacket, CorePrimitiveSemanticEnvelopeEntry>,
    ) {
        private val entriesByPacket = entriesByPacket.toMap()

        fun matches(
            packet: GPUDrawPacket,
            semantic: GPUDrawSemanticPayload.CorePrimitive,
        ): Boolean = entriesByPacket[packet]?.let { entry ->
            entry.packet === packet && entry.semantic === semantic
        } == true
    }

    private fun validateCorePrimitiveCoverageSampleMatrix(
        framePlan: GPUFramePlan,
        semanticEnvelopeAuthority: CorePrimitiveSemanticEnvelopeAuthority,
    ): GPUDiagnostic? {
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
            render.drawPackets.forEach { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    ?: return@forEach
                if (!semanticEnvelopeAuthority.matches(packet, semantic)) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_semantic_integrity",
                        "Core primitive packet authority contradicts its immutable semantic input.",
                    )
                }
                when (
                    val authority = validateCorePrimitiveCoverageSampleAuthority(
                        geometry = semantic.geometry,
                        coverageMode = semantic.coverageMode,
                        targetBounds = semantic.targetBounds,
                        samplePlan = render.samplePlan,
                        capabilities = capabilities,
                    )
                ) {
                    GPUCorePrimitiveCoverageSampleAuthority.Accepted -> Unit
                    is GPUCorePrimitiveCoverageSampleAuthority.Refused -> return diagnostic(
                        authority.code,
                        authority.message,
                        mapOf(
                            "sourceStepIndex" to sourceStepIndex.toString(),
                            "packetId" to packet.packetId.value,
                            "commandId" to packet.commandIdValue.toString(),
                            "coverageMode" to semantic.coverageMode.name,
                            "samplePlan" to render.samplePlan.specializationKey,
                        ),
                    )
                }
            }
        }
        return null
    }

    /**
     * Validates every CorePrimitive semantic envelope before any route matrix can select a later
     * packet refusal. This keeps missing or forged authority globally prior to coverage/sample
     * support diagnostics and remains pure: no resource, native-registry, or ticket work occurs.
     */
    private fun validateCorePrimitiveSemanticEnvelopes(
        framePlan: GPUFramePlan,
    ): GPUDiagnostic? {
        framePlan.steps.forEach { step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@forEach
            render.drawPackets.forEach { packet ->
                val semantic = packet.semanticPayload
                val corePrimitiveStep = corePrimitiveSemanticStep(packet.renderStepId.value)
                if (corePrimitiveStep != null && semantic == null) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_semantic_payload_missing",
                        "Executable core primitive packets require their gathered semantic payload.",
                    )
                }
                if (corePrimitiveStep != null && semantic !is GPUDrawSemanticPayload.CorePrimitive) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_semantic_integrity",
                        "Core primitive packet authority contradicts its immutable semantic input.",
                    )
                }
                if (semantic is GPUDrawSemanticPayload.CorePrimitive &&
                    !hasCorePrimitiveSemanticEnvelopeIntegrity(packet, semantic)
                ) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_semantic_integrity",
                        "Core primitive packet authority contradicts its immutable semantic input.",
                    )
                }
            }
        }
        return null
    }

    private fun captureCorePrimitiveSemanticEnvelopeAuthority(
        framePlan: GPUFramePlan,
    ): CorePrimitiveSemanticEnvelopeAuthority {
        val entries = linkedMapOf<GPUDrawPacket, CorePrimitiveSemanticEnvelopeEntry>()
        framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().forEach { render ->
            render.drawPackets.forEach { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return@forEach
                entries[packet] = CorePrimitiveSemanticEnvelopeEntry(packet, semantic)
            }
        }
        return CorePrimitiveSemanticEnvelopeAuthority(entries)
    }

    private enum class CorePrimitiveSemanticStep {
        Generic,
        RadialGradient,
        SweepGradient,
    }

    private fun corePrimitiveSemanticStep(
        renderStepIdentity: String,
    ): CorePrimitiveSemanticStep? = when (renderStepIdentity) {
        CORE_PRIMITIVE_RENDER_STEP_IDENTITY -> CorePrimitiveSemanticStep.Generic
        "radial.gradient.fill" -> CorePrimitiveSemanticStep.RadialGradient
        "sweep.gradient.fill" -> CorePrimitiveSemanticStep.SweepGradient
        else -> null
    }

    private fun hasCorePrimitiveSemanticEnvelopeIntegrity(
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
    ): Boolean {
        val clipExecutionPlan = packet.clipExecutionPlan ?: return false
        val packetStepMatchesMaterial = when (corePrimitiveSemanticStep(packet.renderStepId.value)) {
            CorePrimitiveSemanticStep.Generic -> true
            CorePrimitiveSemanticStep.RadialGradient ->
                semantic.material is GPUCorePrimitiveMaterialPayload.RadialGradient
            CorePrimitiveSemanticStep.SweepGradient ->
                semantic.material is GPUCorePrimitiveMaterialPayload.SweepGradient
            null -> false
        }
        return packetStepMatchesMaterial &&
            semantic.payloadRef.renderStepIdentity == CORE_PRIMITIVE_RENDER_STEP_IDENTITY &&
            packet.commandIdValue == semantic.payloadRef.commandIdValue &&
            (semantic.analysisRecordId == null ||
                packet.analysisRecordId == semantic.analysisRecordId) &&
            packet.uniformSlot == semantic.payloadRef.uniformSlot &&
            packet.clipCoveragePlan == semantic.clipCoveragePlan &&
            packet.blendPlan?.canonicalIdentity() == semantic.blendPlanIdentity &&
            packet.frameProvenance == semantic.frameProvenance &&
            semantic.clipExecutionPlanIdentity == clipExecutionPlan.canonicalIdentity() &&
            semantic.hasStructuralIntegrity()
    }

    private fun validateSemanticPayload(
        framePlan: GPUFramePlan,
        semanticEnvelopeAuthority: CorePrimitiveSemanticEnvelopeAuthority,
        sourceStepIndex: Int,
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
        clipStencilPreparedRouteSeal:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        coverageMaskPreparedRouteSeal:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
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
                else -> corePrimitiveSemanticStep(packet.renderStepId.value)?.let {
                    diagnostic(
                        "invalid.preflight.core_primitive_semantic_payload_missing",
                        "Executable core primitive packets require their gathered semantic payload.",
                    )
                }
            }
        }
        return when (semantic) {
            is GPUDrawSemanticPayload.SolidRect -> validateSolidRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.CorePrimitive ->
                validateCorePrimitiveSemanticPayload(
                    framePlan,
                    sourceStepIndex,
                    render,
                    packet,
                    semantic,
                    semanticEnvelopeAuthority,
                    clipStencilPreparedRouteSeal,
                    coverageMaskPreparedRouteSeal,
                )
            is GPUDrawSemanticPayload.RegisteredUniformRect ->
                validateRegisteredUniformRectSemanticPayload(render, packet, semantic)
            is GPUDrawSemanticPayload.SeparableBlurRect ->
                validateSeparableBlurRectSemanticPayload(packet, semantic)
            is GPUDrawSemanticPayload.MaskBlur ->
                validateMaskBlurSemanticPayload(framePlan, sourceStepIndex, render, packet, semantic)
            is GPUDrawSemanticPayload.SampledImage ->
                if (!hasExactPreparedSurfaceMixedNativeBoundary(framePlan)) {
                    diagnostic(
                        "unsupported.preflight.sampled_image_unmaterialized",
                        "Prepared sampled-image semantics have no executable native materialization route.",
                    )
                } else {
                    validatePreparedSampledImageScissor(packet, semantic)
                }
            is GPUDrawSemanticPayload.TextA8 ->
                if (hasExactPreparedSurfaceMixedNativeBoundary(framePlan)) {
                    null
                } else {
                    diagnostic(
                        GPUPreparedTextPreflightRefusalCodes.PREPARED_TEXT_UNMATERIALIZED,
                        "Prepared text semantics have no executable native materialization route; " +
                            "the sealed prepared-surface native boundary is required.",
                    )
                }
            is GPUDrawSemanticPayload.ColorGlyph ->
                if (hasExactPreparedSurfaceMixedNativeBoundary(framePlan)) {
                    null
                } else {
                    diagnostic(
                        GPUPreparedTextPreflightRefusalCodes.PREPARED_TEXT_UNMATERIALIZED,
                        "Prepared color-glyph semantics have no executable native materialization route; " +
                        "the sealed prepared-surface native boundary is required.",
                    )
                }
            is GPUDrawSemanticPayload.Vertices ->
                if (hasExactPreparedSurfaceMixedNativeBoundary(framePlan)) {
                    null
                } else {
                    diagnostic(
                        PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE,
                        "Prepared vertices semantics have no executable native materialization route; " +
                            "the sealed prepared-surface native boundary is required.",
                    )
                }
        }
    }

    private fun validatePreparedSampledImageScissor(
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.SampledImage,
    ): GPUDiagnostic? = preparedImageClipPreflightDiagnostic(
        validation = packet.validatePreparedImageClipAuthority(
            semantic.targetBounds,
            semantic.scissorBounds,
        ),
        hasScissor = semantic.scissorBounds != semantic.targetBounds,
    )

    private fun hasExactPreparedSurfaceMixedNativeBoundary(
        framePlan: GPUFramePlan,
    ): Boolean {
        if (nativeBoundary?.supportsPreparedSurfaceMixedSealed != true ||
            framePlan.steps.any { step ->
                step is GPUFrameStep.CopyAsDrawMaterializationStep
            }
        ) {
            return false
        }
        val destinationCopies =
            framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        if (destinationCopies.isNotEmpty()) {
            val packetsById = framePlan.steps
                .filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                .associateBy(GPUDrawPacket::packetId)
            val destinationPreparedPacketIds = packetsById.values
                .filter { packet ->
                    (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                        packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                        packet.semanticPayload is GPUDrawSemanticPayload.Vertices) &&
                        packet.blendPlan?.destinationReadRequirement ==
                        org.graphiks.kanvas.gpu.renderer.passes
                            .GPUBlendDestinationReadRequirement.DestinationTextureRequired
                }
                .map(GPUDrawPacket::packetId)
                .toSet()
            val copyConsumerPacketIds = destinationCopies
                .flatMap(GPUFrameStep.CopyDestinationStep::consumers)
                .map { consumer -> consumer.packetId }
            if (destinationPreparedPacketIds.isEmpty() ||
                copyConsumerPacketIds.size != copyConsumerPacketIds.distinct().size ||
                copyConsumerPacketIds.toSet() != destinationPreparedPacketIds ||
                destinationCopies.map(GPUFrameStep.CopyDestinationStep::snapshot)
                    .distinct().size != destinationCopies.size
            ) {
                return false
            }
        }
        val renderSemantics = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .filterNot { render ->
                render.drawPackets.isNotEmpty() &&
                    render.drawPackets.all { packet ->
                        packet.role == GPUDrawPacketRole.ClipProducer ||
                            packet.role == GPUDrawPacketRole.StencilProducer
                    }
            }
            .map { render -> render.drawPackets.map(GPUDrawPacket::semanticPayload) }
        val supportedMixedSemanticTypes = setOf("CorePrimitive", "SampledImage")
        if (renderSemantics.isEmpty() ||
            renderSemantics.any { run ->
                val semanticTypes = run.filterNotNull()
                    .map(GPUDrawSemanticPayload::canonicalType)
                    .toSet()
                run.isEmpty() ||
                    run.any { it == null } ||
                    (semanticTypes.size != 1 && semanticTypes != supportedMixedSemanticTypes)
            }
        ) {
            return false
        }
        if (framePlan.steps
                .filterIsInstance<GPUFrameStep.RenderPassStep>()
                .any { render ->
                    val preparedTextPacketIds = render.drawPackets
                        .filter { packet ->
                            packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                                packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
                        }
                        .map(GPUDrawPacket::packetId)
                        .toSet()
                    preparedTextPacketIds.isNotEmpty() &&
                        render.preparedTextBindingsByPacketId.keys != preparedTextPacketIds
                }
        ) {
            return false
        }
        val semanticTypes = renderSemantics
            .flatten()
            .filterNotNull()
            .map(GPUDrawSemanticPayload::canonicalType)
            .toSet()
        val hasLayerCompositeSteps =
            framePlan.steps.any { it is GPUFrameStep.LayerCompositeRenderStep }
        return (hasLayerCompositeSteps ||
            semanticTypes.any {
                it == "SampledImage" || it == "TextA8" || it == "ColorGlyph" || it == "Vertices"
            }) &&
            semanticTypes.all {
                it == "CorePrimitive" ||
                    it == "SampledImage" ||
                    it == "TextA8" ||
                    it == "ColorGlyph" ||
                    it == "Vertices"
            }
    }

    private fun validateCorePrimitiveSemanticPayload(
        framePlan: GPUFramePlan,
        sourceStepIndex: Int,
        render: GPUFrameStep.RenderPassStep,
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        semanticEnvelopeAuthority: CorePrimitiveSemanticEnvelopeAuthority,
        clipStencilPreparedRouteSeal:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        coverageMaskPreparedRouteSeal:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    ): GPUDiagnostic? {
        if (!semanticEnvelopeAuthority.matches(packet, semantic)) {
            return diagnostic(
                "invalid.preflight.core_primitive_semantic_integrity",
                "Core primitive packet authority contradicts its immutable semantic input.",
            )
        }
        val supportedPathClip = packet.hasSupportedCorePrimitivePathClip()
        if ((packet.role == GPUDrawPacketRole.PathStencilProducer ||
                packet.role == GPUDrawPacketRole.PathStencilCover) &&
            !supportedPathClip
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_path_stencil",
                "Path stencil accepts analytic coverage only on its authenticated cover role.",
            )
        }
        val blendPlan = requireNotNull(packet.blendPlan)
        val clipExecutionPlan = requireNotNull(packet.clipExecutionPlan)
        val preparedAuthority = packet.corePrimitivePreparedAuthority
        val targetPreparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.resource == render.target }
        val targetPreparation = targetPreparations.singleOrNull()
        val targetDescriptor = targetPreparation?.descriptor as? GPUFrameTextureDescriptor
            ?: if (render.target.value in framePlan.steps
                    .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
                    .map(GPUFrameStep.LayerTargetPrepareStep::targetLabel)
            ) {
                // Layer targets materialize from the pooled single-sample RGBA8 attachment.
                GPUFrameTextureDescriptor(
                    GPUPixelBounds(0, 0, 1, 1),
                    GPUColorFormat("rgba8unorm"),
                    1,
                )
            } else {
                return diagnostic(
                    "invalid.preflight.core_primitive_target_authority",
                    "Core primitive target preparation must be one exact texture.",
                )
            }
        val targetStructuralColorFormat = try {
            targetDescriptor.format.corePrimitiveStructuralColorFormat()
        } catch (_: IllegalArgumentException) {
            null
        } ?: return diagnostic(
            "invalid.preflight.core_primitive_target_authority",
            "Core primitive target preparation must use one supported scene target format.",
        )
        if (preparedAuthority?.structuralPipelineKey?.colorFormat != null &&
            preparedAuthority.structuralPipelineKey.colorFormat != targetStructuralColorFormat
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_target_authority",
                "Core primitive target preparation contradicts its structural pipeline format.",
            )
        }
        val retainedCoverageMaskScope = when (coverageMaskPreparedRouteSeal) {
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty -> null
            is GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route ->
                coverageMaskPreparedRouteSeal.retainedFor(
                    sourceStepIndex,
                    render.drawPackets.map(GPUDrawPacket::packetId),
                )
        }
        val coverageMaskConsumerScope = retainedCoverageMaskScope?.units()
            ?.filterIsInstance<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer>()
            ?.singleOrNull { it.packetId == packet.packetId }
        val coverageMaskConsumerSlot = coverageMaskConsumerScope?.let { scope ->
            val slabSeal = scope.slabAuthority.uniformSlabSeal
            val consumerIndex = scope.uniformSlice.slotIndex - slabSeal.producerSlots.size
            if (consumerIndex !in slabSeal.consumerSlots.indices) {
                return diagnostic(
                    "invalid.preflight.core_primitive_packet_authority",
                    "Coverage-mask consumer slot index is outside its sealed partition.",
                )
            }
            slabSeal.consumerSlots[consumerIndex].also { slot ->
                if (slot.slotIndex != scope.uniformSlice.slotIndex ||
                    slot.packetId != packet.packetId ||
                    slot.commandId != packet.commandIdValue
                ) {
                    return diagnostic(
                        "invalid.preflight.core_primitive_packet_authority",
                        "Coverage-mask consumer indexed slot identity differs from its scope.",
                    )
                }
            }
        }
        val clipStencilPrefix = (clipStencilPreparedRouteSeal as?
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route)
            ?.retainsPrefixFor(sourceStepIndex, listOf(packet.packetId)) == true
        val expectedStructuralPipelineKey = when (packet.role) {
            GPUDrawPacketRole.Shading -> {
                val clipStencilConsumerSeal =
                    (clipStencilPreparedRouteSeal as?
                        GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route)
                        ?.retainedFor(
                            sourceStepIndex,
                            listOf(packet.packetId),
                        ) as? GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                if (coverageMaskConsumerSlot != null) {
                    coverageMaskConsumerSlot.structuralPipelineKey
                } else if (clipStencilConsumerSeal != null) {
                    clipStencilConsumerSeal.route.consumers.single { consumer ->
                        consumer.commandId == packet.commandIdValue &&
                            consumer.sourceOrder == packet.originalPaintOrder
                    }.structuralKey
                } else {
                    corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        clipExecutionPlan,
                        blendPlan,
                        render.samplePlan.sampleCount,
                        targetStructuralColorFormat,
                    ).let { structuralKey ->
                        if (render.resourceUses.any {
                                it.role == GPUFrameResourceRole.PathDepthStencil
                            } || clipStencilPrefix
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
                    render.samplePlan.sampleCount,
                    targetStructuralColorFormat,
                )
            GPUDrawPacketRole.PathStencilCover ->
                corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                    clipExecutionPlan,
                    blendPlan,
                    render.samplePlan.sampleCount,
                    targetStructuralColorFormat,
                )
            else -> return diagnostic(
                "invalid.preflight.core_primitive_packet_authority",
                "Core primitive executable packet role is not a sealed native route role.",
            )
        }
        val expectedBindingLayoutHash = corePrimitiveGradientBindingLayoutHash(
            expectedStructuralPipelineKey.shader,
        ) ?: when (expectedStructuralPipelineKey.uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                CORE_PRIMITIVE_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
            -> error("Gradient bindings must be selected by their structural shader variant")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1 ->
                coverageMaskConsumerSlot?.bindingLayoutHash ?: return diagnostic(
                    "unsupported.native-core-primitive.coverage-mask-direct-route",
                    "Coverage-mask bindings require their dedicated prepared route.",
                )
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1 ->
                return diagnostic(
                    "unsupported.native-core-primitive.coverage-mask-direct-route",
                    "Coverage-mask producer bindings require their dedicated prepared route.",
                )
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
            packet.targetStateHash != corePrimitiveTargetStateHash(
                render.samplePlan.sampleCount,
                targetDescriptor.format,
            ) ||
            packet.scissorBoundsHash != if (coverageMaskConsumerSlot == null) {
                corePrimitiveScissorAuthority(semantic.scissorBounds)
            } else {
                null
            }
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_packet_authority",
                "Core primitive executable packet fields contradict the canonical route authority.",
            )
        }
        val declaredLayerTargetLabels = framePlan.steps
            .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
            .map(GPUFrameStep.LayerTargetPrepareStep::targetLabel)
            .toSet()
        if (targetPreparation == null &&
            render.target.value !in declaredLayerTargetLabels
        ) {
            return diagnostic(
                "invalid.preflight.core_primitive_target_authority",
                "Core primitive target preparation must be one exact texture.",
            )
        }
        if (targetPreparation != null &&
            !isCanonicalCorePrimitiveTargetPreparation(
                targetPreparation,
                render.target,
                semantic.targetBounds,
                targetDescriptor.format,
            )
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

    private data class PreparedRenderScopeRouteIndex(
        val clipStencilByPacketId:
            Map<GPUDrawPacketID, GPUCorePrimitiveClipStencilPreparedScopeRouteSeal>,
        val clipStencilPrefixPacketIds: Set<GPUDrawPacketID>,
        val clipStencilSlabAuthority: GPUCorePrimitiveClipStencilPreparedSlabAuthority?,
        val coverageMaskScope: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal,
        val coverageMaskUnitsByPacketId:
            Map<GPUDrawPacketID, GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal>,
    )

    private fun preparedRenderScopeRouteIndex(
        sourceStepIndex: Int,
        render: GPUFrameStep.RenderPassStep,
        corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    ): PreparedRenderScopeRouteIndex {
        val packetIds = render.drawPackets.map(GPUDrawPacket::packetId)
        val clipStencilByPacketId = packetIds.associateWith { packetId ->
            when (corePrimitiveClipStencilPreparedRoutes) {
                GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty ->
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
                is GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route ->
                    corePrimitiveClipStencilPreparedRoutes.retainedFor(
                        sourceStepIndex,
                        listOf(packetId),
                    )
            }
        }
        val coverageMaskScope = when (corePrimitiveCoverageMaskPreparedRoutes) {
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty ->
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty
            is GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route ->
                corePrimitiveCoverageMaskPreparedRoutes.retainedFor(
                    sourceStepIndex,
                    packetIds,
                )
        }
        val coverageMaskUnitsByPacketId = coverageMaskScope.units().associateBy { unit ->
            when (unit) {
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.packetId
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.packetId
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
                -> error("Coverage-mask scope units must be exact producer or consumer units")
            }
        }
        val clipStencilSlabAuthority = when (corePrimitiveClipStencilPreparedRoutes) {
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty -> null
            is GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route ->
                corePrimitiveClipStencilPreparedRoutes.slabAuthority
        }
        val clipStencilPrefixPacketIds = when (corePrimitiveClipStencilPreparedRoutes) {
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Empty -> emptySet()
            is GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route -> render.drawPackets
                .filter { packet ->
                    corePrimitiveClipStencilPreparedRoutes.retainsPrefixFor(
                        sourceStepIndex,
                        listOf(packet.packetId),
                    )
                }
                .mapTo(linkedSetOf(), GPUDrawPacket::packetId)
        }
        return PreparedRenderScopeRouteIndex(
            clipStencilByPacketId,
            clipStencilPrefixPacketIds,
            clipStencilSlabAuthority,
            coverageMaskScope,
            coverageMaskUnitsByPacketId,
        )
    }

    private fun materializeRenderOperands(
        framePlan: GPUFramePlan,
        ownerScope: String,
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    ): GPUResourceMaterializationDecision {
        val renderScopes = framePlan.steps.mapIndexedNotNull { sourceStepIndex, step ->
            (step as? GPUFrameStep.RenderPassStep)?.let { render ->
                Triple(
                    sourceStepIndex,
                    render,
                    preparedRenderScopeRouteIndex(
                        sourceStepIndex,
                        render,
                        corePrimitiveClipStencilPreparedRoutes,
                        corePrimitiveCoverageMaskPreparedRoutes,
                    ),
                )
            }
        }
        val packets = renderScopes.flatMap { (sourceStepIndex, render, preparedScopeRoutes) ->
            render.drawPackets
                .filterNot { packet -> packet.role == GPUDrawPacketRole.W4ePrepared }
                .map { packet -> Triple(sourceStepIndex, packet, preparedScopeRoutes) }
        }
        if (packets.isEmpty()) {
            return GPUResourceMaterializationDecision.Materialized(resources = emptyList(), targetId = context.targetId)
        }
        return try {
            val operands = packets.flatMap { (sourceStepIndex, packet, preparedScopeRoutes) ->
                plannedRenderOperands(
                    sourceStepIndex,
                    packet,
                    ownerScope,
                    corePrimitiveDirectRoutes,
                    preparedScopeRoutes,
                )
            }
            resourceProvider.materializeCommandOperands(
                GPUCommandOperandMaterializationRequest(
                    targetId = context.targetId,
                    taskIds = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                        .filter { it.drawPackets.any { packet -> packet.role != GPUDrawPacketRole.W4ePrepared } }
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
        preparedScopeRoutes: PreparedRenderScopeRouteIndex,
    ): List<GPUCommandOperandMaterializationPlan> = buildList {
        val clipStencilScope = preparedScopeRoutes.clipStencilByPacketId[packet.packetId]
            ?: GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
        val clipStencilPrefix = packet.packetId in preparedScopeRoutes.clipStencilPrefixPacketIds
        val coverageMaskUnit = preparedScopeRoutes.coverageMaskUnitsByPacketId[packet.packetId]
        add(
            operand(
                packet,
                "setRenderPipeline",
                GPUMaterializedCommandOperandKind.RenderPipeline,
                packet.renderPipelineKey!!.value,
                ownerScope,
            ),
        )
        if (clipStencilScope !is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer) {
            add(
                operand(
                    packet,
                    "setBindGroup",
                    GPUMaterializedCommandOperandKind.BindGroup,
                    packet.bindingLayoutHash,
                    ownerScope,
                ),
            )
        }
        val verticesSemantic = packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
        if (verticesSemantic != null) {
            add(
                operand(
                    packet,
                    "setBindGroup",
                    GPUMaterializedCommandOperandKind.BindGroup,
                    "${verticesSemantic.canonicalHash}.material",
                    ownerScope,
                ),
            )
        }
        val colorGlyph = packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph
        val directCore = corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId)
        val clipStencilSlabs = when (clipStencilScope) {
            is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ->
                clipStencilScope.slabAuthority
            is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer ->
                clipStencilScope.slabAuthority
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
            -> null
        }
        val coverageMaskSlabs =
            (coverageMaskUnit as?
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer)?.slabAuthority
        val verticesIndexed = verticesSemantic?.artifact?.indexCount != null
        if (colorGlyph != null || verticesSemantic != null || directCore != null || clipStencilPrefix ||
            clipStencilSlabs != null || coverageMaskSlabs != null
        ) {
            add(
                operand(
                    packet,
                    "setVertexBuffer",
                    GPUMaterializedCommandOperandKind.VertexBuffer,
                    if (verticesSemantic != null) {
                        "${verticesSemantic.canonicalHash}.vertices." +
                            verticesSemantic.artifact.vertexBytesForUpload().size
                    } else if (colorGlyph != null) {
                        "${colorGlyph.canonicalHash}.vertices.${colorGlyph.vertexData.size}"
                    } else if (clipStencilSlabs != null) {
                        "clip-stencil.${clipStencilSlabs.vertexResource.value}@" +
                            "${clipStencilSlabs.vertexGeneration}.vertices." +
                            clipStencilSlabs.vertexByteSize
                    } else if (clipStencilPrefix) {
                        val prefixSlab = requireNotNull(preparedScopeRoutes.clipStencilSlabAuthority)
                        "clip-stencil.${prefixSlab.vertexResource.value}@" +
                            "${prefixSlab.vertexGeneration}.vertices." + prefixSlab.vertexByteSize
                    } else if (coverageMaskSlabs != null) {
                        "coverage-mask.${coverageMaskSlabs.vertexResource.value}@" +
                            "${coverageMaskSlabs.vertexGeneration}.vertices." +
                            coverageMaskSlabs.vertexByteSize
                    } else {
                        "core-direct.$sourceStepIndex.${packet.packetId.value}.vertices." +
                            requireNotNull(directCore).vertexCount * 2
                    },
                    ownerScope,
                ),
            )
            if (verticesSemantic == null || verticesIndexed) {
                add(
                    operand(
                        packet,
                        "setIndexBuffer",
                        GPUMaterializedCommandOperandKind.IndexBuffer,
                        if (verticesSemantic != null) {
                            "${verticesSemantic.canonicalHash}.indices." +
                                requireNotNull(verticesSemantic.artifact.indexBytesForUpload()).size
                        } else if (colorGlyph != null) {
                            "${colorGlyph.canonicalHash}.indices.${colorGlyph.indexData.size}"
                        } else if (clipStencilSlabs != null) {
                            "clip-stencil.${clipStencilSlabs.indexResource.value}@" +
                                "${clipStencilSlabs.indexGeneration}.indices." +
                                clipStencilSlabs.indexByteSize
                        } else if (clipStencilPrefix) {
                            val prefixSlab = requireNotNull(preparedScopeRoutes.clipStencilSlabAuthority)
                            "clip-stencil.${prefixSlab.indexResource.value}@" +
                                "${prefixSlab.indexGeneration}.indices." + prefixSlab.indexByteSize
                        } else if (coverageMaskSlabs != null) {
                            "coverage-mask.${coverageMaskSlabs.indexResource.value}@" +
                                "${coverageMaskSlabs.indexGeneration}.indices." +
                                coverageMaskSlabs.indexByteSize
                        } else {
                            "core-direct.$sourceStepIndex.${packet.packetId.value}.indices." +
                                requireNotNull(directCore).indexCount
                        },
                        ownerScope,
                    ),
                )
            }
        }
    }

    private fun validateRenderOperands(
        framePlan: GPUFramePlan,
        materialized: GPUResourceMaterializationDecision.Materialized,
        ownerScope: String,
        corePrimitiveDirectRoutes: GPUCorePrimitiveDirectNativeFrameRouteSeal,
        corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedFrameRouteSeal,
        corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    ): GPUDiagnostic? {
        val renderScopes = framePlan.steps.mapIndexedNotNull { sourceStepIndex, step ->
            (step as? GPUFrameStep.RenderPassStep)?.let { render ->
                Triple(
                    sourceStepIndex,
                    render,
                    preparedRenderScopeRouteIndex(
                        sourceStepIndex,
                        render,
                        corePrimitiveClipStencilPreparedRoutes,
                        corePrimitiveCoverageMaskPreparedRoutes,
                    ),
                )
            }
        }
        val packets = renderScopes.flatMap { (sourceStepIndex, render, preparedScopeRoutes) ->
            render.drawPackets
                .filterNot { packet -> packet.role == GPUDrawPacketRole.W4ePrepared }
                .map { packet -> Triple(sourceStepIndex, packet, preparedScopeRoutes) }
        }
        val bridge = materialized.operandBridge
        val expectedTasks = renderScopes
            .filter { (_, render) -> render.drawPackets.any { it.role != GPUDrawPacketRole.W4ePrepared } }
            .flatMap { (_, render) -> render.sourceTaskIds }.map { it.value }.distinct()
        val expectedPlansByPacket = packets.map { (sourceStepIndex, packet, preparedScopeRoutes) ->
            plannedRenderOperands(
                sourceStepIndex,
                packet,
                ownerScope,
                corePrimitiveDirectRoutes,
                preparedScopeRoutes,
            )
        }
        val expectedPlans = expectedPlansByPacket.flatten()
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
        for ((packetIndex, scopedPacket) in packets.withIndex()) {
            val (sourceStepIndex, packet, preparedScopeRoutes) = scopedPacket
            val expected = expectedPlansByPacket[packetIndex]
            val packetBridge = bridge.subList(bridgeOffset, bridgeOffset + expected.size)
            bridgeOffset += expected.size
            val pipelines = packetBridge.filter { it.commandLabel == "setRenderPipeline" && it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline }
            val bindGroups = packetBridge.filter { it.commandLabel == "setBindGroup" && it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup }
            val vertices = packetBridge.filter { it.commandLabel == "setVertexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.VertexBuffer }
            val indices = packetBridge.filter { it.commandLabel == "setIndexBuffer" && it.operand.kind == GPUMaterializedCommandOperandKind.IndexBuffer }
            val clipStencilScope = preparedScopeRoutes.clipStencilByPacketId[packet.packetId]
                ?: GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
            val clipStencilPrefix = packet.packetId in preparedScopeRoutes.clipStencilPrefixPacketIds
            val coverageMaskUnit =
                preparedScopeRoutes.coverageMaskUnitsByPacketId[packet.packetId]
            val verticesSemantic =
                packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
            val indexedPayload = packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                verticesSemantic?.artifact?.indexCount != null ||
                corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId) != null ||
                clipStencilPrefix ||
                clipStencilScope is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                clipStencilScope is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer ||
                coverageMaskUnit is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
            val expectedBindGroupCount =
                if (clipStencilScope is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer) {
                    0
                } else if (verticesSemantic != null) {
                    2
                } else {
                    1
                }
            val expectedVertexIndexCount =
                if (verticesSemantic != null) {
                    if (indexedPayload) 2 else 1
                } else if (indexedPayload) {
                    2
                } else {
                    0
                }
            if (pipelines.size != 1 || bindGroups.size != expectedBindGroupCount ||
                vertices.size + indices.size != expectedVertexIndexCount ||
                (indexedPayload && (vertices.size != 1 || indices.size != 1)) ||
                (!indexedPayload && verticesSemantic == null &&
                    (vertices.isNotEmpty() || indices.isNotEmpty()))
            ) {
                return diagnostic(
                    "invalid.preflight.render_operand_bijection",
                    "Every render packet requires one pipeline, its sealed bind-group count, and exact indexed buffers when required.",
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
        corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal,
    ): List<GPUCommandEncoderScopePlan> {
        val renderBridgesByStepIndex = buildMap {
            var bridgeOffset = 0
            framePlan.steps.forEachIndexed { sourceStepIndex, step ->
                val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
                val preparedScopeRoutes = preparedRenderScopeRouteIndex(
                    sourceStepIndex,
                    render,
                    corePrimitiveClipStencilPreparedRoutes,
                    corePrimitiveCoverageMaskPreparedRoutes,
                )
                val operandCount = render.drawPackets
                    .filterNot { packet -> packet.role == GPUDrawPacketRole.W4ePrepared }
                    .sumOf { packet ->
                    val clipStencilScope =
                        preparedScopeRoutes.clipStencilByPacketId[packet.packetId]
                            ?: GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
                    val coverageMaskUnit =
                        preparedScopeRoutes.coverageMaskUnitsByPacketId[packet.packetId]
                    val verticesSemantic =
                        packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
                    val bindGroupCount =
                        if (clipStencilScope is
                            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
                        ) {
                            0
                        } else if (verticesSemantic != null) {
                            2
                        } else {
                            1
                        }
                    val indexedCount = if (
                        verticesSemantic != null
                    ) {
                        if (verticesSemantic.artifact.indexCount != null) 2 else 1
                    } else if (
                        packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph ||
                        corePrimitiveDirectRoutes.routeOrNull(sourceStepIndex, packet.packetId) != null ||
                        packet.packetId in preparedScopeRoutes.clipStencilPrefixPacketIds ||
                        clipStencilScope is
                            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                        clipStencilScope is
                            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                        || coverageMaskUnit is
                            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
                    ) {
                        2
                    } else {
                        0
                    }
                    1 + bindGroupCount + indexedCount
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
                val mixedCorePrimitiveAndImage =
                    corePacketIds.isNotEmpty() &&
                        step.drawPackets.any {
                            it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                        } &&
                        step.drawPackets.all {
                            it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                                it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                        }
                val isW4dGeneral = step in framePlan.w4dGeneralRenderSteps()
                val stepCorePrimitivePathStencilRoutes = if (isW4dGeneral) {
                    GPUCorePrimitivePathStencilNativeRouteSeal.Empty
                } else {
                    corePrimitivePathStencilRoutes.retainedFor(
                        index,
                        step.drawPackets
                            .filter { packet ->
                                packet.role == GPUDrawPacketRole.PathStencilProducer ||
                                    packet.role == GPUDrawPacketRole.PathStencilCover
                            }
                            .map { it.packetId },
                    )
                }
                val stepCorePrimitiveNativeScopeRoutes = if (isW4dGeneral) {
                    GPUCorePrimitiveNativeScopeRouteSeal.Empty
                } else {
                    corePrimitiveNativeScopeRoutes.retainedFor(
                        index,
                        if (corePrimitiveNativeScopeRoutes.hasRouteForStep(index)) {
                            corePacketIds
                        } else {
                            emptyList()
                        },
                    )
                }
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
                val stepCorePrimitiveCoverageMaskPreparedRoutes =
                    when (corePrimitiveCoverageMaskPreparedRoutes) {
                        GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Empty ->
                            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty
                        is GPUCorePrimitiveCoverageMaskPreparedFrameRouteSeal.Route ->
                            corePrimitiveCoverageMaskPreparedRoutes.retainedFor(
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
                val stream = if (step.w5bInitialClearV3 != null) {
                    GPUPassCommandStream(
                        streamId = "frame.${framePlan.frameId.value}.commands.$index",
                        packetStreamId = "w5b.initial-clear.$index",
                        passId = passPlan.passId,
                        commands = listOf(
                            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand.BeginRenderPass(
                                corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
                                step.loadStore.dumpLabel()),
                            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand.EndRenderPass(passPlan.passId)),
                        sourcePassIds = listOf("w5b.${step.w5bInitialClearV3.graph.id.value}.initial-clear"))
                } else if (step.drawPackets.all { packet ->
                        packet.role == GPUDrawPacketRole.W4ePrepared
                    }
                ) {
                    GPUPassCommandStream(
                        streamId = "frame.${framePlan.frameId.value}.commands.$index",
                        packetStreamId = "w4e.sealed.$index",
                        passId = passPlan.passId,
                        commands = listOf(
                            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand.BeginRenderPass(
                                step.drawPackets.first().targetStateHash,
                                step.loadStore.dumpLabel(),
                            ),
                            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand.Draw(
                                step.drawPackets.single().vertexSourceLabel,
                                step.drawPackets.single().packetId,
                            ),
                            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand.EndRenderPass(
                                passPlan.passId,
                            ),
                        ),
                        sourcePassIds = step.drawPackets.map(GPUDrawPacket::passId),
                    )
                } else {
                    GPUPassCommandStream.fromBatchPlan(
                        streamId = "frame.${framePlan.frameId.value}.commands.$index",
                        batchPlan = passPlan,
                        loadStoreLabel = step.loadStore.dumpLabel(),
                        materialization = stepMaterialized,
                    )
                }
                val sealedCoverageMaskPreparedRoutes = when (
                    stepCorePrimitiveCoverageMaskPreparedRoutes
                ) {
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer,
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer,
                    -> sealGPUCorePrimitiveCoverageMaskPreparedCommandAuthority(
                        stepCorePrimitiveCoverageMaskPreparedRoutes,
                        step.drawPackets.single(),
                        stream,
                        passPlan.passId,
                        step.loadStore.dumpLabel(),
                    )
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition,
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition,
                    -> sealGPUCorePrimitiveCoverageMaskPreparedPartitionCommandAuthority(
                        stepCorePrimitiveCoverageMaskPreparedRoutes,
                        step.drawPackets,
                        stream,
                        passPlan.passId,
                        step.loadStore.dumpLabel(),
                    )
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
                    -> stepCorePrimitiveCoverageMaskPreparedRoutes
                }
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = index,
                    operationKind = GPUEncoderOperationKind.Render,
                    scopeLabel = "step.$index",
                    sourceTaskIds = step.sourceTaskIds,
                    sourcePacketIds = step.drawPackets.map { it.packetId },
                    mixedCorePrimitiveAndImage = mixedCorePrimitiveAndImage,
                    facadeOperationClasses = stream.commandLabels,
                    targetGeneration = when (sealedCoverageMaskPreparedRoutes) {
                        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ->
                            sealedCoverageMaskPreparedRoutes
                                .attachmentAuthority.resourceGeneration
                        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer ->
                            sealedCoverageMaskPreparedRoutes.sceneTargetGeneration
                        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition ->
                            sealedCoverageMaskPreparedRoutes.units.first()
                                .attachmentAuthority.resourceGeneration
                        is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition ->
                            sealedCoverageMaskPreparedRoutes.units.first().sceneTargetGeneration
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
                        -> context.targetGeneration
                    },
                    resourceGenerationLabels = labels,
                    passCommandStream = stream,
                    targetResource = step.target,
                    corePrimitiveDirectNativeRouteSeal = stepCorePrimitiveDirectRoutes,
                    corePrimitivePathStencilNativeRouteSeal = stepCorePrimitivePathStencilRoutes,
                    corePrimitiveNativeScopeRouteSeal = stepCorePrimitiveNativeScopeRoutes,
                    corePrimitiveClipStencilPreparedRouteSeal =
                        stepCorePrimitiveClipStencilPreparedRoutes,
                    corePrimitiveCoverageMaskPreparedRouteSeal =
                        sealedCoverageMaskPreparedRoutes,
                ).attachNativeOperandKeys(
                    nativeOperandKeys(
                        step,
                        labels,
                        stream,
                        stepCorePrimitiveDirectRoutes,
                        stepCorePrimitiveNativeScopeRoutes,
                        stepCorePrimitiveClipStencilPreparedRoutes,
                        sealedCoverageMaskPreparedRoutes,
                        preparedRenderScopeRouteIndex(
                            index,
                            step,
                            corePrimitiveClipStencilPreparedRoutes,
                            corePrimitiveCoverageMaskPreparedRoutes,
                        ).clipStencilPrefixPacketIds.isNotEmpty(),
                        clipStencilPrefixDepthStencilBinding =
                            if (preparedRenderScopeRouteIndex(
                                    index,
                                    step,
                                    corePrimitiveClipStencilPreparedRoutes,
                                    corePrimitiveCoverageMaskPreparedRoutes,
                                ).clipStencilPrefixPacketIds.isNotEmpty()
                            ) {
                                (corePrimitiveClipStencilPreparedRoutes as?
                                    GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route)?.let {
                                    "GPUFrameTextureRef:${it.attachmentAuthority.resource.value}@" +
                                        it.attachmentAuthority.resourceGeneration
                                }
                            } else {
                                null
                            },
                    ),
                    allowsClipStencilPrefixDepthStencil =
                        preparedRenderScopeRouteIndex(
                            index,
                            step,
                            corePrimitiveClipStencilPreparedRoutes,
                            corePrimitiveCoverageMaskPreparedRoutes,
                        ).clipStencilPrefixPacketIds.isNotEmpty(),
                    allowsW4dGeneralDepthStencil = step.drawPackets.singleOrNull()
                        ?.corePrimitivePreparedAuthority
                        ?.w4dGeneralFrameMaterializationAuthority
                        ?.pathPass(step.drawPackets.single().passId)
                        ?.depthStencilResourceId != null,
                    allowsW4ePreparedDepthStencil = step.drawPackets.singleOrNull()
                        ?.let { packet ->
                            packet.role == GPUDrawPacketRole.W4ePrepared &&
                                (
                                    (packet.w4ePreparedClipPass as? org.graphiks.kanvas.gpu.renderer.passes
                                        .GPUW4ePreparedClipPassAuthority.Producer)
                                        ?.depthStencilResourceId != null
                                    || packet.w4ePreparedPath?.depthStencilResourceId != null
                                )
                        } == true,
                )
            }
            is GPUFrameStep.ComputePassStep -> scope(index, GPUEncoderOperationKind.Compute, step.sourceTaskIds, listOf("beginComputePass") + List(step.dispatches.size) { "dispatchWorkgroups" } + "endComputePass", labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.UploadResourceStep -> scope(
                index,
                GPUEncoderOperationKind.Upload,
                step.sourceTaskIds,
                when (step.destinationKind) {
                    org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Buffer ->
                        listOf("writeBufferOrCopyBuffer")
                    org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Texture ->
                        listOf("writeTexture")
                },
                labels,
                nativeOperandKeys(step, labels),
            )
            is GPUFrameStep.CopyResourceStep -> scope(index, GPUEncoderOperationKind.Copy, step.sourceTaskIds, List(step.regions.size) { "copyResource" }, labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.CopyDestinationStep -> scope(index, GPUEncoderOperationKind.CopyDestination, step.sourceTaskIds, listOf("copyTextureToTexture"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.CopyAsDrawMaterializationStep -> scope(index, GPUEncoderOperationKind.CopyAsDraw, step.sourceTaskIds, listOf("beginRenderPass", "copyAsDraw", "endRenderPass"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.ReadbackCopyStep -> scope(index, GPUEncoderOperationKind.Readback, step.sourceTaskIds, listOf("copyTextureToBuffer"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.SurfaceBlitRenderPassStep -> scope(index, GPUEncoderOperationKind.SurfaceBlit, step.sourceTaskIds, listOf("beginRenderPass", "surfaceBlit", "endRenderPass"), labels, nativeOperandKeys(step, labels))
            is GPUFrameStep.LayerCompositeRenderStep -> scope(
                index,
                GPUEncoderOperationKind.LayerComposite,
                step.sourceTaskIds,
                listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass"),
                labels,
                nativeOperandKeys(step, labels),
            )
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
        corePrimitiveClipStencilPreparedRoutes:
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal =
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
        corePrimitiveCoverageMaskPreparedRoutes:
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal =
            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Empty,
        clipStencilPrefix: Boolean = false,
        clipStencilPrefixDepthStencilBinding: String? = null,
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
                val w4ePacket = step.drawPackets.singleOrNull()?.takeIf { packet ->
                    packet.role == GPUDrawPacketRole.W4ePrepared
                }
                if (w4ePacket != null && step.drawPackets.all { packet ->
                        packet.role == GPUDrawPacketRole.W4ePrepared
                    }
                ) {
                    val preparedPass = w4ePacket.w4ePreparedClipPass
                    val preparedPath = w4ePacket.w4ePreparedPath
                    return when {
                        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
                            .GPUW4ePreparedClipPassAuthority.Initialize ||
                            preparedPass is org.graphiks.kanvas.gpu.renderer.passes
                            .GPUW4ePreparedClipPassAuthority.PathMaskClear ->
                            listOf(
                                key(GPUPreparedNativeOperandRole.RenderColorTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"),
                                key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:clear"),
                            )
                        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
                            .GPUW4ePreparedClipPassAuthority.Producer -> buildList {
                            if (preparedPass.sampleCount == 4) {
                                add(key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                                add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:resolve"))
                            } else {
                                add(key(GPUPreparedNativeOperandRole.RenderColorTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                            }
                            preparedPass.depthStencilResourceId?.let {
                                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:depth"))
                            }
                            if (preparedPass.geometry is org.graphiks.kanvas.gpu.renderer.passes
                                    .GPUW4ePreparedClipGeometry.Path
                            ) {
                                val pathGeometry = preparedPass.geometry.copyPathGeometryF32()
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:path-producer"))
                                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:path-vertices"))
                                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:path-indices"))
                                if (pathGeometry.copyDirectTriangleF32OrNull() == null) {
                                    add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                        GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:path-cover"))
                                }
                            } else {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"))
                                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:producer"))
                            }
                        }
                        preparedPass is org.graphiks.kanvas.gpu.renderer.passes
                            .GPUW4ePreparedClipPassAuthority.Fold -> listOf(
                            key(GPUPreparedNativeOperandRole.RenderColorTarget,
                                GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"),
                            key(GPUPreparedNativeOperandRole.RenderPipeline,
                                GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"),
                            key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:fold"),
                        )
                        preparedPath != null -> buildList {
                            if (preparedPath.sample == org.graphiks.kanvas.gpu.plan.SamplePlan.Multisample4) {
                                add(key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                                preparedPath.resolveTargetResourceId?.let {
                                    add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                                        GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:resolve"))
                                }
                            } else {
                                add(key(GPUPreparedNativeOperandRole.RenderColorTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:target"))
                            }
                            if (preparedPath.depthStencilResourceId != null) {
                                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                    GPUPreparedNativeOperandKind.TextureView, "w4e:${w4ePacket.passId}:depth"))
                            }
                            val directPath = when (val geometry = preparedPath.copyGeometry()) {
                                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Fill ->
                                    geometry.valueF32.copyDirectTriangleF32OrNull() != null
                                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Stroke ->
                                    geometry.valueF32.copyFillGeometryF32().copyDirectTriangleF32OrNull() != null
                                is org.graphiks.kanvas.gpu.plan.PathDrawGeometry.InverseDomainSource -> false
                                org.graphiks.kanvas.gpu.plan.PathDrawGeometry.Empty -> false
                            }
                            val inverseDomainConsumer = w4ePacket.w4ePreparedClipConsumer as?
                                org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority.InverseDomain
                            val stencilProducer = preparedPath.phase in setOf(
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.SingleSampleStencilProducer,
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.MultisampleStencilProducer,
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilProducer,
                            )
                            val hardMaskStencilCover = preparedPath.phase ==
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilCover
                            val hardMaskProducer = preparedPath.phase ==
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskProducer
                            val stencilCover = preparedPath.phase in setOf(
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.SingleSampleStencilColorCover,
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.MultisampleStencilColorCover,
                                org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeMaskStencilCover,
                            )
                            if (hardMaskProducer) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:hard-mask-producer"))
                                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:hard-mask-vertices"))
                                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:hard-mask-indices"))
                            } else if (stencilProducer) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:stencil-producer"))
                                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:stencil-vertices"))
                                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:stencil-indices"))
                            } else if (hardMaskStencilCover) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:stencil-cover"))
                            } else if (inverseDomainConsumer?.interiorCoverage is org.graphiks.kanvas.gpu.renderer.passes
                                    .GPUW4ePreparedInverseInteriorCoverage.Geometry && stencilCover
                            ) {
                                if (w4ePacket.w5bFinalFrameWitnessV3?.w4eLane?.owns(w4ePacket) != true) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-interior"))
                                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-vertices"))
                                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-indices"))
                                }
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
                            } else if (inverseDomainConsumer != null && inverseDomainConsumer.interiorCoverage is
                                org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage.Zero
                            ) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
                            } else if (inverseDomainConsumer != null) {
                                if (w4ePacket.w5bFinalFrameWitnessV3?.w4eLane?.owns(w4ePacket) != true) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-main"))
                                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-main-vertices"))
                                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                    GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-main-indices"))
                                }
                                if (inverseDomainConsumer.interiorCoverage is org.graphiks.kanvas.gpu.renderer.passes
                                        .GPUW4ePreparedInverseInteriorCoverage.Geometry
                                ) {
                                    add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                        GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-interior"))
                                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-vertices"))
                                    add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:inverse-domain-interior-indices"))
                                }
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:inverse-domain-cover"))
                                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:inverse-domain-color"))
                            } else {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline,
                                    GPUPreparedNativeOperandKind.RenderPipeline, "w4e:${w4ePacket.passId}:pipeline"))
                                add(key(GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup, "w4e:${w4ePacket.passId}:consumer"))
                                if (directPath && !stencilCover && preparedPath.phase !=
                                    org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeBinaryColorCover
                                ) {
                                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:consumer-vertices"))
                                    add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                                        GPUPreparedNativeOperandKind.Buffer, "w4e:${w4ePacket.passId}:consumer-indices"))
                                }
                            }
                        }
                        else -> error("W4e packet has no sealed pass authority")
                    }
                }
                val firstPlannedPathAuthority = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority
                val w4dGeneralAuthority = firstPlannedPathAuthority
                    ?.w4dGeneralFrameMaterializationAuthority
                if (w4dGeneralAuthority != null && step.drawPackets.all { packet ->
                        packet.corePrimitivePreparedAuthority
                            ?.w4dGeneralFrameMaterializationAuthority === w4dGeneralAuthority
                    }
                ) {
                    val packet = requireNotNull(step.drawPackets.singleOrNull()) {
                        "W4d.2 native materialization scopes retain exactly one packet"
                    }
                    val fact = requireNotNull(w4dGeneralAuthority.pathPass(packet.passId)) {
                        "W4d.2 native materialization scope must retain its sealed path-pass fact"
                    }
                    val target = requireNotNull(w4dGeneralAuthority.resource(fact.targetResourceId)) {
                        "W4d.2 native materialization scope must retain its sealed target binding"
                    }
                    val vertex = requireNotNull(w4dGeneralAuthority.resource(fact.vertexResourceId)) {
                        "W4d.2 native materialization scope must retain its sealed vertex binding"
                    }
                    val index = requireNotNull(w4dGeneralAuthority.resource(fact.indexResourceId)) {
                        "W4d.2 native materialization scope must retain its sealed index binding"
                    }
                    val uniform = requireNotNull(w4dGeneralAuthority.resource(fact.uniformResourceId)) {
                        "W4d.2 native materialization scope must retain its sealed uniform binding"
                    }
                    return buildList {
                        if (fact.sampleCountI32 == 4) {
                            add(key(
                                GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                "w4d-general:${w4dGeneralAuthority.planId}:msaa:${target.value}",
                            ))
                            if (step.sampleContinuation?.resolveAction ==
                                GPUSampleResolveAction.ResolveCanonical
                            ) {
                                val resolve = requireNotNull(
                                    w4dGeneralAuthority.resource(fact.resolveTargetResourceId.orEmpty()),
                                ) {
                                    "W4d.2 final resolve requires one sealed canonical target"
                                }
                                add(key(
                                    GPUPreparedNativeOperandRole.RenderResolveTarget,
                                    GPUPreparedNativeOperandKind.TextureView,
                                    "w4d-general:${w4dGeneralAuthority.planId}:resolve:${resolve.value}",
                                ))
                            }
                        } else {
                            add(key(
                                GPUPreparedNativeOperandRole.RenderColorTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                "w4d-general:${w4dGeneralAuthority.planId}:color:${target.value}",
                            ))
                        }
                        fact.depthStencilResourceId?.let { depthResourceId ->
                            val depth = requireNotNull(w4dGeneralAuthority.resource(depthResourceId)) {
                                "W4d.2 depth/stencil pass requires one sealed binding"
                            }
                            add(key(
                                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                "w4d-general:${w4dGeneralAuthority.planId}:depth:${depth.value}",
                            ))
                        }
                        add(key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "w4d-general:${w4dGeneralAuthority.planId}:pipeline:${fact.pathPassId}",
                        ))
                        add(key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "w4d-general:${w4dGeneralAuthority.planId}:uniform:${uniform.value}",
                        ))
                        add(key(
                            GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "w4d-general:${w4dGeneralAuthority.planId}:vertex:${vertex.value}",
                        ))
                        add(key(
                            GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "w4d-general:${w4dGeneralAuthority.planId}:index:${index.value}",
                        ))
                    }
                }
                val plannedPathScratch = firstPlannedPathAuthority?.w4dSessionScratch
                    ?.let(GPUPlannedPathSessionScratch::from)
                    ?: firstPlannedPathAuthority?.w4cSessionScratch
                        ?.let(GPUPlannedPathSessionScratch::from)
                if (plannedPathScratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.let(plannedPathScratch::owns) == true
                    }
                ) {
                    val packet = requireNotNull(step.drawPackets.singleOrNull())
                    val lane = plannedPathScratch.lane.label
                    val depthStencilKey = step.resourceUses.singleOrNull { use ->
                        use.role == GPUFrameResourceRole.PathDepthStencil
                    }?.let { use ->
                        val resourceIndex = step.resourceUses.indexOf(use)
                        key(
                            GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                            GPUPreparedNativeOperandKind.TextureView,
                            resources[resourceIndex + 1],
                        )
                    }
                    return listOf(
                        key(
                            GPUPreparedNativeOperandRole.RenderColorTarget,
                            GPUPreparedNativeOperandKind.TextureView,
                            targetResourceLabel,
                        ),
                    ) + listOfNotNull(depthStencilKey) + listOf(
                        key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "$lane.${plannedPathScratch.planId}.pipeline.${packet.packetId.value}",
                        ),
                        key(
                            GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "$lane.${plannedPathScratch.planId}.scratch.vertex",
                        ),
                        key(
                            GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "$lane.${plannedPathScratch.planId}.scratch.index",
                        ),
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "$lane.${plannedPathScratch.planId}.scratch.uniform.${packet.commandIdValue}",
                        ),
                    )
                }
                val w5aRRectScratch = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority?.w5aAnalyticRRectSessionScratch
                if (w5aRRectScratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.w5aAnalyticRRectSessionScratch === w5aRRectScratch
                    }
                ) {
                    val facts = w5aRRectScratch.payloadFacts
                    return listOf(
                        key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel),
                        key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w4b.${facts.planId}.pipeline"),
                        key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4b.${facts.planId}.scratch.vertex"),
                        key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4b.${facts.planId}.scratch.index"),
                    ) + step.drawPackets.map { packet ->
                        key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup,
                            "w4b.${facts.planId}.scratch.uniform.${packet.commandIdValue}")
                    }
                }
                val w5aRectScratch = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority?.w5aAnalyticRectSessionScratch
                if (w5aRectScratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.w5aAnalyticRectSessionScratch === w5aRectScratch
                    }
                ) {
                    val facts = w5aRectScratch.payloadFacts
                    return listOf(
                        key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel),
                        key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w4a.${facts.planId}.pipeline"),
                        key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4a.${facts.planId}.scratch.vertex"),
                        key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4a.${facts.planId}.scratch.index"),
                    ) + step.drawPackets.map { packet ->
                        key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup,
                            "w4a.${facts.planId}.scratch.uniform.${packet.commandIdValue}")
                    }
                }
                val w4bScratch = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority?.w4bSessionScratch
                if (w4bScratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.w4bSessionScratch === w4bScratch
                    }
                ) {
                    return listOf(
                        key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel),
                        key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w4b.${w4bScratch.planId}.pipeline"),
                        key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4b.${w4bScratch.planId}.scratch.vertex"),
                        key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4b.${w4bScratch.planId}.scratch.index"),
                    ) + step.drawPackets.map { packet ->
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "w4b.${w4bScratch.planId}.scratch.uniform.${packet.commandIdValue}",
                        )
                    }
                }
                val w4aScratch = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority?.w4aSessionScratch
                if (w4aScratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.w4aSessionScratch === w4aScratch
                    }
                ) {
                    return listOf(
                        key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel),
                        key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w4a.${w4aScratch.planId}.pipeline"),
                        key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4a.${w4aScratch.planId}.scratch.vertex"),
                        key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w4a.${w4aScratch.planId}.scratch.index"),
                    ) + step.drawPackets.map { packet ->
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "w4a.${w4aScratch.planId}.scratch.uniform.${packet.commandIdValue}",
                        )
                    }
                }
                step.w5bInitialClearV3?.let {
                    return listOf(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel))
                }
                val w5bWitness = step.drawPackets.firstOrNull()?.corePrimitivePreparedAuthority?.w5bFrameWitnessV3
                if (w5bWitness != null) {
                    val scratch = w5bWitness.scratchFor(step.drawPackets.first())
                    return buildList {
                        add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel))
                        step.resourceUses.singleOrNull { it.role == GPUFrameResourceRole.PathDepthStencil }?.let { use ->
                            add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget, GPUPreparedNativeOperandKind.TextureView, use.resource.value))
                        }
                        add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w5b.${scratch.planId}.pipeline.${step.drawPackets.first().commandIdValue}"))
                        add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w5b.${scratch.planId}.vertex"))
                        add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w5b.${scratch.planId}.index"))
                        step.drawPackets.forEach { packet ->
                            add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w5b.${scratch.planId}.uniform.${packet.commandIdValue}"))
                        }
                    }
                }
                val w3Scratch = step.drawPackets.firstOrNull()
                    ?.corePrimitivePreparedAuthority?.w3SessionScratch
                if (w3Scratch != null && step.drawPackets.all {
                        it.corePrimitivePreparedAuthority?.w3SessionScratch === w3Scratch
                    }
                ) {
                    return buildList {
                        add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, targetResourceLabel))
                        add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w3.${w3Scratch.planId}.pipeline.0"))
                        add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w3.${w3Scratch.planId}.scratch.vertex"))
                        add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w3.${w3Scratch.planId}.scratch.index"))
                        step.drawPackets.forEachIndexed { index, packet ->
                            if (index > 0 && w3Scratch.packetStructuralPipelineKeys[index - 1] != w3Scratch.packetStructuralPipelineKeys[index]) {
                                add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w3.${w3Scratch.planId}.pipeline.$index"))
                            }
                            add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w3.${w3Scratch.planId}.scratch.uniform.${packet.commandIdValue}"))
                        }
                    }
                }
                val textA8 = step.drawPackets.all {
                    it.semanticPayload is GPUDrawSemanticPayload.TextA8
                }
                val colorGlyph = step.drawPackets.all { it.semanticPayload is GPUDrawSemanticPayload.ColorGlyph }
                val directCore = corePrimitiveDirectRoutes is GPUCorePrimitiveDirectNativeRouteSeal.Routes
                val indexedCore = corePrimitiveNativeScopeRoutes is GPUCorePrimitiveNativeScopeRouteSeal.Routes
                val pathCore = indexedCore && step.drawPackets.any { packet ->
                    packet.role == GPUDrawPacketRole.PathStencilProducer ||
                        packet.role == GPUDrawPacketRole.PathStencilCover
                }
                val clipStencilCore =
                    corePrimitiveClipStencilPreparedRoutes is
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                        corePrimitiveClipStencilPreparedRoutes is
                        GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
                val coverageMaskCore =
                    corePrimitiveCoverageMaskPreparedRoutes is
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
                    corePrimitiveCoverageMaskPreparedRoutes is
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer ||
                    corePrimitiveCoverageMaskPreparedRoutes is
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition ||
                    corePrimitiveCoverageMaskPreparedRoutes is
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition
                check(listOf(pathCore, clipStencilCore, coverageMaskCore).count { it } <= 1) {
                    "Prepared CorePrimitive multi-pass native scope seals are mutually exclusive"
                }
                val drawOperandOwnership = if (colorGlyph) {
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
                } else {
                    GPUPreparedNativeOperandOwnership.Borrowed
                }
                val streamBridges = requireNotNull(stream).operandBridge
                val nativeBridges = if (pathCore || clipStencilCore || coverageMaskCore) {
                    val pipelineBridges = streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
                    }
                    check(pipelineBridges.size == step.drawPackets.size) {
                        "Indexed CorePrimitive pipelines must retain exact packet-order evidence"
                    }
                    val bindGroupBridges = streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                    }
                    if (clipStencilCore) {
                        val expectedBindGroups = if (
                            corePrimitiveClipStencilPreparedRoutes is
                            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
                        ) {
                            0
                        } else {
                            1
                        }
                        check(bindGroupBridges.size == expectedBindGroups) {
                            "Clip-stencil producer forbids bind groups and consumers require exactly one"
                        }
                    } else if (coverageMaskCore) {
                        check(bindGroupBridges.size == step.drawPackets.size) {
                            "Coverage-mask producers and consumers require one bind group per packet"
                        }
                    }
                    if (coverageMaskCore &&
                        (corePrimitiveCoverageMaskPreparedRoutes is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
                            corePrimitiveCoverageMaskPreparedRoutes is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition)
                    ) {
                        streamBridges.filter { bridge ->
                            bridge.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline ||
                                bridge.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                        }
                    } else if (coverageMaskCore) {
                        pipelineBridges + bindGroupBridges
                    } else {
                        pipelineBridges.zip(step.drawPackets)
                            .distinctBy { (_, packet) -> packet.renderPipelineKey }
                            .map { (bridge, _) -> bridge } + bindGroupBridges
                    }
                } else if (directCore) {
                    val pipelineBridges = streamBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
                    }
                    pipelineBridges.zip(step.drawPackets)
                        .distinctBy { (_, packet) -> packet.renderPipelineKey }
                        .map { (bridge, _) -> bridge } +
                        listOfNotNull(
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
                if (textA8) {
                    check(step.drawPackets.size == 1) {
                        "Prepared TextA8 native scopes retain one exact ordered subrun"
                    }
                    val packet = step.drawPackets.single()
                    return targetKeys + buildList {
                        add(
                        key(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            "prepared-text:${packet.packetId.value}:pipeline",
                        ),
                        )
                        add(
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "prepared-text:${packet.packetId.value}:draw-group",
                        ),
                        )
                        add(
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "prepared-text:${packet.packetId.value}:material-group",
                        ),
                        )
                        add(
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "prepared-text:${packet.packetId.value}:atlas-group",
                        ),
                        )
                        if (step.preparedTextBindingsByPacketId[packet.packetId]
                                ?.coverageMaskResource != null
                        ) {
                            add(
                                key(
                                    GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup,
                                    "prepared-text:${packet.packetId.value}:coverage-mask-group",
                                ),
                            )
                        }
                        if (packet.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead) {
                            add(
                                key(
                                    GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup,
                                    "prepared-text:${packet.packetId.value}:destination-group",
                                ),
                            )
                        }
                        add(
                        key(
                            GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            "prepared-text:${packet.packetId.value}:instances",
                        ),
                        )
                    }
                }
                val depthStencilKeys = if (pathCore || clipStencilCore || clipStencilPrefix) {
                    val depthStencilRole = if (clipStencilCore) {
                        GPUFrameResourceRole.ClipDepthStencil
                    } else {
                        GPUFrameResourceRole.PathDepthStencil
                    }
                    step.resourceUses.withIndex().singleOrNull { (_, use) ->
                        use.role == depthStencilRole
                    }?.let { (resourceIndex, _) ->
                        listOf(
                            key(
                                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                resources[resourceIndex + 1],
                            ),
                        )
                    } ?: clipStencilPrefixDepthStencilBinding?.let {
                        listOf(
                            key(
                                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                                GPUPreparedNativeOperandKind.TextureView,
                                it,
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
                if (pathCore || clipStencilCore || coverageMaskCore || clipStencilPrefix) {
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
                    val geometryKeys = if (coverageMaskCore &&
                        (corePrimitiveCoverageMaskPreparedRoutes is
                            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
                            corePrimitiveCoverageMaskPreparedRoutes is
                            GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition)
                    ) {
                        emptyList()
                    } else if (clipStencilPrefix) {
                        listOf(
                            sharedGeometryKey(
                                GPUFrameResourceRole.VertexData,
                                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            ),
                            sharedGeometryKey(
                                GPUFrameResourceRole.IndexData,
                                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            ),
                        )
                    } else {
                        listOf(
                            sharedGeometryKey(
                                GPUFrameResourceRole.VertexData,
                                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                            ),
                            sharedGeometryKey(
                                GPUFrameResourceRole.IndexData,
                                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            ),
                        )
                    }
                    val bindGroupKeys = nativeBridges.filter {
                        it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
                    }.map(::bridgeKey)
                    if (clipStencilPrefix) {
                        targetKeys + depthStencilKeys + pipelineKeys + bindGroupKeys + geometryKeys
                    } else if (coverageMaskCore && geometryKeys.isEmpty()) {
                        targetKeys + nativeBridges.map(::bridgeKey)
                    } else {
                        targetKeys + depthStencilKeys + pipelineKeys + geometryKeys + bindGroupKeys
                    }
                } else {
                    val bridgeKeys = nativeBridges.map { bridge ->
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
                            else -> error(
                                "Render native operand bridge contains an unsupported operand kind",
                            )
                        }
                    }
                    if (step.drawPackets.all { packet ->
                            packet.semanticPayload is GPUDrawSemanticPayload.Vertices
                        }
                    ) {
                        val destinationBindGroups = step.drawPackets.count { packet ->
                            packet.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead
                        }
                        val firstBuffer = bridgeKeys.indexOfFirst { candidate ->
                            candidate.kind == GPUPreparedNativeOperandKind.Buffer
                        }.let { index -> if (index < 0) bridgeKeys.size else index }
                        targetKeys + bridgeKeys.take(firstBuffer) +
                            List(destinationBindGroups) { index ->
                                key(
                                    GPUPreparedNativeOperandRole.RenderBindGroup,
                                    GPUPreparedNativeOperandKind.BindGroup,
                                    "prepared-vertices:destination-bind-group:$index",
                                )
                            } + bridgeKeys.drop(firstBuffer)
                    } else {
                        targetKeys + bridgeKeys
                    }
                }
            }
            is GPUFrameStep.ComputePassStep -> step.dispatches.mapIndexed { index, dispatch ->
                key(GPUPreparedNativeOperandRole.ComputePipeline, GPUPreparedNativeOperandKind.ComputePipeline, "dispatch.$index:${dispatch.programKey.value}")
            }
            is GPUFrameStep.UploadResourceStep -> when (step.destinationKind) {
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Buffer -> listOf(
                    key(GPUPreparedNativeOperandRole.UploadSource, GPUPreparedNativeOperandKind.Buffer, resources[0]),
                    key(GPUPreparedNativeOperandRole.UploadDestination, GPUPreparedNativeOperandKind.Buffer, resources[1]),
                )
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Texture -> listOf(
                    key(
                        GPUPreparedNativeOperandRole.UploadSource,
                        GPUPreparedNativeOperandKind.Buffer,
                        if (step.r8ResourcePlan != null ||
                            step.materialResourcePlan != null
                        ) {
                            "prepared-text-upload-data:${step.staging.value}"
                        } else {
                            "prepared-image-upload-data:${step.staging.value}"
                        },
                    ),
                    key(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Texture,
                        resources[1],
                        if (step.r8ResourcePlan != null) {
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
                        } else {
                            GPUPreparedNativeOperandOwnership.Borrowed
                        },
                    ),
                )
            }
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
            is GPUFrameStep.LayerCompositeRenderStep -> listOf(
                key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, resources[0]),
                key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "layer-composite:${step.tokenLabel}:pipeline"),
                key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "layer-composite:${step.tokenLabel}:bind-group"),
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
    textureFormatSampleSupport = GPUTextureFormatSampleSupport(textureFormatSampleSupport),
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

/**
 * Rebases one direct CorePrimitive analytic-shape uniform80 slab to exactly one render scope's
 * commands: the frame's shared slab covers every analytic direct packet, but a two-render
 * dst-copy frame materializes each pass through its own pooled run, so each run must own a slab
 * plan whose slots start at zero and per-packet seals whose offsets address only that pass's
 * payloads.
 */
internal fun sliceAnalyticShapeUniformSealsToCommands(
    seals: List<GPUCorePrimitiveAnalyticShapeUniformSeal>,
    stepCommandIds: List<Int>,
    semanticsByCommandId: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
): List<GPUCorePrimitiveAnalyticShapeUniformSeal> {
    val framePlan = seals.first().plan
    val sealByCommand = seals.associateBy { it.commandId }
    var nextOffset = 0L
    val slots = stepCommandIds.map { commandId ->
        val seal = sealByCommand.getValue(commandId)
        val frameSlot = framePlan.slots[seal.slotIndex]
        val rebased = GPUUniformSlabSlot(
            slotLabel = frameSlot.slotLabel,
            payloadHash = frameSlot.payloadHash,
            payloadBytes = frameSlot.payloadBytes,
            alignedOffset = nextOffset,
            allocatedBytes = frameSlot.allocatedBytes,
        )
        nextOffset += frameSlot.allocatedBytes
        rebased
    }
    val totalBytes = (
        (nextOffset + framePlan.alignmentBytes - 1L) / framePlan.alignmentBytes
        ) * framePlan.alignmentBytes
    val slicedPlan = GPUUniformSlabPlan(
        planHash = framePlan.planHash,
        sourceLabel = framePlan.sourceLabel,
        deviceGeneration = framePlan.deviceGeneration,
        alignmentBytes = framePlan.alignmentBytes,
        totalBytes = totalBytes,
        uploadBudgetBytes = framePlan.uploadBudgetBytes,
        slots = slots,
    )
    return stepCommandIds.mapIndexed { index, commandId ->
        val seal = sealByCommand.getValue(commandId)
        GPUCorePrimitiveAnalyticShapeUniformSeal(
            plan = slicedPlan,
            slotIndex = index,
            commandId = seal.commandId,
            packetId = seal.packetId,
            semanticAuthority = GPUCorePrimitivePreparedSemanticAuthority.capture(
                semanticsByCommandId.getValue(commandId),
            ),
            renderScissor = seal.renderScissor,
            structuralPipelineKey = seal.structuralPipelineKey,
            renderPipelineKey = seal.renderPipelineKey,
            bindingLayoutHash = seal.bindingLayoutHash,
            resourceGeneration = seal.resourceGeneration,
            payloadBytes = seal.payloadBytesSnapshot(),
        )
    }
}

/**
 * Rebases one layout's analytic-clip uniform64 seals to exactly one render scope's commands,
 * mirroring [sliceAnalyticShapeUniformSealsToCommands]: the step owns a zero-based sliced plan
 * and its own packed upload so the per-render-scope run materializer binds exact offsets.
 */
internal fun sliceAnalyticClipUniformSealsToCommands(
    seals: List<GPUCorePrimitiveAnalyticClipUniformSeal>,
    stepCommandIds: List<Int>,
): List<GPUCorePrimitiveAnalyticClipUniformSeal> {
    val framePlan = seals.first().plan
    val sealByCommand = seals.associateBy { it.commandId }
    var nextOffset = 0L
    val slots = stepCommandIds.map { commandId ->
        val seal = sealByCommand.getValue(commandId)
        val frameSlot = framePlan.slots[seal.slotIndex]
        val rebased = GPUUniformSlabSlot(
            slotLabel = frameSlot.slotLabel,
            payloadHash = frameSlot.payloadHash,
            payloadBytes = frameSlot.payloadBytes,
            alignedOffset = nextOffset,
            allocatedBytes = frameSlot.allocatedBytes,
        )
        nextOffset += frameSlot.allocatedBytes
        rebased
    }
    val totalBytes = (
        (nextOffset + framePlan.alignmentBytes - 1L) / framePlan.alignmentBytes
        ) * framePlan.alignmentBytes
    val slicedPlan = GPUUniformSlabPlan(
        planHash = framePlan.planHash,
        sourceLabel = framePlan.sourceLabel,
        deviceGeneration = framePlan.deviceGeneration,
        alignmentBytes = framePlan.alignmentBytes,
        totalBytes = totalBytes,
        uploadBudgetBytes = framePlan.uploadBudgetBytes,
        slots = slots,
    )
    return stepCommandIds.mapIndexed { index, commandId ->
        val seal = sealByCommand.getValue(commandId)
        GPUCorePrimitiveAnalyticClipUniformSeal(
            plan = slicedPlan,
            slotIndex = index,
            commandId = seal.commandId,
            packetId = seal.packetId,
            clipCanonicalIdentity = seal.clipCanonicalIdentity,
            clipType = seal.clipType,
            clipBounds = seal.clipBounds,
            clipRadii = seal.clipRadii,
            antiAlias = seal.antiAlias,
            conservativeScissor = seal.conservativeScissor,
            structuralPipelineKey = seal.structuralPipelineKey,
            renderPipelineKey = seal.renderPipelineKey,
            bindingLayoutHash = seal.bindingLayoutHash,
            resourceGeneration = seal.resourceGeneration,
            payloadBytes = seal.payloadBytesSnapshot(),
        )
    }
}

/**
 * Rebases one layout's analytic-intersection uniform160 seals to exactly one render scope's
 * commands, mirroring [sliceAnalyticClipUniformSealsToCommands] for the uniform64 lane.
 */
internal fun sliceAnalyticIntersectionUniformSealsToCommands(
    seals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal>,
    stepCommandIds: List<Int>,
): List<GPUCorePrimitiveAnalyticIntersectionUniformSeal> {
    val framePlan = seals.first().plan
    val sealByCommand = seals.associateBy { it.commandId }
    var nextOffset = 0L
    val slots = stepCommandIds.map { commandId ->
        val seal = sealByCommand.getValue(commandId)
        val frameSlot = framePlan.slots[seal.slotIndex]
        val rebased = GPUUniformSlabSlot(
            slotLabel = frameSlot.slotLabel,
            payloadHash = frameSlot.payloadHash,
            payloadBytes = frameSlot.payloadBytes,
            alignedOffset = nextOffset,
            allocatedBytes = frameSlot.allocatedBytes,
        )
        nextOffset += frameSlot.allocatedBytes
        rebased
    }
    val totalBytes = (
        (nextOffset + framePlan.alignmentBytes - 1L) / framePlan.alignmentBytes
        ) * framePlan.alignmentBytes
    val slicedPlan = GPUUniformSlabPlan(
        planHash = framePlan.planHash,
        sourceLabel = framePlan.sourceLabel,
        deviceGeneration = framePlan.deviceGeneration,
        alignmentBytes = framePlan.alignmentBytes,
        totalBytes = totalBytes,
        uploadBudgetBytes = framePlan.uploadBudgetBytes,
        slots = slots,
    )
    return stepCommandIds.mapIndexed { index, commandId ->
        val seal = sealByCommand.getValue(commandId)
        GPUCorePrimitiveAnalyticIntersectionUniformSeal(
            plan = slicedPlan,
            slotIndex = index,
            commandId = seal.commandId,
            packetId = seal.packetId,
            clipCanonicalIdentity = seal.clipCanonicalIdentity,
            elements = seal.elements,
            conservativeScissor = seal.conservativeScissor,
            structuralPipelineKey = seal.structuralPipelineKey,
            renderPipelineKey = seal.renderPipelineKey,
            bindingLayoutHash = seal.bindingLayoutHash,
            resourceGeneration = seal.resourceGeneration,
            payloadBytes = seal.payloadBytesSnapshot(),
        )
    }
}

private fun diagnostic(code: String, message: String, facts: Map<String, String> = emptyMap()): GPUDiagnostic =
    preflightDiagnostic(code, message, facts)

/**
 * Rebases one direct CorePrimitive uniform32 slab seal to exactly one render scope's commands:
 * the step owns a zero-based sliced plan and its own packed upload so the per-render-scope run
 * materializer binds exact offsets for the dst-copy lanes and the layout-split steps.
 */
internal fun sliceUniformSlabSealToCommands(
    seal: GPUCorePrimitiveUniformSlabSeal,
    stepCommandIds: List<Int>,
): GPUCorePrimitiveUniformSlabSeal {
    val plan = seal.plan
    val packed = seal.packedBytesForUpload()
    val slotByCommand = plan.slots.indices.associate { index ->
        seal.commandIds[index] to plan.slots[index]
    }
    var nextOffset = 0L
    val slots = stepCommandIds.map { commandId ->
        val slot = slotByCommand.getValue(commandId)
        val rebased = GPUUniformSlabSlot(
            slotLabel = slot.slotLabel,
            payloadHash = slot.payloadHash,
            payloadBytes = slot.payloadBytes,
            alignedOffset = nextOffset,
            allocatedBytes = slot.allocatedBytes,
        )
        nextOffset += slot.allocatedBytes
        rebased
    }
    val coveredBytes = nextOffset
    val totalBytes = (
        (coveredBytes + plan.alignmentBytes - 1L) / plan.alignmentBytes
        ) * plan.alignmentBytes
    val slicedPacked = ByteArray(totalBytes.toInt())
    stepCommandIds.forEachIndexed { index, commandId ->
        val slot = slotByCommand.getValue(commandId)
        val payload = ByteArray(slot.payloadBytes.toInt()) { byteIndex ->
            packed[slot.alignedOffset.toInt() + byteIndex]
        }
        payload.copyInto(slicedPacked, slots[index].alignedOffset.toInt())
    }
    return GPUCorePrimitiveUniformSlabSeal(
        plan = GPUUniformSlabPlan(
            planHash = plan.planHash,
            sourceLabel = plan.sourceLabel,
            deviceGeneration = plan.deviceGeneration,
            alignmentBytes = plan.alignmentBytes,
            totalBytes = totalBytes,
            uploadBudgetBytes = plan.uploadBudgetBytes,
            slots = slots,
        ),
        commandIds = stepCommandIds,
        packedBytes = slicedPacked,
    )
}

/** Maps the passive prepared-image clip handoff to the stable execution refusal contract. */
internal fun preparedImageClipPreflightDiagnostic(
    validation: GPUPreparedImageClipAuthorityValidation,
    hasScissor: Boolean,
): GPUDiagnostic? = when (validation) {
    GPUPreparedImageClipAuthorityValidation.Accepted -> null
    GPUPreparedImageClipAuthorityValidation.ScissorAuthorityMismatch ->
        diagnostic(
            "invalid.preflight.prepared_image_scissor_authority",
            "Prepared sampled-image packet and semantic scissor authorities differ.",
        )
    GPUPreparedImageClipAuthorityValidation.CoverageMismatch ->
        diagnostic(
            "invalid.preflight.prepared_image_scissor_coverage",
            "Prepared sampled-image scissor must retain one exact coverage plan.",
        )
    GPUPreparedImageClipAuthorityValidation.ExecutionMismatch ->
        diagnostic(
            "invalid.preflight.prepared_image_scissor_execution",
            if (hasScissor) {
                "Prepared sampled-image scissor must retain one exact native execution plan."
            } else {
                "Wide-open prepared sampled images must retain the no-clip execution plan."
            },
        )
}

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
    is GPUFrameStep.LayerTargetPrepareStep -> emptyList()
    is GPUFrameStep.LayerChildrenRenderStep -> emptyList()
    is GPUFrameStep.LayerCompositeRenderStep -> listOf(
        org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef(step.parentTargetLabel),
        org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef(step.sourceLabel),
    )
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

private fun validateMaskBlurSemanticPayload(
    framePlan: GPUFramePlan,
    sourceStepIndex: Int,
    render: GPUFrameStep.RenderPassStep,
    packet: GPUDrawPacket,
    semantic: GPUDrawSemanticPayload.MaskBlur,
): GPUDiagnostic? = null
