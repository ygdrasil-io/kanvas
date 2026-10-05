package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32

/** Closed zero-coverage producer: it owns no V/I/U window and never emits a scissor. */
public class W4eClipMaskProducerConstantRecipeV1 internal constructor(
    public val passId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val resolveTarget: W4eClipMaskProducerPhysicalOperandV1?,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1?,
    public val sampleCountI32: Int,
) {
    public val coverageF32: Float = 0f
    public val fullscreenVertexCountI32: Int = 3
    public fun copyDrawDomainI32(): RectI32 = requireNotNull(target.copyExtentI32()).let { RectI32(0, 0, it.width, it.height) }
    init {
        require(packetOrdinalI32 >= 0 && sampleCountI32 in setOf(1, 4) && target.sampleCountI32 == sampleCountI32)
        require((sampleCountI32 == 1) == (resolveTarget == null))
        require(depthStencil == null || depthStencil.sampleCountI32 == sampleCountI32)
    }
}

public class W4eClipMaskProducerConstantNativeSiteRecipeV1 internal constructor(
    public val host: W4eClipMaskProducerConstantRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eClipMaskProducerConstant
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.passId.value); int("packet", host.packetOrdinalI32); int("bundle", 0)
        text("target", host.target.id.value); text("resolve", host.resolveTarget?.id?.value ?: "none")
        text("depth", host.depthStencil?.id?.value ?: "none"); int("samples", host.sampleCountI32)
        rect("domain", host.copyDrawDomainI32()); float("coverage", host.coverageF32)
        int("draw.vertexCount", host.fullscreenVertexCountI32); text("abi", "NoBindGroup")
        text("blend", "CoverageReplace"); text("load", "Clear"); text("store", "Store")
    }
}

public fun freezeW4eClipMaskProducerConstantRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>,
    resources: List<PlanResource>,
): Map<PlanPassId, W4eClipMaskProducerConstantRecipeV1> {
    val rows = resources.associateBy { it.id }
    val result = linkedMapOf<PlanPassId, W4eClipMaskProducerConstantRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { pass ->
        val producer = pass as? PlanPass.ClipMaskProducer ?: return@forEach
        if (producer.realization != PlanPass.W4eClipMaskProducerRealizationV1.ConstantZero) return@forEach
        require(!producer.inverseCoverage && producer.copyScissorI32().isEmpty) {
            "ConstantZero may represent only an ordinary empty producer scissor."
        }
        fun operand(id: PlanResourceId) = rows.getValue(id).let { row -> W4eClipMaskProducerPhysicalOperandV1(
            row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages()) }
        require(result.put(producer.id, W4eClipMaskProducerConstantRecipeV1(
            producer.id, producer.ordinal, operand(producer.target), producer.resolveTarget?.let(::operand),
            producer.depthStencil?.let(::operand), producer.sampleCountI32,
        )) == null)
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}
