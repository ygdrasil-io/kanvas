package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File

fun main(args: Array<String>) {
    try {
        generateSkiaRenders(args)
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

internal fun generateSkiaRenders(args: Array<String>) {
    val options = parseSkiaRenderGeneratorOptions(args)
    val config = RenderConfig.fromEnvironment()
    RuntimeEffectWgsl4kWiring.install()
    val gms = selectSkiaGmsForRender(SkiaGmRegistry.all(), options)

    if (gms.isEmpty()) {
        println("No GMs registered")
        kotlin.system.exitProcess(0)
    }

    var rendered = 0
    var failed = 0

    for (gm in gms) {
        val familyDir = File(options.outputDir, gm.renderFamily.name.lowercase())
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

internal data class SkiaRenderGeneratorOptions(
    val outputDir: File,
    val family: RenderFamily?,
    val name: String?,
    val includeBlocking: Boolean,
)

internal fun parseSkiaRenderGeneratorOptions(args: Array<String>): SkiaRenderGeneratorOptions {
    require(args.isNotEmpty()) { "Usage: SkiaRenderGenerator <outputDir> [--family FAMILY] [--name NAME] [--include-blocking]" }
    val outputDir = File(args[0])
    var family: RenderFamily? = null
    var name: String? = null
    var includeBlocking = false
    var index = 1
    while (index < args.size) {
        when (val arg = args[index]) {
            "--family" -> {
                require(index + 1 < args.size) { "--family requires a value" }
                val rawFamily = args[index + 1]
                family = RenderFamily.entries.firstOrNull { it.name == rawFamily.uppercase() }
                    ?: throw IllegalArgumentException("Unknown GM family: $rawFamily")
                index += 2
            }
            "--name" -> {
                require(index + 1 < args.size) { "--name requires a value" }
                name = args[index + 1]
                index += 2
            }
            "--include-blocking" -> {
                includeBlocking = true
                index += 1
            }
            else -> throw IllegalArgumentException("Unknown SkiaRenderGenerator argument: $arg")
        }
    }
    return SkiaRenderGeneratorOptions(outputDir, family, name, includeBlocking)
}

internal fun selectSkiaGmsForRender(
    gms: List<SkiaGm>,
    options: SkiaRenderGeneratorOptions,
): List<SkiaGm> =
    gms.filter { gm ->
        (options.includeBlocking || gm.renderCost != RenderCost.BLOCKING) &&
            (options.family == null || gm.renderFamily == options.family) &&
            (options.name == null || gm.name == options.name)
    }
