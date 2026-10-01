package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.math.geometry.matchesCanonicalPathFillGeometryF32
import org.graphiks.kanvas.gpu.renderer.recording.GPUW6aLayerFramePlan

/** Native realization of the closed W7 source binding, before any unrelated W6 allocation. */
internal object GPUW4eInverseAaCoverageNativeV1 {
    fun preflight(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
        require(frame.validatesW4dAaSources(framePlan))
        frame.w4eInverseAaCoverageAuthorities.values.forEach { authority ->
            val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().singleOrNull {
                it.w6aPassV1 === authority.owner
            }
            require(authority.validates(render, frame.refs) &&
                authority.recipe.binding === authority.binding &&
                authority.payload.matchesDeclaredResources(authority.binding.resources()) &&
                authority.phases.zip(render!!.drawPackets).all { (phase, packet) ->
                    packet.w4ePreparedPath === authority.prepared.pathFor(phase.id.value) &&
                        packet.w4ePreparedClipConsumer === authority.prepared.consumerFor(phase.id.value)
                }) { "W7 inverse-AA source authority, packets, or frozen W4e payload changed before allocation." }
        }
    }

    fun materialize(
        device: GPUDevice,
        queue: GPUQueue,
        frame: GPUW6aLayerFramePlan,
        framePlan: GPUFramePlan,
        views: Map<PlanResourceId, GPUTextureView>,
        geometryBuffers: Map<PlanResourceId, GPUBuffer>,
        generation: GPUDeviceGenerationID,
        owned: W6aOwnedHandles,
    ): Map<Int, GPUPreparedNativeScopeOperand.Render> = frame.w4eInverseAaCoverageAuthorities.values.associate { authority ->
        require(authority.recipe.binding === authority.binding && authority.binding.validatesNativeOperationFacts())
        val render = framePlan.steps.withIndex().single { (_, step) ->
            (step as? GPUFrameStep.RenderPassStep)?.w6aPassV1 === authority.owner
        }
        val stepIndex = render.index
        val scope = render.value as GPUFrameStep.RenderPassStep
        val payload = authority.payload
        queue.writeBuffer(geometryBuffers.getValue(payload.vertexResourceId), 0uL, ArrayBuffer.of(payload.copyVertexData()))
        queue.writeBuffer(geometryBuffers.getValue(payload.indexResourceId), 0uL, ArrayBuffer.of(payload.copyIndexData()))
        queue.writeBuffer(geometryBuffers.getValue(payload.uniformResourceId), 0uL, ArrayBuffer.of(payload.copyUniformData()))
        fun buffer(id: PlanResourceId) = GPUPreparedNativeBufferOperand(geometryBuffers.getValue(id), generation,
            byteCapacity = frame.physical.resource(id).byteSize)
        fun attachment(id: String) = GPUPreparedNativeTextureViewOperand(
            views.getValue(frame.graph.resources().single { it.id.value == id }.id), generation)
        val producer = authority.phases.first()
        val cover = authority.phases.last()
        require(authority.phases.size == 2 && producer.phase == PathRenderPhase.MultisampleStencilProducer &&
            cover.phase == PathRenderPhase.MultisampleStencilColorCover && producer.atomicGroup != null &&
            producer.atomicGroup == cover.atomicGroup && producer.target == cover.target &&
            producer.depthStencil != null && producer.depthStencil == cover.depthStencil &&
            producer.resolveTarget == null && cover.resolveTarget != null) { "W7 inverse-AA requires its closed producer/cover pair." }
        val childOwned = owned.own(GPUW4eNativeOwnedHandles())
        val producerConsumer = authority.prepared.consumerFor(producer.id.value) as?
            GPUW4ePreparedClipConsumerAuthority.InverseDomain ?: error("W7 producer lost inverse-domain consumer")
        val coverConsumer = authority.prepared.consumerFor(cover.id.value) as?
            GPUW4ePreparedClipConsumerAuthority.InverseDomain ?: error("W7 cover lost inverse-domain consumer")
        val interior = (producerConsumer.interiorCoverage as? GPUW4ePreparedInverseInteriorCoverage.Geometry)
            ?.copyGeometryF32() ?: error("W7 admits Geometry interiors only")
        val interiorOperation = authority.binding.operations().filterIsInstance<PlanW4eInverseAaCoverageSourceBindingV1.InteriorStencil>().single()
        val coverOperation = authority.binding.operations().filterIsInstance<PlanW4eInverseAaCoverageSourceBindingV1.ZeroWhiteCover>().single()
        val domain = interiorOperation.facts.copyDomainTargetLocalI32()
        fun sameDomain(first: org.graphiks.math.geometry.RectI32, second: org.graphiks.math.geometry.RectI32): Boolean =
            first.left == second.left && first.top == second.top && first.right == second.right && first.bottom == second.bottom
        fun samePreparedDomain(first: GPUPixelBounds, second: GPUPixelBounds): Boolean =
            first.left == second.left && first.top == second.top && first.right == second.right && first.bottom == second.bottom
        fun preparedDomainMatchesRecord(prepared: GPUPixelBounds, record: org.graphiks.math.geometry.RectI32): Boolean =
            prepared.left == record.left && prepared.top == record.top &&
                prepared.right == record.right && prepared.bottom == record.bottom
        require(coverConsumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry &&
            samePreparedDomain(producerConsumer.domain, coverConsumer.domain) &&
            preparedDomainMatchesRecord(producerConsumer.domain, domain) &&
            interior.matchesCanonicalPathFillGeometryF32(interiorOperation.copyInteriorGeometryF32()) &&
            interiorOperation.passId == producer.id && coverOperation.passId == cover.id &&
            interiorOperation.operands == listOf("Pipeline", "Vertex", "Index") && coverOperation.operands == listOf("Pipeline", "BindGroup"))
        val target = interiorOperation.facts.target
        val resolve = requireNotNull(coverOperation.facts.resolveTarget)
        val depth = requireNotNull(interiorOperation.facts.depthStencil)
        require(target == producer.target && target == cover.target && resolve == cover.resolveTarget &&
            depth == producer.depthStencil && depth == cover.depthStencil &&
            interiorOperation.facts.resolveTarget == null && coverOperation.facts.depthStencil == depth &&
            sameDomain(coverOperation.facts.copyDomainTargetLocalI32(), domain))
        val interiorSlice = interiorOperation.slice
        val uniformSlice = coverOperation.slice
        val format = GPUTextureFormat.RGBA8UnormSrgb
        val direct = interiorOperation.mode == PlanW4eInverseAaCoverageSourceBindingV1.InteriorMode.DirectReplaceOne
        val parity = interiorOperation.mode == PlanW4eInverseAaCoverageSourceBindingV1.InteriorMode.Parity
        require((interior.copyDirectTriangleF32OrNull() != null) == direct)
        val producerPipeline = createW4ePathGeometryPipeline(device, format, 4, 0f,
            stencil = if (direct) w4eStencilReplaceState() else w4ePathStencilProducerState(parity), colorWrite = false,
            label = "Kanvas.frame.w7.inverseAa.interior", owned = childOwned)
        val coverPipeline = createW4eUnmaskedCoverPipeline(device, format, 4, stencil = w4eStencilZeroReadState(),
            owned = childOwned, finalBlend = scope.drawPackets.last().blendPlan)
        val bind = childOwned.own(device.createBindGroup(BindGroupDescriptor(
            label = "Kanvas.frame.w7.inverseAa.white", layout = coverPipeline.layout,
            entries = listOf(BindGroupEntry(0u, BufferBinding(buffer(payload.uniformResourceId).buffer,
                uniformSlice.offsetBytes.toULong(), uniformSlice.byteSize.toULong()))),
        )))
        fun indexed() = listOf(
            GPUPreparedNativeRenderCommand.SetVertexBuffer(0, buffer(payload.vertexResourceId), 0L, payload.vertexUsefulBytes, 8L),
            GPUPreparedNativeRenderCommand.SetIndexBuffer(buffer(payload.indexResourceId), GPUPreparedNativeIndexFormat.Uint32, 0L, payload.indexUsefulBytes),
            GPUPreparedNativeRenderCommand.SetScissor(domain.left, domain.top, domain.width(), domain.height()),
            GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
                indexCount = interiorSlice.indexCount,
                firstIndex = interiorSlice.firstIndex,
                baseVertex = interiorSlice.baseVertex,
                vertexCount = interiorSlice.vertexCount,
                maxLocalIndex = interiorSlice.maxLocalIndex,
            )),
        )
        val config = GPUPreparedNativeRenderPassConfig(attachment(target.value), attachment(resolve.value), attachment(depth.value),
            GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeStoreOperation.Store, GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
            depthReadOnly = true, stencilClearValue = 0u, stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
            stencilStoreOperation = GPUPreparedNativeStoreOperation.Store, stencilReadOnly = false)
        stepIndex to GPUPreparedNativeScopeOperand.Render(stepIndex, config, buildList {
            add(GPUPreparedNativeRenderCommand.SetStencilReference(1u)); add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand.noBindings(producerPipeline, generation))); addAll(indexed())
            add(GPUPreparedNativeRenderCommand.SetStencilReference(0u)); add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(coverPipeline.pipeline, generation)))
            add(GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(bind, generation)))
            add(GPUPreparedNativeRenderCommand.SetScissor(domain.left, domain.top, domain.width(), domain.height())); add(GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)))
        }, w6aPassV1 = authority.owner)
    }
}
