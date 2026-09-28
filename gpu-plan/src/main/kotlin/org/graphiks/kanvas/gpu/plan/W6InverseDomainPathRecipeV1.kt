package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.RectI32

/** The only W6-native zero-interior inverse-domain source forms. */
public enum class W6InverseDomainZeroSourceFormV1 { Empty, InverseDomainSource }
public enum class W6InverseDomainZeroShaderFamilyV1 { UnmaskedDomainCover }
public enum class W6InverseDomainZeroTopologyV1 { FullscreenTriangle }
public enum class W6InverseDomainZeroGroupZeroAbiV1 { InverseDomainZeroUniform }

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
