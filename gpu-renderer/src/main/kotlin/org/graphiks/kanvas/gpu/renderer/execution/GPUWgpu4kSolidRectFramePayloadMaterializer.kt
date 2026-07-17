package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

internal class GPUPreparedSceneNativeTargetCloser(
    private val viewHandle: AutoCloseable,
    private val textureHandle: AutoCloseable,
    private val onAllClosed: () -> Unit = {},
) : AutoCloseable {
    private var closeRequested = false
    private var viewClosed = false
    private var textureClosed = false
    private var terminalNotified = false

    @get:Synchronized
    val canBorrow: Boolean get() = !closeRequested

    @get:Synchronized
    val quarantinedHandleCount: Int
        get() = (if (closeRequested && !viewClosed) 1 else 0) +
            (if (closeRequested && !textureClosed) 1 else 0)

    @Synchronized
    override fun close() {
        if (viewClosed && textureClosed) return
        closeRequested = true
        var firstFailure: Throwable? = null
        if (!viewClosed) {
            try {
                viewHandle.close()
                viewClosed = true
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        }
        if (!textureClosed) {
            try {
                textureHandle.close()
                textureClosed = true
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        if (viewClosed && textureClosed && !terminalNotified) {
            terminalNotified = true
            onAllClosed()
        }
        firstFailure?.let { throw it }
    }
}

internal class GPUWgpu4kPreparedSceneTargetLifecycle {
    private var canonicalTexture: GPUTexture? = null
    private var canonicalView: GPUTextureView? = null
    private var creations = 0L
    private var closes = 0L
    private var nativeUses = 0L

    @Synchronized
    fun recordCreation(texture: GPUTexture, view: GPUTextureView) {
        check(canonicalTexture == null && canonicalView == null)
        canonicalTexture = texture
        canonicalView = view
        creations += 1L
    }

    @Synchronized
    fun recordUse(texture: GPUTexture, view: GPUTextureView) {
        check(texture === canonicalTexture && view === canonicalView) {
            "Prepared scene frames must borrow the exact canonical native texture and view"
        }
        nativeUses += 1L
    }

    @Synchronized
    fun recordClose(texture: GPUTexture, view: GPUTextureView) {
        check(texture === canonicalTexture && view === canonicalView)
        closes += 1L
    }

    @Synchronized
    fun snapshot(): Triple<Long, Long, Long> = Triple(creations, closes, nativeUses)
}

internal class GPUWgpu4kPreparedSceneTarget private constructor(
    val texture: GPUTexture,
    val view: GPUTextureView,
    val width: Int,
    val height: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
    private val lifecycle: GPUWgpu4kPreparedSceneTargetLifecycle,
) : AutoCloseable {
    private val closer = GPUPreparedSceneNativeTargetCloser(textureHandle = texture, viewHandle = view) {
        lifecycle.recordClose(texture, view)
    }

    @Synchronized
    fun borrow(): Pair<GPUTexture, GPUTextureView> {
        check(closer.canBorrow) { "Prepared scene target is closing or closed" }
        lifecycle.recordUse(texture, view)
        return texture to view
    }

    override fun close() = closer.close()

    companion object {
        fun create(
            device: GPUDevice,
            width: Int,
            height: Int,
            deviceGeneration: GPUDeviceGenerationID,
            targetGeneration: Long,
            lifecycle: GPUWgpu4kPreparedSceneTargetLifecycle,
        ): GPUWgpu4kPreparedSceneTarget {
            val texture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width.toUInt(), height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "Kanvas.preparedScene.canonicalTarget",
                ),
            )
            return try {
                val view = texture.createView()
                lifecycle.recordCreation(texture, view)
                GPUWgpu4kPreparedSceneTarget(
                    texture,
                    view,
                    width,
                    height,
                    deviceGeneration,
                    targetGeneration,
                    lifecycle,
                )
            } catch (failure: Throwable) {
                texture.close()
                throw failure
            }
        }
    }
}

internal data class GPUWgpu4kSolidRectMaterializationCounters(
    val payloadResourceCreations: Long,
    val targetWidth: Int?,
    val targetHeight: Int?,
    val targetFormat: String?,
    val msaaColorAttachmentCreations: Long = 0L,
    val distinctMsaaColorViews: Int = 0,
)

/**
 * First production materializer for the closed solid-rectangle + RGBA8 readback route.
 *
 * It creates only typed public-wgpu4k resources. Encoding and submission stay exclusively in
 * [GPUWgpu4kFrameEncodingBackend]. Unsupported frame shapes refuse before an encoder is created.
 */
internal class GPUWgpu4kSolidRectFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget? = null,
    private val solidRectCache: GPUWgpu4kSolidRectSessionCache? = null,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var materialized = false
    private var materializing = false
    private var closed = false
    private var payloadResourceCreationCount = 0L
    private var materializedTargetWidth: Int? = null
    private var materializedTargetHeight: Int? = null
    private var materializedTargetFormat: String? = null
    private var msaaColorAttachmentCreationCount = 0L
    private var distinctMsaaColorViewCount = 0

    @Synchronized
    internal fun counters(): GPUWgpu4kSolidRectMaterializationCounters =
        GPUWgpu4kSolidRectMaterializationCounters(
            payloadResourceCreations = payloadResourceCreationCount,
            targetWidth = materializedTargetWidth,
            targetHeight = materializedTargetHeight,
            targetFormat = materializedTargetFormat,
            msaaColorAttachmentCreations = msaaColorAttachmentCreationCount,
            distinctMsaaColorViews = distinctMsaaColorViewCount,
        )

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        synchronized(this) {
            if (closed || materialized) {
                return refused(
                    "unsupported.native-solid-rect.materializer-state",
                    "The first native solid rectangle materializer is one-shot and already consumed.",
                )
            }
            materialized = true
        }
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.isEmpty()) {
            return refused("unsupported.native-solid-rect.render-scope", "At least one render scope is required.")
        }
        val renderStep = renderSteps.first()
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        if (readbackSteps.size > 1) {
            return refused("unsupported.native-solid-rect.readback-scope", "At most one readback scope is accepted.")
        }
        val readbackStep = readbackSteps.singleOrNull()
        if (renderSteps.any { it.target != renderStep.target } ||
            (readbackStep != null && renderStep.target != readbackStep.source)
        ) {
            return refused(
                "unsupported.native-solid-rect.target-substitution",
                "The render target and readback source must be the same canonical target.",
            )
        }
        val renderScopes = renderSteps.map { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Render
            } ?: return refused("unsupported.native-solid-rect.render-plan", "A render scope is not executable.")
        }
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyResourceStep>()
        val copyScopes = copySteps.map { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Copy
            } ?: return refused("unsupported.native-solid-rect.copy-plan", "A copy-break scope is not executable.")
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused("unsupported.native-solid-rect.readback-plan", "The readback scope is not executable.")
        }
        val acceptedScopes = (renderScopes + copyScopes + listOfNotNull(readbackScope))
            .sortedBy { it.sourceStepIndex }
        if (encoderPlan.scopes != acceptedScopes) {
            return refused(
                "unsupported.native-solid-rect.scope-order",
                "The native solid route accepts only ordered render, neutral texture-copy, and readback scopes.",
            )
        }
        val semanticPacketsByRenderStep = renderSteps.map { step ->
            step.drawPackets.map { packet ->
                val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SolidRect
                    ?: return refused(
                        "unsupported.native-solid-rect.semantic-payload",
                        "Every solid rectangle packet requires one canonical semantic payload.",
                    )
                packet to semantic
            }
        }
        val semanticPackets = semanticPacketsByRenderStep.flatten()
        if (semanticPackets.any { (packet, _) -> !packet.blendPlan.isCanonicalSolidRectSrcOver() }) {
            return refused(
                "unsupported.native-solid-rect.blend-authority",
                "Every native SolidRect packet must use the exact premultiplied fixed-function SrcOver plan.",
            )
        }
        val uniformBlocks = semanticPackets.map { (_, semantic) ->
            semantic.payloadRef.uniformBlock
                ?: return refused("unsupported.native-solid-rect.uniform", "A canonical uniform block is missing.")
        }
        if (uniformBlocks.any { block ->
                block.byteSize != CANONICAL_UNIFORM_BYTES.toLong() ||
                    block.bytes.size != CANONICAL_UNIFORM_BYTES ||
                    block.bytes.any { it !in 0..255 }
            }
        ) {
            return refused(
                "unsupported.native-solid-rect.uniform-layout",
                "Every solid rectangle must use the canonical 64-byte uniform block.",
            )
        }

        val targetPreparation = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
            .filter { it.resource == renderStep.target }
            .singleOrNull()
            ?: return refused(
                "unsupported.native-solid-rect.target-declaration",
                "The canonical render target must have one exact preparation declaration.",
            )
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        if (targetPreparation.role != GPUFrameResourceRole.SceneTarget || targetDescriptor == null) {
            return refused(
                "unsupported.native-solid-rect.target-declaration",
                "The canonical render target must be declared as one scene texture.",
            )
        }
        if (targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-solid-rect.target-contract",
                "The native canonical target requires exact RenderAttachment+CopySource usages and FrameLocal lifetime.",
            )
        }
        val expectedTargetGeneration = generationSeal.resourceGenerations[renderStep.target]
            ?: return refused(
                "unsupported.native-solid-rect.prepared-target",
                "The canonical target has no sealed resource generation.",
            )
        val preparedTarget = resources.ordinaryResources.singleOrNull {
            it.logicalResource == renderStep.target &&
                it.role == GPUFrameResourceRole.SceneTarget &&
                it.deviceGeneration == generationSeal.deviceGeneration &&
                it.resourceGeneration == expectedTargetGeneration &&
                it.concreteResource is GPUPreparedConcreteResourceRef.Texture
        } ?: return refused(
            "unsupported.native-solid-rect.prepared-target",
            "The canonical target preparation evidence is missing or substituted.",
        )
        if (preparedTarget.concreteResource !is GPUPreparedConcreteResourceRef.Texture) {
            return refused(
                "unsupported.native-solid-rect.prepared-target",
                "The canonical target preparation must resolve to one texture.",
            )
        }
        if (targetDescriptor.format.value != RGBA8_UNORM) {
            return refused(
                "unsupported.native-solid-rect.target-format",
                "The first native solid rectangle route requires an exact rgba8unorm target.",
            )
        }
        if (targetDescriptor.sampleCount != 1) {
            return refused(
                "unsupported.native-solid-rect.target-sample-count",
                "The first native solid rectangle route requires a single-sample target.",
            )
        }
        val targetBounds = targetDescriptor.logicalBounds
        val scissorsByRenderStep = semanticPacketsByRenderStep.map { packets ->
            packets.map { (packet, _) ->
                when (val parsed = packet.solidRectNativeScissor(targetBounds)) {
                    is SolidRectNativeScissorResult.Valid -> parsed.scissor
                    is SolidRectNativeScissorResult.Invalid -> return refused(
                        "unsupported.native-solid-rect.scissor",
                        parsed.message,
                    )
                }
            }
        }
        val sampleCount = when (val samplePlan = renderStep.samplePlan) {
            GPUSamplePlan.SingleSampleFrame -> 1
            is GPUSamplePlan.MultisampleFrame -> samplePlan.sampleCount
            is GPUSamplePlan.LocalResolveApproximation -> return refused(
                "unsupported.native-solid-rect.sample-plan",
                "Local resolve approximation is not an executable native sample plan.",
            )
        }
        if (sampleCount !in setOf(1, 4) || renderSteps.any { step ->
                when (val plan = step.samplePlan) {
                    GPUSamplePlan.SingleSampleFrame -> sampleCount != 1
                    is GPUSamplePlan.MultisampleFrame -> plan.sampleCount != sampleCount
                    is GPUSamplePlan.LocalResolveApproximation -> true
                }
            }
        ) {
            return refused(
                "unsupported.native-solid-rect.sample-plan",
                "All render scopes must use one exact single-sample or 4x MSAA plan.",
            )
        }
        val storeOperations = renderSteps.map { step ->
            val operation = when (step.loadStore.storePlan) {
                GPUStorePlan.Store -> GPUPreparedNativeStoreOperation.Store
                GPUStorePlan.Discard -> GPUPreparedNativeStoreOperation.Discard
                GPUStorePlan.ResolveAndStore -> return refused(
                    "unsupported.native-solid-rect.store-operation",
                    "ResolveAndStore is ambiguous because native MSAA resolve is planned independently.",
                )
            }
            val continuationStore = when (step.sampleContinuation?.storeAction) {
                null -> null
                GPUSampleStoreAction.Store -> GPUPreparedNativeStoreOperation.Store
                GPUSampleStoreAction.Discard -> GPUPreparedNativeStoreOperation.Discard
            }
            if (continuationStore != null && continuationStore != operation) {
                return refused(
                    "unsupported.native-solid-rect.store-operation",
                    "The render load/store plan contradicts its typed MSAA continuation store action.",
                )
            }
            operation
        }
        if (sampleCount == 1 && copySteps.isNotEmpty()) {
            return refused(
                "unsupported.native-solid-rect.copy-plan",
                "Neutral copy breaks are accepted only by the explicit MSAA continuation lane.",
            )
        }
        if (sampleCount > 1 && (copySteps.size != renderSteps.size - 1 ||
                renderSteps.any { it.sampleContinuation == null })
        ) {
            return refused(
                "unsupported.native-solid-rect.msaa-continuation",
                "The 4x MSAA route requires one typed continuation per pass and one copy break between passes.",
            )
        }
        if (sampleCount > 1) {
            val expectedOrder = buildList {
                renderSteps.forEachIndexed { index, step ->
                    add(framePlan.steps.indexOf(step))
                    copySteps.getOrNull(index)?.let { add(framePlan.steps.indexOf(it)) }
                }
                readbackStep?.let { add(framePlan.steps.indexOf(it)) }
            }
            if (expectedOrder != expectedOrder.sorted() || expectedOrder.distinct().size != expectedOrder.size) {
                return refused(
                    "unsupported.native-solid-rect.scope-order",
                    "MSAA render and neutral copy-break scopes must alternate before readback.",
                )
            }
        }
        val scratchRef = copySteps.map { it.destination }.distinct().singleOrNull()
        val scratchPreparation = scratchRef?.let { ref ->
            framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap { it.requests }
                .singleOrNull { it.resource == ref }
        }
        val scratchDescriptor = scratchPreparation?.descriptor as? GPUFrameTextureDescriptor
        val preparedScratch = scratchRef?.let { ref ->
            val expectedGeneration = generationSeal.resourceGenerations[ref]
                ?: return refused(
                    "unsupported.native-solid-rect.copy-scratch",
                    "The neutral copy scratch has no sealed resource generation.",
                )
            resources.ordinaryResources.singleOrNull {
                it.logicalResource == ref &&
                    it.role == GPUFrameResourceRole.CopyScratch &&
                    it.deviceGeneration == generationSeal.deviceGeneration &&
                    it.resourceGeneration == expectedGeneration &&
                    it.concreteResource is GPUPreparedConcreteResourceRef.Texture
            } ?: return refused(
                "unsupported.native-solid-rect.copy-scratch",
                "The neutral copy scratch preparation evidence is missing or substituted.",
            )
        }
        if (sampleCount > 1 && (scratchRef == null || scratchPreparation == null ||
                scratchPreparation.role != GPUFrameResourceRole.CopyScratch ||
                scratchDescriptor == null || scratchDescriptor.sampleCount != 1 ||
                scratchDescriptor.format != targetDescriptor.format ||
                scratchPreparation.usages != setOf(GPUFrameResourceUsage.CopyDestination) ||
                preparedScratch?.textureAllocation == null ||
                copySteps.any { copy ->
                    copy.source != renderStep.target || copy.destination != scratchRef ||
                        copy.regions.singleOrNull()?.let { region ->
                            region.sourceOffsetBytes != 0L || region.destinationOffsetBytes != 0L ||
                                region.logicalBounds != targetBounds ||
                                region.byteSize != targetBounds.checkedByteSize(RGBA_BYTES_PER_PIXEL, 1)
                        } != false
                }
            )
        ) {
            return refused(
                "unsupported.native-solid-rect.copy-scratch",
                "Every MSAA break must copy the exact canonical target into one typed single-sample scratch.",
            )
        }
        if (sampleCount > 1) {
            val expectedMsaaBytes = targetBounds.checkedByteSize(RGBA_BYTES_PER_PIXEL, sampleCount)
            if (framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaColor] !=
                expectedMsaaBytes
            ) {
                return refused(
                    "unsupported.native-solid-rect.msaa-budget",
                    "The 4x MSAA attachment must be included exactly in aggregate frame memory accounting.",
                )
            }
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-solid-rect.readback-output",
                "The optional readback scope must match exactly one output-owned staging lease.",
            )
        }
        if (readbackStep != null && output != null) {
            if (targetBounds.left != 0 || targetBounds.top != 0 ||
                readbackStep.request.sourceBounds != targetBounds ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height
            ) {
                return refused(
                    "unsupported.native-solid-rect.target-bounds",
                    "The readback must cover the exact prepared target bounds from origin zero.",
                )
            }
            if (output.request != readbackStep.request || output.layout.width <= 0 || output.layout.height <= 0 ||
                output.request.sourceBounds.left != 0 || output.request.sourceBounds.top != 0 ||
                output.layout.unpaddedBytesPerRow !=
                output.layout.width.toLong() * RGBA_BYTES_PER_PIXEL.toLong() ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L
            ) {
                return refused(
                    "unsupported.native-solid-rect.readback-layout",
                    "The output-owned RGBA8 staging layout is not executable by the native route.",
                )
            }
        }

        preparedSceneTarget?.let { prepared ->
            if (prepared.width != targetBounds.width || prepared.height != targetBounds.height ||
                prepared.deviceGeneration != generationSeal.deviceGeneration ||
                prepared.targetGeneration != generationSeal.targetGeneration
            ) {
                return refused(
                    "unsupported.native-solid-rect.prepared-scene-target-incompatible",
                    "The session-owned native target does not match the sealed frame target.",
                )
            }
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-solid-rect.materializer-state",
                    "The native solid rectangle materializer closed during validation.",
                )
            }
            materializing = true
        }
        var ephemeralCache: GPUWgpu4kSolidRectSessionCache? = null
        return try {
            synchronized(this) {
                materializedTargetWidth = targetBounds.width
                materializedTargetHeight = targetBounds.height
                materializedTargetFormat = targetDescriptor.format.value
            }
            val preparedNativeTarget = preparedSceneTarget?.borrow()
            val targetTexture = preparedNativeTarget?.first ?: device.createTexture(
                TextureDescriptor(
                    size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "Kanvas.frame.solidRect.target",
                ),
            ).tracked()
            val targetView = preparedNativeTarget?.second ?: targetTexture.createView().tracked()
            val ownsCanonicalTarget = preparedNativeTarget == null
            val msaaTexture = if (sampleCount > 1) {
                device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt()),
                        format = GPUTextureFormat.RGBA8Unorm,
                        usage = GPUTextureUsage.RenderAttachment,
                        sampleCount = sampleCount.toUInt(),
                        label = "Kanvas.frame.solidRect.msaaColor",
                    ),
                ).tracked().also {
                    synchronized(this) { msaaColorAttachmentCreationCount += 1L }
                }
            } else {
                null
            }
            val msaaView = msaaTexture?.createView()?.tracked()?.also {
                synchronized(this) { distinctMsaaColorViewCount = 1 }
            }
            val scratchAllocation = preparedScratch?.textureAllocation
            val scratchTexture = scratchAllocation?.let { allocation ->
                device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(
                            allocation.backingWidth.toUInt(),
                            allocation.backingHeight.toUInt(),
                        ),
                        format = GPUTextureFormat.RGBA8Unorm,
                        usage = GPUTextureUsage.CopyDst,
                        label = "Kanvas.frame.solidRect.copyScratch",
                    ),
                ).tracked()
            }
            val uniforms = uniformBlocks.mapIndexed { index, uniformBlock ->
                device.createBuffer(
                    BufferDescriptor(
                        size = CANONICAL_UNIFORM_BYTES.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.solidRect.uniform64.$index",
                    ),
                ).tracked().also { uniform ->
                    queue.writeBuffer(
                        uniform,
                        0uL,
                        ArrayBuffer.of(
                            ByteArray(CANONICAL_UNIFORM_BYTES) { uniformBlock.bytes[it].toByte() },
                        ),
                    )
                }
            }
            val staging = output?.let { readbackOutput ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readbackOutput.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.solidRect.readback1024",
                    ),
                ).tracked()
            }

            val effectiveCache = solidRectCache ?: GPUWgpu4kSolidRectSessionCache(device).also {
                ephemeralCache = it
            }
            val invariants = effectiveCache.acquire(
                GPUWgpu4kSolidRectPipelineCacheKey(
                    targetFormat = targetDescriptor.format.value,
                    sampleCount = sampleCount,
                ),
            )
            val bindGroups = uniforms.mapIndexed { index, uniform ->
                device.createBindGroup(
                    BindGroupDescriptor(
                        label = "Kanvas.frame.solidRect.bindGroup.$index",
                        layout = invariants.bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(
                                binding = 0u,
                                resource = BufferBinding(
                                    buffer = uniform,
                                    offset = 0uL,
                                    size = CANONICAL_UNIFORM_BYTES.toULong(),
                                ),
                            ),
                        ),
                    ),
                ).tracked()
            }

            val borrowedOwnership = GPUPreparedNativeOperandOwnership.Borrowed
            val completionOwnership = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
            val colorTargetOperand = GPUPreparedNativeTextureViewOperand(
                msaaView ?: targetView,
                generationSeal.deviceGeneration,
                borrowedOwnership,
            )
            val resolveTargetOperand = msaaView?.let {
                GPUPreparedNativeTextureViewOperand(
                    targetView,
                    generationSeal.deviceGeneration,
                    borrowedOwnership,
                )
            }
            val pipelineOperand = GPUPreparedNativeRenderPipelineOperand(
                invariants.pipeline,
                generationSeal.deviceGeneration,
                borrowedOwnership,
            )
            var packetOffset = 0
            val renderOperands = renderSteps.indices.map { index ->
                val renderStepAtIndex = renderSteps[index]
                val renderScopeAtIndex = renderScopes[index]
                val semanticPacketsAtIndex = semanticPacketsByRenderStep[index]
                val scissorsAtIndex = scissorsByRenderStep[index]
                val loadOperation = when (renderStepAtIndex.loadStore.loadOp) {
                    "clear" -> GPUPreparedNativeLoadOperation.Clear
                    "load" -> GPUPreparedNativeLoadOperation.Load
                    else -> return refused(
                        "unsupported.native-solid-rect.load-operation",
                        "Every native solid rectangle render must use an exact clear or load operation.",
                    )
                }
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = renderScopeAtIndex.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = colorTargetOperand,
                        resolveTarget = resolveTargetOperand,
                        loadOperation = loadOperation,
                        storeOperation = storeOperations[index],
                        clearColor = if (loadOperation == GPUPreparedNativeLoadOperation.Clear) {
                            GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        } else {
                            null
                        },
                    ),
                    commands = buildList {
                        semanticPacketsAtIndex.indices.forEach { packetIndex ->
                            val bindGroup = bindGroups[packetOffset + packetIndex]
                            val scissor = scissorsAtIndex[packetIndex]
                            // The preflighted command stream owns one pipeline/bind-group pair per
                            // packet. Preserve that exact operand order even when every packet
                            // borrows the same session-cached native pipeline.
                            add(GPUPreparedNativeRenderCommand.SetPipeline(pipelineOperand))
                            add(
                                GPUPreparedNativeRenderCommand.SetBindGroup(
                                    0,
                                    GPUPreparedNativeBindGroupOperand(
                                        bindGroup,
                                        generationSeal.deviceGeneration,
                                        borrowedOwnership,
                                    ),
                                ),
                            )
                            add(
                                GPUPreparedNativeRenderCommand.SetScissor(
                                    scissor.x,
                                    scissor.y,
                                    scissor.width,
                                    scissor.height,
                                ),
                            )
                            add(
                                GPUPreparedNativeRenderCommand.Draw(
                                    GPUPreparedNativeDrawCall.Draw(vertexCount = 3),
                                ),
                            )
                        }
                    },
                    semanticPayloads = semanticPacketsAtIndex.map { it.second },
                )
                    .also { packetOffset += semanticPacketsAtIndex.size }
            }
            val canonicalTextureOperand = GPUPreparedNativeTextureOperand(
                targetTexture,
                generationSeal.deviceGeneration,
                borrowedOwnership,
            )
            val scratchTextureOperand = scratchTexture?.let {
                GPUPreparedNativeTextureOperand(
                    it,
                    generationSeal.deviceGeneration,
                    borrowedOwnership,
                )
            }
            val copyOperands = copySteps.indices.map { index ->
                val region = copySteps[index].regions.single()
                val regionBounds = checkNotNull(region.logicalBounds)
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = copyScopes[index].sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.Copy,
                    source = canonicalTextureOperand,
                    destination = checkNotNull(scratchTextureOperand),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = regionBounds.left,
                        sourceOriginY = regionBounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = regionBounds.width,
                        height = regionBounds.height,
                    ),
                )
            }
            val readbackOperand = if (readbackScope != null && output != null && staging != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = canonicalTextureOperand,
                    destination = GPUPreparedNativeBufferOperand(
                        staging,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                    ),
                    layout = GPUPreparedNativeReadbackLayout(
                        originX = output.request.sourceBounds.left,
                        originY = output.request.sourceBounds.top,
                        width = output.layout.width,
                        height = output.layout.height,
                        bytesPerRow = output.layout.paddedBytesPerRow,
                        rowsPerImage = output.layout.rowsPerImage,
                        bufferOffset = output.layout.bufferOffset,
                        mappedSize = output.layout.totalBufferBytes,
                        format = GPUTextureFormat.RGBA8Unorm,
                    ),
                )
            } else {
                null
            }
            val scopeOperandByStepIndex = buildMap<Int, GPUPreparedNativeScopeOperand> {
                renderOperands.forEach { put(it.sourceStepIndex, it) }
                copyOperands.forEach { put(it.sourceStepIndex, it) }
                readbackOperand?.let { put(it.sourceStepIndex, it) }
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = encoderPlan.scopes.map { scope ->
                        GPUPreparedNativeScopeKey(
                            scope.sourceStepIndex,
                            scope.operationKind,
                            scope.resourceGenerationLabels,
                            scope.nativeOperandKeys,
                        )
                    },
                ),
                scopeOperands = encoderPlan.scopes.map { scope ->
                    checkNotNull(scopeOperandByStepIndex[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = buildList {
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(
                                listOfNotNull(targetView.takeIf { ownsCanonicalTarget }, msaaView) +
                                    bindGroups,
                            ),
                            completionOwnership,
                        ),
                    )
                    uniforms.forEach { uniform ->
                        add(GPUPreparedNativeAuxiliaryHandle(uniform, completionOwnership))
                    }
                    ephemeralCache?.let { cache ->
                        add(GPUPreparedNativeAuxiliaryHandle(cache, completionOwnership))
                    }
                    msaaTexture?.let { texture ->
                        add(GPUPreparedNativeAuxiliaryHandle(texture, completionOwnership))
                    }
                    val ownedTextures = listOfNotNull(
                        targetTexture.takeIf { ownsCanonicalTarget },
                        scratchTexture,
                    )
                    if (ownedTextures.isNotEmpty()) {
                        add(
                            GPUPreparedNativeAuxiliaryHandle(
                                GPUPreparedNativeCompletionAnchor(ownedTextures),
                                completionOwnership,
                            ),
                        )
                    }
                },
            )
            val materialization = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native solid rectangle materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
            }
            materialization
        } catch (failure: Throwable) {
            runCatching { ephemeralCache?.close() }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-solid-rect.materialization",
                "Public wgpu4k resource materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = if (acquiredSurface == null) {
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    } else {
        GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "unsupported.native-solid-rect.surface",
            "The first native solid rectangle route is offscreen-only.",
        )
    }

    override fun close() {
        synchronized(this) {
            closed = true
            if (!materializing) preRegistrationHandles.closeRetainingFailures()
        }
    }

    private fun refused(code: String, message: String) =
        GPUPreparedNativeFramePayloadMaterialization.Refused(code, message)

    @Synchronized
    private fun recordPayloadResourceCreation() {
        payloadResourceCreationCount = Math.addExact(payloadResourceCreationCount, 1L)
    }

    private fun <T : AutoCloseable> T.tracked(): T {
        preRegistrationHandles.track(this)
        recordPayloadResourceCreation()
        return this
    }

    private companion object {
        const val CANONICAL_UNIFORM_BYTES = 64
        const val RGBA_BYTES_PER_PIXEL = 4
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
        const val RGBA8_UNORM = "rgba8unorm"

        val SOLID_RECT_WGSL = """
            struct SolidRectBlock {
                rect: vec4<f32>,
                radii: vec4<f32>,
                color: vec4<f32>,
                reserved: vec4<f32>,
            }

            @group(0) @binding(0) var<uniform> solid: SolidRectBlock;

            @vertex
            fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4<f32> {
                var positions = array<vec2<f32>, 3>(
                    vec2<f32>(-1.0, -1.0),
                    vec2<f32>(3.0, -1.0),
                    vec2<f32>(-1.0, 3.0),
                );
                return vec4<f32>(positions[vertex_index], 0.0, 1.0);
            }

            @fragment
            fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                if (position.x < solid.rect.x || position.x >= solid.rect.z ||
                    position.y < solid.rect.y || position.y >= solid.rect.w) {
                    discard;
                }
                return vec4<f32>(solid.color.rgb * solid.color.a, solid.color.a);
            }
        """.trimIndent()
    }
}

