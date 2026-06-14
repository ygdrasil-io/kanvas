package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies R6 first-route telemetry counters remain deterministic observations. */
class GPUFirstRouteTelemetryCounterTest {
    /** First-route events aggregate into stable PM counter lines without claiming support. */
    @Test
    fun `first route events aggregate deterministic telemetry counters`() {
        val ledger = GPUTelemetryLedger.empty()
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandFamily(
                    family = GPUFirstRouteCommandFamily.Rect,
                    count = 1L,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandFamily(
                    family = GPUFirstRouteCommandFamily.Rect,
                    count = 2L,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.Route(
                    kind = GPUFirstRouteRouteKind.GPUNative,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.Route(
                    kind = GPUFirstRouteRouteKind.RefuseDiagnostic,
                    refusalCode = "unsupported.clip.complex",
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.WGSLModuleValidation(
                    outcome = GPUFirstRouteWGSLModuleValidationOutcome.Success,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.WGSLModuleValidation(
                    outcome = GPUFirstRouteWGSLModuleValidationOutcome.Failure,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.ResourceMaterialization(
                    outcome = GPUFirstRouteResourceMaterializationOutcome.Materialized,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.ResourceMaterialization(
                    outcome = GPUFirstRouteResourceMaterializationOutcome.Refused,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandSubmission(
                    outcome = GPUFirstRouteCommandSubmissionOutcome.Submitted,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandSubmission(
                    outcome = GPUFirstRouteCommandSubmissionOutcome.Refused,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.NegativeCPUFallbackRefusal,
            )

        assertEquals(
            expected = listOf(
                "counter:first_route.command.count:family=Rect:3:count",
                "counter:first_route.route.count:kind=GPUNative:1:count",
                "counter:first_route.route.count:kind=RefuseDiagnostic:1:count",
                "counter:first_route.route.refusal.count:code=unsupported.clip.complex:1:count",
                "counter:first_route.wgsl_module_validation.count:outcome=Success:1:count",
                "counter:first_route.wgsl_module_validation.count:outcome=Failure:1:count",
                "counter:first_route.resource_materialization.count:outcome=Materialized:1:count",
                "counter:first_route.resource_materialization.count:outcome=Refused:1:count",
                "counter:first_route.command_submission.count:outcome=Submitted:1:count",
                "counter:first_route.command_submission.count:outcome=Refused:1:count",
                "counter:first_route.negative_cpu_fallback.refusal.count:policy=forbidden:1:count",
            ),
            actual = ledger.firstRouteCounterDumpLines(),
        )
        assertFalse(
            actual = ledger.firstRouteCounterDumpLines().any { line ->
                line.contains("support=", ignoreCase = true) ||
                    line.contains("route_support", ignoreCase = true) ||
                    line.contains("accepted", ignoreCase = true) ||
                    line.contains("fallback_success", ignoreCase = true)
            },
            message = "Telemetry counters must not claim route support or hidden fallback success.",
        )
    }

    /** First-route counter domains stay closed so PM dumps are stable across runs. */
    @Test
    fun `first route counter domains and outcomes are closed`() {
        assertEquals(
            listOf(
                "CommandFamily",
                "RouteKind",
                "RouteRefusal",
                "WGSLModuleValidation",
                "ResourceMaterialization",
                "CommandSubmission",
                "NegativeCPUFallbackRefusal",
            ),
            GPUFirstRouteCounterDomain.values().map { it.name },
        )
        assertEquals(
            listOf("Rect", "RRect", "Path", "Text", "Image", "Vertices"),
            GPUFirstRouteCommandFamily.values().map { it.name },
        )
        assertEquals(
            listOf("GPUNative", "CPUPreparedGPU", "CPUReferenceOnly", "RefuseDiagnostic"),
            GPUFirstRouteRouteKind.values().map { it.name },
        )
        assertEquals(
            listOf("Success", "Failure"),
            GPUFirstRouteWGSLModuleValidationOutcome.values().map { it.name },
        )
        assertEquals(
            listOf("Materialized", "Deferred", "Refused"),
            GPUFirstRouteResourceMaterializationOutcome.values().map { it.name },
        )
        assertEquals(
            listOf("Submitted", "Failed", "Refused"),
            GPUFirstRouteCommandSubmissionOutcome.values().map { it.name },
        )
    }

    /** Invalid event facts fail before they can produce misleading counters. */
    @Test
    fun `first route telemetry events reject ambiguous facts`() {
        assertFailsWith<IllegalArgumentException> {
            GPUFirstRouteTelemetryEvent.CommandFamily(
                family = GPUFirstRouteCommandFamily.Rect,
                count = 0L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUFirstRouteTelemetryEvent.Route(
                kind = GPUFirstRouteRouteKind.RefuseDiagnostic,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUFirstRouteTelemetryEvent.Route(
                kind = GPUFirstRouteRouteKind.GPUNative,
                refusalCode = "unsupported.clip.complex",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUFirstRouteTelemetryEvent.Route(
                kind = GPUFirstRouteRouteKind.RefuseDiagnostic,
                refusalCode = "unsupported:clip",
            )
        }
    }
}
