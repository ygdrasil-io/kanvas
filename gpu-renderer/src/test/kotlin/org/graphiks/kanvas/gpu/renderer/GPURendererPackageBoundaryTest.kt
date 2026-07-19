package org.graphiks.kanvas.gpu.renderer

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.validation.GPUForbiddenImportCheck
import org.graphiks.kanvas.gpu.renderer.validation.GPUPackageBoundaryCheck
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationFixture

/** Verifies package-boundary checks required by the GPU renderer layout spec. */
class GPURendererPackageBoundaryTest {
    /** Ensures the current production source set contains no forbidden upstream imports. */
    @Test
    fun `gpu renderer production source has no forbidden imports`() {
        val violations = GPUForbiddenImportCheck().findViolations(mainSourceRoot)

        assertTrue(
            actual = violations.isEmpty(),
            message = "Forbidden imports in gpu-renderer source: ${violations.joinToString()}",
        )
    }

    /** Ensures forbidden Skia, Graphite, and Ganesh imports are reported deterministically. */
    @Test
    fun `forbidden import check rejects skia graphite and ganesh imports`() {
        val root = temporarySourceRoot()
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/commands/InvalidImports.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.commands

                import org.jetbrains.skia.Paint
                import org.skia.ganesh.Renderer
                import skgpu.graphite.Recorder

                class InvalidImports
            """.trimIndent(),
        )

        val violations = GPUForbiddenImportCheck().findViolations(root)

        assertContains(violations.joinToString("\n"), "Skia-like public API")
        assertContains(violations.joinToString("\n"), "Graphite/Ganesh source")
    }

    /** Ensures the current production source set stays under the canonical renderer root. */
    @Test
    fun `gpu renderer production source satisfies package boundary rules`() {
        val violations = GPUPackageBoundaryCheck().findViolations(mainSourceRoot)

        assertTrue(
            actual = violations.isEmpty(),
            message = "Package-boundary violations in gpu-renderer source: ${violations.joinToString()}",
        )
    }

    /** Keeps payload contracts passive so command/planning imports cannot close a package cycle. */
    @Test
    fun `payload contracts do not import commands or analysis`() {
        val payloadSources = productionFile("payloads").walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }

        listOf("commands", "analysis").forEach { forbiddenPackage ->
            assertTrue(
                actual = "import org.graphiks.kanvas.gpu.renderer.$forbiddenPackage." !in payloadSources,
                message = "payloads must remain passive and must not import $forbiddenPackage",
            )
        }
    }

    /** Ensures geometry remains independent from execution-only contracts. */
    @Test
    fun `geometry source does not import execution contracts`() {
        val pathTessellatorSource = productionFile("geometry/PathTessellator.kt").readText()

        assertTrue(
            actual = "org.graphiks.kanvas.gpu.renderer.execution." !in pathTessellatorSource,
            message = "Geometry must not import execution contracts",
        )
    }

    /** Keeps the frame-plan/resource dependency one-way and handle-free. */
    @Test
    fun `frame planning imports only handle free resource contracts`() {
        val resourcesRoot = productionFile("resources")
        val recordingRoot = productionFile("recording")
        val resourceSources = resourcesRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }
        val recordingSources = recordingRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }

        listOf("recording", "execution", "passes").forEach { forbiddenPackage ->
            assertTrue(
                actual = "import org.graphiks.kanvas.gpu.renderer.$forbiddenPackage." !in resourceSources,
                message = "resources must not import $forbiddenPackage",
            )
        }
        listOf("GPUConcreteResourceProvider", "GPUMaterialized", "GPUPrepared").forEach { forbiddenType ->
            assertTrue(
                actual = "import org.graphiks.kanvas.gpu.renderer.resources.$forbiddenType" !in recordingSources,
                message = "recording must not import concrete resource type $forbiddenType",
            )
        }
    }

    /** Keeps task-list construction on the recording side of the recording/execution boundary. */
    @Test
    fun `public prepared frame recorders belong to recording not execution`() {
        val recordingSources = productionFile("recording").walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }
        val executionSources = productionFile("execution").walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }

        listOf("GPUColorGlyphFrameRecorder", "GPUSolidRectFrameRecorder").forEach { recorder ->
            assertContains(recordingSources, "class $recorder")
            assertTrue(
                actual = "class $recorder" !in executionSources,
                message = "$recorder constructs task lists and must not be owned by execution",
            )
        }
    }

    /** Keeps frame telemetry observational and dependency direction one-way into execution. */
    @Test
    fun `frame structural telemetry stays independent from execution resources and recording`() {
        val telemetrySource = productionFile("telemetry/TelemetryContracts.kt").readText()
        val executorSource = productionFile("execution/GPUFrameExecutor.kt").readText()
        val coordinatorSource = productionFile("execution/GPUFrameCoordinator.kt").readText()

        listOf("execution", "resources", "recording").forEach { forbiddenPackage ->
            assertTrue(
                actual = "import org.graphiks.kanvas.gpu.renderer.$forbiddenPackage." !in telemetrySource,
                message = "telemetry must not import $forbiddenPackage",
            )
        }
        assertContains(executorSource, "org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID")
        assertContains(coordinatorSource, "org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID")
    }

    /** Keeps low-level execution and preflight seams hidden behind the coordinator product API. */
    @Test
    fun `frame execution seams remain internal and coordinator is the product entry`() {
        val executorSource = productionFile("execution/GPUFrameExecutor.kt").readText()
        val coordinatorSource = productionFile("execution/GPUFrameCoordinator.kt").readText()
        val preparedSource = productionFile("execution/PreparedGPUFrame.kt").readText()
        val preflighterSource = productionFile("execution/GPUFramePreflighter.kt").readText()
        val targetSource = productionFile("resources/GPUSceneTarget.kt").readText()

        listOf(
            "internal class GPUFrameExecutor",
            "internal fun interface GPUFrameExecutionPort",
            "internal interface GPUFrameEncodingBackend",
            "internal interface GPUFrameResourceRetention",
        ).forEach { declaration -> assertContains(executorSource, declaration) }
        assertContains(coordinatorSource, "class GPUFrameCoordinator internal constructor")
        assertContains(coordinatorSource, "internal fun interface GPUFramePlanningPort")
        assertContains(coordinatorSource, "internal fun interface GPUFramePreflightPort")
        assertContains(preparedSource, "internal class PreparedGPUFrame")
        assertContains(preparedSource, "internal sealed interface GPUFramePreflightResult")
        assertContains(preflighterSource, "internal class GPUFramePreflighter")
        assertContains(targetSource, "internal class GPUSceneTarget")
    }

    /** Prevents scene callers from bypassing the coordinator through low-level execution types. */
    @Test
    fun `scene sources do not call frame executor or preflight seams directly`() {
        val sceneSources = sceneSourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString("\n") { file -> file.readText() }
        listOf(
            "GPUFrameExecutor",
            "GPUFrameExecutionPort",
            "GPUFramePreflighter",
            "GPUFramePreflightPort",
            "PreparedGPUFrame",
            "GPUSceneTarget",
        ).forEach { forbiddenType ->
            assertTrue(
                actual = forbiddenType !in sceneSources,
                message = "gpu-renderer-scenes must route through GPUFrameCoordinator, not $forbiddenType",
            )
        }
    }

    /** Keeps color-glyph native evidence on the canonical prepared-frame route. */
    @Test
    fun `color glyph native tests use task lists and prepared scene readback only`() {
        val testFiles = listOf(
            "execution/GPUColorGlyphRenderSmokeTest.kt",
            "execution/GPUColorGlyphTrueColrFixtureTest.kt",
        )
        val violations = buildList {
            testFiles.forEach { relativePath ->
                val source = testFile(relativePath).readText()
                listOf(
                    "createOffscreenTarget(" to "legacy offscreen-target creation",
                    "target.encode(" to "legacy immediate target encoding",
                    "drawColorGlyphPass(" to "legacy immediate color-glyph draw",
                    "target.readRgba(" to "legacy immediate readback",
                ).forEach { (forbiddenCall, description) ->
                    if (forbiddenCall in source) add("$relativePath uses $description: $forbiddenCall")
                }
                listOf(
                    listOf("GPUTaskList", "buildPreparedColorGlyphTestTaskList(") to "one recorded task list",
                    listOf("prepareSceneFrameSession(") to "one prepared scene session",
                    listOf("GPUSceneFrameOutputRequest.ReadbackRgba") to "planned RGBA readback",
                ).forEach { (acceptedCalls, description) ->
                    if (acceptedCalls.none(source::contains)) {
                        add("$relativePath is missing $description: ${acceptedCalls.joinToString(" or ")}")
                    }
                }
            }
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "Color-glyph native tests bypass the canonical frame route:\n${violations.joinToString("\n")}",
        )
    }

    /** Ensures active specs expose one frame-planning authority without legacy contradictions. */
    @Test
    fun `active gpu renderer specs expose one coherent frame planning authority`() {
        val specs = authoritySpecFiles.associateWith { fileName ->
            authoritySpecRoot.resolve(fileName).readText()
        }
        val violations = buildList {
            canonicalAuthorityOwners.forEach { (concept, expectedOwner) ->
                val authorityRows = specs.flatMap { (fileName, text) ->
                    text.lineSequence()
                        .filter { line -> line.startsWith("| `$concept` |") }
                        .map { line -> "$fileName:$line" }
                        .toList()
                }
                if (authorityRows.size != 1) {
                    add(
                        "$concept must have exactly one normative authority row; " +
                            "found ${authorityRows.size}: ${authorityRows.joinToString()}",
                    )
                } else if ("| `$concept` | `$expectedOwner` owns" !in authorityRows.single()) {
                    add(
                        "$concept must be owned by $expectedOwner; " +
                            "found: ${authorityRows.single()}",
                    )
                }
            }

            val forbiddenAuthority = listOf(
                "a second blend-mode enum in state" to
                    Regex(
                        "(?ms)^### `state`\\s*$.*?" +
                            "^- `GPUBlendMode`\\s*$.*?^### `color`\\s*$",
                    ),
                "an unconditional LCD product refusal" to
                    Regex(
                        "(?im)^(?:LCD subpixel masks are not a target representation\\.|" +
                            "\\| LCD subpixel text \\| Future research; stable refusal\\. \\||" +
                            "- Do not support LCD subpixel text as part of this target\\.)$",
                    ),
                "materialization before final task and frame order" to
                    Regex(
                        "GPUResourceMaterializationDecision\\s*" +
                            "-> GPUTaskList finalization",
                    ),
                "a direct submission entry that bypasses GPUFrameCoordinator" to
                    Regex("`GPUExecutionContext\\.submit\\(\\)`"),
                "a CPU destination snapshot product route" to
                    Regex(
                        "(?i)(?:allow|accept|create|upload|use)[^\\n]{0,80}" +
                            "CPU[^\\n]{0,40}destination snapshot",
                    ),
                "presentation as GPU completion" to
                    Regex(
                        "(?i)(?:present(?:ation)?\\s+" +
                            "(?:is|means|constitutes|completes)\\s+" +
                            "(?:GPU|queue) completion|" +
                            "treat[^\\n]{0,40}present[^\\n]{0,40}as[^\\n]{0,20}" +
                            "(?:GPU|queue) completion)",
                    ),
                "GPUTaskList or GPUResourceProvider encoding GPU work" to
                    Regex(
                        "(?i)\\bencoded\\s+(?:by|through)\\s+" +
                            "(?:`GPUResourceProvider`|`GPUTaskList`)",
                    ),
                "late destination-read products flowing back to semantic planning stages" to
                    Regex(
                        "(?is)(?:" +
                            "(?:`GPUDestinationReadAction`|\\bActions?\\b)" +
                            "[^.]{0,320}\\b(?:consum(?:e|es|ed)|carr(?:y|ies|ied)|" +
                            "feed(?:s|ing)?|fed|pass(?:es|ed)?|return(?:s|ed)?|" +
                            "send(?:s|ing)?|sent)\\b" +
                            "[^.]{0,240}(?:`GPUTaskList`|`GPUDrawLayerPlanner`)|" +
                            "(?:`GPUDestinationReadPlan`|`GPUDestinationReadAction`|" +
                            "`GPUDestinationReadBinding`|`GPUDestinationCopyPlan`|" +
                            "`GPUDestinationCopyTextureDescriptor`|" +
                            "`GPUResourceMaterializationDecision`|`GPUCommandEncoderPlan`)" +
                            "[^.]{0,320}\\b(?:consum(?:e|es|ed)|carr(?:y|ies|ied)|" +
                            "flow(?:s|ed|ing)?|feed(?:s|ing)?|fed|pass(?:es|ed)?|" +
                            "return(?:s|ed)?|send(?:s|ing)?|sent)\\b[^.]{0,240}" +
                            "(?:`GPUTaskList`|`GPUDrawLayerPlanner`)|" +
                            "(?:`GPUTaskList`|`GPUDrawLayerPlanner`)" +
                            "[^.]{0,240}\\b(?:consum(?:e|es|ed)|receive(?:s|d)|" +
                            "accept(?:s|ed)|carr(?:y|ies|ied))\\b[^.]{0,320}" +
                            "(?:`GPUDestinationReadPlan`|`GPUDestinationReadAction`|" +
                            "`GPUDestinationReadBinding`|`GPUDestinationCopyPlan`|" +
                            "`GPUDestinationCopyTextureDescriptor`|" +
                            "`GPUResourceMaterializationDecision`|`GPUCommandEncoderPlan`)" +
                            ")",
                    ),
            )
            forbiddenAuthority.forEach { (description, pattern) ->
                specs.forEach { (fileName, text) ->
                    pattern.findAll(text).forEach { match ->
                        add("$fileName authorizes $description: ${match.value}")
                    }
                }
            }
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "GPU renderer authority conflicts:\n${violations.joinToString("\n")}",
        )
    }

    /** Ensures GPU capabilities do not reintroduce stringly typed GPU spec concepts. */
    @Test
    fun `gpu capabilities do not reintroduce stringly typed GPU spec concepts`() {
        val capabilitySource = productionFile("capabilities/CapabilityContracts.kt").readText()

        assertTrue(
            actual = !capabilitySource.contains("supportedTextureFormats: Set<String>"),
            message = "GPUCapabilities.supportedTextureFormats must use GPUTextureFormat, not String",
        )
        assertTrue(
            actual = !capabilitySource.contains("supportedTextureUsageLabels: Set<String>"),
            message = "GPUCapabilities.supportedTextureUsage must use GPUTextureUsage, not String labels",
        )
        assertTrue(
            actual = !capabilitySource.contains("featureLabels: Set<String>"),
            message = "Renderer feature gates must use GPURendererFeature, not String labels",
        )
    }

    /** Ensures package ownership violations fail with stable diagnostic text. */
    @Test
    fun `package boundary check rejects wrong roots reserved packages and validation imports`() {
        val root = temporarySourceRoot()
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/webgpu/BrowserOnly.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.webgpu

                class BrowserOnly
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/commands/ValidationLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.commands

                import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationReport

                class ValidationLeak
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/legacy/Legacy.kt",
            text = """
                package org.graphiks.kanvas.legacy

                class Legacy
            """.trimIndent(),
        )

        val violations = GPUPackageBoundaryCheck().findViolations(root)
        val report = violations.joinToString("\n")

        assertContains(report, "canonical package root")
        assertContains(report, "reserved package segment")
        assertContains(report, "validation helper import")
    }

    /** Ensures the dependency matrix from the layout spec is enforced by validation tooling. */
    @Test
    fun `package boundary check rejects dependency matrix violations`() {
        val root = temporarySourceRoot()
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/color/FoundationLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.color

                import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
                import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
                import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
                import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContext

                class FoundationLeak
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/clips/DomainLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.clips

                import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContext

                class DomainLeak
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/materials/TextureHandleLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.materials

                import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef

                class TextureHandleLeak
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/wgsl/DomainSemanticsLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.wgsl

                import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor

                class DomainSemanticsLeak
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/execution/SemanticsLeak.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.execution

                import org.graphiks.kanvas.gpu.renderer.geometry.GPUShapeDescriptor

                class SemanticsLeak
            """.trimIndent(),
        )

