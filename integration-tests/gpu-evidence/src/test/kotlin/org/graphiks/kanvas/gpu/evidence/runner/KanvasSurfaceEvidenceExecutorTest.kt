package org.graphiks.kanvas.gpu.evidence.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

@OptIn(ExperimentalUnsignedTypes::class)
class KanvasSurfaceEvidenceExecutorTest {
    @Test fun `surface success becomes rendered evidence only with one runtime submission`() {
        val telemetry = FakeTelemetryProbe()
        val rendered = executor(telemetry).execute(case(telemetry, RenderResult(
            pixels = ubyteArrayOf(0x11u, 0x22u, 0x33u, 0xffu), width = 1, height = 1,
            diagnostics = Diagnostics(), stats = RenderStats(1, 0, 1, 1, 1f),
        )))

        val observation = assertIs<EvidenceExecutionResult.Observed>(rendered).observation
        val surface = assertIs<SceneObservation.Rendered>(observation)
        assertEquals("kanvas.surface.render", surface.route.routeId)
        assertEquals("Completed", surface.route.furthestPhase)
        assertEquals("rendered", surface.route.outcome)
        assertEquals(1L, surface.route.structuralCounters["queue.submit"])
        assertTrue(surface.route.structuralCounters.getValue("render.draw") > 0L)
        assertTrue(surface.route.structuralCounters.getValue("render.pipelineBind") > 0L)
        assertEquals(1L, surface.route.runtimeTelemetryDelta.submissions)
        assertTrue(surface.comparison.passed)
    }

    @Test fun `surface route preserves the prepared structural sequence`() {
        val telemetry = FakeTelemetryProbe()
        val observed = assertIs<SceneObservation.Rendered>(assertIs<EvidenceExecutionResult.Observed>(executor(telemetry).execute(case(telemetry, renderedResult().copy(structuralSteps = listOf("HardClipStencilProducer", "AnalyticRRect"))))).observation)

        assertEquals(listOf("HardClipStencilProducer", "AnalyticRRect"), observed.route.structuralEvents.map { it.kind })
    }

    @Test fun `surface result with zero runtime submissions is an execution failure`() {
        val telemetry = FakeTelemetryProbe(submissionDelta = 0)
        val result = executor(telemetry).execute(case(telemetry, renderedResult()))

        assertEquals("failed.gpu.execution", assertIs<EvidenceExecutionResult.ExecutionFailure>(result).stableReasonCode)
    }

    @Test fun `surface result with zero draw or pipeline work is an execution failure`() {
        val noDrawTelemetry = FakeTelemetryProbe()
        val noPipelineTelemetry = FakeTelemetryProbe()
        val noDraw = executor(noDrawTelemetry).execute(case(noDrawTelemetry, renderedResult(draws = 0)))
        val noPipeline = executor(noPipelineTelemetry).execute(case(noPipelineTelemetry, renderedResult(pipelines = 0)))

        assertEquals("failed.gpu.execution", assertIs<EvidenceExecutionResult.ExecutionFailure>(noDraw).stableReasonCode)
        assertEquals("failed.gpu.execution", assertIs<EvidenceExecutionResult.ExecutionFailure>(noPipeline).stableReasonCode)
    }

    @Test fun `surface exception is a deterministic execution failure`() {
        val telemetry = FakeTelemetryProbe()
        val result = executor(telemetry).execute(case(telemetry, failure = IllegalStateException("surface unavailable")))

        val failure = assertIs<EvidenceExecutionResult.ExecutionFailure>(result)
        assertEquals("failed.kanvas.surface", failure.stableReasonCode)
        assertEquals("surface unavailable", failure.message)
        assertEquals("failed", failure.route.outcome)
        assertNull(failure.route.furthestPhase)
    }

    @Test fun `surface setup exception has no completed route phase`() {
        val telemetry = FakeTelemetryProbe()
        val result = executor(telemetry).execute(case(telemetry, setupFailure = IllegalStateException("surface setup unavailable")))

        val failure = assertIs<EvidenceExecutionResult.ExecutionFailure>(result)
        assertEquals("failed.kanvas.surface", failure.stableReasonCode)
        assertNull(failure.route.furthestPhase)
    }

