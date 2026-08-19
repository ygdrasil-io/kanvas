package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Closure guard: the retired immediate/CPU-path adapter symbols must
 * never reappear in the production surface/gpu sources.
 *
 * The retired symbols are the adapter file (GPULegacyImmediatePathAdapter.kt),
 * its display family, its dump type and the `legacyDump` plumbing, the legacy
 * immediate renderer (`renderViaGpuLegacy`), the legacy port
 * (`GPUPreparedSurfaceLegacyPort`), and the legacy-only helper machinery
 * (`GPUClipExecution.kt` — `renderWithClip`, `GPUClipRouteTrace`,
 * `cachePixels`; the CPU text-atlas builders — `buildTextAtlasMesh`;
 * `LayerScissorOffscreenTarget`, `GPUClipUsePrepass`,
 * `GPUClipCoverageFrameCache`, `acquireClipMask`, `expandPicturesForGpuReplay`),
 * plus the two `legacy.surface.prepared.*` gate codes. The guard below pins
 * every retired token.
 */
class GPUPreparedSurfaceLegacyAbsenceTest {

    @Test
    fun `retired legacy adapter symbols are absent from production`() {
        val retired = listOf(
            "GPULegacyImmediatePathAdapter",
            "LegacyDisplayOpFamily",
            "GPULegacyImmediatePathDump",
            "legacyDump",
            "renderViaGpuLegacy",
            "GPUPreparedSurfaceLegacyPort",
            "GPUClipRouteTrace",
            "renderWithClip",
            "cachePixels",
            "buildTextAtlasMesh",
            "LayerScissorOffscreenTarget",
            "GPUClipUsePrepass",
            "GPUClipCoverageFrameCache",
            "acquireClipMask",
            "expandPicturesForGpuReplay",
            "legacy.surface.prepared",
        )
        val root = productionSurfaceGpuSourceRoot()
        assertTrue(
            root.isDirectory,
            "production surface/gpu source root missing: ${root.absolutePath}",
        )
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { line -> retired.any { token -> token in line } } }
            .toList()
        assertEquals(emptyList(), offenders)
    }

    /**
     * The :kanvas:test working directory is the kanvas/ module directory, so the
     * module-relative `src/main/...` resolves first. If that assumption ever
     * changes (e.g. tests run from the repository root), fall back to walking up
     * to the directory that contains settings.gradle.kts, exactly like
     * GPUPreparedSurfaceFrameBuilderTextTest.repositoryRoot().
     */
    private fun productionSurfaceGpuSourceRoot(): java.io.File {
        val moduleRelative = java.io.File("src/main/kotlin/org/graphiks/kanvas/surface/gpu")
        if (moduleRelative.isDirectory) return moduleRelative
        val repositoryRoot = generateSequence(
            java.io.File(System.getProperty("user.dir")).absoluteFile,
        ) { it.parentFile }
            .first { candidate -> java.io.File(candidate, "settings.gradle.kts").isFile }
        return java.io.File(repositoryRoot, "kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu")
    }
}
