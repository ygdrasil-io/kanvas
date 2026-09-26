package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.kanvas.render.ir.PreparedVerticesUploadPayloadV1

/** Fixed W6 host axes for the already sealed prepared-vertices payload. */
public enum class W6PreparedVerticesHostFamilyV1 { PreparedVertices }
public enum class W6PreparedVerticesHostTopologyV1 { TriangleList, TriangleStrip }
public enum class W6PreparedVerticesHostIndexWidthV1 { None, Uint16, Uint32 }
public enum class W6PreparedVerticesHostAttributeV1 { Position, Color, TexCoord }
public enum class W6PreparedVerticesHostPrimitiveAlphaV1 { Absent, VertexColor }
public enum class W6PreparedVerticesHostTargetFormatV1 { RGBA8UnormSrgb }
public enum class W6PreparedVerticesHostUniformAbiV1 { DrawUniform64 }
public enum class W6PreparedVerticesHostGroupZeroAbiV1 { DrawUniform64 }
public enum class W6PreparedVerticesHostCoordinateSlotV1 { InputPosition }
public enum class W6PreparedVerticesHostMaterialSourceKindV1 { MaterialV5, ColorSourceV4 }

/** Immutable vertex-input layout already encoded by [PreparedVerticesUploadPayloadV1]. */
public class W6PreparedVerticesHostLayoutV1 internal constructor(
    attributes: List<W6PreparedVerticesHostAttributeV1>,
    offsetsBytesI32: Map<W6PreparedVerticesHostAttributeV1, Int>,
    public val strideBytesI32: Int,
) {
    private val attributesSnapshot = java.util.Collections.unmodifiableList(attributes.toList())
    private val offsetsSnapshot = java.util.Collections.unmodifiableMap(LinkedHashMap(offsetsBytesI32))
    public fun attributes(): List<W6PreparedVerticesHostAttributeV1> = attributesSnapshot
    public fun offsetsBytesI32(): Map<W6PreparedVerticesHostAttributeV1, Int> = offsetsSnapshot

    init {
        require(attributesSnapshot.firstOrNull() == W6PreparedVerticesHostAttributeV1.Position)
        require(attributesSnapshot.size == attributesSnapshot.distinct().size && strideBytesI32 >= 8)
        require(offsetsSnapshot.keys == attributesSnapshot.toSet())
        require(offsetsSnapshot.getValue(W6PreparedVerticesHostAttributeV1.Position) == 0)
        require(offsetsSnapshot.values.all { it >= 0 && it < strideBytesI32 })
    }

    override fun equals(other: Any?): Boolean = other is W6PreparedVerticesHostLayoutV1 &&
        attributesSnapshot == other.attributesSnapshot && offsetsSnapshot == other.offsetsSnapshot &&
        strideBytesI32 == other.strideBytesI32

    override fun hashCode(): Int = listOf(attributesSnapshot, offsetsSnapshot, strideBytesI32).fold(1) { hash, value ->
        31 * hash + value.hashCode()
    }
}

/** Typed source identity selected by the final planner, never by native materialization. */
public class W6PreparedVerticesHostMaterialSourceV1 internal constructor(
    public val kind: W6PreparedVerticesHostMaterialSourceKindV1,
    public val materialRef: MaterialPlanRef,
    public val programStructuralId: MaterialProgramPlanId,
    public val canonicalBindingIdentity: String,
) {
    init { require(canonicalBindingIdentity.isNotBlank()) }
    override fun equals(other: Any?): Boolean = other is W6PreparedVerticesHostMaterialSourceV1 &&
        kind == other.kind && materialRef == other.materialRef && programStructuralId == other.programStructuralId &&
        canonicalBindingIdentity == other.canonicalBindingIdentity
    override fun hashCode(): Int = listOf(kind, materialRef, programStructuralId, canonicalBindingIdentity)
        .fold(1) { hash, value -> 31 * hash + value.hashCode() }

    /** Rechecks the planner-owned material/source table without exposing its internal footprint API. */
    public fun authenticates(table: MaterialPlanTable): Boolean = runCatching {
        table.entry(materialRef).program.structuralId == programStructuralId &&
            RawMaterialRequirementsV2.measureV4(table, materialRef).canonicalIdentity == canonicalBindingIdentity
    }.getOrDefault(false)
}

/** Canonical, handle-free program identity. WGSL remains a renderer implementation detail. */
public class W6PreparedVerticesHostProgramIdentityV1 internal constructor(
    public val canonicalIdentity: String,
) {
    init { require(canonicalIdentity.startsWith("w6-prepared-vertices-host-v1:")) }
    override fun equals(other: Any?): Boolean = other is W6PreparedVerticesHostProgramIdentityV1 &&
        canonicalIdentity == other.canonicalIdentity
    override fun hashCode(): Int = canonicalIdentity.hashCode()
}

