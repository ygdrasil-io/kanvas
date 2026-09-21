package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.W5fColorOperationEmitterV1
import org.graphiks.kanvas.gpu.renderer.recording.*

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
            val uniform = owned.own(device.createBuffer(BufferDescriptor(size = geometryUniform.byteSize.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst, label = "w6a.geometry.uniform")))
            val uniformBytes = ByteArray(Math.toIntExact(geometryUniform.byteSize))
            graph.passes().filterIsInstance<PlanPass.LayerComposite>().forEach { composite ->
                val filter = composite.restore.colorFilter ?: return@forEach
                val offset = requireNotNull(composite.restore.colorFilterUniformOffsetI64)
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
                                val layout = owned.own(device.createBindGroupLayout(if (mapped != null)
                                    corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity)
                                    else requireNotNull(template).groupZeroLayout.nativeDescriptorV1("w6a.rect.group0")))
                                val data = binding?.data
                                val verticesSemantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices
                                val pipeline = if (mapped == null) pipeline(requireNotNull(template).sourceWgsl, layout, w6aColorTarget(draw.blend), owned, template,
                                    verticesSemantic?.artifact)
                                    else geometryPipeline(mapped, layout, owned, template)
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
                                    val (vertices, indices) = when (draw) {
                                        is AnalyticRectDraw -> packW4RasterGeometry(listOf(draw.copyRasterBounds())).let { it.vertices to it.indices }
                                        is AnalyticRRectDraw -> packW4RasterGeometry(listOf(draw.copyRasterBounds())).let { it.vertices to it.indices }
                                        is W5bPointDraw -> draw.copyVerticesF32() to draw.copyIndicesI32()
                                        is PathDraw -> {
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
                                val scissor = when (draw) {
                                    is SolidRectDraw -> draw.copyScissor()
                                    is AnalyticRectDraw -> draw.copyScissor()
                                    is AnalyticRRectDraw -> draw.copyScissor()
                                    is PathDraw -> draw.copyScissorI32()
                                    is W5bPointDraw -> draw.copyScissorI32()
                                    is W5bVerticesDraw -> draw.copyScissorI32()
                                    else -> error("Unadmitted W6 geometry")
                                }
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

    private fun geometryPipeline(mapped: GPUWgpu4kCorePrimitivePipelineMapping.Mapped, groupZero: GPUBindGroupLayout,
        owned: W6aOwnedHandles, template: GPUW5aGeometryHostTemplateV1?): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code =
            requireNotNull(corePrimitiveMaterialGeometryWgslV1(mapped.componentIdentity)))))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, module, layout)
        return owned.own(device.createRenderPipeline(descriptor)).also { pipeline -> template?.let {
            owned.templates[pipeline] = GPUW5aGeometryPipelineTemplate(it.pipelineRecipeId, descriptor, groupZero,
                materialCoordinateSlot = it.materialCoordinateSlot)
        } }
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
