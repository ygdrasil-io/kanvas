package org.graphiks.kanvas.test

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.png.PngEncoder
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.pow

object ComparisonUtils {
    private const val BYTES_PER_PIXEL = 4
    private val REC2020_TRANSFER_FN = SkcmsTransferFunction(
        g = 2.2222137f,
        a = 0.909668f,
        b = 0.09033203f,
        c = 0.222229f,
        d = 0.08123779f,
        e = 0f,
        f = 0f,
    )

    data class ComparisonResult(
        val similarity: Double,
        val pixelMatch: Double,
        val ssim: Double,
        val meanChannelError: Double,
        val totalPixels: Int,
        val matchingPixels: Int,
        val maxDiff: IntArray,
        val meanDiff: DoubleArray,
        val diffRgba: ByteArray?,
        val isPassing: Boolean,
        val minSimilarity: Double,
    )

    fun compareRgba(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int = 0,
        minSimilarity: Double = 100.0,
    ): ComparisonResult {
        require(actual.size == reference.size) {
            "Buffer sizes differ: actual=${actual.size}, reference=${reference.size}"
        }
        require(actual.size == width * height * BYTES_PER_PIXEL) {
            "Buffer size mismatch: expected=${width * height * BYTES_PER_PIXEL}, actual=${actual.size}"
        }

        val totalPixels = width * height
        var matchingPixels = 0
        val maxDiff = intArrayOf(0, 0, 0, 0)
        val sumDiff = longArrayOf(0L, 0L, 0L, 0L)
        var mismatchCount = 0
        val diffRgba = ByteArray(actual.size)

        for (i in 0 until totalPixels) {
            val base = i * BYTES_PER_PIXEL
            val aR = actual[base].toInt() and 0xFF
            val aG = actual[base + 1].toInt() and 0xFF
            val aB = actual[base + 2].toInt() and 0xFF
            val aA = actual[base + 3].toInt() and 0xFF

            val rR = reference[base].toInt() and 0xFF
            val rG = reference[base + 1].toInt() and 0xFF
            val rB = reference[base + 2].toInt() and 0xFF
            val rA = reference[base + 3].toInt() and 0xFF

            val dR = abs(aR - rR)
            val dG = abs(aG - rG)
            val dB = abs(aB - rB)
            val dA = abs(aA - rA)

            if (dR <= tolerance && dG <= tolerance && dB <= tolerance && dA <= tolerance) {
                matchingPixels++
                diffRgba[base] = 0
                diffRgba[base + 1] = 0
                diffRgba[base + 2] = 0
                diffRgba[base + 3] = 0
            } else {
                mismatchCount++
                maxDiff[0] = maxOf(maxDiff[0], dR)
                maxDiff[1] = maxOf(maxDiff[1], dG)
                maxDiff[2] = maxOf(maxDiff[2], dB)
                maxDiff[3] = maxOf(maxDiff[3], dA)
                sumDiff[0] += dR
                sumDiff[1] += dG
                sumDiff[2] += dB
                sumDiff[3] += dA

                diffRgba[base] = 255.toByte()
                diffRgba[base + 1] = 0.toByte()
                diffRgba[base + 2] = 0.toByte()
                diffRgba[base + 3] = 255.toByte()
            }
        }

        val similarity = if (totalPixels > 0) (matchingPixels.toDouble() / totalPixels) * 100.0 else 100.0
        val meanDiff = sumDiff.map { if (mismatchCount > 0) it.toDouble() / mismatchCount else 0.0 }.toDoubleArray()
        val meanChannelError = if (totalPixels > 0) {
            sumDiff.sum().toDouble() / (totalPixels.toDouble() * BYTES_PER_PIXEL * 255.0)
        } else {
            0.0
        }
        val isPassing = similarity >= minSimilarity

        return ComparisonResult(
            similarity = similarity,
            pixelMatch = similarity,
            ssim = computeSSIM(actual, reference, width, height),
            meanChannelError = meanChannelError,
            totalPixels = totalPixels,
            matchingPixels = matchingPixels,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            diffRgba = if (mismatchCount > 0) diffRgba else null,
            isPassing = isPassing,
            minSimilarity = minSimilarity,
        )
    }

