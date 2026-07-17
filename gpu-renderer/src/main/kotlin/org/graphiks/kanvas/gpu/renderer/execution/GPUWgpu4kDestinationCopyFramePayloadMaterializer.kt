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
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan

internal data class GPUWgpu4kDestinationCopyMaterializationCounters(
    val nativeResourceCreations: Long,
    val snapshotLogicalBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds?,
    val snapshotBackingWidth: Int?,
    val snapshotBackingHeight: Int?,
    val copySourceOriginX: Int?,
    val copySourceOriginY: Int?,
    val copyWidth: Int?,
    val copyHeight: Int?,
)

/** Builds the closed destination-solid shader only from the canonical blend-formula authority. */
internal fun destinationSolidRectWgsl(plan: GPUBlendPlan): String? {
    if (plan !is GPUBlendPlan.ShaderBlendWithDstRead ||
        plan.destinationReadRequirement !=
        GPUBlendDestinationReadRequirement.DestinationTextureRequired ||
        plan.sourceCoverageEncoding != GPUSourceCoverageEncoding.None
    ) {
        return null
    }
    val formulaWgsl = GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
        modeLabel = plan.mode.gpuLabel,
        formulaId = plan.formulaId,
    ) ?: return null
    return """
        struct DestinationSolidRectBlock {
            rect: vec4<f32>,
            radii: vec4<f32>,
            color: vec4<f32>,
            reserved: vec4<f32>,
            logicalOriginAndExtent: vec4<f32>,
            inversePhysicalBacking: vec4<f32>,
        }
        @group(0) @binding(0) var<uniform> solid: DestinationSolidRectBlock;
        @group(0) @binding(1) var destinationSnapshot: texture_2d<f32>;
        @vertex fn vs_main(@builtin(vertex_index) i: u32) -> @builtin(position) vec4<f32> {
            var p = array<vec2<f32>, 3>(
                vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0)
            );
            return vec4<f32>(p[i], 0.0, 1.0);
        }
        $formulaWgsl
        @fragment fn fs_main(@builtin(position) p: vec4<f32>) -> @location(0) vec4<f32> {
            if (p.x < solid.rect.x || p.x >= solid.rect.z ||
                p.y < solid.rect.y || p.y >= solid.rect.w) { discard; }
            let local = vec2<i32>(
                i32(p.x - solid.logicalOriginAndExtent.x),
                i32(p.y - solid.logicalOriginAndExtent.y),
            );
            let dst = textureLoad(destinationSnapshot, local, 0);
            let src = vec4<f32>(solid.color.rgb * solid.color.a, solid.color.a);
            return kanvasBlendPremul(src, dst);
        }
    """.trimIndent()
}

/**
 * Closed 10D route: render, bounded destination snapshot, one destination-reading draw, readback.
 *
 * The frame plan remains the only route selector. This materializer accepts one exact consumer from
 * the canonical full-coverage blend-formula family and refuses other shapes before creating a native
 * handle. It does not use the legacy target API.
 */
