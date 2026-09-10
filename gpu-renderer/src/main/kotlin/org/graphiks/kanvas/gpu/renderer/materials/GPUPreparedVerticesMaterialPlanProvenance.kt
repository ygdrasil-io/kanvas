package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram

/** Compiler-issued W5a vertices emission, bindable only to its exact program and command. */
public class GPUPreparedVerticesMaterialPlanEmission internal constructor(
    private val table: MaterialPlanTable,
    private val ref: MaterialPlanRef,
    private val program: GPUPreparedMaterialProgram,
) {
    private val admissionToken: GPUPreparedVerticesW5aAdmissionToken =
        requireNotNull(program.preparedVerticesW5aAdmissionToken) {
            "Prepared vertices W5a emission requires a compiler-issued admission token"
        }

    public fun bind(
        commandIdValueI32: Int,
        candidate: GPUPreparedMaterialProgram,
    ): GPUPreparedVerticesMaterialPlanProvenance? {
        if (commandIdValueI32 < 0 || candidate != program ||
            candidate.preparedVerticesW5aAdmissionToken !== admissionToken
        ) return null
        val entry = runCatching { table.entry(ref) }.getOrNull() ?: return null
        if (entry.program.versionI32 != 1 || entry.bindings.versionI32 != 1) return null
        return GPUPreparedVerticesMaterialPlanProvenance(
            table, ref, commandIdValueI32, program, admissionToken,
            entry.program.versionI32, entry.bindings.versionI32,
        )
    }
}

/** Immutable authority binding the exact W5a table/ref, program, and normalized command. */
public class GPUPreparedVerticesMaterialPlanProvenance internal constructor(
    private val table: MaterialPlanTable,
    val ref: MaterialPlanRef,
    private val commandIdValueI32: Int,
    private val program: GPUPreparedMaterialProgram,
    private val admissionToken: GPUPreparedVerticesW5aAdmissionToken,
    private val programVersionI32: Int,
    private val bindingVersionI32: Int,
) {
    public val sourcePlanTable: MaterialPlanTable get() = table

    public fun remappedEmission(table: MaterialPlanTable, ref: MaterialPlanRef): GPUPreparedVerticesMaterialPlanEmission {
        require(requireNotNull(W5aMaterialSourceStage.lower(this.table, this.ref)).canonicalIdentity ==
            requireNotNull(W5aMaterialSourceStage.lower(table, ref)).canonicalIdentity)
        return GPUPreparedVerticesMaterialPlanEmission(table, ref, program)
    }
    init {
        require(program.preparedVerticesW5aAdmissionToken === admissionToken) {
            "Prepared vertices W5a provenance requires its compiler-issued admission token"
        }
    }

    public fun canonicalIdentity(): String =
        "w5a-vertices-plan-v1:command=$commandIdValueI32:ref=${ref.indexI32}:" +
            "program=$programVersionI32:binding=$bindingVersionI32:" +
            "material=${program.materialKey}:${program.abiHash}:table=${tableSnapshotIdentity()}"

    public fun validates(commandIdValueI32: Int, candidate: GPUPreparedMaterialProgram): Boolean =
        commandIdValueI32 == this.commandIdValueI32 && candidate == program &&
            candidate.preparedVerticesW5aAdmissionToken === admissionToken &&
            runCatching {
                val entry = table.entry(ref)
                entry.program.versionI32 == programVersionI32 &&
                    entry.bindings.versionI32 == bindingVersionI32 &&
                    programVersionI32 == 1 && bindingVersionI32 == 1
            }.getOrDefault(false)

    private fun tableSnapshotIdentity(): String = table.entries().joinToString("|") { entry ->
        val binding = when (val value = entry.bindings) {
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.SolidRgbaF32V1 ->
                value.copyRgbaF32().let { color ->
                    listOf(color.red, color.green, color.blue, color.alpha)
                        .joinToString(",") { it.toRawBits().toString() }
                }
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1 ->
                value.alphaF32.toRawBits().toString()
            org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.EmptyV1 -> "empty"
        }
        "${entry.program.structuralId.value}@${entry.program.versionI32}:${entry.bindings.versionI32}:$binding"
    }
}
