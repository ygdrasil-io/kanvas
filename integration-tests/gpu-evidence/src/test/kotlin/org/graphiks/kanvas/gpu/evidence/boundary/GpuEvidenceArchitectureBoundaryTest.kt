package org.graphiks.kanvas.gpu.evidence.boundary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpuEvidenceArchitectureBoundaryTest {
    @Test
    fun `selection forwarding is shared across correctness generation verification and promotion while performance stays isolated`() {
        val build = File("build.gradle.kts").readText()
        val correctnessTask = build.substringAfter("val generateGpuEvidence")
            .substringBefore("val warmupFrames")
        val performanceTask = build.substringAfter("tasks.register<JavaExec>(\"gpuEvidencePerformance\")")
            .substringBefore("tasks.register(\"generateBootstrapGpuEvidence\")")
        val generatedVerificationTask = build.substringAfter("tasks.register<JavaExec>(\"verifyGeneratedGpuEvidence\")")
            .substringBefore("tasks.register<JavaExec>(\"verifyPromotedGpuEvidence\")")
        val promotedVerificationTask = build.substringAfter("tasks.register<JavaExec>(\"verifyPromotedGpuEvidence\")")
            .substringBefore("tasks.register<JavaExec>(\"migratePromotedGpuEvidenceV1ToV2\")")
        val promotionTask = build.substringAfter("tasks.register<JavaExec>(\"promoteGpuEvidence\")")

        assertTrue(build.contains("val scene = providers.gradleProperty(\"scene\")"))
        assertTrue(build.contains("val scenesFile = providers.gradleProperty(\"scenesFile\")"))
        assertTrue(build.contains("val all = providers.gradleProperty(\"all\")"))
        val helper = build.substringAfter("fun selectionArguments(): List<String> {")
            .substringBefore("\n}\n\ndependencies {")
        assertTrue(helper.contains("scene.orNull"))
        assertTrue(helper.contains("scenesFile.orNull"))
        assertTrue(helper.contains("all.isPresent"))
        assertTrue(helper.contains("\"--scene\""))
        assertTrue(helper.contains("\"--scenes-file\""))
        assertTrue(helper.contains("\"--all\""))
        assertTrue(helper.contains("require"))
        assertTrue(helper.contains("selectAll -> listOf(\"--all\")"))
        assertTrue(helper.contains("else -> listOf(\"--all\")"))
        assertEquals(3, Regex("\\+ selectionArguments\\(\\)").findAll(build).count(), "correctness generation, generated verification, and promotion must share one selection helper")
        assertEquals(
            normalize("""
                listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", correctnessSourceCommit.get()) + selectionArguments()
            """),
            normalize(argumentProviderBody(correctnessTask)),
            "correctness generation must append the shared selection arguments",
        )
        assertEquals(
            normalize("""
                listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get(), "--warmup-frames", warmupFrames.get(), "--measured-frames", measuredFrames.get()) + optionalSceneArgument()
            """),
            normalize(argumentProviderBody(performanceTask)),
            "performance arguments may only append optional scene",
        )
        assertTrue(argumentProviderBody(generatedVerificationTask).contains("+ selectionArguments()"), "generated verification must share the explicit selection helper")
        assertTrue(argumentProviderBody(generatedVerificationTask).contains("--root"), "generated verification must still target the generated correctness root")
        assertTrue(promotedVerificationTask.contains("\"--allow-historical-commit\""), "promoted verification must preserve historical dual-read verification")
        assertTrue(promotedVerificationTask.contains("\"--all\""), "promoted verification must explicitly verify the full promoted catalogue")
        assertTrue(argumentProviderBody(promotionTask).contains("+ selectionArguments()"), "promotion must share the explicit selection helper")
        assertTrue(argumentProviderBody(promotionTask).contains("promotionRebaselineArguments"), "promotion must preserve explicit rebaseline metadata forwarding")
        assertFalse(build.contains("gpu-renderer-scenes"), "retired scenes module must remain absent")
        val dependencies = build.substringAfter("dependencies {").substringBefore("\n}")
        assertFalse(dependencies.contains("scene"), "scene must not alter dependencies")
        assertFalse(outsideArgumentProvider(correctnessTask).contains("--scene"), "scene selection must not alter correctness task configuration outside CLI arguments")
        assertFalse(outsideArgumentProvider(correctnessTask).contains("--scenes-file"), "scene selection must not alter correctness task configuration outside CLI arguments")
        assertFalse(outsideArgumentProvider(performanceTask).contains("--scenes-file"), "scene file selection must not alter performance task configuration")
        assertFalse(outsideArgumentProvider(generatedVerificationTask).contains("--scene"), "scene selection must not alter generated verification task configuration outside CLI arguments")
        assertFalse(outsideArgumentProvider(generatedVerificationTask).contains("--scenes-file"), "scene selection must not alter generated verification task configuration outside CLI arguments")
        assertFalse(outsideArgumentProvider(promotionTask).contains("--all"), "promotion full selection must come from the shared helper rather than a hard-coded full-only task")
        assertFalse(performanceTask.contains("promotionRebaselineArguments"), "performance must not receive correctness-only promotion flags")
        assertFalse(performanceTask.contains("--reviewer"), "performance must not receive correctness-only promotion flags")
        assertFalse(performanceTask.contains("--reason"), "performance must not receive correctness-only promotion flags")
        assertFalse(performanceTask.contains("--all"), "performance must not silently become a catalogue-wide correctness task")
        assertTrue(build.contains("description = \"Generates GPU correctness evidence for the selected scenes or the full catalogue when no selector is provided.\""))
        assertTrue(build.contains("description = \"Verifies generated GPU correctness evidence for the selected scenes or the full catalogue when no selector is provided.\""))
        assertTrue(build.contains("description = \"Promotes selected GPU correctness scenes for the daily workflow; an existing full catalogue requires -Pall=true, -PpromotionRebaseline=true, and prior/new comparison summaries.\""))
        assertTrue(build.contains("description = \"Alias for generateGpuEvidence.\""))
    }

    @Test
    fun `correctness tasks default source commit to current git head while performance stays explicit`() {
        val build = File("build.gradle.kts").readText()
        val correctnessTask = build.substringAfter("val generateGpuEvidence")
            .substringBefore("val warmupFrames")
        val performanceTask = build.substringAfter("tasks.register<JavaExec>(\"gpuEvidencePerformance\")")
            .substringBefore("tasks.register(\"generateBootstrapGpuEvidence\")")
        val generatedVerificationTask = build.substringAfter("tasks.register<JavaExec>(\"verifyGeneratedGpuEvidence\")")
            .substringBefore("tasks.register<JavaExec>(\"verifyPromotedGpuEvidence\")")

        assertTrue(build.contains("val sourceCommit = providers.gradleProperty(\"sourceCommit\")"))
        assertTrue(build.contains("commandLine(\"git\", \"rev-parse\", \"HEAD\")"))
        assertTrue(build.contains("currentGitHeadSourceCommit"))
        assertTrue(build.contains("correctnessSourceCommit"))
        assertTrue(correctnessTask.contains("correctnessSourceCommit.get()"), "correctness generation must consume the defaultable source commit provider")
        assertTrue(generatedVerificationTask.contains("correctnessSourceCommit.get()"), "generated verification must consume the same defaultable source commit provider")
        assertFalse(correctnessTask.contains("sourceCommit.isPresent"), "correctness generation must not require an explicit -PsourceCommit")
        assertFalse(generatedVerificationTask.contains("sourceCommit.isPresent"), "generated verification must not require an explicit -PsourceCommit")
        assertTrue(performanceTask.contains("sourceCommit.isPresent"), "performance must keep requiring an explicit -PsourceCommit")
        assertTrue(performanceTask.contains("\"--source-commit\", sourceCommit.get()"), "performance must keep forwarding only the explicit sourceCommit property")
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
            "SurfaceSrgbClipPathCpuOracle.kt",
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

    @Test
    fun `checked-in promoted evidence uses root v2 metadata and scene bundles stay metadata-light`() {
        val moduleRoot = File(".").canonicalFile
        val repositoryRoot = moduleRoot.parentFile.parentFile
        val promotedRoot = repositoryRoot.resolve("reports/gpu-renderer/evidence/correctness/promoted")

        assertTrue(promotedRoot.resolve("catalog.json").isFile, "promoted v2 root must publish catalog.json")
        assertTrue(promotedRoot.resolve("environment.json").isFile, "promoted v2 root must publish environment.json")
        assertTrue(promotedRoot.resolve("promotion.json").isFile, "promoted v2 root must publish promotion.json")

        promotedRoot.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .forEach { sceneDir ->
                assertFalse(sceneDir.resolve("environment.json").exists(), "${sceneDir.name} must not carry scene-local environment.json in v2")
                assertFalse(sceneDir.resolve("promotion.json").exists(), "${sceneDir.name} must not carry scene-local promotion.json in v2")
            }
    }
}
