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

private fun normalizeSample(value: Int, maxSample: Int): Int =
    (value.coerceIn(0, maxSample).toDouble() * 255.0 / maxSample).roundToInt().coerceIn(0, 255)

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
