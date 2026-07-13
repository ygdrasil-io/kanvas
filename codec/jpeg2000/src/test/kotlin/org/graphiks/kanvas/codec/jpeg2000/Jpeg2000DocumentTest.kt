package org.graphiks.kanvas.codec.jpeg2000

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat

class Jpeg2000DocumentTest {

    @Test
    fun `reversible 53 inverse reconstructs an odd length lifting vector`() {
        assertArrayEquals(
            intArrayOf(-3, 2, 7, -1, 4),
            inverseReversible53(low = intArrayOf(-3, 6, 1), high = intArrayOf(0, -6), length = 5),
        )
    }

    @Test
    fun `reversible 53 forward analysis round trips an odd length vector`() {
        val samples = intArrayOf(-60, 17, 103, -44, 91)
        val (low, high) = forwardReversible53(samples)

        assertArrayEquals(samples, inverseReversible53(low, high, samples.size))
    }

    @Test
    fun `zero coding contexts use the directional JPEG 2000 subband tables`() {
        assertEquals(5, j2kZeroCodingContext(0, 1, 0, J2kSubbandOrientation.HL))
        assertEquals(3, j2kZeroCodingContext(1, 0, 0, J2kSubbandOrientation.HL))
        assertEquals(5, j2kZeroCodingContext(1, 0, 0, J2kSubbandOrientation.LH))
        assertEquals(6, j2kZeroCodingContext(0, 0, 2, J2kSubbandOrientation.HH))
        assertEquals(7, j2kZeroCodingContext(1, 0, 2, J2kSubbandOrientation.HH))
        assertEquals(8, j2kZeroCodingContext(0, 0, 3, J2kSubbandOrientation.HH))
        assertEquals(8, j2kZeroCodingContext(1, 2, 2, J2kSubbandOrientation.HL))
    }

