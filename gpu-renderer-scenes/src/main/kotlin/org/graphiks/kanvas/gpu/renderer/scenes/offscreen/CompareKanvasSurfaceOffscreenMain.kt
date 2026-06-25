package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.RRect
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
        "solid-rrect" -> renderSolidRRect(320, 240)
        "solid-path" -> renderSolidPath(320, 240)
        "solid-star-path" -> renderSolidStarPath(320, 240)
        else -> error("Unknown scene: $sceneName")
    }

    val gpuRgba = render.rgba
    val width = render.width
    val height = render.height

    require(gpuRgba.size == width * height * BYTES_PER_PIXEL) {
        "GPU RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${gpuRgba.size}"
    }

    val referencePixels = generateReferencePixels(width, height, render.scene)
    // tolerance=1 accounts for WGSL vs Kotlin f32 rounding in SDF coverage;
    // the rect comparison uses tolerance=0 because it's binary inside/outside
    // tolerance=1 accounts for WGSL vs Kotlin f32 rounding in SDF coverage;
    // the rect and path comparisons use tolerance=0 (binary inside/outside)
    val tolerance = if (render.scene is RRectScene) 1 else 0
    val comparison = comparePixels(gpuRgba, referencePixels, tolerance = tolerance)

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

private sealed interface Scene {
    val r: Int; val g: Int; val b: Int; val a: Int
    fun isInside(x: Int, y: Int): Boolean
    fun coverage(x: Int, y: Int): Float
}

private data class RectScene(
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    override val r: Int, override val g: Int, override val b: Int, override val a: Int,
) : Scene {
    override fun isInside(x: Int, y: Int): Boolean =
        x >= left && x < right && y >= top && y < bottom

    override fun coverage(x: Int, y: Int): Float =
        if (isInside(x, y)) 1f else 0f
}

private data class RRectScene(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val rx: Float, val ry: Float,
    override val r: Int, override val g: Int, override val b: Int, override val a: Int,
) : Scene {
    override fun isInside(x: Int, y: Int): Boolean =
        coverage(x.toFloat(), y.toFloat()) > 0.5f

    // Sample at pixel center (x+0.5, y+0.5) to match GPU @builtin(position)
    override fun coverage(x: Int, y: Int): Float =
        coverage(x.toFloat() + 0.5f, y.toFloat() + 0.5f)

    private fun coverage(px: Float, py: Float): Float {
        val centreX = 0.5f * (left + right)
        val centreY = 0.5f * (top + bottom)
        val halfX = 0.5f * (right - left)
        val halfY = 0.5f * (bottom - top)
        val rxClamped = maxOf(rx, 1e-4f)
        val ryClamped = maxOf(ry, 1e-4f)
        val qAbsX = kotlin.math.abs(px - centreX)
        val qAbsY = kotlin.math.abs(py - centreY)
        val qX = qAbsX - (halfX - rxClamped)
        val qY = qAbsY - (halfY - ryClamped)
        val outerRectSdf = maxOf(qAbsX - halfX, qAbsY - halfY)
        val qmX = maxOf(qX, 0f)
        val qmY = maxOf(qY, 0f)
        val nX = qmX / rxClamped
        val nY = qmY / ryClamped
        val nl = kotlin.math.sqrt(nX * nX + nY * nY)
        val nlSafe = maxOf(nl, 1e-6f)
        val dirX = nX / nlSafe
        val dirY = nY / nlSafe
        val effectiveR = kotlin.math.sqrt(
            (rxClamped * dirX) * (rxClamped * dirX) + (ryClamped * dirY) * (ryClamped * dirY)
        )
        val cornerSdf = (nl - 1.0f) * effectiveR
        val inCornerBand = if (qX >= 0f && qY >= 0f) 1f else 0f
        val bandSdf = if (inCornerBand > 0.5f) cornerSdf else outerRectSdf
        return (0.5f - bandSdf).coerceIn(0f, 1f)
    }
}

