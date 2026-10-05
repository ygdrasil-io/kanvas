package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.plan.PreparedSourceFrameV6

/** Exact common source owner, retained by prepared inventories without local compilation. */
internal class GPUPreparedCommonSourceV6(val frame: PreparedSourceFrameV6, val sourceKeyI32: Int) {
    val ref: MaterialPlanRef = frame.ref(sourceKeyI32)
    val stage: W5aMaterialSourceStage = requireNotNull(W5aMaterialSourceStage.colorV4(frame.table,
        PlanDrawMaterialAuthority.MaterialV5(ref), frame.packedSource(sourceKeyI32)))
    val program = requireNotNull(stage.composedProof?.composedProgramV6)
    val layout = requireNotNull(stage.composedLayout)
    fun bind(commandIdI32: Int): W5aPacketMaterialSourceV2 = W5aPacketMaterialSourceV2.bindCommon(stage, commandIdI32)
}
