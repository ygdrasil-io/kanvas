package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.math.color.ColorF32

/** Normalizes admitted W5 sources once, before a graph is published Ready. */
public object EffectiveMaterialPlanner {
    public sealed interface Result {
        public data class Ready(
            public val table: MaterialPlanTable,
            public val root: MaterialPlanRef,
            public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
        ) : Result
        public data class Refused(public val diagnosticCode: String) : Result
    }

    /** Callers without a sealed target fact intentionally retain no clamp capability. */
    public fun plan(draw: DrawNode): Result = plan(draw, BlendTargetClampV1.Unavailable)

    internal sealed interface Normalization {
        data object NoOp : Normalization
        data class Source(val table: MaterialPlanTable, val root: MaterialPlanRef, val blend: BlendPlan) : Normalization
        data class Refused(val diagnosticCode: String) : Normalization
    }

    /** Compatibility boundary: existing owners cannot promote destination-read draws. */
    public fun plan(draw: DrawNode, targetClamp: BlendTargetClampV1): Result =
        when (val source = normalize(draw, targetClamp)) {
            Normalization.NoOp -> error("Compatibility normalization cannot elide draws")
            is Normalization.Refused -> Result.Refused(source.diagnosticCode)
            is Normalization.Source -> if (source.blend is BlendPlan.DestinationReadV1) {
                Result.Refused("unsupported.w5b.destination-read.task-2")
            } else Result.Ready(source.table, source.root, source.blend)
        }

    /**
     * Normalizes an admitted W5 source and final blend together. Destination-read candidates and
     * `DST` remain plans so prepared-family bridges can carry them to the frame sealer unchanged.
     */
    public fun planW5b(
        draw: DrawNode,
        targetClamp: BlendTargetClampV1,
        coverage: CoveragePlan = CoveragePlan.FullOrScissor,
        sample: SamplePlan = SamplePlan.SingleSample,
    ): Result = when (
        val source = normalize(
            draw = draw,
            targetClamp = targetClamp,
            allowDestinationCandidate = true,
            coverage = coverage,
            sample = sample,
            elideNoOp = false,
        )
    ) {
        Normalization.NoOp -> error("W5b source normalization must retain NoOp authority")
        is Normalization.Refused -> Result.Refused(source.diagnosticCode)
        is Normalization.Source -> Result.Ready(source.table, source.root, source.blend)
    }

