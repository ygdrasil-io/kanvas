package org.graphiks.kanvas.gpu.evidence.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceCapabilities
import org.graphiks.kanvas.gpu.evidence.runner.EvidencePreparedFramePort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgramPreparation
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRecordingRequest
import org.graphiks.kanvas.gpu.evidence.runner.KanvasSurfaceRenderSession
import org.graphiks.kanvas.gpu.evidence.runner.ScenePreparation
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

@OptIn(ExperimentalUnsignedTypes::class)
class PerformanceRunnerTest {
    @Test fun `cold and measured timings enclose only the public Surface render call`() {
        val events = mutableListOf<String>()
        val fixture = surfaceFixture(events = events)
        var tick = 0

        val run = GpuEvidencePerformanceRunner(
            fixture.backend,
            "a".repeat(40),
            clock = MonotonicClock { events += "clock[$tick]"; tick++.toLong() },
        ).run(fixture.evidenceCase)

        assertIs<PerformanceVerdict.EligibleMeasurement>(run.verdict)
        assertEquals(90, run.timingSamplesNanos.size)
        assertTimedRender(events, "clock[0]", "clock[1]")
        (2 until 182 step 2).forEach { start -> assertTimedRender(events, "clock[$start]", "clock[${start + 1}]") }
        val coldEnd = events.indexOf("clock[1]")
        val firstMeasuredStart = events.indexOf("clock[2]")
        assertEquals(10, events.subList(coldEnd + 1, firstMeasuredStart).count { it == "surface.render" })
    }

    @Test fun `eligible runner measures one reusable Surface session across cold warmup and measured phases`() {
        val fixture = surfaceFixture()
        var ticks = 0L

        val run = GpuEvidencePerformanceRunner(
            fixture.backend,
            "a".repeat(40),
            clock = MonotonicClock { ticks++ * 100L },
        ).run(fixture.evidenceCase)

        assertIs<PerformanceVerdict.EligibleMeasurement>(run.verdict)
        assertEquals(1, fixture.recordings)
        assertEquals(1, fixture.opens)
        assertEquals(101, fixture.renders)
        assertEquals(90, run.timingSamplesNanos.size)
        assertEquals(1L, run.telemetry.cold.delta.getValue("submissions").value)
        assertEquals(10L, run.telemetry.warmup.delta.getValue("submissions").value)
        assertEquals(90L, run.telemetry.measured.delta.getValue("submissions").value)
        assertEquals(101L, run.telemetry.total!!.delta.getValue("submissions").value)
        assertEquals(MetricSource.Derived, run.telemetry.measured.delta.getValue("submissions").source)
    }

    @Test fun `cold Surface pixels are compared to the independent CPU oracle`() {
        val fixture = surfaceFixture(wrongColdPixels = true)

        val run = GpuEvidencePerformanceRunner(fixture.backend, "a".repeat(40)).run(fixture.evidenceCase)

        val failure = assertIs<PerformanceVerdict.Failed>(run.verdict)
        assertEquals("cold validation CPU oracle comparison failed", failure.reason)
    }

    @Test fun `non Surface render program is rejected before measurement`() {
        val catalogCase = GpuEvidenceCatalog.renderCases.first()
        assertFailsWith<IllegalArgumentException> {
            catalogCase.copy(program = SceneProgram {
                ScenePreparation.Refused("test", "unused", emptyList())
            })
        }
    }

    @Test fun `zero Surface runtime submissions fail measurement deterministically`() {
        val fixture = surfaceFixture(submissionDelta = 0L)

        val run = GpuEvidencePerformanceRunner(fixture.backend, "a".repeat(40)).run(fixture.evidenceCase)

        assertEquals("Surface render did not produce exactly one runtime submission", assertIs<PerformanceVerdict.Failed>(run.verdict).reason)
    }

    @Test fun `zero Surface draw or pipeline work fails measurement deterministically`() {
        val noDrawFixture = surfaceFixture(draws = 0)
        val noDraw = GpuEvidencePerformanceRunner(noDrawFixture.backend, "a".repeat(40)).run(noDrawFixture.evidenceCase)
        val noPipelineFixture = surfaceFixture(pipelines = 0)
        val noPipeline = GpuEvidencePerformanceRunner(noPipelineFixture.backend, "a".repeat(40)).run(noPipelineFixture.evidenceCase)

        assertEquals("Surface render did not report draw work", assertIs<PerformanceVerdict.Failed>(noDraw.verdict).reason)
        assertEquals("Surface render did not report pipeline work", assertIs<PerformanceVerdict.Failed>(noPipeline.verdict).reason)
    }

