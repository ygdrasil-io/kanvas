package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.SizeI32

/** The two analytic producer forms admitted by I1; Path and Empty never reach this recipe. */
public enum class W4eClipMaskProducerGeometryV1 { Rect, RRect }
public enum class W4eClipMaskProducerShaderFamilyV1 { AnalyticCoverage }
public enum class W4eClipMaskProducerGroupZeroAbiV1 { ProducerUniform }
public enum class W4eClipMaskProducerLoadV1 { Clear }
public enum class W4eClipMaskProducerStoreV1 { Store }

/** Immutable physical operand selected before native allocation. */
public class W4eClipMaskProducerPhysicalOperandV1 internal constructor(
    public val id: PlanResourceId,
    public val role: PlanResourceRole,
    public val format: PlanTextureFormat?,
    extent: SizeI32?,
    public val sampleCountI32: Int,
    public val byteSizeI64: Long,
    public val lifetime: PlanResourceLifetime,
    usages: Set<PlanResourceUsage>,
) {
    private val extentSnapshot = extent?.copy()
    private val usagesSnapshot = java.util.Collections.unmodifiableSet(LinkedHashSet(usages))
    public fun copyExtentI32(): SizeI32? = extentSnapshot?.copy()
    public fun usages(): Set<PlanResourceUsage> = usagesSnapshot
}

/** Final native site for one analytic W4e clip producer bound into a W6 frame. */
public class W4eClipMaskProducerRecipeV1 internal constructor(
    public val passId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val resolveTarget: W4eClipMaskProducerPhysicalOperandV1?,
    public val depthStencil: W4eClipMaskProducerPhysicalOperandV1?,
    public val uniform: W4eClipMaskProducerPhysicalOperandV1,
    public val uniformPurpose: String,
    public val uniformOffsetBytesI64: Long,
    public val uniformByteSizeI64: Long,
    public val geometry: W4eClipMaskProducerGeometryV1,
    geometryF32: ClipGeometryF32,
    public val inverseCoverage: Boolean,
    public val antiAlias: Boolean,
    public val sampleCountI32: Int,
    public val shaderFamily: W4eClipMaskProducerShaderFamilyV1,
    public val groupZeroAbi: W4eClipMaskProducerGroupZeroAbiV1,
    public val load: W4eClipMaskProducerLoadV1,
    public val store: W4eClipMaskProducerStoreV1,
    public val fullscreenVertexCountI32: Int = 3,
) {
    private val geometrySnapshot = when (geometryF32) {
        is ClipGeometryF32.Rect -> ClipGeometryF32.Rect(geometryF32.copyRectF32())
        is ClipGeometryF32.RRect -> ClipGeometryF32.RRect(geometryF32.copyRRectF32())
        is ClipGeometryF32.Path, ClipGeometryF32.Empty -> error("I1 freezes only analytic clip geometry.")
    }
    /** Immutable math-owned input; later catalog encoding writes its exact F32 components. */
    public fun copyGeometryF32(): ClipGeometryF32 = when (val value = geometrySnapshot) {
        is ClipGeometryF32.Rect -> ClipGeometryF32.Rect(value.copyRectF32())
        is ClipGeometryF32.RRect -> ClipGeometryF32.RRect(value.copyRRectF32())
        is ClipGeometryF32.Path, ClipGeometryF32.Empty -> error("unreachable")
    }
    init {
        require(packetOrdinalI32 >= 0 && sampleCountI32 in setOf(1, 4))
        require(uniformPurpose == W4eNativePayloadPlan.PRODUCER_UNIFORM && uniformOffsetBytesI64 >= 0L && uniformByteSizeI64 == 64L)
        require(uniform.role == PlanResourceRole.UniformData && PlanResourceUsage.Uniform in uniform.usages())
        require(target.role in setOf(PlanResourceRole.CoverageMaskScratch, PlanResourceRole.CoverageMaskMultisampleScratch))
        require(PlanResourceUsage.RenderAttachment in target.usages() && fullscreenVertexCountI32 == 3)
        require((sampleCountI32 == 1) == (resolveTarget == null))
        require((sampleCountI32 == 1) == (depthStencil == null))
        require(shaderFamily == W4eClipMaskProducerShaderFamilyV1.AnalyticCoverage && groupZeroAbi == W4eClipMaskProducerGroupZeroAbiV1.ProducerUniform && load == W4eClipMaskProducerLoadV1.Clear && store == W4eClipMaskProducerStoreV1.Store)
    }
}