    internal fun normalize(draw: DrawNode, targetClamp: BlendTargetClampV1, allowDestinationCandidate: Boolean = false,
        coverage: CoveragePlan = CoveragePlan.FullOrScissor, sample: SamplePlan = SamplePlan.SingleSample,
        elideNoOp: Boolean = true): Normalization {
        val blend = FinalBlendPlanner.plan(draw.blend, coverage, sample, targetClamp,
            if (coverage == CoveragePlan.AnalyticScalarAA) BlendCoverageApplicationV1.SourceMultiplication
            else BlendCoverageApplicationV1.DestinationInterpolation)
            ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        if (blend is BlendPlan.DestinationReadV1 && !allowDestinationCandidate) {
            return Normalization.Refused("unsupported.w5b.destination-read.task-2")
        }
        if (allowDestinationCandidate && elideNoOp && blend == BlendPlan.NoOpV1) return Normalization.NoOp
        if (draw.effects !is EffectStack.Empty || draw.resource != null || draw.operationBlendMode != null) {
            return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        }
        var material = draw.material
        val opacityInnerToOuter = mutableListOf<Float>()
        var visited = 0
        while (material is MaterialNode.Opacity) {
            if (++visited > 64 || !material.alpha.isFinite() || material.alpha !in 0f..1f) {
                return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
            }
            opacityInnerToOuter += material.alpha
            material = material.material
        }
        val base = when (material) {
            MaterialNode.Transparent -> return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
            is MaterialNode.Solid -> MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(
                    material.color.redNormalized,
                    material.color.greenNormalized,
                    material.color.blueNormalized,
                    material.color.alphaNormalized,
                )),
            )
            is MaterialNode.LinearGradient -> {
                if (material.tileMode != org.graphiks.kanvas.render.ir.TileMode.CLAMP ||
                    material.interpolation != org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB ||
                    draw.origin != org.graphiks.kanvas.render.ir.DrawOrigin.RECT)
                    return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                when (val stops = normalizeGradientStopsV1(material.stops())) {
                    is NormalizedGradientStopsV1.Refused -> return Normalization.Refused(stops.code)
                    is NormalizedGradientStopsV1.Solid -> MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                        MaterialBindingPlan.SolidRgbaF32V1.of(stops.colorF32))
                    is NormalizedGradientStopsV1.Stops -> {
                        val coordinates = MaterialCoordinatePlanV1.fromCtm(draw.transform)
                            ?: return Normalization.Refused(W5cPlanDiagnostics.CoordinatesUnavailable)
                        val inverseF32 = coordinates.copyInverseCtmF32()
                        val bounds = (draw.geometry as? org.graphiks.kanvas.render.ir.GeometryNode.Rect)?.copyBounds()
                            ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                        val valuesF32 = listOf(material.start.x, material.start.y, material.end.x, material.end.y,
                            bounds.left, bounds.top, bounds.right, bounds.bottom)
                        // Include the inverse-mapped raster footprint in the finite local domain.
                        val radiusF64 = (kotlin.math.abs(inverseF32.sx.toDouble()) + kotlin.math.abs(inverseF32.kx.toDouble()) +
                            kotlin.math.abs(inverseF32.ky.toDouble()) + kotlin.math.abs(inverseF32.sy.toDouble())) * 2.0
                        val deviceCorners = listOf(org.graphiks.math.geometry.Point2F32(bounds.left, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.left, bounds.bottom),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.bottom)).map(draw.transform::transform)
                        val deviceXF64 = deviceCorners.maxOf { kotlin.math.abs(it.x.toDouble()) } + 2.0
                        val deviceYF64 = deviceCorners.maxOf { kotlin.math.abs(it.y.toDouble()) } + 2.0
                        val mappingBoundF64 = maxOf(
                            kotlin.math.abs(inverseF32.sx.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.kx.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.tx.toDouble()),
                            kotlin.math.abs(inverseF32.ky.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.sy.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.ty.toDouble()),
                        ) * 1.00001
                        if (valuesF32.any { !it.isFinite() || kotlin.math.abs(it.toDouble()) + radiusF64 > 1e8 } ||
                            !mappingBoundF64.isFinite() || mappingBoundF64 > 1e8 ||
                            inverseF32.persp0 != 0f || inverseF32.persp1 != 0f || inverseF32.persp2 != 1f)
                            return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        val dxF32 = material.end.x - material.start.x
                        val dyF32 = material.end.y - material.start.y
                        val lengthSquaredF32 = dxF32 * dxF32 + dyF32 * dyF32
                        val degeneracy = LinearGradientDegeneracyV1(dxF32, dyF32, lengthSquaredF32,
                            kotlin.math.sqrt(lengthSquaredF32) <= 0.000030517578125f)
                        val numericAuthority = LinearGradientNumericAuthorityV1.seal(coordinates, material.start, material.end,
                            degeneracy, stops.slab, mappingBoundF64, valuesF32.take(4).maxOf { kotlin.math.abs(it.toDouble()) })
                            ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        MaterialPlanEntry(MaterialProgramPlan.LinearGradientClampSrgbV1,
                            MaterialBindingPlan.LinearGradientV1(material.start, material.end,
                                GradientStopRangeV1(0u, stops.slab.copyStops().size.toUInt()), degeneracy, numericAuthority), stops.slab)
                    }
                }
            }
            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        }
        val shaderAlpha = opacityInnerToOuter.asReversed().fold(1f) { accumulated, alpha -> accumulated * alpha }
        val paintAlpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
        if (!shaderAlpha.isFinite() || shaderAlpha !in 0f..1f || !paintAlpha.isFinite() || paintAlpha !in 0f..1f) {
            return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
        }
        if (shaderAlpha == 0f || paintAlpha == 0f) {
            return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
        }
        val entries = mutableListOf(base)
        if (shaderAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(paintAlpha),
            )
        }
        return Normalization.Source(MaterialPlanTable.of(entries), MaterialPlanRef(entries.lastIndex), blend)
    }
}
