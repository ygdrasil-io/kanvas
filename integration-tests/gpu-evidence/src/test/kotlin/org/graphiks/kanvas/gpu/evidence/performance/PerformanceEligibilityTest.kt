package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary

class PerformanceEligibilityTest {
    @Test fun `fallback adapter is diagnostic only`() {
        assertEquals(
            PerformanceVerdict.DiagnosticOnly("fallback adapter"),
            PerformanceEligibility.evaluate(GPUBackendAdapterSummary("Apple", isFallbackAdapter = true)),
        )
    }

    @Test fun `software adapter tokens are diagnostic only case insensitively`() {
        listOf("llvmpipe", "SwiftShader Vulkan", "software rasterizer").forEach { token ->
            assertEquals(
                PerformanceVerdict.DiagnosticOnly("software adapter"),
                PerformanceEligibility.evaluate(GPUBackendAdapterSummary(token, vendor = "Mesa")),
            )
        }
    }

    @Test fun `hardware identity is eligible even without backend or driver`() {
        assertEquals(
            PerformanceVerdict.EligibleMeasurement("hardware adapter"),
            PerformanceEligibility.evaluate(GPUBackendAdapterSummary("Apple/Apple M2 Max", vendor = "Apple", device = "Apple M2 Max")),
        )
    }

    @Test fun `missing adapter identity is unavailable`() {
        assertEquals(
            PerformanceVerdict.Unavailable("adapter identity unavailable"),
            PerformanceEligibility.evaluate(null),
        )
        assertEquals(
            PerformanceVerdict.Unavailable("adapter identity unavailable"),
            PerformanceEligibility.evaluate(GPUBackendAdapterSummary("unknown-adapter")),
        )
    }
}