internal class GPUWgpu4kDestinationCopyFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget? = null,
    private val destinationCopyCache: GPUWgpu4kDestinationCopySessionCache? = null,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var materialized = false
    private var materializing = false
    private var closed = false
    private var nativeResourceCreations = 0L
    private var logicalBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds? = null
    private var backingWidth: Int? = null
    private var backingHeight: Int? = null
    private var sourceOriginX: Int? = null
    private var sourceOriginY: Int? = null
    private var copyWidth: Int? = null
    private var copyHeight: Int? = null

    @Synchronized
    internal fun counters() = GPUWgpu4kDestinationCopyMaterializationCounters(
        nativeResourceCreations,
        logicalBounds,
        backingWidth,
        backingHeight,
        sourceOriginX,
        sourceOriginY,
        copyWidth,
        copyHeight,
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
                    "unsupported.native-destination-copy.materializer-state",
                    "The destination-copy materializer is one-shot and already consumed.",
                )
            }
            materialized = true
        }

        if (framePlan.steps.count { it is GPUFrameStep.CopyDestinationStep } > 1) {
            return materializeMultipleDestinationConsumers(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }

        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renders.size !in 1..2) {
            return refused(
                "unsupported.native-destination-copy.render-count",
                "The pilot route requires one consumer pass and at most one real prefix pass.",
            )
        }
        val copy = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.copy-count",
                "The pilot route requires exactly one destination copy.",
            )
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.readback-count",
                "The pilot route requires exactly one final readback.",
            )
        val consumer = copy.consumers.singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.consumer-ambiguous",
                "The pilot destination snapshot requires one exact consumer.",
            )
        val consumerRender = renders.singleOrNull { render ->
            consumer.renderTaskId in render.sourceTaskIds &&
                render.drawPackets.any { packet -> packet.packetId == consumer.packetId }
        }
            ?: return refused(
                "unsupported.native-destination-copy.consumer-binding",
                "The destination consumer render task is missing or ambiguous.",
            )
        val consumerPacket = consumerRender.drawPackets.singleOrNull { it.packetId == consumer.packetId }
            ?: return refused(
                "unsupported.native-destination-copy.consumer-binding",
                "The destination consumer packet is missing or ambiguous.",
            )
        if (consumerPacket.commandIdValue != consumer.commandId.value) {
            return refused(
                "unsupported.native-destination-copy.consumer-binding",
                "The destination consumer command does not match its packet.",
            )
        }
        val consumerSemantic = consumerPacket.semanticPayload as? GPUDrawSemanticPayload.SolidRect
            ?: return refused(
                "unsupported.native-destination-copy.consumer-semantic",
                "The pilot destination consumer requires one canonical solid-rectangle payload.",
            )
        if (consumerSemantic.payloadRef.commandIdValue != consumer.commandId.value) {
            return refused(
                "unsupported.native-destination-copy.consumer-semantic",
                "The destination consumer semantic payload belongs to another command.",
            )
        }
        val consumerBlend = consumerPacket.blendPlan
        val destinationShaderSource = consumerBlend?.let(::destinationSolidRectWgsl)
        if (destinationShaderSource == null) {
            return refused(
                "unsupported.native-destination-copy.consumer-blend",
                "The destination consumer requires one canonical full-coverage destination-read formula.",
            )
        }
        val backgroundRender = renders.singleOrNull { it !== consumerRender }
        val backgroundSemantic = backgroundRender?.let { render ->
            render.drawPackets.singleOrNull()?.semanticPayload as? GPUDrawSemanticPayload.SolidRect
                ?: return refused(
                    "unsupported.native-destination-copy.background-semantic",
                    "The optional real prefix requires one canonical solid-rectangle payload.",
                )
        }
        if (backgroundRender?.target?.let { it != copy.source } == true ||
            copy.source != consumerRender.target || copy.source != readback.source
        ) {
            return refused(
                "unsupported.native-destination-copy.target-substitution",
                "Real prefix, destination copy, consumer, and readback must share one canonical target.",
            )
        }
        if (renders.any { it.samplePlan != GPUSamplePlan.SingleSampleFrame }) {
            return refused(
                "unsupported.native-destination-copy.render-sample-plan",
                "Every pilot render pass must use the exact single-sample plan.",
            )
        }

        val backgroundIndex = backgroundRender?.let(framePlan.steps::indexOf)
        val copyIndex = framePlan.steps.indexOf(copy)
        val consumerIndex = framePlan.steps.indexOf(consumerRender)
        val readbackIndex = framePlan.steps.indexOf(readback)
        if (backgroundIndex?.let { it >= copyIndex } == true ||
            !(copyIndex < consumerIndex && consumerIndex < readbackIndex)
        ) {
            return refused(
                "unsupported.native-destination-copy.scope-order",
                "The pilot route requires optional real prefix, copy, destination draw, then readback.",
            )
        }
        val expectedScopes = buildList {
            backgroundIndex?.let { add(it to GPUEncoderOperationKind.Render) }
            add(copyIndex to GPUEncoderOperationKind.CopyDestination)
            add(consumerIndex to GPUEncoderOperationKind.Render)
            add(readbackIndex to GPUEncoderOperationKind.Readback)
        }
        if (encoderPlan.scopes.map { it.sourceStepIndex to it.operationKind } != expectedScopes) {
            return refused(
                "unsupported.native-destination-copy.encoder-plan",
                "The encoder plan does not preserve the exact pilot scope order.",
            )
        }
        val backgroundScope = backgroundIndex?.let { index ->
            encoderPlan.scopes.single { it.sourceStepIndex == index }
        }
        val copyScope = encoderPlan.scopes.single { it.sourceStepIndex == copyIndex }
        val consumerScope = encoderPlan.scopes.single { it.sourceStepIndex == consumerIndex }
        val readbackScope = encoderPlan.scopes.single { it.sourceStepIndex == readbackIndex }

        val declarations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
        val targetPreparation = declarations.singleOrNull { it.resource == copy.source }
            ?: return refused(
                "unsupported.native-destination-copy.target-declaration",
                "The canonical target needs one preparation declaration.",
            )
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused(
                "unsupported.native-destination-copy.target-declaration",
                "The canonical target declaration must be a texture.",
            )
        val snapshotPreparation = declarations.singleOrNull { it.resource == copy.snapshot }
            ?: return refused(
                "unsupported.native-destination-copy.snapshot-declaration",
                "The destination snapshot needs one preparation declaration.",
            )
        val snapshotDescriptor = snapshotPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused(
                "unsupported.native-destination-copy.snapshot-declaration",
                "The destination snapshot declaration must be a texture.",
            )
        if (targetPreparation.role != GPUFrameResourceRole.SceneTarget ||
            targetDescriptor.logicalBounds.left != 0 || targetDescriptor.logicalBounds.top != 0 ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1
        ) {
            return refused(
                "unsupported.native-destination-copy.target-declaration",
                "The pilot target must be origin-zero, rgba8unorm, and single-sample.",
            )
        }
        if (targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            )
        ) {
            return refused(
                "unsupported.native-destination-copy.target-topology",
                "The canonical target must be both a render attachment and a copy source.",
            )
        }
        if (snapshotPreparation.role != GPUFrameResourceRole.DestinationSnapshot ||
            snapshotDescriptor.logicalBounds != copy.logicalBounds ||
            snapshotDescriptor.format != targetDescriptor.format || snapshotDescriptor.sampleCount != 1 ||
            snapshotPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.TextureBinding,
            )
        ) {
            return refused(
                "unsupported.native-destination-copy.snapshot-declaration",
                "The snapshot declaration must exactly match the logical destination-copy region.",
            )
        }
        val sourceKey = copy.sourceKey
        if (sourceKey.target.value != copy.source.value ||
            sourceKey.targetGeneration != generationSeal.targetGeneration ||
            sourceKey.deviceGeneration != generationSeal.deviceGeneration ||
            sourceKey.format != targetDescriptor.format ||
            sourceKey.colorInterpretation != readback.request.outputColorInterpretation ||
            sourceKey.sampleContinuation != null ||
            sourceKey.sourceIntermediate != null
        ) {
            return refused(
                "unsupported.native-destination-copy.source-key",
                "The destination source key must identify the exact single-sample canonical target.",
            )
        }
        val targetBounds = targetDescriptor.logicalBounds
        val bounds = copy.logicalBounds
        if (bounds.left < targetBounds.left || bounds.top < targetBounds.top ||
            bounds.right > targetBounds.right || bounds.bottom > targetBounds.bottom || bounds.isEmpty
        ) {
            return refused(
                "unsupported.native-destination-copy.source-bounds",
                "The destination-copy source region must be contained by the canonical target.",
            )
        }

        fun preparedTexture(resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef) =
            resources.ordinaryResources.singleOrNull {
                it.logicalResource == resource &&
                    it.deviceGeneration == generationSeal.deviceGeneration &&
                    it.resourceGeneration == generationSeal.resourceGenerations[resource] &&
                    it.concreteResource is GPUPreparedConcreteResourceRef.Texture
            }
        if (preparedTexture(copy.source) == null) {
            return refused(
                "unsupported.native-destination-copy.prepared-target",
                "The canonical target preparation evidence is missing or substituted.",
            )
        }
        val preparedSnapshot = preparedTexture(copy.snapshot)
            ?: return refused(
                "unsupported.native-destination-copy.prepared-snapshot",
                "The destination snapshot preparation evidence is missing or substituted.",
            )
        val snapshotAllocation = preparedSnapshot.textureAllocation
            ?: return refused(
                "unsupported.native-destination-copy.snapshot-backing",
                "The destination snapshot must retain logical and physical lease evidence.",
            )
        if (snapshotAllocation.logicalBounds != bounds ||
            snapshotAllocation.format != snapshotDescriptor.format ||
            snapshotAllocation.sampleCount != 1 ||
            snapshotAllocation.usages != snapshotPreparation.usages ||
            snapshotAllocation.backingWidth < bounds.width ||
            snapshotAllocation.backingHeight < bounds.height
        ) {
            return refused(
                "unsupported.native-destination-copy.snapshot-backing",
                "The destination snapshot lease does not contain the exact logical copy extent.",
            )
        }

        val output = resources.outputOwnedReadbacks.singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.readback-output",
                "The pilot requires one output-owned readback staging lease.",
            )
        if (output.request != readback.request || readback.request.sourceBounds != targetBounds ||
            output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
            output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
            output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
        ) {
            return refused(
                "unsupported.native-destination-copy.readback-layout",
                "The final output must read the exact canonical target as rgba8.",
            )
        }
        if (backgroundSemantic?.let { !canonicalUniform(it) } == true ||
            !canonicalUniform(consumerSemantic)
        ) {
            return refused(
                "unsupported.native-destination-copy.uniform-layout",
                "Every pilot draw requires canonical solid uniforms.",
            )
        }

        preparedSceneTarget?.let { prepared ->
            if (prepared.width != targetBounds.width || prepared.height != targetBounds.height ||
                prepared.deviceGeneration != generationSeal.deviceGeneration ||
                prepared.targetGeneration != generationSeal.targetGeneration
            ) {
                return refused(
                    "unsupported.native-destination-copy.prepared-scene-target-incompatible",
                    "The session-owned native target does not match the sealed destination-copy frame.",
                )
            }
        }
        val backgroundLoad = backgroundRender?.let { render ->
            render.loadStore.toNativeLoadOperation()
                ?: return refused(
                    "unsupported.native-destination-copy.load-operation",
                    "The optional real prefix requires an exact clear or load operation.",
                )
        }
        val consumerLoad = consumerRender.loadStore.toNativeLoadOperation()
            ?: return refused(
                "unsupported.native-destination-copy.load-operation",
                "The destination consumer requires an exact clear or load operation.",
            )

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-destination-copy.materializer-state",
                    "The destination-copy materializer closed during validation.",
                )
            }
            materializing = true
        }
        return try {
            synchronized(this) {
                logicalBounds = bounds
                backingWidth = snapshotAllocation.backingWidth
                backingHeight = snapshotAllocation.backingHeight
                sourceOriginX = bounds.left
                sourceOriginY = bounds.top
                copyWidth = bounds.width
                copyHeight = bounds.height
            }
            val preparedNativeTarget = preparedSceneTarget?.borrow()
            val targetTexture = preparedNativeTarget?.first ?: device.createTexture(
                TextureDescriptor(
                    size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "Kanvas.frame.destinationCopy.target",
                ),
            ).tracked()
            val targetView = preparedNativeTarget?.second ?: targetTexture.createView().tracked()
            val ownsCanonicalTarget = preparedNativeTarget == null
            val cachedSnapshot = destinationCopyCache?.acquire(
                listOf(
                    GPUDestinationCopySnapshotSpec(
                        snapshotAllocation.backingWidth,
                        snapshotAllocation.backingHeight,
                    ),
                ),
            )?.single()
            val snapshotTexture = cachedSnapshot?.texture ?: device.createTexture(
                TextureDescriptor(
                    size = Extent3D(
                        snapshotAllocation.backingWidth.toUInt(),
                        snapshotAllocation.backingHeight.toUInt(),
                    ),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                    label = "Kanvas.frame.destinationCopy.snapshot",
                ),
            ).tracked()
            val snapshotView = cachedSnapshot?.view ?: snapshotTexture.createView().tracked()
            val ownsSnapshot = cachedSnapshot == null
            val backgroundUniform = backgroundSemantic?.let { semantic ->
                createUniform(
                    "Kanvas.frame.destinationCopy.backgroundUniform64",
                    semantic.payloadRef.uniformBlock!!.bytes.toByteArray(),
                )
            }
            val consumerUniformBytes = destinationUniformBytes(
                consumerSemantic,
                bounds,
                snapshotAllocation.backingWidth,
                snapshotAllocation.backingHeight,
            )
            val consumerUniform = createUniform(
                "Kanvas.frame.destinationCopy.consumerUniform96",
                consumerUniformBytes,
            )
            val staging = device.createBuffer(
                BufferDescriptor(
                    size = output.stagingLease.backingBufferBytes.toULong(),
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.destinationCopy.readback",
                ),
            ).tracked()

            val backgroundLayout = backgroundSemantic?.let {
                device.createBindGroupLayout(
                    BindGroupLayoutDescriptor(
                        label = "Kanvas.frame.destinationCopy.backgroundLayout",
                        entries = listOf(uniformLayoutEntry()),
                    ),
                ).tracked()
            }
            val consumerLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.frame.destinationCopy.consumerLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                    ),
                ),
            ).tracked()
            val backgroundShader = backgroundSemantic?.let {
                device.createShaderModule(
                    ShaderModuleDescriptor(
                        label = "Kanvas.frame.destinationCopy.backgroundShader",
                        code = SOLID_WGSL,
                    ),
                ).tracked()
            }
            val consumerShader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.frame.destinationCopy.consumerShader",
                    code = destinationShaderSource,
                ),
            ).tracked()
            val backgroundPipelineLayout = backgroundLayout?.let { layout ->
                device.createPipelineLayout(
                    PipelineLayoutDescriptor(
                        label = "Kanvas.frame.destinationCopy.backgroundPipelineLayout",
                        bindGroupLayouts = listOf(layout),
                    ),
                ).tracked()
            }
            val consumerPipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.frame.destinationCopy.consumerPipelineLayout",
                    bindGroupLayouts = listOf(consumerLayout),
                ),
            ).tracked()
            val backgroundPipeline = if (backgroundPipelineLayout != null && backgroundShader != null) {
                createPipeline(
                    "Kanvas.frame.destinationCopy.backgroundPipeline",
                    backgroundPipelineLayout,
                    backgroundShader,
                )
            } else {
                null
            }
            val consumerPipeline = createPipeline(
                "Kanvas.frame.destinationCopy.consumerPipeline",
                consumerPipelineLayout,
                consumerShader,
            )
            val backgroundBindGroup = if (backgroundLayout != null && backgroundUniform != null) {
                device.createBindGroup(
                    BindGroupDescriptor(
                        label = "Kanvas.frame.destinationCopy.backgroundBindGroup",
                        layout = backgroundLayout,
                        entries = listOf(uniformEntry(backgroundUniform, CANONICAL_UNIFORM_BYTES)),
                    ),
                ).tracked()
            } else {
                null
            }
            val consumerBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.destinationCopy.consumerBindGroup",
                    layout = consumerLayout,
                    entries = listOf(
                        uniformEntry(consumerUniform, DESTINATION_UNIFORM_BYTES),
                        BindGroupEntry(binding = 1u, resource = snapshotView),
                    ),
                ),
            ).tracked()

            val borrowed = GPUPreparedNativeOperandOwnership.Borrowed
            val completionOwned = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
            val backgroundOperand = if (backgroundScope != null && backgroundPipeline != null &&
                backgroundBindGroup != null && backgroundSemantic != null && backgroundLoad != null
            ) {
                renderOperand(
                    backgroundScope,
                    targetView,
                    backgroundPipeline,
                    backgroundBindGroup,
                    backgroundSemantic,
                    backgroundLoad,
                    generationSeal.deviceGeneration,
                )
            } else {
                null
            }
            val copyOperand = GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = copyScope.sourceStepIndex,
                operationKind = GPUEncoderOperationKind.CopyDestination,
                source = GPUPreparedNativeTextureOperand(targetTexture, generationSeal.deviceGeneration, borrowed),
                destination = GPUPreparedNativeTextureOperand(
                    snapshotTexture,
                    generationSeal.deviceGeneration,
                    borrowed,
                ),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = bounds.left,
                    sourceOriginY = bounds.top,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = bounds.width,
                    height = bounds.height,
                ),
            )
            val consumerOperand = renderOperand(
                consumerScope,
                targetView,
                consumerPipeline,
                consumerBindGroup,
                consumerSemantic,
                consumerLoad,
                generationSeal.deviceGeneration,
            )
            val readbackOperand = GPUPreparedNativeScopeOperand.Readback(
                sourceStepIndex = readbackScope.sourceStepIndex,
                source = GPUPreparedNativeTextureOperand(targetTexture, generationSeal.deviceGeneration, borrowed),
                destination = GPUPreparedNativeBufferOperand(
                    staging,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
                layout = GPUPreparedNativeReadbackLayout(
                    originX = 0,
                    originY = 0,
                    width = output.layout.width,
                    height = output.layout.height,
                    bytesPerRow = output.layout.paddedBytesPerRow,
                    rowsPerImage = output.layout.rowsPerImage,
                    bufferOffset = output.layout.bufferOffset,
                    mappedSize = output.layout.totalBufferBytes,
                    format = GPUTextureFormat.RGBA8Unorm,
                ),
            )
            val scopeOperands = listOfNotNull(
                backgroundOperand,
                copyOperand,
                consumerOperand,
                readbackOperand,
            )
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
                scopeOperands = scopeOperands,
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = buildList {
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(
                                buildList<AutoCloseable> {
                                    if (ownsCanonicalTarget) add(targetView)
                                    backgroundPipeline?.let(::add)
                                    add(consumerPipeline)
                                    backgroundBindGroup?.let(::add)
                                    add(consumerBindGroup)
                                },
                            ),
                            completionOwned,
                        ),
                    )
                    if (ownsSnapshot) {
                        add(GPUPreparedNativeAuxiliaryHandle(snapshotView, completionOwned))
                    }
                    backgroundUniform?.let {
                        add(GPUPreparedNativeAuxiliaryHandle(it, completionOwned))
                    }
                    add(GPUPreparedNativeAuxiliaryHandle(consumerUniform, completionOwned))
                    backgroundPipelineLayout?.let {
                        add(GPUPreparedNativeAuxiliaryHandle(it, completionOwned))
                    }
                    add(GPUPreparedNativeAuxiliaryHandle(consumerPipelineLayout, completionOwned))
                    backgroundShader?.let {
                        add(GPUPreparedNativeAuxiliaryHandle(it, completionOwned))
                    }
                    add(GPUPreparedNativeAuxiliaryHandle(consumerShader, completionOwned))
                    backgroundLayout?.let {
                        add(GPUPreparedNativeAuxiliaryHandle(it, completionOwned))
                    }
                    add(GPUPreparedNativeAuxiliaryHandle(consumerLayout, completionOwned))
                    if (ownsCanonicalTarget || ownsSnapshot) {
                        add(
                            GPUPreparedNativeAuxiliaryHandle(
                                GPUPreparedNativeCompletionAnchor(
                                    buildList {
                                        if (ownsCanonicalTarget) add(targetTexture)
                                        if (ownsSnapshot) add(snapshotTexture)
                                    },
                                ),
                                completionOwned,
                            ),
                        )
                    }
                },
            )
            val materialization = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Destination-copy materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
            }
            materialization
        } catch (failure: Throwable) {
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-destination-copy.materialization",
                "Public wgpu4k destination-copy materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun materializeMultipleDestinationConsumers(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        data class ConsumerBinding(
            val copy: GPUFrameStep.CopyDestinationStep,
            val render: GPUFrameStep.RenderPassStep,
            val semantic: GPUDrawSemanticPayload.SolidRect,
            val shaderSource: String,
            val bounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
            val backingWidth: Int,
            val backingHeight: Int,
        )
        data class SnapshotNative(
            val binding: ConsumerBinding,
            val texture: io.ygdrasil.webgpu.GPUTexture,
            val view: io.ygdrasil.webgpu.GPUTextureView,
            val ownedByPayload: Boolean,
        )
        data class DrawNative(
            val render: GPUFrameStep.RenderPassStep,
            val semantic: GPUDrawSemanticPayload.SolidRect,
            val uniform: io.ygdrasil.webgpu.GPUBuffer,
            val layout: io.ygdrasil.webgpu.GPUBindGroupLayout,
            val shader: io.ygdrasil.webgpu.GPUShaderModule,
            val pipelineLayout: io.ygdrasil.webgpu.GPUPipelineLayout,
            val pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
            val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        )

        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val copies = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.readback-count",
                "A multi-consumer destination frame requires exactly one final readback.",
            )
        if (copies.size < 2 || renders.size < copies.size) {
            return refused(
                "unsupported.native-destination-copy.multi-consumer-shape",
                "A multi-consumer destination frame requires one or more ordered render segments per copy.",
            )
        }
        if (renders.any { it.samplePlan != GPUSamplePlan.SingleSampleFrame || it.drawPackets.size != 1 }) {
            return refused(
                "unsupported.native-destination-copy.multi-render-shape",
                "The first multi-consumer route requires single-sample render segments with one exact packet.",
            )
        }

        val declarations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
        val target = renders.first().target
        val targetPreparation = declarations.singleOrNull { it.resource == target }
            ?: return refused(
                "unsupported.native-destination-copy.target-declaration",
                "The canonical target needs one preparation declaration.",
            )
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused(
                "unsupported.native-destination-copy.target-declaration",
                "The canonical target declaration must be a texture.",
            )
        val targetBounds = targetDescriptor.logicalBounds
        if (renders.any { it.target != target } || readback.source != target ||
            targetPreparation.role != GPUFrameResourceRole.SceneTarget ||
            targetDescriptor.logicalBounds.left != 0 || targetDescriptor.logicalBounds.top != 0 ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            )
        ) {
            return refused(
                "unsupported.native-destination-copy.target-topology",
                "Every multi-consumer segment must share one origin-zero rgba8unorm target.",
            )
        }

        fun preparedTexture(resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef) =
            resources.ordinaryResources.singleOrNull {
                it.logicalResource == resource &&
                    it.deviceGeneration == generationSeal.deviceGeneration &&
                    it.resourceGeneration == generationSeal.resourceGenerations[resource] &&
                    it.concreteResource is GPUPreparedConcreteResourceRef.Texture
            }
        if (preparedTexture(target) == null) {
            return refused(
                "unsupported.native-destination-copy.prepared-target",
                "The canonical target preparation evidence is missing or substituted.",
            )
        }

        val consumerBindings = mutableListOf<ConsumerBinding>()
        val consumerPacketIds = mutableSetOf<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID>()
        val snapshotResources = mutableSetOf<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef>()
        for (copy in copies) {
            val consumer = copy.consumers.singleOrNull()
                ?: return refused(
                    "unsupported.native-destination-copy.consumer-ambiguous",
                    "Each bounded destination snapshot requires one exact consumer.",
                )
            if (!consumerPacketIds.add(consumer.packetId) || !snapshotResources.add(copy.snapshot)) {
                return refused(
                    "unsupported.native-destination-copy.consumer-ambiguous",
                    "Destination consumers and snapshot resources must be unique within one frame.",
                )
            }
            val render = renders.singleOrNull { candidate ->
                consumer.renderTaskId in candidate.sourceTaskIds &&
                    candidate.drawPackets.single().packetId == consumer.packetId
            } ?: return refused(
                "unsupported.native-destination-copy.consumer-binding",
                "Each destination consumer must own one exact split render segment.",
            )
            val packet = render.drawPackets.single()
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SolidRect
                ?: return refused(
                    "unsupported.native-destination-copy.consumer-semantic",
                    "Every destination consumer requires one canonical solid-rectangle payload.",
                )
            if (packet.commandIdValue != consumer.commandId.value ||
                semantic.payloadRef.commandIdValue != consumer.commandId.value || !canonicalUniform(semantic)
            ) {
                return refused(
                    "unsupported.native-destination-copy.consumer-binding",
                    "Destination consumer command and semantic identities must match their packet.",
                )
            }
            val shaderSource = packet.blendPlan?.let(::destinationSolidRectWgsl)
                ?: return refused(
                    "unsupported.native-destination-copy.consumer-blend",
                    "Every destination consumer requires one canonical full-coverage formula.",
                )
            val snapshotPreparation = declarations.singleOrNull { it.resource == copy.snapshot }
                ?: return refused(
                    "unsupported.native-destination-copy.snapshot-declaration",
                    "Each destination snapshot needs one preparation declaration.",
                )
            val snapshotDescriptor = snapshotPreparation.descriptor as? GPUFrameTextureDescriptor
                ?: return refused(
                    "unsupported.native-destination-copy.snapshot-declaration",
                    "Each destination snapshot declaration must be a texture.",
                )
            if (copy.source != target || snapshotPreparation.role != GPUFrameResourceRole.DestinationSnapshot ||
                snapshotDescriptor.logicalBounds != copy.logicalBounds ||
                snapshotDescriptor.format != targetDescriptor.format || snapshotDescriptor.sampleCount != 1 ||
                snapshotPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                )
            ) {
                return refused(
                    "unsupported.native-destination-copy.snapshot-declaration",
                    "Each snapshot must exactly match its bounded logical destination-copy region.",
                )
            }
            val bounds = copy.logicalBounds
            if (bounds.left < targetBounds.left || bounds.top < targetBounds.top ||
                bounds.right > targetBounds.right || bounds.bottom > targetBounds.bottom || bounds.isEmpty
            ) {
                return refused(
                    "unsupported.native-destination-copy.source-bounds",
                    "Every destination-copy source region must be contained by the canonical target.",
                )
            }
            val sourceKey = copy.sourceKey
            if (sourceKey.target.value != target.value ||
                sourceKey.targetGeneration != generationSeal.targetGeneration ||
                sourceKey.deviceGeneration != generationSeal.deviceGeneration ||
                sourceKey.format != targetDescriptor.format ||
                sourceKey.colorInterpretation != readback.request.outputColorInterpretation ||
                sourceKey.sampleContinuation != null || sourceKey.sourceIntermediate != null
            ) {
                return refused(
                    "unsupported.native-destination-copy.source-key",
                    "Every snapshot source key must identify the exact canonical target.",
                )
            }
            val preparedSnapshot = preparedTexture(copy.snapshot)
                ?: return refused(
                    "unsupported.native-destination-copy.prepared-snapshot",
                    "Prepared destination snapshot evidence is missing or substituted.",
                )
            val allocation = preparedSnapshot.textureAllocation
                ?: return refused(
                    "unsupported.native-destination-copy.snapshot-backing",
                    "Every destination snapshot must retain logical and physical lease evidence.",
                )
            if (allocation.logicalBounds != bounds || allocation.format != snapshotDescriptor.format ||
                allocation.sampleCount != 1 || allocation.usages != snapshotPreparation.usages ||
                allocation.backingWidth < bounds.width || allocation.backingHeight < bounds.height
            ) {
                return refused(
                    "unsupported.native-destination-copy.snapshot-backing",
                    "A destination snapshot lease does not contain its logical copy extent.",
                )
            }
            val copyIndex = framePlan.steps.indexOf(copy)
            val renderIndex = framePlan.steps.indexOf(render)
            if (copyIndex >= renderIndex) {
                return refused(
                    "unsupported.native-destination-copy.scope-order",
                    "Each destination copy must immediately precede its consumer segment.",
                )
            }
            consumerBindings += ConsumerBinding(
                copy,
                render,
                semantic,
                shaderSource,
                bounds,
                allocation.backingWidth,
                allocation.backingHeight,
            )
        }

        val directRenders = renders.filterNot { render -> consumerBindings.any { it.render == render } }
        for (render in directRenders) {
            val packet = render.drawPackets.single()
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SolidRect
                ?: return refused(
                    "unsupported.native-destination-copy.direct-semantic",
                    "Mixed direct destination frames require canonical solid-rectangle packets.",
                )
            val blend = packet.blendPlan as? GPUBlendPlan.ShaderBlendNoDstRead
            if (!canonicalUniform(semantic) ||
                semantic.payloadRef.commandIdValue != packet.commandIdValue ||
                blend?.mode?.gpuLabel != "src" ||
                blend.formulaId != "src@v1" || blend.sourceCoverageEncoding != GPUSourceCoverageEncoding.None
            ) {
                return refused(
                    "unsupported.native-destination-copy.direct-blend",
                    "The first mixed route accepts only exact full-coverage Src direct packets.",
                )
            }
        }

        val expectedScopes = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.RenderPassStep -> index to GPUEncoderOperationKind.Render
                is GPUFrameStep.CopyDestinationStep -> index to GPUEncoderOperationKind.CopyDestination
                is GPUFrameStep.ReadbackCopyStep -> index to GPUEncoderOperationKind.Readback
                else -> null
            }
        }
        if (encoderPlan.scopes.map { it.sourceStepIndex to it.operationKind } != expectedScopes) {
            return refused(
                "unsupported.native-destination-copy.encoder-plan",
                "The encoder plan must preserve every split render, destination copy, and final readback.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
            ?: return refused(
                "unsupported.native-destination-copy.readback-output",
                "A multi-consumer destination frame requires one output-owned staging lease.",
            )
        if (output.request != readback.request || readback.request.sourceBounds != targetBounds ||
            output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
            output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
            output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
        ) {
            return refused(
                "unsupported.native-destination-copy.readback-layout",
                "The final output must read the exact canonical target as rgba8.",
            )
        }
        preparedSceneTarget?.let { prepared ->
            if (prepared.width != targetBounds.width || prepared.height != targetBounds.height ||
                prepared.deviceGeneration != generationSeal.deviceGeneration ||
                prepared.targetGeneration != generationSeal.targetGeneration
            ) {
                return refused(
                    "unsupported.native-destination-copy.prepared-scene-target-incompatible",
                    "The session-owned target does not match the sealed multi-consumer frame.",
                )
            }
        }
        val loadsByRender = renders.associateWith { render ->
            render.loadStore.toNativeLoadOperation()
                ?: return refused(
                    "unsupported.native-destination-copy.load-operation",
                    "Every split render segment requires an exact clear or load operation.",
                )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-destination-copy.materializer-state",
                    "The destination-copy materializer closed during validation.",
                )
            }
            val firstBinding = consumerBindings.first()
            logicalBounds = firstBinding.bounds
            backingWidth = firstBinding.backingWidth
            backingHeight = firstBinding.backingHeight
            sourceOriginX = firstBinding.bounds.left
            sourceOriginY = firstBinding.bounds.top
            copyWidth = firstBinding.bounds.width
            copyHeight = firstBinding.bounds.height
            materializing = true
        }
        return try {
            val preparedNativeTarget = preparedSceneTarget?.borrow()
            val targetTexture = preparedNativeTarget?.first ?: device.createTexture(
                TextureDescriptor(
                    size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "Kanvas.frame.destinationCopy.multi.target",
                ),
            ).tracked()
            val targetView = preparedNativeTarget?.second ?: targetTexture.createView().tracked()
            val ownsCanonicalTarget = preparedNativeTarget == null
            val cachedSnapshots = destinationCopyCache?.acquire(
                consumerBindings.map { binding ->
                    GPUDestinationCopySnapshotSpec(binding.backingWidth, binding.backingHeight)
                },
            )
            val snapshots = consumerBindings.mapIndexed { index, binding ->
                val cached = cachedSnapshots?.get(index)
                val texture = cached?.texture ?: device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(binding.backingWidth.toUInt(), binding.backingHeight.toUInt()),
                        format = GPUTextureFormat.RGBA8Unorm,
                        usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                        label = "Kanvas.frame.destinationCopy.multi.snapshot.$index",
                    ),
                ).tracked()
                SnapshotNative(
                    binding,
                    texture,
                    cached?.view ?: texture.createView().tracked(),
                    ownedByPayload = cached == null,
                )
            }
            val snapshotByRender = snapshots.associateBy { it.binding.render }
            val draws = renders.mapIndexed { index, render ->
                val semantic = render.drawPackets.single().semanticPayload as GPUDrawSemanticPayload.SolidRect
                val snapshot = snapshotByRender[render]
                val uniformBytes = if (snapshot == null) {
                    semantic.payloadRef.uniformBlock!!.bytes.toByteArray()
                } else {
                    destinationUniformBytes(
                        semantic,
                        snapshot.binding.bounds,
                        snapshot.binding.backingWidth,
                        snapshot.binding.backingHeight,
                    )
                }
                val uniform = createUniform(
                    "Kanvas.frame.destinationCopy.multi.uniform.$index",
                    uniformBytes,
                )
                val layout = device.createBindGroupLayout(
                    BindGroupLayoutDescriptor(
                        label = "Kanvas.frame.destinationCopy.multi.layout.$index",
                        entries = buildList {
                            add(uniformLayoutEntry())
                            if (snapshot != null) {
                                add(
                                    BindGroupLayoutEntry(
                                        binding = 1u,
                                        visibility = GPUShaderStage.Fragment,
                                        texture = TextureBindingLayout(
                                            sampleType = GPUTextureSampleType.Float,
                                            viewDimension = GPUTextureViewDimension.TwoD,
                                            multisampled = false,
                                        ),
                                    ),
                                )
                            }
                        },
                    ),
                ).tracked()
                val shader = device.createShaderModule(
                    ShaderModuleDescriptor(
                        label = "Kanvas.frame.destinationCopy.multi.shader.$index",
                        code = snapshot?.binding?.shaderSource ?: SOLID_WGSL,
                    ),
                ).tracked()
                val pipelineLayout = device.createPipelineLayout(
                    PipelineLayoutDescriptor(
                        label = "Kanvas.frame.destinationCopy.multi.pipelineLayout.$index",
                        bindGroupLayouts = listOf(layout),
                    ),
                ).tracked()
                val pipeline = createPipeline(
                    "Kanvas.frame.destinationCopy.multi.pipeline.$index",
                    pipelineLayout,
                    shader,
                )
                val bindGroup = device.createBindGroup(
                    BindGroupDescriptor(
                        label = "Kanvas.frame.destinationCopy.multi.bindGroup.$index",
                        layout = layout,
                        entries = buildList {
                            add(
                                uniformEntry(
                                    uniform,
                                    if (snapshot == null) CANONICAL_UNIFORM_BYTES else DESTINATION_UNIFORM_BYTES,
                                ),
                            )
                            if (snapshot != null) add(BindGroupEntry(binding = 1u, resource = snapshot.view))
                        },
                    ),
                ).tracked()
                DrawNative(render, semantic, uniform, layout, shader, pipelineLayout, pipeline, bindGroup)
            }
            val staging = device.createBuffer(
                BufferDescriptor(
                    size = output.stagingLease.backingBufferBytes.toULong(),
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.destinationCopy.multi.readback",
                ),
            ).tracked()
            val borrowed = GPUPreparedNativeOperandOwnership.Borrowed
            val completionOwned = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
            val operands = mutableMapOf<Int, GPUPreparedNativeScopeOperand>()
            for (draw in draws) {
                val stepIndex = framePlan.steps.indexOf(draw.render)
                val scope = encoderPlan.scopes.single { it.sourceStepIndex == stepIndex }
                operands[stepIndex] = renderOperand(
                    scope,
                    targetView,
                    draw.pipeline,
                    draw.bindGroup,
                    draw.semantic,
                    loadsByRender.getValue(draw.render),
                    generationSeal.deviceGeneration,
                )
            }
            for (snapshot in snapshots) {
                val stepIndex = framePlan.steps.indexOf(snapshot.binding.copy)
                operands[stepIndex] = GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = stepIndex,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        borrowed,
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        snapshot.texture,
                        generationSeal.deviceGeneration,
                        borrowed,
                    ),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = snapshot.binding.bounds.left,
                        sourceOriginY = snapshot.binding.bounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = snapshot.binding.bounds.width,
                        height = snapshot.binding.bounds.height,
                    ),
                )
            }
            val readbackIndex = framePlan.steps.indexOf(readback)
            operands[readbackIndex] = GPUPreparedNativeScopeOperand.Readback(
                sourceStepIndex = readbackIndex,
                source = GPUPreparedNativeTextureOperand(
                    targetTexture,
                    generationSeal.deviceGeneration,
                    borrowed,
                ),
                destination = GPUPreparedNativeBufferOperand(
                    staging,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
                layout = GPUPreparedNativeReadbackLayout(
                    originX = 0,
                    originY = 0,
                    width = output.layout.width,
                    height = output.layout.height,
                    bytesPerRow = output.layout.paddedBytesPerRow,
                    rowsPerImage = output.layout.rowsPerImage,
                    bufferOffset = output.layout.bufferOffset,
                    mappedSize = output.layout.totalBufferBytes,
                    format = GPUTextureFormat.RGBA8Unorm,
                ),
            )
            val scopeOperands = encoderPlan.scopes.map { scope ->
                requireNotNull(operands[scope.sourceStepIndex])
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
                scopeOperands = scopeOperands,
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = buildList {
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(
                                buildList {
                                    if (ownsCanonicalTarget) add(targetView)
                                    draws.forEach { add(it.pipeline) }
                                    draws.forEach { add(it.bindGroup) }
                                },
                            ),
                            completionOwned,
                        ),
                    )
                    snapshots.filter { it.ownedByPayload }.forEach {
                        add(GPUPreparedNativeAuxiliaryHandle(it.view, completionOwned))
                    }
                    draws.forEach { add(GPUPreparedNativeAuxiliaryHandle(it.uniform, completionOwned)) }
                    draws.forEach { add(GPUPreparedNativeAuxiliaryHandle(it.pipelineLayout, completionOwned)) }
                    draws.forEach { add(GPUPreparedNativeAuxiliaryHandle(it.shader, completionOwned)) }
                    draws.forEach { add(GPUPreparedNativeAuxiliaryHandle(it.layout, completionOwned)) }
                    val ownedSnapshots = snapshots.filter { it.ownedByPayload }
                    if (ownsCanonicalTarget || ownedSnapshots.isNotEmpty()) {
                        add(
                            GPUPreparedNativeAuxiliaryHandle(
                                GPUPreparedNativeCompletionAnchor(
                                    buildList {
                                        if (ownsCanonicalTarget) add(targetTexture)
                                        ownedSnapshots.forEach { add(it.texture) }
                                    },
                                ),
                                completionOwned,
                            ),
                        )
                    }
                },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Destination-copy materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
            }
            result
        } catch (failure: Throwable) {
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-destination-copy.materialization",
                "Public wgpu4k multi-consumer materialization failed: " +
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
            "unsupported.native-destination-copy.surface",
            "The destination-copy pilot is offscreen-only.",
        )
    }

    override fun close() {
        synchronized(this) {
            closed = true
            if (!materializing) preRegistrationHandles.closeRetainingFailures()
        }
    }

    private fun GPULoadStorePlan.toNativeLoadOperation(): GPUPreparedNativeLoadOperation? =
        when (loadOp) {
            "clear" -> GPUPreparedNativeLoadOperation.Clear
            "load" -> GPUPreparedNativeLoadOperation.Load
            else -> null
        }

    private fun renderOperand(
        scope: GPUCommandEncoderScopePlan,
        targetView: io.ygdrasil.webgpu.GPUTextureView,
        pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
        bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        semantic: GPUDrawSemanticPayload.SolidRect,
        load: GPUPreparedNativeLoadOperation,
        generation: org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID,
    ) = GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = scope.sourceStepIndex,
        pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = GPUPreparedNativeTextureViewOperand(targetView, generation),
            loadOperation = load,
            storeOperation = GPUPreparedNativeStoreOperation.Store,
            clearColor = if (load == GPUPreparedNativeLoadOperation.Clear) {
                GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
            } else {
                null
            },
        ),
        commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(
                GPUPreparedNativeRenderPipelineOperand(pipeline, generation),
            ),
            GPUPreparedNativeRenderCommand.SetBindGroup(
                0,
                GPUPreparedNativeBindGroupOperand(bindGroup, generation),
            ),
            GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
        ),
        semanticPayloads = listOf(semantic),
    )

    private fun createUniform(
        label: String,
        bytes: ByteArray,
    ): io.ygdrasil.webgpu.GPUBuffer {
        val buffer = device.createBuffer(
            BufferDescriptor(
                size = bytes.size.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = label,
            ),
        ).tracked()
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes))
        return buffer
    }

    private fun createPipeline(
        label: String,
        layout: io.ygdrasil.webgpu.GPUPipelineLayout,
        shader: io.ygdrasil.webgpu.GPUShaderModule,
    ) = device.createRenderPipeline(
        RenderPipelineDescriptor(
            label = label,
            layout = layout,
            vertex = VertexState(module = shader, entryPoint = "vs_main"),
            primitive = PrimitiveState(),
            multisample = MultisampleState(count = 1u),
            fragment = FragmentState(
                module = shader,
                entryPoint = "fs_main",
                targets = listOf(ColorTargetState(GPUTextureFormat.RGBA8Unorm)),
            ),
        ),
    ).tracked()

    private fun uniformLayoutEntry() = BindGroupLayoutEntry(
        binding = 0u,
        visibility = GPUShaderStage.Fragment,
        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
    )

    private fun uniformEntry(buffer: io.ygdrasil.webgpu.GPUBuffer, size: Int) = BindGroupEntry(
        binding = 0u,
        resource = BufferBinding(buffer, 0uL, size.toULong()),
    )

    private fun canonicalUniform(semantic: GPUDrawSemanticPayload.SolidRect): Boolean =
        semantic.payloadRef.uniformBlock?.let { block ->
            block.byteSize == CANONICAL_UNIFORM_BYTES.toLong() &&
                block.bytes.size == CANONICAL_UNIFORM_BYTES && block.bytes.all { it in 0..255 }
        } == true

    private fun destinationUniformBytes(
        semantic: GPUDrawSemanticPayload.SolidRect,
        bounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
        physicalWidth: Int,
        physicalHeight: Int,
    ): ByteArray = ByteBuffer.allocate(DESTINATION_UNIFORM_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put(semantic.payloadRef.uniformBlock!!.bytes.toByteArray())
            putFloat(bounds.left.toFloat())
            putFloat(bounds.top.toFloat())
            putFloat(bounds.width.toFloat())
            putFloat(bounds.height.toFloat())
            putFloat(1f / physicalWidth.toFloat())
            putFloat(1f / physicalHeight.toFloat())
            putFloat(0f)
            putFloat(0f)
        }.array()

    private fun List<Int>.toByteArray() = ByteArray(size) { get(it).toByte() }

    private fun <T : AutoCloseable> T.tracked(): T {
        preRegistrationHandles.track(this)
        synchronized(this@GPUWgpu4kDestinationCopyFramePayloadMaterializer) {
            nativeResourceCreations = Math.addExact(nativeResourceCreations, 1)
        }
        return this
    }

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)

    private companion object {
        const val CANONICAL_UNIFORM_BYTES = 64
        const val DESTINATION_UNIFORM_BYTES = 96
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val RGBA8_UNORM = "rgba8unorm"

        val SOLID_WGSL = """
            struct SolidRectBlock {
                rect: vec4<f32>,
                radii: vec4<f32>,
                color: vec4<f32>,
                reserved: vec4<f32>,
            }
            @group(0) @binding(0) var<uniform> solid: SolidRectBlock;
            @vertex fn vs_main(@builtin(vertex_index) i: u32) -> @builtin(position) vec4<f32> {
                var p = array<vec2<f32>, 3>(
                    vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0)
                );
                return vec4<f32>(p[i], 0.0, 1.0);
            }
            @fragment fn fs_main(@builtin(position) p: vec4<f32>) -> @location(0) vec4<f32> {
                if (p.x < solid.rect.x || p.x >= solid.rect.z ||
                    p.y < solid.rect.y || p.y >= solid.rect.w) { discard; }
                return vec4<f32>(solid.color.rgb * solid.color.a, solid.color.a);
            }
        """.trimIndent()

    }
}
