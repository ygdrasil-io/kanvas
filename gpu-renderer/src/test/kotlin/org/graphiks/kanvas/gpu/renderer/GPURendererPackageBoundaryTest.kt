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

    /** Ensures geometry remains independent from execution-only contracts. */
    @Test
    fun `geometry source does not import execution contracts`() {
        val pathTessellatorSource = productionFile("geometry/PathTessellator.kt").readText()

        assertTrue(
            actual = "org.graphiks.kanvas.gpu.renderer.execution." !in pathTessellatorSource,
            message = "Geometry must not import execution contracts",
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

    /** Test constants used by package-boundary validation. */
    private companion object {
        /** Main Kotlin source root for the gpu-renderer module under Gradle test execution. */
        val mainSourceRoot = File("src/main/kotlin")
    }
}
