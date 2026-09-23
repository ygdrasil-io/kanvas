@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Independent RGBA8 oracle for the admitted W6d distant-diffuse family. */
object W6dLightingCpuOracle {
    enum class EdgeMode { CLAMP, DECAL }

    fun distantDiffuseRgba8(
        width: Int,
        height: Int,
        alpha: FloatArray,
        childLeft: Int,
        childTop: Int,
        childRight: Int,
        childBottom: Int,
        directionX: Float,
        directionY: Float,
        directionZ: Float,
        surfaceDepth: Float,
        kd: Float,
    ): UByteArray {
        require(width > 0 && height > 0 && alpha.size == width * height)
        val leftMode = if (childLeft == 0) EdgeMode.CLAMP else EdgeMode.DECAL
        val topMode = if (childTop == 0) EdgeMode.CLAMP else EdgeMode.DECAL
        val rightMode = if (childRight == width) EdgeMode.CLAMP else EdgeMode.DECAL
        val bottomMode = if (childBottom == height) EdgeMode.CLAMP else EdgeMode.DECAL
        fun sample(x: Int, y: Int): Float {
            var sx = x
            var sy = y
            if (sx < childLeft) {
                if (leftMode == EdgeMode.DECAL) return 0f
                sx = childLeft
            } else if (sx >= childRight) {
                if (rightMode == EdgeMode.DECAL) return 0f
                sx = childRight - 1
            }
            if (sy < childTop) {
                if (topMode == EdgeMode.DECAL) return 0f
                sy = childTop
            } else if (sy >= childBottom) {
                if (bottomMode == EdgeMode.DECAL) return 0f
                sy = childBottom - 1
            }
            return alpha[sy * width + sx]
        }
        val directionLength = sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ)
        return UByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val dx = .25f * ((sample(x + 1, y - 1) + 2f * sample(x + 1, y) + sample(x + 1, y + 1)) -
                    (sample(x - 1, y - 1) + 2f * sample(x - 1, y) + sample(x - 1, y + 1)))
                val dy = .25f * ((sample(x - 1, y + 1) + 2f * sample(x, y + 1) + sample(x + 1, y + 1)) -
                    (sample(x - 1, y - 1) + 2f * sample(x, y - 1) + sample(x + 1, y - 1)))
                val nx = -surfaceDepth * dx
                val ny = -surfaceDepth * dy
                val normalLength = sqrt(nx * nx + ny * ny + 1f)
                val diffuse = if (directionLength == 0f) 0f else max(0f,
                    (nx * directionX + ny * directionY + directionZ) / (normalLength * directionLength))
                val linear = min(1f, kd * diffuse)
                val srgb = if (linear <= .0031308f) 12.92f * linear else 1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - .055f
                val channel = (srgb * 255f).roundToInt().toUByte()
                val index = (y * width + x) * 4
                output[index] = channel
                output[index + 1] = channel
                output[index + 2] = channel
                output[index + 3] = 255u
            }
        }
    }
}
