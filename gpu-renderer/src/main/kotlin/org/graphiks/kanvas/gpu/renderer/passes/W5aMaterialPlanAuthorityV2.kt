package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.plan.materialPlanRef
import org.graphiks.kanvas.gpu.plan.colorSourceCoordinatesV4
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.materializeW5aSolid
import org.graphiks.kanvas.gpu.renderer.planning.W5aMaterialPlanLowerer

private fun MaterialPlanTable.authenticatesDeferredImageV3(authority: PlanDrawMaterialAuthority): Boolean {
    val color = authority as? PlanDrawMaterialAuthority.MaterialV4 ?: return false
    val coordinates = color.coordinates as? org.graphiks.kanvas.gpu.plan.SourceCoordinatesV4.V3 ?: return false
    val entry = entry(color.ref)
    if (entry.program !is org.graphiks.kanvas.gpu.plan.ImageMaterialProgramV3) return false
    val image = entry.bindings as? org.graphiks.kanvas.gpu.plan.ImageSampleV3 ?: return false
    return coordinates.plan === image.execution.coordinates && authenticatesImage(color.ref, image.execution) &&
        colorSourceProofV4(color.ref).authenticates(this, color.ref, color.coordinates)
}

/**
 * Versioned W5a material witness.  It is issued only for closed material-table draws and is
 * deliberately absent from the historical W4 capability lanes.
 */
