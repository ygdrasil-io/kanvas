package org.graphiks.kanvas.codec.gif

import org.graphiks.math.SkIRect
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/**
 * Pure Kotlin GIF87a/GIF89a decoder.
 *
 * This first slice decodes indexed GIF frames into sRGB, unpremultiplied
 * RGBA_8888 bitmaps without ImageIO/AWT/JNI. Frames are eagerly parsed and
 * composed so callers can request the first image through the regular
 * [SkCodec.getImage] path, with minimal multi-frame metadata available too.
 */
public class SkGifKotlinCodec private constructor(
    private val frames: List<DecodedFrame>,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    private val repetitionCount: Int,
) : SkCodec() {

    internal class DecodedFrame(
        val pixels: IntArray,
        val durationMs: Int,
        val requiredFrame: Int,
        val alphaType: SkAlphaType,
        val frameRect: SkIRect,
        val nextRequiredFrame: Int,
    )

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = canvasWidth,
            height = canvasHeight,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kGIF

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getFrameCount(): Int = frames.size

    override fun getRepetitionCount(): Int = repetitionCount

    override fun getFrameInfo(): List<FrameInfo> = frames.map { frame ->
        FrameInfo(
            requiredFrame = frame.requiredFrame,
            durationMs = frame.durationMs,
            alphaType = frame.alphaType,
            frameRect = frame.frameRect,
        )
    }

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result =
        getPixels(info, dst, Options())

    override fun getPixels(info: SkImageInfo, dst: SkBitmap, opts: Options): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidConversion
        }
        val frameIndex = opts.frameIndex
        if (frameIndex !in frames.indices) return Result.kInvalidParameters
        System.arraycopy(frames[frameIndex].pixels, 0, dst.pixels8888, 0, frames[frameIndex].pixels.size)
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "gif"

        override fun matches(data: ByteArray): Boolean =
            data.size >= SIGNATURE_SIZE &&
                data[0] == 'G'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == '8'.code.toByte() &&
                (data[4] == '7'.code.toByte() || data[4] == '9'.code.toByte()) &&
                data[5] == 'a'.code.toByte()

        override fun make(data: ByteArray): SkCodec? {
            if (!matches(data)) return null
            return try {
                Parser(data).parse()
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    private class Parser(private val bytes: ByteArray) {
        private var offset: Int = 0
        private var gce: GraphicControl = GraphicControl()
        private var repetitionCount: Int = 0

        fun parse(): SkGifKotlinCodec? {
            skip(SIGNATURE_SIZE)
            val width = readU16LE()
            val height = readU16LE()
            if (width <= 0 || height <= 0) return null
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null

            val packed = readU8()
            val hasGlobalColorTable = (packed and 0x80) != 0
            val globalColorTableSize = 1 shl ((packed and 0x07) + 1)
            val backgroundColorIndex = readU8()
            readU8()

            val globalColorTable = if (hasGlobalColorTable) readColorTable(globalColorTableSize) else null
            val frames = ArrayList<DecodedFrame>()
            val canvas = IntArray(width * height)
            var nextRequiredFrame = SkCodec.kNoFrame

            while (offset < bytes.size) {
                when (readU8()) {
                    BLOCK_TRAILER -> break
                    BLOCK_EXTENSION -> readExtension()
                    BLOCK_IMAGE -> {
                        val frame = readImage(
                            canvasWidth = width,
                            canvasHeight = height,
                            globalColorTable = globalColorTable,
                            backgroundColorIndex = backgroundColorIndex,
                            canvas = canvas,
                            frameIndex = frames.size,
                            requiredFrame = nextRequiredFrame,
                        ) ?: return null
                        frames += frame
                        nextRequiredFrame = frame.nextRequiredFrame
                    }
                    else -> return null
                }
            }

            if (frames.isEmpty()) return null
            return SkGifKotlinCodec(frames, width, height, repetitionCount)
        }

        private fun readExtension() {
            when (readU8()) {
                EXT_GRAPHIC_CONTROL -> {
                    val size = readU8()
                    if (size != 4) {
                        skipSubBlocks()
                        return
                    }
                    val packed = readU8()
                    val delayCs = readU16LE()
                    val transparentIndex = readU8()
                    readU8()
                    gce = GraphicControl(
                        disposal = (packed ushr 2) and 0x07,
                        delayMs = delayCs * 10,
                        transparentIndex = if ((packed and 0x01) != 0) transparentIndex else -1,
                    )
                }
                EXT_APPLICATION -> readApplicationExtension()
                else -> skipSubBlocks()
            }
        }

        private fun readApplicationExtension() {
            val blockSize = readU8()
            if (blockSize != 11) {
                skip(blockSize)
                skipSubBlocks()
                return
            }
            requireRemaining(11)
            val app = String(bytes, offset, 11, Charsets.US_ASCII)
            offset += 11
            val payload = readSubBlocks()
            if (app == "NETSCAPE2.0" && payload.size >= 3 && (payload[0].toInt() and 0xFF) == 1) {
                val loopCount = (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
                repetitionCount = if (loopCount == 0) {
                    SkCodec.kRepetitionCountInfinite
                } else {
                    loopCount
                }
            }
        }

        private fun readImage(
            canvasWidth: Int,
            canvasHeight: Int,
            globalColorTable: IntArray?,
            backgroundColorIndex: Int,
            canvas: IntArray,
            frameIndex: Int,
            requiredFrame: Int,
        ): DecodedFrame? {
            val left = readU16LE()
            val top = readU16LE()
            val width = readU16LE()
            val height = readU16LE()
            if (width <= 0 || height <= 0) return null
            if (left < 0 || top < 0 || left + width > canvasWidth || top + height > canvasHeight) return null

            val packed = readU8()
            val hasLocalColorTable = (packed and 0x80) != 0
            val interlaced = (packed and 0x40) != 0
            val localColorTableSize = 1 shl ((packed and 0x07) + 1)
            val colorTable = if (hasLocalColorTable) readColorTable(localColorTableSize) else globalColorTable
            if (colorTable == null || colorTable.isEmpty()) return null

            val lzwMinimumCodeSize = readU8()
            val imageData = readSubBlocks()
            val indexes = decodeLzw(imageData, lzwMinimumCodeSize, width * height) ?: return null
            val beforeFrame = canvas.copyOf()
            val frameGce = gce

            for (i in indexes.indices) {
                val localY = if (interlaced) deinterlacedY(i / width, height) else i / width
                val localX = i % width
                val index = indexes[i]
                if (index == frameGce.transparentIndex) continue
                if (index !in colorTable.indices) return null
                canvas[(top + localY) * canvasWidth + left + localX] = colorTable[index]
            }

            val pixels = canvas.copyOf()
            val frameRect = SkIRect.MakeXYWH(left, top, width, height)

            val nextRequiredFrame = when (frameGce.disposal) {
                DISPOSAL_RESTORE_BACKGROUND -> {
                    val backgroundColor = backgroundColor(globalColorTable, backgroundColorIndex, frameGce)
                    clearRect(canvas, canvasWidth, left, top, width, height, backgroundColor)
                    frameIndex
                }
                DISPOSAL_RESTORE_PREVIOUS -> {
                    System.arraycopy(beforeFrame, 0, canvas, 0, canvas.size)
                    requiredFrame
                }
                else -> frameIndex
            }
            val frame = DecodedFrame(
                pixels = pixels,
                durationMs = frameGce.delayMs,
                requiredFrame = requiredFrame,
                alphaType = SkAlphaType.kUnpremul,
                frameRect = frameRect,
                nextRequiredFrame = nextRequiredFrame,
            )
            gce = GraphicControl()
            return frame
        }

        private fun backgroundColor(
            globalColorTable: IntArray?,
            backgroundColorIndex: Int,
            gce: GraphicControl,
        ): Int {
            if (backgroundColorIndex == gce.transparentIndex) return TRANSPARENT
            return globalColorTable?.getOrNull(backgroundColorIndex) ?: TRANSPARENT
        }

        private fun readColorTable(entryCount: Int): IntArray {
            if (entryCount <= 0) fail()
            val table = IntArray(entryCount)
            for (i in 0 until entryCount) {
                val r = readU8()
                val g = readU8()
                val b = readU8()
                table[i] = argb(0xFF, r, g, b)
            }
            return table
        }

        private fun readSubBlocks(): ByteArray {
            val out = ArrayList<Byte>()
            while (true) {
                val size = readU8()
                if (size == 0) break
                requireRemaining(size)
                for (i in 0 until size) out += bytes[offset + i]
                offset += size
            }
            return out.toByteArray()
        }

        private fun skipSubBlocks() {
            while (true) {
                val size = readU8()
                if (size == 0) return
                skip(size)
            }
        }

        private fun readU8(): Int {
            requireRemaining(1)
            return bytes[offset++].toInt() and 0xFF
        }

        private fun readU16LE(): Int {
            val lo = readU8()
            val hi = readU8()
            return lo or (hi shl 8)
        }

        private fun skip(count: Int) {
            requireRemaining(count)
            offset += count
        }

        private fun requireRemaining(count: Int) {
            if (count < 0 || offset + count > bytes.size) fail()
        }

        private fun fail(): Nothing = throw IllegalArgumentException("invalid GIF")
    }
}

public class GifKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkGifKotlinCodec.Decoder)
}

