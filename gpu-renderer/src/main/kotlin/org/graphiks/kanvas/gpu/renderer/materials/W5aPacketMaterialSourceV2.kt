package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable

/** Immutable source-only authority attached before frame linearization, never to a producer. */
internal class W5aPacketMaterialSourceV2 private constructor(
    val commandIdI32: Int,
    val stage: W5aMaterialSourceStage,
) {
    val canonicalIdentity: String = "w5a-source-v2:$commandIdI32:${stage.canonicalIdentity}"

    companion object {
        fun issue(table: MaterialPlanTable, ref: MaterialPlanRef, commandIdI32: Int): W5aPacketMaterialSourceV2 =
            W5aPacketMaterialSourceV2(commandIdI32, requireNotNull(W5aMaterialSourceStage.lower(table, ref)))
    }
}
