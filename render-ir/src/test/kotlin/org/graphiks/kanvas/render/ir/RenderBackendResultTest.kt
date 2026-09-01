package org.graphiks.kanvas.render.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
