package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

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
        "solid-path" -> renderSolidPath(320, 240)
        "solid-star-path" -> renderSolidStarPath(320, 240)
        else -> error("Unknown scene: $sceneName")
    }

    val rgba = result.rgbaBytes()
    require(rgba.size == description.width * description.height * BYTES_PER_PIXEL) {
        "RGBA buffer size mismatch: expected ${description.width * description.height * BYTES_PER_PIXEL}, got ${rgba.size}"
    }

    val outputFile = outputDir.resolve(RENDER_FILE_NAME)
    writePng(rgba, description.width, description.height, outputFile.toPath())

    println(
        "KanvasSurface offscreen render complete: scene=$sceneName " +
            "nonTransparentPixels=${result.nonTransparentPixels()} " +
            "dispatched=${result.stats.opsDispatched} refused=${result.stats.opsRefused} " +
            "output=${outputFile.absolutePath}"
    )

    if (result.nonTransparentPixels() == 0) {
        error("ZERO non-transparent pixels — no actual rendering occurred for scene=$sceneName")
    }

    if (result.stats.opsRefused > 0) {
        println("WARNING: ${result.stats.opsRefused} command(s) refused:")
        result.diagnostics.entries.forEach { d -> if (d.code.startsWith("refuse:")) println("  $d") }
    }
}

private data class SceneDescription(
    val width: Int,
    val height: Int,
)

private fun renderSolidRRect(width: Int, height: Int): Pair<RenderResult, SceneDescription> {
    val surface = Surface(width = width, height = height)
    val canvas = surface.canvas()

    val blue = Paint.fill(Color.fromRGBA(0f, 0.5f, 1f, 1f))
    canvas.drawRRect(RRect(Rect(50f, 50f, 270f, 190f), 20f), blue)

    val result = surface.render()
    return Pair(result, SceneDescription(width, height))
}

private fun renderSolidStarPath(width: Int, height: Int): Pair<RenderResult, SceneDescription> {
    val surface = Surface(width = width, height = height)
    val canvas = surface.canvas()

    val magenta = Paint.fill(Color.fromRGBA(1f, 0f, 1f, 1f))
    val path = Path {
        moveTo(160f, 20f)
        lineTo(180f, 80f)
        lineTo(250f, 80f)
        lineTo(195f, 120f)
        lineTo(215f, 185f)
        lineTo(160f, 150f)
        lineTo(105f, 185f)
        lineTo(125f, 120f)
        lineTo(70f, 80f)
        lineTo(140f, 80f)
        close()
    }
    canvas.drawPath(path, magenta)

    val result = surface.render()
    return Pair(result, SceneDescription(width, height))
}

private fun renderSolidPath(width: Int, height: Int): Pair<RenderResult, SceneDescription> {
    val surface = Surface(width = width, height = height)
    val canvas = surface.canvas()

    val green = Paint.fill(Color.fromRGBA(0f, 1f, 0f, 1f))
    val path = Path {
        moveTo(80f, 50f)
        lineTo(240f, 50f)
        lineTo(160f, 190f)
        close()
    }
    canvas.drawPath(path, green)

    val result = surface.render()
    return Pair(result, SceneDescription(width, height))
}

private fun renderSolidRedRect(width: Int, height: Int): Pair<RenderResult, SceneDescription> {
    val surface = Surface(width = width, height = height)
    val canvas = surface.canvas()

    val red = Paint.fill(Color.fromRGBA(1f, 0f, 0f, 1f))
    canvas.drawRect(Rect(50f, 50f, 270f, 190f), red)

    val result = surface.render()
    return Pair(result, SceneDescription(width, height))
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun RenderResult.rgbaBytes(): ByteArray =
    ByteArray(pixels.size) { index -> pixels[index].toByte() }

@OptIn(ExperimentalUnsignedTypes::class)
private fun RenderResult.nonTransparentPixels(): Int {
    var count = 0
    var alphaIndex = 3
    while (alphaIndex < pixels.size) {
        if (pixels[alphaIndex] != 0.toUByte()) count++
        alphaIndex += BYTES_PER_PIXEL
    }
    return count
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
