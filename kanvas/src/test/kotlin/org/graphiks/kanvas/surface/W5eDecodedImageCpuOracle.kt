@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.math.geometry.RectF32
import kotlin.math.floor

/** Independent discrete oracle: image coordinates use pixel centres i + 0.5. */
internal object W5eDecodedImageCpuOracle {
    // Opaque sRGB primaries keep transfer/attachment quantization exact.
    fun pixels(): ByteArray = byteArrayOf(
        -1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1,
        -1, -1, 0, -1, 0, -1, -1, -1, -1, 0, -1, -1,
    )

    fun nearest(widthI32: Int, heightI32: Int, srcF32: RectF32, dstF32: RectF32): UByteArray {
        val source = pixels()
        val result = UByteArray(widthI32 * heightI32 * 4)
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val xF64 = xI32 + .5
            val yF64 = yI32 + .5
            if (xF64 < minOf(dstF32.left, dstF32.right) || xF64 >= maxOf(dstF32.left, dstF32.right) ||
                yF64 < minOf(dstF32.top, dstF32.bottom) || yF64 >= maxOf(dstF32.top, dstF32.bottom)) continue
            val sXF64 = srcF32.left + (xF64 - dstF32.left) / (dstF32.right - dstF32.left) * (srcF32.right - srcF32.left)
            val sYF64 = srcF32.top + (yF64 - dstF32.top) / (dstF32.bottom - dstF32.top) * (srcF32.bottom - srcF32.top)
            val texelI32 = floor(sYF64).toInt().coerceIn(0, 1) * 3 + floor(sXF64).toInt().coerceIn(0, 2)
            repeat(4) { channelI32 -> result[(yI32 * widthI32 + xI32) * 4 + channelI32] = source[texelI32 * 4 + channelI32].toUByte() }
        }
        return result
    }
}