/** Planner-owned target facts; neither format nor sample count is a native default. */
public class W6PreparedVerticesHostTargetV1 internal constructor(
    public val format: W6PreparedVerticesHostTargetFormatV1,
    public val sampleCountI32: Int,
) {
    init { require(format == W6PreparedVerticesHostTargetFormatV1.RGBA8UnormSrgb && sampleCountI32 == 1) }
    override fun equals(other: Any?): Boolean = other is W6PreparedVerticesHostTargetV1 &&
        format == other.format && sampleCountI32 == other.sampleCountI32
    override fun hashCode(): Int = 31 * format.hashCode() + sampleCountI32
    public companion object {
        public val Rgba8UnormSrgbSingleSample: W6PreparedVerticesHostTargetV1 =
            W6PreparedVerticesHostTargetV1(W6PreparedVerticesHostTargetFormatV1.RGBA8UnormSrgb, 1)
    }
}

/** Immutable final RenderPass recipe for one prepared-vertices draw. Upload bytes stay in the payload. */
public class W6PreparedVerticesHostRecipeV1 internal constructor(
    public val site: W6GeometrySiteKeyV1,
    public val family: W6PreparedVerticesHostFamilyV1,
    public val layout: W6PreparedVerticesHostLayoutV1,
    public val topology: W6PreparedVerticesHostTopologyV1,
    public val indexWidth: W6PreparedVerticesHostIndexWidthV1,
    public val primitiveAlpha: W6PreparedVerticesHostPrimitiveAlphaV1,
    public val source: W6PreparedVerticesHostMaterialSourceV1,
    public val blend: BlendPlan,
    public val target: W6PreparedVerticesHostTargetV1,
    public val uniformAbi: W6PreparedVerticesHostUniformAbiV1,
    public val groupZeroAbi: W6PreparedVerticesHostGroupZeroAbiV1,
    public val coordinateSlot: W6PreparedVerticesHostCoordinateSlotV1,
    materialOriginDeviceI32: Point2I32,
    public val payloadCanonicalIdentity: String,
    public val hostProgramIdentity: W6PreparedVerticesHostProgramIdentityV1,
) {
    private val materialOriginSnapshot = Point2I32(materialOriginDeviceI32.x, materialOriginDeviceI32.y)
    public val materialOriginDeviceI32: Point2I32 get() = Point2I32(materialOriginSnapshot.x, materialOriginSnapshot.y)

    init {
        require(family == W6PreparedVerticesHostFamilyV1.PreparedVertices)
        require(target == W6PreparedVerticesHostTargetV1.Rgba8UnormSrgbSingleSample)
        require(uniformAbi == W6PreparedVerticesHostUniformAbiV1.DrawUniform64 &&
            groupZeroAbi == W6PreparedVerticesHostGroupZeroAbiV1.DrawUniform64 &&
            coordinateSlot == W6PreparedVerticesHostCoordinateSlotV1.InputPosition)
        require(payloadCanonicalIdentity.isNotBlank())
        require((primitiveAlpha == W6PreparedVerticesHostPrimitiveAlphaV1.VertexColor) ==
            layout.attributes().contains(W6PreparedVerticesHostAttributeV1.Color))
    }

    override fun equals(other: Any?): Boolean = other is W6PreparedVerticesHostRecipeV1 &&
        site == other.site && family == other.family && layout == other.layout && topology == other.topology &&
        indexWidth == other.indexWidth && primitiveAlpha == other.primitiveAlpha && source == other.source &&
        blend == other.blend && target == other.target && uniformAbi == other.uniformAbi &&
        groupZeroAbi == other.groupZeroAbi && coordinateSlot == other.coordinateSlot &&
        materialOriginSnapshot == other.materialOriginSnapshot && payloadCanonicalIdentity == other.payloadCanonicalIdentity &&
        hostProgramIdentity == other.hostProgramIdentity

    override fun hashCode(): Int = listOf(site, family, layout, topology, indexWidth, primitiveAlpha, source, blend,
        target, uniformAbi, groupZeroAbi, coordinateSlot, materialOriginSnapshot, payloadCanonicalIdentity,
        hostProgramIdentity).fold(1) { hash, value -> 31 * hash + value.hashCode() }
}

