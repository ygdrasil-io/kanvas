package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
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
        assertContains(markdown, "KGPU M0,M1")
        assertContains(markdown, "`KGPU-M0-007`,`KGPU-M1-001`,`KGPU-M1-002`")
        assertContains(markdown, "`KGPU-M1-003`,`KGPU-M1-004`")
        assertContains(markdown, "`KGPU-M2-001`,`KGPU-M2-002`")
        assertContains(markdown, "`KGPU-M2-003`,`KGPU-M2-004`")
        assertContains(markdown, "`KGPU-M3-001`,`KGPU-M3-003`,`KGPU-M3-004`,`KGPU-M3-005`")
        assertContains(markdown, "`KGPU-M4-001`,`KGPU-M4-002`")
        assertContains(markdown, "`KGPU-M4-003`")
        assertContains(markdown, "`KGPU-M5-004`")
        assertContains(markdown, "`KGPU-M6-001`")
        assertContains(markdown, "`KGPU-M6-004`")
        assertContains(markdown, "`KGPU-M7-003`")
        assertContains(markdown, "`KGPU-M7-004`")
        assertContains(markdown, "`KGPU-M9-001`")
        assertContains(markdown, "`KGPU-M10-001`,`KGPU-M10-004`")
        assertContains(markdown, "R0,R1,R2,R3,R4,R5,R6")
        assertContains(markdown, "`ShouldRender`")
    }

    @Test
    fun `catalog report writer creates markdown and json`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-report")
        SceneCatalogReport(GPURendererSceneRegistry.scenes).writeTo(root)

        assertTrue(root.resolve("catalog.md").readText().contains("GPU Renderer Scene Catalog"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"solid-card-stack\""))
        assertTrue(
            root.resolve("catalog.json").readText()
                .contains("\"ticketIds\": [\"KGPU-M0-007\",\"KGPU-M1-001\",\"KGPU-M1-002\"]"),
        )
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M1-003\",\"KGPU-M1-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M2-001\",\"KGPU-M2-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M3-001\",\"KGPU-M3-003\",\"KGPU-M3-004\",\"KGPU-M3-005\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M4-001\",\"KGPU-M4-002\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M4-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M5-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M6-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-003\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M7-004\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M9-001\"]"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"ticketIds\": [\"KGPU-M10-001\",\"KGPU-M10-004\"]"))
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
