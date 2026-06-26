package org.graphiks.kanvas

import org.graphiks.kanvas.font.scaler.GlyphScaleResult
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import kotlin.system.exitProcess

private object TextGpuEvidence

/**
 * Native WebGPU evidence for the A8 DrawTextRun route (opt-in, JavaExec only).
 *
 * Renders real Latin text through `Surface.renderToRgba()` on WebGPU and checks
 * GPU↔CPU coverage parity against [TextRunCpuOracle]. Prints a structured evidence
 * block and exits non-zero on failure. Skips cleanly when WebGPU is unavailable.
 */
fun main() {
    val fontResource = "/fonts/liberation/LiberationSans-Regular.ttf"
    val width = 256
    val height = 96
    val text = "ABC"
    val size = 48f

    val fontBytes = TextGpuEvidence::class.java.getResourceAsStream(fontResource)?.readBytes()
        ?: error("text-gpu-evidence: font resource missing $fontResource")
    val scaler = GlyphScaler.fromBytes(fontBytes)

    val glyphIds = ArrayList<UShort>()
    val positions = ArrayList<KanvasPoint>()
    var pen = 12f
    val baseline = 56f
    for (ch in text) {
        val glyphId = scaler.glyphIdForCodepoint(ch.code) ?: continue
        glyphIds += glyphId.toUShort()
        positions += KanvasPoint(pen, baseline)
        val scaled = scaler.scaleGlyphOrDiagnostic(glyphId, size)
        pen += if (scaled is GlyphScaleResult.Success) scaled.glyph.advanceWidth else size * 0.6f
    }

    val blob = TextBlob(
        glyphRuns = listOf(KanvasGlyphRun(glyphs = glyphIds, positions = positions)),
        typeface = KanvasTypeface(fontResource),
        fontSize = size,
    )

    val surface = Surface(width = width, height = height)
    Canvas(surface).drawTextBlob(blob, 0f, 0f, Paint().color(1f, 1f, 1f, 1f))

    val command = surface.recorder.recordedCommands()
        .filterIsInstance<NormalizedDrawCommand.DrawTextRun>()
        .single()
    val plan = TextRunDispatchPlanner.plan(command, width, height)
    if (plan !is TextRunDispatchPlan.Draws) {
        println("text-gpu-evidence: FAIL plan refused: ${(plan as TextRunDispatchPlan.Refused).reason}")
        exitProcess(1)
    }
    val cpuRgba = TextRunCpuOracle.composite(plan, width, height)
    val cpuNonZero = TextRunCpuOracle.nonTransparentPixels(cpuRgba)

    val probe = GPUBackendRuntimeFactory.createOrNull()
    if (probe == null) {
        println("text-gpu-evidence: SKIP webgpu-unavailable (cpuOracleNonTransparent=$cpuNonZero glyphs=${plan.placements.size})")
        return
    }
    probe.close()

    val result = surface.renderToRgba()
    val gpuNonZero = result.nonTransparentPixels

    var overlap = 0
    var i = 3
    while (i < result.rgba.size && i < cpuRgba.size) {
        val gpu = result.rgba[i].toInt() and 0xFF
        val cpu = cpuRgba[i].toInt() and 0xFF
        if (gpu > 0 && cpu > 0) overlap++
        i += 4
    }
    val parity = overlap.toDouble() / maxOf(1, maxOf(gpuNonZero, cpuNonZero))

    println("text-gpu-evidence: text=\"$text\" size=$size surface=${width}x$height")
    println("text-gpu-evidence: dispatched=${result.dispatchedCount} refused=${result.refusedCount}")
    result.diagnostics.forEach { println("text-gpu-evidence: diag $it") }
    println("text-gpu-evidence: atlas=${plan.atlasWidth}x${plan.atlasHeight} atlasBytes=${plan.atlasBytes.size} glyphQuads=${plan.placements.size}")
    println("text-gpu-evidence: gpuNonTransparent=$gpuNonZero cpuNonTransparent=$cpuNonZero overlap=$overlap parity=${"%.3f".format(parity)}")
    println("text-gpu-evidence: nonclaim=no-aa no-subpixel no-sdf no-color no-shaping no-bearing no-multipage")

    val ratio = minOf(gpuNonZero, cpuNonZero).toDouble() / maxOf(1, maxOf(gpuNonZero, cpuNonZero))
    val ok = result.dispatchedCount == 1 && gpuNonZero > 0 && cpuNonZero > 0 && ratio >= 0.5 && parity >= 0.5
    if (ok) {
        println("text-gpu-evidence: PASS real GPU A8 text pixels with CPU parity")
        exitProcess(0)
    } else {
        println("text-gpu-evidence: FAIL (countRatio=${"%.3f".format(ratio)} parity=${"%.3f".format(parity)})")
        exitProcess(1)
    }
}
