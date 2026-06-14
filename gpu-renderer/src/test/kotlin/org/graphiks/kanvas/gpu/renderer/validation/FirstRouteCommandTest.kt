package org.graphiks.kanvas.gpu.renderer.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Locks the deterministic first-route fixture dump used by parallel GPU renderer lanes. */
class FirstRouteCommandTest {
    /** Ensures the first-route fixture names every contract category without fake GPU success. */
    @Test
    fun `first route fixture dump covers the full contract chain`() {
        val lines = GPUValidationFixture()
            .firstSliceConceptOwnershipDump()
            .lines()

        assertEquals(
            expected = listOf(
                "commands:GPUDrawCommandID:canonical command identifier",
                "commands:NormalizedDrawCommand.FillRect:first-slice draw command",
                "commands:GPUMaterialDescriptor.SolidColor:first-slice material descriptor",
                "analysis:GPUDrawAnalysisRecord:first-route analysis dump schema",
                "routing:GPURouteDecision.Refused:first-route route dump schema without product promotion",
                "materials:GPUPaintPipelinePlan:first-route solid material dump schema",
                "wgsl:WGSLReflectionResult:first-route WGSL reflection dump schema",
                "payloads:GPUPayloadGatherPlan:first-route payload dump schema",
                "pipelines:GPUPipelineKeyPreimage.Render:first-route pipeline-key preimage dump schema",
                "resources:GPUResourceMaterializationDecision.Refused:first-route resource decision dump schema",
                "execution:GPUCommandSubmission.Refused:first-route submission dump schema refuses before backend work",
                "telemetry:GPUTelemetryLedger:first-route telemetry dump schema",
                "routing:NegativeCPUFallbackRefusal:forbidden CPU-rendered texture fallback remains refused",
                "routing:UnsupportedRouteFamilyRefusal:first-route unsupportedFamilies=perspective-transform,singular-transform,unsupported-target-format,unsupported-blend,non-simple-clip,layer-filter-destination-read,missing-capability,wgsl-validation-or-abi-mismatch diagnostics=none",
            ),
            actual = lines,
        )
        assertFalse(
            actual = lines.any { line ->
                "GPUCommandSubmission.Submitted" in line || "backend success" in line
            },
            message = "First-route contract fixture must not fake backend submission success: $lines",
        )
    }
}
