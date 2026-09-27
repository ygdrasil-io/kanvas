package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.RectI32

/** Frozen native axes for the one W6 single-sample direct path-color packet. */
public enum class W6PathRenderDirectColorShaderFamilyV1 { W4ePathMaterial }
public enum class W6PathRenderDirectColorTopologyV1 { TriangleList }
public enum class W6PathRenderDirectColorGroupZeroAbiV1 { W4eConsumerUniform }

/**
 * The final, physical W4e direct-color draw.  This is deliberately limited to the ordinary
 * un-clipped direct-triangle site: masks, inverse domains and stencil phases retain their
 * own recipes.  All operands are rows that the payload actually consumes.
 */
public class W6PathRenderDirectColorRecipeV1 internal constructor(
    public val passId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val resolveTarget: W4eClipMaskProducerPhysicalOperandV1?,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1?,
    public val vertex: W4eClipMaskProducerPhysicalOperandV1,
    public val index: W4eClipMaskProducerPhysicalOperandV1,
    public val uniform: W4eClipMaskProducerPhysicalOperandV1,
    public val uniformOffsetBytesI64: Long,
    public val uniformByteSizeI64: Long,
    geometry: PathFillGeometryF32,
    scissor: RectI32,
    public val vertexFirstI32: Int,
    public val vertexCountI32: Int,
    public val indexFirstI32: Int,
    public val indexCountI32: Int,
    public val baseVertexI32: Int,
    public val maxLocalIndexI32: Int,
    public val antiAlias: Boolean,
    public val sampleCountI32: Int,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val blend: BlendPlan,
    public val shaderFamily: W6PathRenderDirectColorShaderFamilyV1 = W6PathRenderDirectColorShaderFamilyV1.W4ePathMaterial,
    public val topology: W6PathRenderDirectColorTopologyV1 = W6PathRenderDirectColorTopologyV1.TriangleList,
    public val groupZeroAbi: W6PathRenderDirectColorGroupZeroAbiV1 = W6PathRenderDirectColorGroupZeroAbiV1.W4eConsumerUniform,
) {
    private val geometrySnapshot = geometry
    private val scissorSnapshot = scissor.copy()
    public fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshot
    public fun copyScissorI32(): RectI32 = scissorSnapshot.copy()
    init {
        val triangle = geometry.copyDirectTriangleF32OrNull()
        require(packetOrdinalI32 >= 0 && triangle != null && vertexFirstI32 >= 0 && indexFirstI32 >= 0 && baseVertexI32 >= 0)
        require(vertexCountI32 == 3 && indexCountI32 == 3 && maxLocalIndexI32 in 0 until vertexCountI32)
        require(sampleCountI32 == 1 && target.sampleCountI32 == 1 && resolveTarget == null && depthStencil == null)
        require(PlanResourceUsage.RenderAttachment in target.usages())
        require(vertex.role == PlanResourceRole.VertexData && PlanResourceUsage.Vertex in vertex.usages())
        require(index.role == PlanResourceRole.IndexData && PlanResourceUsage.Index in index.usages())
        require(uniform.role == PlanResourceRole.UniformData && PlanResourceUsage.Uniform in uniform.usages())
        require(uniformOffsetBytesI64 >= 0L && uniformByteSizeI64 == 16L)
    }
}

public class W6PathRenderDirectColorNativeSiteRecipeV1 internal constructor(
    public val host: W6PathRenderDirectColorRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32 = 1
    override val owner = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family = NativeSiteRecipeFamilyV1.W6PathRenderDirectColor
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        fun operand(name: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$name.id", value.id.value); enum("$name.role", value.role); text("$name.format", value.format.toString())
            value.copyExtentI32()?.let { int("$name.width", it.width); int("$name.height", it.height) }
            int("$name.samples", value.sampleCountI32); long("$name.bytes", value.byteSizeI64); enum("$name.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { i, use -> enum("$name.use.$i", use) }
        }
        text("owner", host.passId.value); int("packet", host.packetOrdinalI32); int("bundle", 0)
        operand("target", host.target); text("resolve.present", "false"); text("depth.present", "false")
        operand("vertex", host.vertex); operand("index", host.index); operand("uniform", host.uniform)
        long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.bytes", host.uniformByteSizeI64)
        val geometry = host.copyGeometryF32(); enum("fill", geometry.fillRule); rect("scissor", host.copyScissorI32())
        geometry.copyDirectTriangleF32OrNull()!!.copyVerticesF32().forEachIndexed { i, value -> float("triangle.$i", value) }
        geometry.copyDirectTriangleF32OrNull()!!.copyIndicesI32().forEachIndexed { i, value -> int("index.$i", value) }
        int("vertex.first", host.vertexFirstI32); int("vertex.count", host.vertexCountI32); int("index.first", host.indexFirstI32); int("index.count", host.indexCountI32); int("baseVertex", host.baseVertexI32); int("maxLocalIndex", host.maxLocalIndexI32)
        int("antiAlias", if (host.antiAlias) 1 else 0); int("samples", host.sampleCountI32); enum("shader", host.shaderFamily); enum("topology", host.topology); enum("abi", host.groupZeroAbi); blend("blend", host.blend); enum("load", host.load); enum("store", host.store)
    }
}

public fun freezeW6PathRenderDirectColorRecipesV1(bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>): Map<PlanPassId, W6PathRenderDirectColorRecipeV1> {
    val rows = resources.associateBy { it.id }
    fun operand(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())
    return java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6PathRenderDirectColorRecipeV1>().apply {
        bindings.forEach { binding -> binding.nativePasses().forEach { candidate ->
            val pass = candidate as? PlanPass.PathRenderPass ?: return@forEach
            if (pass.phase != PathRenderPhase.SingleSampleDirectColor) return@forEach
            // IIIa1 owns only the un-clipped Fill direct-triangle packet.  Other DirectColor
            // forms (stroke, clipped/masked, and non-triangle fill) deliberately keep the
            // established W4e fallback until their own site recipes are introduced.
            val draw = pass.draw as? GeneralPathDraw ?: return@forEach
            val geometry = (draw.copyPathGeometry() as? PathDrawGeometry.Fill)?.valueF32 ?: return@forEach
            if (geometry.copyDirectTriangleF32OrNull() == null) return@forEach
            val geometrySlice = requireNotNull(binding.payload.geometrySlice(pass.id.value, W4eNativePayloadPlan.CONSUMER_DIRECT))
            val uniformSlice = requireNotNull(binding.payload.uniformSlice(pass.id.value, W4eNativePayloadPlan.CONSUMER_UNIFORM))
            require(put(pass.id, W6PathRenderDirectColorRecipeV1(pass.id, pass.ordinal, operand(rows.getValue(pass.target)), pass.resolveTarget?.let { operand(rows.getValue(it)) }, pass.depthStencil?.let { operand(rows.getValue(it)) }, operand(rows.getValue(binding.payload.vertexResourceId)), operand(rows.getValue(binding.payload.indexResourceId)), operand(rows.getValue(binding.payload.uniformResourceId)), uniformSlice.offsetBytes, uniformSlice.byteSize, geometry, pass.draw.copyScissorI32(), geometrySlice.baseVertex, geometrySlice.vertexCount, geometrySlice.firstIndex, geometrySlice.indexCount, geometrySlice.baseVertex, geometrySlice.maxLocalIndex, pass.draw.sample == SamplePlan.Multisample4, 1, pass.load, pass.store, pass.draw.blend)) == null)
        } }
    })
}
