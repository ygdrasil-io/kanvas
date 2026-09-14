package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.GradientStop
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.kanvas.color.ColorInterpolationProgramV1
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2F32

public class GradientStopPlanV1 private constructor(public val positionF32: Float, public val straightSrgbF32: ColorF32,
    public val domain: ColorInterpolation, public val preparedTupleF32: ColorF32,
    public val preparationRecipeIdentity: String?) {
    /** Historical public constructor remains straight-sRGB only. */
    public constructor(positionF32: Float, straightSrgbF32: ColorF32) :
        this(positionF32, straightSrgbF32, ColorInterpolation.SRGB, straightSrgbF32, null)
    public operator fun component1(): Float = positionF32
    public operator fun component2(): ColorF32 = straightSrgbF32
    public fun copy(positionF32: Float = this.positionF32, straightSrgbF32: ColorF32 = this.straightSrgbF32): GradientStopPlanV1 {
        require(domain == ColorInterpolation.SRGB || straightSrgbF32 == this.straightSrgbF32) { W5fPlanDiagnostics.Schema }
        return if (domain == ColorInterpolation.SRGB) GradientStopPlanV1(positionF32, straightSrgbF32)
        else GradientStopPlanV1(positionF32, straightSrgbF32, domain, preparedTupleF32, preparationRecipeIdentity)
    }
    override fun equals(other: Any?): Boolean = other is GradientStopPlanV1 &&
        positionF32.toRawBits() == other.positionF32.toRawBits() && straightSrgbF32 == other.straightSrgbF32 &&
        domain == other.domain && preparedTupleF32 == other.preparedTupleF32 && preparationRecipeIdentity == other.preparationRecipeIdentity
    override fun hashCode(): Int = if (domain == ColorInterpolation.SRGB)
        31 * positionF32.hashCode() + straightSrgbF32.hashCode()
    else listOf(positionF32.toRawBits(), straightSrgbF32, domain, preparedTupleF32, preparationRecipeIdentity).hashCode()
    override fun toString(): String = "GradientStopPlanV1(positionF32=$positionF32, straightSrgbF32=$straightSrgbF32)" +
        if (domain == ColorInterpolation.SRGB) "" else ":$domain:$preparationRecipeIdentity:$preparedTupleF32"
    internal companion object {
        /** Called only by checked-frame stop preparation; no public signed-tuple constructor. */
        fun prepared(positionF32: Float, original: ColorF32, domain: ColorInterpolation,
            tuple: ColorF32, recipeIdentity: String): GradientStopPlanV1 {
            require(domain == ColorInterpolation.LINEAR || domain == ColorInterpolation.OKLAB) { W5fPlanDiagnostics.Schema }
            require(tuple.alpha.toRawBits() == original.alpha.toRawBits()) { W5fPlanDiagnostics.Schema }
            return GradientStopPlanV1(positionF32, original, domain, tuple, recipeIdentity)
        }
    }
}
public data class GradientStopRangeV1(public val baseIndexU32: UInt, public val countU32: UInt) {
    init { require(countU32 in 2u..65_538u && baseIndexU32.toLong() + countU32.toLong() <= UInt.MAX_VALUE.toLong()) }
}

/** The sole immutable stop snapshot shared by every material in a frame. */
public class GradientStopSlabPlanV1 private constructor(stops: List<GradientStopPlanV1>) {
    private val storedStops = immutableList(stops)
    public fun copyStops(): List<GradientStopPlanV1> = storedStops.toList()
    internal fun rangeHasDomain(range: GradientStopRangeV1, domain: ColorInterpolation): Boolean {
        val first = range.baseIndexU32.toLong()
        val last = first + range.countU32.toLong()
        return last <= storedStops.size.toLong() && (first.toInt() until last.toInt()).all {
            storedStops[it].domain == domain
        }
    }
    public val byteSizeI64: Long = Math.multiplyExact(storedStops.size.toLong(), 32L)
    public val canonicalIdentity: String = storedStops.joinToString(";") { stop ->
        val color = stop.straightSrgbF32
        val original = listOf(stop.positionF32, color.red, color.green, color.blue, color.alpha)
            .joinToString(",") { it.toBits().toString() }
        if (stop.domain == ColorInterpolation.SRGB) original else original + ":${stop.domain}:${stop.preparationRecipeIdentity}:" +
            stop.preparedTupleF32.let { listOf(it.red, it.green, it.blue, it.alpha) }
                .joinToString(",") { it.toRawBits().toString() }
    }
    public companion object {
        public fun of(stops: List<GradientStopPlanV1>): GradientStopSlabPlanV1 {
            require(stops.size.toLong() <= UInt.MAX_VALUE.toLong() && stops.size.toLong() * 32L <= Int.MAX_VALUE)
            require(stops.all { stop -> stop.positionF32.isFinite() && stop.positionF32 in 0f..1f &&
                stop.straightSrgbF32.let { color -> listOf(color.red, color.green, color.blue, color.alpha)
                    .all { it.isFinite() && it in 0f..1f } } })
            require(stops.all { stop ->
                when (stop.domain) {
                    ColorInterpolation.SRGB -> stop.preparedTupleF32 == stop.straightSrgbF32 && stop.preparationRecipeIdentity == null
                    ColorInterpolation.LINEAR, ColorInterpolation.OKLAB -> stop.preparedTupleF32.let { tuple ->
                        listOf(tuple.red, tuple.green, tuple.blue, tuple.alpha).all(Float::isFinite) &&
                            tuple.alpha.toRawBits() == stop.straightSrgbF32.alpha.toRawBits() &&
                            stop.preparationRecipeIdentity == if (stop.domain == ColorInterpolation.OKLAB)
                                ColorInterpolationProgramV1.OKLAB_RECIPE_VERSION else "linear-srgb-eotf-ordered-f32-v1"
                    }
                    else -> false
                }
            })
            return GradientStopSlabPlanV1(stops)
        }
    }
}