    fun rgbaToBufferedImage(rgba: ByteArray, width: Int, height: Int): BufferedImage {
        require(rgba.size == width * height * BYTES_PER_PIXEL) { "RGBA buffer size mismatch" }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * BYTES_PER_PIXEL
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val a = rgba[i + 3].toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    fun saveRgbaAsPng(rgba: ByteArray, width: Int, height: Int, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val bitmap = rgbaToSkBitmap(rgba, width, height)
        val bytes = PngEncoder.encode(bitmap) ?: error("Failed to encode PNG: ${outputFile.name}")
        outputFile.writeBytes(bytes)
    }

    fun bufferedImageToRgba(image: BufferedImage): ByteArray {
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

    fun loadPngAsSrgbRgba(file: File): ByteArray {
        return decodePngAsSrgbRgba(file.readBytes())
    }

    fun loadPngAsSrgbRgba(stream: InputStream): ByteArray {
        return decodePngAsSrgbRgba(stream.readBytes())
    }

    fun readPngAsSrgbBufferedImage(file: File): BufferedImage {
        val rgba = loadPngAsSrgbRgba(file)
        val size = readPngSize(file.readBytes())
        return rgbaToBufferedImage(rgba, size.first, size.second)
    }

    fun readPngAsSrgbBufferedImage(stream: InputStream): BufferedImage {
        val data = stream.readBytes()
        val rgba = decodePngAsSrgbRgba(data)
        val size = readPngSize(data)
        return rgbaToBufferedImage(rgba, size.first, size.second)
    }

    private fun rgbaToSkBitmap(rgba: ByteArray, width: Int, height: Int): SkBitmap {
        require(rgba.size == width * height * BYTES_PER_PIXEL) { "RGBA buffer size mismatch" }
        val bitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * BYTES_PER_PIXEL
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val a = rgba[i + 3].toInt() and 0xFF
                bitmap.setPixel(x, y, SkColorSetARGB(a, r, g, b))
            }
        }
        return bitmap
    }

    private fun decodePngAsSrgbRgba(data: ByteArray): ByteArray {
        val codec = Codec.MakeFromData(data) ?: error("Failed to decode PNG")
        val info = codec.getInfo()
        val natural = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
        val result = codec.getPixels(info, natural)
        if (result != Codec.Result.kSuccess) error("Failed to decode PNG: $result")
        val sourceColorSpace = pngIccpColorSpace(data) ?: info.colorSpace
        return skBitmapToSrgbRgba(natural, sourceColorSpace)
    }

