package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GPUTextOrderingTraceTest {
    @Test
    fun `default ordering trace report matches repo golden`() {
        val root = projectRoot()

        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-ordering-trace.json")).trimEnd(),
            defaultGPUTextOrderingTraceReportJson().trimEnd(),
        )
    }

    @Test
    fun `accepted A8 ordering trace links upload instance generation and draw tasks`() {
        val result = planGPUTextOrderingTrace(defaultGPUTextOrderingFixture())

        val accepted = assertIs<GPUTextOrderingPlanningResult.Accepted>(result)
        val trace = accepted.trace

        assertEquals("gpu-text-ordering-a8-0", trace.token.tokenId)
        assertEquals("atlas-page-generation-split.0", trace.token.subRunId)
        assertEquals("gpu-text-resource-a8-0", trace.token.resourcePlanId)
        assertEquals("gpu-text-upload-a8-page-0", trace.token.uploadPlanId)
        assertEquals("gpu-text-instance-buffer-a8-0", trace.token.instanceBufferPlanId)
        assertEquals("draw-text-a8-001", trace.token.drawTaskId)
        assertContains(trace.dependencyEdges.map { edge -> edge.edgeId }, "edge:upload-a8-page-0->draw-text-a8-001")
        assertContains(trace.dependencyEdges.map { edge -> edge.edgeId }, "edge:instance-a8-0->draw-text-a8-001")
        assertContains(trace.dependencyEdges.map { edge -> edge.edgeId }, "edge:generation-check-a8-page-0->draw-text-a8-001")
        assertEquals("validated", trace.generationChecks.single().status)
        assertEquals("resident", trace.resourceStates.single().residencyState)
    }

    @Test
    fun `ordering validator refuses unsafe ordering cases with stable diagnostics`() {
        val cases = listOf(
            defaultGPUTextOrderingFixture().copy(uploadBeforeSampleEdgePresent = false) to
                Triple(GPUTextRouteBlocker.UPLOAD_PLAN, "text.gpu.upload-before-sample-edge-missing", "unsupported.text.upload_plan_missing"),
            defaultGPUTextOrderingFixture().copy(observedAtlasGeneration = 4) to
                Triple(GPUTextRouteBlocker.STALE_GENERATION, "text.gpu.atlas-generation-stale", "unsupported.text.atlas_generation_stale"),
            defaultGPUTextOrderingFixture().copy(evictionBeforeDraw = true, evictionBarrierRecorded = false) to
                Triple(GPUTextRouteBlocker.EVICTION_BARRIER, "text.gpu.eviction-before-dependent-draw", "unsupported.text.eviction_before_dependent_draw"),
            defaultGPUTextOrderingFixture().copy(instanceUploadBeforeDraw = false) to
                Triple(GPUTextRouteBlocker.INSTANCE_UPLOAD_ORDER, "text.gpu.instance-upload-after-draw", "unsupported.text.instance_upload_after_draw"),
        )

        cases.forEach { (fixture, expected) ->
            val refusal = assertIs<GPUTextOrderingPlanningResult.Refused>(
                planGPUTextOrderingTrace(fixture),
            ).refusal

            assertEquals(expected.first, refusal.blocker)
            assertEquals(expected.second, refusal.handoffDiagnostic)
            assertEquals(expected.third, refusal.rendererDiagnostic)
            assertEquals("AtlasMaskSample", refusal.attemptedRoute)
            assertEquals("GlyphAtlasArtifact", refusal.artifactType)
        }
    }

    @Test
    fun `ordering trace reports snapshot caller supplied lists`() {
        val edges = mutableListOf(
            GPUTextOrderingEdge(
                edgeId = "edge:a->b",
                fromTaskId = "a",
                toTaskId = "b",
                edgeKind = "upload-before-sample",
                status = "satisfied",
            ),
        )
        val trace = GPUTextOrderingTrace(
            traceId = "trace:test",
            token = defaultGPUTextOrderingToken(),
            tasks = listOf(defaultGPUTextOrderingTask("a", "upload"), defaultGPUTextOrderingTask("b", "draw")),
            dependencyEdges = edges,
            generationChecks = listOf(defaultGPUTextGenerationCheck()),
            resourceStates = listOf(defaultGPUTextOrderingResourceState()),
            diagnostics = listOf("text.gpu.ordering-validated"),
        )

        edges += GPUTextOrderingEdge(
            edgeId = "edge:b->c",
            fromTaskId = "b",
            toTaskId = "c",
            edgeKind = "draw-before-eviction",
            status = "satisfied",
        )

        assertEquals(listOf("edge:a->b"), trace.dependencyEdges.map { edge -> edge.edgeId })
    }

    @Test
    fun `ordering trace report is deterministic and non promotional`() {
        val json = defaultGPUTextOrderingTraceReportJson()

        assertContains(json, """"ownerTickets":["KFONT-M11-008"]""")
        assertContains(json, """"classification":"GPU-gated"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"uploadExecution":"not-executed"""")
        assertContains(json, """"gpuTaskGraphExecuted":false""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Ordering trace leaked forbidden token $token: $json")
        }
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
