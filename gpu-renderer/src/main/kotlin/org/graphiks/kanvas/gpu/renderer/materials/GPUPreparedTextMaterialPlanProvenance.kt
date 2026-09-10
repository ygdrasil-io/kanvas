package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram

/**
 * Sealed W5a provenance retained by the prepared A8 text lane.
 *
 * The existing TextA8 ABI still consumes evaluated linear-premultiplied uniforms, but this
 * witness keeps the issuing table, root reference, version, command identity and authenticated
 * program together so no later stage can silently substitute an equivalent-looking material.
 */
public class GPUPreparedTextMaterialPlanEmission internal constructor(
    private val table: MaterialPlanTable,
    private val ref: MaterialPlanRef,
    private val program: GPUPreparedMaterialProgram,
) {
    private val admissionToken: GPUPreparedTextW5aAdmissionToken =
        requireNotNull(program.preparedTextW5aAdmissionToken) {
        "Prepared text W5a emission requires a compiler-issued admission token"
    }

    /** Binds this compiler-issued material result to its one normalized command. */
    public fun bind(
        commandIdValue: Int,
        candidate: GPUPreparedMaterialProgram,
    ): GPUPreparedTextMaterialPlanProvenance? {
        if (commandIdValue < 0 || candidate != program ||
            candidate.preparedTextW5aAdmissionToken !== admissionToken
        ) return null
        val entry = runCatching { table.entry(ref) }.getOrNull() ?: return null
        if (entry.program.versionI32 != 1 || entry.bindings.versionI32 != 1) return null
        return GPUPreparedTextMaterialPlanProvenance(
            table = table,
            ref = ref,
            commandIdValue = commandIdValue,
            program = program,
            admissionToken = admissionToken,
            programVersionI32 = entry.program.versionI32,
            bindingVersionI32 = entry.bindings.versionI32,
        )
    }
}

public class GPUPreparedTextMaterialPlanProvenance internal constructor(
    private val table: MaterialPlanTable,
    val ref: MaterialPlanRef,
    private val commandIdValue: Int,
    private val program: GPUPreparedMaterialProgram,
    private val admissionToken: GPUPreparedTextW5aAdmissionToken,
    private val programVersionI32: Int,
    private val bindingVersionI32: Int,
) {
    init {
        require(program.preparedTextW5aAdmissionToken === admissionToken) {
            "Prepared text W5a provenance requires its compiler-issued admission token"
        }
    }

    fun canonicalIdentity(): String =
        "w5a-text-plan-v1:command=$commandIdValue:ref=${ref.indexI32}:" +
            "program=$programVersionI32:binding=$bindingVersionI32:" +
            "material=${program.materialKey}:${program.abiHash}:table=${tableSnapshotIdentity()}"

    fun validates(commandIdValue: Int, candidate: GPUPreparedMaterialProgram): Boolean =
        commandIdValue == this.commandIdValue &&
            candidate == program &&
            candidate.preparedTextW5aAdmissionToken === admissionToken &&
            runCatching {
                val entry = table.entry(ref)
                entry.program.versionI32 == programVersionI32 &&
                    entry.bindings.versionI32 == bindingVersionI32 &&
                    programVersionI32 == 1 && bindingVersionI32 == 1
            }.getOrDefault(false)

    internal fun matchesAdmissionToken(candidate: GPUPreparedTextW5aAdmissionToken?): Boolean =
        admissionToken === candidate

    private fun tableSnapshotIdentity(): String = table.entries().joinToString("|") { entry ->
        val binding = when (val value = entry.bindings) {
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.SolidRgbaF32V1 ->
                value.copyRgbaF32().let { color ->
                    listOf(color.red, color.green, color.blue, color.alpha)
                        .joinToString(",") { channel -> channel.toRawBits().toString() }
                }
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1 ->
                value.alphaF32.toRawBits().toString()
            org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.EmptyV1 -> "empty"
        }
        "${entry.program.structuralId.value}@${entry.program.versionI32}:${entry.bindings.versionI32}:$binding"
    }
}
