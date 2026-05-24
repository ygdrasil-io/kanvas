package org.skia.codec.real

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import java.util.ServiceLoader
import java.util.stream.Stream

class CodecAllKotlinRealImageTest {
    @Test
    fun `real image fixture runtime uses pure kotlin codec providers only`() {
        val providerNames = ServiceLoader.load(CodecDecoderProvider::class.java)
            .map { it::class.qualifiedName ?: it.javaClass.name }
            .sorted()
            .toList()

        assertEquals(
            listOf(
                "org.skia.codec.ExtendedCodecDecoderProvider",
                "org.skia.codec.IcoKotlinDecoderProvider",
                "org.skia.codec.bmp.BmpKotlinDecoderProvider",
                "org.skia.codec.gif.GifKotlinDecoderProvider",
                "org.skia.codec.jpeg.JpegKotlinDecoderProvider",
                "org.skia.codec.png.PngKotlinDecoderProvider",
                "org.skia.codec.wbmp.WbmpKotlinDecoderProvider",
                "org.skia.codec.webp.WebpKotlinDecoderProvider",
            ),
            providerNames,
        )

        val runtimeClasspath = System.getProperty("kanvas.codec.realImageTestRuntimeClasspath")
            ?: error("Missing kanvas.codec.realImageTestRuntimeClasspath test system property")
        assertTrue(
            runtimeClasspath.contains("codec-all-kotlin"),
            "real image tests must run with codec-all-kotlin on the Gradle test runtime classpath",
        )
        forbiddenCodecArtifacts.forEach { artifact ->
            assertFalse(
                runtimeClasspath.contains(artifact),
                "real image tests must not run with temporary AWT/ImageIO codec artifact $artifact on classpath",
            )
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `checks real image fixture through codec-all-kotlin dispatch`(fixture: RealImageFixture) {
        val data = readFixture(fixture.path)
        val codec = SkCodec.MakeFromData(data)
        assertNotNull(codec, "${fixture.path} should be accepted by codec-all-kotlin dispatch")

        val checkedCodec = codec!!
        assertEquals(fixture.format, checkedCodec.getEncodedFormat(), fixture.path)
        assertEquals(fixture.width, checkedCodec.getInfo().width, fixture.path)
        assertEquals(fixture.height, checkedCodec.getInfo().height, fixture.path)
        assertEquals(fixture.frameCount, checkedCodec.getFrameCount(), fixture.path)

        val (bitmap, result) = checkedCodec.getImage()
        assertEquals(fixture.expectedResult, result, fixture.path)
        if (fixture.expectedResult != SkCodec.Result.kSuccess) {
            assertEquals(null, bitmap, "${fixture.path} is documented as unsupported for pixel decode")
            return
        }
        assertNotNull(bitmap, fixture.path)
        val checkedBitmap = bitmap!!

        val hasTransparentPixel = (0 until checkedBitmap.height).any { y ->
            (0 until checkedBitmap.width).any { x ->
                ((checkedBitmap.getPixel(x, y) ushr 24) and 0xFF) < 255
            }
        }
        assertEquals(fixture.hasAlpha, hasTransparentPixel, "${fixture.path} alpha/opacity expectation")

        fixture.pixelProbes.forEach { probe ->
            val actual = checkedBitmap.getPixel(probe.x, probe.y)
            val delta = maxChannelDelta(actual, probe.argb)
            assertTrue(
                delta <= probe.tolerance,
                "${fixture.path} pixel(${probe.x}, ${probe.y}) expected ${probe.argb.hex()} +/- ${probe.tolerance}, " +
                    "got ${actual.hex()} with max channel delta $delta",
            )
        }
    }

    data class RealImageFixture(
        val name: String,
        val path: String,
        val format: SkEncodedImageFormat,
        val width: Int,
        val height: Int,
        val frameCount: Int = 1,
        val hasAlpha: Boolean,
        val expectedResult: SkCodec.Result = SkCodec.Result.kSuccess,
        val pixelProbes: List<PixelProbe>,
    ) {
        override fun toString(): String = name
    }

    data class PixelProbe(
        val x: Int,
        val y: Int,
        val argb: Int,
        val tolerance: Int = 0,
    )

    private companion object {
        private val forbiddenCodecArtifacts = listOf(
            "codec-all-awt",
            "codec-png-imageio",
            "codec-jpeg-imageio",
            "codec-gif-imageio",
            "codec-bmp-imageio",
            "codec-wbmp-imageio",
            "codec-webp-imageio",
        )

        @JvmStatic
        fun fixtures(): Stream<Named<RealImageFixture>> = realImageFixtures
            .map { Named.of(it.name, it) }
            .stream()

        private val realImageFixtures = listOf(
            RealImageFixture(
                name = "png rgb mandrill_64",
                path = "/codec-real-images/png/mandrill_64.png",
                format = SkEncodedImageFormat.kPNG,
                width = 64,
                height = 64,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF5E522B.toInt())),
            ),
            RealImageFixture(
                name = "png rgba color_wheel",
                path = "/codec-real-images/png/color_wheel.png",
                format = SkEncodedImageFormat.kPNG,
                width = 128,
                height = 128,
                hasAlpha = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0x00000000)),
            ),
            RealImageFixture(
                name = "jpeg baseline dog",
                path = "/codec-real-images/jpeg/dog.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 180,
                height = 180,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF505E13.toInt(), tolerance = 8)),
            ),
            RealImageFixture(
                name = "jpeg progressive grayscale",
                path = "/codec-real-images/jpeg/grayscale_progressive.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 128,
                height = 128,
                hasAlpha = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFF000000.toInt(), tolerance = 16),
                    PixelProbe(127, 127, 0xFFFFFFFF.toInt(), tolerance = 16),
                ),
            ),
            RealImageFixture(
                name = "gif static box",
                path = "/codec-real-images/gif/box.gif",
                format = SkEncodedImageFormat.kGIF,
                width = 200,
                height = 55,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF000000.toInt())),
            ),
            RealImageFixture(
                name = "gif multi-frame test640x479",
                path = "/codec-real-images/gif/test640x479.gif",
                format = SkEncodedImageFormat.kGIF,
                width = 640,
                height = 479,
                frameCount = 4,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF4FEFB9.toInt())),
            ),
            RealImageFixture(
                name = "webp vp8x animated stoplight metadata only",
                path = "/codec-real-images/webp/stoplight.webp",
                format = SkEncodedImageFormat.kWEBP,
                width = 11,
                height = 29,
                hasAlpha = true,
                expectedResult = SkCodec.Result.kUnimplemented,
                pixelProbes = emptyList(),
            ),
        )

        private fun readFixture(path: String): ByteArray {
            val stream = CodecAllKotlinRealImageTest::class.java.getResourceAsStream(path)
            require(stream != null) { "Missing real image fixture resource $path" }
            return stream.use { it.readBytes() }
        }

        private fun maxChannelDelta(a: Int, b: Int): Int =
            maxOf(
                kotlin.math.abs(((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)),
                kotlin.math.abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)),
                kotlin.math.abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)),
                kotlin.math.abs((a and 0xFF) - (b and 0xFF)),
            )

        private fun Int.hex(): String =
            "0x" + toUInt().toString(radix = 16).uppercase().padStart(8, '0')
    }
}
