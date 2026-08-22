package org.graphiks.kanvas.gpu.evidence.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.BootstrapEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class GPUPreparedEvidenceExecutorContractTest {
    @Test fun `opened session samples telemetry around canonical prepared frame and closes in finally`() {
        val events = mutableListOf<String>()
        val result = GPUPreparedEvidenceExecutor(FakePort(events), "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.first())
        val rendered = assertIs<EvidenceExecutionResult.Observed>(result).observation
        assertIs<SceneObservation.Rendered>(rendered)
        assertEquals(listOf("telemetry-before", "prepare-program", "prepare-scene-frame", "render-frame", "wait-completion", "close-prepared-frame", "telemetry-after"), events)
        assertEquals(1L, rendered.route.runtimeTelemetryDelta.submissions)
    }

    @Test fun `unregistered effect carries product route identity and refuses before session`() {
        val events = mutableListOf<String>()
        val result = GPUPreparedEvidenceExecutor(FakePort(events), "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.last())
        val refused = assertIs<SceneObservation.Refused>(assertIs<EvidenceExecutionResult.Observed>(result).observation)
        assertEquals("product.runtime-effect.custom", refused.route.routeId)
        assertEquals("unsupported.runtime_effect.custom_wgsl_not_registered", refused.stableReasonCode)
        assertEquals(0L, refused.submissionDelta)
        assertEquals(listOf("telemetry-before", "prepare-program", "telemetry-after"), events)
    }

    @Test fun `missing capabilities is unavailable before telemetry and preparation`() {
        val events = mutableListOf<String>()
        val result = GPUPreparedEvidenceExecutor(FakePort(events, capabilitiesAvailable = false), "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.first())
        assertIs<SceneObservation.Unavailable>(assertIs<EvidenceExecutionResult.Observed>(result).observation)
        assertEquals(emptyList(), events)
    }

    @Test fun `timeout after submission is non promotable execution failure and closes frame`() = assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.Timeout), "timeout")
    @Test fun `failed completion after submission is non promotable execution failure and closes frame`() = assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.Failed), "failed")
    @Test fun `missing readback after submission is non promotable execution failure`() = assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.MissingReadback), "readback")
    @Test fun `wrong readback id after submission is non promotable execution failure`() = assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.WrongReadback), "readback")
    @Test fun `incomplete phase and missing structural submit cannot render`() { assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.Incomplete), "Completed"); assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.NoStructuralSubmit), "queue.submit") }
    @Test fun `structural submission without runtime telemetry submission cannot render`() = assertExecutionFailure(FakePort(mutableListOf(), completion = CompletionKind.RuntimeDeltaZero), "runtime submission")

    @Test fun `completed product diagnostic fields are retained in deterministic execution failure diagnostics`() {
        val diagnostic = GPUDiagnostic(GPUDiagnosticCode("failed.test.completion"), GPUDiagnosticDomain.Execution, GPUDiagnosticSeverity.Fatal, "completion failed", mapOf("zeta" to "last", "alpha" to "first"), isTerminal = true, isRetryable = false)
        val failure = assertIs<EvidenceExecutionResult.ExecutionFailure>(GPUPreparedEvidenceExecutor(FakePort(mutableListOf(), completion = CompletionKind.Diagnostic, completionDiagnostic = diagnostic), "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.first()))
        assertEquals(listOf("diagnostic.code=failed.test.completion", "diagnostic.domain=Execution", "diagnostic.severity=Fatal", "diagnostic.message=completion failed", "diagnostic.terminal=true", "diagnostic.retryable=false", "diagnostic.fact.alpha=first", "diagnostic.fact.zeta=last"), failure.diagnostics)
    }

    private fun assertExecutionFailure(port: FakePort, expected: String) {
        val failure = assertIs<EvidenceExecutionResult.ExecutionFailure>(GPUPreparedEvidenceExecutor(port, "a".repeat(40)).execute(BootstrapEvidenceCatalog.cases.first()))
        assertEquals(true, failure.message.contains(expected, ignoreCase = true))
        assertEquals(true, failure.route.runtimeTelemetryDelta.submissions > 0L || expected == "runtime submission")
        assertEquals(true, port.events.contains("close-prepared-frame"))
        assertEquals(true, port.events.indexOf("close-prepared-frame") < port.events.indexOf("telemetry-after"))
    }

    private enum class CompletionKind { Success, Timeout, Failed, MissingReadback, WrongReadback, Incomplete, NoStructuralSubmit, RuntimeDeltaZero, Diagnostic }
    private class FakePort(val events: MutableList<String>, private val capabilitiesAvailable: Boolean = true, private val completion: CompletionKind = CompletionKind.Success, private val completionDiagnostic: GPUDiagnostic? = null) : EvidenceBackendPort {
        override val capabilities = if (capabilitiesAvailable) EvidenceCapabilities("test") else null
        override val deviceGeneration = 1L
        private var submissions = 0L
        private var telemetryCalls = 0
        override fun telemetry(): GPUBackendRuntimeTelemetry { events += if (telemetryCalls++ == 0) "telemetry-before" else "telemetry-after"; return GPUBackendRuntimeTelemetry(submissions = submissions) }
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
            events += "prepare-program"
            return if (context.descriptor.id.value.contains("unregistered")) EvidenceProgramPreparation.Refused("product.runtime-effect.custom", "unsupported.runtime_effect.custom_wgsl_not_registered", "unregistered", emptyList()) else EvidenceProgramPreparation.Recorded("product.solid-rect", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
        }
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort {
            events += "prepare-scene-frame"
            return object : EvidencePreparedFramePort {
                override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                    events += "render-frame"; if (completion != CompletionKind.RuntimeDeltaZero) submissions++; events += "wait-completion"
                    return when (completion) {
                        CompletionKind.Timeout -> throw java.util.concurrent.TimeoutException("timeout")
                        CompletionKind.Failed -> EvidenceCompletedFrame("a", "Completed", "Failed", "failed.frame", "failed completion", null, null, emptyList(), emptyList(), mapOf("queue.submit" to 1))
                        CompletionKind.MissingReadback -> EvidenceCompletedFrame("a", "Completed", "Succeeded", null, null, null, null, emptyList(), emptyList(), mapOf("queue.submit" to 1))
                        CompletionKind.WrongReadback -> EvidenceCompletedFrame("a", "Completed", "Succeeded", null, null, "wrong", ByteArray(width * height * 4), emptyList(), emptyList(), mapOf("queue.submit" to 1))
                        CompletionKind.Incomplete -> EvidenceCompletedFrame("a", "Submitted", "Succeeded", null, null, program.readbackRequestId, ByteArray(width * height * 4), emptyList(), emptyList(), mapOf("queue.submit" to 1))
                        CompletionKind.NoStructuralSubmit -> EvidenceCompletedFrame("a", "Completed", "Succeeded", null, null, program.readbackRequestId, ByteArray(width * height * 4), emptyList(), emptyList(), emptyMap())
                        CompletionKind.RuntimeDeltaZero -> EvidenceCompletedFrame("a", "Completed", "Succeeded", null, null, program.readbackRequestId, ByteArray(width * height * 4), emptyList(), emptyList(), mapOf("queue.submit" to 1))
                        CompletionKind.Diagnostic -> EvidenceCompletedFrame("a", "Completed", "Failed", completionDiagnostic?.code?.value, completionDiagnostic?.message, null, null, emptyList(), emptyList(), mapOf("queue.submit" to 1), completionDiagnostic?.let(::completionDiagnosticLines).orEmpty())
                        CompletionKind.Success -> EvidenceCompletedFrame.succeeded(program.readbackRequestId, ByteArray(width * height * 4))
                    }
                }
                override fun close() { events += "close-prepared-frame" }
            }
        }
    }
}
