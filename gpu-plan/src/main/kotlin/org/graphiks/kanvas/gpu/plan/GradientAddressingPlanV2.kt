package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2F32

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
    private val degeneracy: LinearGradientDegeneracyV1,
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
            tileGraph == GradientTileOperationGraphV2.clamp() && binding.copyUniformValuesF32() == uniformValuesF32 &&
            when (binding) { is MaterialBindingPlan.LinearGradientV2 -> binding.degeneracy == degeneracy } &&
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
        fun sealLinear(program: GradientAddressingProgramV2, coordinates: MaterialCoordinatePlanV2,
            startF32: Point2F32, endF32: Point2F32, degeneracy: LinearGradientDegeneracyV1,
            slab: GradientStopSlabPlanV1, localMagnitudeF64: Double, uniformMagnitudeF64: Double): GradientNumericAuthorityV2? {
            val tile = GradientTileOperationGraphV2.clamp()
            if (program.family != GradientFamilyV2.LINEAR || program.requestedTileMode != tile.requestedMode ||
                program.effectiveTileMode != tile.effectiveMode || program.tileGraphId != tile.contractId ||
                program.coordinateTopologyId != coordinates.topologyIdentity ||
                degeneracy != LinearGradientDegeneracyV1.of(startF32, endF32) ||
                degeneracy.copyScalarsF32().any { !it.isFinite() }) return null
            val schema = GradientNumericOperationGraphV1.linear()
            val stops = slab.copyStops()
            val proof = schema.proveLinearDomainV1(localMagnitudeF64, uniformMagnitudeF64, degeneracy, stops, startF32, endF32)
            if (proof != GradientNumericDomainProofV1.ProvenFinite) return null
            return GradientNumericAuthorityV2(GradientNumericOperationGraphV1.Linear(schema.root, proof), tile,
                program, coordinates, listOf(startF32.x, startF32.y, endF32.x, endF32.y), degeneracy,
                GradientStopRangeV1(0u, stops.size.toUInt()), slab.canonicalIdentity, localMagnitudeF64, uniformMagnitudeF64)
        }
    }
}
