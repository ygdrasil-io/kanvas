package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
}
