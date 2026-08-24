package org.graphiks.kanvas.gpu.evidence.boundary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpuEvidenceArchitectureBoundaryTest {
    @Test
    fun `single scene passthrough is limited to correctness and performance captures`() {
        val build = File("build.gradle.kts").readText()
        val correctnessTask = build.substringAfter("val generateGpuEvidence")
            .substringBefore("val warmupFrames")
        val performanceTask = build.substringAfter("tasks.register<JavaExec>(\"gpuEvidencePerformance\")")
            .substringBefore("tasks.register(\"generateBootstrapGpuEvidence\")")

        assertTrue(build.contains("val scene = providers.gradleProperty(\"scene\")"))
        assertEquals(
            "fun optionalSceneArgument(): List<String> = scene.orNull?.let { listOf(\"--scene\", it) }.orEmpty()",
            build.lineSequence().first { it.startsWith("fun optionalSceneArgument") }.trim(),
            "scene must be absent or the exact two-argument --scene pair",
        )
        assertEquals(1, Regex("--scene").findAll(build).count(), "only the shared CLI argument may mention --scene")
        assertEquals(2, Regex("\\+ optionalSceneArgument\\(\\)").findAll(build).count(), "only correctness and performance may forward scene")
        assertEquals(
            normalize("""
                listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get()) + optionalSceneArgument()
            """),
            normalize(argumentProviderBody(correctnessTask)),
            "correctness arguments may only append optional scene",
        )
        assertEquals(
            normalize("""
                listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get(), "--warmup-frames", warmupFrames.get(), "--measured-frames", measuredFrames.get()) + optionalSceneArgument()
            """),
            normalize(argumentProviderBody(performanceTask)),
            "performance arguments may only append optional scene",
        )
        assertFalse(build.contains("gpu-renderer-scenes"), "retired scenes module must remain absent")
        val dependencies = build.substringAfter("dependencies {").substringBefore("\n}")
        assertFalse(dependencies.contains("scene"), "scene must not alter dependencies")
        assertFalse(outsideArgumentProvider(correctnessTask).contains("scene"), "scene must not alter correctness task configuration")
        assertFalse(outsideArgumentProvider(performanceTask).contains("scene"), "scene must not alter performance task configuration")
    }

    private fun argumentProviderBody(task: String): String {
        val marker = "argumentProviders.add(org.gradle.process.CommandLineArgumentProvider"
        val providerStart = task.indexOf(marker).also { require(it >= 0) { "missing argument provider" } }
        val bodyStart = task.indexOf('{', providerStart) + 1
        val bodyEnd = task.indexOf("})", bodyStart).also { require(it >= 0) { "unterminated argument provider" } }
        return task.substring(bodyStart, bodyEnd).trim()
    }

    private fun outsideArgumentProvider(task: String): String {
        val start = task.indexOf("argumentProviders.add").also { require(it >= 0) { "missing argument provider" } }
        val end = task.indexOf("})", start).also { require(it >= 0) { "unterminated argument provider" } } + 2
        return task.removeRange(start, end)
    }

    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim()

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
            "SurfaceSrgbGradientCpuOracle.kt",
        )
        requiredSurfaceOracles.forEach { name ->
            assertTrue(oracleRoot.resolve(name).isFile, "missing required Surface sRGB oracle: $name")
        }
        val legacyOracleExceptions = setOf("CpuOracle.kt", "ReferenceRaster.kt")
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
