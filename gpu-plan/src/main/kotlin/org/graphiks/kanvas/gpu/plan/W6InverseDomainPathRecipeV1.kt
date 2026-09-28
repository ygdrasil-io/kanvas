package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.RectI32

/** The only W6-native zero-interior inverse-domain source forms. */
public enum class W6InverseDomainZeroSourceFormV1 { Empty, InverseDomainSource }
public enum class W6InverseDomainZeroShaderFamilyV1 { UnmaskedDomainCover }
public enum class W6InverseDomainZeroTopologyV1 { FullscreenTriangle }
public enum class W6InverseDomainZeroGroupZeroAbiV1 { InverseDomainZeroUniform }
public enum class W6InverseDomainDirectBundleV1 { DomainStencil, InteriorZero, ColorCover }
public enum class W6InverseDomainDirectShaderV1 { PathGeometry, InverseDomainCover }
public enum class W6InverseDomainDirectTopologyV1 { TriangleList, FullscreenTriangle }
public enum class W6InverseDomainDirectGroupZeroAbiV1 { NoBindings, InverseDomainUniform }
public enum class W6InverseDomainDirectStencilV1 { ClearReplaceOne, TestZeroKeep }

/**
 * One final W6 inverse-domain Zero packet.  This deliberately owns no mask, V/I, or D24S8:
 * geometry interiors are a later, three-bundle task.
 */
public class W6InverseDomainZeroCoverRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val uniform: W4eClipMaskProducerPhysicalOperandV1,
    public val uniformSlice: W4eNativeUniformSlice,
    uniformBytes: ByteArray,
    public val sourceForm: W6InverseDomainZeroSourceFormV1,
    domainI32: RectI32,
    sourceScissorI32: RectI32,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val blend: BlendPlan,
    public val shaderFamily: W6InverseDomainZeroShaderFamilyV1 = W6InverseDomainZeroShaderFamilyV1.UnmaskedDomainCover,
    public val topology: W6InverseDomainZeroTopologyV1 = W6InverseDomainZeroTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6InverseDomainZeroGroupZeroAbiV1 = W6InverseDomainZeroGroupZeroAbiV1.InverseDomainZeroUniform,
) {
    public val owner: NativeSiteOwnerV1 get() = NativeSiteOwnerV1(ownerPassId, packetOrdinalI32, 0)
    private val domain = domainI32.copy()
    private val sourceScissor = sourceScissorI32.copy()
    private val bytes = uniformBytes.copyOf()
    public val fullscreenVertexCountI32: Int = 3
    public fun copyDomainI32(): RectI32 = domain.copy()
    public fun copySourceScissorI32(): RectI32 = sourceScissor.copy()
    public fun copyUniformBytes(): ByteArray = bytes.copyOf()

    init {
        require(packetOrdinalI32 >= 0 && !domain.isEmpty && !sourceScissor.isEmpty)
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        require(uniform.role == PlanResourceRole.UniformData && PlanResourceUsage.Uniform in uniform.usages())
        require(uniformSlice.purpose == W4eNativePayloadPlan.INVERSE_DOMAIN_ZERO_UNIFORM && uniformSlice.byteSize == 16L)
        require(bytes.size.toLong() == uniformSlice.byteSize)
    }
}

