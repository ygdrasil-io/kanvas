package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable

/** Immutable source-only authority attached before frame linearization, never to a producer. */
internal class W5aPacketMaterialSourceV2 private constructor(
    val commandIdI32: Int,
    val stage: W5aMaterialSourceStage,
) {
    val canonicalIdentity: String = "${if (stage.imageV3 == null) "w5a-source-v2" else "w5e-source-v3"}:$commandIdI32:${stage.canonicalIdentity}"

    companion object {
        fun issueImage(table: MaterialPlanTable, authority: org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority,
            execution: org.graphiks.kanvas.gpu.plan.ImageSampleExecutionPlanV1,commandIdI32: Int,
            packedSourceV4: org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2? = null): W5aPacketMaterialSourceV2 {
            val source = when (authority) {
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV3 -> issueImageV3(table,authority.ref,commandIdI32)
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV4 -> issue(table,authority,commandIdI32,packedSourceV4)
                else -> error(org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics.InvalidContract)
            }
            // The packet must retain the exact selected image/child/cell owner,
            // including when its final source root is an external V4 filter.
            require(source.stage.imageV3 === execution) { org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics.InvalidContract }
            return source
        }
        fun issue(table: MaterialPlanTable, authority: org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority,
            commandIdI32: Int, packedSourceV4: org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2? = null): W5aPacketMaterialSourceV2 =
            when (authority) {
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV1 -> issue(table,authority.ref,commandIdI32,authority.coordinates)
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV2 -> issue(table,authority.ref,commandIdI32,authority.coordinates)
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV4,
                is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV5 -> W5aPacketMaterialSourceV2(commandIdI32,
                    requireNotNull(W5aMaterialSourceStage.colorV4(table,authority,requireNotNull(packedSourceV4))) {
                        org.graphiks.kanvas.gpu.plan.W5fPlanDiagnostics.Schema })
                else -> error("Unsupported material source authority")
            }
        fun issueImageV3(table: MaterialPlanTable, root: MaterialPlanRef, commandIdI32: Int): W5aPacketMaterialSourceV2 =
            W5aPacketMaterialSourceV2(commandIdI32, W5aMaterialSourceStage.imageV3(table, root))
        fun issue(table: MaterialPlanTable, ref: MaterialPlanRef, commandIdI32: Int,
            coordinates: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV2): W5aPacketMaterialSourceV2 =
            W5aPacketMaterialSourceV2(commandIdI32, requireNotNull(W5aMaterialSourceStage.lower(table, ref, coordinates)) {
                org.graphiks.kanvas.gpu.plan.W5dPlanDiagnostics.CoordinatePlanSchema
            })
        fun issue(table: MaterialPlanTable, ref: MaterialPlanRef, commandIdI32: Int,
            coordinates: org.graphiks.kanvas.gpu.plan.MaterialCoordinatePlanV1? = null): W5aPacketMaterialSourceV2 =
            W5aPacketMaterialSourceV2(commandIdI32, requireNotNull(W5aMaterialSourceStage.lower(table, ref, coordinates)))
    }
}
