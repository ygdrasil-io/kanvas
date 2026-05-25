package org.skia.codec.webp

import org.graphiks.math.SkIRect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader

class SkWebpKotlinCodecTest {
    private companion object {
        const val VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST: Int = 4 * 8 * 3 * 11

        fun vp8CoefficientProbabilityIndexForTest(type: Int, band: Int, context: Int, probability: Int): Int =
            (((type * 8 + band) * 3 + context) * 11) + probability

        fun coefficientBandForTest(coefficient: Int): Int =
            intArrayOf(0, 1, 2, 3, 6, 4, 5, 6, 6, 6, 6, 6, 6, 6, 6, 7)[coefficient]
    }

    @Test
    fun `sniffs RIFF WEBP signature only`() {
        assertFalse(SkWebpKotlinCodec.Decoder.matches(ByteArray(0)))
        assertFalse(SkWebpKotlinCodec.Decoder.matches("not-a-webp".toByteArray()))
        assertFalse(SkWebpKotlinCodec.Decoder.matches(riff("WAVE", chunk("fmt ", byteArrayOf(1, 2, 3, 4)))))
        assertTrue(SkWebpKotlinCodec.Decoder.matches(vp8xWebp(width = 1, height = 1, flags = 0)))
    }

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "webp" })
    }

    @Test
    fun `parses VP8X dimensions and flags`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 321, height = 123, flags = 0x3E),
                animChunk(background = 0, loopCount = 0),
                anmfChunk(
                    x = 0,
                    y = 0,
                    width = 1,
                    height = 1,
                    durationMs = 1,
                    flags = 0,
                    frameChunks = arrayOf(vp8lChunk(1, 1)),
                ),
            ),
        )

        assertNotNull(codec)
        assertTrue(codec is SkWebpKotlinCodec)
        codec as SkWebpKotlinCodec
        assertEquals(SkEncodedImageFormat.kWEBP, codec.getEncodedFormat())
        assertEquals(321, codec.getInfo().width)
        assertEquals(123, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())
        assertTrue(codec.metadata.flags.icc)
        assertTrue(codec.metadata.flags.alpha)
        assertTrue(codec.metadata.flags.exif)
        assertTrue(codec.metadata.flags.xmp)
        assertTrue(codec.metadata.flags.animation)
        assertEquals(0x3E, codec.metadata.flags.raw)
    }

    @Test
    fun `extracts VP8X ICC profile chunk`() {
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 2, height = 1, flags = 0x20),
                chunk("ICCP", iccBytes),
                vp8lChunk(width = 2, height = 1),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        val checkedCodec = codec!!
        assertEquals(WebpBitstreamFormat.VP8L, checkedCodec.metadata.format)
        assertTrue(checkedCodec.metadata.flags.icc)
        val profile = checkedCodec.getICCProfile()
        assertNotNull(profile)
        assertEquals(iccBytes.size, profile!!.size)
        assertTrue(profile.hasTrc)
        assertTrue(profile.hasToXYZD50)
    }

    @Test
    fun `ignores invalid VP8X ICC profile bytes without rejecting metadata`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 2, height = 1, flags = 0x20),
                chunk("ICCP", byteArrayOf(1, 2, 3, 4)),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertTrue(codec!!.metadata.flags.icc)
        assertNull(codec.getICCProfile())
        assertEquals(2, codec.getInfo().width)
        assertEquals(1, codec.getInfo().height)
    }

    @Test
    fun `extracts VP8X EXIF and XMP metadata chunks`() {
        val exif = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,
        )
        val xmp = "<x:xmpmeta><rdf:RDF/></x:xmpmeta>".toByteArray(Charsets.UTF_8)
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 2, height = 1, flags = 0x0C),
                chunk("EXIF", exif),
                chunk("XMP ", xmp),
                vp8lChunk(width = 2, height = 1),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        val metadata = codec!!.metadata
        assertEquals(WebpBitstreamFormat.VP8L, metadata.format)
        assertTrue(metadata.flags.exif)
        assertTrue(metadata.flags.xmp)
        assertArrayEquals(exif, metadata.exifData)
        assertArrayEquals(xmp, metadata.xmpData)
    }

    @Test
    fun `parses VP8X alpha chunk for VP8 bitstream`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 2, height = 2, flags = 0x10),
                alphaChunk(
                    control = alphaControl(
                        compression = WebpAlphaCompression.NONE,
                        filtering = 1,
                        preprocessing = 2,
                    ),
                    payload = byteArrayOf(0x00, 0x40, 0x80.toByte(), 0xFF.toByte()),
                ),
                vp8Chunk(width = 2, height = 2),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        val metadata = codec!!.metadata
        assertEquals(WebpBitstreamFormat.VP8, metadata.format)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(metadata.flags.alpha)
        assertNotNull(metadata.alphaChunk)
        val alpha = metadata.alphaChunk!!
        assertEquals(WebpAlphaCompression.NONE, alpha.compression)
        assertEquals(1, alpha.filtering)
        assertEquals(2, alpha.preprocessing)
        assertEquals(4, alpha.payloadSize)

        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )
        assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `parses animated WebP frame metadata`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 4, height = 2, flags = 0x12),
                animChunk(background = argb(0x40, 10, 20, 30), loopCount = 7),
                anmfChunk(
                    x = 0,
                    y = 0,
                    width = 2,
                    height = 2,
                    durationMs = 45,
                    flags = 0x03,
                    frameChunks = arrayOf(vp8lLiteralChunk(2, 2, IntArray(4) { argb(0xFF, 1, 2, 3) })),
                ),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        val checkedCodec = codec!!
        assertEquals(1, checkedCodec.getFrameCount())
        assertEquals(4, checkedCodec.getInfo().width)
        assertEquals(2, checkedCodec.getInfo().height)
        assertEquals(SkAlphaType.kUnpremul, checkedCodec.getInfo().alphaType)
        assertEquals(7, checkedCodec.metadata.animation!!.loopCount)
        assertEquals(6, checkedCodec.getRepetitionCount())
        assertEquals(argb(0x40, 10, 20, 30), checkedCodec.metadata.animation.backgroundColor)
        val frame = checkedCodec.metadata.animation.frames.single()
        assertEquals(45, frame.durationMs)
        assertFalse(frame.blend)
        assertTrue(frame.disposeToBackground)
        assertEquals(
            SkCodec.FrameInfo(SkCodec.kNoFrame, 45, SkAlphaType.kUnpremul, SkIRect.MakeXYWH(0, 0, 2, 2)),
            checkedCodec.getFrameInfo().single(),
        )
    }

    @Test
    fun `decodes animated VP8L frames with disposal to background`() {
        val transparent = argb(0, 0, 0, 0)
        val red = argb(0xFF, 200, 0, 0)
        val blue = argb(0xFF, 0, 0, 200)
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 4, height = 1, flags = 0x12),
                animChunk(background = transparent, loopCount = 0),
                anmfChunk(
                    x = 0,
                    y = 0,
                    width = 4,
                    height = 1,
                    durationMs = 10,
                    flags = 0x03,
                    frameChunks = arrayOf(vp8lLiteralChunk(4, 1, IntArray(4) { red })),
                ),
                anmfChunk(
                    x = 2,
                    y = 0,
                    width = 2,
                    height = 1,
                    durationMs = 20,
                    flags = 0x02,
                    frameChunks = arrayOf(vp8lLiteralChunk(2, 1, IntArray(2) { blue })),
                ),
            ),
        )!!
        val dst = SkBitmap(
            width = 4,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 0)))
        assertArrayEquals(IntArray(4) { red }, dst.pixels8888)

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 1)))
        assertArrayEquals(intArrayOf(transparent, transparent, blue, blue), dst.pixels8888)
    }

    @Test
    fun `rejects malformed animated WebP chunks`() {
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x02),
                    anmfChunk(
                        x = 0,
                        y = 0,
                        width = 2,
                        height = 2,
                        durationMs = 1,
                        flags = 0,
                        frameChunks = arrayOf(vp8lChunk(2, 2)),
                    ),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x02),
                    animChunk(background = 0, loopCount = 0),
                    anmfChunk(
                        x = 2,
                        y = 0,
                        width = 2,
                        height = 2,
                        durationMs = 1,
                        flags = 0,
                        frameChunks = arrayOf(vp8lChunk(2, 2)),
                    ),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x02),
                    animChunk(background = 0, loopCount = 0),
                    anmfChunk(
                        x = 0,
                        y = 0,
                        width = 1,
                        height = 1,
                        durationMs = 1,
                        flags = 0,
                        frameChunks = arrayOf(vp8lChunk(2, 2)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `parses VP8X lossless-compressed alpha chunk metadata`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 3, height = 1, flags = 0x10),
                alphaChunk(
                    control = alphaControl(compression = WebpAlphaCompression.LOSSLESS),
                    payload = byteArrayOf(0x2F, 0x00, 0x00),
                ),
                vp8Chunk(width = 3, height = 1),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        val alpha = codec!!.metadata.alphaChunk
        assertNotNull(alpha)
        assertEquals(WebpAlphaCompression.LOSSLESS, alpha!!.compression)
        assertEquals(3, alpha.payloadSize)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
    }

    @Test
    fun `rejects malformed VP8X alpha chunks`() {
        val validAlpha = alphaChunk(control = 0, payload = byteArrayOf(1, 2, 3, 4))

        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0),
                    validAlpha,
                    vp8Chunk(width = 2, height = 2),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x10),
                    validAlpha,
                    validAlpha,
                    vp8Chunk(width = 2, height = 2),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x10),
                    alphaChunk(control = 0x40, payload = byteArrayOf(1, 2, 3, 4)),
                    vp8Chunk(width = 2, height = 2),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x10),
                    validAlpha,
                    vp8lChunk(width = 2, height = 2),
                ),
            ),
        )
    }

    @Test
    fun `keeps odd sized VP8X EXIF chunk payload separate from padding`() {
        val exif = byteArrayOf(1, 2, 3)
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 1, height = 1, flags = 0x08),
                chunk("EXIF", exif),
                vp8lChunk(width = 1, height = 1),
            ),
        ) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertArrayEquals(exif, codec!!.metadata.exifData)
        assertEquals(WebpBitstreamFormat.VP8L, codec.metadata.format)
    }

    @Test
    fun `rejects duplicate VP8X EXIF and XMP chunks`() {
        val exif = byteArrayOf(1, 2, 3, 4)
        val xmp = byteArrayOf(5, 6, 7, 8)

        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 1, height = 1, flags = 0x08),
                    chunk("EXIF", exif),
                    chunk("EXIF", exif),
                    vp8lChunk(width = 1, height = 1),
                ),
            ),
        )
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 1, height = 1, flags = 0x04),
                    chunk("XMP ", xmp),
                    chunk("XMP ", xmp),
                    vp8lChunk(width = 1, height = 1),
                ),
            ),
        )
    }

    @Test
    fun `parses VP8L dimensions`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lWebp(width = 4096, height = 2048)) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertEquals(WebpBitstreamFormat.VP8L, codec!!.metadata.format)
        assertEquals(4096, codec.getInfo().width)
        assertEquals(2048, codec.getInfo().height)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
    }

    @Test
    fun `parses VP8 keyframe dimensions`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8Webp(width = 640, height = 480)) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertEquals(WebpBitstreamFormat.VP8, codec!!.metadata.format)
        assertEquals(640, codec.getInfo().width)
        assertEquals(480, codec.getInfo().height)
        assertEquals(SkAlphaType.kOpaque, codec.getInfo().alphaType)
        assertEquals(20, codec.metadata.payloadOffset)
        assertEquals(10, codec.metadata.payloadSize)
    }

    @Test
    fun `rejects VP8 keyframe with first partition outside chunk`() {
        assertNull(
            SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8Chunk(width = 2, height = 2, firstPartitionSize = 1),
                ),
            ),
        )
    }

    @Test
    fun `parses VP8 frame tag fields`() {
        val frameTag = parseVp8FrameTag(
            vp8Payload(width = 2, height = 2, firstPartitionSize = 2, version = 3, showFrame = false) +
                byteArrayOf(0x11, 0x22),
            offset = 0,
            size = 12,
        )

        assertNotNull(frameTag)
        assertTrue(frameTag!!.keyFrame)
        assertEquals(3, frameTag.version)
        assertFalse(frameTag.showFrame)
        assertEquals(2, frameTag.firstPartitionSize)
    }

    @Test
    fun `VP8 bool reader decodes deterministic branch bits`() {
        assertEquals(0, Vp8BoolReader(byteArrayOf(0x00, 0x00)).readBit(128))
        assertEquals(1, Vp8BoolReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte())).readBit(128))
        assertEquals(0, Vp8BoolReader(byteArrayOf(0x00, 0x00)).readBit(1))
        assertEquals(1, Vp8BoolReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte())).readBit(255))
    }

    @Test
    fun `VP8 bool reader reads half-probability literals msb first`() {
        val reader = Vp8BoolReader(byteArrayOf(0xA0.toByte(), 0x00, 0x00))

        assertEquals(0b1010, reader.readLiteral(4))
    }

    @Test
    fun `VP8 bool reader rejects invalid probability and truncated refill`() {
        assertNull(Vp8BoolReader(byteArrayOf(0x00, 0x00)).readBit(0))

        val reader = Vp8BoolReader(byteArrayOf(0x00, 0x00))
        repeat(8) {
            assertEquals(0, reader.readBit(128))
        }
        assertNull(reader.readBit(128))
    }

    @Test
    fun `parses VP8 lossy keyframe macroblock header foundation`() {
        val headerBits = Vp8TestBitWriter().apply {
            writeBit(0) // YUV color space.
            writeBit(0) // No pixel value clamp.
            writeBit(0) // Segmentation disabled for this first supported slice.
            writeBit(1) // Simple loop filter.
            writeLiteral(17, 6)
            writeLiteral(3, 3)
            writeBit(0) // Loop filter deltas disabled for this first supported slice.
            writeLiteral(0, 2) // One coefficient partition.
            writeLiteral(42, 7)
            writeSignedDelta(3)
            writeSignedDelta(-2)
            writeSignedDelta(0)
            writeSignedDelta(4)
            writeSignedDelta(0)
        }.toByteArray()

        val result = readVp8LossyFrameHeader(Vp8BoolReader(headerBits), width = 17, height = 33)

        assertTrue(result is Vp8LossyHeaderDecodeResult.Header)
        val header = (result as Vp8LossyHeaderDecodeResult.Header).header
        assertEquals(0, header.colorSpace)
        assertEquals(0, header.clampType)
        assertEquals(2, header.macroblockWidth)
        assertEquals(3, header.macroblockHeight)
        assertEquals(1, header.coefficientPartitionCount)
        assertTrue(header.loopFilter.simpleFilter)
        assertEquals(17, header.loopFilter.level)
        assertEquals(3, header.loopFilter.sharpness)
        assertEquals(42, header.quantization.yAcIndex)
        assertEquals(3, header.quantization.yDcDelta)
        assertEquals(-2, header.quantization.y2DcDelta)
        assertEquals(0, header.quantization.y2AcDelta)
        assertEquals(4, header.quantization.uvDcDelta)
        assertEquals(0, header.quantization.uvAcDelta)
    }

    @Test
    fun `VP8 lossy header parser marks unsupported feature branches before pixel decode`() {
        val segmented = Vp8TestBitWriter().apply {
            writeBit(0)
            writeBit(0)
            writeBit(1)
        }.toByteArray()

        assertEquals(
            Vp8LossyHeaderDecodeResult.Unsupported,
            readVp8LossyFrameHeader(Vp8BoolReader(segmented), width = 16, height = 16),
        )
    }

    @Test
    fun `parses VP8 keyframe macroblock modes from positioned bitstream`() {
        val modeBits = Vp8TestBitWriter().apply {
            repeat(4) {
                writeBit(0) // Macroblock is not skipped.
                writeBit(0) // Y DC mode.
                writeBit(0) // UV DC mode.
            }
        }.toByteArray()
        val header = Vp8LossyFrameHeader(
            colorSpace = 0,
            clampType = 0,
            macroblockWidth = 2,
            macroblockHeight = 2,
            loopFilter = Vp8LoopFilterHeader(simpleFilter = false, level = 0, sharpness = 0),
            quantization = Vp8QuantizationHeader(
                yAcIndex = 0,
                yDcDelta = 0,
                y2DcDelta = 0,
                y2AcDelta = 0,
                uvDcDelta = 0,
                uvAcDelta = 0,
            ),
        )

        val macroblocks = readVp8KeyFrameMacroblockModes(
            reader = Vp8BoolReader(modeBits),
            header = header,
            noCoeffSkip = true,
        )

        assertNotNull(macroblocks)
        assertEquals(4, macroblocks!!.size)
        assertTrue(macroblocks.all { it.yMode == Vp8LumaPredictionMode.DC })
        assertTrue(macroblocks.all { it.uvMode == Vp8IntraPredictionMode.DC })
        assertTrue(macroblocks.none { it.skipCoefficients })
    }

    @Test
    fun `VP8 keyframe macroblock mode parser rejects truncated mode stream`() {
        val header = Vp8LossyFrameHeader(
            colorSpace = 0,
            clampType = 0,
            macroblockWidth = 4,
            macroblockHeight = 4,
            loopFilter = Vp8LoopFilterHeader(simpleFilter = false, level = 0, sharpness = 0),
            quantization = Vp8QuantizationHeader(
                yAcIndex = 0,
                yDcDelta = 0,
                y2DcDelta = 0,
                y2AcDelta = 0,
                uvDcDelta = 0,
                uvAcDelta = 0,
            ),
        )

        assertNull(readVp8KeyFrameMacroblockModes(Vp8BoolReader(byteArrayOf()), header, noCoeffSkip = true))
    }

    @Test
    fun `parses VP8 keyframe B_PRED luma subblock modes`() {
        val modeBits = byteArrayOf(0x7C, 0x00, 0x00)
        val header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1)

        val macroblocks = readVp8KeyFrameMacroblockModes(
            reader = Vp8BoolReader(modeBits),
            header = header,
            noCoeffSkip = true,
        )

        assertNotNull(macroblocks)
        val macroblock = macroblocks!!.single()
        assertEquals(Vp8LumaPredictionMode.B_PRED, macroblock.yMode)
        assertEquals(Vp8IntraPredictionMode.DC, macroblock.uvMode)
        assertFalse(macroblock.skipCoefficients)
        assertEquals(List(16) { Vp8LumaSubblockPredictionMode.B_DC }, macroblock.lumaSubblockModes)
    }

    @Test
    fun `VP8 keyframe B_PRED subblock parser uses left macroblock context`() {
        val modeBits = byteArrayOf(0x7C, 0x66, 0x32, 0xB1.toByte(), 0xC4.toByte(), 0x00, 0x00)
        val header = vp8CoefficientTestHeader(macroblockWidth = 2, macroblockHeight = 1, partitionCount = 1)

        val macroblocks = readVp8KeyFrameMacroblockModes(
            reader = Vp8BoolReader(modeBits),
            header = header,
            noCoeffSkip = true,
        )

        assertNotNull(macroblocks)
        assertEquals(List(16) { Vp8LumaSubblockPredictionMode.B_DC }, macroblocks!![0].lumaSubblockModes)
        assertEquals(
            listOf(
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_TM,
                Vp8LumaSubblockPredictionMode.B_TM,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_VE,
                Vp8LumaSubblockPredictionMode.B_DC,
                Vp8LumaSubblockPredictionMode.B_DC,
            ),
            macroblocks[1].lumaSubblockModes,
        )
    }

    @Test
    fun `VP8 keyframe B_PRED subblock mode parser rejects truncated stream`() {
        val header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1)

        assertNull(
            readVp8KeyFrameMacroblockModes(
                reader = Vp8BoolReader(byteArrayOf(0x7C)),
                header = header,
                noCoeffSkip = true,
            ),
        )
    }

    @Test
    fun `VP8 inverse transforms handle DC-only blocks`() {
        val dctInput = IntArray(16).also { it[0] = 16 }
        val whtInput = IntArray(16).also { it[0] = 8 }

        assertArrayEquals(IntArray(16) { 2 }, inverseVp8Dct4x4(dctInput))
        assertArrayEquals(IntArray(16) { 1 }, inverseVp8WalshHadamard4x4(whtInput))
    }

    @Test
    fun `VP8 inverse Walsh Hadamard transform handles AC-bearing blocks`() {
        val input = IntArray(16).also {
            it[0] = 16
            it[1] = 8
            it[4] = -8
            it[15] = 4
        }

        assertArrayEquals(
            intArrayOf(
                2, 1, 0, -1,
                1, 2, -1, 0,
                4, 3, 2, 1,
                3, 4, 1, 2,
            ),
            inverseVp8WalshHadamard4x4(input),
        )
    }

    @Test
    fun `VP8 coefficient token decode handles EOB zero one and small tokens`() {
        val tokenProbabilities = IntArray(11) { 128 }
        // Pre-encoded for Vp8BoolReader; this is not a raw MSB-first bit sequence.
        val tokens = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00)

        val result = decodeVp8CoefficientBlock(Vp8BoolReader(tokens), tokenProbabilities)

        assertTrue(result is Vp8CoefficientDecodeResult.Block)
        val block = (result as Vp8CoefficientDecodeResult.Block)
        assertTrue(block.hasNonZero)
        assertArrayEquals(
            IntArray(16).also {
                it[1] = -1
                it[4] = 2
            },
            block.coefficients,
        )
    }

    @Test
    fun `VP8 coefficient token decode handles category tokens and start coefficient`() {
        val tokenProbabilities = IntArray(11) { 128 }
        // Pre-encoded for Vp8BoolReader; this is not a raw MSB-first bit sequence.
        val tokens = byteArrayOf(0xF0.toByte(), 0x00, 0x00, 0x00)

        val result = decodeVp8CoefficientBlock(
            reader = Vp8BoolReader(tokens),
            probabilities = tokenProbabilities,
            startCoefficient = 1,
        )

        assertTrue(result is Vp8CoefficientDecodeResult.Block)
        val block = (result as Vp8CoefficientDecodeResult.Block)
        assertTrue(block.hasNonZero)
        assertArrayEquals(
            IntArray(16).also { it[1] = 5 },
            block.coefficients,
        )
    }

    @Test
    fun `VP8 coefficient token decode advances coefficient bands and token contexts`() {
        val flat = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) { 128 }
        flat[vp8CoefficientProbabilityIndexForTest(type = 0, band = 0, context = 0, probability = 0)] = 255
        flat[vp8CoefficientProbabilityIndexForTest(type = 0, band = 1, context = 1, probability = 0)] = 1
        val probabilities = Vp8CoefficientProbabilities.fromFlat(flat)

        val result = decodeVp8CoefficientBlock(
            reader = Vp8BoolReader(ByteArray(4)),
            probabilities = probabilities,
            type = 0,
            initialContext = 0,
        )

        assertTrue(result is Vp8CoefficientDecodeResult.Block)
        assertFalse((result as Vp8CoefficientDecodeResult.Block).hasNonZero)
        assertTrue(result.coefficients.all { it == 0 })
    }

    @Test
    fun `VP8 coefficient token decode keeps coefficient fourteen in band six`() {
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3, 6, 4, 5, 6, 6, 6, 6, 6, 6, 6, 6, 7),
            VP8_COEFFICIENT_BANDS,
        )
    }

    @Test
    fun `VP8 coefficient token decode rejects invalid probabilities and truncated tokens`() {
        assertEquals(
            Vp8CoefficientDecodeResult.Invalid,
            decodeVp8CoefficientBlock(Vp8BoolReader(byteArrayOf(0x00, 0x00)), IntArray(10) { 128 }),
        )
        assertEquals(
            Vp8CoefficientDecodeResult.Invalid,
            decodeVp8CoefficientBlock(Vp8BoolReader(byteArrayOf(0x00, 0x00)), IntArray(11) { 0 }),
        )

        assertEquals(
            Vp8CoefficientDecodeResult.Invalid,
            decodeVp8CoefficientBlock(Vp8BoolReader(byteArrayOf(0xFF.toByte())), IntArray(11) { 128 }),
        )
    }

    @Test
    fun `VP8 coefficient context selects probabilities from neighboring nonzero blocks`() {
        val probabilitiesByContext = arrayOf(
            IntArray(11) { 1 },
            IntArray(11) { 128 },
            IntArray(11) { 255 },
        )
        val tokens = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00)

        val result = decodeVp8CoefficientBlockWithContext(
            reader = Vp8BoolReader(tokens),
            probabilitiesByContext = probabilitiesByContext,
            leftHasNonZero = true,
            topHasNonZero = false,
        )

        assertEquals(0, vp8CoefficientContext(leftHasNonZero = false, topHasNonZero = false))
        assertEquals(1, vp8CoefficientContext(leftHasNonZero = true, topHasNonZero = false))
        assertEquals(2, vp8CoefficientContext(leftHasNonZero = true, topHasNonZero = true))
        assertTrue(result is Vp8CoefficientDecodeResult.Block)
        assertArrayEquals(
            IntArray(16).also {
                it[1] = -1
                it[4] = 2
            },
            (result as Vp8CoefficientDecodeResult.Block).coefficients,
        )
    }

    @Test
    fun `VP8 macroblock coefficient decoder reads selected row partitions`() {
        val partition0 = ByteArray(16)
        val partition1 = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00) + ByteArray(32)
        val data = partition0 + partition1
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 2, partitionCount = 2),
            coefficientPartitions = listOf(
                Vp8CoefficientPartition(offset = 0, end = partition0.size),
                Vp8CoefficientPartition(offset = partition0.size, end = data.size),
            ),
        )
        val modes = List(2) {
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            )
        }

        val result = decodeVp8MacroblockCoefficients(
            data = data,
            layout = layout,
            macroblockModes = modes,
            probabilities = Vp8CoefficientProbabilities.filled(128),
        )

        assertTrue(result is Vp8MacroblockCoefficientDecodeResult.Macroblocks)
        val macroblocks = (result as Vp8MacroblockCoefficientDecodeResult.Macroblocks).macroblocks
        assertEquals(2, macroblocks.size)
        assertTrue(macroblocks[0].luma.all { block -> block.all { it == 0 } })
        assertTrue(macroblocks[0].u.all { block -> block.all { it == 0 } })
        assertTrue(macroblocks[0].v.all { block -> block.all { it == 0 } })
        assertArrayEquals(
            IntArray(16).also {
                it[1] = -1
                it[4] = 2
            },
            macroblocks[1].y2,
        )
        assertTrue(macroblocks[1].luma.all { block -> block.all { it == 0 } })
    }

    @Test
    fun `VP8 macroblock coefficient decoder preserves skipped coefficient contexts`() {
        val partition = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00) + ByteArray(32)
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 2, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = partition.size)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = true,
            ),
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            ),
        )

        val result = decodeVp8MacroblockCoefficients(
            data = partition,
            layout = layout,
            macroblockModes = modes,
            probabilities = Vp8CoefficientProbabilities.filled(128),
        )

        assertTrue(result is Vp8MacroblockCoefficientDecodeResult.Macroblocks)
        val macroblocks = (result as Vp8MacroblockCoefficientDecodeResult.Macroblocks).macroblocks
        assertTrue(macroblocks[0].luma.all { block -> block.all { it == 0 } })
        assertArrayEquals(
            IntArray(16).also {
                it[1] = -1
                it[4] = 2
            },
            macroblocks[1].y2,
        )
        assertTrue(macroblocks[1].luma.all { block -> block.all { it == 0 } })
    }

    @Test
    fun `VP8 macroblock coefficient decoder keeps B_PRED coefficients in luma blocks`() {
        val partition = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00) + ByteArray(32)
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = partition.size)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.B_PRED,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            ),
        )

        val result = decodeVp8MacroblockCoefficients(
            data = partition,
            layout = layout,
            macroblockModes = modes,
            probabilities = Vp8CoefficientProbabilities.filled(128),
        )

        assertTrue(result is Vp8MacroblockCoefficientDecodeResult.Macroblocks)
        val macroblocks = (result as Vp8MacroblockCoefficientDecodeResult.Macroblocks).macroblocks
        assertTrue(macroblocks.single().y2.all { it == 0 })
        assertArrayEquals(
            IntArray(16).also {
                it[1] = -1
                it[4] = 2
            },
            macroblocks.single().luma[0],
        )
    }

    @Test
    fun `VP8 macroblock coefficient decoder rejects inconsistent inputs`() {
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = 1)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            ),
        )

        assertEquals(
            Vp8MacroblockCoefficientDecodeResult.Invalid,
            decodeVp8MacroblockCoefficients(
                data = ByteArray(0),
                layout = layout,
                macroblockModes = modes,
                probabilities = Vp8CoefficientProbabilities.filled(128),
            ),
        )
        assertEquals(
            Vp8MacroblockCoefficientDecodeResult.Invalid,
            decodeVp8MacroblockCoefficients(
                data = ByteArray(1),
                layout = layout,
                macroblockModes = emptyList(),
                probabilities = Vp8CoefficientProbabilities.filled(128),
            ),
        )
    }

    @Test
    fun `VP8 non B_PRED pipeline reconstructs a neutral keyframe macroblock`() {
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = 0)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            ),
        )

        val result = reconstructVp8NonBPredKeyFramePlanes(
            layout = layout,
            macroblockModes = modes,
            macroblockCoefficients = listOf(zeroVp8MacroblockCoefficients()),
        )

        assertTrue(result is Vp8ReconstructionResult.Planes)
        val planes = (result as Vp8ReconstructionResult.Planes).planes
        assertEquals(16, planes.width)
        assertEquals(16, planes.height)
        assertTrue(planes.yPlane.all { it == 128 })
        assertTrue(planes.uPlane.all { it == 128 })
        assertTrue(planes.vPlane.all { it == 128 })
        assertArrayEquals(IntArray(16 * 16) { argb(128, 128, 128) }, planes.toRgba())
    }

    @Test
    fun `VP8 non B_PRED pipeline reconstructs adjacent macroblocks with parsed coefficients`() {
        val partition = ByteArray(16)
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 2, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = partition.size)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.DC,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
            ),
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.HORIZONTAL,
                uvMode = Vp8IntraPredictionMode.HORIZONTAL,
                skipCoefficients = false,
            ),
        )

        val result = reconstructVp8NonBPredKeyFramePlanes(
            data = partition,
            layout = layout,
            macroblockModes = modes,
            probabilities = Vp8CoefficientProbabilities.filled(128),
        )

        assertTrue(result is Vp8ReconstructionResult.Planes)
        val planes = (result as Vp8ReconstructionResult.Planes).planes
        assertEquals(32, planes.width)
        assertEquals(16, planes.height)
        assertTrue(planes.yPlane.all { it == 128 })
        assertTrue(planes.uPlane.all { it == 128 })
        assertTrue(planes.vPlane.all { it == 128 })
        assertArrayEquals(IntArray(32 * 16) { argb(128, 128, 128) }, planes.toRgba())
    }

    @Test
    fun `VP8 reconstruction pipeline accepts B_PRED macroblocks`() {
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = 0)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.B_PRED,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
                lumaSubblockModes = List(16) { Vp8LumaSubblockPredictionMode.B_DC },
            ),
        )

        val result =
            reconstructVp8NonBPredKeyFramePlanes(
                layout = layout,
                macroblockModes = modes,
                macroblockCoefficients = listOf(zeroVp8MacroblockCoefficients()),
            )
        assertTrue(result is Vp8ReconstructionResult.Planes)
        assertTrue((result as Vp8ReconstructionResult.Planes).planes.yPlane.all { it in 128..129 })
    }

    @Test
    fun `VP8 parsed pipeline reports invalid B_PRED coefficient data`() {
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = 1)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.B_PRED,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
                lumaSubblockModes = List(16) { Vp8LumaSubblockPredictionMode.B_DC },
            ),
        )

        assertEquals(
            Vp8ReconstructionResult.Invalid,
            reconstructVp8NonBPredKeyFramePlanes(
                data = ByteArray(0),
                layout = layout,
                macroblockModes = modes,
                probabilities = Vp8CoefficientProbabilities.filled(128),
            ),
        )
    }

    @Test
    fun `VP8 coefficient probability update parser preserves base table without update bits`() {
        val base = Vp8CoefficientProbabilities.filled(128)
        val updateProbabilities = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) { 255 }

        val result = readVp8CoefficientProbabilityUpdates(
            reader = Vp8BoolReader(ByteArray(140)),
            base = base,
            updateProbabilities = updateProbabilities,
        )

        assertTrue(result is Vp8CoefficientProbabilityUpdateResult.Probabilities)
        val probabilities = (result as Vp8CoefficientProbabilityUpdateResult.Probabilities).probabilities
        assertEquals(128, probabilities.valueAt(type = 0, band = 0, context = 0, probability = 0))
        assertEquals(128, probabilities.valueAt(type = 3, band = 7, context = 2, probability = 10))
        assertArrayEquals(IntArray(11) { 128 }, probabilities.tokenProbabilities(type = 1, band = 4, context = 2))
    }

    @Test
    fun `VP8 coefficient probability update parser applies literal updates`() {
        val base = Vp8CoefficientProbabilities.filled(128)
        val updateProbabilities = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) { 128 }
        val updateBits = ByteArray(200).also {
            it[0] = 0xA6.toByte() // first update flag plus the first seven literal bits for 77.
            it[1] = 0x33.toByte() // final literal bit, followed by zero update flags.
        }

        val result = readVp8CoefficientProbabilityUpdates(
            reader = Vp8BoolReader(updateBits),
            base = base,
            updateProbabilities = updateProbabilities,
        )

        assertTrue(result is Vp8CoefficientProbabilityUpdateResult.Probabilities)
        val probabilities = (result as Vp8CoefficientProbabilityUpdateResult.Probabilities).probabilities
        assertEquals(77, probabilities.valueAt(type = 0, band = 0, context = 0, probability = 0))
        assertEquals(128, probabilities.valueAt(type = 3, band = 7, context = 2, probability = 10))
        assertArrayEquals(
            intArrayOf(77) + IntArray(10) { 128 },
            probabilities.tokenProbabilities(type = 0, band = 0, context = 0),
        )
    }

    @Test
    fun `VP8 coefficient probability update parser rejects malformed inputs`() {
        val base = Vp8CoefficientProbabilities.filled(128)

        assertEquals(
            Vp8CoefficientProbabilityUpdateResult.Invalid,
            readVp8CoefficientProbabilityUpdates(
                reader = Vp8BoolReader(ByteArray(140)),
                base = base,
                updateProbabilities = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST - 1) { 255 },
            ),
        )
        assertEquals(
            Vp8CoefficientProbabilityUpdateResult.Invalid,
            readVp8CoefficientProbabilityUpdates(
                reader = Vp8BoolReader(ByteArray(140)),
                base = base,
                updateProbabilities = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) { 0 },
            ),
        )
        assertEquals(
            Vp8CoefficientProbabilityUpdateResult.Invalid,
            readVp8CoefficientProbabilityUpdates(
                reader = Vp8BoolReader(byteArrayOf(0xFF.toByte())),
                base = base,
                updateProbabilities = IntArray(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) { 255 },
            ),
        )
    }

    @Test
    fun `VP8 decoded coefficient tokens reconstruct an intra 4x4 block`() {
        val tokenProbabilities = IntArray(11) { 128 }
        // Pre-encoded for Vp8BoolReader; this is not a raw MSB-first bit sequence.
        val tokens = byteArrayOf(0xB7.toByte(), 0x11, 0x00, 0x00)
        val result = decodeVp8CoefficientBlock(Vp8BoolReader(tokens), tokenProbabilities)

        assertTrue(result is Vp8CoefficientDecodeResult.Block)
        val pixels = reconstructVp8Intra4x4Block(
            mode = Vp8IntraPredictionMode.DC,
            left = intArrayOf(100, 100, 100, 100),
            top = intArrayOf(100, 100, 100, 100),
            topLeft = 100,
            coefficients = (result as Vp8CoefficientDecodeResult.Block).coefficients,
            dcQuant = 1,
            acQuant = 8,
        )

        assertArrayEquals(
            intArrayOf(
                100, 101, 101, 102,
                100, 100, 101, 101,
                99, 99, 100, 100,
                98, 99, 99, 100,
            ),
            pixels,
        )
    }

    @Test
    fun `VP8 luma macroblock reconstruction injects inverse Y2 DC into 16x16 prediction`() {
        val y2 = IntArray(16).also { coefficients -> coefficients[0] = 128 }
        val blocks = Array(16) { IntArray(16) }

        val pixels = reconstructVp8Intra16x16LumaMacroblock(
            mode = Vp8LumaPredictionMode.VERTICAL,
            left = null,
            top = IntArray(16) { x -> 32 + x },
            topLeft = 0,
            y2Coefficients = y2,
            coefficientsByBlock = blocks,
            dcQuant = 1,
            acQuant = 1,
            y2DcQuant = 1,
            y2AcQuant = 1,
        )

        assertEquals(16 * 16, pixels.size)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                assertEquals(34 + x, pixels[y * 16 + x])
            }
        }
    }

    @Test
    fun `VP8 luma macroblock reconstruction preserves luma AC residuals with zero Y2`() {
        val blocks = Array(16) { IntArray(16) }
        blocks[0][1] = -1
        blocks[0][4] = 2

        val pixels = reconstructVp8Intra16x16LumaMacroblock(
            mode = Vp8LumaPredictionMode.DC,
            left = IntArray(16) { 100 },
            top = IntArray(16) { 100 },
            topLeft = 100,
            y2Coefficients = IntArray(16),
            coefficientsByBlock = blocks,
            dcQuant = 1,
            acQuant = 8,
            y2DcQuant = 1,
            y2AcQuant = 1,
        )

        assertArrayEquals(
            intArrayOf(
                100, 101, 101, 102,
                100, 100, 101, 101,
                99, 99, 100, 100,
                98, 99, 99, 100,
            ),
            IntArray(16) { index ->
                val y = index / 4
                val x = index % 4
                pixels[y * 16 + x]
            },
        )
        assertEquals(100, pixels[4])
        assertEquals(100, pixels[16 * 4])
    }

    @Test
    fun `VP8 B_PRED luma subblock reconstructs directional predictions`() {
        val top = intArrayOf(10, 11, 12, 13, 14, 15, 16, 17)
        val left = intArrayOf(20, 21, 22, 23)

        assertArrayEquals(
            intArrayOf(
                10, 11, 12, 13,
                10, 11, 12, 13,
                10, 11, 12, 13,
                10, 11, 12, 13,
            ),
            reconstructVp8LumaSubblock(
                mode = Vp8LumaSubblockPredictionMode.B_VE,
                left = left,
                top = top,
                topLeft = 9,
                coefficients = IntArray(16),
                dcQuant = 1,
                acQuant = 1,
            ),
        )
        assertArrayEquals(
            intArrayOf(
                18, 18, 18, 18,
                21, 21, 21, 21,
                22, 22, 22, 22,
                23, 23, 23, 23,
            ),
            reconstructVp8LumaSubblock(
                mode = Vp8LumaSubblockPredictionMode.B_HE,
                left = left,
                top = top,
                topLeft = 9,
                coefficients = IntArray(16),
                dcQuant = 1,
                acQuant = 1,
            ),
        )
        assertArrayEquals(
            intArrayOf(
                11, 12, 13, 14,
                12, 13, 14, 15,
                13, 14, 15, 16,
                14, 15, 16, 17,
            ),
            reconstructVp8LumaSubblock(
                mode = Vp8LumaSubblockPredictionMode.B_LD,
                left = left,
                top = top,
                topLeft = 9,
                coefficients = IntArray(16),
                dcQuant = 1,
                acQuant = 1,
            ),
        )
    }

    @Test
    fun `VP8 B_PRED luma macroblock reconstructs subblocks in raster order`() {
        val blocks = Array(16) { IntArray(16) }
        blocks[0][1] = -1
        blocks[0][4] = 2

        val pixels = reconstructVp8BPredLumaMacroblock(
            lumaSubblockModes = List(16) { Vp8LumaSubblockPredictionMode.B_DC },
            left = IntArray(16) { 100 },
            top = IntArray(16) { 100 },
            topLeft = 100,
            coefficientsByBlock = blocks,
            dcQuant = 1,
            acQuant = 8,
        )

        assertArrayEquals(
            intArrayOf(
                100, 101, 101, 102,
                100, 100, 101, 101,
                99, 99, 100, 100,
                98, 99, 99, 100,
            ),
            IntArray(16) { index ->
                val y = index / 4
                val x = index % 4
                pixels[y * 16 + x]
            },
        )
        assertEquals(100, pixels[4])
        assertEquals(100, pixels[16 * 4])
    }

    @Test
    fun `VP8 B_PRED right edge luma subblocks use external top right samples`() {
        val pixels = reconstructVp8BPredLumaMacroblock(
            lumaSubblockModes = List(16) { index ->
                when (index) {
                    3, 7 -> Vp8LumaSubblockPredictionMode.B_LD
                    else -> Vp8LumaSubblockPredictionMode.B_DC
                }
            },
            left = IntArray(16) { 100 },
            top = IntArray(16) { x -> 10 + x },
            topLeft = 9,
            topRight = intArrayOf(26, 27, 28, 29),
            coefficientsByBlock = Array(16) { IntArray(16) },
            dcQuant = 1,
            acQuant = 1,
        )

        assertArrayEquals(
            intArrayOf(
                23, 24, 25, 26,
                24, 25, 26, 27,
                25, 26, 27, 28,
                26, 27, 28, 29,
            ),
            IntArray(16) { index ->
                val y = index / 4
                val x = index % 4
                pixels[y * 16 + 12 + x]
            },
        )
        assertArrayEquals(
            intArrayOf(
                27, 28, 28, 27,
                28, 28, 27, 27,
                28, 27, 27, 28,
                27, 27, 28, 29,
            ),
            IntArray(16) { index ->
                val y = index / 4
                val x = index % 4
                pixels[(4 + y) * 16 + 12 + x]
            },
        )
    }

    @Test
    fun `VP8 B_PRED top left frame default uses above row sentinel`() {
        val pixels = reconstructVp8BPredLumaMacroblock(
            lumaSubblockModes = List(16) { Vp8LumaSubblockPredictionMode.B_TM },
            left = null,
            top = null,
            topLeft = null,
            coefficientsByBlock = Array(16) { IntArray(16) },
            dcQuant = 1,
            acQuant = 1,
        )

        assertEquals(129, pixels[0])
    }

    @Test
    fun `VP8 luma macroblock reconstruction excludes B_PRED`() {
        assertThrows<IllegalArgumentException> {
            reconstructVp8Intra16x16LumaMacroblock(
                mode = Vp8LumaPredictionMode.B_PRED,
                left = null,
                top = null,
                topLeft = null,
                y2Coefficients = IntArray(16),
                coefficientsByBlock = Array(16) { IntArray(16) },
                dcQuant = 1,
                acQuant = 1,
                y2DcQuant = 1,
                y2AcQuant = 1,
            )
        }
    }

    @Test
    fun `VP8 intra reconstruction applies prediction and residuals`() {
        val dc = reconstructVp8IntraPlane(
            width = 4,
            height = 4,
            mode = Vp8IntraPredictionMode.DC,
            left = intArrayOf(100, 104, 108, 112),
            top = intArrayOf(120, 124, 128, 132),
            topLeft = 96,
            residual = IntArray(16).also {
                it[0] = -200
                it[5] = 9
                it[15] = 200
            },
        )

        assertEquals(0, dc[0])
        assertEquals(116, dc[1])
        assertEquals(125, dc[5])
        assertEquals(255, dc[15])
    }

    @Test
    fun `VP8 true-motion prediction uses neighboring samples`() {
        val plane = reconstructVp8IntraPlane(
            width = 4,
            height = 4,
            mode = Vp8IntraPredictionMode.TRUE_MOTION,
            left = intArrayOf(90, 110, 130, 150),
            top = intArrayOf(80, 100, 120, 140),
            topLeft = 100,
        )

        assertArrayEquals(
            intArrayOf(
                70, 90, 110, 130,
                90, 110, 130, 150,
                110, 130, 150, 170,
                130, 150, 170, 190,
            ),
            plane,
        )
    }

    @Test
    fun `VP8 YUV420 composition writes opaque project-packed pixels`() {
        val pixels = composeVp8Yuv420ToRgba(
            yPlane = intArrayOf(
                10, 20, 30,
                40, 50, 60,
            ),
            uPlane = intArrayOf(128, 140),
            vPlane = intArrayOf(128, 120),
            width = 3,
            height = 2,
        )

        assertArrayEquals(
            intArrayOf(
                argb(10, 10, 10),
                argb(20, 20, 20),
                argb(18, 32, 51),
                argb(40, 40, 40),
                argb(50, 50, 50),
                argb(48, 62, 81),
            ),
            pixels,
        )
    }

    @Test
    fun `VP8 simple loop filter smooths eligible edge samples`() {
        val filtered = filterVp8SimpleLoopFilterSample(
            p1 = 100,
            p0 = 100,
            q0 = 108,
            q1 = 108,
            limit = 20,
        )

        assertTrue(filtered.filtered)
        assertEquals(102, filtered.p0)
        assertEquals(106, filtered.q0)

        val clippedAdjustment = filterVp8SimpleLoopFilterSample(
            p1 = 0,
            p0 = 0,
            q0 = 45,
            q1 = 0,
            limit = 100,
        )

        assertTrue(clippedAdjustment.filtered)
        assertEquals(15, clippedAdjustment.p0)
        assertEquals(30, clippedAdjustment.q0)

        val untouched = filterVp8SimpleLoopFilterSample(
            p1 = 0,
            p0 = 20,
            q0 = 240,
            q1 = 255,
            limit = 20,
        )

        assertFalse(untouched.filtered)
        assertEquals(20, untouched.p0)
        assertEquals(240, untouched.q0)

        val oddHalfDifferenceBoundary = filterVp8SimpleLoopFilterSample(
            p1 = 100,
            p0 = 100,
            q0 = 104,
            q1 = 105,
            limit = 10,
        )

        assertTrue(oddHalfDifferenceBoundary.filtered)
    }

    @Test
    fun `VP8 simple loop filter applies vertical and horizontal plane edges`() {
        val verticalPlane = intArrayOf(
            100, 100, 108, 108, 126,
            100, 100, 108, 108, 126,
            10, 20, 240, 250, 255,
        )

        assertArrayEquals(
            intArrayOf(
                100, 102, 106, 108, 126,
                100, 102, 106, 108, 126,
                10, 20, 240, 250, 255,
            ),
            applyVp8SimpleVerticalLoopFilter(
                plane = verticalPlane,
                width = 5,
                height = 3,
                edgeX = 2,
                limit = 25,
            ),
        )

        val horizontalPlane = intArrayOf(
            100, 100, 10,
            100, 100, 20,
            108, 108, 240,
            108, 108, 250,
            126, 126, 255,
        )

        assertArrayEquals(
            intArrayOf(
                100, 100, 10,
                102, 102, 20,
                106, 106, 240,
                108, 108, 250,
                126, 126, 255,
            ),
            applyVp8SimpleHorizontalLoopFilter(
                plane = horizontalPlane,
                width = 3,
                height = 5,
                edgeY = 2,
                limit = 25,
            ),
        )
    }

    @Test
    fun `VP8 simple loop filter applies to reconstructed luma planes`() {
        val yPlane = IntArray(8 * 4) { index ->
            if (index % 8 < 4) 100 else 106
        }
        val planes = Vp8ReconstructedPlanes(
            yPlane = yPlane,
            uPlane = IntArray(4 * 2) { 128 },
            vPlane = IntArray(4 * 2) { 128 },
            width = 8,
            height = 4,
        )

        val filtered = applyVp8SimpleLoopFilterIfNeeded(
            planes = planes,
            loopFilter = Vp8LoopFilterHeader(simpleFilter = true, level = 10, sharpness = 7),
        )

        assertArrayEquals(
            IntArray(8 * 4) { index ->
                when (index % 8) {
                    3 -> 102
                    4 -> 105
                    in 0..2 -> 100
                    else -> 106
                }
            },
            filtered.yPlane,
        )
        assertArrayEquals(planes.uPlane, filtered.uPlane)
        assertArrayEquals(planes.vPlane, filtered.vPlane)
    }

    @Test
    fun `VP8 simple loop filter derives sharpness adjusted edge limits`() {
        val limits = deriveVp8LoopFilterLimits(Vp8LoopFilterHeader(simpleFilter = true, level = 10, sharpness = 7))

        assertEquals(26, limits.macroblockEdge)
        assertEquals(22, limits.subblockEdge)
        assertEquals(22, limits.forEdge(4))
        assertEquals(26, limits.forEdge(16))
    }

    @Test
    fun `returns unimplemented for pixel decode after metadata parse`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8xWebp(width = 2, height = 2, flags = 0))!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `returns input error for VP8 lossy with truncated first partition header`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff("WEBP", vp8ChunkWithPartition(width = 2, height = 2, partition = byteArrayOf(0x00))),
        )!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `decodes supported VP8 lossy non B_PRED keyframe pixels`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8SupportedNonBPredWebp(width = 2, height = 2))!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        assertArrayEquals(IntArray(4) { argb(128, 128, 128) }, dst.pixels8888)
    }

    @Test
    fun `composes uncompressed VP8X alpha into supported VP8 lossy pixels`() {
        val alpha = byteArrayOf(0x00, 0x40, 0x80.toByte(), 0xFF.toByte())
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8xChunk(width = 2, height = 2, flags = 0x10),
                alphaChunk(control = alphaControl(compression = WebpAlphaCompression.NONE), payload = alpha),
                vp8ChunkWithPartitions(width = 2, height = 2, vp8FirstPartition(), ByteArray(64)),
            ),
        )!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        assertArrayEquals(
            intArrayOf(
                argb(0, 128, 128, 128),
                argb(64, 128, 128, 128),
                argb(128, 128, 128, 128),
                argb(255, 128, 128, 128),
            ),
            dst.pixels8888,
        )
    }

    @Test
    fun `returns unimplemented for unsupported VP8X alpha transforms on supported VP8 lossy pixels`() {
        val unsupportedAlphaCases = listOf(
            alphaControl(compression = WebpAlphaCompression.NONE, filtering = 1),
            alphaControl(compression = WebpAlphaCompression.NONE, preprocessing = 1),
            alphaControl(compression = WebpAlphaCompression.LOSSLESS),
        )

        for (control in unsupportedAlphaCases) {
            val codec = SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x10),
                    alphaChunk(control = control, payload = byteArrayOf(1, 2, 3, 4)),
                    vp8ChunkWithPartitions(width = 2, height = 2, vp8FirstPartition(), ByteArray(64)),
                ),
            )!!
            val dst = SkBitmap(
                width = 2,
                height = 2,
                colorType = SkColorType.kRGBA_8888,
                colorSpace = SkColorSpace.makeSRGB(),
            )

            assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
        }
    }

    @Test
    fun `returns input error for malformed VP8X uncompressed alpha on supported VP8 lossy pixels`() {
        val malformedAlphaCases = listOf(
            alphaChunk(control = alphaControl(compression = WebpAlphaCompression.NONE), payload = byteArrayOf(1, 2, 3)),
            alphaChunk(control = alphaControl(compression = WebpAlphaCompression.NONE), payload = ByteArray(0)),
            ByteArray(0),
        )

        for (alphaChunk in malformedAlphaCases) {
            val codec = SkWebpKotlinCodec.Decoder.make(
                riff(
                    "WEBP",
                    vp8xChunk(width = 2, height = 2, flags = 0x10),
                    alphaChunk,
                    vp8ChunkWithPartitions(width = 2, height = 2, vp8FirstPartition(), ByteArray(64)),
                ),
            )!!
            val dst = SkBitmap(
                width = 2,
                height = 2,
                colorType = SkColorType.kRGBA_8888,
                colorSpace = SkColorSpace.makeSRGB(),
            )

            assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
        }
    }

    @Test
    fun `VP8 lossy helper reconstructs B_PRED luma macroblocks`() {
        val layout = Vp8LossyBitstreamLayout(
            header = vp8CoefficientTestHeader(macroblockWidth = 1, macroblockHeight = 1, partitionCount = 1),
            coefficientPartitions = listOf(Vp8CoefficientPartition(offset = 0, end = 0)),
        )
        val modes = listOf(
            Vp8MacroblockMode(
                yMode = Vp8LumaPredictionMode.B_PRED,
                uvMode = Vp8IntraPredictionMode.DC,
                skipCoefficients = false,
                lumaSubblockModes = List(16) { Vp8LumaSubblockPredictionMode.B_DC },
            ),
        )

        val result =
            reconstructVp8NonBPredKeyFramePlanes(
                layout = layout,
                macroblockModes = modes,
                macroblockCoefficients = listOf(zeroVp8MacroblockCoefficients()),
            )
        assertTrue(result is Vp8ReconstructionResult.Planes)
        val planes = (result as Vp8ReconstructionResult.Planes).planes
        assertEquals(128, planes.yPlane[0])
        assertEquals(128, planes.uPlane[0])
        assertEquals(128, planes.vPlane[0])
    }

    @Test
    fun `decodes VP8 lossy B_PRED keyframe pixels`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8ChunkWithPartitions(
                    width = 2,
                    height = 2,
                    firstPartition = vp8BPredFirstPartition(),
                    coefficients = ByteArray(64),
                ),
            ),
        )!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        assertArrayEquals(IntArray(4) { argb(128, 128, 128) }, dst.pixels8888)
    }

    @Test
    fun `returns unimplemented for VP8 lossy normal loop filter pending integration`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff("WEBP", vp8ChunkWithPartition(width = 2, height = 2, partition = vp8FirstPartition(filterLevel = 1))),
        )!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `decodes VP8 lossy simple loop filter in supported pixel subset`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            riff(
                "WEBP",
                vp8ChunkWithPartitions(
                    width = 2,
                    height = 2,
                    firstPartition = vp8FirstPartition(simpleFilter = true, filterLevel = 1),
                    coefficients = ByteArray(64),
                ),
            ),
        )!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `parses VP8 lossy multi coefficient partition layout`() {
        val firstPartition = Vp8TestBitWriter().apply {
            writeBit(0)
            writeBit(0)
            writeBit(0)
            writeBit(1)
            writeLiteral(12, 6)
            writeLiteral(0, 3)
            writeBit(0)
            writeLiteral(2, 2) // Four coefficient partitions.
            writeLiteral(8, 7)
            repeat(5) { writeSignedDelta(0) }
        }.toByteArray()
        val coefficientPartitionSizes = byteArrayOf(
            1, 0, 0,
            2, 0, 0,
            0, 0, 0,
        )
        val coefficientBytes = byteArrayOf(
            0xA1.toByte(),
            0xB1.toByte(),
            0xB2.toByte(),
            0xD1.toByte(),
            0xD2.toByte(),
            0xD3.toByte(),
        )
        val data = riff(
            "WEBP",
            chunk(
                "VP8 ",
                vp8Payload(width = 17, height = 17, firstPartitionSize = firstPartition.size) +
                    firstPartition +
                    coefficientPartitionSizes +
                    coefficientBytes,
            ),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(data) as SkWebpKotlinCodec

        val result = decodeVp8LossyBitstreamLayout(data, codec.metadata)

        assertTrue(result is Vp8LossyBitstreamLayoutDecodeResult.Layout)
        val layout = (result as Vp8LossyBitstreamLayoutDecodeResult.Layout).layout
        assertEquals(4, layout.header.coefficientPartitionCount)
        assertEquals(listOf(1, 2, 0, 3), layout.coefficientPartitions.map { it.size })
        assertEquals(
            listOf(
                listOf(0xA1),
                listOf(0xB1, 0xB2),
                emptyList(),
                listOf(0xD1, 0xD2, 0xD3),
            ),
            layout.coefficientPartitions.map { partition ->
                data.copyOfRange(partition.offset, partition.end).map { it.toInt() and 0xFF }
            },
        )
    }

    @Test
    fun `rejects VP8 lossy coefficient partition table outside payload`() {
        val firstPartition = Vp8TestBitWriter().apply {
            writeBit(0)
            writeBit(0)
            writeBit(0)
            writeBit(0)
            writeLiteral(0, 6)
            writeLiteral(0, 3)
            writeBit(0)
            writeLiteral(1, 2) // Two coefficient partitions, requiring one 24-bit size.
            writeLiteral(0, 7)
            repeat(5) { writeSignedDelta(0) }
        }.toByteArray()
        val data = riff(
            "WEBP",
            chunk(
                "VP8 ",
                vp8Payload(width = 2, height = 2, firstPartitionSize = firstPartition.size) +
                    firstPartition +
                    byteArrayOf(5, 0),
            ),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(data) as SkWebpKotlinCodec
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(Vp8LossyBitstreamLayoutDecodeResult.Invalid, decodeVp8LossyBitstreamLayout(data, codec.metadata))
        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `decodes VP8L simple literal pixels`() {
        val expected = intArrayOf(
            argb(0xFF, 0x11, 0x22, 0x33),
            argb(0x80, 0x44, 0x66, 0x77),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lLiteralWebp(width = 2, height = 1, expected))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L normal Huffman single-symbol literals`() {
        val expected = intArrayOf(
            argb(0x00, 0x00, 0x00, 0x00),
            argb(0x00, 0x00, 0x00, 0x00),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lNormalSingleSymbolWebp(width = 2, height = 1))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L LZ77 copy length`() {
        val expected = intArrayOf(
            argb(0xFF, 0x11, 0x25, 0x33),
            argb(0xFF, 0x11, 0x25, 0x33),
            argb(0xFF, 0x11, 0x25, 0x33),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lCopyLengthWebp(width = 3, height = 1))!!
        val dst = SkBitmap(
            width = 3,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L color cache references`() {
        val expected = intArrayOf(
            argb(0xFF, 0x31, 0x42, 0x53),
            argb(0xFF, 0x31, 0x42, 0x53),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorCacheWebp(width = 2, height = 1, expected[0]))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L subtract green transform`() {
        val expected = intArrayOf(
            argb(0xFF, 0x40, 0x25, 0x58),
            argb(0x80, 0x04, 0xFE, 0x13),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lSubtractGreenWebp(width = 2, height = 1, expected))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L predictor transform modes`() {
        val width = 5
        val height = 3
        val residuals = IntArray(width * height) { argb(0x00, 0x03, 0x05, 0x07) }

        for (mode in 0..13) {
            val expected = applyPredictorFixture(width, height, mode, residuals)
            val codec = SkWebpKotlinCodec.Decoder.make(vp8lPredictorWebp(width, height, mode, residuals))!!

            assertWebpPixels(codec, width, height, expected)
        }
    }

    @Test
    fun `decodes VP8L predictor transform border rules`() {
        val width = 4
        val height = 3
        val residuals = IntArray(width * height) { argb(0x00, 0x01, 0x02, 0x03) }
        val expected = applyPredictorFixture(width, height, mode = 3, residuals)
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lPredictorWebp(width, height, mode = 3, residuals))!!

        assertEquals(argb(0xFF, 0x01, 0x02, 0x03), expected[0])
        assertEquals(argb(0xFF, 0x02, 0x04, 0x06), expected[1]) // top row uses L.
        assertEquals(argb(0xFF, 0x02, 0x04, 0x06), expected[width]) // left column uses T.
        assertEquals(argb(0xFF, 0x03, 0x06, 0x09), expected[width * 2 - 1]) // right edge TR wraps to row start.
        assertWebpPixels(codec, width, height, expected)
    }

    @Test
    fun `decodes VP8L color transform`() {
        val expected = intArrayOf(
            argb(0xFF, 0x5A, 0x40, 0x46),
            argb(0xC0, 0x1E, 0x20, 0x78),
        )
        val multipliers = ColorTransformMultipliers(
            greenToRed = 0x20,
            greenToBlue = 0x10,
            redToBlue = 0xE0,
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorTransformWebp(width = 2, height = 1, multipliers, expected))!!

        assertWebpPixels(codec, width = 2, height = 1, expected)
    }

    @Test
    fun `decodes VP8L fixture with combined predictor color and subtract green transforms`() {
        val expected = intArrayOf(
            argb(0xFF, 40, 20, 60),
            argb(0xFF, 45, 25, 65),
            argb(0xFF, 45, 25, 65),
            argb(0xFF, 50, 30, 70),
        )
        val multipliers = ColorTransformMultipliers(
            greenToRed = 0x20,
            greenToBlue = 0x10,
            redToBlue = 0xE0,
        )
        val codec = SkWebpKotlinCodec.Decoder.make(
            vp8lCombinedTransformsWebp(
                width = 2,
                height = 2,
                predictorMode = 1,
                multipliers = multipliers,
                argb = expected,
            ),
        )!!

        assertWebpPixels(codec, width = 2, height = 2, expected)
    }

    @Test
    fun `decodes representative VP8L lossless fixture corpus`() {
        val literalGrid = IntArray(16 * 16) { i ->
            if (((i / 16) + (i % 16)) % 2 == 0) {
                argb(0xFF, 0x00, 0x00, 0xFF)
            } else {
                argb(0x80, 0xFF, 0x00, 0x00)
            }
        }
        val palette = intArrayOf(
            argb(0xFF, 0x00, 0x00, 0xFF),
            argb(0x80, 0xFF, 0x00, 0x00),
        )
        val paletteIndexes = IntArray(literalGrid.size) { i ->
            if (((i / 16) + (i % 16)) % 2 == 0) 0 else 1
        }
        val transformTarget = intArrayOf(
            argb(0xFF, 40, 20, 60),
            argb(0xFF, 45, 25, 65),
            argb(0xFF, 45, 25, 65),
            argb(0xFF, 50, 30, 70),
        )
        val multipliers = ColorTransformMultipliers(
            greenToRed = 0x20,
            greenToBlue = 0x10,
            redToBlue = 0xE0,
        )
        val corpus = listOf(
            Vp8lFixtureCorpusCase(
                name = "literal-alpha-grid",
                width = 16,
                height = 16,
                expected = literalGrid,
                encoded = vp8lLiteralWebp(width = 16, height = 16, literalGrid),
            ),
            Vp8lFixtureCorpusCase(
                name = "palette-alpha-grid",
                width = 16,
                height = 16,
                expected = literalGrid,
                encoded = vp8lColorIndexingWebp(width = 16, height = 16, palette, paletteIndexes),
            ),
            Vp8lFixtureCorpusCase(
                name = "lz77-copy-run",
                width = 3,
                height = 1,
                expected = intArrayOf(
                    argb(0xFF, 0x11, 0x25, 0x33),
                    argb(0xFF, 0x11, 0x25, 0x33),
                    argb(0xFF, 0x11, 0x25, 0x33),
                ),
                encoded = vp8lCopyLengthWebp(width = 3, height = 1),
            ),
            Vp8lFixtureCorpusCase(
                name = "predictor-color-subtract-green",
                width = 2,
                height = 2,
                expected = transformTarget,
                encoded = vp8lCombinedTransformsWebp(
                    width = 2,
                    height = 2,
                    predictorMode = 1,
                    multipliers = multipliers,
                    argb = transformTarget,
                ),
            ),
            Vp8lFixtureCorpusCase(
                name = "color-cache-reference",
                width = 2,
                height = 1,
                expected = intArrayOf(
                    argb(0xFF, 0x31, 0x42, 0x53),
                    argb(0xFF, 0x31, 0x42, 0x53),
                ),
                encoded = vp8lColorCacheWebp(width = 2, height = 1, pixel = argb(0xFF, 0x31, 0x42, 0x53)),
            ),
        )

        for (case in corpus) {
            val codec = SkWebpKotlinCodec.Decoder.make(case.encoded) as SkWebpKotlinCodec?
            assertNotNull(codec, case.name)
            assertEquals(WebpBitstreamFormat.VP8L, codec!!.metadata.format, case.name)
            assertWebpPixels(codec, case.width, case.height, case.expected)
        }
    }

    @Test
    fun `decodes VP8L packed color indexing transform`() {
        val table = intArrayOf(
            argb(0xFF, 0x10, 0x20, 0x30),
            argb(0xFF, 0x20, 0x40, 0x60),
            argb(0xFF, 0x30, 0x60, 0x90),
            argb(0xFF, 0x40, 0x80, 0xC0),
        )
        val indices = intArrayOf(0, 1, 2, 3, 0)
        val expected = IntArray(indices.size) { table[indices[it]] }
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorIndexingWebp(width = 5, height = 1, table, indices))!!

        assertWebpPixels(codec, width = 5, height = 1, expected)
    }

    @Test
    fun `decodes VP8L unbundled color indexing transform and transparent invalid index`() {
        val table = IntArray(17) { i -> argb(0xFF, i, i * 2, i * 3) }
        val indices = intArrayOf(16, 17)
        val expected = intArrayOf(table[16], 0)
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorIndexingWebp(width = 2, height = 1, table, indices))!!

        assertWebpPixels(codec, width = 2, height = 1, expected)
    }

    @Test
    fun `decodes VP8L color indexing copy distances using packed width`() {
        val table = intArrayOf(
            argb(0xFF, 0x10, 0x20, 0x30),
            argb(0xFF, 0x20, 0x40, 0x60),
            argb(0xFF, 0x30, 0x60, 0x90),
            argb(0xFF, 0x40, 0x80, 0xC0),
        )
        val indices = intArrayOf(
            0, 1, 2, 3,
            0, 1, 2, 3,
        )
        val expected = IntArray(indices.size) { table[indices[it]] }
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorIndexingCopyWebp(width = 4, height = 2, table, indices))!!

        assertWebpPixels(codec, width = 4, height = 2, expected)
    }

    @Test
    fun `VP8L pixel decode rejects invalid color cache size`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lInvalidColorCacheWebp(width = 1, height = 1))!!
        val dst = SkBitmap(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `VP8L pixel decode rejects truncated normal Huffman code`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lNormalHuffmanWebp(width = 1, height = 1))!!
        val dst = SkBitmap(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `rejects truncated and metadata-less RIFF WEBP`() {
        assertNull(SkWebpKotlinCodec.Decoder.make(byteArrayOf('R'.code.toByte(), 'I'.code.toByte())))
        assertNull(SkWebpKotlinCodec.Decoder.make(riff("WEBP", chunk("VP8X", ByteArray(9)))))
        assertNull(SkWebpKotlinCodec.Decoder.make(riff("WEBP", chunk("ALPH", byteArrayOf(1, 2, 3)))))
        val declaredTooLarge = riff("WEBP", chunk("VP8X", ByteArray(10))).copyOf(20)
        assertNull(SkWebpKotlinCodec.Decoder.make(declaredTooLarge))
    }

    private fun vp8xWebp(width: Int, height: Int, flags: Int): ByteArray {
        return riff("WEBP", vp8xChunk(width, height, flags))
    }

    private fun vp8xChunk(width: Int, height: Int, flags: Int): ByteArray {
        val payload = ByteArray(10)
        payload[0] = flags.toByte()
        write24LE(payload, 4, width - 1)
        write24LE(payload, 7, height - 1)
        return chunk("VP8X", payload)
    }

    private fun animChunk(background: Int, loopCount: Int): ByteArray {
        val payload = ByteArray(6)
        payload[0] = blue(background).toByte()
        payload[1] = green(background).toByte()
        payload[2] = red(background).toByte()
        payload[3] = alpha(background).toByte()
        writeU16LE(payload, 4, loopCount)
        return chunk("ANIM", payload)
    }

    private fun anmfChunk(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        durationMs: Int,
        flags: Int,
        frameChunks: Array<ByteArray>,
    ): ByteArray {
        require((x and 1) == 0 && (y and 1) == 0)
        val payload = ByteArrayOutputStream()
        val header = ByteArray(16)
        write24LE(header, 0, x / 2)
        write24LE(header, 3, y / 2)
        write24LE(header, 6, width - 1)
        write24LE(header, 9, height - 1)
        write24LE(header, 12, durationMs)
        header[15] = flags.toByte()
        payload.write(header)
        for (frameChunk in frameChunks) payload.write(frameChunk)
        return chunk("ANMF", payload.toByteArray())
    }

    private fun vp8lWebp(width: Int, height: Int): ByteArray {
        return riff("WEBP", vp8lChunk(width, height))
    }

    private fun vp8lChunk(width: Int, height: Int): ByteArray {
        val bits = ((width - 1) and 0x3FFF) or (((height - 1) and 0x3FFF) shl 14)
        val payload = ByteArray(5)
        payload[0] = 0x2F
        payload[1] = (bits and 0xFF).toByte()
        payload[2] = ((bits ushr 8) and 0xFF).toByte()
        payload[3] = ((bits ushr 16) and 0xFF).toByte()
        payload[4] = ((bits ushr 24) and 0xFF).toByte()
        return chunk("VP8L", payload)
    }

    private fun vp8lLiteralWebp(width: Int, height: Int, argb: IntArray): ByteArray {
        return riff("WEBP", vp8lLiteralChunk(width, height, argb))
    }

    private fun vp8lLiteralChunk(width: Int, height: Int, argb: IntArray): ByteArray {
        require(argb.size == width * height)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = argb.uniqueChannel { (it ushr 8) and 0xFF }
        val red = argb.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = argb.uniqueChannel { it and 0xFF }
        val alpha = argb.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0)) // distance alphabet is unused by literal-only pixels.
        for (pixel in argb) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
        return chunk("VP8L", byteArrayOf(0x2F) + writer.toByteArray())
    }

    private fun vp8lNormalHuffmanWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        writer.writeBits(0, 1) // normal Huffman code with no complete code description.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lNormalSingleSymbolWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        repeat(5) {
            writeNormalSingleSymbolCode(writer, symbol = 0)
        }
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lCopyLengthWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        writeNormalTwoSymbolCode(writer, first = 0x25, second = 257)
        writeSimpleCode(writer, intArrayOf(0x11))
        writeSimpleCode(writer, intArrayOf(0x33))
        writeSimpleCode(writer, intArrayOf(0xFF))
        writeSimpleCode(writer, intArrayOf(1)) // distance prefix 1 maps to the left pixel.
        writer.writeBits(0, 1) // literal green 0x25.
        writer.writeBits(1, 1) // length prefix 1 => copy two pixels.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lColorCacheWebp(width: Int, height: Int, pixel: Int): ByteArray {
        require(width * height == 2)
        val colorCacheBits = 3
        val colorCacheIndex = colorCacheIndex(pixel, colorCacheBits)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(1, 1) // color_cache_present
        writer.writeBits(colorCacheBits, 4)
        writer.writeBits(0, 1) // meta_prefix_present
        val greenSymbols = intArrayOf(green(pixel), 256 + 24 + colorCacheIndex)
        writeNormalTwoSymbolCode(writer, first = greenSymbols[0], second = greenSymbols[1])
        writeSimpleCode(writer, intArrayOf(red(pixel)))
        writeSimpleCode(writer, intArrayOf(blue(pixel)))
        writeSimpleCode(writer, intArrayOf(alpha(pixel)))
        writeSimpleCode(writer, intArrayOf(0)) // distance alphabet is unused.
        writer.writeBits(0, 1) // literal pixel.
        writer.writeBits(1, 1) // color cache reference.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lSubtractGreenWebp(width: Int, height: Int, argb: IntArray): ByteArray {
        require(argb.size == width * height)
        val transformed = IntArray(argb.size) { i ->
            val pixel = argb[i]
            val green = green(pixel)
            argb(
                alpha = alpha(pixel),
                red = (red(pixel) - green) and 0xFF,
                green = green,
                blue = (blue(pixel) - green) and 0xFF,
            )
        }
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(2, 2) // subtract green transform.
        writer.writeBits(0, 1) // transform_present terminator
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = transformed.uniqueChannel { (it ushr 8) and 0xFF }
        val red = transformed.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = transformed.uniqueChannel { it and 0xFF }
        val alpha = transformed.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0))
        for (pixel in transformed) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lPredictorWebp(width: Int, height: Int, mode: Int, residuals: IntArray): ByteArray {
        require(residuals.size == width * height)
        require(mode in 0..13)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(0, 2) // predictor transform.
        writer.writeBits(0, 3) // size_bits = 2, so this tiny fixture has one predictor block.
        writeVp8lLiteralImageData(writer, intArrayOf(argb(0xFF, 0, mode, 0)))
        writer.writeBits(0, 1) // transform_present terminator
        writeVp8lLiteralImageData(writer, residuals)
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lColorTransformWebp(
        width: Int,
        height: Int,
        multipliers: ColorTransformMultipliers,
        argb: IntArray,
    ): ByteArray {
        require(argb.size == width * height)
        val transformed = IntArray(argb.size) { i -> applyColorTransformFixture(argb[i], multipliers) }
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(1, 2) // color transform.
        writer.writeBits(0, 3) // size_bits = 2, so this tiny fixture has one multiplier block.
        writeVp8lLiteralImageData(
            writer,
            intArrayOf(argb(0xFF, multipliers.redToBlue, multipliers.greenToBlue, multipliers.greenToRed)),
        )
        writer.writeBits(0, 1) // transform_present terminator
        writeVp8lLiteralImageData(writer, transformed)
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lCombinedTransformsWebp(
        width: Int,
        height: Int,
        predictorMode: Int,
        multipliers: ColorTransformMultipliers,
        argb: IntArray,
    ): ByteArray {
        require(argb.size == width * height)
        require(predictorMode in 0..13)
        val predictorResiduals = predictorResiduals(width, predictorMode, argb)
        val colorTransformed = IntArray(argb.size) { i ->
            applyColorTransformFixture(predictorResiduals[i], multipliers)
        }
        val transformed = IntArray(argb.size) { i -> applySubtractGreenFixture(colorTransformed[i]) }
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(0, 2) // predictor transform.
        writer.writeBits(0, 3) // size_bits = 2, so this tiny fixture has one predictor block.
        writeVp8lLiteralImageData(writer, intArrayOf(argb(0xFF, 0, predictorMode, 0)))
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(1, 2) // color transform.
        writer.writeBits(0, 3) // size_bits = 2, so this tiny fixture has one multiplier block.
        writeVp8lLiteralImageData(
            writer,
            intArrayOf(argb(0xFF, multipliers.redToBlue, multipliers.greenToBlue, multipliers.greenToRed)),
        )
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(2, 2) // subtract green transform.
        writer.writeBits(0, 1) // transform_present terminator
        writeVp8lLiteralImageData(writer, transformed)
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lColorIndexingWebp(width: Int, height: Int, table: IntArray, indices: IntArray): ByteArray {
        require(table.size in 1..256)
        require(indices.size == width * height)
        val widthBits = colorIndexingWidthBits(table.size)
        val packedWidth = (width + (1 shl widthBits) - 1) / (1 shl widthBits)
        val indexedPixels = packColorIndexes(indices, width, height, packedWidth, widthBits)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(3, 2) // color indexing transform.
        writer.writeBits(table.size - 1, 8)
        writeVp8lLiteralImageData(writer, colorTableDeltas(table))
        writer.writeBits(0, 1) // transform_present terminator
        writeVp8lLiteralImageData(writer, indexedPixels)
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lColorIndexingCopyWebp(width: Int, height: Int, table: IntArray, indices: IntArray): ByteArray {
        require(table.size in 1..4)
        require(indices.size == width * height)
        val widthBits = colorIndexingWidthBits(table.size)
        val packedWidth = (width + (1 shl widthBits) - 1) / (1 shl widthBits)
        require(packedWidth == 1 && height == 2)
        val indexedPixels = packColorIndexes(indices, width, height, packedWidth, widthBits)
        require(indexedPixels[0] == indexedPixels[1])

        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(3, 2) // color indexing transform.
        writer.writeBits(table.size - 1, 8)
        writeVp8lLiteralImageData(writer, colorTableDeltas(table))
        writer.writeBits(0, 1) // transform_present terminator
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        writeNormalTwoSymbolCode(writer, first = green(indexedPixels[0]), second = 256)
        writeSimpleCode(writer, intArrayOf(red(indexedPixels[0])))
        writeSimpleCode(writer, intArrayOf(blue(indexedPixels[0])))
        writeSimpleCode(writer, intArrayOf(alpha(indexedPixels[0])))
        writeSimpleCode(writer, intArrayOf(0)) // distance code 1 maps to the previous packed row.
        writer.writeBits(0, 1) // literal packed indexes for row 0.
        writer.writeBits(1, 1) // length prefix 0 => copy one packed pixel for row 1.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lInvalidColorCacheWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(1, 1) // color_cache_present
        writer.writeBits(0, 4) // invalid: valid color cache bits are 1..11.
        return vp8lWebpFromBits(writer)
    }

    private fun writeVp8lLiteralImageData(writer: Vp8lTestBitWriter, argb: IntArray) {
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = argb.uniqueChannel { (it ushr 8) and 0xFF }
        val red = argb.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = argb.uniqueChannel { it and 0xFF }
        val alpha = argb.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0))
        for (pixel in argb) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
    }

    private fun writeVp8lHeaderBits(writer: Vp8lTestBitWriter, width: Int, height: Int) {
        writer.writeBits(width - 1, 14)
        writer.writeBits(height - 1, 14)
        writer.writeBits(1, 1) // alpha_is_used
        writer.writeBits(0, 3) // version
    }

    private fun writeSimpleCode(writer: Vp8lTestBitWriter, symbols: IntArray) {
        require(symbols.size in 1..2)
        writer.writeBits(1, 1) // simple code
        writer.writeBits(symbols.size - 1, 1)
        writer.writeBits(1, 1) // first symbol uses 8 bits.
        writer.writeBits(symbols[0], 8)
        if (symbols.size == 2) writer.writeBits(symbols[1], 8)
    }

    private fun writeNormalSingleSymbolCode(writer: Vp8lTestBitWriter, symbol: Int) {
        require(symbol in 0..1)
        writer.writeBits(0, 1) // normal code
        writer.writeBits(0, 4) // four code length code lengths.
        writer.writeBits(0, 3) // symbol 17 length.
        writer.writeBits(0, 3) // symbol 18 length.
        writer.writeBits(1, 3) // symbol 0 length.
        writer.writeBits(1, 3) // symbol 1 length.
        writer.writeBits(1, 1) // custom max_symbol.
        writer.writeBits(0, 3) // two bits encode max_symbol.
        writer.writeBits(0, 2) // max_symbol = 2.
        writer.writeBits(if (symbol == 0) 1 else 0, 1)
        writer.writeBits(if (symbol == 0) 0 else 1, 1)
    }

    private fun writeNormalTwoSymbolCode(writer: Vp8lTestBitWriter, first: Int, second: Int) {
        require(first < second)
        writer.writeBits(0, 1) // normal code
        writer.writeBits(0, 4) // four code length code lengths.
        writer.writeBits(0, 3) // symbol 17 length.
        writer.writeBits(1, 3) // symbol 18 length.
        writer.writeBits(0, 3) // symbol 0 length.
        writer.writeBits(1, 3) // symbol 1 length.
        writer.writeBits(1, 1) // custom max_symbol.
        writer.writeBits(4, 3) // ten bits encode max_symbol.
        writer.writeBits(second + 1 - 2, 10)
        writeCodeLengthZeroRun(writer, first)
        writeCodeLengthOne(writer)
        writeCodeLengthZeroRun(writer, second - first - 1)
        writeCodeLengthOne(writer)
    }

    private fun writeCodeLengthOne(writer: Vp8lTestBitWriter) {
        writer.writeBits(0, 1)
    }

    private fun writeCodeLengthZeroRun(writer: Vp8lTestBitWriter, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val repeat = minOf(remaining, 138)
            require(repeat >= 11)
            writer.writeBits(1, 1) // code length code symbol 18.
            writer.writeBits(repeat - 11, 7)
            remaining -= repeat
        }
    }

    private fun vp8lWebpFromBits(writer: Vp8lTestBitWriter): ByteArray =
        riff("WEBP", chunk("VP8L", byteArrayOf(0x2F) + writer.toByteArray()))

    private fun IntArray.uniqueChannel(component: (Int) -> Int): IntArray =
        map(component).distinct().sorted().also { require(it.size <= 2) }.toIntArray()

    private fun vp8Webp(width: Int, height: Int): ByteArray {
        return riff("WEBP", vp8Chunk(width, height))
    }

    private fun vp8Chunk(width: Int, height: Int, firstPartitionSize: Int = 0): ByteArray =
        chunk("VP8 ", vp8Payload(width, height, firstPartitionSize))

    private fun vp8ChunkWithPartition(width: Int, height: Int, partition: ByteArray): ByteArray =
        chunk("VP8 ", vp8Payload(width, height, partition.size) + partition)

    private fun vp8ChunkWithPartitions(width: Int, height: Int, firstPartition: ByteArray, coefficients: ByteArray): ByteArray =
        chunk("VP8 ", vp8Payload(width, height, firstPartition.size) + firstPartition + coefficients)

    private fun vp8SupportedNonBPredWebp(width: Int, height: Int): ByteArray =
        riff("WEBP", vp8ChunkWithPartitions(width, height, vp8FirstPartition(), ByteArray(64)))

    private fun vp8FirstPartition(simpleFilter: Boolean = false, filterLevel: Int = 0): ByteArray =
        Vp8TestBitWriter().apply {
            writeBit(0) // YUV color space.
            writeBit(0) // No pixel value clamp.
            writeBit(0) // Segmentation disabled.
            writeBit(if (simpleFilter) 1 else 0)
            writeLiteral(filterLevel, 6)
            writeLiteral(0, 3)
            writeBit(0) // Loop filter deltas disabled.
            writeLiteral(0, 2) // One coefficient partition.
            writeLiteral(0, 7)
            repeat(5) { writeSignedDelta(0) }
            repeat(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) {
                writeBit(0)
            }
            writeBit(0) // Macroblock skip flags omitted.
            writeBit(0) // Y DC mode.
            writeBit(0) // UV DC mode.
        }.toByteArray()

    private fun vp8BPredFirstPartition(): ByteArray =
        Vp8TestBitWriter().apply {
            writeBit(0) // YUV color space.
            writeBit(0) // No pixel value clamp.
            writeBit(0) // Segmentation disabled.
            writeBit(0) // Normal loop filter disabled by level 0.
            writeLiteral(0, 6)
            writeLiteral(0, 3)
            writeBit(0) // Loop filter deltas disabled.
            writeLiteral(0, 2) // One coefficient partition.
            writeLiteral(0, 7)
            repeat(5) { writeSignedDelta(0) }
            repeat(VP8_COEFFICIENT_PROBABILITY_COUNT_FOR_TEST) {
                writeBit(0)
            }
            writeBit(0) // Macroblock skip flags omitted.
            repeat(4) { writeBit(1) } // B_PRED luma mode.
            repeat(16) { writeBit(0) } // B_DC subblock modes.
            writeBit(0) // UV DC mode.
        }.toByteArray()

    private fun vp8Payload(
        width: Int,
        height: Int,
        firstPartitionSize: Int = 0,
        version: Int = 0,
        showFrame: Boolean = true,
    ): ByteArray {
        val payload = ByteArray(10)
        val tag = (firstPartitionSize shl 5) or
            ((if (showFrame) 1 else 0) shl 4) or
            (version shl 1)
        payload[0] = (tag and 0xFF).toByte()
        payload[1] = ((tag ushr 8) and 0xFF).toByte()
        payload[2] = ((tag ushr 16) and 0xFF).toByte()
        payload[3] = 0x9D.toByte()
        payload[4] = 0x01
        payload[5] = 0x2A
        writeU16LE(payload, 6, width)
        writeU16LE(payload, 8, height)
        return payload
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun alphaChunk(control: Int, payload: ByteArray): ByteArray =
        chunk("ALPH", byteArrayOf(control.toByte()) + payload)

    private fun alphaControl(
        compression: WebpAlphaCompression,
        filtering: Int = 0,
        preprocessing: Int = 0,
    ): Int {
        require(filtering in 0..3)
        require(preprocessing in 0..3)
        val compressionBits = when (compression) {
            WebpAlphaCompression.NONE -> 0
            WebpAlphaCompression.LOSSLESS -> 1
        }
        return (preprocessing shl 4) or (filtering shl 2) or compressionBits
    }

    private fun riff(type: String, vararg chunks: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(type.toByteArray(Charsets.US_ASCII))
        for (chunk in chunks) payload.write(chunk)
        val payloadBytes = payload.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeU32LE(payloadBytes.size)
            write(payloadBytes)
        }.toByteArray()
    }

    private fun chunk(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(type.toByteArray(Charsets.US_ASCII))
            writeU32LE(payload.size)
            write(payload)
            if ((payload.size and 1) != 0) write(0)
        }.toByteArray()

    private fun write24LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    }

    private fun writeU16LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun alpha(pixel: Int): Int = (pixel ushr 24) and 0xFF
    private fun red(pixel: Int): Int = (pixel ushr 16) and 0xFF
    private fun green(pixel: Int): Int = (pixel ushr 8) and 0xFF
    private fun blue(pixel: Int): Int = pixel and 0xFF

    private data class ColorTransformMultipliers(
        val greenToRed: Int,
        val greenToBlue: Int,
        val redToBlue: Int,
    )

    private data class Vp8lFixtureCorpusCase(
        val name: String,
        val width: Int,
        val height: Int,
        val expected: IntArray,
        val encoded: ByteArray,
    )

    private fun assertWebpPixels(codec: SkCodec, width: Int, height: Int, expected: IntArray) {
        val dst = SkBitmap(
            width = width,
            height = height,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (y in 0 until height) {
            for (x in 0 until width) {
                val expectedPixel = expected[y * width + x]
                val actual = dst.getPixel(x, y)
                assertEquals(alpha(expectedPixel), alpha(actual))
                assertEquals(red(expectedPixel), red(actual))
                assertEquals(green(expectedPixel), green(actual))
                assertEquals(blue(expectedPixel), blue(actual))
            }
        }
    }

    private fun applyPredictorFixture(width: Int, height: Int, mode: Int, residuals: IntArray): IntArray {
        require(residuals.size == width * height)
        val pixels = IntArray(residuals.size)
        for (i in residuals.indices) {
            val x = i % width
            val y = i / width
            val predictor = when {
                x == 0 && y == 0 -> argb(0xFF, 0, 0, 0)
                y == 0 -> pixels[i - 1]
                x == 0 -> pixels[i - width]
                else -> predictorFixturePixel(pixels, width, x, y, mode)
            }
            pixels[i] = addPixels(residuals[i], predictor)
        }
        return pixels
    }

    private fun applyColorTransformFixture(pixel: Int, multipliers: ColorTransformMultipliers): Int {
        val transformedRed = (red(pixel) - colorTransformDelta(multipliers.greenToRed, green(pixel))) and 0xFF
        val transformedBlue = (
            blue(pixel) -
                colorTransformDelta(multipliers.greenToBlue, green(pixel)) -
                colorTransformDelta(multipliers.redToBlue, red(pixel))
            ) and 0xFF
        return argb(alpha(pixel), transformedRed, green(pixel), transformedBlue)
    }

    private fun applySubtractGreenFixture(pixel: Int): Int =
        argb(
            alpha = alpha(pixel),
            red = (red(pixel) - green(pixel)) and 0xFF,
            green = green(pixel),
            blue = (blue(pixel) - green(pixel)) and 0xFF,
        )

    private fun predictorResiduals(width: Int, mode: Int, pixels: IntArray): IntArray {
        val residuals = IntArray(pixels.size)
        for (i in pixels.indices) {
            val x = i % width
            val y = i / width
            val predictor = when {
                x == 0 && y == 0 -> argb(0xFF, 0, 0, 0)
                y == 0 -> pixels[i - 1]
                x == 0 -> pixels[i - width]
                else -> predictorFixturePixel(pixels, width, x, y, mode)
            }
            residuals[i] = subtractPixels(pixels[i], predictor)
        }
        return residuals
    }

    private fun colorTransformDelta(multiplier: Int, color: Int): Int =
        (signedByte(multiplier) * signedByte(color)) shr 5

    private fun signedByte(value: Int): Int =
        if ((value and 0x80) == 0) value and 0xFF else (value and 0xFF) - 256

    private fun colorTableDeltas(table: IntArray): IntArray {
        val deltas = IntArray(table.size)
        var previous = 0
        for (i in table.indices) {
            deltas[i] = subtractPixels(table[i], previous)
            previous = table[i]
        }
        return deltas
    }

    private fun packColorIndexes(
        indices: IntArray,
        width: Int,
        height: Int,
        packedWidth: Int,
        widthBits: Int,
    ): IntArray {
        val packed = IntArray(packedWidth * height)
        val pixelsPerPackedPixel = 1 shl widthBits
        val indexBits = 8 ushr widthBits
        val mask = (1 shl indexBits) - 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = indices[y * width + x]
                require(index and mask == index)
                val packedOffset = y * packedWidth + x / pixelsPerPackedPixel
                val shift = (x and (pixelsPerPackedPixel - 1)) * indexBits
                val green = green(packed[packedOffset]) or (index shl shift)
                packed[packedOffset] = argb(0xFF, 0, green, 0)
            }
        }
        return packed
    }

    private fun colorIndexingWidthBits(tableSize: Int): Int =
        when (tableSize) {
            in 1..2 -> 3
            in 3..4 -> 2
            in 5..16 -> 1
            else -> 0
        }

    private fun predictorFixturePixel(pixels: IntArray, width: Int, x: Int, y: Int, mode: Int): Int {
        val left = pixels[y * width + x - 1]
        val top = pixels[(y - 1) * width + x]
        val topLeft = pixels[(y - 1) * width + x - 1]
        val topRight = if (x == width - 1) pixels[y * width] else pixels[(y - 1) * width + x + 1]
        return when (mode) {
            0 -> argb(0xFF, 0, 0, 0)
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

    private fun addPixels(residual: Int, predictor: Int): Int =
        argb(
            alpha = (alpha(residual) + alpha(predictor)) and 0xFF,
            red = (red(residual) + red(predictor)) and 0xFF,
            green = (green(residual) + green(predictor)) and 0xFF,
            blue = (blue(residual) + blue(predictor)) and 0xFF,
        )

    private fun averagePixels(a: Int, b: Int): Int =
        argb(
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
        argb(
            alpha = clampByte(alpha(left) + alpha(top) - alpha(topLeft)),
            red = clampByte(red(left) + red(top) - red(topLeft)),
            green = clampByte(green(left) + green(top) - green(topLeft)),
            blue = clampByte(blue(left) + blue(top) - blue(topLeft)),
        )

    private fun clampAddSubtractHalf(average: Int, topLeft: Int): Int =
        argb(
            alpha = clampByte(alpha(average) + (alpha(average) - alpha(topLeft)) / 2),
            red = clampByte(red(average) + (red(average) - red(topLeft)) / 2),
            green = clampByte(green(average) + (green(average) - green(topLeft)) / 2),
            blue = clampByte(blue(average) + (blue(average) - blue(topLeft)) / 2),
        )

    private fun clampByte(value: Int): Int = value.coerceIn(0, 255)

    private fun vp8CoefficientTestHeader(
        macroblockWidth: Int,
        macroblockHeight: Int,
        partitionCount: Int,
    ): Vp8LossyFrameHeader =
        Vp8LossyFrameHeader(
            colorSpace = 0,
            clampType = 0,
            macroblockWidth = macroblockWidth,
            macroblockHeight = macroblockHeight,
            coefficientPartitionCount = partitionCount,
            loopFilter = Vp8LoopFilterHeader(simpleFilter = false, level = 0, sharpness = 0),
            quantization = Vp8QuantizationHeader(
                yAcIndex = 0,
                yDcDelta = 0,
                y2DcDelta = 0,
                y2AcDelta = 0,
                uvDcDelta = 0,
                uvAcDelta = 0,
            ),
        )

    private fun zeroVp8MacroblockCoefficients(): Vp8MacroblockCoefficients =
        Vp8MacroblockCoefficients(
            y2 = IntArray(16),
            luma = Array(16) { IntArray(16) },
            u = Array(4) { IntArray(16) },
            v = Array(4) { IntArray(16) },
        )

    private fun colorCacheIndex(pixel: Int, bits: Int): Int =
        (pixel * 0x1e35a7bd) ushr (32 - bits)

    private fun subtractPixels(pixel: Int, predictor: Int): Int =
        argb(
            alpha = (alpha(pixel) - alpha(predictor)) and 0xFF,
            red = (red(pixel) - red(predictor)) and 0xFF,
            green = (green(pixel) - green(predictor)) and 0xFF,
            blue = (blue(pixel) - blue(predictor)) and 0xFF,
        )

    private fun ByteArrayOutputStream.writeU32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private class Vp8lTestBitWriter {
        private val bytes = ArrayList<Int>()
        private var bitOffset = 0

        fun writeBits(value: Int, count: Int) {
            for (i in 0 until count) {
                if ((bitOffset and 7) == 0) bytes.add(0)
                val bit = (value ushr i) and 1
                val index = bytes.lastIndex
                bytes[index] = bytes[index] or (bit shl (bitOffset and 7))
                bitOffset++
            }
        }

        fun writeSymbol(symbols: IntArray, value: Int) {
            if (symbols.size == 1) {
                require(symbols[0] == value)
                return
            }
            writeBits(symbols.indexOf(value), 1)
        }

        fun toByteArray(): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }
    }

    private class Vp8TestBitWriter {
        private val bytes = ArrayList<Int>()
        private var currentByte = 0
        private var bitCount = 0

        fun writeBit(bit: Int) {
            require(bit == 0 || bit == 1)
            currentByte = currentByte or (bit shl (7 - bitCount))
            bitCount++
            if (bitCount == 8) flush()
        }

        fun writeLiteral(value: Int, bitCount: Int) {
            require(bitCount in 0..31)
            for (i in bitCount - 1 downTo 0) {
                writeBit((value ushr i) and 1)
            }
        }

        fun writeSignedDelta(value: Int) {
            require(value in -15..15)
            if (value == 0) {
                writeBit(0)
                return
            }
            writeBit(1)
            writeLiteral(kotlin.math.abs(value), 4)
            writeBit(if (value < 0) 1 else 0)
        }

        fun toByteArray(): ByteArray {
            if (bitCount > 0) flush()
            bytes += 0
            bytes += 0
            return ByteArray(bytes.size) { bytes[it].toByte() }
        }

        private fun flush() {
            bytes += currentByte
            currentByte = 0
            bitCount = 0
        }
    }
}
