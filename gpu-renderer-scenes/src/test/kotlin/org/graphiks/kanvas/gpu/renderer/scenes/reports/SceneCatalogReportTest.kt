package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.CandidateScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.CandidateSceneFrenchText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.CandidateSceneStatus
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneHumanDocs
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneDimensions
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneId
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneRoadmapLink
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneTag

class SceneCatalogReportTest {
    @Test
    fun `catalog report contains business ids and roadmap correspondence`() {
        val markdown = SceneCatalogReport(GPURendererSceneRegistry.scenes).toMarkdown()
        assertContains(markdown, "| `solid-card-stack` | Solid Card Stack |")
        assertContains(markdown, "| `product-route-smoke-lanes` | Product Route Smoke Lanes |")
        assertContains(markdown, "| `bitmap-sampler-matrix` | Bitmap Sampler Matrix |")
        assertContains(markdown, "| `runtime-effect-uniform-ladder` | Runtime Effect Uniform Ladder |")
        assertContains(markdown, "| `mesh-ribbon-depth-stack` | Mesh Ribbon Depth Stack |")
        assertContains(markdown, "| `gradient-tile-mode-boundary` | Gradient Tile Mode Boundary |")
        assertContains(markdown, "| `path-aa-stroke-join-board` | Path AA Stroke Join Board |")
        assertContains(markdown, "| `layer-filter-chain-board` | Layer Filter Chain Board |")
        assertContains(markdown, "| `legacy-parity-snapshot-board` | Legacy Parity Snapshot Board |")
        assertContains(markdown, "KGPU M0,M1")
        assertContains(markdown, "`KGPU-M0-007`,`KGPU-M1-001`,`KGPU-M1-002`")
        assertContains(markdown, "`KGPU-M1-003`,`KGPU-M1-004`")
        assertContains(markdown, "`KGPU-M2-001`,`KGPU-M2-002`")
        assertContains(markdown, "`KGPU-M2-003`,`KGPU-M2-004`")
        assertContains(markdown, "`KGPU-M3-001`,`KGPU-M3-003`,`KGPU-M3-004`,`KGPU-M3-005`")
        assertContains(markdown, "`KGPU-M3-002`")
        assertContains(markdown, "`KGPU-M4-001`,`KGPU-M4-002`")
        assertContains(markdown, "`KGPU-M4-003`")
        assertContains(markdown, "`KGPU-M4-004`")
        assertContains(markdown, "`KGPU-M5-001`")
        assertContains(markdown, "`KGPU-M5-002`")
        assertContains(markdown, "`KGPU-M5-004`")
        assertContains(markdown, "`KGPU-M6-001`")
        assertContains(markdown, "`KGPU-M6-002`")
        assertContains(markdown, "`KGPU-M6-003`")
        assertContains(markdown, "`KGPU-M6-004`")
        assertContains(markdown, "`KGPU-M7-001`")
        assertContains(markdown, "`KGPU-M7-002`")
        assertContains(markdown, "`KGPU-M7-003`")
        assertContains(markdown, "`KGPU-M7-004`")
        assertContains(markdown, "`KGPU-M8-001`,`KGPU-M8-002`,`KGPU-M8-003`")
        assertContains(markdown, "`KGPU-M9-001`")
        assertContains(markdown, "`KGPU-M9-002`")
        assertContains(markdown, "`KGPU-M9-003`")
        assertContains(markdown, "`KGPU-M10-001`,`KGPU-M10-004`")
        assertContains(markdown, "`KGPU-M10-002`")
        assertContains(markdown, "`KGPU-M10-003`")
        assertContains(markdown, "R0,R1,R2,R3,R4,R5,R6")
        assertContains(markdown, "`ShouldRender`")
    }

    @Test
    fun `catalog report writer creates markdown and json`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-report")
        SceneCatalogReport(GPURendererSceneRegistry.scenes).writeTo(root)

