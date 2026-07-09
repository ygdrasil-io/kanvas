package org.graphiks.kanvas.test

import java.awt.image.BufferedImage
import java.io.File

object ReferenceManager {
    private const val BYTES_PER_PIXEL = 4

    fun hasReference(path: String): Boolean {
        return object {}.javaClass.getResource(path) != null
    }

    fun loadReference(path: String): ByteArray {
        val resource = object {}.javaClass.getResourceAsStream(path)
            ?: error("Reference PNG not found: $path")
        return resource.use { stream ->
            ComparisonUtils.loadPngAsSrgbRgba(stream)
        }
    }

    fun savePng(rgba: ByteArray, width: Int, height: Int, outputFile: File) {
        ComparisonUtils.saveRgbaAsPng(rgba, width, height, outputFile)
    }
}
