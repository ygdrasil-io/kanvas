package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.W5aMaterialPlanEvaluator
import org.graphiks.math.color.ColorF32

/** Renderer adapter for the shared sealed W5a source-stage evaluator. */
internal class W5aMaterialPlanLowerer {
    fun lower(table: MaterialPlanTable, root: MaterialPlanRef): ColorF32? =
        W5aMaterialPlanEvaluator.lower(table, root)
}