public class W6InverseDomainZeroNativeSiteRecipeV1 internal constructor(
    public val host: W6InverseDomainZeroCoverRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.owner
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6InverseDomainZeroCover
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(name: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$name.id", value.id.value); enum("$name.role", value.role); text("$name.format", value.format.toString())
            value.copyExtentI32()?.let { int("$name.width", it.width); int("$name.height", it.height) }
            int("$name.samples", value.sampleCountI32); long("$name.bytes", value.byteSizeI64); enum("$name.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { index, usage -> enum("$name.usage.$index", usage) }
        }
        text("owner", host.ownerPassId.value); int("packet", host.packetOrdinalI32); int("bundle", 0)
        text("predicate", "Zero"); enum("source", host.sourceForm)
        operand("target", host.target); operand("uniform", host.uniform)
        rect("domain", host.copyDomainI32()); rect("source.scissor", host.copySourceScissorI32())
        long("uniform.offset", host.uniformSlice.offsetBytes); long("uniform.bytes", host.uniformSlice.byteSize)
        host.copyUniformBytes().forEachIndexed { index, value -> int("uniform.byte.$index", value.toInt() and 0xff) }
        enum("load", host.load); enum("store", host.store); blend("blend", host.blend)
        enum("shader", host.shaderFamily); enum("topology", host.topology); enum("abi", host.groupZeroAbi)
        int("vertices", host.fullscreenVertexCountI32); text("depth.present", "false"); text("vi.present", "false")
    }
}

/**
 * The one final W6 Geometry DirectTriangle packet.  All three native programs deliberately
 * retain the same PathRenderPass identity; bundle ordinal, not an invented pass, distinguishes
 * their allocation sites.  The payload snapshots make same-size geometry non-interchangeable.
 */
public class W6InverseDomainDirectRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
    public val vertex: W4eClipMaskProducerPhysicalOperandV1,
    public val index: W4eClipMaskProducerPhysicalOperandV1,
    public val uniform: W4eClipMaskProducerPhysicalOperandV1,
    public val quadSlice: W4eNativeGeometrySlice,
    public val interiorSlice: W4eNativeGeometrySlice,
    public val uniformSlice: W4eNativeUniformSlice,
    quadVerticesF32: FloatArray,
    quadIndicesI32: IntArray,
    interiorVerticesF32: FloatArray,
    interiorIndicesI32: IntArray,
    uniformBytes: ByteArray,
    public val fillRule: FillRule,
    domainI32: RectI32,
    sourceScissorI32: RectI32,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val blend: BlendPlan,
) {
    private val domain = domainI32.copy(); private val sourceScissor = sourceScissorI32.copy()
    private val quadVertices = quadVerticesF32.copyOf(); private val quadIndices = quadIndicesI32.copyOf()
    private val interiorVertices = interiorVerticesF32.copyOf(); private val interiorIndices = interiorIndicesI32.copyOf()
    private val bytes = uniformBytes.copyOf()
    public fun owner(bundle: Int): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, packetOrdinalI32, bundle)
    public fun copyDomainI32(): RectI32 = domain.copy(); public fun copySourceScissorI32(): RectI32 = sourceScissor.copy()
    public fun copyQuadVerticesF32(): FloatArray = quadVertices.copyOf(); public fun copyQuadIndicesI32(): IntArray = quadIndices.copyOf()
    public fun copyInteriorVerticesF32(): FloatArray = interiorVertices.copyOf(); public fun copyInteriorIndicesI32(): IntArray = interiorIndices.copyOf()
    public fun copyUniformBytes(): ByteArray = bytes.copyOf()
    public val fullscreenVertexCountI32: Int = 3
    public fun shader(bundle: W6InverseDomainDirectBundleV1): W6InverseDomainDirectShaderV1 = if (bundle == W6InverseDomainDirectBundleV1.ColorCover) W6InverseDomainDirectShaderV1.InverseDomainCover else W6InverseDomainDirectShaderV1.PathGeometry
    public fun topology(bundle: W6InverseDomainDirectBundleV1): W6InverseDomainDirectTopologyV1 = if (bundle == W6InverseDomainDirectBundleV1.ColorCover) W6InverseDomainDirectTopologyV1.FullscreenTriangle else W6InverseDomainDirectTopologyV1.TriangleList
    public fun groupZeroAbi(bundle: W6InverseDomainDirectBundleV1): W6InverseDomainDirectGroupZeroAbiV1 = if (bundle == W6InverseDomainDirectBundleV1.ColorCover) W6InverseDomainDirectGroupZeroAbiV1.InverseDomainUniform else W6InverseDomainDirectGroupZeroAbiV1.NoBindings
    public fun stencil(bundle: W6InverseDomainDirectBundleV1): W6InverseDomainDirectStencilV1 = when (bundle) {
        W6InverseDomainDirectBundleV1.DomainStencil -> W6InverseDomainDirectStencilV1.ClearReplaceOne
        W6InverseDomainDirectBundleV1.InteriorZero -> W6InverseDomainDirectStencilV1.ClearReplaceOne
        W6InverseDomainDirectBundleV1.ColorCover -> W6InverseDomainDirectStencilV1.TestZeroKeep
    }
    init {
        require(packetOrdinalI32 >= 0 && !domain.isEmpty && !sourceScissor.isEmpty)
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        require(depthStencil.role == PlanResourceRole.DepthStencil && depthStencil.format ==
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
            PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())
        require(vertex.role == PlanResourceRole.VertexData && PlanResourceUsage.Vertex in vertex.usages())
        require(index.role == PlanResourceRole.IndexData && PlanResourceUsage.Index in index.usages())
        require(uniform.role == PlanResourceRole.UniformData && PlanResourceUsage.Uniform in uniform.usages())
        require(quadSlice.purpose == W4eNativePayloadPlan.INVERSE_DOMAIN_QUAD &&
            interiorSlice.purpose == W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR &&
            uniformSlice.purpose == W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM && uniformSlice.byteSize == 16L)
        require(quadVertices.size == quadSlice.vertexCount * 2 && quadIndices.size == quadSlice.indexCount &&
            interiorVertices.size == interiorSlice.vertexCount * 2 && interiorIndices.size == interiorSlice.indexCount &&
            bytes.size.toLong() == uniformSlice.byteSize)
    }
}