private data class SolidRectNativeScissor(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private sealed interface SolidRectNativeScissorResult {
    data class Valid(val scissor: SolidRectNativeScissor) : SolidRectNativeScissorResult
    data class Invalid(val message: String) : SolidRectNativeScissorResult
}

private fun GPUDrawPacket.solidRectNativeScissor(
    targetBounds: GPUPixelBounds,
): SolidRectNativeScissorResult {
    val hash = scissorBoundsHash
    if (hash == null) {
        return SolidRectNativeScissorResult.Valid(
            SolidRectNativeScissor(0, 0, targetBounds.width, targetBounds.height),
        )
    }
    if (!hash.startsWith("scissor_")) {
        return SolidRectNativeScissorResult.Invalid(
            "SolidRect packet ${packetId.value} has no canonical device-rect scissor authority.",
        )
    }
    val values = hash.removePrefix("scissor_").split('_').map { it.toFloatOrNull() }
    if (values.size != 4 || values.any { it == null || !it.isFinite() }) {
        return SolidRectNativeScissorResult.Invalid(
            "SolidRect packet ${packetId.value} has a malformed device-rect scissor.",
        )
    }
    val (leftFloat, topFloat, rightFloat, bottomFloat) = values.map { requireNotNull(it) }
    if (listOf(leftFloat, topFloat, rightFloat, bottomFloat).any { it.toInt().toFloat() != it }) {
        return SolidRectNativeScissorResult.Invalid(
            "SolidRect packet ${packetId.value} requires integral native scissor bounds.",
        )
    }
    val left = leftFloat.toInt()
    val top = topFloat.toInt()
    val right = rightFloat.toInt()
    val bottom = bottomFloat.toInt()
    if (left < targetBounds.left || top < targetBounds.top ||
        right > targetBounds.right || bottom > targetBounds.bottom ||
        right <= left || bottom <= top
    ) {
        return SolidRectNativeScissorResult.Invalid(
            "SolidRect packet ${packetId.value} scissor must be non-empty and contained by its target.",
        )
    }
    return SolidRectNativeScissorResult.Valid(
        SolidRectNativeScissor(
            x = left - targetBounds.left,
            y = top - targetBounds.top,
            width = right - left,
            height = bottom - top,
        ),
    )
}