/** Freezes only W5b Vertices draws in final RenderPass planner order. */
public fun freezeW6PreparedVerticesHostsV1(
    passes: List<PlanPass>,
    table: MaterialPlanTable,
): Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1> {
    val recipes = linkedMapOf<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1>()
    passes.forEach { pass ->
        val render = pass as? PlanPass.RenderPass ?: return@forEach
        val origin = requireNotNull(render.copyMaterialDeviceOriginI32()) {
            "W6 prepared-vertices host recipe requires the final material origin for ${render.id.value}."
        }
        render.draws().forEachIndexed { drawOrdinalI32, draw ->
            val vertices = draw as? W5bVerticesDraw ?: return@forEachIndexed
            val payload = requireNotNull(vertices.sealedUploadPayloadOrNull()) {
                "W6 prepared-vertices host recipe requires the sealed upload payload."
            }
            val site = W6GeometrySiteKeyV1(render.id, drawOrdinalI32)
            val layout = preparedVerticesLayout(payload)
            val source = preparedVerticesSource(vertices.materialAuthority, table)
            val recipe = W6PreparedVerticesHostRecipeV1(
                site = site,
                family = W6PreparedVerticesHostFamilyV1.PreparedVertices,
                layout = layout,
                topology = when (payload.topology) {
                    PreparedVerticesUploadPayloadV1.Topology.TriangleList -> W6PreparedVerticesHostTopologyV1.TriangleList
                    PreparedVerticesUploadPayloadV1.Topology.TriangleStrip -> W6PreparedVerticesHostTopologyV1.TriangleStrip
                },
                indexWidth = when (payload.indexElementBytesI32) {
                    null -> W6PreparedVerticesHostIndexWidthV1.None
                    2 -> W6PreparedVerticesHostIndexWidthV1.Uint16
                    4 -> W6PreparedVerticesHostIndexWidthV1.Uint32
                    else -> error("Prepared vertices index width changed after sealing.")
                },
                primitiveAlpha = if (payload.hasColors) W6PreparedVerticesHostPrimitiveAlphaV1.VertexColor
                    else W6PreparedVerticesHostPrimitiveAlphaV1.Absent,
                source = source,
                blend = vertices.blend,
                target = W6PreparedVerticesHostTargetV1.Rgba8UnormSrgbSingleSample,
                uniformAbi = W6PreparedVerticesHostUniformAbiV1.DrawUniform64,
                groupZeroAbi = W6PreparedVerticesHostGroupZeroAbiV1.DrawUniform64,
                coordinateSlot = W6PreparedVerticesHostCoordinateSlotV1.InputPosition,
                materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
                payloadCanonicalIdentity = payload.canonicalIdentity,
                hostProgramIdentity = preparedVerticesProgramIdentity(layout, payload, vertices.blend, source),
            )
            require(recipes.put(site, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(recipes)
}

private fun preparedVerticesLayout(payload: PreparedVerticesUploadPayloadV1): W6PreparedVerticesHostLayoutV1 {
    val attributes = buildList {
        add(W6PreparedVerticesHostAttributeV1.Position)
        if (payload.hasColors) add(W6PreparedVerticesHostAttributeV1.Color)
        if (payload.hasTexCoords) add(W6PreparedVerticesHostAttributeV1.TexCoord)
    }
    var offset = 0
    val offsets = linkedMapOf<W6PreparedVerticesHostAttributeV1, Int>()
    attributes.forEach { attribute ->
        offsets[attribute] = offset
        offset += when (attribute) {
            W6PreparedVerticesHostAttributeV1.Position,
            W6PreparedVerticesHostAttributeV1.TexCoord,
            -> 8
            W6PreparedVerticesHostAttributeV1.Color -> 4
        }
    }
    require(offset == payload.vertexStrideBytesI32)
    return W6PreparedVerticesHostLayoutV1(attributes, offsets, payload.vertexStrideBytesI32)
}

private fun preparedVerticesSource(
    authority: PlanDrawMaterialAuthority,
    table: MaterialPlanTable,
): W6PreparedVerticesHostMaterialSourceV1 {
    val ref = authority.materialPlanRef()
    val coordinates = authority.colorSourceCoordinatesV4()
    val kind = when (authority) {
        is PlanDrawMaterialAuthority.MaterialV5 -> W6PreparedVerticesHostMaterialSourceKindV1.MaterialV5
        is PlanDrawMaterialAuthority.MaterialV4 -> W6PreparedVerticesHostMaterialSourceKindV1.ColorSourceV4
        else -> error("W6 prepared vertices only admits W5 material source authorities.")
    }
    val canonicalBindingIdentity = if (coordinates != null) RawMaterialRequirementsV2.measureV4(table, ref).canonicalIdentity
        else RawMaterialRequirementsV2.measureLegacy(table, ref).canonicalIdentity
    return W6PreparedVerticesHostMaterialSourceV1(kind, ref, table.entry(ref).program.structuralId,
        canonicalBindingIdentity)
}

private fun preparedVerticesProgramIdentity(
    layout: W6PreparedVerticesHostLayoutV1,
    payload: PreparedVerticesUploadPayloadV1,
    blend: BlendPlan,
    source: W6PreparedVerticesHostMaterialSourceV1,
): W6PreparedVerticesHostProgramIdentityV1 = W6PreparedVerticesHostProgramIdentityV1(
    "w6-prepared-vertices-host-v1:" + listOf(
        "layout=${layout.attributes().joinToString(",")}:${layout.offsetsBytesI32().entries.joinToString(",")}:${layout.strideBytesI32}",
        "topology=${payload.topology.name}",
        "index=${payload.indexElementBytesI32 ?: 0}",
        "primitiveAlpha=${payload.hasColors}",
        "blend=${blend.canonicalLabel}",
        "target=rgba8unorm-srgb:1",
        "abi=group0-draw-uniform-64",
        "source=${source.kind}:${source.programStructuralId.value}:${source.canonicalBindingIdentity}",
    ).joinToString("|"),
)