private data class GraphicControl(
    val disposal: Int = DISPOSAL_NONE,
    val delayMs: Int = 0,
    val transparentIndex: Int = -1,
)

private const val SIGNATURE_SIZE: Int = 6
private const val MAX_DIMENSION: Int = 100_000
private const val BLOCK_IMAGE: Int = 0x2C
private const val BLOCK_EXTENSION: Int = 0x21
private const val BLOCK_TRAILER: Int = 0x3B
private const val EXT_GRAPHIC_CONTROL: Int = 0xF9
private const val EXT_APPLICATION: Int = 0xFF
private const val DISPOSAL_NONE: Int = 0
private const val DISPOSAL_RESTORE_BACKGROUND: Int = 2
private const val DISPOSAL_RESTORE_PREVIOUS: Int = 3
private const val TRANSPARENT: Int = 0

private fun decodeLzw(data: ByteArray, minimumCodeSize: Int, expectedSize: Int): IntArray? {
    if (minimumCodeSize !in 2..8) return null
    if (expectedSize < 0) return null

    val clearCode = 1 shl minimumCodeSize
    val endCode = clearCode + 1
    var codeSize = minimumCodeSize + 1
    val reader = BitReader(data)
    val dictionary = ArrayList<IntArray>(MAX_LZW_CODES)

    fun resetDictionary() {
        dictionary.clear()
        for (i in 0 until clearCode) dictionary += intArrayOf(i)
        dictionary += intArrayOf()
        dictionary += intArrayOf()
        codeSize = minimumCodeSize + 1
    }

    resetDictionary()
    val out = IntArray(expectedSize)
    var outSize = 0
    var previous: IntArray? = null

    while (true) {
        val code = reader.read(codeSize) ?: return null
        when {
            code == clearCode -> {
                resetDictionary()
                previous = null
            }
            code == endCode -> break
            else -> {
                val entry = when {
                    code < dictionary.size && dictionary[code].isNotEmpty() -> dictionary[code]
                    code == dictionary.size && previous != null -> previous + previous[0]
                    else -> return null
                }
                if (outSize + entry.size > expectedSize) return null
                for (value in entry) out[outSize++] = value

                val prev = previous
                if (prev != null && dictionary.size < MAX_LZW_CODES) {
                    dictionary += prev + entry[0]
                    if (dictionary.size == (1 shl codeSize) && codeSize < MAX_LZW_CODE_SIZE) {
                        codeSize++
                    }
                }
                previous = entry
            }
        }
    }

    return if (outSize == expectedSize) out else null
}