        val violations = GPUPackageBoundaryCheck().findViolations(root)
        val report = violations.joinToString("\n")

        assertContains(report, "foundation package dependency violation")
        assertContains(report, "domain package dependency violation")
        assertContains(report, "materials concrete resource import violation")
        assertContains(report, "wgsl domain semantics import violation")
        assertContains(report, "execution semantic package import violation")
    }

    /** Ensures package-level cycles are treated as structure failures. */
    @Test
    fun `package boundary check rejects package import cycles`() {
        val root = temporarySourceRoot()
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/alpha/Alpha.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.alpha

                import org.graphiks.kanvas.gpu.renderer.beta.Beta

                class Alpha(val beta: Beta)
            """.trimIndent(),
        )
        root.writeSource(
            relativePath = "org/graphiks/kanvas/gpu/renderer/beta/Beta.kt",
            text = """
                package org.graphiks.kanvas.gpu.renderer.beta

                import org.graphiks.kanvas.gpu.renderer.alpha.Alpha

                class Beta(val alpha: Alpha)
            """.trimIndent(),
        )

        val violations = GPUPackageBoundaryCheck().findViolations(root)

        assertContains(violations.joinToString("\n"), "package cycle")
    }

    /** Ensures validation fixtures expose deterministic concept ownership evidence. */
    @Test
    fun `validation fixture emits first slice ownership dump`() {
        val lines = GPUValidationFixture()
            .firstSliceConceptOwnershipDump()
            .lines()

        assertContains(lines, "commands:GPUDrawCommandID:canonical command identifier")
        assertContains(lines, "commands:NormalizedDrawCommand.FillRect:first-slice draw command")
        assertContains(lines, "commands:GPUMaterialDescriptor.SolidColor:first-slice material descriptor")
    }

    /** Ensures alias evidence is explicit when a public concept name is renamed. */
    @Test
    fun `validation fixture emits command id alias evidence`() {
        val lines = GPUValidationFixture()
            .aliasEvidenceDump()
            .lines()

        assertContains(lines, "commands:GPUCommandId:alias of GPUDrawCommandID")
    }

    /** Writes one Kotlin source file below this temporary source root. */
    private fun File.writeSource(relativePath: String, text: String) {
        val source = resolve(relativePath)
        source.parentFile.mkdirs()
        source.writeText(text)
    }

    /** Creates a temporary Kotlin source root for negative package-boundary fixtures. */
    private fun temporarySourceRoot(): File =
        Files.createTempDirectory("gpu-renderer-boundary-test").toFile()

    /** Resolves one production source file below the canonical renderer root. */
    private fun productionFile(relativePath: String): File =
        mainSourceRoot.resolve("org/graphiks/kanvas/gpu/renderer").resolve(relativePath)

    /** Resolves one test source file below the canonical renderer root. */
    private fun testFile(relativePath: String): File =
        testSourceRoot.resolve("org/graphiks/kanvas/gpu/renderer").resolve(relativePath)

    /** Test constants used by package-boundary validation. */
    private companion object {
        /** Main Kotlin source root for the gpu-renderer module under Gradle test execution. */
        val mainSourceRoot = File("src/main/kotlin")

        /** Test Kotlin source root for architecture guards under Gradle test execution. */
        val testSourceRoot = File("src/test/kotlin")

        /** Scene source root, which may depend only on the coordinator product route. */
        val sceneSourceRoot = File("../gpu-renderer-scenes/src")

        /** Active authority pack root relative to the gpu-renderer Gradle project. */
        val authoritySpecRoot = File("../.upstream/specs/gpu-renderer")

        /** Active authority files synchronized by the frame-planning amendment. */
        val authoritySpecFiles = listOf(
            "README.md",
            "02-gpu-recording-task-graph.md",
            "10-gpu-execution-context-submission.md",
            "12-blend-color-target-state.md",
            "20-destination-read-strategy.md",
            "21-text-glyph-pipeline.md",
            "24-clip-stencil-mask-pipeline.md",
            "32-target-authority-taxonomy-diagnostics.md",
            "34-analysis-materialization-recording.md",
            "35-package-class-layout.md",
            "37-draw-packet-command-stream.md",
        )

        /** Concepts that must have one normative authority row with this exact owner. */
        val canonicalAuthorityOwners = mapOf(
            "GPUBlendPlan" to "passes",
            "GPUFramePlan" to "recording",
            "GPUFrameCoordinator" to "execution",
            "GPUFramePreflighter" to "execution",
            "PreparedGPUFrame" to "execution",
            "GPUSceneTarget" to "resources",
            "GPUQueueCompletionTicket" to "execution",
            "LCDCoverage" to "passes",
            "RefusedCompositeCommand" to "recording",
        )
    }
}
