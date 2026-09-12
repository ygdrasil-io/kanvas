package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/** Scalar range proof for all reassociations/contractions of the emitted homogeneous rows. */
internal fun MaterialCoordinatePlanV2.proveCoordinateDomainF64(deviceBoundsF32: RectF32): Double? {
    if (!deviceBoundsF32.isFinite() || !deviceBoundsF32.isSorted()) return null
    var finite = true
    fun magnitude(range: ClosedFloatingPointRange<Double>): Double = maxOf(kotlin.math.abs(range.start), kotlin.math.abs(range.endInclusive))
    fun rounded(lowerF64: Double, upperF64: Double): ClosedFloatingPointRange<Double> {
        // Outward F64 endpoints plus a full F32 ULP cover rounding, and MIN_NORMAL covers FTZ.
        val errorF64 = maxOf(kotlin.math.abs(lowerF64), kotlin.math.abs(upperF64)) * Math.scalb(1.0, -23) + java.lang.Float.MIN_NORMAL
        val lower = Math.nextDown(lowerF64 - errorF64)
        val upper = Math.nextUp(upperF64 + errorF64)
        finite = finite && lower.isFinite() && upper.isFinite() &&
            maxOf(kotlin.math.abs(lower), kotlin.math.abs(upper)) <= Float.MAX_VALUE.toDouble()
        return lower..upper
    }
    fun input(valueF32: Float): ClosedFloatingPointRange<Double> {
        val valueF64 = valueF32.toDouble()
        return if (kotlin.math.abs(valueF64) < java.lang.Float.MIN_NORMAL) minOf(0.0, valueF64)..maxOf(0.0, valueF64)
            else valueF64..valueF64
    }
    fun flushed(range: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> =
        if (range.start < java.lang.Float.MIN_NORMAL && range.endInclusive > -java.lang.Float.MIN_NORMAL)
            minOf(0.0, range.start)..maxOf(0.0, range.endInclusive) else range
    fun product(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> {
        val values = listOf(a.start * b.start, a.start * b.endInclusive, a.endInclusive * b.start, a.endInclusive * b.endInclusive)
        return values.min()..values.max()
    }
    fun add(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> =
        rounded(a.start + b.start, a.endInclusive + b.endInclusive)
    fun mul(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> =
        product(a, b).let { rounded(it.start, it.endInclusive) }
    fun fma(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>, c: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> =
        product(a, b).let { rounded(it.start + c.start, it.endInclusive + c.endInclusive) }
    var x = deviceBoundsF32.left.toDouble()..deviceBoundsF32.right.toDouble()
    var y = deviceBoundsF32.top.toDouble()..deviceBoundsF32.bottom.toDouble()
    fun row(aF32: Float, bF32: Float, cF32: Float): ClosedFloatingPointRange<Double> {
        val a = input(aF32); val b = input(bF32); val c = input(cF32)
        val ax = mul(a, x); val by = mul(b, y)
        val schedules = listOf(add(add(ax, by), c), add(ax, add(by, c)), add(add(ax, c), by),
            add(fma(a, x, c), by), add(fma(b, y, c), ax),
            add(fma(a, x, by), c), add(fma(b, y, ax), c), fma(a, x, add(by, c)), fma(b, y, add(ax, c)),
            fma(a, x, fma(b, y, c)), fma(b, y, fma(a, x, c)))
        return schedules.minOf { it.start }..schedules.maxOf { it.endInclusive }
    }
    fun quotient(n: ClosedFloatingPointRange<Double>, w: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> {
        val values = listOf(n.start / w.start, n.start / w.endInclusive, n.endInclusive / w.start, n.endInclusive / w.endInclusive)
        // The normal fraction division has the WGSL division error; exponent scaling is exact
        // for normals. Enclose both surviving and flushed subnormal results.
        val errorF64 = values.maxOf { kotlin.math.abs(it) } * Math.scalb(1.0, -20) + java.lang.Float.MIN_NORMAL
        return rounded(values.min() - errorF64, values.max() + errorF64)
    }
    val operations = copyOperations()
    for ((indexI32, operation) in operations.withIndex()) when (operation) {
        is MaterialCoordinateOperationV2.ClampRectF32 -> {
            val subsetF32 = operation.subsetF32
            // This also closes a cross-zero quotient. No matrix may consume that open range.
            x = flushed(subsetF32.left.toDouble()..subsetF32.right.toDouble())
            y = flushed(subsetF32.top.toDouble()..subsetF32.bottom.toDouble())
        }
        is MaterialCoordinateOperationV2.InverseMatrixF32 -> {
            val matrixF32 = operation.inverseF32
            val hx = row(matrixF32.sx, matrixF32.kx, matrixF32.tx)
            val hy = row(matrixF32.ky, matrixF32.sy, matrixF32.ty)
            val hw = row(matrixF32.persp0, matrixF32.persp1, matrixF32.persp2)
            if (!finite) return null
            if (matrixF32.persp0 == 0f && matrixF32.persp1 == 0f && matrixF32.persp2 == 1f) {
                x = hx
                y = hy
            } else if (hw.start <= 0.0 && hw.endInclusive >= 0.0) {
                if (operations.getOrNull(indexI32 + 1) !is MaterialCoordinateOperationV2.ClampRectF32) return null
            } else {
                x = quotient(hx, hw)
                y = quotient(hy, hw)
                if (!finite) return null
            }
        }
    }
    return maxOf(magnitude(x), magnitude(y)).takeIf { it.isFinite() }
}

public data class GradientAddressingProgramV2(
    public val family: GradientFamilyV2,
    public val requestedTileMode: GradientTileModeV2,
    public val effectiveTileMode: GradientTileModeV2,
    public val tileGraphId: String,
    public val coordinateTopologyId: String,
) : MaterialProgramPlan {
    override val versionI32: Int = 2
    override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId(
        "w5d-gradient-v2:${family.name}:${requestedTileMode.name}:${effectiveTileMode.name}:$tileGraphId:$coordinateTopologyId",
    )
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
}

/** V2 proof cannot be cast to V1; it binds the complete coordinate and tile authorities. */
public class GradientNumericAuthorityV2 private constructor(
    public val graph: GradientNumericOperationGraphV1,
    public val tileGraph: GradientTileOperationGraphV2,
    private val program: GradientAddressingProgramV2,
    public val coordinates: MaterialCoordinatePlanV2,
    uniformValuesF32: List<Float>,
    private val degeneracy: GradientDegeneracyV1,
    private val range: GradientStopRangeV1,
    private val slabIdentity: String,
    private val localMagnitudeF64: Double,
    private val uniformMagnitudeF64: Double,
) {
    private val uniformValuesF32 = immutableList(uniformValuesF32)
    internal val domainIdentity: String = "gradient-domain-v2:${program.structuralId.value}:${graph.contractId}:" +
        "${this.uniformValuesF32}:$degeneracy:${coordinates.canonicalIdentity}:${tileGraph.contractId}:" +
        "${localMagnitudeF64.toBits()}:${uniformMagnitudeF64.toBits()}"
    public val canonicalIdentity: String = "$domainIdentity:$range:$slabIdentity"
    public fun authenticates(program: GradientAddressingProgramV2, binding: MaterialBindingPlan.GradientV2,
        slab: GradientStopSlabPlanV1, coordinates: MaterialCoordinatePlanV2): Boolean =
        program == this.program && graph.contractId == "WgslFloatEnvelopeV1" &&
            graph.domainProof == GradientNumericDomainProofV1.ProvenFinite && this.coordinates == coordinates &&
            tileGraph == program.requestedTileMode.operationGraph() &&
            tileGraph.effectiveMode == program.effectiveTileMode && binding.copyUniformValuesF32() == uniformValuesF32 &&
            when (binding) {
                is MaterialBindingPlan.LinearGradientV2 -> binding.degeneracy == degeneracy
                is MaterialBindingPlan.RadialGradientV2 -> binding.degeneracy == degeneracy
                is MaterialBindingPlan.SweepGradientV2 -> binding.degeneracy == degeneracy
                is MaterialBindingPlan.ConicalGradientV2 -> binding.degeneracy == degeneracy
            } &&
            binding.stopRange == range && slab.canonicalIdentity == slabIdentity

    internal fun rebase(binding: MaterialBindingPlan.GradientV2, sourceSlab: GradientStopSlabPlanV1,
        newRange: GradientStopRangeV1, newSlab: GradientStopSlabPlanV1): GradientNumericAuthorityV2 {
        require(authenticates(program, binding, sourceSlab, coordinates)) { W5dPlanDiagnostics.CoordinatePlanSchema }
        fun sequence(slab: GradientStopSlabPlanV1, selected: GradientStopRangeV1): List<GradientStopPlanV1> =
            slab.copyStops().subList(selected.baseIndexU32.toInt(), (selected.baseIndexU32 + selected.countU32).toInt())
        require(sequence(sourceSlab, range) == sequence(newSlab, newRange)) { W5dPlanDiagnostics.CoordinatePlanSchema }
        return GradientNumericAuthorityV2(graph, tileGraph, program, coordinates, uniformValuesF32, degeneracy,
            newRange, newSlab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
    }
    internal companion object {
        private fun matches(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2,
            family: GradientFamilyV2, tile: GradientTileOperationGraphV2): Boolean =
            program.family == family && program.effectiveTileMode == tile.effectiveMode &&
                program.tileGraphId == tile.contractId && program.coordinateTopologyId == coordinates.topologyIdentity

        fun sealConical(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2, startF32: Point2F32, endF32: Point2F32,
            degeneracy: ConicalGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV2? {
            if (degeneracy != ConicalGradientDegeneracyV1.of(startF32, degeneracy.conicalStartRadiusF32,
                    endF32, degeneracy.conicalEndRadiusF32) || degeneracy.copyScalarsF32().any { !it.isFinite() } ||
                degeneracy.conicalStartRadiusF32 < 0f || degeneracy.conicalEndRadiusF32 < 0f) return null
            val tile = program.requestedTileMode.operationGraph()
            if (!matches(program, coordinates, GradientFamilyV2.CONICAL, tile)) return null
            val schema = GradientNumericOperationGraphV1.conical(tile)
            val stops = slab.copyStops()
            val proof = schema.proveConicalDomainV1(localMagnitudeF64, startF32, endF32, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV2(GradientNumericOperationGraphV1.Conical(schema.root, proof),
                tile, program, coordinates,
                listOf(startF32.x, startF32.y, endF32.x, endF32.y), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

        fun sealSweep(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2, centerF32: Point2F32,
            degeneracy: SweepGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV2? {
            if (degeneracy != SweepGradientDegeneracyV1.of(degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32) ||
                degeneracy.sweepOrderingInvalid || listOf(degeneracy.startAngleDegreesF32,
                    degeneracy.endAngleDegreesF32, degeneracy.sweepSpanDegreesF32).any { !it.isFinite() }) return null
            val tile = program.requestedTileMode.operationGraph()
            if (!matches(program, coordinates, GradientFamilyV2.SWEEP, tile) ||
                (degeneracy.sweepFullCoverage && tile.effectiveMode != GradientTileModeV2.CLAMP)) return null
            val schema = GradientNumericOperationGraphV1.sweep(tile)
            val stops = slab.copyStops()
            val proof = schema.proveSweepDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV2(GradientNumericOperationGraphV1.Sweep(schema.root, proof),
                tile, program, coordinates,
                listOf(centerF32.x, centerF32.y, degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

        fun sealRadial(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2, centerF32: Point2F32, radiusF32: Float,
            degeneracy: RadialGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV2? {
            if (!radiusF32.isFinite() || radiusF32 < 0f || degeneracy !=
                RadialGradientDegeneracyV1(radiusF32, radiusF32 <= 0.000030517578125f)) return null
            val tile = program.requestedTileMode.operationGraph()
            if (!matches(program, coordinates, GradientFamilyV2.RADIAL, tile)) return null
            val schema = GradientNumericOperationGraphV1.radial(tile)
            val stops = slab.copyStops()
            val proof = schema.proveRadialDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV2(GradientNumericOperationGraphV1.Radial(schema.root, proof),
                tile, program, coordinates,
                listOf(centerF32.x, centerF32.y, radiusF32, 0f), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

        fun sealLinear(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2,
            startF32: Point2F32, endF32: Point2F32, degeneracy: LinearGradientDegeneracyV1,
            slab: GradientStopSlabPlanV1, localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV2? {
            val tile = program.requestedTileMode.operationGraph()
            if (program.family != GradientFamilyV2.LINEAR || program.requestedTileMode != tile.requestedMode ||
                program.effectiveTileMode != tile.effectiveMode || program.tileGraphId != tile.contractId ||
                program.coordinateTopologyId != coordinates.topologyIdentity ||
                degeneracy != LinearGradientDegeneracyV1.of(startF32, endF32) ||
                degeneracy.copyScalarsF32().any { !it.isFinite() }) return null
            val schema = GradientNumericOperationGraphV1.linear(tile)
            val stops = slab.copyStops()
            val proof = schema.proveLinearDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops, startF32, endF32)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV2(GradientNumericOperationGraphV1.Linear(schema.root, proof), tile,
                program, coordinates, listOf(startF32.x, startF32.y, endF32.x, endF32.y), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }
    }
}