public sealed interface GradientDegeneracyV1

internal fun GradientDegeneracyV1.consumesAverage(effectiveTileMode: GradientTileModeV2): Boolean =
    effectiveTileMode in setOf(GradientTileModeV2.REPEAT, GradientTileModeV2.MIRROR) && when (this) {
        is LinearGradientDegeneracyV1 -> linearDegenerate
        is RadialGradientDegeneracyV1 -> radialDegenerate
        is SweepGradientDegeneracyV1 -> sweepDegenerate && !sweepOrderingInvalid && !sweepFullCoverage
        is ConicalGradientDegeneracyV1 -> conicalFullyDegenerate
    }
public data class LinearGradientDegeneracyV1(
    public val linearDxF32: Float, public val linearDyF32: Float,
    public val linearX2F32: Float, public val linearY2F32: Float,
    public val linearLen2F32: Float, public val linearLengthF32: Float,
    public val linearDegenerate: Boolean,
) : GradientDegeneracyV1 {
    public fun copyScalarsF32(): List<Float> = listOf(linearDxF32, linearDyF32,
        linearX2F32, linearY2F32, linearLen2F32, linearLengthF32)
    internal companion object {
        fun of(startF32: Point2F32, endF32: Point2F32): LinearGradientDegeneracyV1 {
            val dxF32 = endF32.x - startF32.x
            val dyF32 = endF32.y - startF32.y
            val x2F32 = dxF32 * dxF32
            val y2F32 = dyF32 * dyF32
            val len2F32 = x2F32 + y2F32
            val lengthF32 = kotlin.math.sqrt(len2F32)
            return LinearGradientDegeneracyV1(dxF32, dyF32, x2F32, y2F32,
                len2F32, lengthF32, lengthF32 <= 0.000030517578125f)
        }
    }
}

public data class RadialGradientDegeneracyV1(public val radialRadiusF32: Float, public val radialDegenerate: Boolean) : GradientDegeneracyV1

