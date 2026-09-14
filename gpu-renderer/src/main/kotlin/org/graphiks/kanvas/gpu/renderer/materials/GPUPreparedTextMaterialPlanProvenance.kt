package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram

/**
 * Sealed W5a provenance retained by the prepared A8 text lane.
 *
 * The TextA8 source-stage consumes raw W5a bindings. This witness keeps the issuing table,
 * root reference, version, command identity and authenticated program together.
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
        commandIdValueI32: Int,
        candidate: GPUPreparedMaterialProgram,
    ): GPUPreparedTextMaterialPlanProvenance? {
        if (commandIdValueI32 < 0 || candidate != program ||
            candidate.preparedTextW5aAdmissionToken !== admissionToken
        ) return null
        val entry = runCatching { table.entry(ref) }.getOrNull() ?: return null
        if (entry.program.versionI32 != 1 || entry.bindings.versionI32 != 1) return null
        return GPUPreparedTextMaterialPlanProvenance(
            table = table,
            ref = ref,
            commandIdValueI32 = commandIdValueI32,
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
    private val commandIdValueI32: Int,
    private val program: GPUPreparedMaterialProgram,
    private val admissionToken: GPUPreparedTextW5aAdmissionToken,
    private val programVersionI32: Int,
    private val bindingVersionI32: Int,
) {
    public val sourcePlanTable: MaterialPlanTable get() = table

    public fun remap(table: MaterialPlanTable, ref: MaterialPlanRef): GPUPreparedTextMaterialPlanProvenance {
        require(requireNotNull(W5aMaterialSourceStage.lower(this.table, this.ref)).canonicalIdentity ==
            requireNotNull(W5aMaterialSourceStage.lower(table, ref)).canonicalIdentity)
        return GPUPreparedTextMaterialPlanProvenance(table, ref, commandIdValueI32, program,
            admissionToken, programVersionI32, bindingVersionI32)
    }
    init {
        require(program.preparedTextW5aAdmissionToken === admissionToken) {
            "Prepared text W5a provenance requires its compiler-issued admission token"
        }
    }

    fun canonicalIdentity(): String =
        "w5a-text-plan-v1:command=$commandIdValueI32:ref=${ref.indexI32}:" +
            "program=$programVersionI32:binding=$bindingVersionI32:" +
            "material=${program.materialKey}:${program.abiHash}:table=${tableSnapshotIdentity()}"

    fun validates(commandIdValueI32: Int, candidate: GPUPreparedMaterialProgram): Boolean =
        commandIdValueI32 == this.commandIdValueI32 &&
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
            is org.graphiks.kanvas.gpu.plan.ComposedMaterialBindingV5 -> error(org.graphiks.kanvas.gpu.plan.W5gPlanDiagnostics.Unpromoted)
            is org.graphiks.kanvas.gpu.plan.ColorFilterBindingV4 -> error(org.graphiks.kanvas.gpu.plan.W5fPlanDiagnostics.Unpromoted)
            is org.graphiks.kanvas.gpu.plan.GradientInterpolationBindingV4 -> error(org.graphiks.kanvas.gpu.plan.W5fPlanDiagnostics.Unpromoted)
            is org.graphiks.kanvas.gpu.plan.ImageSampleV3 -> error("W5e images do not admit Text")
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.SolidRgbaF32V1 ->
                value.copyRgbaF32().let { color ->
                    listOf(color.red, color.green, color.blue, color.alpha)
                        .joinToString(",") { channel -> channel.toRawBits().toString() }
                }
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.OpacityF32V1 ->
                value.alphaF32.toRawBits().toString()
            org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.EmptyV1 -> "empty"
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.GradientV2 -> error("W5d gradients do not admit Text")
            is org.graphiks.kanvas.gpu.plan.MaterialBindingPlan.GradientV1 -> error("W5c gradients do not admit Text")
        }
        "${entry.program.structuralId.value}@${entry.program.versionI32}:${entry.bindings.versionI32}:$binding"
    }
}
