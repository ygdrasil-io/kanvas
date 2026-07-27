package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor

/**
 * The sole owner and assembler for the closed mixed {CorePrimitive, SampledImage} surface route.
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
    private val preparedImageHandleFactory: GPUPreparedImageNativeHandleFactory,
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

        val accepted = when (
            val result = preflight.validate(
                framePlan,
                encoderPlan,
                resources,
                preparedImageShaderContract(),
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
            coreMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
                queue,
                corePrimitiveCache,
                corePrimitiveLimits,
            )
            val coreReady = when (
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
            coreLifecycle = coreReady.leaseLifecycle

            val imageRuns = accepted.orderedRuns.mapNotNull { run ->
                (run as? GPUPreparedSurfaceNativeRunPlan.Image)?.plan
            }
            val imageReady = when (
                val result = GPUWgpu4kPreparedImageRenderRunMaterializer(
                    preparedImageCache,
                    preparedImageHandleFactory,
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
            imageOwner = imageReady.ownedResources.singleOrNull()
                as? GPUPreparedRenderRunOwnedResources
                ?: throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.image-owner",
                    "The frame-global image lot must return one exact transferable owner.",
                )

            imageReady.uniformUploads.forEach { upload ->
                encodePreparedImageUniformUpload(queue, upload)
            }
            val imageRunByStep = imageRuns.associateBy(
                GPUPreparedSurfaceImageRenderRunPlan::sourceScopeIndex,
            )
            val visibleImageHandles = mutableListOf<AutoCloseable>()
            val finalImageOperands = imageReady.scopeOperands.map { operand ->
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
            imageOwner.detachOwnedHandles(distinctVisibleImageHandles)
            imageAnchor = GPUPreparedNativeCompletionAnchor(distinctVisibleImageHandles)

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
                coreReady.renderOperands +
                    finalImageOperands +
                    listOfNotNull(readbackOperand, surfaceOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            if (operandsByStep.size != accepted.exactScopeKeys.size) {
                throw PreparedSurfaceMaterializationFailure(
                    "invalid.prepared-surface.operand-partition",
                    "The native Core/Image lots do not form one exact encoder-scope partition.",
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
                auxiliaryOwnedHandles = listOf(
                    GPUPreparedNativeAuxiliaryHandle(
                        requireNotNull(imageAnchor),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    GPUPreparedNativeAuxiliaryHandle(
                        requireNotNull(imageOwner),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
                leaseLifecycle = coreLifecycle,
                pathDepthStencilViewAuthority =
                    coreReady.pathDepthStencilViewAuthority,
            )
            val draft = GPUPreparedNativeFrameDraft(payload)
            setupLedger.transferAll()
            coreLifecycle = null
            imageAnchor = null
            imageOwner = null
            GPUPreparedNativeFramePayloadMaterialization.Materialized(draft)
        } catch (failure: Throwable) {
            val rollbackOwner = PreparedSurfaceRollbackOwner(
                listOfNotNull(imageAnchor, imageOwner),
            )
            val locallyRetainedOwner = rollbackOwner.takeUnless(
                PreparedSurfaceRollbackOwner::closeRetainingFailures,
            )
            val closeOwnerRetained = retainedCloseOwner(
                retainedImageRollbackOwner,
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