private class BitReader(private val bytes: ByteArray) {
    private var bitOffset: Int = 0

    fun read(size: Int): Int? {
        if (size <= 0 || size > MAX_LZW_CODE_SIZE) return null
        if (bitOffset + size > bytes.size * 8) return null
        var value = 0
        for (i in 0 until size) {
            val byte = bytes[(bitOffset + i) / 8].toInt() and 0xFF
            val bit = (byte ushr ((bitOffset + i) and 7)) and 1
            value = value or (bit shl i)
        }
        bitOffset += size
        return value
    }
}

private fun deinterlacedY(row: Int, height: Int): Int {
    var r = row
    for (y in 0 until height step 8) {
        if (r == 0) return y
        r--
    }
    for (y in 4 until height step 8) {
        if (r == 0) return y
        r--
    }
    for (y in 2 until height step 4) {
        if (r == 0) return y
        r--
    }
    for (y in 1 until height step 2) {
        if (r == 0) return y
        r--
    }
    return row
}

private fun clearRect(
    canvas: IntArray,
    canvasWidth: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    color: Int,
) {
    for (y in top until top + height) {
        java.util.Arrays.fill(canvas, y * canvasWidth + left, y * canvasWidth + left + width, color)
    }
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)

private const val MAX_LZW_CODES: Int = 4096
private const val MAX_LZW_CODE_SIZE: Int = 12
