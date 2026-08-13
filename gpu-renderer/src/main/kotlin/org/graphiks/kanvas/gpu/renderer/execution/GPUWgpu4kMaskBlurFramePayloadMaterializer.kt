package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TextureDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurLocalGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurStage
import org.graphiks.kanvas.gpu.renderer.payloads.MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.maskBlurStageFromRenderStepId
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.topLevelMaskBlurCompositeClipRefusal
import org.graphiks.kanvas.gpu.renderer.recording.topLevelMaskBlurCompositeRectClipOrNull
import org.graphiks.kanvas.gpu.renderer.recording.GPUTopLevelMaskBlurCompositeRectClip
import org.graphiks.kanvas.gpu.renderer.recording.topLevelMaskBlurScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/**
 * Materializes the prepared top-level mask blur lane (Task 11): the closed five-stage
 * chain per blur draw (local shape coverage -> blur-h -> blur-v -> style -> scene
 * composite) plus the surrounding scene renders, destination snapshot, and readback.
 *
 * The chain mirrors the legacy dispatcher semantics (GPUMaskBlurDispatch.kt): local-space
 * analytic coverage, separable Gaussian with the legacy kernel ABI, style formulas, and
 * color x coverage shading (SRC_OVER / SRC fixed-function, or the destination-read
 * formula with a scene snapshot for advanced blends). Up to one non-blur core render may
 * ride the frame (materialized via the pooled core run materializer); wider core mixes
 * are refused with the documented scope code.
 */
