package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability

/**
 * The activation route is intentionally narrower than the historical GMs. These assertions
 * preserve the real Surface boundary code and operation count for each named GM, rather than
 * relabelling the refusal from source inspection.
 */
class LinearGradientGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `targeted gradient GMs retain their actual Surface terminal diagnostic`() {
        GpuAvailability.requireWebGpu()
        val expectedCodes = mapOf(
            "linear_gradient" to "unsupported.material.mapping.linear_gradient_stop_count",
            "fillrect_gradient" to "unsupported.material.source_unimplemented",
            "gradient_matrix" to "unsupported.material.source_unimplemented",
        )
        expectedCodes.forEach { (name, expectedCode) ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
                "$name unexpectedly rendered instead of refusing at the Surface boundary"
            }
            val code = attempt.diagnostic.substringBefore(":")
            assertEquals(expectedCode, code)
            assertTrue(attempt.operationCount > 0, "$name must record operations before refusal")
            println(
                "task3.gm-refusal gm=$name operations=${attempt.operationCount} " +
                    "diagnostic=$code message=${attempt.diagnostic.substringAfter(": ")}",
            )
        }
    }
}
