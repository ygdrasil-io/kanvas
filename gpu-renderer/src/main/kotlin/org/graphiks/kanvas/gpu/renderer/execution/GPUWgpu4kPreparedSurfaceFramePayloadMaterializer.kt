package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor

/**
 * The sole owner and assembler for the closed mixed
 * {CorePrimitive, SampledImage, TextA8, ColorGlyph} surface route.
 *
 * Pure preflight always runs before the first target borrow, cache acquisition, or native factory
 * call. Core and image resources are then materialized frame-globally and only their operands are
 * reordered into the exact full encoder plan. No child payload or child draft is ever created.
 */
internal class GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val corePrimitiveCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val preparedImageCache: GPUWgpu4kPreparedImageSessionCache,
    private val preparedTextCache: GPUWgpu4kPreparedTextSessionCache,
    private val colorGlyphCache: GPUWgpu4kColorGlyphSessionCache? = null,
    private val preparedImageHandleFactory: GPUPreparedImageNativeHandleFactory,
    private val preparedImageCapabilities: GPUCapabilities,
    private val surfaceBlitCache: GPUWgpu4kSurfaceBlitSessionCache,
    private val surfaceTargetResolver: GPUAcquiredSurfaceNativeTargetResolver =
        GPUAcquiredSurfaceNativeTargetResolver.Unavailable,
    private val corePrimitiveLimits: GPULimits,
    private val preflight: GPUPreparedSurfaceNativePreflight =
        GPUPreparedSurfaceNativePreflight(),
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    override val capabilities: Set<GPUPreparedNativeFrameMaterializerCapability> =
        setOf(GPUPreparedNativeFrameMaterializerCapability.PreparedSurfaceMixedSealed)

    private var consumed = false
    private var closed = false

    @Synchronized
    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        if (closed || consumed) {
            return refused(
                "unsupported.prepared-surface.materializer-state",
                "The mixed prepared-surface materializer is one-shot and already consumed.",
            )
        }
        consumed = true

        val shaderContract = when (
            val validation = validatePreparedImageShader(GPU_PREPARED_IMAGE_WGSL)
        ) {
            is GPUPreparedImageShaderValidationResult.Ready -> validation.shaderContract
            is GPUPreparedImageShaderValidationResult.Refused ->
                return refused(
                    validation.code,
                    "Prepared-image WGSL validation refused before mixed materialization.",
                )
        }
        val accepted = when (
            val result = preflight.validate(
                framePlan,
                encoderPlan,
                resources,
                shaderContract,
                generationSeal,
            )
        ) {
            is GPUPreparedSurfaceNativePreflightResult.Accepted -> result.plan
            is GPUPreparedSurfaceNativePreflightResult.Refused ->
                return refused(result.code, result.message)
        }

        var coreLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var imageOwner: GPUPreparedRenderRunOwnedResources? = null
        var imageAnchor: GPUPreparedNativeCompletionAnchor? = null
        var retainedImageRollbackOwner: AutoCloseable? = null
        var textOwner: GPUPreparedRenderRunOwnedResources? = null
        var textAnchor: GPUPreparedNativeCompletionAnchor? = null
        var retainedTextRollbackOwner: AutoCloseable? = null
        var r8Owner: GPUPreparedRenderRunOwnedResources? = null
        var retainedR8RollbackOwner: AutoCloseable? = null
        var colorGlyphOwner: GPUPreparedRenderRunOwnedResources? = null
        var retainedColorGlyphRollbackOwner: AutoCloseable? = null
        val setupLedger = GPUPreRegistrationNativeHandleLedger()
        var coreMaterializer: GPUWgpu4kCorePrimitiveRenderRunMaterializer? = null
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val targetViewOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )

            val corePlans = accepted.orderedRuns.mapNotNull { run ->
                (run as? GPUPreparedSurfaceNativeRunPlan.Core)?.plan
            }
            val coreReady = if (corePlans.isEmpty()) {
                null
            } else {
                coreMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
                    queue,
                    corePrimitiveCache,
                    corePrimitiveLimits,
                )
                when (
                    val result = coreMaterializer.materializeAcceptedRuns(
                        corePlans,
                        targetTexture,
                        targetView,
                        generationSeal,
                    )
                ) {
                    is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                    is GPUCorePrimitiveRenderRunMaterialization.Refused ->
                        throw PreparedSurfaceMaterializationFailure(result.code, result.message)
                }
            }
            coreLifecycle = coreReady?.leaseLifecycle

            val imageRuns = accepted.orderedRuns.mapNotNull { run ->
                (run as? GPUPreparedSurfaceNativeRunPlan.Image)?.plan
            }
            val imageReady = if (imageRuns.isEmpty()) {
                null
            } else {
                when (
                    val result = GPUWgpu4kPreparedImageRenderRunMaterializer(
                        preparedImageCache,
                        preparedImageHandleFactory,
                        preparedImageCapabilities,
                    ).materializeAcceptedFrame(
                        accepted.imageFrames,
                        imageRuns,
                        generationSeal.deviceGeneration,
                    )
                ) {
                    is GPUPreparedRenderRunMaterialization.Ready -> result
                    is GPUPreparedRenderRunMaterialization.Refused -> {
                        retainedImageRollbackOwner = result.retainedCloseOwner
                        throw PreparedSurfaceMaterializationFailure(result.code, result.message)
                    }
                }
            }
            imageOwner = imageReady?.ownedResources?.singleOrNull()
                as? GPUPreparedRenderRunOwnedResources
            if (imageReady != null && imageOwner == null) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.image-owner",
                    "The frame-global image lot must return one exact transferable owner.",
                )
            }

            imageReady?.uniformUploads.orEmpty().forEach { upload ->
                encodePreparedImageUniformUpload(queue, upload)
            }
            val imageRunByStep = imageRuns.associateBy(
                GPUPreparedSurfaceImageRenderRunPlan::sourceScopeIndex,
            )
            val visibleImageHandles = mutableListOf<AutoCloseable>()
            val finalImageOperands = imageReady?.scopeOperands.orEmpty().map { operand ->
                when (operand) {
                    is GPUPreparedNativeScopeOperand.TextureUpload -> {
                        visibleImageHandles += operand.destination.texture
                        GPUPreparedNativeScopeOperand.TextureUpload(
                            sourceStepIndex = operand.sourceStepIndex,
                            data = operand.data,
                            destination = GPUPreparedNativeTextureOperand(
                                operand.destination.texture,
                                generationSeal.deviceGeneration,
                                GPUPreparedNativeOperandOwnership.Borrowed,
                            ),
                            destinationKey = operand.destinationKey,
                            layout = operand.layout,
                            uploadRole = operand.uploadRole,
                        )
                    }
                    is GPUPreparedNativeScopeOperand.PreparedImageRenderRun -> {
                        val run = imageRunByStep[operand.sourceStepIndex]
                            ?: throw PreparedSurfaceMaterializationFailure(
                                "invalid.prepared-surface.image-run",
                                "A materialized image run is absent from the accepted run partition.",
                            )
                        operand.toTargetBoundRender(
                            run,
                            targetViewOperand,
                            generationSeal,
                            visibleImageHandles,
                        )
                    }
                    else -> throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.image-operand",
                        "The frame-global image lot returned an unsupported operand.",
                    )
                }
            }
            val distinctVisibleImageHandles = visibleImageHandles.distinctByNativeIdentity()
            imageOwner?.detachOwnedHandles(distinctVisibleImageHandles)
            imageAnchor = distinctVisibleImageHandles
                .takeIf(List<AutoCloseable>::isNotEmpty)
                ?.let(::GPUPreparedNativeCompletionAnchor)

            val acceptedR8Uploads = (
                accepted.textPlan?.textureUploads
                    ?.filterIsInstance<GPUPreparedTextTextureUploadPlan.Atlas>()
                    .orEmpty() +
                    accepted.colorGlyphPlan?.atlasUploads.orEmpty()
                )
                .groupBy { upload -> upload.exactScopeKey.sourceStepIndex }
                .map { (_, uploads) ->
                    require(uploads.map { it.exactScopeKey }.distinct().size == 1 &&
                        uploads.all { it.resourcePlan === uploads.first().resourcePlan }
                    ) {
                        "A shared prepared-text upload must retain one exact R8 plan"
                    }
                    uploads.first()
                }
                .sortedBy { upload -> upload.exactScopeKey.sourceStepIndex }
            val preparedR8Resources = if (acceptedR8Uploads.isEmpty()) {
                null
            } else {
                when (
                    val result = GPUWgpu4kPreparedR8FrameMaterializer(device)
                        .materializeAcceptedUploads(
                            acceptedR8Uploads,
                            generationSeal.deviceGeneration,
                        )
                ) {
                    is GPUWgpu4kPreparedR8FrameMaterialization.Ready -> result.resources
                    is GPUWgpu4kPreparedR8FrameMaterialization.Refused -> {
                        retainedR8RollbackOwner = result.retainedCloseOwner
                        throw PreparedSurfaceMaterializationFailure(result.code, result.message)
                    }
                }
            }
            r8Owner = preparedR8Resources?.ownedResources

            val textReady = accepted.textPlan?.let { textPlan ->
                when (
                    val result = GPUWgpu4kPreparedTextRenderRunMaterializer(
                        device,
                        preparedTextCache,
                    ).materializeAcceptedRun(
                        textPlan,
                        generationSeal.deviceGeneration,
                        preparedR8Resources,
                    )
                ) {
                    is GPUPreparedRenderRunMaterialization.Ready -> result
                    is GPUPreparedRenderRunMaterialization.Refused -> {
                        retainedTextRollbackOwner = result.retainedCloseOwner
                        throw PreparedSurfaceMaterializationFailure(result.code, result.message)
                    }
                }
            }
            textOwner = textReady?.ownedResources?.singleOrNull()
                as? GPUPreparedRenderRunOwnedResources
            if (textReady != null && textOwner == null) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.text-owner",
                    "The frame-global prepared-text lot must return one exact transferable owner.",
                )
            }
            textReady?.uniformUploads.orEmpty().forEach { upload ->
                encodePreparedImageUniformUpload(queue, upload)
            }
            val renderStepsByIndex = framePlan.steps.withIndex()
                .mapNotNull { indexed ->
                    (indexed.value as?
                        org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep)
                        ?.let { indexed.index to it }
                }
                .toMap()
            val visibleTextHandles = mutableListOf<AutoCloseable>()
            val finalTextOperands = textReady?.scopeOperands.orEmpty().map { operand ->
                when (operand) {
                    is GPUPreparedNativeScopeOperand.TextureUpload -> {
                        visibleTextHandles += operand.destination.texture
                        GPUPreparedNativeScopeOperand.TextureUpload(
                            sourceStepIndex = operand.sourceStepIndex,
                            data = operand.data,
                            destination = GPUPreparedNativeTextureOperand(
                                operand.destination.texture,
                                generationSeal.deviceGeneration,
                                GPUPreparedNativeOperandOwnership.Borrowed,
                            ),
                            destinationKey = operand.destinationKey,
                            layout = operand.layout,
                            uploadRole = operand.uploadRole,
                        )
                    }
                    is GPUPreparedNativeScopeOperand.PreparedTextRenderRun ->
                        operand.toTargetBoundRender(
                            renderStep = renderStepsByIndex.getValue(operand.sourceStepIndex),
                            target = targetViewOperand,
                            generationSeal = generationSeal,
                            visibleHandles = visibleTextHandles,
                        )
                    else -> throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.text-operand",
                        "The frame-global prepared-text lot returned an unsupported operand.",
                    )
                }
            }
            val distinctVisibleTextHandles = visibleTextHandles.distinctByNativeIdentity()
            textOwner?.detachOwnedHandles(distinctVisibleTextHandles)
            textAnchor = distinctVisibleTextHandles
                .takeIf(List<AutoCloseable>::isNotEmpty)
                ?.let(::GPUPreparedNativeCompletionAnchor)

            val colorGlyphReady = accepted.colorGlyphPlan?.let { colorPlan ->
                val r8Resources = preparedR8Resources
                    ?: throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.color-glyph-r8",
                        "Prepared ColorGlyph requires its accepted frame-local R8 upload.",
                    )
                when (
                    val result = GPUWgpu4kColorGlyphRenderRunMaterializer(
                        device,
                        queue,
                        requireNotNull(colorGlyphCache) {
                            "Prepared ColorGlyph requires the session invariant cache"
                        },
                    ).materializeAcceptedRun(
                        colorPlan,
                        r8Resources,
                        generationSeal.deviceGeneration,
                    )
                ) {
                    is GPUPreparedRenderRunMaterialization.Ready -> result
                    is GPUPreparedRenderRunMaterialization.Refused -> {
                        retainedColorGlyphRollbackOwner = result.retainedCloseOwner
                        throw PreparedSurfaceMaterializationFailure(result.code, result.message)
                    }
                }
            }
            colorGlyphOwner = colorGlyphReady?.ownedResources?.singleOrNull()
                as? GPUPreparedRenderRunOwnedResources
            if (colorGlyphReady != null && colorGlyphOwner == null) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.color-glyph-owner",
                    "The frame-global ColorGlyph lot must return one exact transferable owner.",
                )
            }
            val visibleColorGlyphHandles = mutableListOf<AutoCloseable>()
            val finalColorGlyphOperands = colorGlyphReady?.scopeOperands.orEmpty().map { operand ->
                when (operand) {
                    is GPUPreparedNativeScopeOperand.PreparedColorGlyphRenderRun ->
                        operand.toTargetBoundRender(
                            renderStep = renderStepsByIndex.getValue(operand.sourceStepIndex),
                            target = targetViewOperand,
                            visibleHandles = visibleColorGlyphHandles,
                        )
                    else -> throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.color-glyph-operand",
                        "The frame-global ColorGlyph lot returned an unsupported operand.",
                    )
                }
            }
            val distinctVisibleColorGlyphHandles =
                visibleColorGlyphHandles.distinctByNativeIdentity()

            val targetFormat = framePlan.preparedSurfaceTargetFormat()
                ?: throw PreparedSurfaceMaterializationFailure(
                    "unsupported.prepared-surface.target-format",
                    "The mixed prepared surface requires one exact supported scene-target format.",
                )
            val readbackOperand = accepted.readback?.let { readback ->
                val staging = setupLedger.track(
                    device.createBuffer(
                        BufferDescriptor(
                            size = readback.stagingLease.backingBufferBytes.toULong(),
                            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                            mappedAtCreation = false,
                            label = "Kanvas.frame.preparedSurface.readback",
                        ),
                    ),
                )
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readback.sourceStepIndex,
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
                        originX = readback.request.sourceBounds.left,
                        originY = readback.request.sourceBounds.top,
                        width = readback.layout.width,
                        height = readback.layout.height,
                        bytesPerRow = readback.layout.paddedBytesPerRow,
                        rowsPerImage = readback.layout.rowsPerImage,
                        bufferOffset = readback.layout.bufferOffset,
                        mappedSize = readback.layout.totalBufferBytes,
                        format = targetFormat,
                    ),
                )
            }
            val surfaceOperand = accepted.surfaceChain?.let { surface ->
                val format = surface.descriptor.format.value.preparedSurfaceWgpu4kFormat()
                    ?: throw PreparedSurfaceMaterializationFailure(
                        "unsupported.native-frame-payload.surface-format",
                        "The mixed prepared surface has an unsupported output format.",
                    )
                val cached = surfaceBlitCache.acquire(format)
                GPUPreparedNativeScopeOperand.SurfaceBlit(
                    sourceStepIndex = surface.blitStepIndex,
                    source = GPUPreparedNativeTextureViewOperand(
                        cached.sourceView,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    output = surface.output,
                    pipeline = GPUPreparedNativeRenderPipelineOperand(
                        cached.pipeline,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    bindGroup = GPUPreparedNativeBindGroupOperand(
                        cached.bindGroup,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                )
            }

            val operandsByStep = (
                coreReady?.renderOperands.orEmpty() +
                    finalImageOperands +
                    preparedR8Resources?.uploadOperands.orEmpty() +
                    finalTextOperands +
                    finalColorGlyphOperands +
                    listOfNotNull(readbackOperand, surfaceOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            if (operandsByStep.size != accepted.exactScopeKeys.size) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.operand-partition",
                    "The native prepared-surface lots do not form one exact encoder-scope partition.",
                )
            }
            val orderedOperands = accepted.exactScopeKeys.map { scope ->
                operandsByStep[scope.sourceStepIndex]
                    ?: throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.operand-partition",
                        "A globally preflighted encoder scope has no native operand.",
                    )
            }
            if (orderedOperands.map { it.sourceStepIndex to it.operationKind } !=
                accepted.exactScopeKeys.map { it.sourceStepIndex to it.operationKind }
            ) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.operand-order",
                    "Native mixed operands do not retain the exact encoder order.",
                )
            }
            orderedOperands.zip(accepted.exactScopeKeys).forEach { (operand, scope) ->
                val actual = operand.preparedSurfaceOperandDescriptors()
                val expected = scope.operandKeys.map { key -> key.kind to key.ownership }
                if (actual != expected) {
                    throw PreparedSurfaceMaterializationFailure(
                        "invalid.prepared-surface.operand-topology",
                        "Mixed scope ${scope.sourceStepIndex} ${scope.operationKind} has " +
                            "native descriptors $actual but preflight sealed $expected.",
                    )
                }
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = accepted.frameId,
                    contextIdentity = accepted.contextIdentity,
                    encoderPlanId = accepted.encoderPlanId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = accepted.exactScopeKeys,
                ),
                scopeOperands = orderedOperands,
                scopeOperandKeys = accepted.exactScopeKeys.map(
                    GPUPreparedNativeScopeKey::operandKeys,
                ),
                auxiliaryOwnedHandles = listOfNotNull(
                    imageAnchor,
                    imageOwner,
                    r8Owner,
                    textAnchor,
                    textOwner,
                    colorGlyphOwner,
                ).map { owner ->
                    GPUPreparedNativeAuxiliaryHandle(
                        owner,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    )
                },
                leaseLifecycle = coreLifecycle,
                pathDepthStencilViewAuthority =
                    coreReady?.pathDepthStencilViewAuthority.orEmpty(),
            )
            val draft = GPUPreparedNativeFrameDraft(payload)
            r8Owner?.detachOwnedHandles(
                requireNotNull(preparedR8Resources)
                    .texturesByPlan
                    .values
                    .map(NativeTexture::texture),
            )
            colorGlyphOwner?.detachOwnedHandles(distinctVisibleColorGlyphHandles)
            setupLedger.transferAll()
            coreLifecycle = null
            imageAnchor = null
            imageOwner = null
            r8Owner = null
            textAnchor = null
            textOwner = null
            colorGlyphOwner = null
            GPUPreparedNativeFramePayloadMaterialization.Materialized(draft)
        } catch (failure: Throwable) {
            val rollbackOwner = PreparedSurfaceRollbackOwner(
                listOfNotNull(
                    imageAnchor,
                    imageOwner,
                    r8Owner,
                    textAnchor,
                    textOwner,
                    colorGlyphOwner,
                ),
            )
            val locallyRetainedOwner = rollbackOwner.takeUnless(
                PreparedSurfaceRollbackOwner::closeRetainingFailures,
            )
            val closeOwnerRetained = retainedCloseOwner(
                retainedCloseOwner(
                    retainedCloseOwner(
                        retainedImageRollbackOwner,
                        retainedR8RollbackOwner,
                    ),
                    retainedCloseOwner(
                        retainedTextRollbackOwner,
                        retainedColorGlyphRollbackOwner,
                    ),
                ),
                locallyRetainedOwner,
            )
            val ledgerRetained = setupLedger.takeUnless(
                GPUPreRegistrationNativeHandleLedger::closeRetainingFailures,
            )
            coreLifecycle?.rollbackOrQuarantine()
            val typed = failure as? PreparedSurfaceMaterializationFailure
            GPUPreparedNativeFramePayloadMaterialization.Refused(
                code = typed?.code ?: "failed.prepared-surface.materialization",
                message = typed?.message
                    ?: "Mixed prepared-surface materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
                retainedPreRegistrationLedger = ledgerRetained,
                retainedCloseOwner = closeOwnerRetained,
            )
        } finally {
            coreMaterializer?.close()
        }
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding =
        bindWgpu4kLateSurface(draft, acquiredSurface, surfaceTargetResolver)

    @Synchronized
    override fun close() {
        closed = true
    }

    private fun refused(
        code: String,
        message: String,
    ) = GPUPreparedNativeFramePayloadMaterialization.Refused(code, message)
}