internal class W5aMaterialPlanVersionWitnessV2 private constructor(
    private val programVersionsI32: List<Int>,
) {
    internal fun validates(): Boolean =
        programVersionsI32.isNotEmpty() && programVersionsI32.all { it in MATERIAL_PLAN_VERSION_I32..6 }

    internal companion object {
        const val MATERIAL_PLAN_VERSION_I32: Int = 1

        fun issue(
            table: MaterialPlanTable?,
            authorities: List<PlanDrawMaterialAuthority>,
        ): W5aMaterialPlanVersionWitnessV2? {
            val materialTable = table ?: return null
            if (authorities.isEmpty() || authorities.any { it is PlanDrawMaterialAuthority.LegacyColorV1 }) return null
            val versions = try {
                authorities.map { authority ->
                    val entry = materialTable.entry(authority.materialPlanRef())
                    val deferredImageV3 = materialTable.authenticatesDeferredImageV3(authority)
                    if (entry.program.versionI32 == 3 && !deferredImageV3) return null
                    if ((entry.program.versionI32 == 4 || deferredImageV3) != (authority is PlanDrawMaterialAuthority.MaterialV4)) return null
                    if ((entry.program.versionI32 in 5..6) != (authority is PlanDrawMaterialAuthority.MaterialV5)) return null
                    entry.program.versionI32
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            return W5aMaterialPlanVersionWitnessV2(versions).takeIf(W5aMaterialPlanVersionWitnessV2::validates)
        }
    }
}

/**
 * Frame-owned W5a authority for authentic prepared CorePrimitive lanes.
 *
 * The immutable table is sealed after geometry admission. CorePrimitive retains a neutral
 * geometry uniform slot; the fragment source-stage consumes this table's raw bindings.
 */
class W5aCorePrimitiveMaterialAuthorityV2 private constructor(
    private val table: MaterialPlanTable,
    refsByCommandIdI32: Map<Int, MaterialPlanRef>,
    private val sourceRefsByCommandIdI32: Map<Int, MaterialPlanRef>,
    private val finalBlendsByCommandIdI32: Map<Int, org.graphiks.kanvas.gpu.plan.BlendPlan>,
    private val materialWitness: W5aMaterialPlanVersionWitnessV2,
    private val authoritiesByCommandIdI32: Map<Int, PlanDrawMaterialAuthority>,
    private val packedV4ByCommandIdI32: Map<Int, org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2>,
) {
    private val refsByCommandId: Map<Int, MaterialPlanRef> =
        java.util.Collections.unmodifiableMap(LinkedHashMap(refsByCommandIdI32))

    /** Material-only witness retained by the native solid payload, independent of geometry. */
    sealed interface MaterializedSolidV2 {
        val commandIdI32: Int
        val ref: MaterialPlanRef
        val sourceRef: MaterialPlanRef
        val sourcePlanTable: MaterialPlanTable
        val finalBlend: org.graphiks.kanvas.gpu.plan.BlendPlan?
        val coordinates: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV1?
        val coordinatesV2: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV2?
        val materialAuthority: PlanDrawMaterialAuthority
        val packedSourceV4: org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2?
        val premultipliedRgbaF32: List<Float>
        fun validates(commandIdI32: Int): Boolean
    }

    private class MaterializedSolid(
        override val commandIdI32: Int,
        override val ref: MaterialPlanRef,
        rgba: List<Float>,
        private val frameAuthority: W5aCorePrimitiveMaterialAuthorityV2,
        val geometryInventory: org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveFrameGeometryInventory?,
    ) : MaterializedSolidV2 {
        override val sourcePlanTable: MaterialPlanTable get() = frameAuthority.table
        override val coordinates: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV1?
            get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV1)?.coordinates
        override val coordinatesV2: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV2?
            get() = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV2)?.coordinates
        override val materialAuthority: PlanDrawMaterialAuthority get() = frameAuthority.authoritiesByCommandIdI32.getValue(commandIdI32)
        override val packedSourceV4: org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2? get() = frameAuthority.packedV4ByCommandIdI32[commandIdI32]
        override val finalBlend: org.graphiks.kanvas.gpu.plan.BlendPlan?
            get() = frameAuthority.finalBlendsByCommandIdI32[commandIdI32]
        override val sourceRef: MaterialPlanRef get() = frameAuthority.sourceRefsByCommandIdI32.getValue(commandIdI32)
        override val premultipliedRgbaF32: List<Float> =
            java.util.Collections.unmodifiableList(ArrayList(rgba))

        override fun validates(commandIdI32: Int): Boolean =
            commandIdI32 == this.commandIdI32 && frameAuthority.validates(commandIdI32, ref)
    }

    private fun validates(commandIdI32: Int, ref: MaterialPlanRef): Boolean =
        materialWitness.validates() && refsByCommandId[commandIdI32] == ref &&
            ref.indexI32 < table.sizeI32 &&
            (table.entry(ref).program.versionI32 in setOf(1,2,4,5,6) ||
                table.authenticatesDeferredImageV3(authoritiesByCommandIdI32.getValue(commandIdI32))) &&
            (table.entry(ref).bindings.versionI32 == table.entry(ref).program.versionI32 ||
                table.entry(ref).program.versionI32 == 4 &&
                table.entry(ref).bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1 &&
                (authoritiesByCommandIdI32[commandIdI32] as? PlanDrawMaterialAuthority.MaterialV4)?.let {
                    table.colorSourceProofV4(ref).authenticates(table,ref,it.coordinates)
                } == true)

    internal fun materializeSource(commandIdI32: Int, materialRef: MaterialPlanRef,
        geometryInventory: org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveFrameGeometryInventory? = null,
    ): MaterializedSolidV2? {
        if (!validates(commandIdI32, materialRef)) return null
        val authority = authoritiesByCommandIdI32.getValue(commandIdI32)
        val color = if (authority.colorSourceCoordinatesV4() != null) {
            org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage.colorV4(table,authority,
                packedV4ByCommandIdI32[commandIdI32] ?: return null) ?: return null
            org.graphiks.math.color.ColorF32.Transparent
        } else W5aMaterialPlanLowerer().lower(table, materialRef) ?: return null
        return MaterializedSolid(commandIdI32, materialRef, listOf(color.red, color.green, color.blue, color.alpha), this, geometryInventory)
    }

    internal fun materialize(
        semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
        geometryInventory: org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveFrameGeometryInventory? = null,
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
            if (materialRef != sourceRefsByCommandIdI32[commandId] || !validates(commandId, expectedRef) || !core.hasStructuralIntegrity()) return null
            result[commandId] = core.materializeW5aSolid(
                materializeSource(commandId, expectedRef, geometryInventory) ?: return null,
                geometryInventory?.materialEnvelopeGeometryByCommandId?.getValue(commandId),
            )
        }
        return java.util.Collections.unmodifiableMap(result)
    }

    companion object {
        internal fun geometryInventory(source: MaterializedSolidV2):
            org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveFrameGeometryInventory? =
            (source as? MaterializedSolid)?.geometryInventory

        fun issue(
            sourceTable: MaterialPlanTable,
            refsByCommandIdI32: Map<Int, MaterialPlanRef>,
            authoritiesByCommandIdI32: Map<Int, PlanDrawMaterialAuthority>,
            sourcePlansByCommandIdI32: Map<Int, Pair<MaterialPlanTable, MaterialPlanRef>> =
                refsByCommandIdI32.mapValues { sourceTable to it.value },
            finalBlendsByCommandIdI32: Map<Int, org.graphiks.kanvas.gpu.plan.BlendPlan> = emptyMap(),
            packedV4ByCommandIdI32: Map<Int, org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2> = emptyMap(),
        ): W5aCorePrimitiveMaterialAuthorityV2? {
            val refsByCommandId = refsByCommandIdI32
            if (refsByCommandId.isEmpty() || refsByCommandId.keys.any { it < 0 }) return null
            if (authoritiesByCommandIdI32.keys != refsByCommandId.keys ||
                authoritiesByCommandIdI32.any { (id,authority) -> authority is PlanDrawMaterialAuthority.LegacyColorV1 ||
                    authority.materialPlanRef() != refsByCommandId[id] }) return null
            if (packedV4ByCommandIdI32.keys != authoritiesByCommandIdI32.filterValues { it.colorSourceCoordinatesV4() != null }.keys) return null
            if (sourcePlansByCommandIdI32.keys != refsByCommandId.keys) return null
            if (finalBlendsByCommandIdI32.isNotEmpty() && finalBlendsByCommandIdI32.keys != refsByCommandId.keys) return null
            val sourceRefs = linkedMapOf<Int, MaterialPlanRef>()
            refsByCommandId.forEach { (commandIdI32, ref) ->
                val source = sourcePlansByCommandIdI32.getValue(commandIdI32)
                fun stage(table: MaterialPlanTable, ref: MaterialPlanRef) = when (val authority = authoritiesByCommandIdI32.getValue(commandIdI32)) {
                    is PlanDrawMaterialAuthority.MaterialV5 -> org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage.colorV4(table,
                        authority.copy(ref=ref),packedV4ByCommandIdI32.getValue(commandIdI32))
                    is PlanDrawMaterialAuthority.MaterialV1 -> org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage.lower(table,ref,authority.coordinates)
                    is PlanDrawMaterialAuthority.MaterialV2 -> org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage.lower(table,ref,authority.coordinates)
                    is PlanDrawMaterialAuthority.MaterialV4 -> org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage.colorV4(table,
                        authority.copy(ref = ref),packedV4ByCommandIdI32.getValue(commandIdI32))
                    else -> null
                }
                val original = stage(source.first,source.second) ?: return null
                val rebased = stage(sourceTable,ref) ?: return null
                if (original.canonicalIdentity != rebased.canonicalIdentity) return null
                sourceRefs[commandIdI32] = source.second
            }
            // MaterialPlanTable is immutable and was defensively snapshot at frame capture.
            val ownedTable = sourceTable
            val authorities = authoritiesByCommandIdI32.values.toList()
            val witness = W5aMaterialPlanVersionWitnessV2.issue(ownedTable, authorities)
                ?: return null
            return W5aCorePrimitiveMaterialAuthorityV2(ownedTable, refsByCommandId,
                java.util.Collections.unmodifiableMap(sourceRefs),
                java.util.Collections.unmodifiableMap(LinkedHashMap(finalBlendsByCommandIdI32)), witness,
                java.util.Collections.unmodifiableMap(LinkedHashMap(authoritiesByCommandIdI32)),
                java.util.Collections.unmodifiableMap(LinkedHashMap(packedV4ByCommandIdI32)))
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
