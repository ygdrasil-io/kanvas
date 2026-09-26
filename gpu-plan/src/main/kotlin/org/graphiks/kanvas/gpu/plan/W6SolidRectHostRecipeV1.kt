package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32

/** Stable planner-owned location of one W6 SolidRect native geometry site. */
public data class W6GeometrySiteKeyV1(
    public val ownerPassId: PlanPassId,
    public val drawOrdinalI32: Int,
) {
    init { require(drawOrdinalI32 >= 0) }
}

/** The only W6 fullscreen-triangle family admitted by this bounded host-recipe prerequisite. */
public enum class W6SolidRectGeometryFamilyV1 { FullscreenTriangle }

/** Canonical finite F32 payload used by a legacy color that has no W5 uniform row. */
@ConsistentCopyVisibility
public data class W6CanonicalColorF32V1 private constructor(
    public val redF32: Float,
    public val greenF32: Float,
    public val blueF32: Float,
    public val alphaF32: Float,
) {
    public fun copyColorF32(): ColorF32 = ColorF32.of(redF32, greenF32, blueF32, alphaF32)

    public companion object {
        public fun of(color: ColorF32): W6CanonicalColorF32V1 = of(
            color.red, color.green, color.blue, color.alpha,
        )

        public fun of(redF32: Float, greenF32: Float, blueF32: Float, alphaF32: Float): W6CanonicalColorF32V1 {
            fun canonical(value: Float): Float {
                require(value.isFinite()) { "Frozen W6 SolidRect color must be finite" }
                return if (value == 0f) 0f else value
            }
            return W6CanonicalColorF32V1(
                canonical(redF32), canonical(greenF32), canonical(blueF32), canonical(alphaF32),
            )
        }
    }
}

/** The W6 host either binds the existing sixteen-byte uniform row or embeds a frozen legacy color. */
public sealed interface W6SolidRectColorModeV1 {
    public data object UniformColor16 : W6SolidRectColorModeV1
    @ConsistentCopyVisibility
    public data class FrozenColor internal constructor(public val color: W6CanonicalColorF32V1) : W6SolidRectColorModeV1
}

/** Target fact selected by the W6 planner, not a native default. */
public enum class W6SolidRectTargetFormatV1 { RGBA8UnormSrgb }

public data class W6SolidRectTargetV1(
    public val format: W6SolidRectTargetFormatV1,
    public val sampleCountI32: Int,
) {
    init { require(sampleCountI32 == 1) }

    public companion object {
        public val Rgba8UnormSrgbSingleSample: W6SolidRectTargetV1 =
            W6SolidRectTargetV1(W6SolidRectTargetFormatV1.RGBA8UnormSrgb, 1)
    }
}

/** W5 source-coordinate contract for the SolidRect host fragment stage. */
public enum class W6SolidRectCoordinateSlotV1 { FragmentPosition }

/** The host bind-group ABI is fixed at group zero; its entry shape follows the frozen color mode. */
public enum class W6SolidRectGroupZeroAbiV1 { UniformColor16, Empty }

/** I32 material origin already selected with the final target mapping. */
public data class W6SolidRectMaterialOriginI32V1(
    public val xI32: Int,
    public val yI32: Int,
)

/**
 * Immutable, renderer-neutral W6 SolidRect host recipe.  It contains every fact used to select
 * the fullscreen shader, group-zero layout, fixed-function blend, and material coordinate origin.
 */
@ConsistentCopyVisibility
public data class W6SolidRectHostRecipeV1 internal constructor(
    public val site: W6GeometrySiteKeyV1,
    public val family: W6SolidRectGeometryFamilyV1,
    public val colorMode: W6SolidRectColorModeV1,
    public val blend: BlendPlan,
    public val materialOriginDeviceI32: W6SolidRectMaterialOriginI32V1,
    public val target: W6SolidRectTargetV1,
    public val coordinateSlot: W6SolidRectCoordinateSlotV1,
    public val groupZeroAbi: W6SolidRectGroupZeroAbiV1,
) {
    init {
        require(family == W6SolidRectGeometryFamilyV1.FullscreenTriangle)
        require(target == W6SolidRectTargetV1.Rgba8UnormSrgbSingleSample)
        require(coordinateSlot == W6SolidRectCoordinateSlotV1.FragmentPosition)
        require((colorMode is W6SolidRectColorModeV1.UniformColor16) ==
            (groupZeroAbi == W6SolidRectGroupZeroAbiV1.UniformColor16))
    }
}

/** Freezes every SolidRect draw in final planner pass order, without admitting other geometry families. */
public fun freezeW6SolidRectHostsV1(passes: List<PlanPass>): Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1> {
    val recipes = linkedMapOf<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1>()
    passes.forEach { pass ->
        val render = pass as? PlanPass.RenderPass ?: return@forEach
        val origin = requireNotNull(render.copyMaterialDeviceOriginI32()) {
            "W6 SolidRect host recipe requires the final material origin for ${render.id.value}."
        }
        render.draws().forEachIndexed { drawOrdinalI32, draw ->
            val solid = draw as? SolidRectDraw ?: return@forEachIndexed
            val colorMode = when (val authority = solid.materialAuthority) {
                is PlanDrawMaterialAuthority.LegacyColorV1 -> W6SolidRectColorModeV1.FrozenColor(
                    W6CanonicalColorF32V1.of(authority.copyColorF32()),
                )
                else -> W6SolidRectColorModeV1.UniformColor16
            }
            val site = W6GeometrySiteKeyV1(render.id, drawOrdinalI32)
            val recipe = W6SolidRectHostRecipeV1(
                site = site,
                family = W6SolidRectGeometryFamilyV1.FullscreenTriangle,
                colorMode = colorMode,
                blend = solid.blend,
                materialOriginDeviceI32 = W6SolidRectMaterialOriginI32V1(origin.x, origin.y),
                target = W6SolidRectTargetV1.Rgba8UnormSrgbSingleSample,
                coordinateSlot = W6SolidRectCoordinateSlotV1.FragmentPosition,
                groupZeroAbi = if (colorMode is W6SolidRectColorModeV1.UniformColor16)
                    W6SolidRectGroupZeroAbiV1.UniformColor16 else W6SolidRectGroupZeroAbiV1.Empty,
            )
            require(recipes.put(site, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(recipes))
}
