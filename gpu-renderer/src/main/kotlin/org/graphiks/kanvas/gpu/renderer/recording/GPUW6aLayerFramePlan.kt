package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.*
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

/** Mechanical W6b ABI translation.  Colour is deliberately white because the coverage shader
 * substitutes every W4 colour slot with opaque white before native execution. */
internal fun w6bCoverageUniformBytes(recipe: W6bCoverageRasterBundleHostRecipeV1): ByteArray = when (recipe.uniformAbi) {
    W6bCoverageRasterUniformAbiV1.Coverage32 -> requireNotNull(recipe.uniform32).let { operand ->
        ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(operand.targetWidthI32.toFloat()); putFloat(operand.targetHeightI32.toFloat()); putInt(0); putInt(0)
            putFloat(operand.color.redF32); putFloat(operand.color.greenF32); putFloat(operand.color.blueF32); putFloat(operand.color.alphaF32)
        }.array()
    }
    W6bCoverageRasterUniformAbiV1.AnalyticShape80 -> requireNotNull(recipe.analytic80).let { operand ->
        ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(operand.targetWidthI32.toFloat()); putFloat(operand.targetHeightI32.toFloat()); putInt(if (operand.antiAlias) 1 else 0); putInt(0)
            putFloat(operand.color.redF32); putFloat(operand.color.greenF32); putFloat(operand.color.blueF32); putFloat(operand.color.alphaF32)
            operand.copyDeviceBounds().let { putFloat(it.left); putFloat(it.top); putFloat(it.right); putFloat(it.bottom) }
            operand.copyRadiiF32().forEach(::putFloat)
        }.array()
    }
}

/** Only the white colour window [16,32) is intentionally changed by coverage composition; the
 * header [0,16) and analytic geometry [32,80) must match the existing W4 lowerer byte-for-byte. */
internal fun w6bCoverageUniformMatchesLowerer(recipe: W6bCoverageRasterBundleHostRecipeV1, lowererBytes: ByteArray): Boolean {
    val expected = w6bCoverageUniformBytes(recipe)
    if (lowererBytes.size != expected.size) return false
    return if (expected.size == 32) lowererBytes.copyOfRange(0, 16).contentEquals(expected.copyOfRange(0, 16))
    else lowererBytes.copyOfRange(0, 16).contentEquals(expected.copyOfRange(0, 16)) &&
        lowererBytes.copyOfRange(32, 80).contentEquals(expected.copyOfRange(32, 80))
}

