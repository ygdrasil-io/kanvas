package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.PointMode
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

/** Planner-owned axes for the raw W6b coverage producer.  No renderer key occurs here. */
public enum class W6bCoverageRasterFamilyV1 { AnalyticRect, AnalyticRRect, Point, PathFill, PathStroke }
public enum class W6bCoverageRasterPhaseV1 { Direct, StencilProducer, StencilCover }
public enum class W6bCoverageRasterUniformAbiV1 { Coverage32, AnalyticShape80 }
/** The W6b coverage source is a single-sample premultiplied sRGB color attachment. */
public enum class W6bCoverageRasterTargetV1 { Rgba8UnormSrgbSingleSample }
public enum class W6bCoverageRasterStencilV1 { None, WindingProducer, EvenOddProducer, CoverTestNonZero }
public enum class W6bCoverageRasterRoleV1 { Shading, PathStencilProducer, PathStencilCover }
public enum class W6bCoverageRasterTopologyV1 { DirectTriangleList, StencilEdgeFan }
public enum class W6bCoverageRasterClipV1 { None, Scissor }

/**
 * Logical payload for the existing 32-byte coverage ABI.  The W5 lowerers may still construct
 * a material colour while recording a coverage-only packet, but the coverage shader replaces
 * every colour slot with this canonical opaque white before native upload.
 */
public class W6bCoverageUniform32OperandsV1 internal constructor(
    public val targetWidthI32: Int,
    public val targetHeightI32: Int,
    public val color: W6CanonicalColorF32V1,
) {
    init { require(targetWidthI32 > 0 && targetHeightI32 > 0 && color == CanonicalOpaqueWhite) }
    public companion object {
        public val CanonicalOpaqueWhite: W6CanonicalColorF32V1 = W6CanonicalColorF32V1.of(1f, 1f, 1f, 1f)
    }
}

/** All fields that affect the 80-byte analytic coverage ABI, including its canonical white window. */
public class W6bCoverageAnalyticUniform80OperandsV1 internal constructor(
    public val targetWidthI32: Int,
    public val targetHeightI32: Int,
    public val antiAlias: Boolean,
    deviceBounds: RectF32,
    radiiF32: FloatArray,
    public val drawOrigin: DrawOrigin,
    public val color: W6CanonicalColorF32V1,
) {
    private val bounds = deviceBounds.copy()
    private val radii = radiiF32.copyOf()
    public fun copyDeviceBounds(): RectF32 = bounds.copy()
    public fun copyRadiiF32(): FloatArray = radii.copyOf()
    init {
        require(targetWidthI32 > 0 && targetHeightI32 > 0 && !bounds.isEmpty && radii.size == 8 &&
            radii.all { it.isFinite() && it >= 0f } && color == W6bCoverageUniform32OperandsV1.CanonicalOpaqueWhite)
    }
}

/** One direct/producer/cover selector, tied to its already sealed physical bundle. */
public class W6bCoverageRasterBundleHostRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val siteOrdinalI32: Int,
    public val bundleOrdinalI32: Int,
    public val phase: W6bCoverageRasterPhaseV1,
    public val role: W6bCoverageRasterRoleV1,
    public val topology: W6bCoverageRasterTopologyV1,
    public val clip: W6bCoverageRasterClipV1,
    public val family: W6bCoverageRasterFamilyV1,
    public val uniformAbi: W6bCoverageRasterUniformAbiV1,
    public val target: W6bCoverageRasterTargetV1,
    public val output: PlanResourceId,
    /** Explicitly null for direct coverage; retained for both stencil bundles otherwise. */
    public val depthStencil: PlanResourceId?,
    public val blend: BlendPlan,
    public val stencil: W6bCoverageRasterStencilV1,
    public val fillRule: FillRule?,
    scissor: RectI32,
    rasterBounds: RectI32,
    public val pointMode: PointMode?,
    public val pathStrategy: PathFillStrategy?,
    public val geometry: W6bCoverageRasterBundleGeometryV1,
    public val uniform32: W6bCoverageUniform32OperandsV1?,
    public val analytic80: W6bCoverageAnalyticUniform80OperandsV1?,
) {
    private val scissorSnapshot = scissor.copy()
    private val rasterBoundsSnapshot = rasterBounds.copy()
    public fun copyScissorI32(): RectI32 = scissorSnapshot.copy()
    public fun copyRasterBoundsI32(): RectI32 = rasterBoundsSnapshot.copy()
    init {
        require(siteOrdinalI32 == 0)
        require(bundleOrdinalI32 == geometry.bundleOrdinalI32 && !scissorSnapshot.isEmpty)
        require(!rasterBoundsSnapshot.isEmpty) { "W6b coverage raster bounds must be non-empty." }
        require((uniformAbi == W6bCoverageRasterUniformAbiV1.Coverage32) == (uniform32 != null) &&
            (uniformAbi == W6bCoverageRasterUniformAbiV1.AnalyticShape80) == (analytic80 != null))
        require(geometry.uniformWindow.usefulBytesI64 == if (uniformAbi == W6bCoverageRasterUniformAbiV1.Coverage32) 32L else 80L)
        when (phase) {
            W6bCoverageRasterPhaseV1.Direct -> require(geometry.role == W6bCoverageRasterBundleRoleV1.Direct && role == W6bCoverageRasterRoleV1.Shading && topology == W6bCoverageRasterTopologyV1.DirectTriangleList && stencil == W6bCoverageRasterStencilV1.None && depthStencil == null)
            W6bCoverageRasterPhaseV1.StencilProducer -> require(geometry.role == W6bCoverageRasterBundleRoleV1.StencilProducer && role == W6bCoverageRasterRoleV1.PathStencilProducer && topology == W6bCoverageRasterTopologyV1.StencilEdgeFan && clip == W6bCoverageRasterClipV1.None && stencil in setOf(W6bCoverageRasterStencilV1.WindingProducer, W6bCoverageRasterStencilV1.EvenOddProducer) && fillRule != null && depthStencil != null)
            W6bCoverageRasterPhaseV1.StencilCover -> require(geometry.role == W6bCoverageRasterBundleRoleV1.StencilCover && role == W6bCoverageRasterRoleV1.PathStencilCover && topology == W6bCoverageRasterTopologyV1.DirectTriangleList && stencil == W6bCoverageRasterStencilV1.CoverTestNonZero && fillRule != null && depthStencil != null)
        }
        require((family == W6bCoverageRasterFamilyV1.Point) == (pointMode == PointMode.POINTS)) {
            "W6b Point mode must be POINTS and only Point carries it."
        }
        require((family == W6bCoverageRasterFamilyV1.PathFill || family == W6bCoverageRasterFamilyV1.PathStroke) == (pathStrategy != null)) {
            "W6b path strategy must be retained only for paths."
        }
    }
}

