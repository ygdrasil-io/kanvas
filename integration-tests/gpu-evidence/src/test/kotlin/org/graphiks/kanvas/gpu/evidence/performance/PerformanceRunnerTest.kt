package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCapabilities
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCompletedFrame
import org.graphiks.kanvas.gpu.evidence.runner.EvidencePreparedFramePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgramPreparation
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRecordingRequest
import org.graphiks.kanvas.gpu.evidence.runner.PreparedEvidenceProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class PerformanceRunnerTest {
    @Test fun `eligible runner records phase snapshots for one ten and ninety submissions`() {
        val evidenceCase = GpuEvidenceCatalog.cases.first { it.descriptor.id.value == "solid-card-stack" }
        val backend = CountingBackend(evidenceCase)
        var ticks = 0L
        val run = GpuEvidencePerformanceRunner(
            backend,
            "a".repeat(40),
            clock = MonotonicClock { ticks++ * 100L },
        ).run(evidenceCase)

        assertIs<PerformanceVerdict.EligibleMeasurement>(run.verdict)
        assertEquals(90, run.timingSamplesNanos.size)
        assertEquals(1L, run.telemetry.cold.delta.getValue("submissions").value)
        assertEquals(10L, run.telemetry.warmup.delta.getValue("submissions").value)
        assertEquals(90L, run.telemetry.measured.delta.getValue("submissions").value)
        assertEquals(MetricSource.Derived, run.telemetry.measured.delta.getValue("submissions").source)
    }

    @Test fun `hardware eligibility is preserved when capabilities are unavailable`() {
        val evidenceCase = GpuEvidenceCatalog.cases.first { it.descriptor.id.value == "solid-card-stack" }
        val run = GpuEvidencePerformanceRunner(
            CountingBackend(evidenceCase, null),
            "a".repeat(40),
        ).run(evidenceCase)

        assertIs<PerformanceVerdict.Unavailable>(run.verdict)
        assertEquals("GPU capabilities unavailable", run.verdict.reason)
        assertIs<PerformanceVerdict.EligibleMeasurement>(run.eligibility)
    }

    private class CountingBackend(private val evidenceCase: EvidenceCase, capabilitiesValue: EvidenceCapabilities? = EvidenceCapabilities("test")) : EvidenceBackendPort {
        private var submissions = 0L
        override val capabilities = capabilitiesValue
        override val adapter = EvidenceAdapter("Apple GPU", "Apple", "M2 Max", "Apple", "test", false)
        override val deviceGeneration = 1L
        override fun telemetry() = GPUBackendRuntimeTelemetry(
            submissions = submissions,
            commandBuffers = submissions,
            renderPasses = submissions,
            buffersCreated = submissions,
            texturesCreated = submissions,
            queueWrites = submissions,
            uniformSlabsCreated = submissions,
            bindGroupsCreated = submissions,
            passBatchPlans = submissions,
        )
        override fun prepare(program: org.graphiks.kanvas.gpu.evidence.runner.SceneProgram, context: EvidenceRecordingRequest) =
            EvidenceProgramPreparation.Recorded("test", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
            private val bytes = evidenceCase.oracle!!.render(width, height)
            override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                submissions++
                return EvidenceCompletedFrame.succeeded(program.readbackRequestId, bytes)
            }
            override fun renderCompletionOnly(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                submissions++
                return EvidenceCompletedFrame.succeeded(program.readbackRequestId, bytes)
            }
            override fun close() = Unit
        }
    }
}