private fun GPUPreparedNativeScopeOperand.PreparedImageRenderRun.toTargetBoundRender(
    run: GPUPreparedSurfaceImageRenderRunPlan,
    target: GPUPreparedNativeTextureViewOperand,
    generationSeal: GPUPreparedGenerationSeal,
    visibleHandles: MutableList<AutoCloseable>,
): GPUPreparedNativeScopeOperand.Render {
    require(sourceStepIndex == run.sourceScopeIndex)
    val commands = buildList {
        drawEntries.forEach { entry ->
            visibleHandles += entry.bindGroup.bindGroup
            add(GPUPreparedNativeRenderCommand.SetPipeline(entry.pipeline))
            add(
                GPUPreparedNativeRenderCommand.SetBindGroup(
                    0,
                    GPUPreparedNativeBindGroupOperand(
                        entry.bindGroup.bindGroup,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    dynamicOffsets = listOf(entry.dynamicUniformOffset),
                ),
            )
            add(
                GPUPreparedNativeRenderCommand.SetScissor(
                    entry.scissor.left,
                    entry.scissor.top,
                    entry.scissor.width,
                    entry.scissor.height,
                ),
            )
            add(
                GPUPreparedNativeRenderCommand.Draw(
                    GPUPreparedNativeDrawCall.Draw(vertexCount = 6),
                ),
            )
        }
    }
    return GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = sourceStepIndex,
        pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = target,
            loadOperation = when (run.renderStep.loadStore.loadOp) {
                "clear" -> GPUPreparedNativeLoadOperation.Clear
                "load" -> GPUPreparedNativeLoadOperation.Load
                else -> error("Unsupported mixed prepared-surface load operation")
            },
            storeOperation = GPUPreparedNativeStoreOperation.Store,
            clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                .takeIf { run.renderStep.loadStore.loadOp == "clear" },
        ),
        commands = commands,
        semanticPayloads = run.packets.map<GPUDrawSemanticPayload.SampledImage, GPUDrawSemanticPayload> {
            it
        },
    )
}

