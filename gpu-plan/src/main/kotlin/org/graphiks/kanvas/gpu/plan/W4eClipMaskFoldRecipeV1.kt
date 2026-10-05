package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32

/** Frozen W6-bound W4e fold: the two sampled masks are combined into a fresh accumulator. */
public enum class W4eClipMaskFoldShaderFamilyV1 { ClipCombine }
public enum class W4eClipMaskFoldGroupZeroAbiV1 { PreviousThenSourceTexture }
public enum class W4eClipMaskFoldLoadV1 { Clear }
public enum class W4eClipMaskFoldStoreV1 { Store }

public class W4eClipMaskFoldRecipeV1 internal constructor(
    public val passId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val previous: W4eClipMaskProducerPhysicalOperandV1,
    public val source: W4eClipMaskProducerPhysicalOperandV1,
    public val output: W4eClipMaskProducerPhysicalOperandV1,
    public val operation: ClipCombineOperation,
    domainI32: RectI32,
    public val shaderFamily: W4eClipMaskFoldShaderFamilyV1,
    public val groupZeroAbi: W4eClipMaskFoldGroupZeroAbiV1,
    public val load: W4eClipMaskFoldLoadV1,
    public val store: W4eClipMaskFoldStoreV1,
    public val clearColorF32: Float = 0f,
    public val fullscreenVertexCountI32: Int = 3,
) {
    private val domain = domainI32.copy()
    public fun copyDomainI32(): RectI32 = domain.copy()
    init {
        require(packetOrdinalI32 >= 0 && !domain.isEmpty && clearColorF32.isFinite() && clearColorF32 == 0f)
        require(previous.role == PlanResourceRole.CoverageMaskAccumulator && source.role in setOf(PlanResourceRole.CoverageMaskScratch, PlanResourceRole.CoverageMaskMultisampleScratch))
        require(output.role == PlanResourceRole.CoverageMaskAccumulator && previous.id != source.id && output.id != previous.id && output.id != source.id)
        require(previous.sampleCountI32 == 1 && source.sampleCountI32 == 1 && output.sampleCountI32 == 1)
        require(PlanResourceUsage.Sampled in previous.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.RenderAttachment in output.usages())
        require(shaderFamily == W4eClipMaskFoldShaderFamilyV1.ClipCombine && groupZeroAbi == W4eClipMaskFoldGroupZeroAbiV1.PreviousThenSourceTexture)
        require(load == W4eClipMaskFoldLoadV1.Clear && store == W4eClipMaskFoldStoreV1.Store && fullscreenVertexCountI32 == 3)
    }
}

public class W4eClipMaskFoldNativeSiteRecipeV1 internal constructor(public val host: W4eClipMaskFoldRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eClipMaskFold
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(name: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$name.id", value.id.value); enum("$name.role", value.role); text("$name.format", value.format.toString())
            value.copyExtentI32()?.let { int("$name.width", it.width); int("$name.height", it.height) }
            int("$name.samples", value.sampleCountI32); long("$name.bytes", value.byteSizeI64); enum("$name.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { index, usage -> enum("$name.usage.$index", usage) }
        }
        text("owner", host.passId.value); int("packetOrdinal", host.packetOrdinalI32); operand("previous", host.previous); operand("source", host.source); operand("output", host.output)
        enum("operation", host.operation); rect("domain", host.copyDomainI32()); enum("shader", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi)
        enum("load", host.load); enum("store", host.store); float("clearColor", host.clearColorF32); int("draw.fullscreenVertexCount", host.fullscreenVertexCountI32)
    }
}

/** Freezes every final W6-bound fold, but never manufactures a direct-W4d site. */
public fun freezeW4eClipMaskFoldRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>,
): Map<PlanPassId, W4eClipMaskFoldRecipeV1> {
    val rows = resources.associateBy { it.id }
    val recipes = linkedMapOf<PlanPassId, W4eClipMaskFoldRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { pass ->
        val fold = pass as? PlanPass.ClipMaskFold ?: return@forEach
        require(binding.nativePass(fold.id) === fold)
        fun operand(id: PlanResourceId): W4eClipMaskProducerPhysicalOperandV1 {
            val row = rows.getValue(id)
            return W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())
        }
        val recipe = W4eClipMaskFoldRecipeV1(fold.id, fold.ordinal, operand(fold.previous), operand(fold.source), operand(fold.output), fold.operation,
            fold.copyDomainI32(), W4eClipMaskFoldShaderFamilyV1.ClipCombine, W4eClipMaskFoldGroupZeroAbiV1.PreviousThenSourceTexture,
            W4eClipMaskFoldLoadV1.Clear, W4eClipMaskFoldStoreV1.Store)
        require(recipes.put(fold.id, recipe) == null)
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}
