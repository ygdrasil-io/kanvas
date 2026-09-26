package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.PreparedVerticesUploadPayloadV1

/** A sealed allocation slot. No two resource IDs alias without an explicit future proof. */
public class PlanPhysicalSlotV1 internal constructor(
    public val slotI32: Int,
    public val resourceId: PlanResourceId,
    /** Pessimistic reservation retained through completion, even when a cache is warm. */
    public val reservedBytesI64: Long,
) {
    init { require(slotI32 >= 0 && reservedBytesI64 >= 0L) }
}

/** A typed slot for an opaque native W6d shader-module/render-pipeline lease. */
public class PlanProgramSlotV1 internal constructor(
    public val slotI32: Int,
    public val lease: W6dProgramLeaseV1,
) {
    init { require(slotI32 >= 0 && lease.reservedBytesI64 > 0L) }
}

/** The request retains W5's captured identity/generation contract; IDs and slots belong to the graph. */
public class PlanCacheBindingV1 internal constructor(
    public val resourceId: PlanResourceId,
    public val request: PlanCacheResourceRequest,
    public val uploadResourceId: PlanResourceId? = null,
    public val uploadBytesPerRowI64: Long? = null,
)

/** Pre-publication input from the sole FrameSourceLayoutV4 issuer; never crosses into the renderer. */
internal class SourcePhysicalConstructionV1(
    val resources: List<PlanResource> = emptyList(),
    val uniforms: Map<String, PlanResourceId> = emptyMap(),
    val caches: List<PlanCacheBindingV1> = emptyList(),
    val w6cColorUniformBindings: Map<String, W6cColorUniformBindingV1> = emptyMap(),
    val w4eGeometry: List<PlanW4eGeometryBindingV1> = emptyList(),
    /** Final W6 SolidRect host choices, attached before the peak/layout publication boundary. */
    val w6SolidRectHostRecipes: Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1> = emptyMap(),
    /** Final W6 non-W4e CorePrimitive host choices, attached beside the already sealed physical source. */
    val w6CorePrimitiveHostRecipes: Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1> = emptyMap(),
    /** Final W6 prepared-vertices host choices; upload bytes remain in their sealed render-ir payload. */
    val w6PreparedVerticesHostRecipes: Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1> = emptyMap(),
    /** Final unfiltered non-destination-read fullscreen layer restores. */
    val w6PlainLayerCompositeRecipes: Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1> = emptyMap(),
    /** Final W4e ClipMaskInitialize recipes, limited to the final W4e bindings. */
    val w4eClipMaskInitializeRecipes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1> = emptyMap(),
)

internal fun requireW6cColorUniformWindow(offsetBytesI64: Long, capacityBytesI64: Long, dynamicBytesI64: Long) {
    require(dynamicBytesI64 >= 0L)
    require(offsetBytesI64 >= 0L && offsetBytesI64 % 4L == 0L)
    require(capacityBytesI64 >= 16L && capacityBytesI64 % 16L == 0L)
    require(Math.addExact(offsetBytesI64, maxOf(16L, dynamicBytesI64)) <= capacityBytesI64)
    require(capacityBytesI64 / 4L <= UInt.MAX_VALUE.toLong() + 1L)
}

/** The only FrameSourceLayoutV4-issued W6c byte window; renderers never derive it. */
internal class W6cColorUniformBindingV1(
    val resourceId: PlanResourceId,
    val offsetBytesI64: Long,
    val capacityBytesI64: Long,
) {
    init { requireW6cColorUniformWindow(offsetBytesI64, capacityBytesI64, 0L) }
}

