package org.graphiks.kanvas.gpu.evidence.boundary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpuEvidenceArchitectureBoundaryTest {
    @Test
    fun `gpu evidence has no second renderer boundary`() {
        val projectRoot = File(".").canonicalFile
        val forbidden = mapOf(
            "direct backend dependency" to Regex("wgpu4k|io\\.ygdrasil\\.webgpu"),
            "legacy module dependency" to Regex("project\\(\\\"?:gpu-renderer-scenes\\\"?\\)"),
            "legacy source import" to Regex("org\\.graphiks\\.kanvas\\.gpu\\.renderer\\.scenes"),
            "direct target allocation" to Regex("\\.createOffscreenTarget\\("),
            "direct encoding" to Regex("\\.encode(?:OffscreenTexture)?\\("),
            "scene-owned WGSL" to Regex("@(vertex|fragment|compute)|@group\\("),
        )
        val text = buildList {
            add(projectRoot.resolve("build.gradle.kts").readText())
            projectRoot.resolve("src").walkTopDown()
                .filter {
                    it.isFile &&
                        it.name != "GpuEvidenceArchitectureBoundaryTest.kt" &&
                        it.extension in setOf("kt", "kts", "wgsl")
                }
                .forEach { add(it.readText()) }
        }.joinToString("\n")
        forbidden.forEach { (label, pattern) ->
            assertFalse(pattern.containsMatchIn(text), "$label crossed the gpu-evidence boundary")
        }
        assertTrue(
            projectRoot.resolve("build.gradle.kts").readText().contains("implementation(project(\":kanvas\"))"),
            "gpu evidence must depend directly on the public Kanvas Surface API",
        )
        val oracleRoot = projectRoot.resolve("src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle")
        val requiredSurfaceOracles = listOf(
            "SurfaceSrgbOracleMath.kt",
            "SurfaceSrgbSrcOverCpuOracle.kt",
            "SurfaceSrgbSeparableMaskBlurCpuOracle.kt",
        )
        requiredSurfaceOracles.forEach { name ->
            assertTrue(oracleRoot.resolve(name).isFile, "missing required Surface sRGB oracle: $name")
        }
        val legacyOracleExceptions = setOf("CpuOracle.kt", "ReferenceRaster.kt", "GradientCpuOracle.kt", "GradientCpuOracleTest.kt")
        val surfaceOracleFiles = oracleRoot.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" && it.name !in legacyOracleExceptions }
        assertTrue(surfaceOracleFiles.any { it.name.startsWith("SurfaceSrgb") }, "Surface sRGB oracle scan must not be vacuous")
        val surfaceOracleText = surfaceOracleFiles
            .joinToString("\n") { it.readText().replace(Regex("^package\\s+org\\.graphiks\\.kanvas\\.gpu\\.evidence\\.oracle\\s*$", RegexOption.MULTILINE), "") }
        assertFalse(
            Regex("org\\.graphiks\\.kanvas\\.").containsMatchIn(surfaceOracleText),
            "Surface sRGB oracles must be independent of product types",
        )
    }
}