private fun GPUPreparedNativeScopeOperand.PreparedTextRenderRun.toTargetBoundRender(
    renderStep: org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep,
    target: GPUPreparedNativeTextureViewOperand,
    generationSeal: GPUPreparedGenerationSeal,
    visibleHandles: MutableList<AutoCloseable>,
): GPUPreparedNativeScopeOperand.Render {
    val borrowedCommands = commands.map { command ->
        when (command) {
            is GPUPreparedNativeRenderCommand.SetPipeline -> command
            is GPUPreparedNativeRenderCommand.SetBindGroup -> {
                visibleHandles += command.bindGroup.bindGroup
                GPUPreparedNativeRenderCommand.SetBindGroup(
                    index = command.index,
                    bindGroup = GPUPreparedNativeBindGroupOperand(
                        command.bindGroup.bindGroup,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    dynamicOffsets = command.dynamicOffsets,
                )
            }
            is GPUPreparedNativeRenderCommand.SetVertexBuffer -> {
                visibleHandles += command.buffer.buffer
                GPUPreparedNativeRenderCommand.SetVertexBuffer(
                    slot = command.slot,
                    buffer = GPUPreparedNativeBufferOperand(
                        command.buffer.buffer,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                        command.buffer.byteCapacity,
                    ),
                    offset = command.offset,
                    size = command.size,
                    vertexStrideBytes = command.vertexStrideBytes,
                )
            }
            is GPUPreparedNativeRenderCommand.SetScissor -> command
            is GPUPreparedNativeRenderCommand.Draw -> command
            else -> throw PreparedSurfaceMaterializationFailure(
                "invalid.prepared-surface.text-command",
                "Prepared TextA8 emitted a command outside its closed instanced ABI.",
            )
        }
    }
    return GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = sourceStepIndex,
        pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = target,
            loadOperation = when (renderStep.loadStore.loadOp) {
                "clear" -> GPUPreparedNativeLoadOperation.Clear
                "load" -> GPUPreparedNativeLoadOperation.Load
                else -> error("Unsupported mixed prepared-surface load operation")
            },
            storeOperation = GPUPreparedNativeStoreOperation.Store,
            clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                .takeIf { renderStep.loadStore.loadOp == "clear" },
        ),
        commands = borrowedCommands,
        semanticPayloads = semanticPayloads.map<GPUDrawSemanticPayload.TextA8, GPUDrawSemanticPayload> {
            it
        },
    )
}