internal class GPUWgpu4kMaskBlurFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val maskBlurCache: GPUWgpu4kMaskBlurSessionCache,
    private val corePrimitiveCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
    private val onDestinationSnapshotCreated: () -> Unit = {},
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var consumed = false
    private var materializing = false
    private var closed = false

    private data class StageEntry(
        val stage: GPUMaskBlurStage,
        val step: GPUFrameStep.RenderPassStep,
        val scope: GPUCommandEncoderScopePlan,
        val semantic: GPUDrawSemanticPayload,
        val packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
    )

    private data class ChainEntry(
        val commandId: Int,
        val semantic: GPUDrawSemanticPayload.MaskBlur,
        val stages: Map<GPUMaskBlurStage, StageEntry>,
        val ordered: List<StageEntry>,
        val localWidth: Int,
        val localHeight: Int,
    )

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        synchronized(this) {
            if (closed || consumed) {
                return refused(
                    "unsupported.native-mask-blur.materializer-state",
                    "The mask blur materializer is one-shot and already consumed.",
                )
            }
            consumed = true
        }
        return try {
            materializeMaskBlurFrame(framePlan, encoderPlan, resources, generationSeal)
        } catch (failure: Throwable) {
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-mask-blur.materialization",
                "Public wgpu4k mask blur materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun materializeMaskBlurFrame(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        fun invalid(suffix: String, message: String) = refused(
            "invalid.native-mask-blur.$suffix",
            message,
        )


        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.isEmpty()) {
            return invalid("render-scope", "Top-level mask blur requires at least one render scope.")
        }

        val coreRuns = mutableListOf<StageEntry>()
        val solidRuns = mutableListOf<StageEntry>()
        val stageEntries = mutableListOf<StageEntry>()
        for (step in renderSteps) {
            val packets = step.drawPackets
            when (val semantic = packets.firstOrNull()?.semanticPayload) {
                is GPUDrawSemanticPayload.MaskBlur -> {
                    if (packets.size != 1) {
                        return invalid(
                            "packet-count",
                            "Every mask blur stage scope requires exactly one packet.",
                        )
                    }
                    val maskBlurSemantic = semantic
                    val packet = packets.single()
                    val stage = maskBlurStageFromRenderStepId(packet.renderStepId.value)
                        ?: return invalid(
                            "stage",
                            "Mask blur packet ${packet.packetId.value} rides an unknown stage " +
                                step.drawPackets.single().renderStepId.value,
                        )
                    if (!semantic.hasCanonicalHashIntegrity() ||
                        packet!!.commandIdValue != semantic.payloadRef.commandIdValue ||
                        packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                        packet.vertexSourceLabel != TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL
                    ) {
                        return invalid(
                            "packet-authority",
                            "A mask blur packet contradicts its immutable stage or semantic authority.",
                        )
                    }
                    val scope = encoderPlan.scopes.singleOrNull {
                        it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                            it.operationKind == GPUEncoderOperationKind.Render
                    } ?: return invalid(
                        "render-plan",
                        "The ${stage.wireId} mask blur scope is not executable.",
                    )
                    stageEntries += StageEntry(stage, step, scope, maskBlurSemantic, packet)
                }
                is GPUDrawSemanticPayload.CorePrimitive -> {
                    if (packets.size != 1) {
                        return invalid(
                            "packet-count",
                            "Every core render scope inside a mask blur frame requires exactly one packet.",
                        )
                    }
                    val scope = encoderPlan.scopes.singleOrNull {
                        it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                            it.operationKind == GPUEncoderOperationKind.Render
                    } ?: return invalid(
                        "render-plan",
                        "A core render scope is not executable.",
                    )
                    coreRuns += StageEntry(GPUMaskBlurStage.Composite, step, scope, semantic, packets.single())
                }
                is GPUDrawSemanticPayload.SolidRect -> {
                    val scope = encoderPlan.scopes.singleOrNull {
                        it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                            it.operationKind == GPUEncoderOperationKind.Render
                    } ?: return invalid(
                        "render-plan",
                        "A solid render scope is not executable.",
                    )
                    if (packets.any { solidPacket ->
                            !solidPacket.blendPlan.isCanonicalSolidRectSrcOver() ||
                                solidPacket.semanticPayload?.payloadRef?.uniformBlock == null
                        }
                    ) {
                        return invalid(
                            "solid-authority",
                            "SolidRect scene renders inside mask blur frames require the canonical src-over authority.",
                        )
                    }
                    // Batched solid renders (the planner coalesces adjacent same-segment
                    // renders) carry one packet per draw in a single scope.
                    packets.forEach { solidPacket ->
                        solidRuns += StageEntry(
                            GPUMaskBlurStage.Composite,
                            step,
                            scope,
                            solidPacket.semanticPayload as? GPUDrawSemanticPayload.SolidRect
                                ?: return invalid("solid-semantic", "A solid packet lost its typed semantic."),
                            solidPacket,
                        )
                    }
                }
                else -> return invalid(
                    "semantic-payload",
                    "Top-level mask blur frames accept only CorePrimitive and MaskBlur packets.",
                )
            }
        }
        if (coreRuns.size > 1) {
            return refused(
                "unsupported.native-mask-blur.multi-core-render",
                "The top-level mask blur lane accepts at most one non-blur core render per frame; " +
                    "observed ${coreRuns.size}.",
            )
        }
        if (stageEntries.isEmpty()) {
            return invalid("chain-count", "Top-level mask blur requires at least one blur chain.")
        }

        val chains = stageEntries.groupBy { entry -> entry.semantic.payloadRef.commandIdValue }
            .map { (commandId, entries) ->
                val chainSemantic = (entries.first().semantic as GPUDrawSemanticPayload.MaskBlur)
                val ordered = entries.sortedBy { entry ->
                    framePlan.steps.indexOf(entry.step)
                }
                val byStage = ordered.associateBy(StageEntry::stage)
                val expectedStages = listOf(
                    GPUMaskBlurStage.Mask,
                    GPUMaskBlurStage.BlurH,
                    GPUMaskBlurStage.BlurV,
                    GPUMaskBlurStage.Style,
                    GPUMaskBlurStage.Composite,
                )
                if (byStage.keys != expectedStages.toSet() ||
                    ordered.map(StageEntry::stage) != expectedStages
                ) {
                    return invalid(
                        "chain-shape",
                        "Mask blur chain $commandId must contain the exact ordered five stages.",
                    )
                }
                val semantic = chainSemantic
                ChainEntry(
                    commandId = commandId,
                    semantic = semantic,
                    stages = byStage,
                    ordered = ordered,
                    localWidth = semantic.localWidth,
                    localHeight = semantic.localHeight,
                )
            }
        val localBounds = chains.first().let { chain ->
            GPUPixelBounds(0, 0, chain.localWidth, chain.localHeight)
        }
        if (chains.any { chain ->
                chain.localWidth != localBounds.width || chain.localHeight != localBounds.height
            }
        ) {
            return refused(
                "unsupported.native-mask-blur.mixed-local-sizes",
                "The top-level mask blur lane serializes one local mask size per frame.",
            )
        }
        val localScissor = topLevelMaskBlurScissorAuthority(localBounds)

        // Frame-level scope shape: prepare + renders + optional one copy + optional one readback.
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        if (copySteps.size > 1 || framePlan.steps.any { step ->
                step !is GPUFrameStep.PrepareResourcesStep &&
                    step !is GPUFrameStep.RenderPassStep &&
                    step !is GPUFrameStep.CopyDestinationStep &&
                    step !is GPUFrameStep.ReadbackCopyStep
            }
        ) {
            return invalid(
                "scope-shape",
                "The mask blur lane accepts prepare, render, one destination copy, and readback scopes only.",
            )
        }
        val copyStep = copySteps.singleOrNull()
        val copyScope = copyStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.CopyDestination
            } ?: return invalid("copy-plan", "The destination snapshot copy scope is not executable.")
        }
        val readbackStep = readbackSteps.singleOrNull()
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return invalid("readback-plan", "The mask blur readback scope is not executable.")
        }
        if (encoderPlan.scopes.any { scope ->
                framePlan.steps.getOrNull(scope.sourceStepIndex) == null
            }
        ) {
            return invalid("scope-order", "Mask blur encoder scopes must exactly cover the ordered frame steps.")
        }

        // Resource evidence: every blur render target must carry one exact texture declaration.
        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        fun exactPrepared(
            preparation: GPUResourcePreparationRequest,
            texture: Boolean,
        ): Boolean {
            val expectedGeneration = generationSeal.resourceGenerations[preparation.resource] ?: return false
            val evidence = evidenceByResource[preparation.resource] ?: return false
            return evidence.role == preparation.role &&
                evidence.deviceGeneration == generationSeal.deviceGeneration &&
                evidence.resourceGeneration == expectedGeneration &&
                if (texture) {
                    evidence.concreteResource is GPUPreparedConcreteResourceRef.Texture
                } else {
                    evidence.concreteResource is GPUPreparedConcreteResourceRef.Buffer
                }
        }
        chains.forEach { chain ->
            val stageTargets = listOf(
                GPUMaskBlurStage.Mask,
                GPUMaskBlurStage.BlurH,
                GPUMaskBlurStage.BlurV,
                GPUMaskBlurStage.Style,
            ).map { stage -> chain.stages.getValue(stage).step.target }
            if (stageTargets.distinct().size != 4) {
                return invalid(
                    "target-alias",
                    "Mask blur chain ${chain.commandId} local targets must be distinct.",
                )
            }
            stageTargets.forEach { target ->
                val preparation = preparationByResource[target]
                    ?: return invalid(
                        "target-preparation",
                        "Mask blur chain ${chain.commandId} lost a local target preparation.",
                    )
                if (!exactPrepared(preparation, texture = true)) {
                    return invalid(
                        "prepared-resources",
                        "Mask blur intermediate texture declarations are missing or substituted.",
                    )
                }
            }
        }

        // Per-stage pass authority: load/store, scissors, sample chains, target state.
        val clearLoadStore = GPULoadStorePlan("clear", GPUStorePlan.Store)
        chains.forEach { chain ->
            val stages = chain.stages
            val mask = stages.getValue(GPUMaskBlurStage.Mask)
            val blurH = stages.getValue(GPUMaskBlurStage.BlurH)
            val blurV = stages.getValue(GPUMaskBlurStage.BlurV)
            val style = stages.getValue(GPUMaskBlurStage.Style)
            val composite = stages.getValue(GPUMaskBlurStage.Composite)
            listOf(mask, blurH, blurV, style).forEach { entry ->
                if (entry.step.samplePlan != org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame ||
                    entry.step.loadStore != clearLoadStore
                ) {
                    return invalid(
                        "pass-state",
                        "Every mask blur local stage requires one single-sample clear-and-store pass.",
                    )
                }
            }
            if (composite.step.loadStore !in setOf(
                    GPULoadStorePlan("load", GPUStorePlan.Store),
                    GPULoadStorePlan("clear", GPUStorePlan.Store),
                )
            ) {
                return invalid(
                    "composite-load",
                    "The mask blur composite requires one load-and-store or clear-and-store scene pass.",
                )
            }
            if (mask.step.drawPackets.single().scissorBoundsHash != localScissor ||
                blurH.step.drawPackets.single().scissorBoundsHash != localScissor ||
                blurV.step.drawPackets.single().scissorBoundsHash != localScissor ||
                style.step.drawPackets.single().scissorBoundsHash != localScissor
            ) {
                return invalid(
                    "local-scissor",
                    "Mask blur local stages must retain the full local mask scissor authority.",
                )
            }
            val compositeScissor = chain.semantic.scissorBounds
            if (composite.step.drawPackets.single().scissorBoundsHash != topLevelMaskBlurScissorAuthority(compositeScissor)) {
                return invalid(
                    "composite-scissor",
                    "The mask blur composite must retain the semantic scissor authority.",
                )
            }
            if (mask.step.drawPackets.single().renderStepId.value != TOP_LEVEL_MASK_BLUR_MASK_STEP ||
                blurH.step.drawPackets.single().renderStepId.value != TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP ||
                blurV.step.drawPackets.single().renderStepId.value != TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP ||
                style.step.drawPackets.single().renderStepId.value != TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP ||
                composite.step.drawPackets.single().renderStepId.value != MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY
            ) {
                return invalid("step-identity", "Mask blur stage steps lost their closed render identities.")
            }
            if (mask.step.drawPackets.single().targetStateHash != TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK ||
                blurH.step.drawPackets.single().targetStateHash != TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL ||
                blurV.step.drawPackets.single().targetStateHash != TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL ||
                style.step.drawPackets.single().targetStateHash != TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL ||
                composite.step.drawPackets.single().targetStateHash != TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE
            ) {
                return invalid("target-state", "Mask blur stage target state hashes were substituted.")
            }
        }
        val unsupportedClipComposite = chains.firstOrNull { chain ->
            topLevelMaskBlurCompositeClipRefusal(
                chain.stages.getValue(GPUMaskBlurStage.Composite).packet,
            ) != null
        }
        if (unsupportedClipComposite != null) {
            return refused(
                "unsupported.native-mask-blur.clip",
                "The top-level mask blur composite applies only NoClip or ScissorOnly clip execution; " +
                    "the composite clip is outside the lane scope.",
            )
        }
        val dstReadComposites = chains.filter { chain ->
            chain.stages.getValue(GPUMaskBlurStage.Composite).packet.blendPlan
                ?.destinationReadRequirement ==
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
                    .DestinationTextureRequired
        }
        if (dstReadComposites.isNotEmpty() && (copyStep == null || copyScope == null)) {
            return invalid(
                "dst-read-copy",
                "Destination-reading mask blur composites require one exact scene snapshot copy.",
            )
        }
        if (dstReadComposites.isEmpty() && copyStep != null) {
            return invalid(
                "unexpected-copy",
                "A scene snapshot copy without a destination-reading mask blur composite is refused.",
            )
        }

        val (targetTexture, targetView) = preparedSceneTarget.borrow()
        val targetFormat = resolveSceneFormat(framePlan)
            ?: return invalid("scene-format", "The prepared scene format is not resolvable.")
        val cached = maskBlurCache.acquire(localBounds.width, localBounds.height)
        val invariants = cached.invariants
        val intermediates = cached.intermediates
        val compositePipelines = maskBlurCache.acquireCompositePipelines(targetFormat)

        // Core render: delegate to the pooled core run materializer (at most one run).
        var coreLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var coreRunMaterializer: GPUWgpu4kCorePrimitiveRenderRunMaterializer? = null
        val coreOperands = mutableListOf<GPUPreparedNativeScopeOperand.Render>()
        if (coreRuns.isNotEmpty()) {
            val run = coreRuns.single()
            val seal = run.scope.corePrimitiveNativeScopeRouteSeal as?
                GPUCorePrimitiveNativeScopeRouteSeal.Routes
                ?: return invalid(
                    "core-route-seal",
                    "The core render scope requires its pure-preflight route seal.",
                )
            val plan = try {
                GPUCorePrimitiveRenderRunPlan(
                    sourceScopeIndices = listOf(run.scope.sourceStepIndex),
                    packetIds = run.step.drawPackets.map { packet -> packet.packetId },
                    renderStep = run.step,
                    preparationRequests = run.step.resourceUses.map { use ->
                        preparationByResource.getValue(use.resource)
                    },
                    resourceEvidences = run.step.resourceUses.map { use ->
                        evidenceByResource.getValue(use.resource)
                    },
                    routeSeal = seal,
                    exactScopeKey = GPUPreparedNativeScopeKey(
                        run.scope.sourceStepIndex,
                        run.scope.operationKind,
                        run.scope.resourceGenerationLabels,
                        run.scope.nativeOperandKeys,
                    ),
                )
            } catch (failure: Throwable) {
                return invalid(
                    "core-plan",
                    "The core render cannot form its pooled run plan: " +
                        "${failure::class.simpleName.orEmpty()}.",
                )
            }
            coreRunMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
                queue,
                corePrimitiveCache,
                limits,
            )
            when (
                val result = coreRunMaterializer.materializeAcceptedRuns(
                    listOf(plan),
                    targetTexture,
                    targetView,
                    generationSeal,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> {
                    coreOperands += result.renderOperands
                    coreLifecycle = result.leaseLifecycle
                }
                is GPUCorePrimitiveRenderRunMaterialization.Refused ->
                    return refused(result.code, result.message)
            }
        }

        // Non-blur SolidRect scene renders: fullscreen color with the canonical src-over
        // blend and the packet's device-rect scissor (the solid lane authority).
        val solidOperands = mutableListOf<GPUPreparedNativeScopeOperand.Render>()
        val solidUniforms = mutableListOf<io.ygdrasil.webgpu.GPUBuffer>()
        val solidBindGroups = mutableListOf<io.ygdrasil.webgpu.GPUBindGroup>()
        if (solidRuns.isNotEmpty()) {
            val sceneBounds = sceneTargetBounds(framePlan)
                ?: return invalid("scene-bounds", "The prepared scene bounds are not resolvable.")
            solidRuns.groupBy(StageEntry::scope).forEach { (_, runs) ->
                val firstRun = runs.first()
                val draws = runs.map { run ->
                    val block = run.packet.semanticPayload?.payloadRef?.uniformBlock
                        ?: return invalid("solid-uniform", "SolidRect scene renders lost their uniform block.")
                    val uniform = createUniform(
                        "Kanvas.frame.maskBlur.solidUniform16",
                        block.bytes.map { byte -> byte.toByte() }.toByteArray(),
                    )
                    val bindGroup = device.createBindGroup(
                        BindGroupDescriptor(
                            label = "Kanvas.frame.maskBlur.solidBindGroup",
                            layout = invariants.maskBindGroupLayout,
                            entries = listOf(
                                BindGroupEntry(0u, BufferBinding(uniform, 0uL, block.bytes.size.toULong())),
                            ),
                        ),
                    ).tracked()
                    val scissor = when (val parsed = run.packet.solidRectNativeScissor(sceneBounds)) {
                        is SolidRectNativeScissorResult.Valid -> {
                            if (run.packet.scissorBoundsHash == null) {
                                // Batched scene renders inside mask blur frames may lose the
                                // preflighted scissor authority; the uniform block's device
                                // rect is the same authority and restores the per-draw scissor.
                                val bytes = run.semantic.payloadRef?.uniformBlock?.bytes
                                    ?: return invalid("solid-scissor", "A batched solid draw lost its uniform block.")
                                val floats = java.nio.ByteBuffer.wrap(bytes.map { it.toByte() }.toByteArray())
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                val l = floats.float
                                val t = floats.float
                                val r = floats.float
                                val b = floats.float
                                if (l < 0f || t < 0f || r <= l || b <= t ||
                                    r > sceneBounds.right.toFloat() || b > sceneBounds.bottom.toFloat()
                                ) {
                                    return invalid(
                                        "solid-scissor",
                                        "A batched solid draw's device rect is not a valid scissor.",
                                    )
                                }
                                SolidRectNativeScissor(l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt())
                            } else {
                                parsed.scissor
                            }
                        }
                        is SolidRectNativeScissorResult.Invalid -> return invalid(
                            "solid-scissor",
                            parsed.message,
                        )
                    }
                    solidUniforms += uniform
                    solidBindGroups += bindGroup
                    SolidDraw(
                        bindGroup,
                        GPUPixelBounds(
                            scissor.x,
                            scissor.y,
                            scissor.x + scissor.width,
                            scissor.y + scissor.height,
                        ),
                        run.semantic,
                    )
                }
                solidOperands += renderOperand(
                    entry = firstRun,
                    targetView = targetView,
                    pipeline = compositePipelines.solidPipeline,
                    clear = false,
                    draws = draws,
                    generationSeal = generationSeal,
                )
            }
        }

        // Blur chain uniforms, bind groups, and scope operands.
        val formulaOrdinals = listOf(
            "multiply", "screen", "overlay", "darken", "lighten",
            "color_dodge", "color_burn", "hard_light", "soft_light",
            "difference", "exclusion", "hue", "saturation", "color", "luminosity",
        )
        val auxiliaryUniforms = mutableListOf<io.ygdrasil.webgpu.GPUBuffer>()
        val auxiliaryBindGroups = mutableListOf<io.ygdrasil.webgpu.GPUBindGroup>()
        val blurOperands = mutableListOf<GPUPreparedNativeScopeOperand.Render>()
        val compositeEntries = mutableListOf<CompositePendingEntry>()
        chains.forEach { chain ->
            val semantic = chain.semantic
            val maskUniform = createUniform(
                "Kanvas.frame.maskBlur.maskUniform592",
                maskUniformBytes(semantic),
            )
            val blurUniform = createUniform(
                "Kanvas.frame.maskBlur.blurUniform144",
                blurUniformBytes(semantic),
            )
            val styleUniform = createUniform(
                "Kanvas.frame.maskBlur.styleUniform16",
                styleUniformBytes(semantic),
            )
            val compositeUniform = createUniform(
                "Kanvas.frame.maskBlur.compositeUniform32",
                compositeUniformBytes(semantic),
            )
            val compositePacket = chain.stages.getValue(GPUMaskBlurStage.Composite).packet
            val dstRead = compositePacket.blendPlan?.destinationReadRequirement ==
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
                    .DestinationTextureRequired
            val dstUniform = if (dstRead) {
                createUniform(
                    "Kanvas.frame.maskBlur.compositeDstUniform48",
                    compositeDstUniformBytes(
                        semantic,
                        compositePacket.blendPlan?.mode?.gpuLabel.orEmpty(),
                        formulaOrdinals,
                    ),
                )
            } else {
                null
            }
            val rectClip = topLevelMaskBlurCompositeRectClipOrNull(compositePacket)
            val clipUniform = rectClip?.let { clip ->
                createUniform(
                    "Kanvas.frame.maskBlur.compositeClipUniform64",
                    compositeClipUniformBytes(
                        semantic.targetBounds.width,
                        semantic.targetBounds.height,
                        clip,
                        semantic,
                    ),
                )
            }
            val maskBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.maskBlur.maskBindGroup",
                    layout = invariants.maskBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(0u, BufferBinding(maskUniform, 0uL, 592uL)),
                    ),
                ),
            ).tracked()
            val blurHBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.maskBlur.blurHBindGroup",
                    layout = invariants.blurBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(0u, BufferBinding(blurUniform, 0uL, 144uL)),
                        BindGroupEntry(1u, intermediates.maskView),
                        BindGroupEntry(2u, invariants.sampler),
                    ),
                ),
            ).tracked()
            val blurVBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.maskBlur.blurVBindGroup",
                    layout = invariants.blurBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(0u, BufferBinding(blurUniform, 0uL, 144uL)),
                        BindGroupEntry(1u, intermediates.horizontalView),
                        BindGroupEntry(2u, invariants.sampler),
                    ),
                ),
            ).tracked()
            val styleBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.maskBlur.styleBindGroup",
                    layout = invariants.styleBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(0u, BufferBinding(styleUniform, 0uL, 16uL)),
                        BindGroupEntry(1u, intermediates.verticalView),
                        BindGroupEntry(2u, invariants.sampler),
                        BindGroupEntry(3u, intermediates.maskView),
                        BindGroupEntry(4u, invariants.sampler),
                    ),
                ),
            ).tracked()
            auxiliaryUniforms += maskUniform
            auxiliaryUniforms += blurUniform
            auxiliaryUniforms += styleUniform
            auxiliaryUniforms += compositeUniform
            dstUniform?.let(auxiliaryUniforms::add)
            clipUniform?.let(auxiliaryUniforms::add)
            auxiliaryBindGroups += maskBindGroup
            auxiliaryBindGroups += blurHBindGroup
            auxiliaryBindGroups += blurVBindGroup
            auxiliaryBindGroups += styleBindGroup

            val localStageBindings = mapOf(
                GPUMaskBlurStage.Mask to (invariants.maskPipeline to (maskBindGroup to intermediates.maskView)),
                GPUMaskBlurStage.BlurH to (invariants.blurHPipeline to (blurHBindGroup to intermediates.horizontalView)),
                GPUMaskBlurStage.BlurV to (invariants.blurVPipeline to (blurVBindGroup to intermediates.verticalView)),
                GPUMaskBlurStage.Style to (invariants.stylePipeline to (styleBindGroup to intermediates.styledView)),
            )
            chain.ordered.forEach { entry ->
                if (entry.stage == GPUMaskBlurStage.Composite) {
                    compositeEntries += CompositePendingEntry(
                        entry = entry,
                        chain = chain,
                        uniform = compositeUniform,
                        dstUniform = dstUniform,
                        clipUniform = clipUniform,
                        rectClip = rectClip,
                        scissor = semantic.scissorBounds,
                        blendModeLabel = compositePacket.blendPlan?.mode?.gpuLabel.orEmpty(),
                        dstRead = dstRead,
                    )
                    return@forEach
                }
                val (pipeline, bindings) = localStageBindings.getValue(entry.stage)
                val (bindGroup, view) = bindings
                blurOperands += renderOperand(
                    entry = entry,
                    targetView = view,
                    pipeline = pipeline,
                    bindGroup = bindGroup,
                    clear = true,
                    scissor = localBounds,
                    generationSeal = generationSeal,
                )
            }
        }

        // Destination snapshot + composite operands.
        var dstSnapshotTexture: io.ygdrasil.webgpu.GPUTexture? = null
        var dstSnapshotView: io.ygdrasil.webgpu.GPUTextureView? = null
        val compositeOperands = mutableListOf<GPUPreparedNativeScopeOperand.Render>()
        val dstCopyOperand: GPUPreparedNativeScopeOperand? = if (
            compositeEntries.isNotEmpty() && compositeEntries.first().dstRead
        ) {
            if (copyStep == null || copyScope == null) {
                return invalid("dst-read-copy", "Destination-reading composites lost their copy scope.")
            }
            val snapshotBounds = sceneTargetBounds(framePlan)
                ?: return invalid("scene-bounds", "The prepared scene bounds are not resolvable.")

            // The snapshot mirrors the scene format and copies the FULL target bounds
            // (the recording's CopyDestinationStep layout is the authority), so the
            // composite samples the true scene texel under the blur. The copy is a
            // native GPU texture copy — destination reads never touch the CPU.
            val snapshot = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(snapshotBounds.width.toUInt(), snapshotBounds.height.toUInt(), 1u),
                    format = targetFormat,
                    usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                    label = "Kanvas.frame.maskBlur.destinationSnapshot",
                ),
            ).tracked()

            dstSnapshotTexture = snapshot
            val snapshotView = snapshot.createView().tracked()
            dstSnapshotView = snapshotView
            onDestinationSnapshotCreated()
            // Task 7: an admitted analytic device-rect clip on the composite binds the
            // uniform64 clip block (mirroring the core lane's analytic clip ABI) so the
            // dst-read composite shader multiplies the blurred mask coverage by the
            // clip coverage. The shared dst bind group follows the existing first-chain
            // dst uniform authority for multi-chain dst-read frames.
            val dstReadClip = compositeEntries.first().rectClip
            val dstBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = if (dstReadClip != null) {
                        "Kanvas.frame.maskBlur.compositeDstClipBindGroup"
                    } else {
                        "Kanvas.frame.maskBlur.compositeDstBindGroup"
                    },
                    layout = if (dstReadClip != null) {
                        invariants.compositeDstClipBindGroupLayout
                    } else {
                        invariants.compositeDstBindGroupLayout
                    },
                    entries = buildList {
                        add(BindGroupEntry(0u, BufferBinding(compositeEntries.first().dstUniform!!, 0uL, 48uL)))
                        add(BindGroupEntry(1u, intermediates.styledView))
                        add(BindGroupEntry(2u, invariants.sampler))
                        add(BindGroupEntry(3u, snapshotView))
                        add(BindGroupEntry(4u, invariants.sampler))
                        if (dstReadClip != null) {
                            add(
                                BindGroupEntry(
                                    5u,
                                    BufferBinding(compositeEntries.first().clipUniform!!, 0uL, 64uL),
                                ),
                            )
                        }
                    },
                ),
            ).tracked()
            auxiliaryBindGroups += dstBindGroup
            compositeEntries.forEach { pending ->
                compositeOperands += renderOperand(
                    entry = pending.entry,
                    targetView = targetView,
                    pipeline = if (dstReadClip != null) {
                        maskBlurCache.acquireCompositeClipPipelines(targetFormat).dstPipeline
                    } else {
                        compositePipelines.dstPipeline
                    },
                    bindGroup = dstBindGroup,
                    clear = pending.entry.step.loadStore.loadOp == "clear",
                    scissor = pending.scissor,
                    generationSeal = generationSeal,
                )
            }
            GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = copyScope.sourceStepIndex,
                operationKind = GPUEncoderOperationKind.CopyDestination,
                source = GPUPreparedNativeTextureOperand(
                    targetTexture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                destination = GPUPreparedNativeTextureOperand(
                    snapshot,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = 0,
                    sourceOriginY = 0,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = snapshotBounds.width,
                    height = snapshotBounds.height,
                ),
            )
        } else {
            compositeEntries.forEach { pending ->
                val isSrcOver = pending.blendModeLabel == "src_over"
                val clip = pending.rectClip
                val compositeBindGroup = device.createBindGroup(
                    BindGroupDescriptor(
                        label = if (clip != null) {
                            "Kanvas.frame.maskBlur.compositeClipBindGroup"
                        } else {
                            "Kanvas.frame.maskBlur.compositeBindGroup"
                        },
                        layout = if (clip != null) {
                            invariants.compositeClipBindGroupLayout
                        } else {
                            invariants.compositeBindGroupLayout
                        },
                        entries = buildList {
                            add(BindGroupEntry(0u, BufferBinding(pending.uniform, 0uL, 32uL)))
                            add(BindGroupEntry(1u, intermediates.styledView))
                            add(BindGroupEntry(2u, invariants.sampler))
                            if (clip != null) {
                                add(
                                    BindGroupEntry(
                                        3u,
                                        BufferBinding(pending.clipUniform!!, 0uL, 64uL),
                                    ),
                                )
                            }
                        },
                    ),
                ).tracked()
                auxiliaryBindGroups += compositeBindGroup
                compositeOperands += renderOperand(
                    entry = pending.entry,
                    targetView = targetView,
                    pipeline = when {
                        clip != null && isSrcOver ->
                            maskBlurCache.acquireCompositeClipPipelines(targetFormat).srcOverPipeline
                        clip != null ->
                            maskBlurCache.acquireCompositeClipPipelines(targetFormat).srcPipeline
                        isSrcOver -> compositePipelines.srcOverPipeline
                        else -> compositePipelines.srcPipeline
                    },
                    bindGroup = compositeBindGroup,
                    clear = pending.entry.step.loadStore.loadOp == "clear",
                    scissor = pending.scissor,
                    generationSeal = generationSeal,
                )
            }
            null
        }

        // Readback.
        val readbackOutput = resources.outputOwnedReadbacks.singleOrNull()
        val staging = readbackOutput?.let { output ->
            device.createBuffer(
                BufferDescriptor(
                    size = output.stagingLease.backingBufferBytes.toULong(),
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    mappedAtCreation = false,
                    label = "Kanvas.frame.maskBlur.readback",
                ),
            ).tracked()
        }
        val readbackOperand = if (readbackStep != null && readbackScope != null) {
            if (readbackOutput == null || staging == null) {
                return invalid("readback-output", "The mask blur readback requires one output-owned staging lease.")
            }
            GPUPreparedNativeScopeOperand.Readback(
                sourceStepIndex = readbackScope.sourceStepIndex,
                source = GPUPreparedNativeTextureOperand(
                    targetTexture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                destination = GPUPreparedNativeBufferOperand(
                    staging,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
                layout = GPUPreparedNativeReadbackLayout(
                    originX = readbackOutput.request.sourceBounds.left,
                    originY = readbackOutput.request.sourceBounds.top,
                    width = readbackOutput.layout.width,
                    height = readbackOutput.layout.height,
                    bytesPerRow = readbackOutput.layout.paddedBytesPerRow,
                    rowsPerImage = readbackOutput.layout.rowsPerImage,
                    bufferOffset = readbackOutput.layout.bufferOffset,
                    mappedSize = readbackOutput.layout.totalBufferBytes,
                    format = targetFormat,
                ),
            )
        } else {
            null
        }

        val operandByStepIndex = buildMap<Int, GPUPreparedNativeScopeOperand> {
            coreOperands.forEach { operand -> put(operand.sourceStepIndex, operand) }
            solidOperands.forEach { operand -> put(operand.sourceStepIndex, operand) }
            blurOperands.forEach { operand -> put(operand.sourceStepIndex, operand) }
            compositeOperands.forEach { operand -> put(operand.sourceStepIndex, operand) }
            dstCopyOperand?.let { put(it.sourceStepIndex, it) }
            readbackOperand?.let { put(it.sourceStepIndex, it) }
        }
        if (encoderPlan.scopes.any { scope -> scope.sourceStepIndex !in operandByStepIndex }) {
            return invalid(
                "scope-operands",
                "Mask blur operands must exactly cover every encoder scope.",
            )
        }
        val payload = GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                framePlan.frameId,
                encoderPlan.contextIdentity,
                encoderPlan.planId,
                generationSeal.deviceGeneration,
                generationSeal.targetGeneration,
                encoderPlan.scopes.map { scope ->
                    GPUPreparedNativeScopeKey(
                        scope.sourceStepIndex,
                        scope.operationKind,
                        scope.resourceGenerationLabels,
                        scope.nativeOperandKeys,
                    )
                },
            ),
            scopeOperands = encoderPlan.scopes.map { scope ->
                requireNotNull(operandByStepIndex[scope.sourceStepIndex])
            },
            scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
            auxiliaryOwnedHandles = buildList {
                solidBindGroups.forEach { bindGroup ->
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(listOf(bindGroup)),
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }
                solidUniforms.forEach { uniform ->
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            uniform,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }
                auxiliaryBindGroups.forEach { bindGroup ->
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(listOf(bindGroup)),
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }
                auxiliaryUniforms.forEach { uniform ->
                    // Direct completion auxiliaries: uniforms are referenced only through
                    // bind groups and are not themselves scope-operand handles.
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            uniform,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }

                dstSnapshotTexture?.let { snapshot ->
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(listOf(snapshot)),
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }
                dstSnapshotView?.let { view ->
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            view,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                }
            },
            leaseLifecycle = coreLifecycle,
        )
        val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
            GPUPreparedNativeFrameDraft(payload),
        )
        synchronized(this) {
            check(!closed) { "Mask blur materializer closed during materialization" }
            preRegistrationHandles.transferAll()
            materializing = false
        }
        coreRunMaterializer?.close()
        return result
    }

    private data class CompositePendingEntry(
        val entry: StageEntry,
        val chain: ChainEntry,
        val uniform: io.ygdrasil.webgpu.GPUBuffer,
        val dstUniform: io.ygdrasil.webgpu.GPUBuffer?,
        val clipUniform: io.ygdrasil.webgpu.GPUBuffer?,
        val rectClip: GPUTopLevelMaskBlurCompositeRectClip?,
        val scissor: GPUPixelBounds,
        val blendModeLabel: String,
        val dstRead: Boolean,
    )

    private data class SolidDraw(
        val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        val scissor: GPUPixelBounds,
        val semantic: GPUDrawSemanticPayload,
    )
    private fun renderOperand(
        entry: StageEntry,
        targetView: io.ygdrasil.webgpu.GPUTextureView,
        pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
        bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        clear: Boolean,
        scissor: GPUPixelBounds,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeScopeOperand.Render = renderOperand(
        entry = entry,
        targetView = targetView,
        pipeline = pipeline,
        clear = clear,
        draws = listOf(SolidDraw(bindGroup, scissor, entry.semantic)),
        generationSeal = generationSeal,
    )

    private fun renderOperand(
        entry: StageEntry,
        targetView: io.ygdrasil.webgpu.GPUTextureView,
        pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
        clear: Boolean,
        draws: List<SolidDraw>,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeScopeOperand.Render {
        val borrowed = GPUPreparedNativeOperandOwnership.Borrowed
        return GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = entry.scope.sourceStepIndex,
            pass = GPUPreparedNativeRenderPassConfig(
                colorTarget = GPUPreparedNativeTextureViewOperand(
                    targetView,
                    generationSeal.deviceGeneration,
                    borrowed,
                ),
                resolveTarget = null,
                loadOperation = if (clear) {
                    GPUPreparedNativeLoadOperation.Clear
                } else {
                    GPUPreparedNativeLoadOperation.Load
                },
                storeOperation = GPUPreparedNativeStoreOperation.Store,
                clearColor = if (clear) {
                    GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                } else {
                    null
                },
            ),
            commands = buildList {
                draws.forEach { draw ->
                    // One pipeline + bind-group per draw: the preflighted scope keys
                    // describe a SetPipeline/SetBindGroup sequence per batched draw.
                    add(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            GPUPreparedNativeRenderPipelineOperand(
                                pipeline,
                                generationSeal.deviceGeneration,
                                borrowed,
                            ),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            GPUPreparedNativeBindGroupOperand(
                                draw.bindGroup,
                                generationSeal.deviceGeneration,
                                borrowed,
                            ),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            draw.scissor.left,
                            draw.scissor.top,
                            draw.scissor.width,
                            draw.scissor.height,
                        ),
                    )
                    add(GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)))
                }
            },
            semanticPayloads = draws.map { draw -> draw.semantic },
        )
    }

    private fun maskUniformBytes(semantic: GPUDrawSemanticPayload.MaskBlur): ByteArray {
        val geometry = semantic.localGeometry
        val kind = when (geometry) {
            is GPUMaskBlurLocalGeometry.Rect -> 0
            is GPUMaskBlurLocalGeometry.RRect -> 1
            is GPUMaskBlurLocalGeometry.Path -> 2
        }
        val fillRule = when (geometry) {
            is GPUMaskBlurLocalGeometry.Path ->
                if (geometry.fillRule == "even-odd" || geometry.fillRule == "EvenOdd" ||
                    geometry.fillRule == "evenOdd"
                ) {
                    1
                } else {
                    0
                }
            else -> 0
        }
        val inverse = when (geometry) {
            is GPUMaskBlurLocalGeometry.Path -> if (geometry.inverseFill) 1 else 0
            else -> 0
        }
        val pathVertices = when (geometry) {
            is GPUMaskBlurLocalGeometry.Path -> geometry.vertices
            else -> emptyList()
        }
        return ByteBuffer.allocate(592).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(semantic.localWidth.toFloat())
            putFloat(semantic.localHeight.toFloat())
            putInt(pathVertices.size / 2)
            putInt(kind)
            putInt(fillRule)
            putInt(inverse)
            putInt(0)
            putInt(0)
            when (geometry) {
                is GPUMaskBlurLocalGeometry.Rect -> {
                    putFloat(geometry.left); putFloat(geometry.top)
                    putFloat(geometry.right); putFloat(geometry.bottom)
                }
                is GPUMaskBlurLocalGeometry.RRect -> {
                    putFloat(geometry.left); putFloat(geometry.top)
                    putFloat(geometry.right); putFloat(geometry.bottom)
                    geometry.radii.forEach(::putFloat)
                }
                is GPUMaskBlurLocalGeometry.Path -> {
                    putFloat(0f); putFloat(0f); putFloat(0f); putFloat(0f)
                    repeat(8) { putFloat(0f) }
                }
            }
            for (i in 0 until 32) {
                if (i < pathVertices.size / 2) {
                    putFloat(pathVertices[i * 2])
                    putFloat(pathVertices[i * 2 + 1])
                    putFloat(0f)
                    putFloat(0f)
                } else {
                    putFloat(0f); putFloat(0f); putFloat(0f); putFloat(0f)
                }
            }
        }.array()
    }

    private fun blurUniformBytes(semantic: GPUDrawSemanticPayload.MaskBlur): ByteArray =
        ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(semantic.tapCount)
            putInt(0)
            putFloat(semantic.localWidth.toFloat())
            putFloat(semantic.localHeight.toFloat())
            repeat(4) { putFloat(0f) }
            semantic.weights.forEach(::putFloat)
            repeat(3) { putFloat(0f) }
        }.array()

    private fun styleUniformBytes(semantic: GPUDrawSemanticPayload.MaskBlur): ByteArray =
        ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(semantic.style.ordinal)
            repeat(3) { putInt(0) }
        }.array()

    private fun compositeUniformBytes(semantic: GPUDrawSemanticPayload.MaskBlur): ByteArray =
        ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(semantic.deviceBounds.left)
            putFloat(semantic.deviceBounds.top)
            putFloat(semantic.deviceBounds.right)
            putFloat(semantic.deviceBounds.bottom)
            semantic.premultipliedRgba.forEach(::putFloat)
        }.array()

    private fun compositeDstUniformBytes(
        semantic: GPUDrawSemanticPayload.MaskBlur,
        blendModeLabel: String,
        formulaOrdinals: List<String>,
    ): ByteArray {
        val mode = formulaOrdinals.indexOf(blendModeLabel).takeIf { it >= 0 } ?: 3
        return ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(semantic.deviceBounds.left)
            putFloat(semantic.deviceBounds.top)
            putFloat(semantic.deviceBounds.right)
            putFloat(semantic.deviceBounds.bottom)
            semantic.premultipliedRgba.forEach(::putFloat)
            putInt(mode)
            putInt(0)
        }.array()
    }

    /**
     * Packs the 64-byte analytic clip block for the composite bind group (Task 7),
     * mirroring the core lane's `CorePrimitiveAnalyticClipBlock` ABI byte-for-byte:
     * target_size, clip_type (0 = rect), anti_alias, premul_rgba, clip_bounds,
     * clip_radii (zeroed for a rect).
     */
    private fun compositeClipUniformBytes(
        targetWidth: Int,
        targetHeight: Int,
        clip: GPUTopLevelMaskBlurCompositeRectClip,
        semantic: GPUDrawSemanticPayload.MaskBlur,
    ): ByteArray = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(targetWidth.toFloat())
        putFloat(targetHeight.toFloat())
        putInt(0)
        putInt(if (clip.antiAlias) 1 else 0)
        semantic.premultipliedRgba.forEach(::putFloat)
        putFloat(clip.left)
        putFloat(clip.top)
        putFloat(clip.right)
        putFloat(clip.bottom)
        repeat(4) { putFloat(0f) }
    }.array()

    private fun sceneTargetBounds(framePlan: GPUFramePlan): GPUPixelBounds? {
        val scenePreparation = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .firstOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?: return null
        val descriptor = scenePreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return null
        return descriptor.logicalBounds
    }

    private fun resolveSceneFormat(framePlan: GPUFramePlan): GPUTextureFormat? {
        val scenePreparation = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .firstOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?: return null
        val descriptor = scenePreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return null
        return when (descriptor.format.value) {
            "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
            "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
            "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
            "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
            else -> null
        }
    }

    private fun createUniform(label: String, bytes: ByteArray) = device.createBuffer(
        BufferDescriptor(
            size = bytes.size.toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            label = label,
        ),
    ).tracked().also { buffer ->
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes))
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = if (acquiredSurface == null) {
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    } else {
        GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "unsupported.native-mask-blur.surface",
            "The top-level mask blur route is offscreen-only.",
        )
    }

    override fun close() {
        synchronized(this) {
            closed = true
            if (!materializing) preRegistrationHandles.closeRetainingFailures()
        }
    }

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)
}
