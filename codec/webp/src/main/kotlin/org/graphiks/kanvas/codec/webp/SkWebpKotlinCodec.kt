package org.graphiks.kanvas.codec.webp

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
import org.skia.foundation.skcms.skcmsParse

/**
 * Pure Kotlin WebP metadata codec.
 *
 * This first slice only sniffs RIFF/WEBP and parses the container
 * metadata needed by [SkCodec.getInfo]. Pixel reconstruction is only
 * implemented for the current VP8L subset; VP8 alpha metadata is parsed,
 * but VP8 lossy pixel reconstruction and animation are intentionally left
 * for later slices.
 */
public class SkWebpKotlinCodec internal constructor(
    internal val metadata: WebpMetadata,
    private val data: ByteArray,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = metadata.width,
            height = metadata.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = if (metadata.hasAlpha) SkAlphaType.kUnpremul else SkAlphaType.kOpaque,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kWEBP

    override fun getICCProfile(): SkcmsICCProfile? = metadata.iccProfile

    override fun getFrameCount(): Int = metadata.animation?.frames?.size ?: 1

    override fun getRepetitionCount(): Int =
        metadata.animation?.loopCount?.let { loopCount ->
            if (loopCount == 0) SkCodec.kRepetitionCountInfinite else loopCount - 1
        } ?: 0

    override fun getFrameInfo(): List<FrameInfo> =
        metadata.animation?.frames?.mapIndexed { index, frame ->
            FrameInfo(
                requiredFrame = if (index == 0) kNoFrame else index - 1,
                durationMs = frame.durationMs,
                alphaType = if (metadata.hasAlpha) SkAlphaType.kUnpremul else SkAlphaType.kOpaque,
                frameRect = SkIRect.MakeXYWH(frame.x, frame.y, frame.width, frame.height),
            )
        } ?: super.getFrameInfo()

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result =
        getPixels(info, dst, Options())

    override fun getPixels(info: SkImageInfo, dst: SkBitmap, opts: Options): Result {
        if (info.width != metadata.width || info.height != metadata.height) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidParameters
        }
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        metadata.animation?.let { animation ->
            return decodeAnimatedFrame(info, dst, animation, opts.frameIndex)
        }
        if (metadata.format == WebpBitstreamFormat.VP8) {
            return when (val decoded = decodeVp8LossyPixels(data, metadata)) {
                Vp8LossyDecodeResult.Invalid -> Result.kErrorInInput
                Vp8LossyDecodeResult.Unsupported -> Result.kUnimplemented
                is Vp8LossyDecodeResult.Pixels -> {
                    decoded.rgba.copyInto(dst.pixels8888)
                    Result.kSuccess
                }
            }
        }
        if (metadata.format != WebpBitstreamFormat.VP8L) {
            return Result.kUnimplemented
        }
        return when (val decoded = decodeSimpleVp8l(data, metadata)) {
            Vp8lDecodeResult.Unsupported -> Result.kUnimplemented
            Vp8lDecodeResult.Invalid -> Result.kErrorInInput
            is Vp8lDecodeResult.Pixels -> {
                decoded.argb.copyInto(dst.pixels8888)
                Result.kSuccess
            }
        }
    }

    private fun decodeAnimatedFrame(
        info: SkImageInfo,
        dst: SkBitmap,
        animation: WebpAnimation,
        frameIndex: Int,
    ): Result {
        if (frameIndex !in animation.frames.indices) return Result.kInvalidParameters
        val canvas = IntArray(info.width * info.height) { animation.backgroundColor }
        var previousFrame: WebpAnimationFrame? = null
        for (index in 0..frameIndex) {
            previousFrame?.let { previous ->
                if (previous.disposeToBackground) {
                    canvas.fillRect(
                        canvasWidth = info.width,
                        x = previous.x,
                        y = previous.y,
                        width = previous.width,
                        height = previous.height,
                        color = animation.backgroundColor,
                    )
                }
            }
            val frame = animation.frames[index]
            val framePixels = when (val result = decodeAnimationFramePixels(frame)) {
                AnimationFrameDecodeResult.Invalid -> return Result.kErrorInInput
                AnimationFrameDecodeResult.Unsupported -> return Result.kUnimplemented
                is AnimationFrameDecodeResult.Pixels -> result.pixels
            }
            canvas.compositeFrame(info.width, frame, framePixels)
            previousFrame = frame
        }
        canvas.copyInto(dst.pixels8888)
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "webp"

        override fun matches(data: ByteArray): Boolean =
            data.size >= RIFF_HEADER_SIZE &&
                data.hasAscii(0, "RIFF") &&
                data.hasAscii(8, "WEBP")

        override fun make(data: ByteArray): SkCodec? {
            if (!matches(data)) return null
            val metadata = parseMetadata(data) ?: return null
            return SkWebpKotlinCodec(metadata, data)
        }
    }
}

public class WebpKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkWebpKotlinCodec.Decoder)
}

private sealed interface AnimationFrameDecodeResult {
    data class Pixels(val pixels: IntArray) : AnimationFrameDecodeResult
    data object Unsupported : AnimationFrameDecodeResult
    data object Invalid : AnimationFrameDecodeResult
}

internal data class WebpMetadata(
    val width: Int,
    val height: Int,
    val format: WebpBitstreamFormat,
    val flags: WebpVp8xFlags = WebpVp8xFlags(),
    val payloadOffset: Int = -1,
    val payloadSize: Int = 0,
    val iccProfile: SkcmsICCProfile? = null,
    val exifData: ByteArray? = null,
    val xmpData: ByteArray? = null,
    val alphaChunk: WebpAlphaChunk? = null,
    val animation: WebpAnimation? = null,
) {
    val hasAlpha: Boolean get() =
        flags.alpha || alphaChunk != null || format == WebpBitstreamFormat.VP8L || animation != null
}

internal data class WebpAnimation(
    val backgroundColor: Int,
    val loopCount: Int,
    val frames: List<WebpAnimationFrame>,
)

internal data class WebpAnimationFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val durationMs: Int,
    val blend: Boolean,
    val disposeToBackground: Boolean,
    val chunks: ByteArray,
    val format: WebpBitstreamFormat,
    val hasAlpha: Boolean,
) {
    fun asSingleFrameWebp(): ByteArray =
        if (format == WebpBitstreamFormat.VP8 && hasAlpha) {
            riffBytes("WEBP", vp8xChunkBytes(width, height, flags = 0x10), chunks)
        } else {
            riffBytes("WEBP", chunks)
        }
}

internal data class WebpAlphaChunk(
    val compression: WebpAlphaCompression,
    val filtering: Int,
    val preprocessing: Int,
    val payloadOffset: Int,
    val payloadSize: Int,
)

internal enum class WebpAlphaCompression {
    NONE,
    LOSSLESS,
}

internal enum class WebpBitstreamFormat {
    VP8X,
    VP8L,
    VP8,
}

internal data class WebpVp8xFlags(
    val icc: Boolean = false,
    val alpha: Boolean = false,
    val exif: Boolean = false,
    val xmp: Boolean = false,
    val animation: Boolean = false,
    val raw: Int = 0,
)

private const val RIFF_HEADER_SIZE: Int = 12
private const val CHUNK_HEADER_SIZE: Int = 8
private const val VP8X_PAYLOAD_SIZE: Int = 10
private const val ANIM_PAYLOAD_SIZE: Int = 6
private const val ANMF_HEADER_SIZE: Int = 16
private const val VP8L_HEADER_SIZE: Int = 5
private const val VP8_KEYFRAME_HEADER_SIZE: Int = 10
private const val MAX_WEBP_DIMENSION: Int = 16_777_216

private fun decodeAnimationFramePixels(frame: WebpAnimationFrame): AnimationFrameDecodeResult {
    val codec = SkWebpKotlinCodec.Decoder.make(frame.asSingleFrameWebp())
        ?: return AnimationFrameDecodeResult.Invalid
    val info = codec.getInfo()
    if (info.width != frame.width || info.height != frame.height || info.colorType != SkColorType.kRGBA_8888) {
        return AnimationFrameDecodeResult.Invalid
    }
    val bitmap = SkBitmap(
        width = frame.width,
        height = frame.height,
        colorType = SkColorType.kRGBA_8888,
        colorSpace = SkColorSpace.makeSRGB(),
    )
    return when (codec.getPixels(info, bitmap)) {
        SkCodec.Result.kSuccess -> AnimationFrameDecodeResult.Pixels(bitmap.pixels8888.copyOf())
        SkCodec.Result.kUnimplemented -> AnimationFrameDecodeResult.Unsupported
        else -> AnimationFrameDecodeResult.Invalid
    }
}

private fun IntArray.compositeFrame(canvasWidth: Int, frame: WebpAnimationFrame, framePixels: IntArray) {
    for (row in 0 until frame.height) {
        val canvasOffset = (frame.y + row) * canvasWidth + frame.x
        val frameOffset = row * frame.width
        for (x in 0 until frame.width) {
            val src = framePixels[frameOffset + x]
            val dstIndex = canvasOffset + x
            this[dstIndex] = if (frame.blend) blendSrcOver(src, this[dstIndex]) else src
        }
    }
}

private fun IntArray.fillRect(canvasWidth: Int, x: Int, y: Int, width: Int, height: Int, color: Int) {
    for (row in 0 until height) {
        fill(color, (y + row) * canvasWidth + x, (y + row) * canvasWidth + x + width)
    }
}

private fun blendSrcOver(src: Int, dst: Int): Int {
    val srcAlpha = alpha(src)
    if (srcAlpha == 255) return src
    if (srcAlpha == 0) return dst
    val dstAlpha = alpha(dst)
    val outAlpha = srcAlpha + ((dstAlpha * (255 - srcAlpha) + 127) / 255)
    if (outAlpha == 0) return 0
    fun blendChannel(srcChannel: Int, dstChannel: Int): Int =
        ((srcChannel * srcAlpha * 255) +
            (dstChannel * dstAlpha * (255 - srcAlpha)) +
            (outAlpha * 127)) / (outAlpha * 255)
    return packArgb(
        outAlpha,
        blendChannel(red(src), red(dst)).coerceIn(0, 255),
        blendChannel(green(src), green(dst)).coerceIn(0, 255),
        blendChannel(blue(src), blue(dst)).coerceIn(0, 255),
    )
}

private fun riffBytes(type: String, vararg chunks: ByteArray): ByteArray {
    var payloadSize = type.length
    for (chunk in chunks) payloadSize += chunk.size
    val out = ByteArray(RIFF_HEADER_SIZE + payloadSize)
    writeAscii(out, 0, "RIFF")
    writeU32LE(out, 4, payloadSize)
    writeAscii(out, 8, type)
    var offset = RIFF_HEADER_SIZE
    for (chunk in chunks) {
        chunk.copyInto(out, offset)
        offset += chunk.size
    }
    return out
}

private fun vp8xChunkBytes(width: Int, height: Int, flags: Int): ByteArray {
    val out = ByteArray(CHUNK_HEADER_SIZE + VP8X_PAYLOAD_SIZE)
    writeAscii(out, 0, "VP8X")
    writeU32LE(out, 4, VP8X_PAYLOAD_SIZE)
    out[8] = flags.toByte()
    write24LE(out, 12, width - 1)
    write24LE(out, 15, height - 1)
    return out
}

private fun parseMetadata(data: ByteArray): WebpMetadata? {
    var extended: WebpMetadata? = null
    var extendedBitstream: WebpMetadata? = null
    var animationBackgroundColor: Int? = null
    var animationLoopCount: Int = 0
    val animationFrames = ArrayList<WebpAnimationFrame>()
    var iccProfile: SkcmsICCProfile? = null
    var exifData: ByteArray? = null
    var xmpData: ByteArray? = null
    var alphaChunk: WebpAlphaChunk? = null
    var offset = RIFF_HEADER_SIZE
    while (offset <= data.size - CHUNK_HEADER_SIZE) {
        val fourcc = readFourcc(data, offset)
        val size = readU32LE(data, offset + 4)
        val payloadOffset = offset + CHUNK_HEADER_SIZE
        val payloadEnd = payloadOffset.toLong() + size
        if (payloadEnd > data.size.toLong()) return null

        when (fourcc) {
            "VP8X" -> {
                if (extended != null) return null
                extended = parseVp8x(data, payloadOffset, size) ?: return null
            }
            "ANIM" -> {
                val base = extended ?: return null
                if (!base.flags.animation) {
                    // The spec says ANIM without the animation flag must be ignored.
                } else {
                    if (animationBackgroundColor != null || size != ANIM_PAYLOAD_SIZE.toLong()) return null
                    animationBackgroundColor = parseAnimationBackgroundColor(data, payloadOffset)
                    animationLoopCount = readU16LE(data, payloadOffset + 4)
                }
            }
            "ANMF" -> {
                val base = extended ?: return null
                if (!base.flags.animation) {
                    // The spec says ANMF without the animation flag should not appear, but still readers ignore unknown chunks.
                } else {
                    if (size > Int.MAX_VALUE) return null
                    val frame = parseAnimationFrame(
                        data = data,
                        offset = payloadOffset,
                        size = size.toInt(),
                        canvasWidth = base.width,
                        canvasHeight = base.height,
                    ) ?: return null
                    animationFrames += frame
                }
            }
            "ICCP" -> {
                if (iccProfile != null || size > Int.MAX_VALUE) return null
                iccProfile = parseIccProfile(data, payloadOffset, size.toInt())
            }
            "EXIF" -> {
                if (exifData != null || size > Int.MAX_VALUE) return null
                exifData = copyChunkPayload(data, payloadOffset, size.toInt()) ?: return null
            }
            "XMP " -> {
                if (xmpData != null || size > Int.MAX_VALUE) return null
                xmpData = copyChunkPayload(data, payloadOffset, size.toInt()) ?: return null
            }
            "ALPH" -> {
                val base = extended ?: return null
                if (base.flags.animation) return null
                if (alphaChunk != null || size > Int.MAX_VALUE || !base.flags.alpha) return null
                alphaChunk = parseAlphaChunk(data, payloadOffset, size.toInt())
                    ?: return null
            }
            "VP8L" -> {
                if (extended?.flags?.animation == true) return null
                val bitstream = parseVp8l(data, payloadOffset, size) ?: return null
                if (extended == null) return bitstream
                if (extendedBitstream != null) return null
                extendedBitstream = bitstream
            }
            "VP8 " -> {
                if (extended?.flags?.animation == true) return null
                val bitstream = parseVp8(data, payloadOffset, size) ?: return null
                if (extended == null) return bitstream
                if (extendedBitstream != null) return null
                extendedBitstream = bitstream
            }
        }

        val paddedSize = size + (size and 1L)
        val next = payloadOffset.toLong() + paddedSize
        if (next > Int.MAX_VALUE) return null
        offset = next.toInt()
    }
    val base = extended ?: return null
    val animation = if (base.flags.animation) {
        val backgroundColor = animationBackgroundColor ?: return null
        if (animationFrames.isEmpty()) return null
        WebpAnimation(backgroundColor, animationLoopCount, animationFrames)
    } else {
        null
    }
    val bitstream = extendedBitstream
    if (animation == null && alphaChunk != null && bitstream?.format != WebpBitstreamFormat.VP8) return null
    return base.copy(
        format = bitstream?.format ?: base.format,
        payloadOffset = bitstream?.payloadOffset ?: base.payloadOffset,
        payloadSize = bitstream?.payloadSize ?: base.payloadSize,
        iccProfile = iccProfile,
        exifData = exifData,
        xmpData = xmpData,
        alphaChunk = alphaChunk,
        animation = animation,
    )
}

private fun parseAnimationBackgroundColor(data: ByteArray, offset: Int): Int {
    val blue = data[offset].toInt() and 0xFF
    val green = data[offset + 1].toInt() and 0xFF
    val red = data[offset + 2].toInt() and 0xFF
    val alpha = data[offset + 3].toInt() and 0xFF
    return packArgb(alpha, red, green, blue)
}

private fun parseAnimationFrame(
    data: ByteArray,
    offset: Int,
    size: Int,
    canvasWidth: Int,
    canvasHeight: Int,
): WebpAnimationFrame? {
    if (size < ANMF_HEADER_SIZE || offset < 0 || offset + size > data.size) return null
    val x = read24LE(data, offset) * 2
    val y = read24LE(data, offset + 3) * 2
    val width = read24LE(data, offset + 6) + 1
    val height = read24LE(data, offset + 9) + 1
    val durationMs = read24LE(data, offset + 12)
    val flags = data[offset + 15].toInt() and 0xFF
    if (!validDimensions(width, height)) return null
    if (x < 0 || y < 0 || x + width > canvasWidth || y + height > canvasHeight) return null

    var alphaChunkSeen = false
    var bitstream: WebpMetadata? = null
    var frameOffset = offset + ANMF_HEADER_SIZE
    val frameEnd = offset + size
    while (frameOffset <= frameEnd - CHUNK_HEADER_SIZE) {
        val fourcc = readFourcc(data, frameOffset)
        val chunkSize = readU32LE(data, frameOffset + 4)
        if (chunkSize > Int.MAX_VALUE) return null
        val payloadOffset = frameOffset + CHUNK_HEADER_SIZE
        val payloadEnd = payloadOffset.toLong() + chunkSize
        if (payloadEnd > frameEnd.toLong()) return null
        when (fourcc) {
            "ALPH" -> {
                if (alphaChunkSeen || parseAlphaChunk(data, payloadOffset, chunkSize.toInt()) == null) return null
                alphaChunkSeen = true
            }
            "VP8L" -> {
                if (bitstream != null) return null
                bitstream = parseVp8l(data, payloadOffset, chunkSize) ?: return null
            }
            "VP8 " -> {
                if (bitstream != null) return null
                bitstream = parseVp8(data, payloadOffset, chunkSize) ?: return null
            }
        }
        val paddedSize = chunkSize + (chunkSize and 1L)
        val next = payloadOffset.toLong() + paddedSize
        if (next > Int.MAX_VALUE) return null
        frameOffset = next.toInt()
    }
    if (frameOffset != frameEnd) return null
    val checkedBitstream = bitstream ?: return null
    if (checkedBitstream.width != width || checkedBitstream.height != height) return null
    if (alphaChunkSeen && checkedBitstream.format != WebpBitstreamFormat.VP8) return null
    return WebpAnimationFrame(
        x = x,
        y = y,
        width = width,
        height = height,
        durationMs = durationMs,
        blend = (flags and 0x02) == 0,
        disposeToBackground = (flags and 0x01) != 0,
        chunks = data.copyOfRange(offset + ANMF_HEADER_SIZE, frameEnd),
        format = checkedBitstream.format,
        hasAlpha = alphaChunkSeen || checkedBitstream.format == WebpBitstreamFormat.VP8L,
    )
}

private fun parseAlphaChunk(
    data: ByteArray,
    offset: Int,
    size: Int,
): WebpAlphaChunk? {
    if (size < 1 || offset < 0 || offset + size > data.size) return null
    val control = data[offset].toInt() and 0xFF
    if ((control and 0xC0) != 0) return null
    val compression = when (control and 0x03) {
        0 -> WebpAlphaCompression.NONE
        1 -> WebpAlphaCompression.LOSSLESS
        else -> return null
    }
    return WebpAlphaChunk(
        compression = compression,
        filtering = (control ushr 2) and 0x03,
        preprocessing = (control ushr 4) and 0x03,
        payloadOffset = offset + 1,
        payloadSize = size - 1,
    )
}