        assertTrue(root.resolve("catalog.md").readText().contains("GPU Renderer Scene Catalog"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"solid-card-stack\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"product-route-smoke-lanes\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"bitmap-sampler-matrix\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"runtime-effect-uniform-ladder\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"mesh-ribbon-depth-stack\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"gradient-tile-mode-boundary\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"path-aa-stroke-join-board\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"layer-filter-chain-board\""))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"legacy-parity-snapshot-board\""))
        assertTrue(
            root.resolve("catalog.json").readText()
                .contains("\"ticketIds\": [\"KGPU-M0-007\",\"KGPU-M1-001\",\"KGPU-M1-002\"]"),
        )
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M1-003\",\"KGPU-M1-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M2-001\",\"KGPU-M2-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M3-001\",\"KGPU-M3-003\",\"KGPU-M3-004\",\"KGPU-M3-005\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M3-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M4-001\",\"KGPU-M4-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M4-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M4-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M5-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M5-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M5-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M8-001\",\"KGPU-M8-002\",\"KGPU-M8-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M9-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M9-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M9-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M10-001\",\"KGPU-M10-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M10-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M10-003\"]"))
    }

    @Test
    fun `catalog report writes French markdown with executable docs and candidates`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-report-fr")
        SceneCatalogReport(
            scenes = GPURendererSceneRegistry.scenes,
            humanDocs = GPURendererSceneHumanDocs.docs,
            candidateScenes = GPURendererSceneHumanDocs.candidateScenes,
        ).writeTo(root)

        val french = root.resolve("catalog.fr.md").readText()
        assertContains(french, "# Catalogue des scenes GPU Renderer")
        assertContains(french, "## Scenes executables")
        assertContains(french, "### Solid Card Stack (`solid-card-stack`)")
        assertContains(french, "Intention:")
        assertContains(french, "Valide:")
        assertContains(french, "Ne revendique pas:")
        assertContains(french, "Preuve:")
        assertContains(french, "## Candidates amont")
        assertContains(french, "### Runtime Effect Uniform Ladder (`runtime-effect-uniform-ladder`)")
        assertContains(french, "### Gradient Tile Mode Boundary (`gradient-tile-mode-boundary`)")
        assertContains(french, "### Path AA Stroke Join Board (`path-aa-stroke-join-board`)")
        assertContains(french, "### Layer Filter Chain Board (`layer-filter-chain-board`)")
        assertContains(french, "### Simple Latin Glyph Atlas Strip (`simple-latin-glyph-atlas-strip`)")
        assertContains(french, "Statut: `dependency-gated`")
    }

    @Test
    fun `catalog json separates executable scenes from candidate scenes`() {
        val json = SceneCatalogReport(
            scenes = GPURendererSceneRegistry.scenes,
            humanDocs = GPURendererSceneHumanDocs.docs,
            candidateScenes = GPURendererSceneHumanDocs.candidateScenes,
        ).toJson()

        assertContains(json, "\"humanDocs\": {")
        assertContains(json, "\"fr\": {")
        assertContains(json, "\"intention\": \"Verifier une pile de cartes solides avec ordre de dessin stable.\"")
        assertContains(json, "\"candidateScenes\": [")
        assertContains(json, "\"sceneId\": \"runtime-effect-uniform-ladder\"")
        assertContains(json, "\"sceneId\": \"gradient-tile-mode-boundary\"")
        assertContains(json, "\"sceneId\": \"path-aa-stroke-join-board\"")
        assertContains(json, "\"sceneId\": \"layer-filter-chain-board\"")
        assertContains(json, "\"sceneId\": \"simple-latin-glyph-atlas-strip\"")
        assertContains(json, "\"status\": \"dependency-gated\"")
        assertFalse(json.substringAfter("\"candidateScenes\": [").contains("\"status\": \"runner-gap\""))
        assertTrue(json.indexOf("\"scenes\": [") < json.indexOf("\"candidateScenes\": ["))
    }

    @Test
    fun `catalog json omits scene human docs when none are provided but keeps candidate array`() {
        val scene = GPURendererScene(
            sceneId = SceneId("sample-report-scene"),
            title = "Sample Report Scene",
            description = "Sample report scene.",
            dimensions = SceneDimensions(16, 16),
            tags = setOf(SceneTag.Rect),
            roadmapLinks = listOf(SceneRoadmapLink.milestone("M1")),
            expectation = SceneExpectation.ShouldRender,
            commands = listOf(Unit),
        )
        val candidate = CandidateScene(
            sceneId = SceneId("sample-report-candidate"),
            title = "Sample Report Candidate",
            roadmapLinks = listOf(SceneRoadmapLink.milestone("M1")),
            tags = setOf(SceneTag.Rect),
            status = CandidateSceneStatus.Candidate,
            french = CandidateSceneFrenchText(
                intention = "Intention candidate de rapport.",
                validationTarget = "Validation candidate de rapport.",
                nonClaims = "Non revendication candidate.",
                rationale = "Raison candidate de rapport.",
            ),
        )

        val json = SceneCatalogReport(
            scenes = listOf(scene),
            candidateScenes = listOf(candidate),
        ).toJson()

        val executableScenesJson = json.substringBefore("\"candidateScenes\": [")
        assertFalse(executableScenesJson.contains("\"humanDocs\""))
        assertContains(json, "\"candidateScenes\": [")
        assertContains(json, "\"sceneId\": \"sample-report-candidate\"")
    }

    @Test
    fun `catalog report json escapes non-printing control characters`() {
        val json = SceneCatalogReport(
            listOf(
                GPURendererScene(
                    sceneId = SceneId("control-char-scene"),
                    title = "Control\u0001Title",
                    description = "Description",
                    dimensions = SceneDimensions(1, 1),
                    tags = setOf(SceneTag.Rect),
                    roadmapLinks = listOf(SceneRoadmapLink.milestone("M0")),
                    expectation = SceneExpectation.ShouldRender,
                    commands = listOf(Unit),
                ),
            ),
        ).toJson()

        assertContains(json, "\"title\": \"Control\\u0001Title\"")
        assertFalse(json.contains('\u0001'))
    }
}
