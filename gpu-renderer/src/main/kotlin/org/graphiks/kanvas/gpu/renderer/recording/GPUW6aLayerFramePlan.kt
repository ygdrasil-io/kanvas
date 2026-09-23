package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.*
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.planning.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.*
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.execution.*
import org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage

internal fun w6aRenderPacketsMatch(pass: PlanPass, packets: List<GPUDrawPacket>): Boolean {
    val w4e = packets.singleOrNull()?.takeIf { it.role == GPUDrawPacketRole.W4ePrepared }
    if (w4e != null) return when (pass) {
        is PlanPass.RenderPass -> pass.draws().singleOrNull()?.let { it is PathDraw && it.commandIndex == w4e.commandIdValue } == true
        is PlanPass.StencilGeometryProducerV3 -> pass.commandIndexI32 == w4e.commandIdValue &&
            w4e.w4ePreparedPath?.phase == PathRenderPhase.SingleSampleStencilProducer
        is PlanPass.StencilCover -> pass.draw.commandIndex == w4e.commandIdValue
        is PlanPass.ClipMaskInitialize, is PlanPass.ClipMaskProducer, is PlanPass.ClipMaskFold -> w4e.w4ePreparedClipPass?.passId == pass.id.value
        else -> false
    }
    return when (pass) {
    is PlanPass.RenderPass -> packets.map { it.commandIdValue } == pass.draws().map { it.commandIndex } &&
        packets.all { it.role == GPUDrawPacketRole.Shading }
    is PlanPass.StencilGeometryProducerV3 -> packets.singleOrNull()?.let {
        it.commandIdValue == pass.commandIndexI32 && it.role == GPUDrawPacketRole.PathStencilProducer } == true
    is PlanPass.StencilCover -> packets.singleOrNull()?.let {
        it.commandIdValue == pass.draw.commandIndex && it.role == GPUDrawPacketRole.PathStencilCover } == true
    is PlanPass.LayerComposite,
    is PlanPass.PictureAggregateBeginPass,
    is PlanPass.PictureAggregateSealPass,
    is PlanPass.PictureSourcePass,
    is PlanPass.PictureComposite,
    is PlanPass.FilterPass,
    is PlanPass.FilterComposite,
    is PlanPass.FilterSourceClear,
    is PlanPass.FilterCoverageRetainPass,
    -> packets.isEmpty()
    is PlanPass.FilterCoverageSourcePass -> pass.rasterBinding?.let { binding -> when {
        binding.draw is SolidRectDraw -> packets.isEmpty()
        binding.depthStencil == null -> packets.size == 1 && packets.single().role == GPUDrawPacketRole.Shading
        else -> packets.size == 2 && packets[0].role == GPUDrawPacketRole.PathStencilProducer &&
            packets[1].role == GPUDrawPacketRole.PathStencilCover
    } } ?: packets.isEmpty()
    else -> false
}
}

/** Native-only view of a W5 row already sealed into a `MASK_SHADER` operation. */
internal data class GPUW6bMaskShaderMaterialV1(
    val binding: FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned,
    val stage: W5aMaterialSourceStage,
    val sourceInputWgsl: String,
)

