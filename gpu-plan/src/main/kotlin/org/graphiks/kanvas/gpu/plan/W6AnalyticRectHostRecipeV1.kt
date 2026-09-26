package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

/** Fixed planner-owned CorePrimitive axes for the bounded W6 analytic-rectangle host. */
public enum class W6CorePrimitiveHostGeometryFamilyV1 { AnalyticRect }
public enum class W6CorePrimitiveHostUniformAbiV1 { AnalyticShape80 }
public enum class W6CorePrimitiveHostTargetFormatV1 { RGBA8UnormSrgb }
public enum class W6CorePrimitiveHostCoordinateSlotV1 { FragmentPosition }
public enum class W6CorePrimitiveHostGroupZeroAbiV1 { DynamicUniform80 }
public enum class W6CorePrimitiveHostCoverageV1 { AnalyticScalarAA }

/** Fixed render-target axes selected by the planner rather than accepted as native defaults. */
public data class W6CorePrimitiveHostTargetV1(
    public val format: W6CorePrimitiveHostTargetFormatV1,
    public val sampleCountI32: Int,
) {
    init { require(format == W6CorePrimitiveHostTargetFormatV1.RGBA8UnormSrgb && sampleCountI32 == 1) }

    public companion object {
        public val Rgba8UnormSrgbSingleSample: W6CorePrimitiveHostTargetV1 =
            W6CorePrimitiveHostTargetV1(W6CorePrimitiveHostTargetFormatV1.RGBA8UnormSrgb, 1)
    }
}

/** Renderer-neutral selector for the existing analytic CorePrimitive implementation. */
public data class W6CorePrimitiveHostSelectorV1(
    public val family: W6CorePrimitiveHostGeometryFamilyV1,
    public val uniformAbi: W6CorePrimitiveHostUniformAbiV1,
    public val target: W6CorePrimitiveHostTargetV1,
    public val blend: BlendPlan,
    public val coverage: W6CorePrimitiveHostCoverageV1,
    public val coordinateSlot: W6CorePrimitiveHostCoordinateSlotV1,
    public val groupZeroAbi: W6CorePrimitiveHostGroupZeroAbiV1,
) {
    init {
        require(family == W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect)
        require(uniformAbi == W6CorePrimitiveHostUniformAbiV1.AnalyticShape80)
        require(target == W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample)
        require(coverage == W6CorePrimitiveHostCoverageV1.AnalyticScalarAA)
        require(coordinateSlot == W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition)
        require(groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80)
    }
}

/** Immutable final-pass recipe for one W6 AnalyticRect draw; no renderer-owned geometry is added. */
@ConsistentCopyVisibility
public data class W6AnalyticRectHostRecipeV1 internal constructor(
    public val site: W6GeometrySiteKeyV1,
    public val selector: W6CorePrimitiveHostSelectorV1,
    public val deviceBounds: RectF32,
    public val rasterBounds: RectI32,
    public val scissor: RectI32,
    public val materialOriginDeviceI32: Point2I32,
) {
    init {
        require(!deviceBounds.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty)
    }
}

/** Freezes only AnalyticRectDraw sites in final RenderPass planner order. */
public fun freezeW6AnalyticRectHostsV1(passes: List<PlanPass>): Map<W6GeometrySiteKeyV1, W6AnalyticRectHostRecipeV1> {
    val recipes = linkedMapOf<W6GeometrySiteKeyV1, W6AnalyticRectHostRecipeV1>()
    passes.forEach { pass ->
        val render = pass as? PlanPass.RenderPass ?: return@forEach
        val origin = requireNotNull(render.copyMaterialDeviceOriginI32()) {
            "W6 AnalyticRect host recipe requires the final material origin for ${render.id.value}."
        }
        render.draws().forEachIndexed { drawOrdinalI32, draw ->
            val analytic = draw as? AnalyticRectDraw ?: return@forEachIndexed
            val site = W6GeometrySiteKeyV1(render.id, drawOrdinalI32)
            val recipe = W6AnalyticRectHostRecipeV1(
                site = site,
                selector = W6CorePrimitiveHostSelectorV1(
                    family = W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect,
                    uniformAbi = W6CorePrimitiveHostUniformAbiV1.AnalyticShape80,
                    target = W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample,
                    blend = analytic.blend,
                    coverage = W6CorePrimitiveHostCoverageV1.AnalyticScalarAA,
                    coordinateSlot = W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition,
                    groupZeroAbi = W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80,
                ),
                deviceBounds = analytic.copyDeviceBounds(),
                rasterBounds = analytic.copyRasterBounds(),
                scissor = analytic.copyScissor(),
                materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
            )
            require(recipes.put(site, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(recipes)
}
