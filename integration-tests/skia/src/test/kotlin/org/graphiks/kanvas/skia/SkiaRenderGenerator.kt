package org.graphiks.kanvas.skia

import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: SkiaRenderGenerator <outputDir>")
        kotlin.system.exitProcess(1)
    }

    val outputDir = File(args[0])
    val config = RenderConfig.fromEnvironment()
    RuntimeEffectWgsl4kWiring.install()
    val gms = SkiaGmRegistry.all()

    if (gms.isEmpty()) {
        println("No GMs registered")
        kotlin.system.exitProcess(0)
    }

    var rendered = 0
    var failed = 0

    val includeBlocking = args.any { it == "--include-blocking" }

    for (gm in gms) {
        if (!includeBlocking && gm.renderCost == RenderCost.BLOCKING) {
            println("[SKIP] ${gm.name} — BLOCKING")
            continue
        }

        val familyDir = File(outputDir, gm.renderFamily.name.lowercase())
        familyDir.mkdirs()
        val outputFile = File(familyDir, "${gm.name}.png")

        try {
            val result = SkiaGmRenderer.render(gm, config = config)
            ComparisonUtils.saveRgbaAsPng(result.rgba, result.width, result.height, outputFile)
            println("[RENDER] ${gm.renderFamily.name.lowercase()}/${gm.name}.png (${result.width}x${result.height}, dispatch=${result.dispatchedCount}, refuse=${result.refusedCount})")
            result.diagnostics.forEach { d -> println("  ${d}") }
            rendered++
        } catch (e: Throwable) {
            println("[FAIL] ${gm.name} — ${e.message}")
            failed++
        }
    }

    println()
    println("Done: $rendered rendered, $failed failed")

    if (rendered == 0) {
        kotlin.system.exitProcess(1)
    }
}