/** Fixed roundTiesToEven preflight from W5 §7.2; shader branches consume this snapshot. */
public data class ConicalGradientDegeneracyV1(
    public val conicalDxF32: Float, public val conicalDyF32: Float,
    public val conicalStartRadiusF32: Float, public val conicalEndRadiusF32: Float,
    public val conicalDrF32: Float, public val conicalX2F32: Float, public val conicalY2F32: Float,
    public val conicalDdF32: Float, public val conicalCenterLengthF32: Float,
    public val conicalDr2F32: Float, public val conicalAbsDrF32: Float,
    public val conicalAF32: Float, public val conicalScaleF32: Float,
    public val conicalLinearEquation: Boolean, public val conicalCentersCoincident: Boolean,
    public val conicalRadiiEqual: Boolean, public val conicalFullyDegenerate: Boolean,
    public val conicalConcentric: Boolean, public val conicalSharedRadiusAboveEpsilon: Boolean,
    public val conicalBranchTagU32: UInt,
) : GradientDegeneracyV1 {
    public fun copyScalarsF32(): List<Float> = listOf(conicalDxF32, conicalDyF32,
        conicalStartRadiusF32, conicalEndRadiusF32, conicalDrF32, conicalX2F32, conicalY2F32,
        conicalDdF32, conicalCenterLengthF32, conicalDr2F32, conicalAbsDrF32, conicalAF32, conicalScaleF32)
    internal companion object {
        fun of(startF32: Point2F32, startRadiusF32: Float, endF32: Point2F32, endRadiusF32: Float): ConicalGradientDegeneracyV1 {
            val epsilonF32 = 0.000030517578125f
            val dxF32 = endF32.x - startF32.x
            val dyF32 = endF32.y - startF32.y
            val drF32 = endRadiusF32 - startRadiusF32
            val x2F32 = dxF32 * dxF32
            val y2F32 = dyF32 * dyF32
            val ddF32 = x2F32 + y2F32
            val centerLengthF32 = kotlin.math.sqrt(ddF32)
            val dr2F32 = drF32 * drF32
            val absDrF32 = kotlin.math.abs(drF32)
            val aF32 = ddF32 - dr2F32
            val scaleF32 = epsilonF32 * maxOf(maxOf(1f, ddF32), dr2F32)
            val linearEquation = kotlin.math.abs(aF32) <= scaleF32
            val centersCoincident = centerLengthF32 <= epsilonF32
            val radiiEqual = absDrF32 <= epsilonF32
            val fullyDegenerate = centersCoincident && radiiEqual
            val concentric = centersCoincident && !radiiEqual
            val sharedRadiusAboveEpsilon = fullyDegenerate && endRadiusF32 > epsilonF32
            val branchTagU32 = when { fullyDegenerate -> 0u; concentric -> 1u; linearEquation -> 2u; else -> 3u }
            return ConicalGradientDegeneracyV1(dxF32, dyF32, startRadiusF32, endRadiusF32, drF32,
                x2F32, y2F32, ddF32, centerLengthF32, dr2F32, absDrF32, aF32, scaleF32,
                linearEquation, centersCoincident, radiiEqual, fullyDegenerate, concentric, sharedRadiusAboveEpsilon, branchTagU32)
        }
    }
}

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
    internal val coordinates: MaterialCoordinatePlanV1,
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
                is MaterialBindingPlan.ConicalGradientV1 -> program == MaterialProgramPlan.ConicalGradientClampSrgbV1 &&
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
        fun sealConical(coordinates: MaterialCoordinatePlanV1, startF32: Point2F32, endF32: Point2F32,
            degeneracy: ConicalGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV1? {
            if (degeneracy != ConicalGradientDegeneracyV1.of(startF32, degeneracy.conicalStartRadiusF32,
                    endF32, degeneracy.conicalEndRadiusF32) || degeneracy.copyScalarsF32().any { !it.isFinite() } ||
                degeneracy.conicalStartRadiusF32 < 0f || degeneracy.conicalEndRadiusF32 < 0f) return null
            val schema = GradientNumericOperationGraphV1.conical()
            val stops = slab.copyStops()
            val proof = schema.proveConicalDomainV1(localMagnitudeF64, startF32, endF32, degeneracy, stops)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV1(GradientNumericOperationGraphV1.Conical(schema.root, proof),
                MaterialProgramPlan.ConicalGradientClampSrgbV1, coordinates,
                listOf(startF32.x, startF32.y, endF32.x, endF32.y), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }

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
            if (degeneracy != LinearGradientDegeneracyV1.of(startF32, endF32) ||
                degeneracy.copyScalarsF32().any { !it.isFinite() }) return null
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
    requireGradientStorageCapabilitiesV4(byteSizeI64,capabilities)
}

internal fun requireGradientStorageCapabilitiesV4(byteSizeI64: Long, capabilities: PlanCapabilitySnapshot) {
    require(byteSizeI64 >= 0L && byteSizeI64 % 32L == 0L) { W5cPlanDiagnostics.StopBudget }
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

internal fun normalizeGradientStopsV1(input: List<GradientStop>, preserveValidityMask: Boolean = false): NormalizedGradientStopsV1 {
    if (input.isEmpty()) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.EmptyStops)
    if (input.any { !it.position.isFinite() }) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.NonFinite)
    if ((input.size.toLong() + 2L) * 32L > Int.MAX_VALUE) return NormalizedGradientStopsV1.Refused(W5cPlanDiagnostics.StopBudget)
    fun canonicalF32(valueF32: Float): Float = if (valueF32 == 0f) 0f else valueF32
    fun color(stop: GradientStop): ColorF32 = stop.color.let {
        ColorF32.of(it.redNormalized, it.greenNormalized, it.blueNormalized, it.alphaNormalized)
    }
    if (input.size == 1) return if (preserveValidityMask) NormalizedGradientStopsV1.Stops(GradientStopSlabPlanV1.of(
        listOf(GradientStopPlanV1(0f, color(input.single())), GradientStopPlanV1(1f, color(input.single())))))
        else NormalizedGradientStopsV1.Solid(color(input.single()))
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

/** W5c normalization remains authoritative; only exterior duplicate endpoints are removed. */
internal fun normalizeGradientStopsV2(input: List<GradientStop>, effectiveTileMode: GradientTileModeV2,
    preserveValidityMask: Boolean = false): NormalizedGradientStopsV1 {
    val normalized = normalizeGradientStopsV1(input, preserveValidityMask)
    if (effectiveTileMode == GradientTileModeV2.CLAMP || normalized !is NormalizedGradientStopsV1.Stops) return normalized
    val stops = normalized.slab.copyStops().toMutableList()
    if (stops[0].positionF32 == 0f && stops[1].positionF32 == 0f) stops.removeAt(0)
    if (stops[stops.lastIndex].positionF32 == 1f && stops[stops.lastIndex - 1].positionF32 == 1f) stops.removeAt(stops.lastIndex)
    return NormalizedGradientStopsV1.Stops(GradientStopSlabPlanV1.of(stops))
}