private fun GPUPreparedNativeScopeOperand.PreparedColorGlyphRenderRun.toTargetBoundRender(
    renderStep: org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep,
    target: GPUPreparedNativeTextureViewOperand,
    visibleHandles: MutableList<AutoCloseable>,
): GPUPreparedNativeScopeOperand.Render {
    commands.forEach { command ->
        when (command) {
            is GPUPreparedNativeRenderCommand.SetBindGroup ->
                visibleHandles += command.bindGroup.bindGroup
            is GPUPreparedNativeRenderCommand.SetVertexBuffer ->
                visibleHandles += command.buffer.buffer
            is GPUPreparedNativeRenderCommand.SetIndexBuffer ->
                visibleHandles += command.buffer.buffer
            is GPUPreparedNativeRenderCommand.SetPipeline,
            is GPUPreparedNativeRenderCommand.SetScissor,
            is GPUPreparedNativeRenderCommand.DrawIndexed,
            -> Unit
            else -> throw PreparedSurfaceMaterializationFailure(
                "invalid.prepared-surface.color-glyph-command",
                "Prepared ColorGlyph emitted a command outside its closed indexed ABI.",
            )
        }
    }
    return GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = sourceStepIndex,
        pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = target,
            loadOperation = when (renderStep.loadStore.loadOp) {
                "clear" -> GPUPreparedNativeLoadOperation.Clear
                "load" -> GPUPreparedNativeLoadOperation.Load
                else -> error("Unsupported mixed prepared-surface load operation")
            },
            storeOperation = GPUPreparedNativeStoreOperation.Store,
            clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                .takeIf { renderStep.loadStore.loadOp == "clear" },
        ),
        commands = commands,
        semanticPayloads =
            semanticPayloads.map<GPUDrawSemanticPayload.ColorGlyph, GPUDrawSemanticPayload> {
                it
            },
    )
}

