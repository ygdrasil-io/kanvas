package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUTextResourceContractEvidence
import org.graphiks.kanvas.glyph.gpu.GPUTextResourceContractFixture
import org.graphiks.kanvas.glyph.gpu.GPUTextResourceContractPlanningResult
import org.graphiks.kanvas.glyph.gpu.planGPUTextResourceContracts

/**
 * Result of planning renderer-side text resource contracts from a pure-Kotlin
 * text GPU artifact fixture.
 */
sealed interface GPUTextResourcePlanEvidenceResult {
    /**
     * Accepted resource contract with dumpable upload, instance-layout, and
     * binding evidence.
     */
    data class Accepted(
        val evidence: GPUTextResourceContractEvidence,
    ) : GPUTextResourcePlanEvidenceResult {
        /** Deterministic evidence lines for PM dumps and route diagnostics. */
        fun dumpLines(): List<String> = buildList {
            add("text.resource-plan.upload " +
                "planAvailable=true " +
                "byteSize=${evidence.uploadPlan.byteSize} " +
                "uploadBeforeSample=${evidence.uploadPlan.uploadBeforeSampleDependency}")
            add("text.resource-plan.instance-layout " +
                "layoutId=${evidence.instanceLayout.layoutId} " +
                "strideBytes=${evidence.instanceLayout.strideBytes} " +
                "attributes=${evidence.instanceLayout.attributes.joinToString(",") { it.name }} " +
                "layoutHash=${evidence.instanceLayout.layoutHash}")
            add("text.resource-plan.binding " +
                "planId=${evidence.bindingPlan.bindingPlanId} " +
                "bindingHash=${evidence.bindingPlan.bindingLayoutHash}")
        }
    }

    /** Refused resource contract with a stable diagnostic code. */
    data class Refused(
        val code: String,
        val message: String,
    ) : GPUTextResourcePlanEvidenceResult {
        /** Deterministic evidence lines for PM dumps. */
        fun dumpLines(): List<String> = listOf(
            "text.resource-plan.refused code=$code message=$message",
        )
    }
}

/**
 * Plans renderer-side text resource contracts by delegating to
 * [planGPUTextResourceContracts] and wrapping the result in renderer-owned
 * evidence types.
 */
fun planRendererTextResourceContracts(
    fixture: GPUTextResourceContractFixture,
): GPUTextResourcePlanEvidenceResult {
    val result = planGPUTextResourceContracts(fixture)
    return when (result) {
        is GPUTextResourceContractPlanningResult.Accepted ->
            GPUTextResourcePlanEvidenceResult.Accepted(evidence = result.evidence)
        is GPUTextResourceContractPlanningResult.Refused ->
            GPUTextResourcePlanEvidenceResult.Refused(
                code = result.refusal.rendererDiagnostic,
                message = result.refusal.toCanonicalJson(),
            )
    }
}
