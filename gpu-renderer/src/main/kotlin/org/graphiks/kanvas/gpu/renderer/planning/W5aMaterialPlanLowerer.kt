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
        packedSourceV4: org.graphiks.kanvas.gpu.plan.RawMaterialRequirementsV2? = null,
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload? {
        val ref = when(authority) {
            is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV5 -> authority.ref
            is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV1 -> authority.ref
            is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV2 -> authority.ref
            is org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority.MaterialV4 -> authority.ref
            else -> return null
        }
        val owner = org.graphiks.kanvas.gpu.renderer.passes.W5aCorePrimitiveMaterialAuthorityV2.issue(
            requireNotNull(table), mapOf(commandIdI32 to ref), mapOf(commandIdI32 to authority),
            packedV4ByCommandIdI32 = packedSourceV4?.let { mapOf(commandIdI32 to it) }.orEmpty(),
        ) ?: error("Invalid W5a source authority")
        return org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload.SolidColor(
            requireNotNull(owner.materializeSource(commandIdI32, ref)),
        )
    }
    fun lower(table: MaterialPlanTable, root: MaterialPlanRef): ColorF32? {
        var leaf = root
        while (table.entry(leaf).bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1)
            leaf = MaterialPlanRef(leaf.indexI32-1)
        if (table.entry(leaf).bindings is org.graphiks.kanvas.gpu.plan.ComposedMaterialBindingV5 ||
            table.entry(leaf).bindings is org.graphiks.kanvas.gpu.plan.ColorFilterBindingV4 ||
            table.entry(leaf).bindings is org.graphiks.kanvas.gpu.plan.GradientInterpolationBindingV4) {
            table.colorSourceProofV4(root)
            // This is only the historical geometry color slot. material() below
            // requires the exact packed V4 authority for the color-writing stage.
            return ColorF32.Transparent
        }
        if (table.gradientStopSlab != null) {
            var indexI32 = root.indexI32
            while (table.entry(MaterialPlanRef(indexI32)).bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1) indexI32--
            val entry = table.entry(MaterialPlanRef(indexI32))
            if (entry.bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.GradientV1 ||
                entry.bindings is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.GradientV2)
                return ColorF32.Transparent
        }
        return W5aMaterialSourceStage.lower(table, root)?.let { ColorF32.Transparent }
    }
}
