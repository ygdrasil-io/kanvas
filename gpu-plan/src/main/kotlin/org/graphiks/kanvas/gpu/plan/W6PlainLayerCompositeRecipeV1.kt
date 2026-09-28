package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** Stable planner-owned location of the one fullscreen restore operation in a LayerComposite pass. */
public data class W6LayerCompositeSiteKeyV1(
    public val ownerPassId: PlanPassId,
    public val siteOrdinalI32: Int,
) {
    init { require(siteOrdinalI32 == 0) }
}

public enum class W6PlainLayerCompositeFamilyV1 { FullscreenRestore }
public enum class W6PlainLayerCompositeTargetFormatV1 { RGBA8UnormSrgb }
public enum class W6PlainLayerCompositeGroupZeroAbiV1 { OneTexture }

/** Target facts deliberately selected by the planner rather than accepted as native defaults. */
public data class W6PlainLayerCompositeTargetV1(
    public val format: W6PlainLayerCompositeTargetFormatV1,
    public val sampleCountI32: Int,
) {
    init { require(format == W6PlainLayerCompositeTargetFormatV1.RGBA8UnormSrgb && sampleCountI32 == 1) }

    public companion object {
        public val Rgba8UnormSrgbSingleSample: W6PlainLayerCompositeTargetV1 =
            W6PlainLayerCompositeTargetV1(W6PlainLayerCompositeTargetFormatV1.RGBA8UnormSrgb, 1)
    }
}

/**
 * Immutable, planner-owned recipe for the unfiltered, non-destination-read layer restore.
 * Filtered and destination-read restores intentionally have no entry in this map yet.
 */
public class W6PlainLayerCompositeRecipeV1 internal constructor(
    public val site: W6LayerCompositeSiteKeyV1,
    public val family: W6PlainLayerCompositeFamilyV1,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    sourceBoundsLayerI32: RectI32,
    destinationOriginParentI32: Point2I32,
    alphaF32: Float,
    public val blend: BlendPlan,
    public val target: W6PlainLayerCompositeTargetV1,
    public val groupZeroAbi: W6PlainLayerCompositeGroupZeroAbiV1,
) {
    private val sourceBoundsSnapshotI32 = sourceBoundsLayerI32.copy()
    private val destinationOriginSnapshotI32 = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y)
    /** Finite canonical F32: signed zero is normalized before it reaches the renderer. */
    public val alphaF32: Float = canonicalAlpha(alphaF32)

    public fun copySourceBoundsLayerI32(): RectI32 = sourceBoundsSnapshotI32.copy()
    public fun copyDestinationOriginParentI32(): Point2I32 =
        Point2I32(destinationOriginSnapshotI32.x, destinationOriginSnapshotI32.y)

    init {
        require(family == W6PlainLayerCompositeFamilyV1.FullscreenRestore)
        require(source != destination && !sourceBoundsSnapshotI32.isEmpty)
        require(blend !is BlendPlan.DestinationReadV1)
        require(target == W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample)
        require(groupZeroAbi == W6PlainLayerCompositeGroupZeroAbiV1.OneTexture)
    }

    override fun equals(other: Any?): Boolean = other is W6PlainLayerCompositeRecipeV1 &&
        site == other.site && family == other.family && source == other.source && destination == other.destination &&
        sourceBoundsSnapshotI32 == other.sourceBoundsSnapshotI32 &&
        destinationOriginSnapshotI32 == other.destinationOriginSnapshotI32 && alphaF32.toBits() == other.alphaF32.toBits() &&
        blend == other.blend && target == other.target && groupZeroAbi == other.groupZeroAbi

    override fun hashCode(): Int = listOf(site, family, source, destination, sourceBoundsSnapshotI32,
        destinationOriginSnapshotI32, alphaF32.toBits(), blend, target, groupZeroAbi).fold(1) { hash, value ->
        31 * hash + value.hashCode()
    }

    private companion object {
        fun canonicalAlpha(value: Float): Float {
            require(value.isFinite() && value in 0f..1f) { "Frozen W6 plain layer restore alpha must be finite and normalized." }
            return if (value == 0f) 0f else value
        }
    }
}

/**
 * Freezes the one-texture SrcOver site for each final plain layer restore and for the sealed
 * W4d AA resolved-colour source.  Both sites deliberately use the same fully specified
 * fullscreen ABI; the latter is not allowed to inherit the mutable layer-restore state.
 */
