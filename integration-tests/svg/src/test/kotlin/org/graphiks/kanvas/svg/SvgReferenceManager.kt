package org.graphiks.kanvas.svg

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Manages reference PNG loading from checked-in repository resources.
 * PNGs are stored under src/test/resources/generated-references/
 * mirroring the SVG structure under src/main/resources/by-render-family/
 */
object SvgReferenceManager {
    private const val BYTES_PER_PIXEL = 4

    /**
     * Resolves the reference PNG path from an SVG path.
     * Ex: "/by-render-family/geometric/geometric-1.svg"
     *     → "/generated-references/by-render-family/geometric/geometric-1.png"
     */
    fun getReferencePngPath(svgPath: String): String {
        require(svgPath.endsWith(".svg")) { "svgPath must end with .svg" }
        val pngPath = svgPath.removeSuffix(".svg") + ".png"
        return pngPath.replaceFirst("by-render-family", "generated-references/by-render-family")
    }

    /**
     * Loads a reference PNG from resources and decodes it to RGBA.
     * @param svgPath Path to the SVG (ex: "/by-render-family/geometric/geometric-1.svg")
     * @return RGBA ByteArray (4 bytes per pixel)
     */
    fun loadReferencePng(svgPath: String): ByteArray {
        val pngPath = getReferencePngPath(svgPath)
        val resource = object {}.javaClass.getResourceAsStream(pngPath)
            ?: error("Reference PNG not found: $pngPath (for SVG: $svgPath)")
        return resource.use { stream ->
            val image = ImageIO.read(stream)
                ?: error("Failed to decode PNG: $pngPath")
            bufferedImageToRgba(image)
        }
    }

    /**
     * Checks whether a reference PNG exists for a given SVG.
     */
    fun hasReferencePng(svgPath: String): Boolean {
        val pngPath = getReferencePngPath(svgPath)
        return object {}.javaClass.getResource(pngPath) != null
    }

    private fun bufferedImageToRgba(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val rgba = ByteArray(width * height * BYTES_PER_PIXEL)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = image.getRGB(x, y)
                val i = (y * width + x) * BYTES_PER_PIXEL
                rgba[i] = ((rgb shr 16) and 0xFF).toByte()
                rgba[i + 1] = ((rgb shr 8) and 0xFF).toByte()
                rgba[i + 2] = (rgb and 0xFF).toByte()
                rgba[i + 3] = ((rgb shr 24) and 0xFF).toByte()
            }
        }
        return rgba
    }
}
