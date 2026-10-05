package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable

/** Surface-local evidence retained before source elision; never a renderer admission token. */
internal class GPUPreparedElidedNoOpOperation private constructor(
    val operationIndexI32: Int,
    val sourceTable: MaterialPlanTable,
    val sourceRef: MaterialPlanRef,
    val blend: BlendPlan,
) {
    companion object {
        fun fromValidated(operationIndexI32: Int, sourceTable: MaterialPlanTable,
            sourceRef: MaterialPlanRef, blend: BlendPlan): GPUPreparedElidedNoOpOperation {
            require(operationIndexI32 >= 0 && blend == BlendPlan.NoOpV1)
            sourceTable.entry(sourceRef)
            return GPUPreparedElidedNoOpOperation(operationIndexI32, sourceTable, sourceRef, blend)
        }
    }
}
