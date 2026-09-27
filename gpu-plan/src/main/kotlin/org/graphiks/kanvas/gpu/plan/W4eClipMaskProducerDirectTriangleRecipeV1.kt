package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.RectI32

public enum class W4eDirectTriangleLoadV1 { Clear }
public enum class W4eDirectTriangleStoreV1 { Store }
public enum class W4eDirectTriangleShaderFamilyV1 { PathGeometry }
public enum class W4eDirectTriangleTopologyV1 { TriangleList }
public enum class W4eDirectTriangleGroupZeroAbiV1 { NoBindGroup }
public enum class W4eDirectTriangleStencilV1 { NoopD24S8 }
public enum class W4eDirectTriangleBlendV1 { CoverageReplace }

/** Frozen W6-bound hard Path producer whose sealed math geometry is exactly one direct triangle. */
public class W4eClipMaskProducerDirectTriangleRecipeV1 internal constructor(
    public val passId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val resolveTarget: W4eClipMaskProducerPhysicalOperandV1?,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
    /** Shared W4e payload slabs consumed by this exact indexed draw. */
    public val vertex: W4eClipMaskProducerPhysicalOperandV1,
    public val index: W4eClipMaskProducerPhysicalOperandV1,
    public val depthStencilState: W4eClipMaskProducerDepthStencilStateV1,
    geometry: ClipGeometryF32.Path,
    public val vertexFirstI32: Int,
    public val vertexCountI32: Int,
    public val indexFirstI32: Int,
    public val indexCountI32: Int,
    public val baseVertexI32: Int,
    public val maxLocalIndexI32: Int,
    public val inverseCoverage: Boolean,
    public val antiAlias: Boolean,
    public val sampleCountI32: Int,
    public val load: W4eDirectTriangleLoadV1 = W4eDirectTriangleLoadV1.Clear,
    public val store: W4eDirectTriangleStoreV1 = W4eDirectTriangleStoreV1.Store,
    public val shaderFamily: W4eDirectTriangleShaderFamilyV1 = W4eDirectTriangleShaderFamilyV1.PathGeometry,
    public val topology: W4eDirectTriangleTopologyV1 = W4eDirectTriangleTopologyV1.TriangleList,
    public val groupZeroAbi: W4eDirectTriangleGroupZeroAbiV1 = W4eDirectTriangleGroupZeroAbiV1.NoBindGroup,
) {
    public val stencil: W4eDirectTriangleStencilV1 = W4eDirectTriangleStencilV1.NoopD24S8
    public val blend: W4eDirectTriangleBlendV1 = W4eDirectTriangleBlendV1.CoverageReplace
    public val clearCoverageF32: Float = if (inverseCoverage) 1f else 0f
    public val fragmentCoverageF32: Float = if (inverseCoverage) 0f else 1f
    private val path = geometry.copyPathGeometryF32()
    public fun copyGeometryF32(): ClipGeometryF32.Path = ClipGeometryF32.Path(path)
    public fun copyScissorI32(): RectI32 = path.copyConservativeScissorI32()
    init {
        require(packetOrdinalI32 >= 0 && vertexFirstI32 >= 0 && indexFirstI32 >= 0 && baseVertexI32 >= 0)
        require(vertexCountI32 == 3 && indexCountI32 == 3 && maxLocalIndexI32 in 0 until vertexCountI32 && path.copyDirectTriangleF32OrNull() != null)
        require(sampleCountI32 in setOf(1, 4) && target.sampleCountI32 == sampleCountI32)
        require((sampleCountI32 == 1) == (resolveTarget == null))
        require(target.role == if (sampleCountI32 == 1) PlanResourceRole.CoverageMaskScratch else PlanResourceRole.CoverageMaskMultisampleScratch)
        require(depthStencil.role == PlanResourceRole.CoverageMaskDepthStencil && depthStencil.sampleCountI32 == sampleCountI32)
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())
        require(vertex.role == PlanResourceRole.VertexData && PlanResourceUsage.Vertex in vertex.usages())
        require(index.role == PlanResourceRole.IndexData && PlanResourceUsage.Index in index.usages())
    }
}

