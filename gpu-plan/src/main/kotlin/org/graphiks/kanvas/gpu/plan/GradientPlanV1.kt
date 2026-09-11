package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.GradientStop
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2F32

public data class GradientStopPlanV1(public val positionF32: Float, public val straightSrgbF32: ColorF32)
public data class GradientStopRangeV1(public val baseIndexU32: UInt, public val countU32: UInt) {
    init { require(countU32 in 2u..65_538u && baseIndexU32.toLong() + countU32.toLong() <= UInt.MAX_VALUE.toLong()) }
}

/** The sole immutable stop snapshot shared by every material in a frame. */
public class GradientStopSlabPlanV1 private constructor(stops: List<GradientStopPlanV1>) {
    private val storedStops = immutableList(stops)
    public fun copyStops(): List<GradientStopPlanV1> = storedStops.toList()
    public val byteSizeI64: Long = Math.multiplyExact(storedStops.size.toLong(), 32L)
    public val canonicalIdentity: String = storedStops.joinToString(";") { stop ->
        val color = stop.straightSrgbF32
        listOf(stop.positionF32, color.red, color.green, color.blue, color.alpha).joinToString(",") { it.toBits().toString() }
    }
    public companion object {
        public fun of(stops: List<GradientStopPlanV1>): GradientStopSlabPlanV1 {
            require(stops.size.toLong() <= UInt.MAX_VALUE.toLong() && stops.size.toLong() * 32L <= Int.MAX_VALUE)
            require(stops.all { stop -> stop.positionF32.isFinite() && stop.positionF32 in 0f..1f &&
                stop.straightSrgbF32.let { color -> listOf(color.red, color.green, color.blue, color.alpha)
                    .all { it.isFinite() && it in 0f..1f } } })
            return GradientStopSlabPlanV1(stops)
        }
    }
}

public sealed interface GradientDegeneracyV1
public data class LinearGradientDegeneracyV1(public val axisXF32: Float, public val axisYF32: Float,
    public val lengthSquaredF32: Float, public val degenerate: Boolean) : GradientDegeneracyV1

public data class RadialGradientDegeneracyV1(public val radialRadiusF32: Float, public val radialDegenerate: Boolean) : GradientDegeneracyV1

public data class SweepGradientDegeneracyV1(
    public val startAngleDegreesF32: Float, public val endAngleDegreesF32: Float,
    public val sweepSpanDegreesF32: Float, public val sweepOrderingInvalid: Boolean,
    public val sweepDegenerate: Boolean, public val sweepClampLeadingSegment: Boolean,
    public val sweepFullCoverage: Boolean,
) : GradientDegeneracyV1 {
    internal companion object {
        fun of(startAngleDegreesF32: Float, endAngleDegreesF32: Float): SweepGradientDegeneracyV1 {
            val sweepSpanDegreesF32 = endAngleDegreesF32 - startAngleDegreesF32
            val sweepOrderingInvalid = startAngleDegreesF32 > endAngleDegreesF32
            val sweepDegenerate = sweepSpanDegreesF32 <= 0.000030517578125f
            val sweepClampLeadingSegment = sweepDegenerate && endAngleDegreesF32 > 0.000030517578125f
            val sweepFullCoverage = startAngleDegreesF32 <= 0f && endAngleDegreesF32 >= 360f
            return SweepGradientDegeneracyV1(startAngleDegreesF32, endAngleDegreesF32, sweepSpanDegreesF32,
                sweepOrderingInvalid, sweepDegenerate, sweepClampLeadingSegment, sweepFullCoverage)
        }
    }
}

