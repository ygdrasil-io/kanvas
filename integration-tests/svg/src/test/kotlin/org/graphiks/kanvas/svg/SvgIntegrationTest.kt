package org.graphiks.kanvas.svg

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.TestAbortedException
import java.io.File

/**
 * Tests d'intégration pour valider que le rendu SVG de Kanvas correspond aux références PNG.
 */
class SvgIntegrationTest {
    @TempDir
    lateinit var tempDir: File

    private fun requireWebGpu() {
        if (!gpuAvailable) {
            throw TestAbortedException("WebGPU not available on this machine")
        }
    }

    companion object {
        private val gpuAvailable: Boolean by lazy {
            GPUBackendRuntimeFactory.createOrNull() != null
        }
    }

    private fun testSvg(svgPath: String, minSimilarity: Double, tolerance: Int = 0) {
        val svgContent = object {}.javaClass.getResource(svgPath)?.readText()
            ?: error("SVG not found: $svgPath")

        if (!SvgReferenceManager.hasReferencePng(svgPath)) {
            error("Reference PNG not found for $svgPath. Run: ./scripts/generate_svg_references.sh")
        }

        val (actualRgba, width, height) = SvgGpuRenderer.renderSvgContentToRgba(
            svgContent = svgContent,
            width = 800,
            height = 600
        )

        val referenceRgba = SvgReferenceManager.loadReferencePng(svgPath)

        val comparison = SvgComparisonUtils.compareRgba(
            actual = actualRgba,
            reference = referenceRgba,
            width = width,
            height = height,
            tolerance = tolerance,
            minSimilarity = minSimilarity
        )

        val svgName = File(svgPath).nameWithoutExtension
        val outputDir = File(tempDir, svgName)
        outputDir.mkdirs()

        SvgComparisonUtils.saveRgbaAsPng(actualRgba, width, height, File(outputDir, "kanvas.png"))
        SvgComparisonUtils.saveRgbaAsPng(referenceRgba, width, height, File(outputDir, "reference.png"))
        comparison.diffRgba?.let { diff ->
            SvgComparisonUtils.saveRgbaAsPng(diff, width, height, File(outputDir, "diff.png"))
        }

        println(
            "[${if (comparison.isPassing) "PASS" else "FAIL"}] $svgPath: " +
            "similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%), " +
            "matching=${comparison.matchingPixels}/${comparison.totalPixels}, " +
            "maxDiff=(R=${comparison.maxDiff[0]},G=${comparison.maxDiff[1]},B=${comparison.maxDiff[2]},A=${comparison.maxDiff[3]})"
        )

        assertTrue(comparison.isPassing) {
            "SVG $svgPath: similarité=${"%.2f".format(comparison.similarity)}% " +
            "(seuil: ${comparison.minSimilarity}%)"
        }
    }

    private fun getMinSimilarity(svgPath: String): Double {
        return when {
            svgPath.contains("geometric") -> 100.0
            svgPath.contains("gradients") -> 99.0
            svgPath.contains("transparencies") -> 99.0
            svgPath.contains("complex-paths") -> 98.0
            svgPath.contains("typography") -> 95.0
            else -> 99.0
        }
    }

    private fun getTolerance(svgPath: String): Int {
        return when {
            svgPath.contains("geometric") -> 0
            svgPath.contains("gradients") -> 1
            svgPath.contains("transparencies") -> 1
            svgPath.contains("complex-paths") -> 2
            svgPath.contains("typography") -> 2
            else -> 1
        }
    }

    @Test
    fun `test geometric SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/geometric/geometric-1.svg",
            "/by-render-family/geometric/geometric-2.svg",
            "/by-render-family/geometric/geometric-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test gradient SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/gradients/gradient-1.svg",
            "/by-render-family/gradients/gradient-2.svg",
            "/by-render-family/gradients/gradient-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test transparency SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/transparencies/transparent-1.svg",
            "/by-render-family/transparencies/transparent-2.svg",
            "/by-render-family/transparencies/transparent-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test complex-path SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/complex-paths/complex-1.svg",
            "/by-render-family/complex-paths/complex-2.svg",
            "/by-render-family/complex-paths/complex-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test layer SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/layers/layer-1.svg",
            "/by-render-family/layers/layer-2.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test shadow SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/shadows/shadow-1.svg",
            "/by-render-family/shadows/shadow-2.svg",
            "/by-render-family/shadows/shadow-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test texture SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/textures/texture-1.svg",
            "/by-render-family/textures/texture-2.svg",
            "/by-render-family/textures/texture-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test typography SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/typography/typography-1.svg",
            "/by-render-family/typography/typography-2.svg",
            "/by-render-family/typography/typography-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test realistic SVGs`() {
        requireWebGpu()
        listOf(
            "/by-render-family/realistic/detailed-1.svg",
            "/by-render-family/realistic/detailed-2.svg",
            "/by-render-family/realistic/detailed-3.svg"
        ).forEach { svgPath ->
            testSvg(svgPath, getMinSimilarity(svgPath), getTolerance(svgPath))
        }
    }

    @Test
    fun `test single SVG for debugging`() {
        requireWebGpu()
        val svgPath = "/by-render-family/geometric/geometric-1.svg"
        testSvg(svgPath, 100.0, 0)
    }
}
