package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GPUTextSubRunPlanTest {
    @Test
    fun `default subrun plan report matches repo golden`() {
        val root = projectRoot()
        val expected = Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-subrun-plan.json"))

        assertEquals(expected.trimEnd(), defaultGPUTextSubRunPlanReportJson().trimEnd())
    }

    @Test
    fun `planner splits A8 atlas entries by page and generation without reordering glyphs`() {
        val report = defaultGPUTextSubRunPlanReport()
        val scenario = report.scenario("atlas-page-generation-split")

        assertEquals(
            listOf("glyphs:0..0", "glyphs:1..1", "glyphs:2..2"),
            scenario.subRuns.map { subRun -> subRun.sourceGlyphRange },
        )
        assertEquals(listOf("atlas-page", "atlas-page", "atlas-generation"), scenario.subRuns.flatMap { it.splitReasons })
        assertEquals(listOf("a8-page-0", "a8-page-1", "a8-page-0"), scenario.subRuns.map { it.atlasPageId })
        assertEquals(listOf(3, 3, 4), scenario.subRuns.map { it.atlasGeneration })
        assertEquals(listOf("sample-after-upload:0", "sample-after-upload:1", "sample-after-upload:2"), scenario.subRuns.map { it.orderingToken })
    }

    @Test
    fun `planner preserves unsafe clip layer destination and budget barriers`() {
        val report = defaultGPUTextSubRunPlanReport()
        val scenario = report.scenario("clip-layer-budget-split")

        assertEquals(
            listOf("clip", "layer", "destination-read", "instance-budget"),
            scenario.subRuns.flatMap { subRun -> subRun.splitReasons },
        )
        assertEquals(listOf("clip:scissor-a", "clip:scissor-b", "clip:scissor-b", "clip:scissor-b"), scenario.subRuns.map { it.clipKey })
        assertEquals(listOf("layer:root", "layer:root", "layer:savelayer-a", "layer:savelayer-a"), scenario.subRuns.map { it.layerKey })
        assertEquals(listOf(false, false, true, true), scenario.subRuns.map { it.destinationReadRequired })
        assertEquals(listOf("glyphs:0..0", "glyphs:1..1", "glyphs:2..2", "glyphs:3..3"), scenario.subRuns.map { it.sourceGlyphRange })
    }

    @Test
    fun `planner emits refused subruns for unsupported representations with stable diagnostics`() {
        val report = defaultGPUTextSubRunPlanReport()
        val scenario = report.scenario("representation-refusal-split")

        assertEquals(listOf("accepted", "refused", "refused", "refused"), scenario.subRuns.map { it.routeOutcome })
        assertEquals(
            listOf(
                "unsupported.text.sdf_route_unavailable",
                "unsupported.text.color_plan_unsupported",
                "unsupported.text.bitmap_route_unsupported",
            ),
            scenario.subRuns.filter { it.routeOutcome == "refused" }.map { it.rendererDiagnostic },
        )
        assertEquals(
            listOf("A8MaskAtlas", "SDFMaskAtlas", "COLRColorGlyph", "BitmapGlyph"),
            scenario.subRuns.map { it.representation },
        )
    }

    @Test
    fun `canonical subrun report is deterministic and non promotional`() {
        val json = defaultGPUTextSubRunPlanReportJson()

        assertEquals(json, defaultGPUTextSubRunPlanReportJson())
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.GPUTextSubRunPlanReport.v1"""")
        assertContains(json, """"ownerTickets":["KFONT-M11-006"]""")
        assertContains(json, """"classification":"GPU-gated"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"rendererDiagnostic":"unsupported.text.instance_buffer_budget_exceeded"""")
        assertContains(json, """"nonClaims":["no-complete-target-support-claim","no-broad-gpu-text-support-claim","no-dftext-retirement","no-executed-gpu-upload-claim"]""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Subrun report leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `subrun report snapshots caller supplied lists`() {
        val ownerTickets = mutableListOf("KFONT-M11-006")
        val nonClaims = mutableListOf("no-complete-target-support-claim")
        val scenario = GPUTextSubRunScenario(
            scenarioId = "snapshot-scenario",
            splitKind = "snapshot",
            subRuns = listOf(
                GPUTextSubRunPlan(
                    subRunId = "snapshot-scenario.0",
                    parentCommandId = "draw-text-a8-001",
                    sourceGlyphRange = "glyphs:0..0",
                    representation = "A8MaskAtlas",
                    route = "AtlasMaskSample",
                    renderStep = "A8TextMaskStep",
                    routeOutcome = "accepted",
                    atlasPageId = "a8-page-0",
                    atlasGeneration = 1,
                    materialKey = "material:text-black",
                    clipKey = "clip:scissor-a",
                    layerKey = "layer:root",
                    destinationReadRequired = false,
                    instanceBudgetGlyphLimit = 1,
                    orderingToken = "sample-after-upload:0",
                    splitReasons = listOf("atlas-page"),
                    handoffDiagnostic = null,
                    rendererDiagnostic = null,
                ),
            ),
        )
        val report = GPUTextSubRunPlanReport(
            ownerTickets = ownerTickets,
            classification = "GPU-gated",
            scenarios = listOf(scenario),
            nonClaims = nonClaims,
        )

        ownerTickets += "KFONT-M11-007"
        nonClaims += "no-broad-gpu-text-support-claim"

        assertEquals(listOf("KFONT-M11-006"), report.ownerTickets)
        assertEquals(listOf("no-complete-target-support-claim"), report.nonClaims)
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
