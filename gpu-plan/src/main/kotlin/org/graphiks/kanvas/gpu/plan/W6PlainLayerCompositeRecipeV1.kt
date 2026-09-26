package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32

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

/** Freezes exactly one restore site for each final plain LayerComposite pass, in planner order. */
public fun freezeW6PlainLayerCompositeRecipesV1(
    passes: List<PlanPass>,
): Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1> {
    val recipes = linkedMapOf<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1>()
    passes.forEach { pass ->
        val composite = pass as? PlanPass.LayerComposite ?: return@forEach
        if (composite.restore.colorFilter != null || composite.restore.blend is BlendPlan.DestinationReadV1) return@forEach
        val site = W6LayerCompositeSiteKeyV1(composite.id, 0)
        val recipe = W6PlainLayerCompositeRecipeV1(
            site = site,
            family = W6PlainLayerCompositeFamilyV1.FullscreenRestore,
            source = composite.source,
            destination = composite.destination,
            sourceBoundsLayerI32 = composite.copySourceBoundsLayerI32(),
            destinationOriginParentI32 = composite.copyDestinationOriginParentI32(),
            alphaF32 = composite.restore.alphaF32,
            blend = composite.restore.blend,
            target = W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample,
            groupZeroAbi = W6PlainLayerCompositeGroupZeroAbiV1.OneTexture,
        )
        require(recipes.put(site, recipe) == null)
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}
