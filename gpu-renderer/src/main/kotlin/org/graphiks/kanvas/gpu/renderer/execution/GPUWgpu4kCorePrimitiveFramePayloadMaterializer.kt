package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedCandidateDecision
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRouteRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.sealGPUCorePrimitiveCoverageMaskPreparedRoute
import org.graphiks.kanvas.gpu.renderer.passes.snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionPacketAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveAnalyticIntersectionUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveCoverageMaskConsumerUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveCoverageMaskProducerUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.validateCorePrimitiveClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Public-wgpu4k materializer for direct and unified indexed path CorePrimitive routes. */
internal class GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val sessionCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
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
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
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
        val renderStep = renderSteps.singleOrNull()
        if (renderStep == null || renderStep.drawPackets.isEmpty()) {
            return refused(
                "unsupported.native-core-primitive.render-shape",
                "Direct CorePrimitive requires one non-empty multi-packet render scope; " +
                    "observed ${renderSteps.size} scope(s).",
            )
        }
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
            return materializeIndexedPathCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
                renderStep,
                renderScope,
                unifiedRoute,
            )
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
        val uniformLayout = preparedPassSeal.structuralPipelineKey.uniformLayout
        corePrimitiveAnalyticShapeClosedRouteDiagnostic(uniformLayout)?.let { (code, message) ->
            return refused(code, message)
        }
        if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1 ||
            uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1
        ) {
            return refused(
                "unsupported.native-core-primitive.dedicated-multi-pass-route",
                "Clip-stencil and coverage-mask programs require their dedicated native routes.",
            )
        }
        val analyticClipUniformSeals = preparedPassSeal.analyticClipUniformSeals
        val analyticIntersectionUniformSeals = preparedPassSeal.analyticIntersectionUniformSeals
        val exactUniformAuthority = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                preparedPassSeal.uniformSlabSeal != null && analyticClipUniformSeals.isEmpty() &&
                    analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                preparedPassSeal.uniformSlabSeal == null &&
                    analyticClipUniformSeals.size == semanticPackets.size &&
                    analyticIntersectionUniformSeals.isEmpty()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                preparedPassSeal.uniformSlabSeal == null && analyticClipUniformSeals.isEmpty() &&
                    analyticIntersectionUniformSeals.size == semanticPackets.size
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                error("AnalyticShapeUniform80V1 was refused before direct uniform authority validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct uniform authority validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct uniform authority validation")
        }
        if (!exactUniformAuthority) {
            return refused(
                "invalid.native-core-primitive.analytic-uniform-seal",
                "The direct pass seal must retain exactly one complete packet-order uniform32, uniform64, or uniform160 authority.",
            )
        }
        val sealedUniformPlan = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                requireNotNull(preparedPassSeal.uniformSlabSeal).plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                analyticClipUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                analyticIntersectionUniformSeals.first().plan
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                error("AnalyticShapeUniform80V1 was refused before direct uniform plan selection")
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
                error("AnalyticShapeUniform80V1 was refused before direct binding layout selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct binding layout selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct binding selection")
        }
        val targetBounds = semanticPackets.first().third.targetBounds
        val acceptedGeometries = semanticPackets.mapIndexed { packetIndex, (_, packet, semantic) ->
            val packetAuthority = packet.corePrimitivePreparedAuthority
            val expectedAnalyticSeal = analyticClipUniformSeals.getOrNull(packetIndex)
            val expectedAnalyticIntersectionSeal = analyticIntersectionUniformSeals.getOrNull(packetIndex)
            if (!semantic.hasStructuralIntegrity() || packet.role != GPUDrawPacketRole.Shading ||
                packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.bindingLayoutHash != expectedBindingLayoutHash ||
                packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(semantic.scissorBounds) ||
                packetAuthority?.structuralPipelineKey != preparedPassSeal.structuralPipelineKey ||
                packetAuthority.renderPipelineKey != packet.renderPipelineKey ||
                packetAuthority.uniformSlabSeal !== preparedPassSeal.uniformSlabSeal ||
                packetAuthority.analyticClipUniformSeal !== expectedAnalyticSeal ||
                packetAuthority.analyticIntersectionUniformSeal !== expectedAnalyticIntersectionSeal ||
                semantic.targetBounds != targetBounds || semantic.payloadRef.uniformBlock?.byteSize !=
                CORE_PRIMITIVE_UNIFORM_BYTES.toLong() || semantic.payloadRef.uniformBlock.bytes !=
                corePrimitiveUniformBytes(semantic.targetBounds, semantic.premultipliedRgba)
            ) {
                return refused(
                    "invalid.native-core-primitive.packet-authority",
                    "A CorePrimitive packet contradicts its immutable semantic, pipeline, uniform, or target authority.",
                )
            }
            sealedRoutes.routesByPacketId.getValue(packet.packetId)
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
                val expectedPackedBytes = ByteArray(sealedUniformPlan.totalBytes.toInt())
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
                    val exactRange = try {
                        Math.addExact(slot.alignedOffset, 160L) <= sealedUniformPlan.totalBytes
                    } catch (_: ArithmeticException) {
                        false
                    }
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
                        !exactRange || !seal.hasExactPayload(expectedBytes)
                    ) return@validation false
                    expectedBytes.copyInto(expectedPackedBytes, slot.alignedOffset.toInt())
                    exactPayloads += GPUUniformSlabPayload(slot.slotLabel, expectedBytes)
                }
                sealedUniformPlan.hasExactPayloads(
                    "core-primitive-analytic-intersection-uniform-pass",
                    generationSeal.deviceGeneration.value,
                    limits.minUniformBufferOffsetAlignment,
                    exactPayloads,
                ) && preparedPassSeal.packedUniformBytesForUpload().contentEquals(expectedPackedBytes)
            }
            if (!exactIntersectionAuthority) {
                return refused(
                    "invalid.native-core-primitive.analytic-intersection-uniform-seal",
                    "The uniform160 pass seal contradicts its exact packet, clip, layout, offset, payload, or generation authority.",
                )
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
        val expectedEncoderSteps = 1 + if (readbackStep == null) 0 else 1
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
        if (encoderPlan.scopes != listOfNotNull(renderScope, readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "CorePrimitive encoder scopes must preserve render order then optional readback.",
            )
        }
        if (renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame || renderStep.sampleContinuation != null ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null || semanticPackets.any { (_, _, semantic) ->
                semantic.scissorBounds.isEmpty ||
                semantic.scissorBounds.left < targetBounds.left || semantic.scissorBounds.top < targetBounds.top ||
                semantic.scissorBounds.right > targetBounds.right || semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.render-state",
                "CorePrimitive requires one clear/store single-sample pass and contained scissors.",
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
        if (preparations.size != 4 + (if (readbackStep == null) 0 else 1) ||
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
            targetDescriptor.logicalBounds != targetBounds || targetDescriptor.format.value != RGBA8_UNORM ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact frame-local rgba8unorm scene target.",
            )
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
        val uniformSlabSeal = preparedPassSeal.uniformSlabSeal
        val uniformSlabPlan = sealedUniformPlan
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
        val uniformUploadBytes = preparedPassSeal.packedUniformBytesForUpload()
        val pipelineMapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
            preparedPassSeal.structuralPipelineKey,
        ) as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
            "unsupported.native-core-primitive.pipeline",
            "The direct CorePrimitive structural key has no exact native pipeline.",
        )
        val expectedComponentIdentity = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                error("AnalyticShapeUniform80V1 was refused before direct component selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct component selection")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct component selection")
        }
        val exactProgram = when (uniformLayout) {
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2 ->
                !pipelineMapping.identity.program.isAnalyticClip() &&
                    !pipelineMapping.identity.program.isAnalyticIntersection4()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1 ->
                pipelineMapping.identity.program.isAnalyticClip()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1 ->
                pipelineMapping.identity.program.isAnalyticIntersection4()
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1 ->
                error("AnalyticShapeUniform80V1 was refused before direct program validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1 ->
                error("NoBindingsV1 was refused before direct program validation")
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            -> error("Coverage-mask layouts were refused before direct program validation")
        }
        if (pipelineMapping.componentIdentity != expectedComponentIdentity || !exactProgram) {
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
        if (resources.ordinaryResources.size != 4 ||
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
            val targetViewOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
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
                    val semantic = semanticPackets[index].third
                    val slice = arena.slices[index]
                    val uniformSlot = uniformSlabPlan.slots[index]
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            sharedBindGroupOperand,
                            dynamicOffsets = listOf(uniformSlot.alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            semantic.scissorBounds.left,
                            semantic.scissorBounds.top,
                            semantic.scissorBounds.width,
                            semantic.scissorBounds.height,
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
                    colorTarget = targetViewOperand,
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
                        format = GPUTextureFormat.RGBA8Unorm,
                    ),
                )
            } else {
                null
            }
            val operandsByStep = (listOf(renderOperand) + listOfNotNull(readbackOperand))
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
        val producerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
        }
        val consumerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
        }
        if (producerEntries.isEmpty() || consumerEntries.isEmpty() ||
            renderEntries.size != producerEntries.size + consumerEntries.size ||
            renderEntries.take(producerEntries.size) != producerEntries ||
            renderEntries.drop(producerEntries.size) != consumerEntries
        ) return invalid(
            "scope",
            "Coverage-mask requires only sealed producers followed by only sealed consumers.",
        )
        val producers = producerEntries.map { entry ->
            entry to (entry.seal as GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer)
        }
        val consumers = consumerEntries.map { entry ->
            entry to (entry.seal as GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer)
        }
        val route = producers.first().second.route
        val slab = producers.first().second.slabAuthority
        val attachment = producers.first().second.attachmentAuthority
        val sceneTarget = consumers.first().second.sceneTarget
        val sceneTargetGeneration = consumers.first().second.sceneTargetGeneration
        if (producers.size != route.producers.size || consumers.size != route.consumers.size ||
            producers.any { (entry, seal) ->
                seal.sourceStepIndex != entry.scope.sourceStepIndex ||
                    entry.scope.sourcePacketIds != listOf(seal.packetId) || seal.route !== route ||
                    seal.slabAuthority !== slab || seal.attachmentAuthority !== attachment
            } || consumers.any { (entry, seal) ->
                seal.sourceStepIndex != entry.scope.sourceStepIndex ||
                    entry.scope.sourcePacketIds != listOf(seal.packetId) || seal.route !== route ||
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
            !consumers.last().second.isLastConsumer
        ) return invalid(
            "seal",
            "Coverage-mask scope order, identity, dependency, or retained frame authority was substituted.",
        )

        val producerPackets = producers.map { (entry, seal) ->
            val packet = entry.render.drawPackets.singleOrNull() ?: return invalid(
                "packet",
                "Every coverage-mask producer requires one exact packet.",
            )
            if (packet.packetId != seal.packetId || packet.commandIdValue != seal.commandId ||
                packet.role != GPUDrawPacketRole.ClipProducer
            ) return invalid(
                "packet",
                "A coverage-mask producer packet identity, role, or order was substituted.",
            )
            packet
        }
        val liveProducerAuthority = validateCorePrimitiveClipProducerAuthority(framePlan)
        if (liveProducerAuthority.diagnostic != null ||
            liveProducerAuthority.sealedProducerPacketIds != producerPackets.map { it.packetId }.toSet()
        ) return invalid(
            "producer-authority",
            "Coverage-mask live producer authority, resources, or dependency graph was substituted.",
        )
        val consumerPacketsAndSemantics = consumers.mapIndexed { index, (entry, seal) ->
            val packet = entry.render.drawPackets.singleOrNull() ?: return invalid(
                "packet",
                "Every coverage-mask consumer requires one exact packet.",
            )
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return invalid("packet", "Every coverage-mask consumer requires a typed semantic.")
            val slot = slab.uniformSlabSeal.consumerSlots.getOrNull(index)
                ?: return invalid("packet", "Coverage-mask consumer uniform slot authority is missing.")
            val routeConsumer = route.consumers.getOrNull(index)
                ?: return invalid("packet", "Coverage-mask consumer route authority is missing.")
            val preparedAuthority = packet.corePrimitivePreparedAuthority
            val stableRenderPipelineKey = slot.structuralPipelineKey.stableRenderPipelineKey(
                org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
            )
            if (packet.packetId != seal.packetId || packet.commandIdValue != seal.commandId ||
                packet.originalPaintOrder != seal.sourceOrder || packet.role != GPUDrawPacketRole.Shading ||
                !semantic.hasStructuralIntegrity() || semantic.payloadRef.commandIdValue != seal.commandId ||
                packet.renderStepId.value != CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
                packet.renderStepVersion != 1 ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.resourceSlot != semantic.payloadRef.resourceSlot ||
                packet.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                packet.clipCoveragePlan != semantic.clipCoveragePlan ||
                packet.frameProvenance != semantic.frameProvenance ||
                packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
                packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                packet.scissorBoundsHash != null ||
                slot.slotIndex != slab.uniformSlabSeal.producerSlots.size + index ||
                slot.packetId != packet.packetId || slot.commandId != packet.commandIdValue ||
                slot.sourceOrder != packet.originalPaintOrder ||
                !slot.semanticAuthority.matches(semantic) ||
                slot.structuralPipelineKey != routeConsumer.structuralKey ||
                slot.renderPipelineKey != stableRenderPipelineKey ||
                slot.bindingLayoutHash != CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH ||
                packet.renderPipelineKey != slot.renderPipelineKey ||
                packet.bindingLayoutHash != slot.bindingLayoutHash ||
                preparedAuthority == null ||
                preparedAuthority.structuralPipelineKey != slot.structuralPipelineKey ||
                preparedAuthority.renderPipelineKey != slot.renderPipelineKey ||
                preparedAuthority.coverageMaskUniformSlabSeal !== slab.uniformSlabSeal ||
                preparedAuthority.uniformSlabSeal != null ||
                preparedAuthority.analyticClipUniformSeal != null ||
                preparedAuthority.analyticIntersectionUniformSeal != null
            ) return invalid(
                "packet",
                "A coverage-mask consumer packet contradicts its semantic or order authority.",
            )
            packet to semantic
        }
        val plans = (producerPackets + consumerPacketsAndSemantics.map { it.first }).map { packet ->
            packet.clipExecutionPlan as? org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.CoverageMask
                ?: return invalid("route", "Every coverage-mask packet requires its live mask plan.")
        }
        val plan = plans.first()
        if (plans.any { it.canonicalIdentity() != plan.canonicalIdentity() } ||
            plan.canonicalIdentity() != route.planCanonicalIdentity ||
            plan.contentKey != route.contentKey || plan.bounds != route.bounds ||
            plan.orderingToken != route.orderingToken || plan.producers.size != producers.size
        ) return invalid("route", "The live coverage-mask plan differs from the sealed route.")

        val freshAttachment = try {
            GPUCorePrimitiveCoverageMaskAttachmentAuthority(
                attachment.resource.value,
                route.attachment.width,
                route.attachment.height,
                org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskAttachmentFormat.Rgba8Unorm,
                1,
                generationSeal.deviceGeneration,
                attachment.resourceGeneration,
            )
        } catch (_: IllegalArgumentException) {
            return invalid("route", "Coverage-mask attachment authority is invalid.")
        }
        val request = GPUCorePrimitiveCoverageMaskPreparedRouteRequest(
            plan,
            consumerPacketsAndSemantics.zip(slab.uniformSlabSeal.consumerSlots).map { pair ->
                val (packetAndSemantic, slot) = pair
                val (packet, semantic) = packetAndSemantic
                GPUCorePrimitiveCoverageMaskConsumerInput(
                    packet.packetId,
                    packet.commandIdValue,
                    packet.originalPaintOrder,
                    slot.semanticAuthority,
                    semantic.coverageMode,
                    packet.blendPlan ?: return invalid(
                        "route",
                        "Coverage-mask consumer blend authority is missing.",
                    ),
                    plan.orderingToken,
                    packet.role,
                    semantic.geometry,
                )
            },
            freshAttachment,
        )
        val candidate = when (val decision =
            snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(request)
        ) {
            is GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Accepted -> decision.candidate
            is GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Refused ->
                return invalid("route", decision.message)
        }
        val freshRoute = when (val sealed =
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, request)
        ) {
            is GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted -> sealed
            is GPUCorePrimitiveCoverageMaskPreparedRoute.Refused ->
                return invalid("route", sealed.message)
        }
        if (freshRoute.contentKey != route.contentKey ||
            freshRoute.planCanonicalIdentity != route.planCanonicalIdentity ||
            freshRoute.bounds != route.bounds || freshRoute.orderingToken != route.orderingToken ||
            freshRoute.producers != route.producers || freshRoute.consumers != route.consumers ||
            freshRoute.attachment != route.attachment
        ) return invalid("route", "Pure coverage-mask re-snapshot or re-seal diverged.")

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
            scenePreparation.resource != sceneTarget || sceneDescriptor.format.value != RGBA8_UNORM ||
            sceneDescriptor.sampleCount != 1 || scenePreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || scenePreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            scenePreparation.byteSize != exactSceneBytes ||
            maskPreparation.resource != attachment.resource ||
            maskDescriptor.logicalBounds != route.bounds || maskDescriptor.format.value != RGBA8_UNORM ||
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

        val expectedUniformPayloads = plan.producers.map { producer ->
            corePrimitiveCoverageMaskProducerUniformBytes(plan, producer)
        } + consumerPacketsAndSemantics.map { (_, semantic) ->
            corePrimitiveCoverageMaskConsumerUniformBytes(plan, semantic)
        }
        val expectedUniformBytes = ByteArray(uniformSeal.plan.totalBytes.toInt())
        expectedUniformPayloads.forEachIndexed { index, bytes ->
            val slot = uniformSeal.plan.slots[index]
            bytes.copyInto(expectedUniformBytes, slot.alignedOffset.toInt())
        }
        if (!uniformSeal.packedBytesSnapshot().contentEquals(expectedUniformBytes) ||
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
            packCorePrimitiveFrameGeometry(freshRoute.consumers.map { consumer ->
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

        producers.forEachIndexed { index, (entry, _) ->
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
        consumers.forEachIndexed { index, (entry, _) ->
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

        val structuralKeys = route.producers.map { it.structuralKey } +
            route.consumers.map { it.structuralKey }
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
        if (cacheKeys.filterKeys { it.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer }
                .values.any { it.componentIdentity != PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY } ||
            cacheKeys.filterKeys { it.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer }
                .values.singleOrNull()?.componentIdentity !=
            PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
        ) return invalid("pipeline", "Coverage-mask producer or consumer pipeline identity was substituted.")

        synchronized(this) {
            if (closed) return refused(
                "unsupported.native-core-primitive.materializer-state",
                "The CorePrimitive materializer closed during coverage-mask validation.",
            )
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
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
            val maskRequirement = GPUWgpu4kCorePrimitiveCoverageMaskRequirement(
                route.attachment.width,
                route.attachment.height,
                GPUTextureFormat.RGBA8Unorm,
                1,
                GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    generationSeal.deviceGeneration,
                    slab.vertexByteSize,
                    slab.indexByteSize,
                    slab.uniformByteSize,
                    componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
                    coverageMask = maskRequirement,
                ),
            )) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val maskHandles = requireNotNull(pooled.handles.coverageMask)
            require(maskHandles.requirement == maskRequirement)
            uploadExact(
                pooled.handles.vertexBuffer,
                ArrayBuffer.of(packedGeometry.vertices),
                slab.vertexByteSize,
                pooled.capacities.vertexBytes,
            )
            uploadExact(
                pooled.handles.indexBuffer,
                ArrayBuffer.of(packedGeometry.indices),
                slab.indexByteSize,
                pooled.capacities.indexBytes,
            )
            uploadExact(
                pooled.handles.uniformBuffer,
                ArrayBuffer.of(expectedUniformBytes),
                slab.uniformByteSize,
                pooled.capacities.uniformBytes,
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
            val maskOperand = GPUPreparedNativeTextureViewOperand(
                maskHandles.view,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
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
            val producerBindGroup = GPUPreparedNativeBindGroupOperand(
                pooled.handles.bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val consumerBindGroup = GPUPreparedNativeBindGroupOperand(
                maskHandles.consumerBindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val pipelineOperands = acquiredByStructural.mapValues { (structural, acquired) ->
                if (structural.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer) {
                    GPUPreparedNativeRenderPipelineOperand.fromCoverageMaskConsumerAcquisition(
                        acquired,
                        generationSeal.deviceGeneration,
                        limits.minUniformBufferOffsetAlignment,
                    )
                } else {
                    GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
                        acquired,
                        generationSeal.deviceGeneration,
                    )
                }
            }
            val producerOperands = producers.mapIndexed { index, (entry, seal) ->
                GPUPreparedNativeScopeOperand.Render(
                    entry.scope.sourceStepIndex,
                    GPUPreparedNativeRenderPassConfig(
                        colorTarget = maskOperand,
                        loadOperation = if (index == 0) GPUPreparedNativeLoadOperation.Clear
                        else GPUPreparedNativeLoadOperation.Load,
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (index == 0) GPUPreparedNativeClearColor(1.0, 1.0, 1.0, 1.0)
                        else null,
                    ),
                    listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[route.producers[index].structuralKey]),
                        ),
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            producerBindGroup,
                            listOf(seal.uniformSlice.alignedOffset),
                        ),
                        GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
                    ),
                )
            }
            val consumerOperands = consumers.mapIndexed { index, (entry, seal) ->
                val packed = packedGeometry.slices[index]
                GPUPreparedNativeScopeOperand.Render(
                    entry.scope.sourceStepIndex,
                    GPUPreparedNativeRenderPassConfig(
                        colorTarget = targetOperand,
                        loadOperation = if (index == 0) GPUPreparedNativeLoadOperation.Clear
                        else GPUPreparedNativeLoadOperation.Load,
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (index == 0) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        else null,
                    ),
                    listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[route.consumers[index].structuralKey]),
                        ),
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            consumerBindGroup,
                            listOf(seal.uniformSlice.alignedOffset),
                        ),
                        GPUPreparedNativeRenderCommand.SetVertexBuffer(
                            0,
                            vertexOperand,
                            0L,
                            slab.vertexByteSize,
                            8L,
                        ),
                        GPUPreparedNativeRenderCommand.SetIndexBuffer(
                            indexOperand,
                            GPUPreparedNativeIndexFormat.Uint32,
                            0L,
                            slab.indexByteSize,
                        ),
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = packed.indexCount,
                                firstIndex = packed.firstIndex,
                                baseVertex = packed.baseVertex,
                                vertexCount = packed.vertexCount,
                                maxLocalIndex = packed.maxLocalIndex,
                            ),
                        ),
                    ),
                    listOf(consumerPacketsAndSemantics[index].second),
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
                        GPUTextureFormat.RGBA8Unorm,
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
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during coverage-mask materialization" }
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

        val renderEntries = encoderPlan.scopes.filter {
            it.operationKind == GPUEncoderOperationKind.Render
        }.map { scope ->
            val render = framePlan.steps.getOrNull(scope.sourceStepIndex) as?
                GPUFrameStep.RenderPassStep ?: return refused(
                "invalid.native-core-primitive.clip-stencil-scope",
                "Every prepared clip-stencil render scope must retain its exact frame step.",
            )
            RenderEntry(scope, render, scope.corePrimitiveClipStencilPreparedRouteSeal)
        }
        val producerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
        }
        val consumerEntries = renderEntries.filter {
            it.seal is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
        }
        if (producerEntries.size != 1 || consumerEntries.isEmpty() ||
            renderEntries.size != 1 + consumerEntries.size ||
            renderEntries.any {
                it.seal !is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer &&
                    it.seal !is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
            }
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
                routeConsumer.scissor != semantic.scissorBounds
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
        if (encoderPlan.scopes != renderEntries.map(RenderEntry::scope) + listOfNotNull(readbackScope) ||
            framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } !=
            renderEntries.size + (if (readbackStep == null) 0 else 1)
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
        val arena = producerSeal.geometryArena
        val slab = producerSeal.slabAuthority
        val uniformSeal = slab.uniformSlabSeal
        val vertexData = arena.copyVertices()
        val indexData = arena.copyIndices()
        val vertexBytes = slab.vertexByteSize
        val indexBytes = slab.indexByteSize
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            targetBounds.width != route.attachment.width || targetBounds.height != route.attachment.height ||
            targetPreparation.resource != producerEntry.render.target ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            clipDepthStencilPreparation.resource != producerSeal.attachmentAuthority.resource ||
            clipDescriptor.logicalBounds != targetBounds ||
            clipDescriptor.format.value != DEPTH24PLUS_STENCIL8 || clipDescriptor.sampleCount != 1 ||
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
            uniformDescriptor.byteSize != uniformSeal.plan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSeal.plan.alignmentBytes ||
            uniformPreparation.byteSize != uniformSeal.plan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            uniformSeal.plan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformSeal.plan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
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
            Math.multiplyExact(Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()), 4L)
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.clip-stencil-resource-contract",
                "Prepared clip-stencil D24S8 byte sizing overflowed.",
            )
        }
        if (clipDepthStencilPreparation.byteSize != expectedDepthBytes ||
            producerEntry.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                "load",
                GPUStorePlan.Store,
            ) || producerEntry.render.depthStencilLoadStore !=
            org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                GPUStorePlan.Store,
                0u,
            ) || orderedConsumers.withIndex().any { (index, pair) ->
                pair.first.render.target != producerEntry.render.target ||
                    pair.first.render.loadStore != org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                        if (index == 0) "clear" else "load",
                        GPUStorePlan.Store,
                    ) || pair.first.render.depthStencilLoadStore !=
                    org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.ReadOnlyKeep
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

        val expectedProducerRoles = listOf(
            GPUPreparedNativeOperandRole.RenderColorTarget,
            GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
            GPUPreparedNativeOperandRole.RenderPipeline,
            GPUPreparedNativeOperandRole.RenderVertexBuffer,
            GPUPreparedNativeOperandRole.RenderIndexBuffer,
        )
        val expectedProducerKinds = listOf(
            GPUPreparedNativeOperandKind.TextureView,
            GPUPreparedNativeOperandKind.TextureView,
            GPUPreparedNativeOperandKind.RenderPipeline,
            GPUPreparedNativeOperandKind.Buffer,
            GPUPreparedNativeOperandKind.Buffer,
        )
        val expectedConsumerRoles = expectedProducerRoles +
            GPUPreparedNativeOperandRole.RenderBindGroup
        val expectedConsumerKinds = expectedProducerKinds + GPUPreparedNativeOperandKind.BindGroup
        if (producerEntry.scope.nativeOperandKeys.map { it.role } != expectedProducerRoles ||
            producerEntry.scope.nativeOperandKeys.map { it.kind } != expectedProducerKinds ||
            orderedConsumers.any { (entry, _) ->
                entry.scope.nativeOperandKeys.map { it.role } != expectedConsumerRoles ||
                    entry.scope.nativeOperandKeys.map { it.kind } != expectedConsumerKinds
            } || renderEntries.flatMap { it.scope.nativeOperandKeys }.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            }
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
            route.consumers.map { it.structuralKey }
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
                1,
                GPUTextureUsage.RenderAttachment,
            )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    deviceGeneration = generationSeal.deviceGeneration,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    uniformBytes = uniformSeal.plan.totalBytes,
                    componentIdentity = PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                    clipDepthStencil = clipRequirement,
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
            require(clipHandles.requirement == clipRequirement) {
                "Pooled clip D24S8 attachment differs from its exact requirement"
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
                ArrayBuffer.of(uniformSeal.packedBytesForUpload()),
                uniformSeal.plan.totalBytes,
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
            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
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
            val bindGroupOperand = GPUPreparedNativeBindGroupOperand(
                pooled.handles.bindGroup,
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
                        bindGroupOperand,
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
            val producerOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = producerEntry.scope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = targetOperand,
                    depthStencilTarget = clipOperand,
                    loadOperation = GPUPreparedNativeLoadOperation.Load,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = null,
                    depthReadOnly = true,
                    stencilClearValue = 0u,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    stencilReadOnly = false,
                ),
                commands = geometryCommands(
                    route.producer.structuralKey,
                    producerSeal.geometrySlice,
                    requireNotNull(route.producer.scissor),
                    null,
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
            val consumerOperands = orderedConsumers.mapIndexed { index, (entry, seal) ->
                val routeConsumer = route.consumers[index]
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = entry.scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = targetOperand,
                        depthStencilTarget = clipOperand,
                        loadOperation = if (index == 0) {
                            GPUPreparedNativeLoadOperation.Clear
                        } else {
                            GPUPreparedNativeLoadOperation.Load
                        },
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (index == 0) {
                            GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        } else {
                            null
                        },
                        depthReadOnly = true,
                        stencilReadOnly = true,
                    ),
                    commands = geometryCommands(
                        routeConsumer.structuralKey,
                        seal.geometrySlice,
                        requireNotNull(routeConsumer.scissor),
                        seal.uniformSlice.alignedOffset,
                    ),
                    semanticPayloads = listOf(consumerSemantics[index]),
                    operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
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
                        format = GPUTextureFormat.RGBA8Unorm,
                    ),
                )
            } else {
                null
            }
            val operandsByStep = (listOf(producerOperand) + consumerOperands +
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
        val preparedPathExact = preparedPathPass != null &&
            preparedPathPass.uniformSlabSeal === unifiedRoute.uniformSlabSeal &&
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
                    preparedDirectPass.uniformSlabSeal === unifiedRoute.uniformSlabSeal &&
                    preparedDirectPass.structuralPipelineKey == directUnits.firstOrNull()?.structuralPipelineKey &&
                    directUnits.all {
                        it.structuralPipelineKey == preparedDirectPass.structuralPipelineKey
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
                    if (producerSemantic == null || coverSemantic == null ||
                        producerSemantic.payloadRef.commandIdValue != coverSemantic.payloadRef.commandIdValue ||
                        producerSemantic.geometry != coverSemantic.geometry ||
                        producerSemantic.targetBounds != coverSemantic.targetBounds ||
                        producerSemantic.scissorBounds != coverSemantic.scissorBounds ||
                        producerSemantic.coverageMode != org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUCorePrimitiveCoverageMode.Stencil1x ||
                        coverSemantic.coverageMode != org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUCorePrimitiveCoverageMode.Stencil1x ||
                        producer.uniformSlot != cover.uniformSlot ||
                        producer.clipExecutionPlan != cover.clipExecutionPlan ||
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
            }
        }
        if (packetIndex != renderStep.drawPackets.size ||
            unifiedRoute.commandIds != unifiedRoute.uniformSlabSeal.commandIds
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-packet-authority",
                "Indexed CorePrimitive route does not cover the exact prepared packet stream.",
            )
        }
        val targetBounds = packetPlans.first().semantic.targetBounds
        if (readbackStep != null && readbackStep.request.sourceBounds != targetBounds) {
            return refused(
                "unsupported.native-core-primitive.indexed-readback-layout",
                "Indexed CorePrimitive readback must cover the exact canonical target bounds.",
            )
        }
        if (packetPlans.any { plan ->
                val authority = plan.packet.corePrimitivePreparedAuthority
                !plan.semantic.hasStructuralIntegrity() ||
                    plan.packet.commandIdValue != plan.semantic.payloadRef.commandIdValue ||
                    plan.packet.uniformSlot != plan.semantic.payloadRef.uniformSlot ||
                    plan.packet.bindingLayoutHash != CORE_PRIMITIVE_BINDING_LAYOUT_HASH ||
                    plan.packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                    plan.packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
                    plan.packet.scissorBoundsHash != corePrimitiveScissorAuthority(plan.semantic.scissorBounds) ||
                    authority?.structuralPipelineKey != plan.structuralKey ||
                    authority.renderPipelineKey != plan.packet.renderPipelineKey ||
                    authority.uniformSlabSeal !== unifiedRoute.uniformSlabSeal ||
                    unifiedRoute.uniformSlabSeal.commandIds.getOrNull(plan.uniformSlotIndex) !=
                    plan.packet.commandIdValue ||
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
        if (renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame || renderStep.sampleContinuation != null ||
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
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-depth-stencil",
                "Indexed CorePrimitive depth/stencil size overflows.",
            )
        }
        if (targetPreparation.resource != renderStep.target || targetDescriptor?.logicalBounds != targetBounds ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthDescriptor?.logicalBounds != targetBounds ||
            depthDescriptor.format.value != DEPTH24PLUS_STENCIL8 || depthDescriptor.sampleCount != 1 ||
            depthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencilPreparation.byteSize != depthBytes
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-attachment-contract",
                "Indexed CorePrimitive target or D24S8 attachment contract is not exact.",
            )
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
        val uniformSlabSeal = unifiedRoute.uniformSlabSeal
        val uniformPlan = uniformSlabSeal.plan
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
            add(GPUPreparedNativeOperandRole.RenderColorTarget)
            add(GPUPreparedNativeOperandRole.RenderDepthStencilTarget)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandRole.RenderPipeline) }
            add(GPUPreparedNativeOperandRole.RenderVertexBuffer)
            add(GPUPreparedNativeOperandRole.RenderIndexBuffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandRole.RenderBindGroup) }
        }
        val expectedNativeKinds = buildList {
            add(GPUPreparedNativeOperandKind.TextureView)
            add(GPUPreparedNativeOperandKind.TextureView)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandKind.RenderPipeline) }
            add(GPUPreparedNativeOperandKind.Buffer)
            add(GPUPreparedNativeOperandKind.Buffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandKind.BindGroup) }
        }
        if (renderScope.nativeOperandKeys.map { it.role } != expectedNativeRoles ||
            renderScope.nativeOperandKeys.map { it.kind } != expectedNativeKinds ||
            renderScope.nativeOperandKeys.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            }
        ) {
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
                RGBA8_UNORM,
                1,
            ).copy(pipelineIdentity = mapped.identity)
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
                1,
                GPUTextureUsage.RenderAttachment,
            )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    generationSeal.deviceGeneration,
                    vertexBytes,
                    indexBytes,
                    uniformPlan.totalBytes,
                    pathRequirement,
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
            require(pathHandles.requirement == pathRequirement) {
                "Pooled CorePrimitive path depth/stencil attachment differs from its exact requirement"
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
                ArrayBuffer.of(uniformSlabSeal.packedBytesForUpload()),
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
            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
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
                            bindGroupOperand,
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
                    colorTarget = targetOperand,
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
                        format = GPUTextureFormat.RGBA8Unorm,
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

    @Synchronized
    override fun close() {
        closed = true
        if (!materializing) preRegistrationHandles.closeRetainingFailures()
    }

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)

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
        const val RGBA8_UNORM = "rgba8unorm"
        const val DEPTH24PLUS_STENCIL8 = "depth24plus-stencil8"
        const val CORE_PRIMITIVE_UNIFORM_BYTES = 32
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
    }
}

private class GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(
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
