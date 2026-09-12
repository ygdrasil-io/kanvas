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
        gradientDeviceBoundsI32: org.graphiks.math.geometry.RectI32? = null,
    ): Result = when (
        val source = normalize(
            draw = draw,
            targetClamp = targetClamp,
            allowDestinationCandidate = true,
            coverage = coverage,
            sample = sample,
            elideNoOp = false,
            gradientDeviceBoundsI32 = gradientDeviceBoundsI32,
        )
    ) {
        Normalization.NoOp -> error("W5b source normalization must retain NoOp authority")
        is Normalization.Refused -> Result.Refused(source.diagnosticCode)
        is Normalization.Source -> Result.Ready(source.table, source.root, source.blend)
    }

    internal fun normalize(draw: DrawNode, targetClamp: BlendTargetClampV1, allowDestinationCandidate: Boolean = false,
        coverage: CoveragePlan = CoveragePlan.FullOrScissor, sample: SamplePlan = SamplePlan.SingleSample,
        elideNoOp: Boolean = true,
        gradientDeviceBoundsI32: org.graphiks.math.geometry.RectI32? = null): Normalization {
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
            is MaterialNode.LinearGradient, is MaterialNode.RadialGradient, is MaterialNode.SweepGradient, is MaterialNode.ConicalGradient -> {
                val linear = material as? MaterialNode.LinearGradient
                val radial = material as? MaterialNode.RadialGradient
                val sweep = material as? MaterialNode.SweepGradient
                val conical = material as? MaterialNode.ConicalGradient
                val tileMode = linear?.tileMode ?: radial?.tileMode ?: sweep?.tileMode ?: requireNotNull(conical).tileMode
                val interpolation = linear?.interpolation ?: radial?.interpolation ?: sweep?.interpolation ?: requireNotNull(conical).interpolation
                if (conical != null && listOf(conical.start.x, conical.start.y, conical.end.x, conical.end.y,
                        conical.startRadius, conical.endRadius).any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                if (conical != null && (conical.startRadius < 0f || conical.endRadius < 0f))
                    return Normalization.Refused(W5cPlanDiagnostics.NegativeRadius)
                val conicalDegeneracy = conical?.let { ConicalGradientDegeneracyV1.of(it.start, it.startRadius, it.end, it.endRadius) }
                if (conicalDegeneracy != null && conicalDegeneracy.copyScalarsF32().any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                if (sweep != null && listOf(sweep.center.x, sweep.center.y, sweep.startAngle, sweep.endAngle).any { !it.isFinite() })
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                val sweepDegeneracy = sweep?.let { SweepGradientDegeneracyV1.of(it.startAngle, it.endAngle) }
                if (sweepDegeneracy?.sweepOrderingInvalid == true) return Normalization.Refused(W5cPlanDiagnostics.SweepOrdering)
                if (sweepDegeneracy != null && !sweepDegeneracy.sweepSpanDegreesF32.isFinite())
                    return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                if (radial != null && (!radial.radius.isFinite() || !radial.center.x.isFinite() || !radial.center.y.isFinite()))
                    return Normalization.Refused(W5cPlanDiagnostics.NonFinite)
                if (radial != null && radial.radius < 0f)
                    return Normalization.Refused(W5cPlanDiagnostics.NegativeRadius)
                if (tileMode != org.graphiks.kanvas.render.ir.TileMode.CLAMP ||
                    interpolation != org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB ||
                    draw.origin !in setOf(org.graphiks.kanvas.render.ir.DrawOrigin.RECT,
                        org.graphiks.kanvas.render.ir.DrawOrigin.RRECT, org.graphiks.kanvas.render.ir.DrawOrigin.PATH))
                    return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                when (val stops = normalizeGradientStopsV1(linear?.stops() ?: radial?.stops() ?: sweep?.stops()
                    ?: requireNotNull(conical).stops(), preserveValidityMask = conical != null)) {
                    is NormalizedGradientStopsV1.Refused -> return Normalization.Refused(stops.code)
                    is NormalizedGradientStopsV1.Solid -> MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
                        MaterialBindingPlan.SolidRgbaF32V1.of(stops.colorF32))
                    is NormalizedGradientStopsV1.Stops -> {
                        val coordinates = MaterialCoordinatePlanV1.fromCtm(draw.transform)
                            ?: return Normalization.Refused(W5cPlanDiagnostics.CoordinatesUnavailable)
                        val inverseF32 = coordinates.copyInverseCtmF32()
                        val bounds = when (val geometry = draw.geometry) {
                            is org.graphiks.kanvas.render.ir.GeometryNode.Rect -> geometry.copyBounds()
                            is org.graphiks.kanvas.render.ir.GeometryNode.RRect -> geometry.copyShape().rect
                            is org.graphiks.kanvas.render.ir.GeometryNode.Path -> null
                            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                        }
                        if (bounds == null && (gradientDeviceBoundsI32 == null || gradientDeviceBoundsI32.isEmpty))
                            return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        val uniformValuesF32 = if (linear != null) listOf(linear.start.x, linear.start.y, linear.end.x, linear.end.y)
                            else if (radial != null) listOf(radial.center.x, radial.center.y, radial.radius, 0f)
                            else if (conical != null) listOf(conical.start.x, conical.start.y, conical.end.x, conical.end.y) +
                                requireNotNull(conicalDegeneracy).copyScalarsF32()
                            else requireNotNull(sweep).let { listOf(it.center.x, it.center.y, it.startAngle, it.endAngle,
                                requireNotNull(sweepDegeneracy).sweepSpanDegreesF32) }
                        val valuesF32 = uniformValuesF32 + listOf(
                            bounds?.left ?: 0f, bounds?.top ?: 0f, bounds?.right ?: 0f, bounds?.bottom ?: 0f)
                        // Include the inverse-mapped raster footprint in the finite local domain.
                        val radiusF64 = (kotlin.math.abs(inverseF32.sx.toDouble()) + kotlin.math.abs(inverseF32.kx.toDouble()) +
                            kotlin.math.abs(inverseF32.ky.toDouble()) + kotlin.math.abs(inverseF32.sy.toDouble())) * 2.0
                        val deviceCorners = if (bounds == null) emptyList() else listOf(org.graphiks.math.geometry.Point2F32(bounds.left, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.top),
                            org.graphiks.math.geometry.Point2F32(bounds.left, bounds.bottom),
                            org.graphiks.math.geometry.Point2F32(bounds.right, bounds.bottom)).map(draw.transform::transform)
                        // W4 already owns the conservative stroke/hairline raster bounds. Use
                        // those facts directly; never rebuild an outline for material planning.
                        val deviceXF64 = maxOf(deviceCorners.maxOfOrNull { kotlin.math.abs(it.x.toDouble()) } ?: 0.0,
                            gradientDeviceBoundsI32?.let { maxOf(kotlin.math.abs(it.left.toDouble()), kotlin.math.abs(it.right.toDouble())) } ?: 0.0) + 2.0
                        val deviceYF64 = maxOf(deviceCorners.maxOfOrNull { kotlin.math.abs(it.y.toDouble()) } ?: 0.0,
                            gradientDeviceBoundsI32?.let { maxOf(kotlin.math.abs(it.top.toDouble()), kotlin.math.abs(it.bottom.toDouble())) } ?: 0.0) + 2.0
                        val mappingBoundF64 = maxOf(
                            kotlin.math.abs(inverseF32.sx.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.kx.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.tx.toDouble()),
                            kotlin.math.abs(inverseF32.ky.toDouble()) * deviceXF64 + kotlin.math.abs(inverseF32.sy.toDouble()) * deviceYF64 + kotlin.math.abs(inverseF32.ty.toDouble()),
                        ) * 1.00001
                        if (valuesF32.any { !it.isFinite() || kotlin.math.abs(it.toDouble()) + radiusF64 > 1e8 } ||
                            !mappingBoundF64.isFinite() || mappingBoundF64 > 1e8 ||
                            inverseF32.persp0 != 0f || inverseF32.persp1 != 0f || inverseF32.persp2 != 1f)
                            return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                        val stopRange = GradientStopRangeV1(0u, stops.slab.copyStops().size.toUInt())
                        val uniformMagnitudeF64 = uniformValuesF32.maxOf { kotlin.math.abs(it.toDouble()) }
                        if (conical != null) {
                            val degeneracy = requireNotNull(conicalDegeneracy)
                            val numericAuthority = GradientNumericAuthorityV1.sealConical(coordinates, conical.start, conical.end,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.ConicalGradientClampSrgbV1,
                                MaterialBindingPlan.ConicalGradientV1(conical.start, conical.end, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else if (sweep != null) {
                            val degeneracy = requireNotNull(sweepDegeneracy)
                            val numericAuthority = GradientNumericAuthorityV1.sealSweep(coordinates, sweep.center, degeneracy,
                                stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.SweepGradientClampSrgbV1,
                                MaterialBindingPlan.SweepGradientV1(sweep.center, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else if (radial != null) {
                            val degeneracy = RadialGradientDegeneracyV1(radial.radius, radial.radius <= 0.000030517578125f)
                            val numericAuthority = GradientNumericAuthorityV1.sealRadial(coordinates, radial.center, radial.radius,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.RadialGradientClampSrgbV1,
                                MaterialBindingPlan.RadialGradientV1(radial.center, radial.radius, stopRange, degeneracy, numericAuthority), stops.slab)
                        } else {
                            requireNotNull(linear)
                            val degeneracy = LinearGradientDegeneracyV1.of(linear.start, linear.end)
                            val numericAuthority = GradientNumericAuthorityV1.sealLinear(coordinates, linear.start, linear.end,
                                degeneracy, stops.slab, mappingBoundF64, uniformMagnitudeF64)
                                ?: return Normalization.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(MaterialProgramPlan.LinearGradientClampSrgbV1,
                                MaterialBindingPlan.LinearGradientV1(linear.start, linear.end, stopRange, degeneracy, numericAuthority), stops.slab)
                        }
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
