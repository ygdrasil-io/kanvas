package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class RunGpuRendererSceneKadreMainTest {
    @Test
    fun `unknown scene fails before Kadre setup`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        val failure = assertFailsWith<IllegalStateException> {
            runGpuRendererSceneKadre(arrayOf("missing-scene", "60", output.toString()))
        }

        assertContains(failure.message ?: "", "Unknown GPU renderer scene")
        assertFalse(output.exists())
    }

    @Test
    fun `solid card stack frames zero writes dry session report`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        runGpuRendererSceneKadre(arrayOf("solid-card-stack", "0", output.toString()))

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"schemaVersion\": 1")
        assertContains(sessionJson, "\"sceneId\": \"solid-card-stack\"")
        assertContains(sessionJson, "\"status\": \"dry-session\"")
        assertContains(sessionJson, "\"requestedFrames\": 0")
        assertContains(sessionJson, "\"presentedFrames\": 0")
        assertContains(sessionJson, "\"manualValidation\": true")
        assertContains(sessionJson, "\"productRefusal\": false")
    }

    @Test
    fun `non first windowed scene writes not yet rendered without opening Kadre`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        runGpuRendererSceneKadre(arrayOf("mesh-ribbon", "60", output.toString()))

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"sceneId\": \"mesh-ribbon\"")
        assertContains(sessionJson, "\"status\": \"not-yet-rendered\"")
        assertContains(sessionJson, "\"reason\": \"scene-renderer-not-yet-implemented\"")
        assertContains(sessionJson, "\"requestedFrames\": 60")
        assertContains(sessionJson, "\"presentedFrames\": 0")
        assertContains(sessionJson, "\"manualValidation\": true")
    }

    @Test
    fun `frames must be a non negative integer`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")

        val invalid = assertFailsWith<IllegalArgumentException> {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "abc", output.toString()))
        }
        val negative = assertFailsWith<IllegalArgumentException> {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "-1", output.toString()))
        }

        assertContains(invalid.message ?: "", "frames must be an Int")
        assertContains(negative.message ?: "", "frames must be >= 0")
        assertFalse(output.exists())
    }

    @Test
    fun `runner invocation failure overwrites stale session report`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")
        WindowedSceneSessionReport.drySession(scene = solidCardStackScene(), requestedFrames = 0)
            .writeTo(output)
        val originalLauncher = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = WindowedSceneRunnerLauncher { _, _, _ ->
            error("simulated runner failure")
        }

        try {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "60", output.toString()))
        } finally {
            kadreWindowedSceneRunnerLauncher = originalLauncher
        }

        val sessionJson = output.readText()
        assertContains(sessionJson, "\"status\": \"blocked\"")
        assertContains(sessionJson, "\"reason\": \"kadre-windowed-runner-invocation-failed\"")
        assertContains(sessionJson, "IllegalStateException: simulated runner failure")
        assertFalse(sessionJson.contains("\"status\": \"dry-session\""))
    }

    @Test
    fun `runner loading failures overwrite stale session report and rethrow`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main")
            .resolve("session.json")
        WindowedSceneSessionReport.drySession(scene = solidCardStackScene(), requestedFrames = 0)
            .writeTo(output)
        val originalLauncher = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = WindowedSceneRunnerLauncher { _, _, _ ->
            throw ClassNotFoundException("missing test runner")
        }

        val failure = try {
            assertFailsWith<ClassNotFoundException> {
                runGpuRendererSceneKadre(arrayOf("solid-card-stack", "60", output.toString()))
            }
        } finally {
            kadreWindowedSceneRunnerLauncher = originalLauncher
        }

        val sessionJson = output.readText()
        assertContains(failure.message ?: "", "missing test runner")
        assertContains(sessionJson, "\"status\": \"blocked\"")
        assertContains(sessionJson, "\"reason\": \"kadre-windowed-runner-loading-failed\"")
        assertContains(sessionJson, "ClassNotFoundException: missing test runner")
        assertFalse(sessionJson.contains("\"status\": \"dry-session\""))
    }

    @Test
    fun `session report status invariants are enforced`() {
        val scene = solidCardStackScene()
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.DrySession,
                reason = "frames-zero-dry-session",
                requestedFrames = 1,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.NotYetRendered,
                reason = " ",
                requestedFrames = 60,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.Blocked,
                reason = "kadre-windowed-runner-invocation-failed",
                requestedFrames = 60,
                presentedFrames = 0,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, null),
                adapterInfo = null,
                error = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.Presented,
                reason = "kadre-windowed-presented-frames",
                requestedFrames = 60,
                presentedFrames = 59,
                surface = WindowedSceneSurface(scene.dimensions.width, scene.dimensions.height, "BGRA8Unorm"),
                adapterInfo = "adapter",
                error = null,
            )
        }

        val presented = WindowedSceneSessionReport.presented(
            scene = scene,
            requestedFrames = 60,
            surfaceFormat = "BGRA8Unorm",
            adapterInfo = "adapter",
        )
        assertEquals("presented", presented.status)
        assertContains(presented.toJson(), "\"productRefusal\": false")
    }

    private fun solidCardStackScene() =
        GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
}
