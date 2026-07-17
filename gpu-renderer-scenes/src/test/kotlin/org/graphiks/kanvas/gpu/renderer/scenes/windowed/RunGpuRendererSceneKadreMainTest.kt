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
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main").resolve("session.json")
        val failure = assertFailsWith<IllegalStateException> {
            runGpuRendererSceneKadre(arrayOf("missing-scene", "60", output.toString()))
        }
        assertContains(failure.message.orEmpty(), "Unknown GPU renderer scene")
        assertFalse(output.exists())
    }

    @Test
    fun `zero frames writes a dry session without loading Kadre`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main").resolve("session.json")
        var launches = 0
        withLauncher(WindowedSceneRunnerLauncher { _, _, _ -> launches++ }) {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "0", output.toString()))
        }
        assertContains(output.readText(), "\"status\": \"dry-session\"")
        assertEquals(0, launches)
    }

    @Test
    fun `every catalog scene reaches the common prepared Kadre launcher`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-windowed-all")
        val launched = mutableListOf<String>()
        withLauncher(WindowedSceneRunnerLauncher { scene, frames, output ->
            launched += scene.sceneId.value
            WindowedSceneSessionReport.blocked(
                scene = scene,
                requestedFrames = frames,
                reason = "unsupported.wgpu4k.surface-status-v29",
                error = "dependency-gated native surface status mapping",
            ).writeTo(output)
        }) {
            GPURendererSceneRegistry.registry.scenes.forEach { scene ->
                runGpuRendererSceneKadre(
                    arrayOf(scene.sceneId.value, "1", root.resolve("${scene.sceneId.value}.json").toString()),
                )
            }
        }
        assertEquals(
            GPURendererSceneRegistry.registry.scenes.map { it.sceneId.value },
            launched,
        )
    }

    @Test
    fun `frames must be a non negative integer`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main").resolve("session.json")
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                runGpuRendererSceneKadre(arrayOf("solid-card-stack", "abc", output.toString()))
            }.message.orEmpty(),
            "frames must be an Int",
        )
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                runGpuRendererSceneKadre(arrayOf("solid-card-stack", "-1", output.toString()))
            }.message.orEmpty(),
            "frames must be >= 0",
        )
    }

    @Test
    fun `runner invocation failure writes a blocked report`() {
        val output = Files.createTempDirectory("gpu-renderer-scenes-windowed-main").resolve("session.json")
        withLauncher(WindowedSceneRunnerLauncher { _, _, _ -> error("simulated runner failure") }) {
            runGpuRendererSceneKadre(arrayOf("solid-card-stack", "60", output.toString()))
        }
        val json = output.readText()
        assertContains(json, "\"status\": \"blocked\"")
        assertContains(json, "kadre-windowed-runner-invocation-failed")
    }

    @Test
    fun `presented report retains raw completion timing`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        val report = WindowedSceneSessionReport.presented(
            scene = scene,
            requestedFrames = 2,
            surfaceFormat = "BGRA8Unorm",
            adapterInfo = "adapter",
            frameTiming = WindowedFrameTimingReport.wallClockEncodePresent(
                warmupFrames = 1,
                samples = listOf(12_000_000L, 10_000_000L),
            ),
        )
        assertContains(report.toJson(), "\"metricSource\": \"wall-clock-encode-present\"")
        assertContains(report.toJson(), "\"rawSampleCount\": 2")
    }

    private inline fun withLauncher(launcher: WindowedSceneRunnerLauncher, block: () -> Unit) {
        val original = kadreWindowedSceneRunnerLauncher
        kadreWindowedSceneRunnerLauncher = launcher
        try {
            block()
        } finally {
            kadreWindowedSceneRunnerLauncher = original
        }
    }
}
