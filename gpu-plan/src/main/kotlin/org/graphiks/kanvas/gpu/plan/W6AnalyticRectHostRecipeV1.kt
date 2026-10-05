package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.PointMode

/** Fixed planner-owned CorePrimitive axes for the bounded W6 analytic-rectangle host. */
public enum class W6CorePrimitiveHostGeometryFamilyV1 { AnalyticRect, AnalyticRRect, Point }
public enum class W6CorePrimitiveHostUniformAbiV1 { AnalyticShape80, Point32 }
public enum class W6CorePrimitiveHostTargetFormatV1 { RGBA8UnormSrgb }
public enum class W6CorePrimitiveHostCoordinateSlotV1 { FragmentPosition }
public enum class W6CorePrimitiveHostGroupZeroAbiV1 { DynamicUniform80, DynamicUniform32 }
public enum class W6CorePrimitiveHostCoverageV1 { AnalyticScalarAA, FullOrScissor }
public enum class W6CorePrimitiveHostTopologyV1 { IndexedTriangleList }

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
    public val topology: W6CorePrimitiveHostTopologyV1,
    public val coordinateSlot: W6CorePrimitiveHostCoordinateSlotV1,
    public val groupZeroAbi: W6CorePrimitiveHostGroupZeroAbiV1,
) {
    init {
        require(target == W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample)
        require(topology == W6CorePrimitiveHostTopologyV1.IndexedTriangleList)
        require(coordinateSlot == W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition)
        when (family) {
            W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect,
            W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect,
            -> require(uniformAbi == W6CorePrimitiveHostUniformAbiV1.AnalyticShape80 &&
                coverage == W6CorePrimitiveHostCoverageV1.AnalyticScalarAA &&
                groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80)
            W6CorePrimitiveHostGeometryFamilyV1.Point -> require(
                uniformAbi == W6CorePrimitiveHostUniformAbiV1.Point32 &&
                    coverage == W6CorePrimitiveHostCoverageV1.FullOrScissor &&
                    groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform32,
            )
        }
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
public class W6AnalyticRectHostRecipeV1 internal constructor(
    override public val site: W6GeometrySiteKeyV1,
    override public val selector: W6CorePrimitiveHostSelectorV1,
    deviceBounds: RectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
    materialOriginDeviceI32: Point2I32,
) : W6CorePrimitiveHostRecipeV1 {
    private val deviceBoundsSnapshot = deviceBounds.copy()
    private val rasterBoundsSnapshot = rasterBounds.copy()
    private val scissorSnapshot = scissor.copy()
    private val materialOriginSnapshot = Point2I32(materialOriginDeviceI32.x, materialOriginDeviceI32.y)
    public val deviceBounds: RectF32 get() = deviceBoundsSnapshot.copy()
    override public val rasterBounds: RectI32 get() = rasterBoundsSnapshot.copy()
    override public val scissor: RectI32 get() = scissorSnapshot.copy()
    override public val materialOriginDeviceI32: Point2I32 get() =
        Point2I32(materialOriginSnapshot.x, materialOriginSnapshot.y)

    init {
        require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect)
        require(!deviceBoundsSnapshot.isEmpty && !rasterBoundsSnapshot.isEmpty && !scissorSnapshot.isEmpty)
    }

    override fun equals(other: Any?): Boolean = other is W6AnalyticRectHostRecipeV1 &&
        site == other.site && selector == other.selector && deviceBoundsSnapshot == other.deviceBoundsSnapshot &&
        rasterBoundsSnapshot == other.rasterBoundsSnapshot && scissorSnapshot == other.scissorSnapshot &&
        materialOriginSnapshot == other.materialOriginSnapshot

    override fun hashCode(): Int = listOf(site, selector, deviceBoundsSnapshot, rasterBoundsSnapshot,
        scissorSnapshot, materialOriginSnapshot).fold(1) { hash, value -> 31 * hash + value.hashCode() }
}

/** Immutable final-pass recipe for one W6 AnalyticRRect draw, including its canonical radii. */
public class W6AnalyticRRectHostRecipeV1 internal constructor(
    override public val site: W6GeometrySiteKeyV1,
    override public val selector: W6CorePrimitiveHostSelectorV1,
    deviceShape: RRectF32,
    public val drawOrigin: DrawOrigin,
    rasterBounds: RectI32,
    scissor: RectI32,
    materialOriginDeviceI32: Point2I32,
) : W6CorePrimitiveHostRecipeV1 {
    private val deviceShapeSnapshot = deviceShape.copyFrozen()
    private val rasterBoundsSnapshot = rasterBounds.copy()
    private val scissorSnapshot = scissor.copy()
    private val materialOriginSnapshot = Point2I32(materialOriginDeviceI32.x, materialOriginDeviceI32.y)
    public val deviceShape: RRectF32 get() = deviceShapeSnapshot.copyFrozen()
    override public val rasterBounds: RectI32 get() = rasterBoundsSnapshot.copy()
    override public val scissor: RectI32 get() = scissorSnapshot.copy()
    override public val materialOriginDeviceI32: Point2I32 get() =
        Point2I32(materialOriginSnapshot.x, materialOriginSnapshot.y)

    init {
        require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect)
        require(drawOrigin == DrawOrigin.RECT || drawOrigin == DrawOrigin.RRECT)
        require(!deviceShapeSnapshot.rect.isEmpty && !rasterBoundsSnapshot.isEmpty && !scissorSnapshot.isEmpty)
    }

    override fun equals(other: Any?): Boolean = other is W6AnalyticRRectHostRecipeV1 &&
        site == other.site && selector == other.selector && deviceShapeSnapshot == other.deviceShapeSnapshot &&
        drawOrigin == other.drawOrigin && rasterBoundsSnapshot == other.rasterBoundsSnapshot &&
        scissorSnapshot == other.scissorSnapshot && materialOriginSnapshot == other.materialOriginSnapshot

    override fun hashCode(): Int = listOf(site, selector, deviceShapeSnapshot, drawOrigin, rasterBoundsSnapshot,
        scissorSnapshot, materialOriginSnapshot).fold(1) { hash, value -> 31 * hash + value.hashCode() }
}

