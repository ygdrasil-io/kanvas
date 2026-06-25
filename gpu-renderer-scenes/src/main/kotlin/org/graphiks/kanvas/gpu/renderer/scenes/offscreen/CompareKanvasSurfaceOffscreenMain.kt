package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.Surface

private const val BYTES_PER_PIXEL: Int = 4
private const val RENDER_FILE_NAME: String = "render.png"
private const val REFERENCE_FILE_NAME: String = "reference.png"
private const val DIFF_FILE_NAME: String = "diff.png"

private data class BitmapComparison(
    val similarity: Double,
    val totalPixels: Int,
    val matchingPixels: Int,
    val maxR: Int, val maxG: Int, val maxB: Int, val maxA: Int,
    val meanR: Double, val meanG: Double, val meanB: Double, val meanA: Double,
)

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: CompareKanvasSurfaceOffscreenMainKt <output-dir> <scene-name>"
    }

    val outputDir = File(args[0])
    val sceneName = args[1]
    outputDir.mkdirs()

    val render = when (sceneName) {
        "solid-red-rect" -> renderSolidRedRect(320, 240)
        else -> error("Unknown scene: $sceneName")
    }

    val gpuRgba = render.rgba
    val width = render.width
    val height = render.height

    require(gpuRgba.size == width * height * BYTES_PER_PIXEL) {
        "GPU RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${gpuRgba.size}"
    }

    val referencePixels = generateReferencePixels(width, height, render.scene)
    val comparison = comparePixels(gpuRgba, referencePixels, tolerance = 0)

    writePng(gpuRgba, width, height, outputDir.resolve(RENDER_FILE_NAME).toPath())
    writePng(referencePixels, width, height, outputDir.resolve(REFERENCE_FILE_NAME).toPath())

    if (comparison.similarity < 100.0) {
        val diffPixels = generateDiffPixels(gpuRgba, referencePixels, width, height)
        writePng(diffPixels, width, height, outputDir.resolve(DIFF_FILE_NAME).toPath())
    }

    println(
        "KanvasSurface GPU vs CPU comparison complete: scene=$sceneName " +
            "similarity=${"%.2f".format(comparison.similarity)}% " +
            "matching=${comparison.matchingPixels}/${comparison.totalPixels} " +
            "maxDiff=(R=${comparison.maxR},G=${comparison.maxG},B=${comparison.maxB},A=${comparison.maxA}) " +
            "meanDiff=(R=${"%.2f".format(comparison.meanR)},G=${"%.2f".format(comparison.meanG)}," +
            "B=${"%.2f".format(comparison.meanB)},A=${"%.2f".format(comparison.meanA)}) " +
            "output=${outputDir.absolutePath}"
    )

    if (comparison.similarity < 100.0) {
        error(
            "GPU output does not match CPU reference! " +
                "similarity=%.2f%% matching=%d/%d".format(
                    comparison.similarity, comparison.matchingPixels, comparison.totalPixels
                )
        )
    }

    println("PASS: GPU output matches CPU reference (100% similarity)")
}

private data class SceneRender(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val scene: RectScene,
)

private data class RectScene(
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val r: Int, val g: Int, val b: Int, val a: Int,
)

private fun renderSolidRedRect(width: Int, height: Int): SceneRender {
    val surface = Surface(width = width, height = height)
    val canvas = Canvas(surface)

    val red = Paint().apply {
        r = 1f
        g = 0f
        b = 0f
        a = 1f
    }
    canvas.drawRect(Rect(50f, 50f, 270f, 190f), red)

    val result = surface.renderToRgba()
    val scene = RectScene(left = 50, top = 50, right = 270, bottom = 190, r = 255, g = 0, b = 0, a = 255)

    println(
        "GPU render: nonTransparentPixels=${result.nonTransparentPixels} " +
            "dispatched=${result.dispatchedCount} refused=${result.refusedCount}"
    )

    return SceneRender(rgba = result.rgba, width = width, height = height, scene = scene)
}

private fun generateReferencePixels(width: Int, height: Int, scene: RectScene): ByteArray {
    val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = (y * width + x) * BYTES_PER_PIXEL
            val inside = x >= scene.left && x < scene.right && y >= scene.top && y < scene.bottom
            if (inside) {
                pixels[i] = scene.r.toByte()
                pixels[i + 1] = scene.g.toByte()
                pixels[i + 2] = scene.b.toByte()
                pixels[i + 3] = scene.a.toByte()
            }
            // else: already zeroed
        }
    }
    return pixels
}