/** Final, ordered W6b coverage selection and logical uniform authority for one raster pass. */
public class W6bCoverageRasterHostRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val siteOrdinalI32: Int,
    bundles: List<W6bCoverageRasterBundleHostRecipeV1>,
) {
    private val snapshot = java.util.Collections.unmodifiableList(bundles.toList())
    public fun bundles(): List<W6bCoverageRasterBundleHostRecipeV1> = snapshot
    public fun bundle(ordinalI32: Int): W6bCoverageRasterBundleHostRecipeV1 = snapshot.single { it.bundleOrdinalI32 == ordinalI32 }
    init { require(siteOrdinalI32 == 0 && snapshot.map { it.bundleOrdinalI32 } == snapshot.indices.toList() && snapshot.all { it.ownerPassId == ownerPassId }) }
}

public fun freezeW6bCoverageRasterHostsV1(
    passes: List<PlanPass>, resources: List<PlanResource>, geometryByPass: Map<PlanPassId, W6bCoverageRasterGeometryV1>,
): Map<PlanPassId, W6bCoverageRasterHostRecipeV1> {
    val rows = resources.associateBy(PlanResource::id)
    return java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6bCoverageRasterHostRecipeV1>().also { result ->
        passes.forEach { pass ->
            val coverage = pass as? PlanPass.FilterCoverageSourcePass ?: return@forEach
            val binding = coverage.rasterBinding ?: return@forEach
            if (binding.draw is SolidRectDraw) return@forEach
            val geometry = geometryByPass.getValue(coverage.id)
            val extent = requireNotNull(rows.getValue(coverage.output).copyExtent())
            require(rows.getValue(coverage.output).format ==
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)) {
                "W6b coverage raster output must retain its frozen sRGB color attachment."
            }
            val path = binding.draw as? PathDraw
            val family = when (binding.draw) {
                is AnalyticRectDraw -> W6bCoverageRasterFamilyV1.AnalyticRect
                is AnalyticRRectDraw -> W6bCoverageRasterFamilyV1.AnalyticRRect
                is W5bPointDraw -> W6bCoverageRasterFamilyV1.Point
                is PathFillDraw -> W6bCoverageRasterFamilyV1.PathFill
                is PathStrokeDraw -> W6bCoverageRasterFamilyV1.PathStroke
                else -> error("Unadmitted W6b coverage draw.")
            }
            val fillRule = path?.copyPathGeometry()?.let { geometry -> when (geometry) {
                is PathDrawGeometry.Fill -> geometry.valueF32.fillRule
                is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32().fillRule
                else -> error("Unadmitted W6b coverage path geometry.")
            } }
            val uniformAbi = if (binding.draw is AnalyticRectDraw || binding.draw is AnalyticRRectDraw)
                W6bCoverageRasterUniformAbiV1.AnalyticShape80 else W6bCoverageRasterUniformAbiV1.Coverage32
            val uniform32 = if (uniformAbi == W6bCoverageRasterUniformAbiV1.Coverage32)
                W6bCoverageUniform32OperandsV1(extent.width, extent.height, W6bCoverageUniform32OperandsV1.CanonicalOpaqueWhite) else null
            val analytic = when (val draw = binding.draw) {
                is AnalyticRectDraw -> W6bCoverageAnalyticUniform80OperandsV1(extent.width, extent.height,
                    draw.coverage == CoveragePlan.AnalyticScalarAA,
                    draw.copyDeviceBounds(), FloatArray(8), DrawOrigin.RECT, W6bCoverageUniform32OperandsV1.CanonicalOpaqueWhite)
                is AnalyticRRectDraw -> draw.copyDeviceShape().let { shape -> W6bCoverageAnalyticUniform80OperandsV1(
                    extent.width, extent.height, draw.coverage == CoveragePlan.AnalyticScalarAA, shape.rect, floatArrayOf(shape.topLeft.x, shape.topLeft.y, shape.topRight.x, shape.topRight.y,
                        shape.bottomRight.x, shape.bottomRight.y, shape.bottomLeft.x, shape.bottomLeft.y), draw.origin,
                    W6bCoverageUniform32OperandsV1.CanonicalOpaqueWhite) }
                else -> null
            }
            val bundles = geometry.bundles().map { bundle ->
                val phase = when (bundle.role) {
                    W6bCoverageRasterBundleRoleV1.Direct -> W6bCoverageRasterPhaseV1.Direct
                    W6bCoverageRasterBundleRoleV1.StencilProducer -> W6bCoverageRasterPhaseV1.StencilProducer
                    W6bCoverageRasterBundleRoleV1.StencilCover -> W6bCoverageRasterPhaseV1.StencilCover
                }
                val stencil = when (phase) {
                    W6bCoverageRasterPhaseV1.Direct -> W6bCoverageRasterStencilV1.None
                    W6bCoverageRasterPhaseV1.StencilCover -> W6bCoverageRasterStencilV1.CoverTestNonZero
                    W6bCoverageRasterPhaseV1.StencilProducer -> if (fillRule == FillRule.EVEN_ODD) W6bCoverageRasterStencilV1.EvenOddProducer else W6bCoverageRasterStencilV1.WindingProducer
                }
                val role = when (phase) { W6bCoverageRasterPhaseV1.Direct -> W6bCoverageRasterRoleV1.Shading; W6bCoverageRasterPhaseV1.StencilProducer -> W6bCoverageRasterRoleV1.PathStencilProducer; W6bCoverageRasterPhaseV1.StencilCover -> W6bCoverageRasterRoleV1.PathStencilCover }
                // W4d lowers retained path-stencil passes, including multi-segment strokes,
                // as ordinary edge fans; W6b admits no separate stroke-fan route.
                val topology = if (phase == W6bCoverageRasterPhaseV1.StencilProducer)
                    W6bCoverageRasterTopologyV1.StencilEdgeFan
                else W6bCoverageRasterTopologyV1.DirectTriangleList
                val scissor = path?.copyScissorI32() ?: when (val draw = binding.draw) {
                    is AnalyticRectDraw -> draw.copyScissor(); is AnalyticRRectDraw -> draw.copyScissor(); is W5bPointDraw -> draw.copyScissorI32(); else -> error("Unadmitted W6b scissor") }
                val clip = if (phase == W6bCoverageRasterPhaseV1.StencilProducer || scissor == RectI32(0, 0, extent.width, extent.height)) W6bCoverageRasterClipV1.None else W6bCoverageRasterClipV1.Scissor
                val rasterBounds = when (val draw = binding.draw) {
                    is AnalyticRectDraw -> draw.copyRasterBounds(); is AnalyticRRectDraw -> draw.copyRasterBounds()
                    is W5bPointDraw -> draw.copyBoundsI32(); is PathDraw -> draw.copyScissorI32(); else -> error("Unadmitted W6b raster bounds")
                }
                W6bCoverageRasterBundleHostRecipeV1(coverage.id, 0, bundle.bundleOrdinalI32, phase, role, topology, clip, family, uniformAbi,
                    W6bCoverageRasterTargetV1.Rgba8UnormSrgbSingleSample, coverage.output, binding.depthStencil, binding.draw.blend, stencil, fillRule, path?.copyScissorI32() ?: when (val draw = binding.draw) {
                        is AnalyticRectDraw -> draw.copyScissor(); is AnalyticRRectDraw -> draw.copyScissor(); is W5bPointDraw -> draw.copyScissorI32(); else -> error("Unadmitted W6b scissor") },
                    rasterBounds, (binding.draw as? W5bPointDraw)?.pointMode, path?.strategy,
                    bundle, uniform32, analytic)
            }
            require(result.put(coverage.id, W6bCoverageRasterHostRecipeV1(coverage.id, 0, bundles)) == null)
        }
    })
}
