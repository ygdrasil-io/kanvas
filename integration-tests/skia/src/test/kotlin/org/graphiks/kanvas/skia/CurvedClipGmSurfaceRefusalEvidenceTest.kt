package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurvedClipGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `curved clip GMs expose fresh terminal route facts`() {
        GpuAvailability.requireWebGpu()
        val expected = mapOf(
            "clipcubic" to ExpectedTerminalRefusal("unsupported.stroke.width_invalid", 17),
            "clippedcubic" to ExpectedTerminalRefusal("unsupported.core_primitive.stencil_edge_fan_budget", 19),
        )
        expected.forEach { (name, refusal) ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
                "$name unexpectedly rendered"
            }
            assertEquals(refusal.diagnostic, attempt.diagnostic.substringBefore(":"), name)
            assertEquals(refusal.operationCount, attempt.operationCount, name)
            println(
                "task6.gm-refusal gm=$name operations=${attempt.operationCount} " +
                    "diagnostic=${attempt.diagnostic.substringBefore(":")}",
            )
        }
    }

    private data class ExpectedTerminalRefusal(
        val diagnostic: String,
        val operationCount: Int,
    )
}
