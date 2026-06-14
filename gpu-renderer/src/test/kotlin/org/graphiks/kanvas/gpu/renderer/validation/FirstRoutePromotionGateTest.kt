package org.graphiks.kanvas.gpu.renderer.validation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies that first-route promotion stays evidence gated. */
class FirstRoutePromotionGateTest {
    /** Ensures missing evidence prevents promotion and reports every missing category. */
    @Test
    fun `promotion gate refuses reports missing first route evidence categories`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "NormalizedDrawCommand.FillRect",
                    detail = "first-route command evidence",
                ),
            ),
        )

        assertFalse(result.passed)
        assertEquals(
            expected = listOf(
                "analysis",
                "route",
                "material",
                "wgsl",
                "payload",
                "pipeline-key",
                "resource-decision",
                "submission",
                "readback",
                "telemetry",
                "pipeline-cache",
                "negative-cpu-fallback",
                "unsupported-route-refusals",
            ),
            actual = result.missingEvidence,
        )
    }

    /** Ensures positive-looking route dumps are still insufficient without negative fallback evidence. */
    @Test
    fun `promotion gate requires negative CPU fallback evidence`() {
        val result = GPUPromotionGateCheck().evaluate(reportWith(*positiveRouteEvidenceEntries.toTypedArray()))

        assertFalse(result.passed)
        assertEquals(listOf("negative-cpu-fallback"), result.missingEvidence)
    }

    /** Ensures accepted-route evidence is not enough without nearby unsupported-family refusals. */
    @Test
    fun `promotion gate requires unsupported route family refusal evidence`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                *completeEvidenceEntries
                    .filterNot { entry -> entry.concept == "UnsupportedRouteFamilyRefusal" }
                    .toTypedArray(),
            ),
        )

        assertFalse(result.passed)
        assertEquals(listOf("unsupported-route-refusals"), result.missingEvidence)
        assertContains(
            result.diagnostics.joinToString("\n"),
            "unsupported-route-refusals requires UnsupportedRouteFamilyRefusal",
        )
    }

    /** Ensures unsupported-route refusal evidence covers every canonical first-route family. */
    @Test
    fun `promotion gate requires unsupported route family refusal evidence to cover canonical families`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                *completeEvidenceEntries
                    .map { entry ->
                        when (entry.concept) {
                            "UnsupportedRouteFamilyRefusal" ->
                                entry.copy(detail = "first-route unsupportedFamilies=perspective-transform diagnostics=none")
                            else -> entry
                        }
                    }
                    .toTypedArray(),
            ),
        )

        assertFalse(result.passed)
        assertEquals(listOf("unsupported-route-refusals"), result.missingEvidence)
        assertContains(
            result.diagnostics.joinToString("\n"),
            "unsupported-route-refusals requires UnsupportedRouteFamilyRefusal covering canonical " +
                "first-route unsupported families; missing=singular-transform,unsupported-target-format," +
                "unsupported-blend,non-simple-clip,layer-filter-destination-read,missing-capability," +
                "wgsl-validation-or-abi-mismatch",
        )
    }

    /** Ensures schema/refusal dumps cannot be misread as positive GPU support evidence. */
    @Test
    fun `promotion gate refuses route resource and submission refusal dumps as positive evidence`() {
        val result = GPUPromotionGateCheck().evaluate(reportWith(*refusedEvidenceEntries.toTypedArray()))

        assertFalse(result.passed)
        assertEquals(
            expected = listOf("route", "resource-decision", "submission", "readback", "unsupported-route-refusals"),
            actual = result.missingEvidence,
        )
    }

    /** Ensures report status remains part of the promotion gate. */
    @Test
    fun `promotion gate refuses incomplete validation reports even when evidence categories are present`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                *completeEvidenceEntries.toTypedArray(),
                status = GPUValidationStatus.Incomplete,
            ),
        )

        assertFalse(result.passed)
        assertContains(result.diagnostics.joinToString("\n"), "validation report status is Incomplete")
    }

    /** Ensures contradictory positive and refusal evidence cannot pass by category presence alone. */
    @Test
    fun `promotion gate refuses conflicting positive and refusal evidence`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                *completeEvidenceEntries.toTypedArray(),
                GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUReadbackResult.Refused",
                    detail = "late readback refused",
                ),
            ),
        )

        assertFalse(result.passed)
        assertEquals(emptyList(), result.missingEvidence)
        assertContains(
            result.diagnostics.joinToString("\n"),
            "readback has conflicting positive and non-positive evidence: GPUReadbackResult.Completed, GPUReadbackResult.Refused",
        )
    }

    /** Ensures package ownership alone cannot satisfy first-route promotion categories. */
    @Test
    fun `promotion gate refuses package owned but non canonical evidence concepts`() {
        val result = GPUPromotionGateCheck().evaluate(
            reportWith(
                *completeEvidenceEntries
                    .map { entry ->
                        when (entry.concept) {
                            "WGSLReflectionResult" ->
                                entry.copy(concept = "WGSLValidationDiagnostic", detail = "non positive WGSL evidence")
                            "GPUTelemetryLedger" ->
                                entry.copy(concept = "GPUCacheTelemetry.memory", detail = "non canonical telemetry evidence")
                            else -> entry
                        }
                    }
                    .toTypedArray(),
            ),
        )

        assertFalse(result.passed)
        assertEquals(listOf("wgsl", "telemetry"), result.missingEvidence)
        assertContains(
            result.diagnostics.joinToString("\n"),
            "wgsl requires WGSLReflectionResult but found WGSLValidationDiagnostic",
        )
        assertContains(
            result.diagnostics.joinToString("\n"),
            "telemetry requires GPUTelemetryLedger but found GPUCacheTelemetry.memory, GPUCacheTelemetry.pipeline",
        )
    }

    /** Ensures hand-built complete PM entries are visibly labeled as synthetic test evidence. */
    @Test
    fun `first route PM evidence bundle rejects unlabeled hand built complete entries`() {
        val unlabeledEntries = completeEvidenceEntries.map { entry ->
            entry.copy(detail = entry.detail.removePrefix("first-route "))
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            GPUValidationFixture().firstRoutePMEvidenceBundle(
                entries = unlabeledEntries,
            )
        }

        assertContains(
            failure.message.orEmpty(),
            "Custom first-route PM evidence entries must use a synthetic-test or diagnostic report name",
        )
    }

    /** Builds a validation report with a single deterministic dump. */
    private fun reportWith(
        vararg entries: GPUContractDump.Entry,
        status: GPUValidationStatus = GPUValidationStatus.Passed,
    ): GPUValidationReport =
        GPUValidationReport(
            name = "first-route",
            status = status,
            dumps = listOf(
                GPUContractDump(
                    name = "first-route-test-dump",
                    entries = entries.toList(),
                ),
            ),
        )

    private companion object {
        /** Evidence entries for every positive contract category. */
        val positiveRouteEvidenceEntries = listOf(
            GPUContractDump.Entry(
                ownerPackage = "commands",
                concept = "NormalizedDrawCommand.FillRect",
                detail = "first-route command evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "analysis",
                concept = "GPUDrawAnalysisRecord",
                detail = "first-route analysis evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "routing",
                concept = "GPURouteDecision.Native",
                detail = "first-route route evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "materials",
                concept = "GPUPaintPipelinePlan",
                detail = "first-route material evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "wgsl",
                concept = "WGSLReflectionResult",
                detail = "first-route WGSL evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "payloads",
                concept = "GPUPayloadGatherPlan",
                detail = "first-route payload evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "pipelines",
                concept = "GPUPipelineKeyPreimage.Render",
                detail = "first-route pipeline key evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "resources",
                concept = "GPUResourceMaterializationDecision.Materialized",
                detail = "first-route resource decision evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUCommandSubmission.Submitted",
                detail = "first-route submission evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUReadbackResult.Completed",
                detail = "first-route readback evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "telemetry",
                concept = "GPUTelemetryLedger",
                detail = "first-route telemetry evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "telemetry",
                concept = "GPUCacheTelemetry.pipeline",
                detail = "first-route pipeline cache evidence",
            ),
            GPUContractDump.Entry(
                ownerPackage = "routing",
                concept = "UnsupportedRouteFamilyRefusal",
                detail = "first-route unsupportedFamilies=perspective-transform,singular-transform," +
                    "unsupported-target-format,unsupported-blend,non-simple-clip,layer-filter-destination-read," +
                    "missing-capability,wgsl-validation-or-abi-mismatch diagnostics=none",
            ),
        )

        /** Evidence entries including the negative CPU fallback refusal. */
        val completeEvidenceEntries = positiveRouteEvidenceEntries + GPUContractDump.Entry(
            ownerPackage = "routing",
            concept = "NegativeCPUFallbackRefusal",
            detail = "forbidden CPU-rendered texture fallback remains refused",
        )

        /** Evidence-shaped entries that are still refusal/schema evidence, not accepted route evidence. */
        val refusedEvidenceEntries = positiveRouteEvidenceEntries
            .filterNot { entry -> entry.ownerPackage in setOf("routing", "resources", "execution") } +
            listOf(
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "GPURouteDecision.Refused",
                    detail = "first-route route dump schema without product promotion",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "resources",
                    concept = "GPUResourceMaterializationDecision.Refused",
                    detail = "first-route resource decision dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUCommandSubmission.Refused",
                    detail = "first-route submission dump schema refuses before backend work",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUReadbackResult.Refused",
                    detail = "first-route readback dump schema refuses before backend work",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "NegativeCPUFallbackRefusal",
                    detail = "forbidden CPU-rendered texture fallback remains refused",
                ),
            )
    }
}