/** Immutable final-pass recipe for one non-W4e W5b Point draw. Geometry remains sealed scalar sequences. */
public class W6PointHostRecipeV1 internal constructor(
    override public val site: W6GeometrySiteKeyV1,
    override public val selector: W6CorePrimitiveHostSelectorV1,
    public val pointMode: PointMode,
    verticesF32: List<Float>,
    indicesI32: List<Int>,
    bounds: RectI32,
    scissor: RectI32,
    materialOriginDeviceI32: Point2I32,
) : W6CorePrimitiveHostRecipeV1 {
    private val verticesSnapshot = immutableList(verticesF32)
    private val indicesSnapshot = immutableList(indicesI32)
    private val boundsSnapshot = bounds.copy()
    private val scissorSnapshot = scissor.copy()
    private val materialOriginSnapshot = Point2I32(materialOriginDeviceI32.x, materialOriginDeviceI32.y)
    public val vertexCountI32: Int get() = verticesSnapshot.size / 2
    public val indexCountI32: Int get() = indicesSnapshot.size
    public val maxIndexI32: Int get() = indicesSnapshot.max()
    public val bounds: RectI32 get() = boundsSnapshot.copy()
    override public val rasterBounds: RectI32 get() = boundsSnapshot.copy()
    override public val scissor: RectI32 get() = scissorSnapshot.copy()
    override public val materialOriginDeviceI32: Point2I32 get() =
        Point2I32(materialOriginSnapshot.x, materialOriginSnapshot.y)

    init {
        require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.Point)
        require(pointMode == PointMode.POINTS && !boundsSnapshot.isEmpty && !scissorSnapshot.isEmpty)
        require(verticesSnapshot.size >= 8 && verticesSnapshot.size % 8 == 0 && verticesSnapshot.all(Float::isFinite))
        require(indicesSnapshot.size == verticesSnapshot.size / 8 * 6 && indicesSnapshot.isNotEmpty())
        require(indicesSnapshot.all { it in 0 until verticesSnapshot.size / 2 })
    }

    public fun copyVerticesF32(): FloatArray = verticesSnapshot.toFloatArray()
    public fun copyIndicesI32(): IntArray = indicesSnapshot.toIntArray()

    override fun equals(other: Any?): Boolean = other is W6PointHostRecipeV1 &&
        site == other.site && selector == other.selector && pointMode == other.pointMode &&
        verticesSnapshot == other.verticesSnapshot && indicesSnapshot == other.indicesSnapshot &&
        boundsSnapshot == other.boundsSnapshot && scissorSnapshot == other.scissorSnapshot &&
        materialOriginSnapshot == other.materialOriginSnapshot

    override fun hashCode(): Int = listOf(site, selector, pointMode, verticesSnapshot, indicesSnapshot,
        boundsSnapshot, scissorSnapshot, materialOriginSnapshot).fold(1) { hash, value -> 31 * hash + value.hashCode() }
}

private fun RRectF32.copyFrozen(): RRectF32 = RRectF32.of(
    rect.copy(), topLeft, topRight, bottomRight, bottomLeft,
)

/** Freezes only non-W4e CorePrimitive geometry in final RenderPass planner order. */
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
                        topology = W6CorePrimitiveHostTopologyV1.IndexedTriangleList,
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
                        topology = W6CorePrimitiveHostTopologyV1.IndexedTriangleList,
                        coordinateSlot = W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition,
                        groupZeroAbi = W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80,
                    ),
                    deviceShape = draw.copyDeviceShape(),
                    drawOrigin = draw.origin,
                    rasterBounds = draw.copyRasterBounds(),
                    scissor = draw.copyScissor(),
                    materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
                )
                is W5bPointDraw -> if (draw.clipOnly == null) W6PointHostRecipeV1(
                    site = site,
                    selector = W6CorePrimitiveHostSelectorV1(
                        family = W6CorePrimitiveHostGeometryFamilyV1.Point,
                        uniformAbi = W6CorePrimitiveHostUniformAbiV1.Point32,
                        target = W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample,
                        blend = draw.blend,
                        coverage = W6CorePrimitiveHostCoverageV1.FullOrScissor,
                        topology = W6CorePrimitiveHostTopologyV1.IndexedTriangleList,
                        coordinateSlot = W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition,
                        groupZeroAbi = W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform32,
                    ),
                    pointMode = draw.pointMode,
                    verticesF32 = immutableList(draw.copyVerticesF32().toList()),
                    indicesI32 = immutableList(draw.copyIndicesI32().toList()),
                    bounds = draw.copyBoundsI32(),
                    scissor = draw.copyScissorI32(),
                    materialOriginDeviceI32 = Point2I32(origin.x, origin.y),
                ) else return@forEachIndexed
                else -> return@forEachIndexed
            }
            require(recipes.put(site, recipe) == null)
        }
    }
    return java.util.Collections.unmodifiableMap(recipes)
}
