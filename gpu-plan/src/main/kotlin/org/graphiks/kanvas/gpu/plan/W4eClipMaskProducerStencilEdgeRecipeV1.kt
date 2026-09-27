package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.RectI32

public enum class W4eStencilEdgeShaderFamilyV1 { PathGeometry }
public enum class W4eStencilEdgeTopologyV1 { TriangleList }
public enum class W4eStencilEdgeGroupZeroAbiV1 { NoBindGroup }
public enum class W4eStencilEdgeColorWriteV1 { Disabled }
public enum class W4eStencilEdgeStencilModeV1 { WindingIncrementDecrement, EvenOddInvert }
public enum class W4eStencilEdgeLoadV1 { Clear }
public enum class W4eStencilEdgeStoreV1 { Store }

/**
 * Bundle zero of a W6-bound Path stencil producer.  The following cover draw is deliberately
 * not represented here: IIb2 owns that second native bundle.
 */
public class W4eClipMaskProducerStencilEdgeRecipeV1 internal constructor(
    public val passId: PlanPassId, public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val resolveTarget: W4eClipMaskProducerPhysicalOperandV1?,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
    public val vertex: W4eClipMaskProducerPhysicalOperandV1,
    public val index: W4eClipMaskProducerPhysicalOperandV1,
    public val depthStencilState: W4eClipMaskProducerDepthStencilStateV1,
    geometry: ClipGeometryF32.Path,
    public val vertexFirstI32: Int, public val vertexCountI32: Int,
    public val indexFirstI32: Int, public val indexCountI32: Int,
    public val baseVertexI32: Int, public val maxLocalIndexI32: Int,
    public val inverseCoverage: Boolean, public val antiAlias: Boolean, public val sampleCountI32: Int,
    public val shaderFamily: W4eStencilEdgeShaderFamilyV1 = W4eStencilEdgeShaderFamilyV1.PathGeometry,
    public val topology: W4eStencilEdgeTopologyV1 = W4eStencilEdgeTopologyV1.TriangleList,
    public val groupZeroAbi: W4eStencilEdgeGroupZeroAbiV1 = W4eStencilEdgeGroupZeroAbiV1.NoBindGroup,
    public val colorWrite: W4eStencilEdgeColorWriteV1 = W4eStencilEdgeColorWriteV1.Disabled,
    public val stencilMode: W4eStencilEdgeStencilModeV1 = if (geometry.copyPathGeometryF32().fillRule in setOf(FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD)) W4eStencilEdgeStencilModeV1.EvenOddInvert else W4eStencilEdgeStencilModeV1.WindingIncrementDecrement,
    public val load: W4eStencilEdgeLoadV1 = W4eStencilEdgeLoadV1.Clear,
    public val store: W4eStencilEdgeStoreV1 = W4eStencilEdgeStoreV1.Store,
) {
    private val path = geometry.copyPathGeometryF32()
    private val fan = requireNotNull(path.copyStencilEdgeFanF32OrNull())
    public fun copyGeometryF32(): ClipGeometryF32.Path = ClipGeometryF32.Path(path)
    public fun copyScissorI32(): RectI32 = path.copyConservativeScissorI32()
    public fun copyVerticesF32(): FloatArray = fan.copyVerticesF32()
    public fun copyIndicesI32(): IntArray = fan.copyIndicesI32()
    public fun copyContourStartsI32(): IntArray = fan.copyContourStartsI32()
    public val fillRule: FillRule get() = path.fillRule
    init {
        require(packetOrdinalI32 >= 0 && vertexFirstI32 >= 0 && indexFirstI32 >= 0 && baseVertexI32 >= 0)
        require(vertexCountI32 == fan.copyVerticesF32().size / 2 && indexCountI32 == fan.copyIndicesI32().size && maxLocalIndexI32 in 0 until vertexCountI32)
        require(sampleCountI32 in setOf(1, 4) && target.sampleCountI32 == sampleCountI32 && depthStencil.sampleCountI32 == sampleCountI32)
        require((sampleCountI32 == 1) == (resolveTarget == null))
        require(target.role == if (sampleCountI32 == 1) PlanResourceRole.CoverageMaskScratch else PlanResourceRole.CoverageMaskMultisampleScratch)
        require(depthStencil.role == PlanResourceRole.CoverageMaskDepthStencil)
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())
        require(vertex.role == PlanResourceRole.VertexData && PlanResourceUsage.Vertex in vertex.usages())
        require(index.role == PlanResourceRole.IndexData && PlanResourceUsage.Index in index.usages())
    }
}

