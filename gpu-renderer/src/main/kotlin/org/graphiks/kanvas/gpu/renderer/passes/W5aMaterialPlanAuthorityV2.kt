package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.materializeW5aSolid
import org.graphiks.kanvas.gpu.renderer.planning.W5aMaterialPlanLowerer

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

/**
 * Frame-owned W5a authority for the historical prepared CorePrimitive point route.
 *
 * The immutable table is snapshot once at frame capture. Draw commands retain only a
 * [MaterialPlanRef]; the table is consulted when CorePrimitive first writes a color uniform.
 */
class W5aCorePrimitiveMaterialAuthorityV2 private constructor(
    private val table: MaterialPlanTable,
    refsByCommandId: Map<Int, MaterialPlanRef>,
    private val materialWitness: W5aMaterialPlanVersionWitnessV2,
) {
    private val refsByCommandId: Map<Int, MaterialPlanRef> =
        java.util.Collections.unmodifiableMap(LinkedHashMap(refsByCommandId))

    /** Material-only witness retained by the native solid payload, independent of geometry. */
    sealed interface MaterializedSolidV2 {
        val commandIdI32: Int
        val ref: MaterialPlanRef
        val sourcePlanTable: MaterialPlanTable
        val premultipliedRgba: List<Float>
        fun validates(commandIdI32: Int): Boolean
    }

    private class MaterializedSolid(
        override val commandIdI32: Int,
        override val ref: MaterialPlanRef,
        rgba: List<Float>,
        private val frameAuthority: W5aCorePrimitiveMaterialAuthorityV2,
    ) : MaterializedSolidV2 {
        override val sourcePlanTable: MaterialPlanTable get() = frameAuthority.table
        override val premultipliedRgba: List<Float> =
            java.util.Collections.unmodifiableList(ArrayList(rgba))

        override fun validates(commandIdI32: Int): Boolean =
            commandIdI32 == this.commandIdI32 && frameAuthority.validates(commandIdI32, ref)
    }

    private fun validates(commandIdI32: Int, ref: MaterialPlanRef): Boolean =
        materialWitness.validates() && refsByCommandId[commandIdI32] == ref &&
            ref.indexI32 < table.sizeI32 &&
            table.entry(ref).program.versionI32 == W5aMaterialPlanVersionWitnessV2.MATERIAL_PLAN_VERSION_I32 &&
            table.entry(ref).bindings.versionI32 == W5aMaterialPlanVersionWitnessV2.MATERIAL_PLAN_VERSION_I32

    internal fun materializeSource(commandIdI32: Int, materialRef: MaterialPlanRef): MaterializedSolidV2? {
        if (!validates(commandIdI32, materialRef)) return null
        val color = W5aMaterialPlanLowerer().lower(table, materialRef) ?: return null
        return MaterializedSolid(commandIdI32, materialRef, listOf(color.red, color.green, color.blue, color.alpha), this)
    }

    internal fun materialize(
        semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    ): Map<Int, GPUDrawSemanticPayload>? {
        if (!materialWitness.validates() || refsByCommandId.isEmpty()) return null
        if (refsByCommandId.keys.any { it !in semanticsByCommandId }) return null
        val result = LinkedHashMap<Int, GPUDrawSemanticPayload>(semanticsByCommandId.size)
        semanticsByCommandId.forEach { (commandId, semantic) ->
            val expectedRef = refsByCommandId[commandId]
            if (expectedRef == null) {
                if (semantic is GPUDrawSemanticPayload.CorePrimitive &&
                    semantic.material is GPUCorePrimitiveMaterialPayload.W5aMaterialPlanRefV1
                ) return null
                result[commandId] = semantic
                return@forEach
            }
            val core = semantic as? GPUDrawSemanticPayload.CorePrimitive ?: return null
            val materialRef = (core.material as? GPUCorePrimitiveMaterialPayload.W5aMaterialPlanRefV1)
                ?.ref ?: return null
            if (materialRef != expectedRef || !validates(commandId, materialRef) || !core.hasStructuralIntegrity()) return null
            result[commandId] = core.materializeW5aSolid(
                materializeSource(commandId, materialRef) ?: return null,
            )
        }
        return java.util.Collections.unmodifiableMap(result)
    }

    companion object {
        fun issue(
            sourceTable: MaterialPlanTable,
            refsByCommandId: Map<Int, MaterialPlanRef>,
        ): W5aCorePrimitiveMaterialAuthorityV2? {
            if (refsByCommandId.isEmpty() || refsByCommandId.keys.any { it < 0 }) return null
            // MaterialPlanTable is immutable and was defensively snapshot at frame capture.
            val ownedTable = sourceTable
            val authorities = refsByCommandId.values.map { ref ->
                try {
                    ownedTable.entry(ref)
                    PlanDrawMaterialAuthority.MaterialV1(ref)
                } catch (_: IllegalArgumentException) {
                    return null
                }
            }
            val witness = W5aMaterialPlanVersionWitnessV2.issue(ownedTable, authorities)
                ?: return null
            return W5aCorePrimitiveMaterialAuthorityV2(ownedTable, refsByCommandId, witness)
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
