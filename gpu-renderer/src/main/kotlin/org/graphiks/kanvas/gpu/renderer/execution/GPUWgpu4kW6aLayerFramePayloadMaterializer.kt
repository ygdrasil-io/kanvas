package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.W5fColorOperationEmitterV1
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.wgsl.W6bMaskCoverageSnippet
import org.graphiks.kanvas.gpu.renderer.wgsl.W6bSeparableBlurSnippet
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.mapRectBoundsF64OrNull

/** Native translation of exact W6 resources and passes behind one ordinary frame draft. */
internal class GPUWgpu4kW6aLayerFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val rootTarget: GPUWgpu4kPreparedSceneTarget,
) : GPUPreparedNativeFramePayloadMaterializer {
    private var consumed = false

    override fun bindLateSurface(draft: GPUPreparedNativeFrameDraft, acquiredSurface: GPUAcquiredSurfaceOutput?): GPUPreparedNativeFrameLateSurfaceBinding =
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired

    override fun materializeReusable(framePlan: GPUFramePlan, sourceWitness: W5hFrameSourceValidationWitnessV1,
        encoderPlan: GPUCommandEncoderPlan, resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal): GPUPreparedNativeFramePayloadMaterialization {
        val frame = framePlan.w6aLayerFrameV1
        if (consumed || frame == null || !frame.validates(framePlan) || !sourceWitness.authenticates(framePlan))
            return GPUPreparedNativeFramePayloadMaterialization.Refused("w6a.layer.invalid_plan", "Missing or consumed W6 frame authority")
        consumed = true
        val owned = W6aOwnedHandles()
        var readbackBuffer: GPUBuffer? = null
        try {
            val graph = frame.graph
            val materialSourceAlphaReplacement = frozenMaterialSourceAlphaReplacement(graph)
            val generation = generationSeal.deviceGeneration
            val root = frame.physical.resource(graph.resources().single { it.role == PlanResourceRole.LogicalTarget }.id)
            require(rootTarget.width == root.copyExtent()?.width && rootTarget.height == root.copyExtent()?.height &&
                rootTarget.deviceGeneration == generation && rootTarget.targetGeneration == generationSeal.targetGeneration)
            val (rootTexture, rootView) = rootTarget.borrow()
            val views = linkedMapOf(root.id to rootView)
            val textures = linkedMapOf(root.id to rootTexture)
            graph.resources().filter { it.kind == PlanResourceKind.Texture2D && it.lifetime == PlanResourceLifetime.FrameLocal && it.id != root.id }.forEach { resource ->
                val slot = frame.physical.slot(resource.id)
                val extent = requireNotNull(resource.copyExtent())
                val format = when (resource.format) {
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) -> GPUTextureFormat.RGBA8UnormSrgb
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) -> GPUTextureFormat.Depth24PlusStencil8
                    PlanTextureFormat.CoverageMask -> GPUTextureFormat.RGBA8Unorm
                    else -> error("Unadmitted W6 texture format")
                }
                val usage = resource.usages().fold(GPUTextureUsage.None) { result, value -> result or when (value) {
                    PlanResourceUsage.RenderAttachment, PlanResourceUsage.DepthStencilAttachment -> GPUTextureUsage.RenderAttachment
                    PlanResourceUsage.Sampled -> GPUTextureUsage.TextureBinding
                    PlanResourceUsage.CopySource -> GPUTextureUsage.CopySrc
                    PlanResourceUsage.CopyDestination -> GPUTextureUsage.CopyDst
                    else -> error("Unadmitted layer usage")
                } }
                val texture = owned.own(device.createTexture(TextureDescriptor(size = Extent3D(extent.width.toUInt(), extent.height.toUInt()),
                    format = format, usage = usage, sampleCount = resource.sampleCountI32.toUInt(), label = "w6a.slot.${slot.slotI32}")))
                views[resource.id] = owned.own(texture.createView())
                textures[resource.id] = texture
            }
            val drawData = graph.passes().mapNotNull { frame.physical.geometryBinding(it.id)?.data } +
                frame.physical.w4eGeometryBindings().map { PlanDrawDataResources(it.payload.vertexResourceId, it.payload.indexResourceId, it.payload.uniformResourceId) }
            val drawUniformIds = drawData.map { it.uniform }.toSet()
            val geometryUniform = frame.physical.resource(graph.resources().single {
                it.role == PlanResourceRole.UniformData && it.id !in drawUniformIds }.id)
            val geometryBuffers = drawData.flatMap { listOf(it.vertex, it.index, it.uniform) }.distinct().associateWith { id ->
                val row = frame.physical.resource(id)
                val usage = when (row.role) {
                    PlanResourceRole.VertexData -> GPUBufferUsage.Vertex
                    PlanResourceRole.IndexData -> GPUBufferUsage.Index
                    PlanResourceRole.UniformData -> GPUBufferUsage.Uniform
                    else -> error("Invalid W4 data resource")
                }
                owned.own(device.createBuffer(BufferDescriptor(size = row.byteSize.toULong(),
                    usage = usage or GPUBufferUsage.CopyDst, label = "w6a.slot.${frame.physical.slot(id).slotI32}")))
            }
            // Graph-texture Picture sources name their existing W5 parent-filter row.  Allocate
            // precisely that published resource; no renderer-side uniform resource exists.
            val graphTextureUniformBuffers = graph.passes().filterIsInstance<PlanPass.PictureSourcePass>()
                .mapNotNull { it.graphTextureOperand?.takeIf { operand -> operand.colorFilter != null }?.uniformResource }
                .distinct().associateWith { id ->
                    val row = frame.physical.resource(id)
                    require(row.role == PlanResourceRole.SourceUniformData && row.kind == PlanResourceKind.Buffer &&
                        PlanResourceUsage.Uniform in row.usages())
                    owned.own(device.createBuffer(BufferDescriptor(size = row.byteSize.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "w6b.parent.filter.${frame.physical.slot(id).slotI32}")))
                }
            val graphTextureOperandsBySource = graph.passes().filterIsInstance<PlanPass.PictureSourcePass>()
                .mapNotNull { pass -> pass.graphTextureOperand?.let { operand -> pass.output to operand } }
                .toMap()
            val uniform = owned.own(device.createBuffer(BufferDescriptor(size = geometryUniform.byteSize.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst, label = "w6a.geometry.uniform")))
            val uniformBytes = ByteArray(Math.toIntExact(geometryUniform.byteSize))
            graph.passes().forEach { pass ->
                val restore = when (pass) {
                    is PlanPass.LayerComposite -> pass.restore
                    is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Layer)?.restore
                    else -> null
                } ?: return@forEach
                val filter = restore.colorFilter ?: return@forEach
                val offset = requireNotNull(restore.colorFilterUniformOffsetI64)
                val data = filter.copyDynamicBytes()
                data.copyInto(uniformBytes, Math.toIntExact(offset))
            }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(uniformBytes))
            val renderOperands = mutableListOf<GPUPreparedNativeScopeOperand>()
            val pathViews = mutableMapOf<Int, GPUTextureView>()
            val w4eOperands = frame.w4eAuthorities.flatMap { (binding, authority) ->
                val payload = binding.payload
                require(payload.matchesDeclaredResources(graph.resources()))
                queue.writeBuffer(geometryBuffers.getValue(payload.vertexResourceId), 0uL, ArrayBuffer.of(payload.copyVertexData()))
                queue.writeBuffer(geometryBuffers.getValue(payload.indexResourceId), 0uL, ArrayBuffer.of(payload.copyIndexData()))
                queue.writeBuffer(geometryBuffers.getValue(payload.uniformResourceId), 0uL, ArrayBuffer.of(payload.copyUniformData()))
                fun buffer(id: PlanResourceId) = GPUPreparedNativeBufferOperand(geometryBuffers.getValue(id), generation,
                    byteCapacity = frame.physical.resource(id).byteSize)
                val entries = framePlan.steps.mapIndexedNotNull { index, step ->
                    val render = step as? GPUFrameStep.RenderPassStep ?: return@mapIndexedNotNull null
                    if (render.w6aPassV1?.id !in binding.graphPassIds()) return@mapIndexedNotNull null
                    GPUW4eNativePassEntry(index, render, render.drawPackets.single())
                }
                require(entries.first().packet.w4ePreparedFrameAuthority?.validatesRenderSteps(framePlan.frameId.value,
                    framePlan.capabilitySeal.sealHash, entries.map { it.render }) == true)
                val extent = binding.copyExtentI32()
                val childOwned = owned.own(GPUW4eNativeOwnedHandles())
                encodeW4eNativePasses(device, generation, entries, payload, buffer(payload.vertexResourceId),
                    buffer(payload.indexResourceId), buffer(payload.uniformResourceId), childOwned,
                    { id -> GPUPreparedNativeTextureViewOperand(views.getValue(graph.resources().single { it.id.value == id }.id), generation) },
                    { id -> graph.resources().single { it.id.value == id }.format == PlanTextureFormat.CoverageMask },
                    GPUPreparedNativeTextureViewOperand(views.getValue(binding.target), generation), null,
                    org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, extent.width, extent.height),
                    commonSource = true, authority::consumerFor, { code, message -> IllegalArgumentException("$code: $message") })
                    .map { native ->
                        val pass = graph.passes()[native.sourceStepIndex - 1]
                        native.pass.depthStencilTarget?.let { pathViews[native.sourceStepIndex] = it.view }
                        native.sourceStepIndex to GPUPreparedNativeScopeOperand.Render(native.sourceStepIndex, native.pass, native.commands,
                            native.semanticPayloads, native.operandLayout, passSegment = native.passSegment, w6aPassV1 = pass)
                    }
            }.toMap()
            graph.passes().forEachIndexed { ordinal, pass ->
                val stepIndex = ordinal + 1
                val step = framePlan.steps[stepIndex]
                w4eOperands[stepIndex]?.let { renderOperands += it; return@forEachIndexed }
                when (pass) {
                    is PlanPass.RenderPass, is PlanPass.StencilGeometryProducerV3, is PlanPass.StencilCover -> {
                        val render = step as GPUFrameStep.RenderPassStep
                        val targetId = when (pass) {
                            is PlanPass.RenderPass -> pass.target
                            is PlanPass.StencilGeometryProducerV3 -> pass.target
                            is PlanPass.StencilCover -> pass.target
                        }
                        val depthId = when (pass) {
                            is PlanPass.StencilGeometryProducerV3 -> pass.depthStencil
                            is PlanPass.StencilCover -> pass.depthStencil
                            else -> null
                        }
                        val draws = when (pass) {
                            is PlanPass.RenderPass -> pass.draws()
                            is PlanPass.StencilGeometryProducerV3 -> listOf(graph.passes().filterIsInstance<PlanPass.StencilCover>()
                                .single { it.draw.commandIndex == pass.commandIndexI32 }.draw)
                            is PlanPass.StencilCover -> listOf(pass.draw)
                        }
                        val commands = buildList {
                            draws.zip(render.drawPackets).forEach { (draw, packet) ->
                                val template = frame.template(packet)
                                val binding = frame.physical.geometryBinding(pass.id)
                                val mapped = binding?.let { frame.geometryPipeline(packet) }
                                // W6b has already selected this source pass and its target.  Its
                                // source stage is transparent and must never consume the final
                                // draw blend; that one belongs exclusively to FilterComposite.
                                val maskMaterialSource = pass.materializesW6bMaskSourceV1()
                                val layout = owned.own(device.createBindGroupLayout(if (mapped != null) corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity)
                                else requireNotNull(template).groupZeroLayout.nativeDescriptorV1("w6a.rect.group0")))
                                val data = binding?.data
                                val verticesSemantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices
                                val pipeline = if (mapped == null) pipeline(requireNotNull(template).sourceWgsl, layout,
                                    w6aColorTarget(if (maskMaterialSource) BlendPlan.LegacySrcOverV1 else draw.blend), owned, template,
                                    verticesSemantic?.artifact)
                                    else geometryPipeline(mapped, layout, owned, template,
                                        if (maskMaterialSource) BlendPlan.LegacySrcOverV1 else null,
                                        maskMaterialSource && pass is PlanPass.StencilCover)
                                val uniformPayload = binding?.let { frame.analyticUniform(packet) }
                                val nativeUniform = data?.let { geometryBuffers.getValue(it.uniform) } ?: uniform
                                if (data != null) {
                                    require(draws.size == 1)
                                    if (verticesSemantic != null) {
                                        val vertices = verticesSemantic.artifact.vertexBytesForUpload()
                                        val indices = verticesSemantic.artifact.indexBytesForUpload()
                                        require(vertices.size.toLong() == binding.vertexBytesI64 &&
                                            (indices?.size?.toLong() ?: 0L) == binding.indexBytesI64 &&
                                            requireNotNull(uniformPayload).size.toLong() == binding.uniformBytesI64)
                                        queue.writeBuffer(geometryBuffers.getValue(data.vertex), binding.vertexOffsetI64.toULong(), ArrayBuffer.of(vertices))
                                        if (indices != null) queue.writeBuffer(geometryBuffers.getValue(data.index), binding.indexOffsetI64.toULong(),
                                            ArrayBuffer.of(indices.copyOf(Math.toIntExact(binding.indexUploadBytesI64))))
                                    } else {
                                    val sourceBounds = if (maskMaterialSource) {
                                        requireNotNull(graph.resources().single { it.id == targetId }.copyExtent()).let { extent ->
                                            RectI32(0, 0, extent.width, extent.height)
                                        }
                                    } else null
                                    val (vertices, indices) = when (draw) {
                                        // The source target is published by the frozen W6b pass.
                                        // Expand only this existing source draw to that target; W5
                                        // then shades the material once while FilterCoverage owns
                                        // the original shape coverage independently.
                                        is AnalyticRectDraw -> packW4RasterGeometry(listOf(sourceBounds ?: draw.copyRasterBounds())).let { it.vertices to it.indices }
                                        is AnalyticRRectDraw -> packW4RasterGeometry(listOf(sourceBounds ?: draw.copyRasterBounds())).let { it.vertices to it.indices }
                                        is W5bPointDraw -> sourceBounds?.let { bounds ->
                                            fullMaskMaterialPointGeometry(draw, bounds)
                                        } ?: (draw.copyVerticesF32() to draw.copyIndicesI32())
                                        is PathDraw -> {
                                            sourceBounds?.let { bounds ->
                                                if (pass is PlanPass.StencilCover) {
                                                    packW4RasterGeometry(listOf(bounds)).let { it.vertices to it.indices }
                                                } else fullMaskMaterialTriangleGeometry(bounds,
                                                    binding.vertexCountI32, binding.indexCountI32)
                                            } ?: run {
                                                val fill = when (val geometry = draw.copyPathGeometry()) {
                                                    is PathDrawGeometry.Fill -> geometry.valueF32
                                                    is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
                                                    else -> error("Unadmitted W6 path geometry")
                                                }
                                                when (pass) {
                                                    is PlanPass.StencilGeometryProducerV3 -> requireNotNull(fill.copyStencilEdgeFanF32OrNull()).let {
                                                        it.copyVerticesF32() to it.copyIndicesI32() }
                                                    is PlanPass.StencilCover -> packW4RasterGeometry(listOf(draw.copyScissorI32())).let { it.vertices to it.indices }
                                                    else -> requireNotNull(fill.copyDirectTriangleF32OrNull()).let { it.copyVerticesF32() to it.copyIndicesI32() }
                                                }
                                            }
                                        }
                                        else -> error("Unadmitted W6 geometry data")
                                    }
                                    require(vertices.size * 4L == binding.vertexBytesI64 && indices.size * 4L == binding.indexBytesI64 &&
                                        requireNotNull(uniformPayload).size.toLong() == binding.uniformBytesI64)
                                    queue.writeBuffer(geometryBuffers.getValue(data.vertex), binding.vertexOffsetI64.toULong(), ArrayBuffer.of(vertices))
                                    queue.writeBuffer(geometryBuffers.getValue(data.index), binding.indexOffsetI64.toULong(), ArrayBuffer.of(indices))
                                    }
                                    queue.writeBuffer(nativeUniform, binding.uniformOffsetI64.toULong(), ArrayBuffer.of(requireNotNull(uniformPayload)))
                                }
                                val bind = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
                                    entries = listOf(BindGroupEntry(0u, BufferBinding(nativeUniform, 0uL,
                                        uniformPayload?.size?.toULong() ?: geometryUniform.byteSize.toULong()))))))
                                add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)))
                                add(GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(bind, generation),
                                    if (mapped == null) emptyList() else binding.let { listOf(it.uniformOffsetI64) }))
                                val scissor = if (maskMaterialSource) {
                                    val extent = requireNotNull(graph.resources().single { it.id == targetId }.copyExtent())
                                    RectI32(0, 0, extent.width, extent.height)
                                } else when (draw) {
                                    // The W6a rect vertex shader is fullscreen; its raster domain is
                                    // therefore the immutable visible rect intersected with the clip,
                                    // not the clip alone.
                                    is SolidRectDraw -> draw.copyVisibleBounds().also {
                                        require(it.intersect(draw.copyScissor()))
                                    }
                                    is AnalyticRectDraw -> draw.copyScissor()
                                    is AnalyticRRectDraw -> draw.copyScissor()
                                    is PathDraw -> draw.copyScissorI32()
                                    is W5bPointDraw -> draw.copyScissorI32()
                                    is W5bVerticesDraw -> draw.copyScissorI32()
                                    else -> error("Unadmitted W6 geometry")
                                }
                                // W6a construction already rebases non-root PlanDraws to their
                                // frozen target; WebGPU therefore receives this texture-local scissor.
                                add(GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()))
                                if (data == null) add(GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)))
                                else {
                                    add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0,
                                        GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.vertex), generation), binding.vertexOffsetI64, binding.vertexBytesI64, binding.vertexStrideBytesI32.toLong()))
                                    if (binding.indexCountI32 == 0) add(GPUPreparedNativeRenderCommand.Draw(
                                        GPUPreparedNativeDrawCall.Draw(binding.vertexCountI32, 1, 0, 0))) else {
                                    add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                                        GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.index), generation),
                                        if (binding.indexElementBytesI32 == 2) GPUPreparedNativeIndexFormat.Uint16 else GPUPreparedNativeIndexFormat.Uint32,
                                        binding.indexOffsetI64, binding.indexBytesI64))
                                    add(GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
                                        indexCount = binding.indexCountI32, firstIndex = 0, baseVertex = 0,
                                        vertexCount = binding.vertexCountI32, maxLocalIndex = binding.maxLocalIndexI32)))
                                    }
                                }
                            }
                        }
                        val clear = render.loadStore.loadOp == "clear"
                        depthId?.let { pathViews[stepIndex] = views.getValue(it) }
                        renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(targetId), generation),
                                depthStencilTarget = depthId?.let { GPUPreparedNativeTextureViewOperand(views.getValue(it), generation) },
                                loadOperation = if (clear) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                                clearColor = if (clear) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
                                depthReadOnly = true, stencilReadOnly = depthId == null,
                                stencilClearValue = if (pass is PlanPass.StencilGeometryProducerV3) 0u else null,
                                stencilLoadOperation = depthId?.let { if (pass is PlanPass.StencilGeometryProducerV3)
                                    GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load },
                                stencilStoreOperation = depthId?.let { GPUPreparedNativeStoreOperation.Store }),
                            commands, render.drawPackets.map { requireNotNull(it.semanticPayload) }, w6aPassV1 = pass)
                    }
                    is PlanPass.LayerComposite -> {
                        val filter = pass.restore.colorFilter
                        val destinationRead = pass.restore.blend as? BlendPlan.DestinationReadV1
                        val entries = buildList {
                            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
                            if (filter != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                                    minBindingSize = maxOf(16L, filter.dynamicByteCountI64).toULong())))
                            if (destinationRead != null) add(BindGroupLayoutEntry(2u, GPUShaderStage.Fragment,
                                texture = TextureBindingLayout()))
                        }
                        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = entries)))
                        val source = pass.copySourceBoundsLayerI32()
                        val destination = pass.copyDestinationOriginParentI32()
                        val colorDeclaration = filter?.let { execution ->
                            "struct W5fMaterialBlock { words: array<vec4<u32>, ${maxOf(1L, (execution.dynamicByteCountI64 + 15L) / 16L)}>, }\n" +
                                "@group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterialBlock;\n" +
                                "fn w6a_restore_filter(input: vec4<f32>) -> vec4<f32> {\n" +
                                W5fColorOperationEmitterV1.emit(execution.copyOperationGraph(), "input", 0L) + "}\n"
                        }.orEmpty()
                        val formula = destinationRead?.let { blend ->
                            requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(blend.mode.name.lowercase(), "w6a_restore_blend"))
                        }.orEmpty()
                        val filterExpression = if (filter == null) "alpha_applied" else "w6a_restore_filter(alpha_applied)"
                        val blendExpression = if (destinationRead == null) filterExpression else
                            "w6a_restore_blend($filterExpression, textureLoad(destination_snapshot, vec2<i32>(position.xy), 0))"
                        val snapshotDeclaration = if (destinationRead == null) "" else
                            "@group(0) @binding(2) var destination_snapshot: texture_2d<f32>;"
                        val shader = W6A_VERTEX_SHADER + """
                            @group(0) @binding(0) var layer_source: texture_2d<f32>;
                            $colorDeclaration
                            $snapshotDeclaration
                            $formula
                            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                                let alpha_applied = textureLoad(layer_source, vec2<i32>(position.xy) - vec2<i32>(${destination.x}, ${destination.y}) + vec2<i32>(${source.left}, ${source.top}), 0) * ${pass.restore.alphaF32};
                                return $blendExpression;
                            }
                        """
                        val pipeline = pipeline(shader, layout, w6aColorTarget(pass.restore.blend), owned)
                        val bindings = buildList {
                            add(BindGroupEntry(0u, views.getValue(pass.source)))
                            if (filter != null) add(BindGroupEntry(1u, BufferBinding(uniform,
                                requireNotNull(pass.restore.colorFilterUniformOffsetI64).toULong(),
                                maxOf(16L, filter.dynamicByteCountI64).toULong())))
                            if (destinationRead != null) add(BindGroupEntry(2u, views.getValue(requireNotNull(destinationRead.snapshotResource))))
                        }
                        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = bindings)))
                        renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(pass.destination), generation)),
                            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                                GPUPreparedNativeRenderCommand.SetScissor(destination.x, destination.y, source.width(), source.height()),
                                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0))),
                            operationKindOverride = GPUEncoderOperationKind.LayerComposite, w6aPassV1 = pass)
                    }
                    is PlanPass.PictureAggregateBeginPass, is PlanPass.FilterSourceClear -> {
                        val target = when (pass) {
                            is PlanPass.PictureAggregateBeginPass -> pass.target
                            is PlanPass.FilterSourceClear -> pass.output
                        }
                        renderOperands += emptyRender(stepIndex, views.getValue(target), generation, clear = true, pass, owned)
                    }
                    is PlanPass.FilterCoverageSourcePass -> {
                        val outputOrigin = frame.targetOriginDeviceI32(pass.output)
                        val extent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                        when (val binding = pass.rasterBinding) {
                            null -> pass.sealedAlphaSource?.let { alpha ->
                                val inputOrigin = frame.targetOriginDeviceI32(alpha.sealedSourceId)
                                renderOperands += textureRender(stepIndex, views.getValue(pass.output), views.getValue(alpha.sealedSourceId), generation,
                                    W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.alphaCoverageFragment(
                                        inputOrigin.x, inputOrigin.y, outputOrigin.x, outputOrigin.y,
                                    ), BlendPlan.LegacySrcOverV1, 0, 0, extent.width, extent.height, pass, owned)
                            } ?: run {
                                // Task 3's image-only witness owns no mask producer and remains
                                // transparent.  This is plan-published absence, not discovery.
                                renderOperands += emptyRender(stepIndex, views.getValue(pass.output), generation, clear = true, pass, owned)
                            }
                            else -> when (binding.draw) {
                                is SolidRectDraw -> {
                                renderOperands += coverageSolidRectRender(stepIndex, views.getValue(pass.output), generation,
                                    extent.width, extent.height, pass, owned)
                                }
                                else -> renderOperands += coverageRasterRender(stepIndex, views.getValue(pass.output),
                                    binding.depthStencil?.let(views::get), generation, frame, step as? GPUFrameStep.RenderPassStep
                                        ?: error("W6b coverage requires its frozen render step"), pass, binding,
                                    geometryBuffers, uniform, owned)
                            }
                        }
                    }
                    is PlanPass.FilterCoverageRetainPass -> {
                        val inputOrigin = frame.targetOriginDeviceI32(pass.source)
                        val outputOrigin = frame.targetOriginDeviceI32(pass.output)
                        val extent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                        renderOperands += textureRender(stepIndex, views.getValue(pass.output), views.getValue(pass.source), generation,
                            sampledCompositeShader(outputOrigin.x - inputOrigin.x, outputOrigin.y - inputOrigin.y, 1f),
                            BlendPlan.LegacySrcOverV1, 0, 0, extent.width, extent.height, pass, owned)
                    }
                    is PlanPass.PictureAggregateSealPass -> {
                        renderOperands += emptyRender(stepIndex, views.getValue(pass.aggregateTarget), generation, clear = false, pass, owned)
                    }
                    is PlanPass.PictureSourcePass -> {
                        val operand = pass.graphTextureOperand
                        if (operand == null) {
                            val layerInput = requireNotNull(pass.layerInput) {
                                "W6b non-graph Picture source must retain its frozen layer input."
                            }
                            val inputOrigin = frame.targetOriginDeviceI32(layerInput)
                            val outputOrigin = frame.targetOriginDeviceI32(pass.output)
                            val extent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                            renderOperands += textureRender(stepIndex, views.getValue(pass.output), views.getValue(layerInput), generation,
                                sampledCompositeShader(outputOrigin.x - inputOrigin.x, outputOrigin.y - inputOrigin.y, 1f),
                                BlendPlan.LegacySrcOverV1, 0, 0, extent.width, extent.height, pass, owned)
                        } else {
                            val inputOrigin = frame.targetOriginDeviceI32(operand.sealedSourceId)
                            val outputOrigin = frame.targetOriginDeviceI32(pass.output)
                            val extent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                            val shader = W6A_VERTEX_SHADER + """
                                @group(0) @binding(0) var picture_source: texture_2d<f32>;
                                @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                                    let source_position = vec2<i32>(position.xy) + vec2<i32>(${outputOrigin.x - inputOrigin.x}, ${outputOrigin.y - inputOrigin.y});
                                    let source_extent = vec2<i32>(textureDimensions(picture_source));
                                    if (source_position.x < 0 || source_position.y < 0 || source_position.x >= source_extent.x || source_position.y >= source_extent.y) {
                                        return vec4<f32>(0.0);
                                    }
                                    return textureLoad(picture_source, source_position, 0);
                                }
                            """
                            renderOperands += pictureSourceRender(stepIndex, views.getValue(pass.output), views.getValue(operand.sealedSourceId),
                                null, null, generation, shader, 0, 0, extent.width, extent.height, pass, owned)
                        }
                    }
                    is PlanPass.FilterPass -> {
                        val outputOrigin = frame.targetOriginDeviceI32(pass.output)
                        val outputExtent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                        when (val operation = pass.operation) {
                            is FilterPassOperationV1.SeparableBlur -> {
                                require(operation.kind in setOf(
                                    FilterImplementationKindV1.IMAGE_BLUR_X,
                                    FilterImplementationKindV1.IMAGE_BLUR_Y,
                                    FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                                    FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
                                )) { "W6b cannot materialize ${operation.kind}." }
                                require(pass.inputs().size == 1)
                                val input = pass.inputs().single()
                                val inputOrigin = frame.targetOriginDeviceI32(input)
                                val known = operation.bounds.copyKnownContentDeviceI32()
                                    ?: operation.bounds.copyRequiredInputDeviceI32()
                                val shader = W6A_VERTEX_SHADER + W6bSeparableBlurSnippet.fragment(
                                    operation.axis,
                                    operation.sigmaF32,
                                    operation.tileMode,
                                    inputOrigin.x,
                                    inputOrigin.y,
                                    outputOrigin.x,
                                    outputOrigin.y,
                                    known.left,
                                    known.top,
                                    known.right,
                                    known.bottom,
                                    transparentOutsideSource = operation.kind in setOf(
                                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
                                    ),
                                )
                                renderOperands += textureRender(stepIndex, views.getValue(pass.output), views.getValue(input), generation,
                                    shader, BlendPlan.LegacySrcOverV1, 0, 0, outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.MaskBlurStyle -> {
                                val blurredOrigin = frame.targetOriginDeviceI32(operation.blurredCoverageSource)
                                val original = operation.originalCoverageSource
                                renderOperands += maskStyleRender(stepIndex, views.getValue(pass.output),
                                    views.getValue(operation.blurredCoverageSource), original?.let(views::get), generation,
                                    operation.style, blurredOrigin.x, blurredOrigin.y, outputOrigin.x, outputOrigin.y,
                                    original?.let(frame::targetOriginDeviceI32)?.x, original?.let(frame::targetOriginDeviceI32)?.y,
                                    outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.MaterializedSource -> {
                                require(pass.inputs().size == 2)
                                val input = pass.inputs().first()
                                val inputOrigin = frame.targetOriginDeviceI32(input)
                                val coverage = pass.inputs().last()
                                val coverageOrigin = frame.targetOriginDeviceI32(coverage)
                                renderOperands += maskedMaterialSourceRender(stepIndex, views.getValue(pass.output), views.getValue(input),
                                    views.getValue(coverage), generation, inputOrigin.x, inputOrigin.y, coverageOrigin.x,
                                    coverageOrigin.y, outputOrigin.x, outputOrigin.y,
                                    materialSourceAlphaReplacement.getValue(input),
                                    outputExtent.width, outputExtent.height, pass, owned)
                            }
                            else -> error("W6b Task 4 materializes only frozen mask blur and W5 source operations.")
                        }
                    }
                    is PlanPass.PictureComposite -> {
                        val operands = requireNotNull(pass.operands) { "W6b Picture composite needs frozen operands." }
                        val source = operands.copySourceBoundsTargetI32()
                        val destination = operands.copyDestinationOriginTargetI32()
                        val scissor = pictureCompositeScissor(frame, pass.destination, source, destination, operands)
                        val operand = graphTextureOperandsBySource[pass.source]
                        renderOperands += if (scissor == null) emptyRender(stepIndex, views.getValue(pass.destination), generation,
                            clear = false, pass, owned) else if (operand == null) {
                            textureRender(stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                sampledCompositeShader(source.left - destination.x, source.top - destination.y, 1f), operands.blend,
                                scissor.left, scissor.top, scissor.width(), scissor.height(), pass, owned)
                        } else {
                            require(operand.finalBlend.canonicalLabel == operands.blend.canonicalLabel) {
                                "W6b Picture composite blend differs from its frozen graph-texture operand."
                            }
                            val filter = operand.colorFilter
                            val filterOffset = filter?.let { requireNotNull(operand.colorFilterUniformOffsetI64) }
                            val filterCapacity = filter?.let { requireNotNull(operand.colorFilterUniformByteCountI64) }
                            val filterBuffer = filter?.let { execution ->
                                val offset = requireNotNull(filterOffset)
                                val capacity = requireNotNull(filterCapacity)
                                val bindingBytes = maxOf(16L, execution.dynamicByteCountI64)
                                require(Math.addExact(offset, bindingBytes) <= capacity)
                                graphTextureUniformBuffers.getValue(operand.uniformResource).also { buffer ->
                                    if (execution.dynamicByteCountI64 > 0L)
                                        queue.writeBuffer(buffer, offset.toULong(), ArrayBuffer.of(execution.copyDynamicBytes()))
                                }
                            }
                            filteredCompositeRender(
                                stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                source, destination, operand.alphaF32, filter, filterBuffer,
                                if (filter == null) null else 0L, filterCapacity, filterOffset?.div(4L) ?: 0L,
                                (operands.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get),
                                operands.blend, pass, owned, scissor,
                            )
                        }
                    }
                    is PlanPass.FilterComposite -> {
                        val source = pass.copySourceBoundsTargetI32()
                        val destination = pass.copyDestinationOriginParentI32()
                        when (val operation = pass.operation) {
                            is FilterCompositeOperationV1.Draw -> renderOperands += textureRender(
                                stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                sampledCompositeShader(source.left - destination.x, source.top - destination.y, 1f), operation.blend,
                                destination.x, destination.y, source.width(), source.height(), pass, owned,
                            )
                            is FilterCompositeOperationV1.Layer -> {
                                val restore = operation.restore
                                val destinationRead = restore.blend as? BlendPlan.DestinationReadV1
                                renderOperands += filteredCompositeRender(
                                    stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation, source, destination,
                                    restore.alphaF32, restore.colorFilter, uniform, restore.colorFilterUniformOffsetI64,
                                    restore.colorFilter?.let { maxOf(16L, it.dynamicByteCountI64) }, 0L,
                                    destinationRead?.snapshotResource?.let(views::get), restore.blend, pass, owned,
                                )
                            }
                            is FilterCompositeOperationV1.Picture -> {
                                val terminal = requireNotNull(operation.terminal)
                                val scissor = pictureCompositeScissor(frame, pass.destination, source, destination, terminal)
                                val operand = graphTextureOperandsBySource[pass.evaluationKey.boundSourceId]
                                renderOperands += if (scissor == null) {
                                    emptyRender(stepIndex, views.getValue(pass.destination), generation, clear = false, pass, owned)
                                } else if (operand == null) {
                                    // Inner Picture draws already carry their W5 material in the
                                    // filter source.  They have no parent graph-texture operand,
                                    // but their frozen terminal still owns a destination snapshot
                                    // and exact blend.
                                    filteredCompositeRender(
                                        stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                        source, destination, 1f, null, null, null, null, 0L,
                                        (terminal.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get),
                                        terminal.blend, pass, owned, scissor,
                                    )
                                } else {
                                    require(operand.finalBlend.canonicalLabel == terminal.blend.canonicalLabel) {
                                        "W6b Picture terminal blend differs from its frozen graph-texture operand."
                                    }
                                    val filter = operand.colorFilter
                                    val filterOffset = filter?.let { requireNotNull(operand.colorFilterUniformOffsetI64) }
                                    val filterCapacity = filter?.let { requireNotNull(operand.colorFilterUniformByteCountI64) }
                                    val filterBuffer = filter?.let { execution ->
                                        val offset = requireNotNull(filterOffset)
                                        val capacity = requireNotNull(filterCapacity)
                                        val bindingBytes = maxOf(16L, execution.dynamicByteCountI64)
                                        require(Math.addExact(offset, bindingBytes) <= capacity)
                                        graphTextureUniformBuffers.getValue(operand.uniformResource).also { buffer ->
                                            if (execution.dynamicByteCountI64 > 0L)
                                                queue.writeBuffer(buffer, offset.toULong(), ArrayBuffer.of(execution.copyDynamicBytes()))
                                        }
                                    }
                                    filteredCompositeRender(
                                        stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation, source, destination,
                                        operand.alphaF32, filter, filterBuffer, if (filter == null) null else 0L, filterCapacity,
                                        filterOffset?.div(4L) ?: 0L,
                                        (terminal.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get), terminal.blend,
                                        pass, owned, scissor,
                                    )
                                }
                            }
                        }
                    }
                    is PlanPass.ReadbackPass -> {
                        val output = resources.outputOwnedReadbacks.single()
                        val staging = frame.physical.resource(pass.staging)
                        require(output.stagingLease.backingBufferBytes == staging.byteSize && output.layout.paddedBytesPerRow == pass.bytesPerRow &&
                            output.layout.totalBufferBytes == pass.mappedBytesI64)
                        val buffer = device.createBuffer(BufferDescriptor(size = staging.byteSize.toULong(),
                            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst, label = "w6a.readback"))
                        readbackBuffer = buffer
                        renderOperands += GPUPreparedNativeScopeOperand.Readback(stepIndex,
                            GPUPreparedNativeTextureOperand(rootTexture, generation),
                            GPUPreparedNativeBufferOperand(buffer, generation, GPUPreparedNativeOperandOwnership.OutputOwnedReadback),
                            GPUPreparedNativeReadbackLayout(0, 0, graph.targetExtent.width, graph.targetExtent.height, pass.bytesPerRow,
                                graph.targetExtent.height, 0L, requireNotNull(pass.mappedBytesI64), GPUTextureFormat.RGBA8UnormSrgb))
                    }
                    is PlanPass.TextureCopy -> {
                        val region = requireNotNull(pass.copySourceBoundsI32())
                        renderOperands += GPUPreparedNativeScopeOperand.Copy(stepIndex, GPUEncoderOperationKind.Copy,
                            GPUPreparedNativeTextureOperand(textures.getValue(pass.source), generation),
                            GPUPreparedNativeTextureOperand(textures.getValue(pass.destination), generation),
                            GPUPreparedNativeTextureCopyLayout(region.left, region.top, pass.copyDestinationOriginI32().x,
                                pass.copyDestinationOriginI32().y, region.width(), region.height()))
                    }
                    else -> error("Unadmitted W6 native pass")
                }
            }
            val keys = encoderPlan.scopes.map { GPUPreparedNativeScopeKey(it.sourceStepIndex, it.operationKind, it.resourceGenerationLabels, it.nativeOperandKeys) }
            val payload = GPUPreparedNativeFramePayload(GPUPreparedNativeFrameIdentity(framePlan.frameId, encoderPlan.contextIdentity,
                encoderPlan.planId, generation, generationSeal.targetGeneration, keys), renderOperands,
                encoderPlan.scopes.map { it.nativeOperandKeys },
                listOf(GPUPreparedNativeAuxiliaryHandle(owned, GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion)) +
                    framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
                        .mapNotNull { frame.destinationCopy(it) }.distinct().map { copy ->
                            GPUPreparedNativeAuxiliaryHandle(GPUW6aDestinationNativeV1(frame, copy, views.getValue(copy.destination)),
                                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion) },
                pathDepthStencilViewAuthority = pathViews)
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(GPUPreparedNativeFrameDraft(payload))
        } catch (failure: Throwable) {
            val cleanup = AutoCloseable {
                owned.close()
                readbackBuffer?.close()
                readbackBuffer = null
            }
            val retained = if (runCatching { cleanup.close() }.isSuccess) null else cleanup
            return GPUPreparedNativeFramePayloadMaterialization.Refused("w6a.layer.native_materialization",
                "Layer native materialization failed: ${failure.message.orEmpty()}", retainedCloseOwner = retained)
        }
    }

    /**
     * Consumes a sealed terminal source.  The caller provides only plan-published alpha,
     * color-filter binding, blend and destination snapshot facts; this lowers no scene state.
     */
    private fun filteredCompositeRender(
        stepIndex: Int,
        target: GPUTextureView,
        sourceTexture: GPUTextureView,
        generation: GPUDeviceGenerationID,
        source: RectI32,
        destination: org.graphiks.math.geometry.Point2I32,
        alpha: Float,
        filter: ColorFilterExecutionPlanV1?,
        filterBuffer: GPUBuffer?,
        filterBufferOffsetI64: Long?,
        filterBindingByteCountI64: Long?,
        filterWordOffsetI64: Long,
        destinationSnapshot: GPUTextureView?,
        blend: BlendPlan,
        pass: PlanPass,
        owned: W6aOwnedHandles,
        scissorOverride: RectI32? = null,
    ): GPUPreparedNativeScopeOperand.Render {
        val destinationRead = blend as? BlendPlan.DestinationReadV1
        require((destinationRead != null) == (destinationSnapshot != null))
        val entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (filter != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                    minBindingSize = requireNotNull(filterBindingByteCountI64).toULong())))
            if (destinationRead != null) add(BindGroupLayoutEntry(2u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        }
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = entries)))
        val colorDeclaration = filter?.let { execution ->
            "struct W5fMaterialBlock { words: array<vec4<u32>, ${maxOf(1L, (requireNotNull(filterBindingByteCountI64) + 15L) / 16L)}>, }\n" +
                "@group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterialBlock;\n" +
                "fn w6b_terminal_filter(input: vec4<f32>) -> vec4<f32> {\n" +
                W5fColorOperationEmitterV1.emit(execution.copyOperationGraph(), "input", filterWordOffsetI64) + "}\n"
        }.orEmpty()
        val formula = destinationRead?.let { selected ->
            requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(selected.mode.name.lowercase(), "w6b_terminal_blend"))
        }.orEmpty()
        val filtered = if (filter == null) "alpha_applied" else "w6b_terminal_filter(alpha_applied)"
        val output = if (destinationRead == null) filtered else
            "w6b_terminal_blend($filtered, textureLoad(destination_snapshot, vec2<i32>(position.xy), 0))"
        val snapshotDeclaration = if (destinationRead == null) "" else
            "@group(0) @binding(2) var destination_snapshot: texture_2d<f32>;"
        val shader = W6A_VERTEX_SHADER + """
            @group(0) @binding(0) var terminal_source: texture_2d<f32>;
            $colorDeclaration
            $snapshotDeclaration
            $formula
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let alpha_applied = textureLoad(terminal_source,
                    vec2<i32>(position.xy) - vec2<i32>(${destination.x}, ${destination.y}) + vec2<i32>(${source.left}, ${source.top}), 0) * $alpha;
                return $output;
            }
        """
        val pipeline = pipeline(shader, layout, w6aColorTarget(blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, sourceTexture))
            if (filter != null) add(BindGroupEntry(1u, BufferBinding(requireNotNull(filterBuffer),
                requireNotNull(filterBufferOffsetI64).toULong(), requireNotNull(filterBindingByteCountI64).toULong())))
            if (destinationRead != null) add(BindGroupEntry(2u, requireNotNull(destinationSnapshot)))
        })))
        val scissor = scissorOverride ?: RectI32(destination.x, destination.y,
            Math.addExact(destination.x, source.width()), Math.addExact(destination.y, source.height()))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            operationKindOverride = if (pass is PlanPass.LayerComposite) GPUEncoderOperationKind.LayerComposite else null,
            w6aPassV1 = pass,
        )
    }

    /** Applies the typed deferred clip only at the frozen Picture terminal. */
    private fun pictureCompositeScissor(
        frame: GPUW6aLayerFramePlan,
        destinationTarget: PlanResourceId,
        source: RectI32,
        destination: org.graphiks.math.geometry.Point2I32,
        operands: PictureCompositeOperandsV1,
    ): RectI32? {
        val result = RectI32(destination.x, destination.y,
            Math.addExact(destination.x, source.width()), Math.addExact(destination.y, source.height()))
        val deviceClip = when (val clip = operands.deferredClip) {
            ClipStackNode.Empty -> return result
            is ClipStackNode.DeviceRect -> {
                val bounds = clip.copyBounds()
                if (bounds.isEmpty) return null
                requireNotNull(operands.copyClipToDeviceF64().mapRectBoundsF64OrNull(RectF64(
                    bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
                ))?.toExactDeviceRectI32OrNull()) { "W6b Picture deferred clip is not an exact device scissor." }
            }
            is ClipStackNode.Operations -> error("Task 3 requires a typed DeviceRect deferred Picture clip.")
        }
        val origin = frame.targetOriginDeviceI32(destinationTarget)
        val local = RectI32(
            Math.subtractExact(deviceClip.left, origin.x), Math.subtractExact(deviceClip.top, origin.y),
            Math.subtractExact(deviceClip.right, origin.x), Math.subtractExact(deviceClip.bottom, origin.y),
        )
        return result.takeIf { it.intersect(local) }
    }

    /** The plan has already admitted only integral hard-edge clips; never widen one at lowering. */
    private fun RectF64.toExactDeviceRectI32OrNull(): RectI32? {
        fun coordinate(value: Double): Int? = if (value.isFinite() &&
            value >= Int.MIN_VALUE.toDouble() && value <= Int.MAX_VALUE.toDouble() &&
            value == value.toLong().toDouble()) value.toInt() else null
        val left = coordinate(left) ?: return null
        val top = coordinate(top) ?: return null
        val right = coordinate(right) ?: return null
        val bottom = coordinate(bottom) ?: return null
        return RectI32(left, top, right, bottom)
    }

    /** Materializes one published graph-texture source and, when present, its frozen W5 filter row. */
    private fun pictureSourceRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        filterBuffer: GPUBuffer?,
        filterBindingByteCountI64: Long?,
        generation: GPUDeviceGenerationID,
        shader: String,
        scissorX: Int,
        scissorY: Int,
        scissorWidth: Int,
        scissorHeight: Int,
        pass: PlanPass.PictureSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(scissorWidth > 0 && scissorHeight > 0)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (filterBindingByteCountI64 != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform, minBindingSize = filterBindingByteCountI64.toULong())))
        })))
        val pipeline = pipeline(shader, layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, source))
            if (filterBindingByteCountI64 != null) add(BindGroupEntry(1u, BufferBinding(requireNotNull(filterBuffer),
                0uL, filterBindingByteCountI64.toULong())))
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissorX, scissorY, scissorWidth, scissorHeight),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Rasterizes an already-issued W4 solid-rect lane as raw, unshaded coverage. */
    private fun coverageSolidRectRender(
        stepIndex: Int,
        target: GPUTextureView,
        generation: GPUDeviceGenerationID,
        widthI32: Int,
        heightI32: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = emptyList())))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.solidRectCoverageFragment(), layout,
            w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = emptyList())))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, widthI32, heightI32),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Emits the exact frozen W4 geometry as raw coverage, never as a reconstructed source draw. */
    private fun coverageRasterRender(
        stepIndex: Int,
        target: GPUTextureView,
        depthStencil: GPUTextureView?,
        generation: GPUDeviceGenerationID,
        frame: GPUW6aLayerFramePlan,
        render: GPUFrameStep.RenderPassStep,
        pass: PlanPass.FilterCoverageSourcePass,
        coverageBinding: PlanPass.W6bRasterCoverageBindingV1,
        geometryBuffers: Map<PlanResourceId, GPUBuffer>,
        fallbackUniform: GPUBuffer,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val draw = coverageBinding.draw
        require(draw !is SolidRectDraw && draw !is W5bVerticesDraw) {
            "W6b coverage raster requires one admitted W4 analytic or path producer."
        }
        val data = requireNotNull(coverageBinding.drawDataResources)
        val coverBinding = requireNotNull(frame.physical.geometryBinding(pass.id))
        val packets = render.drawPackets
        val stencil = coverageBinding.depthStencil != null
        require(packets.size == if (stencil) 2 else 1)
        val commands = buildList {
            packets.forEachIndexed { packetIndexI32, packet ->
                val producer = stencil && packetIndexI32 == 0
                val mapped = requireNotNull(frame.geometryPipeline(packet)) {
                    "W6b coverage raster requires a mapped frozen W4 pipeline."
                }
                val template = frame.template(packet)
                val layout = owned.own(device.createBindGroupLayout(
                    corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity),
                ))
                val pipeline = if (producer) geometryPipeline(mapped, layout, owned, template)
                    else coverageGeometryPipeline(mapped, layout, requireNotNull(template), owned)
                val uniformPayload = frame.analyticUniform(packet)
                val nativeUniform = geometryBuffers[data.uniform] ?: fallbackUniform
                val fill = (draw as? PathDraw)?.copyPathGeometry()?.let { geometry -> when (geometry) {
                    is PathDrawGeometry.Fill -> geometry.valueF32
                    is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
                    else -> error("Unadmitted W6b coverage path geometry")
                } }
                val packed: W6bPackedGeometry = when (draw) {
                    is AnalyticRectDraw -> packW4RasterGeometry(listOf(draw.copyRasterBounds())).let {
                        W6bPackedGeometry(it.vertices, it.indices)
                    }
                    is AnalyticRRectDraw -> packW4RasterGeometry(listOf(draw.copyRasterBounds())).let {
                        W6bPackedGeometry(it.vertices, it.indices)
                    }
                    is W5bPointDraw -> W6bPackedGeometry(draw.copyVerticesF32(), draw.copyIndicesI32())
                    is PathDraw -> when {
                        producer -> requireNotNull(fill).copyStencilEdgeFanF32OrNull()?.let { fan ->
                            W6bPackedGeometry(fan.copyVerticesF32(), fan.copyIndicesI32())
                        } ?: error("W6b coverage stencil producer lacks its frozen edge fan")
                        stencil -> packW4RasterGeometry(listOf(draw.copyScissorI32())).let {
                            W6bPackedGeometry(it.vertices, it.indices)
                        }
                        else -> requireNotNull(fill).copyDirectTriangleF32OrNull()?.let { direct ->
                            W6bPackedGeometry(direct.copyVerticesF32(), direct.copyIndicesI32())
                        } ?: error("W6b coverage path lacks its frozen direct triangles")
                    }
                    else -> error("Unadmitted W6b coverage geometry")
                }
                val vertexOffset = if (producer) 0L else coverBinding.vertexOffsetI64
                val indexOffset = if (producer) 0L else coverBinding.indexOffsetI64
                val uniformOffset = if (producer) 0L else coverBinding.uniformOffsetI64
                val vertexBytes = Math.multiplyExact(packed.vertices.size.toLong(), 4L)
                val indexBytes = Math.multiplyExact(packed.indices.size.toLong(), 4L)
                require(Math.addExact(vertexOffset, vertexBytes) <= frame.physical.resource(data.vertex).byteSize &&
                    Math.addExact(indexOffset, indexBytes) <= frame.physical.resource(data.index).byteSize &&
                    Math.addExact(uniformOffset, uniformPayload.size.toLong()) <= frame.physical.resource(data.uniform).byteSize)
                queue.writeBuffer(geometryBuffers.getValue(data.vertex), vertexOffset.toULong(), ArrayBuffer.of(packed.vertices))
                queue.writeBuffer(geometryBuffers.getValue(data.index), indexOffset.toULong(), ArrayBuffer.of(packed.indices))
                queue.writeBuffer(nativeUniform, uniformOffset.toULong(), ArrayBuffer.of(uniformPayload))
                val bind = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
                    BindGroupEntry(0u, BufferBinding(nativeUniform, 0uL, uniformPayload.size.toULong())),
                ))))
                val scissor = when (draw) {
                    is AnalyticRectDraw -> draw.copyScissor()
                    is AnalyticRRectDraw -> draw.copyScissor()
                    is W5bPointDraw -> draw.copyScissorI32()
                    is PathDraw -> draw.copyScissorI32()
                    else -> error("Unadmitted W6b coverage scissor")
                }
                add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)))
                add(GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(bind, generation),
                    listOf(uniformOffset)))
                add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0,
                    GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.vertex), generation), vertexOffset, vertexBytes, 8L))
                add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                    GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.index), generation),
                    GPUPreparedNativeIndexFormat.Uint32, indexOffset, indexBytes))
                add(GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()))
                add(GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = packed.indices.size, firstIndex = 0, baseVertex = 0,
                    vertexCount = packed.vertices.size / 2, maxLocalIndex = packed.indices.max(),
                )))
            }
        }
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                depthStencilTarget = depthStencil?.let { GPUPreparedNativeTextureViewOperand(it, generation) },
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                depthReadOnly = true,
                stencilReadOnly = depthStencil == null,
                stencilClearValue = if (depthStencil == null) null else 0u,
                stencilLoadOperation = depthStencil?.let { GPUPreparedNativeLoadOperation.Clear },
                stencilStoreOperation = depthStencil?.let { GPUPreparedNativeStoreOperation.Store },
            ),
            commands,
            // These are the published W4 packets that the coverage operand consumes.
            // Retain the exact instances so prepared-surface validation observes the
            // same packet order as the frozen plan rather than a renderer-side proxy.
            render.drawPackets.map { requireNotNull(it.semanticPayload) },
            w6aPassV1 = pass,
        )
    }

    /** Applies one frozen mask style to its blurred input and optional original coverage. */
    private fun maskStyleRender(
        stepIndex: Int,
        target: GPUTextureView,
        blurred: GPUTextureView,
        original: GPUTextureView?,
        generation: GPUDeviceGenerationID,
        style: org.graphiks.kanvas.render.ir.MaskBlurStyle,
        blurredOriginDeviceXI32: Int,
        blurredOriginDeviceYI32: Int,
        outputOriginDeviceXI32: Int,
        outputOriginDeviceYI32: Int,
        originalOriginDeviceXI32: Int?,
        originalOriginDeviceYI32: Int?,
        widthI32: Int,
        heightI32: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require((style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) == (original == null))
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (original != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        })))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskStyleFragment(
            style, blurredOriginDeviceXI32, blurredOriginDeviceYI32, outputOriginDeviceXI32, outputOriginDeviceYI32,
            originalOriginDeviceXI32, originalOriginDeviceYI32,
        ), layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, blurred))
            original?.let { add(BindGroupEntry(1u, it)) }
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, widthI32, heightI32),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Applies exactly one frozen mask to an already materialized source texture. */
    private fun maskedMaterialSourceRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        coverage: GPUTextureView,
        generation: GPUDeviceGenerationID,
        sourceOriginDeviceXI32: Int,
        sourceOriginDeviceYI32: Int,
        coverageOriginDeviceXI32: Int,
        coverageOriginDeviceYI32: Int,
        outputOriginDeviceXI32: Int,
        outputOriginDeviceYI32: Int,
        replacesSourceAlpha: Boolean,
        widthI32: Int,
        heightI32: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
            BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskedMaterialSourceFragment(
            sourceOriginDeviceXI32, sourceOriginDeviceYI32, coverageOriginDeviceXI32, coverageOriginDeviceYI32,
            outputOriginDeviceXI32, outputOriginDeviceYI32, replacesSourceAlpha,
        ), layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source), BindGroupEntry(1u, coverage),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, widthI32, heightI32),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Emits exactly one frozen source->target fullscreen render; no pass selection occurs here. */
    private fun textureRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        shader: String,
        blend: BlendPlan,
        scissorX: Int,
        scissorY: Int,
        scissorWidth: Int,
        scissorHeight: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(scissorWidth > 0 && scissorHeight > 0)
        // A freshly allocated W6b source or filter target must begin transparent.
        // These fullscreen shaders intentionally emit transparent pixels outside the
        // frozen source domain; with source-over blend, loading uninitialized target
        // memory would otherwise preserve those undefined pixels.
        val clearTarget = pass is PlanPass.PictureSourcePass || pass is PlanPass.FilterPass
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(shader, layout, w6aColorTarget(blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (clearTarget) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (clearTarget) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissorX, scissorY, scissorWidth, scissorHeight),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            operationKindOverride = if (pass is PlanPass.LayerComposite) GPUEncoderOperationKind.LayerComposite else null,
            w6aPassV1 = pass,
        )
    }

    private fun emptyRender(
        stepIndex: Int,
        target: GPUTextureView,
        generation: GPUDeviceGenerationID,
        clear: Boolean,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = emptyList())))
        val pipeline = pipeline(W6A_VERTEX_SHADER + """
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                return vec4<f32>(0.0);
            }
        """, layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = emptyList())))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (clear) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (clear) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    private fun sampledCompositeShader(sourceOffsetX: Int, sourceOffsetY: Int, alpha: Float): String = W6A_VERTEX_SHADER + """
        @group(0) @binding(0) var w6b_source: texture_2d<f32>;
        @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            let source_position = vec2<i32>(position.xy) + vec2<i32>($sourceOffsetX, $sourceOffsetY);
            let source_extent = vec2<i32>(textureDimensions(w6b_source));
            if (source_position.x < 0 || source_position.y < 0 || source_position.x >= source_extent.x || source_position.y >= source_extent.y) {
                return vec4<f32>(0.0);
            }
            return textureLoad(w6b_source, source_position, 0) * $alpha;
        }
    """

    private fun PlanPass.materializesW6bMaskSourceV1(): Boolean = when (this) {
        is PlanPass.RenderPass -> w6bMaskSourceBinding != null
        is PlanPass.StencilCover -> coverageSource != null
        else -> false
    }

    private fun fullMaskMaterialPointGeometry(draw: W5bPointDraw, bounds: RectI32): Pair<FloatArray, IntArray> {
        val vertices = draw.copyVerticesF32()
        require(vertices.size % 8 == 0)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        for (offsetI32 in vertices.indices step 8) {
            vertices[offsetI32] = left
            vertices[offsetI32 + 1] = top
            vertices[offsetI32 + 2] = right
            vertices[offsetI32 + 3] = top
            vertices[offsetI32 + 4] = right
            vertices[offsetI32 + 5] = bottom
            vertices[offsetI32 + 6] = left
            vertices[offsetI32 + 7] = bottom
        }
        return vertices to draw.copyIndicesI32()
    }

    /** Reuses the frozen direct-path binding capacity while covering its published source extent. */
    private fun fullMaskMaterialTriangleGeometry(bounds: RectI32, vertexCountI32: Int,
        indexCountI32: Int): Pair<FloatArray, IntArray> {
        require(vertexCountI32 >= 3 && indexCountI32 >= 3 && indexCountI32 % 3 == 0)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val vertices = FloatArray(Math.multiplyExact(vertexCountI32, 2))
        vertices[0] = left
        vertices[1] = top
        vertices[2] = right * 2f - left
        vertices[3] = top
        vertices[4] = left
        vertices[5] = bottom * 2f - top
        for (offsetI32 in 6 until vertices.size step 2) {
            vertices[offsetI32] = left
            vertices[offsetI32 + 1] = top
        }
        return vertices to IntArray(indexCountI32) { indexI32 -> indexI32 % 3 }
    }

    private fun geometryPipeline(mapped: GPUWgpu4kCorePrimitivePipelineMapping.Mapped, groupZero: GPUBindGroupLayout,
        owned: W6aOwnedHandles, template: GPUW5aGeometryHostTemplateV1?, sourceBlend: BlendPlan? = null,
        sourceIgnoresDepthStencil: Boolean = false): GPURenderPipeline {
        require(!sourceIgnoresDepthStencil || sourceBlend != null)
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code =
            requireNotNull(corePrimitiveMaterialGeometryWgslV1(mapped.componentIdentity)))))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        val sourceIdentity = if (sourceIgnoresDepthStencil) mapped.identity.copy(
            program = GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            blendProgram = GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver,
        ) else mapped.identity
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(sourceIdentity, module, layout).let { descriptor ->
            sourceBlend?.let { blend ->
                val fragment = requireNotNull(descriptor.fragment)
                RenderPipelineDescriptor(
                    label = descriptor.label,
                    layout = descriptor.layout,
                    vertex = descriptor.vertex,
                    primitive = descriptor.primitive,
                    depthStencil = descriptor.depthStencil,
                    multisample = descriptor.multisample,
                    fragment = FragmentState(module = fragment.module, entryPoint = fragment.entryPoint,
                        targets = listOf(w6aColorTarget(blend)), constants = fragment.constants),
                )
            } ?: descriptor
        }
        return owned.own(device.createRenderPipeline(descriptor)).also { pipeline -> template?.let {
            owned.templates[pipeline] = GPUW5aGeometryPipelineTemplate(it.pipelineRecipeId, descriptor, groupZero,
                materialCoordinateSlot = it.materialCoordinateSlot)
        } }
    }

    private fun coverageGeometryPipeline(mapped: GPUWgpu4kCorePrimitivePipelineMapping.Mapped,
        groupZero: GPUBindGroupLayout, template: GPUW5aGeometryHostTemplateV1, owned: W6aOwnedHandles): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = composeW5aHostCoverageV1(template))))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        return owned.own(device.createRenderPipeline(corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, module, layout)))
    }

    private fun pipeline(shader: String, groupZero: GPUBindGroupLayout, target: ColorTargetState,
        owned: W6aOwnedHandles, template: GPUW5aGeometryHostTemplateV1? = null,
        vertices: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact? = null): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = shader)))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        val descriptor = RenderPipelineDescriptor(layout = layout, vertex = VertexState(module, entryPoint = template?.vertexEntryPoint ?: "vs_main",
            buffers = vertices?.let { listOf(preparedVerticesVertexLayoutV6(it.layout)) }.orEmpty()),
            fragment = FragmentState(module = module, targets = listOf(target), entryPoint = template?.fragmentEntryPoint ?: "fs_main"),
            primitive = PrimitiveState(topology = if (vertices?.topology == org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode.TriangleStrip)
                GPUPrimitiveTopology.TriangleStrip else GPUPrimitiveTopology.TriangleList,
                stripIndexFormat = if (vertices?.topology == org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode.TriangleStrip)
                    vertices.indexFormat?.let { if (it == "uint16") GPUIndexFormat.Uint16 else GPUIndexFormat.Uint32 } else null))
        return owned.own(device.createRenderPipeline(descriptor)).also { pipeline -> template?.let {
            owned.templates[pipeline] = GPUW5aGeometryPipelineTemplate(it.pipelineRecipeId, descriptor, groupZero,
                materialCoordinateSlot = it.materialCoordinateSlot)
        } }
    }
}

