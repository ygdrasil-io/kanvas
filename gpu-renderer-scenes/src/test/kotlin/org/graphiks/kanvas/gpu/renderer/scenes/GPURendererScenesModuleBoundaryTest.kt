package org.graphiks.kanvas.gpu.renderer.scenes

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPURendererScenesModuleBoundaryTest {
    @Test
    fun `catalog packages do not import Kadre or gpu raster`() {
        val root = repoPath("gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog")
        val source = Files.walk(root).filter { Files.isRegularFile(it) }.toList().joinToString("\n") { it.readText() }

        assertFalse("org.graphiks.kadre" in source)
        assertFalse("org.skia.gpu.webgpu" in source)
    }

    @Test
    fun `check task does not depend on opt in render tasks`() {
        val build = repoPath("gpu-renderer-scenes/build.gradle.kts").readText()
        val checkDependsOnRenderTasks = build.contains("dependsOn(\"renderGpuRendererSceneOffscreen\")") ||
            build.contains("dependsOn(\"runGpuRendererSceneKadre\")")

        assertFalse(checkDependsOnRenderTasks, "check must not depend on opt-in render tasks")
        assertTrue(build.contains("renderGpuRendererSceneOffscreen"))
        assertTrue(build.contains("runGpuRendererSceneKadre"))
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

    private fun repoPath(path: String): java.nio.file.Path = repoRoot().resolve(path)

    private fun repoRoot(): java.nio.file.Path {
        var current: java.nio.file.Path? = Path("").toAbsolutePath()
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