/** Exact W4 upload/draw ranges within graph-owned physical buffers, fixed before publication. */
public class PlanGeometryBufferBindingV1 internal constructor(
    public val data: PlanDrawDataResources,
    public val vertexOffsetI64: Long,
    public val indexOffsetI64: Long,
    public val uniformOffsetI64: Long,
    public val vertexCountI32: Int,
    public val indexCountI32: Int,
    public val maxLocalIndexI32: Int,
    public val uniformBytesI64: Long,
    public val vertexStrideBytesI32: Int = 8,
    public val indexElementBytesI32: Int = 4,
    /** Sealed canonical V/I bytes for the only pre-publication Vertices authority. */
    public val verticesUploadPayload: PreparedVerticesUploadPayloadV1? = null,
) {
    public val vertexBytesI64: Long = Math.multiplyExact(vertexCountI32.toLong(), vertexStrideBytesI32.toLong())
    public val indexBytesI64: Long = Math.multiplyExact(indexCountI32.toLong(), indexElementBytesI32.toLong())
    public val indexUploadBytesI64: Long = Math.addExact(indexBytesI64, (4L - indexBytesI64 % 4L) % 4L)
}

/** Graph-owned binding and allocation authority, frozen after the frame-wide W5 source binding. */
public class PlanPhysicalLayoutV1 private constructor(
    resources: List<PlanResource>,
    cacheBindings: List<PlanCacheBindingV1>,
    uniformsByCommand: Map<Int, PlanResourceId>,
    geometryByPass: Map<PlanPassId, PlanGeometryBufferBindingV1>,
    w4eGeometry: List<PlanW4eGeometryBindingV1>,
    pictureComposites: Map<PlanPassId, PictureCompositeOperandsV1>,
    spatialCaches: List<SpatialFilterCachePlanV1>,
    programLeases: List<W6dProgramLeaseV1>,
    solidRectHostRecipes: Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1>,
    corePrimitiveHostRecipes: Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1>,
    preparedVerticesHostRecipes: Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1>,
    plainLayerCompositeRecipes: Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1>,
    w4eClipMaskInitializeRecipes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1>,
) {
    private val resources = immutableList(resources)
    private val caches = immutableList(cacheBindings)
    private val uniforms = java.util.Collections.unmodifiableMap(LinkedHashMap(uniformsByCommand))
    private val geometry = java.util.Collections.unmodifiableMap(LinkedHashMap(geometryByPass))
    private val w4e = immutableList(w4eGeometry)
    private val pictures = java.util.Collections.unmodifiableMap(LinkedHashMap(pictureComposites))
    private val spatialCaches = immutableList(spatialCaches)
    private val solidRectHosts = java.util.Collections.unmodifiableMap(LinkedHashMap(solidRectHostRecipes))
    private val corePrimitiveHosts = java.util.Collections.unmodifiableMap(LinkedHashMap(corePrimitiveHostRecipes))
    private val preparedVerticesHosts = java.util.Collections.unmodifiableMap(LinkedHashMap(preparedVerticesHostRecipes))
    private val plainLayerComposites = java.util.Collections.unmodifiableMap(LinkedHashMap(plainLayerCompositeRecipes))
    private val clipMaskInitializes = java.util.Collections.unmodifiableMap(LinkedHashMap(w4eClipMaskInitializeRecipes))
    private val slots = immutableList(buildList {
        resources.forEachIndexed { indexI32, resource ->
            add(PlanPhysicalSlotV1(indexI32, resource.id, resource.byteSize))
        }
        caches.filter { it.request is PlanCacheResourceRequest.Sampler }.forEachIndexed { offsetI32, cache ->
            add(PlanPhysicalSlotV1(resources.size + offsetI32, cache.resourceId, 0L))
        }
    })
    private val programSlots = immutableList(programLeases.mapIndexed { offsetI32, lease ->
        PlanProgramSlotV1(slots.size + offsetI32, lease)
    })

    public fun slots(): List<PlanPhysicalSlotV1> = slots
    public fun programSlots(): List<PlanProgramSlotV1> = programSlots
    public fun programSlot(ownerPassId: PlanPassId): PlanProgramSlotV1 =
        programSlots.single { it.lease.ownerPassId == ownerPassId }
    public fun cacheBindings(): List<PlanCacheBindingV1> = caches
    /** Planner-selected spatial cache requests; native code receives a preflight binding for these values only. */
    public fun spatialCachePlans(): List<SpatialFilterCachePlanV1> = spatialCaches
    public fun spatialCachePlan(outputResourceId: PlanResourceId): SpatialFilterCachePlanV1 =
        spatialCaches.single { it.outputResourceId == outputResourceId }
    public fun slot(resourceId: PlanResourceId): PlanPhysicalSlotV1 = slots.single { it.resourceId == resourceId }
    public fun resource(resourceId: PlanResourceId): PlanResource {
        slot(resourceId)
        return resources.single { it.id == resourceId }
    }
    public fun sourceUniform(commandIndexI32: Int): PlanResource = resource(uniforms.getValue(commandIndexI32))
    public fun geometryBinding(passId: PlanPassId): PlanGeometryBufferBindingV1? = geometry[passId]
    /** The semantic terminal and physical binding share this exact sealed operand, without renderer derivation. */
    public fun pictureCompositeBinding(passId: PlanPassId): PictureCompositeOperandsV1? = pictures[passId]
    public fun w4eGeometryBindings(): List<PlanW4eGeometryBindingV1> = w4e
    public fun w4eGeometryBinding(passId: PlanPassId): PlanW4eGeometryBindingV1? = w4e.singleOrNull { passId in it.graphPassIds() }
    public fun cacheBinding(request: PlanCacheResourceRequest): PlanCacheBindingV1 = caches.single {
        it.request === request
    }.also { slot(it.resourceId) }
    /** Exact W6 SolidRect recipe sealed for this final graph site. */
    public fun w6SolidRectHostRecipe(site: W6GeometrySiteKeyV1): W6SolidRectHostRecipeV1 =
        requireNotNull(solidRectHosts[site]) { "Missing frozen W6 SolidRect host recipe for ${site.ownerPassId.value}/${site.drawOrdinalI32}." }
    public fun w6SolidRectHostRecipes(): Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1> = solidRectHosts
    /** Exact W6 non-W4e CorePrimitive recipe sealed for this final graph site. */
    public fun w6CorePrimitiveHostRecipe(site: W6GeometrySiteKeyV1): W6CorePrimitiveHostRecipeV1 =
        requireNotNull(corePrimitiveHosts[site]) { "Missing frozen W6 CorePrimitive host recipe for ${site.ownerPassId.value}/${site.drawOrdinalI32}." }
    public fun w6CorePrimitiveHostRecipes(): Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1> = corePrimitiveHosts
    /** Exact W6 Prepared Vertices recipe sealed for this final graph site. */
    public fun w6PreparedVerticesHostRecipe(site: W6GeometrySiteKeyV1): W6PreparedVerticesHostRecipeV1 =
        requireNotNull(preparedVerticesHosts[site]) { "Missing frozen W6 prepared-vertices host recipe for ${site.ownerPassId.value}/${site.drawOrdinalI32}." }
    public fun w6PreparedVerticesHostRecipes(): Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1> = preparedVerticesHosts
    /** Exact W6 plain fullscreen layer-restore recipe sealed for this final graph site. */
    public fun w6PlainLayerCompositeRecipe(site: W6LayerCompositeSiteKeyV1): W6PlainLayerCompositeRecipeV1 =
        requireNotNull(plainLayerComposites[site]) { "Missing frozen W6 plain layer-composite recipe for ${site.ownerPassId.value}/${site.siteOrdinalI32}." }
    public fun w6PlainLayerCompositeRecipeOrNull(site: W6LayerCompositeSiteKeyV1): W6PlainLayerCompositeRecipeV1? =
        plainLayerComposites[site]
    public fun w6PlainLayerCompositeRecipes(): Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1> = plainLayerComposites
    /** Exact W4e clip-mask initialize recipe sealed for this final W4e binding pass. */
    public fun w4eClipMaskInitializeRecipe(passId: PlanPassId): W4eClipMaskInitializeRecipeV1 =
        requireNotNull(clipMaskInitializes[passId]) { "Missing frozen W4e ClipMaskInitialize recipe for ${passId.value}." }
    public fun w4eClipMaskInitializeRecipes(): Map<PlanPassId, W4eClipMaskInitializeRecipeV1> = clipMaskInitializes

    internal companion object {
        fun seal(graph: RenderGraphConstruction, source: SourcePhysicalConstructionV1): PlanPhysicalLayoutV1 {
            val rows = graph.resources()
            require(source.resources.all { row -> rows.any { it === row } })
            require(source.uniforms.values.toSet() == rows.filter { it.role == PlanResourceRole.SourceUniformData }.map { it.id }.toSet())
            require(source.caches.filter { it.request !is PlanCacheResourceRequest.Sampler }.map { it.resourceId }.toSet() ==
                rows.filter { it.lifetime == PlanResourceLifetime.DeviceSessionCache }.map { it.id }.toSet())
            val expectedSolidRectHosts = freezeW6SolidRectHostsV1(graph.passes())
            require(source.w6SolidRectHostRecipes.keys == expectedSolidRectHosts.keys)
            source.w6SolidRectHostRecipes.forEach { (site, recipe) ->
                require(recipe.site == site && recipe == expectedSolidRectHosts.getValue(site)) {
                    "W6 SolidRect host recipe changed after final pass binding."
                }
            }
            val expectedCorePrimitiveHosts = freezeW6CorePrimitiveHostsV1(graph.passes())
            require(source.w6CorePrimitiveHostRecipes.keys == expectedCorePrimitiveHosts.keys)
            source.w6CorePrimitiveHostRecipes.forEach { (site, recipe) ->
                require(recipe.site == site && recipe == expectedCorePrimitiveHosts.getValue(site)) {
                    "W6 CorePrimitive host recipe changed after final pass binding."
                }
            }
            val expectedPreparedVerticesHosts = freezeW6PreparedVerticesHostsV1(graph.passes(), requireNotNull(graph.materialTable))
            require(source.w6PreparedVerticesHostRecipes.keys == expectedPreparedVerticesHosts.keys)
            source.w6PreparedVerticesHostRecipes.forEach { (site, recipe) ->
                require(recipe.site == site && recipe == expectedPreparedVerticesHosts.getValue(site)) {
                    "W6 prepared-vertices host recipe changed after final pass binding."
                }
            }
            val expectedPlainLayerComposites = freezeW6PlainLayerCompositeRecipesV1(graph.passes())
            require(source.w6PlainLayerCompositeRecipes.keys == expectedPlainLayerComposites.keys)
            source.w6PlainLayerCompositeRecipes.forEach { (site, recipe) ->
                require(recipe.site == site && recipe == expectedPlainLayerComposites.getValue(site)) {
                    "W6 plain layer-composite recipe changed after final pass binding."
                }
            }
            val expectedClipMaskInitializes = freezeW4eClipMaskInitializeRecipesV1(source.w4eGeometry)
            require(source.w4eClipMaskInitializeRecipes.keys == expectedClipMaskInitializes.keys)
            source.w4eClipMaskInitializeRecipes.forEach { (passId, recipe) ->
                val bound = source.w4eGeometry.single { passId in it.graphPassIds() }.nativePass(passId)
                val final = graph.passes().single { it.id == passId }
                val output = rows.single { it.id == recipe.output }
                require(bound === final && final is PlanPass.ClipMaskInitialize &&
                    recipe.passId == passId && recipe == expectedClipMaskInitializes.getValue(passId) &&
                    output.format == PlanTextureFormat.CoverageMask && output.sampleCountI32 == 1 &&
                    PlanResourceUsage.RenderAttachment in output.usages()) {
                    "W4e ClipMaskInitialize recipe changed after final binding."
                }
            }
            // A frozen Clear/DrawColor Picture entry owns a LegacyColor operand directly.  It
            // has no W5 source uniform (and must not fabricate one after graph construction),
            // while every material-backed draw retains the exact existing W5 row.
            val uniforms = RenderGraph.visualDraws(graph.passes()).mapNotNull { draw ->
                if (draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1) return@mapNotNull null
                val table = requireNotNull(graph.materialTable)
                val ref = draw.materialAuthority.materialPlanRef()
                val identity = if (draw.materialAuthority.colorSourceCoordinatesV4() != null)
                    RawMaterialRequirementsV2.measureV4(table, ref).canonicalIdentity
                else RawMaterialRequirementsV2.measureLegacy(table, ref).canonicalIdentity
                draw.commandIndex to source.uniforms.getValue(identity)
            }.toMap()
            val maskShaderUniforms = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                ((pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding
                    as? FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned)?.uniformResource
            }.toSet()
            val graphTextureUniforms = graph.passes().filterIsInstance<PlanPass.PictureSourcePass>().mapNotNull { pass ->
                pass.graphTextureOperand?.uniformResource
            }.toSet()
            val colorFilterUniforms = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                (pass.operation as? FilterPassOperationV1.ColorFilter)?.uniformResource
            }.toSet()
            graph.passes().filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
                val operation = pass.operation as? FilterPassOperationV1.ColorFilter ?: return@forEach
                val uniform = requireNotNull(operation.uniformResource) {
                    "W6c ColorFilter must publish its W5f uniform before the physical layout seals."
                }
                val capacity = requireNotNull(operation.uniformCapacityBytesI64)
                val offset = requireNotNull(operation.uniformOffsetBytesI64)
                val row = rows.single { it.id == uniform }
                require(row.role == PlanResourceRole.SourceUniformData && row.byteSize == capacity)
                requireW6cColorUniformWindow(offset, capacity, operation.execution.dynamicByteCountI64)
                val identity = W6cComposePlanner.colorUniformIdentity(operation.execution)
                require(source.uniforms.getValue(identity) == uniform)
                val binding = source.w6cColorUniformBindings.getValue(identity)
                require(binding.resourceId == uniform && binding.offsetBytesI64 == offset && binding.capacityBytesI64 == capacity)
            }
            require((uniforms.values.toSet() + maskShaderUniforms + graphTextureUniforms + colorFilterUniforms) ==
                source.uniforms.values.toSet())
            val geometry = graph.passes().mapNotNull { pass ->
                if (source.w4eGeometry.any { pass.id in it.graphPassIds() }) return@mapNotNull null
                val data = when (pass) {
                    is PlanPass.RenderPass -> pass.drawDataResources
                    is PlanPass.StencilGeometryProducerV3 -> pass.drawDataResources
                    is PlanPass.StencilCover -> pass.drawDataResources
                    is PlanPass.FilterCoverageSourcePass -> pass.rasterBinding?.drawDataResources
                    else -> null
                } ?: return@mapNotNull null
                val draw = when (pass) {
                    is PlanPass.RenderPass -> pass.draws().single()
                    is PlanPass.StencilCover -> pass.draw
                    is PlanPass.StencilGeometryProducerV3 -> graph.passes().filterIsInstance<PlanPass.StencilCover>()
                        .single { it.draw.commandIndex == pass.commandIndexI32 }.draw
                    is PlanPass.FilterCoverageSourcePass -> requireNotNull(pass.rasterBinding).draw
                    else -> error("Unreachable data binding")
                }
                val fill = (draw as? PathDraw)?.copyPathGeometry()?.let { shape -> when (shape) {
                    is PathDrawGeometry.Fill -> shape.valueF32
                    is PathDrawGeometry.Stroke -> shape.valueF32.copyFillGeometryF32()
                    else -> error("Unadmitted path data binding")
                } }
                val fan = fill?.copyStencilEdgeFanF32OrNull()
                val direct = fill?.copyDirectTriangleF32OrNull()
                val producer = pass is PlanPass.StencilGeometryProducerV3
                val cover = pass is PlanPass.StencilCover || pass is PlanPass.FilterCoverageSourcePass &&
                    draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover
                val binding = if (draw is W5bVerticesDraw) {
                    val upload = requireNotNull(draw.sealedUploadPayloadOrNull())
                    require(upload.vertexCountI32 == draw.geometryF32.vertexCountI32 &&
                        upload.indexCountI32 == draw.geometryF32.indexCountI32 &&
                        upload.vertexStrideBytesI32 == draw.vertexStrideBytesI32 &&
                        upload.indexElementBytesI32 == draw.indexElementBytesI32)
                    PlanGeometryBufferBindingV1(data, 0L, 0L, 0L,
                        upload.vertexCountI32, upload.indexCountI32 ?: 0, draw.geometryF32.maxIndexI32 ?: 0,
                        64L, upload.vertexStrideBytesI32, upload.indexElementBytesI32 ?: 0, upload)
                } else PlanGeometryBufferBindingV1(data,
                    if (cover) Math.multiplyExact(requireNotNull(fan).vertexCountI32.toLong(), 8L) else 0L,
                    if (cover) Math.multiplyExact(requireNotNull(fan).indexCountI32.toLong(), 4L) else 0L,
                    if (cover) maxOf(32L, graph.capabilities.minUniformBufferOffsetAlignment.toLong()) else 0L,
                    if (producer) requireNotNull(fan).vertexCountI32 else if (draw is W5bPointDraw) draw.copyVerticesF32().size / 2 else direct?.vertexCountI32 ?: 4,
                    if (producer) requireNotNull(fan).indexCountI32 else if (draw is W5bPointDraw) draw.copyIndicesI32().size else direct?.indexCountI32 ?: 6,
                    if (producer) requireNotNull(fan).copyIndicesI32().max() else if (draw is W5bPointDraw) draw.copyIndicesI32().max() else direct?.copyIndicesI32()?.max() ?: 3,
                    if (draw is AnalyticRectDraw || draw is AnalyticRRectDraw) 80L else 32L)
                require(Math.addExact(binding.vertexOffsetI64, binding.vertexBytesI64) <= rows.single { it.id == data.vertex }.byteSize &&
                    Math.addExact(binding.indexOffsetI64, binding.indexUploadBytesI64) <= rows.single { it.id == data.index }.byteSize &&
                    Math.addExact(binding.uniformOffsetI64, binding.uniformBytesI64) <= rows.single { it.id == data.uniform }.byteSize)
                pass.id to binding
            }.toMap()
            source.w6CorePrimitiveHostRecipes.values.filterIsInstance<W6PointHostRecipeV1>().forEach { recipe ->
                val render = graph.passes().filterIsInstance<PlanPass.RenderPass>().single { it.id == recipe.site.ownerPassId }
                val draw = render.draws()[recipe.site.drawOrdinalI32] as W5bPointDraw
                val binding = geometry.getValue(render.id)
                require(draw.clipOnly == null && draw.pointMode == recipe.pointMode &&
                    draw.copyVerticesF32().contentEquals(recipe.copyVerticesF32()) &&
                    draw.copyIndicesI32().contentEquals(recipe.copyIndicesI32()) &&
                    draw.copyBoundsI32() == recipe.bounds && draw.copyScissorI32() == recipe.scissor &&
                    binding.vertexCountI32 == recipe.vertexCountI32 &&
                    binding.indexCountI32 == recipe.indexCountI32 &&
                    binding.maxLocalIndexI32 == recipe.maxIndexI32 &&
                    binding.uniformBytesI64 == 32L) {
                    "W6 Point host recipe or physical source changed after final pass binding."
                }
            }
            source.w6PreparedVerticesHostRecipes.values.forEach { recipe ->
                val render = graph.passes().filterIsInstance<PlanPass.RenderPass>().single { it.id == recipe.site.ownerPassId }
                val draw = render.draws()[recipe.site.drawOrdinalI32] as W5bVerticesDraw
                val payload = requireNotNull(draw.sealedUploadPayloadOrNull())
                val binding = geometry.getValue(render.id)
                require(payload.canonicalIdentity == recipe.payloadCanonicalIdentity &&
                    binding.verticesUploadPayload?.canonicalIdentity == recipe.payloadCanonicalIdentity &&
                    binding.vertexStrideBytesI32 == recipe.layout.strideBytesI32 && binding.uniformBytesI64 == 64L &&
                    binding.vertexCountI32 == payload.vertexCountI32 && binding.indexCountI32 == (payload.indexCountI32 ?: 0) &&
                    binding.indexElementBytesI32 == (payload.indexElementBytesI32 ?: 0) && draw.blend == recipe.blend) {
                    "W6 prepared-vertices host recipe or physical source changed after final pass binding."
                }
            }
            source.w4eGeometry.forEach { lane ->
                require(lane.payload.matchesDeclaredResources(rows))
                require(rows.single { it.id == lane.target }.copyExtent() == lane.copyExtentI32())
                require(lane.graphPassIds().all { id -> graph.passes().any { it.id == id } })
                require(lane.nativePasses().filterIsInstance<PlanPass.PathRenderPass>().all { it.target == lane.target })
                require(lane.graphPassIds().all { id -> when (val pass = graph.passes().single { it.id == id }) {
                    is PlanPass.RenderPass -> pass.target == lane.target
                    is PlanPass.StencilGeometryProducerV3 -> pass.target == lane.target
                    is PlanPass.StencilCover -> pass.target == lane.target
                    is PlanPass.PathRenderPass -> pass.target == lane.target
                    is PlanPass.ClipMaskInitialize,
                    is PlanPass.ClipMaskProducer,
                    is PlanPass.ClipMaskFold,
                    -> true
                    else -> false
                } }) { "W4e native binding target must equal its semantic graph pass target." }
            }
            require(source.w4eGeometry.flatMap { it.graphPassIds() }.let { it.size == it.distinct().size })
            val pictures = graph.passes().mapNotNull { pass ->
                val operand = when (pass) {
                    is PlanPass.PictureComposite -> requireNotNull(pass.operands)
                    is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Picture)?.terminal
                    else -> null
                } ?: return@mapNotNull null
                val sourceRow = rows.single { it.id == operand.source }
                require(PlanResourceUsage.Sampled in sourceRow.usages())
                val extent = requireNotNull(sourceRow.copyExtent())
                val rect = operand.copySourceBoundsTargetI32()
                require(rect.left >= 0 && rect.top >= 0 && rect.right <= extent.width && rect.bottom <= extent.height)
                pass.id to operand
            }.toMap()
            val spatialCaches = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                // A physical id is frame-local and cannot establish cache provenance.  Only an
                // immutable captured SceneSnapshot revision authorizes cross-frame reuse.
                val sourceRevision = pass.evaluationKey.sourceRevisionIdentity ?: return@mapNotNull null
                val output = rows.single { it.id == pass.output }
                val operation = pass.operation
                val orderedInputs = pass.inputs().map { input ->
                    val row = rows.single { it.id == input }
                    val sourceIdentity = "$sourceRevision:${input.value}:${row.role}:${row.ordinal}"
                    SpatialFilterInputGenerationV1(
                        sourceIdentity = sourceIdentity,
                        generationI64 = spatialCacheGenerationV1(sourceIdentity),
                        subsetDeviceI32 = when (input) {
                            pass.evaluationKey.boundSourceId -> pass.evaluationKey.copyDesiredOutputDeviceI32()
                            else -> operation.bounds.copyRequiredInputDeviceI32()
                        },
                    )
                }
                SpatialFilterCachePlanV1(
                    outputResourceId = pass.output,
                    key = SpatialFilterCacheKeyV1(
                        passIdentity = pass.id.value,
                        evaluationKey = pass.evaluationKey,
                        bounds = operation.bounds,
                        format = (output.format as PlanTextureFormat.Color).value,
                        colorSpaceIdentity = "rgba8-srgb-linear-premul",
                        sampleCountI32 = output.sampleCountI32,
                        capabilityGenerationI64 = graph.capabilities.deviceGeneration,
                        backendGenerationI64 = graph.capabilities.deviceGeneration,
                        semanticVersionI32 = 1,
                        inputGenerations = orderedInputs,
                    ),
                    reservedBytesI64 = output.byteSize,
                )
            }
            require(spatialCaches.map { it.outputResourceId }.distinct().size == spatialCaches.size)
            val layout = PlanPhysicalLayoutV1(rows, source.caches, uniforms, geometry, source.w4eGeometry, pictures, spatialCaches,
                graph.w6dProgramLeases(), source.w6SolidRectHostRecipes, source.w6CorePrimitiveHostRecipes,
                source.w6PreparedVerticesHostRecipes, source.w6PlainLayerCompositeRecipes, source.w4eClipMaskInitializeRecipes)
            require(layout.slots.map { it.resourceId }.distinct().size == layout.slots.size)
            require(layout.programSlots().map { it.slotI32 }.distinct().size == layout.programSlots().size)
            val frozenPrograms = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                pass.frozenSamplingProgram?.let { pass.id to it }
            }
            require(layout.programSlots().map { it.lease.ownerPassId }.toSet() == frozenPrograms.map { it.first }.toSet())
            frozenPrograms.forEach { (ownerPassId, binding) ->
                require(layout.programSlot(ownerPassId).lease.matches(binding, graph.capabilities.deviceGeneration, graph.passes().size))
            }
            // All reservations (including cache hits) remain live until frame completion.
            require(rows.all { it.firstPassIndex == 0 && it.lastPassIndexExclusive == graph.passes().size })
            source.caches.forEach { binding ->
                val request = binding.request
                if (request !is PlanCacheResourceRequest.Sampler) {
                    val row = layout.resource(binding.resourceId)
                    require(row.byteSize == request.byteSizeI64 && row.lifetime == request.lifetime)
                    if (request is PlanCacheResourceRequest.Storage) require(row.role == PlanResourceRole.RuntimeStorageData &&
                        row.kind == PlanResourceKind.Buffer && row.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination))
                }
                if (request is PlanCacheResourceRequest.Texture) {
                    val row = layout.resource(binding.resourceId)
                    require(row.format == PlanTextureFormat.ImageV1(request.format) && row.copyExtent()?.let {
                        it.width == request.widthI32 && it.height == request.heightI32 } == true && row.usages() == request.usages())
                    val upload = layout.resource(requireNotNull(binding.uploadResourceId))
                    val rowBytes = requireNotNull(binding.uploadBytesPerRowI64)
                    require(upload.role == PlanResourceRole.ImageUploadStaging &&
                        upload.byteSize == Math.multiplyExact(rowBytes, request.heightI32.toLong()) &&
                        rowBytes >= request.widthI32.toLong() * request.format.bytesPerPixelI32 &&
                        rowBytes % 256L == 0L && rowBytes % graph.capabilities.copyBytesPerRowAlignment == 0L)
                } else require(binding.uploadResourceId == null && binding.uploadBytesPerRowI64 == null)
            }
            return layout
        }
    }
}

/** Stable non-cryptographic generation tag for an already immutable canonical source revision. */
private fun spatialCacheGenerationV1(identity: String): Long {
    var value = -3750763034362895579L // FNV-1a offset basis
    identity.encodeToByteArray().forEach { byte ->
        value = (value xor (byte.toLong() and 0xffL)) * 1099511628211L
    }
    return value and Long.MAX_VALUE
}
