package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage
import org.graphiks.math.color.ColorF32

/** W5a source authority. The historical geometry color slot is deliberately neutral. */
internal class W5aMaterialPlanLowerer {
    fun material(
        table: MaterialPlanTable?,
        authority: org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority,
        commandIdI32: Int,
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload? {
        if (authority !is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV1) return null
        val owner = org.graphiks.kanvas.gpu.renderer.passes.W5aCorePrimitiveMaterialAuthorityV2.issue(
            requireNotNull(table), mapOf(commandIdI32 to authority.ref),
            coordinatesByCommandIdI32 = authority.coordinates?.let { mapOf(commandIdI32 to it) }.orEmpty(),
        ) ?: error("Invalid W5a source authority")
        return org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload.SolidColor(
            requireNotNull(owner.materializeSource(commandIdI32, authority.ref)),
        )
    }
    fun lower(table: MaterialPlanTable, root: MaterialPlanRef): ColorF32? {
        if (table.gradientStopSlab != null) {
            var indexI32 = root.indexI32
            while (table.entry(MaterialPlanRef(indexI32)).bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1) indexI32--
            val entry = table.entry(MaterialPlanRef(indexI32))
            if (entry.program == org.graphiks.kanvas.gpu.plan.MaterialProgramPlan.LinearGradientClampSrgbV1)
                return ColorF32.Transparent
        }
        return W5aMaterialSourceStage.lower(table, root)?.let { ColorF32.Transparent }
    }
}
