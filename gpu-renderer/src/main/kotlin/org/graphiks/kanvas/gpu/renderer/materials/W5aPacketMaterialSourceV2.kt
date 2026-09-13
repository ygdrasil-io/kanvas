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