private fun comparePixels(
    a: ByteArray,
    b: ByteArray,
    tolerance: Int = 0,
): BitmapComparison {
    require(a.size == b.size) { "Buffer sizes differ: ${a.size} vs ${b.size}" }
    val total = a.size / BYTES_PER_PIXEL
    var matching = 0
    var maxR = 0; var maxG = 0; var maxB = 0; var maxA = 0
    var sumR = 0L; var sumG = 0L; var sumB = 0L; var sumA = 0L
    var mismatchCount = 0

    for (i in 0 until total) {
        val base = i * BYTES_PER_PIXEL
        val ra = a[base].toInt() and 0xFF
        val rb = b[base].toInt() and 0xFF
        val ga = a[base + 1].toInt() and 0xFF
        val gb = b[base + 1].toInt() and 0xFF
        val ba = a[base + 2].toInt() and 0xFF
        val bb = b[base + 2].toInt() and 0xFF
        val aa = a[base + 3].toInt() and 0xFF
        val ab = b[base + 3].toInt() and 0xFF

        val dr = kotlin.math.abs(ra - rb)
        val dg = kotlin.math.abs(ga - gb)
        val db = kotlin.math.abs(ba - bb)
        val da = kotlin.math.abs(aa - ab)

        if (maxOf(dr, dg, db, da) <= tolerance) {
            matching++
        } else {
            maxR = maxOf(maxR, dr)
            maxG = maxOf(maxG, dg)
            maxB = maxOf(maxB, db)
            maxA = maxOf(maxA, da)
            sumR += dr; sumG += dg; sumB += db; sumA += da
            mismatchCount++
        }
    }

    val similarity = if (total > 0) (matching.toDouble() / total) * 100.0 else 100.0
    return BitmapComparison(
        similarity = similarity,
        totalPixels = total,
        matchingPixels = matching,
        maxR = maxR, maxG = maxG, maxB = maxB, maxA = maxA,
        meanR = if (mismatchCount > 0) sumR.toDouble() / mismatchCount else 0.0,
        meanG = if (mismatchCount > 0) sumG.toDouble() / mismatchCount else 0.0,
        meanB = if (mismatchCount > 0) sumB.toDouble() / mismatchCount else 0.0,
        meanA = if (mismatchCount > 0) sumA.toDouble() / mismatchCount else 0.0,
    )
}

private fun generateDiffPixels(
    actual: ByteArray,
    reference: ByteArray,
    width: Int,
    height: Int,
): ByteArray {
    val diff = ByteArray(actual.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = (y * width + x) * BYTES_PER_PIXEL
            var dr = (actual[i].toInt() and 0xFF) - (reference[i].toInt() and 0xFF)
            var dg = (actual[i + 1].toInt() and 0xFF) - (reference[i + 1].toInt() and 0xFF)
            var db = (actual[i + 2].toInt() and 0xFF) - (reference[i + 2].toInt() and 0xFF)

            dr = dr.coerceIn(-128, 127)
            dg = dg.coerceIn(-128, 127)
            db = db.coerceIn(-128, 127)

            diff[i] = (128 + dr).toByte()
            diff[i + 1] = (128 + dg).toByte()
            diff[i + 2] = (128 + db).toByte()
            diff[i + 3] = 255.toByte()
        }
    }
    return diff
}

private fun writePng(pixels: ByteArray, width: Int, height: Int, path: java.nio.file.Path) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (pixelIndex in 0 until width * height) {
        val base = pixelIndex * BYTES_PER_PIXEL
        val r = pixels[base].toInt() and 0xFF
        val g = pixels[base + 1].toInt() and 0xFF
        val b = pixels[base + 2].toInt() and 0xFF
        val a = pixels[base + 3].toInt() and 0xFF
        image.setRGB(pixelIndex % width, pixelIndex / width, (a shl 24) or (r shl 16) or (g shl 8) or b)
    }
    require(ImageIO.write(image, "png", path.toFile())) {
        "No PNG writer available"
    }
}
