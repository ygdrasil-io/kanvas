package org.graphiks.kanvas.skia

import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SkiaGmRunner {
    @TempDir
    lateinit var tempDir: File

    companion object {
        @JvmStatic
        fun allGms() = SkiaGmRegistry.all()
    }

    @ParameterizedTest
    @MethodSource("allGms")
    fun `render GM`(gm: SkiaGm) {
        GpuAvailability.requireWebGpu()
        val result = SkiaGmRenderer.render(gm)
        val refPath = "/generated-references/${gm.renderFamily.name.lowercase()}/${gm.name}.png"

        if (!ReferenceManager.hasReference(refPath)) {
            ReferenceManager.savePng(
                result.rgba, result.width, result.height,
                File(tempDir, "${gm.name}.png"),
            )
            error("Reference PNG not found for ${gm.name}. Run generateSkiaReferences task.")
        }

        val reference = ReferenceManager.loadReference(refPath)

        val comparison = ComparisonUtils.compareRgba(
            actual = result.rgba,
            reference = reference,
            width = result.width,
            height = result.height,
            tolerance = gm.tolerance,
            minSimilarity = gm.minSimilarity,
        )

        val outputDir = File(tempDir, gm.name)
        outputDir.mkdirs()
        ComparisonUtils.saveRgbaAsPng(result.rgba, result.width, result.height, File(outputDir, "kanvas.png"))
        ComparisonUtils.saveRgbaAsPng(reference, result.width, result.height, File(outputDir, "reference.png"))
        comparison.diffRgba?.let { diff ->
            ComparisonUtils.saveRgbaAsPng(diff, result.width, result.height, File(outputDir, "diff.png"))
        }

        println(
            "[${if (comparison.isPassing) "PASS" else "FAIL"}] ${gm.name}: " +
            "similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%)",
        )

        assertTrue(comparison.isPassing) {
            "${gm.name}: similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%)"
        }
    }
}
