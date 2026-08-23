package org.graphiks.kanvas.gpu.evidence.catalog

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCapabilities
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCompletedFrame
import org.graphiks.kanvas.gpu.evidence.runner.EvidencePreparedFramePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgramPreparation
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRecordingRequest
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRuntimePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceExecutionResult
import org.graphiks.kanvas.gpu.evidence.runner.GPUPreparedEvidenceExecutor
import org.graphiks.kanvas.gpu.evidence.runner.GpuEvidenceCliRunner
import org.graphiks.kanvas.gpu.evidence.runner.PreparedEvidenceProgram
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class CatalogExpectationInvariantTest {
    @Test
    fun `every render case has exactly one oracle and every refusal has none`() {
        assertEquals(4, GpuEvidenceCatalog.cases.size)
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            when (evidenceCase.descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> assertNotNull(evidenceCase.oracle, evidenceCase.descriptor.id.value)
                is EvidenceExpectation.ShouldRefuse -> assertEquals(null, evidenceCase.oracle, evidenceCase.descriptor.id.value)
            }
        }
    }

    @Test
    fun `expectation gate cannot turn a wrong terminal outcome into pass`() {
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val observation = when (evidenceCase.descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> refused(evidenceCase, "unsupported.wrong")
                is EvidenceExpectation.ShouldRefuse -> rendered(evidenceCase)
            }
            assertIs<EvidenceVerdict.Fail>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation), evidenceCase.descriptor.id.value)
        }
    }

    @Test
    fun `fake product port exercises every catalog outcome without bypassing the gate`() {
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val observed = assertIs<EvidenceExecutionResult.Observed>(GPUPreparedEvidenceExecutor(ExpectedOutcomePort(), "a".repeat(40)).execute(evidenceCase)).observation
            assertIs<EvidenceVerdict.Pass>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observed), evidenceCase.descriptor.id.value)
        }
    }

    @Test
    fun `unavailable fake product port cannot produce a pass for any catalog case`() {
        val executor = GPUPreparedEvidenceExecutor(UnavailablePort, "a".repeat(40))

        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val observed = assertIs<EvidenceExecutionResult.Observed>(executor.execute(evidenceCase)).observation
            assertIs<SceneObservation.Unavailable>(observed)
            assertIs<EvidenceVerdict.Unavailable>(EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observed))
        }
    }

    @Test
    fun `cli never writes generated evidence for unavailable or execution failure`() {
        listOf(UnavailablePort, FailingPort).forEach { backend ->
            val root = Files.createTempDirectory("gpu-evidence-invariant")
            val code = GpuEvidenceCliRunner(FakeRuntime(backend)).run(args(root))

            assertEquals(1, code)
            assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
        }
    }

    private fun refused(evidenceCase: EvidenceCase, code: String): SceneObservation.Refused = SceneObservation.Refused(
        code, "wrong outcome", 0L,
        RouteEvidence("fake", null, null, "refused", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry()),
        emptyList(), environment(),
    )

    private fun rendered(evidenceCase: EvidenceCase): SceneObservation.Rendered = SceneObservation.Rendered(
        ByteArray(evidenceCase.descriptor.width * evidenceCase.descriptor.height * 4),
        RouteEvidence("fake", "attempt", "Completed", "rendered", emptyList(), emptyList(), mapOf("queue.submit" to 1L), GPUBackendRuntimeTelemetry(submissions = 1L)),
        emptyList(), environment(),
        ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(evidenceCase.descriptor.width * evidenceCase.descriptor.height * 4), 1),
    )

    private fun environment() = EvidenceEnvironment("a".repeat(40), "test", "test", "test", "test", null, 1L, "fake", true)
    private fun args(root: java.nio.file.Path) = arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "solid-card-stack")

    private class FakeRuntime(private val backend: EvidenceBackendPort?) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? = backend
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private object UnavailablePort : EvidenceBackendPort {
        override val capabilities: EvidenceCapabilities? = null
        override val deviceGeneration: Long = 1L
        override fun telemetry() = GPUBackendRuntimeTelemetry()
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = error("unreachable")
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable")
    }

    private object FailingPort : EvidenceBackendPort {
        override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake")
        override val deviceGeneration: Long = 1L
        override fun telemetry() = GPUBackendRuntimeTelemetry()
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation =
            EvidenceProgramPreparation.Recorded("fake", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
            override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame = error("prepared session failed")
            override fun close() = Unit
        }
    }

    private class ExpectedOutcomePort : EvidenceBackendPort {
        override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake")
        override val deviceGeneration: Long = 1L
        private var current: EvidenceCase? = null
        override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = if (current?.descriptor?.expectation == EvidenceExpectation.ShouldRender) 1L else 0L)
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
            val evidenceCase = requireNotNull(GpuEvidenceCatalog.cases.firstOrNull { it.descriptor == context.descriptor })
            current = evidenceCase
            return when (val expectation = evidenceCase.descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> EvidenceProgramPreparation.Recorded("fake.render", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
                is EvidenceExpectation.ShouldRefuse -> EvidenceProgramPreparation.Refused("fake.refusal", expectation.stableReasonCode, "expected refusal", emptyList())
            }
        }
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
            override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                val evidenceCase = requireNotNull(current)
                return EvidenceCompletedFrame.succeeded(program.readbackRequestId, requireNotNull(evidenceCase.oracle).render(width, height))
            }
            override fun close() = Unit
        }
    }
}