    @Test fun `malformed Surface dimensions or RGBA byte size fail measurement deterministically`() {
        val malformedDimensions = surfaceFixture(resultWidth = 1)
        val malformedPixels = surfaceFixture(malformedPixelCount = true)

        val dimensionsRun = GpuEvidencePerformanceRunner(malformedDimensions.backend, "a".repeat(40)).run(malformedDimensions.evidenceCase)
        val pixelsRun = GpuEvidencePerformanceRunner(malformedPixels.backend, "a".repeat(40)).run(malformedPixels.evidenceCase)

        assertEquals("Surface render dimensions did not match the evidence descriptor", assertIs<PerformanceVerdict.Failed>(dimensionsRun.verdict).reason)
        assertEquals("Surface render did not produce descriptor-sized RGBA pixels", assertIs<PerformanceVerdict.Failed>(pixelsRun.verdict).reason)
    }

    @Test fun `hardware eligibility is preserved when capabilities are unavailable`() {
        val fixture = surfaceFixture(capabilities = null)

        val run = GpuEvidencePerformanceRunner(fixture.backend, "a".repeat(40)).run(fixture.evidenceCase)

        assertIs<PerformanceVerdict.Unavailable>(run.verdict)
        assertEquals("GPU capabilities unavailable", run.verdict.reason)
        assertIs<PerformanceVerdict.EligibleMeasurement>(run.eligibility)
    }

    private fun surfaceFixture(
        capabilities: EvidenceCapabilities? = EvidenceCapabilities("test"),
        submissionDelta: Long = 1L,
        wrongColdPixels: Boolean = false,
        draws: Int = 1,
        pipelines: Int = 1,
        resultWidth: Int? = null,
        resultHeight: Int? = null,
        malformedPixelCount: Boolean = false,
        events: MutableList<String>? = null,
    ): SurfaceFixture {
        val catalogCase = GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }
        val probe = SurfaceTelemetryProbe(capabilities, submissionDelta, events)
        var recordings = 0
        var opens = 0
        var renders = 0
        val surfaceProgram = KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { width, height, _ ->
            opens++
            recordings++
            val output = requireNotNull(catalogCase.oracle).render(width, height).also {
                if (wrongColdPixels) it[0] = (it[0].toInt() xor 0xff).toByte()
            }
            object : KanvasSurfaceRenderSession {
                override fun render(): RenderResult {
                    renders++
                    events?.add("surface.render")
                    probe.observeRender()
                    return RenderResult(
                        if (malformedPixelCount) output.copyOf(output.size - 1).toUByteArray() else output.toUByteArray(),
                        resultWidth ?: width, resultHeight ?: height,
                        diagnostics = Diagnostics(), stats = RenderStats(1, 0, pipelines, draws, 1f),
                    )
                }
            }
        })
        return SurfaceFixture(catalogCase.copy(program = surfaceProgram), probe, { recordings }, { opens }, { renders })
    }

    private class SurfaceFixture(
        val evidenceCase: EvidenceCase,
        val backend: SurfaceTelemetryProbe,
        private val recordingCount: () -> Int,
        private val openCount: () -> Int,
        private val renderCount: () -> Int,
    ) {
        val recordings get() = recordingCount()
        val opens get() = openCount()
        val renders get() = renderCount()
    }

    private class SurfaceTelemetryProbe(
        override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("test"),
        private val submissionDelta: Long = 1L,
        private val events: MutableList<String>? = null,
    ) : EvidenceBackendPort {
        var submissions = 0L
            private set
        override val adapter = EvidenceAdapter("Apple GPU", "Apple", "M2 Max", "Apple", "test", false)
        override val deviceGeneration = 1L
        override fun telemetry(): GPUBackendRuntimeTelemetry {
            events?.add("telemetry")
            return GPUBackendRuntimeTelemetry(
                submissions = submissions, commandBuffers = submissions, renderPasses = submissions,
                buffersCreated = submissions, texturesCreated = submissions, queueWrites = submissions,
                uniformSlabsCreated = submissions, bindGroupsCreated = submissions, passBatchPlans = submissions,
            )
        }
        fun observeRender() { submissions += submissionDelta }
        override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation =
            error("performance Surface measurement must not prepare scenes")
        override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort =
            error("performance Surface measurement must not prepare frames")
    }

    private fun assertTimedRender(events: List<String>, start: String, end: String) {
        val startIndex = events.indexOf(start)
        val endIndex = events.indexOf(end)
        assertEquals(listOf(start, "surface.render", end), events.subList(startIndex, endIndex + 1))
    }
}