private fun parseIccProfile(data: ByteArray, offset: Int, size: Int): SkcmsICCProfile? {
    if (size <= 0 || offset < 0 || offset + size > data.size) return null
    return try {
        skcmsParse(data.copyOfRange(offset, offset + size))
    } catch (_: Throwable) {
        null
    }
}

private fun copyChunkPayload(data: ByteArray, offset: Int, size: Int): ByteArray? {
    if (size <= 0 || offset < 0 || offset + size > data.size) return null
    return data.copyOfRange(offset, offset + size)
}

private fun parseVp8x(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8X_PAYLOAD_SIZE) return null
    val rawFlags = data[offset].toInt() and 0xFF
    val width = read24LE(data, offset + 4) + 1
    val height = read24LE(data, offset + 7) + 1
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8X,
        flags = WebpVp8xFlags(
            icc = (rawFlags and 0x20) != 0,
            alpha = (rawFlags and 0x10) != 0,
            exif = (rawFlags and 0x08) != 0,
            xmp = (rawFlags and 0x04) != 0,
            animation = (rawFlags and 0x02) != 0,
            raw = rawFlags,
        ),
    )
}

private fun parseVp8l(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8L_HEADER_SIZE) return null
    if ((data[offset].toInt() and 0xFF) != 0x2F) return null
    val b1 = data[offset + 1].toInt() and 0xFF
    val b2 = data[offset + 2].toInt() and 0xFF
    val b3 = data[offset + 3].toInt() and 0xFF
    val b4 = data[offset + 4].toInt() and 0xFF
    val width = 1 + (((b2 and 0x3F) shl 8) or b1)
    val height = 1 + (((b4 and 0x0F) shl 10) or (b3 shl 2) or ((b2 and 0xC0) ushr 6))
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8L,
        payloadOffset = offset,
        payloadSize = size.toInt(),
    )
}

private fun parseVp8(data: ByteArray, offset: Int, size: Long): WebpMetadata? {
    if (size < VP8_KEYFRAME_HEADER_SIZE) return null
    val frameTag = parseVp8FrameTag(data, offset, size) ?: return null
    if (!frameTag.keyFrame) return null
    if ((data[offset + 3].toInt() and 0xFF) != 0x9D ||
        (data[offset + 4].toInt() and 0xFF) != 0x01 ||
        (data[offset + 5].toInt() and 0xFF) != 0x2A
    ) {
        return null
    }
    val width = readU16LE(data, offset + 6) and 0x3FFF
    val height = readU16LE(data, offset + 8) and 0x3FFF
    if (!validDimensions(width, height)) return null
    return WebpMetadata(
        width = width,
        height = height,
        format = WebpBitstreamFormat.VP8,
        payloadOffset = offset,
        payloadSize = size.toInt(),
    )
}

internal data class Vp8FrameTag(
    val keyFrame: Boolean,
    val version: Int,
    val showFrame: Boolean,
    val firstPartitionSize: Int,
)

internal fun parseVp8FrameTag(data: ByteArray, offset: Int, size: Long): Vp8FrameTag? {
    if (size < 3 || size > Int.MAX_VALUE || offset < 0 || offset + size > data.size) return null
    val tag = read24LE(data, offset)
    val firstPartitionSize = tag ushr 5
    if (firstPartitionSize > size.toInt() - VP8_KEYFRAME_HEADER_SIZE) return null
    return Vp8FrameTag(
        keyFrame = (tag and 0x01) == 0,
        version = (tag ushr 1) and 0x07,
        showFrame = ((tag ushr 4) and 0x01) != 0,
        firstPartitionSize = firstPartitionSize,
    )
}

private fun validDimensions(width: Int, height: Int): Boolean =
    width in 1..MAX_WEBP_DIMENSION && height in 1..MAX_WEBP_DIMENSION

private fun ByteArray.hasAscii(offset: Int, text: String): Boolean {
    if (offset < 0 || offset + text.length > size) return false
    for (i in text.indices) {
        if (this[offset + i] != text[i].code.toByte()) return false
    }
    return true
}

private fun readFourcc(bytes: ByteArray, offset: Int): String =
    CharArray(4) { (bytes[offset + it].toInt() and 0xFF).toChar() }.concatToString()

private fun read24LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16)

private fun readU16LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8)

private fun readU32LE(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xFFL) or
        ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

private fun writeAscii(out: ByteArray, offset: Int, text: String) {
    for (i in text.indices) out[offset + i] = text[i].code.toByte()
}

private fun write24LE(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xFF).toByte()
    out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
}

private fun writeU32LE(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xFF).toByte()
    out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}

private sealed interface Vp8lDecodeResult {
    data class Pixels(val argb: IntArray) : Vp8lDecodeResult
    data object Unsupported : Vp8lDecodeResult
    data object Invalid : Vp8lDecodeResult
}

private fun decodeSimpleVp8l(data: ByteArray, metadata: WebpMetadata): Vp8lDecodeResult {
    val payloadOffset = metadata.payloadOffset
    val payloadSize = metadata.payloadSize
    if (payloadOffset < 0 || payloadSize < VP8L_HEADER_SIZE) return Vp8lDecodeResult.Invalid
    if (payloadOffset + payloadSize > data.size) return Vp8lDecodeResult.Invalid
    if ((data[payloadOffset].toInt() and 0xFF) != 0x2F) return Vp8lDecodeResult.Invalid

    val bits = Vp8lBitReader(data, payloadOffset + 1, payloadOffset + payloadSize)
    val width = bits.readBits(14) ?: return Vp8lDecodeResult.Invalid
    val height = bits.readBits(14) ?: return Vp8lDecodeResult.Invalid
    bits.readBits(1) ?: return Vp8lDecodeResult.Invalid // alpha_is_used is only a hint.
    val version = bits.readBits(3) ?: return Vp8lDecodeResult.Invalid
    if (version != 0) return Vp8lDecodeResult.Invalid
    if (width + 1 != metadata.width || height + 1 != metadata.height) return Vp8lDecodeResult.Invalid

    return decodeVp8lImage(bits, metadata.width, metadata.height, allowTransforms = true)
}

private fun decodeVp8lImage(
    bits: Vp8lBitReader,
    width: Int,
    height: Int,
    allowTransforms: Boolean,
): Vp8lDecodeResult {
    val transforms = ArrayList<Vp8lTransform>()
    var imageWidth = width
    if (allowTransforms) {
        while ((bits.readBits(1) ?: return Vp8lDecodeResult.Invalid) != 0) {
            val transform = when (bits.readBits(2) ?: return Vp8lDecodeResult.Invalid) {
                VP8L_TRANSFORM_PREDICTOR -> {
                    val sizeBits = (bits.readBits(3) ?: return Vp8lDecodeResult.Invalid) + 2
                    val blockSize = 1 shl sizeBits
                    val transformWidth = ceilDiv(imageWidth, blockSize)
                    val transformHeight = ceilDiv(height, blockSize)
                    val predictors = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = transformWidth,
                        height = transformHeight,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb
                    }
                    Vp8lTransform.Predictor(sizeBits, transformWidth, predictors)
                }
                VP8L_TRANSFORM_COLOR -> {
                    val sizeBits = (bits.readBits(3) ?: return Vp8lDecodeResult.Invalid) + 2
                    val blockSize = 1 shl sizeBits
                    val transformWidth = ceilDiv(imageWidth, blockSize)
                    val transformHeight = ceilDiv(height, blockSize)
                    val multipliers = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = transformWidth,
                        height = transformHeight,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb
                    }
                    Vp8lTransform.Color(sizeBits, transformWidth, multipliers)
                }
                VP8L_TRANSFORM_SUBTRACT_GREEN -> Vp8lTransform.SubtractGreen
                VP8L_TRANSFORM_COLOR_INDEXING -> {
                    val tableSize = (bits.readBits(8) ?: return Vp8lDecodeResult.Invalid) + 1
                    val table = when (val result = decodeVp8lImage(
                        bits = bits,
                        width = tableSize,
                        height = 1,
                        allowTransforms = false,
                    )) {
                        Vp8lDecodeResult.Invalid -> return Vp8lDecodeResult.Invalid
                        Vp8lDecodeResult.Unsupported -> return Vp8lDecodeResult.Unsupported
                        is Vp8lDecodeResult.Pixels -> result.argb.cumulativeColorTable()
                    }
                    val widthBits = colorIndexingWidthBits(tableSize)
                    val outputWidth = imageWidth
                    imageWidth = ceilDiv(imageWidth, 1 shl widthBits)
                    Vp8lTransform.ColorIndexing(table, widthBits, outputWidth)
                }
                else -> return Vp8lDecodeResult.Invalid
            }
            transforms += transform
        }
    }

    val colorCache = when (bits.readBits(1) ?: return Vp8lDecodeResult.Invalid) {
        0 -> null
        else -> {
            val colorCacheBits = bits.readBits(4) ?: return Vp8lDecodeResult.Invalid
            if (colorCacheBits !in 1..11) return Vp8lDecodeResult.Invalid
            Vp8lColorCache(colorCacheBits)
        }
    }

    val metaPrefixPresent = bits.readBits(1) ?: return Vp8lDecodeResult.Invalid
    if (metaPrefixPresent != 0) return Vp8lDecodeResult.Unsupported

    val group = when (val groupResult = SimpleVp8lPrefixGroup.read(bits, colorCacheSize = colorCache?.size ?: 0)) {
        Vp8lPrefixGroupReadResult.Invalid -> return Vp8lDecodeResult.Invalid
        Vp8lPrefixGroupReadResult.Unsupported -> return Vp8lDecodeResult.Unsupported
        is Vp8lPrefixGroupReadResult.Group -> groupResult.group
    }

    var pixels = IntArray(imageWidth * height)
    var i = 0
    while (i < pixels.size) {
        val green = group.green.decode(bits) ?: return Vp8lDecodeResult.Invalid
        if (green < 256) {
            val red = group.red.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val blue = group.blue.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val alpha = group.alpha.decode(bits) ?: return Vp8lDecodeResult.Invalid
            val pixel = packArgb(alpha, red, green, blue)
            pixels[i++] = pixel
            colorCache?.insert(pixel)
            continue
        }
        if (green >= 256 + VP8L_LENGTH_PREFIX_CODE_COUNT) {
            val cache = colorCache ?: return Vp8lDecodeResult.Unsupported
            val cacheIndex = green - 256 - VP8L_LENGTH_PREFIX_CODE_COUNT
            val pixel = cache[cacheIndex] ?: return Vp8lDecodeResult.Invalid
            pixels[i++] = pixel
            cache.insert(pixel)
            continue
        }
        val length = readVp8lPrefixValue(bits, green - 256) ?: return Vp8lDecodeResult.Invalid
        val distancePrefix = group.distance.decode(bits) ?: return Vp8lDecodeResult.Invalid
        val distanceCode = readVp8lPrefixValue(bits, distancePrefix) ?: return Vp8lDecodeResult.Invalid
        val distance = pixelDistanceFromCode(distanceCode, imageWidth)
        if (distance < 1 || distance > i || i + length > pixels.size) return Vp8lDecodeResult.Invalid
        repeat(length) {
            val pixel = pixels[i - distance]
            pixels[i] = pixel
            colorCache?.insert(pixel)
            i++
        }
    }
    var currentWidth = imageWidth
    transforms.asReversed().forEach { transform ->
        when (transform) {
            is Vp8lTransform.Predictor -> {
                if (!pixels.applyPredictorTransform(currentWidth, transform)) return Vp8lDecodeResult.Invalid
            }
            is Vp8lTransform.Color -> {
                if (!pixels.applyColorTransform(currentWidth, transform)) return Vp8lDecodeResult.Invalid
            }
            is Vp8lTransform.ColorIndexing -> {
                val expanded = pixels.applyColorIndexingTransform(currentWidth, height, transform)
                    ?: return Vp8lDecodeResult.Invalid
                pixels = expanded
                currentWidth = transform.outputWidth
            }
            Vp8lTransform.SubtractGreen -> pixels.applySubtractGreenTransform()
        }
    }
    return Vp8lDecodeResult.Pixels(pixels)
}

private const val VP8L_TRANSFORM_PREDICTOR: Int = 0
private const val VP8L_TRANSFORM_COLOR: Int = 1
private const val VP8L_TRANSFORM_SUBTRACT_GREEN: Int = 2
private const val VP8L_TRANSFORM_COLOR_INDEXING: Int = 3
private const val VP8L_LENGTH_PREFIX_CODE_COUNT: Int = 24

private sealed interface Vp8lTransform {
    data class Predictor(
        val sizeBits: Int,
        val width: Int,
        val predictors: IntArray,
    ) : Vp8lTransform
    data class Color(
        val sizeBits: Int,
        val width: Int,
        val multipliers: IntArray,
    ) : Vp8lTransform
    data class ColorIndexing(
        val table: IntArray,
        val widthBits: Int,
        val outputWidth: Int,
    ) : Vp8lTransform
    data object SubtractGreen : Vp8lTransform
}

private class Vp8lColorCache(
    private val bits: Int,
) {
    private val colors = IntArray(1 shl bits)

    val size: Int get() = colors.size

    operator fun get(index: Int): Int? =
        if (index in colors.indices) colors[index] else null

    fun insert(color: Int) {
        colors[index(color)] = color
    }

    private fun index(color: Int): Int =
        (color * VP8L_COLOR_CACHE_HASH_MULTIPLIER) ushr (32 - bits)
}

private const val VP8L_COLOR_CACHE_HASH_MULTIPLIER: Int = 0x1e35a7bd

private fun IntArray.applySubtractGreenTransform() {
    for (i in indices) {
        val pixel = this[i]
        val green = (pixel ushr 8) and 0xFF
        val red = (((pixel ushr 16) and 0xFF) + green) and 0xFF
        val blue = ((pixel and 0xFF) + green) and 0xFF
        this[i] = packArgb((pixel ushr 24) and 0xFF, red, green, blue)
    }
}

private fun IntArray.applyPredictorTransform(width: Int, transform: Vp8lTransform.Predictor): Boolean {
    for (i in indices) {
        val x = i % width
        val y = i / width
        val predictor = predictorPixel(x, y, width, transform) ?: return false
        this[i] = addPixels(this[i], predictor)
    }
    return true
}

private fun IntArray.applyColorTransform(width: Int, transform: Vp8lTransform.Color): Boolean {
    for (i in indices) {
        val x = i % width
        val y = i / width
        val multiplierIndex = (y ushr transform.sizeBits) * transform.width + (x ushr transform.sizeBits)
        if (multiplierIndex !in transform.multipliers.indices) return false
        this[i] = inverseColorTransform(this[i], transform.multipliers[multiplierIndex])
    }
    return true
}

private fun IntArray.applyColorIndexingTransform(
    width: Int,
    height: Int,
    transform: Vp8lTransform.ColorIndexing,
): IntArray? {
    if (height < 0 || transform.outputWidth < 0) return null
    if (size != width * height) return null
    val packedPixelsPerPixel = 1 shl transform.widthBits
    if (width != ceilDiv(transform.outputWidth, packedPixelsPerPixel)) return null
    val indexBits = 8 ushr transform.widthBits
    val indexMask = (1 shl indexBits) - 1
    val output = IntArray(transform.outputWidth * height)
    for (y in 0 until height) {
        for (x in 0 until transform.outputWidth) {
            val packed = this[y * width + x / packedPixelsPerPixel]
            val shift = (x and (packedPixelsPerPixel - 1)) * indexBits
            val index = (green(packed) ushr shift) and indexMask
            output[y * transform.outputWidth + x] =
                if (index < transform.table.size) transform.table[index] else 0
        }
    }
    return output
}

private fun IntArray.cumulativeColorTable(): IntArray {
    val table = IntArray(size)
    var previous = 0
    for (i in indices) {
        table[i] = addPixels(this[i], previous)
        previous = table[i]
    }
    return table
}

private fun colorIndexingWidthBits(tableSize: Int): Int =
    when (tableSize) {
        in 1..2 -> 3
        in 3..4 -> 2
        in 5..16 -> 1
        else -> 0
    }

private fun inverseColorTransform(pixel: Int, multiplier: Int): Int {
    val alpha = alpha(pixel)
    val green = green(pixel)
    val greenToRed = blue(multiplier)
    val greenToBlue = green(multiplier)
    val redToBlue = red(multiplier)
    val transformedRed = (red(pixel) + colorTransformDelta(greenToRed, green)) and 0xFF
    val transformedBlue = (
        blue(pixel) +
            colorTransformDelta(greenToBlue, green) +
            colorTransformDelta(redToBlue, transformedRed)
        ) and 0xFF
    return packArgb(alpha, transformedRed, green, transformedBlue)
}

private fun colorTransformDelta(multiplier: Int, color: Int): Int =
    (signedByte(multiplier) * signedByte(color)) shr 5

private fun signedByte(value: Int): Int =
    if ((value and 0x80) == 0) value and 0xFF else (value and 0xFF) - 256

private fun IntArray.predictorPixel(
    x: Int,
    y: Int,
    width: Int,
    transform: Vp8lTransform.Predictor,
): Int? {
    if (x == 0 && y == 0) return VP8L_BLACK_PREDICTOR
    if (y == 0) return this[y * width + x - 1]
    if (x == 0) return this[(y - 1) * width + x]

    val modeIndex = (y ushr transform.sizeBits) * transform.width + (x ushr transform.sizeBits)
    if (modeIndex !in transform.predictors.indices) return null
    val mode = (transform.predictors[modeIndex] ushr 8) and 0xFF
    if (mode !in 0..13) return null

    val left = this[y * width + x - 1]
    val top = this[(y - 1) * width + x]
    val topLeft = this[(y - 1) * width + x - 1]
    val topRight = if (x == width - 1) this[y * width] else this[(y - 1) * width + x + 1]
    return when (mode) {
        0 -> VP8L_BLACK_PREDICTOR
        1 -> left
        2 -> top
        3 -> topRight
        4 -> topLeft
        5 -> averagePixels(averagePixels(left, topRight), top)
        6 -> averagePixels(left, topLeft)
        7 -> averagePixels(left, top)
        8 -> averagePixels(topLeft, top)
        9 -> averagePixels(top, topRight)
        10 -> averagePixels(averagePixels(left, topLeft), averagePixels(top, topRight))
        11 -> selectPredictor(left, top, topLeft)
        12 -> clampAddSubtractFull(left, top, topLeft)
        else -> clampAddSubtractHalf(averagePixels(left, top), topLeft)
    }
}

private const val VP8L_BLACK_PREDICTOR: Int = -0x1000000

private fun addPixels(residual: Int, predictor: Int): Int =
    packArgb(
        alpha = (alpha(residual) + alpha(predictor)) and 0xFF,
        red = (red(residual) + red(predictor)) and 0xFF,
        green = (green(residual) + green(predictor)) and 0xFF,
        blue = (blue(residual) + blue(predictor)) and 0xFF,
    )

