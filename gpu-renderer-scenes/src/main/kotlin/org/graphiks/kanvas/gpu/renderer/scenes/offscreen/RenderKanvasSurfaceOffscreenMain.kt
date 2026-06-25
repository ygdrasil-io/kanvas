package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.RRect
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.Surface
import org.graphiks.kanvas.SurfaceRenderResult

private const val BYTES_PER_PIXEL: Int = 4
private const val RENDER_FILE_NAME: String = "render.png"

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: RenderKanvasSurfaceOffscreenMainKt <output-dir> <scene-name>"
    }

    val outputDir = File(args[0])
    val sceneName = args[1]
    outputDir.mkdirs()

    val (result, description) = when (sceneName) {
        "solid-red-rect" -> renderSolidRedRect(320, 240)
        "solid-rrect" -> renderSolidRRect(320, 240)
        else -> error("Unknown scene: $sceneName")
    }

    val rgba = result.rgba
    require(rgba.size == description.width * description.height * BYTES_PER_PIXEL) {
        "RGBA buffer size mismatch: expected ${description.width * description.height * BYTES_PER_PIXEL}, got ${rgba.size}"
    }

    val outputFile = outputDir.resolve(RENDER_FILE_NAME)
    writePng(rgba, description.width, description.height, outputFile.toPath())

    println(
        "KanvasSurface offscreen render complete: scene=$sceneName " +
            "nonTransparentPixels=${result.nonTransparentPixels} " +
            "dispatched=${result.dispatchedCount} refused=${result.refusedCount} " +
            "output=${outputFile.absolutePath}"
    )

    if (result.nonTransparentPixels == 0) {
        error("ZERO non-transparent pixels — no actual rendering occurred for scene=$sceneName")
    }

    if (result.refusedCount > 0) {
        println("WARNING: ${result.refusedCount} command(s) refused:")
        result.diagnostics.forEach { d -> if (d.startsWith("refuse:")) println("  $d") }
    }
}

private data class SceneDescription(
    val width: Int,
    val height: Int,
)

private fun renderSolidRRect(width: Int, height: Int): Pair<SurfaceRenderResult, SceneDescription> {
    val surface = Surface(width = width, height = height)
    val canvas = Canvas(surface)

    val blue = Paint().apply {
        r = 0f
        g = 0.5f
        b = 1f
        a = 1f
    }
    canvas.drawRRect(RRect(Rect(50f, 50f, 270f, 190f), 20f, 20f), blue)

    val result = surface.renderToRgba()
    return Pair(result, SceneDescription(width, height))
}

private fun renderSolidRedRect(width: Int, height: Int): Pair<SurfaceRenderResult, SceneDescription> {
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
    return Pair(result, SceneDescription(width, height))
}

private fun writePng(pixels: ByteArray, width: Int, height: Int, path: java.nio.file.Path) {
    require(pixels.size == width * height * BYTES_PER_PIXEL) {
        "RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${pixels.size}"
    }
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
