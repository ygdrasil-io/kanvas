package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority

/**
 * Versioned W5a material witness.  It is issued only for closed material-table draws and is
 * deliberately absent from the historical W4 capability lanes.
 */
internal class W5aMaterialPlanVersionWitnessV2 private constructor(
    private val programVersionsI32: List<Int>,
) {
    internal fun validates(): Boolean =
        programVersionsI32.isNotEmpty() && programVersionsI32.all { it == MATERIAL_PLAN_VERSION_I32 }

    internal companion object {
        const val MATERIAL_PLAN_VERSION_I32: Int = 1

        fun issue(
            table: MaterialPlanTable?,
            authorities: List<PlanDrawMaterialAuthority>,
        ): W5aMaterialPlanVersionWitnessV2? {
            val materialTable = table ?: return null
            if (authorities.isEmpty() || authorities.any { it !is PlanDrawMaterialAuthority.MaterialV1 }) return null
            val versions = try {
                authorities.map { authority ->
                    materialTable.entry((authority as PlanDrawMaterialAuthority.MaterialV1).ref).program.versionI32
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            return W5aMaterialPlanVersionWitnessV2(versions).takeIf(W5aMaterialPlanVersionWitnessV2::validates)
        }
    }
}

/** W5a final Rect authority; a historical W4a V1 packet cannot carry this witness. */
internal class W5aAnalyticRectSessionScratchV2 private constructor(
    internal val payloadFacts: W4aSessionScratchV1,
    private val materialWitness: W5aMaterialPlanVersionWitnessV2,
) {
    internal fun validatesMaterialPlanVersion(): Boolean = materialWitness.validates()

    internal fun matches(
        expectedPlanId: String,
        capabilityHash: String,
        generation: Long,
        expectedTarget: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef,
        expectedStaging: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef,
        bounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
        packets: List<GPUDrawPacket>,
    ): Boolean = validatesMaterialPlanVersion() && payloadFacts.matches(
        expectedPlanId, capabilityHash, generation, expectedTarget, expectedStaging, bounds, packets,
    )

    internal companion object {
        fun issue(
            payloadFacts: W4aSessionScratchV1,
            materialWitness: W5aMaterialPlanVersionWitnessV2,
        ): W5aAnalyticRectSessionScratchV2? =
            W5aAnalyticRectSessionScratchV2(payloadFacts, materialWitness)
                .takeIf(W5aAnalyticRectSessionScratchV2::validatesMaterialPlanVersion)
    }
}

/** W5a final RRect authority; a historical W4b V1 packet cannot carry this witness. */
internal class W5aAnalyticRRectSessionScratchV2 private constructor(
    internal val payloadFacts: W4bSessionScratchV1,
    private val materialWitness: W5aMaterialPlanVersionWitnessV2,
) {
    internal fun validatesMaterialPlanVersion(): Boolean = materialWitness.validates()

    internal fun matches(
        expectedPlanId: String,
        capabilityHash: String,
        generation: Long,
        expectedTarget: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef,
        expectedStaging: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef,
        bounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
        packets: List<GPUDrawPacket>,
    ): Boolean = validatesMaterialPlanVersion() && payloadFacts.matches(
        expectedPlanId, capabilityHash, generation, expectedTarget, expectedStaging, bounds, packets,
    )

    internal companion object {
        fun issue(
            payloadFacts: W4bSessionScratchV1,
            materialWitness: W5aMaterialPlanVersionWitnessV2,
        ): W5aAnalyticRRectSessionScratchV2? =
            W5aAnalyticRRectSessionScratchV2(payloadFacts, materialWitness)
                .takeIf(W5aAnalyticRRectSessionScratchV2::validatesMaterialPlanVersion)
    }
}
