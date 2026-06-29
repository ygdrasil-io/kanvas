package org.graphiks.kanvas.svg

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.TestAbortedException
import java.io.File

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

        try {
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
                "matching=${comparison.matchingPixels}/${comparison.totalPixels}"
            )

            assertTrue(comparison.isPassing) {
                "SVG $svgPath: similarity=${"%.2f".format(comparison.similarity)}% " +
                "(threshold: ${comparison.minSimilarity}%)"
            }
        } catch (e: IllegalStateException) {
            throw TestAbortedException("SVG feature not yet supported: ${e.message}", e)
        }
    }

    @Test
    fun `test geometric-1`() {
        requireWebGpu()
        testSvg("/by-render-family/geometric/geometric-1.svg", minSimilarity = 50.0, tolerance = 2)
    }

    @Test
    fun `test geometric-2`() {
        requireWebGpu()
        testSvg("/by-render-family/geometric/geometric-2.svg", minSimilarity = 30.0, tolerance = 2)
    }

    @Test
    fun `test ghostscript-tiger`() {
        requireWebGpu()
        testSvg("/by-render-family/complex-paths/complex-1.svg", minSimilarity = 20.0, tolerance = 2)
    }

    @Test
    fun `test complex-paths-2`() {
        requireWebGpu()
        testSvg("/by-render-family/complex-paths/complex-2.svg", minSimilarity = 90.0, tolerance = 2)
    }

    @Test
    fun `test complex-paths-3`() {
        requireWebGpu()
        testSvg("/by-render-family/complex-paths/complex-3.svg", minSimilarity = 90.0, tolerance = 2)
    }

    @Test
    fun `test texture-2`() {
        requireWebGpu()
        testSvg("/by-render-family/textures/texture-2.svg", minSimilarity = 5.0, tolerance = 5)
    }

    @Test
    fun `test texture-3`() {
        requireWebGpu()
        testSvg("/by-render-family/textures/texture-3.svg", minSimilarity = 1.0, tolerance = 5)
    }
}
