package org.graphiks.kanvas.svg

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Gère le chargement des PNG de référence checkés dans le repo.
 * Les PNG sont stockés dans src/test/resources/generated-references/
 * avec la même structure que les SVG dans src/main/resources/by-render-family/
 */
object SvgReferenceManager {
    private const val BYTES_PER_PIXEL = 4

    /**
     * Résout le chemin d'un PNG de référence à partir d'un chemin SVG.
     * Ex: "/by-render-family/geometric/geometric-1.svg"
     *     → "/generated-references/by-render-family/geometric/geometric-1.png"
     */
    fun getReferencePngPath(svgPath: String): String {
        require(svgPath.endsWith(".svg")) { "svgPath must end with .svg" }
        val pngPath = svgPath.removeSuffix(".svg") + ".png"
        return pngPath.replaceFirst("by-render-family", "generated-references/by-render-family")
    }

    /**
     * Charge un PNG de référence depuis les ressources et le décode en RGBA.
     * @param svgPath Chemin vers le SVG (ex: "/by-render-family/geometric/geometric-1.svg")
     * @return ByteArray RGBA (4 bytes par pixel)
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
     * Vérifie qu'un PNG de référence existe pour un SVG donné.
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