public class W6InverseDomainDirectNativeSiteRecipeV1 internal constructor(
    public val host: W6InverseDomainDirectRecipeV1,
    public val bundle: W6InverseDomainDirectBundleV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.owner(bundle.ordinal)
    override val family: NativeSiteRecipeFamilyV1 = when (bundle) {
        W6InverseDomainDirectBundleV1.DomainStencil -> NativeSiteRecipeFamilyV1.W6InverseDomainGeometryDomainStencil
        W6InverseDomainDirectBundleV1.InteriorZero -> NativeSiteRecipeFamilyV1.W6InverseDomainGeometryInteriorZero
        W6InverseDomainDirectBundleV1.ColorCover -> NativeSiteRecipeFamilyV1.W6InverseDomainGeometryColorCover
    }
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(name: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$name.id", value.id.value); enum("$name.role", value.role); text("$name.format", value.format.toString())
            value.copyExtentI32()?.let { int("$name.width", it.width); int("$name.height", it.height) }
            int("$name.samples", value.sampleCountI32); long("$name.bytes", value.byteSizeI64); enum("$name.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { i, use -> enum("$name.use.$i", use) }
        }
        text("owner", host.ownerPassId.value); int("packet", host.packetOrdinalI32); int("bundle", bundle.ordinal)
        text("predicate", "Geometry"); enum("bundle.role", bundle); enum("fill", host.fillRule)
        enum("shader", host.shader(bundle)); enum("topology", host.topology(bundle)); enum("abi", host.groupZeroAbi(bundle)); enum("stencil", host.stencil(bundle))
        operand("target", host.target); operand("depth", host.depthStencil); operand("vertex", host.vertex); operand("index", host.index); operand("uniform", host.uniform)
        rect("domain", host.copyDomainI32()); rect("source.scissor", host.copySourceScissorI32())
        long("uniform.offset", host.uniformSlice.offsetBytes); long("uniform.bytes", host.uniformSlice.byteSize)
        host.copyUniformBytes().forEachIndexed { i, value -> int("uniform.byte.$i", value.toInt() and 0xff) }
        host.copyQuadVerticesF32().forEachIndexed { i, value -> float("quad.vertex.$i", value) }
        host.copyQuadIndicesI32().forEachIndexed { i, value -> int("quad.index.$i", value) }
        host.copyInteriorVerticesF32().forEachIndexed { i, value -> float("interior.vertex.$i", value) }
        host.copyInteriorIndicesI32().forEachIndexed { i, value -> int("interior.index.$i", value) }
        int("quad.first", host.quadSlice.firstIndex); int("quad.count", host.quadSlice.indexCount); int("quad.base", host.quadSlice.baseVertex)
        int("interior.first", host.interiorSlice.firstIndex); int("interior.count", host.interiorSlice.indexCount); int("interior.base", host.interiorSlice.baseVertex)
        enum("load", host.load); enum("store", host.store); blend("blend", host.blend)
        if (bundle == W6InverseDomainDirectBundleV1.ColorCover) int("draw.vertices", host.fullscreenVertexCountI32)
        text("scene.depth", "D24S8"); text("depth.clear", "1"); text("stencil.clear", "0"); text("depth.load", "Clear"); text("depth.store", "Store"); text("stencil.load", "Clear"); text("stencil.store", "Store"); text("domain.draw.omitted.commonSource", "true")
    }
}

