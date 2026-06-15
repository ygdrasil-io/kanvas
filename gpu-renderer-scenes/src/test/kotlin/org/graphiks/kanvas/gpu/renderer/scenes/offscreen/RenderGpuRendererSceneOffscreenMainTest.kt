package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RenderGpuRendererSceneOffscreenMainTest {
    @Test
    fun `unknown scene fails before backend setup`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        val failure = assertFailsWith<IllegalStateException> {
            renderGpuRendererSceneOffscreen(arrayOf("missing-scene", root.toString()))
        }

        assertContains(failure.message ?: "", "Unknown GPU renderer scene")
        assertFalse(root.resolve("missing-scene").exists())
    }

    @Test
    fun `non first route scene writes not yet rendered report under scene directory`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")

        renderGpuRendererSceneOffscreen(arrayOf("mesh-ribbon", root.toString()))

        val runJson = root.resolve("mesh-ribbon").resolve("run.json").readText()
        assertContains(runJson, "\"sceneId\": \"mesh-ribbon\"")
        assertContains(runJson, "\"status\": \"not-yet-rendered\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "\"imagePath\": null")
        assertContains(runJson, "runner-subset:mesh-ribbon")
    }

    @Test
    fun `solid card stack backend failure report remains representable`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen-main")
        val sceneOutput = root.resolve("solid-card-stack")

        OffscreenRunReport.failed(
            sceneId = "solid-card-stack",
            reason = "webgpu-context-unavailable",
        ).writeTo(sceneOutput)

        val runJson = sceneOutput.resolve("run.json").readText()
        assertContains(runJson, "\"sceneId\": \"solid-card-stack\"")
        assertContains(runJson, "\"status\": \"render-failed\"")
        assertContains(runJson, "\"productRefusal\": false")
        assertContains(runJson, "webgpu-context-unavailable")
    }
}
