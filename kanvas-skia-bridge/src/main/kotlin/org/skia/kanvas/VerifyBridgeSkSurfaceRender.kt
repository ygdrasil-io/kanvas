package org.skia.kanvas

import org.skia.core.SkSurface
import org.skia.foundation.SkPaint
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect

private const val WIDTH: Int = 200
private const val HEIGHT: Int = 200

/**
 * Verifies that SkiaKanvasSurface.flush() renders into the wrapped SkSurface.
 *
 * Run via: ./gradlew :kanvas-skia-bridge:verifyBridgeSkSurfaceRender
 */
fun main() {
    val skSurface: SkSurface = SkSurface.Companion.MakeRasterN32Premul(WIDTH, HEIGHT)
    val kanvasSurface = SkiaKanvasSurface.wrap(skSurface)

    // Draw solid red rect via the bridge
    val paint = SkPaint().apply { color = SkColorSetARGB(255, 255, 0, 0) }
    kanvasSurface.drawRect(SkRect.MakeLTRB(25f, 25f, 175f, 175f), paint)

    // flush() auto-renders to the wrapped SkSurface
    val frame = kanvasSurface.flush()
    require(!frame.isEmpty) { "Frame must not be empty after drawing red rect" }

    // Read pixels from the SkSurface's snapshot
    val snapshot = skSurface.makeImageSnapshot()
    val pixelCount = snapshot.width * snapshot.height

    var nonTransparentPixels = 0
    for (i in 0 until pixelCount) {
        val a = (snapshot.pixels[i] ushr 24) and 0xFF
        if (a > 0) nonTransparentPixels++
    }

    val expectedRectPixels = (175 - 25) * (175 - 25) // 150x150 = 22500

    println(
        "Bridge SkSurface verification: " +
            "nonTransparentPixels=$nonTransparentPixels " +
            "expected=$expectedRectPixels " +
            "totalPixels=$pixelCount"
    )

    require(nonTransparentPixels == expectedRectPixels) {
        "Expected $expectedRectPixels non-transparent pixels but got $nonTransparentPixels"
    }

    println("PASS: Wrapped SkSurface contains rendered rect via flush()")
}
