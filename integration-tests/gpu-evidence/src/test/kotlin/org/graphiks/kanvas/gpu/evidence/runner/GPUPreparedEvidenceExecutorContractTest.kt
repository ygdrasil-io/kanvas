package org.graphiks.kanvas.gpu.evidence.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.nio.file.Files
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceBundleWriter
import org.graphiks.kanvas.gpu.evidence.catalog.BootstrapEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class GPUPreparedEvidenceExecutorContractTest {
    @Test
    fun `recorded case follows canonical prepared session sequence and samples telemetry delta`() {
        val events = mutableListOf<String>()
        val port = FakeEvidenceBackendPort(events)
        val result = GPUPreparedEvidenceExecutor(port, "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.first(),
        )

        assertIs<SceneObservation.Rendered>(result)
        assertEquals(
            listOf("prepare-program", "prepare-scene-frame", "render-frame", "wait-completion", "close-prepared-frame"),
            events,
        )
        assertEquals(1L, result.route.runtimeTelemetryDelta.submissions)
    }

    @Test
    fun `unregistered effect refuses before prepared frame with zero submissions`() {
        val events = mutableListOf<String>()
        val port = FakeEvidenceBackendPort(events)
        val result = GPUPreparedEvidenceExecutor(port, "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.last(),
        )

        assertIs<SceneObservation.Refused>(result)
        assertEquals("unsupported.runtime_effect.custom_wgsl_not_registered", result.stableReasonCode)
        assertEquals(0L, result.submissionDelta)
        assertEquals(listOf("prepare-program"), events)
    }

    @Test
    fun `missing capabilities is unavailable without invoking a scene program`() {
        val events = mutableListOf<String>()
        val result = GPUPreparedEvidenceExecutor(FakeEvidenceBackendPort(events, capabilitiesAvailable = false), "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.first(),
        )

        assertIs<SceneObservation.Unavailable>(result)
        assertEquals("unavailable.gpu.capabilities", result.stableReasonCode)
        assertEquals(emptyList(), events)
    }

    @Test
    fun `successful readback without runtime submission telemetry is a typed failure`() {
        val result = GPUPreparedEvidenceExecutor(FakeEvidenceBackendPort(mutableListOf(), recordSubmission = false), "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.first(),
        )

        assertIs<SceneObservation.Refused>(result)
        assertEquals("failed.gpu.telemetry.submission_delta", result.stableReasonCode)
    }

    @Test
    fun `missing readback is a typed refusal after prepared frame closes`() {
        val events = mutableListOf<String>()
        val result = GPUPreparedEvidenceExecutor(FakeEvidenceBackendPort(events, missingReadback = true), "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.first(),
        )

        assertIs<SceneObservation.Refused>(result)
        assertEquals("failed.gpu.readback", result.stableReasonCode)
        assertEquals("close-prepared-frame", events.last())
    }

    @Test
    fun `render-route refusal is serialized with the lowercase refused outcome`() {
        val result = GPUPreparedEvidenceExecutor(FakeEvidenceBackendPort(mutableListOf(), missingReadback = true), "a".repeat(40)).execute(
            BootstrapEvidenceCatalog.cases.first(),
        )
        val refusal = assertIs<SceneObservation.Refused>(result)

        EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), "a".repeat(40)).writeGenerated(
            BootstrapEvidenceCatalog.cases.first().descriptor,
            refusal,
        )
        assertEquals("refused", refusal.route.outcome)
    }

    private class FakeEvidenceBackendPort(
        private val events: MutableList<String>,
        capabilitiesAvailable: Boolean = true,
        private val missingReadback: Boolean = false,
        private val recordSubmission: Boolean = true,
    ) : EvidenceBackendPort {
        override val capabilities = if (capabilitiesAvailable) EvidenceCapabilities("test") else null
        override val deviceGeneration = 1L
        private var submissions = 0L

        override fun telemetry(): GPUBackendRuntimeTelemetry = GPUBackendRuntimeTelemetry(submissions = submissions)

        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort {
            events += "prepare-scene-frame"
            return object : EvidencePreparedFramePort {
                override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                    events += "render-frame"
                    if (!missingReadback && recordSubmission) submissions++
                    events += "wait-completion"
                    return if (missingReadback) {
                        EvidenceCompletedFrame("test-attempt", "Completed", "Succeeded", null, null, "wrong-request", null, emptyList(), emptyList(), mapOf("queue.submit" to 1L))
                    } else EvidenceCompletedFrame.succeeded(program.readbackRequestId, ByteArray(width * height * 4))
                }

                override fun close() {
                    events += "close-prepared-frame"
                }
            }
        }

        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
            events += "prepare-program"
            return if (context.descriptor.expectation is org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation.ShouldRefuse) {
                EvidenceProgramPreparation.Refused(
                    "unsupported.runtime_effect.custom_wgsl_not_registered",
                    "unregistered",
                    emptyList(),
                )
            } else {
                EvidenceProgramPreparation.Recorded(
                    "test.route",
                    PreparedEvidenceProgram(null, context.readbackRequestId),
                    emptyList(),
                )
            }
        }
    }
}