internal fun w6bCoverageUniformMismatch(recipe: W6bCoverageRasterBundleHostRecipeV1, lowererBytes: ByteArray): String {
    val expected = w6bCoverageUniformBytes(recipe)
    if (lowererBytes.size != expected.size) return "size lowerer=${lowererBytes.size} recipe=${expected.size}"
    val ranges = if (expected.size == 32) listOf(0 until 16) else listOf(0 until 16, 32 until 80)
    val offset = ranges.asSequence().flatMap { it.asSequence() }.firstOrNull { lowererBytes[it] != expected[it] }
        ?: return "size lowerer=${lowererBytes.size} recipe=${expected.size}"
    fun float(bytes: ByteArray, base: Int): String = if (base + 4 <= bytes.size) ByteBuffer.wrap(bytes, base, 4).order(ByteOrder.LITTLE_ENDIAN).float.toString() else "<none>"
    val base = offset / 4 * 4
    return "offset=$offset word=$base lowerer=${float(lowererBytes, base)} recipe=${float(expected, base)} lowererHex=${lowererBytes.copyOfRange(base, base + 4).joinToString("") { "%02x".format(it) }} recipeHex=${expected.copyOfRange(base, base + 4).joinToString("") { "%02x".format(it) }}"
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
    /**
     * Native lowering may only consume this pre-existing terminal chain.  The graph witness
     * owns all source generations, F64 planning and checked I32 target sealing; this projection
     * merely rejects an altered frozen payload before any native handle is created.
     */
    private val frozenShadowPasses: List<PlanPass.FilterPass> = scheduledPasses.filterIsInstance<PlanPass.FilterPass>()
        .filter { pass -> pass.operation.kind in setOf(
            FilterImplementationKindV1.DROP_SHADOW_COLORIZE,
            FilterImplementationKindV1.DROP_SHADOW_COMPOSITE,
        ) }.also { passes ->
            passes.forEach { pass ->
                val bounds = pass.operation.bounds
                require(!bounds.copyRequiredInputDeviceI32().isEmpty && !bounds.copyDesiredOutputDeviceI32().isEmpty &&
                    bounds.copyProducedOutputDeviceI32()?.isEmpty == false) { "W6b shadow has no sealed bounds." }
                when (val operation = pass.operation) {
                    is FilterPassOperationV1.DropShadowColorize -> require(pass.inputs().size == 1 && operation.linearSampling != null)
                    is FilterPassOperationV1.DropShadowComposite -> {
                        require(operation.mode == org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.COMPOSITE)
                        require(pass.inputs().size == 2 && pass.inputs().last() == operation.originalInput &&
                            operation.copyShadowSampleOffsetTargetLocalI32() != null &&
                            operation.copyOriginalSampleOffsetTargetLocalI32() != null)
                    }
                    else -> error("Unreachable frozen shadow operation.")
                }
            }
        }
    private val templates = mutableMapOf<GPUDrawPacketID, GPUW5aGeometryHostTemplateV1>()
    /** Each W6 SolidRect packet retains its final planner ordinal/site for later native consumption. */
    private val solidRectSitesByPacket = java.util.IdentityHashMap<GPUDrawPacket, W6GeometrySiteKeyV1>()
    /** Each W6 analytic CorePrimitive packet retains its final planner ordinal/site for native consumption. */
    private val corePrimitiveSitesByPacket = java.util.IdentityHashMap<GPUDrawPacket, W6GeometrySiteKeyV1>()
    /** Each W6 Prepared Vertices packet retains its final planner ordinal/site for native consumption. */
    private val preparedVerticesSitesByPacket = java.util.IdentityHashMap<GPUDrawPacket, W6GeometrySiteKeyV1>()
    /** Every admitted fullscreen restore is projected once by its pass/site, not by a renderer key. */
    private val plainLayerCompositeSites = linkedSetOf<W6LayerCompositeSiteKeyV1>()
    /** Each bound W4e initialization packet carries the exact planner recipe through native encoding. */
    private val clipMaskInitializeRecipesByPacket = java.util.IdentityHashMap<GPUDrawPacket, W4eClipMaskInitializeRecipeV1>()
    private val analyticUniforms = mutableMapOf<GPUDrawPacketID, ByteArray>()
    private val geometryPipelines = mutableMapOf<GPUDrawPacketID, GPUWgpu4kCorePrimitivePipelineMapping.Mapped>()
    /** W6b owns a distinct recipe-derived projection; it must never alias lowerer mappings. */
    private val coverageRasterPipelines = mutableMapOf<GPUDrawPacketID, GPUWgpu4kCorePrimitivePipelineMapping.Mapped>()
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
        val resourceAllocations = graph.resources().map { resource -> GPUFrameMemoryAllocation(refs.getValue(resource.id).value,
            when (resource.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.LayerTarget -> GPUFrameMemoryCategory.LayerTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, resource.byteSize,
            if (resource.kind == PlanResourceKind.Texture2D) GPUFrameMemoryResourceKind.Texture2D else GPUFrameMemoryResourceKind.Buffer,
            resource.copyExtent()?.let { GPUPixelBounds(0, 0, it.width, it.height) }, resource.firstPassIndex, resource.lastPassIndexExclusive) }
        val programAllocations = physical.programSlots().map { slot ->
            val lease = slot.lease
            GPUFrameMemoryAllocation(
                label = "w6d.program.slot.${slot.slotI32}.${lease.ownerPassId.value}",
                category = GPUFrameMemoryCategory.ReusableScratch,
                bytes = lease.reservedBytesI64,
                resourceKind = GPUFrameMemoryResourceKind.LogicalProgram,
                extent = null,
                firstPassIndex = lease.firstPassIndexI32,
                lastPassIndexExclusive = lease.lastPassIndexExclusiveI32,
            )
        }
        val allocations = resourceAllocations + programAllocations
        memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            minOf(graph.budget.maxFrameLocalBytes, request.rendererAggregateMemoryBudgetBytes ?: Long.MAX_VALUE), requireNotNull(request.capabilities.limits)))
        require(memory.diagnostic == null && memory.targetResidentBytes + memory.peakFrameTransientBytes ==
            graph.peakFrameLocalBytes)
        val colorFilterUniformIds = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
            (pass.operation as? FilterPassOperationV1.ColorFilter)?.let {
                val recipe = physical.nativeSiteRecipeCatalogV1().recipe(NativeSiteOwnerV1(pass.id, 0, 0))
                    as? W6FilterColorFilterNativeSiteRecipeV1
                    ?: error("ColorFilter preparation lacks its frozen native-site recipe.")
                recipe.host.uniformResource
            }
        }.toSet()
        val maskShaderUniformIds = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
            val binding = (pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding as?
                FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned ?: return@mapNotNull null
            val recipe = physical.nativeSiteRecipeCatalogV1().recipe(NativeSiteOwnerV1(pass.id, 0, 0))
                as? W6FilterMaskShaderNativeSiteRecipeV1
                ?: error("MaskShader preparation lacks its frozen native-site recipe.")
            require(recipe.host.uniformResource == binding.uniformResource)
            recipe.host.uniformResource
        }.toSet()
        val filteredLayerCompositeUniformIds = graph.passes().filterIsInstance<PlanPass.LayerComposite>().mapNotNull { pass ->
            physical.w6FilteredLayerCompositeRecipeOrNull(W6LayerCompositeSiteKeyV1(pass.id, 0))?.also { recipe ->
                require(recipe.source == pass.source && recipe.destination == pass.destination)
            }?.uniformResource
        }.toSet()
        val filteredFilterCompositeLayerUniformIds = graph.passes().filterIsInstance<PlanPass.FilterComposite>().mapNotNull { pass ->
            physical.w6FilterCompositeLayerFilteredRecipeOrNull(pass.id)?.also { recipe ->
                require(recipe.source == pass.source && recipe.destination == pass.destination)
            }?.uniformResource
        }.toSet()
        val filteredDestinationFilterCompositeLayerUniformIds = graph.passes().filterIsInstance<PlanPass.FilterComposite>().mapNotNull { pass ->
            physical.w6FilterCompositeLayerFilteredDestinationRecipeOrNull(pass.id)?.also { recipe ->
                require(recipe.source == pass.source && recipe.destination == pass.destination)
            }?.uniformResource
        }.toSet()
        val filteredPictureCompositeUniformIds = graph.passes().filterIsInstance<PlanPass.PictureComposite>().mapNotNull { pass ->
            physical.w6PictureCompositeGraphFilteredRecipeOrNull(pass.id)?.also { recipe ->
                require(recipe.source == pass.source && recipe.destination == pass.destination)
            }?.uniformResource
        }.toSet()
        val preparations = graph.resources().filter { it.kind == PlanResourceKind.Texture2D && it.lifetime == PlanResourceLifetime.FrameLocal ||
            it.role in setOf(PlanResourceRole.ReadbackStaging, PlanResourceRole.MaskTableData) || physical.w4eGeometryBindings().any { binding ->
                it.id in setOf(binding.payload.vertexResourceId, binding.payload.indexResourceId, binding.payload.uniformResourceId) } || it.id in colorFilterUniformIds || it.id in maskShaderUniformIds || it.id in filteredLayerCompositeUniformIds || it.id in filteredFilterCompositeLayerUniformIds || it.id in filteredDestinationFilterCompositeLayerUniformIds || it.id in filteredPictureCompositeUniformIds }
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
                    PlanResourceRole.UniformData, PlanResourceRole.SourceUniformData -> GPUFrameResourceRole.UniformData
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
                    if (native is PlanPass.ClipMaskInitialize) {
                        val recipe = physical.w4eClipMaskInitializeRecipe(native.id)
                        require(recipe.passId == native.id && recipe.output == native.output &&
                            recipe.copyDomainI32() == native.copyDomainI32() &&
                            recipe.clearCoverageF32 == native.clearCoverageF32) {
                            "W4e ClipMaskInitialize recipe differs from the final bound pass."
                        }
                        require(clipMaskInitializeRecipesByPacket.put(packet, recipe) == null) {
                            "W4e ClipMaskInitialize recipe projected more than once."
                        }
                    }
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
                        // W6a construction seals the W5 material-coordinate bridge on every
                        // ordinary render.  W6b passes have already localized their coverage
                        // and texture operands and therefore need no target-origin lookup.
                        val targetOrigin = when (pass) {
                            is PlanPass.RenderPass -> requireNotNull(pass.copyMaterialDeviceOriginI32()) {
                                "W6a render has no plan-sealed W5 material origin for ${pass.id.value}."
                            }
                            is PlanPass.FilterCoverageSourcePass -> Point2I32.Origin
                            else -> Point2I32.Origin
                        }
                        val packets = packetInputs.mapIndexed { drawOrdinalI32, (packetPass, draw, coverageProducer) ->
                            // A frozen Picture stream may contain only Clear/DrawColor entries.
                            // Those legacy-color SolidRect operands are complete without a W5
                            // material table; every material-backed branch below still demands
                            // the exact pre-issued table before it can lower.
                            val table = graph.materialPlanTableOrNull()
                            val packed = draw.materialAuthority.colorSourceCoordinatesV4()?.let { graph.packedMaterialSourceV4(draw.materialAuthority) }
                            val packet = when (draw) {
                                is W5bVerticesDraw -> lowerW5bVerticesDraw(draw, targetBounds, graph, requireNotNull(verticesSource),
                                    seal.sealHash, requireNotNull(physical.geometryBinding(pass.id)))
                                is SolidRectDraw -> GpuPlanTaskListLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, table, null, packed)
                                is W5bPointDraw -> GpuPlanTaskListLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, requireNotNull(table), null, packed, graph,
                                    coveragePassId = (pass as? PlanPass.FilterCoverageSourcePass)?.id)
                                is AnalyticRectDraw -> W4aAnalyticRectGraphLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, requireNotNull(table), w5b = true, packedSourceV4 = packed,
                                    packetSuffix = if (pass is PlanPass.FilterCoverageSourcePass) ".w6b.${pass.id.value}" else "",
                                    coverageOnly = pass is PlanPass.FilterCoverageSourcePass).packet
                                is AnalyticRRectDraw -> W4bAnalyticRRectGraphLowerer().packet(draw, ColorF32.Transparent,
                                    draw.commandIndex, targetBounds, requireNotNull(table), w5b = true, packedSourceV4 = packed,
                                    packetSuffix = if (pass is PlanPass.FilterCoverageSourcePass) ".w6b.${pass.id.value}" else "",
                                    coverageOnly = pass is PlanPass.FilterCoverageSourcePass).packet
                                is PathFillDraw -> if (packetPass == null)
                                    W4cPathFillGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, requireNotNull(table), targetBounds, graph).packet
                                    else W4cPathFillGraphLowerer().w5bPacket(packetPass, listOf(draw), requireNotNull(table), targetBounds, graph).packet
                                is PathStrokeDraw -> if (packetPass == null)
                                    W4dPathStrokeGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, requireNotNull(table), targetBounds, graph).packet
                                    else W4dPathStrokeGraphLowerer().w5bPacket(packetPass, listOf(draw), requireNotNull(table), targetBounds, graph).packet
                                else -> error("Unadmitted W6 geometry")
                            }
                            if (draw is SolidRectDraw) {
                                val owner = requireNotNull(packetPass) { "W6 SolidRect host recipe only admits RenderPass draws." }
                                val site = W6GeometrySiteKeyV1(owner.id, drawOrdinalI32)
                                val recipe = physical.w6SolidRectHostRecipe(site)
                                require(recipe.site == site)
                                require(solidRectSitesByPacket.put(packet, site) == null)
                                templates[packet.packetId] = w6aGeometryTemplate(packet, recipe)
                            }
                            else if ((draw is AnalyticRectDraw || draw is AnalyticRRectDraw) && packetPass != null) {
                                val site = W6GeometrySiteKeyV1(packetPass.id, drawOrdinalI32)
                                val recipe = physical.w6CorePrimitiveHostRecipe(site)
                                require(recipe.site == site)
                                require((draw is AnalyticRectDraw && recipe is W6AnalyticRectHostRecipeV1) ||
                                    (draw is AnalyticRRectDraw && recipe is W6AnalyticRRectHostRecipeV1)) {
                                    "W6 analytic CorePrimitive host recipe shape changed after final pass binding."
                                }
                                require(w6aCorePrimitivePacketMatchesRecipe(packet, draw, recipe)) {
                                    "W6 analytic CorePrimitive packet geometry differs from its frozen host recipe."
                                }
                                require(corePrimitiveSitesByPacket.put(packet, site) == null)
                                val key = w6aCorePrimitiveStructuralKey(recipe, targetBounds)
                                val mapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key)
                                require(mapping is GPUWgpu4kCorePrimitivePipelineMapping.Mapped)
                                geometryPipelines[packet.packetId] = mapping
                                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                                val uniform = buildCorePrimitiveAnalyticShapeUniform(semantic,
                                    GPUCorePrimitivePreparedSemanticAuthority.capture(semantic))
                                require(uniform is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted)
                                analyticUniforms[packet.packetId] = uniform.bytes.copyOf()
                                templates[packet.packetId] = w6aCorePrimitiveGeometryTemplate(packet, recipe, key)
                            }
                            else if (draw is W5bPointDraw && packetPass != null && draw.clipOnly == null) {
                                val site = W6GeometrySiteKeyV1(packetPass.id, drawOrdinalI32)
                                val recipe = physical.w6CorePrimitiveHostRecipe(site) as? W6PointHostRecipeV1
                                    ?: error("W6 Point host recipe shape changed after final pass binding.")
                                require(recipe.site == site && w6aPointPacketMatchesRecipe(packet, draw, recipe)) {
                                    "W6 Point packet geometry differs from its frozen host recipe."
                                }
                                require(corePrimitiveSitesByPacket.put(packet, site) == null)
                                val key = w6aPointStructuralKey(packet, recipe, targetBounds)
                                val mapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key)
                                require(mapping is GPUWgpu4kCorePrimitivePipelineMapping.Mapped)
                                geometryPipelines[packet.packetId] = mapping
                                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                                analyticUniforms[packet.packetId] = requireNotNull(semantic.payloadRef.uniformBlock).bytes
                                    .map(Int::toByte).toByteArray()
                                templates[packet.packetId] = w6aPointGeometryTemplate(packet, recipe, key)
                            }
                            else if (draw is W5bVerticesDraw) {
                                val owner = requireNotNull(packetPass) {
                                    "W6 prepared-vertices host recipe only admits RenderPass draws."
                                }
                                val site = W6GeometrySiteKeyV1(owner.id, drawOrdinalI32)
                                val recipe = physical.w6PreparedVerticesHostRecipe(site)
                                require(recipe.site == site && w6aPreparedVerticesPacketMatchesRecipe(packet, recipe)) {
                                    "W6 prepared-vertices packet layout, uniform, or source differs from its frozen host recipe."
                                }
                                require(preparedVerticesSitesByPacket.put(packet, site) == null)
                                templates[packet.packetId] = w6aPreparedVerticesGeometryTemplate(packet, recipe)
                                analyticUniforms[packet.packetId] = preparedVerticesDrawUniformBytes(
                                    packet.semanticPayload as GPUDrawSemanticPayload.Vertices, null)
                            }
                            else {
                                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                                val key = if (draw is PathFillDraw) if (packetPass == null)
                                    W4cPathFillGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                        coverageProducer, requireNotNull(table), targetBounds, graph).structuralPipelineKey
                                    else W4cPathFillGraphLowerer().w5bPacket(packetPass, listOf(draw), requireNotNull(table), targetBounds, graph).structuralPipelineKey
                                    else if (draw is PathStrokeDraw) if (packetPass == null)
                                        W4dPathStrokeGraphLowerer().w6bCoveragePacket("${pass.id.value}.coverage", draw,
                                            coverageProducer, requireNotNull(table), targetBounds, graph).structuralPipelineKey
                                        else W4dPathStrokeGraphLowerer().w5bPacket(packetPass, listOf(draw), requireNotNull(table), targetBounds, graph).structuralPipelineKey
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
                            if (pass is PlanPass.FilterCoverageSourcePass && draw !is SolidRectDraw) {
                                val recipe = physical.w6bCoverageRasterHostRecipe(pass.id).bundle(drawOrdinalI32)
                                val lowered = requireNotNull(analyticUniforms[packet.packetId])
                                require(w6bCoverageUniformMatchesLowerer(recipe, lowered)) {
                                    "W6b coverage translation changed an ABI field outside its canonical-colour window: ${w6bCoverageUniformMismatch(recipe, lowered)}"
                                }
                                analyticUniforms[packet.packetId] = w6bCoverageUniformBytes(recipe)
                                requireNotNull(geometryPipelines[packet.packetId]) {
                                    "W6b coverage raster lowerer did not retain its compatibility pipeline."
                                }
                                val recipeMapping = w6bCoverageRasterPipelineMapping(recipe, packet)
                                require(coverageRasterPipelines.put(packet.packetId, recipeMapping) == null) {
                                    "W6b coverage raster packet has more than one recipe projection."
                                }
                                if (recipe.role != W6bCoverageRasterRoleV1.PathStencilProducer) {
                                    templates[packet.packetId] = requireNotNull(
                                        sealCorePrimitiveGeometryHostTemplateV1(packet, w6bCoverageRasterStructuralKey(recipe)),
                                    ).copy(materialDevicePointWgsl = "fragment_position.xy")
                                }
                            }
                            packet
                        }
                        val clear = when (pass) {
                            is PlanPass.PictureAggregateBeginPass, is PlanPass.PictureSourcePass,
                            is PlanPass.FilterPass, is PlanPass.FilterSourceClear -> true
                            is PlanPass.FilterCoverageSourcePass -> pass.sealedAlphaSource == null
                            is PlanPass.RenderPass -> pass.load == AttachmentLoadPlan.ClearTransparent
                            else -> false
                        }
                        val sampled = when (pass) {
                            is PlanPass.LayerComposite -> buildList {
                                add(GPUFrameResourceUse(refs.getValue(pass.source),
                                    GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                                physical.w6FilteredLayerCompositeRecipeOrNull(W6LayerCompositeSiteKeyV1(pass.id, 0))?.let { recipe ->
                                    require(recipe.source == pass.source && recipe.destination == pass.destination)
                                    add(GPUFrameResourceUse(refs.getValue(recipe.uniformResource),
                                        GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
                                }
                            }
                            is PlanPass.PictureSourcePass -> listOfNotNull((pass.graphTextureOperand?.sealedSourceId ?: pass.layerInput)?.let { source -> GPUFrameResourceUse(
                                refs.getValue(source), GPUFrameResourceRole.FilterTarget,
                                GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false) })
                            is PlanPass.PictureComposite -> physical.w6PictureCompositeGraphFilteredRecipeOrNull(pass.id)?.let { recipe ->
                                buildList { add(GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)); add(GPUFrameResourceUse(refs.getValue(recipe.uniformResource), GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false)); recipe.destinationSnapshot?.let { add(GPUFrameResourceUse(refs.getValue(it), GPUFrameResourceRole.DestinationSnapshot, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)) } }
                            } ?: physical.w6PictureCompositeGraphDestinationRecipeOrNull(pass.id)?.let { recipe ->
                                listOf(GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false), GPUFrameResourceUse(refs.getValue(recipe.destinationSnapshot), GPUFrameResourceRole.DestinationSnapshot, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            } ?: physical.w6PictureCompositeGraphRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(GPUFrameResourceUse(refs.getValue(recipe.source),
                                    GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            } ?: listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            is PlanPass.FilterPass -> buildList {
                                pass.inputs().forEach { input -> add(GPUFrameResourceUse(refs.getValue(input),
                                    GPUFrameResourceRole.FilterTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)) }
                                (pass.operation as? FilterPassOperationV1.ColorFilter)?.let { operation ->
                                    add(GPUFrameResourceUse(refs.getValue(requireNotNull(operation.uniformResource)),
                                        GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
                                }
                                ((pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding as?
                                    FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned)?.let { binding ->
                                    add(GPUFrameResourceUse(refs.getValue(binding.uniformResource),
                                        GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
                                }
                                (pass.operation as? FilterPassOperationV1.MaskTable)?.let { operation ->
                                    add(GPUFrameResourceUse(refs.getValue(operation.tableResourceId),
                                        GPUFrameResourceRole.StorageData, GPUFrameResourceUsage.Storage, GPUFrameResourceLifetime.FrameLocal, false))
                                }
                            }
                            is PlanPass.FilterComposite -> physical.w6FilterCompositePictureDestinationRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(
                                    GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                    GPUFrameResourceUse(refs.getValue(recipe.destinationSnapshot), GPUFrameResourceRole.DestinationSnapshot,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                )
                            } ?: physical.w6FilterCompositePicturePlainRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                    GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            } ?: physical.w6FilterCompositePictureGraphRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                    GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
                            } ?: physical.w6FilterCompositeLayerFilteredDestinationRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(
                                    GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                    GPUFrameResourceUse(refs.getValue(recipe.uniformResource), GPUFrameResourceRole.UniformData,
                                        GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false),
                                    GPUFrameResourceUse(refs.getValue(recipe.destinationSnapshot), GPUFrameResourceRole.DestinationSnapshot,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                )
                            } ?: physical.w6FilterCompositeLayerDestinationRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(
                                    GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                    GPUFrameResourceUse(refs.getValue(recipe.destinationSnapshot), GPUFrameResourceRole.DestinationSnapshot,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                )
                            } ?: physical.w6FilterCompositeLayerFilteredRecipeOrNull(pass.id)?.let { recipe ->
                                require(recipe.ownerPassId == pass.id && recipe.source == pass.source && recipe.destination == pass.destination)
                                listOf(
                                    GPUFrameResourceUse(refs.getValue(recipe.source), GPUFrameResourceRole.FilterTarget,
                                        GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false),
                                    GPUFrameResourceUse(refs.getValue(recipe.uniformResource), GPUFrameResourceRole.UniformData,
                                        GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false),
                                )
                            } ?: listOf(GPUFrameResourceUse(refs.getValue(pass.source),
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
                        if (pass is PlanPass.LayerComposite) {
                            val site = W6LayerCompositeSiteKeyV1(pass.id, 0)
                            physical.w6PlainLayerCompositeRecipeOrNull(site)?.let { recipe ->
                                require(recipe.site == site && recipe.source == pass.source && recipe.destination == pass.destination)
                                require(plainLayerCompositeSites.add(site)) {
                                    "W6 plain layer-composite recipe projected more than once."
                                }
                            }
                            physical.w6FilteredLayerCompositeRecipeOrNull(site)?.let { recipe ->
                                require(recipe.site == site && recipe.source == pass.source && recipe.destination == pass.destination)
                            }
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
                    // W4e's old material-coordinate bridge consumes the exact origin already
                    // frozen in its W6a geometry binding.  In particular, no W6b operand/pass can look
                    // up a target origin or convert device coordinates back to target-local.
                    val origin = binding.copyMaterialDeviceOriginI32()
                    val template = requireNotNull(sealW4eMaterialGeometryHostV1(packet, commonFinalSource = true))
                    templates[packet.packetId] = template.copy(materialDevicePointWgsl =
                        "${requireNotNull(template.materialCoordinateSlot).devicePointWgsl} + vec2<f32>(${origin.x}.0, ${origin.y}.0)")
                }
            }
            require(frameAuthority.validatesRenders(request.frameId.value, seal.sealHash, renders))
        }
        require(solidRectSitesByPacket.values.toSet() == physical.w6SolidRectHostRecipes().keys &&
            solidRectSitesByPacket.size == physical.w6SolidRectHostRecipes().size) {
            "Every frozen W6 SolidRect host recipe must project to exactly one packet."
        }
        require(corePrimitiveSitesByPacket.values.toSet() == physical.w6CorePrimitiveHostRecipes().keys &&
            corePrimitiveSitesByPacket.size == physical.w6CorePrimitiveHostRecipes().size) {
            "Every frozen W6 analytic CorePrimitive host recipe must project to exactly one packet."
        }
        require(preparedVerticesSitesByPacket.values.toSet() == physical.w6PreparedVerticesHostRecipes().keys &&
            preparedVerticesSitesByPacket.size == physical.w6PreparedVerticesHostRecipes().size) {
            "Every frozen W6 prepared-vertices host recipe must project to exactly one packet."
        }
        require(plainLayerCompositeSites == physical.w6PlainLayerCompositeRecipes().keys) {
            "Every frozen W6 plain layer-composite recipe must project to exactly one LayerComposite pass."
        }
        require(clipMaskInitializeRecipesByPacket.values.map { it.passId }.toSet() ==
            physical.w4eClipMaskInitializeRecipes().keys &&
            clipMaskInitializeRecipesByPacket.size == physical.w4eClipMaskInitializeRecipes().size) {
            "Every frozen W4e ClipMaskInitialize recipe must project to exactly one packet."
        }
    }

    internal fun taskList(): GPUTaskList = GPUTaskList(request.frameId, seal, listOf(recording), graph.id.value,
        tasks, emptyList(), GPUTaskPhase.entries, memory, w6aLayerFrameV1 = this)

    /** Returns only the recipe attached during W6 recording; native encoding cannot rediscover it. */
    internal fun w4eClipMaskInitializeRecipeOrNull(packet: GPUDrawPacket): W4eClipMaskInitializeRecipeV1? =
        clipMaskInitializeRecipesByPacket[packet]

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
    /** Planner site retained with the packet; no native stage may infer it from material authority. */
    internal fun solidRectSite(packet: GPUDrawPacket): W6GeometrySiteKeyV1? = solidRectSitesByPacket[packet]
    /** Planner site retained with the packet; native code must not infer it from an analytic draw. */
    internal fun corePrimitiveSite(packet: GPUDrawPacket): W6GeometrySiteKeyV1? = corePrimitiveSitesByPacket[packet]
    /** Planner site retained with the packet; native code must not infer it from prepared upload bytes. */
    internal fun preparedVerticesSite(packet: GPUDrawPacket): W6GeometrySiteKeyV1? = preparedVerticesSitesByPacket[packet]
    internal fun validatesW4eFragments(frame: GPUFramePlan): Boolean = validates(frame) && w4eAuthorities.all { (binding, _) ->
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().filter { it.w6aPassV1?.id in binding.graphPassIds() }
        val authority = renders.firstOrNull()?.drawPackets?.singleOrNull()?.w4ePreparedFrameAuthority
        renders.size == binding.graphPassIds().size && authority?.nativePayload === binding.payload &&
            authority.validatesRenderSteps(frame.frameId.value, frame.capabilitySeal.sealHash, renders)
    }
    internal fun analyticUniform(packet: GPUDrawPacket): ByteArray = requireNotNull(analyticUniforms[packet.packetId]).copyOf()
    internal fun geometryPipeline(packet: GPUDrawPacket): GPUWgpu4kCorePrimitivePipelineMapping.Mapped? = geometryPipelines[packet.packetId]
    /** W6b may consume only the mapping projected from its frozen raster recipe. */
    internal fun coverageRasterPipeline(packet: GPUDrawPacket): GPUWgpu4kCorePrimitivePipelineMapping.Mapped? = coverageRasterPipelines[packet.packetId]
}

/**
 * Converts the planner selector to the already-closed CorePrimitive mapping domain.  The W4
 * lowerer mapping is retained only as a compatibility witness; the returned value is stored in
 * the W6b-owned map, so later native materialization cannot fall back to the lowerer map.
 */
private fun w6bCoverageRasterPipelineMapping(
    recipe: W6bCoverageRasterBundleHostRecipeV1,
    packet: GPUDrawPacket,
): GPUWgpu4kCorePrimitivePipelineMapping.Mapped {
    require(packet.role == when (recipe.role) {
        W6bCoverageRasterRoleV1.Shading -> GPUDrawPacketRole.Shading
        W6bCoverageRasterRoleV1.PathStencilProducer -> GPUDrawPacketRole.PathStencilProducer
        W6bCoverageRasterRoleV1.PathStencilCover -> GPUDrawPacketRole.PathStencilCover
    }) { "W6b packet role differs from its frozen recipe." }
    val recipeKey = w6bCoverageRasterStructuralKey(recipe)
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
        ?: error("W6b coverage raster requires CorePrimitive semantics.")
    val lowererKey = when (recipe.role) {
        W6bCoverageRasterRoleV1.PathStencilProducer,
        W6bCoverageRasterRoleV1.PathStencilCover -> corePrimitivePathStencilRenderPipelineStructuralKey(
            semantic, if (recipe.role == W6bCoverageRasterRoleV1.PathStencilProducer)
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
            else GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
            requireNotNull(packet.clipExecutionPlan), requireNotNull(packet.blendPlan), 1,
            GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb)
        W6bCoverageRasterRoleV1.Shading -> corePrimitiveRenderPipelineStructuralKey(
            semantic, requireNotNull(packet.clipExecutionPlan), requireNotNull(packet.blendPlan), 1,
            GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb)
    }
    require(recipeKey.copy(colorFormat = lowererKey.colorFormat) == lowererKey) {
        "W6b recipe selector differs from the W4 lowerer structural key outside its target format."
    }
    val recipeMapping = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(recipeKey)
        as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped
        ?: error("Frozen W6b recipe has no native CorePrimitive pipeline mapping.")
    return recipeMapping
}

/** Complete recipe-only conversion to the pre-existing CorePrimitive structural-key ABI. */
private fun w6bCoverageRasterStructuralKey(
    recipe: W6bCoverageRasterBundleHostRecipeV1,
): GPUCorePrimitiveRenderPipelineStructuralKey {
    require(recipe.target == W6bCoverageRasterTargetV1.Rgba8UnormSrgbSingleSample) {
        "W6b coverage raster recipe must target its frozen sRGB color attachment."
    }
    val role = when (recipe.role) {
        W6bCoverageRasterRoleV1.Shading -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading
        W6bCoverageRasterRoleV1.PathStencilProducer -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
        W6bCoverageRasterRoleV1.PathStencilCover -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover
    }
    val shader = when (recipe.family) {
        W6bCoverageRasterFamilyV1.AnalyticRect -> if (requireNotNull(recipe.analytic80).antiAlias)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape
        else GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
        W6bCoverageRasterFamilyV1.AnalyticRRect -> GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape
        W6bCoverageRasterFamilyV1.Point -> GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
        W6bCoverageRasterFamilyV1.PathFill, W6bCoverageRasterFamilyV1.PathStroke ->
            if (recipe.role == W6bCoverageRasterRoleV1.Shading)
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
            else GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil
    }
    val topology = when (recipe.topology) {
        W6bCoverageRasterTopologyV1.DirectTriangleList -> GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        W6bCoverageRasterTopologyV1.StencilEdgeFan -> GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
    }
    val depthStencil = when (recipe.stencil) {
        W6bCoverageRasterStencilV1.None -> GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None
        W6bCoverageRasterStencilV1.WindingProducer -> w6bPathStencilState(true, false)
        W6bCoverageRasterStencilV1.EvenOddProducer -> w6bPathStencilState(true, true)
        W6bCoverageRasterStencilV1.CoverTestNonZero -> w6bPathStencilState(false, false)
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = shader, topology = topology, role = role,
        blend = if (role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer)
            GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone
        else W5bBlendPlanLowerer.lowerForRecording(recipe.blend).corePrimitiveStructuralBlend(),
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        colorFormat = GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb,
        depthStencil = depthStencil, sampleCount = 1,
    )
}

private fun w6bPathStencilState(producer: Boolean, evenOdd: Boolean): GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil {
    fun face(compare: GPUClipStencilCompare, pass: GPUClipStencilOperation,
        depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep) =
        GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(compare, pass,
            GPUClipStencilOperation.Keep, depthFail)
    val pair = if (producer) when (evenOdd) {
        true -> face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert) to
            face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert)
        false -> face(GPUClipStencilCompare.Always, GPUClipStencilOperation.IncrementWrap) to
            face(GPUClipStencilCompare.Always, GPUClipStencilOperation.DecrementWrap)
    } else face(GPUClipStencilCompare.NotEqual, GPUClipStencilOperation.Zero,
        GPUClipStencilOperation.Zero) to face(GPUClipStencilCompare.NotEqual,
        GPUClipStencilOperation.Zero, GPUClipStencilOperation.Zero)
    return GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        pair.first, pair.second, 0xffu, if (producer && evenOdd) 0x01u else 0xffu)
}
