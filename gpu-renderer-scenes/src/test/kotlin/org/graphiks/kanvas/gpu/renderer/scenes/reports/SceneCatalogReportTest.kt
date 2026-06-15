package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class SceneCatalogReportTest {
    @Test
    fun `catalog report contains business ids and roadmap correspondence`() {
        val markdown = SceneCatalogReport(GPURendererSceneRegistry.scenes).toMarkdown()
        assertContains(markdown, "| `solid-card-stack` | Solid Card Stack |")
        assertContains(markdown, "KGPU M0,M1")
        assertContains(markdown, "R0,R1,R2,R3,R4,R5,R6")
        assertContains(markdown, "`ShouldRender`")
    }

    @Test
    fun `catalog report writer creates markdown and json`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-report")
        SceneCatalogReport(GPURendererSceneRegistry.scenes).writeTo(root)

        assertTrue(root.resolve("catalog.md").readText().contains("GPU Renderer Scene Catalog"))
        assertTrue(root.resolve("catalog.json").readText().contains("\"sceneId\": \"solid-card-stack\""))
    }
}
