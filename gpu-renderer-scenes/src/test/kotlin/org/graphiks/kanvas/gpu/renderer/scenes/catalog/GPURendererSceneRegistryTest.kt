package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class GPURendererSceneRegistryTest {
    @Test
    fun `initial registry has business named scenes for M0 through M10`() {
        val scenes = GPURendererSceneRegistry.scenes
        assertEquals(expectedScenes.map { it.sceneId }, scenes.map { it.sceneId.value })
        assertEquals(emptyList(), GPURendererSceneRegistry.registry.validate())
        val roadmapLinks = scenes.flatMap { it.roadmapLinks }
        assertTrue(roadmapLinks.map { it.milestone }.containsAll((0..10).map { "M$it" }))
        assertTrue(roadmapLinks.mapNotNull { it.rStage }.containsAll(RStage.entries))
        assertTrue(scenes.any { scene -> scene.roadmapLinks.any { it.milestone == "M10" } })
    }

    @Test
    fun `initial registry matches exact business scene matrix`() {
        val scenes = GPURendererSceneRegistry.scenes
        assertEquals(expectedScenes.size, scenes.size)

        expectedScenes.zip(scenes).forEach { (expected, scene) ->
            assertEquals(expected.sceneId, scene.sceneId.value)
            assertEquals(expected.tags, scene.tags, "${expected.sceneId} tags")
            assertEquals(
                expected.commandFamilies,
                scene.commands.map { it.family },
                "${expected.sceneId} command families",
            )
            assertEquals(
                expected.roadmapLinks,
                scene.roadmapLinks.map { RoadmapExpectation(it.milestone, it.rStage, it.ticketId) },
                "${expected.sceneId} roadmap links",
            )
            assertEquals(SceneExpectation.ShouldRender, scene.expectation, "${expected.sceneId} expectation")
            assertTrue(expected.tags.isNotEmpty(), "${expected.sceneId} matrix tags must not be empty")
            assertTrue(
                expected.commandFamilies.isNotEmpty(),
                "${expected.sceneId} matrix command families must not be empty",
            )
            assertTrue(
                expected.roadmapLinks.isNotEmpty(),
                "${expected.sceneId} matrix roadmap links must not be empty",
            )
            assertTrue(scene.tags.isNotEmpty(), "${expected.sceneId} tags must not be empty")
            assertTrue(scene.commands.isNotEmpty(), "${expected.sceneId} commands must not be empty")
            assertTrue(scene.roadmapLinks.isNotEmpty(), "${expected.sceneId} roadmap links must not be empty")
        }
    }

    @Test
    fun `solid card stack is the first renderable command subset`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
        assertTrue(scene.roadmapLinks.any { it.rStage == RStage.R6 })
    }

    @Test
    fun `first route rollback panel is backed by controlled flag and rollback lanes`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("first-route-rollback-panel")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.LegacyComparison), scene.tags)
        assertEquals(
            listOf("KGPU-M1-003", "KGPU-M1-004"),
            scene.roadmapLinks.mapNotNull { it.ticketId },
        )
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(
            listOf(
                "legacy-before-route",
                "product-flagged-fillrect-route",
                "legacy-rollback-route",
                "unsupported-variant-refusal",
            ),
            fills.map { it.label },
        )
        assertTrue(fills.all { it.paintOrder > 0 })
    }

    @Test
    fun `runtime effect color tile is backed by registered SimpleRT scene payload`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("runtime-effect-color-tile")
        val command = assertIs<SceneCommand.RuntimeEffectTile>(scene.commands.single())

        assertTrue(command.hasFixturePayload)
        assertEquals("runtime.simple_rt", command.stableId)
        assertEquals("wgsl/runtime_simple_rt", command.wgslImplementationId)
        assertEquals("kotlin/simple_rt", command.cpuImplementationId)
        assertEquals("gColor", command.uniformName)
        assertEquals(0, command.uniformOffset)
        assertEquals(16, command.uniformSize)
    }

    @Test
    fun `release gate progress board is backed by bounded rrect scissor and gradient payloads`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("release-gate-progress-board")

        assertEquals(setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip), scene.tags)
        assertEquals(
            listOf("KGPU-M2-003", "KGPU-M2-004"),
            scene.roadmapLinks.mapNotNull { it.ticketId },
        )
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertIs<SceneCommand.LinearGradientRect>(scene.commands[3])
        assertIs<SceneCommand.FillRect>(scene.commands[4])
    }

    @Test
    fun `path coverage review board is backed by prepared M3 contracts and atlas refusal only`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("path-coverage-review-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Path, SceneTag.Stroke), scene.tags)
        assertEquals(
            listOf("KGPU-M3-001", "KGPU-M3-003", "KGPU-M3-004", "KGPU-M3-005"),
            scene.roadmapLinks.mapNotNull { it.ticketId },
        )
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertEquals(
            listOf(
                "path-fill-prepared-contract",
                "simple-stroke-prepared-contract",
                "bounded-clip-prepared-contract",
                "atlas-policy-refusal-gate",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4), fills.map { it.paintOrder })
        assertTrue(scene.roadmapLinks.none { it.ticketId == "KGPU-M3-002" })
    }

    @Test
    fun `translucent card overlap is backed by bounded SrcOver alpha rectangles`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("translucent-card-overlap")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.Blend), scene.tags)
        assertEquals(listOf("KGPU-M7-003"), scene.roadmapLinks.mapNotNull { it.ticketId })
        assertEquals(3, fills.size)
        assertTrue(fills.all { it.color.a < 1f })
        assertEquals(listOf(1, 2, 3), fills.map { it.paintOrder })
    }

    @Test
    fun `asset intake thumbnail grid is backed by decoded bitmap fixtures and upload ownership tickets`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("asset-intake-thumbnail-grid")

        assertEquals(setOf(SceneTag.Image, SceneTag.Clip, SceneTag.RRect), scene.tags)
        assertEquals(
            listOf("KGPU-M4-001", "KGPU-M4-002"),
            scene.roadmapLinks.mapNotNull { it.ticketId },
        )
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertIs<SceneCommand.BitmapRect>(scene.commands[3])
        assertIs<SceneCommand.BitmapRect>(scene.commands[4])
    }

    @Test
    fun `codec provenance gate board is backed by M4 dependency refusals only`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("codec-provenance-gate-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Image), scene.tags)
        assertEquals(listOf("KGPU-M4-003"), scene.roadmapLinks.mapNotNull { it.ticketId })
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertIs<SceneCommand.BitmapRect>(scene.commands[3])
        assertEquals(
            listOf(
                "codec-registry-snapshot",
                "dependency-codec-refusal",
                "missing-provenance-refusal",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(2, 3, 4), fills.map { it.paintOrder })
        assertTrue(scene.roadmapLinks.none { it.ticketId == "KGPU-M4-004" })
    }

    @Test
    fun `cache source ledger board is backed by visible source classification buckets`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("cache-source-ledger-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.Cache), scene.tags)
        assertEquals(listOf("KGPU-M9-001"), scene.roadmapLinks.mapNotNull { it.ticketId })
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(
            listOf(
                "observed-runtime-source",
                "observed-partial-source",
                "derived-report-source",
                "unavailable-runtime-source",
                "reporting-only-source",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), fills.map { it.paintOrder })
    }

    @Test
    fun `legacy inventory hygiene board is backed by inventory and archive hygiene tickets only`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("legacy-inventory-hygiene-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.LegacyComparison), scene.tags)
        assertEquals(
            listOf("KGPU-M10-001", "KGPU-M10-004"),
            scene.roadmapLinks.mapNotNull { it.ticketId },
        )
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertEquals(
            listOf(
                "legacy-route-ownership-inventoried",
                "replacement-status-inventoried",
                "archive-historical-only",
                "legacy-default-active",
                "shadow-parity-blocked",
                "retirement-blocked",
                "no-product-activation",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), fills.map { it.paintOrder })
        assertTrue(scene.roadmapLinks.none { it.ticketId == "KGPU-M10-002" || it.ticketId == "KGPU-M10-003" })
    }

    @Test
    fun `layered shadow card is backed by bounded shadow layer and drop shadow payloads`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("layered-shadow-card")
        assertIs<SceneCommand.Clear>(scene.commands[0])
        val layer = assertIs<SceneCommand.SaveLayer>(scene.commands[1])
        val filter = assertIs<SceneCommand.FilterNode>(scene.commands[2])

        assertTrue(layer.hasFixturePayload)
        assertEquals("bounded-shadow-card", layer.layerKind)
        assertEquals("shadow-card-layer", filter.inputLabel)
        assertEquals("drop-shadow", filter.kind?.wireName)
        assertEquals(0.72f, filter.strength)
    }

    @Test
    fun `filter dag refusal board is backed by stable refusal classes only`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("filter-dag-refusal-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.Filter), scene.tags)
        assertEquals(listOf("KGPU-M5-004"), scene.roadmapLinks.mapNotNull { it.ticketId })
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(
            listOf(
                "bounded-filter-candidate",
                "unbounded-intermediate-refusal",
                "recursive-dag-refusal",
                "picture-prepass-refusal",
                "cpu-filter-texture-refusal",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), fills.map { it.paintOrder })
    }

    @Test
    fun `receipt text run names real font inputs and unpromoted text routes`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("receipt-text-run")
        val command = assertIs<SceneCommand.TextRun>(scene.commands.single())

        assertTrue(command.hasFixturePayload)
        assertEquals("TOTAL 42.00", command.text)
        assertEquals("Liberation Sans", command.fontFamily)
        assertEquals("kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf", command.fontSourceId)
        assertEquals("simple-latin", command.shapingMode)
        assertEquals("font.glyph.outline-path", command.glyphRoute)
        assertEquals("webgpu.text.glyph-atlas.simple-latin", command.webGpuCandidateRoute)
        assertEquals("unsupported.text.draw_run_route_unavailable", command.fallbackReason)
    }

    @Test
    fun `text handoff boundary board is backed by typed artifacts and refused route gates only`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("text-handoff-boundary-board")
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertEquals(setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Text), scene.tags)
        assertEquals(listOf("KGPU-M6-001"), scene.roadmapLinks.mapNotNull { it.ticketId })
        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertEquals(
            listOf(
                "typed-artifact-reference",
                "renderer-payload-accepted",
                "draw-text-run-route-refused",
                "cpu-texture-fallback-refused",
            ),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4), fills.map { it.paintOrder })
        assertTrue(scene.roadmapLinks.none { it.ticketId == "KGPU-M6-002" || it.ticketId == "KGPU-M6-003" })
    }

    @Test
    fun `mesh ribbon is backed by bounded ribbon strip payload`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("mesh-ribbon")
        assertIs<SceneCommand.Clear>(scene.commands[0])
        val command = assertIs<SceneCommand.MeshRibbon>(scene.commands[1])

        assertTrue(command.hasFixturePayload)
        assertEquals("bounded-ribbon-strip", command.meshKind)
        assertEquals(28f, command.thickness)
    }

    private data class SceneExpectationRow(
        val sceneId: String,
        val tags: Set<SceneTag>,
        val commandFamilies: List<String>,
        val roadmapLinks: List<RoadmapExpectation>,
    )

    private data class RoadmapExpectation(
        val milestone: String,
        val rStage: RStage? = null,
        val ticketId: String? = null,
    )

    private companion object {
        val expectedScenes = listOf(
            SceneExpectationRow(
                sceneId = "solid-card-stack",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("clear", "fill-rect", "fill-rect", "fill-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M0", RStage.R0),
                    RoadmapExpectation("M1", RStage.R1),
                    RoadmapExpectation("M1", RStage.R2),
                    RoadmapExpectation("M1", RStage.R3),
                    RoadmapExpectation("M1", RStage.R4),
                    RoadmapExpectation("M1", RStage.R5),
                    RoadmapExpectation("M1", RStage.R6),
                ),
            ),
            SceneExpectationRow(
                sceneId = "first-route-rollback-panel",
                tags = setOf(SceneTag.Rect, SceneTag.LegacyComparison),
                commandFamilies = listOf("clear", "fill-rect", "fill-rect", "fill-rect", "fill-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M1", ticketId = "KGPU-M1-003"),
                    RoadmapExpectation("M1", ticketId = "KGPU-M1-004"),
                ),
            ),
            SceneExpectationRow(
                sceneId = "rounded-panel-gradient",
                tags = setOf(SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip),
                commandFamilies = listOf("fill-rrect", "clip", "linear-gradient-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M2", RStage.R1),
                    RoadmapExpectation("M2", RStage.R2),
                    RoadmapExpectation("M2", RStage.R3),
                ),
            ),
            SceneExpectationRow(
                sceneId = "release-gate-progress-board",
                tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip),
                commandFamilies = listOf("clear", "fill-rrect", "clip", "linear-gradient-rect", "fill-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M2", ticketId = "KGPU-M2-003"),
                    RoadmapExpectation("M2", ticketId = "KGPU-M2-004"),
                ),
            ),
            SceneExpectationRow(
                sceneId = "path-badge-and-stroke",
                tags = setOf(SceneTag.RRect, SceneTag.Rect),
                commandFamilies = listOf("fill-rrect", "fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M3")),
            ),
            SceneExpectationRow(
                sceneId = "path-coverage-review-board",
                tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Path, SceneTag.Stroke),
                commandFamilies = listOf(
                    "clear",
                    "fill-rrect",
                    "clip",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(
                    RoadmapExpectation("M3", ticketId = "KGPU-M3-001"),
                    RoadmapExpectation("M3", ticketId = "KGPU-M3-003"),
                    RoadmapExpectation("M3", ticketId = "KGPU-M3-004"),
                    RoadmapExpectation("M3", ticketId = "KGPU-M3-005"),
                ),
            ),
            SceneExpectationRow(
                sceneId = "clipped-avatar-grid",
                tags = setOf(SceneTag.Clip, SceneTag.Image),
                commandFamilies = listOf("clip", "bitmap-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M3"), RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "texture-swatch-board",
                tags = setOf(SceneTag.Image),
                commandFamilies = listOf("bitmap-rect", "bitmap-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M4")),
            ),
            SceneExpectationRow(
                sceneId = "asset-intake-thumbnail-grid",
                tags = setOf(SceneTag.Image, SceneTag.Clip, SceneTag.RRect),
                commandFamilies = listOf("clear", "fill-rrect", "clip", "bitmap-rect", "bitmap-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M4", ticketId = "KGPU-M4-001"),
                    RoadmapExpectation("M4", ticketId = "KGPU-M4-002"),
                ),
            ),
            SceneExpectationRow(
                sceneId = "codec-provenance-gate-board",
                tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Image),
                commandFamilies = listOf(
                    "clear",
                    "fill-rrect",
                    "clip",
                    "bitmap-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(RoadmapExpectation("M4", ticketId = "KGPU-M4-003")),
            ),
            SceneExpectationRow(
                sceneId = "layered-shadow-card",
                tags = setOf(SceneTag.Layer, SceneTag.Filter),
                commandFamilies = listOf("clear", "save-layer", "filter-node"),
                roadmapLinks = listOf(RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "filtered-photo-chip",
                tags = setOf(SceneTag.Filter, SceneTag.Image),
                commandFamilies = listOf("bitmap-rect", "filter-node"),
                roadmapLinks = listOf(RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "filter-dag-refusal-board",
                tags = setOf(SceneTag.Rect, SceneTag.Filter),
                commandFamilies = listOf(
                    "clear",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(RoadmapExpectation("M5", ticketId = "KGPU-M5-004")),
            ),
            SceneExpectationRow(
                sceneId = "receipt-text-run",
                tags = setOf(SceneTag.Text),
                commandFamilies = listOf("text-run"),
                roadmapLinks = listOf(RoadmapExpectation("M6")),
            ),
            SceneExpectationRow(
                sceneId = "text-handoff-boundary-board",
                tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.Text),
                commandFamilies = listOf(
                    "clear",
                    "fill-rrect",
                    "clip",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(RoadmapExpectation("M6", ticketId = "KGPU-M6-001")),
            ),
            SceneExpectationRow(
                sceneId = "runtime-effect-color-tile",
                tags = setOf(SceneTag.RuntimeEffect),
                commandFamilies = listOf("runtime-effect"),
                roadmapLinks = listOf(RoadmapExpectation("M7")),
            ),
            SceneExpectationRow(
                sceneId = "blend-mode-strip",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M7")),
            ),
            SceneExpectationRow(
                sceneId = "translucent-card-overlap",
                tags = setOf(SceneTag.Rect, SceneTag.Blend),
                commandFamilies = listOf("clear", "fill-rect", "fill-rect", "fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M7", ticketId = "KGPU-M7-003")),
            ),
            SceneExpectationRow(
                sceneId = "mesh-ribbon",
                tags = setOf(SceneTag.Vertices),
                commandFamilies = listOf("clear", "vertices"),
                roadmapLinks = listOf(RoadmapExpectation("M8")),
            ),
            SceneExpectationRow(
                sceneId = "cache-pressure-deck",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect", "fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M9")),
            ),
            SceneExpectationRow(
                sceneId = "cache-source-ledger-board",
                tags = setOf(SceneTag.Rect, SceneTag.Cache),
                commandFamilies = listOf(
                    "clear",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(RoadmapExpectation("M9", ticketId = "KGPU-M9-001")),
            ),
            SceneExpectationRow(
                sceneId = "legacy-route-comparison",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M10")),
            ),
            SceneExpectationRow(
                sceneId = "legacy-inventory-hygiene-board",
                tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Clip, SceneTag.LegacyComparison),
                commandFamilies = listOf(
                    "clear",
                    "fill-rrect",
                    "clip",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                    "fill-rect",
                ),
                roadmapLinks = listOf(
                    RoadmapExpectation("M10", ticketId = "KGPU-M10-001"),
                    RoadmapExpectation("M10", ticketId = "KGPU-M10-004"),
                ),
            ),
        )
    }
}
