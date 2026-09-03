package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.hasExactCorePrimitivePathClipPair
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedCandidateDecision
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRouteRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.W3SessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.passes.sealGPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.validateGPUCorePrimitiveCoverageMaskPreparedAuthority
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveGradientAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveGradientBindingLayoutHash
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionPacketAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

private fun GPUColorFormat.isCorePrimitiveSceneTargetFormat(): Boolean =
    this == GPUColorFormat.RGBA8Unorm ||
        this == GPUColorFormat.RGBA8UnormSrgb ||
        this == GPUColorFormat.BGRA8Unorm

private fun GPUColorFormat.corePrimitiveInterpretationOrNull(): GPUColorInterpretation? = when (this) {
    GPUColorFormat.RGBA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    GPUColorFormat.RGBA8UnormSrgb -> GPUColorInterpretation.LinearPremul
    GPUColorFormat.BGRA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    else -> null
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.toGPUColorFormat(): GPUColorFormat =
    when (this) {
        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm ->
            GPUColorFormat.RGBA8Unorm
        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb ->
            GPUColorFormat.RGBA8UnormSrgb
        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Bgra8Unorm ->
            GPUColorFormat.BGRA8Unorm
    }

private fun GPUColorFormat.toCorePrimitiveGPUTextureFormat(): GPUTextureFormat = when (this) {
    GPUColorFormat.RGBA8Unorm -> GPUTextureFormat.RGBA8Unorm
    GPUColorFormat.RGBA8UnormSrgb -> GPUTextureFormat.RGBA8UnormSrgb
    GPUColorFormat.BGRA8Unorm -> GPUTextureFormat.BGRA8Unorm
    else -> throw IllegalArgumentException("Unsupported CorePrimitive scene target format: $value")
}

private fun GPUFramePlan.corePrimitiveSceneTargetDescriptor(
    target: GPUFrameResourceRef,
): GPUFrameTextureDescriptor? = steps
    .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
    .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
    .singleOrNull {
        it.role == GPUFrameResourceRole.SceneTarget && it.resource == target
    }?.descriptor as? GPUFrameTextureDescriptor

/** Ordered destination snapshot authority for one direct CorePrimitive scope. */
internal data class CorePrimitiveDestinationCopyAuthority(
    val step: GPUFrameStep.CopyDestinationStep,
    val snapshotPreparation: GPUResourcePreparationRequest,
    val snapshotDescriptor: GPUFrameTextureDescriptor,
    val copyScope: GPUCommandEncoderScopePlan,
)

/** Native destination snapshot resources for the ordered copy and the dst-read bind group. */
internal data class CorePrimitiveDestinationSnapshotHandles(
    val texture: GPUTexture,
    val binding: GPUWgpu4kCorePrimitiveDstReadBinding,
)

private fun CorePrimitiveDestinationSnapshotHandles.payloadOwnedAuxiliaryHandles(): List<GPUPreparedNativeAuxiliaryHandle> {
    val completionOwnership = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
    return listOf(
        GPUPreparedNativeAuxiliaryHandle(binding.view, completionOwnership),
        GPUPreparedNativeAuxiliaryHandle(binding.sampler, completionOwnership),
        GPUPreparedNativeAuxiliaryHandle(
            GPUPreparedNativeCompletionAnchor(listOf(texture)),
            completionOwnership,
        ),
    )
}

internal sealed interface CorePrimitiveDestinationCopyValidation {
    data class Accepted(val authorities: List<CorePrimitiveDestinationCopyAuthority>) :
        CorePrimitiveDestinationCopyValidation

    data class Refused(
        val code: String,
        val message: String,
    ) : CorePrimitiveDestinationCopyValidation
}

/** Public-wgpu4k materializer for direct and unified indexed path CorePrimitive routes. */
internal class GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val sessionCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
    private val coverageMaskProducerMaterializer: GPUWgpu4kCoverageMaskProducerMaterializerPort =
        GPUWgpu4kCoverageMaskProducerMaterializer(queue, sessionCache, limits),
    private val onDestinationSnapshotCreated: () -> Unit = {},
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var consumed = false
    private var materializing = false
    private var closed = false

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        synchronized(this) {
            if (closed || consumed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer is one-shot and already consumed.",
                )
            }
            consumed = true
        }

        val w3Render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().singleOrNull()
        val w3Scratch = w3Render?.drawPackets?.firstOrNull()
            ?.corePrimitivePreparedAuthority?.w3SessionScratch
        if (w3Render != null && w3Scratch != null) {
            return materializeW3SessionScratch(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
                w3Render,
                w3Scratch,
            )
        }

        if (encoderPlan.scopes.any { scope ->
                scope.corePrimitiveClipStencilPreparedRouteSeal is
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                    scope.corePrimitiveClipStencilPreparedRouteSeal is
                    GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
            }
        ) {
            return materializePreparedClipStencilCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }
        if (encoderPlan.scopes.any { scope ->
                scope.corePrimitiveCoverageMaskPreparedRouteSeal is
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
                    scope.corePrimitiveCoverageMaskPreparedRouteSeal is
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer ||
                    scope.corePrimitiveCoverageMaskPreparedRouteSeal is
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition ||
                    scope.corePrimitiveCoverageMaskPreparedRouteSeal is
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition
            }
        ) {
            return materializePreparedCoverageMaskCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }

        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val hasPathStencilScope = renderSteps.any { render ->
            render.drawPackets.any { packet ->
                packet.role == GPUDrawPacketRole.PathStencilProducer ||
                    packet.role == GPUDrawPacketRole.PathStencilCover
            }
        }
        val destinationCopyCount = framePlan.steps.count { it is GPUFrameStep.CopyDestinationStep }
        if (renderSteps.size == 2 && destinationCopyCount > 0 &&
            (hasPathStencilScope || destinationCopyCount == 1)
        ) {
            // The admitted two-render dst-copy shape (producer render, ordered
            // snapshot copy, consuming render) materializes through the dedicated core dst-copy
            // lane: each pass acquires its own pooled run (per-pass pipeline and bind group,
            // Graphite per-pass BindGraphicsPipeline recipe) and the ordered TextureCopy lands
            // between the passes in the same encoder.
            return materializeDirectMultiRenderDstCopyCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }
        if (renderSteps.size == 3 && framePlan.steps.any { it is GPUFrameStep.CopyDestinationStep } &&
            hasPathStencilScope
        ) {
            // The continued path dst-read shape (background render, producer
            // render, ordered snapshot copy, cover render) materializes through a dedicated
            // lane that shares one frame-local path D24S8 across the producer and cover runs.
            return materializeContinuedPathDstReadCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }
        if (renderSteps.size >= 2 && framePlan.steps.any { it is GPUFrameStep.CopyDestinationStep } &&
            !hasPathStencilScope
        ) {
            return materializeDirectMultiRenderDestinationCopySequenceCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }
        if (renderSteps.size >= 2) {
            // The layout split emits one render pass per uniform-layout group
            // (each pass owns its own slab). The split lane materializes every pass with its
            // own pooled run, in step order, without an ordered destination copy.
            return materializeDirectMultiRenderSplitCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
        }
        val renderStep = renderSteps.singleOrNull()
        if (renderStep == null || renderStep.drawPackets.isEmpty()) {
            return refused(
                "unsupported.native-core-primitive.render-shape",
                "Direct CorePrimitive requires one non-empty multi-packet render scope; " +
                    "observed ${renderSteps.size} scope(s).",
            )
        }
        val sampleCount = renderStep.samplePlan.sampleCount
        val isMsaa4x = renderStep.samplePlan == GPUSamplePlan.MultisampleFrame(4)
        val renderScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == framePlan.steps.indexOf(renderStep) &&
                it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-core-primitive.render-plan",
            "The direct CorePrimitive render scope is absent from the encoder plan.",
        )
        val unifiedRoute = renderScope.corePrimitiveNativeScopeRouteSeal as?
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
        if (unifiedRoute?.orderedUnits?.any {
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
            } == true
        ) {
            val indexed = materializeIndexedPathCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
                renderStep,
                renderScope,
                unifiedRoute,
            )
            return if (isMsaa4x && indexed is GPUPreparedNativeFramePayloadMaterialization.Refused &&
                indexed.code.startsWith("invalid.native-core-primitive.") &&
                indexed.code != "invalid.native-core-primitive.indexed-msaa-authority"
            ) {
                indexed.copy(
                    code = "invalid.native-core-primitive.indexed-msaa-authority",
                    message = "Indexed 4x CorePrimitive native authority is corrupt: ${indexed.message}",
                )
            } else {
                indexed
            }
        }
        val sealedRoutes = renderScope.corePrimitiveDirectNativeRouteSeal as?
            GPUCorePrimitiveDirectNativeRouteSeal.Routes ?: return refused(
            "invalid.native-core-primitive.route-seal",
                "The direct CorePrimitive render scope requires its pure-preflight route seal.",
            )
        val preparedPassSeal = sealedRoutes.preparedPassSeal ?: return refused(
            "invalid.native-core-primitive.prepared-pass-seal",
            "The direct CorePrimitive route requires the builder authority proven by pure preflight.",
        )
        if (sealedRoutes.routesByPacketId.keys.toList() != renderStep.drawPackets.map { it.packetId }) {
            return refused(
                "invalid.native-core-primitive.route-seal",
                "The direct CorePrimitive route seal must exactly match render packet order and identity.",
            )
        }
        val semanticPackets = renderStep.drawPackets.map { packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return refused(
                    "unsupported.native-core-primitive.semantic-payload",
                    "Every direct CorePrimitive scope requires one typed semantic payload.",
                )
            Triple(renderStep, packet, semantic)
        }
        val singleKeySeal = when (preparedPassSeal) {
            is GPUCorePrimitiveMultiKeyDirectPreparedPassSeal ->
                return materializeDirectMultiKeyCore(
                    framePlan = framePlan,
                    encoderPlan = encoderPlan,
                    resources = resources,
                    generationSeal = generationSeal,
                    renderStep = renderStep,
                    renderScope = renderScope,
                    sealedRoutes = sealedRoutes,
                    semanticPackets = semanticPackets,
                    multiKeySeal = preparedPassSeal,
                )
            is GPUCorePrimitiveDirectPreparedPassSeal -> preparedPassSeal
        }
        if (
            singleKeySeal.structuralPipelineKey.shader ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient ||
            singleKeySeal.structuralPipelineKey.shader ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat ||
            singleKeySeal.structuralPipelineKey.shader ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient
            || singleKeySeal.structuralPipelineKey.shader ==
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat
        ) {
            if (singleKeySeal.structuralPipelineKey.blend is
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
            ) {
                return refused(
                    "unsupported.native-core-primitive.blend",
                    "Linear-gradient CorePrimitive native geometry does not admit destination-read blending.",
                )
            }
        }
        val uniformLayout = singleKeySeal.structuralPipelineKey.uniformLayout
        fun refuseAnalyticShape(message: String) = refused(
            "invalid.native-core-primitive.analytic-shape-uniform-seal",
            message,
        )
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1
        ) {
            return refused(
                "unsupported.native-core-primitive.dedicated-multi-pass-route",
                "Clip-stencil and coverage-mask programs require their dedicated native routes.",
            )
        }
        val analyticShapeUniformSeals = singleKeySeal.analyticShapeUniformSeals
        val analyticClipUniformSeals = singleKeySeal.analyticClipUniformSeals
        val analyticIntersectionUniformSeals = singleKeySeal.analyticIntersectionUniformSeals
        val exactUniformAuthority = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                singleKeySeal.uniformSlabSeal != null && analyticShapeUniformSeals.isEmpty() &&
                    analyticClipUniformSeals.isEmpty() &&
                    analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                singleKeySeal.uniformSlabSeal == null && analyticShapeUniformSeals.isEmpty() &&
                    analyticClipUniformSeals.size == semanticPackets.size &&
                    analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                singleKeySeal.uniformSlabSeal == null && analyticShapeUniformSeals.isEmpty() &&
                    analyticClipUniformSeals.isEmpty() &&
                    analyticIntersectionUniformSeals.size == semanticPackets.size
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                singleKeySeal.uniformSlabSeal == null &&
                    analyticShapeUniformSeals.size == semanticPackets.size &&
                    analyticClipUniformSeals.isEmpty() && analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                singleKeySeal.uniformSlabSeal == null &&
                    analyticShapeUniformSeals.size == semanticPackets.size &&
                    analyticClipUniformSeals.isEmpty() && analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
            -> singleKeySeal.uniformSlabSeal != null && analyticShapeUniformSeals.isEmpty() &&
                analyticClipUniformSeals.isEmpty() && analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct uniform authority validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct uniform authority validation")
        }
        if (!exactUniformAuthority) {
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "The direct pass seal must retain one complete packet-order uniform80 authority.",
                )
            }
            return refused(
                "invalid.native-core-primitive.analytic-uniform-seal",
                "The direct pass seal must retain exactly one complete packet-order uniform32, uniform64, or uniform160 authority.",
            )
        }
        val sealedUniformPlan = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                requireNotNull(singleKeySeal.uniformSlabSeal).plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                analyticClipUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                analyticIntersectionUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                analyticShapeUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                analyticShapeUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
            -> requireNotNull(singleKeySeal.uniformSlabSeal).plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1 ->
                requireNotNull(singleKeySeal.uniformSlabSeal).plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct uniform plan selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct uniform plan selection")
        }
        val expectedBindingLayoutHash = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                CORE_PRIMITIVE_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1 ->
                requireNotNull(corePrimitiveGradientBindingLayoutHash(singleKeySeal.structuralPipelineKey.shader))
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct binding layout selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct binding selection")
        }
        val uniformUploadBytes = singleKeySeal.packedUniformBytesForUpload()
        fun packedRangeEquals(offset: Long, expected: ByteArray): Boolean {
            if (offset < 0L || offset > uniformUploadBytes.size.toLong() - expected.size.toLong()) {
                return false
            }
            val start = offset.toInt()
            return expected.indices.all { index -> uniformUploadBytes[start + index] == expected[index] }
        }
        fun packedRangeIsZero(from: Long, until: Long): Boolean {
            if (from < 0L || until < from || until > uniformUploadBytes.size.toLong()) return false
            return (from.toInt() until until.toInt()).all { index -> uniformUploadBytes[index] == 0.toByte() }
        }
        val targetBounds = semanticPackets.first().third.targetBounds
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(renderStep.target) ?: return refused(
            "unsupported.native-core-primitive.target-contract",
            "CorePrimitive requires one exact supported scene target.",
        )
        val declaredTargetFormat = declaredTargetDescriptor.format
        val declaredTargetInterpretation =
            declaredTargetFormat.corePrimitiveInterpretationOrNull() ?: return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        val declaredStructuralColorFormat = declaredTargetFormat.corePrimitiveStructuralColorFormat()
        if (singleKeySeal.structuralPipelineKey.colorFormat != declaredStructuralColorFormat) {
            return refused(
                "invalid.native-core-primitive.target-contract",
                "CorePrimitive target format contradicts its structural pipeline authority.",
            )
        }
        val acceptedGeometries = semanticPackets.mapIndexed { packetIndex, (_, packet, semantic) ->
            val packetAuthority = packet.corePrimitivePreparedAuthority
            val expectedAnalyticShapeSeal = analyticShapeUniformSeals.getOrNull(packetIndex)
            val expectedAnalyticSeal = analyticClipUniformSeals.getOrNull(packetIndex)
            val expectedAnalyticIntersectionSeal = analyticIntersectionUniformSeals.getOrNull(packetIndex)
            if (!semantic.hasStructuralIntegrity() || packet.role != GPUDrawPacketRole.Shading ||
                packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.bindingLayoutHash != expectedBindingLayoutHash ||
                packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                packet.targetStateHash != corePrimitiveTargetStateHash(sampleCount, declaredTargetFormat) ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(semantic.scissorBounds) ||
                packetAuthority?.structuralPipelineKey != singleKeySeal.structuralPipelineKey ||
                packetAuthority.renderPipelineKey != packet.renderPipelineKey ||
                packetAuthority.uniformSlabSeal !== singleKeySeal.uniformSlabSeal ||
                packetAuthority.analyticShapeUniformSeal !== expectedAnalyticShapeSeal ||
                packetAuthority.analyticClipUniformSeal !== expectedAnalyticSeal ||
                packetAuthority.analyticIntersectionUniformSeal !== expectedAnalyticIntersectionSeal ||
                semantic.targetBounds != targetBounds
            ) {
                return refused(
                    "invalid.native-core-primitive.packet-authority",
                    "A CorePrimitive packet contradicts its immutable semantic, pipeline, uniform, or target authority.",
                )
            }
            sealedRoutes.routesByPacketId.getValue(packet.packetId)
        }
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
        ) {
            val analyticUniformBytes = if (
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) 128L else 80L
            val expectedSourceLabel = if (analyticUniformBytes == 128L)
                "core-primitive-analytic-drrect-uniform-pass" else "core-primitive-analytic-shape-uniform-pass"
            val expectedShader = if (analyticUniformBytes == 128L)
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect
            else GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape
            val expectedBindingLayoutHash = if (analyticUniformBytes == 128L)
                CORE_PRIMITIVE_ANALYTIC_DRRECT_BINDING_LAYOUT_HASH
            else CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
            if (sealedUniformPlan.totalBytes > Int.MAX_VALUE.toLong() ||
                sealedUniformPlan.sourceLabel != expectedSourceLabel ||
                sealedUniformPlan.slots.size != semanticPackets.size ||
                analyticShapeUniformSeals.any { seal -> seal.plan !== sealedUniformPlan }
            ) {
                return refuseAnalyticShape(
                    "Analytic shape packets must share one exact host-addressable uniform80 slab plan.",
                )
            }
            fun GPUPixelBounds.isContainedBy(outer: GPUPixelBounds): Boolean =
                left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom
            var verifiedPackedEnd = 0L
            semanticPackets.indices.forEach { packetIndex ->
                val (_, packet, semantic) = semanticPackets[packetIndex]
                val packetAuthority = requireNotNull(packet.corePrimitivePreparedAuthority)
                val seal = analyticShapeUniformSeals[packetIndex]
                val slot = sealedUniformPlan.slots[packetIndex]
                val rebuilt = buildCorePrimitiveAnalyticShapeUniform(
                    semantic,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
                )
                val expectedBytes = when (rebuilt) {
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> rebuilt.bytes
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> return refuseAnalyticShape(
                        "Analytic shape semantic can no longer be recomposed into the sealed uniform80 ABI.",
                    )
                }
                val route = acceptedGeometries[packetIndex]
                val renderScissor = route.renderScissor ?: return refuseAnalyticShape(
                    "Analytic shape route is missing its exact non-empty render scissor.",
                )
                val payloadEnd = try {
                    Math.addExact(slot.alignedOffset, analyticUniformBytes)
                } catch (_: ArithmeticException) {
                    -1L
                }
                val allocatedEnd = try {
                    Math.addExact(slot.alignedOffset, slot.allocatedBytes)
                } catch (_: ArithmeticException) {
                    -1L
                }
                val exactRange = slot.alignedOffset >= verifiedPackedEnd &&
                    payloadEnd >= slot.alignedOffset && allocatedEnd >= payloadEnd &&
                    allocatedEnd <= sealedUniformPlan.totalBytes
                if (route.lane != GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape ||
                    renderScissor.isEmpty || seal.renderScissor != renderScissor ||
                    !renderScissor.isContainedBy(semantic.targetBounds) ||
                    !renderScissor.isContainedBy(semantic.scissorBounds) ||
                    !seal.hasExactSemantic(semantic) ||
                    packetAuthority.analyticShapeUniformSeal !== seal ||
                    seal.slotIndex != packetIndex || seal.commandId != packet.commandIdValue ||
                    seal.packetId != packet.packetId ||
                    seal.structuralPipelineKey != singleKeySeal.structuralPipelineKey ||
                    seal.structuralPipelineKey != packetAuthority.structuralPipelineKey ||
                    seal.structuralPipelineKey.shader != expectedShader ||
                    seal.renderPipelineKey != packet.renderPipelineKey ||
                    seal.renderPipelineKey != packetAuthority.renderPipelineKey ||
                    seal.bindingLayoutHash != expectedBindingLayoutHash ||
                    packet.bindingLayoutHash != seal.bindingLayoutHash ||
                    seal.resourceGeneration != packet.resourceGeneration ||
                    seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                    seal.deviceGeneration != generationSeal.deviceGeneration.value ||
                    seal.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                    seal.payloadBytes != analyticUniformBytes || slot.payloadBytes != analyticUniformBytes ||
                    slot.slotLabel != (if (analyticUniformBytes == 128L)
                        "analytic-drrect-draw-${packet.commandIdValue}"
                    else "analytic-shape-draw-${packet.commandIdValue}") ||
                    seal.alignedOffset != slot.alignedOffset || slot.alignedOffset > UInt.MAX_VALUE.toLong() ||
                    !exactRange || !seal.hasExactPayload(expectedBytes) ||
                    !packedRangeIsZero(verifiedPackedEnd, slot.alignedOffset) ||
                    !packedRangeEquals(slot.alignedOffset, expectedBytes) ||
                    !packedRangeIsZero(payloadEnd, allocatedEnd)
                ) {
                    return refuseAnalyticShape(
                        "Analytic shape uniform80 seal contradicts packet, semantic, route, layout, offset, payload, or generation authority.",
                    )
                }
                verifiedPackedEnd = allocatedEnd
            }
            if (uniformUploadBytes.size.toLong() != sealedUniformPlan.totalBytes ||
                !packedRangeIsZero(verifiedPackedEnd, sealedUniformPlan.totalBytes)
            ) {
                return refuseAnalyticShape(
                    "Analytic shape uniform80 pass seal contradicts its exact slab plan or packed upload bytes.",
                )
            }
        }
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1
        ) {
            val uniformSeal = requireNotNull(singleKeySeal.uniformSlabSeal)
            if (sealedUniformPlan.totalBytes > Int.MAX_VALUE.toLong() ||
                sealedUniformPlan.sourceLabel != "core-primitive-uniform-pass" ||
                sealedUniformPlan.slots.size != semanticPackets.size ||
                uniformSeal.commandIds != semanticPackets.map { (_, packet, _) -> packet.commandIdValue }
            ) {
                return refused(
                    "invalid.native-core-primitive.gradient-uniform-seal",
                    "Gradient packets must share one exact ordered gradient uniform slab plan.",
                )
            }
            val expectedPayloads = semanticPackets.map { (_, packet, semantic) ->
                when (uniformLayout) {
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 ->
                        requireNotNull(semantic.payloadRef.uniformBlock).bytes
                    GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1 ->
                        when (val built = buildCorePrimitiveGradientAnalyticShapeUniform(
                            semantic,
                            GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
                        )) {
                            is GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult.Accepted ->
                                built.bytes.map { byte -> byte.toInt() and 0xff }
                            is GPUCorePrimitiveGradientAnalyticShapeUniformBuildResult.Refused ->
                                return refused(built.code, built.message)
                        }
                    else -> error("Not a gradient uniform layout")
                }
            }
            var verifiedPackedEnd = 0L
            semanticPackets.indices.forEach { packetIndex ->
                val (_, packet, semantic) = semanticPackets[packetIndex]
                val slot = sealedUniformPlan.slots[packetIndex]
                val expectedPayload = expectedPayloads[packetIndex]
                val payloadEnd = try {
                    Math.addExact(slot.alignedOffset, expectedPayload.size.toLong())
                } catch (_: ArithmeticException) {
                    -1L
                }
                val allocatedEnd = try {
                    Math.addExact(slot.alignedOffset, slot.allocatedBytes)
                } catch (_: ArithmeticException) {
                    -1L
                }
                if (slot.slotLabel != "draw-${packet.commandIdValue}" ||
                    slot.payloadBytes != expectedPayload.size.toLong() ||
                    !uniformSeal.hasExactPayload(packetIndex, packet.commandIdValue, expectedPayload) ||
                    slot.alignedOffset < verifiedPackedEnd ||
                    payloadEnd < slot.alignedOffset ||
                    allocatedEnd < payloadEnd ||
                    allocatedEnd > sealedUniformPlan.totalBytes ||
                    packet.corePrimitivePreparedAuthority?.structuralPipelineKey !=
                    singleKeySeal.structuralPipelineKey ||
                    (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1 &&
                        acceptedGeometries[packetIndex].lane != GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape) ||
                    (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1 &&
                        acceptedGeometries[packetIndex].lane != GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry)
                ) {
                    return refused(
                        "invalid.native-core-primitive.gradient-uniform-seal",
                        "Gradient uniform slab payload contradicts packet, route, layout, or offset authority.",
                    )
                }
                verifiedPackedEnd = allocatedEnd
            }
            if (uniformUploadBytes.size.toLong() != sealedUniformPlan.totalBytes ||
                !packedRangeIsZero(verifiedPackedEnd, sealedUniformPlan.totalBytes)
            ) {
                return refused(
                    "invalid.native-core-primitive.gradient-uniform-seal",
                    "Gradient uniform slab upload bytes contradict the exact sealed plan.",
                )
            }
        }
        if (uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1
        ) {
            val exactPayloads = mutableListOf<GPUUniformSlabPayload>()
            val exactIntersectionAuthority = run validation@{
                if (sealedUniformPlan.totalBytes > Int.MAX_VALUE.toLong() ||
                    sealedUniformPlan.sourceLabel != "core-primitive-analytic-intersection-uniform-pass" ||
                    sealedUniformPlan.slots.size != semanticPackets.size ||
                    analyticIntersectionUniformSeals.any { seal -> seal.plan !== sealedUniformPlan }
                ) return@validation false
                var verifiedPackedEnd = 0L
                semanticPackets.indices.forEach { packetIndex ->
                    val (_, packet, semantic) = semanticPackets[packetIndex]
                    val packetAuthority = requireNotNull(packet.corePrimitivePreparedAuthority)
                    val packetClip = corePrimitiveAnalyticIntersectionPacketAuthority(
                        packet,
                        semantic.targetBounds,
                    ) ?: return@validation false
                    val expectedClip = packetClip.clip
                    val seal = analyticIntersectionUniformSeals[packetIndex]
                    val slot = sealedUniformPlan.slots[packetIndex]
                    val expectedBytes = corePrimitiveAnalyticIntersectionUniformBytes(semantic, expectedClip)
                    val payloadEnd = try {
                        Math.addExact(slot.alignedOffset, 160L)
                    } catch (_: ArithmeticException) {
                        -1L
                    }
                    val allocatedEnd = try {
                        Math.addExact(slot.alignedOffset, slot.allocatedBytes)
                    } catch (_: ArithmeticException) {
                        -1L
                    }
                    val exactRange = slot.alignedOffset >= verifiedPackedEnd &&
                        payloadEnd >= slot.alignedOffset && allocatedEnd >= payloadEnd &&
                        allocatedEnd <= sealedUniformPlan.totalBytes
                    val exactElements = seal.elements.size == expectedClip.elements.size &&
                        seal.elements.indices.all { elementIndex ->
                            val actual = seal.elements[elementIndex]
                            val expected = expectedClip.elements[elementIndex]
                            actual.clipType == expected.clipType && actual.clipBounds == expected.bounds &&
                                actual.clipRadii == expected.packedRadii &&
                                actual.antiAlias == expected.antiAlias
                        }
                    if (seal.slotIndex != packetIndex || seal.commandId != packet.commandIdValue ||
                        seal.packetId != packet.packetId ||
                        seal.clipCanonicalIdentity != packetClip.canonicalIdentity || !exactElements ||
                        seal.conservativeScissor != expectedClip.conservativeScissor ||
                        semantic.scissorBounds != expectedClip.conservativeScissor ||
                        seal.structuralPipelineKey != packetAuthority.structuralPipelineKey ||
                        seal.renderPipelineKey != packetAuthority.renderPipelineKey ||
                        seal.bindingLayoutHash != CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH ||
                        seal.resourceGeneration != packet.resourceGeneration ||
                        seal.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                        seal.deviceGeneration != generationSeal.deviceGeneration.value ||
                        seal.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
                        seal.payloadBytes != 160L || slot.payloadBytes != 160L ||
                        slot.slotLabel != "analytic-intersection-draw-${packet.commandIdValue}" ||
                        seal.alignedOffset != slot.alignedOffset || slot.alignedOffset > UInt.MAX_VALUE.toLong() ||
                        !exactRange || !seal.hasExactPayload(expectedBytes) ||
                        !packedRangeIsZero(verifiedPackedEnd, slot.alignedOffset) ||
                        !packedRangeEquals(slot.alignedOffset, expectedBytes) ||
                        !packedRangeIsZero(payloadEnd, allocatedEnd)
                    ) return@validation false
                    verifiedPackedEnd = allocatedEnd
                    exactPayloads += GPUUniformSlabPayload(slot.slotLabel, expectedBytes)
                }
                sealedUniformPlan.hasExactPayloads(
                    "core-primitive-analytic-intersection-uniform-pass",
                    generationSeal.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    exactPayloads,
                ) && uniformUploadBytes.size.toLong() == sealedUniformPlan.totalBytes &&
                    packedRangeIsZero(verifiedPackedEnd, sealedUniformPlan.totalBytes)
            }
            if (!exactIntersectionAuthority) {
                return refused(
                    "invalid.native-core-primitive.analytic-intersection-uniform-seal",
                    "The uniform160 pass seal contradicts its exact packet, clip, layout, offset, payload, or generation authority.",
                )
            }
        }
        val renderScissors = acceptedGeometries.mapIndexed { index, route ->
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1
            ) {
                route.renderScissor ?: return refuseAnalyticShape(
                    "Analytic shape route is missing its exact non-empty render scissor.",
                )
            } else {
                semanticPackets[index].third.scissorBounds
            }
        }
        val arena = try {
            packCorePrimitiveFrameGeometry(acceptedGeometries)
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.geometry-arena",
                "Direct CorePrimitive geometry cannot be packed safely: ${failure::class.simpleName.orEmpty()}.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        val geometrySlicesValid: Boolean
        try {
            vertexBytes = Math.multiplyExact(arena.vertices.size.toLong(), Float.SIZE_BYTES.toLong())
            indexBytes = Math.multiplyExact(arena.indices.size.toLong(), Int.SIZE_BYTES.toLong())
            val totalVertexCount = arena.vertices.size / 2
            var expectedFirstIndex = 0
            var expectedBaseVertex = 0
            geometrySlicesValid = arena.vertices.size % 2 == 0 && arena.slices.all { slice ->
                val nextFirstIndex = Math.addExact(slice.firstIndex, slice.indexCount)
                val nextBaseVertex = Math.addExact(slice.baseVertex, slice.vertexCount)
                val maximumAddressedVertex = Math.addExact(slice.baseVertex, slice.maxLocalIndex)
                val valid = slice.firstIndex == expectedFirstIndex &&
                    slice.baseVertex == expectedBaseVertex &&
                    slice.indexCount > 0 && slice.vertexCount > 0 &&
                    slice.maxLocalIndex in 0 until slice.vertexCount &&
                    nextFirstIndex <= arena.indices.size && nextBaseVertex <= totalVertexCount &&
                    maximumAddressedVertex < totalVertexCount
                expectedFirstIndex = nextFirstIndex
                expectedBaseVertex = nextBaseVertex
                valid
            } && expectedFirstIndex == arena.indices.size && expectedBaseVertex == totalVertexCount
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices overflow their exact shared-slab convention.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L || vertexBytes % 8L != 0L || indexBytes % 4L != 0L ||
            !geometrySlicesValid
        ) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices violate the exact shared-slab offset convention.",
            )
        }

        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "Direct CorePrimitive accepts only render scopes and one optional readback scope.",
            )
        }
        if (readbackStep != null && readbackStep.request.sourceBounds != targetBounds) {
            return refused(
                "unsupported.native-core-primitive.readback-layout",
                "CorePrimitive readback must cover the exact canonical target bounds.",
            )
        }
        // Defensive reachability guard: every direct-only frame retains the unified scope route
        // seal, so real destination-reading frames materialize through the frame-global run path
        // (materializeSingleSampleFrameGlobalCore) below; this single-key branch keeps the same
        // dst-copy authority so the validation and emission stay consistent whichever route runs.
        val copyAuthority = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = listOf(renderStep),
                targetBounds = targetBounds,
                targetFormat = declaredTargetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities.singleOrNull()
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        }
        val expectedEncoderSteps = 1 +
            (if (copyAuthority == null) 0 else 1) +
            (if (readbackStep == null) 0 else 1)
        if (framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } != expectedEncoderSteps) {
            return refused(
                "unsupported.native-core-primitive.encoder-shape",
                "Direct CorePrimitive contains an unsupported encoder operation.",
            )
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The direct CorePrimitive readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(copyAuthority?.copyScope, renderScope, readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "CorePrimitive encoder scopes must preserve copy, render, then optional readback order.",
            )
        }
        val exactSampleAuthority = when (renderStep.samplePlan) {
            GPUSamplePlan.SingleSampleFrame -> renderStep.sampleContinuation == null
            is GPUSamplePlan.MultisampleFrame -> renderStep.samplePlan.sampleCount == 4 &&
                renderStep.sampleContinuation?.let { continuation ->
                    continuation.key.target.value == renderStep.target.value &&
                        continuation.key.targetGeneration == generationSeal.targetGeneration &&
                        continuation.key.deviceGeneration == generationSeal.deviceGeneration &&
                        continuation.key.colorFormat == declaredTargetFormat &&
                        continuation.key.colorInterpretation == declaredTargetInterpretation &&
                        continuation.key.samplePlan == renderStep.samplePlan &&
                        continuation.key.attachmentAuthority ==
                        org.graphiks.kanvas.gpu.renderer.passes
                            .GPUSampleAttachmentAuthority.PreparedFramePayload &&
                        continuation.key.colorAttachment.value ==
                        "msaa-color:${renderStep.target.value}:${generationSeal.targetGeneration}" &&
                        continuation.key.depthStencilAttachment == null &&
                        continuation.loadTransition == GPUSampleLoadTransition.FreshClear &&
                        continuation.storeAction == GPUSampleStoreAction.Store &&
                        continuation.resolveAction == GPUSampleResolveAction.ResolveCanonical
                } == true
            is GPUSamplePlan.LocalResolveApproximation -> false
        }
        if (!exactSampleAuthority ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null || renderStep.depthStencilLoadStore != null ||
            renderStep.resourceUses.any {
                it.role == GPUFrameResourceRole.PathDepthStencil ||
                    it.role == GPUFrameResourceRole.ClipDepthStencil
            } || semanticPackets.any { (_, _, semantic) ->
                semantic.scissorBounds.isEmpty ||
                semantic.scissorBounds.left < targetBounds.left || semantic.scissorBounds.top < targetBounds.top ||
                semantic.scissorBounds.right > targetBounds.right || semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.render-state",
                "CorePrimitive requires one exact clear/store 1x or color-only 4x resolve pass and contained scissors.",
            )
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
            ?: return refused("unsupported.native-core-primitive.target", "CorePrimitive target declaration is missing.")
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
            ?: return refused("unsupported.native-core-primitive.vertex", "CorePrimitive vertex slab declaration is missing.")
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
            ?: return refused("unsupported.native-core-primitive.index", "CorePrimitive index slab declaration is missing.")
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
            ?: return refused("unsupported.native-core-primitive.uniform", "CorePrimitive uniform slab declaration is missing.")
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        val expectedPreparationCount = 4 +
            (if (copyAuthority == null) 0 else 1) +
            (if (readbackStep == null) 0 else 1)
        if (preparations.size != expectedPreparationCount ||
            (readbackStep == null) != (stagingPreparation == null)
        ) {
            return refused(
                "unsupported.native-core-primitive.resource-shape",
                "CorePrimitive requires exactly target, shared vertex/index/uniform slabs, and optional readback staging.",
            )
        }
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        if (targetPreparation.resource != renderSteps.first().target ||
            renderSteps.any { it.target != targetPreparation.resource } || targetDescriptor == null ||
            targetDescriptor != declaredTargetDescriptor ||
            targetDescriptor.logicalBounds != targetBounds ||
            !targetDescriptor.format.isCorePrimitiveSceneTargetFormat() ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact frame-local supported scene target.",
            )
        }
        if (isMsaa4x) {
            val expectedMsaaBytes = try {
                Math.multiplyExact(
                    Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                    Math.multiplyExact(RGBA_BYTES_PER_PIXEL, sampleCount.toLong()),
                )
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.native-core-primitive.msaa-budget",
                    "The 4x CorePrimitive color attachment byte size overflowed.",
                )
            }
            if (framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaColor] !=
                expectedMsaaBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.msaa-budget",
                    "The 4x CorePrimitive color attachment must have exact aggregate memory authority.",
                )
            }
        }
        fun exactGeometryBuffer(
            preparation: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            bytes: Long,
        ): Boolean {
            val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor ?: return false
            return preparation.role == role && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                preparation.byteSize == bytes &&
                preparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactGeometryBuffer(vertexPreparation, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, vertexBytes) ||
            !exactGeometryBuffer(indexPreparation, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, indexBytes) ||
            setOf(vertexPreparation.resource, indexPreparation.resource, uniformPreparation.resource).size != 3
        ) {
            return refused(
                "unsupported.native-core-primitive.buffer-contract",
                "CorePrimitive shared Float32x2 vertex and Uint32 index slabs are not exact.",
            )
        }
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform slab requires one exact buffer descriptor.",
            )
        val uniformSlabPlan = sealedUniformPlan
        if (uniformSlabPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformSlabPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment
        ) {
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "Analytic shape uniform80 slab authority is stale for the materialized device generation.",
                )
            }
            return refused(
                "invalid.native-core-primitive.uniform-seal-generation",
                "CorePrimitive builder uniform authority is stale for the materialized device generation.",
            )
        }
        if (uniformDescriptor.byteSize != uniformSlabPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSlabPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformSlabPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "Analytic shape uniform80 preparation differs from the sealed aligned slab plan.",
                )
            }
            return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform preparation differs from the sealed aligned slab plan.",
            )
        }
        if (uniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "Analytic shape uniform80 slab exceeds the host-addressable ByteArray size.",
                )
            }
            return refused(
                "unsupported.native-core-primitive.uniform-slab-host-size",
                "CorePrimitive uniform slab exceeds the host-addressable ByteArray size.",
            )
        }
        val pipelineMappingCandidate = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
            singleKeySeal.structuralPipelineKey,
        )
        val pipelineMapping = pipelineMappingCandidate as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped
        if (pipelineMapping == null) {
            // Destination-reading keys without a formula program (scalar-coverage dst-read on the
            // analytic-shape lane) refuse by name so the surface router continues on the legacy
            // route.
            if (singleKeySeal.structuralPipelineKey.blend is
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
            ) {
                return refused(
                    "unsupported.native-core-primitive.dst-read-formula",
                    "The destination-read formula program is not available for this direct structural shape.",
                )
            }
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "Analytic shape structural authority has no exact native pipeline.",
                )
            }
            return refused(
                "unsupported.native-core-primitive.pipeline",
                "The direct CorePrimitive structural key has no exact native pipeline.",
            )
        }
        val expectedComponentIdentity = when {
            singleKeySeal.structuralPipelineKey.blend is
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination ->
                requireNotNull(
                    singleKeySeal.structuralPipelineKey.corePrimitiveNativeComponentIdentityOrNull(),
                )
            else -> when (uniformLayout) {
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                    PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_DRRECT_COMPONENT_IDENTITY
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
                -> corePrimitiveGradientComponentIdentity(singleKeySeal.structuralPipelineKey.shader)
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                    error("NoBindingsV1 was refused before direct component selection")
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
                -> error("Coverage-mask layouts were refused before direct component selection")
            }
        }
        val exactProgram = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                !pipelineMapping.identity.program.isAnalyticClip() &&
                    !pipelineMapping.identity.program.isAnalyticShape() &&
                    !pipelineMapping.identity.program.isAnalyticIntersection4()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                pipelineMapping.identity.program.isAnalyticClip()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                pipelineMapping.identity.program.isAnalyticIntersection4()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                pipelineMapping.identity.program.isAnalyticShapeProgram()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1 ->
                pipelineMapping.identity.program.isAnalyticDRRect()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
            -> pipelineMapping.identity.program.isGradient()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct program validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct program validation")
        }
        if (pipelineMapping.componentIdentity != expectedComponentIdentity || !exactProgram) {
            if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ||
                uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1
            ) {
                return refuseAnalyticShape(
                    "Analytic shape pipeline component, program, and uniform80 layout authorities disagree.",
                )
            }
            return refused(
                "invalid.native-core-primitive.pipeline-layout",
                "The direct CorePrimitive pipeline and uniform layout authorities disagree.",
            )
        }
        val pipelineCacheKey = GPUWgpu4kCorePrimitivePipelineCacheKey(
            pipelineMapping.componentIdentity,
            pipelineMapping.identity,
        )
        val exactUses = setOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                vertexPreparation.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                indexPreparation.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                uniformPreparation.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        )
        if (renderSteps.any { render ->
                render.resourceUses.filter {
                    it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData ||
                        it.role == GPUFrameResourceRole.UniformData
                }.toSet() != exactUses
            }
        ) {
            return refused(
                "invalid.native-core-primitive.render-resource-uses",
                "Every direct CorePrimitive draw must read the exact shared vertex and index slabs.",
            )
        }

        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        val expectedOrdinaryResources = 4 + (if (copyAuthority == null) 0 else 1)
        if (resources.ordinaryResources.size != expectedOrdinaryResources ||
            listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any { preparation ->
                val evidence = preparedByLogical[preparation.resource]
                val expectedKind = if (preparation.role == GPUFrameResourceRole.SceneTarget) {
                    GPUPreparedConcreteResourceRef.Texture::class.java
                } else {
                    GPUPreparedConcreteResourceRef.Buffer::class.java
                }
                evidence == null || evidence.role != preparation.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[preparation.resource] ||
                    !expectedKind.isInstance(evidence.concreteResource)
            } || listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any {
                generationSeal.resourceGenerations[it.resource] == null
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-resources",
                "CorePrimitive prepared target and geometry evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed CorePrimitive target.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional CorePrimitive readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != targetPreparation.resource || readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned CorePrimitive RGBA8 readback layout is not exact.",
                )
            }
        }

        if (!isMsaa4x && unifiedRoute != null) {
            return materializeSingleSampleFrameGlobalCore(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                resources = resources,
                generationSeal = generationSeal,
                renderStep = renderStep,
                renderScope = renderScope,
                route = unifiedRoute,
                readbackScope = readbackScope,
                output = output,
                targetFormat = declaredTargetFormat,
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val msaaColorRequirement = if (isMsaa4x) {
                GPUWgpu4kCorePrimitiveMsaaColorRequirement(
                    target = renderStep.target,
                    colorAttachment = requireNotNull(renderStep.sampleContinuation).key.colorAttachment,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    width = targetBounds.width,
                    height = targetBounds.height,
                    format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                )
            } else {
                null
            }
            val dstReadSnapshot = copyAuthority?.let { authority ->
                createCorePrimitiveDestinationSnapshot(
                    authority,
                    declaredTargetFormat,
                    targetBounds,
                )
            }
            val invariants = when (
                val acquired = sessionCache.acquire(
                    pipelineCacheKey,
                )
            ) {
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> acquired
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedSessionCacheAcquire(acquired.reason)
                }
            }
            frameLease = when (
                val checkout = sessionCache.acquireFrame(
                    GPUWgpu4kCorePrimitiveFramePoolRequirements(
                        generationSeal.deviceGeneration,
                        vertexBytes,
                        indexBytes,
                        uniformSlabPlan.totalBytes,
                        componentIdentity = pipelineMapping.componentIdentity,
                        sampleCount = sampleCount,
                        msaaColor = msaaColorRequirement,
                        dstRead = dstReadSnapshot?.binding,
                    ),
                )
            ) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val vertexBuffer = pooled.handles.vertexBuffer
            val indexBuffer = pooled.handles.indexBuffer
            val uniformBuffer = pooled.handles.uniformBuffer
            val bindGroup = pooled.handles.bindGroup
            check(pooled.handles.sampleCount == sampleCount &&
                pooled.handles.msaaColor?.requirement == msaaColorRequirement &&
                (isMsaa4x == (pooled.handles.msaaColor != null))
            ) { "Pooled CorePrimitive color attachment differs from its exact sample authority" }
            uploadExact(
                vertexBuffer,
                ArrayBuffer.of(arena.vertices),
                usedBytes = vertexBytes,
                capacityBytes = pooled.capacities.vertexBytes,
            )
            uploadExact(
                indexBuffer,
                ArrayBuffer.of(arena.indices),
                usedBytes = indexBytes,
                capacityBytes = pooled.capacities.indexBytes,
            )
            uploadExact(
                uniformBuffer,
                ArrayBuffer.of(uniformUploadBytes),
                usedBytes = uniformSlabPlan.totalBytes,
                capacityBytes = pooled.capacities.uniformBytes,
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.readback",
                    ),
                ).tracked()
            }
            val canonicalTargetViewOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val colorTargetViewOperand = pooled.handles.msaaColor?.let { msaa ->
                GPUPreparedNativeTextureViewOperand(
                    msaa.view,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            } ?: canonicalTargetViewOperand
            val pipelineOperand = GPUPreparedNativeRenderPipelineOperand(
                invariants.pipeline,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val vertexOperand = GPUPreparedNativeBufferOperand(
                vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.indexBytes,
            )
            val sharedBindGroupOperand = GPUPreparedNativeBindGroupOperand(
                bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val renderCommands = buildList {
                add(GPUPreparedNativeRenderCommand.SetPipeline(pipelineOperand))
                add(
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        offset = 0L,
                        size = vertexBytes,
                        vertexStrideBytes = 8L,
                    ),
                )
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        offset = 0L,
                        size = indexBytes,
                    ),
                )
                semanticPackets.indices.forEach { index ->
                    val slice = arena.slices[index]
                    val uniformSlot = uniformSlabPlan.slots[index]
                    val renderScissor = renderScissors[index]
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            sharedBindGroupOperand,
                            dynamicOffsets = listOf(uniformSlot.alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            renderScissor.left,
                            renderScissor.top,
                            renderScissor.width,
                            renderScissor.height,
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = slice.indexCount,
                                firstIndex = slice.firstIndex,
                                baseVertex = slice.baseVertex,
                                vertexCount = slice.vertexCount,
                                maxLocalIndex = slice.maxLocalIndex,
                            ),
                        ),
                    )
                }
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = colorTargetViewOperand,
                    resolveTarget = canonicalTargetViewOperand.takeIf { isMsaa4x },
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                ),
                commands = renderCommands,
                semanticPayloads = semanticPackets.map { it.third },
            )
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
                        format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else {
                null
            }
            val copyOperand = copyAuthority?.let { authority ->
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = authority.copyScope.sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        requireNotNull(dstReadSnapshot).texture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = targetBounds.left,
                        sourceOriginY = targetBounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = targetBounds.width,
                        height = targetBounds.height,
                    ),
                )
            }
            val operandsByStep = (listOfNotNull(copyOperand, renderOperand) + listOfNotNull(readbackOperand))
                .associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
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
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = dstReadSnapshot?.payloadOwnedAuxiliaryHandles().orEmpty(),
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.materialization",
                "Public wgpu4k CorePrimitive materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    /**
     * Materializes the closed W3 encoder scratch.  Its V/I/U buffers are physical pooled
     * resources, never logical frame-plan resources or memory-budget allocations.
     */
    private fun materializeW3SessionScratch(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
        renderStep: GPUFrameStep.RenderPassStep,
        scratch: W3SessionScratchV1,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val packets = renderStep.drawPackets
        val semantics = packets.map { it.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive }
        val readbackStep = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .singleOrNull() ?: return refused(
            "invalid.native-core-primitive.w3-readback",
            "W3 requires one sealed readback step.",
        )
        val expectedPlanId = readbackStep.request.requestId.value
            .takeIf { it.startsWith("w3.") && it.endsWith(".readback") }
            ?.removePrefix("w3.")
            ?.removeSuffix(".readback")
            ?.takeIf(String::isNotBlank)
            ?: return refused(
                "invalid.native-core-primitive.w3-scratch",
                "W3 readback identity cannot bind the scratch plan.",
            )
        val maxBufferSize = limits.maxBufferSize ?: return refused(
            "invalid.native-core-primitive.w3-limits",
            "W3 requires an observed maxBufferSize.",
        )
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout ?: return refused(
            "invalid.native-core-primitive.w3-limits",
            "W3 requires an observed dynamic-uniform limit.",
        )
        val targetFormat = framePlan.corePrimitiveSceneTargetDescriptor(renderStep.target)?.format
            ?: return refused("invalid.native-core-primitive.w3-target", "W3 target descriptor is missing.")
        val renderScope = encoderPlan.scopes.singleOrNull {
            it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused("invalid.native-core-primitive.w3-scope", "W3 requires one render encoder scope.")
        val readbackScope = encoderPlan.scopes.singleOrNull {
            it.operationKind == GPUEncoderOperationKind.Readback
        } ?: return refused("invalid.native-core-primitive.w3-scope", "W3 requires one readback encoder scope.")
        if (encoderPlan.scopes.size != 2 ||
            renderScope.sourceStepIndex != framePlan.steps.indexOf(renderStep) ||
            readbackScope.sourceStepIndex != framePlan.steps.indexOf(readbackStep) ||
            targetFormat != GPUColorFormat.RGBA8UnormSrgb ||
            preparedSceneTarget.width != scratch.targetBounds.width ||
            preparedSceneTarget.height != scratch.targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration ||
            generationSeal.capabilitySealHash != framePlan.capabilitySeal.sealHash ||
            resources.ordinaryResources.singleOrNull()?.let { evidence ->
                evidence.logicalResource == scratch.target &&
                    evidence.role == GPUFrameResourceRole.SceneTarget &&
                    evidence.deviceGeneration == generationSeal.deviceGeneration
            } != true ||
            semantics.any { it == null } || !scratch.matches(
                expectedPlanId,
                framePlan.capabilitySeal.sealHash,
                generationSeal.deviceGeneration.value,
                renderStep.target,
                readbackStep.staging,
                semantics.first()?.targetBounds ?: return refused(
                    "invalid.native-core-primitive.w3-scratch",
                    "W3 scratch has no semantic target.",
                ),
                packets,
            ) ||
            packets.any { it.corePrimitivePreparedAuthority?.w3SessionScratch !== scratch } ||
            scratch.uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            scratch.uniformPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            !scratch.hasExactUniformPayloads(limits.minUniformBufferOffsetAlignment, packets) ||
            !scratch.fitsDeviceLimits(maxBufferSize, maxDynamicUniformBuffers)
        ) {
            return refused(
                "invalid.native-core-primitive.w3-scratch",
                "W3 encoder scratch is stale or contradicts the frame authority.",
            )
        }
        val staging = resources.outputOwnedReadbacks.singleOrNull()
            ?: return refused("invalid.native-core-primitive.w3-readback", "W3 output-owned readback is missing.")
        if (staging.stagingResource != scratch.staging || staging.request != readbackStep.request ||
            staging.layout.width != scratch.targetBounds.width ||
            staging.layout.height != scratch.targetBounds.height ||
            staging.stagingLease.backingBufferBytes < staging.layout.totalBufferBytes
        ) {
            return refused(
                "invalid.native-core-primitive.w3-readback",
                "W3 output-owned readback contradicts its sealed staging authority.",
            )
        }
        val coreSemantics = semantics.filterNotNull()
        val routes = coreSemantics.mapIndexed { index, semantic ->
            validateCorePrimitiveDirectNativeRoute(
                semantic,
                semantic.scissorBounds,
                packets[index].blendPlan ?: return refused(
                    "invalid.native-core-primitive.w3-blend",
                    "W3 packet is missing SrcOver blend authority.",
                ),
                GPUSamplePlan.SingleSampleFrame,
                targetFormat.value,
            ) as? GPUCorePrimitiveDirectNativeRoute.Accepted ?: return refused(
                "invalid.native-core-primitive.w3-geometry",
                "W3 scratch geometry is not an admitted direct CorePrimitive route.",
            )
        }
        val arena = try {
            packCorePrimitiveFrameGeometry(routes)
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.w3-geometry",
                "W3 scratch geometry packing failed: ${failure::class.simpleName.orEmpty()}.",
            )
        }
        val vertexBytes = arena.vertices.size.toLong() * Float.SIZE_BYTES
        val indexBytes = arena.indices.size.toLong() * Int.SIZE_BYTES
        if (vertexBytes != scratch.vertexBytes || indexBytes != scratch.indexBytes) {
            return refused("invalid.native-core-primitive.w3-packing", "W3 scratch V/I packing is not exact.")
        }
        val uniformBytes = ByteArray(scratch.uniformPlan.totalBytes.toInt())
        coreSemantics.forEachIndexed { index, semantic ->
            val bytes = semantic.payloadRef.uniformBlock?.bytes?.map(Int::toByte)?.toByteArray()
                ?: return refused("invalid.native-core-primitive.w3-uniform", "W3 packet uniform payload is missing.")
            val slot = scratch.uniformPlan.slots[index]
            if (bytes.size != 32 || slot.payloadBytes != 32L ||
                slot.alignedOffset + bytes.size > uniformBytes.size
            ) {
                return refused("invalid.native-core-primitive.w3-uniform", "W3 uniform packing is not exact.")
            }
            bytes.copyInto(uniformBytes, slot.alignedOffset.toInt())
        }
        val mapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(scratch.structuralPipelineKey)
            as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped
            ?: return refused("unsupported.native-core-primitive.w3-pipeline", "W3 structural pipeline is unavailable.")
        val cache = when (val acquired = sessionCache.acquire(
            GPUWgpu4kCorePrimitivePipelineCacheKey(mapping.componentIdentity, mapping.identity),
        )) {
            is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> acquired
            is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> return refusedSessionCacheAcquire(acquired.reason)
        }
        synchronized(this) {
            if (closed) return refused("unsupported.native-core-primitive.materializer-state", "The W3 materializer is closed.")
            materializing = true
        }
        var lease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var transferred = false
        return try {
            lease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    generationSeal.deviceGeneration,
                    scratch.vertexBytes,
                    scratch.indexBytes,
                    scratch.uniformPlan.totalBytes,
                    componentIdentity = mapping.componentIdentity,
                    sampleCount = 1,
                ),
            )) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(lease)
            uploadExact(pooled.handles.vertexBuffer, ArrayBuffer.of(arena.vertices), scratch.vertexBytes, pooled.capacities.vertexBytes)
            uploadExact(pooled.handles.indexBuffer, ArrayBuffer.of(arena.indices), scratch.indexBytes, pooled.capacities.indexBytes)
            uploadExact(pooled.handles.uniformBuffer, ArrayBuffer.of(uniformBytes), scratch.uniformPlan.totalBytes, pooled.capacities.uniformBytes)
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val stagingBuffer = device.createBuffer(
                BufferDescriptor(
                    size = staging.stagingLease.backingBufferBytes.toULong(),
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    mappedAtCreation = false,
                    label = "Kanvas.frame.w3.readback",
                ),
            ).tracked()
            val generation = generationSeal.deviceGeneration
            val pipeline = GPUPreparedNativeRenderPipelineOperand(cache.pipeline, generation, GPUPreparedNativeOperandOwnership.Borrowed)
            val vertex = GPUPreparedNativeBufferOperand(pooled.handles.vertexBuffer, generation, GPUPreparedNativeOperandOwnership.Borrowed, pooled.capacities.vertexBytes)
            val index = GPUPreparedNativeBufferOperand(pooled.handles.indexBuffer, generation, GPUPreparedNativeOperandOwnership.Borrowed, pooled.capacities.indexBytes)
            val bindGroup = GPUPreparedNativeBindGroupOperand(pooled.handles.bindGroup, generation, GPUPreparedNativeOperandOwnership.Borrowed)
            val commands = buildList {
                add(GPUPreparedNativeRenderCommand.SetPipeline(pipeline))
                add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0L, scratch.vertexBytes, 8L))
                add(GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0L, scratch.indexBytes))
                coreSemantics.indices.forEach { indexValue ->
                    val slice = arena.slices[indexValue]
                    val scissor = coreSemantics[indexValue].scissorBounds
                    add(GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup, listOf(scratch.uniformPlan.slots[indexValue].alignedOffset)))
                    add(GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width, scissor.height))
                    add(GPUPreparedNativeRenderCommand.DrawIndexed(
                        GPUPreparedNativeDrawCall.DrawIndexed(
                            indexCount = slice.indexCount,
                            firstIndex = slice.firstIndex,
                            baseVertex = slice.baseVertex,
                            vertexCount = slice.vertexCount,
                            maxLocalIndex = slice.maxLocalIndex,
                        ),
                    ))
                }
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                renderScope.sourceStepIndex,
                GPUPreparedNativeRenderPassConfig(
                    GPUPreparedNativeTextureViewOperand(targetView, generation, GPUPreparedNativeOperandOwnership.Borrowed),
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                ),
                commands,
                coreSemantics,
            )
            val readbackOperand = GPUPreparedNativeScopeOperand.Readback(
                readbackScope.sourceStepIndex,
                GPUPreparedNativeTextureOperand(targetTexture, generation, GPUPreparedNativeOperandOwnership.Borrowed),
                GPUPreparedNativeBufferOperand(stagingBuffer, generation, GPUPreparedNativeOperandOwnership.OutputOwnedReadback),
                GPUPreparedNativeReadbackLayout(
                    staging.request.sourceBounds.left,
                    staging.request.sourceBounds.top,
                    staging.layout.width,
                    staging.layout.height,
                    staging.layout.paddedBytesPerRow,
                    staging.layout.rowsPerImage,
                    staging.layout.bufferOffset,
                    staging.layout.totalBufferBytes,
                    targetFormat.toCorePrimitiveGPUTextureFormat(),
                ),
            )
            val scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys }
            val keys = encoderPlan.scopes.mapIndexed { index, scope ->
                GPUPreparedNativeScopeKey(scope.sourceStepIndex, scope.operationKind, scope.resourceGenerationLabels, scopeOperandKeys[index])
            }
            val payload = GPUPreparedNativeFramePayload(
                GPUPreparedNativeFrameIdentity(framePlan.frameId, encoderPlan.contextIdentity, encoderPlan.planId, generation, generationSeal.targetGeneration, keys),
                keys.map { key -> if (key.sourceStepIndex == renderScope.sourceStepIndex) renderOperand else readbackOperand },
                scopeOperandKeys,
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            synchronized(this) {
                check(!closed) { "Native W3 materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                transferred = true
            }
            GPUPreparedNativeFramePayloadMaterialization.Materialized(GPUPreparedNativeFrameDraft(payload))
        } catch (failure: Throwable) {
            if (!transferred) terminalizePooledLeaseBeforeRegistration(lease)
            synchronized(this) { materializing = false; preRegistrationHandles.closeRetainingFailures() }
            refused("failed.native-core-primitive.w3-materialization", "W3 native materialization failed: ${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.")
        }
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = if (acquiredSurface == null) {
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    } else {
        GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "unsupported.native-core-primitive.surface",
            "The direct CorePrimitive route is offscreen-only before surface decoration.",
        )
    }

    /**
     * Materializes one direct CorePrimitive scope whose pass seal admits N distinct structural
     * pipelines (Graphite `DrawPass.fFullPipelines` + `BindGraphicsPipeline(index)`): every key
     * acquires its own session-cache pipeline, the shared uniform32 slab keeps one pooled bind
     * group, and the render commands bind each key's pipeline only when the active key changes.
     * Single-sample frames that also carry the unified route seal delegate to the frame-global
     * run materializer, which owns the shared-slab partition across mixed prepared-surface runs.
     */
    private fun materializeDirectMultiKeyCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
        renderStep: GPUFrameStep.RenderPassStep,
        renderScope: GPUCommandEncoderScopePlan,
        sealedRoutes: GPUCorePrimitiveDirectNativeRouteSeal.Routes,
        semanticPackets: List<Triple<GPUFrameStep.RenderPassStep, GPUDrawPacket, GPUDrawSemanticPayload.CorePrimitive>>,
        multiKeySeal: GPUCorePrimitiveMultiKeyDirectPreparedPassSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val sampleCount = renderStep.samplePlan.sampleCount
        val isMsaa4x = renderStep.samplePlan == GPUSamplePlan.MultisampleFrame(4)
        val packetStructuralKeys = semanticPackets.map { (_, packet, _) ->
            requireNotNull(packet.corePrimitivePreparedAuthority).structuralPipelineKey
        }
        val multiKeyAuthority = when (
            val authority = validateMultiKeyDirectPassSealAuthority(
                multiKeySeal,
                packetStructuralKeys,
                generationSeal.deviceGeneration.value,
                limits,
            )
        ) {
            is GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Accepted -> authority
            is GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Refused ->
                return refused(authority.code, authority.message)
        }
        val analyticShapeMultiKey = multiKeySeal.analyticShapeUniformSeals.isNotEmpty()
        if (analyticShapeMultiKey) {
            // Mixed fixed-function blends on the analytic-shape lane are admitted by the
            // multi-key pass seal, but their AA coverage semantics are not yet verified against
            // the CPU oracle on the prepared lane (the analytic shape shader modulates the source
            // by coverage, which cannot express every blend, e.g. CLEAR). Those frames continue
            // on the legacy route.
            return refused(
                "unsupported.native-core-primitive.analytic-shape-multi-key",
                "Multi-key analytic-shape CorePrimitive passes remain on the legacy route until " +
                    "their AA coverage semantics are verified on the prepared lane.",
            )
        }
        // The analytic-shape multi-key variant is refused above, so every multi-key pass that
        // reaches this point is uniform32: the shared dynamic-uniform32 layout and 32-byte block
        // are the only remaining authorities.
        val multiKeyBindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH
        val multiKeyUniformBytes = CORE_PRIMITIVE_UNIFORM_BYTES
        if (multiKeySeal.uniformSlabSeal?.commandIds != semanticPackets.map { it.second.commandIdValue } ||
            semanticPackets.withIndex().any { (packetIndex, entry) ->
                val packet = entry.second
                val authority = packet.corePrimitivePreparedAuthority
                authority?.uniformSlabSeal !== multiKeySeal.uniformSlabSeal ||
                    authority?.analyticShapeUniformSeal != null ||
                    authority?.analyticClipUniformSeal != null ||
                    authority?.analyticIntersectionUniformSeal != null ||
                    packet.bindingLayoutHash != multiKeyBindingLayoutHash
            }
        ) {
            return refused(
                "invalid.native-core-primitive.multi-key-uniform-seal",
                "Multi-key CorePrimitive packets must share one exact packet-order dynamic uniform32 slab authority.",
            )
        }
        val targetBounds = semanticPackets.first().third.targetBounds
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(renderStep.target) ?: return refused(
            "unsupported.native-core-primitive.target-contract",
            "CorePrimitive requires one exact supported scene target.",
        )
        val declaredTargetFormat = declaredTargetDescriptor.format
        val declaredTargetInterpretation =
            declaredTargetFormat.corePrimitiveInterpretationOrNull() ?: return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        val declaredStructuralColorFormat = declaredTargetFormat.corePrimitiveStructuralColorFormat()
        if (multiKeySeal.structuralPipelineKeys.any { key ->
                key.colorFormat != declaredStructuralColorFormat
            }
        ) {
            return refused(
                "invalid.native-core-primitive.target-contract",
                "CorePrimitive target format contradicts its multi-key structural pipeline authority.",
            )
        }
        val keyIndexByStructuralKey = multiKeySeal.structuralPipelineKeys
            .withIndex()
            .associate { (index, key) -> key to index }
        val acceptedGeometries = semanticPackets.mapIndexed { packetIndex, (_, packet, semantic) ->
            val packetAuthority = packet.corePrimitivePreparedAuthority
            val expectedKey = packetAuthority?.structuralPipelineKey
            val expectedKeyIndex = keyIndexByStructuralKey[expectedKey]
            val exactUniformAuthority =
                packetAuthority?.uniformSlabSeal === multiKeySeal.uniformSlabSeal &&
                    packetAuthority.analyticShapeUniformSeal == null &&
                    packetAuthority.analyticClipUniformSeal == null &&
                    packetAuthority.analyticIntersectionUniformSeal == null
            val exactUniformPayload =
                semantic.payloadRef.uniformBlock?.byteSize == CORE_PRIMITIVE_UNIFORM_BYTES.toLong() &&
                    semantic.payloadRef.uniformBlock.bytes ==
                    corePrimitiveUniformBytes(semantic.targetBounds, semantic.premultipliedRgba)
            if (!semantic.hasStructuralIntegrity() || packet.role != GPUDrawPacketRole.Shading ||
                packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.bindingLayoutHash != multiKeyBindingLayoutHash ||
                packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                packet.targetStateHash != corePrimitiveTargetStateHash(sampleCount, declaredTargetFormat) ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(semantic.scissorBounds) ||
                packetAuthority == null ||
                expectedKeyIndex == null ||
                packetAuthority.renderPipelineKey != packet.renderPipelineKey ||
                !exactUniformAuthority ||
                semantic.targetBounds != targetBounds ||
                semantic.payloadRef.uniformBlock?.byteSize != multiKeyUniformBytes.toLong() ||
                !exactUniformPayload
            ) {
                return refused(
                    "invalid.native-core-primitive.packet-authority",
                    "A multi-key CorePrimitive packet contradicts its immutable semantic, pipeline, uniform, or target authority.",
                )
            }
            sealedRoutes.routesByPacketId.getValue(packet.packetId)
        }
        val renderScissors = semanticPackets.map { it.third.scissorBounds }
        val arena = try {
            packCorePrimitiveFrameGeometry(acceptedGeometries)
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.geometry-arena",
                "Direct CorePrimitive geometry cannot be packed safely: ${failure::class.simpleName.orEmpty()}.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        val geometrySlicesValid: Boolean
        try {
            vertexBytes = Math.multiplyExact(arena.vertices.size.toLong(), Float.SIZE_BYTES.toLong())
            indexBytes = Math.multiplyExact(arena.indices.size.toLong(), Int.SIZE_BYTES.toLong())
            val totalVertexCount = arena.vertices.size / 2
            var expectedFirstIndex = 0
            var expectedBaseVertex = 0
            geometrySlicesValid = arena.vertices.size % 2 == 0 && arena.slices.all { slice ->
                val nextFirstIndex = Math.addExact(slice.firstIndex, slice.indexCount)
                val nextBaseVertex = Math.addExact(slice.baseVertex, slice.vertexCount)
                val maximumAddressedVertex = Math.addExact(slice.baseVertex, slice.maxLocalIndex)
                val valid = slice.firstIndex == expectedFirstIndex &&
                    slice.baseVertex == expectedBaseVertex &&
                    slice.indexCount > 0 && slice.vertexCount > 0 &&
                    slice.maxLocalIndex in 0 until slice.vertexCount &&
                    nextFirstIndex <= arena.indices.size && nextBaseVertex <= totalVertexCount &&
                    maximumAddressedVertex < totalVertexCount
                expectedFirstIndex = nextFirstIndex
                expectedBaseVertex = nextBaseVertex
                valid
            } && expectedFirstIndex == arena.indices.size && expectedBaseVertex == totalVertexCount
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices overflow their exact shared-slab convention.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L || vertexBytes % 8L != 0L || indexBytes % 4L != 0L ||
            !geometrySlicesValid
        ) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices violate the exact shared-slab offset convention.",
            )
        }

        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "Direct CorePrimitive accepts only render scopes and one optional readback scope.",
            )
        }
        if (readbackStep != null && readbackStep.request.sourceBounds != targetBounds) {
            return refused(
                "unsupported.native-core-primitive.readback-layout",
                "CorePrimitive readback must cover the exact canonical target bounds.",
            )
        }
        // Structurally unreachable for destination reading: an all-dst-read multi-key pass has
        // identical keys (one mode, one component) and therefore seals as a single key, while
        // mixed fixed+dst-read or multi-mode dst-read keys are refused by the multi-key-component
        // authority. The branch stays for defensive consistency with the frame-global and
        // single-key dst-copy paths.
        val copyAuthority = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = listOf(renderStep),
                targetBounds = targetBounds,
                targetFormat = declaredTargetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities.singleOrNull()
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        }
        val expectedEncoderSteps = 1 +
            (if (copyAuthority == null) 0 else 1) +
            (if (readbackStep == null) 0 else 1)
        if (framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } != expectedEncoderSteps) {
            return refused(
                "unsupported.native-core-primitive.encoder-shape",
                "Direct CorePrimitive contains an unsupported encoder operation.",
            )
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The direct CorePrimitive readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(copyAuthority?.copyScope, renderScope, readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "CorePrimitive encoder scopes must preserve copy, render, then optional readback order.",
            )
        }
        val exactSampleAuthority = when (renderStep.samplePlan) {
            GPUSamplePlan.SingleSampleFrame -> renderStep.sampleContinuation == null
            is GPUSamplePlan.MultisampleFrame -> renderStep.samplePlan.sampleCount == 4 &&
                renderStep.sampleContinuation?.let { continuation ->
                    continuation.key.target.value == renderStep.target.value &&
                        continuation.key.targetGeneration == generationSeal.targetGeneration &&
                        continuation.key.deviceGeneration == generationSeal.deviceGeneration &&
                        continuation.key.colorFormat == declaredTargetFormat &&
                        continuation.key.colorInterpretation == declaredTargetInterpretation &&
                        continuation.key.samplePlan == renderStep.samplePlan &&
                        continuation.key.attachmentAuthority ==
                        org.graphiks.kanvas.gpu.renderer.passes
                            .GPUSampleAttachmentAuthority.PreparedFramePayload &&
                        continuation.key.colorAttachment.value ==
                        "msaa-color:${renderStep.target.value}:${generationSeal.targetGeneration}" &&
                        continuation.key.depthStencilAttachment == null &&
                        continuation.loadTransition == GPUSampleLoadTransition.FreshClear &&
                        continuation.storeAction == GPUSampleStoreAction.Store &&
                        continuation.resolveAction == GPUSampleResolveAction.ResolveCanonical
                } == true
            is GPUSamplePlan.LocalResolveApproximation -> false
        }
        if (!exactSampleAuthority ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null || renderStep.depthStencilLoadStore != null ||
            renderStep.resourceUses.any {
                it.role == GPUFrameResourceRole.PathDepthStencil ||
                    it.role == GPUFrameResourceRole.ClipDepthStencil
            } || semanticPackets.any { (_, _, semantic) ->
                semantic.scissorBounds.isEmpty ||
                semantic.scissorBounds.left < targetBounds.left || semantic.scissorBounds.top < targetBounds.top ||
                semantic.scissorBounds.right > targetBounds.right || semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.render-state",
                "CorePrimitive requires one exact clear/store 1x or color-only 4x resolve pass and contained scissors.",
            )
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
            ?: return refused("unsupported.native-core-primitive.target", "CorePrimitive target declaration is missing.")
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
            ?: return refused("unsupported.native-core-primitive.vertex", "CorePrimitive vertex slab declaration is missing.")
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
            ?: return refused("unsupported.native-core-primitive.index", "CorePrimitive index slab declaration is missing.")
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
            ?: return refused("unsupported.native-core-primitive.uniform", "CorePrimitive uniform slab declaration is missing.")
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        val expectedPreparationCount = 4 +
            (if (copyAuthority == null) 0 else 1) +
            (if (readbackStep == null) 0 else 1)
        if (preparations.size != expectedPreparationCount ||
            (readbackStep == null) != (stagingPreparation == null)
        ) {
            return refused(
                "unsupported.native-core-primitive.resource-shape",
                "CorePrimitive requires exactly target, shared vertex/index/uniform slabs, and optional readback staging.",
            )
        }
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        if (targetPreparation.resource != renderStep.target ||
            targetDescriptor == null ||
            targetDescriptor != declaredTargetDescriptor ||
            targetDescriptor.logicalBounds != targetBounds ||
            !targetDescriptor.format.isCorePrimitiveSceneTargetFormat() ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact frame-local supported scene target.",
            )
        }
        if (isMsaa4x) {
            val expectedMsaaBytes = try {
                Math.multiplyExact(
                    Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                    Math.multiplyExact(RGBA_BYTES_PER_PIXEL, sampleCount.toLong()),
                )
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.native-core-primitive.msaa-budget",
                    "The 4x CorePrimitive color attachment byte size overflowed.",
                )
            }
            if (framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaColor] !=
                expectedMsaaBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.msaa-budget",
                    "The 4x CorePrimitive color attachment must have exact aggregate memory authority.",
                )
            }
        }
        fun exactGeometryBuffer(
            preparation: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            bytes: Long,
        ): Boolean {
            val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor ?: return false
            return preparation.role == role && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                preparation.byteSize == bytes &&
                preparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactGeometryBuffer(vertexPreparation, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, vertexBytes) ||
            !exactGeometryBuffer(indexPreparation, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, indexBytes) ||
            setOf(vertexPreparation.resource, indexPreparation.resource, uniformPreparation.resource).size != 3
        ) {
            return refused(
                "unsupported.native-core-primitive.buffer-contract",
                "CorePrimitive shared Float32x2 vertex and Uint32 index slabs are not exact.",
            )
        }
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform slab requires one exact buffer descriptor.",
            )
        val uniformSlabPlan = multiKeySeal.uniformPlan
        if (uniformSlabPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformSlabPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment
        ) {
            return refused(
                "invalid.native-core-primitive.uniform-seal-generation",
                "CorePrimitive builder uniform authority is stale for the materialized device generation.",
            )
        }
        if (uniformDescriptor.byteSize != uniformSlabPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSlabPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformSlabPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform preparation differs from the sealed aligned slab plan.",
            )
        }
        if (uniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
            return refused(
                "unsupported.native-core-primitive.uniform-slab-host-size",
                "CorePrimitive uniform slab exceeds the host-addressable ByteArray size.",
            )
        }
        val exactUses = setOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                vertexPreparation.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                indexPreparation.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                uniformPreparation.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        )
        if (renderStep.resourceUses.filter {
                it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData ||
                    it.role == GPUFrameResourceRole.UniformData
            }.toSet() != exactUses
        ) {
            return refused(
                "invalid.native-core-primitive.render-resource-uses",
                "Every direct CorePrimitive draw must read the exact shared vertex and index slabs.",
            )
        }

        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        val expectedOrdinaryResources = 4 + (if (copyAuthority == null) 0 else 1)
        if (resources.ordinaryResources.size != expectedOrdinaryResources ||
            listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any { preparation ->
                val evidence = preparedByLogical[preparation.resource]
                val expectedKind = if (preparation.role == GPUFrameResourceRole.SceneTarget) {
                    GPUPreparedConcreteResourceRef.Texture::class.java
                } else {
                    GPUPreparedConcreteResourceRef.Buffer::class.java
                }
                evidence == null || evidence.role != preparation.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[preparation.resource] ||
                    !expectedKind.isInstance(evidence.concreteResource)
            } || listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any {
                generationSeal.resourceGenerations[it.resource] == null
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-resources",
                "CorePrimitive prepared target and geometry evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed CorePrimitive target.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional CorePrimitive readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != targetPreparation.resource || readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned CorePrimitive RGBA8 readback layout is not exact.",
                )
            }
        }

        // Single-sample frames that also retain the unified route seal delegate to the
        // frame-global run materializer, which owns the shared-slab partition across runs.
        val unifiedRoute = renderScope.corePrimitiveNativeScopeRouteSeal as?
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
        if (!isMsaa4x && unifiedRoute != null) {
            return materializeSingleSampleFrameGlobalCore(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                resources = resources,
                generationSeal = generationSeal,
                renderStep = renderStep,
                renderScope = renderScope,
                route = unifiedRoute,
                readbackScope = readbackScope,
                output = output,
                targetFormat = declaredTargetFormat,
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during multi-key validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val msaaColorRequirement = if (isMsaa4x) {
                GPUWgpu4kCorePrimitiveMsaaColorRequirement(
                    target = renderStep.target,
                    colorAttachment = requireNotNull(renderStep.sampleContinuation).key.colorAttachment,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    width = targetBounds.width,
                    height = targetBounds.height,
                    format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                )
            } else {
                null
            }
            val acquiredByCacheKey = linkedMapOf<
                GPUWgpu4kCorePrimitivePipelineCacheKey,
                GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired,
                >()
            multiKeyAuthority.cacheKeys.forEach { cacheKey ->
                when (val acquired = sessionCache.acquire(cacheKey)) {
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired ->
                        acquiredByCacheKey[cacheKey] = acquired
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                        synchronized(this) { materializing = false }
                        return refusedSessionCacheAcquire(acquired.reason)
                    }
                }
            }
            val dstReadSnapshot = copyAuthority?.let { authority ->
                createCorePrimitiveDestinationSnapshot(
                    authority,
                    declaredTargetFormat,
                    targetBounds,
                )
            }
            frameLease = when (
                val checkout = sessionCache.acquireFrame(
                    GPUWgpu4kCorePrimitiveFramePoolRequirements(
                        generationSeal.deviceGeneration,
                        vertexBytes,
                        indexBytes,
                        uniformSlabPlan.totalBytes,
                        componentIdentity = multiKeyAuthority.componentIdentity,
                        sampleCount = sampleCount,
                        msaaColor = msaaColorRequirement,
                        dstRead = dstReadSnapshot?.binding,
                    ),
                )
            ) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val vertexBuffer = pooled.handles.vertexBuffer
            val indexBuffer = pooled.handles.indexBuffer
            val uniformBuffer = pooled.handles.uniformBuffer
            val bindGroup = pooled.handles.bindGroup
            check(pooled.handles.sampleCount == sampleCount &&
                pooled.handles.msaaColor?.requirement == msaaColorRequirement &&
                (isMsaa4x == (pooled.handles.msaaColor != null))
            ) { "Pooled CorePrimitive color attachment differs from its exact sample authority" }
            uploadExact(
                vertexBuffer,
                ArrayBuffer.of(arena.vertices),
                usedBytes = vertexBytes,
                capacityBytes = pooled.capacities.vertexBytes,
            )
            uploadExact(
                indexBuffer,
                ArrayBuffer.of(arena.indices),
                usedBytes = indexBytes,
                capacityBytes = pooled.capacities.indexBytes,
            )
            uploadExact(
                uniformBuffer,
                ArrayBuffer.of(multiKeySeal.packedUniformBytesForUpload()),
                usedBytes = uniformSlabPlan.totalBytes,
                capacityBytes = pooled.capacities.uniformBytes,
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.multiKey.readback",
                    ),
                ).tracked()
            }
            val canonicalTargetViewOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val colorTargetViewOperand = pooled.handles.msaaColor?.let { msaa ->
                GPUPreparedNativeTextureViewOperand(
                    msaa.view,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            } ?: canonicalTargetViewOperand
            val pipelineOperandByCacheKey = acquiredByCacheKey.mapValues { (_, acquired) ->
                GPUPreparedNativeRenderPipelineOperand(
                    acquired.pipeline,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            }
            val vertexOperand = GPUPreparedNativeBufferOperand(
                vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.indexBytes,
            )
            val sharedBindGroupOperand = GPUPreparedNativeBindGroupOperand(
                bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val renderCommands = buildList {
                var lastKeyIndex = keyIndexByStructuralKey.getValue(
                    requireNotNull(semanticPackets.first().second.corePrimitivePreparedAuthority)
                        .structuralPipelineKey,
                )
                var lastPipeline = pipelineOperandByCacheKey.getValue(
                    multiKeyAuthority.cacheKeys[lastKeyIndex],
                )
                add(GPUPreparedNativeRenderCommand.SetPipeline(lastPipeline))
                add(
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        offset = 0L,
                        size = vertexBytes,
                        vertexStrideBytes = 8L,
                    ),
                )
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        offset = 0L,
                        size = indexBytes,
                    ),
                )
                semanticPackets.indices.forEach { index ->
                    val (_, packet, _) = semanticPackets[index]
                    val slice = arena.slices[index]
                    val uniformSlot = uniformSlabPlan.slots[index]
                    val renderScissor = renderScissors[index]
                    val keyIndex = keyIndexByStructuralKey.getValue(
                        requireNotNull(packet.corePrimitivePreparedAuthority)
                            .structuralPipelineKey,
                    )
                    if (keyIndex != lastKeyIndex) {
                        val pipeline = pipelineOperandByCacheKey.getValue(
                            multiKeyAuthority.cacheKeys[keyIndex],
                        )
                        add(GPUPreparedNativeRenderCommand.SetPipeline(pipeline))
                        lastKeyIndex = keyIndex
                        lastPipeline = pipeline
                    }
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            sharedBindGroupOperand,
                            dynamicOffsets = listOf(uniformSlot.alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            renderScissor.left,
                            renderScissor.top,
                            renderScissor.width,
                            renderScissor.height,
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = slice.indexCount,
                                firstIndex = slice.firstIndex,
                                baseVertex = slice.baseVertex,
                                vertexCount = slice.vertexCount,
                                maxLocalIndex = slice.maxLocalIndex,
                            ),
                        ),
                    )
                }
                check(lastKeyIndex >= 0 && lastPipeline !== null)
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = colorTargetViewOperand,
                    resolveTarget = canonicalTargetViewOperand.takeIf { isMsaa4x },
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                ),
                commands = renderCommands,
                semanticPayloads = semanticPackets.map { it.third },
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
                        format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else {
                null
            }
            val copyOperand = copyAuthority?.let { authority ->
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = authority.copyScope.sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        requireNotNull(dstReadSnapshot).texture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = targetBounds.left,
                        sourceOriginY = targetBounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = targetBounds.width,
                        height = targetBounds.height,
                    ),
                )
            }
            val operandsByStep = (listOfNotNull(copyOperand, renderOperand) + listOfNotNull(readbackOperand))
                .associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
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
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = dstReadSnapshot?.payloadOwnedAuxiliaryHandles().orEmpty(),
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during multi-key materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.multi-key-materialization",
                "Public wgpu4k multi-key CorePrimitive materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun materializePreparedCoverageMaskCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        data class RenderEntry(
            val scope: GPUCommandEncoderScopePlan,
            val render: GPUFrameStep.RenderPassStep,
            val seal: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal,
        )

        fun invalid(suffix: String, message: String) = refused(
            "invalid.native-core-primitive.coverage-mask.$suffix",
            message,
        )

        if (encoderPlan.deviceGeneration != generationSeal.deviceGeneration ||
            encoderPlan.targetGeneration != generationSeal.targetGeneration ||
            generationSeal.capabilitySealHash != framePlan.capabilitySeal.sealHash
        ) return invalid(
            "generation",
            "Prepared coverage-mask device, target, or capability generation was substituted.",
        )

        val renderEntries = encoderPlan.scopes.filter {
            it.operationKind == GPUEncoderOperationKind.Render
        }.map { scope ->
            val render = framePlan.steps.getOrNull(scope.sourceStepIndex) as?
                GPUFrameStep.RenderPassStep ?: return invalid(
                "scope",
                "Every coverage-mask render scope must retain its exact frame step.",
            )
            RenderEntry(scope, render, scope.corePrimitiveCoverageMaskPreparedRouteSeal)
        }
        val producerScopeEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer ||
                it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ProducerPartition
        }
        val consumerScopeEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer ||
                it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.ConsumerPartition
        }
        if (producerScopeEntries.isEmpty() || consumerScopeEntries.isEmpty() ||
            renderEntries.size != producerScopeEntries.size + consumerScopeEntries.size ||
            renderEntries.take(producerScopeEntries.size) != producerScopeEntries ||
            renderEntries.drop(producerScopeEntries.size) != consumerScopeEntries
        ) return invalid(
            "scope",
            "Coverage-mask requires only sealed producers followed by only sealed consumers.",
        )
        val producers = producerScopeEntries.flatMap { entry -> entry.seal.units().map { unit ->
            entry to (unit as? GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
                ?: return invalid("scope", "Coverage-mask producer scope contains a non-producer unit."))
        } }
        val consumers = consumerScopeEntries.flatMap { entry -> entry.seal.units().map { unit ->
            entry to (unit as? GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
                ?: return invalid("scope", "Coverage-mask consumer scope contains a non-consumer unit."))
        } }
        val producerIndicesByScopeEntry =
            IdentityHashMap<RenderEntry, MutableList<Int>>()
        producers.forEachIndexed { index, (entry, _) ->
            producerIndicesByScopeEntry.getOrPut(entry) { mutableListOf() } += index
        }
        val consumerUnitsByScopeEntry = IdentityHashMap<
            RenderEntry,
            MutableList<Pair<Int, GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer>>,
            >()
        consumers.forEachIndexed { index, (entry, consumer) ->
            consumerUnitsByScopeEntry.getOrPut(entry) { mutableListOf() } += index to consumer
        }
        fun exactScopePackets(entry: RenderEntry): List<GPUDrawPacketID> = entry.seal.units().map { unit ->
            when (unit) {
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.packetId
                is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.packetId
                else -> error("Coverage-mask scope contains a non-unit seal")
            }
        }
        if ((producerScopeEntries + consumerScopeEntries).any { entry ->
                val packetIds = exactScopePackets(entry)
                entry.scope.sourcePacketIds != packetIds ||
                    entry.render.drawPackets.map(GPUDrawPacket::packetId) != packetIds
            }
        ) return invalid(
            "scope",
            "Coverage-mask scope packet identities differ from its retained ordered partition.",
        )
        val packetsBySourceStepIndex = linkedMapOf<Int, Map<GPUDrawPacketID, GPUDrawPacket>>()
        renderEntries.forEach { entry ->
            val packetsById = entry.render.drawPackets.associateBy(GPUDrawPacket::packetId)
            if (packetsById.size != entry.render.drawPackets.size ||
                packetsBySourceStepIndex.put(entry.scope.sourceStepIndex, packetsById) != null
            ) return invalid(
                "scope",
                "Coverage-mask scopes require unique source steps and packet identities.",
            )
        }
        val route = producers.first().second.route
        val slab = producers.first().second.slabAuthority
        val attachment = producers.first().second.attachmentAuthority
        val orderedPackets = (producers.map { (entry, seal) ->
            packetsBySourceStepIndex[entry.scope.sourceStepIndex]?.get(seal.packetId)
                ?: return invalid(
                    "authority",
                    "Coverage-mask producer packet authority is missing from its sealed scope.",
                )
        } + consumers.map { (entry, seal) ->
            packetsBySourceStepIndex[entry.scope.sourceStepIndex]?.get(seal.packetId)
                ?: return invalid(
                    "authority",
                    "Coverage-mask consumer packet authority is missing from its sealed scope.",
                )
        })
        when (
            val validation = validateGPUCorePrimitiveCoverageMaskPreparedAuthority(
                orderedPackets,
                slab.uniformSlabSeal,
            )
        ) {
            is GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Accepted ->
                if (validation.route !== route) {
                    return invalid(
                        "authority",
                        "Coverage-mask live authority differs from its retained passive route.",
                    )
                }
            is GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Refused ->
                return invalid("authority", validation.message)
        }
        val sceneTarget = consumers.first().second.sceneTarget
        val sceneTargetGeneration = consumers.first().second.sceneTargetGeneration
        val sceneTargetFormat =
            framePlan.corePrimitiveSceneTargetDescriptor(sceneTarget)?.format ?: return invalid(
                "resource-contract",
                "Coverage-mask requires one exact supported scene target.",
            )
        val sceneStructuralColorFormat = try {
            sceneTargetFormat.corePrimitiveStructuralColorFormat()
        } catch (_: IllegalArgumentException) {
            return invalid(
                "resource-contract",
                "Coverage-mask requires one exact supported scene target.",
            )
        }
        if (producers.size != route.producers.size || consumers.size != route.consumers.size ||
            producers.any { (entry, seal) ->
                seal.sourceStepIndex != entry.scope.sourceStepIndex ||
                    seal.packetId !in entry.scope.sourcePacketIds || seal.route !== route ||
                    seal.slabAuthority !== slab || seal.attachmentAuthority !== attachment
            } || consumers.any { (entry, seal) ->
                seal.sourceStepIndex != entry.scope.sourceStepIndex ||
                    seal.packetId !in entry.scope.sourcePacketIds || seal.route !== route ||
                    seal.slabAuthority !== slab || seal.attachmentAuthority !== attachment ||
                    seal.sceneTarget != sceneTarget ||
                    seal.sceneTargetGeneration != sceneTargetGeneration
            } || producers.map { it.second.sourceOrder } != route.producers.map { it.sourceOrder } ||
            consumers.map { it.second.packetId } != route.consumers.map { it.packetId } ||
            consumers.map { it.second.commandId } != route.consumers.map { it.commandId } ||
            consumers.map { it.second.sourceOrder } != route.consumers.map { it.sourceOrder } ||
            consumers.map { it.second.dependencyFromPreviousConsumerToken } !=
            slab.uniformSlabSeal.consumerSlots.map { it.dependencyFromPreviousConsumerToken } ||
            consumers.dropLast(1).any { it.second.isLastConsumer } ||
            !consumers.last().second.isLastConsumer ||
            route.producers.any {
                it.structuralKey.colorFormat !=
                    GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm
            } ||
            route.consumers.any { it.structuralKey.colorFormat != sceneStructuralColorFormat }
        ) return invalid(
            "seal",
            "Coverage-mask scope order, identity, dependency, or retained frame authority was substituted.",
        )

        val consumerSemantics = route.consumers.map { consumer ->
            consumer.semanticAuthority.retainedSemantic()
        }
        try {
            renderEntries.forEach { entry ->
                entry.seal.requireExactCoverageMaskPassCommandAuthority(
                    requireNotNull(entry.scope.passCommandStream),
                )
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = entry.scope.sourceStepIndex,
                    operationKind = entry.scope.operationKind,
                    scopeLabel = entry.scope.scopeLabel,
                    sourceTaskIds = entry.scope.sourceTaskIds,
                    sourcePacketIds = entry.scope.sourcePacketIds,
                    mixedCorePrimitiveAndImage = entry.scope.mixedCorePrimitiveAndImage,
                    facadeOperationClasses = entry.scope.facadeOperationClasses,
                    targetGeneration = entry.scope.targetGeneration,
                    resourceGenerationLabels = entry.scope.resourceGenerationLabels,
                    passCommandStream = entry.scope.passCommandStream,
                    corePrimitiveDirectNativeRouteSeal =
                        entry.scope.corePrimitiveDirectNativeRouteSeal,
                    corePrimitivePathStencilNativeRouteSeal =
                        entry.scope.corePrimitivePathStencilNativeRouteSeal,
                    corePrimitiveNativeScopeRouteSeal =
                        entry.scope.corePrimitiveNativeScopeRouteSeal,
                    corePrimitiveClipStencilPreparedRouteSeal =
                        entry.scope.corePrimitiveClipStencilPreparedRouteSeal,
                    corePrimitiveCoverageMaskPreparedRouteSeal =
                        entry.scope.corePrimitiveCoverageMaskPreparedRouteSeal,
                ).attachNativeOperandKeys(entry.scope.nativeOperandKeys)
            }
        } catch (_: IllegalArgumentException) {
            return invalid(
                "command-authority",
                "Coverage-mask command stream, bridge, provenance, generation labels, or native keys diverged.",
            )
        }

        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return invalid("frame-shape", "Coverage-mask accepts only its render chain and one optional readback.")
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return invalid("readback", "Coverage-mask lost its optional scene readback scope.")
        }
        if (encoderPlan.scopes != renderEntries.map(RenderEntry::scope) + listOfNotNull(readbackScope) ||
            framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } !=
            renderEntries.size + (if (readbackStep == null) 0 else 1)
        ) return invalid(
            "scope-order",
            "Coverage-mask scopes must remain producers, consumers, then optional readback.",
        )

        val preparationSteps = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        val retainedCoverageMaskRenderSteps = IdentityHashMap<GPUFrameStep, Unit>().apply {
            renderEntries.forEach { entry -> put(entry.render, Unit) }
        }
        if (preparationSteps.size != 1 || framePlan.steps.any { step ->
                step !is GPUFrameStep.PrepareResourcesStep &&
                    !retainedCoverageMaskRenderSteps.containsKey(step) && step !== readbackStep
            }
        ) return invalid(
            "frame-shape",
            "Coverage-mask requires one preparation step and no foreign frame step.",
        )
        val preparations = preparationSteps
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val scenePreparation = preparation(GPUFrameResourceRole.SceneTarget)
        val maskPreparation = preparation(GPUFrameResourceRole.ClipMask)
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        if (preparations.size != 5 + (if (readbackStep == null) 0 else 1) ||
            scenePreparation == null || maskPreparation == null || vertexPreparation == null ||
            indexPreparation == null || uniformPreparation == null ||
            (readbackStep == null) != (stagingPreparation == null) ||
            preparations.map { it.resource }.toSet().size != preparations.size ||
            generationSeal.resourceGenerations.keys != preparations.map { it.resource }.toSet()
        ) return invalid(
            "resource-shape",
            "Coverage-mask requires exactly distinct scene/mask/V/I/U resources and optional staging.",
        )
        val sceneDescriptor = scenePreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return invalid("resource-contract", "Coverage-mask scene authority is not a texture.")
        val maskDescriptor = maskPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return invalid("resource-contract", "Coverage-mask attachment authority is not a texture.")
        val vertexDescriptor = vertexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return invalid("resource-contract", "Coverage-mask vertex authority is not a buffer.")
        val indexDescriptor = indexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return invalid("resource-contract", "Coverage-mask index authority is not a buffer.")
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return invalid("resource-contract", "Coverage-mask uniform authority is not a buffer.")
        val targetBounds = sceneDescriptor.logicalBounds
        val uniformSeal = slab.uniformSlabSeal
        val exactSceneBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                RGBA_BYTES_PER_PIXEL,
            )
        } catch (_: ArithmeticException) {
            return invalid("resource-contract", "Coverage-mask scene byte sizing overflowed.")
        }
        val exactMaskBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(route.attachment.width.toLong(), route.attachment.height.toLong()),
                RGBA_BYTES_PER_PIXEL,
            )
        } catch (_: ArithmeticException) {
            return invalid("resource-contract", "Coverage-mask attachment byte sizing overflowed.")
        }
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            targetBounds.width != preparedSceneTarget.width ||
            targetBounds.height != preparedSceneTarget.height ||
            scenePreparation.resource != sceneTarget || sceneDescriptor.format != sceneTargetFormat ||
            !sceneDescriptor.format.isCorePrimitiveSceneTargetFormat() ||
            sceneDescriptor.sampleCount != 1 || scenePreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || scenePreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            scenePreparation.byteSize != exactSceneBytes ||
            maskPreparation.resource != attachment.resource ||
            maskDescriptor.logicalBounds != route.bounds ||
            maskDescriptor.format.value != COVERAGE_MASK_RGBA8_UNORM ||
            maskDescriptor.sampleCount != 1 || maskPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.TextureBinding,
            ) || maskPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            maskPreparation.byteSize != exactMaskBytes ||
            vertexPreparation.resource != slab.vertexResource ||
            indexPreparation.resource != slab.indexResource ||
            uniformPreparation.resource != slab.uniformResource ||
            generationSeal.resourceGenerations[sceneTarget] != sceneTargetGeneration ||
            generationSeal.resourceGenerations[attachment.resource] != attachment.resourceGeneration ||
            generationSeal.resourceGenerations[slab.vertexResource] != slab.vertexGeneration ||
            generationSeal.resourceGenerations[slab.indexResource] != slab.indexGeneration ||
            generationSeal.resourceGenerations[slab.uniformResource] != slab.uniformGeneration ||
            vertexDescriptor.byteSize != slab.vertexByteSize || vertexDescriptor.alignmentBytes != 4L ||
            vertexPreparation.byteSize != slab.vertexByteSize || vertexPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Vertex,
            ) || vertexPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            indexDescriptor.byteSize != slab.indexByteSize || indexDescriptor.alignmentBytes != 4L ||
            indexPreparation.byteSize != slab.indexByteSize || indexPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Index,
            ) || indexPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformDescriptor.byteSize != slab.uniformByteSize ||
            uniformDescriptor.alignmentBytes != slab.uniformAlignmentBytes ||
            uniformPreparation.byteSize != slab.uniformByteSize || uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformSeal.plan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformSeal.plan.sourceLabel != "core-primitive-coverage-mask-uniform-pass" ||
            uniformSeal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformSeal.plan.totalBytes != slab.uniformByteSize ||
            uniformSeal.plan.slots.size != producers.size + consumers.size ||
            uniformSeal.plan.slots.any { slot ->
                slot.payloadBytes != 64L ||
                    slot.alignedOffset % limits.minUniformBufferOffsetAlignment != 0L
            }
        ) return invalid(
            "resource-contract",
            "Coverage-mask target, attachment, slabs, usages, lifetimes, or generations were substituted.",
        )

        if (!uniformSeal.hasZeroPadding() ||
            producers.map { it.second.uniformSlice } + consumers.map { it.second.uniformSlice } !=
            uniformSeal.plan.slots.mapIndexed { index, slot ->
                GPUCorePrimitiveCoverageMaskPreparedUniformSlice(
                    slab.uniformResource,
                    slab.uniformGeneration,
                    index,
                    slot.alignedOffset,
                    slot.payloadBytes,
                    slot.allocatedBytes,
                )
            }
        ) return invalid("uniform-abi", "Coverage-mask ABI64 bytes, padding, or slices were substituted.")

        val packedGeometry = try {
            packCorePrimitiveFrameGeometry(route.consumers.map { consumer ->
                when (val geometry = consumer.geometry) {
                    is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.Rect ->
                        GPUCorePrimitiveDirectNativeRoute.Accepted(
                            floatArrayOf(
                                geometry.left, geometry.top, geometry.right, geometry.top,
                                geometry.right, geometry.bottom, geometry.left, geometry.bottom,
                            ),
                            intArrayOf(0, 2, 1, 0, 3, 2),
                        )
                    is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.DirectTriangles ->
                        GPUCorePrimitiveDirectNativeRoute.Accepted(
                            geometry.vertices.toFloatArray(),
                            geometry.indices.toIntArray(),
                        )
                }
            })
        } catch (_: IllegalArgumentException) {
            return invalid("geometry", "Coverage-mask consumer geometry cannot be packed exactly.")
        }
        if (packedGeometry.vertices.size.toLong() * Float.SIZE_BYTES != slab.vertexByteSize ||
            packedGeometry.indices.size.toLong() * Int.SIZE_BYTES != slab.indexByteSize ||
            packedGeometry.slices.zip(consumers).any { (packed, consumer) ->
                val sealed = consumer.second.geometrySlice
                packed.firstIndex != sealed.firstIndex || packed.indexCount != sealed.indexCount ||
                    packed.baseVertex != sealed.baseVertex || packed.vertexCount != sealed.vertexCount ||
                    consumer.second.draw != GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed(
                        packed.indexCount,
                        packed.firstIndex,
                        packed.baseVertex,
                    )
            } || packedGeometry.slices.size != consumers.size ||
            producers.any { it.second.draw != GPUCorePrimitiveCoverageMaskPreparedDraw.Draw(3) }
        ) return invalid("geometry", "Coverage-mask packed geometry slices or draws were substituted.")

        producerScopeEntries.forEachIndexed { index, entry ->
            val maskUse = entry.render.resourceUses.singleOrNull { use ->
                use.resource == attachment.resource && use.role == GPUFrameResourceRole.ClipMask &&
                    use.usage == GPUFrameResourceUsage.RenderAttachment && use.write &&
                    use.lifetime == GPUFrameResourceLifetime.FrameLocal
            }
            val uniformUse = entry.render.resourceUses.singleOrNull { use ->
                use.resource == slab.uniformResource && use.role == GPUFrameResourceRole.UniformData &&
                    use.usage == GPUFrameResourceUsage.Uniform && !use.write &&
                    use.lifetime == GPUFrameResourceLifetime.FrameLocal
            }
            if (entry.render.target != attachment.resource || entry.render.resourceUses.size != 2 ||
                maskUse == null || uniformUse == null || entry.render.depthStencilLoadStore != null ||
                entry.render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                entry.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                    if (index == 0) "clear" else "load",
                    GPUStorePlan.Store,
                    if (index == 0) {
                        org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL
                    } else null,
                )
            ) return invalid("render-state", "Coverage-mask producer resources or load/store were substituted.")
        }
        consumerScopeEntries.forEachIndexed { index, entry ->
            fun exactUse(
                role: GPUFrameResourceRole,
                usage: GPUFrameResourceUsage,
                resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef,
            ) = entry.render.resourceUses.singleOrNull { use ->
                use.resource == resource && use.role == role && use.usage == usage && !use.write &&
                    use.lifetime == GPUFrameResourceLifetime.FrameLocal
            }
            if (entry.render.target != sceneTarget || entry.render.resourceUses.size != 4 ||
                exactUse(GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, slab.vertexResource) == null ||
                exactUse(GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, slab.indexResource) == null ||
                exactUse(GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, slab.uniformResource) == null ||
                exactUse(GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, attachment.resource) == null ||
                entry.render.depthStencilLoadStore != null ||
                entry.render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                entry.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                    if (index == 0) "clear" else "load",
                    GPUStorePlan.Store,
                )
            ) return invalid("render-state", "Coverage-mask consumer resources or load/store were substituted.")
        }

        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        if (resources.ordinaryResources.size != 5 ||
            listOf(scenePreparation, maskPreparation, vertexPreparation, indexPreparation, uniformPreparation)
                .any { request ->
                    val evidence = preparedByLogical[request.resource]
                    val texture = request.role == GPUFrameResourceRole.SceneTarget ||
                        request.role == GPUFrameResourceRole.ClipMask
                    evidence == null || evidence.role != request.role ||
                        evidence.deviceGeneration != generationSeal.deviceGeneration ||
                        evidence.resourceGeneration != generationSeal.resourceGenerations[request.resource] ||
                        if (texture) evidence.concreteResource !is GPUPreparedConcreteResourceRef.Texture
                        else evidence.concreteResource !is GPUPreparedConcreteResourceRef.Buffer
                }
        ) return invalid("prepared-resources", "Coverage-mask concrete resource evidence is missing or extra.")
        if (preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration ||
            preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height
        ) return invalid("prepared-target", "Coverage-mask prepared scene target differs from its seal.")

        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return invalid("readback", "Coverage-mask optional readback must be scene-only and output-owned.")
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
                ?: return invalid("readback", "Coverage-mask readback staging is not a buffer.")
            val (exactUnpaddedBytesPerRow, exactPaddedBytesPerRow, exactTotalBufferBytes) = try {
                val unpadded = Math.multiplyExact(targetBounds.width.toLong(), RGBA_BYTES_PER_PIXEL)
                val padded = Math.multiplyExact(
                    Math.addExact(unpadded, WEBGPU_COPY_ROW_ALIGNMENT - 1L) /
                        WEBGPU_COPY_ROW_ALIGNMENT,
                    WEBGPU_COPY_ROW_ALIGNMENT,
                )
                Triple(
                    unpadded,
                    padded,
                    Math.addExact(
                        Math.multiplyExact(padded, (targetBounds.height - 1).toLong()),
                        unpadded,
                    ),
                )
            } catch (_: ArithmeticException) {
                return invalid("readback", "Coverage-mask padded readback byte sizing overflowed.")
            }
            val exactReadbackScope = readbackScope
                ?: return invalid("readback", "Coverage-mask scene readback scope is missing.")
            fun resourceLabel(
                resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef,
            ): String = "${resource::class.simpleName}:${resource.value}@${
                generationSeal.resourceGenerations.getValue(resource)
            }"
            val expectedResourceGenerationLabels = listOf(
                resourceLabel(sceneTarget),
                resourceLabel(stagingPreparation.resource),
            )
            val expectedReadbackOperandKeys = listOf(
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.ReadbackSource,
                    GPUPreparedNativeOperandKind.Texture,
                    gpuPreparedNativeBindingKey(expectedResourceGenerationLabels[0]),
                ),
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.ReadbackDestination,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey(expectedResourceGenerationLabels[1]),
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
            )
            if (readbackStep.source != sceneTarget || readbackStep.staging != stagingPreparation.resource ||
                readbackStep.request.sourceBounds != targetBounds || output.request != readbackStep.request ||
                output.stagingResource != stagingPreparation.resource ||
                stagingDescriptor.byteSize != output.layout.totalBufferBytes ||
                stagingDescriptor.alignmentBytes != 4L ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != exactUnpaddedBytesPerRow ||
                output.layout.paddedBytesPerRow != exactPaddedBytesPerRow ||
                output.layout.rowsPerImage != targetBounds.height || output.layout.bufferOffset != 0L ||
                output.layout.totalBufferBytes != exactTotalBufferBytes ||
                output.layout.totalBufferBytes != output.stagingLease.logicalMinimumBytes ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes ||
                output.stagingLease.backingBufferBytes != stagingDescriptor.byteSize ||
                output.stagingLease.resourceRef != output.concreteResource.ref ||
                output.stagingLease.deviceGeneration != generationSeal.deviceGeneration ||
                output.stagingLease.usages != stagingPreparation.usages ||
                exactReadbackScope.resourceGenerationLabels != expectedResourceGenerationLabels ||
                exactReadbackScope.nativeOperandKeys != expectedReadbackOperandKeys
            ) return invalid("readback", "Coverage-mask scene readback authority was substituted.")
        }

        val structuralKeys = route.consumers.map { it.structuralKey }
        val cacheKeys = linkedMapOf<GPUCorePrimitiveRenderPipelineStructuralKey, GPUWgpu4kCorePrimitivePipelineCacheKey>()
        structuralKeys.distinct().forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey) as?
                GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
                "unsupported.native-core-primitive.coverage-mask.pipeline",
                "Coverage-mask contains a structural pipeline outside the closed native programs.",
            )
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                mapped.componentIdentity,
                mapped.identity,
            )
        }
        if (cacheKeys.isEmpty() || cacheKeys.values.any { it.componentIdentity !=
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
            }
        ) return invalid("pipeline", "Coverage-mask consumer pipeline identity was substituted.")

        synchronized(this) {
            if (closed) return refused(
                "unsupported.native-core-primitive.materializer-state",
                "The CorePrimitive materializer closed during coverage-mask validation.",
            )
            materializing = true
        }
        var producerLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var producerLifecycleTransferred = false
        return try {
            val acquiredByStructural = linkedMapOf<
                GPUCorePrimitiveRenderPipelineStructuralKey,
                GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired
                >()
            cacheKeys.forEach { (structuralKey, cacheKey) ->
                val acquired = when (val result = sessionCache.acquire(cacheKey)) {
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> result
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                        synchronized(this) { materializing = false }
                        return refusedSessionCacheAcquire(result.reason)
                    }
                }
                acquiredByStructural[structuralKey] = acquired
            }
            val producerReady = when (
                val result = coverageMaskProducerMaterializer.materialize(
                    GPUWgpu4kCoverageMaskProducerRequest.borrowSealed(
                        uniformSlabSeal = uniformSeal.producerUniformSlabSeal,
                        scopes = producerScopeEntries.map { scopeEntry ->
                            GPUWgpu4kCoverageMaskProducerScope(
                                scopeEntry.scope.sourceStepIndex,
                                requireNotNull(producerIndicesByScopeEntry[scopeEntry]),
                            )
                        },
                        deviceGeneration = generationSeal.deviceGeneration,
                        resourceEnvelope = GPUWgpu4kCoverageMaskResourceEnvelope.borrowBuilderPacked(
                            vertexBytes = slab.vertexByteSize,
                            indexBytes = slab.indexByteSize,
                            uniformSlabSeal = uniformSeal.producerUniformSlabSeal,
                            coverageMaskConsumerBindGroupRequired = true,
                        ),
                    ),
                )
            ) {
                is GPUWgpu4kCoverageMaskProducerMaterialization.Ready -> result
                is GPUWgpu4kCoverageMaskProducerMaterialization.Refused -> {
                    synchronized(this) { materializing = false }
                    return refused(result.code, result.message)
                }
            }
            producerLifecycle = producerReady.leaseLifecycle
            val borrowed = producerReady.borrowedResources
            uploadExact(
                borrowed.vertexBuffer.buffer,
                ArrayBuffer.of(packedGeometry.vertices),
                slab.vertexByteSize,
                requireNotNull(borrowed.vertexBuffer.byteCapacity),
            )
            uploadExact(
                borrowed.indexBuffer.buffer,
                ArrayBuffer.of(packedGeometry.indices),
                slab.indexByteSize,
                requireNotNull(borrowed.indexBuffer.byteCapacity),
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.coverageMask.readback",
                    ),
                ).tracked()
            }
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val vertexOperand = borrowed.vertexBuffer
            val indexOperand = borrowed.indexBuffer
            val consumerBindGroup = requireNotNull(borrowed.consumerBindGroup)
            val pipelineOperands = acquiredByStructural.mapValues { (structural, acquired) ->
                GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                    acquired,
                    generationSeal.deviceGeneration,
                    limits.minUniformBufferOffsetAlignment,
                )
            }
            val producerOperands = producerReady.scopeOperands
            val consumerOperands = consumerScopeEntries.mapIndexed { scopeIndex, scopeEntry ->
                val scopedConsumers = requireNotNull(consumerUnitsByScopeEntry[scopeEntry])
                val commands = buildList {
                    scopedConsumers.forEachIndexed { unitIndex, (index, seal) ->
                        add(GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[route.consumers[index].structuralKey]),
                        ))
                        add(GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            consumerBindGroup,
                            listOf(seal.uniformSlice.alignedOffset),
                        ))
                        if (unitIndex == 0) {
                            add(GPUPreparedNativeRenderCommand.SetVertexBuffer(
                                0, vertexOperand, 0L, slab.vertexByteSize, 8L,
                            ))
                            add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                                indexOperand, GPUPreparedNativeIndexFormat.Uint32, 0L, slab.indexByteSize,
                            ))
                        }
                        val packed = packedGeometry.slices[index]
                        add(GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = packed.indexCount,
                                firstIndex = packed.firstIndex,
                                baseVertex = packed.baseVertex,
                                vertexCount = packed.vertexCount,
                                maxLocalIndex = packed.maxLocalIndex,
                            ),
                        ))
                    }
                }
                GPUPreparedNativeScopeOperand.Render(
                    scopeEntry.scope.sourceStepIndex,
                    GPUPreparedNativeRenderPassConfig(
                        colorTarget = targetOperand,
                        loadOperation = if (scopeIndex == 0) GPUPreparedNativeLoadOperation.Clear
                        else GPUPreparedNativeLoadOperation.Load,
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (scopeIndex == 0) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        else null,
                    ),
                    commands,
                    scopedConsumers.map { (index, _) -> consumerSemantics[index] },
                    GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitiveFullTarget,
                )
            }
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    readbackScope.sourceStepIndex,
                    GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    GPUPreparedNativeBufferOperand(
                        stagingBuffer,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                    ),
                    GPUPreparedNativeReadbackLayout(
                        output.request.sourceBounds.left,
                        output.request.sourceBounds.top,
                        output.layout.width,
                        output.layout.height,
                        output.layout.paddedBytesPerRow,
                        output.layout.rowsPerImage,
                        output.layout.bufferOffset,
                        output.layout.totalBufferBytes,
                        sceneTargetFormat.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else null
            val byStep = (producerOperands + consumerOperands + listOfNotNull(readbackOperand))
                .associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val payload = GPUPreparedNativeFramePayload(
                GPUPreparedNativeFrameIdentity(
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
                encoderPlan.scopes.map { scope -> requireNotNull(byStep[scope.sourceStepIndex]) },
                encoderPlan.scopes.map { it.nativeOperandKeys },
                leaseLifecycle = producerReady.leaseLifecycle,
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during coverage-mask materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                producerLifecycleTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!producerLifecycleTransferred) {
                producerLifecycle?.let { lifecycle ->
                    if (lifecycle.releaseBeforeSubmit() !is GPUPreparedNativeFrameLeaseTransition.Applied) {
                        lifecycle.quarantineUncertain()
                    }
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.coverage-mask-materialization",
                "Public wgpu4k prepared coverage-mask materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun materializePreparedClipStencilCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        data class RenderEntry(
            val scope: GPUCommandEncoderScopePlan,
            val render: GPUFrameStep.RenderPassStep,
            val seal: GPUCorePrimitiveClipStencilPreparedScopeRouteSeal,
        )

        val allRenderEntries = encoderPlan.scopes.filter {
            it.operationKind == GPUEncoderOperationKind.Render
        }.map { scope ->
            val render = framePlan.steps.getOrNull(scope.sourceStepIndex) as?
                GPUFrameStep.RenderPassStep ?: return refused(
                "invalid.native-core-primitive.clip-stencil-scope",
                "Every prepared clip-stencil render scope must retain its exact frame step.",
            )
            RenderEntry(scope, render, scope.corePrimitiveClipStencilPreparedRouteSeal)
        }
        val prefixEntries = allRenderEntries.filter { entry ->
            entry.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty &&
                entry.scope.allowsClipStencilPrefixDepthStencil
        }
        val renderEntries = allRenderEntries.filter { entry ->
            entry.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer ||
                entry.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
        }
        val producerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
        }
        val consumerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
        }
        if (allRenderEntries.size != prefixEntries.size + renderEntries.size ||
            producerEntries.size != 1 || consumerEntries.isEmpty() ||
            renderEntries.size != 1 + consumerEntries.size ||
            prefixEntries.size > 1
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-scope",
                "Prepared clip-stencil requires one sealed producer followed by only sealed consumers.",
            )
        }
        val producerEntry = producerEntries.single()
        val producerSeal = producerEntry.seal as
            GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
        val orderedConsumers = consumerEntries.map { entry ->
            entry to (entry.seal as GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer)
        }
        if (renderEntries.first() !== producerEntry ||
            orderedConsumers.map { it.first } != renderEntries.drop(1) ||
            producerSeal.sourceStepIndex != producerEntry.scope.sourceStepIndex ||
            producerEntry.scope.sourcePacketIds != listOf(producerSeal.packetId) ||
            orderedConsumers.any { (entry, seal) ->
                seal.sourceStepIndex != entry.scope.sourceStepIndex ||
                    entry.scope.sourcePacketIds != listOf(seal.packetId) ||
                    seal.route !== producerSeal.route ||
                    seal.geometryArena !== producerSeal.geometryArena ||
                    seal.slabAuthority !== producerSeal.slabAuthority ||
                    seal.attachmentAuthority !== producerSeal.attachmentAuthority
            } || orderedConsumers.map { it.second.sourceOrder } !=
            producerSeal.route.consumers.map { it.sourceOrder } ||
            orderedConsumers.map { it.second.commandId } !=
            producerSeal.route.consumers.map { it.commandId } ||
            orderedConsumers.dropLast(1).any { it.second.isLastConsumer } ||
            !orderedConsumers.last().second.isLastConsumer
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-seal",
                "Prepared clip-stencil scope order or retained frame authority was substituted.",
            )
        }

        val producerPacket = producerEntry.render.drawPackets.singleOrNull()
        if (producerPacket?.packetId != producerSeal.packetId ||
            producerPacket.commandIdValue != producerSeal.commandId ||
            producerPacket.role != GPUDrawPacketRole.StencilProducer
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-packet",
                "Prepared clip-stencil producer packet identity or role was substituted.",
            )
        }
        val consumerSemantics = orderedConsumers.map { (entry, seal) ->
            val packet = entry.render.drawPackets.singleOrNull()
                ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-packet",
                    "Every prepared clip-stencil consumer requires one exact packet.",
                )
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-packet",
                    "Every prepared clip-stencil consumer requires one typed semantic payload.",
                )
            val routeConsumer = producerSeal.route.consumers.singleOrNull {
                it.commandId == seal.commandId && it.sourceOrder == seal.sourceOrder
            } ?: return refused(
                "invalid.native-core-primitive.clip-stencil-packet",
                "Prepared clip-stencil consumer is absent from its sealed route.",
            )
            if (packet.packetId != seal.packetId || packet.commandIdValue != seal.commandId ||
                packet.originalPaintOrder != seal.sourceOrder || packet.role != GPUDrawPacketRole.Shading ||
                !semantic.hasStructuralIntegrity() ||
                semantic.payloadRef.commandIdValue != seal.commandId ||
                routeConsumer.structuralKey != packet.corePrimitivePreparedAuthority?.structuralPipelineKey ||
                (routeConsumer.scissor ?: semantic.targetBounds) != semantic.scissorBounds
            ) {
                return refused(
                    "invalid.native-core-primitive.clip-stencil-packet",
                    "Prepared clip-stencil consumer packet contradicts its sealed semantic authority.",
                )
            }
            semantic
        }

        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.clip-stencil-frame-shape",
                "Prepared clip-stencil accepts only its render chain and one optional readback.",
            )
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "invalid.native-core-primitive.clip-stencil-readback",
                "Prepared clip-stencil lost its optional readback scope.",
            )
        }
        if (encoderPlan.scopes != prefixEntries.map(RenderEntry::scope) +
            renderEntries.map(RenderEntry::scope) + listOfNotNull(readbackScope) ||
            framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } !=
            prefixEntries.size + renderEntries.size + (if (readbackStep == null) 0 else 1)
        ) {
            return refused(
                "unsupported.native-core-primitive.clip-stencil-scope-order",
                "Prepared clip-stencil scopes must remain producer, consumers, then optional readback.",
            )
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparationOrNull = preparation(GPUFrameResourceRole.SceneTarget)
        val vertexPreparationOrNull = preparation(GPUFrameResourceRole.VertexData)
        val indexPreparationOrNull = preparation(GPUFrameResourceRole.IndexData)
        val uniformPreparationOrNull = preparation(GPUFrameResourceRole.UniformData)
        val clipDepthStencilPreparationOrNull = preparation(GPUFrameResourceRole.ClipDepthStencil)
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        if (preparations.size != 5 + (if (readbackStep == null) 0 else 1) ||
            targetPreparationOrNull == null || vertexPreparationOrNull == null ||
            indexPreparationOrNull == null || uniformPreparationOrNull == null ||
            clipDepthStencilPreparationOrNull == null ||
            (readbackStep == null) != (stagingPreparation == null)
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-resource-shape",
                "Prepared clip-stencil requires exactly target, V/I/U slabs, one clip D24S8, and optional staging.",
            )
        }
        val targetPreparation = requireNotNull(targetPreparationOrNull)
        val vertexPreparation = requireNotNull(vertexPreparationOrNull)
        val indexPreparation = requireNotNull(indexPreparationOrNull)
        val uniformPreparation = requireNotNull(uniformPreparationOrNull)
        val clipDepthStencilPreparation = requireNotNull(clipDepthStencilPreparationOrNull)
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil target is not a texture.",
            )
        val clipDescriptor = clipDepthStencilPreparation.descriptor as? GPUFrameTextureDescriptor
            ?: return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil D24S8 authority is not a texture.",
            )
        val vertexDescriptor = vertexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil vertex authority is not a buffer.",
            )
        val indexDescriptor = indexPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil index authority is not a buffer.",
            )
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil uniform authority is not a buffer.",
            )
        val targetBounds = targetDescriptor.logicalBounds
        val route = producerSeal.route
        val targetStructuralColorFormat = try {
            targetDescriptor.format.corePrimitiveStructuralColorFormat()
        } catch (_: IllegalArgumentException) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil requires one exact supported scene target.",
            )
        }
        val sampleCount = route.attachment.sampleCount
        val isMsaa4x = sampleCount == 4
        val expectedSamplePlan = if (isMsaa4x) {
            GPUSamplePlan.MultisampleFrame(4)
        } else {
            GPUSamplePlan.SingleSampleFrame
        }
        val arena = producerSeal.geometryArena
        val slab = producerSeal.slabAuthority
        val uniformPlan = slab.uniformPlan
        val prefixPacket = prefixEntries.singleOrNull()?.render?.drawPackets?.singleOrNull()
        val prefixSemantic = prefixPacket?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
        val prefixStructuralKey = prefixPacket?.corePrimitivePreparedAuthority?.structuralPipelineKey
        val prefixRect = prefixSemantic?.geometry as? GPUCorePrimitiveGeometry.Rect
        val prefixVertices = prefixRect?.let {
            floatArrayOf(
                it.left, it.top, it.right, it.top,
                it.right, it.bottom, it.left, it.bottom,
            )
        } ?: FloatArray(0)
        val prefixIndices = prefixRect?.let { intArrayOf(0, 2, 1, 0, 3, 2) } ?: IntArray(0)
        val vertexData = prefixVertices + arena.copyVertices()
        val indexData = prefixIndices + arena.copyIndices()
        val vertexBytes = slab.vertexByteSize
        val indexBytes = slab.indexByteSize
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            targetBounds.width != route.attachment.width || targetBounds.height != route.attachment.height ||
            targetPreparation.resource != producerEntry.render.target ||
            !targetDescriptor.format.isCorePrimitiveSceneTargetFormat() ||
            route.producer.structuralKey.colorFormat != targetStructuralColorFormat ||
            route.consumers.any { it.structuralKey.colorFormat != targetStructuralColorFormat } ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            clipDepthStencilPreparation.resource != producerSeal.attachmentAuthority.resource ||
            clipDescriptor.logicalBounds != targetBounds ||
            clipDescriptor.format.value != DEPTH24PLUS_STENCIL8 ||
            clipDescriptor.sampleCount != sampleCount || sampleCount !in setOf(1, 4) ||
            clipDepthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            clipDepthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            vertexPreparation.resource != slab.vertexResource ||
            indexPreparation.resource != slab.indexResource ||
            uniformPreparation.resource != slab.uniformResource ||
            generationSeal.resourceGenerations[slab.vertexResource] != slab.vertexGeneration ||
            generationSeal.resourceGenerations[slab.indexResource] != slab.indexGeneration ||
            generationSeal.resourceGenerations[slab.uniformResource] != slab.uniformGeneration ||
            generationSeal.resourceGenerations[producerSeal.attachmentAuthority.resource] !=
            producerSeal.attachmentAuthority.resourceGeneration ||
            vertexDescriptor.byteSize != vertexBytes || vertexDescriptor.alignmentBytes != 4L ||
            vertexPreparation.byteSize != vertexBytes ||
            vertexPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Vertex,
            ) || vertexPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            indexDescriptor.byteSize != indexBytes || indexDescriptor.alignmentBytes != 4L ||
            indexPreparation.byteSize != indexBytes ||
            indexPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Index,
            ) || indexPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformDescriptor.byteSize != uniformPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            vertexBytes != vertexData.size.toLong() * Float.SIZE_BYTES ||
            indexBytes != indexData.size.toLong() * Int.SIZE_BYTES ||
            arena.slices.map { it.packetId } != listOf(producerSeal.packetId) +
            orderedConsumers.map { it.second.packetId }
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil target, shared slabs, or D24S8 authority was substituted.",
            )
        }
        val expectedDepthBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(
                    Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                    4L,
                ),
                sampleCount.toLong(),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil D24S8 byte sizing overflowed.",
            )
        }
        val producerContinuation = producerEntry.render.sampleContinuation
        if (clipDepthStencilPreparation.byteSize != expectedDepthBytes ||
            producerEntry.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                if (isMsaa4x) "clear" else "load",
                GPUStorePlan.Store,
            ) || producerEntry.render.samplePlan != expectedSamplePlan ||
            producerEntry.render.depthStencilLoadStore !=
            org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                GPUStorePlan.Store,
                0u,
            ) || !hasExactClipStencilContinuation(
                producerEntry.render,
                producerContinuation,
                clipDepthStencilPreparation.resource,
                generationSeal,
                targetDescriptor.format,
                expectedLoad = if (isMsaa4x) GPUSampleLoadTransition.FreshClear else null,
            ) || orderedConsumers.withIndex().any { (index, pair) ->
                pair.first.render.target != producerEntry.render.target ||
                    pair.first.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                        if (isMsaa4x) "load"
                        else if (index == 0 && prefixEntries.isEmpty()) "clear"
                        else "load",
                        GPUStorePlan.Store,
                    ) || pair.first.render.samplePlan != expectedSamplePlan ||
                    pair.first.render.depthStencilLoadStore !=
                    org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.ReadOnlyKeep
                    || !hasExactClipStencilContinuation(
                        pair.first.render,
                        pair.first.render.sampleContinuation,
                        clipDepthStencilPreparation.resource,
                        generationSeal,
                        targetDescriptor.format,
                        expectedLoad = if (isMsaa4x) GPUSampleLoadTransition.RetainedLoad else null,
                        expectedKey = producerContinuation?.key,
                    )
            }
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-render-state",
                "Prepared clip-stencil load/store or shared attachment state was substituted.",
            )
        }

        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        if (resources.ordinaryResources.size != 5 ||
            listOf(
                targetPreparation,
                vertexPreparation,
                indexPreparation,
                uniformPreparation,
                clipDepthStencilPreparation,
            ).any { request ->
                val evidence = preparedByLogical[request.resource]
                val texture = request.role == GPUFrameResourceRole.SceneTarget ||
                    request.role == GPUFrameResourceRole.ClipDepthStencil
                evidence == null || evidence.role != request.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[request.resource] ||
                    if (texture) {
                        evidence.concreteResource !is GPUPreparedConcreteResourceRef.Texture
                    } else {
                        evidence.concreteResource !is GPUPreparedConcreteResourceRef.Buffer
                    }
            }
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-prepared-resources",
                "Prepared clip-stencil concrete resource evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width ||
            preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-prepared-target",
                "Prepared clip-stencil scene target differs from its sealed target.",
            )
        }

        val expectedProducerRoles = buildList {
            if (isMsaa4x) {
                add(GPUPreparedNativeOperandRole.RenderMsaaColorTarget)
                add(GPUPreparedNativeOperandRole.RenderResolveTarget)
            } else {
                add(GPUPreparedNativeOperandRole.RenderColorTarget)
            }
            add(GPUPreparedNativeOperandRole.RenderDepthStencilTarget)
            add(GPUPreparedNativeOperandRole.RenderPipeline)
            add(GPUPreparedNativeOperandRole.RenderVertexBuffer)
            add(GPUPreparedNativeOperandRole.RenderIndexBuffer)
        }
        val expectedProducerKinds = expectedProducerRoles.map { role ->
            when (role) {
                GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
                GPUPreparedNativeOperandRole.RenderResolveTarget,
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                -> GPUPreparedNativeOperandKind.TextureView
                GPUPreparedNativeOperandRole.RenderPipeline ->
                    GPUPreparedNativeOperandKind.RenderPipeline
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                -> GPUPreparedNativeOperandKind.Buffer
                else -> error("Unexpected prepared clip-stencil producer operand role")
            }
        }
        val expectedConsumerRoles = expectedProducerRoles +
            GPUPreparedNativeOperandRole.RenderBindGroup
        val expectedConsumerKinds = expectedProducerKinds + GPUPreparedNativeOperandKind.BindGroup
        val exactMsaaAttachmentKeys = !isMsaa4x || renderEntries.all { entry ->
            val keys = entry.scope.nativeOperandKeys
            val continuation = requireNotNull(entry.render.sampleContinuation)
            val targetGeneration = generationSeal.resourceGenerations[targetPreparation.resource]
            val depthGeneration = generationSeal.resourceGenerations[clipDepthStencilPreparation.resource]
            targetGeneration != null && depthGeneration != null &&
                keys.getOrNull(0)?.bindingKey == gpuPreparedNativeBindingKey(
                    "msaa:${continuation.key.colorAttachment.value}",
                ) && keys.getOrNull(1)?.bindingKey == gpuPreparedNativeBindingKey(
                    "GPUFrameTargetRef:${targetPreparation.resource.value}@$targetGeneration",
                ) && keys.getOrNull(2)?.bindingKey == gpuPreparedNativeBindingKey(
                    "GPUFrameTextureRef:${clipDepthStencilPreparation.resource.value}@$depthGeneration",
                )
        }
        if (producerEntry.scope.nativeOperandKeys.map { it.role } != expectedProducerRoles ||
            producerEntry.scope.nativeOperandKeys.map { it.kind } != expectedProducerKinds ||
            orderedConsumers.any { (entry, _) ->
                entry.scope.nativeOperandKeys.map { it.role } != expectedConsumerRoles ||
                    entry.scope.nativeOperandKeys.map { it.kind } != expectedConsumerKinds
            } || renderEntries.flatMap { it.scope.nativeOperandKeys }.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            } || !exactMsaaAttachmentKeys
        ) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-native-keys",
                "Prepared clip-stencil native keys must exactly encode shared target, D24S8, geometry, and consumer uniforms.",
            )
        }

        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-readback",
                "Prepared clip-stencil optional readback must retain one output-owned staging lease.",
            )
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != targetPreparation.resource ||
                readbackStep.staging != stagingPreparation.resource ||
                readbackStep.request.sourceBounds != targetBounds || output.request != readbackStep.request ||
                output.stagingResource != stagingPreparation.resource ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "invalid.native-core-primitive.clip-stencil-readback",
                    "Prepared clip-stencil readback layout or staging authority was substituted.",
                )
            }
        }

        val structuralKeys = listOf(route.producer.structuralKey) +
            route.consumers.map { it.structuralKey } + listOfNotNull(prefixStructuralKey)
        val cacheKeys = linkedMapOf<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitivePipelineCacheKey
            >()
        structuralKeys.distinct().forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey) as?
                GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
                "unsupported.native-core-primitive.clip-stencil-pipeline",
                "Prepared clip-stencil contains a structural pipeline outside the closed native programs.",
            )
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                mapped.componentIdentity,
                mapped.identity,
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during clip-stencil validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val msaaColorRequirement = if (isMsaa4x) {
                GPUWgpu4kCorePrimitiveMsaaColorRequirement(
                    target = producerEntry.render.target,
                    colorAttachment = requireNotNull(producerContinuation).key.colorAttachment,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    width = targetBounds.width,
                    height = targetBounds.height,
                    format = targetDescriptor.format.toCorePrimitiveGPUTextureFormat(),
                )
            } else {
                null
            }
            val acquiredByStructural = linkedMapOf<
                GPUCorePrimitiveRenderPipelineStructuralKey,
                GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired
                >()
            cacheKeys.forEach { (structuralKey, cacheKey) ->
                val acquired = when (val result = sessionCache.acquire(cacheKey)) {
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> result
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                        synchronized(this) { materializing = false }
                        return refusedSessionCacheAcquire(result.reason)
                    }
                }
                acquiredByStructural[structuralKey] = acquired
            }
            val clipRequirement = GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
                targetBounds.width,
                targetBounds.height,
                GPUTextureFormat.Depth24PlusStencil8,
                sampleCount,
                GPUTextureUsage.RenderAttachment,
                target = producerEntry.render.target.takeIf { isMsaa4x },
                depthStencilAttachment = producerContinuation?.key?.depthStencilAttachment,
                deviceGeneration = generationSeal.deviceGeneration.takeIf { isMsaa4x },
                targetGeneration = generationSeal.targetGeneration.takeIf { isMsaa4x },
            )
            val bindGroupComponentIdentities = acquiredByStructural.values
                .map(GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired::componentIdentity)
                .filter { identity ->
                    identity.bindingPolicy == GPUWgpu4kCorePrimitiveBindingPolicy.DynamicUniformRequired
                }
                .toSet()
            val primaryBindGroupComponentIdentity = bindGroupComponentIdentities.firstOrNull()
                ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-bindings",
                    "Clip-stencil consumers require one dynamic-uniform bind-group component.",
                )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    deviceGeneration = generationSeal.deviceGeneration,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    uniformBytes = uniformPlan.totalBytes,
                    componentIdentity = primaryBindGroupComponentIdentity,
                    clipDepthStencil = clipRequirement,
                    sampleCount = sampleCount,
                    msaaColor = msaaColorRequirement,
                    additionalComponentIdentities = bindGroupComponentIdentities -
                        primaryBindGroupComponentIdentity,
                ),
            )) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val clipHandles = requireNotNull(pooled.handles.clipDepthStencil)
            require(
                clipHandles.requirement == clipRequirement &&
                    pooled.handles.sampleCount == sampleCount &&
                    pooled.handles.msaaColor?.requirement == msaaColorRequirement &&
                    (isMsaa4x == (pooled.handles.msaaColor != null))
            ) {
                "Pooled paired clip attachments differ from their exact requirements"
            }
            uploadExact(
                pooled.handles.vertexBuffer,
                ArrayBuffer.of(vertexData),
                vertexBytes,
                pooled.capacities.vertexBytes,
            )
            uploadExact(
                pooled.handles.indexBuffer,
                ArrayBuffer.of(indexData),
                indexBytes,
                pooled.capacities.indexBytes,
            )
            uploadExact(
                pooled.handles.uniformBuffer,
                ArrayBuffer.of(slab.packedUniformBytesForUpload()),
                uniformPlan.totalBytes,
                pooled.capacities.uniformBytes,
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.clipStencil.readback",
                    ),
                ).tracked()
            }
            val canonicalTargetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val colorTargetOperand = pooled.handles.msaaColor?.let { msaa ->
                GPUPreparedNativeTextureViewOperand(
                    msaa.view,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            } ?: canonicalTargetOperand
            val clipOperand = GPUPreparedNativeTextureViewOperand(
                clipHandles.view,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val vertexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.indexBytes,
            )
            fun bindGroupOperandFor(structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey) =
                GPUPreparedNativeBindGroupOperand(
                    requireNotNull(pooled.handles.bindGroupsByComponentIdentity[
                        acquiredByStructural.getValue(structuralKey).componentIdentity
                    ]),
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            val pipelineOperands = acquiredByStructural.mapValues { (_, acquired) ->
                GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
                    acquired,
                    generationSeal.deviceGeneration,
                )
            }
            fun geometryCommands(
                structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
                slice: GPUCorePrimitiveClipStencilPreparedGeometrySlice,
                scissor: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
                dynamicOffset: Long?,
            ) = buildList {
                add(GPUPreparedNativeRenderCommand.SetPipeline(
                    requireNotNull(pipelineOperands[structuralKey]),
                ))
                add(GPUPreparedNativeRenderCommand.SetStencilReference(route.stencilReference))
                if (dynamicOffset != null) {
                    add(GPUPreparedNativeRenderCommand.SetBindGroup(
                        0,
                        bindGroupOperandFor(structuralKey),
                        listOf(dynamicOffset),
                    ))
                }
                add(GPUPreparedNativeRenderCommand.SetVertexBuffer(
                    0,
                    vertexOperand,
                    0L,
                    vertexBytes,
                    8L,
                ))
                add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                    indexOperand,
                    GPUPreparedNativeIndexFormat.Uint32,
                    0L,
                    indexBytes,
                ))
                add(GPUPreparedNativeRenderCommand.SetScissor(
                    scissor.left,
                    scissor.top,
                    scissor.width,
                    scissor.height,
                ))
                add(GPUPreparedNativeRenderCommand.DrawIndexed(
                    GPUPreparedNativeDrawCall.DrawIndexed(
                        indexCount = slice.indexCount,
                        firstIndex = slice.firstIndex,
                        baseVertex = slice.baseVertex,
                        vertexCount = slice.vertexCount,
                        maxLocalIndex = slice.maxLocalIndex,
                    ),
                ))
            }
            val clipVertexOffset = prefixVertices.size / 2
            val clipIndexOffset = prefixIndices.size
            // Without a direct prefix, retain the established producer/consumer pass layout.
            // The single physical pass is deliberately narrow: a background must clear color
            // and stencil before both the producer and its consumers run in-order.
            val passSegment = prefixEntries.singleOrNull()?.let { prefixEntry ->
                GPUPreparedNativeScopeOperand.RenderPassSegment(
                    id = "clip-stencil.${framePlan.frameId.value}.${producerSeal.route.atomicGroup.value}",
                    firstSourceStepIndex = prefixEntry.scope.sourceStepIndex,
                    lastSourceStepIndex = orderedConsumers.last().first.scope.sourceStepIndex,
                )
            }
            fun shiftedClipSlice(
                slice: GPUCorePrimitiveClipStencilPreparedGeometrySlice,
            ) = slice.copy(
                firstIndex = slice.firstIndex + clipIndexOffset,
                baseVertex = slice.baseVertex + clipVertexOffset,
            )
            val producerOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = producerEntry.scope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = colorTargetOperand,
                    resolveTarget = canonicalTargetOperand.takeIf { isMsaa4x },
                    depthStencilTarget = clipOperand,
                    loadOperation = if (isMsaa4x) {
                        GPUPreparedNativeLoadOperation.Clear
                    } else {
                        GPUPreparedNativeLoadOperation.Load
                    },
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = if (isMsaa4x) {
                        GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                    } else {
                        null
                    },
                    depthReadOnly = true,
                    stencilClearValue = 0u,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    stencilReadOnly = false,
                ),
                commands = geometryCommands(
                    route.producer.structuralKey,
                    shiftedClipSlice(producerSeal.geometrySlice),
                    route.producer.scissor ?: GPUPixelBounds(0, 0, targetBounds.width, targetBounds.height),
                    null,
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
                passSegment = passSegment,
            )
            val consumerOperands = orderedConsumers.mapIndexed { index, (entry, seal) ->
                val routeConsumer = route.consumers[index]
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = entry.scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = colorTargetOperand,
                        resolveTarget = canonicalTargetOperand.takeIf { isMsaa4x },
                        depthStencilTarget = clipOperand,
                        loadOperation = if (!isMsaa4x && index == 0 && prefixEntries.isEmpty()) {
                            GPUPreparedNativeLoadOperation.Clear
                        } else {
                            GPUPreparedNativeLoadOperation.Load
                        },
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (!isMsaa4x && index == 0 && prefixEntries.isEmpty()) {
                            GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        } else {
                            null
                        },
                        depthReadOnly = true,
                        stencilReadOnly = true,
                    ),
                    commands = geometryCommands(
                        routeConsumer.structuralKey,
                        shiftedClipSlice(seal.geometrySlice),
                        routeConsumer.scissor ?: GPUPixelBounds(0, 0, targetBounds.width, targetBounds.height),
                        seal.uniformSlice.alignedOffset,
                    ),
                    semanticPayloads = listOf(consumerSemantics[index]),
                    operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
                    passSegment = passSegment,
                )
            }
            val prefixOperand = prefixEntries.singleOrNull()?.let { prefixScopeEntry ->
                val packet = prefixPacket ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-prefix",
                    "Prepared clip-stencil prefix lost its direct CorePrimitive packet.",
                )
                val semantic = prefixSemantic ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-prefix",
                    "Prepared clip-stencil prefix lost its direct semantic payload.",
                )
                val structuralKey = prefixStructuralKey ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-prefix",
                    "Prepared clip-stencil prefix lost its structural pipeline authority.",
                )
                val uniformSlot = requireNotNull(slab.uniformSlabSeal).plan.slots.singleOrNull {
                    it.slotLabel == "draw-${packet.commandIdValue}"
                } ?: return refused(
                    "invalid.native-core-primitive.clip-stencil-prefix",
                    "Prepared clip-stencil prefix lost its uniform slot authority.",
                )
                val prefixRect = semantic.geometry as? GPUCorePrimitiveGeometry.Rect
                    ?: return refused(
                        "invalid.native-core-primitive.clip-stencil-prefix",
                        "Prepared clip-stencil prefix is not direct Rect geometry.",
                    )
                val prefixCommands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        requireNotNull(pipelineOperands[structuralKey]),
                    ),
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        0,
                        bindGroupOperandFor(structuralKey),
                        listOf(uniformSlot.alignedOffset),
                    ),
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        0L,
                        vertexBytes,
                        8L,
                    ),
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        0L,
                        indexBytes,
                    ),
                    GPUPreparedNativeRenderCommand.SetScissor(
                        semantic.scissorBounds.left,
                        semantic.scissorBounds.top,
                        semantic.scissorBounds.width,
                        semantic.scissorBounds.height,
                    ),
                    GPUPreparedNativeRenderCommand.DrawIndexed(
                        GPUPreparedNativeDrawCall.DrawIndexed(
                            indexCount = 6,
                            firstIndex = 0,
                            baseVertex = 0,
                            vertexCount = 4,
                            maxLocalIndex = 3,
                        ),
                    ),
                )
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = prefixScopeEntry.scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = canonicalTargetOperand,
                        depthStencilTarget = clipOperand,
                        loadOperation = if (prefixScopeEntry.render.loadStore.loadOp == "clear") {
                            GPUPreparedNativeLoadOperation.Clear
                        } else {
                            GPUPreparedNativeLoadOperation.Load
                        },
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (prefixScopeEntry.render.loadStore.loadOp == "clear") {
                            GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        } else {
                            null
                        },
                        depthReadOnly = true,
                        stencilClearValue = 0u,
                        stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                        stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
                        stencilReadOnly = false,
                    ),
                    commands = prefixCommands,
                    semanticPayloads = listOf(semantic),
                    operandLayout = GPUPreparedNativeRenderOperandLayout.CommandOrder,
                    passSegment = passSegment,
                )
            }
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
                        format = targetDescriptor.format.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else {
                null
            }
            val operandsByStep = (listOfNotNull(prefixOperand) + listOf(producerOperand) + consumerOperands +
                listOfNotNull(readbackOperand)).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
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
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
                clipDepthStencilViewAuthority =
                    (listOfNotNull(prefixOperand) + listOf(producerOperand) + consumerOperands).associate { render ->
                        render.sourceStepIndex to clipHandles.view
                    },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during clip-stencil materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.clip-stencil-materialization",
                "Public wgpu4k prepared clip-stencil materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun hasExactClipStencilContinuation(
        render: GPUFrameStep.RenderPassStep,
        continuation: GPUSampleContinuationRequest?,
        depthStencilResource: GPUFrameResourceRef,
        generationSeal: GPUPreparedGenerationSeal,
        targetFormat: GPUColorFormat,
        expectedLoad: GPUSampleLoadTransition?,
        expectedKey: GPUSampleContinuationKey? = null,
    ): Boolean {
        if (expectedLoad == null) {
            return render.samplePlan == GPUSamplePlan.SingleSampleFrame && continuation == null
        }
        val exact = continuation ?: return false
        val key = exact.key
        val targetInterpretation = targetFormat.corePrimitiveInterpretationOrNull() ?: return false
        return render.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
            (expectedKey == null || key == expectedKey) &&
            key.target.value == render.target.value &&
            key.targetGeneration == generationSeal.targetGeneration &&
            key.deviceGeneration == generationSeal.deviceGeneration &&
            key.colorFormat == targetFormat &&
            key.colorInterpretation == targetInterpretation &&
            key.samplePlan == GPUSamplePlan.MultisampleFrame(4) &&
            key.attachmentAuthority == GPUSampleAttachmentAuthority.PreparedFramePayload &&
            key.colorAttachment.value ==
            "msaa-color:${render.target.value}:${generationSeal.targetGeneration}" &&
            key.depthStencilAttachment?.value == depthStencilResource.value &&
            exact.loadTransition == expectedLoad &&
            exact.storeAction == GPUSampleStoreAction.Store &&
            exact.resolveAction == GPUSampleResolveAction.ResolveCanonical
    }

    private fun materializeIndexedPathCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
        renderStep: GPUFrameStep.RenderPassStep,
        renderScope: GPUCommandEncoderScopePlan,
        unifiedRoute: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val pathSeal = renderScope.corePrimitivePathStencilNativeRouteSeal as?
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs ?: return refused(
            "invalid.native-core-primitive.path-route-seal",
            "Indexed CorePrimitive requires one exact non-empty path-pair compatibility seal.",
        )
        val packetIds = renderStep.drawPackets.map { it.packetId }
        if (renderScope.sourcePacketIds != packetIds ||
            unifiedRoute.flattenedPacketIds != packetIds ||
            pathSeal.flattenedPacketIds != unifiedRoute.orderedUnits
                .filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.PathPair>()
                .flatMap(GPUCorePrimitiveNativeScopeRouteUnit.PathPair::flattenedPacketIds)
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-route-seal",
                "Indexed CorePrimitive unified and path seals must match the exact packet order.",
            )
        }
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.indexed-scope-shape",
                "Indexed CorePrimitive accepts one render scope and one optional readback scope.",
            )
        }
        val expectedEncoderSteps = 1 + if (readbackStep == null) 0 else 1
        if (framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } != expectedEncoderSteps) {
            return refused(
                "unsupported.native-core-primitive.indexed-encoder-shape",
                "Indexed CorePrimitive contains an unsupported encoder operation.",
            )
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.indexed-readback-plan",
                "The indexed CorePrimitive readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(renderScope, readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.indexed-scope-order",
                "Indexed CorePrimitive encoder scopes must preserve render order then optional readback.",
            )
        }
        val indexedPathUnits = unifiedRoute.orderedUnits.withIndex()
            .filter { (_, unit) -> unit is GPUCorePrimitiveNativeScopeRouteUnit.PathPair }
        val pathUnits = indexedPathUnits.map { it.value as GPUCorePrimitiveNativeScopeRouteUnit.PathPair }
        val preparedPathPass = pathSeal.preparedPassSeal
        val preparedPathUniformExact = preparedPathPass != null && when (
            val uniformAuthority = unifiedRoute.uniformAuthority
        ) {
            is GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab ->
                preparedPathPass.uniformSlabSeal === uniformAuthority.seal
            is GPUCorePrimitiveNativeScopeUniformAuthority.PathPreparedPass ->
                preparedPathPass === uniformAuthority.seal
            is GPUCorePrimitiveNativeScopeUniformAuthority.DirectPreparedPass -> false
        }
        val preparedPathExact = preparedPathPass != null &&
            preparedPathUniformExact &&
            preparedPathPass.orderedPairs.size == indexedPathUnits.size &&
            preparedPathPass.orderedPairs.zip(indexedPathUnits).all { (prepared, indexedUnit) ->
                val unit = indexedUnit.value as GPUCorePrimitiveNativeScopeRouteUnit.PathPair
                prepared.commandIdValue == unit.commandIdValue &&
                    prepared.uniformSlotIndex == indexedUnit.index &&
                    prepared.producerPacketId == unit.pair.producerPacketId &&
                    prepared.coverPacketId == unit.pair.coverPacketId &&
                    prepared.producerStructuralPipelineKey == unit.producerStructuralPipelineKey &&
                    prepared.coverStructuralPipelineKey == unit.coverStructuralPipelineKey
            }
        if (pathUnits.map { it.pair } != pathSeal.orderedPairs ||
            !preparedPathExact
        ) {
            return refused(
                "invalid.native-core-primitive.path-route-seal",
                "Indexed CorePrimitive path compatibility authority differs from the unified seal.",
            )
        }
        val directUnits = unifiedRoute.orderedUnits
            .filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.Direct>()
        val sampleCount = renderStep.samplePlan.sampleCount
        val isMsaa4x = renderStep.samplePlan == GPUSamplePlan.MultisampleFrame(4)
        val indexedTargetFormat =
            framePlan.corePrimitiveSceneTargetDescriptor(renderStep.target)?.format ?: return refused(
                "invalid.native-core-primitive.indexed-target",
                "Indexed CorePrimitive requires one exact supported scene target.",
            )
        val indexedTargetInterpretation =
            indexedTargetFormat.corePrimitiveInterpretationOrNull() ?: return refused(
                "invalid.native-core-primitive.indexed-target",
                "Indexed CorePrimitive requires one exact supported scene target.",
            )
        val indexedStructuralColorFormat =
            indexedTargetFormat.corePrimitiveStructuralColorFormat()
        val pathDepthStencilUse = renderStep.resourceUses.singleOrNull {
            it.role == GPUFrameResourceRole.PathDepthStencil
        }
        if (isMsaa4x) {
            val continuation = renderStep.sampleContinuation
            val exactAuthority = pathDepthStencilUse != null &&
                renderStep.resourceUses.none { it.role == GPUFrameResourceRole.ClipDepthStencil } &&
                renderStep.drawPackets.all {
                    it.role == GPUDrawPacketRole.PathStencilProducer ||
                        it.role == GPUDrawPacketRole.PathStencilCover ||
                        it.role == GPUDrawPacketRole.Shading
                } && directUnits.all { unit ->
                    unit.structuralPipelineKey.sampleCount == 4 &&
                        unit.structuralPipelineKey.depthStencil ==
                        corePrimitiveDirectPathDepthStencilState()
                } &&
                continuation?.let { authority ->
                    val sealedTargetGeneration = authority.key.targetGeneration
                    authority.key.target.value == renderStep.target.value &&
                        (
                            sealedTargetGeneration == generationSeal.targetGeneration ||
                                sealedTargetGeneration ==
                                PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
                            ) &&
                        authority.key.deviceGeneration == generationSeal.deviceGeneration &&
                        authority.key.colorFormat == indexedTargetFormat &&
                        authority.key.colorInterpretation == indexedTargetInterpretation &&
                        authority.key.samplePlan == renderStep.samplePlan &&
                        authority.key.attachmentAuthority ==
                        org.graphiks.kanvas.gpu.renderer.passes
                            .GPUSampleAttachmentAuthority.PreparedFramePayload &&
                        authority.key.colorAttachment.value ==
                        "msaa-color:${renderStep.target.value}:$sealedTargetGeneration" &&
                        authority.key.depthStencilAttachment?.value == pathDepthStencilUse.resource.value &&
                        authority.loadTransition == GPUSampleLoadTransition.FreshClear &&
                        authority.storeAction == GPUSampleStoreAction.Store &&
                        authority.resolveAction == GPUSampleResolveAction.ResolveCanonical
                } == true
            if (!exactAuthority) {
                return refused(
                    "invalid.native-core-primitive.indexed-msaa-authority",
                    "Indexed 4x CorePrimitive requires exact paired color/resolve and path D24S8 authority.",
                )
            }
        }
        when (val directSeal = renderScope.corePrimitiveDirectNativeRouteSeal) {
            GPUCorePrimitiveDirectNativeRouteSeal.Empty -> if (directUnits.isNotEmpty()) {
                return refused(
                    "invalid.native-core-primitive.direct-route-seal",
                    "Mixed indexed CorePrimitive is missing its derived direct compatibility seal.",
                )
            }
            is GPUCorePrimitiveDirectNativeRouteSeal.Routes -> {
                val preparedDirectPass = directSeal.preparedPassSeal
                val preparedDirectExact = preparedDirectPass != null &&
                    when (val uniformAuthority = unifiedRoute.uniformAuthority) {
                        is GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab ->
                            preparedDirectPass.uniformSlabSeal === uniformAuthority.seal
                        is GPUCorePrimitiveNativeScopeUniformAuthority.DirectPreparedPass,
                        is GPUCorePrimitiveNativeScopeUniformAuthority.PathPreparedPass,
                        -> false
                    } &&
                    when (preparedDirectPass) {
                        is GPUCorePrimitiveMultiKeyDirectPreparedPassSeal ->
                            preparedDirectPass.structuralPipelineKeys ==
                                directUnits.map { it.structuralPipelineKey }.distinct() &&
                                directUnits.all {
                                    it.structuralPipelineKey in preparedDirectPass.structuralPipelineKeys
                                }
                        is GPUCorePrimitiveDirectPreparedPassSeal ->
                            preparedDirectPass.structuralPipelineKey ==
                                directUnits.firstOrNull()?.structuralPipelineKey &&
                                directUnits.all {
                                    it.structuralPipelineKey == preparedDirectPass.structuralPipelineKey
                                }
                    }
                if (directSeal.routesByPacketId.keys.toList() != directUnits.map { it.packetId } ||
                    directUnits.any { directSeal.routesByPacketId[it.packetId] !== it.route } ||
                    !preparedDirectExact
                ) {
                    return refused(
                        "invalid.native-core-primitive.direct-route-seal",
                        "Mixed indexed CorePrimitive direct compatibility authority is not exact.",
                    )
                }
            }
            GPUCorePrimitiveDirectNativeRouteSeal.Missing -> return refused(
                "invalid.native-core-primitive.direct-route-seal",
                "Indexed CorePrimitive direct compatibility authority is missing.",
            )
        }

        data class PacketPlan(
            val packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
            val semantic: GPUDrawSemanticPayload.CorePrimitive,
            val structuralKey: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
            val uniformSlotIndex: Int,
        )

        val packetPlans = mutableListOf<PacketPlan>()
        var packetIndex = 0
        unifiedRoute.orderedUnits.forEachIndexed { uniformSlotIndex, unit ->
            when (unit) {
                is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> {
                    val packet = renderStep.drawPackets.getOrNull(packetIndex)
                        ?: return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct packet is absent.",
                        )
                    if (packet.packetId != unit.packetId || packet.role != GPUDrawPacketRole.Shading) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct unit differs from packet role or identity.",
                        )
                    }
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                        ?: return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct packet has no typed semantic payload.",
                        )
                    packetPlans += PacketPlan(packet, semantic, unit.structuralPipelineKey, uniformSlotIndex)
                    packetIndex += 1
                }
                is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> {
                    val producer = renderStep.drawPackets.getOrNull(packetIndex)
                    val cover = renderStep.drawPackets.getOrNull(packetIndex + 1)
                    if (producer?.packetId != unit.pair.producerPacketId ||
                        cover?.packetId != unit.pair.coverPacketId ||
                        producer.role != GPUDrawPacketRole.PathStencilProducer ||
                        cover.role != GPUDrawPacketRole.PathStencilCover
                    ) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive path unit differs from producer/cover packet order.",
                        )
                    }
                    val producerSemantic = producer.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    val coverSemantic = cover.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    val exactClipPair = hasExactCorePrimitivePathClipPair(producer, cover)
                    if (producerSemantic == null || coverSemantic == null ||
                        producerSemantic.payloadRef.commandIdValue != coverSemantic.payloadRef.commandIdValue ||
                        producerSemantic.geometry != coverSemantic.geometry ||
                        producerSemantic.targetBounds != coverSemantic.targetBounds ||
                        producerSemantic.scissorBounds != coverSemantic.scissorBounds ||
                        producerSemantic.coverageMode != if (isMsaa4x) {
                            org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode.StencilAA
                        } else {
                            org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode.Stencil1x
                        } ||
                        coverSemantic.coverageMode != if (isMsaa4x) {
                            org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode.StencilAA
                        } else {
                            org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode.Stencil1x
                        } ||
                        producer.uniformSlot != cover.uniformSlot ||
                        !exactClipPair ||
                        producerSemantic.payloadRef.uniformBlock?.bytes !=
                        coverSemantic.payloadRef.uniformBlock?.bytes
                    ) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive path pair requires one identical typed semantic payload.",
                        )
                    }
                    packetPlans += PacketPlan(
                        producer,
                        producerSemantic,
                        unit.producerStructuralPipelineKey,
                        uniformSlotIndex,
                    )
                    packetPlans += PacketPlan(
                        cover,
                        coverSemantic,
                        unit.coverStructuralPipelineKey,
                        uniformSlotIndex,
                    )
                    packetIndex += 2
                }
                is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer,
                is GPUCorePrimitiveNativeScopeRouteUnit.PathCover,
                -> return refused(
                    "invalid.native-core-primitive.indexed-packet-authority",
                    "Indexed CorePrimitive cannot retain a continued producer/cover half.",
                )
            }
        }
        if (packetIndex != renderStep.drawPackets.size ||
            unifiedRoute.commandIds != unifiedRoute.uniformCommandIds
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-packet-authority",
                "Indexed CorePrimitive route does not cover the exact prepared packet stream.",
            )
        }
        val analyticClipBindGroupRequired = packetPlans.any { plan ->
            plan.structuralKey.uniformLayout ==
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
        }
        val targetBounds = packetPlans.first().semantic.targetBounds
        if (readbackStep != null && readbackStep.request.sourceBounds != targetBounds) {
            return refused(
                "unsupported.native-core-primitive.indexed-readback-layout",
                "Indexed CorePrimitive readback must cover the exact canonical target bounds.",
            )
        }
        fun hasExactIndexedUniformAuthority(
            plan: PacketPlan,
            authority: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority,
        ): Boolean {
            if (unifiedRoute.uniformCommandIds.getOrNull(plan.uniformSlotIndex) !=
                plan.packet.commandIdValue
            ) return false
            return when (plan.structuralKey.uniformLayout) {
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                    when (val uniformAuthority = unifiedRoute.uniformAuthority) {
                        is GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab ->
                            authority.uniformSlabSeal === uniformAuthority.seal &&
                                authority.analyticShapeUniformSeal == null &&
                                authority.analyticClipUniformSeal == null &&
                                authority.analyticIntersectionUniformSeal == null
                        is GPUCorePrimitiveNativeScopeUniformAuthority.PathPreparedPass -> {
                            val analyticAuthority = uniformAuthority.seal.uniformAuthority as?
                                GPUCorePrimitivePathStencilUniformAuthority.AnalyticClip64
                            plan.packet.role == GPUDrawPacketRole.PathStencilProducer &&
                                uniformAuthority.seal === preparedPathPass &&
                                analyticAuthority?.seals?.getOrNull(plan.uniformSlotIndex)?.commandId ==
                                plan.packet.commandIdValue &&
                                authority.uniformSlabSeal == null &&
                                authority.analyticShapeUniformSeal == null &&
                                authority.analyticClipUniformSeal == null &&
                                authority.analyticIntersectionUniformSeal == null
                        }
                        is GPUCorePrimitiveNativeScopeUniformAuthority.DirectPreparedPass -> false
                    }
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 -> {
                    val pathAuthority = unifiedRoute.uniformAuthority as?
                        GPUCorePrimitiveNativeScopeUniformAuthority.PathPreparedPass
                    val analyticAuthority = pathAuthority?.seal?.uniformAuthority as?
                        GPUCorePrimitivePathStencilUniformAuthority.AnalyticClip64
                    val analyticSeal = analyticAuthority?.seals?.getOrNull(plan.uniformSlotIndex)
                    plan.packet.role == GPUDrawPacketRole.PathStencilCover &&
                        pathAuthority?.seal === preparedPathPass &&
                        authority.uniformSlabSeal == null &&
                        authority.analyticShapeUniformSeal == null &&
                        authority.analyticClipUniformSeal === analyticSeal &&
                        authority.analyticIntersectionUniformSeal == null &&
                        analyticSeal != null &&
                        analyticSeal.plan === unifiedRoute.uniformPlan &&
                        analyticSeal.slotIndex == plan.uniformSlotIndex &&
                        analyticSeal.commandId == plan.packet.commandIdValue &&
                        analyticSeal.packetId == plan.packet.packetId &&
                        analyticSeal.structuralPipelineKey == plan.structuralKey &&
                        analyticSeal.renderPipelineKey == plan.packet.renderPipelineKey
                }
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticDRRectUniform128V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1,
                org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1,
                -> false
            }
        }
        if (packetPlans.any { plan ->
                val authority = plan.packet.corePrimitivePreparedAuthority
                val expectedBindingLayoutHash = when (plan.structuralKey.uniformLayout) {
                    org.graphiks.kanvas.gpu.renderer.passes
                        .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout
                        .DynamicUniform32V2 -> CORE_PRIMITIVE_BINDING_LAYOUT_HASH
                    org.graphiks.kanvas.gpu.renderer.passes
                        .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout
                        .AnalyticClipUniform64V1 -> CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
                    else -> null
                }
                !plan.semantic.hasStructuralIntegrity() ||
                    plan.packet.commandIdValue != plan.semantic.payloadRef.commandIdValue ||
                    plan.packet.uniformSlot != plan.semantic.payloadRef.uniformSlot ||
                    expectedBindingLayoutHash == null ||
                    plan.packet.bindingLayoutHash != expectedBindingLayoutHash ||
                    plan.packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                    plan.packet.targetStateHash != corePrimitiveTargetStateHash(
                        sampleCount,
                        indexedTargetFormat,
                    ) ||
                    plan.packet.scissorBoundsHash != corePrimitiveScissorAuthority(plan.semantic.scissorBounds) ||
                    plan.structuralKey.colorFormat != indexedStructuralColorFormat ||
                    authority?.structuralPipelineKey != plan.structuralKey ||
                    authority.renderPipelineKey != plan.packet.renderPipelineKey ||
                    !hasExactIndexedUniformAuthority(plan, authority) ||
                    plan.semantic.targetBounds != targetBounds ||
                    plan.semantic.payloadRef.uniformBlock?.byteSize != CORE_PRIMITIVE_UNIFORM_BYTES.toLong() ||
                    plan.semantic.payloadRef.uniformBlock.bytes !=
                    corePrimitiveUniformBytes(plan.semantic.targetBounds, plan.semantic.premultipliedRgba)
            }
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-packet-authority",
                "Indexed CorePrimitive packet contradicts its sealed semantic, pipeline, or uniform authority.",
            )
        }
        val exactSamplePlan = if (isMsaa4x) {
            renderStep.sampleContinuation != null
        } else {
            renderStep.samplePlan == GPUSamplePlan.SingleSampleFrame && renderStep.sampleContinuation == null
        }
        if (!exactSamplePlan ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null ||
            renderStep.depthStencilLoadStore != org.graphiks.kanvas.gpu.renderer.recording
                .GPUDepthStencilLoadStorePlan.WritableStencil(
                    org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Discard,
                    0u,
                ) ||
            packetPlans.any { plan ->
                plan.semantic.scissorBounds.isEmpty ||
                    plan.semantic.scissorBounds.left < targetBounds.left ||
                    plan.semantic.scissorBounds.top < targetBounds.top ||
                    plan.semantic.scissorBounds.right > targetBounds.right ||
                    plan.semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.indexed-render-state",
                "Indexed CorePrimitive requires exact clear/store color and clear-zero/discard stencil state.",
            )
        }

        val arena = try {
            GPUCorePrimitiveNativeScopeGeometryArena.pack(unifiedRoute)
        } catch (_: IllegalArgumentException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry cannot be packed safely.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry sizing overflows.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        try {
            vertexBytes = Math.multiplyExact(arena.vertexFloatCount.toLong(), Float.SIZE_BYTES.toLong())
            indexBytes = Math.multiplyExact(arena.indexCount.toLong(), Int.SIZE_BYTES.toLong())
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry byte sizing overflows.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L || vertexBytes % 8L != 0L || indexBytes % 4L != 0L ||
            arena.slices.map { it.packetId } != packetIds
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry slices do not match the sealed packet stream.",
            )
        }
        val vertexData = FloatArray(arena.vertexFloatCount).also(arena::copyVerticesInto)
        val indexData = IntArray(arena.indexCount).also(arena::copyIndicesInto)

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
        val depthStencilPreparation = preparation(GPUFrameResourceRole.PathDepthStencil)
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        if (preparations.size != 5 + (if (readbackStep == null) 0 else 1) ||
            targetPreparation == null || vertexPreparation == null || indexPreparation == null ||
            uniformPreparation == null || depthStencilPreparation == null ||
            (readbackStep == null) != (stagingPreparation == null)
        ) {
            return refused(
                "unsupported.native-core-primitive.indexed-resource-shape",
                "Indexed CorePrimitive requires exactly target, V/I/U slabs, path depth/stencil, " +
                    "and optional readback staging.",
            )
        }
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        val depthDescriptor = depthStencilPreparation.descriptor as? GPUFrameTextureDescriptor
        val depthBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                Math.multiplyExact(4L, sampleCount.toLong()),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-depth-stencil",
                "Indexed CorePrimitive depth/stencil size overflows.",
            )
        }
        if (targetPreparation.resource != renderStep.target || targetDescriptor?.logicalBounds != targetBounds ||
            targetDescriptor.format != indexedTargetFormat ||
            !targetDescriptor.format.isCorePrimitiveSceneTargetFormat() ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthDescriptor?.logicalBounds != targetBounds ||
            depthDescriptor.format.value != DEPTH24PLUS_STENCIL8 ||
            depthDescriptor.sampleCount != sampleCount ||
            depthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencilPreparation.byteSize != depthBytes
        ) {
            if (isMsaa4x) {
                return refused(
                    "invalid.native-core-primitive.indexed-msaa-authority",
                    "Indexed 4x CorePrimitive D24S8 preparation is not exact.",
                )
            }
            return refused(
                "invalid.native-core-primitive.indexed-attachment-contract",
                "Indexed CorePrimitive target or D24S8 attachment contract is not exact.",
            )
        }
        if (isMsaa4x) {
            val expectedAttachmentBytes = try {
                Math.multiplyExact(
                    Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                    Math.multiplyExact(RGBA_BYTES_PER_PIXEL, sampleCount.toLong()),
                )
            } catch (_: ArithmeticException) {
                return refused(
                    "invalid.native-core-primitive.indexed-msaa-authority",
                    "Indexed 4x CorePrimitive paired attachment memory authority overflowed.",
                )
            }
            if (framePlan.memoryBudget.categoryTotals[GPUFrameMemoryCategory.FrameLocalMsaaColor] !=
                expectedAttachmentBytes ||
                framePlan.memoryBudget.categoryTotals[
                    GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil
                ] != depthBytes
            ) {
                return refused(
                    "invalid.native-core-primitive.indexed-msaa-authority",
                    "Indexed 4x CorePrimitive paired attachments require exact aggregate memory authority.",
                )
            }
        }
        fun exactGeometryBuffer(
            preparation: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            bytes: Long,
        ): Boolean {
            val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor ?: return false
            return preparation.role == role && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                preparation.byteSize == bytes &&
                preparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactGeometryBuffer(
                vertexPreparation,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                vertexBytes,
            ) || !exactGeometryBuffer(
                indexPreparation,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                indexBytes,
            ) || setOf(
                vertexPreparation.resource,
                indexPreparation.resource,
                uniformPreparation.resource,
                depthStencilPreparation.resource,
            ).size != 4
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-buffer-contract",
                "Indexed CorePrimitive shared V/I slabs are not exact.",
            )
        }
        val uniformPlan = unifiedRoute.uniformPlan
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
        if (uniformDescriptor == null || uniformPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformPlan.totalBytes > Int.MAX_VALUE.toLong() ||
            uniformDescriptor.byteSize != uniformPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-uniform-contract",
                "Indexed CorePrimitive uniform slab differs from its sealed plan.",
            )
        }
        val exactUses = setOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                vertexPreparation.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                indexPreparation.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                uniformPreparation.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                depthStencilPreparation.resource,
                GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceLifetime.FrameLocal,
                true,
            ),
        )
        if (renderStep.resourceUses.toSet() != exactUses || renderStep.resourceUses.size != exactUses.size) {
            return refused(
                "invalid.native-core-primitive.indexed-render-resource-uses",
                "Indexed CorePrimitive render must retain exactly V/I/U and path depth/stencil uses.",
            )
        }
        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        if (resources.ordinaryResources.size != 5 ||
            listOf(
                targetPreparation,
                vertexPreparation,
                indexPreparation,
                uniformPreparation,
                depthStencilPreparation,
            ).any { preparation ->
                val evidence = preparedByLogical[preparation.resource]
                val textureRole = preparation.role == GPUFrameResourceRole.SceneTarget ||
                    preparation.role == GPUFrameResourceRole.PathDepthStencil
                val expectedKind = if (textureRole) {
                    GPUPreparedConcreteResourceRef.Texture::class.java
                } else {
                    GPUPreparedConcreteResourceRef.Buffer::class.java
                }
                evidence == null || evidence.role != preparation.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[preparation.resource] ||
                    !expectedKind.isInstance(evidence.concreteResource)
            }
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-prepared-resources",
                "Indexed CorePrimitive prepared target, slabs, or depth/stencil evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-prepared-target",
                "Indexed CorePrimitive prepared scene target differs from its sealed target.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.indexed-readback-output",
                "The optional indexed CorePrimitive readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != targetPreparation.resource ||
                readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.indexed-readback-layout",
                    "The output-owned indexed CorePrimitive RGBA8 readback layout is not exact.",
                )
            }
        }

        val structuralKeys = packetPlans.map(PacketPlan::structuralKey).distinct()
        val expectedNativeRoles = buildList {
            if (isMsaa4x) {
                add(GPUPreparedNativeOperandRole.RenderMsaaColorTarget)
                add(GPUPreparedNativeOperandRole.RenderResolveTarget)
            } else {
                add(GPUPreparedNativeOperandRole.RenderColorTarget)
            }
            add(GPUPreparedNativeOperandRole.RenderDepthStencilTarget)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandRole.RenderPipeline) }
            add(GPUPreparedNativeOperandRole.RenderVertexBuffer)
            add(GPUPreparedNativeOperandRole.RenderIndexBuffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandRole.RenderBindGroup) }
        }
        val expectedNativeKinds = buildList {
            add(GPUPreparedNativeOperandKind.TextureView)
            if (isMsaa4x) add(GPUPreparedNativeOperandKind.TextureView)
            add(GPUPreparedNativeOperandKind.TextureView)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandKind.RenderPipeline) }
            add(GPUPreparedNativeOperandKind.Buffer)
            add(GPUPreparedNativeOperandKind.Buffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandKind.BindGroup) }
        }
        val exactMsaaAttachmentKeys = !isMsaa4x || run {
            val targetGeneration = generationSeal.resourceGenerations[targetPreparation.resource]
            val depthGeneration = generationSeal.resourceGenerations[depthStencilPreparation.resource]
            val keys = renderScope.nativeOperandKeys
            targetGeneration != null && depthGeneration != null &&
                keys.getOrNull(0)?.bindingKey == gpuPreparedNativeBindingKey(
                    "msaa:${requireNotNull(renderStep.sampleContinuation).key.colorAttachment.value}",
                ) &&
                keys.getOrNull(1)?.bindingKey == gpuPreparedNativeBindingKey(
                    "GPUFrameTargetRef:${targetPreparation.resource.value}@$targetGeneration",
                ) &&
                keys.getOrNull(2)?.bindingKey == gpuPreparedNativeBindingKey(
                    "GPUFrameTextureRef:${depthStencilPreparation.resource.value}@$depthGeneration",
                )
        }
        if (renderScope.nativeOperandKeys.map { it.role } != expectedNativeRoles ||
            renderScope.nativeOperandKeys.map { it.kind } != expectedNativeKinds ||
            renderScope.nativeOperandKeys.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            } || !exactMsaaAttachmentKeys
        ) {
            if (isMsaa4x) {
                return refused(
                    "invalid.native-core-primitive.indexed-msaa-authority",
                    "Indexed 4x CorePrimitive native keys must exactly bind color, resolve, and Path D24S8.",
                )
            }
            return refused(
                "invalid.native-core-primitive.indexed-native-keys",
                "Indexed CorePrimitive native keys must exactly encode target, depth/stencil, " +
                    "unique pipelines, shared geometry, and packet-order bind groups.",
            )
        }
        val cacheKeys = linkedMapOf<
            org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitivePipelineCacheKey
            >()
        structuralKeys.forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey) as?
                GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
                "unsupported.native-core-primitive.indexed-pipeline",
                "Indexed CorePrimitive contains a structural pipeline outside the closed native programs.",
            )
            if (structuralKey.role == org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
                mapped.identity.program !=
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil
            ) {
                return refused(
                    "invalid.native-core-primitive.indexed-direct-pipeline",
                    "Mixed indexed CorePrimitive direct draws require the neutral path depth/stencil program.",
                )
            }
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                mapped.componentIdentity,
                mapped.identity,
            )
        }

        if (!isMsaa4x) {
            return materializeSingleSampleFrameGlobalCore(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                resources = resources,
                generationSeal = generationSeal,
                renderStep = renderStep,
                renderScope = renderScope,
                route = unifiedRoute,
                readbackScope = readbackScope,
                output = output,
                targetFormat = indexedTargetFormat,
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during indexed validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val continuation = renderStep.sampleContinuation
            val msaaColorRequirement = if (isMsaa4x) {
                GPUWgpu4kCorePrimitiveMsaaColorRequirement(
                    target = renderStep.target,
                    colorAttachment = requireNotNull(continuation).key.colorAttachment,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    width = targetBounds.width,
                    height = targetBounds.height,
                    format = indexedTargetFormat.toCorePrimitiveGPUTextureFormat(),
                )
            } else {
                null
            }
            val pipelineByStructural = linkedMapOf<
                org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
                GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired
                >()
            cacheKeys.forEach { (structuralKey, cacheKey) ->
                val handles = when (val acquired = sessionCache.acquire(cacheKey)) {
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> acquired
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                        synchronized(this) { materializing = false }
                        return refusedSessionCacheAcquire(acquired.reason)
                    }
                }
                pipelineByStructural[structuralKey] = handles
            }
            val pathRequirement = GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
                targetBounds.width,
                targetBounds.height,
                GPUTextureFormat.Depth24PlusStencil8,
                sampleCount,
                GPUTextureUsage.RenderAttachment,
                target = renderStep.target.takeIf { isMsaa4x },
                depthStencilAttachment = continuation?.key?.depthStencilAttachment,
                deviceGeneration = generationSeal.deviceGeneration.takeIf { isMsaa4x },
                targetGeneration = generationSeal.targetGeneration.takeIf { isMsaa4x },
            )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    deviceGeneration = generationSeal.deviceGeneration,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    uniformBytes = uniformPlan.totalBytes,
                    pathDepthStencil = pathRequirement,
                    analyticClipBindGroupRequired = analyticClipBindGroupRequired,
                    sampleCount = sampleCount,
                    msaaColor = msaaColorRequirement,
                ),
            )) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val pathHandles = requireNotNull(pooled.handles.pathDepthStencil)
            require(
                pathHandles.requirement == pathRequirement &&
                    pooled.handles.sampleCount == sampleCount &&
                    pooled.handles.msaaColor?.requirement == msaaColorRequirement &&
                    (isMsaa4x == (pooled.handles.msaaColor != null)) &&
                    (!analyticClipBindGroupRequired ||
                        pooled.handles.analyticClipBindGroupOrNull != null),
            ) { "Pooled CorePrimitive paired path attachments differ from their exact requirements" }
            uploadExact(
                pooled.handles.vertexBuffer,
                ArrayBuffer.of(vertexData),
                vertexBytes,
                pooled.capacities.vertexBytes,
            )
            uploadExact(
                pooled.handles.indexBuffer,
                ArrayBuffer.of(indexData),
                indexBytes,
                pooled.capacities.indexBytes,
            )
            uploadExact(
                pooled.handles.uniformBuffer,
                ArrayBuffer.of(unifiedRoute.packedUniformBytesForUpload()),
                uniformPlan.totalBytes,
                pooled.capacities.uniformBytes,
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.indexed.readback",
                    ),
                ).tracked()
            }
            val canonicalTargetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val colorTargetOperand = pooled.handles.msaaColor?.let { msaa ->
                GPUPreparedNativeTextureViewOperand(
                    msaa.view,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            } ?: canonicalTargetOperand
            val depthStencilOperand = GPUPreparedNativeTextureViewOperand(
                pathHandles.view,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val vertexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.indexBytes,
            )
            val bindGroupOperand = GPUPreparedNativeBindGroupOperand(
                pooled.handles.bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val analyticClipBindGroupOperand =
                pooled.handles.analyticClipBindGroupOrNull?.let { analyticClipBindGroup ->
                    GPUPreparedNativeBindGroupOperand(
                        analyticClipBindGroup,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    )
                }
            val pipelineOperands = pipelineByStructural.mapValues { (_, handles) ->
                GPUPreparedNativeRenderPipelineOperand(
                    handles.pipeline,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            }
            val commands = buildList {
                add(
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        0L,
                        vertexBytes,
                        8L,
                    ),
                )
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        0L,
                        indexBytes,
                    ),
                )
                add(GPUPreparedNativeRenderCommand.SetStencilReference(0u))
                packetPlans.forEachIndexed { index, plan ->
                    val slice = arena.slices[index]
                    add(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[plan.structuralKey]),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            if (plan.structuralKey.uniformLayout ==
                                org.graphiks.kanvas.gpu.renderer.passes
                                    .GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout
                                    .AnalyticClipUniform64V1
                            ) {
                                requireNotNull(analyticClipBindGroupOperand)
                            } else {
                                bindGroupOperand
                            },
                            listOf(uniformPlan.slots[plan.uniformSlotIndex].alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            plan.semantic.scissorBounds.left,
                            plan.semantic.scissorBounds.top,
                            plan.semantic.scissorBounds.width,
                            plan.semantic.scissorBounds.height,
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                slice.indexCount,
                                firstIndex = slice.firstIndex,
                                baseVertex = slice.baseVertex,
                                vertexCount = slice.vertexCount,
                                maxLocalIndex = slice.maxLocalIndex,
                            ),
                        ),
                    )
                }
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = colorTargetOperand,
                    resolveTarget = canonicalTargetOperand.takeIf { isMsaa4x },
                    depthStencilTarget = depthStencilOperand,
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                    depthReadOnly = true,
                    stencilClearValue = 0u,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Discard,
                    stencilReadOnly = false,
                ),
                commands = commands,
                semanticPayloads = packetPlans.map(PacketPlan::semantic),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
                        format = indexedTargetFormat.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else {
                null
            }
            val operandsByStep = (listOf(renderOperand) + listOfNotNull(readbackOperand))
                .associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
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
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
                pathDepthStencilViewAuthority = mapOf(
                    renderScope.sourceStepIndex to pathHandles.view,
                ),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during indexed materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.materialization",
                "Public wgpu4k indexed CorePrimitive materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    /**
     * Materializes the admitted two-render destination-copy shape: the producer
     * render pass writes the destination, the ordered TextureCopy snapshots it, and the consuming
     * pass runs the dst-read formula program against the snapshot (Graphite DrawContext.cpp
     * recipe, per-pass `BindGraphicsPipeline`). Each pass acquires its own pooled run because the
     * producer and dst-read consumer project onto different bind-group component identities; the
     * ordered copy lands between the passes in the same encoder.
     */
    private fun materializeDirectMultiRenderDstCopyCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.size != 2) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct dst-copy lane requires exactly two render scopes.",
            )
        }
        val producerStep = renderSteps.first()
        val consumerStep = renderSteps.last()
        val producerIndex = framePlan.steps.indexOf(producerStep)
        val consumerIndex = framePlan.steps.indexOf(consumerStep)
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val copyStep = copySteps.singleOrNull()
            ?: return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct dst-copy lane requires one ordered destination snapshot copy.",
            )
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "The direct dst-copy lane accepts only its two renders, one ordered copy, and one optional readback.",
            )
        }
        val copyIndex = framePlan.steps.indexOf(copyStep)
        if (producerStep.target != consumerStep.target ||
            producerIndex >= copyIndex || copyIndex >= consumerIndex
        ) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct dst-copy shape requires the producer pass, the ordered snapshot copy, " +
                    "then the consuming pass on one target.",
            )
        }
        val producerScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == producerIndex && it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-core-primitive.render-plan",
            "The direct dst-copy producer render scope is absent from the encoder plan.",
        )
        val consumerScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == consumerIndex && it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-core-primitive.render-plan",
            "The direct dst-copy consumer render scope is absent from the encoder plan.",
        )
        val copyScope = encoderPlan.scopes.singleOrNull {
            it.operationKind == GPUEncoderOperationKind.CopyDestination
        } ?: return refused(
            "unsupported.native-core-primitive.destination-copy-plan",
            "The direct dst-copy snapshot copy scope is absent from the encoder plan.",
        )
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The direct dst-copy readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOf(producerScope, copyScope, consumerScope) + listOfNotNull(readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "The direct dst-copy encoder scopes must remain producer, copy, consumer, then optional readback.",
            )
        }
        val targetBounds = (producerStep.drawPackets.first().semanticPayload
            as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds ?: return refused(
            "unsupported.native-core-primitive.semantic-payload",
            "The direct dst-copy producer requires one typed CorePrimitive semantic payload.",
        )
        if (consumerStep.drawPackets.any { packet ->
                (packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds != targetBounds
            }
        ) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct dst-copy passes must share one exact target bounds.",
            )
        }
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(producerStep.target) ?: return refused(
            "unsupported.native-core-primitive.target-contract",
            "CorePrimitive requires one exact supported scene target.",
        )
        val declaredTargetFormat = declaredTargetDescriptor.format
        if (declaredTargetFormat.corePrimitiveInterpretationOrNull() == null) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        }
        val copyAuthority = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = listOf(producerStep, consumerStep),
                targetBounds = targetBounds,
                targetFormat = declaredTargetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities.singleOrNull()
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        } ?: return refused(
            "invalid.native-core-primitive.multi-render-shape",
            "The direct dst-copy shape requires its ordered destination snapshot authority.",
        )
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional direct dst-copy readback must match one output-owned staging lease.",
            )
        }
        val stagingPreparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.role == GPUFrameResourceRole.ReadbackStaging }
        if (stagingPreparations.size != if (readbackStep == null) 0 else 1) {
            return refused(
                "unsupported.native-core-primitive.readback-staging",
                "The optional direct dst-copy readback must have exactly one staging preparation.",
            )
        }
        val stagingPreparation = stagingPreparations.singleOrNull()
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != producerStep.target ||
                readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned direct dst-copy RGBA8 readback layout is not exact.",
                )
            }
        }
        if (preparedSceneTarget.width != targetBounds.width ||
            preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed CorePrimitive dst-copy target.",
            )
        }

        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        fun buildRunPlan(
            step: GPUFrameStep.RenderPassStep,
            scope: GPUCommandEncoderScopePlan,
            label: String,
        ): GPUCorePrimitiveRenderRunPlan {
            val unifiedRoute = scope.corePrimitiveNativeScopeRouteSeal as?
                GPUCorePrimitiveNativeScopeRouteSeal.Routes ?: throw IllegalArgumentException(
                "$label scope lost its unified CorePrimitive route seal",
            )
            return GPUCorePrimitiveRenderRunPlan(
                sourceScopeIndices = listOf(scope.sourceStepIndex),
                packetIds = step.drawPackets.map { packet -> packet.packetId },
                renderStep = step,
                preparationRequests = step.resourceUses.map { use ->
                    preparationByResource.getValue(use.resource)
                },
                resourceEvidences = step.resourceUses.map { use ->
                    evidenceByResource.getValue(use.resource)
                },
                routeSeal = unifiedRoute,
                exactScopeKey = GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                ),
            )
        }
        val producerPlan = try {
            buildRunPlan(producerStep, producerScope, "producer")
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The direct dst-copy producer run cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }
        val consumerPlan = try {
            buildRunPlan(consumerStep, consumerScope, "consumer")
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The direct dst-copy consumer run cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed after full preflight.",
                )
            }
            materializing = true
        }

        var producerLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var consumerLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var leaseTransferred = false
        val runMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
            queue,
            sessionCache,
            limits,
        )
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val dstRead = createCorePrimitiveDestinationSnapshot(
                copyAuthority,
                declaredTargetFormat,
                targetBounds,
            )
            val producerReady = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(producerPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            producerLifecycle = producerReady.leaseLifecycle
            val consumerReady = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(consumerPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                    dstRead = dstRead,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    if (producerLifecycle?.releaseBeforeSubmit() !is
                        GPUPreparedNativeFrameLeaseTransition.Applied
                    ) {
                        producerLifecycle?.quarantineUncertain()
                    }
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            consumerLifecycle = consumerReady.leaseLifecycle
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.dstCopy.readback",
                    ),
                ).tracked()
            }
            val readbackOperand =
                if (readbackScope != null && output != null && stagingBuffer != null) {
                    GPUPreparedNativeScopeOperand.Readback(
                        sourceStepIndex = readbackScope.sourceStepIndex,
                        source = GPUPreparedNativeTextureOperand(
                            targetTexture,
                            generationSeal.deviceGeneration,
                            GPUPreparedNativeOperandOwnership.Borrowed,
                        ),
                        destination = GPUPreparedNativeBufferOperand(
                            stagingBuffer,
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
                            format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                        ),
                    )
                } else {
                    null
                }
            val copyOperand = GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = copyScope.sourceStepIndex,
                operationKind = GPUEncoderOperationKind.CopyDestination,
                source = GPUPreparedNativeTextureOperand(
                    targetTexture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                destination = GPUPreparedNativeTextureOperand(
                    dstRead.texture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = targetBounds.left,
                    sourceOriginY = targetBounds.top,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = targetBounds.width,
                    height = targetBounds.height,
                ),
            )
            val operandsByStep = (
                producerReady.renderOperands +
                    listOf(copyOperand) +
                    consumerReady.renderOperands +
                    listOfNotNull(readbackOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val exactScopeKeys = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = exactScopeKeys,
                ),
                scopeOperands = exactScopeKeys.map { scope ->
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
                auxiliaryOwnedHandles = dstRead.payloadOwnedAuxiliaryHandles(),
                leaseLifecycle = GPUPreparedNativeCompositeFrameLeaseLifecycle(
                    listOf(producerReady.leaseLifecycle, consumerReady.leaseLifecycle),
                ),
                pathDepthStencilViewAuthority =
                    producerReady.pathDepthStencilViewAuthority + consumerReady.pathDepthStencilViewAuthority,
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) {
                    "Native CorePrimitive materializer closed during dst-copy materialization"
                }
                preRegistrationHandles.transferAll()
                materializing = false
                leaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!leaseTransferred) {
                val lifecycles = listOfNotNull(producerLifecycle, consumerLifecycle)
                if (lifecycles.any { lifecycle ->
                        lifecycle.releaseBeforeSubmit() !is
                            GPUPreparedNativeFrameLeaseTransition.Applied
                    }
                ) {
                    lifecycles.forEach { lifecycle -> lifecycle.quarantineUncertain() }
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.multi-render-dst-copy-materialization",
                "Public wgpu4k direct dst-copy CorePrimitive assembly failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        } finally {
            runMaterializer.close()
        }
    }

    /**
     * Materializes a direct sequence of render/copy/render segments. Render plans are batched by
     * destination snapshot resource so independent uniform authorities share one pooled run where
     * their bind-group topology permits it; ordered copies share native snapshot handles by
     * logical snapshot resource.
     */
    private fun materializeDirectMultiRenderDestinationCopySequenceCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.size < 2) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct destination-copy sequence requires at least two render scopes.",
            )
        }
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        if (copySteps.isEmpty()) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct destination-copy sequence requires at least one ordered snapshot copy.",
            )
        }
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { step ->
                step is GPUFrameStep.CopyResourceStep ||
                    step is GPUFrameStep.CopyAsDrawMaterializationStep
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "The direct destination-copy sequence accepts only render scopes, ordered copies, " +
                    "and one optional readback.",
            )
        }

        val target = renderSteps.first().target
        val renderStepIndices = renderSteps.map(framePlan.steps::indexOf)
        if (renderSteps.any { step ->
                step.target != target ||
                    step.depthStencilLoadStore != null ||
                    step.drawPackets.isEmpty() ||
                    step.drawPackets.any { packet -> packet.role != GPUDrawPacketRole.Shading }
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.dedicated-multi-pass-route",
                "Path-stencil and other depth/stencil CorePrimitive scopes require their dedicated " +
                    "native routes.",
            )
        }
        val targetBounds = (renderSteps.first().drawPackets.first().semanticPayload
            as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds ?: return refused(
            "unsupported.native-core-primitive.semantic-payload",
            "The direct destination-copy sequence requires typed CorePrimitive semantic payloads.",
        )
        if (renderSteps.any { render ->
                render.drawPackets.any { packet ->
                    (packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds !=
                        targetBounds
                }
            }
        ) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The direct destination-copy sequence must share one exact target bounds.",
            )
        }
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(target) ?: return refused(
            "unsupported.native-core-primitive.target-contract",
            "CorePrimitive requires one exact supported scene target.",
        )
        val declaredTargetFormat = declaredTargetDescriptor.format
        if (declaredTargetFormat.corePrimitiveInterpretationOrNull() == null) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        }

        val authorities = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = renderSteps,
                targetBounds = targetBounds,
                targetFormat = declaredTargetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        }
        if (authorities.size != copySteps.size) {
            return refused(
                "invalid.native-core-primitive.destination-copy-shape",
                "The direct destination-copy sequence must retain one authority per copy step.",
            )
        }
        val authorityByConsumerRenderIndex = linkedMapOf<Int, CorePrimitiveDestinationCopyAuthority>()
        authorities.forEach { authority ->
            val consumer = authority.step.consumers.single()
            val consumerRender = renderSteps.singleOrNull { render ->
                render.drawPackets.any { packet -> packet.packetId == consumer.packetId }
            } ?: return refused(
                "invalid.native-core-primitive.destination-copy-consumer",
                "The destination snapshot consumer must resolve to one exact render scope.",
            )
            val consumerIndex = framePlan.steps.indexOf(consumerRender)
            if (authorityByConsumerRenderIndex.put(consumerIndex, authority) != null) {
                return refused(
                    "unsupported.native-core-primitive.destination-copy-shape",
                    "A direct CorePrimitive render run cannot bind more than one destination snapshot.",
                )
            }
            val copyIndex = framePlan.steps.indexOf(authority.step)
            val producerIndex = renderStepIndices.lastOrNull { it < copyIndex }
            if (producerIndex == null || producerIndex >= copyIndex || copyIndex >= consumerIndex) {
                return refused(
                    "invalid.native-core-primitive.multi-render-shape",
                    "Each destination snapshot copy must occur between a producer render and its consumer.",
                )
            }
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The direct destination-copy sequence readback scope is absent from the encoder plan.",
            )
        }
        if (readbackStep != null && framePlan.steps.indexOf(readbackStep) <= renderStepIndices.last()) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "The optional direct destination-copy readback must follow the final render.",
            )
        }
        val authorityByCopyStepIndex = authorities.associateBy { authority ->
            framePlan.steps.indexOf(authority.step)
        }
        val renderScopeByStepIndex = renderStepIndices.associateWith { sourceStepIndex ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == sourceStepIndex &&
                    it.operationKind == GPUEncoderOperationKind.Render
            } ?: return refused(
                "unsupported.native-core-primitive.render-plan",
                "A direct destination-copy sequence render scope is absent from the encoder plan.",
            )
        }
        val expectedScopes = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.RenderPassStep -> renderScopeByStepIndex[index]
                is GPUFrameStep.CopyDestinationStep -> authorityByCopyStepIndex[index]?.copyScope
                is GPUFrameStep.ReadbackCopyStep -> readbackScope
                is GPUFrameStep.PrepareResourcesStep,
                is GPUFrameStep.DependencyBarrierStep,
                is GPUFrameStep.TargetTransitionStep,
                is GPUFrameStep.AcquireSurfaceOutput,
                is GPUFrameStep.PostSubmitPresentAction,
                -> null
                else -> return refused(
                    "unsupported.native-core-primitive.scope-shape",
                    "The direct destination-copy sequence contains an unsupported frame step.",
                )
            }
        }
        if (encoderPlan.scopes != expectedScopes) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "The direct destination-copy sequence must preserve render/copy/readback step order.",
            )
        }

        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional direct destination-copy readback must match one output-owned staging lease.",
            )
        }
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val preparationByResource = preparations.associateBy(GPUResourcePreparationRequest::resource)
        val stagingPreparations = preparations.filter { request ->
            request.role == GPUFrameResourceRole.ReadbackStaging
        }
        if (stagingPreparations.size != if (readbackStep == null) 0 else 1) {
            return refused(
                "unsupported.native-core-primitive.readback-staging",
                "The optional direct destination-copy readback must have exactly one staging preparation.",
            )
        }
        val stagingPreparation = stagingPreparations.singleOrNull()
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != target ||
                readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request ||
                output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The direct destination-copy sequence RGBA8 readback layout is not exact.",
                )
            }
        }
        if (preparedSceneTarget.width != targetBounds.width ||
            preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed direct destination-copy target.",
            )
        }

        val snapshotResources = authorities.map { authority ->
            authority.snapshotPreparation.resource
        }.distinct()
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        val expectedOrdinaryResources = preparations
            .filter { request -> request.role != GPUFrameResourceRole.ReadbackStaging }
            .map(GPUResourcePreparationRequest::resource)
            .toSet()
        if (resources.ordinaryResources.size != expectedOrdinaryResources.size ||
            resources.ordinaryResources.map(GPUPreparedResourceEvidence::logicalResource).toSet() !=
                expectedOrdinaryResources ||
            snapshotResources.any { resource ->
                val preparation = preparationByResource[resource]
                val evidence = evidenceByResource[resource]
                preparation == null || evidence == null ||
                    evidence.role != GPUFrameResourceRole.DestinationSnapshot ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[resource] ||
                    evidence.concreteResource !is GPUPreparedConcreteResourceRef.Texture
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-resources",
                "Direct destination-copy snapshots require exact prepared frame-local texture evidence.",
            )
        }
        val runPlans = renderSteps.map { step ->
            val sourceStepIndex = framePlan.steps.indexOf(step)
            val scope = renderScopeByStepIndex.getValue(sourceStepIndex)
            val unifiedRoute = scope.corePrimitiveNativeScopeRouteSeal as?
                GPUCorePrimitiveNativeScopeRouteSeal.Routes ?: return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "A direct destination-copy sequence scope lost its unified CorePrimitive route seal.",
            )
            try {
                GPUCorePrimitiveRenderRunPlan(
                    sourceScopeIndices = listOf(sourceStepIndex),
                    packetIds = step.drawPackets.map { packet -> packet.packetId },
                    renderStep = step,
                    preparationRequests = step.resourceUses.map { use ->
                        preparationByResource.getValue(use.resource)
                    },
                    resourceEvidences = step.resourceUses.map { use ->
                        evidenceByResource.getValue(use.resource)
                    },
                    routeSeal = unifiedRoute,
                    exactScopeKey = GPUPreparedNativeScopeKey(
                        scope.sourceStepIndex,
                        scope.operationKind,
                        scope.resourceGenerationLabels,
                        scope.nativeOperandKeys,
                    ),
                )
            } catch (failure: Throwable) {
                return refused(
                    "invalid.native-core-primitive.frame-global-plan",
                    "A direct destination-copy run cannot form its frame-global plan: " +
                        "${failure::class.simpleName.orEmpty()}.",
                )
            }
        }
        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed after full preflight.",
                )
            }
            materializing = true
        }

        var leases: List<GPUPreparedNativeFrameLeaseLifecycle> = emptyList()
        var leaseTransferred = false
        val runMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
            queue,
            sessionCache,
            limits,
        )
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val snapshotHandlesByResource = linkedMapOf<GPUFrameResourceRef, CorePrimitiveDestinationSnapshotHandles>()
            authorities.forEach { authority ->
                snapshotHandlesByResource.getOrPut(authority.snapshotPreparation.resource) {
                    createCorePrimitiveDestinationSnapshot(
                        authority,
                        declaredTargetFormat,
                        targetBounds,
                    )
                }
            }
            val readyPerPlan = mutableListOf<GPUCorePrimitiveRenderRunMaterialization.Ready>()
            val runLifecycles = mutableListOf<GPUPreparedNativeFrameLeaseLifecycle>()
            val runBatches = runPlans.groupBy { plan ->
                authorityByConsumerRenderIndex[plan.sourceScopeIndices.single()]
                    ?.snapshotPreparation
                    ?.resource
            }
            runBatches.forEach { (snapshotResource, batchPlans) ->
                val dstRead = snapshotResource?.let(snapshotHandlesByResource::getValue)
                when (
                    val result = runMaterializer.materializeAcceptedRuns(
                        plans = batchPlans,
                        targetTexture = targetTexture,
                        targetView = targetView,
                        generationSeal = generationSeal,
                        dstRead = dstRead,
                    )
                ) {
                    is GPUCorePrimitiveRenderRunMaterialization.Ready -> {
                        readyPerPlan += result
                        runLifecycles += result.leaseLifecycle
                    }
                    is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                        if (runLifecycles.any { lifecycle ->
                                lifecycle.releaseBeforeSubmit() !is
                                    GPUPreparedNativeFrameLeaseTransition.Applied
                            }
                        ) {
                            runLifecycles.forEach { lifecycle -> lifecycle.quarantineUncertain() }
                        }
                        synchronized(this) {
                            materializing = false
                            preRegistrationHandles.closeRetainingFailures()
                        }
                        return refused(result.code, result.message)
                    }
                }
            }
            leases = runLifecycles
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.destinationCopySequence.readback",
                    ),
                ).tracked()
            }
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
                        format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                    ),
                )
            } else {
                null
            }
            val copyOperands = authorities.map { authority ->
                val snapshot = snapshotHandlesByResource.getValue(authority.snapshotPreparation.resource)
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = authority.copyScope.sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        snapshot.texture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = authority.step.logicalBounds.left,
                        sourceOriginY = authority.step.logicalBounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = authority.step.logicalBounds.width,
                        height = authority.step.logicalBounds.height,
                    ),
                )
            }
            val operandsByStep = (
                readyPerPlan.flatMap { ready -> ready.renderOperands } +
                    copyOperands +
                    listOfNotNull(readbackOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val exactScopeKeys = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = exactScopeKeys,
                ),
                scopeOperands = exactScopeKeys.map { scope ->
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
                auxiliaryOwnedHandles = snapshotHandlesByResource.values.flatMap {
                    it.payloadOwnedAuxiliaryHandles()
                },
                leaseLifecycle = combineCorePrimitiveLeaseLifecycles(leases),
                pathDepthStencilViewAuthority = readyPerPlan.flatMap {
                    it.pathDepthStencilViewAuthority.entries
                }.associate { it.toPair() },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) {
                    "Native CorePrimitive materializer closed during destination-copy sequence materialization"
                }
                preRegistrationHandles.transferAll()
                materializing = false
                leaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!leaseTransferred) {
                if (leases.any { lifecycle ->
                        lifecycle.releaseBeforeSubmit() !is
                            GPUPreparedNativeFrameLeaseTransition.Applied
                    }
                ) {
                    leases.forEach { lifecycle -> lifecycle.quarantineUncertain() }
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.multi-render-destination-copy-sequence-materialization",
                "Public wgpu4k direct destination-copy sequence assembly failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        } finally {
            runMaterializer.close()
        }
    }

    /**
     * Materializes the continued path dst-read shape — background render, producer
     * render (fan Clear+Store), ordered snapshot copy, cover render (fan read-only + dst-read).
     * The producer and cover runs share one frame-local path D24S8 (created here, not per-run in
     * the pool) so the cover tests the exact fan the producer stored; the background owns its own
     * standard pooled run.
     */
    private fun materializeContinuedPathDstReadCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.size != 3) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The continued path dst-read lane requires exactly three render scopes.",
            )
        }
        val backgroundStep = renderSteps[0]
        val producerStep = renderSteps[1]
        val coverStep = renderSteps[2]
        val producerIndex = framePlan.steps.indexOf(producerStep)
        val coverIndex = framePlan.steps.indexOf(coverStep)
        val copySteps = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val copyStep = copySteps.singleOrNull()
            ?: return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The continued path dst-read lane requires one ordered destination snapshot copy.",
            )
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "The continued path dst-read lane accepts only its three renders, one ordered copy, and one optional readback.",
            )
        }
        val copyIndex = framePlan.steps.indexOf(copyStep)
        if (backgroundStep.target != producerStep.target ||
            producerStep.target != coverStep.target ||
            producerIndex >= copyIndex || copyIndex >= coverIndex
        ) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The continued path dst-read shape requires the background, producer, ordered snapshot copy, then cover on one target.",
            )
        }
        fun renderScope(step: GPUFrameStep.RenderPassStep): GPUCommandEncoderScopePlan =
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Render
            } ?: throw IllegalArgumentException("continued path dst-read render scope is absent")
        val backgroundScope = try {
            renderScope(backgroundStep)
        } catch (_: IllegalArgumentException) {
            return refused(
                "unsupported.native-core-primitive.render-plan",
                "The continued path dst-read render scope is absent from the encoder plan.",
            )
        }
        val producerScope = try {
            renderScope(producerStep)
        } catch (_: IllegalArgumentException) {
            return refused(
                "unsupported.native-core-primitive.render-plan",
                "The continued path dst-read render scope is absent from the encoder plan.",
            )
        }
        val coverScope = try {
            renderScope(coverStep)
        } catch (_: IllegalArgumentException) {
            return refused(
                "unsupported.native-core-primitive.render-plan",
                "The continued path dst-read render scope is absent from the encoder plan.",
            )
        }
        val copyScope = encoderPlan.scopes.singleOrNull {
            it.operationKind == GPUEncoderOperationKind.CopyDestination
        } ?: return refused(
            "unsupported.native-core-primitive.destination-copy-plan",
            "The continued path dst-read snapshot copy scope is absent from the encoder plan.",
        )
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The continued path dst-read readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOf(backgroundScope, producerScope, copyScope, coverScope) +
            listOfNotNull(readbackScope)
        ) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "The continued path dst-read scopes must remain background, producer, copy, cover, then optional readback.",
            )
        }
        val targetBounds = (producerStep.drawPackets.first().semanticPayload
            as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds ?: return refused(
            "unsupported.native-core-primitive.semantic-payload",
            "The continued path dst-read producer requires one typed CorePrimitive semantic payload.",
        )
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(producerStep.target) ?: return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        val declaredTargetFormat = declaredTargetDescriptor.format
        if (declaredTargetFormat.corePrimitiveInterpretationOrNull() == null) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        }
        val copyAuthority = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = listOf(backgroundStep, producerStep, coverStep),
                targetBounds = targetBounds,
                targetFormat = declaredTargetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities.singleOrNull()
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        } ?: return refused(
            "invalid.native-core-primitive.multi-render-shape",
            "The continued path dst-read shape requires its ordered destination snapshot authority.",
        )
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional continued path dst-read readback must match one output-owned staging lease.",
            )
        }
        val stagingPreparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.role == GPUFrameResourceRole.ReadbackStaging }
        if (stagingPreparations.size != if (readbackStep == null) 0 else 1) {
            return refused(
                "unsupported.native-core-primitive.readback-staging",
                "The optional continued path dst-read readback must have exactly one staging preparation.",
            )
        }
        val stagingPreparation = stagingPreparations.singleOrNull()
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != producerStep.target ||
                readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request ||
                output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width ||
                output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned continued path dst-read RGBA8 readback layout is not exact.",
                )
            }
        }
        if (preparedSceneTarget.width != targetBounds.width ||
            preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed CorePrimitive dst-copy target.",
            )
        }

        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        fun buildRunPlan(
            step: GPUFrameStep.RenderPassStep,
            scope: GPUCommandEncoderScopePlan,
            label: String,
        ): GPUCorePrimitiveRenderRunPlan {
            val unifiedRoute = scope.corePrimitiveNativeScopeRouteSeal as?
                GPUCorePrimitiveNativeScopeRouteSeal.Routes ?: throw IllegalArgumentException(
                "$label scope lost its unified CorePrimitive route seal",
            )
            return GPUCorePrimitiveRenderRunPlan(
                sourceScopeIndices = listOf(scope.sourceStepIndex),
                packetIds = step.drawPackets.map { packet -> packet.packetId },
                renderStep = step,
                preparationRequests = step.resourceUses.map { use ->
                    preparationByResource.getValue(use.resource)
                },
                resourceEvidences = step.resourceUses.map { use ->
                    evidenceByResource.getValue(use.resource)
                },
                routeSeal = unifiedRoute,
                exactScopeKey = GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                ),
            )
        }
        val backgroundPlan = try {
            buildRunPlan(backgroundStep, backgroundScope, "background")
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The continued path dst-read background run cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }
        val producerPlan = try {
            buildRunPlan(producerStep, producerScope, "producer")
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The continued path dst-read producer run cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }
        val coverPlan = try {
            buildRunPlan(coverStep, coverScope, "cover")
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The continued path dst-read cover run cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed after full preflight.",
                )
            }
            materializing = true
        }
        var backgroundLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var producerLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var coverLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var leaseTransferred = false
        val runMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
            queue,
            sessionCache,
            limits,
        )
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val dstRead = createCorePrimitiveDestinationSnapshot(
                copyAuthority,
                declaredTargetFormat,
                targetBounds,
            )
            val pathDepthStencilTexture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt(), 1u),
                    format = GPUTextureFormat.Depth24PlusStencil8,
                    usage = GPUTextureUsage.RenderAttachment,
                    label = "Kanvas.frame.corePrimitive.pathDepthStencil",
                ),
            ).tracked()
            val pathDepthStencilView = pathDepthStencilTexture.createView().tracked()
            val backgroundReady = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(backgroundPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            backgroundLifecycle = backgroundReady.leaseLifecycle
            val producerReady = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(producerPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                    pathDepthStencilView = pathDepthStencilView,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    if (backgroundLifecycle?.releaseBeforeSubmit() !is
                        GPUPreparedNativeFrameLeaseTransition.Applied
                    ) {
                        backgroundLifecycle?.quarantineUncertain()
                    }
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            producerLifecycle = producerReady.leaseLifecycle
            val coverReady = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(coverPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                    dstRead = dstRead,
                    pathDepthStencilView = pathDepthStencilView,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    listOfNotNull(backgroundLifecycle, producerLifecycle).forEach { lifecycle ->
                        if (lifecycle.releaseBeforeSubmit() !is
                            GPUPreparedNativeFrameLeaseTransition.Applied
                        ) {
                            lifecycle.quarantineUncertain()
                        }
                    }
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            coverLifecycle = coverReady.leaseLifecycle
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.continuedPathDstRead.readback",
                    ),
                ).tracked()
            }
            val readbackOperand =
                if (readbackScope != null && output != null && stagingBuffer != null) {
                    GPUPreparedNativeScopeOperand.Readback(
                        sourceStepIndex = readbackScope.sourceStepIndex,
                        source = GPUPreparedNativeTextureOperand(
                            targetTexture,
                            generationSeal.deviceGeneration,
                            GPUPreparedNativeOperandOwnership.Borrowed,
                        ),
                        destination = GPUPreparedNativeBufferOperand(
                            stagingBuffer,
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
                            format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                        ),
                    )
                } else {
                    null
                }
            val copyOperand = GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = copyScope.sourceStepIndex,
                operationKind = GPUEncoderOperationKind.CopyDestination,
                source = GPUPreparedNativeTextureOperand(
                    targetTexture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                destination = GPUPreparedNativeTextureOperand(
                    dstRead.texture,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                ),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = targetBounds.left,
                    sourceOriginY = targetBounds.top,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = targetBounds.width,
                    height = targetBounds.height,
                ),
            )
            val operandsByStep = (
                backgroundReady.renderOperands +
                    producerReady.renderOperands +
                    listOf(copyOperand) +
                    coverReady.renderOperands +
                    listOfNotNull(readbackOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val exactScopeKeys = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = exactScopeKeys,
                ),
                scopeOperands = exactScopeKeys.map { scope ->
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
                auxiliaryOwnedHandles = dstRead.payloadOwnedAuxiliaryHandles(),
                leaseLifecycle = GPUPreparedNativeCompositeFrameLeaseLifecycle(
                    listOf(
                        backgroundReady.leaseLifecycle,
                        producerReady.leaseLifecycle,
                        coverReady.leaseLifecycle,
                    ),
                ),
                pathDepthStencilViewAuthority =
                    producerReady.pathDepthStencilViewAuthority + coverReady.pathDepthStencilViewAuthority,
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) {
                    "Native CorePrimitive materializer closed during continued path dst-read materialization"
                }
                preRegistrationHandles.transferAll()
                materializing = false
                leaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!leaseTransferred) {
                val lifecycles = listOfNotNull(backgroundLifecycle, producerLifecycle, coverLifecycle)
                if (lifecycles.any { lifecycle ->
                        lifecycle.releaseBeforeSubmit() !is
                            GPUPreparedNativeFrameLeaseTransition.Applied
                    }
                ) {
                    lifecycles.forEach { lifecycle -> lifecycle.quarantineUncertain() }
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.continued-path-dst-read-materialization",
                "Public wgpu4k continued path dst-read CorePrimitive assembly failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        } finally {
            runMaterializer.close()
        }
    }

    /**
     * Materializes the layout-split direct shape (N render passes, no ordered
     * destination copy). Independent per-render uniform authorities are packed into one pooled
     * run; the render operands land in step order with one optional trailing readback.
     */
    private fun materializeDirectMultiRenderSplitCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        if (renderSteps.size < 2) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The layout-split lane requires at least two render scopes.",
            )
        }
        if (framePlan.steps.any { step ->
                step is GPUFrameStep.CopyDestinationStep ||
                    step is GPUFrameStep.CopyResourceStep ||
                    step is GPUFrameStep.CopyAsDrawMaterializationStep
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "The layout-split lane accepts only its render scopes and one optional readback.",
            )
        }
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "The layout-split lane accepts only its render scopes and one optional readback.",
            )
        }
        if (renderSteps.map { it.target }.distinct().size != 1) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The layout-split passes must share one exact scene target.",
            )
        }
        val targetBounds = (renderSteps.first().drawPackets.first().semanticPayload
            as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds ?: return refused(
            "unsupported.native-core-primitive.semantic-payload",
            "The layout-split first render requires one typed CorePrimitive semantic payload.",
        )
        if (renderSteps.any { render ->
                render.drawPackets.any { packet ->
                    (packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)?.targetBounds !=
                        targetBounds
                }
            }
        ) {
            return refused(
                "invalid.native-core-primitive.multi-render-shape",
                "The layout-split passes must share one exact target bounds.",
            )
        }
        val declaredTargetDescriptor =
            framePlan.corePrimitiveSceneTargetDescriptor(renderSteps.first().target) ?: return refused(
            "unsupported.native-core-primitive.target-contract",
            "CorePrimitive requires one exact supported scene target.",
        )
        val declaredTargetFormat = declaredTargetDescriptor.format
        if (declaredTargetFormat.corePrimitiveInterpretationOrNull() == null) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact supported scene target.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional layout-split readback must match one output-owned staging lease.",
            )
        }
        val stagingPreparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.role == GPUFrameResourceRole.ReadbackStaging }
        if (stagingPreparations.size != if (readbackStep == null) 0 else 1) {
            return refused(
                "unsupported.native-core-primitive.readback-staging",
                "The optional layout-split readback must have exactly one staging preparation.",
            )
        }
        val stagingPreparation = stagingPreparations.singleOrNull()
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != renderSteps.last().target ||
                readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned layout-split RGBA8 readback layout is not exact.",
                )
            }
        }
        if (preparedSceneTarget.width != targetBounds.width ||
            preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed layout-split target.",
            )
        }

        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        val runPlans = renderSteps.map { step ->
            val scope = encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Render
            } ?: return refused(
                "unsupported.native-core-primitive.render-plan",
                "The layout-split render scope is absent from the encoder plan.",
            )
            val unifiedRoute = scope.corePrimitiveNativeScopeRouteSeal as?
                GPUCorePrimitiveNativeScopeRouteSeal.Routes ?: throw IllegalArgumentException(
                "Layout-split scope lost its unified CorePrimitive route seal",
            )
            try {
                GPUCorePrimitiveRenderRunPlan(
                    sourceScopeIndices = listOf(scope.sourceStepIndex),
                    packetIds = step.drawPackets.map { packet -> packet.packetId },
                    renderStep = step,
                    preparationRequests = step.resourceUses.map { use ->
                        preparationByResource.getValue(use.resource)
                    },
                    resourceEvidences = step.resourceUses.map { use ->
                        evidenceByResource.getValue(use.resource)
                    },
                    routeSeal = unifiedRoute,
                    exactScopeKey = GPUPreparedNativeScopeKey(
                        scope.sourceStepIndex,
                        scope.operationKind,
                        scope.resourceGenerationLabels,
                        scope.nativeOperandKeys,
                    ),
                )
            } catch (failure: Throwable) {
                return refused(
                    "invalid.native-core-primitive.frame-global-plan",
                    "A layout-split run cannot form its frame-global plan: " +
                        "${failure::class.simpleName.orEmpty()}.",
                )
            }
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The layout-split readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != runPlans.map { plan ->
                encoderPlan.scopes.single { it.sourceStepIndex == plan.sourceScopeIndices.single() }
            } + listOfNotNull(readbackScope)
        ) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "The layout-split encoder scopes must remain render passes in step order then the optional readback.",
            )
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed after full preflight.",
                )
            }
            materializing = true
        }

        var leases: List<GPUPreparedNativeFrameLeaseLifecycle> = emptyList()
        var leaseTransferred = false
        val runMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
            queue,
            sessionCache,
            limits,
        )
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.layoutSplit.readback",
                    ),
                ).tracked()
            }
            val readbackOperand =
                if (readbackScope != null && output != null && stagingBuffer != null) {
                    GPUPreparedNativeScopeOperand.Readback(
                        sourceStepIndex = readbackScope.sourceStepIndex,
                        source = GPUPreparedNativeTextureOperand(
                            targetTexture,
                            generationSeal.deviceGeneration,
                            GPUPreparedNativeOperandOwnership.Borrowed,
                        ),
                        destination = GPUPreparedNativeBufferOperand(
                            stagingBuffer,
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
                            format = declaredTargetFormat.toCorePrimitiveGPUTextureFormat(),
                        ),
                    )
                } else {
                    null
                }
            // A batch refusal is returned before ownership transfer so the outer catch can
            // restore the materializer ledger and release any resources created for the batch.
            val readyPerPlan = mutableListOf<GPUCorePrimitiveRenderRunMaterialization.Ready>()
            val runLifecycles = mutableListOf<GPUPreparedNativeFrameLeaseLifecycle>()
            when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = runPlans,
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> {
                    readyPerPlan += result
                    runLifecycles += result.leaseLifecycle
                }
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            leases = runLifecycles
            val operandsByStep = (
                readyPerPlan.flatMap { it.renderOperands } +
                    listOfNotNull(readbackOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val exactScopeKeys = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = exactScopeKeys,
                ),
                scopeOperands = exactScopeKeys.map { scope ->
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
                leaseLifecycle = combineCorePrimitiveLeaseLifecycles(leases),
                pathDepthStencilViewAuthority = readyPerPlan.flatMap { it.pathDepthStencilViewAuthority.entries }
                    .associate { it.toPair() },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) {
                    "Native CorePrimitive materializer closed during layout-split materialization"
                }
                preRegistrationHandles.transferAll()
                materializing = false
                leaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!leaseTransferred) {
                if (leases.any { lifecycle ->
                        lifecycle.releaseBeforeSubmit() !is
                            GPUPreparedNativeFrameLeaseTransition.Applied
                    }
                ) {
                    leases.forEach { lifecycle -> lifecycle.quarantineUncertain() }
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.multi-render-split-materialization",
                "Public wgpu4k layout-split CorePrimitive assembly failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        } finally {
            runMaterializer.close()
        }
    }

    private fun materializeSingleSampleFrameGlobalCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
        renderStep: GPUFrameStep.RenderPassStep,
        renderScope: GPUCommandEncoderScopePlan,
        route: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
        readbackScope: GPUCommandEncoderScopePlan?,
        output: GPUPreparedReadbackOutput?,
        targetFormat: GPUColorFormat,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val targetBounds = (renderStep.drawPackets.first().semanticPayload
            as GPUDrawSemanticPayload.CorePrimitive).targetBounds
        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        val exactScopeKey = GPUPreparedNativeScopeKey(
            renderScope.sourceStepIndex,
            renderScope.operationKind,
            renderScope.resourceGenerationLabels,
            renderScope.nativeOperandKeys,
        )
        val acceptedPlan = try {
            GPUCorePrimitiveRenderRunPlan(
                sourceScopeIndices = listOf(renderScope.sourceStepIndex),
                packetIds = renderStep.drawPackets.map { packet -> packet.packetId },
                renderStep = renderStep,
                preparationRequests = renderStep.resourceUses.map { use ->
                    preparationByResource.getValue(use.resource)
                },
                resourceEvidences = renderStep.resourceUses.map { use ->
                    evidenceByResource.getValue(use.resource)
                },
                routeSeal = route,
                exactScopeKey = exactScopeKey,
            )
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "The fully preflighted pure CorePrimitive route cannot form its frame-global plan: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }
        val copyAuthority = when (
            val validation = validateCorePrimitiveDestinationCopy(
                framePlan = framePlan,
                encoderPlan = encoderPlan,
                renderSteps = listOf(renderStep),
                targetBounds = targetBounds,
                targetFormat = targetFormat,
                targetGeneration = generationSeal.targetGeneration,
            )
        ) {
            is CorePrimitiveDestinationCopyValidation.Accepted -> validation.authorities.singleOrNull()
            is CorePrimitiveDestinationCopyValidation.Refused ->
                return refused(validation.code, validation.message)
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed after full preflight.",
                )
            }
            materializing = true
        }

        var leaseLifecycle: GPUPreparedNativeFrameLeaseLifecycle? = null
        var leaseTransferred = false
        val runMaterializer = GPUWgpu4kCorePrimitiveRenderRunMaterializer(
            queue,
            sessionCache,
            limits,
        )
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val dstRead = copyAuthority?.let { authority ->
                createCorePrimitiveDestinationSnapshot(
                    authority,
                    targetFormat,
                    targetBounds,
                )
            }
            val ready = when (
                val result = runMaterializer.materializeAcceptedRuns(
                    plans = listOf(acceptedPlan),
                    targetTexture = targetTexture,
                    targetView = targetView,
                    generationSeal = generationSeal,
                    dstRead = dstRead,
                )
            ) {
                is GPUCorePrimitiveRenderRunMaterialization.Ready -> result
                is GPUCorePrimitiveRenderRunMaterialization.Refused -> {
                    synchronized(this) {
                        materializing = false
                        preRegistrationHandles.closeRetainingFailures()
                    }
                    return refused(result.code, result.message)
                }
            }
            leaseLifecycle = ready.leaseLifecycle
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.frameGlobal.readback",
                    ),
                ).tracked()
            }
            val readbackOperand =
                if (readbackScope != null && output != null && stagingBuffer != null) {
                    GPUPreparedNativeScopeOperand.Readback(
                        sourceStepIndex = readbackScope.sourceStepIndex,
                        source = GPUPreparedNativeTextureOperand(
                            targetTexture,
                            generationSeal.deviceGeneration,
                            GPUPreparedNativeOperandOwnership.Borrowed,
                        ),
                        destination = GPUPreparedNativeBufferOperand(
                            stagingBuffer,
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
                            format = targetFormat.toCorePrimitiveGPUTextureFormat(),
                        ),
                    )
                } else {
                    null
                }
            val copyOperand = copyAuthority?.let { authority ->
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = authority.copyScope.sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        requireNotNull(dstRead?.texture),
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = targetBounds.left,
                        sourceOriginY = targetBounds.top,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = targetBounds.width,
                        height = targetBounds.height,
                    ),
                )
            }
            val operandsByStep = (
                listOfNotNull(copyOperand) +
                    ready.renderOperands +
                    listOfNotNull(readbackOperand)
                ).associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val exactScopeKeys = encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = exactScopeKeys,
                ),
                scopeOperands = exactScopeKeys.map { scope ->
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
                auxiliaryOwnedHandles = dstRead?.payloadOwnedAuxiliaryHandles().orEmpty(),
                leaseLifecycle = ready.leaseLifecycle,
                pathDepthStencilViewAuthority = ready.pathDepthStencilViewAuthority,
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) {
                    "Native CorePrimitive materializer closed during frame-global materialization"
                }
                preRegistrationHandles.transferAll()
                materializing = false
                leaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!leaseTransferred) {
                val lifecycle = leaseLifecycle
                if (lifecycle != null &&
                    lifecycle.releaseBeforeSubmit() !is GPUPreparedNativeFrameLeaseTransition.Applied
                ) {
                    lifecycle.quarantineUncertain()
                }
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.frame-global-wrapper-materialization",
                "Public wgpu4k frame-global CorePrimitive assembly failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        } finally {
            runMaterializer.close()
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        if (!materializing) preRegistrationHandles.closeRetainingFailures()
    }

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)

    /**
     * Validates every ordered destination snapshot in frame-step order. Each copy retains one
     * exact consumer render and one exact copy scope; callers decide whether the resulting
     * authorities can be materialized by their route-specific run shape.
     */
    private fun validateCorePrimitiveDestinationCopy(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        renderSteps: List<GPUFrameStep.RenderPassStep>,
        targetBounds: GPUPixelBounds,
        targetFormat: GPUColorFormat,
        targetGeneration: Long,
    ): CorePrimitiveDestinationCopyValidation {
        val copies = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        if (copies.isEmpty()) return CorePrimitiveDestinationCopyValidation.Accepted(emptyList())
        if (renderSteps.isEmpty()) {
            return CorePrimitiveDestinationCopyValidation.Refused(
                "invalid.native-core-primitive.destination-copy-consumer",
                "Destination snapshot consumers require one exact render scope.",
            )
        }
        val renderByPacketId = linkedMapOf<GPUDrawPacketID, GPUFrameStep.RenderPassStep>()
        renderSteps.forEach { render ->
            render.drawPackets.forEach { packet ->
                if (renderByPacketId.put(packet.packetId, render) != null) {
                    return CorePrimitiveDestinationCopyValidation.Refused(
                        "invalid.native-core-primitive.destination-copy-consumer",
                        "Destination snapshot packet ownership must be unique across render scopes.",
                    )
                }
            }
        }
        val minimumBytesPerRow = try {
            Math.multiplyExact(targetBounds.width.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return CorePrimitiveDestinationCopyValidation.Refused(
                "invalid.native-core-primitive.destination-copy-source",
                "The destination snapshot row accounting overflowed.",
            )
        }
        val expectedSnapshotBytes = try {
            Math.multiplyExact(minimumBytesPerRow, targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return CorePrimitiveDestinationCopyValidation.Refused(
                "invalid.native-core-primitive.destination-copy-resource",
                "The destination snapshot byte accounting overflowed.",
            )
        }
        val authorities = copies.map { copy ->
            val consumer = copy.consumers.singleOrNull()
                ?: return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-consumer",
                    "The ordered destination snapshot requires one exact consumer.",
                )
            val consumerRender = renderByPacketId[consumer.packetId]
                ?: return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-consumer",
                    "The destination snapshot consumer must be one exact destination-reading packet of the supplied render scopes.",
                )
            val consumerPacket = consumerRender.drawPackets.singleOrNull { packet ->
                packet.packetId == consumer.packetId
            }
            if (consumerPacket == null ||
                consumer.groupingCommandId != consumerPacket.commandIdValue.toString() ||
                consumer.renderTaskId !in consumerRender.sourceTaskIds ||
                consumer.commandId.value != consumerPacket.commandIdValue
            ) {
                return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-consumer",
                    "The destination snapshot consumer must exactly identify one destination-reading packet.",
                )
            }
            if (copy.source != consumerRender.target || copy.logicalBounds != targetBounds) {
                return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-source",
                    "The destination snapshot must copy the exact scene target bounds.",
                )
            }
            val snapshotPreparation = preparations.singleOrNull { request ->
                request.resource == copy.snapshot
            } ?: return CorePrimitiveDestinationCopyValidation.Refused(
                "invalid.native-core-primitive.destination-copy-resource",
                "The destination snapshot has no exact frame preparation.",
            )
            val snapshotDescriptor = snapshotPreparation.descriptor as? GPUFrameTextureDescriptor
            if (snapshotPreparation.role != GPUFrameResourceRole.DestinationSnapshot ||
                snapshotDescriptor == null ||
                snapshotDescriptor.logicalBounds != targetBounds ||
                snapshotDescriptor.format != targetFormat ||
                snapshotDescriptor.sampleCount != 1 ||
                snapshotPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                ) || snapshotPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                snapshotPreparation.byteSize != expectedSnapshotBytes
            ) {
                return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-resource",
                    "The destination snapshot preparation is not one exact frame-local full-target texture.",
                )
            }
            if (copy.sourceKey.target.value != consumerRender.target.value ||
                (copy.sourceKey.targetGeneration != targetGeneration &&
                    copy.sourceKey.targetGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION) ||
                copy.sourceKey.deviceGeneration != framePlan.capabilitySeal.deviceGeneration ||
                copy.sourceKey.format != targetFormat ||
                copy.sourceKey.colorInterpretation != targetFormat.corePrimitiveInterpretationOrNull() ||
                copy.sourceKey.sampleContinuation != null ||
                copy.sourceKey.sourceIntermediate != null ||
                copy.copyLayout.rowsPerImage != targetBounds.height ||
                copy.copyLayout.bytesPerRow < minimumBytesPerRow ||
                copy.copyLayout.bytesPerRow % limits.copyBytesPerRowAlignment != 0L
            ) {
                return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-source",
                    "The destination snapshot copy layout or source authority is not exact.",
                )
            }
            val copyScope = encoderPlan.scopes.singleOrNull { scope ->
                scope.sourceStepIndex == framePlan.steps.indexOf(copy) &&
                    scope.operationKind == GPUEncoderOperationKind.CopyDestination
            } ?: return CorePrimitiveDestinationCopyValidation.Refused(
                "invalid.native-core-primitive.destination-copy-plan",
                "The destination snapshot copy scope is absent from the encoder plan.",
            )
            if (framePlan.steps.indexOf(copy) >= framePlan.steps.indexOf(consumerRender)) {
                return CorePrimitiveDestinationCopyValidation.Refused(
                    "invalid.native-core-primitive.destination-copy-order",
                    "The destination snapshot copy must run before its consuming render scope.",
                )
            }
            CorePrimitiveDestinationCopyAuthority(
                copy,
                snapshotPreparation,
                snapshotDescriptor,
                copyScope,
            )
        }
        val consumerRenderIndices = authorities.map { authority ->
            val consumer = authority.step.consumers.single()
            framePlan.steps.indexOf(renderByPacketId.getValue(consumer.packetId))
        }
        if (consumerRenderIndices.distinct().size != consumerRenderIndices.size) {
            return CorePrimitiveDestinationCopyValidation.Refused(
                "unsupported.native-core-primitive.destination-copy-shape",
                "A direct CorePrimitive render run cannot bind more than one destination snapshot.",
            )
        }
        val copyLifetimes = authorities.mapIndexed { index, authority ->
            val copyIndex = framePlan.steps.indexOf(authority.step)
            val consumerIndex = consumerRenderIndices[index]
            Triple(authority.snapshotPreparation.resource, copyIndex, consumerIndex)
        }
        copyLifetimes.indices.forEach { leftIndex ->
            ((leftIndex + 1) until copyLifetimes.size).forEach { rightIndex ->
                val left = copyLifetimes[leftIndex]
                val right = copyLifetimes[rightIndex]
                if (left.first == right.first &&
                    left.second < right.third && right.second < left.third
                ) {
                    return CorePrimitiveDestinationCopyValidation.Refused(
                        "unsupported.native-core-primitive.destination-copy-shape",
                        "Shared destination snapshot aliases must have non-overlapping consumer lifetimes.",
                    )
                }
            }
        }
        return CorePrimitiveDestinationCopyValidation.Accepted(authorities)
    }

    private fun refusedPoolCheckout(
        reason: GPUWgpu4kCorePrimitiveFramePoolRefusal,
    ): GPUPreparedNativeFramePayloadMaterialization.Refused = when (reason) {
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.DeviceGenerationMismatch -> refused(
            "stale.native-core-primitive.frame-pool-generation",
            "CorePrimitive frame-pool generation ${reason.expected.value} does not match " +
                "${reason.observed.value}.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.InvalidCapacity -> refused(
            "invalid.native-core-primitive.frame-pool-capacity",
            "CorePrimitive ${reason.resource.name} requires a positive host-addressable byte range.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed -> refused(
            "failed.native-core-primitive.frame-pool-allocation",
            "CorePrimitive ${reason.resource.name} pooled allocation failed: ${reason.failureType}.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated -> refused(
            "unsupported.native-core-primitive.frame-pool-saturated",
            "CorePrimitive frame pool already has ${reason.maxSlots} live slots.",
        )
        GPUWgpu4kCorePrimitiveFramePoolRefusal.Closing,
        GPUWgpu4kCorePrimitiveFramePoolRefusal.Closed,
        -> refused(
            "unsupported.native-core-primitive.frame-pool-closed",
            "CorePrimitive frame pool is closing or closed.",
        )
    }

    private fun refusedSessionCacheAcquire(
        reason: GPUWgpu4kCorePrimitiveSessionCacheRefusal,
    ): GPUPreparedNativeFramePayloadMaterialization.Refused = when (reason) {
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.IncompatibleComponentIdentity -> refused(
            "invalid.native-core-primitive.session-cache-component",
            "CorePrimitive component identity does not match the session cache.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.UnsupportedPipelineIdentity -> refused(
            "unsupported.native-core-primitive.session-cache-pipeline",
            "CorePrimitive render pipeline identity is not executable by this native factory.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.Saturated -> refused(
            "unsupported.native-core-primitive.session-cache-saturated",
            "CorePrimitive session cache already has ${reason.maxEntries} live render pipelines.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed -> refused(
            "failed.native-core-primitive.session-cache-creation",
            "CorePrimitive ${reason.resource.name} creation failed: ${reason.failureType}: ${reason.message}.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.CleanupPending -> refused(
            "failed.native-core-primitive.session-cache-cleanup",
            "CorePrimitive session cache retains ${reason.pendingHandles} native cleanup handle(s).",
        )
        GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closing,
        GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closed,
        -> refused(
            "unsupported.native-core-primitive.session-cache-closed",
            "CorePrimitive session cache is closing or closed.",
        )
    }

    private fun terminalizePooledLeaseBeforeRegistration(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease?,
    ) {
        if (lease == null) return
        if (lease.rollbackBeforeSubmit() is GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied) return
        lease.quarantineUncertain()
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    /**
     * Creates the ordered destination snapshot native resources (Graphite DrawContext.cpp:
     * copy-texture-to-texture, GPU-only, ordered before the consuming pass). The texture format
     * matches the scene target so sampling linearizes the same texel domain the shader writes.
     */
    private fun createCorePrimitiveDestinationSnapshot(
        authority: CorePrimitiveDestinationCopyAuthority,
        targetFormat: GPUColorFormat,
        targetBounds: GPUPixelBounds,
    ): CorePrimitiveDestinationSnapshotHandles {
        val texture = device.createTexture(
            TextureDescriptor(
                size = Extent3D(
                    targetBounds.width.toUInt(),
                    targetBounds.height.toUInt(),
                    1u,
                ),
                format = targetFormat.toCorePrimitiveGPUTextureFormat(),
                usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                label = "Kanvas.frame.corePrimitive.destinationSnapshot",
            ),
        ).tracked()
        onDestinationSnapshotCreated()
        val view = texture.createView().tracked()
        val sampler = device.createSampler(
            SamplerDescriptor(
                addressModeU = GPUAddressMode.ClampToEdge,
                addressModeV = GPUAddressMode.ClampToEdge,
                addressModeW = GPUAddressMode.ClampToEdge,
                magFilter = GPUFilterMode.Nearest,
                minFilter = GPUFilterMode.Nearest,
                mipmapFilter = GPUMipmapFilterMode.Nearest,
                lodMinClamp = 0f,
                lodMaxClamp = 0f,
                compare = null,
                maxAnisotropy = 1u.toUShort(),
                label = "Kanvas.frame.corePrimitive.destinationSampler",
            ),
        ).tracked()
        return CorePrimitiveDestinationSnapshotHandles(
            texture,
            GPUWgpu4kCorePrimitiveDstReadBinding(view, sampler),
        )
    }

    private fun uploadExact(
        buffer: GPUBuffer,
        data: ArrayBuffer,
        usedBytes: Long,
        capacityBytes: Long,
    ) {
        require(usedBytes >= 0L) { "CorePrimitive upload byte count must be non-negative" }
        require(usedBytes <= capacityBytes) { "CorePrimitive upload exceeds its native buffer capacity" }
        val explicitSize = usedBytes.toULong()
        require(explicitSize <= data.size) { "CorePrimitive upload exceeds its host data range" }
        queue.writeBuffer(buffer, 0uL, data, 0uL, explicitSize)
    }

    private companion object {
        const val COVERAGE_MASK_RGBA8_UNORM = "rgba8unorm"
        const val DEPTH24PLUS_STENCIL8 = "depth24plus-stencil8"
        const val CORE_PRIMITIVE_UNIFORM_BYTES = 32
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
    }
}