/** Exact handle-free projection of a compiler-authenticated frame, including empty clear scopes. */
class GPUW6aLayerFramePlan internal constructor(private val request: GpuPlanLoweringRequest) {
    internal val graph: RenderGraph = request.graph
    private val framePlan = requireNotNull(graph.layerFramePlanOrNull())
    internal val physical = requireNotNull(graph.physicalLayoutOrNull())
    /**
     * The graph has already issued each mask occurrence's W5 row and uniform resource.  This
     * is a handle-free native projection of that exact row; it neither compiles a public Shader
     * nor adds a material/source/Picture authority to the graph.
     */
    private val maskShaderMaterialsByOccurrenceI32: Map<Int, GPUW6bMaskShaderMaterialV1> = graph.passes()
        .filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
            val binding = (pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding
                as? FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned ?: return@mapNotNull null
            val table = requireNotNull(graph.materialPlanTableOrNull())
            require(binding.materialAuthority.materialPlanRef() == binding.material)
            val stage = when (val authority = binding.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV5,
                is PlanDrawMaterialAuthority.MaterialV4,
                -> requireNotNull(W5aMaterialSourceStage.colorV4(
                    table,
                    authority,
                    graph.packedMaterialSourceV4(authority),
                ))
                is PlanDrawMaterialAuthority.MaterialV1 -> requireNotNull(W5aMaterialSourceStage.lower(
                    table, authority.ref, authority.coordinates))
                is PlanDrawMaterialAuthority.MaterialV2 -> requireNotNull(W5aMaterialSourceStage.lower(
                    table, authority.ref, authority.coordinates))
                else -> error("W6b MaskShader has no frozen W5 material authority.")
            }
            binding.occurrenceIdI32 to GPUW6bMaskShaderMaterialV1(binding, stage,
                if (stage.consumesDevicePositionF32)
                    "${stage.coordinateFunctionName}(device_position)" else "vec2<f32>(0.0)")
        }.toMap().also { rows ->
            require(rows.size == graph.passes().count { pass ->
                (pass as? PlanPass.FilterPass)?.operation is FilterPassOperationV1.MaskShader
            })
        }
    internal val w4eAuthorities = physical.w4eGeometryBindings().associateWith { GPUPlanW4ePreparedAuthority.issueLayered(graph, it) }
    private val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
    private val recording = GPURecordingSeal(request.recordingId, 0L, graph.id.value, graph.id.value, seal.sealHash)
    internal val refs: Map<PlanResourceId, GPUFrameResourceRef> = graph.resources().associate { resource ->
        val slot = physical.slot(resource.id)
        resource.id to if (resource.kind == PlanResourceKind.Texture2D) GPUFrameTargetRef("w6a.slot.${slot.slotI32}.${resource.id.value}")
            else GPUFrameBufferRef("w6a.slot.${slot.slotI32}.${resource.id.value}")
    }
    private val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
    internal val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w6a.${graph.id.value}.readback"), bounds,
        GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
    /** The lowerer consumes the compiler's sealed total order; it never reconstructs Picture work. */
    private val scheduledPasses: List<PlanPass> = framePlan.frozenPassSchedule().let { schedule ->
        val byId = graph.passes().associateBy { it.id }
        require(schedule.isNotEmpty() && schedule == graph.passes().map(PlanPass::id))
        schedule.map(byId::getValue)
    }
    private val templates = mutableMapOf<GPUDrawPacketID, GPUW5aGeometryHostTemplateV1>()
    private val analyticUniforms = mutableMapOf<GPUDrawPacketID, ByteArray>()
    private val geometryPipelines = mutableMapOf<GPUDrawPacketID, GPUWgpu4kCorePrimitivePipelineMapping.Mapped>()
    private val targetOriginsDeviceI32: Map<PlanResourceId, Point2I32> = buildMap {
        put(graph.resources().single { it.role == PlanResourceRole.LogicalTarget }.id, Point2I32.Origin)
        framePlan.scopes().forEach { scope ->
            put(scope.targetResource, scope.mapping.copyLayerOriginDeviceI32())
        }
        framePlan.pictureStreamAggregates().forEach { aggregate ->
            aggregate.aggregateTargetId?.let { put(it, aggregate.outerEvaluationMappingF64.copyLayerOriginDeviceI32()) }
        }
        graph.passes().filterIsInstance<PlanPass.PictureSourcePass>().forEach { pass ->
            pass.graphTextureOperand?.let { operand -> put(pass.output, operand.copyTargetOriginDeviceI32()) }
        }
        // The filter graph owns every source/output origin.  A mask style has two inputs
        // (blurred plus retained original), so only its output origin is introduced here;
        // the retained source inherits its exact already-published origin below.
        graph.passes().filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
            val bounds = pass.operation.bounds
            val boundedInput = when (pass.operation) {
                is FilterPassOperationV1.MaterializedSource -> pass.inputs().first()
                is FilterPassOperationV1.MaskBlurStyle -> null
                else -> pass.inputs().single()
            }
            // A MaskBlurStyle samples the preceding blurred target at its own published
            // origin.  All other single-input filters retain the historical input-origin
            // contract: the bounds' required input deliberately supersedes a provisional
            // Picture-source origin.
            boundedInput?.let { input -> put(input, Point2I32(
                bounds.copyRequiredInputDeviceI32().left,
                bounds.copyRequiredInputDeviceI32().top,
            )) }
            put(pass.output, bounds.copyTargetOriginDeviceI32())
        }
        graph.passes().filterIsInstance<PlanPass.FilterCoverageRetainPass>().forEach { pass ->
            put(pass.output, requireNotNull(get(pass.source)) {
                "A frozen retained coverage source needs its published input origin."
            })
        }
        // Task 3 clears image-only coverage witnesses but does not sample them: their source
        // W5 draw is already frozen in the following FilterSource pass.  Keep an explicit
        // local origin so this graph-only clear can be lowered without inventing coordinates.
        graph.passes().filterIsInstance<PlanPass.FilterCoverageSourcePass>().forEach { pass ->
            putIfAbsent(pass.output, Point2I32.Origin)
        }
    }
    internal fun targetOriginDeviceI32(resource: PlanResourceId): Point2I32 =
        targetOriginsDeviceI32[resource]?.let { Point2I32(it.x, it.y) }
            ?: error("Missing frozen W6 target origin for ${resource.value}")
    internal fun maskShaderMaterial(binding: FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned):
        GPUW6bMaskShaderMaterialV1 = requireNotNull(maskShaderMaterialsByOccurrenceI32[binding.occurrenceIdI32]) {
            "Missing frozen W6b mask-shader W5 row."
        }.also { issued ->
            require(issued.binding == binding) { "W6b mask-shader binding changed after graph publication." }
        }
    internal val memory: GPUFrameMemoryBudgetPlan
    internal val steps: List<GPUFrameStep>
    private val tasks: List<GPUTask>

    init {
        require(graph.verifyW6aLayerCompilerWitness())
        val verticesSource = if (graph.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }.any { it is W5bVerticesDraw })
            PreparedSourceFrameV6.layeredVertices(graph) else null
        val allocations = graph.resources().map { resource -> GPUFrameMemoryAllocation(refs.getValue(resource.id).value,
            when (resource.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.LayerTarget -> GPUFrameMemoryCategory.LayerTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, resource.byteSize,
            if (resource.kind == PlanResourceKind.Texture2D) GPUFrameMemoryResourceKind.Texture2D else GPUFrameMemoryResourceKind.Buffer,
            resource.copyExtent()?.let { GPUPixelBounds(0, 0, it.width, it.height) }, resource.firstPassIndex, resource.lastPassIndexExclusive) }
        memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            minOf(graph.budget.maxFrameLocalBytes, request.rendererAggregateMemoryBudgetBytes ?: Long.MAX_VALUE), requireNotNull(request.capabilities.limits)))
        require(memory.diagnostic == null && memory.targetResidentBytes + memory.peakFrameTransientBytes ==
            graph.peakFrameLocalBytes)
        val preparations = graph.resources().filter { it.kind == PlanResourceKind.Texture2D && it.lifetime == PlanResourceLifetime.FrameLocal ||
            it.role in setOf(PlanResourceRole.ReadbackStaging, PlanResourceRole.MaskTableData) || physical.w4eGeometryBindings().any { binding ->
                it.id in setOf(binding.payload.vertexResourceId, binding.payload.indexResourceId, binding.payload.uniformResourceId) } }
            .map { resource -> GPUResourcePreparationRequest(refs.getValue(resource.id),
                resource.copyExtent()?.let { GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, it.width, it.height),
                    when (resource.format) {
                        is PlanTextureFormat.DepthStencil -> GPUColorFormat("depth24plus-stencil8")
                        PlanTextureFormat.CoverageMask -> GPUColorFormat("rgba8unorm")
                        else -> GPUColorFormat.RGBA8UnormSrgb
                    }, resource.sampleCountI32) }
                    ?: GPUFrameBufferDescriptor(resource.byteSize, graph.capabilities.copyBytesPerRowAlignment.toLong()),
                when (resource.role) {
                    PlanResourceRole.LogicalTarget -> GPUFrameResourceRole.SceneTarget
                    PlanResourceRole.LayerTarget, PlanResourceRole.PictureAggregateSource,
                    PlanResourceRole.FilterSource, PlanResourceRole.FilterTransparentBlack,
                    PlanResourceRole.CoverageSource, PlanResourceRole.CoverageOriginal -> GPUFrameResourceRole.FilterTarget
                    PlanResourceRole.FilterTarget -> GPUFrameResourceRole.FilterTarget
                    PlanResourceRole.DestinationSnapshot -> GPUFrameResourceRole.DestinationSnapshot
                    PlanResourceRole.DepthStencil, PlanResourceRole.CoverageMaskDepthStencil -> GPUFrameResourceRole.PathDepthStencil
                    PlanResourceRole.CoverageMaskAccumulator, PlanResourceRole.CoverageMaskScratch,
                    PlanResourceRole.CoverageMaskMultisampleScratch -> GPUFrameResourceRole.ClipMask
                    PlanResourceRole.VertexData -> GPUFrameResourceRole.VertexData
                    PlanResourceRole.IndexData -> GPUFrameResourceRole.IndexData
                    PlanResourceRole.UniformData -> GPUFrameResourceRole.UniformData
                    PlanResourceRole.MaskTableData -> GPUFrameResourceRole.StorageData
                    else -> GPUFrameResourceRole.ReadbackStaging
                }, resource.usages().map { usage -> when (usage) {
                    PlanResourceUsage.RenderAttachment, PlanResourceUsage.DepthStencilAttachment -> GPUFrameResourceUsage.RenderAttachment
                    PlanResourceUsage.Sampled -> GPUFrameResourceUsage.TextureBinding
                    PlanResourceUsage.CopySource -> GPUFrameResourceUsage.CopySource
                    PlanResourceUsage.CopyDestination -> GPUFrameResourceUsage.CopyDestination
                    PlanResourceUsage.MapRead -> GPUFrameResourceUsage.MapRead
                    PlanResourceUsage.Vertex -> GPUFrameResourceUsage.Vertex
                    PlanResourceUsage.Index -> GPUFrameResourceUsage.Index
                    PlanResourceUsage.Uniform -> GPUFrameResourceUsage.Uniform
                    PlanResourceUsage.StorageRead -> GPUFrameResourceUsage.Storage
                } }.toSet(), GPUFrameResourceLifetime.FrameLocal, resource.byteSize, refs.getValue(resource.id).value) }
        steps = java.util.Collections.unmodifiableList(buildList {
            add(GPUFrameStep.PrepareResourcesStep(preparations, listOf(GPUTaskID("w6a.prepare"))))
            scheduledPasses.forEach { pass ->
                val task = listOf(GPUTaskID("w6a.${pass.id.value}"))
                val w4eBinding = physical.w4eGeometryBinding(pass.id)
                if (w4eBinding != null) {
                    val authority = w4eAuthorities.getValue(w4eBinding)
                    val native = requireNotNull(w4eBinding.nativePass(pass.id))
                    val builder = W4eClipGraphLowerer()
                    val path = native as? PlanPass.PathRenderPass
                    val color: PlanDraw? = when (pass) {
                        is PlanPass.RenderPass -> pass.draws().single() as? PathDraw
                        is PlanPass.StencilCover -> pass.draw
                        else -> null
                    }
                    val consumer = path?.takeUnless { it.phase == PathRenderPhase.SingleSampleStencilProducer }?.let { authority.consumerFor(it.id.value) }
                    val prepared = path?.let { requireNotNull(authority.pathFor(it.id.value)) }
                    val packet = if (prepared == null) builder.preparedClipPacket(native, pass.ordinal,
                        requireNotNull(authority.clipPassFor(native.id.value))) else builder.pathPacket(prepared, consumer, pass.ordinal,
                        color?.blend ?: BlendPlan.LegacySrcOverV1)
                    color?.let { draw -> packet.attachW5aSourceStageV2(org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2.issue(
                        requireNotNull(graph.materialPlanTableOrNull()), draw.materialAuthority, draw.commandIndex,
                        draw.materialAuthority.colorSourceCoordinatesV4()?.let { graph.packedMaterialSourceV4(draw.materialAuthority) })) }
                    val uses = builder.resourceUses(native, refs.mapKeys { it.key.value }, graph.resources().associateBy { it.id.value }, consumer, prepared,
                        PlanDrawDataResources(w4eBinding.payload.vertexResourceId, w4eBinding.payload.indexResourceId, w4eBinding.payload.uniformResourceId))
                    val targetId = when (native) {
                        is PlanPass.PathRenderPass -> native.target
                        is PlanPass.ClipMaskInitialize -> native.output
                        is PlanPass.ClipMaskProducer -> native.target
                        is PlanPass.ClipMaskFold -> native.output
                        else -> error("Unadmitted W4e native pass")
                    }
                    val samples = if (native is PlanPass.ClipMaskProducer && native.sampleCountI32 == 4)
                        GPUSamplePlan.MultisampleFrame(4) else GPUSamplePlan.SingleSampleFrame
                    add(GPUFrameStep.RenderPassStep(refs.getValue(targetId) as GPUFrameTargetRef,
                        GPULoadStorePlan(if (path == null) "clear" else "load", GPUStorePlan.Store), samples,
                        resourceUses = uses, drawPackets = listOf(packet), sourceTaskIds = task,
                        batches = listOf(GPUFrameRenderBatch("w6a.${pass.id.value}", GPUPassBatchKind.Isolated, listOf(packet), task)),
                        depthStencilLoadStore = path?.let(builder::depthStencilLoadStore), w6aPassV1 = pass))
                    return@forEach
                }
                when (pass) {
                    is PlanPass.RenderPass, is PlanPass.LayerComposite, is PlanPass.StencilGeometryProducerV3, is PlanPass.StencilCover,
                    is PlanPass.PictureAggregateBeginPass, is PlanPass.PictureAggregateSealPass,
                    is PlanPass.PictureSourcePass, is PlanPass.PictureComposite,
                    is PlanPass.FilterPass, is PlanPass.FilterComposite, is PlanPass.FilterSourceClear,
                    is PlanPass.FilterCoverageSourcePass, is PlanPass.FilterCoverageRetainPass -> {
                        val render = pass as? PlanPass.RenderPass
                        val targetId = when (pass) {
                            is PlanPass.RenderPass -> pass.target
                            is PlanPass.StencilGeometryProducerV3 -> pass.target
                            is PlanPass.StencilCover -> pass.target
                            is PlanPass.LayerComposite -> pass.destination
                            is PlanPass.PictureAggregateBeginPass -> pass.target
                            is PlanPass.PictureAggregateSealPass -> pass.aggregateTarget
                            is PlanPass.PictureSourcePass -> pass.output
                            is PlanPass.PictureComposite -> pass.destination
                            is PlanPass.FilterPass -> pass.output
                            is PlanPass.FilterComposite -> pass.destination
                            is PlanPass.FilterSourceClear -> pass.output
                            is PlanPass.FilterCoverageSourcePass -> pass.output
                            is PlanPass.FilterCoverageRetainPass -> pass.output
                        }
                        val depthId = when (pass) {
                            is PlanPass.StencilGeometryProducerV3 -> pass.depthStencil
                            is PlanPass.StencilCover -> pass.depthStencil
                            is PlanPass.FilterCoverageSourcePass -> pass.rasterBinding?.depthStencil
                            else -> null
                        }
                        val draws = when (pass) {
                            is PlanPass.RenderPass -> pass.draws()
                            is PlanPass.StencilGeometryProducerV3 -> listOf(graph.passes().filterIsInstance<PlanPass.StencilCover>()
                                .single { it.draw.commandIndex == pass.commandIndexI32 }.draw)
                            is PlanPass.StencilCover -> listOf(pass.draw)
                            else -> emptyList()
                        }
                        val packetInputs: List<Triple<PlanPass?, PlanDraw, Boolean>> = when (pass) {
                            is PlanPass.FilterCoverageSourcePass -> pass.rasterBinding?.let { binding -> when {
                                binding.draw is SolidRectDraw -> emptyList()
                                binding.depthStencil == null -> listOf(Triple(null, binding.draw, false))
                                else -> {
                                    val path = binding.draw as PathDraw
                                    listOf(
                                        Triple(null, path, true),
                                        Triple(null, path, false),
                                    )
                                }
                            } }.orEmpty()
                            else -> draws.map { Triple(pass, it, false) }
                        }
                        val targetExtent = requireNotNull(graph.resources().single { it.id == targetId }.copyExtent())
                        val targetBounds = GPUPixelBounds(0, 0, targetExtent.width, targetExtent.height)
                        val targetOrigin = targetOriginsDeviceI32.getValue(targetId)
                        val packets = packetInputs.map { (packetPass, draw, coverageProducer) ->
                            val table = requireNotNull(graph.materialPlanTableOrNull())
                            val packed = draw.materialAuthority.colorSourceCoordinatesV4()?.let { graph.packedMaterialSourceV4(draw.materialAuthority) }
                            val packet = when (draw) {
                                is W5bVerticesDraw -> lowerW5bVerticesDraw(draw, targetBounds, graph, requireNotNull(verticesSource),
                                    seal.sealHash, requireNotNull(physical.geometryBinding(pass.id)))
                                is SolidRectDraw -> GpuPlanTaskListLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, table, null, packed)
                                is W5bPointDraw -> GpuPlanTaskListLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, table, null, packed, graph)
                                is AnalyticRectDraw -> W4aAnalyticRectGraphLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, table, w5b = true, packedSourceV4 = packed,
                                    packetSuffix = if (pass is PlanPass.FilterCoverageSourcePass) ".w6b.${pass.id.value}" else "",
                                    coverageOnly = pass is PlanPass.FilterCoverageSourcePass).packet
                                is AnalyticRRectDraw -> W4bAnalyticRRectGraphLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, table, w5b = true, packedSourceV4 = packed,
                                    packetSuffix = if (pass is PlanPass.FilterCoverageSourcePass) ".w6b.${pass.id.value}" else "",
                                    coverageOnly = pass is PlanPass.FilterCoverageSourcePass).packet
                                is PathFillDraw -> if (packetPass == null)
                                    W4cPathFillGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, table, targetBounds, graph).packet
                                    else W4cPathFillGraphLowerer().w5bPacket(packetPass, listOf(draw), table, targetBounds, graph).packet
                                is PathStrokeDraw -> if (packetPass == null)
                                    W4dPathStrokeGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, table, targetBounds, graph).packet
                                    else W4dPathStrokeGraphLowerer().w5bPacket(packetPass, listOf(draw), table, targetBounds, graph).packet
                                else -> error("Unadmitted W6 geometry")
                            }
                            if (draw is SolidRectDraw) templates[packet.packetId] = w6aGeometryTemplate(packet, draw.blend, targetOrigin)
                            else if (draw is W5bVerticesDraw) {
                                templates[packet.packetId] = requireNotNull(sealW5aGeometryHostTemplateV1(packet)).copy(
                                    materialDevicePointWgsl = "input.position.xy + vec2<f32>(${targetOrigin.x}.0, ${targetOrigin.y}.0)")
                                analyticUniforms[packet.packetId] = preparedVerticesDrawUniformBytes(
                                    packet.semanticPayload as GPUDrawSemanticPayload.Vertices, null)
                            }
                            else {
                                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                                val key = if (draw is PathFillDraw) if (packetPass == null)
                                    W4cPathFillGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, table, targetBounds, graph).structuralPipelineKey
                                    else W4cPathFillGraphLowerer().w5bPacket(packetPass, listOf(draw), table, targetBounds, graph).structuralPipelineKey
                                    else if (draw is PathStrokeDraw) if (packetPass == null)
                                        W4dPathStrokeGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                            coverageProducer, table, targetBounds, graph).structuralPipelineKey
                                        else W4dPathStrokeGraphLowerer().w5bPacket(packetPass, listOf(draw), table, targetBounds, graph).structuralPipelineKey
                                    else corePrimitiveRenderPipelineStructuralKey(semantic, requireNotNull(packet.clipExecutionPlan),
                                        requireNotNull(packet.blendPlan), 1, GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat())
                                val mapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key)
                                require(mapping is GPUWgpu4kCorePrimitivePipelineMapping.Mapped)
                                geometryPipelines[packet.packetId] = mapping
                                analyticUniforms[packet.packetId] = if (draw is PathDraw || draw is W5bPointDraw)
                                    requireNotNull(semantic.payloadRef.uniformBlock).bytes.map(Int::toByte).toByteArray()
                                else {
                                    val uniform = buildCorePrimitiveAnalyticShapeUniform(semantic, GPUCorePrimitivePreparedSemanticAuthority.capture(semantic))
                                    require(uniform is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted)
                                    uniform.bytes.copyOf()
                                }
                                if (packet.role != GPUDrawPacketRole.PathStencilProducer)
                                    templates[packet.packetId] = requireNotNull(sealCorePrimitiveGeometryHostTemplateV1(packet, key)).copy(
                                        materialDevicePointWgsl = "fragment_position.xy + vec2<f32>(${targetOrigin.x}.0, ${targetOrigin.y}.0)")
                            }
                            packet
                        }
                        val clear = when (pass) {
                            is PlanPass.PictureAggregateBeginPass, is PlanPass.PictureSourcePass,
                            is PlanPass.FilterPass, is PlanPass.FilterSourceClear,
                            is PlanPass.FilterCoverageSourcePass -> true
                            is PlanPass.RenderPass -> pass.load == AttachmentLoadPlan.ClearTransparent
                            else -> false
                        }
                        val sampled = when (pass) {
                            is PlanPass.LayerComposite -> listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            is PlanPass.PictureSourcePass -> pass.graphTextureOperand?.let { operand -> listOf(GPUFrameResourceUse(
                                refs.getValue(operand.sealedSourceId), GPUFrameResourceRole.FilterTarget,
                                GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)) }.orEmpty()
                            is PlanPass.PictureComposite -> listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            is PlanPass.FilterPass -> pass.inputs().map { input -> GPUFrameResourceUse(refs.getValue(input),
                                GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false) }
                            is PlanPass.FilterComposite -> listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            is PlanPass.FilterCoverageSourcePass -> buildList {
                                pass.sealedAlphaSource?.let { alpha -> add(GPUFrameResourceUse(refs.getValue(alpha.sealedSourceId),
                                    GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding,
                                    GPUFrameResourceLifetime.FrameLocal, false)) }
                                depthId?.let { add(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.PathDepthStencil,
                                    GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true)) }
                            }
                            is PlanPass.FilterCoverageRetainPass -> listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            else -> depthId?.let { listOf(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.PathDepthStencil,
                                GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceLifetime.FrameLocal, true)) }.orEmpty()
                        }
                        add(GPUFrameStep.RenderPassStep(refs.getValue(targetId) as GPUFrameTargetRef,
                            GPULoadStorePlan(if (clear) "clear" else "load", GPUStorePlan.Store),
                            GPUSamplePlan.SingleSampleFrame,
                            resourceUses = sampled,
                            drawPackets = packets, sourceTaskIds = task,
                            batches = if (packets.isEmpty()) emptyList() else listOf(GPUFrameRenderBatch("w6a.${pass.id.value}", GPUPassBatchKind.Isolated, packets, task)),
                            depthStencilLoadStore = depthId?.let { GPUDepthStencilLoadStorePlan.WritableStencil(
                                if (pass is PlanPass.StencilGeometryProducerV3 || pass is PlanPass.FilterCoverageSourcePass)
                                    GPUStencilLoadOperation.Clear else GPUStencilLoadOperation.Load,
                                GPUStorePlan.Store, if (pass is PlanPass.StencilGeometryProducerV3 ||
                                    pass is PlanPass.FilterCoverageSourcePass) 0u else null) }, w6aPassV1 = pass))
                    }
                    is PlanPass.ReadbackPass -> add(GPUFrameStep.ReadbackCopyStep(refs.getValue(pass.source) as GPUFrameTargetRef,
                        refs.getValue(pass.staging) as GPUFrameBufferRef, readback, task))
                    is PlanPass.TextureCopy -> add(GPUFrameStep.CopyResourceStep(refs.getValue(pass.source), refs.getValue(pass.destination),
                        listOf(GPUResourceCopyRegion(0L, 0L, pass.copySourceBoundsI32()?.let { bounds ->
                            GPUPixelBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                        }, graph.resources().single { it.id == pass.source }.byteSize)), task))
                    else -> error("Unadmitted W6 pass")
                }
            }
        })
        tasks = steps.map { step ->
            val id = step.sourceTaskIds.single()
            when (step) {
                is GPUFrameStep.PrepareResourcesStep -> GPUTask.PrepareResources(id, request.recordingId, GPUTaskPhase.Prepare, step.requests)
                is GPUFrameStep.RenderPassStep -> GPUTask.Render(id, request.recordingId, GPUTaskPhase.Render,
                    step.target, step.loadStore, step.samplePlan, resourceUses = step.resourceUses,
                    drawPackets = step.drawPackets, batchEligibilityByPacketId = step.drawPackets.associate {
                        it.packetId to GPUPassBatchEligibility(GPUPassBatchKind.Isolated)
                    }, depthStencilLoadStore = step.depthStencilLoadStore, w6aPassV1 = step.w6aPassV1)
                is GPUFrameStep.ReadbackCopyStep -> GPUTask.Readback(id, request.recordingId, GPUTaskPhase.Readback,
                    step.source, step.staging, step.request)
                is GPUFrameStep.CopyResourceStep -> GPUTask.Copy(id, request.recordingId, GPUTaskPhase.Copy,
                    step.source, step.destination, step.regions)
                else -> error("Unadmitted W6 step")
            }
        }
        w4eAuthorities.forEach { (binding, authority) ->
            val renders = tasks.filterIsInstance<GPUTask.Render>().filter { it.w6aPassV1?.id in binding.graphPassIds() }
            val frameAuthority = authority.issueFrameAuthority(request.frameId.value, seal.sealHash, renders)
            renders.forEach { render ->
                val packet = render.drawPackets.single()
                packet.attachW4ePreparedFrameAuthority(frameAuthority)
                if (packet.materialSourcePartitionV3() != null) {
                    val origin = targetOriginsDeviceI32.getValue(binding.target)
                    val template = requireNotNull(sealW4eMaterialGeometryHostV1(packet, commonFinalSource = true))
                    templates[packet.packetId] = template.copy(materialDevicePointWgsl =
                        "${requireNotNull(template.materialCoordinateSlot).devicePointWgsl} + vec2<f32>(${origin.x}.0, ${origin.y}.0)")
                }
            }
            require(frameAuthority.validatesRenders(request.frameId.value, seal.sealHash, renders))
        }
    }

    internal fun taskList(): GPUTaskList = GPUTaskList(request.frameId, seal, listOf(recording), graph.id.value,
        tasks, emptyList(), GPUTaskPhase.entries, memory, w6aLayerFrameV1 = this)

    /** Exact plan-owned snapshot consumer, with coordinates in its target's local space. */
    internal fun destinationCopy(packet: GPUDrawPacket): PlanPass.TextureCopy? {
        val pass = graph.passes().singleOrNull { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws().any { it.commandIndex == packet.commandIdValue }
            is PlanPass.StencilCover -> pass.draw.commandIndex == packet.commandIdValue
            else -> false
        } } ?: return null
        val draw = when (pass) {
            is PlanPass.RenderPass -> pass.draws().single()
            is PlanPass.StencilCover -> pass.draw
            else -> error("Unreachable destination consumer")
        }
        val blend = draw.blend as? BlendPlan.DestinationReadV1 ?: return null
        return graph.passes().filterIsInstance<PlanPass.TextureCopy>().single { it.destination == blend.snapshotResource &&
            it.destinationVersion == blend.requiredDestinationVersion }
    }

    internal fun frame(tasks: GPUTaskList): GPUFramePlan {
        require(tasks.w6aLayerFrameV1 === this && tasks.capabilitySeal === seal &&
            tasks.frameId == request.frameId && tasks.recordingSeals == listOf(recording) && tasks.memoryBudget == memory &&
            tasks.dependencies.isEmpty() && tasks.phaseOrder == GPUTaskPhase.entries && tasks.compositeCommands.isEmpty() &&
            tasks.tasks.size == this.tasks.size && tasks.tasks.zip(this.tasks).all { (a, b) -> a === b })
        return GPUFramePlan(request.frameId, seal, listOf(recording), steps, memory, emptyList(), w6aLayerFrameV1 = this)
    }

    internal fun validates(frame: GPUFramePlan): Boolean = frame.w6aLayerFrameV1 === this &&
        frame.capabilitySeal === seal && frame.steps.size == steps.size && frame.steps.zip(steps).all { (a, b) -> a === b } &&
        frame.frameId == request.frameId && frame.recordingSeals == listOf(recording) && frame.dependencies.isEmpty() &&
        frame.phaseOrder == GPUTaskPhase.entries && frame.diagnostics.isEmpty() && frame.elidedNoOpDraws.isEmpty() &&
        !frame.atomicallyRefused && frame.memoryBudget == memory && graph.verifyW6aLayerCompilerWitness()

    internal fun template(packet: GPUDrawPacket): GPUW5aGeometryHostTemplateV1? = templates[packet.packetId]
    internal fun validatesW4eFragments(frame: GPUFramePlan): Boolean = validates(frame) && w4eAuthorities.all { (binding, _) ->
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().filter { it.w6aPassV1?.id in binding.graphPassIds() }
        val authority = renders.firstOrNull()?.drawPackets?.singleOrNull()?.w4ePreparedFrameAuthority
        renders.size == binding.graphPassIds().size && authority?.nativePayload === binding.payload &&
            authority.validatesRenderSteps(frame.frameId.value, frame.capabilitySeal.sealHash, renders)
    }
    internal fun analyticUniform(packet: GPUDrawPacket): ByteArray = requireNotNull(analyticUniforms[packet.packetId]).copyOf()
    internal fun geometryPipeline(packet: GPUDrawPacket): GPUWgpu4kCorePrimitivePipelineMapping.Mapped? = geometryPipelines[packet.packetId]
}
