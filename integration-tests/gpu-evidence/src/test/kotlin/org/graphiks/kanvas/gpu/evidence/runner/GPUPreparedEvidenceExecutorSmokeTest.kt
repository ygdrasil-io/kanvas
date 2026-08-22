package org.graphiks.kanvas.gpu.evidence.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.BootstrapEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUPreparedEvidenceExecutorSmokeTest {
    @Test
    fun `solid bootstrap route completes with readback and submission telemetry`() {
        assumeTrue(System.getenv("GPU_EVIDENCE_SMOKE") == "1", "set GPU_EVIDENCE_SMOKE=1 to enable GPU smoke validation")
        val backend = requireNotNull(GPUBackendRuntimeFactory.createOrNull()) { "GPU backend runtime is unavailable" }
        try {
            val result = GPUPreparedEvidenceExecutor(ProductEvidenceBackendPort(backend), "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.first())
            val rendered = assertIs<SceneObservation.Rendered>(assertIs<EvidenceExecutionResult.Observed>(result).observation)
            assertEquals("rendered", rendered.route.outcome)
            assertEquals("Completed", rendered.route.furthestPhase)
            assertEquals(64 * 64 * 4, rendered.rgba.size)
            assertTrue(rendered.route.structuralCounters.getOrDefault("queue.submit", 0L) > 0L)
            assertTrue(rendered.route.runtimeTelemetryDelta.submissions > 0L)
        } finally {
            try { backend.close() } finally { GPUBackendRuntimeFactory.dispose() }
        }
    }
}
