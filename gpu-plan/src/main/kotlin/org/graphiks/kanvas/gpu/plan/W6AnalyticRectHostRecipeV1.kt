package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.kanvas.render.ir.DrawOrigin

/** Fixed planner-owned CorePrimitive axes for the bounded W6 analytic-rectangle host. */
public enum class W6CorePrimitiveHostGeometryFamilyV1 { AnalyticRect, AnalyticRRect }
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
        require(family in setOf(
            W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect,
            W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect,
        ))
        require(uniformAbi == W6CorePrimitiveHostUniformAbiV1.AnalyticShape80)
        require(target == W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample)
        require(coverage == W6CorePrimitiveHostCoverageV1.AnalyticScalarAA)
        require(coordinateSlot == W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition)
        require(groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80)
    }
}

/** Common sealed shape of every bounded analytic CorePrimitive host recipe. */
public sealed interface W6CorePrimitiveHostRecipeV1 {
    public val site: W6GeometrySiteKeyV1
    public val selector: W6CorePrimitiveHostSelectorV1
    public val rasterBounds: RectI32
    public val scissor: RectI32
    public val materialOriginDeviceI32: Point2I32
}

/** Immutable final-pass recipe for one W6 AnalyticRect draw; no renderer-owned geometry is added. */
@ConsistentCopyVisibility
public data class W6AnalyticRectHostRecipeV1 internal constructor(
    override public val site: W6GeometrySiteKeyV1,
    override public val selector: W6CorePrimitiveHostSelectorV1,
    public val deviceBounds: RectF32,
    override public val rasterBounds: RectI32,
    override public val scissor: RectI32,
    override public val materialOriginDeviceI32: Point2I32,
) : W6CorePrimitiveHostRecipeV1 {
    init {
        require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect)
        require(!deviceBounds.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty)
    }
}

/** Immutable final-pass recipe for one W6 AnalyticRRect draw, including its canonical radii. */
@ConsistentCopyVisibility
public data class W6AnalyticRRectHostRecipeV1 internal constructor(
    override public val site: W6GeometrySiteKeyV1,
    override public val selector: W6CorePrimitiveHostSelectorV1,
    public val deviceShape: RRectF32,
    public val drawOrigin: DrawOrigin,
    override public val rasterBounds: RectI32,
    override public val scissor: RectI32,
    override public val materialOriginDeviceI32: Point2I32,
) : W6CorePrimitiveHostRecipeV1 {
    init {
        require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect)
        require(drawOrigin == DrawOrigin.RECT || drawOrigin == DrawOrigin.RRECT)
        require(!deviceShape.rect.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty)
    }
}

/** Freezes only AnalyticRectDraw and AnalyticRRectDraw sites in final RenderPass planner order. */
public fun freezeW6CorePrimitiveHostsV1(passes: List<PlanPass>): Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1> {
    val recipes = linkedMapOf<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1>()
    passes.forEach { pass ->
        val render = pass as? PlanPass.RenderPass ?: return@forEach
        val origin = requireNotNull(render.copyMaterialDeviceOriginI32()) {
            "W6 AnalyticRect host recipe requires the final material origin for ${render.id.value}."
        }
        render.draws().forEachIndexed { drawOrdinalI32, draw ->
            val site = W6GeometrySiteKeyV1(render.id, drawOrdinalI32)
            val recipe = when (draw) {
                is AnalyticRectDraw -> W6AnalyticRectHostRecipeV1(
                    site = site,
                    selector = W6CorePrimitiveHostSelectorV1(
                        family = W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect,
                        uniformAbi = W6CorePrimitiveHostUniformAbiV1.AnalyticShape80,
                        target = W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample,
                        blend = draw.blend,
                        coverage = W6CorePrimitiveHostCoverageV1.AnalyticScalarAA,
                        coordinateSlot = W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition,
                        groupZeroAbi = W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80,
                    ),
                    deviceBounds = draw.copyDeviceBounds(),
                    rasterBounds = draw.copyRasterBounds(),
                    scissor = draw.copyScissor(),
                    materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
                )
                is AnalyticRRectDraw -> W6AnalyticRRectHostRecipeV1(
                    site = site,
                    selector = W6CorePrimitiveHostSelectorV1(
                        family = W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect,
                        uniformAbi = W6CorePrimitiveHostUniformAbiV1.AnalyticShape80,
                        target = W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample,
                        blend = draw.blend,
                        coverage = W6CorePrimitiveHostCoverageV1.AnalyticScalarAA,
                        coordinateSlot = W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition,
                        groupZeroAbi = W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80,
                    ),
                    deviceShape = draw.copyDeviceShape(),
                    drawOrigin = draw.origin,
                    rasterBounds = draw.copyRasterBounds(),
                    scissor = draw.copyScissor(),
                    materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
                )
                else -> return@forEachIndexed
            }
            require(recipes.put(site, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(recipes)
}
