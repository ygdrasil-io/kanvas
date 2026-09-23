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

    enum class Family { POINT_DIFFUSE, SPOT_DIFFUSE, DISTANT_SPECULAR, POINT_SPECULAR, SPOT_SPECULAR }

    /**
     * Independent scalar oracle for the five W6d families added after distant diffuse.
     * Coordinates deliberately use texel centres, matching the public raster contract.
     */
    fun remainingFamilyRgba8(
        family: Family,
        width: Int,
        height: Int,
        alpha: FloatArray,
        locationX: Float,
        locationY: Float,
        locationZ: Float,
        targetX: Float = 0f,
        targetY: Float = 0f,
        targetZ: Float = 0f,
        surfaceDepth: Float,
        coefficient: Float,
        shininess: Float = 1f,
        specularExponent: Float = 1f,
        cutoffDegrees: Float = 180f,
    ): UByteArray {
        require(width > 0 && height > 0 && alpha.size == width * height)
        fun normalized(x: Float, y: Float, z: Float): Triple<Float, Float, Float>? {
            val length = sqrt(x * x + y * y + z * z)
            return if (!length.isFinite() || length == 0f) null else Triple(x / length, y / length, z / length)
        }
        fun sample(x: Int, y: Int): Float = alpha[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
        val spotAxis = normalized(targetX - locationX, targetY - locationY, targetZ - locationZ)
        val cutoff = kotlin.math.cos(Math.toRadians(cutoffDegrees.toDouble())).toFloat()
        return UByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val dx = .25f * ((sample(x + 1, y - 1) + 2f * sample(x + 1, y) + sample(x + 1, y + 1)) -
                    (sample(x - 1, y - 1) + 2f * sample(x - 1, y) + sample(x - 1, y + 1)))
                val dy = .25f * ((sample(x - 1, y + 1) + 2f * sample(x, y + 1) + sample(x + 1, y + 1)) -
                    (sample(x - 1, y - 1) + 2f * sample(x, y - 1) + sample(x + 1, y - 1)))
                val normal = normalized(-surfaceDepth * dx, -surfaceDepth * dy, 1f)
                val surfaceToLight = if (family == Family.DISTANT_SPECULAR) {
                    normalized(locationX, locationY, locationZ)
                } else normalized(locationX - (x + .5f), locationY - (y + .5f), locationZ - alpha[y * width + x] * surfaceDepth)
                var contribution = 0f
                if (normal != null && surfaceToLight != null) {
                    val cone = if (family == Family.SPOT_DIFFUSE || family == Family.SPOT_SPECULAR) {
                        val axis = spotAxis
                        if (axis == null) 0f else {
                            val cosAngle = -(surfaceToLight.first * axis.first + surfaceToLight.second * axis.second + surfaceToLight.third * axis.third)
                            if (cosAngle < cutoff) 0f else cosAngle.pow(specularExponent) * min(1f, (cosAngle - cutoff) / .016f)
                        }
                    } else 1f
                    val diffuse = max(0f, normal.first * surfaceToLight.first + normal.second * surfaceToLight.second + normal.third * surfaceToLight.third)
                    contribution = if (family == Family.POINT_DIFFUSE || family == Family.SPOT_DIFFUSE) diffuse * cone else {
                        val half = normalized(surfaceToLight.first, surfaceToLight.second, surfaceToLight.third + 1f)
                        if (half == null) 0f else {
                            val base = normal.first * half.first + normal.second * half.second + normal.third * half.third
                            val powered = when {
                                base < 0f || base == 0f && shininess < 0f -> 0f
                                else -> base.pow(shininess)
                            }
                            if (powered.isFinite()) powered * cone else 0f
                        }
                    }
                }
                val linear = min(1f, max(0f, coefficient * contribution))
                val srgb = if (linear <= .0031308f) 12.92f * linear else
                    1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - .055f
                val channel = (srgb * 255f).roundToInt().toUByte()
                val index = (y * width + x) * 4
                output[index] = channel
                output[index + 1] = channel
                output[index + 2] = channel
                output[index + 3] = if (family == Family.POINT_DIFFUSE || family == Family.SPOT_DIFFUSE) 255u
                else (linear * 255f).roundToInt().toUByte()
            }
        }
    }

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
