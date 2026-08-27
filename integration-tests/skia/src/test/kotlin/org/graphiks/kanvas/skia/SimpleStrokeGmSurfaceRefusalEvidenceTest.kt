package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SimpleStrokeGmSurfaceRefusalEvidenceTest {
    private data class ExpectedSurfaceRefusal(
        val diagnostic: String,
        val operationCount: Int,
    )

    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `stroke GMs expose their actual terminal Surface route before promotion`() {
        GpuAvailability.requireWebGpu()
        val expected = mapOf(
            "strokedline_caps" to ExpectedSurfaceRefusal(
                "unsupported.material.mapping.linear_gradient_stop_count",
                13,
            ),
            "strokes_round" to ExpectedSurfaceRefusal(
                "unsupported.stroke.expansion_budget_exceeded",
                401,
            ),
            "dashcircle" to ExpectedSurfaceRefusal(
                "unsupported.pipeline.capability_missing",
                25,
            ),
        )
        expected.forEach { (name, expectedRefusal) ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
                "$name unexpectedly rendered through an unsupported route"
            }
            val diagnostic = attempt.diagnostic.substringBefore(":")
            assertEquals(expectedRefusal.diagnostic, diagnostic)
            assertEquals(expectedRefusal.operationCount, attempt.operationCount)
            println(
                "task4.gm-refusal gm=$name operations=${attempt.operationCount} " +
                    "diagnostic=$diagnostic",
            )
        }
    }
}
