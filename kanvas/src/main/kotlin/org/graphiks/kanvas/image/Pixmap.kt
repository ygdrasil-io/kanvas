package org.graphiks.kanvas.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.math.color.halfToFloat
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.toArgbInt

class Pixmap(
    val info: ImageInfo,
    data: ByteBuffer,
    val rowBytes: Int,
) {
    private val buffer: ByteBuffer

    init {
        require(rowBytes >= 0) { "rowBytes must be non-negative: $rowBytes" }
        val minRowBytes = info.minimumRowBytes()
        require(info.isEmpty() || rowBytes.toLong() >= minRowBytes) {
            "rowBytes=$rowBytes < minRowBytes=$minRowBytes"
        }
        require(data.remaining().toLong() >= info.computeByteSize(rowBytes)) {
            "buffer remaining=${data.remaining()} < byteSize=${info.computeByteSize(rowBytes)}"
        }
        buffer = data.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    fun width(): Int = info.width

    fun height(): Int = info.height

    fun colorType(): ColorType = info.colorType

    fun alphaType(): AlphaType = info.alphaType

    fun colorSpace(): ImageColorSpace = info.colorSpace

    fun addr(): ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    fun computeByteSize(): Long = info.computeByteSize(rowBytes)

    fun getArgb(x: Int, y: Int): Int {
        if (x !in 0 until width() || y !in 0 until height()) return 0
        if (!info.colorType.capabilities().cpuReadableWritable) {
            throw UnsupportedOperationException("unsupported CPU-readable color type: ${info.colorType}")
        }
        val offset = y * rowBytes + x * info.bytesPerPixel()
        return when (info.colorType) {
            ColorType.RGBA_8888 -> {
                val rgba = buffer.getInt(offset)
                (rgba and -0x1000000) or
                    ((rgba and 0xFF) shl 16) or
                    (rgba and 0xFF00) or
                    ((rgba ushr 16) and 0xFF)
            }
            ColorType.BGRA_8888 -> buffer.getInt(offset)
            ColorType.ALPHA_8 -> Color.fromRGBA(0f, 0f, 0f, byte(offset) / 255f).toArgbInt()
            ColorType.GRAY_8 -> {
                val lightness = byte(offset) / 255f
                Color.fromRGBA(lightness, lightness, lightness, 1f).toArgbInt()
            }
            ColorType.RGB_565,
            ColorType.ARGB_4444,
            ColorType.RGBA_F16,
            ColorType.RGBA_F16_NORM,
                -> pixelColor(offset).toArgbInt()
            else -> 0
        }
    }

    /** Reads the stored premultiplied F16 components without converting through ARGB. */
    fun getPremulRgbaF16(x: Int, y: Int, out: FloatArray): Boolean {
        require(info.colorType == ColorType.RGBA_F16 || info.colorType == ColorType.RGBA_F16_NORM) {
            "getPremulRgbaF16 requires an F16 pixmap, got ${info.colorType}"
        }
        require(out.size >= 4) { "out must hold at least four components" }
        if (x !in 0 until width() || y !in 0 until height()) return false
        val offset = y * rowBytes + x * info.bytesPerPixel()
        out[0] = halfToFloat(ushort(offset).toShort())
        out[1] = halfToFloat(ushort(offset + 2).toShort())
        out[2] = halfToFloat(ushort(offset + 4).toShort())
        out[3] = halfToFloat(ushort(offset + 6).toShort())
        return true
    }

    private fun pixelColor(offset: Int): Color = when (info.colorType) {
        ColorType.RGB_565 -> {
            val packed = ushort(offset)
            Color.fromRGBA(
                ((packed ushr 11) and 0x1F) / 31f,
                ((packed ushr 5) and 0x3F) / 63f,
                (packed and 0x1F) / 31f,
                1f,
            )
        }
        ColorType.ARGB_4444 -> {
            val packed = ushort(offset)
            val alpha = ((packed ushr 12) and 0xF) / 15f
            if (alpha == 0f) Color.TRANSPARENT else Color.fromRGBA(
                (((packed ushr 8) and 0xF) / 15f / alpha).coerceIn(0f, 1f),
                (((packed ushr 4) and 0xF) / 15f / alpha).coerceIn(0f, 1f),
                ((packed and 0xF) / 15f / alpha).coerceIn(0f, 1f),
                alpha,
            )
        }
        ColorType.RGBA_F16,
        ColorType.RGBA_F16_NORM,
            -> {
            val alpha = org.graphiks.math.color.halfToFloat(ushort(offset + 6).toShort())
            if (alpha == 0f) Color.TRANSPARENT else Color.fromRGBA(
                (org.graphiks.math.color.halfToFloat(ushort(offset).toShort()) / alpha).coerceIn(0f, 1f),
                (org.graphiks.math.color.halfToFloat(ushort(offset + 2).toShort()) / alpha).coerceIn(0f, 1f),
                (org.graphiks.math.color.halfToFloat(ushort(offset + 4).toShort()) / alpha).coerceIn(0f, 1f),
                alpha.coerceIn(0f, 1f),
            )
        }
        else -> error("unreachable")
    }

    private fun byte(offset: Int): Int = buffer.get(offset).toInt() and 0xFF

    private fun ushort(offset: Int): Int = byte(offset) or (byte(offset + 1) shl 8)
}

private fun ImageInfo.minimumRowBytes(): Long = width.toLong() * bytesPerPixel().toLong()

private fun ImageInfo.computeByteSize(rowBytes: Int): Long =
    if (isEmpty()) 0L else (height - 1).toLong() * rowBytes.toLong() + minimumRowBytes()