    @Test
    fun `pinned OpenJPEG Ndecomp one J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "d3c85260b35e0e9a955abd66326a4dde05867e4f752176030f7a4f962eba0b31",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG odd Ndecomp one J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x3())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "a2c33040c14e8d0cece4ac9ee69a3ed3cbb437b8e46a3e787c5318b587ef612c",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG JP2 pixel fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp0_5x5Jp2())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "b4472ef2e88573ff29f3b1813e10b1ec413d1591532fcb7aa0a88af42f77ac45",
            actual,
        )
    }

    @Test
    fun `OpenJPEG lossless JP2 pixel fixture decodes source dimensions and opaque grayscale RGBA pixels`() {
        val jp2 = Jpeg2000TestFixtures.openJpegLosslessNdecomp0_5x5Jp2()
        val document = requireNotNull(Jpeg2000Document.open(jp2).document)

        val documentResult = document.decode()

        assertNull(documentResult.diagnostic)
        val documentBitmap = requireNotNull(documentResult.bitmap)
        assertEquals(5, documentBitmap.width)
        assertEquals(5, documentBitmap.height)
        assertArrayEquals(
            intArrayOf(
                0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF010101.toInt(), 0xFF808080.toInt(), 0xFF404040.toInt(),
                0xFF101010.toInt(), 0xFFE0E0E0.toInt(), 0xFF3F3F3F.toInt(), 0xFFC0C0C0.toInt(), 0xFFAAAAAA.toInt(),
                0xFF555555.toInt(), 0xFF090909.toInt(), 0xFF808080.toInt(), 0xFFFEFEFE.toInt(), 0xFF7F7F7F.toInt(),
                0xFF202020.toInt(), 0xFFF0F0F0.toInt(), 0xFF2D2D2D.toInt(), 0xFFD1D1D1.toInt(), 0xFF424242.toInt(),
                0xFFBEBEBE.toInt(), 0xFF0C0C0C.toInt(), 0xFF636363.toInt(), 0xFF999999.toInt(), 0xFFC9C9C9.toInt(),
            ),
            documentBitmap.pixels8888,
        )

        val codec = requireNotNull(Codec.MakeFromData(jp2))
        val (codecBitmap, codecResult) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, codecResult)
        assertArrayEquals(documentBitmap.pixels8888, requireNotNull(codecBitmap).pixels8888)
    }

    @Test
    fun `pinned OpenJPEG random odd Ndecomp one J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x5())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "4d20cb6c3b76efbb54d3bba59490e0f0174c118ad053e15ea62bbe89ee561875",
            actual,
        )
    }

    @Test
    fun `Ndecomp one raw J2K reaches the bounded image facade`() {
        val codec = Codec.MakeFromData(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17())

        assertTrue(codec != null, "Ndecomp one must be recognized before the bounded entropy path runs")
    }

    @Test
    fun `OpenJPEG Ndecomp one reversible J2K fixture decodes pixels exactly`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17()
        val codec = requireNotNull(Codec.MakeFromData(codestream))
        val document = requireNotNull(Jpeg2000Document.open(codestream).document)

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result, "document diagnostic=${document.decode().diagnostic}")
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm",
            width = 96,
            height = 17,
        )
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG Ndecomp one odd frame reconstructs both 53 symmetric edges`() {
        val codec = requireNotNull(Codec.MakeFromData(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x3()))

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels()
        assertEquals(5, decoded.width)
        assertEquals(3, decoded.height)
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG Ndecomp one random five by five frame decodes pixels exactly`() {
        val codec = requireNotNull(Codec.MakeFromData(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x5()))

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm",
            width = 5,
            height = 5,
        )
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y decoded=${decoded.pixels8888.joinToString()}",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG Ndecomp two reversible J2K fixture decodes pixels exactly`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp2_8x8()
        val codec = requireNotNull(Codec.MakeFromData(codestream))
        val document = requireNotNull(Jpeg2000Document.open(codestream).document)

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result, "document diagnostic=${document.decode().diagnostic}")
        val decoded = requireNotNull(bitmap)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
        val expected = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm",
            width = 8,
            height = 8,
        )
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG odd Ndecomp two frame reconstructs both 53 symmetric edges`() {
        val codec = requireNotNull(Codec.MakeFromData(Jpeg2000TestFixtures.openJpegLosslessNdecomp2_5x5()))

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm",
            width = 5,
            height = 5,
        )
        assertEquals(5, decoded.width)
        assertEquals(5, decoded.height)
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y decoded=${decoded.pixels8888.joinToString()}",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG odd Ndecomp two codeblocks contain the expected reversible DWT coefficients`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp2_5x5()
        val samples = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm",
            width = 5,
            height = 5,
        ).map { it.toInt() and 0xFF }.map { it - 128 }.toIntArray()
        val levelOne = forwardReversible53Bands(samples, width = 5, height = 5)
        val levelTwo = forwardReversible53Bands(levelOne.ll, width = 3, height = 3)
        val expected = arrayOf(
            levelTwo.ll,
            levelTwo.hl,
            levelTwo.lh,
            levelTwo.hh,
            levelOne.hl,
            levelOne.lh,
            levelOne.hh,
        )
        val reconstructedLevelOne = inverseReversible53Bands(levelTwo, width = 3, height = 3)
        assertArrayEquals(levelOne.ll, reconstructedLevelOne)
        assertArrayEquals(
            samples,
            inverseReversible53Bands(
                Reversible53Bands(reconstructedLevelOne, levelOne.hl, levelOne.lh, levelOne.hh),
                width = 5,
                height = 5,
            ),
        )

        val actual = decodeNdecompTwoCodeblocks(codestream)

        assertEquals(
            expected.map(IntArray::contentToString),
            actual.map(IntArray::contentToString),
        )
    }

    @Test
    fun `Ndecomp two packet stream separates LL coarse and fine codeblock bodies`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp2_8x8()
        val packetOffset = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        } + 2
        val spans = readNdecompTwoPacketSpans(
            packet = codestream.copyOfRange(packetOffset, codestream.size - 2),
            absoluteOffset = packetOffset,
        )

        assertEquals(3, spans.size)
        assertEquals(1, spans[0].header.codeblocks.size)
        assertEquals(3, spans[1].header.codeblocks.size)
        assertEquals(3, spans[2].header.codeblocks.size)
        assertEquals(spans[0].bodyEnd, spans[1].packetOffset)
        assertEquals(spans[1].bodyEnd, spans[2].packetOffset)
        assertEquals(codestream.size - 2, spans[2].bodyEnd)
    }

    @Test
    fun `pinned OpenJPEG Ndecomp two source PGM has its documented SHA-256`() {
        val source = requireNotNull(
            javaClass.getResourceAsStream("/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm"),
        ) { "missing Ndecomp two OpenJPEG source PGM" }.use { input -> input.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(source)
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "776f58efb28e49ed6656bd5d331757c8546b99fda4754f8d3ca7e3ee36601ed9",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG Ndecomp two J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp2_8x8())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "75abf991e34966f1e929baee2666b63f53f8c49be900c35e8c5ec14ae56b2a78",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG odd Ndecomp two source PGM has its documented SHA-256`() {
        val source = requireNotNull(
            javaClass.getResourceAsStream("/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm"),
        ) { "missing odd Ndecomp two OpenJPEG source PGM" }.use { input -> input.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(source)
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "6e2ee7ce0880c67527f1a1dd6fed83703de8b66943dd9d623d1efb0ba5c8b612",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG odd Ndecomp two J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessNdecomp2_5x5())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "fba82a726aa8992d948e763c443b48491e0106d1b3cdc23b9d8b00390dd1a03f",
            actual,
        )
    }

    @Test
    fun `Ndecomp one packet stream separates LL and HL LH HH codeblock bodies`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17()
        val sod = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        }
        val packetOffset = sod + 2
        val spans = readNdecompOnePacketSpans(
            packet = codestream.copyOfRange(packetOffset, codestream.size - 2),
            absoluteOffset = packetOffset,
        )

        assertEquals(2, spans.size)
        assertEquals(3, spans[0].bodyOffset - spans[0].packetOffset)
        assertEquals(listOf(J2kPacketCodeblock(4, 10, 9)), spans[0].header.codeblocks)
        assertEquals(10, spans[1].bodyOffset - spans[1].packetOffset)
        assertEquals(
            listOf(
                J2kPacketCodeblock(8, 22, 278),
                J2kPacketCodeblock(3, 7, 8),
                J2kPacketCodeblock(7, 19, 29),
            ),
            spans[1].header.codeblocks,
        )
        assertEquals(codestream.size - 2, spans[1].bodyEnd)
    }

    @Test
    fun `Ndecomp one HL block accepts normative MQ marker padding`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17()
        val packetOffset = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        } + 2
        val spans = readNdecompOnePacketSpans(
            packet = codestream.copyOfRange(packetOffset, codestream.size - 2),
            absoluteOffset = packetOffset,
        )
        val entry = spans[1].header.codeblocks[0]
        assertDoesNotThrow {
            J2kTier1Decoder(
                width = 48,
                height = 9,
                numBitPlanes = entry.numBitPlanes,
                passes = entry.passes,
                codeblock = codestream.copyOfRange(spans[1].bodyOffset, spans[1].bodyOffset + entry.bodyLength),
                codeblockOffset = spans[1].bodyOffset,
                orientation = J2kSubbandOrientation.HL,
            ).decode()
        }
    }

    @Test
    fun `pinned OpenJPEG two-codeblock source PGM has its documented SHA-256`() {
        val source = requireNotNull(
            javaClass.getResourceAsStream("/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm"),
        ) { "missing two-codeblock OpenJPEG source PGM" }.use { input -> input.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(source)
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "8ea8d1148129457247b37c889415d3f5edbfde4dff929c09280618899a9eaeca",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG two-codeblock J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLosslessTwoCodeblocks96x17())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "edcce815346bf3c8ffc439aea70b831428afb3d0f7b13d58292083c22f032ae7",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG source PGM has its documented SHA-256`() {
        val source = requireNotNull(
            javaClass.getResourceAsStream("/jpeg2000-openjpeg/source.pgm"),
        ) { "missing OpenJPEG source PGM" }.use { input -> input.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(source)
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "2bdf55049e85c305eb510df45d10ce0150d92bac8663cf55e8e8d8b550fbd702",
            actual,
        )
    }

    @Test
    fun `pinned OpenJPEG J2K fixture has its documented SHA-256`() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(Jpeg2000TestFixtures.openJpegLossless5x3())
            .joinToString(separator = "") { byte ->
                byte.toInt().and(0xff).toString(16).padStart(2, '0')
            }

        assertEquals(
            "078395a38f631ae8eb94476d01fe54a1d002a706ca7ad86849b15917cae937b4",
            actual,
        )
    }

    @Test
    fun `OpenJPEG reversible lossless J2K fixture decodes source PGM pixels exactly`() {
        val codec = requireNotNull(Codec.MakeFromData(Jpeg2000TestFixtures.openJpegLossless5x3()))

        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels()
        assertEquals(5, decoded.width)
        assertEquals(3, decoded.height)
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y decoded=${decoded.pixels8888.joinToString()} trace=${fixtureDecisions().joinToString()}",
                )
            }
        }
    }

    @Test
    fun `OpenJPEG two-codeblock reversible J2K fixture decodes both codeblocks pixel for pixel`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessTwoCodeblocks96x17()
        val codec = Codec.MakeFromData(codestream)

        assertTrue(codec != null, "the two-codeblock raw J2K profile must reach the codec facade")
        if (codec == null) return
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val decoded = requireNotNull(bitmap)
        val expected = sourcePgmPixels(
            resource = "/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm",
            width = 96,
            height = 17,
        )
        assertEquals(96, decoded.width)
        assertEquals(17, decoded.height)
        for (y in 0 until decoded.height) {
            for (x in 0 until decoded.width) {
                val sample = expected[y * decoded.width + x].toInt() and 0xFF
                assertEquals(
                    0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                    decoded.getPixel(x, y),
                    "x=$x y=$y",
                )
            }
        }
    }

    @Test
    fun `two horizontal codeblock dimensions reach the image codec facade`() {
        val codestream = narrowLosslessCodestream(width = 65, height = 1)

        assertTrue(Jpeg2000Codec.Decoder.matches(codestream))
        assertTrue(Codec.MakeFromData(codestream) != null)
    }

    @Test
    fun `raw dimensions outside two-codeblock bounds remain outside image facade`() {
        assertNull(Codec.MakeFromData(narrowLosslessCodestream(width = 129, height = 1)))
        assertNull(Codec.MakeFromData(narrowLosslessCodestream(width = 96, height = 65)))
    }

    @Test
    fun `truncated J2K codeblock is refused without yielding a partial bitmap`() {
        val original = Jpeg2000TestFixtures.openJpegLossless5x3()
        val truncated = ByteArray(original.size - 1).also { result ->
            System.arraycopy(original, 0, result, 0, 137)
            System.arraycopy(original, 138, result, 137, 2)
            result[110] = 0
            result[111] = 0
            result[112] = 0
            result[113] = 33
        }

        val codec = requireNotNull(Codec.MakeFromData(truncated))

        assertEquals(Codec.Result.kErrorInInput, codec.getImage().second)
    }

    @Test
    fun `OpenJPEG fixture packet header declares the independently documented codeblock body`() {
        val codestream = Jpeg2000TestFixtures.openJpegLossless5x3()
        val sod = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        }
        val packetOffset = sod + 2
        val packet = codestream.copyOfRange(packetOffset, codestream.size - 2)

        val header = J2kPacketHeader.read(packet, packetOffset)

        assertEquals(8, header.numBitPlanes)
        assertEquals(22, header.passes)
        assertEquals(3, header.bodyOffset)
        assertEquals(17, packet.size - header.bodyOffset)
    }

    @Test
    fun `OpenJPEG two-codeblock fixture packet header splits exactly two bounded bodies`() {
        val codestream = Jpeg2000TestFixtures.openJpegLosslessTwoCodeblocks96x17()
        val sod = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        }
        val packetOffset = sod + 2
        val packet = codestream.copyOfRange(packetOffset, codestream.size - 2)

        val header = J2kPacketHeader.read(packet, packetOffset, codeblockCount = 2)

        assertEquals(2, header.codeblocks.size)
        assertTrue(header.codeblocks.all { it.numBitPlanes in 1..9 && it.passes in 1..22 && it.bodyLength > 0 })
        assertEquals(packet.size - header.bodyOffset, header.codeblocks.sumOf(J2kPacketCodeblock::bodyLength))
    }

    @Test
    fun `MQ probability transitions match every Annex C state`() {
        val nextMps = intArrayOf(
            1, 2, 3, 4, 5, 38, 7, 8, 9, 10, 11, 12, 13, 29, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 45, 46,
        )
        val nextLps = intArrayOf(
            1, 6, 9, 12, 29, 33, 6, 14, 14, 14, 17, 18, 20, 21, 14, 14,
            15, 16, 17, 18, 19, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
            29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 46,
        )
        val lpsSwitchesMps = setOf(0, 6, 14)

        nextMps.indices.forEach { stateIndex ->
            (0..1).forEach { mps ->
                val state = (stateIndex shl 1) or mps
                assertEquals((nextMps[stateIndex] shl 1) or mps, J2kMqTransitions.afterMps(state))
                assertEquals(
                    (nextLps[stateIndex] shl 1) or (mps xor if (stateIndex in lpsSwitchesMps) 1 else 0),
                    J2kMqTransitions.afterLps(state),
                )
            }
        }
    }

    @Test
    fun `EBCOT traversal visits full stripes before the final row tail`() {
        val order = buildList {
            forEachEbcotStripe(width = 2, height = 5) { x, startY, endY ->
                add("$x:$startY-$endY")
            }
        }

        assertEquals(listOf("0:0-4", "1:0-4", "0:4-5", "1:4-5"), order)
    }

    @Test
    fun `negative vertical sign predictor uses OpenJPEG ctx10 and SPB one`() {
        val decisions = fixtureDecisions()
        val verticalSign = decisions.single { decision ->
            decision.pass == "significance" && decision.bitPlane == 7 &&
                decision.x == 0 && decision.y == 1 && decision.context == 10
        }

        assertEquals(1, verticalSign.signPrediction)
        assertEquals(13, decisions.single { decision ->
            decision.pass == "significance" && decision.bitPlane == 7 &&
                decision.x == 1 && decision.y == 1 && decision.context in 9..13
        }.context)
    }

    @Test
    fun `OpenJPEG reversible lossless J2K fixture is owned and decodes through the entropy path`() {
        val codestream = Jpeg2000TestFixtures.openJpegLossless5x3()

        val document = requireNotNull(Jpeg2000Document.open(codestream).document)

        assertEquals(Jpeg2000Container.J2K, document.container)
        assertEquals(Jpeg2000FrameInfo(5, 3, 1, 8), document.frame)
        assertNull(document.decode().diagnostic)
        assertEquals(Codec.Result.kSuccess, requireNotNull(Codec.MakeFromData(codestream)).getImage().second)
    }

    @Test
    fun `raw J2K is owned by the JPEG 2000 provider and exposes a bounded document`() {
        val codestream = narrowLosslessCodestream()

        assertTrue(Jpeg2000Codec.Decoder.matches(codestream))
        val document = requireNotNull(Jpeg2000Document.open(codestream).document)
        val codec = requireNotNull(Codec.MakeFromData(codestream))

        assertEquals(Jpeg2000Container.J2K, document.container)
        assertEquals(1, document.frame.width)
        assertEquals(1, document.frame.height)
        assertEquals(SkEncodedImageFormat.kJPEG2000, codec.getEncodedFormat())
        assertEquals(Codec.Result.kErrorInInput, codec.getImage().second)
        assertEquals(Codec.Result.kErrorInInput, document.decode().diagnostic?.result)
    }

    @Test
    fun `raw dimensions beyond two codeblocks remain structural but are not exposed as an image codec`() {
        val codestream = narrowLosslessCodestream(width = 129, height = 1)

        assertTrue(Jpeg2000Codec.Decoder.matches(codestream))
        assertEquals(129, requireNotNull(Jpeg2000Document.open(codestream).document).frame.width)
        assertNull(Codec.MakeFromData(codestream))
    }

    @Test
    fun `raw J2K refuses a codeblock size other than the proven 64 by 64 profile`() {
        val opened = Jpeg2000Document.open(narrowLosslessCodestream(codeBlockWidth = 3, codeBlockHeight = 4))

        assertNull(opened.document)
        assertEquals("jpeg2000.cod.profile.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 retains top level boxes and routes its sole valid jp2c codestream`() {
        val codestream = narrowLosslessCodestream()
        val jp2 = jp2(codestream)

        val document = requireNotNull(Jpeg2000Document.open(jp2).document)
        val jp2c = document.boxes.single { it.type == "jp2c" }

        assertTrue(Jpeg2000Codec.Decoder.matches(jp2))
        assertEquals(Jpeg2000Container.JP2, document.container)
        assertEquals(listOf("jP  ", "ftyp", "jp2h", "jp2c"), document.boxes.map { it.type })
        assertArrayEquals(codestream, document.copyPayload(jp2c))
        assertTrue(Codec.MakeFromData(jp2) != null)
    }

    @Test
    fun `bounded open refuses an oversized J2K before parsing its codestream`() {
        val codestream = narrowLosslessCodestream()

        val opened = Jpeg2000Document.open(codestream, Jpeg2000Limits(maxEncodedBytes = 16))

        assertNull(opened.document)
        assertEquals("jpeg2000.limit.encoded-bytes", opened.diagnostic?.code)
        assertEquals(Codec.Result.kOutOfMemory, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses a truncated top level box with a stable diagnostic`() {
        val truncated = jp2(narrowLosslessCodestream()).copyOf(15)

        val opened = Jpeg2000Document.open(truncated)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.box.truncated", opened.diagnostic?.code)
    }

    @Test
    fun `JP2 refuses a duplicate jp2c rather than choosing one codestream`() {
        val codestream = narrowLosslessCodestream()
        val duplicate = jp2(codestream) + ByteArrayOutputStream().also { it.writeBox("jp2c", codestream) }.toByteArray()

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.duplicate", opened.diagnostic?.code)
    }

    @Test
    fun `raw J2K requires SIZ immediately after SOC`() {
        val canonical = narrowLosslessCodestream()
        val forbiddenFirstMarkers = listOf(
            0x52 to narrowCodPayload(),
            0x5C to byteArrayOf(0, 0),
            0x64 to byteArrayOf(),
        )

        forbiddenFirstMarkers.forEach { (marker, payload) ->
            val reordered = ByteArrayOutputStream().also { output ->
                output.writeMarker(0x4F)
                output.writeSegment(marker, payload)
                output.write(canonical, 2, canonical.size - 2)
            }.toByteArray()

            val opened = Jpeg2000Document.open(reordered)

            assertNull(opened.document)
            assertEquals("jpeg2000.siz.order", opened.diagnostic?.code)
            assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
        }
    }

    @Test
    fun `JP2 refuses duplicate signature rather than retaining an ambiguous container`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("jP  ", jp2SignaturePayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.signature.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate ftyp rather than accepting ambiguous brands`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("ftyp", fileTypePayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ftyp.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires ftyp directly after its signature`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ftyp.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires its signature at byte zero`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("free", byteArrayOf())
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.signature.missing", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2h before its sole jp2c codestream`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2c", codestream)
            output.writeBox("jp2h", imageHeaderPayload())
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2h directly after ftyp`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2h.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 requires jp2c directly after jp2h`() {
        val codestream = narrowLosslessCodestream()
        val misplaced = ByteArrayOutputStream().also { output ->
            output.writeBox("jP  ", jp2SignaturePayload())
            output.writeBox("ftyp", fileTypePayload())
            output.writeBox("jp2h", imageHeaderPayload())
            output.writeBox("free", byteArrayOf())
            output.writeBox("jp2c", codestream)
        }.toByteArray()

        val opened = Jpeg2000Document.open(misplaced)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2c.order", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate jp2h rather than choosing a header`() {
        val duplicate = jp2(narrowLosslessCodestream()) + boxed("jp2h", imageHeaderPayload())

        val opened = Jpeg2000Document.open(duplicate)

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.jp2h.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `JP2 refuses duplicate ihdr inside jp2h`() {
        val duplicateHeader = imageHeaderBox() + imageHeaderBox()

        val opened = Jpeg2000Document.open(jp2(narrowLosslessCodestream(), duplicateHeader))

        assertNull(opened.document)
        assertEquals("jpeg2000.jp2.ihdr.duplicate", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    private fun narrowLosslessCodestream(
        width: Int = 1,
        height: Int = 1,
        codeBlockWidth: Int = 4,
        codeBlockHeight: Int = 4,
    ): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeMarker(0x4F) // SOC
        output.writeSegment(0x51, ByteArrayOutputStream().also { siz ->
            siz.writeU16(0) // Rsiz
            siz.writeU32(width); siz.writeU32(height) // Xsiz, Ysiz
            siz.writeU32(0); siz.writeU32(0) // XOsiz, YOsiz
            siz.writeU32(width); siz.writeU32(height) // XTsiz, YTsiz
            siz.writeU32(0); siz.writeU32(0) // XTOsiz, YTOsiz
            siz.writeU16(1) // Csiz
            siz.write(byteArrayOf(7, 1, 1)) // 8-bit unsigned, no subsampling
        }.toByteArray())
        output.writeSegment(0x52, narrowCodPayload(codeBlockWidth, codeBlockHeight))
        output.writeSegment(0x5C, byteArrayOf(0x40, 0x40)) // reversible 5/3, no quantization
        output.writeMarker(0x90)
        output.writeU16(10)
        output.writeU16(0) // Isot
        output.writeU32(14) // Psot: SOT (12 bytes) + SOD (2 bytes)
        output.write(0) // TPsot
        output.write(1) // TNsot
        output.writeMarker(0x93) // SOD, deliberately no EBCOT body
        output.writeMarker(0xD9) // EOC
    }.toByteArray()

    private fun sourcePgmPixels(
        resource: String = "/jpeg2000-openjpeg/source.pgm",
        width: Int = 5,
        height: Int = 3,
    ): ByteArray {
        val pgm = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing source PGM: $resource" }
            .use { it.readBytes() }
        var cursor = 0
        fun token(): String {
            while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            while (cursor < pgm.size && pgm[cursor] == '#'.code.toByte()) {
                while (cursor < pgm.size && pgm[cursor] != '\n'.code.toByte()) cursor++
                while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            }
            val start = cursor
            while (cursor < pgm.size && !pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            return pgm.copyOfRange(start, cursor).decodeToString()
        }
        assertEquals("P2", token())
        assertEquals(width.toString(), token())
        assertEquals(height.toString(), token())
        assertEquals("255", token())
        return ByteArray(width * height) { token().toInt().toByte() }
    }

    private data class Reversible53Bands(
        val ll: IntArray,
        val hl: IntArray,
        val lh: IntArray,
        val hh: IntArray,
    )

    private fun forwardReversible53Bands(samples: IntArray, width: Int, height: Int): Reversible53Bands {
        val lowWidth = (width + 1) ushr 1
        val highWidth = width ushr 1
        val lowHeight = (height + 1) ushr 1
        val highHeight = height ushr 1
        val lowColumns = IntArray(width * lowHeight)
        val highColumns = IntArray(width * highHeight)
        for (x in 0 until width) {
            val (low, high) = forwardReversible53(IntArray(height) { y -> samples[y * width + x] })
            for (y in low.indices) lowColumns[y * width + x] = low[y]
            for (y in high.indices) highColumns[y * width + x] = high[y]
        }
        val ll = IntArray(lowWidth * lowHeight)
        val hl = IntArray(highWidth * lowHeight)
        for (y in 0 until lowHeight) {
            val (low, high) = forwardReversible53(lowColumns.copyOfRange(y * width, (y + 1) * width))
            low.copyInto(ll, y * lowWidth)
            high.copyInto(hl, y * highWidth)
        }
        val lh = IntArray(lowWidth * highHeight)
        val hh = IntArray(highWidth * highHeight)
        for (y in 0 until highHeight) {
            val (low, high) = forwardReversible53(highColumns.copyOfRange(y * width, (y + 1) * width))
            low.copyInto(lh, y * lowWidth)
            high.copyInto(hh, y * highWidth)
        }
        return Reversible53Bands(ll, hl, lh, hh)
    }

    private fun forwardReversible53(samples: IntArray): Pair<IntArray, IntArray> {
        val low = IntArray((samples.size + 1) ushr 1) { index -> samples[index shl 1] }
        val high = IntArray(samples.size ushr 1) { index -> samples[(index shl 1) + 1] }
        for (index in high.indices) {
            high[index] -= Math.floorDiv(low[index] + low[minOf(index + 1, low.lastIndex)], 2)
        }
        for (index in low.indices) {
            low[index] += Math.floorDiv(high[if (index == 0) 0 else index - 1] + high[minOf(index, high.lastIndex)] + 2, 4)
        }
        return low to high
    }

    private fun inverseReversible53Bands(bands: Reversible53Bands, width: Int, height: Int): IntArray {
        val lowWidth = (width + 1) ushr 1
        val highWidth = width ushr 1
        val lowHeight = (height + 1) ushr 1
        val highHeight = height ushr 1
        val lowRows = IntArray(width * lowHeight)
        val highRows = IntArray(width * highHeight)
        for (y in 0 until lowHeight) {
            inverseReversible53(
                bands.ll.copyOfRange(y * lowWidth, (y + 1) * lowWidth),
                bands.hl.copyOfRange(y * highWidth, (y + 1) * highWidth),
                width,
            ).copyInto(lowRows, y * width)
        }
        for (y in 0 until highHeight) {
            inverseReversible53(
                bands.lh.copyOfRange(y * lowWidth, (y + 1) * lowWidth),
                bands.hh.copyOfRange(y * highWidth, (y + 1) * highWidth),
                width,
            ).copyInto(highRows, y * width)
        }
        return IntArray(width * height).also { output ->
            for (x in 0 until width) {
                val samples = inverseReversible53(
                    IntArray(lowHeight) { y -> lowRows[y * width + x] },
                    IntArray(highHeight) { y -> highRows[y * width + x] },
                    height,
                )
                for (y in samples.indices) output[y * width + x] = samples[y]
            }
        }
    }

    private fun decodeNdecompTwoCodeblocks(codestream: ByteArray): Array<IntArray> {
        val packetOffset = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        } + 2
        val spans = readNdecompTwoPacketSpans(
            codestream.copyOfRange(packetOffset, codestream.size - 2),
            packetOffset,
        )
        assertEquals(
            listOf(
                listOf(J2kPacketCodeblock(6, 16, 4)),
                listOf(J2kPacketCodeblock(7, 19, 3), J2kPacketCodeblock(6, 16, 2), J2kPacketCodeblock(7, 19, 2)),
                listOf(J2kPacketCodeblock(7, 19, 8), J2kPacketCodeblock(8, 22, 7), J2kPacketCodeblock(9, 25, 6)),
            ),
            spans.map { it.header.codeblocks },
        )
        fun decode(entry: J2kPacketCodeblock, bodyOffset: Int, width: Int, height: Int, orientation: J2kSubbandOrientation): IntArray =
            J2kTier1Decoder(
                width = width,
                height = height,
                numBitPlanes = entry.numBitPlanes,
                passes = entry.passes,
                codeblock = codestream.copyOfRange(bodyOffset, bodyOffset + entry.bodyLength),
                codeblockOffset = bodyOffset,
                orientation = orientation,
            ).decode()
        val ll = decode(spans[0].header.codeblocks.single(), spans[0].bodyOffset, width = 2, height = 2, J2kSubbandOrientation.LL)
        fun details(
            span: J2kPacketSpan,
            width: Int,
            lowWidth: Int,
            lowHeight: Int,
            highHeight: Int,
        ): Array<IntArray> {
            var offset = span.bodyOffset
            return Array(3) { index ->
                val entry = span.header.codeblocks[index]
                val dimensions = when (index) {
                    0 -> width to lowHeight
                    1 -> lowWidth to highHeight
                    else -> width to highHeight
                }
                decode(entry, offset, dimensions.first, dimensions.second, J2kSubbandOrientation.entries[index + 1]).also {
                    offset += entry.bodyLength
                }
            }
        }
        return arrayOf(
            ll,
            *details(spans[1], width = 1, lowWidth = 2, lowHeight = 2, highHeight = 1),
            *details(
                spans[2],
                width = 2,
                lowWidth = 3,
                lowHeight = 3,
                highHeight = 2,
            ),
        )
    }

    private fun fixtureDecisions(): List<J2kEbcotDecision> {
        val codestream = Jpeg2000TestFixtures.openJpegLossless5x3()
        val sod = (0 until codestream.size - 1).single { index ->
            codestream[index] == 0xFF.toByte() && codestream[index + 1] == 0x93.toByte()
        }
        return traceNarrowJ2kPacket(
            packet = codestream.copyOfRange(sod + 2, codestream.size - 2),
            packetOffset = sod + 2,
            width = 5,
            height = 3,
        )
    }

    private fun jp2(codestream: ByteArray, headerPayload: ByteArray = imageHeaderPayload()): ByteArray = ByteArrayOutputStream().also { output ->
        output.writeBox("jP  ", jp2SignaturePayload())
        output.writeBox("ftyp", fileTypePayload())
        output.writeBox("jp2h", headerPayload)
        output.writeBox("jp2c", codestream)
    }.toByteArray()

    private fun jp2SignaturePayload(): ByteArray = byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A)

    private fun fileTypePayload(): ByteArray = byteArrayOf(
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            'j'.code.toByte(), 'p'.code.toByte(), '2'.code.toByte(), ' '.code.toByte(),
    )

    private fun imageHeaderPayload(): ByteArray = imageHeaderBox()

    private fun imageHeaderBox(): ByteArray = boxed("ihdr", ByteArrayOutputStream().also { ihdr ->
        ihdr.writeU32(1); ihdr.writeU32(1)
        ihdr.writeU16(1)
        ihdr.write(byteArrayOf(7, 7, 0, 0))
    }.toByteArray())

    private fun narrowCodPayload(codeBlockWidth: Int = 4, codeBlockHeight: Int = 4): ByteArray = byteArrayOf(
        0, // Scod: no precinct partitioning
        0, // LRCP
        0, 1, // one layer
        0, // no MCT
        0, // one resolution
        codeBlockWidth.toByte(), codeBlockHeight.toByte(), // 2^(exponent + 2) code-block
        0, // no code-block style flags
        1, // reversible 5/3
    )

    private fun boxed(type: String, payload: ByteArray): ByteArray = ByteArrayOutputStream().also {
        it.writeBox(type, payload)
    }.toByteArray()

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF); write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, payload: ByteArray) {
        writeMarker(marker)
        writeU16(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeBox(type: String, payload: ByteArray) {
        require(type.length == 4)
        writeU32(payload.size + 8)
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write(value ushr 8); write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(value ushr 24); write(value ushr 16); write(value ushr 8); write(value and 0xFF)
    }
}