private fun averagePixels(a: Int, b: Int): Int =
    packArgb(
        alpha = (alpha(a) + alpha(b)) ushr 1,
        red = (red(a) + red(b)) ushr 1,
        green = (green(a) + green(b)) ushr 1,
        blue = (blue(a) + blue(b)) ushr 1,
    )

private fun selectPredictor(left: Int, top: Int, topLeft: Int): Int {
    val pa = alpha(left) + alpha(top) - alpha(topLeft)
    val pr = red(left) + red(top) - red(topLeft)
    val pg = green(left) + green(top) - green(topLeft)
    val pb = blue(left) + blue(top) - blue(topLeft)
    val leftDistance = kotlin.math.abs(pa - alpha(left)) +
        kotlin.math.abs(pr - red(left)) +
        kotlin.math.abs(pg - green(left)) +
        kotlin.math.abs(pb - blue(left))
    val topDistance = kotlin.math.abs(pa - alpha(top)) +
        kotlin.math.abs(pr - red(top)) +
        kotlin.math.abs(pg - green(top)) +
        kotlin.math.abs(pb - blue(top))
    return if (leftDistance < topDistance) left else top
}

private fun clampAddSubtractFull(left: Int, top: Int, topLeft: Int): Int =
    packArgb(
        alpha = clampByte(alpha(left) + alpha(top) - alpha(topLeft)),
        red = clampByte(red(left) + red(top) - red(topLeft)),
        green = clampByte(green(left) + green(top) - green(topLeft)),
        blue = clampByte(blue(left) + blue(top) - blue(topLeft)),
    )

private fun clampAddSubtractHalf(average: Int, topLeft: Int): Int =
    packArgb(
        alpha = clampByte(alpha(average) + (alpha(average) - alpha(topLeft)) / 2),
        red = clampByte(red(average) + (red(average) - red(topLeft)) / 2),
        green = clampByte(green(average) + (green(average) - green(topLeft)) / 2),
        blue = clampByte(blue(average) + (blue(average) - blue(topLeft)) / 2),
    )

private fun clampByte(value: Int): Int = value.coerceIn(0, 255)

private fun alpha(pixel: Int): Int = (pixel ushr 24) and 0xFF
private fun red(pixel: Int): Int = (pixel ushr 16) and 0xFF
private fun green(pixel: Int): Int = (pixel ushr 8) and 0xFF
private fun blue(pixel: Int): Int = pixel and 0xFF

private fun ceilDiv(value: Int, divisor: Int): Int =
    (value + divisor - 1) / divisor

private fun readVp8lPrefixValue(bits: Vp8lBitReader, prefixCode: Int): Int? {
    if (prefixCode !in 0..39) return null
    if (prefixCode < 4) return prefixCode + 1
    val extraBits = (prefixCode - 2) ushr 1
    val offset = (2 + (prefixCode and 1)) shl extraBits
    return offset + (bits.readBits(extraBits) ?: return null) + 1
}

private fun pixelDistanceFromCode(distanceCode: Int, width: Int): Int {
    if (distanceCode > VP8L_DISTANCE_MAP.size) return distanceCode - VP8L_DISTANCE_MAP.size
    val (x, y) = VP8L_DISTANCE_MAP[distanceCode - 1]
    return maxOf(1, x + y * width)
}

private fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private sealed interface Vp8lPrefixGroupReadResult {
    data class Group(val group: SimpleVp8lPrefixGroup) : Vp8lPrefixGroupReadResult
    data object Unsupported : Vp8lPrefixGroupReadResult
    data object Invalid : Vp8lPrefixGroupReadResult
}

private data class SimpleVp8lPrefixGroup(
    val green: Vp8lHuffmanCode,
    val red: Vp8lHuffmanCode,
    val blue: Vp8lHuffmanCode,
    val alpha: Vp8lHuffmanCode,
    val distance: Vp8lHuffmanCode,
) {
    companion object {
        fun read(bits: Vp8lBitReader, colorCacheSize: Int): Vp8lPrefixGroupReadResult {
            val greenAlphabetSize = 256 + VP8L_LENGTH_PREFIX_CODE_COUNT + colorCacheSize
            val green = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = greenAlphabetSize)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val red = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val blue = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val alpha = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 256)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val distance = when (val result = Vp8lHuffmanCode.read(bits, alphabetSize = 40)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lPrefixGroupReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lPrefixGroupReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            return Vp8lPrefixGroupReadResult.Group(SimpleVp8lPrefixGroup(green, red, blue, alpha, distance))
        }
    }
}

private sealed interface Vp8lHuffmanReadResult {
    data class Code(val code: Vp8lHuffmanCode) : Vp8lHuffmanReadResult
    data object Unsupported : Vp8lHuffmanReadResult
    data object Invalid : Vp8lHuffmanReadResult
}

private class Vp8lHuffmanCode(
    private val symbols: IntArray,
    private val entries: List<Vp8lHuffmanEntry>,
    private val maxCodeLength: Int,
) {
    val maxSymbol: Int = symbols.max()

    fun decode(bits: Vp8lBitReader): Int? {
        if (symbols.size == 1) return symbols[0]
        var code = 0
        for (length in 1..maxCodeLength) {
            val bit = bits.readBits(1) ?: return null
            code = code or (bit shl (length - 1))
            val entry = entries.firstOrNull { it.length == length && it.reversedCode == code }
            if (entry != null) return entry.symbol
        }
        return null
    }

    companion object {
        fun read(bits: Vp8lBitReader, alphabetSize: Int): Vp8lHuffmanReadResult {
            val isSimple = bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid
            if (isSimple == 0) return readNormal(bits, alphabetSize)
            val symbolCount = (bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid) + 1
            val isFirst8Bits = bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid
            val first = bits.readBits(1 + 7 * isFirst8Bits) ?: return Vp8lHuffmanReadResult.Invalid
            val symbols = if (symbolCount == 1) {
                intArrayOf(first)
            } else {
                val second = bits.readBits(8) ?: return Vp8lHuffmanReadResult.Invalid
                intArrayOf(first, second).also { it.sort() }
            }
            if (symbols.any { it !in 0 until alphabetSize }) return Vp8lHuffmanReadResult.Invalid
            return fromCodeLengths(IntArray(alphabetSize) { symbol ->
                if (symbol in symbols) 1 else 0
            })
        }

        private fun readNormal(bits: Vp8lBitReader, alphabetSize: Int): Vp8lHuffmanReadResult {
            val codeLengthCodeLengths = IntArray(CODE_LENGTH_CODE_COUNT)
            val codeLengthCount = 4 + (bits.readBits(4) ?: return Vp8lHuffmanReadResult.Invalid)
            if (codeLengthCount > CODE_LENGTH_CODE_ORDER.size) return Vp8lHuffmanReadResult.Invalid
            for (i in 0 until codeLengthCount) {
                codeLengthCodeLengths[CODE_LENGTH_CODE_ORDER[i]] =
                    bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid
            }
            val codeLengthCode = when (val result = fromCodeLengths(codeLengthCodeLengths)) {
                Vp8lHuffmanReadResult.Invalid -> return Vp8lHuffmanReadResult.Invalid
                Vp8lHuffmanReadResult.Unsupported -> return Vp8lHuffmanReadResult.Unsupported
                is Vp8lHuffmanReadResult.Code -> result.code
            }
            val maxSymbol = if ((bits.readBits(1) ?: return Vp8lHuffmanReadResult.Invalid) == 0) {
                alphabetSize
            } else {
                val lengthBits = 2 + 2 * (bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid)
                2 + (bits.readBits(lengthBits) ?: return Vp8lHuffmanReadResult.Invalid)
            }
            if (maxSymbol > alphabetSize) return Vp8lHuffmanReadResult.Invalid

            val codeLengths = IntArray(alphabetSize)
            var symbol = 0
            var previousLength = 8
            while (symbol < maxSymbol) {
                when (val codeLength = codeLengthCode.decode(bits) ?: return Vp8lHuffmanReadResult.Invalid) {
                    in 0..15 -> {
                        codeLengths[symbol++] = codeLength
                        if (codeLength != 0) previousLength = codeLength
                    }
                    16 -> {
                        val repeat = 3 + (bits.readBits(2) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        repeat(repeat) { codeLengths[symbol++] = previousLength }
                    }
                    17 -> {
                        val repeat = 3 + (bits.readBits(3) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        symbol += repeat
                    }
                    18 -> {
                        val repeat = 11 + (bits.readBits(7) ?: return Vp8lHuffmanReadResult.Invalid)
                        if (symbol + repeat > maxSymbol) return Vp8lHuffmanReadResult.Invalid
                        symbol += repeat
                    }
                    else -> return Vp8lHuffmanReadResult.Invalid
                }
            }
            return fromCodeLengths(codeLengths)
        }

        private fun fromCodeLengths(codeLengths: IntArray): Vp8lHuffmanReadResult {
            val symbols = codeLengths.indices.filter { codeLengths[it] > 0 }.toIntArray()
            if (symbols.isEmpty()) return Vp8lHuffmanReadResult.Invalid
            if (codeLengths.any { it !in 0..MAX_HUFFMAN_CODE_LENGTH }) return Vp8lHuffmanReadResult.Invalid

            val counts = IntArray(MAX_HUFFMAN_CODE_LENGTH + 1)
            for (length in codeLengths) {
                if (length > 0) counts[length]++
            }
            var remaining = 1
            for (length in 1..MAX_HUFFMAN_CODE_LENGTH) {
                remaining = (remaining shl 1) - counts[length]
                if (remaining < 0) return Vp8lHuffmanReadResult.Invalid
            }
            if (symbols.size > 1 && remaining != 0) return Vp8lHuffmanReadResult.Invalid

            val nextCode = IntArray(MAX_HUFFMAN_CODE_LENGTH + 1)
            var code = 0
            for (bits in 1..MAX_HUFFMAN_CODE_LENGTH) {
                code = (code + counts[bits - 1]) shl 1
                nextCode[bits] = code
            }
            val entries = ArrayList<Vp8lHuffmanEntry>()
            for (symbol in codeLengths.indices) {
                val length = codeLengths[symbol]
                if (length == 0) continue
                entries += Vp8lHuffmanEntry(symbol, length, reverseBits(nextCode[length], length))
                nextCode[length]++
            }
            return Vp8lHuffmanReadResult.Code(
                Vp8lHuffmanCode(
                    symbols = symbols,
                    entries = entries,
                    maxCodeLength = entries.maxOf { it.length },
                ),
            )
        }
    }

}

private data class Vp8lHuffmanEntry(
    val symbol: Int,
    val length: Int,
    val reversedCode: Int,
)

private const val CODE_LENGTH_CODE_COUNT: Int = 19
private const val MAX_HUFFMAN_CODE_LENGTH: Int = 15
private val CODE_LENGTH_CODE_ORDER = intArrayOf(
    17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
)

private val VP8L_DISTANCE_MAP = arrayOf(
    0 to 1, 1 to 0, 1 to 1, -1 to 1, 0 to 2, 2 to 0, 1 to 2, -1 to 2,
    2 to 1, -2 to 1, 2 to 2, -2 to 2, 0 to 3, 3 to 0, 1 to 3, -1 to 3,
    3 to 1, -3 to 1, 2 to 3, -2 to 3, 3 to 2, -3 to 2, 0 to 4, 4 to 0,
    1 to 4, -1 to 4, 4 to 1, -4 to 1, 3 to 3, -3 to 3, 2 to 4, -2 to 4,
    4 to 2, -4 to 2, 0 to 5, 5 to 0, 1 to 5, -1 to 5, 5 to 1, -5 to 1,
    4 to 3, -4 to 3, 3 to 4, -3 to 4, 2 to 5, -2 to 5, 5 to 2, -5 to 2,
    4 to 4, -4 to 4, 3 to 5, -3 to 5, 5 to 3, -5 to 3, 0 to 6, 6 to 0,
    1 to 6, -1 to 6, 6 to 1, -6 to 1, 2 to 6, -2 to 6, 6 to 2, -6 to 2,
    4 to 5, -4 to 5, 5 to 4, -5 to 4, 3 to 6, -3 to 6, 6 to 3, -6 to 3,
    0 to 7, 7 to 0, 1 to 7, -1 to 7, 5 to 5, -5 to 5, 7 to 1, -7 to 1,
    4 to 6, -4 to 6, 6 to 4, -6 to 4, 2 to 7, -2 to 7, 7 to 2, -7 to 2,
    3 to 7, -3 to 7, 7 to 3, -7 to 3, 5 to 6, -5 to 6, 6 to 5, -6 to 5,
    8 to 0, 4 to 7, -4 to 7, 7 to 4, -7 to 4, 8 to 1, 8 to 2, 6 to 6,
    -6 to 6, 8 to 3, 5 to 7, -5 to 7, 7 to 5, -7 to 5, 8 to 4, 6 to 7,
    -6 to 7, 7 to 6, -7 to 6, 8 to 5, 7 to 7, -7 to 7, 8 to 6, 8 to 7,
)

private fun reverseBits(value: Int, length: Int): Int {
    var result = 0
    for (i in 0 until length) {
        result = (result shl 1) or ((value ushr i) and 1)
    }
    return result
}

private class Vp8lBitReader(
    private val data: ByteArray,
    private val start: Int,
    private val end: Int,
) {
    private var bitOffset: Int = 0

    fun readBits(count: Int): Int? {
        var value = 0
        for (i in 0 until count) {
            val byteOffset = start + ((bitOffset + i) ushr 3)
            if (byteOffset >= end) return null
            val bit = (data[byteOffset].toInt() ushr ((bitOffset + i) and 7)) and 1
            value = value or (bit shl i)
        }
        bitOffset += count
        return value
    }
}

internal class Vp8BoolReader(
    private val data: ByteArray,
    private val start: Int = 0,
    private val end: Int = data.size,
) {
    private val validRange: Boolean = start >= 0 && end >= start && end <= data.size
    private var offset: Int = start
    private var range: Int = 255
    private var value: Int = 0
    private var bitCount: Int = 8

    init {
        if (!validRange) {
            offset = end
        } else {
            value = readByte() shl 8
            value = value or readByte()
        }
    }

    fun readBit(probability: Int): Int? {
        if (!validRange || probability !in 1..255) return null
        val split = 1 + (((range - 1) * probability) ushr 8)
        val bigSplit = split shl 8
        var bit = 0
        if (value >= bigSplit) {
            range -= split
            value -= bigSplit
            bit = 1
        } else {
            range = split
        }
        while (range < 128) {
            range = range shl 1
            value = (value shl 1) and 0xFFFF
            bitCount--
            if (bitCount == 0) {
                if (offset >= end) return null
                value = value or readByte()
                bitCount = 8
            }
        }
        return bit
    }

    fun readLiteral(bitCount: Int): Int? {
        if (bitCount !in 0..31) return null
        var value = 0
        repeat(bitCount) {
            value = (value shl 1) or (readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null)
        }
        return value
    }

    private fun readByte(): Int =
        if (offset < end) data[offset++].toInt() and 0xFF else 0
}

private const val VP8_BOOL_HALF_PROBABILITY: Int = 128

internal data class Vp8LossyFrameHeader(
    val colorSpace: Int,
    val clampType: Int,
    val macroblockWidth: Int,
    val macroblockHeight: Int,
    val coefficientPartitionCount: Int = 1,
    val loopFilter: Vp8LoopFilterHeader,
    val quantization: Vp8QuantizationHeader,
)

internal data class Vp8LoopFilterHeader(
    val simpleFilter: Boolean,
    val level: Int,
    val sharpness: Int,
)

internal data class Vp8QuantizationHeader(
    val yAcIndex: Int,
    val yDcDelta: Int,
    val y2DcDelta: Int,
    val y2AcDelta: Int,
    val uvDcDelta: Int,
    val uvAcDelta: Int,
)

internal sealed interface Vp8LossyHeaderDecodeResult {
    data class Header(val header: Vp8LossyFrameHeader) : Vp8LossyHeaderDecodeResult
    data object Unsupported : Vp8LossyHeaderDecodeResult
    data object Invalid : Vp8LossyHeaderDecodeResult
}

internal data class Vp8CoefficientPartition(
    val offset: Int,
    val end: Int,
) {
    val size: Int get() = end - offset
}

internal data class Vp8LossyBitstreamLayout(
    val header: Vp8LossyFrameHeader,
    val coefficientPartitions: List<Vp8CoefficientPartition>,
)

internal data class Vp8ReconstructedPlanes(
    val yPlane: IntArray,
    val uPlane: IntArray,
    val vPlane: IntArray,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0)
        require(height > 0)
        require(yPlane.size >= width * height)
        require(uPlane.size >= ((width + 1) / 2) * ((height + 1) / 2))
        require(vPlane.size >= ((width + 1) / 2) * ((height + 1) / 2))
    }

    fun toRgba(): IntArray = composeVp8Yuv420ToRgba(
        yPlane = yPlane,
        uPlane = uPlane,
        vPlane = vPlane,
        width = width,
        height = height,
    )
}

internal sealed interface Vp8ReconstructionResult {
    data class Planes(val planes: Vp8ReconstructedPlanes) : Vp8ReconstructionResult
    data object Unsupported : Vp8ReconstructionResult
    data object Invalid : Vp8ReconstructionResult
}

internal sealed interface Vp8LossyDecodeResult {
    data class Pixels(val rgba: IntArray) : Vp8LossyDecodeResult
    data object Unsupported : Vp8LossyDecodeResult
    data object Invalid : Vp8LossyDecodeResult
}

internal sealed interface Vp8LossyBitstreamLayoutDecodeResult {
    data class Layout(val layout: Vp8LossyBitstreamLayout) : Vp8LossyBitstreamLayoutDecodeResult
    data object Unsupported : Vp8LossyBitstreamLayoutDecodeResult
    data object Invalid : Vp8LossyBitstreamLayoutDecodeResult
}

internal data class Vp8MacroblockMode(
    val yMode: Vp8LumaPredictionMode,
    val uvMode: Vp8IntraPredictionMode,
    val skipCoefficients: Boolean,
    val lumaSubblockModes: List<Vp8LumaSubblockPredictionMode>? = null,
)

internal enum class Vp8LumaSubblockPredictionMode {
    B_DC,
    B_TM,
    B_VE,
    B_HE,
    B_LD,
    B_RD,
    B_VR,
    B_VL,
    B_HD,
    B_HU,
}

