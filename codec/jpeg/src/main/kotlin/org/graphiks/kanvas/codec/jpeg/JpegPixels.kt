package org.graphiks.kanvas.codec.jpeg

import kotlin.math.roundToInt

/** Converts decoded JPEG component samples into unpremultiplied ARGB pixels. */
internal fun composePixels(samples: DecodedJpegSamples, colorModel: JpegColorModel): IntArray {
    val expectedPlanes = when (colorModel) {
        JpegColorModel.GRAYSCALE -> 1
        JpegColorModel.YCBCR, JpegColorModel.RGB -> 3
        JpegColorModel.CMYK, JpegColorModel.YCCK -> 4
    }
    if (samples.planes.size != expectedPlanes) fail()
    val maxSample = (1 shl samples.precision) - 1
    val pixels = IntArray(samples.width * samples.height)
    for (index in pixels.indices) {
        val c0 = normalizeSample(samples.planes[0][index], maxSample)
        pixels[index] = when (colorModel) {
            JpegColorModel.GRAYSCALE -> argb(0xFF, c0, c0, c0)
            JpegColorModel.RGB -> argb(
                0xFF,
                c0,
                normalizeSample(samples.planes[1][index], maxSample),
                normalizeSample(samples.planes[2][index], maxSample),
            )
            JpegColorModel.YCBCR -> yCbCrToArgb(
                c0,
                normalizeSample(samples.planes[1][index], maxSample),
                normalizeSample(samples.planes[2][index], maxSample),
            )
            JpegColorModel.CMYK -> invertedCmykToArgb(
                c0,
                normalizeSample(samples.planes[1][index], maxSample),
                normalizeSample(samples.planes[2][index], maxSample),
                normalizeSample(samples.planes[3][index], maxSample),
            )
            JpegColorModel.YCCK -> ycckToArgb(
                c0,
                normalizeSample(samples.planes[1][index], maxSample),
                normalizeSample(samples.planes[2][index], maxSample),
                normalizeSample(samples.planes[3][index], maxSample),
            )
        }
    }
    return pixels
}

/** Converts decoded JPEG component samples into normalized RGBA values for F16 output. */
internal fun composeF16Pixels(samples: DecodedJpegSamples, colorModel: JpegColorModel): FloatArray {
    val expectedPlanes = when (colorModel) {
        JpegColorModel.GRAYSCALE -> 1
        JpegColorModel.YCBCR, JpegColorModel.RGB -> 3
        JpegColorModel.CMYK, JpegColorModel.YCCK -> 4
    }
    if (samples.planes.size != expectedPlanes) fail()
    if (samples.precision == 8) return argbPixelsToF16(composePixels(samples, colorModel))

    val maxSample = (1 shl samples.precision) - 1
    val chromaCenter = (1 shl (samples.precision - 1)).toFloat() / maxSample
    val pixels = FloatArray(samples.width * samples.height * 4)
    for (index in 0 until samples.width * samples.height) {
        val c0 = normalizeSampleF16(samples.planes[0][index], maxSample)
        val offset = index * 4
        when (colorModel) {
            JpegColorModel.GRAYSCALE -> {
                pixels[offset] = c0
                pixels[offset + 1] = c0
                pixels[offset + 2] = c0
            }
            JpegColorModel.RGB -> {
                pixels[offset] = c0
                pixels[offset + 1] = normalizeSampleF16(samples.planes[1][index], maxSample)
                pixels[offset + 2] = normalizeSampleF16(samples.planes[2][index], maxSample)
            }
            JpegColorModel.YCBCR -> yCbCrToF16(
                pixels,
                offset,
                c0,
                normalizeSampleF16(samples.planes[1][index], maxSample),
                normalizeSampleF16(samples.planes[2][index], maxSample),
                chromaCenter,
            )
            JpegColorModel.CMYK -> invertedCmykToF16(
                pixels,
                offset,
                c0,
                normalizeSampleF16(samples.planes[1][index], maxSample),
                normalizeSampleF16(samples.planes[2][index], maxSample),
                normalizeSampleF16(samples.planes[3][index], maxSample),
            )
            JpegColorModel.YCCK -> {
                yCbCrToF16(
                    pixels,
                    offset,
                    c0,
                    normalizeSampleF16(samples.planes[1][index], maxSample),
                    normalizeSampleF16(samples.planes[2][index], maxSample),
                    chromaCenter,
                )
                val k = normalizeSampleF16(samples.planes[3][index], maxSample)
                pixels[offset] *= k
                pixels[offset + 1] *= k
                pixels[offset + 2] *= k
            }
        }
        pixels[offset + 3] = 1f
    }
    return pixels
}

private fun normalizeSample(value: Int, maxSample: Int): Int =
    (value.coerceIn(0, maxSample).toDouble() * 255.0 / maxSample).roundToInt().coerceIn(0, 255)

private fun normalizeSampleF16(value: Int, maxSample: Int): Float =
    value.coerceIn(0, maxSample).toFloat() / maxSample

private fun argbPixelsToF16(argbPixels: IntArray): FloatArray =
    FloatArray(argbPixels.size * 4).also { pixels ->
        for (index in argbPixels.indices) {
            val argb = argbPixels[index]
            val offset = index * 4
            pixels[offset] = ((argb ushr 16) and 0xFF) / 255f
            pixels[offset + 1] = ((argb ushr 8) and 0xFF) / 255f
            pixels[offset + 2] = (argb and 0xFF) / 255f
            pixels[offset + 3] = ((argb ushr 24) and 0xFF) / 255f
        }
    }

private fun yCbCrToF16(
    pixels: FloatArray,
    offset: Int,
    y: Float,
    cb: Float,
    cr: Float,
    chromaCenter: Float,
) {
    pixels[offset] = (y + 1.402f * (cr - chromaCenter)).coerceIn(0f, 1f)
    pixels[offset + 1] = (y - 0.344136f * (cb - chromaCenter) - 0.714136f * (cr - chromaCenter)).coerceIn(0f, 1f)
    pixels[offset + 2] = (y + 1.772f * (cb - chromaCenter)).coerceIn(0f, 1f)
}

private fun invertedCmykToF16(
    pixels: FloatArray,
    offset: Int,
    c: Float,
    m: Float,
    y: Float,
    k: Float,
) {
    pixels[offset] = c * k
    pixels[offset + 1] = m * k
    pixels[offset + 2] = y * k
}

internal fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)

internal fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
    val cbShifted = cb - 128
    val crShifted = cr - 128
    return argb(
        0xFF,
        (y + 1.402 * crShifted).roundToInt().coerceIn(0, 255),
        (y - 0.344136 * cbShifted - 0.714136 * crShifted).roundToInt().coerceIn(0, 255),
        (y + 1.772 * cbShifted).roundToInt().coerceIn(0, 255),
    )
}

internal fun ycckToArgb(y: Int, cb: Int, cr: Int, k: Int): Int {
    val rgb = yCbCrToArgb(y, cb, cr)
    return argb(
        0xFF,
        (((rgb ushr 16) and 0xFF) * k + 127) / 255,
        (((rgb ushr 8) and 0xFF) * k + 127) / 255,
        ((rgb and 0xFF) * k + 127) / 255,
    )
}

internal fun invertedCmykToArgb(c: Int, m: Int, y: Int, k: Int): Int =
    argb(0xFF, (c * k + 127) / 255, (m * k + 127) / 255, (y * k + 127) / 255)
