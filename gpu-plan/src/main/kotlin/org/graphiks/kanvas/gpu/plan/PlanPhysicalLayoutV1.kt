package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.PreparedVerticesUploadPayloadV1

/** A sealed allocation slot. No two resource IDs alias without an explicit future proof. */
public class PlanPhysicalSlotV1 internal constructor(
    public val slotI32: Int,
    public val resourceId: PlanResourceId,
)

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
    val w4eGeometry: List<PlanW4eGeometryBindingV1> = emptyList(),
)

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
) {
    private val resources = immutableList(resources)
    private val caches = immutableList(cacheBindings)
    private val uniforms = java.util.Collections.unmodifiableMap(LinkedHashMap(uniformsByCommand))
    private val geometry = java.util.Collections.unmodifiableMap(LinkedHashMap(geometryByPass))
    private val w4e = immutableList(w4eGeometry)
    private val slots = immutableList((resources.map { it.id } + caches.filter {
        it.request is PlanCacheResourceRequest.Sampler
    }.map { it.resourceId }).mapIndexed { index, id -> PlanPhysicalSlotV1(index, id) })

    public fun slots(): List<PlanPhysicalSlotV1> = slots
    public fun cacheBindings(): List<PlanCacheBindingV1> = caches
    public fun slot(resourceId: PlanResourceId): PlanPhysicalSlotV1 = slots.single { it.resourceId == resourceId }
    public fun resource(resourceId: PlanResourceId): PlanResource {
        slot(resourceId)
        return resources.single { it.id == resourceId }
    }
    public fun sourceUniform(commandIndexI32: Int): PlanResource = resource(uniforms.getValue(commandIndexI32))
    public fun geometryBinding(passId: PlanPassId): PlanGeometryBufferBindingV1? = geometry[passId]
    public fun w4eGeometryBindings(): List<PlanW4eGeometryBindingV1> = w4e
    public fun w4eGeometryBinding(passId: PlanPassId): PlanW4eGeometryBindingV1? = w4e.singleOrNull { passId in it.graphPassIds() }
    public fun cacheBinding(request: PlanCacheResourceRequest): PlanCacheBindingV1 = caches.single {
        it.request === request
    }.also { slot(it.resourceId) }

    internal companion object {
        fun seal(graph: RenderGraphConstruction, source: SourcePhysicalConstructionV1): PlanPhysicalLayoutV1 {
            val rows = graph.resources()
            require(source.resources.all { row -> rows.any { it === row } })
            require(source.uniforms.values.toSet() == rows.filter { it.role == PlanResourceRole.SourceUniformData }.map { it.id }.toSet())
            require(source.caches.filter { it.request !is PlanCacheResourceRequest.Sampler }.map { it.resourceId }.toSet() ==
                rows.filter { it.lifetime == PlanResourceLifetime.DeviceSessionCache }.map { it.id }.toSet())
            val uniforms = RenderGraph.visualDraws(graph.passes()).associate { draw ->
                val table = requireNotNull(graph.materialTable)
                val ref = draw.materialAuthority.materialPlanRef()
                val identity = if (draw.materialAuthority.colorSourceCoordinatesV4() != null)
                    RawMaterialRequirementsV2.measureV4(table, ref).canonicalIdentity
                else RawMaterialRequirementsV2.measureLegacy(table, ref).canonicalIdentity
                draw.commandIndex to source.uniforms.getValue(identity)
            }
            require(uniforms.values.toSet() == source.uniforms.values.toSet())
            val geometry = graph.passes().mapNotNull { pass ->
                if (source.w4eGeometry.any { pass.id in it.graphPassIds() }) return@mapNotNull null
                val data = when (pass) {
                    is PlanPass.RenderPass -> pass.drawDataResources
                    is PlanPass.StencilGeometryProducerV3 -> pass.drawDataResources
                    is PlanPass.StencilCover -> pass.drawDataResources
                    else -> null
                } ?: return@mapNotNull null
                val draw = when (pass) {
                    is PlanPass.RenderPass -> pass.draws().single()
                    is PlanPass.StencilCover -> pass.draw
                    is PlanPass.StencilGeometryProducerV3 -> graph.passes().filterIsInstance<PlanPass.StencilCover>()
                        .single { it.draw.commandIndex == pass.commandIndexI32 }.draw
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
                val cover = pass is PlanPass.StencilCover
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
            source.w4eGeometry.forEach { lane ->
                require(lane.payload.matchesDeclaredResources(rows))
                require(rows.single { it.id == lane.target }.copyExtent() == lane.copyExtentI32())
                require(lane.graphPassIds().all { id -> graph.passes().any { it.id == id } })
                require(lane.nativePasses().filterIsInstance<PlanPass.PathRenderPass>().all { it.target == lane.target })
            }
            require(source.w4eGeometry.flatMap { it.graphPassIds() }.let { it.size == it.distinct().size })
            val layout = PlanPhysicalLayoutV1(rows, source.caches, uniforms, geometry, source.w4eGeometry)
            require(layout.slots.map { it.resourceId }.distinct().size == layout.slots.size)
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