public class W4eClipMaskProducerDirectTriangleNativeSiteRecipeV1 internal constructor(
    public val host: W4eClipMaskProducerDirectTriangleRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eClipMaskProducerDirectTriangle
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.passId.value); int("packet", host.packetOrdinalI32); int("bundle", 0)
        fun operand(n: String, v: W4eClipMaskProducerPhysicalOperandV1) { text("$n.id", v.id.value); enum("$n.role", v.role); text("$n.format", v.format.toString()); v.copyExtentI32()?.let { int("$n.width", it.width); int("$n.height", it.height) }; int("$n.samples", v.sampleCountI32); long("$n.bytes", v.byteSizeI64); enum("$n.lifetime", v.lifetime); v.usages().sortedBy { it.name }.forEachIndexed { i, u -> enum("$n.use.$i", u) } }
        operand("target", host.target); host.resolveTarget?.let { operand("resolve", it) } ?: text("resolve.present", "false"); operand("depth", host.depthStencil); operand("vertex", host.vertex); operand("index", host.index)
        val geometry = host.copyGeometryF32().copyPathGeometryF32(); enum("fill", geometry.fillRule); rect("scissor", host.copyScissorI32())
        geometry.copyDirectTriangleF32OrNull()!!.copyVerticesF32().forEachIndexed { i, v -> float("triangle.$i", v) }
        int("vertex.first", host.vertexFirstI32); int("vertex.count", host.vertexCountI32); int("index.first", host.indexFirstI32); int("index.count", host.indexCountI32); int("baseVertex", host.baseVertexI32); int("maxLocalIndex", host.maxLocalIndexI32); int("inverse", if (host.inverseCoverage) 1 else 0); int("antiAlias", if (host.antiAlias) 1 else 0); int("samples", host.sampleCountI32)
        enum("shader", host.shaderFamily); enum("topology", host.topology); enum("stencil", host.stencil); enum("blend", host.blend); enum("abi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); float("clear.coverage", host.clearCoverageF32); float("fragment.coverage", host.fragmentCoverageF32)
        float("depth.clear", host.depthStencilState.depthClearValueF32); enum("depth.load", host.depthStencilState.depthLoad); enum("depth.store", host.depthStencilState.depthStore); int("depth.readOnly", if (host.depthStencilState.depthReadOnly) 1 else 0)
        int("stencil.clear", host.depthStencilState.stencilClearValueU32.toInt()); enum("stencil.load", host.depthStencilState.stencilLoad); enum("stencil.store", host.depthStencilState.stencilStore); int("stencil.readOnly", if (host.depthStencilState.stencilReadOnly) 1 else 0)
    }
}

public fun freezeW4eClipMaskProducerDirectTriangleRecipesV1(bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>): Map<PlanPassId, W4eClipMaskProducerDirectTriangleRecipeV1> {
    val rows = resources.associateBy { it.id }; val result = linkedMapOf<PlanPassId, W4eClipMaskProducerDirectTriangleRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { pass ->
        val producer = pass as? PlanPass.ClipMaskProducer ?: return@forEach
        val path = producer.copyGeometryF32() as? ClipGeometryF32.Path ?: return@forEach
        if (path.copyPathGeometryF32().copyDirectTriangleF32OrNull() == null) return@forEach
        val slice = requireNotNull(binding.payload.geometrySlice(producer.id.value, W4eNativePayloadPlan.PRODUCER_PATH))
        fun operand(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())
        val target = rows.getValue(producer.target); val resolve = producer.resolveTarget?.let(rows::getValue); val depth = rows.getValue(requireNotNull(producer.depthStencil))
        val vertex = rows.getValue(binding.payload.vertexResourceId); val index = rows.getValue(binding.payload.indexResourceId)
        val depthState = W4eClipMaskProducerDepthStencilStateV1(1f, W4eClipMaskProducerDepthStencilLoadV1.Clear, W4eClipMaskProducerDepthStencilStoreV1.Store, false, 0u, W4eClipMaskProducerDepthStencilLoadV1.Clear, W4eClipMaskProducerDepthStencilStoreV1.Store, false)
        require(result.put(producer.id, W4eClipMaskProducerDirectTriangleRecipeV1(producer.id, producer.ordinal, operand(target), resolve?.let(::operand), operand(depth), operand(vertex), operand(index), depthState, path, slice.baseVertex, slice.vertexCount, slice.firstIndex, slice.indexCount, slice.baseVertex, slice.maxLocalIndex, producer.inverseCoverage, producer.antiAlias, producer.sampleCountI32)) == null)
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}