private data class SceneRender(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val scene: Scene,
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

private data class PathScene(
    val ax: Float, val ay: Float, val bx: Float, val by: Float, val cx: Float, val cy: Float,
    override val r: Int, override val g: Int, override val b: Int, override val a: Int,
) : Scene {
    override fun isInside(x: Int, y: Int): Boolean =
        pointInTriangle(x + 0.5f, y + 0.5f, ax, ay, bx, by, cx, cy)

    override fun coverage(x: Int, y: Int): Float =
        if (isInside(x, y)) 1f else 0f

    private fun pointInTriangle(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        fun sign(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float =
            (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2)
        val d1 = sign(px, py, ax, ay, bx, by)
        val d2 = sign(px, py, bx, by, cx, cy)
        val d3 = sign(px, py, cx, cy, ax, ay)
        val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
        return !(hasNeg && hasPos)
    }
}

private data class PolygonScene(
    val verts: List<Pair<Float, Float>>,
    override val r: Int, override val g: Int, override val b: Int, override val a: Int,
) : Scene {
    override fun isInside(x: Int, y: Int): Boolean =
        pointInPolygon(x + 0.5f, y + 0.5f, verts)

    override fun coverage(x: Int, y: Int): Float =
        if (isInside(x, y)) 1f else 0f

    private fun pointInPolygon(px: Float, py: Float, polygon: List<Pair<Float, Float>>): Boolean {
        // Non-zero winding rule matching GPU stencil-cover (increment/decrement)
        var winding = 0
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first; val yi = polygon[i].second
            val xj = polygon[j].first; val yj = polygon[j].second
            if (yi <= py) {
                if (yj > py && (xj - xi) * (py - yi) / (yj - yi) + xi > px) winding++
            } else if (yj <= py && (xj - xi) * (py - yi) / (yj - yi) + xi > px) winding--
            j = i
        }
        return winding != 0
    }
}

private fun renderSolidStarPath(width: Int, height: Int): SceneRender {
    val surface = Surface(width = width, height = height)
    val canvas = Canvas(surface)

    val magenta = Paint().apply {
        r = 1f
        g = 0f
        b = 1f
        a = 1f
    }
    val path = Path().apply {
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

    val result = surface.renderToRgba()
    val verts = listOf(
        160f to 20f, 180f to 80f, 250f to 80f, 195f to 120f, 215f to 185f,
        160f to 150f, 105f to 185f, 125f to 120f, 70f to 80f, 140f to 80f,
    )
    val scene = PolygonScene(verts = verts, r = 255, g = 0, b = 255, a = 255)

    println(
        "GPU render: nonTransparentPixels=${result.nonTransparentPixels} " +
            "dispatched=${result.dispatchedCount} refused=${result.refusedCount}"
    )

    return SceneRender(rgba = result.rgba, width = width, height = height, scene = scene)
}

private fun renderSolidPath(width: Int, height: Int): SceneRender {
    val surface = Surface(width = width, height = height)
    val canvas = Canvas(surface)

    val green = Paint().apply {
        r = 0f
        g = 1f
        b = 0f
        a = 1f
    }
    val path = Path().apply {
        moveTo(80f, 50f)
        lineTo(240f, 50f)
        lineTo(160f, 190f)
        close()
    }
    canvas.drawPath(path, green)

    val result = surface.renderToRgba()
    val scene = PathScene(
        ax = 80f, ay = 50f, bx = 240f, by = 50f, cx = 160f, cy = 190f,
        r = 0, g = 255, b = 0, a = 255,
    )

    println(
        "GPU render: nonTransparentPixels=${result.nonTransparentPixels} " +
            "dispatched=${result.dispatchedCount} refused=${result.refusedCount}"
    )

    return SceneRender(rgba = result.rgba, width = width, height = height, scene = scene)
}

private fun renderSolidRRect(width: Int, height: Int): SceneRender {
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
    val scene = RRectScene(
        left = 50f, top = 50f, right = 270f, bottom = 190f,
        rx = 20f, ry = 20f, r = 0, g = 128, b = 255, a = 255,
    )

    println(
        "GPU render: nonTransparentPixels=${result.nonTransparentPixels} " +
            "dispatched=${result.dispatchedCount} refused=${result.refusedCount}"
    )

    return SceneRender(rgba = result.rgba, width = width, height = height, scene = scene)
}

private fun generateReferencePixels(width: Int, height: Int, scene: Scene): ByteArray {
    val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = (y * width + x) * BYTES_PER_PIXEL
            val cov = scene.coverage(x, y)
            // GPU outputs premultiplied: (R*a*cov, G*a*cov, B*a*cov, a*cov) as bytes
            pixels[i] = (scene.r * scene.a * cov / 255).toInt().coerceIn(0, 255).toByte()
            pixels[i + 1] = (scene.g * scene.a * cov / 255).toInt().coerceIn(0, 255).toByte()
            pixels[i + 2] = (scene.b * scene.a * cov / 255).toInt().coerceIn(0, 255).toByte()
            pixels[i + 3] = (scene.a * cov).toInt().coerceIn(0, 255).toByte()
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