private fun GPUFramePlan.preparedSurfaceTargetFormat(): GPUTextureFormat? {
    val descriptor = steps
        .filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.PrepareResourcesStep>()
        .flatMap { it.requests }
        .singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
        ?.descriptor as? GPUFrameTextureDescriptor
    return when (descriptor?.format) {
        GPUColorFormat.RGBA8Unorm -> GPUTextureFormat.RGBA8Unorm
        GPUColorFormat.RGBA8UnormSrgb -> GPUTextureFormat.RGBA8UnormSrgb
        GPUColorFormat.BGRA8Unorm -> GPUTextureFormat.BGRA8Unorm
        else -> null
    }
}

private fun String.preparedSurfaceWgpu4kFormat(): GPUTextureFormat? = when (lowercase()) {
    "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
    "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
    "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
    "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
    else -> null
}

private fun List<AutoCloseable>.distinctByNativeIdentity(): List<AutoCloseable> {
    val identities = java.util.Collections.newSetFromMap(
        IdentityHashMap<AutoCloseable, Boolean>(),
    )
    return filter(identities::add)
}

private fun GPUPreparedNativeScopeOperand.preparedSurfaceOperandDescriptors(): List<
    Pair<GPUPreparedNativeOperandKind, GPUPreparedNativeOperandOwnership>
    > = when (this) {
    is GPUPreparedNativeScopeOperand.TextureUpload -> listOf(
        data.key.kind to data.key.ownership,
        destination.nativeKind() to destination.ownership,
    )
    is GPUPreparedNativeScopeOperand.SurfaceBlit -> listOf(
        source.nativeKind() to source.ownership,
        GPUPreparedNativeOperandKind.TextureView to
            GPUPreparedNativeOperandOwnership.Borrowed,
        pipeline.nativeKind() to pipeline.ownership,
        bindGroup.nativeKind() to bindGroup.ownership,
    )
    else -> operands.map { operand -> operand.nativeKind() to operand.ownership }
}