    private fun skBitmapToSrgbRgba(bitmap: SkBitmap, sourceColorSpace: SkColorSpace): ByteArray {
        val rgba = ByteArray(bitmap.width * bitmap.height * BYTES_PER_PIXEL)
        val srgbToXyz = SkNamedGamut.kSRGB
        val xyzToSrgb = invert3x3(srgbToXyz)
        val f16 = FloatArray(4)
        var offset = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val a: Float
                val encodedRgb = if (bitmap.pixelsF16.isNotEmpty() && bitmap.getPixelF16(x, y, f16)) {
                    a = f16[3].coerceIn(0f, 1f)
                    val invA = if (a > 0f) 1f / a else 0f
                    floatArrayOf(
                        (f16[0] * invA).coerceIn(0f, 1f),
                        (f16[1] * invA).coerceIn(0f, 1f),
                        (f16[2] * invA).coerceIn(0f, 1f),
                    )
                } else {
                    val argb = bitmap.getPixel(x, y)
                    a = (SkColorGetA(argb) / 255f).coerceIn(0f, 1f)
                    floatArrayOf(
                        SkColorGetR(argb) / 255f,
                        SkColorGetG(argb) / 255f,
                        SkColorGetB(argb) / 255f,
                    )
                }
                val srgb = convertEncodedRgbToSrgb(encodedRgb, sourceColorSpace, xyzToSrgb)
                rgba[offset++] = quantizeByte(srgb[0]).toByte()
                rgba[offset++] = quantizeByte(srgb[1]).toByte()
                rgba[offset++] = quantizeByte(srgb[2]).toByte()
                rgba[offset++] = quantizeByte(a.toDouble()).toByte()
            }
        }
        return rgba
    }

    private fun convertEncodedRgbToSrgb(
        rgb: FloatArray,
        sourceColorSpace: SkColorSpace,
        xyzToSrgb: Array<DoubleArray>,
    ): DoubleArray {
        val sourceLinear = DoubleArray(3) { i ->
            decodeTransfer(rgb[i].toDouble(), sourceColorSpace.transferFn)
        }
        val xyz = multiply(sourceColorSpace.toXYZD50, sourceLinear)
        val srgbLinear = multiply(xyzToSrgb, xyz)
        return DoubleArray(3) { i -> encodeSrgb(srgbLinear[i].coerceIn(0.0, 1.0)) }
    }

    private fun decodeTransfer(value: Double, transfer: SkcmsTransferFunction): Double {
        val x = value.coerceIn(0.0, 1.0)
        return if (x < transfer.d.toDouble()) {
            transfer.c.toDouble() * x + transfer.f.toDouble()
        } else {
            (transfer.a.toDouble() * x + transfer.b.toDouble()).pow(transfer.g.toDouble()) + transfer.e.toDouble()
        }.coerceIn(0.0, 1.0)
    }

    private fun encodeSrgb(linear: Double): Double =
        if (linear <= 0.0031308) {
            linear * 12.92
        } else {
            1.055 * linear.pow(1.0 / 2.4) - 0.055
        }

    private fun multiply(matrix: SkcmsMatrix3x3, vector: DoubleArray): DoubleArray =
        DoubleArray(3) { row ->
            matrix[row, 0].toDouble() * vector[0] +
                matrix[row, 1].toDouble() * vector[1] +
                matrix[row, 2].toDouble() * vector[2]
        }

    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(3) { row ->
            matrix[row][0] * vector[0] + matrix[row][1] * vector[1] + matrix[row][2] * vector[2]
        }

    private fun invert3x3(matrix: SkcmsMatrix3x3): Array<DoubleArray> {
        val a = matrix[0, 0].toDouble(); val b = matrix[0, 1].toDouble(); val c = matrix[0, 2].toDouble()
        val d = matrix[1, 0].toDouble(); val e = matrix[1, 1].toDouble(); val f = matrix[1, 2].toDouble()
        val g = matrix[2, 0].toDouble(); val h = matrix[2, 1].toDouble(); val i = matrix[2, 2].toDouble()
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) > 1e-12) { "Color transform matrix is not invertible" }
        return arrayOf(
            doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
            doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
            doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
        )
    }

    private fun quantizeByte(value: Double): Int =
        (value.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt().coerceIn(0, 255)

    private fun readPngSize(data: ByteArray): Pair<Int, Int> {
        require(data.size >= 24) { "PNG data too short" }
        val width = readI32BE(data, 16)
        val height = readI32BE(data, 20)
        return width to height
    }

    private fun pngIccpColorSpace(data: ByteArray): SkColorSpace? {
        val profileName = readPngIccpName(data) ?: return null
        return when {
            profileName.equals("sRGB", ignoreCase = true) -> SkColorSpace.makeSRGB()
            profileName.equals("Rec.2020", ignoreCase = true) ||
                profileName.equals("Rec2020", ignoreCase = true) ||
                profileName.equals("BT.2020", ignoreCase = true) ->
                SkColorSpace.makeRGB(REC2020_TRANSFER_FN, SkNamedGamut.kRec2020)
            profileName.equals("Display P3", ignoreCase = true) ||
                profileName.equals("DisplayP3", ignoreCase = true) ->
                SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
            else -> null
        }
    }

    private fun readPngIccpName(data: ByteArray): String? {
        if (data.size < 8) return null
        var offset = 8
        while (offset + 12 <= data.size) {
            val length = readI32BE(data, offset)
            if (length < 0 || offset + 12L + length > data.size) return null
            val typeOffset = offset + 4
            val dataOffset = offset + 8
            val type = String(data, typeOffset, 4, Charsets.ISO_8859_1)
            if (type == "iCCP") {
                val end = dataOffset + length
                var nameEnd = dataOffset
                while (nameEnd < end && data[nameEnd] != 0.toByte()) nameEnd++
                if (nameEnd == end) return null
                val nameLength = nameEnd - dataOffset
                if (nameLength !in 1..79) return null
                return String(data, dataOffset, nameLength, Charsets.ISO_8859_1)
            }
            offset += 12 + length
        }
        return null
    }

    private fun readI32BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    fun computeSSIM(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
    ): Double {
        val blockSize = 16
        val C1 = (0.01 * 255.0).let { it * it }
        val C2 = (0.03 * 255.0).let { it * it }

        fun luminance(r: Int, g: Int, b: Int): Double =
            0.299 * r + 0.587 * g + 0.114 * b

        val blocksX = width / blockSize
        val blocksY = height / blockSize
        if (blocksX == 0 || blocksY == 0) return 1.0

        var totalSSIM = 0.0
        var blockCount = 0

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val n = blockSize * blockSize
                var sumX = 0.0
                var sumY = 0.0
                var sumXX = 0.0
                var sumYY = 0.0
                var sumXY = 0.0

                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                        val i = px * 4
                        val ar = actual[i].toInt() and 0xFF
                        val ag = actual[i + 1].toInt() and 0xFF
                        val ab = actual[i + 2].toInt() and 0xFF
                        val rr = reference[i].toInt() and 0xFF
                        val rg = reference[i + 1].toInt() and 0xFF
                        val rb = reference[i + 2].toInt() and 0xFF

                        val lx = luminance(ar, ag, ab)
                        val ly = luminance(rr, rg, rb)
                        sumX += lx; sumY += ly
                        sumXX += lx * lx; sumYY += ly * ly
                        sumXY += lx * ly
                    }
                }

                val meanX = sumX / n
                val meanY = sumY / n
                val varX = sumXX / n - meanX * meanX
                val varY = sumYY / n - meanY * meanY
                val covXY = sumXY / n - meanX * meanY

                val numerator = (2.0 * meanX * meanY + C1) * (2.0 * covXY + C2)
                val denominator = (meanX * meanX + meanY * meanY + C1) * (varX + varY + C2)
                val ssim = if (denominator > 0.0) numerator / denominator else 1.0
                totalSSIM += ssim
                blockCount++
            }
        }

        return if (blockCount > 0) totalSSIM / blockCount else 1.0
    }

    fun computeSSIMBlocks(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        blockSize: Int = 16,
    ): List<SsimBlock> {
        val C1 = (0.01 * 255.0).let { it * it }
        val C2 = (0.03 * 255.0).let { it * it }
        fun lum(r: Int, g: Int, b: Int): Double = 0.299 * r + 0.587 * g + 0.114 * b

        val blocksX = width / blockSize
        val blocksY = height / blockSize
        if (blocksX == 0 || blocksY == 0) return emptyList()

        val results = mutableListOf<SsimBlock>()
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val n = blockSize * blockSize
                var sumX = 0.0; var sumY = 0.0; var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                        val i = px * 4
                        val lx = lum(actual[i].toInt() and 0xFF, actual[i + 1].toInt() and 0xFF, actual[i + 2].toInt() and 0xFF)
                        val ly = lum(reference[i].toInt() and 0xFF, reference[i + 1].toInt() and 0xFF, reference[i + 2].toInt() and 0xFF)
                        sumX += lx; sumY += ly; sumXX += lx * lx; sumYY += ly * ly; sumXY += lx * ly
                    }
                }
                val mx = sumX / n; val my = sumY / n
                val vx = sumXX / n - mx * mx; val vy = sumYY / n - my * my; val cv = sumXY / n - mx * my
                val num = (2.0 * mx * my + C1) * (2.0 * cv + C2)
                val den = (mx * mx + my * my + C1) * (vx + vy + C2)
                val s = if (den > 0.0) num / den else 1.0
                results.add(SsimBlock(x = bx * blockSize, y = by * blockSize, score = s))
            }
        }
        return results
    }
}

data class SsimBlock(val x: Int, val y: Int, val score: Double)
