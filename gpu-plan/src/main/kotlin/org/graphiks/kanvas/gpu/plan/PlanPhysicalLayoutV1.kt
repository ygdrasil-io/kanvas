package org.graphiks.kanvas.gpu.plan

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
)

/** Graph-owned binding and allocation authority, frozen after the frame-wide W5 source binding. */
public class PlanPhysicalLayoutV1 private constructor(
    resources: List<PlanResource>,
    cacheBindings: List<PlanCacheBindingV1>,
    uniformsByCommand: Map<Int, PlanResourceId>,
) {
    private val resources = immutableList(resources)
    private val caches = immutableList(cacheBindings)
    private val uniforms = java.util.Collections.unmodifiableMap(LinkedHashMap(uniformsByCommand))
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
            val layout = PlanPhysicalLayoutV1(rows, source.caches, uniforms)
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
