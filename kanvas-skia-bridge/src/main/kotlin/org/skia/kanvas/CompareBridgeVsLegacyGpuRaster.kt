package org.skia.kanvas

import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.gpu.webgpu.SkWebGpuDevice
import org.skia.gpu.webgpu.WebGpuContext

private const val LW = 200
private const val LH = 200

fun main() {
    println("=== Bridge vs Legacy SkWebGpuDevice Comparison ===")
    println()

    val results = mutableListOf<PixelComparison>()

    results.add(compareRect())
    results.add(compareRRect())
    results.add(comparePath())

    println()
    println("=== Summary ===")
    for (r in results) {
        val status = if (r.similarity >= 99.0) "PASS" else "FAIL"
        println("$status | ${r.scene} | similarity=${"%.2f".format(r.similarity)}% matching=${r.matching}/${r.total} maxDiff=${r.maxDiff}")
    }
    val allPassed = results.all { it.similarity >= 99.0 }
    if (allPassed) {
        println("All comparisons passed (threshold >= 99.0%)")
    } else {
        System.err.println("FAIL: Some comparisons below threshold")
        System.exit(1)
    }
}

private fun compareRect(): PixelComparison {
    val rect = SkRect.MakeLTRB(25f, 25f, 175f, 175f)
    val paintColor = SkColorSetARGB(255, 255, 0, 0)
    return compareScene("Rect solid fill") { surface, legacyCanvas ->
        val p = SkPaint().apply { color = paintColor }
        surface.drawRect(rect, p)
        legacyCanvas.drawRect(
            SkRect.MakeLTRB(25f, 25f, 175f, 175f),
            p,
        )
    }
}

private fun compareRRect(): PixelComparison {
    val rrectRect = SkRect.MakeLTRB(30f, 30f, 170f, 170f)
    val rrect = SkRRect.MakeRectXY(rrectRect, 15f, 15f)
    val paintColor = SkColorSetARGB(255, 0, 128, 255)
    return compareScene("RRect solid fill") { surface, legacyCanvas ->
        val p = SkPaint().apply { color = paintColor }
        surface.drawRRect(rrect, p)
        legacyCanvas.drawRRect(rrect, p)
    }
}

private fun comparePath(): PixelComparison {
    val path = SkPathBuilder()
        .moveTo(50f, 30f)
        .lineTo(160f, 60f)
        .lineTo(80f, 170f)
        .close()
        .detach()
    val paintColor = SkColorSetARGB(255, 255, 128, 0)
    return compareScene("Path solid triangle fill") { surface, legacyCanvas ->
        val p = SkPaint().apply {
            color = paintColor
            style = SkPaint.Style.kFill_Style
            isAntiAlias = false
        }
        surface.drawPath(path, p)
        legacyCanvas.drawPath(path, p)
    }
}

private typealias LegacyDraw = (SkiaKanvasSurface, SkCanvas) -> Unit

private fun compareScene(sceneName: String, draw: LegacyDraw): PixelComparison {
    println("Scene: $sceneName")

    val bridgeSurface = SkSurface.MakeRasterN32Premul(LW, LH)
    val kanvasSurface = SkiaKanvasSurface.wrap(bridgeSurface)

    val context = WebGpuContext.createOrNull()
    requireNotNull(context) { "WebGPU context unavailable — is the GPU driver active?" }

    val legacyBytes = context.use { ctx ->
        SkWebGpuDevice(ctx, LW, LH).use { device ->
            device.setBackground(0)
            val legacyCanvas = SkCanvas(device)
            draw(kanvasSurface, legacyCanvas)
            kanvasSurface.flush()
            device.flush()
        }
    }

    val bridgeSnapshot = bridgeSurface.makeImageSnapshot()
    requireNotNull(bridgeSnapshot) { "Bridge snapshot must not be null" }
    val bridgePixels = bridgeSnapshot.pixels

    val legacyPixels = legacyBytes.toArgbInts()

    val total = LW * LH
    var matching = 0
    var maxDiff = 0
    for (i in 0 until total) {
        val bp = bridgePixels[i]
        val lp = legacyPixels[i]
        if (bp == lp) {
            matching++
        } else {
            val da = kotlin.math.abs(((bp ushr 24) and 0xFF) - ((lp ushr 24) and 0xFF))
            val dr = kotlin.math.abs(((bp ushr 16) and 0xFF) - ((lp ushr 16) and 0xFF))
            val dg = kotlin.math.abs(((bp ushr 8) and 0xFF) - ((lp ushr 8) and 0xFF))
            val db = kotlin.math.abs((bp and 0xFF) - (lp and 0xFF))
            val d = maxOf(da, dr, dg, db)
            if (d > maxDiff) maxDiff = d
        }
    }
    val similarity = matching.toDouble() / total.toDouble() * 100.0

    println("  similarity=${"%.2f".format(similarity)}% matching=$matching/$total maxDiff=$maxDiff")
    return PixelComparison(sceneName, similarity, matching, total, maxDiff)
}

private fun ByteArray.toArgbInts(): IntArray {
    val count = this.size / 4
    val ints = IntArray(count)
    for (i in 0 until count) {
        val off = i * 4
        val r = this[off].toInt() and 0xFF
        val g = this[off + 1].toInt() and 0xFF
        val b = this[off + 2].toInt() and 0xFF
        val a = this[off + 3].toInt() and 0xFF
        ints[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return ints
}