private val VP8_KEY_FRAME_B_MODE_PROBABILITIES = intArrayOf(
    231, 120, 48, 89, 115, 113, 120, 152, 112,
    152, 179, 64, 126, 170, 118, 46, 70, 95,
    175, 69, 143, 80, 85, 82, 72, 155, 103,
    56, 58, 10, 171, 218, 189, 17, 13, 152,
    144, 71, 10, 38, 171, 213, 144, 34, 26,
    114, 26, 17, 163, 44, 195, 21, 10, 173,
    121, 24, 80, 195, 26, 62, 44, 64, 85,
    170, 46, 55, 19, 136, 160, 33, 206, 71,
    63, 20, 8, 114, 114, 208, 12, 9, 226,
    81, 40, 11, 96, 182, 84, 29, 16, 36,
    134, 183, 89, 137, 98, 101, 106, 165, 148,
    72, 187, 100, 130, 157, 111, 32, 75, 80,
    66, 102, 167, 99, 74, 62, 40, 234, 128,
    41, 53, 9, 178, 241, 141, 26, 8, 107,
    104, 79, 12, 27, 217, 255, 87, 17, 7,
    74, 43, 26, 146, 73, 166, 49, 23, 157,
    65, 38, 105, 160, 51, 52, 31, 115, 128,
    87, 68, 71, 44, 114, 51, 15, 186, 23,
    47, 41, 14, 110, 182, 183, 21, 17, 194,
    66, 45, 25, 102, 197, 189, 23, 18, 22,
    88, 88, 147, 150, 42, 46, 45, 196, 205,
    43, 97, 183, 117, 85, 38, 35, 179, 61,
    39, 53, 200, 87, 26, 21, 43, 232, 171,
    56, 34, 51, 104, 114, 102, 29, 93, 77,
    107, 54, 32, 26, 51, 1, 81, 43, 31,
    39, 28, 85, 171, 58, 165, 90, 98, 64,
    34, 22, 116, 206, 23, 34, 43, 166, 73,
    68, 25, 106, 22, 64, 171, 36, 225, 114,
    34, 19, 21, 102, 132, 188, 16, 76, 124,
    62, 18, 78, 95, 85, 57, 50, 48, 51,
    193, 101, 35, 159, 215, 111, 89, 46, 111,
    60, 148, 31, 172, 219, 228, 21, 18, 111,
    112, 113, 77, 85, 179, 255, 38, 120, 114,
    40, 42, 1, 196, 245, 209, 10, 25, 109,
    100, 80, 8, 43, 154, 1, 51, 26, 71,
    88, 43, 29, 140, 166, 213, 37, 43, 154,
    61, 63, 30, 155, 67, 45, 68, 1, 209,
    142, 78, 78, 16, 255, 128, 34, 197, 171,
    41, 40, 5, 102, 211, 183, 4, 1, 221,
    51, 50, 17, 168, 209, 192, 23, 25, 82,
    125, 98, 42, 88, 104, 85, 117, 175, 82,
    95, 84, 53, 89, 128, 100, 113, 101, 45,
    75, 79, 123, 47, 51, 128, 81, 171, 1,
    57, 17, 5, 71, 102, 57, 53, 41, 49,
    115, 21, 2, 10, 102, 255, 166, 23, 6,
    38, 33, 13, 121, 57, 73, 26, 1, 85,
    41, 10, 67, 138, 77, 110, 90, 47, 114,
    101, 29, 16, 10, 85, 128, 101, 196, 26,
    57, 18, 10, 102, 102, 213, 34, 20, 43,
    117, 20, 15, 36, 163, 128, 68, 1, 26,
    138, 31, 36, 171, 27, 166, 38, 44, 229,
    67, 87, 58, 169, 82, 115, 26, 59, 179,
    63, 59, 90, 180, 59, 166, 93, 73, 154,
    40, 40, 21, 116, 143, 209, 34, 39, 175,
    57, 46, 22, 24, 128, 1, 54, 17, 37,
    47, 15, 16, 183, 34, 223, 49, 45, 183,
    46, 17, 33, 183, 6, 98, 15, 32, 183,
    65, 32, 73, 115, 28, 128, 23, 128, 205,
    40, 3, 9, 115, 51, 192, 18, 6, 223,
    87, 37, 9, 115, 59, 77, 64, 21, 47,
    104, 55, 44, 218, 9, 54, 53, 130, 226,
    64, 90, 70, 205, 40, 41, 23, 26, 57,
    54, 57, 112, 184, 5, 41, 38, 166, 213,
    30, 34, 26, 133, 152, 116, 10, 32, 134,
    75, 32, 12, 51, 192, 255, 160, 43, 51,
    39, 19, 53, 221, 26, 114, 32, 73, 255,
    31, 9, 65, 234, 2, 15, 1, 118, 73,
    88, 31, 35, 67, 102, 85, 55, 186, 85,
    56, 21, 23, 111, 59, 205, 45, 37, 192,
    55, 38, 70, 124, 73, 102, 1, 34, 98,
    102, 61, 71, 37, 34, 53, 31, 243, 192,
    69, 60, 71, 38, 73, 119, 28, 222, 37,
    68, 45, 128, 34, 1, 47, 11, 245, 171,
    62, 17, 19, 70, 146, 85, 55, 62, 70,
    75, 15, 9, 9, 64, 255, 184, 119, 16,
    37, 43, 37, 154, 100, 163, 85, 160, 1,
    63, 9, 92, 136, 28, 64, 32, 201, 85,
    86, 6, 28, 5, 64, 255, 25, 248, 1,
    56, 8, 17, 132, 137, 255, 55, 116, 128,
    58, 15, 20, 82, 135, 57, 26, 121, 40,
    164, 50, 31, 137, 154, 133, 25, 35, 218,
    51, 103, 44, 131, 131, 123, 31, 6, 158,
    86, 40, 64, 135, 148, 224, 45, 183, 128,
    22, 26, 17, 131, 240, 154, 14, 1, 209,
    83, 12, 13, 54, 192, 255, 68, 47, 28,
    45, 16, 21, 91, 64, 222, 7, 1, 197,
    56, 21, 39, 155, 60, 138, 23, 102, 213,
    85, 26, 85, 85, 128, 128, 32, 146, 171,
    18, 11, 7, 63, 144, 171, 4, 4, 246,
    35, 27, 10, 146, 174, 171, 12, 26, 128,
    190, 80, 35, 99, 180, 80, 126, 54, 45,
    85, 126, 47, 87, 176, 51, 41, 20, 32,
    101, 75, 128, 139, 118, 146, 116, 128, 85,
    56, 41, 15, 176, 236, 85, 37, 9, 62,
    146, 36, 19, 30, 171, 255, 97, 27, 20,
    71, 30, 17, 119, 118, 255, 17, 18, 138,
    101, 38, 60, 138, 55, 70, 43, 26, 142,
    138, 45, 61, 62, 219, 1, 81, 188, 64,
    32, 41, 20, 117, 151, 142, 20, 21, 163,
    112, 19, 12, 61, 195, 128, 48, 4, 24,
)

internal data class Vp8MacroblockCoefficients(
    val y2: IntArray,
    val luma: Array<IntArray>,
    val u: Array<IntArray>,
    val v: Array<IntArray>,
) {
    init {
        require(y2.size == VP8_BLOCK_COEFFICIENT_COUNT)
        require(luma.size == VP8_LUMA_BLOCK_COUNT)
        require(u.size == VP8_CHROMA_BLOCK_COUNT)
        require(v.size == VP8_CHROMA_BLOCK_COUNT)
    }
}

internal sealed interface Vp8MacroblockCoefficientDecodeResult {
    data class Macroblocks(val macroblocks: List<Vp8MacroblockCoefficients>) : Vp8MacroblockCoefficientDecodeResult
    data object Invalid : Vp8MacroblockCoefficientDecodeResult
}

internal enum class Vp8LumaPredictionMode {
    DC,
    VERTICAL,
    HORIZONTAL,
    TRUE_MOTION,
    B_PRED,
}

internal fun decodeVp8LossyPixels(data: ByteArray, metadata: WebpMetadata): Vp8LossyDecodeResult {
    if (metadata.format != WebpBitstreamFormat.VP8) return Vp8LossyDecodeResult.Invalid

    val payloadOffset = metadata.payloadOffset
    val payloadSize = metadata.payloadSize
    if (payloadOffset < 0 || payloadSize < VP8_KEYFRAME_HEADER_SIZE) return Vp8LossyDecodeResult.Invalid
    if (payloadOffset + payloadSize > data.size) return Vp8LossyDecodeResult.Invalid
    val frameTag = parseVp8FrameTag(data, payloadOffset, payloadSize.toLong())
        ?: return Vp8LossyDecodeResult.Invalid
    if (!frameTag.keyFrame) return Vp8LossyDecodeResult.Unsupported
    if (!frameTag.showFrame) return Vp8LossyDecodeResult.Unsupported

    val layout = when (val result = decodeVp8LossyBitstreamLayout(data, metadata)) {
        Vp8LossyBitstreamLayoutDecodeResult.Invalid -> return Vp8LossyDecodeResult.Invalid
        Vp8LossyBitstreamLayoutDecodeResult.Unsupported -> return Vp8LossyDecodeResult.Unsupported
        is Vp8LossyBitstreamLayoutDecodeResult.Layout -> result.layout
    }
    if (layout.header.loopFilter.level != 0 && !layout.header.loopFilter.simpleFilter) {
        return Vp8LossyDecodeResult.Unsupported
    }

    val firstPartitionOffset = payloadOffset + VP8_KEYFRAME_HEADER_SIZE
    val firstPartitionEnd = firstPartitionOffset + frameTag.firstPartitionSize
    if (firstPartitionEnd > payloadOffset + payloadSize) return Vp8LossyDecodeResult.Invalid
    val reader = Vp8BoolReader(data, firstPartitionOffset, firstPartitionEnd)
    when (val header = readVp8LossyFrameHeader(reader, metadata.width, metadata.height)) {
        Vp8LossyHeaderDecodeResult.Invalid -> return Vp8LossyDecodeResult.Invalid
        Vp8LossyHeaderDecodeResult.Unsupported -> return Vp8LossyDecodeResult.Unsupported
        is Vp8LossyHeaderDecodeResult.Header -> {
            if (header.header != layout.header) return Vp8LossyDecodeResult.Invalid
        }
    }

    val probabilities = when (val result = readVp8CoefficientProbabilityUpdates(
        reader = reader,
        base = Vp8CoefficientProbabilities.default(),
        updateProbabilities = VP8_COEFFICIENT_UPDATE_PROBABILITIES,
    )) {
        Vp8CoefficientProbabilityUpdateResult.Invalid -> return Vp8LossyDecodeResult.Invalid
        is Vp8CoefficientProbabilityUpdateResult.Probabilities -> result.probabilities
    }
    val noCoeffSkip = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyDecodeResult.Invalid
    if (noCoeffSkip != 0) {
        reader.readLiteral(8) ?: return Vp8LossyDecodeResult.Invalid
        return Vp8LossyDecodeResult.Unsupported
    }
    val macroblockModes = readVp8KeyFrameMacroblockModes(
        reader = reader,
        header = layout.header,
        noCoeffSkip = false,
    ) ?: return Vp8LossyDecodeResult.Invalid

    val planes = when (val result = reconstructVp8KeyFramePlanes(
        data = data,
        layout = layout,
        macroblockModes = macroblockModes,
        probabilities = probabilities,
    )) {
        Vp8ReconstructionResult.Invalid -> return Vp8LossyDecodeResult.Invalid
        Vp8ReconstructionResult.Unsupported -> return Vp8LossyDecodeResult.Unsupported
        is Vp8ReconstructionResult.Planes -> result.planes
    }
    val filteredPlanes = applyVp8SimpleLoopFilterIfNeeded(planes, layout.header.loopFilter)
    val pixels = filteredPlanes.cropTo(metadata.width, metadata.height).toRgba()
    return when (val alpha = decodeVp8AlphaPlane(data, metadata)) {
        Vp8AlphaDecodeResult.Absent -> Vp8LossyDecodeResult.Pixels(pixels)
        Vp8AlphaDecodeResult.Invalid -> Vp8LossyDecodeResult.Invalid
        Vp8AlphaDecodeResult.Unsupported -> Vp8LossyDecodeResult.Unsupported
        is Vp8AlphaDecodeResult.Plane -> Vp8LossyDecodeResult.Pixels(pixels.withAlphaPlane(alpha.alpha))
    }
}

private sealed interface Vp8AlphaDecodeResult {
    data class Plane(val alpha: ByteArray) : Vp8AlphaDecodeResult
    data object Absent : Vp8AlphaDecodeResult
    data object Unsupported : Vp8AlphaDecodeResult
    data object Invalid : Vp8AlphaDecodeResult
}

private fun decodeVp8AlphaPlane(data: ByteArray, metadata: WebpMetadata): Vp8AlphaDecodeResult {
    val alphaChunk = metadata.alphaChunk ?: return if (metadata.flags.alpha) {
        Vp8AlphaDecodeResult.Invalid
    } else {
        Vp8AlphaDecodeResult.Absent
    }
    if (alphaChunk.compression != WebpAlphaCompression.NONE ||
        alphaChunk.filtering != 0 ||
        alphaChunk.preprocessing != 0
    ) {
        return Vp8AlphaDecodeResult.Unsupported
    }

    val expectedSize = metadata.width.toLong() * metadata.height.toLong()
    if (expectedSize > Int.MAX_VALUE) return Vp8AlphaDecodeResult.Invalid
    if (alphaChunk.payloadSize != expectedSize.toInt()) return Vp8AlphaDecodeResult.Invalid
    if (alphaChunk.payloadOffset < 0 || alphaChunk.payloadOffset + alphaChunk.payloadSize > data.size) {
        return Vp8AlphaDecodeResult.Invalid
    }
    return Vp8AlphaDecodeResult.Plane(
        data.copyOfRange(alphaChunk.payloadOffset, alphaChunk.payloadOffset + alphaChunk.payloadSize),
    )
}

private fun IntArray.withAlphaPlane(alpha: ByteArray): IntArray {
    if (alpha.size != size) return this
    return IntArray(size) { index ->
        val alphaByte = alpha[index].toInt() and 0xFF
        (this[index] and 0x00FF_FFFF) or (alphaByte shl 24)
    }
}

internal fun decodeVp8LossyBitstreamLayout(
    data: ByteArray,
    metadata: WebpMetadata,
): Vp8LossyBitstreamLayoutDecodeResult {
    val payloadOffset = metadata.payloadOffset
    val payloadSize = metadata.payloadSize
    if (payloadOffset < 0 || payloadSize < VP8_KEYFRAME_HEADER_SIZE) {
        return Vp8LossyBitstreamLayoutDecodeResult.Invalid
    }
    if (payloadOffset + payloadSize > data.size) return Vp8LossyBitstreamLayoutDecodeResult.Invalid
    val frameTag = parseVp8FrameTag(data, payloadOffset, payloadSize.toLong())
        ?: return Vp8LossyBitstreamLayoutDecodeResult.Invalid
    if (frameTag.firstPartitionSize == 0) return Vp8LossyBitstreamLayoutDecodeResult.Unsupported

    val firstPartitionOffset = payloadOffset + VP8_KEYFRAME_HEADER_SIZE
    val firstPartitionEnd = firstPartitionOffset + frameTag.firstPartitionSize
    if (firstPartitionEnd > payloadOffset + payloadSize) return Vp8LossyBitstreamLayoutDecodeResult.Invalid
    val reader = Vp8BoolReader(data, firstPartitionOffset, firstPartitionEnd)
    val header = when (val result = readVp8LossyFrameHeader(reader, metadata.width, metadata.height)) {
        Vp8LossyHeaderDecodeResult.Invalid -> return Vp8LossyBitstreamLayoutDecodeResult.Invalid
        Vp8LossyHeaderDecodeResult.Unsupported -> return Vp8LossyBitstreamLayoutDecodeResult.Unsupported
        is Vp8LossyHeaderDecodeResult.Header -> result.header
    }
    val coefficientPartitions = readVp8CoefficientPartitions(
        data = data,
        payloadOffset = payloadOffset,
        payloadSize = payloadSize,
        firstPartitionEnd = firstPartitionEnd,
        partitionCount = header.coefficientPartitionCount,
    ) ?: return Vp8LossyBitstreamLayoutDecodeResult.Invalid
    return Vp8LossyBitstreamLayoutDecodeResult.Layout(
        Vp8LossyBitstreamLayout(
            header = header,
            coefficientPartitions = coefficientPartitions,
        ),
    )
}

