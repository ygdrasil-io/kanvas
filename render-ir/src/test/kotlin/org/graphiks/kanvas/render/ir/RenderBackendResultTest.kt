package org.graphiks.kanvas.render.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RenderBackendResultTest {
    @Test
    fun `planning failures require a public diagnostic`() {
        assertFailsWith<IllegalArgumentException> {
            RenderPlanResult.InvalidScene(emptyList())
        }
    }

    @Test
    fun `planning failure snapshots its diagnostics`() {
        val diagnostics = mutableListOf(
            RenderDiagnostic(
                code = RenderDiagnosticCode("scene.invalid"),
                domain = RenderDiagnosticDomain.SCENE,
                severity = RenderDiagnosticSeverity.ERROR,
                message = "The scene cannot be planned.",
            ),
        )
        val result = RenderPlanResult.InvalidScene(diagnostics)

        diagnostics.clear()

        assertEquals("scene.invalid", result.diagnostics.single().code.value)
    }

    @Test
    fun `every failed planning result isolates diagnostics from source and output mutation`() {
        val source = mutableListOf(diagnostic("planning"))
        val results = listOf<RenderPlanResult<Nothing>>(
            RenderPlanResult.GapNotMigrated(source),
            RenderPlanResult.GapOnPromotedScope(source),
            RenderPlanResult.InvalidScene(source),
            RenderPlanResult.ResourceLimitExceeded(source),
        )

        source.clear()

        results.forEach { result ->
            val diagnostics = result.failedDiagnostics()
            assertFailsWith<UnsupportedOperationException> {
                (diagnostics as MutableList<RenderDiagnostic>).clear()
            }
            assertEquals("planning", result.failedDiagnostics().single().code.value)
        }
    }

    @Test
    fun `every execution outcome is distinct and failures isolate diagnostics`() {
        val source = mutableListOf(diagnostic("execution"))
        val outcomes = listOf<RenderExecutionResult>(
            RenderExecutionResult.Completed,
            RenderExecutionResult.UnsupportedCapability(source),
            RenderExecutionResult.InvalidPlan(source),
            RenderExecutionResult.ResourceLimitExceeded(source),
            RenderExecutionResult.DeviceFailure(source),
        )

        source.clear()

        assertEquals(outcomes.size, outcomes.map { it::class }.distinct().size)
        outcomes.drop(1).forEach { outcome ->
            val diagnostics = outcome.failedDiagnostics()
            assertFailsWith<UnsupportedOperationException> {
                (diagnostics as MutableList<RenderDiagnostic>).clear()
            }
            assertEquals("execution", outcome.failedDiagnostics().single().code.value)
        }
    }

    private fun diagnostic(code: String): RenderDiagnostic = RenderDiagnostic(
        code = RenderDiagnosticCode(code),
        domain = RenderDiagnosticDomain.SCENE,
        severity = RenderDiagnosticSeverity.ERROR,
        message = "diagnostic: $code",
    )

    private fun RenderPlanResult<Nothing>.failedDiagnostics(): List<RenderDiagnostic> = when (this) {
        is RenderPlanResult.GapNotMigrated -> diagnostics
        is RenderPlanResult.GapOnPromotedScope -> diagnostics
        is RenderPlanResult.InvalidScene -> diagnostics
        is RenderPlanResult.ResourceLimitExceeded -> diagnostics
        is RenderPlanResult.Ready -> error("Ready is not a failure")
    }

    private fun RenderExecutionResult.failedDiagnostics(): List<RenderDiagnostic> = when (this) {
        RenderExecutionResult.Completed -> error("Completed is not a failure")
        is RenderExecutionResult.UnsupportedCapability -> diagnostics
        is RenderExecutionResult.InvalidPlan -> diagnostics
        is RenderExecutionResult.ResourceLimitExceeded -> diagnostics
        is RenderExecutionResult.DeviceFailure -> diagnostics
    }
}