/** Value-dependent proof shared by admitted families, transported only after exact stop-range validation. */
public class GradientNumericAuthorityV1 private constructor(
    public val graph: GradientNumericOperationGraphV1,
    private val program: MaterialProgramPlan,
    private val coordinates: MaterialCoordinatePlanV1,
    uniformValuesF32: List<Float>,
    private val degeneracy: GradientDegeneracyV1,
    private val range: GradientStopRangeV1,
    private val slabIdentity: String,
    private val localMagnitudeF64: Double,
    private val uniformMagnitudeF64: Double,
) {
    private val uniformValuesF32 = immutableList(uniformValuesF32)
    internal val domainIdentity: String = "gradient-domain-v1:${program.structuralId.value}:${graph.contractId}:" +
        "${this.uniformValuesF32}:$degeneracy:${coordinates.canonicalIdentity}:" +
        "${localMagnitudeF64.toBits()}:${uniformMagnitudeF64.toBits()}"
    public val canonicalIdentity: String = "$domainIdentity:$range:$slabIdentity"
    override fun toString(): String = canonicalIdentity

    public fun authenticates(program: MaterialProgramPlan, binding: MaterialBindingPlan.GradientV1,
        slab: GradientStopSlabPlanV1, coordinates: MaterialCoordinatePlanV1): Boolean =
        program.structuralId == this.program.structuralId && graph.contractId == "WgslFloatEnvelopeV1" &&
            graph.domainProof == GradientNumericDomainProofV1.ProvenFinite && this.coordinates == coordinates &&
            binding.copyUniformValuesF32() == uniformValuesF32 &&
            when (binding) {
                is MaterialBindingPlan.LinearGradientV1 -> program == MaterialProgramPlan.LinearGradientClampSrgbV1 &&
                    binding.degeneracy == degeneracy
                is MaterialBindingPlan.RadialGradientV1 -> program == MaterialProgramPlan.RadialGradientClampSrgbV1 &&
                    binding.degeneracy == degeneracy
                is MaterialBindingPlan.SweepGradientV1 -> program == MaterialProgramPlan.SweepGradientClampSrgbV1 &&
                    binding.degeneracy == degeneracy
            } && binding.stopRange == range && slab.canonicalIdentity == slabIdentity

    internal fun rebase(binding: MaterialBindingPlan.GradientV1, sourceSlab: GradientStopSlabPlanV1,
        newRange: GradientStopRangeV1, newSlab: GradientStopSlabPlanV1): GradientNumericAuthorityV1 {
        require(authenticates(program, binding, sourceSlab, coordinates)) { W5cPlanDiagnostics.NumericDomainUnbounded }
        fun sequence(slab: GradientStopSlabPlanV1, selected: GradientStopRangeV1): List<GradientStopPlanV1> =
            slab.copyStops().subList(selected.baseIndexU32.toInt(), (selected.baseIndexU32 + selected.countU32).toInt())
        require(sequence(sourceSlab, range) == sequence(newSlab, newRange)) { W5cPlanDiagnostics.NumericDomainUnbounded }
        return GradientNumericAuthorityV1(graph, program, coordinates, uniformValuesF32, degeneracy,
            newRange, newSlab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
    }

    internal companion object {
        fun sealSweep(coordinates: MaterialCoordinatePlanV1, centerF32: Point2F32,
            degeneracy: SweepGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV1? {
            if (degeneracy != SweepGradientDegeneracyV1.of(degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32) ||
                degeneracy.sweepOrderingInvalid || listOf(degeneracy.startAngleDegreesF32,
                    degeneracy.endAngleDegreesF32, degeneracy.sweepSpanDegreesF32).any { !it.isFinite() }) return null
            val schema = GradientNumericOperationGraphV1.sweep()
            val stops = slab.copyStops()
            val proof = schema.proveSweepDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV1(GradientNumericOperationGraphV1.Sweep(schema.root, proof),
                MaterialProgramPlan.SweepGradientClampSrgbV1, coordinates,
                listOf(centerF32.x, centerF32.y, degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

        fun sealLinear(coordinates: MaterialCoordinatePlanV1, startF32: Point2F32, endF32: Point2F32,
            degeneracy: LinearGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV1? {
            val schema = MaterialProgramPlan.LinearGradientClampSrgbV1.copyGradientNumericOperationGraphV1()
            val stops = slab.copyStops()
            val proof = schema.proveLinearDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops, startF32, endF32)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV1(GradientNumericOperationGraphV1.Linear(schema.root, proof),
                MaterialProgramPlan.LinearGradientClampSrgbV1, coordinates,
                listOf(startF32.x, startF32.y, endF32.x, endF32.y), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

        fun sealRadial(coordinates: MaterialCoordinatePlanV1, centerF32: Point2F32, radiusF32: Float,
            degeneracy: RadialGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV1? {
            if (!radiusF32.isFinite() || radiusF32 < 0f || degeneracy !=
                RadialGradientDegeneracyV1(radiusF32, radiusF32 <= 0.000030517578125f)) return null
            val schema = MaterialProgramPlan.RadialGradientClampSrgbV1.copyGradientNumericOperationGraphV1()
            val stops = slab.copyStops()
            val proof = schema.proveRadialDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV1(GradientNumericOperationGraphV1.Radial(schema.root, proof),
                MaterialProgramPlan.RadialGradientClampSrgbV1, coordinates,
                listOf(centerF32.x, centerF32.y, radiusF32, 0f), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }
    }
}

internal fun GradientStopSlabPlanV1.requireStorageCapabilities(capabilities: PlanCapabilitySnapshot) {
    require(capabilities.supportedOperations().containsAll(setOf(PlanOperationCapability.StorageBuffer,
        PlanOperationCapability.CopyUpload)) && capabilities.maxStorageBuffersPerShaderStageI32?.let { it >= 1 } == true &&
        capabilities.maxBindingsPerBindGroupI32?.let { it >= 2 } == true &&
        capabilities.maxBindGroupsI32?.let { it >= 2 } == true &&
        capabilities.maxStorageBufferBindingSizeBytesI64 != null) { W5cPlanDiagnostics.StorageUnavailable }
    require(byteSizeI64 <= capabilities.maxBufferSizeBytes &&
        byteSizeI64 <= requireNotNull(capabilities.maxStorageBufferBindingSizeBytesI64)) { W5cPlanDiagnostics.StopBudget }
}

internal sealed interface NormalizedGradientStopsV1 {
    data class Refused(val code: String) : NormalizedGradientStopsV1
    data class Solid(val colorF32: ColorF32) : NormalizedGradientStopsV1
    data class Stops(val slab: GradientStopSlabPlanV1) : NormalizedGradientStopsV1
}

internal fun normalizeGradientStopsV1(input: List<GradientStop>): NormalizedGradientStopsV1 {
    if (input.isEmpty()) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.EmptyStops)
    if (input.any { !it.position.isFinite() }) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.NonFinite)
    if ((input.size.toLong() + 2L) * 32L > Int.MAX_VALUE) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.StopBudget)
    fun canonicalF32(valueF32: Float): Float = if (valueF32 == 0f) 0f else valueF32
    fun color(stop: GradientStop): ColorF32 = stop.color.let {
        ColorF32.of(it.redNormalized, it.greenNormalized, it.blueNormalized, it.alphaNormalized)
    }
    if (input.size == 1) return NormalizedGradientStopsV1.Solid(color(input.single()))
    val normalized = ArrayList<GradientStopPlanV1>(input.size + 2)
    var previousF32 = 0f
    for (stop in input) {
        val positionF32 = canonicalF32(maxOf(previousF32, stop.position.coerceIn(0f, 1f)))
        val value = GradientStopPlanV1(positionF32, color(stop))
        if (normalized.size >= 2 && normalized[normalized.lastIndex - 1].positionF32 == positionF32)
            normalized[normalized.lastIndex] = value
        else normalized += value
        previousF32 = positionF32
    }
    if (normalized.first().positionF32 > 0f) normalized.add(0, normalized.first().copy(positionF32 = 0f))
    if (normalized.last().positionF32 < 1f) normalized.add(normalized.last().copy(positionF32 = 1f))
    // WGSL division must have a normal finite denominator on every interpolation branch.
    if (normalized.zipWithNext().any { (a, b) -> b.positionF32 != a.positionF32 &&
            b.positionF32 - a.positionF32 < java.lang.Float.MIN_NORMAL })
        return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
    return NormalizedGradientStopsV1.Stops(GradientStopSlabPlanV1.of(normalized))
}