internal fun readVp8LossyFrameHeader(
    reader: Vp8BoolReader,
    width: Int,
    height: Int,
): Vp8LossyHeaderDecodeResult {
    val colorSpace = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val clampType = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid

    val segmentationEnabled = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (segmentationEnabled != 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val simpleFilter = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val filterLevel = reader.readLiteral(6) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val sharpness = reader.readLiteral(3) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val loopFilterDeltaEnabled = reader.readBit(VP8_BOOL_HALF_PROBABILITY)
        ?: return Vp8LossyHeaderDecodeResult.Invalid
    if (loopFilterDeltaEnabled != 0) return Vp8LossyHeaderDecodeResult.Unsupported

    val partitionBits = reader.readLiteral(2) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val coefficientPartitionCount = 1 shl partitionBits

    val yAcIndex = reader.readLiteral(7) ?: return Vp8LossyHeaderDecodeResult.Invalid
    val quantization = Vp8QuantizationHeader(
        yAcIndex = yAcIndex,
        yDcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        y2DcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        y2AcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        uvDcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
        uvAcDelta = reader.readVp8SignedDelta() ?: return Vp8LossyHeaderDecodeResult.Invalid,
    )

    return Vp8LossyHeaderDecodeResult.Header(
        Vp8LossyFrameHeader(
            colorSpace = colorSpace,
            clampType = clampType,
            macroblockWidth = (width + 15) / 16,
            macroblockHeight = (height + 15) / 16,
            coefficientPartitionCount = coefficientPartitionCount,
            loopFilter = Vp8LoopFilterHeader(
                simpleFilter = simpleFilter != 0,
                level = filterLevel,
                sharpness = sharpness,
            ),
            quantization = quantization,
        ),
    )
}

private fun readVp8CoefficientPartitions(
    data: ByteArray,
    payloadOffset: Int,
    payloadSize: Int,
    firstPartitionEnd: Int,
    partitionCount: Int,
): List<Vp8CoefficientPartition>? {
    if (partitionCount !in 1..8) return null
    val payloadEnd = payloadOffset + payloadSize
    val sizeTableSize = (partitionCount - 1) * 3
    val coefficientPartitionOffset = firstPartitionEnd + sizeTableSize
    if (coefficientPartitionOffset > payloadEnd) return null

    val partitions = ArrayList<Vp8CoefficientPartition>(partitionCount)
    var offset = coefficientPartitionOffset
    for (partitionIndex in 0 until partitionCount) {
        val end = if (partitionIndex == partitionCount - 1) {
            payloadEnd
        } else {
            val sizeOffset = firstPartitionEnd + partitionIndex * 3
            val size = read24LE(data, sizeOffset)
            offset + size
        }
        if (end < offset || end > payloadEnd) return null
        partitions += Vp8CoefficientPartition(offset = offset, end = end)
        offset = end
    }
    return partitions
}

internal fun readVp8KeyFrameMacroblockModes(
    reader: Vp8BoolReader,
    header: Vp8LossyFrameHeader,
    noCoeffSkip: Boolean,
): List<Vp8MacroblockMode>? {
    val macroblocks = ArrayList<Vp8MacroblockMode>(header.macroblockWidth * header.macroblockHeight)
    val topSubblockModes = MutableList(header.macroblockWidth * VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
        Vp8LumaSubblockPredictionMode.B_DC
    }
    repeat(header.macroblockHeight) {
        val leftSubblockModes = MutableList(VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
            Vp8LumaSubblockPredictionMode.B_DC
        }
        repeat(header.macroblockWidth) { macroblockX ->
            val skipCoefficients = if (noCoeffSkip) {
                reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
            } else {
                0
            }
            val yMode = reader.readVp8KeyFrameYMode() ?: return null
            val lumaSubblockModes = if (yMode == Vp8LumaPredictionMode.B_PRED) {
                reader.readVp8KeyFrameBPredSubblockModes(
                    topSubblockModes = topSubblockModes,
                    leftSubblockModes = leftSubblockModes,
                    macroblockX = macroblockX,
                ) ?: return null
            } else {
                null
            }
            val uvMode = reader.readVp8KeyFrameUvMode() ?: return null
            macroblocks += Vp8MacroblockMode(
                yMode = yMode,
                uvMode = uvMode,
                skipCoefficients = skipCoefficients != 0,
                lumaSubblockModes = lumaSubblockModes,
            )

            val contextModes = lumaSubblockModes ?: List(VP8_LUMA_BLOCK_COUNT) {
                yMode.toVp8LumaSubblockPredictionMode()
            }
            for (x in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
                topSubblockModes[macroblockX * VP8_BLOCKS_PER_MACROBLOCK_SIDE + x] =
                    contextModes[12 + x]
            }
            for (y in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
                leftSubblockModes[y] = contextModes[y * VP8_BLOCKS_PER_MACROBLOCK_SIDE + 3]
            }
        }
    }
    return macroblocks
}

private fun Vp8BoolReader.readVp8KeyFrameYMode(): Vp8LumaPredictionMode? {
    if ((readBit(145) ?: return null) == 0) return Vp8LumaPredictionMode.DC
    if ((readBit(156) ?: return null) == 0) return Vp8LumaPredictionMode.VERTICAL
    if ((readBit(163) ?: return null) == 0) return Vp8LumaPredictionMode.HORIZONTAL
    if ((readBit(128) ?: return null) == 0) return Vp8LumaPredictionMode.TRUE_MOTION
    return Vp8LumaPredictionMode.B_PRED
}

private fun Vp8BoolReader.readVp8KeyFrameBPredSubblockModes(
    topSubblockModes: List<Vp8LumaSubblockPredictionMode>,
    leftSubblockModes: List<Vp8LumaSubblockPredictionMode>,
    macroblockX: Int,
): List<Vp8LumaSubblockPredictionMode>? {
    val modes = ArrayList<Vp8LumaSubblockPredictionMode>(VP8_LUMA_BLOCK_COUNT)
    for (blockIndex in 0 until VP8_LUMA_BLOCK_COUNT) {
        val blockX = blockIndex and 3
        val blockY = blockIndex ushr 2
        val aboveMode = if (blockY == 0) {
            topSubblockModes[macroblockX * VP8_BLOCKS_PER_MACROBLOCK_SIDE + blockX]
        } else {
            modes[blockIndex - VP8_BLOCKS_PER_MACROBLOCK_SIDE]
        }
        val leftMode = if (blockX == 0) {
            leftSubblockModes[blockY]
        } else {
            modes[blockIndex - 1]
        }
        modes += readVp8KeyFrameBMode(
            VP8_KEY_FRAME_B_MODE_PROBABILITIES.vp8KeyFrameBModeProbabilities(aboveMode, leftMode),
        ) ?: return null
    }
    return modes
}

private fun Vp8BoolReader.readVp8KeyFrameBMode(
    probabilities: IntArray,
): Vp8LumaSubblockPredictionMode? {
    if ((readBit(probabilities[0]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_DC
    if ((readBit(probabilities[1]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_TM
    if ((readBit(probabilities[2]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_VE
    if ((readBit(probabilities[3]) ?: return null) == 0) {
        if ((readBit(probabilities[4]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_HE
        return if ((readBit(probabilities[5]) ?: return null) == 0) {
            Vp8LumaSubblockPredictionMode.B_RD
        } else {
            Vp8LumaSubblockPredictionMode.B_VR
        }
    }
    if ((readBit(probabilities[6]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_LD
    if ((readBit(probabilities[7]) ?: return null) == 0) return Vp8LumaSubblockPredictionMode.B_VL
    return if ((readBit(probabilities[8]) ?: return null) == 0) {
        Vp8LumaSubblockPredictionMode.B_HD
    } else {
        Vp8LumaSubblockPredictionMode.B_HU
    }
}

private fun IntArray.vp8KeyFrameBModeProbabilities(
    aboveMode: Vp8LumaSubblockPredictionMode,
    leftMode: Vp8LumaSubblockPredictionMode,
): IntArray {
    val offset = (aboveMode.ordinal * Vp8LumaSubblockPredictionMode.entries.size + leftMode.ordinal) * 9
    return copyOfRange(offset, offset + 9)
}

private fun Vp8LumaPredictionMode.toVp8LumaSubblockPredictionMode(): Vp8LumaSubblockPredictionMode =
    when (this) {
        Vp8LumaPredictionMode.DC -> Vp8LumaSubblockPredictionMode.B_DC
        Vp8LumaPredictionMode.VERTICAL -> Vp8LumaSubblockPredictionMode.B_VE
        Vp8LumaPredictionMode.HORIZONTAL -> Vp8LumaSubblockPredictionMode.B_HE
        Vp8LumaPredictionMode.TRUE_MOTION -> Vp8LumaSubblockPredictionMode.B_TM
        Vp8LumaPredictionMode.B_PRED -> error("B_PRED keeps explicit 4x4 sub-block modes")
    }

private fun Vp8BoolReader.readVp8KeyFrameUvMode(): Vp8IntraPredictionMode? {
    if ((readBit(142) ?: return null) == 0) return Vp8IntraPredictionMode.DC
    if ((readBit(114) ?: return null) == 0) return Vp8IntraPredictionMode.VERTICAL
    if ((readBit(183) ?: return null) == 0) return Vp8IntraPredictionMode.HORIZONTAL
    return Vp8IntraPredictionMode.TRUE_MOTION
}

private fun Vp8BoolReader.readVp8SignedDelta(): Int? {
    val present = readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
    if (present == 0) return 0
    val magnitude = readLiteral(4) ?: return null
    val negative = readBit(VP8_BOOL_HALF_PROBABILITY) ?: return null
    return if (negative == 0) magnitude else -magnitude
}

internal fun inverseVp8Dct4x4(input: IntArray): IntArray {
    require(input.size == 16)
    val tmp = IntArray(16)
    for (i in 0 until 4) {
        val a = input[i] + input[8 + i]
        val b = input[i] - input[8 + i]
        val c = ((input[4 + i] * 20091) shr 16) - ((input[12 + i] * 35468) shr 16)
        val d = ((input[4 + i] * 35468) shr 16) + ((input[12 + i] * 20091) shr 16)
        tmp[i] = a + d
        tmp[4 + i] = b + c
        tmp[8 + i] = b - c
        tmp[12 + i] = a - d
    }

    val out = IntArray(16)
    for (i in 0 until 4) {
        val base = i * 4
        val a = tmp[base] + tmp[base + 2]
        val b = tmp[base] - tmp[base + 2]
        val c = ((tmp[base + 1] * 20091) shr 16) - ((tmp[base + 3] * 35468) shr 16)
        val d = ((tmp[base + 1] * 35468) shr 16) + ((tmp[base + 3] * 20091) shr 16)
        out[base] = (a + d + 4) shr 3
        out[base + 1] = (b + c + 4) shr 3
        out[base + 2] = (b - c + 4) shr 3
        out[base + 3] = (a - d + 4) shr 3
    }
    return out
}

internal fun inverseVp8WalshHadamard4x4(input: IntArray): IntArray {
    require(input.size == 16)
    val tmp = IntArray(16)
    for (i in 0 until 4) {
        val a = input[i] + input[12 + i]
        val b = input[4 + i] + input[8 + i]
        val c = input[4 + i] - input[8 + i]
        val d = input[i] - input[12 + i]
        tmp[i] = a + b
        tmp[4 + i] = c + d
        tmp[8 + i] = a - b
        tmp[12 + i] = d - c
    }

    val out = IntArray(16)
    for (i in 0 until 4) {
        val base = i * 4
        val a = tmp[base] + tmp[base + 3]
        val b = tmp[base + 1] + tmp[base + 2]
        val c = tmp[base + 1] - tmp[base + 2]
        val d = tmp[base] - tmp[base + 3]
        out[base] = (a + b + 3) shr 3
        out[base + 1] = (c + d + 3) shr 3
        out[base + 2] = (a - b + 3) shr 3
        out[base + 3] = (d - c + 3) shr 3
    }
    return out
}

internal sealed interface Vp8CoefficientDecodeResult {
    data class Block(val coefficients: IntArray, val hasNonZero: Boolean) : Vp8CoefficientDecodeResult
    data object Invalid : Vp8CoefficientDecodeResult
}

internal sealed interface Vp8CoefficientProbabilityUpdateResult {
    data class Probabilities(val probabilities: Vp8CoefficientProbabilities) : Vp8CoefficientProbabilityUpdateResult
    data object Invalid : Vp8CoefficientProbabilityUpdateResult
}

internal class Vp8CoefficientProbabilities private constructor(
    private val values: IntArray,
) {
    init {
        require(values.size == VP8_COEFFICIENT_PROBABILITY_TOTAL)
        require(values.all { it in 0..255 })
    }

    fun valueAt(type: Int, band: Int, context: Int, probability: Int): Int {
        require(type in 0 until VP8_COEFFICIENT_TYPE_COUNT)
        require(band in 0 until VP8_COEFFICIENT_BAND_COUNT)
        require(context in 0 until VP8_COEFFICIENT_CONTEXT_COUNT)
        require(probability in 0 until VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT)
        return values[vp8CoefficientProbabilityIndex(type, band, context, probability)]
    }

    fun tokenProbabilities(type: Int, band: Int, context: Int): IntArray =
        IntArray(VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT) { probability ->
            valueAt(type, band, context, probability)
        }

    fun copyValues(): IntArray = values.copyOf()

    companion object {
        fun filled(value: Int): Vp8CoefficientProbabilities {
            require(value in 0..255)
            return Vp8CoefficientProbabilities(IntArray(VP8_COEFFICIENT_PROBABILITY_TOTAL) { value })
        }

        fun fromFlat(values: IntArray): Vp8CoefficientProbabilities =
            Vp8CoefficientProbabilities(values.copyOf())

        fun default(): Vp8CoefficientProbabilities =
            fromFlat(VP8_DEFAULT_COEFFICIENT_PROBABILITIES)
    }
}

internal fun readVp8CoefficientProbabilityUpdates(
    reader: Vp8BoolReader,
    base: Vp8CoefficientProbabilities,
    updateProbabilities: IntArray,
): Vp8CoefficientProbabilityUpdateResult {
    if (updateProbabilities.size != VP8_COEFFICIENT_PROBABILITY_TOTAL) {
        return Vp8CoefficientProbabilityUpdateResult.Invalid
    }
    if (updateProbabilities.any { it !in 1..255 }) {
        return Vp8CoefficientProbabilityUpdateResult.Invalid
    }

    val updated = base.copyValues()
    for (type in 0 until VP8_COEFFICIENT_TYPE_COUNT) {
        for (band in 0 until VP8_COEFFICIENT_BAND_COUNT) {
            for (context in 0 until VP8_COEFFICIENT_CONTEXT_COUNT) {
                for (probability in 0 until VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT) {
                    val index = vp8CoefficientProbabilityIndex(type, band, context, probability)
                    val shouldUpdate = reader.readBit(updateProbabilities[index])
                        ?: return Vp8CoefficientProbabilityUpdateResult.Invalid
                    if (shouldUpdate != 0) {
                        updated[index] = reader.readLiteral(8)
                            ?: return Vp8CoefficientProbabilityUpdateResult.Invalid
                    }
                }
            }
        }
    }
    return Vp8CoefficientProbabilityUpdateResult.Probabilities(Vp8CoefficientProbabilities.fromFlat(updated))
}

internal fun decodeVp8CoefficientBlock(
    reader: Vp8BoolReader,
    probabilities: IntArray,
    startCoefficient: Int = 0,
): Vp8CoefficientDecodeResult {
    if (probabilities.size != VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT) return Vp8CoefficientDecodeResult.Invalid
    if (probabilities.any { it !in 1..255 }) return Vp8CoefficientDecodeResult.Invalid
    if (startCoefficient !in 0 until VP8_BLOCK_COEFFICIENT_COUNT) return Vp8CoefficientDecodeResult.Invalid

    val coefficients = IntArray(VP8_BLOCK_COEFFICIENT_COUNT)
    var hasNonZero = false
    for (coefficientIndex in startCoefficient until VP8_BLOCK_COEFFICIENT_COUNT) {
        if ((reader.readBit(probabilities[0]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) {
            return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
        }
        if ((reader.readBit(probabilities[1]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) continue

        val magnitude = readVp8CoefficientMagnitude(reader, probabilities)
            ?: return Vp8CoefficientDecodeResult.Invalid
        val sign = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8CoefficientDecodeResult.Invalid
        coefficients[VP8_ZIGZAG[coefficientIndex]] = if (sign == 0) magnitude else -magnitude
        hasNonZero = true
    }
    return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
}

internal fun decodeVp8CoefficientBlock(
    reader: Vp8BoolReader,
    probabilities: Vp8CoefficientProbabilities,
    type: Int,
    initialContext: Int,
    startCoefficient: Int = 0,
): Vp8CoefficientDecodeResult {
    if (type !in 0 until VP8_COEFFICIENT_TYPE_COUNT) return Vp8CoefficientDecodeResult.Invalid
    if (initialContext !in 0 until VP8_COEFFICIENT_CONTEXT_COUNT) return Vp8CoefficientDecodeResult.Invalid
    if (startCoefficient !in 0 until VP8_BLOCK_COEFFICIENT_COUNT) return Vp8CoefficientDecodeResult.Invalid

    val coefficients = IntArray(VP8_BLOCK_COEFFICIENT_COUNT)
    var hasNonZero = false
    var context = initialContext
    for (coefficientIndex in startCoefficient until VP8_BLOCK_COEFFICIENT_COUNT) {
        val tokenProbabilities = probabilities.tokenProbabilities(
            type = type,
            band = VP8_COEFFICIENT_BANDS[coefficientIndex],
            context = context,
        )
        if ((reader.readBit(tokenProbabilities[0]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) {
            return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
        }
        if ((reader.readBit(tokenProbabilities[1]) ?: return Vp8CoefficientDecodeResult.Invalid) == 0) {
            context = VP8_COEFFICIENT_CONTEXT_ZERO
            continue
        }

        val magnitude = readVp8CoefficientMagnitude(reader, tokenProbabilities)
            ?: return Vp8CoefficientDecodeResult.Invalid
        val sign = reader.readBit(VP8_BOOL_HALF_PROBABILITY) ?: return Vp8CoefficientDecodeResult.Invalid
        coefficients[VP8_ZIGZAG[coefficientIndex]] = if (sign == 0) magnitude else -magnitude
        hasNonZero = true
        context = VP8_COEFFICIENT_CONTEXT_NON_ZERO
    }
    return Vp8CoefficientDecodeResult.Block(coefficients, hasNonZero)
}

internal fun decodeVp8CoefficientBlockWithContext(
    reader: Vp8BoolReader,
    probabilitiesByContext: Array<IntArray>,
    leftHasNonZero: Boolean,
    topHasNonZero: Boolean,
    startCoefficient: Int = 0,
): Vp8CoefficientDecodeResult {
    if (probabilitiesByContext.size != VP8_COEFFICIENT_CONTEXT_COUNT) return Vp8CoefficientDecodeResult.Invalid
    val context = vp8CoefficientContext(leftHasNonZero, topHasNonZero)
    return decodeVp8CoefficientBlock(reader, probabilitiesByContext[context], startCoefficient)
}

internal fun vp8CoefficientContext(leftHasNonZero: Boolean, topHasNonZero: Boolean): Int =
    (if (leftHasNonZero) 1 else 0) + (if (topHasNonZero) 1 else 0)

internal fun decodeVp8MacroblockCoefficients(
    data: ByteArray,
    layout: Vp8LossyBitstreamLayout,
    macroblockModes: List<Vp8MacroblockMode>,
    probabilities: Vp8CoefficientProbabilities,
): Vp8MacroblockCoefficientDecodeResult {
    val header = layout.header
    val macroblockCount = header.macroblockWidth * header.macroblockHeight
    if (macroblockModes.size != macroblockCount) return Vp8MacroblockCoefficientDecodeResult.Invalid
    if (header.coefficientPartitionCount !in 1..8) return Vp8MacroblockCoefficientDecodeResult.Invalid
    if (layout.coefficientPartitions.size != header.coefficientPartitionCount) {
        return Vp8MacroblockCoefficientDecodeResult.Invalid
    }
    if (layout.coefficientPartitions.any { it.offset < 0 || it.end < it.offset || it.end > data.size }) {
        return Vp8MacroblockCoefficientDecodeResult.Invalid
    }

    val readers = layout.coefficientPartitions.map { partition ->
        Vp8BoolReader(data, partition.offset, partition.end)
    }
    val macroblocks = ArrayList<Vp8MacroblockCoefficients>(macroblockCount)
    val topY2NonZero = BooleanArray(header.macroblockWidth)
    val topLumaNonZero = BooleanArray(header.macroblockWidth * VP8_BLOCKS_PER_MACROBLOCK_SIDE)
    val topUNonZero = BooleanArray(header.macroblockWidth * VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE)
    val topVNonZero = BooleanArray(header.macroblockWidth * VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE)

    for (macroblockY in 0 until header.macroblockHeight) {
        var leftY2NonZero = false
        val leftLumaNonZero = BooleanArray(VP8_BLOCKS_PER_MACROBLOCK_SIDE)
        val leftUNonZero = BooleanArray(VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE)
        val leftVNonZero = BooleanArray(VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE)
        val reader = readers[macroblockY and (header.coefficientPartitionCount - 1)]

        for (macroblockX in 0 until header.macroblockWidth) {
            val mode = macroblockModes[macroblockY * header.macroblockWidth + macroblockX]
            var y2 = IntArray(VP8_BLOCK_COEFFICIENT_COUNT)
            val luma = Array(VP8_LUMA_BLOCK_COUNT) { IntArray(VP8_BLOCK_COEFFICIENT_COUNT) }
            val u = Array(VP8_CHROMA_BLOCK_COUNT) { IntArray(VP8_BLOCK_COEFFICIENT_COUNT) }
            val v = Array(VP8_CHROMA_BLOCK_COUNT) { IntArray(VP8_BLOCK_COEFFICIENT_COUNT) }

            if (!mode.skipCoefficients) {
                val lumaType: Int
                val lumaStartCoefficient: Int
                if (mode.yMode == Vp8LumaPredictionMode.B_PRED) {
                    lumaType = VP8_COEFFICIENT_TYPE_LUMA
                    lumaStartCoefficient = 0
                    leftY2NonZero = false
                    topY2NonZero[macroblockX] = false
                } else {
                    val y2Context = vp8CoefficientContext(
                        leftHasNonZero = leftY2NonZero,
                        topHasNonZero = topY2NonZero[macroblockX],
                    )
                    val y2Result = decodeVp8CoefficientBlock(
                        reader = reader,
                        probabilities = probabilities,
                        type = VP8_COEFFICIENT_TYPE_LUMA_Y2,
                        initialContext = y2Context,
                    )
                    if (y2Result !is Vp8CoefficientDecodeResult.Block) {
                        return Vp8MacroblockCoefficientDecodeResult.Invalid
                    }
                    y2 = y2Result.coefficients
                    leftY2NonZero = y2Result.hasNonZero
                    topY2NonZero[macroblockX] = y2Result.hasNonZero
                    lumaType = VP8_COEFFICIENT_TYPE_LUMA_AC
                    lumaStartCoefficient = 1
                }

                val lumaResult = decodeVp8CoefficientPlane(
                    reader = reader,
                    probabilities = probabilities,
                    type = lumaType,
                    startCoefficient = lumaStartCoefficient,
                    macroblockX = macroblockX,
                    blockColumns = VP8_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topLumaNonZero,
                    leftNonZero = leftLumaNonZero,
                    destination = luma,
                )
                if (!lumaResult) return Vp8MacroblockCoefficientDecodeResult.Invalid

                val uResult = decodeVp8CoefficientPlane(
                    reader = reader,
                    probabilities = probabilities,
                    type = VP8_COEFFICIENT_TYPE_CHROMA,
                    macroblockX = macroblockX,
                    blockColumns = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topUNonZero,
                    leftNonZero = leftUNonZero,
                    destination = u,
                )
                if (!uResult) return Vp8MacroblockCoefficientDecodeResult.Invalid

                val vResult = decodeVp8CoefficientPlane(
                    reader = reader,
                    probabilities = probabilities,
                    type = VP8_COEFFICIENT_TYPE_CHROMA,
                    macroblockX = macroblockX,
                    blockColumns = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topVNonZero,
                    leftNonZero = leftVNonZero,
                    destination = v,
                )
                if (!vResult) return Vp8MacroblockCoefficientDecodeResult.Invalid
            } else {
                leftY2NonZero = false
                topY2NonZero[macroblockX] = false
                clearVp8CoefficientContexts(
                    macroblockX = macroblockX,
                    blockColumns = VP8_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topLumaNonZero,
                    leftNonZero = leftLumaNonZero,
                )
                clearVp8CoefficientContexts(
                    macroblockX = macroblockX,
                    blockColumns = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topUNonZero,
                    leftNonZero = leftUNonZero,
                )
                clearVp8CoefficientContexts(
                    macroblockX = macroblockX,
                    blockColumns = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    blockRows = VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE,
                    topNonZero = topVNonZero,
                    leftNonZero = leftVNonZero,
                )
            }

            macroblocks += Vp8MacroblockCoefficients(y2 = y2, luma = luma, u = u, v = v)
        }
    }

    return Vp8MacroblockCoefficientDecodeResult.Macroblocks(macroblocks)
}

internal fun reconstructVp8NonBPredKeyFramePlanes(
    data: ByteArray,
    layout: Vp8LossyBitstreamLayout,
    macroblockModes: List<Vp8MacroblockMode>,
    probabilities: Vp8CoefficientProbabilities,
): Vp8ReconstructionResult =
    reconstructVp8KeyFramePlanes(
        data = data,
        layout = layout,
        macroblockModes = macroblockModes,
        probabilities = probabilities,
    )

internal fun reconstructVp8KeyFramePlanes(
    data: ByteArray,
    layout: Vp8LossyBitstreamLayout,
    macroblockModes: List<Vp8MacroblockMode>,
    probabilities: Vp8CoefficientProbabilities,
): Vp8ReconstructionResult {
    val coefficients = when (val result = decodeVp8MacroblockCoefficients(
        data = data,
        layout = layout,
        macroblockModes = macroblockModes,
        probabilities = probabilities,
    )) {
        Vp8MacroblockCoefficientDecodeResult.Invalid -> return Vp8ReconstructionResult.Invalid
        is Vp8MacroblockCoefficientDecodeResult.Macroblocks -> result.macroblocks
    }
    return reconstructVp8KeyFramePlanes(
        layout = layout,
        macroblockModes = macroblockModes,
        macroblockCoefficients = coefficients,
    )
}

internal fun reconstructVp8NonBPredKeyFramePlanes(
    layout: Vp8LossyBitstreamLayout,
    macroblockModes: List<Vp8MacroblockMode>,
    macroblockCoefficients: List<Vp8MacroblockCoefficients>,
): Vp8ReconstructionResult =
    reconstructVp8KeyFramePlanes(
        layout = layout,
        macroblockModes = macroblockModes,
        macroblockCoefficients = macroblockCoefficients,
    )

internal fun reconstructVp8KeyFramePlanes(
    layout: Vp8LossyBitstreamLayout,
    macroblockModes: List<Vp8MacroblockMode>,
    macroblockCoefficients: List<Vp8MacroblockCoefficients>,
): Vp8ReconstructionResult {
    val header = layout.header
    val macroblockCount = header.macroblockWidth * header.macroblockHeight
    if (header.macroblockWidth <= 0 || header.macroblockHeight <= 0) return Vp8ReconstructionResult.Invalid
    if (macroblockModes.size != macroblockCount || macroblockCoefficients.size != macroblockCount) {
        return Vp8ReconstructionResult.Invalid
    }

    val quantization = header.quantization.toVp8QuantizationFactors()
    val width = header.macroblockWidth * VP8_MACROBLOCK_SIZE
    val height = header.macroblockHeight * VP8_MACROBLOCK_SIZE
    val chromaWidth = header.macroblockWidth * VP8_CHROMA_MACROBLOCK_SIZE
    val chromaHeight = header.macroblockHeight * VP8_CHROMA_MACROBLOCK_SIZE
    val yPlane = IntArray(width * height)
    val uPlane = IntArray(chromaWidth * chromaHeight)
    val vPlane = IntArray(chromaWidth * chromaHeight)

    for (macroblockY in 0 until header.macroblockHeight) {
        for (macroblockX in 0 until header.macroblockWidth) {
            val macroblockIndex = macroblockY * header.macroblockWidth + macroblockX
            val mode = macroblockModes[macroblockIndex]
            val coefficients = macroblockCoefficients[macroblockIndex]

            val lumaBlockX = macroblockX * VP8_MACROBLOCK_SIZE
            val lumaBlockY = macroblockY * VP8_MACROBLOCK_SIZE
            val lumaLeft = yPlane.leftSamples(
                stride = width,
                blockX = lumaBlockX,
                blockY = lumaBlockY,
                blockHeight = VP8_MACROBLOCK_SIZE,
            )
            val lumaTop = yPlane.topSamples(
                stride = width,
                blockX = lumaBlockX,
                blockY = lumaBlockY,
                blockWidth = VP8_MACROBLOCK_SIZE,
            )
            val lumaTopLeft = yPlane.topLeftSample(
                stride = width,
                blockX = lumaBlockX,
                blockY = lumaBlockY,
            )
            val luma = if (mode.yMode == Vp8LumaPredictionMode.B_PRED) {
                reconstructVp8BPredLumaMacroblock(
                    lumaSubblockModes = mode.lumaSubblockModes ?: return Vp8ReconstructionResult.Invalid,
                    left = lumaLeft,
                    top = lumaTop,
                    topLeft = lumaTopLeft,
                    coefficientsByBlock = coefficients.luma,
                    dcQuant = quantization.yDc,
                    acQuant = quantization.yAc,
                    topRight = yPlane.topRightSamples(
                        stride = width,
                        blockX = lumaBlockX,
                        blockY = lumaBlockY,
                        blockWidth = VP8_MACROBLOCK_SIZE,
                    ),
                )
            } else {
                reconstructVp8Intra16x16LumaMacroblock(
                    mode = mode.yMode,
                    left = lumaLeft,
                    top = lumaTop,
                    topLeft = lumaTopLeft,
                    y2Coefficients = coefficients.y2,
                    coefficientsByBlock = coefficients.luma,
                    dcQuant = quantization.yDc,
                    acQuant = quantization.yAc,
                    y2DcQuant = quantization.y2Dc,
                    y2AcQuant = quantization.y2Ac,
                )
            }
            yPlane.copyBlock(
                stride = width,
                blockX = lumaBlockX,
                blockY = lumaBlockY,
                blockWidth = VP8_MACROBLOCK_SIZE,
                blockHeight = VP8_MACROBLOCK_SIZE,
                block = luma,
            )

            val chromaX = macroblockX * VP8_CHROMA_MACROBLOCK_SIZE
            val chromaY = macroblockY * VP8_CHROMA_MACROBLOCK_SIZE
            val u = reconstructVp8IntraChromaMacroblock(
                mode = mode.uvMode,
                left = uPlane.leftSamples(
                    stride = chromaWidth,
                    blockX = chromaX,
                    blockY = chromaY,
                    blockHeight = VP8_CHROMA_MACROBLOCK_SIZE,
                ),
                top = uPlane.topSamples(
                    stride = chromaWidth,
                    blockX = chromaX,
                    blockY = chromaY,
                    blockWidth = VP8_CHROMA_MACROBLOCK_SIZE,
                ),
                topLeft = uPlane.topLeftSample(stride = chromaWidth, blockX = chromaX, blockY = chromaY),
                coefficientsByBlock = coefficients.u,
                dcQuant = quantization.uvDc,
                acQuant = quantization.uvAc,
            )
            uPlane.copyBlock(
                stride = chromaWidth,
                blockX = chromaX,
                blockY = chromaY,
                blockWidth = VP8_CHROMA_MACROBLOCK_SIZE,
                blockHeight = VP8_CHROMA_MACROBLOCK_SIZE,
                block = u,
            )

            val v = reconstructVp8IntraChromaMacroblock(
                mode = mode.uvMode,
                left = vPlane.leftSamples(
                    stride = chromaWidth,
                    blockX = chromaX,
                    blockY = chromaY,
                    blockHeight = VP8_CHROMA_MACROBLOCK_SIZE,
                ),
                top = vPlane.topSamples(
                    stride = chromaWidth,
                    blockX = chromaX,
                    blockY = chromaY,
                    blockWidth = VP8_CHROMA_MACROBLOCK_SIZE,
                ),
                topLeft = vPlane.topLeftSample(stride = chromaWidth, blockX = chromaX, blockY = chromaY),
                coefficientsByBlock = coefficients.v,
                dcQuant = quantization.uvDc,
                acQuant = quantization.uvAc,
            )
            vPlane.copyBlock(
                stride = chromaWidth,
                blockX = chromaX,
                blockY = chromaY,
                blockWidth = VP8_CHROMA_MACROBLOCK_SIZE,
                blockHeight = VP8_CHROMA_MACROBLOCK_SIZE,
                block = v,
            )
        }
    }

    return Vp8ReconstructionResult.Planes(
        Vp8ReconstructedPlanes(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
        ),
    )
}

private fun Vp8ReconstructedPlanes.cropTo(visibleWidth: Int, visibleHeight: Int): Vp8ReconstructedPlanes {
    if (visibleWidth == width && visibleHeight == height) return this
    if (visibleWidth !in 1..width || visibleHeight !in 1..height) return this

    val croppedY = IntArray(visibleWidth * visibleHeight)
    for (y in 0 until visibleHeight) {
        System.arraycopy(yPlane, y * width, croppedY, y * visibleWidth, visibleWidth)
    }

    val chromaStride = (width + 1) / 2
    val croppedChromaWidth = (visibleWidth + 1) / 2
    val croppedChromaHeight = (visibleHeight + 1) / 2
    val croppedU = IntArray(croppedChromaWidth * croppedChromaHeight)
    val croppedV = IntArray(croppedChromaWidth * croppedChromaHeight)
    for (y in 0 until croppedChromaHeight) {
        System.arraycopy(uPlane, y * chromaStride, croppedU, y * croppedChromaWidth, croppedChromaWidth)
        System.arraycopy(vPlane, y * chromaStride, croppedV, y * croppedChromaWidth, croppedChromaWidth)
    }

    return Vp8ReconstructedPlanes(
        yPlane = croppedY,
        uPlane = croppedU,
        vPlane = croppedV,
        width = visibleWidth,
        height = visibleHeight,
    )
}

private fun decodeVp8CoefficientPlane(
    reader: Vp8BoolReader,
    probabilities: Vp8CoefficientProbabilities,
    type: Int,
    startCoefficient: Int = 0,
    macroblockX: Int,
    blockColumns: Int,
    blockRows: Int,
    topNonZero: BooleanArray,
    leftNonZero: BooleanArray,
    destination: Array<IntArray>,
): Boolean {
    for (blockY in 0 until blockRows) {
        for (blockX in 0 until blockColumns) {
            val blockIndex = blockY * blockColumns + blockX
            val topIndex = macroblockX * blockColumns + blockX
            val context = vp8CoefficientContext(
                leftHasNonZero = leftNonZero[blockY],
                topHasNonZero = topNonZero[topIndex],
            )
            val result = decodeVp8CoefficientBlock(
                reader = reader,
                probabilities = probabilities,
                type = type,
                initialContext = context,
                startCoefficient = startCoefficient,
            )
            if (result !is Vp8CoefficientDecodeResult.Block) return false
            destination[blockIndex] = result.coefficients
            leftNonZero[blockY] = result.hasNonZero
            topNonZero[topIndex] = result.hasNonZero
        }
    }
    return true
}

private data class Vp8QuantizationFactors(
    val yDc: Int,
    val yAc: Int,
    val y2Dc: Int,
    val y2Ac: Int,
    val uvDc: Int,
    val uvAc: Int,
)

private fun Vp8QuantizationHeader.toVp8QuantizationFactors(): Vp8QuantizationFactors {
    val yAc = yAcIndex.coerceIn(0, 127)
    return Vp8QuantizationFactors(
        yDc = VP8_DC_QUANT[yAcIndex.withDelta(yDcDelta, max = 127)],
        yAc = VP8_AC_QUANT[yAc],
        y2Dc = VP8_DC_QUANT[yAcIndex.withDelta(y2DcDelta, max = 127)] * 2,
        y2Ac = ((VP8_AC_QUANT[yAcIndex.withDelta(y2AcDelta, max = 127)] * 101581) shr 16).coerceAtLeast(8),
        uvDc = VP8_DC_QUANT[yAcIndex.withDelta(uvDcDelta, max = 117)],
        uvAc = VP8_AC_QUANT[yAcIndex.withDelta(uvAcDelta, max = 127)],
    )
}

private fun Int.withDelta(delta: Int, max: Int): Int = (this + delta).coerceIn(0, max)

private fun IntArray.leftSamples(
    stride: Int,
    blockX: Int,
    blockY: Int,
    blockHeight: Int,
): IntArray? {
    if (blockX == 0) return null
    return IntArray(blockHeight) { y -> this[(blockY + y) * stride + blockX - 1] }
}

private fun IntArray.topSamples(
    stride: Int,
    blockX: Int,
    blockY: Int,
    blockWidth: Int,
): IntArray? {
    if (blockY == 0) return null
    return IntArray(blockWidth) { x -> this[(blockY - 1) * stride + blockX + x] }
}

private fun IntArray.topLeftSample(stride: Int, blockX: Int, blockY: Int): Int? =
    if (blockX == 0 || blockY == 0) null else this[(blockY - 1) * stride + blockX - 1]

private fun IntArray.topRightSamples(
    stride: Int,
    blockX: Int,
    blockY: Int,
    blockWidth: Int,
): IntArray? {
    if (blockY == 0) return null
    val start = blockX + blockWidth
    if (start >= stride) return null
    val row = (blockY - 1) * stride
    return IntArray(VP8_BLOCK_SIZE) { x ->
        this[row + minOf(start + x, stride - 1)]
    }
}

private fun IntArray.copyBlock(
    stride: Int,
    blockX: Int,
    blockY: Int,
    blockWidth: Int,
    blockHeight: Int,
    block: IntArray,
) {
    require(block.size >= blockWidth * blockHeight)
    for (y in 0 until blockHeight) {
        for (x in 0 until blockWidth) {
            this[(blockY + y) * stride + blockX + x] = block[y * blockWidth + x]
        }
    }
}

private fun clearVp8CoefficientContexts(
    macroblockX: Int,
    blockColumns: Int,
    blockRows: Int,
    topNonZero: BooleanArray,
    leftNonZero: BooleanArray,
) {
    for (blockY in 0 until blockRows) leftNonZero[blockY] = false
    for (blockX in 0 until blockColumns) topNonZero[macroblockX * blockColumns + blockX] = false
}

internal fun dequantizeVp8CoefficientBlock(
    coefficients: IntArray,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    require(coefficients.size == VP8_BLOCK_COEFFICIENT_COUNT)
    require(dcQuant >= 0)
    require(acQuant >= 0)

    return IntArray(VP8_BLOCK_COEFFICIENT_COUNT) { index ->
        coefficients[index] * if (index == 0) dcQuant else acQuant
    }
}

internal fun reconstructVp8Intra4x4Block(
    mode: Vp8IntraPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    coefficients: IntArray,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    val residual = inverseVp8Dct4x4(
        dequantizeVp8CoefficientBlock(
            coefficients = coefficients,
            dcQuant = dcQuant,
            acQuant = acQuant,
        ),
    )
    return reconstructVp8IntraPlane(
        width = 4,
        height = 4,
        mode = mode,
        left = left,
        top = top,
        topLeft = topLeft,
        residual = residual,
    )
}

internal fun reconstructVp8BPredLumaMacroblock(
    lumaSubblockModes: List<Vp8LumaSubblockPredictionMode>,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    coefficientsByBlock: Array<IntArray>,
    dcQuant: Int,
    acQuant: Int,
    topRight: IntArray? = null,
): IntArray {
    require(lumaSubblockModes.size == VP8_LUMA_BLOCK_COUNT)
    require(left == null || left.size >= VP8_MACROBLOCK_SIZE)
    require(top == null || top.size >= VP8_MACROBLOCK_SIZE)
    require(topRight == null || topRight.size >= VP8_BLOCK_SIZE)
    require(coefficientsByBlock.size == VP8_LUMA_BLOCK_COUNT)

    val out = IntArray(VP8_MACROBLOCK_SIZE * VP8_MACROBLOCK_SIZE)
    for (blockY in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
        for (blockX in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
            val blockIndex = blockY * VP8_BLOCKS_PER_MACROBLOCK_SIDE + blockX
            val pixelX = blockX * VP8_BLOCK_SIZE
            val pixelY = blockY * VP8_BLOCK_SIZE
            val block = reconstructVp8LumaSubblock(
                mode = lumaSubblockModes[blockIndex],
                left = bPredLeftSamples(out, left, pixelX, pixelY),
                top = bPredTopSamples(out, top, topRight, pixelX, pixelY),
                topLeft = bPredTopLeftSample(out, left, top, topLeft, pixelX, pixelY),
                coefficients = coefficientsByBlock[blockIndex],
                dcQuant = dcQuant,
                acQuant = acQuant,
            )
            out.copyBlock(
                stride = VP8_MACROBLOCK_SIZE,
                blockX = pixelX,
                blockY = pixelY,
                blockWidth = VP8_BLOCK_SIZE,
                blockHeight = VP8_BLOCK_SIZE,
                block = block,
            )
        }
    }
    return out
}

internal fun reconstructVp8Intra16x16LumaMacroblock(
    mode: Vp8LumaPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    y2Coefficients: IntArray,
    coefficientsByBlock: Array<IntArray>,
    dcQuant: Int,
    acQuant: Int,
    y2DcQuant: Int,
    y2AcQuant: Int,
): IntArray {
    require(mode != Vp8LumaPredictionMode.B_PRED)
    require(y2Coefficients.size == VP8_BLOCK_COEFFICIENT_COUNT)
    require(coefficientsByBlock.size == VP8_LUMA_BLOCK_COUNT)
    require(left == null || left.size >= VP8_MACROBLOCK_SIZE)
    require(top == null || top.size >= VP8_MACROBLOCK_SIZE)

    val lumaDcCoefficients = inverseVp8WalshHadamard4x4(
        dequantizeVp8CoefficientBlock(
            coefficients = y2Coefficients,
            dcQuant = y2DcQuant,
            acQuant = y2AcQuant,
        ),
    )
    val residual = IntArray(VP8_MACROBLOCK_SIZE * VP8_MACROBLOCK_SIZE)
    for (blockY in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
        for (blockX in 0 until VP8_BLOCKS_PER_MACROBLOCK_SIDE) {
            val blockIndex = blockY * VP8_BLOCKS_PER_MACROBLOCK_SIDE + blockX
            val dequantizedBlock = dequantizeVp8CoefficientBlock(
                coefficients = coefficientsByBlock[blockIndex],
                dcQuant = dcQuant,
                acQuant = acQuant,
            )
            dequantizedBlock[0] = lumaDcCoefficients[blockIndex]
            val blockResidual = inverseVp8Dct4x4(
                dequantizedBlock,
            )
            for (y in 0 until VP8_BLOCK_SIZE) {
                for (x in 0 until VP8_BLOCK_SIZE) {
                    residual[(blockY * VP8_BLOCK_SIZE + y) * VP8_MACROBLOCK_SIZE + blockX * VP8_BLOCK_SIZE + x] =
                        blockResidual[y * VP8_BLOCK_SIZE + x]
                }
            }
        }
    }

    return reconstructVp8IntraPlane(
        width = VP8_MACROBLOCK_SIZE,
        height = VP8_MACROBLOCK_SIZE,
        mode = mode.toIntraPredictionMode(),
        left = left,
        top = top,
        topLeft = topLeft,
        residual = residual,
    )
}

internal fun reconstructVp8IntraChromaMacroblock(
    mode: Vp8IntraPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    coefficientsByBlock: Array<IntArray>,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    require(coefficientsByBlock.size == VP8_CHROMA_BLOCK_COUNT)
    require(left == null || left.size >= VP8_CHROMA_MACROBLOCK_SIZE)
    require(top == null || top.size >= VP8_CHROMA_MACROBLOCK_SIZE)

    val residual = IntArray(VP8_CHROMA_MACROBLOCK_SIZE * VP8_CHROMA_MACROBLOCK_SIZE)
    for (blockY in 0 until VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE) {
        for (blockX in 0 until VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE) {
            val blockIndex = blockY * VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE + blockX
            val blockResidual = inverseVp8Dct4x4(
                dequantizeVp8CoefficientBlock(
                    coefficients = coefficientsByBlock[blockIndex],
                    dcQuant = dcQuant,
                    acQuant = acQuant,
                ),
            )
            for (y in 0 until VP8_BLOCK_SIZE) {
                for (x in 0 until VP8_BLOCK_SIZE) {
                    residual[
                        (blockY * VP8_BLOCK_SIZE + y) * VP8_CHROMA_MACROBLOCK_SIZE +
                            blockX * VP8_BLOCK_SIZE + x
                    ] = blockResidual[y * VP8_BLOCK_SIZE + x]
                }
            }
        }
    }

    return reconstructVp8IntraPlane(
        width = VP8_CHROMA_MACROBLOCK_SIZE,
        height = VP8_CHROMA_MACROBLOCK_SIZE,
        mode = mode,
        left = left,
        top = top,
        topLeft = topLeft,
        residual = residual,
    )
}

private fun Vp8LumaPredictionMode.toIntraPredictionMode(): Vp8IntraPredictionMode =
    when (this) {
        Vp8LumaPredictionMode.DC -> Vp8IntraPredictionMode.DC
        Vp8LumaPredictionMode.VERTICAL -> Vp8IntraPredictionMode.VERTICAL
        Vp8LumaPredictionMode.HORIZONTAL -> Vp8IntraPredictionMode.HORIZONTAL
        Vp8LumaPredictionMode.TRUE_MOTION -> Vp8IntraPredictionMode.TRUE_MOTION
        Vp8LumaPredictionMode.B_PRED -> error("B_PRED uses per-4x4 luma modes")
    }

private fun readVp8CoefficientMagnitude(reader: Vp8BoolReader, probabilities: IntArray): Int? {
    if ((reader.readBit(probabilities[2]) ?: return null) == 0) return 1
    if ((reader.readBit(probabilities[3]) ?: return null) == 0) {
        if ((reader.readBit(probabilities[4]) ?: return null) == 0) return 2
        return if ((reader.readBit(probabilities[5]) ?: return null) == 0) 3 else 4
    }

    if ((reader.readBit(probabilities[6]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 5, VP8_COEFFICIENT_CATEGORY1_PROBS)
    }
    if ((reader.readBit(probabilities[7]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 7, VP8_COEFFICIENT_CATEGORY2_PROBS)
    }
    if ((reader.readBit(probabilities[8]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 11, VP8_COEFFICIENT_CATEGORY3_PROBS)
    }
    if ((reader.readBit(probabilities[9]) ?: return null) == 0) {
        return readVp8CoefficientCategory(reader, 19, VP8_COEFFICIENT_CATEGORY4_PROBS)
    }
    return if ((reader.readBit(probabilities[10]) ?: return null) == 0) {
        readVp8CoefficientCategory(reader, 35, VP8_COEFFICIENT_CATEGORY5_PROBS)
    } else {
        readVp8CoefficientCategory(reader, 67, VP8_COEFFICIENT_CATEGORY6_PROBS)
    }
}

private fun readVp8CoefficientCategory(reader: Vp8BoolReader, base: Int, probabilities: IntArray): Int? {
    var value = base
    for (probability in probabilities) {
        value = (value shl 1) + (reader.readBit(probability) ?: return null)
    }
    return value - (base shl probabilities.size) + base
}

private const val VP8_BLOCK_COEFFICIENT_COUNT: Int = 16
private const val VP8_COEFFICIENT_TYPE_COUNT: Int = 4
private const val VP8_COEFFICIENT_BAND_COUNT: Int = 8
private const val VP8_COEFFICIENT_CONTEXT_COUNT: Int = 3
private const val VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT: Int = 11
private const val VP8_COEFFICIENT_PROBABILITY_TOTAL: Int =
    VP8_COEFFICIENT_TYPE_COUNT *
        VP8_COEFFICIENT_BAND_COUNT *
        VP8_COEFFICIENT_CONTEXT_COUNT *
        VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT
private const val VP8_BLOCK_SIZE: Int = 4
private const val VP8_BLOCKS_PER_MACROBLOCK_SIDE: Int = 4
private const val VP8_LUMA_BLOCK_COUNT: Int = 16
private const val VP8_CHROMA_BLOCKS_PER_MACROBLOCK_SIDE: Int = 2
private const val VP8_CHROMA_BLOCK_COUNT: Int = 4
private const val VP8_MACROBLOCK_SIZE: Int = 16
private const val VP8_CHROMA_MACROBLOCK_SIZE: Int = 8
private const val VP8_COEFFICIENT_TYPE_LUMA: Int = 0
private const val VP8_COEFFICIENT_TYPE_LUMA_Y2: Int = 1
private const val VP8_COEFFICIENT_TYPE_CHROMA: Int = 2
private const val VP8_COEFFICIENT_TYPE_LUMA_AC: Int = 3
private const val VP8_COEFFICIENT_CONTEXT_ZERO: Int = 1
private const val VP8_COEFFICIENT_CONTEXT_NON_ZERO: Int = 2

private fun vp8CoefficientProbabilityIndex(type: Int, band: Int, context: Int, probability: Int): Int =
    (((type * VP8_COEFFICIENT_BAND_COUNT + band) * VP8_COEFFICIENT_CONTEXT_COUNT + context) *
        VP8_COEFFICIENT_TOKEN_PROBABILITY_COUNT) + probability

private val VP8_ZIGZAG = intArrayOf(
    0, 1, 4, 8,
    5, 2, 3, 6,
    9, 12, 13, 10,
    7, 11, 14, 15,
)

internal val VP8_COEFFICIENT_BANDS = intArrayOf(
    // Values from libvpx entropy.c vp8_coef_bands.
    0, 1, 2, 3,
    6, 4, 5, 6,
    6, 6, 6, 6,
    6, 6, 6, 7,
)

private val VP8_DEFAULT_COEFFICIENT_PROBABILITIES = intArrayOf(
    // Values from libvpx default_coef_probs.h.
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    253, 136, 254, 255, 228, 219, 128, 128, 128, 128, 128,
    189, 129, 242, 255, 227, 213, 255, 219, 128, 128, 128,
    106, 126, 227, 252, 214, 209, 255, 255, 128, 128, 128,
    1, 98, 248, 255, 236, 226, 255, 255, 128, 128, 128,
    181, 133, 238, 254, 221, 234, 255, 154, 128, 128, 128,
    78, 134, 202, 247, 198, 180, 255, 219, 128, 128, 128,
    1, 185, 249, 255, 243, 255, 128, 128, 128, 128, 128,
    184, 150, 247, 255, 236, 224, 128, 128, 128, 128, 128,
    77, 110, 216, 255, 236, 230, 128, 128, 128, 128, 128,
    1, 101, 251, 255, 241, 255, 128, 128, 128, 128, 128,
    170, 139, 241, 252, 236, 209, 255, 255, 128, 128, 128,
    37, 116, 196, 243, 228, 255, 255, 255, 128, 128, 128,
    1, 204, 254, 255, 245, 255, 128, 128, 128, 128, 128,
    207, 160, 250, 255, 238, 128, 128, 128, 128, 128, 128,
    102, 103, 231, 255, 211, 171, 128, 128, 128, 128, 128,
    1, 152, 252, 255, 240, 255, 128, 128, 128, 128, 128,
    177, 135, 243, 255, 234, 225, 128, 128, 128, 128, 128,
    80, 129, 211, 255, 194, 224, 128, 128, 128, 128, 128,
    1, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    246, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    255, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    198, 35, 237, 223, 193, 187, 162, 160, 145, 155, 62,
    131, 45, 198, 221, 172, 176, 220, 157, 252, 221, 1,
    68, 47, 146, 208, 149, 167, 221, 162, 255, 223, 128,
    1, 149, 241, 255, 221, 224, 255, 255, 128, 128, 128,
    184, 141, 234, 253, 222, 220, 255, 199, 128, 128, 128,
    81, 99, 181, 242, 176, 190, 249, 202, 255, 255, 128,
    1, 129, 232, 253, 214, 197, 242, 196, 255, 255, 128,
    99, 121, 210, 250, 201, 198, 255, 202, 128, 128, 128,
    23, 91, 163, 242, 170, 187, 247, 210, 255, 255, 128,
    1, 200, 246, 255, 234, 255, 128, 128, 128, 128, 128,
    109, 178, 241, 255, 231, 245, 255, 255, 128, 128, 128,
    44, 130, 201, 253, 205, 192, 255, 255, 128, 128, 128,
    1, 132, 239, 251, 219, 209, 255, 165, 128, 128, 128,
    94, 136, 225, 251, 218, 190, 255, 255, 128, 128, 128,
    22, 100, 174, 245, 186, 161, 255, 199, 128, 128, 128,
    1, 182, 249, 255, 232, 235, 128, 128, 128, 128, 128,
    124, 143, 241, 255, 227, 234, 128, 128, 128, 128, 128,
    35, 77, 181, 251, 193, 211, 255, 205, 128, 128, 128,
    1, 157, 247, 255, 236, 231, 255, 255, 128, 128, 128,
    121, 141, 235, 255, 225, 227, 255, 255, 128, 128, 128,
    45, 99, 188, 251, 195, 217, 255, 224, 128, 128, 128,
    1, 1, 251, 255, 213, 255, 128, 128, 128, 128, 128,
    203, 1, 248, 255, 255, 128, 128, 128, 128, 128, 128,
    137, 1, 177, 255, 224, 255, 128, 128, 128, 128, 128,
    253, 9, 248, 251, 207, 208, 255, 192, 128, 128, 128,
    175, 13, 224, 243, 193, 185, 249, 198, 255, 255, 128,
    73, 17, 171, 221, 161, 179, 236, 167, 255, 234, 128,
    1, 95, 247, 253, 212, 183, 255, 255, 128, 128, 128,
    239, 90, 244, 250, 211, 209, 255, 255, 128, 128, 128,
    155, 77, 195, 248, 188, 195, 255, 255, 128, 128, 128,
    1, 24, 239, 251, 218, 219, 255, 205, 128, 128, 128,
    201, 51, 219, 255, 196, 186, 128, 128, 128, 128, 128,
    69, 46, 190, 239, 201, 218, 255, 228, 128, 128, 128,
    1, 191, 251, 255, 255, 128, 128, 128, 128, 128, 128,
    223, 165, 249, 255, 213, 255, 128, 128, 128, 128, 128,
    141, 124, 248, 255, 255, 128, 128, 128, 128, 128, 128,
    1, 16, 248, 255, 255, 128, 128, 128, 128, 128, 128,
    190, 36, 230, 255, 236, 255, 128, 128, 128, 128, 128,
    149, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    1, 226, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    247, 192, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    240, 128, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    1, 134, 252, 255, 255, 128, 128, 128, 128, 128, 128,
    213, 62, 250, 255, 255, 128, 128, 128, 128, 128, 128,
    55, 93, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128,
    202, 24, 213, 235, 186, 191, 220, 160, 240, 175, 255,
    126, 38, 182, 232, 169, 184, 228, 174, 255, 187, 128,
    61, 46, 138, 219, 151, 178, 240, 170, 255, 216, 128,
    1, 112, 230, 250, 199, 191, 247, 159, 255, 255, 128,
    166, 109, 228, 252, 211, 215, 255, 174, 128, 128, 128,
    39, 77, 162, 232, 172, 180, 245, 178, 255, 255, 128,
    1, 52, 220, 246, 198, 199, 249, 220, 255, 255, 128,
    124, 74, 191, 243, 183, 193, 250, 221, 255, 255, 128,
    24, 71, 130, 219, 154, 170, 243, 182, 255, 255, 128,
    1, 182, 225, 249, 219, 240, 255, 224, 128, 128, 128,
    149, 150, 226, 252, 216, 205, 255, 171, 128, 128, 128,
    28, 108, 170, 242, 183, 194, 254, 223, 255, 255, 128,
    1, 81, 230, 252, 204, 203, 255, 192, 128, 128, 128,
    123, 102, 209, 247, 188, 196, 255, 233, 128, 128, 128,
    20, 95, 153, 243, 164, 173, 255, 203, 128, 128, 128,
    1, 222, 248, 255, 216, 213, 128, 128, 128, 128, 128,
    168, 175, 246, 252, 235, 205, 255, 255, 128, 128, 128,
    47, 116, 215, 255, 211, 212, 255, 255, 128, 128, 128,
    1, 121, 236, 253, 212, 214, 255, 255, 128, 128, 128,
    141, 84, 213, 252, 201, 202, 255, 219, 128, 128, 128,
    42, 80, 160, 240, 162, 185, 255, 205, 128, 128, 128,
    1, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    244, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128,
    238, 1, 255, 128, 128, 128, 128, 128, 128, 128, 128
)

private val VP8_COEFFICIENT_UPDATE_PROBABILITIES = intArrayOf(
    // Values from libvpx coefupdateprobs.h.
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    176, 246, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    223, 241, 252, 255, 255, 255, 255, 255, 255, 255, 255,
    249, 253, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 244, 252, 255, 255, 255, 255, 255, 255, 255, 255,
    234, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    253, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 246, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    239, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 248, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    251, 255, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    251, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 254, 253, 255, 254, 255, 255, 255, 255, 255, 255,
    250, 255, 254, 255, 254, 255, 255, 255, 255, 255, 255,
    254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    217, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    225, 252, 241, 253, 255, 255, 254, 255, 255, 255, 255,
    234, 250, 241, 250, 253, 255, 253, 254, 255, 255, 255,
    255, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    223, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    238, 253, 254, 254, 255, 255, 255, 255, 255, 255, 255,
    255, 248, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    249, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 253, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    247, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    253, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 254, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    250, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    186, 251, 250, 255, 255, 255, 255, 255, 255, 255, 255,
    234, 251, 244, 254, 255, 255, 255, 255, 255, 255, 255,
    251, 251, 243, 253, 254, 255, 254, 255, 255, 255, 255,
    255, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    236, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    251, 253, 253, 254, 254, 255, 255, 255, 255, 255, 255,
    255, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 254, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    248, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    250, 254, 252, 254, 255, 255, 255, 255, 255, 255, 255,
    248, 254, 249, 253, 255, 255, 255, 255, 255, 255, 255,
    255, 253, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    246, 253, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    252, 254, 251, 254, 254, 255, 255, 255, 255, 255, 255,
    255, 254, 252, 255, 255, 255, 255, 255, 255, 255, 255,
    248, 254, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    253, 255, 254, 254, 255, 255, 255, 255, 255, 255, 255,
    255, 251, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    245, 251, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    253, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 251, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    252, 253, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 254, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    249, 255, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 254, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 253, 255, 255, 255, 255, 255, 255, 255, 255,
    250, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
    255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255
)

private val VP8_DC_QUANT = intArrayOf(
    4, 5, 6, 7, 8, 9, 10, 10,
    11, 12, 13, 14, 15, 16, 17, 17,
    18, 19, 20, 20, 21, 21, 22, 22,
    23, 23, 24, 25, 25, 26, 27, 28,
    29, 30, 31, 32, 33, 34, 35, 36,
    37, 37, 38, 39, 40, 41, 42, 43,
    44, 45, 46, 46, 47, 48, 49, 50,
    51, 52, 53, 54, 55, 56, 57, 58,
    59, 60, 61, 62, 63, 64, 65, 66,
    67, 68, 69, 70, 71, 72, 73, 74,
    75, 76, 76, 77, 78, 79, 80, 81,
    82, 83, 84, 85, 86, 87, 88, 89,
    91, 93, 95, 96, 98, 100, 101, 102,
    104, 106, 108, 110, 112, 114, 116, 118,
    122, 124, 126, 128, 130, 132, 134, 136,
    138, 140, 143, 145, 148, 151, 154, 157,
)

private val VP8_AC_QUANT = intArrayOf(
    4, 5, 6, 7, 8, 9, 10, 11,
    12, 13, 14, 15, 16, 17, 18, 19,
    20, 21, 22, 23, 24, 25, 26, 27,
    28, 29, 30, 31, 32, 33, 34, 35,
    36, 37, 38, 39, 40, 41, 42, 43,
    44, 45, 46, 47, 48, 49, 50, 51,
    52, 53, 54, 55, 56, 57, 58, 60,
    62, 64, 66, 68, 70, 72, 74, 76,
    78, 80, 82, 84, 86, 88, 90, 92,
    94, 96, 98, 100, 102, 104, 106, 108,
    110, 112, 114, 116, 119, 122, 125, 128,
    131, 134, 137, 140, 143, 146, 149, 152,
    155, 158, 161, 164, 167, 170, 173, 177,
    181, 185, 189, 193, 197, 201, 205, 209,
    213, 217, 221, 225, 229, 234, 239, 245,
    249, 254, 259, 264, 269, 274, 279, 284,
)

private val VP8_COEFFICIENT_CATEGORY1_PROBS = intArrayOf(159)
private val VP8_COEFFICIENT_CATEGORY2_PROBS = intArrayOf(165, 145)
private val VP8_COEFFICIENT_CATEGORY3_PROBS = intArrayOf(173, 148, 140)
private val VP8_COEFFICIENT_CATEGORY4_PROBS = intArrayOf(176, 155, 140, 135)
private val VP8_COEFFICIENT_CATEGORY5_PROBS = intArrayOf(180, 157, 141, 134, 130)
private val VP8_COEFFICIENT_CATEGORY6_PROBS = intArrayOf(254, 254, 243, 230, 196, 177, 153, 140, 133, 130, 129)

internal enum class Vp8IntraPredictionMode {
    DC,
    VERTICAL,
    HORIZONTAL,
    TRUE_MOTION,
}

internal fun reconstructVp8IntraPlane(
    width: Int,
    height: Int,
    mode: Vp8IntraPredictionMode,
    left: IntArray?,
    top: IntArray?,
    topLeft: Int?,
    residual: IntArray = IntArray(width * height),
): IntArray {
    require(width > 0)
    require(height > 0)
    require(left == null || left.size >= height)
    require(top == null || top.size >= width)
    require(residual.size == width * height)

    val dcPrediction = if (mode == Vp8IntraPredictionMode.DC) dcPrediction(width, height, left, top) else 0
    val out = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val predicted = when (mode) {
                Vp8IntraPredictionMode.DC -> dcPrediction
                Vp8IntraPredictionMode.VERTICAL -> top?.get(x) ?: 127
                Vp8IntraPredictionMode.HORIZONTAL -> left?.get(y) ?: 129
                Vp8IntraPredictionMode.TRUE_MOTION -> {
                    val base = topLeft ?: 128
                    clip8((left?.get(y) ?: 129) + (top?.get(x) ?: 127) - base)
                }
            }
            val index = y * width + x
            out[index] = clip8(predicted + residual[index])
        }
    }
    return out
}

private fun dcPrediction(width: Int, height: Int, left: IntArray?, top: IntArray?): Int {
    return when {
        left != null && top != null -> {
            val sum = left.sumPrefix(height) + top.sumPrefix(width)
            (sum + ((width + height) / 2)) / (width + height)
        }
        left != null -> (left.sumPrefix(height) + (height / 2)) / height
        top != null -> (top.sumPrefix(width) + (width / 2)) / width
        else -> 128
    }
}

private fun IntArray.sumPrefix(count: Int): Int {
    var sum = 0
    for (i in 0 until count) sum += this[i]
    return sum
}

internal fun composeVp8Yuv420ToRgba(
    yPlane: IntArray,
    uPlane: IntArray,
    vPlane: IntArray,
    width: Int,
    height: Int,
): IntArray {
    require(width > 0)
    require(height > 0)
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    require(yPlane.size >= width * height)
    require(uPlane.size >= chromaWidth * chromaHeight)
    require(vPlane.size >= chromaWidth * chromaHeight)

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val luma = yPlane[y * width + x]
            val chromaIndex = (y / 2) * chromaWidth + (x / 2)
            val cb = uPlane[chromaIndex] - 128
            val cr = vPlane[chromaIndex] - 128
            val red = clip8(luma + ((91881 * cr) shr 16))
            val green = clip8(luma - ((22554 * cb + 46802 * cr) shr 16))
            val blue = clip8(luma + ((116130 * cb) shr 16))
            pixels[y * width + x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    return pixels
}

internal fun reconstructVp8LumaSubblock(
    mode: Vp8LumaSubblockPredictionMode,
    left: IntArray,
    top: IntArray,
    topLeft: Int,
    coefficients: IntArray,
    dcQuant: Int,
    acQuant: Int,
): IntArray {
    require(left.size >= VP8_BLOCK_SIZE)
    require(top.size >= VP8_BLOCK_SIZE * 2)
    require(topLeft in 0..255)
    require(coefficients.size == VP8_BLOCK_COEFFICIENT_COUNT)

    val predicted = predictVp8LumaSubblock(mode, left, top, topLeft)
    val residual = inverseVp8Dct4x4(
        dequantizeVp8CoefficientBlock(
            coefficients = coefficients,
            dcQuant = dcQuant,
            acQuant = acQuant,
        ),
    )
    return IntArray(VP8_BLOCK_SIZE * VP8_BLOCK_SIZE) { index ->
        clip8(predicted[index] + residual[index])
    }
}

private fun predictVp8LumaSubblock(
    mode: Vp8LumaSubblockPredictionMode,
    left: IntArray,
    top: IntArray,
    topLeft: Int,
): IntArray {
    val pred = IntArray(VP8_BLOCK_SIZE * VP8_BLOCK_SIZE)
    fun set(x: Int, y: Int, value: Int) {
        pred[y * VP8_BLOCK_SIZE + x] = value
    }

    when (mode) {
        Vp8LumaSubblockPredictionMode.B_DC -> {
            val dc = (left[0] + left[1] + left[2] + left[3] + top[0] + top[1] + top[2] + top[3] + 4) shr 3
            for (i in pred.indices) pred[i] = dc
        }
        Vp8LumaSubblockPredictionMode.B_TM -> {
            for (y in 0 until VP8_BLOCK_SIZE) {
                for (x in 0 until VP8_BLOCK_SIZE) set(x, y, clip8(left[y] + top[x] - topLeft))
            }
        }
        Vp8LumaSubblockPredictionMode.B_VE -> {
            val values = intArrayOf(
                avg3(topLeft, top[0], top[1]),
                avg3(top[0], top[1], top[2]),
                avg3(top[1], top[2], top[3]),
                avg3(top[2], top[3], top[4]),
            )
            for (y in 0 until VP8_BLOCK_SIZE) for (x in 0 until VP8_BLOCK_SIZE) set(x, y, values[x])
        }
        Vp8LumaSubblockPredictionMode.B_HE -> {
            val values = intArrayOf(
                avg3(topLeft, left[0], left[1]),
                avg3(left[0], left[1], left[2]),
                avg3(left[1], left[2], left[3]),
                avg3(left[2], left[3], left[3]),
            )
            for (y in 0 until VP8_BLOCK_SIZE) for (x in 0 until VP8_BLOCK_SIZE) set(x, y, values[y])
        }
        Vp8LumaSubblockPredictionMode.B_LD -> {
            val p0 = avg3(top[0], top[1], top[2])
            val p1 = avg3(top[1], top[2], top[3])
            val p2 = avg3(top[2], top[3], top[4])
            val p3 = avg3(top[3], top[4], top[5])
            val p4 = avg3(top[4], top[5], top[6])
            val p5 = avg3(top[5], top[6], top[7])
            val p6 = avg3(top[6], top[7], top[7])
            val values = intArrayOf(p0, p1, p2, p3, p1, p2, p3, p4, p2, p3, p4, p5, p3, p4, p5, p6)
            for (i in values.indices) pred[i] = values[i]
        }
        Vp8LumaSubblockPredictionMode.B_RD -> {
            val p0 = avg3(left[0], topLeft, top[0])
            val p1 = avg3(topLeft, top[0], top[1])
            val p2 = avg3(top[0], top[1], top[2])
            val p3 = avg3(top[1], top[2], top[3])
            val p4 = avg3(left[1], left[0], topLeft)
            val p5 = avg3(left[2], left[1], left[0])
            val p6 = avg3(left[3], left[2], left[1])
            val values = intArrayOf(p0, p1, p2, p3, p4, p0, p1, p2, p5, p4, p0, p1, p6, p5, p4, p0)
            for (i in values.indices) pred[i] = values[i]
        }
        Vp8LumaSubblockPredictionMode.B_VR -> {
            val p0 = avg2(topLeft, top[0])
            val p1 = avg2(top[0], top[1])
            val p2 = avg2(top[1], top[2])
            val p3 = avg2(top[2], top[3])
            val p4 = avg3(left[0], topLeft, top[0])
            val p5 = avg3(topLeft, top[0], top[1])
            val p6 = avg3(top[0], top[1], top[2])
            val p7 = avg3(top[1], top[2], top[3])
            val p8 = avg3(left[1], left[0], topLeft)
            val p9 = avg3(left[2], left[1], left[0])
            val values = intArrayOf(p0, p1, p2, p3, p4, p5, p6, p7, p8, p0, p1, p2, p9, p4, p5, p6)
            for (i in values.indices) pred[i] = values[i]
        }
        Vp8LumaSubblockPredictionMode.B_VL -> {
            val p0 = avg2(top[0], top[1])
            val p1 = avg2(top[1], top[2])
            val p2 = avg2(top[2], top[3])
            val p3 = avg2(top[3], top[4])
            val p4 = avg3(top[0], top[1], top[2])
            val p5 = avg3(top[1], top[2], top[3])
            val p6 = avg3(top[2], top[3], top[4])
            val p7 = avg3(top[3], top[4], top[5])
            val p8 = avg3(top[4], top[5], top[6])
            val p9 = avg3(top[5], top[6], top[7])
            val values = intArrayOf(p0, p1, p2, p3, p4, p5, p6, p7, p1, p2, p3, p8, p5, p6, p7, p9)
            for (i in values.indices) pred[i] = values[i]
        }
        Vp8LumaSubblockPredictionMode.B_HD -> {
            val p0 = avg2(left[0], topLeft)
            val p1 = avg3(left[0], topLeft, top[0])
            val p2 = avg3(topLeft, top[0], top[1])
            val p3 = avg3(top[0], top[1], top[2])
            val p4 = avg2(left[1], left[0])
            val p5 = avg3(left[1], left[0], topLeft)
            val p6 = avg2(left[2], left[1])
            val p7 = avg3(left[2], left[1], left[0])
            val p8 = avg2(left[3], left[2])
            val p9 = avg3(left[3], left[2], left[1])
            val values = intArrayOf(p0, p1, p2, p3, p4, p5, p0, p1, p6, p7, p4, p5, p8, p9, p6, p7)
            for (i in values.indices) pred[i] = values[i]
        }
        Vp8LumaSubblockPredictionMode.B_HU -> {
            val p0 = avg2(left[0], left[1])
            val p1 = avg3(left[0], left[1], left[2])
            val p2 = avg2(left[1], left[2])
            val p3 = avg3(left[1], left[2], left[3])
            val p4 = avg2(left[2], left[3])
            val p5 = avg3(left[2], left[3], left[3])
            val p6 = left[3]
            val values = intArrayOf(p0, p1, p2, p3, p2, p3, p4, p5, p4, p5, p6, p6, p6, p6, p6, p6)
            for (i in values.indices) pred[i] = values[i]
        }
    }
    return pred
}

private fun bPredLeftSamples(
    macroblock: IntArray,
    externalLeft: IntArray?,
    pixelX: Int,
    pixelY: Int,
): IntArray = IntArray(VP8_BLOCK_SIZE) { y ->
    when {
        pixelX > 0 -> macroblock[(pixelY + y) * VP8_MACROBLOCK_SIZE + pixelX - 1]
        externalLeft != null -> externalLeft[pixelY + y]
        else -> 129
    }
}

private fun bPredTopSamples(
    macroblock: IntArray,
    externalTop: IntArray?,
    externalTopRight: IntArray?,
    pixelX: Int,
    pixelY: Int,
): IntArray {
    val samples = IntArray(VP8_BLOCK_SIZE * 2)
    for (x in samples.indices) {
        val sourceX = pixelX + x
        samples[x] = when {
            pixelY > 0 && sourceX < VP8_MACROBLOCK_SIZE -> macroblock[(pixelY - 1) * VP8_MACROBLOCK_SIZE + sourceX]
            pixelY > 0 && externalTopRight != null -> externalTopRight[sourceX - VP8_MACROBLOCK_SIZE]
            pixelY > 0 -> samples[x - 1]
            externalTop != null && sourceX < VP8_MACROBLOCK_SIZE -> externalTop[sourceX]
            externalTopRight != null -> externalTopRight[sourceX - VP8_MACROBLOCK_SIZE]
            externalTop != null -> externalTop[VP8_MACROBLOCK_SIZE - 1]
            else -> 127
        }
    }
    return samples
}

private fun bPredTopLeftSample(
    macroblock: IntArray,
    externalLeft: IntArray?,
    externalTop: IntArray?,
    externalTopLeft: Int?,
    pixelX: Int,
    pixelY: Int,
): Int = when {
    pixelX > 0 && pixelY > 0 -> macroblock[(pixelY - 1) * VP8_MACROBLOCK_SIZE + pixelX - 1]
    pixelX > 0 -> externalTop?.get(pixelX - 1) ?: 127
    pixelY > 0 -> externalLeft?.get(pixelY - 1) ?: 129
    else -> externalTopLeft ?: 127
}

private fun avg2(a: Int, b: Int): Int = (a + b + 1) shr 1

private fun avg3(a: Int, b: Int, c: Int): Int = (a + 2 * b + c + 2) shr 2

internal data class Vp8SimpleLoopFilterSample(
    val p0: Int,
    val q0: Int,
    val filtered: Boolean,
)

internal fun filterVp8SimpleLoopFilterSample(
    p1: Int,
    p0: Int,
    q0: Int,
    q1: Int,
    limit: Int,
): Vp8SimpleLoopFilterSample {
    require(p1 in 0..255)
    require(p0 in 0..255)
    require(q0 in 0..255)
    require(q1 in 0..255)
    require(limit in 0..255)

    if ((4 * kotlin.math.abs(p0 - q0) + kotlin.math.abs(p1 - q1)) > (2 * limit + 1)) {
        return Vp8SimpleLoopFilterSample(p0 = p0, q0 = q0, filtered = false)
    }

    val adjustment = 3 * (q0 - p0) + clipSigned8(p1 - q1)
    return Vp8SimpleLoopFilterSample(
        p0 = clip8(p0 + clipSigned4((adjustment + 4) shr 3)),
        q0 = clip8(q0 - clipSigned4((adjustment + 3) shr 3)),
        filtered = true,
    )
}

internal fun applyVp8SimpleVerticalLoopFilter(
    plane: IntArray,
    width: Int,
    height: Int,
    edgeX: Int,
    limit: Int,
): IntArray {
    require(width > 3)
    require(height > 0)
    require(plane.size >= width * height)
    require(edgeX in 2..(width - 2))

    val filtered = plane.copyOf()
    for (y in 0 until height) {
        val row = y * width
        val sample = filterVp8SimpleLoopFilterSample(
            p1 = plane[row + edgeX - 2],
            p0 = plane[row + edgeX - 1],
            q0 = plane[row + edgeX],
            q1 = plane[row + edgeX + 1],
            limit = limit,
        )
        filtered[row + edgeX - 1] = sample.p0
        filtered[row + edgeX] = sample.q0
    }
    return filtered
}

internal fun applyVp8SimpleHorizontalLoopFilter(
    plane: IntArray,
    width: Int,
    height: Int,
    edgeY: Int,
    limit: Int,
): IntArray {
    require(width > 0)
    require(height > 3)
    require(plane.size >= width * height)
    require(edgeY in 2..(height - 2))

    val filtered = plane.copyOf()
    for (x in 0 until width) {
        val sample = filterVp8SimpleLoopFilterSample(
            p1 = plane[(edgeY - 2) * width + x],
            p0 = plane[(edgeY - 1) * width + x],
            q0 = plane[edgeY * width + x],
            q1 = plane[(edgeY + 1) * width + x],
            limit = limit,
        )
        filtered[(edgeY - 1) * width + x] = sample.p0
        filtered[edgeY * width + x] = sample.q0
    }
    return filtered
}

internal fun applyVp8SimpleLoopFilterIfNeeded(
    planes: Vp8ReconstructedPlanes,
    loopFilter: Vp8LoopFilterHeader,
): Vp8ReconstructedPlanes {
    if (!loopFilter.simpleFilter || loopFilter.level == 0) return planes

    val limits = deriveVp8LoopFilterLimits(loopFilter)
    var yPlane = planes.yPlane
    for (edgeX in VP8_BLOCK_SIZE until planes.width step VP8_BLOCK_SIZE) {
        if (edgeX in 2..(planes.width - 2)) {
            yPlane = applyVp8SimpleVerticalLoopFilter(
                plane = yPlane,
                width = planes.width,
                height = planes.height,
                edgeX = edgeX,
                limit = limits.forEdge(edgeX),
            )
        }
    }
    for (edgeY in VP8_BLOCK_SIZE until planes.height step VP8_BLOCK_SIZE) {
        if (edgeY in 2..(planes.height - 2)) {
            yPlane = applyVp8SimpleHorizontalLoopFilter(
                plane = yPlane,
                width = planes.width,
                height = planes.height,
                edgeY = edgeY,
                limit = limits.forEdge(edgeY),
            )
        }
    }

    return planes.copy(yPlane = yPlane)
}

internal data class Vp8LoopFilterLimits(
    val macroblockEdge: Int,
    val subblockEdge: Int,
) {
    fun forEdge(edge: Int): Int =
        if (edge % VP8_MACROBLOCK_SIZE == 0) macroblockEdge else subblockEdge
}

internal fun deriveVp8LoopFilterLimits(loopFilter: Vp8LoopFilterHeader): Vp8LoopFilterLimits {
    var interiorLimit = loopFilter.level
    if (loopFilter.sharpness != 0) {
        interiorLimit = interiorLimit shr if (loopFilter.sharpness > 4) 2 else 1
        interiorLimit = interiorLimit.coerceAtMost(9 - loopFilter.sharpness)
    }
    if (interiorLimit == 0) interiorLimit = 1

    return Vp8LoopFilterLimits(
        macroblockEdge = (((loopFilter.level + 2) * 2) + interiorLimit).coerceIn(0, 255),
        subblockEdge = ((loopFilter.level * 2) + interiorLimit).coerceIn(0, 255),
    )
}

private fun clip8(value: Int): Int = value.coerceIn(0, 255)

private fun clipSigned8(value: Int): Int = value.coerceIn(-128, 127)

private fun clipSigned4(value: Int): Int = value.coerceIn(-16, 15)