private fun combineCorePrimitiveLeaseLifecycles(
    lifecycles: List<GPUPreparedNativeFrameLeaseLifecycle>,
): GPUPreparedNativeFrameLeaseLifecycle {
    require(lifecycles.isNotEmpty())
    return if (lifecycles.size == 1) {
        lifecycles.single()
    } else {
        GPUPreparedNativeCompositeFrameLeaseLifecycle(lifecycles)
    }
}

internal class GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(
    private val lease: GPUWgpu4kCorePrimitiveFramePoolLease,
) : GPUPreparedNativeFrameLeaseLifecycle {
    override fun releaseBeforeSubmit(): GPUPreparedNativeFrameLeaseTransition =
        lease.rollbackBeforeSubmit().toPreparedTransition()

    override fun markSubmitted(): GPUPreparedNativeFrameLeaseTransition =
        lease.markSubmitted().toPreparedTransition()

    override fun releaseAfterCompletion(): GPUPreparedNativeFrameLeaseTransition =
        lease.completeSuccessfully().toPreparedTransition()

    override fun quarantineUncertain(): GPUPreparedNativeFrameLeaseTransition =
        lease.quarantineUncertain().toPreparedTransition()
}

private fun GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.toPreparedTransition():
    GPUPreparedNativeFrameLeaseTransition = when (this) {
    GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied ->
        GPUPreparedNativeFrameLeaseTransition.Applied
    is GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Refused ->
        GPUPreparedNativeFrameLeaseTransition.Refused(reason)
}