private fun PathRenderDraw.w6InverseDomainOrNull(): ClipPlanStrategy.InverseDomain? = when (this) {
    is ClippedGeneralPathDraw -> clip as? ClipPlanStrategy.InverseDomain
    is ClippedBinaryMaskedPathDraw -> clip as? ClipPlanStrategy.InverseDomain
    is GeneralPathDraw, is BinaryMaskedPathDraw -> null
}

private fun W4eNativePayloadPlan.copyUniformSliceBytes(slice: W4eNativeUniformSlice): ByteArray =
    copyUniformData().copyOfRange(Math.toIntExact(slice.offsetBytes), Math.toIntExact(slice.offsetBytes + slice.byteSize))

/** Freezes exactly the final W6 Zero inverse-domain packets, never their Geometry siblings. */
public fun freezeW6InverseDomainZeroCoverRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>,
): Map<PlanPassId, W6InverseDomainZeroCoverRecipeV1> {
    val rows = resources.associateBy { it.id }
    fun operand(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(
        row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages(),
    )
    val result = linkedMapOf<PlanPassId, W6InverseDomainZeroCoverRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { candidate ->
        val pass = candidate as? PlanPass.PathRenderPass ?: return@forEach
        val inverse = pass.draw.w6InverseDomainOrNull() ?: return@forEach
        if (inverse.geometryF32.interiorCoverageF32 != InverseInteriorCoverageF32.Zero) return@forEach
        require(pass.phase == PathRenderPhase.SingleSampleDirectColor && pass.depthStencil == null &&
            pass.resolveTarget == null && pass.draw.sample == SamplePlan.SingleSample) {
            "W6 InverseDomain.Zero must be one direct-colour U16 cover without D24S8 or resolve."
        }
        val sourceForm = when (pass.draw.copyPathGeometry()) {
            PathDrawGeometry.Empty -> W6InverseDomainZeroSourceFormV1.Empty
            is PathDrawGeometry.InverseDomainSource -> W6InverseDomainZeroSourceFormV1.InverseDomainSource
            is PathDrawGeometry.Fill, is PathDrawGeometry.Stroke -> error(
                "W6 InverseDomain.Zero refuses Fill/Stroke and its V/I source payload."
            )
        }
        require(binding.payload.geometrySlice(pass.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_ZERO_SOURCE) == null) {
            "W6 InverseDomain.Zero must not retain a source V/I slice."
        }
        val slice = requireNotNull(binding.payload.uniformSlice(pass.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_ZERO_UNIFORM))
        val recipe = W6InverseDomainZeroCoverRecipeV1(pass.id, pass.ordinal, operand(rows.getValue(pass.target)),
            operand(rows.getValue(binding.payload.uniformResourceId)), slice, binding.payload.copyUniformSliceBytes(slice),
            sourceForm, inverse.geometryF32.copyDomainI32(), pass.draw.copyScissorI32(), pass.load, pass.store, pass.draw.blend)
        require(result.put(pass.id, recipe) == null) { "One W6 InverseDomain.Zero owner may publish only one bundle." }
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}

/** Freezes the DirectTriangle Geometry triplet, leaving fan topology for Task 4. */
public fun freezeW6InverseDomainDirectRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>,
): Map<PlanPassId, W6InverseDomainDirectRecipeV1> {
    val rows = resources.associateBy { it.id }
    fun operand(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())
    fun vertices(payload: W4eNativePayloadPlan, slice: W4eNativeGeometrySlice): FloatArray = payload.copyVertexData().copyOfRange(slice.baseVertex * 2, (slice.baseVertex + slice.vertexCount) * 2)
    fun indices(payload: W4eNativePayloadPlan, slice: W4eNativeGeometrySlice): IntArray = payload.copyIndexData().copyOfRange(slice.firstIndex, slice.firstIndex + slice.indexCount)
    val result = linkedMapOf<PlanPassId, W6InverseDomainDirectRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { candidate ->
        val pass = candidate as? PlanPass.PathRenderPass ?: return@forEach
        val inverse = pass.draw.w6InverseDomainOrNull() ?: return@forEach
        val geometry = inverse.geometryF32.interiorCoverageF32 as? InverseInteriorCoverageF32.Geometry ?: return@forEach
        val direct = geometry.copyGeometryF32().copyDirectTriangleF32OrNull() ?: return@forEach
        require(pass.phase == PathRenderPhase.SingleSampleDirectColor && pass.draw.sample == SamplePlan.SingleSample &&
            pass.resolveTarget == null && pass.depthStencil != null && pass.depthStencilLoadStore == null) {
            "W6 InverseDomain.Geometry Direct requires one scene-local D24S8 direct-colour pass."
        }
        val quad = requireNotNull(binding.payload.geometrySlice(pass.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_QUAD))
        val interior = requireNotNull(binding.payload.geometrySlice(pass.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR))
        val uniform = requireNotNull(binding.payload.uniformSlice(pass.id.value, W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM))
        val extent = requireNotNull(rows.getValue(pass.target).copyExtent())
        val directNdc = direct.copyVerticesF32().also { values -> values.indices.step(2).forEach { offset ->
            values[offset] = values[offset] * 2f / extent.width - 1f
            values[offset + 1] = 1f - values[offset + 1] * 2f / extent.height
        } }
        require(vertices(binding.payload, interior).contentEquals(directNdc) && indices(binding.payload, interior).contentEquals(direct.copyIndicesI32())) {
            "W6 InverseDomain.Geometry Direct payload no longer matches :math geometry."
        }
        val recipe = W6InverseDomainDirectRecipeV1(pass.id, pass.ordinal, operand(rows.getValue(pass.target)),
            operand(rows.getValue(requireNotNull(pass.depthStencil))), operand(rows.getValue(binding.payload.vertexResourceId)),
            operand(rows.getValue(binding.payload.indexResourceId)), operand(rows.getValue(binding.payload.uniformResourceId)), quad, interior, uniform,
            vertices(binding.payload, quad), indices(binding.payload, quad), vertices(binding.payload, interior), indices(binding.payload, interior),
            binding.payload.copyUniformSliceBytes(uniform), geometry.copyGeometryF32().fillRule, inverse.geometryF32.copyDomainI32(), pass.draw.copyScissorI32(), pass.load, pass.store, pass.draw.blend)
        require(result.put(pass.id, recipe) == null) { "One W6 InverseDomain.Geometry Direct owner may publish only one triplet." }
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}