private data class W6bPackedGeometry(val vertices: FloatArray, val indices: IntArray)

private class W6aOwnedHandles : AutoCloseable, GPUW5aGeometryPipelineTemplateProvider {
    private val handles = mutableListOf<AutoCloseable>()
    val templates = java.util.IdentityHashMap<GPURenderPipeline, GPUW5aGeometryPipelineTemplate>()
    fun <T : AutoCloseable> own(value: T): T = value.also { handles += it }
    override fun sourceTemplate(pipeline: GPURenderPipeline): GPUW5aGeometryPipelineTemplate? = templates[pipeline]
        ?: handles.filterIsInstance<GPUW5aGeometryPipelineTemplateProvider>().firstNotNullOfOrNull { it.sourceTemplate(pipeline) }
    override fun close() {
        var failure: Throwable? = null
        val iterator = handles.listIterator(handles.size)
        while (iterator.hasPrevious()) try { iterator.previous().close(); iterator.remove() }
        catch (error: Throwable) { if (failure == null) failure = error else failure.addSuppressed(error) }
        failure?.let { throw it }
    }
}

/** Whether the W5 source stage sealed alpha from the graph-texture coverage edge. */
private fun frozenMaterialSourceAlphaReplacement(graph: RenderGraph): Map<PlanResourceId, Boolean> =
    buildMap {
        graph.passes().forEach { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.coverageSource?.let {
                // A draw-owned W6b coverage edge is the explicit, frozen replacement
                // for the source shape alpha.  Retaining the source raster alpha here
                // would multiply the shape coverage twice before FilterComposite.
                put(pass.target, pass.w6bMaskSourceBinding != null)
            }
            is PlanPass.StencilCover -> pass.coverageSource?.let {
                // The paired stencil source is the same transparent W6b auto-layer
                // contract as RenderPass.  Its W4 producer supplies coverage separately.
                put(pass.target, true)
            }
            is PlanPass.PictureSourcePass -> pass.coverageSource?.let {
                put(pass.output, true)
            }
            else -> Unit
        } }
    }