    @Test fun `terminal Surface messages become independent zero-submission refusals only when strictly parseable`() {
        val observedTelemetry = FakeTelemetryProbe(submissionDelta = 0)
        val observed = executor(observedTelemetry).execute(refusalCase(observedTelemetry, "unsupported.observed: rejected"))
        assertEquals("unsupported.observed", assertIs<SceneObservation.Refused>(assertIs<EvidenceExecutionResult.Observed>(observed).observation).stableReasonCode)

        val malformedTelemetry = FakeTelemetryProbe(submissionDelta = 0)
        val malformed = executor(malformedTelemetry).execute(refusalCase(malformedTelemetry, "unsupported observed: rejected"))
        assertEquals("failed.kanvas.surface", assertIs<EvidenceExecutionResult.ExecutionFailure>(malformed).stableReasonCode)

        val submittedTelemetry = FakeTelemetryProbe()
        val submitted = executor(submittedTelemetry).execute(refusalCase(submittedTelemetry, "unsupported.observed: rejected", submitBeforeFailure = true))
        assertEquals("failed.kanvas.surface", assertIs<EvidenceExecutionResult.ExecutionFailure>(submitted).stableReasonCode)
    }

    @Test fun `surface program records once and reuses the same surface session`() {
        var recordings = 0
        val program = KanvasSurfaceProgram(routeId = "kanvas.surface.render", record = { recordings++ })

        val first = program.openSession(1, 1)
        val second = program.openSession(1, 1)

        assertEquals(1, recordings)
        assertTrue(first === second)
    }

    private fun executor(telemetry: FakeTelemetryProbe) =
        KanvasSurfaceEvidenceExecutor(backend = telemetry, sourceCommit = "a".repeat(40))

    private fun case(
        telemetry: FakeTelemetryProbe,
        result: RenderResult = renderedResult(),
        failure: Exception? = null,
        setupFailure: Exception? = null,
    ) = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("surface-contract"), "Surface contract", "Surface execution contract.",
            1, 1, 1L, emptySet(), EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("literal-rgba", 1), ComparisonPolicy(0, 100.0, 1, "Exact literal RGBA8 oracle."), emptySet(),
        ),
        program = KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
            setupFailure?.let { throw it }
            object : KanvasSurfaceRenderSession {
                override fun render(): RenderResult {
                    failure?.let { throw it }
                    telemetry.observeRender()
                    return result
                }
            }
        }),
        oracle = CpuOracle { _, _ -> byteArrayOf(0x11, 0x22, 0x33, 0xff.toByte()) },
    )

    private fun renderedResult(draws: Int = 1, pipelines: Int = 1) = RenderResult(
        pixels = ubyteArrayOf(0x11u, 0x22u, 0x33u, 0xffu), width = 1, height = 1,
        diagnostics = Diagnostics(), stats = RenderStats(1, 0, pipelines, draws, 1f),
    )

    private fun refusalCase(telemetry: FakeTelemetryProbe, message: String, submitBeforeFailure: Boolean = false) = EvidenceCase(
        descriptor = EvidenceSceneDescriptor(
            EvidenceSceneId("surface-refusal"), "Surface refusal", "Surface terminal refusal contract.",
            1, 1, 1L, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.expected"), OraclePolicy.StableRefusal, null, emptySet(),
        ),
        program = KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
            KanvasSurfaceRenderSession {
                if (submitBeforeFailure) telemetry.observeRender()
                throw IllegalStateException(message)
            }
        }),
        oracle = null,
    )

    private class FakeTelemetryProbe(private val submissionDelta: Long = 1L) : EvidenceBackendPort {
        override val capabilities = EvidenceCapabilities("fake")
        override val deviceGeneration = 7L
        private var submissions = 4L
        override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
        fun observeRender() { submissions += submissionDelta }
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = error("Surface programs must not be prepared")
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("Surface programs must not create prepared frames")
    }
}
