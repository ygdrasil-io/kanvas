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

public data class LinearGradientDegeneracyV1(public val axisXF32: Float, public val axisYF32: Float,
    public val lengthSquaredF32: Float, public val degenerate: Boolean)

/** Value-dependent proof, issued only by the planner and transported through range rebasing. */
public class LinearGradientNumericAuthorityV1 private constructor(
    public val graph: GradientNumericOperationGraphV1,
    private val coordinates: MaterialCoordinatePlanV1,
    private val startF32: Point2F32,
    private val endF32: Point2F32,
    private val degeneracy: LinearGradientDegeneracyV1,
    private val range: GradientStopRangeV1,
    private val slabIdentity: String,
    private val localMagnitudeF64: Double,
    private val uniformMagnitudeF64: Double,
) {
    private val programId = MaterialProgramPlan.LinearGradientClampSrgbV1.structuralId
    internal val domainIdentity: String = "linear-domain-v1:${programId.value}:${graph.contractId}:" +
        "$startF32:$endF32:$degeneracy:${coordinates.canonicalIdentity}:" +
        "${localMagnitudeF64.toBits()}:${uniformMagnitudeF64.toBits()}"
    public val canonicalIdentity: String = "$domainIdentity:$range:$slabIdentity"
    override fun toString(): String = canonicalIdentity

    public fun authenticates(program: MaterialProgramPlan, binding: MaterialBindingPlan.LinearGradientV1,
        slab: GradientStopSlabPlanV1, coordinates: MaterialCoordinatePlanV1): Boolean =
        program.structuralId == programId && graph.contractId == "WgslFloatEnvelopeV1" &&
            graph.domainProof == GradientNumericDomainProofV1.ProvenFinite && this.coordinates == coordinates &&
            binding.startF32 == startF32 && binding.endF32 == endF32 && binding.degeneracy == degeneracy &&
            binding.stopRange == range && slab.canonicalIdentity == slabIdentity

    internal fun rebase(binding: MaterialBindingPlan.LinearGradientV1, sourceSlab: GradientStopSlabPlanV1,
        newRange: GradientStopRangeV1, newSlab: GradientStopSlabPlanV1): LinearGradientNumericAuthorityV1 {
        require(authenticates(MaterialProgramPlan.LinearGradientClampSrgbV1, binding, sourceSlab, coordinates)) {
            W5cPlanDiagnostics.NumericDomainUnbounded
        }
        fun sequence(slab: GradientStopSlabPlanV1, selected: GradientStopRangeV1): List<GradientStopPlanV1> =
            slab.copyStops().subList(selected.baseIndexU32.toInt(), (selected.baseIndexU32 + selected.countU32).toInt())
        require(sequence(sourceSlab, range) == sequence(newSlab, newRange)) { W5cPlanDiagnostics.NumericDomainUnbounded }
        return LinearGradientNumericAuthorityV1(graph, coordinates, startF32, endF32, degeneracy,
            newRange, newSlab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
    }

    internal companion object {
        fun seal(coordinates: MaterialCoordinatePlanV1, startF32: Point2F32, endF32: Point2F32,
            degeneracy: LinearGradientDegeneracyV1, slab: GradientStopSlabPlanV1,
            localMagnitudeF64: Double, uniformMagnitudeF64: Double): LinearGradientNumericAuthorityV1? {
            val schema = MaterialProgramPlan.LinearGradientClampSrgbV1.copyGradientNumericOperationGraphV1()
            val stops = slab.copyStops()
            val proof = schema.proveLinearDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops, startF32, endF32)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            val provenGraph = GradientNumericOperationGraphV1.Linear(schema.root, proof)
            return LinearGradientNumericAuthorityV1(provenGraph, coordinates, startF32, endF32, degeneracy,
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
