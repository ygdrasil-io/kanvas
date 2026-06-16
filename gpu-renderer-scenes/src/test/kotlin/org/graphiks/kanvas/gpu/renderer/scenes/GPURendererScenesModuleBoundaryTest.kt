package org.graphiks.kanvas.gpu.renderer.scenes

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.readText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.offscreen.rectOnlyCommandSequenceUnsupportedReason
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.fail

class GPURendererScenesModuleBoundaryTest {
    @Test
    fun `scene module build and sources stay decoupled from gpu raster internals`() {
        val buildFile = repoPath("gpu-renderer-scenes/build.gradle.kts").readText()
        assertFalse(
            "implementation(project(\":gpu-raster\"))" in buildFile,
            "gpu-renderer-scenes must not depend on :gpu-raster directly",
        )

        val srcRoot = repoPath("gpu-renderer-scenes/src")
        val sceneSourceFiles = Files.walk(srcRoot)
            .filter { path ->
                Files.isRegularFile(path) && path.fileName.toString() != "GPURendererScenesModuleBoundaryTest.kt"
            }
            .toList()
        val sceneSource = sceneSourceFiles.joinToString("\n") { it.readText() }

        assertFalse("org.skia.gpu.webgpu" in sceneSource)
        assertFalse(
            sceneSourceFiles.any { file ->
                file.readText().lineSequence().any { line ->
                    line.trim().startsWith("import io.ygdrasil.webgpu")
                }
            },
            "gpu-renderer-scenes/src must not import io.ygdrasil.webgpu directly",
        )
    }

    @Test
    fun `catalog packages do not import Kadre or gpu raster`() {
        val root = repoPath("gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog")
        val source = Files.walk(root).filter { Files.isRegularFile(it) }.toList().joinToString("\n") { it.readText() }

        assertFalse("org.graphiks.kadre" in source)
        assertFalse("org.skia.gpu.webgpu" in source)
    }

    @Test
    fun `check task graph does not include opt in render tasks`() {
        val output = runGradleDryRun(":gpu-renderer-scenes:check")

        assertTrue(
            output.contains(":gpu-renderer-scenes:check"),
            "Gradle dry-run did not include the requested check task.\n$output",
        )
        assertFalse(
            output.contains(":gpu-renderer-scenes:renderGpuRendererSceneOffscreen"),
            "check must not depend on the opt-in offscreen render task.\n$output",
        )
        assertFalse(
            output.contains(":gpu-renderer-scenes:runGpuRendererSceneKadre"),
            "check must not depend on the opt-in Kadre windowed render task.\n$output",
        )
        assertFalse(
            output.contains(":gpu-renderer-scenes:compileKadreKotlin"),
            "check must not compile the opt-in Kadre source set.\n$output",
        )
    }

    @Test
    fun `src main windowed launcher loads Kadre runner only by reflection`() {
        val launcher = repoPath(
            "gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/" +
                "RunGpuRendererSceneKadreMain.kt",
        ).readText()

        assertFalse(
            launcher.lineSequence().any { it.trim().startsWith("import org.graphiks.kadre") },
            "src/main windowed launcher must not import Kadre directly",
        )
        assertFalse(
            "KadreWindowedSceneRunner(" in launcher,
            "src/main windowed launcher must not instantiate the Kadre runner directly",
        )
        assertFalse(
            "KadreWindowedSceneRunner::class" in launcher,
            "src/main windowed launcher must not reference the Kadre runner class directly",
        )
        assertTrue(launcher.contains("KADRE_RUNNER_CLASS"))
        assertTrue(
            launcher.contains(
                "org.graphiks.kanvas.gpu.renderer.scenes.windowed.KadreWindowedSceneRunner",
            ),
        )
        assertTrue(launcher.contains("Class.forName(KADRE_RUNNER_CLASS)"))
    }

    @Test
    fun `rect only offscreen gate matches current faithful runtime subset`() {
        val supported = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        assertNull(rectOnlyCommandSequenceUnsupportedReason(supported.commands))

        val richer = GPURendererSceneRegistry.registry.requireScene("rounded-panel-gradient")
        assertEquals(
            "rect-only offscreen render supports only clear, fill-rect, and clip command families: " +
                "fill-rrect, linear-gradient-rect",
            rectOnlyCommandSequenceUnsupportedReason(richer.commands),
        )
    }

    private fun runGradleDryRun(task: String): String {
        val output = StringBuilder()
        val process = ProcessBuilder("./gradlew", "--no-daemon", task, "--dry-run")
            .directory(repoRoot().toFile())
            .redirectErrorStream(true)
            .start()
        val reader = thread(start = true, name = "gradle-dry-run-output-reader") {
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
        }
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(1_000)
            fail("Gradle dry-run timed out for $task.\n$output")
        }

        reader.join(5_000)
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            fail("Gradle dry-run failed for $task with exit code $exitCode.\n$output")
        }
        return output.toString()
    }

    private fun repoPath(path: String): Path = repoRoot().resolve(path)

    private fun repoRoot(): Path {
        var current: Path? = Path("").toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts")) &&
                Files.exists(current.resolve("gpu-renderer-scenes"))
            ) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate Kanvas repository root")
    }
}
