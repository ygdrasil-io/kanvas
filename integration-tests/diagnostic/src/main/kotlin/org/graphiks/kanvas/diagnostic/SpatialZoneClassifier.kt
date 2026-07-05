package org.graphiks.kanvas.diagnostic

import kotlin.math.abs

/**
 * Classifies pixel regions by visual characteristic: EDGE (high local gradient),
 * SOLID (uniform color), GRADIENT (progressive color change), TEXT (dense edges).
 */
enum class ZoneType { EDGE, SOLID, GRADIENT, TEXT }

/**
 * A labeled spatial region with aggregated diff statistics, used by the agent
 * to localize rendering discrepancies.
 */
data class ZoneRegion(
    val label: String,
    val bounds: ZoneBounds,
    val type: ZoneType,
    val dominantChannel: String,
    val severity: String,
    val avgDelta: Double,
)

/**
 * Pixel coordinates of a rectangular region: x, y, width, height.
 */
data class ZoneBounds(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Classifies pixels in a reference image into spatial zones using Sobel edge
 * detection on the luminance channel. TEXT zones are identified by edge density
 * in 64x64 blocks.
 */
object SpatialZoneClassifier {
    fun classify(
        rgba: ByteArray,
        width: Int,
        height: Int,
    ): Array<ZoneType> {
        val zones = Array(width * height) { ZoneType.SOLID }
        val lum = DoubleArray(width * height) { i ->
            val base = i * 4
            0.299 * (rgba[base].toInt() and 0xFF) + 0.587 * (rgba[base + 1].toInt() and 0xFF) + 0.114 * (rgba[base + 2].toInt() and 0xFF)
        }

        val sobelThreshold = 30.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = abs(
                    -1 * lum[i - width - 1] + 0 * lum[i - 1] + 1 * lum[i + width - 1] +
                    -2 * lum[i - width]     + 0 * lum[i]     + 2 * lum[i + width] +
                    -1 * lum[i - width + 1] + 0 * lum[i + 1] + 1 * lum[i + width + 1])
                val gy = abs(
                    -1 * lum[i - width - 1] - 2 * lum[i - 1] - 1 * lum[i + width - 1] +
                     0 * lum[i - width]     + 0 * lum[i]     + 0 * lum[i + width] +
                     1 * lum[i - width + 1] + 2 * lum[i + 1] + 1 * lum[i + width + 1])
                if (gx + gy > sobelThreshold) zones[i] = ZoneType.EDGE
            }
        }
        return zones
    }

    fun classifyTextZones(zones: Array<ZoneType>, width: Int, height: Int): Array<ZoneType> {
        val result = zones.copyOf()
        val blockSize = 64
        for (by in 0 until height step blockSize) {
            for (bx in 0 until width step blockSize) {
                var edgeCount = 0; var total = 0
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val x = bx + dx; val y = by + dy
                        if (x >= width || y >= height) continue
                        if (zones[y * width + x] == ZoneType.EDGE) edgeCount++
                        total++
                    }
                }
                if (total > 0 && edgeCount.toDouble() / total > 0.15) {
                    for (dy in 0 until blockSize) {
                        for (dx in 0 until blockSize) {
                            val x = bx + dx; val y = by + dy
                            if (x < width && y < height) result[y * width + x] = ZoneType.TEXT
                        }
                    }
                }
            }
        }
        return result
    }
}