public fun freezeW6PlainLayerCompositeRecipesV1(
    passes: List<PlanPass>,
): Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1> {
    val recipes = linkedMapOf<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1>()
    passes.forEach { pass ->
        val composite = pass as? PlanPass.LayerComposite
        val aa = pass as? PlanPass.PathAaColorComposite
        if (composite == null && aa == null) return@forEach
        if (composite != null && (composite.restore.colorFilter != null || composite.restore.blend is BlendPlan.DestinationReadV1)) return@forEach
        val site = W6LayerCompositeSiteKeyV1(pass.id, 0)
        val recipe = W6PlainLayerCompositeRecipeV1(
            site = site,
            family = W6PlainLayerCompositeFamilyV1.FullscreenRestore,
            source = composite?.source ?: requireNotNull(aa).source,
            destination = composite?.destination ?: requireNotNull(aa).destination,
            sourceBoundsLayerI32 = composite?.copySourceBoundsLayerI32() ?: requireNotNull(aa).copySourceBoundsLayerI32(),
            destinationOriginParentI32 = composite?.copyDestinationOriginParentI32() ?: requireNotNull(aa).copyDestinationOriginLayerI32(),
            alphaF32 = composite?.restore?.alphaF32 ?: 1f,
            blend = composite?.restore?.blend ?: BlendPlan.LegacySrcOverV1,
            target = W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample,
            groupZeroAbi = W6PlainLayerCompositeGroupZeroAbiV1.OneTexture,
        )
        require(recipes.put(site, recipe) == null)
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}

/** IIIa1's sole active filtered restore: one texture plus the pre-issued layer uniform window. */
public enum class W6FilteredLayerCompositeShaderFamilyV1 { W5fColorOperationTextureLoad }
public enum class W6FilteredLayerCompositeGroupZeroAbiV1 { TextureAndW5fUniform }
public class W6FilteredLayerCompositeRecipeV1 internal constructor(
    public val site: W6LayerCompositeSiteKeyV1,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    targetExtentI32: SizeI32,
    sourceExtentI32: SizeI32,
    sourceBoundsLayerI32: RectI32,
    destinationOriginParentI32: Point2I32,
    scissorParentI32: RectI32,
    alphaF32: Float,
    public val blend: BlendPlan,
    public val execution: ColorFilterExecutionPlanV1,
    public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long,
    public val uniformCapacityBytesI64: Long,
    public val target: W6PlainLayerCompositeTargetV1,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val groupZeroAbi: W6FilteredLayerCompositeGroupZeroAbiV1 = W6FilteredLayerCompositeGroupZeroAbiV1.TextureAndW5fUniform,
    public val shaderFamily: W6FilteredLayerCompositeShaderFamilyV1 = W6FilteredLayerCompositeShaderFamilyV1.W5fColorOperationTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenSourceBounds = sourceBoundsLayerI32.copy()
    private val frozenTargetExtent = targetExtentI32.copy()
    private val frozenSourceExtent = sourceExtentI32.copy()
    private val frozenDestinationOrigin = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y)
    private val frozenScissor = scissorParentI32.copy()
    public val alphaF32: Float = if (alphaF32 == 0f) 0f else alphaF32
    init {
        require(source != destination && !frozenSourceBounds.isEmpty && !frozenScissor.isEmpty && frozenTargetExtent.width > 0 && frozenTargetExtent.height > 0 && frozenSourceExtent.width > 0 && frozenSourceExtent.height > 0)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && blend !is BlendPlan.DestinationReadV1)
        require(target == W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample && sourceFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceSampleCountI32 == 1)
        require(uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, execution.dynamicByteCountI64)) <= uniformCapacityBytesI64)
    }
    public fun copySourceBoundsLayerI32() = frozenSourceBounds.copy()
    public fun copyTargetExtentI32() = frozenTargetExtent.copy()
    public fun copySourceExtentI32() = frozenSourceExtent.copy()
    public fun copyDestinationOriginParentI32() = Point2I32(frozenDestinationOrigin.x, frozenDestinationOrigin.y)
    public fun copyScissorParentI32() = frozenScissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(site.ownerPassId, site.siteOrdinalI32, 0)
    public fun canonicalLogicalEncodingV1() = W6FilteredLayerCompositeNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public fun freezeW6FilteredLayerCompositeRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<W6LayerCompositeSiteKeyV1, W6FilteredLayerCompositeRecipeV1> {
    val uniform = resources.single {
        it.id == planResourceId(PlanResourceRole.UniformData, 0) &&
            it.kind == PlanResourceKind.Buffer &&
            it.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination)
    }
    return java.util.Collections.unmodifiableMap(linkedMapOf<W6LayerCompositeSiteKeyV1, W6FilteredLayerCompositeRecipeV1>().apply {
        passes.filterIsInstance<PlanPass.LayerComposite>().forEach { pass ->
            val filter = pass.restore.colorFilter ?: return@forEach
            if (pass.restore.blend is BlendPlan.DestinationReadV1) return@forEach
            val uniformOffset = requireNotNull(pass.restore.colorFilterUniformOffsetI64) {
                "Active filtered LayerComposite ${pass.id.value} is missing its sealed W5f uniform window."
            }
            val bounds = pass.copySourceBoundsLayerI32(); val origin = pass.copyDestinationOriginParentI32()
            val scissor = RectI32(origin.x, origin.y, Math.addExact(origin.x, bounds.width()), Math.addExact(origin.y, bounds.height()))
            val site = W6LayerCompositeSiteKeyV1(pass.id, 0)
            val target = resources.single { it.id == pass.destination }
            val source = resources.single { it.id == pass.source }
            require(put(site, W6FilteredLayerCompositeRecipeV1(site, pass.source, pass.destination,
                requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), bounds, origin, scissor, pass.restore.alphaF32, pass.restore.blend, filter, uniform.id, uniformOffset, uniform.byteSize,
                W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, 1)) == null)
        }
    })
}