private fun GPUPreparedNativeFrameLeaseLifecycle.rollbackOrQuarantine() {
    if (releaseBeforeSubmit() is GPUPreparedNativeFrameLeaseTransition.Applied) return
    quarantineUncertain()
}

private class PreparedSurfaceRollbackOwner(
    owners: List<AutoCloseable>,
) : AutoCloseable {
    private val pending = owners.asReversed().distinctByNativeIdentity().toMutableList()

    @Synchronized
    fun closeRetainingFailures(): Boolean {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (_: Throwable) {
                // Retain the exact owner so the adapter can quarantine and retry it.
            }
        }
        return pending.isEmpty()
    }

    override fun close() {
        check(closeRetainingFailures()) {
            "Prepared-surface rollback retains ${pending.size} native owner(s)"
        }
    }
}

private fun retainedCloseOwner(
    childRetainedOwner: AutoCloseable?,
    locallyRetainedOwner: AutoCloseable?,
): AutoCloseable? {
    val retained = listOfNotNull(
        childRetainedOwner,
        locallyRetainedOwner,
    ).distinctByNativeIdentity()
    return when (retained.size) {
        0 -> null
        1 -> retained.single()
        else -> PreparedSurfaceRollbackOwner(retained)
    }
}

private class PreparedSurfaceMaterializationFailure(
    val code: String,
    message: String,
) : RuntimeException(message)