/** Versioned catalog wrapper for I1's final W6-bound analytic producer site. */
public class W4eClipMaskProducerNativeSiteRecipeV1 internal constructor(
    public val host: W4eClipMaskProducerRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(host.passId, host.packetOrdinalI32, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eClipMaskProducer
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(name: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$name.id", value.id.value); enum("$name.role", value.role); text("$name.format", value.format.toString())
            value.copyExtentI32()?.let { extent -> int("$name.width", extent.width); int("$name.height", extent.height) }
            int("$name.samples", value.sampleCountI32); long("$name.bytes", value.byteSizeI64); enum("$name.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { index, usage -> enum("$name.usage.$index", usage) }
        }
        text("owner", host.passId.value)
        int("packetOrdinal", host.packetOrdinalI32)
        operand("target", host.target)
        host.resolveTarget?.let { operand("resolve", it) } ?: text("resolve.present", "false")
        host.depthStencil?.let { operand("depth", it) } ?: text("depth.present", "false")
        operand("uniform", host.uniform); text("uniform.purpose", host.uniformPurpose); long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.size", host.uniformByteSizeI64)
        enum("geometry", host.geometry); when (val geometry = host.copyGeometryF32()) {
            is ClipGeometryF32.Rect -> rectF32("geometry.rect", geometry.copyRectF32())
            is ClipGeometryF32.RRect -> geometry.copyRRectF32().let { rrect ->
                rectF32("geometry.rrect.rect", rrect.rect)
                float("geometry.rrect.topLeft.x", rrect.topLeft.x); float("geometry.rrect.topLeft.y", rrect.topLeft.y)
                float("geometry.rrect.topRight.x", rrect.topRight.x); float("geometry.rrect.topRight.y", rrect.topRight.y)
                float("geometry.rrect.bottomRight.x", rrect.bottomRight.x); float("geometry.rrect.bottomRight.y", rrect.bottomRight.y)
                float("geometry.rrect.bottomLeft.x", rrect.bottomLeft.x); float("geometry.rrect.bottomLeft.y", rrect.bottomLeft.y)
            }
            is ClipGeometryF32.Path, ClipGeometryF32.Empty -> error("I1 only catalogs analytic clip producers.")
        }
        int("inverseCoverage", if (host.inverseCoverage) 1 else 0); int("antiAlias", if (host.antiAlias) 1 else 0); int("sampleCount", host.sampleCountI32)
        enum("shader", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); int("draw.fullscreenVertexCount", host.fullscreenVertexCountI32)
    }
}

private fun W4eClipMaskProducerPhysicalOperandV1(row: PlanResource) = W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())

/** Freezes only final W6-bound analytic producer packets. */
public fun freezeW4eClipMaskProducerRecipesV1(bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>): Map<PlanPassId, W4eClipMaskProducerRecipeV1> {
    val rows = resources.associateBy { it.id }
    val recipes = linkedMapOf<PlanPassId, W4eClipMaskProducerRecipeV1>()
    bindings.forEach { binding -> binding.nativePasses().forEach { pass ->
        val producer = pass as? PlanPass.ClipMaskProducer ?: return@forEach
        val geometry = when (producer.copyGeometryF32()) {
            is ClipGeometryF32.Rect -> W4eClipMaskProducerGeometryV1.Rect
            is ClipGeometryF32.RRect -> W4eClipMaskProducerGeometryV1.RRect
            is ClipGeometryF32.Path, ClipGeometryF32.Empty -> return@forEach
        }
        val slice = requireNotNull(binding.payload.uniformSlice(producer.id.value, W4eNativePayloadPlan.PRODUCER_UNIFORM))
        val target = rows.getValue(producer.target)
        val resolve = producer.resolveTarget?.let(rows::getValue)
        val depth = producer.depthStencil?.let(rows::getValue)
        val uniform = rows.getValue(binding.payload.uniformResourceId)
        require(recipes.put(producer.id, W4eClipMaskProducerRecipeV1(producer.id, producer.ordinal,
            W4eClipMaskProducerPhysicalOperandV1(target), resolve?.let(::W4eClipMaskProducerPhysicalOperandV1), depth?.let(::W4eClipMaskProducerPhysicalOperandV1),
            W4eClipMaskProducerPhysicalOperandV1(uniform), slice.purpose, slice.offsetBytes, slice.byteSize, geometry,
            producer.copyGeometryF32(), producer.inverseCoverage, producer.antiAlias, producer.sampleCountI32,
            W4eClipMaskProducerShaderFamilyV1.AnalyticCoverage, W4eClipMaskProducerGroupZeroAbiV1.ProducerUniform,
            W4eClipMaskProducerLoadV1.Clear, W4eClipMaskProducerStoreV1.Store)) == null)
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}
