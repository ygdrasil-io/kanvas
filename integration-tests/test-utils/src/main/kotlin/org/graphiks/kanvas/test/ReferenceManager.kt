package org.graphiks.kanvas.test

import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

object ReferenceManager {
    private const val BYTES_PER_PIXEL = 4

    fun hasReference(path: String): Boolean {
        return object {}.javaClass.getResource(path) != null
    }

    fun loadReference(path: String): ByteArray {
        val resource = object {}.javaClass.getResourceAsStream(path)
            ?: error("Reference PNG not found: $path")
        return resource.use { stream ->
            val image = ImageIO.read(stream)
                ?: error("Failed to decode PNG: $path")
            ComparisonUtils.bufferedImageToRgba(image)
        }
    }

    fun savePng(rgba: ByteArray, width: Int, height: Int, outputFile: File) {
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, outputFile)
    }
}