public class W4eClipMaskProducerStencilEdgeNativeSiteRecipeV1 internal constructor(public val host: W4eClipMaskProducerStencilEdgeRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1
    override val owner = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family = NativeSiteRecipeFamilyV1.W4eClipMaskProducerStencilEdge
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.passId.value); int("packet", host.packetOrdinalI32); int("bundle", 0)
        fun operand(n: String, v: W4eClipMaskProducerPhysicalOperandV1) { text("$n.id", v.id.value); enum("$n.role", v.role); text("$n.format", v.format.toString()); v.copyExtentI32()?.let { int("$n.width", it.width); int("$n.height", it.height) }; int("$n.samples", v.sampleCountI32); long("$n.bytes", v.byteSizeI64); enum("$n.lifetime", v.lifetime); v.usages().sortedBy { it.name }.forEachIndexed { i, u -> enum("$n.use.$i", u) } }
        operand("target", host.target); host.resolveTarget?.let { operand("resolve", it) } ?: text("resolve.present", "false"); operand("depth", host.depthStencil); operand("vertex", host.vertex); operand("index", host.index)
        enum("fill", host.fillRule); rect("scissor", host.copyScissorI32()); host.copyVerticesF32().forEachIndexed { i, v -> float("geometry.vertex.$i", v) }; host.copyIndicesI32().forEachIndexed { i, v -> int("geometry.index.$i", v) }; host.copyContourStartsI32().forEachIndexed { i, v -> int("geometry.contourStart.$i", v) }
        int("vertex.first", host.vertexFirstI32); int("vertex.count", host.vertexCountI32); int("index.first", host.indexFirstI32); int("index.count", host.indexCountI32); int("baseVertex", host.baseVertexI32); int("maxLocalIndex", host.maxLocalIndexI32); int("inverse", if (host.inverseCoverage) 1 else 0); int("antiAlias", if (host.antiAlias) 1 else 0); int("samples", host.sampleCountI32)
        enum("shader", host.shaderFamily); enum("topology", host.topology); enum("abi", host.groupZeroAbi); enum("colorWrite", host.colorWrite); enum("stencil", host.stencilMode); enum("load", host.load); enum("store", host.store)
        float("depth.clear", host.depthStencilState.depthClearValueF32); enum("depth.load", host.depthStencilState.depthLoad); enum("depth.store", host.depthStencilState.depthStore); int("stencil.clear", host.depthStencilState.stencilClearValueU32.toInt()); enum("stencil.load", host.depthStencilState.stencilLoad); enum("stencil.store", host.depthStencilState.stencilStore)
    }
}

public fun freezeW4eClipMaskProducerStencilEdgeRecipesV1(bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>): Map<PlanPassId, W4eClipMaskProducerStencilEdgeRecipeV1> {
    val rows = resources.associateBy { it.id }; val result = linkedMapOf<PlanPassId, W4eClipMaskProducerStencilEdgeRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { pass ->
        val producer = pass as? PlanPass.ClipMaskProducer ?: return@forEach
        val path = producer.copyGeometryF32() as? ClipGeometryF32.Path ?: return@forEach
        if (path.copyPathGeometryF32().copyStencilEdgeFanF32OrNull() == null) return@forEach
        val slice = requireNotNull(binding.payload.geometrySlice(producer.id.value, W4eNativePayloadPlan.PRODUCER_PATH))
        fun operand(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())
        val target = rows.getValue(producer.target); val resolve = producer.resolveTarget?.let(rows::getValue); val depth = rows.getValue(requireNotNull(producer.depthStencil))
        val vertex = rows.getValue(binding.payload.vertexResourceId); val index = rows.getValue(binding.payload.indexResourceId)
        val state = W4eClipMaskProducerDepthStencilStateV1(1f, W4eClipMaskProducerDepthStencilLoadV1.Clear, W4eClipMaskProducerDepthStencilStoreV1.Store, false, 0u, W4eClipMaskProducerDepthStencilLoadV1.Clear, W4eClipMaskProducerDepthStencilStoreV1.Store, false)
        require(result.put(producer.id, W4eClipMaskProducerStencilEdgeRecipeV1(producer.id, producer.ordinal, operand(target), resolve?.let(::operand), operand(depth), operand(vertex), operand(index), state, path, slice.baseVertex, slice.vertexCount, slice.firstIndex, slice.indexCount, slice.baseVertex, slice.maxLocalIndex, producer.inverseCoverage, producer.antiAlias, producer.sampleCountI32)) == null)
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}
