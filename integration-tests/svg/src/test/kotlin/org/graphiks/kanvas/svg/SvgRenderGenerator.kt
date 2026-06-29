package org.graphiks.kanvas.svg

import java.io.File

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: SvgRenderGenerator <inputDir> <outputDir>")
        kotlin.system.exitProcess(1)
    }

    val inputDir = File(args[0])
    val outputDir = File(args[1])

    require(inputDir.exists()) { "Input directory not found: $inputDir" }

    val svgFiles = inputDir.walkTopDown().filter { it.extension == "svg" }.toList()

    if (svgFiles.isEmpty()) {
        println("No SVG files found in $inputDir")
        kotlin.system.exitProcess(0)
    }

    var rendered = 0
    var skipped = 0

    for (svgFile in svgFiles) {
        val relativePath = inputDir.toURI().relativize(svgFile.toURI()).path
        val outputFile = File(outputDir, relativePath.removeSuffix(".svg") + ".png")

        try {
            val svgContent = svgFile.readText()
            val (rgba, width, height) = SvgGpuRenderer.renderSvgContentToRgba(
                svgContent = svgContent,
                width = 800,
                height = 600
            )
            SvgComparisonUtils.saveRgbaAsPng(rgba, width, height, outputFile)
            println("[RENDER] $relativePath -> ${outputFile.name} (${width}x$height)")
            rendered++
        } catch (e: IllegalStateException) {
            println("[SKIP] $relativePath — ${e.message}")
            skipped++
        } catch (e: Exception) {
            println("[SKIP] $relativePath — ${e.message}")
            skipped++
        }
    }

    println()
    println("Done: $rendered rendered, $skipped skipped")

    if (rendered == 0) {
        kotlin.system.exitProcess(1)
    }
}
