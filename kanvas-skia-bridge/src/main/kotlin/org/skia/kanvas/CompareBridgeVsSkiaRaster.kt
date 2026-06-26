package org.skia.kanvas

import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect

private const val WIDTH = 200
private const val HEIGHT = 200

data class PixelComparison(
    val scene: String,
    val similarity: Double,
    val matching: Int,
    val total: Int,
    val maxDiff: Int,
) {
    val passed: Boolean get() = similarity >= 99.0
}

fun main() {
    println("=== Bridge vs Skia Software Raster Comparison ===")
    println()

    val results = mutableListOf<PixelComparison>()

    results.add(compareRect())
    results.add(compareRRect())

    println()
    println("=== Summary ===")
    for (r in results) {
        val status = if (r.passed) "PASS" else "FAIL"
        println("$status | ${r.scene} | similarity=${"%.2f".format(r.similarity)}% matching=${r.matching}/${r.total} maxDiff=${r.maxDiff}")
    }
    val allPassed = results.all { it.passed }
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

private typealias SceneDraw = (SkiaKanvasSurface, SkCanvas) -> Unit

private fun compareScene(sceneName: String, draw: SceneDraw): PixelComparison {
    println("Scene: $sceneName")

    val bridgeSurface = SkSurface.MakeRasterN32Premul(WIDTH, HEIGHT)
    val kanvasSurface = SkiaKanvasSurface.wrap(bridgeSurface)
    val legacySurface = SkSurface.MakeRasterN32Premul(WIDTH, HEIGHT)

    draw(kanvasSurface, legacySurface.canvas)

    // Flush bridge (triggers renderToRgba() GPU)
    kanvasSurface.flush()

    val bridgeSnapshot = bridgeSurface.makeImageSnapshot()
    requireNotNull(bridgeSnapshot) { "Bridge snapshot must not be null" }
    val bridgePixels = bridgeSnapshot.pixels

    val legacySnapshot = legacySurface.makeImageSnapshot()
    requireNotNull(legacySnapshot) { "Legacy snapshot must not be null" }
    val legacyPixels = legacySnapshot.pixels

    val total = WIDTH * HEIGHT
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
