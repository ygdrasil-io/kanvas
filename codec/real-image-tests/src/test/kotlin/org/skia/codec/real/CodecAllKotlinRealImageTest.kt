package org.skia.codec.real

import org.graphiks.math.SkIRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
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
            runtimeClasspath.contains("codec/build"),
            "real image tests must run with the :codec aggregator on the Gradle test runtime classpath",
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
        assertEquals(fixture.origin, checkedCodec.getOrigin(), fixture.path)
        assertEquals(fixture.frameCount, checkedCodec.getFrameCount(), fixture.path)
        fixture.hasICCProfile?.let { expected ->
            assertEquals(expected, checkedCodec.getICCProfile() != null, "${fixture.path} ICC profile expectation")
        }
        fixture.frameInfo.forEach { expected ->
            val frameInfo = checkedCodec.getFrameInfo()
            assertTrue(expected.index in frameInfo.indices, "${fixture.path} missing frame info index ${expected.index}")
            val actual = frameInfo[expected.index]
            expected.requiredFrame?.let { assertEquals(it, actual.requiredFrame, "${fixture.path} frame ${expected.index} requiredFrame") }
            expected.durationMs?.let { assertEquals(it, actual.durationMs, "${fixture.path} frame ${expected.index} durationMs") }
            expected.alphaType?.let { assertEquals(it, actual.alphaType, "${fixture.path} frame ${expected.index} alphaType") }
            expected.frameRect?.let { assertEquals(it, actual.frameRect, "${fixture.path} frame ${expected.index} frameRect") }
        }

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
            assertPixel(fixture.path, checkedBitmap, probe)
        }
        fixture.framePixelProbes.forEach { frameProbe ->
            val frameBitmap = SkBitmap(checkedCodec.getInfo().width, checkedCodec.getInfo().height)
            val frameResult = checkedCodec.getPixels(
                checkedCodec.getInfo(),
                frameBitmap,
                SkCodec.Options(frameIndex = frameProbe.frameIndex),
            )
            assertEquals(SkCodec.Result.kSuccess, frameResult, "${fixture.path} frame ${frameProbe.frameIndex}")
            frameProbe.pixelProbes.forEach { probe ->
                assertPixel("${fixture.path} frame ${frameProbe.frameIndex}", frameBitmap, probe)
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeFixtures")
    fun `rejects invalid real image fixtures through codec-all-kotlin dispatch`(fixture: NegativeFixture) {
        val data = readFixture(fixture.path)
        assertNull(SkCodec.MakeFromData(data), "${fixture.path} should be rejected: ${fixture.reason}")
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
        val origin: SkEncodedOrigin = SkEncodedOrigin.kTopLeft,
        val hasICCProfile: Boolean? = null,
        val frameInfo: List<FrameInfoExpectation> = emptyList(),
        val pixelProbes: List<PixelProbe>,
        val framePixelProbes: List<FramePixelProbe> = emptyList(),
    ) {
        override fun toString(): String = name
    }

    data class NegativeFixture(
        val name: String,
        val path: String,
        val reason: String,
    ) {
        override fun toString(): String = name
    }

    data class FrameInfoExpectation(
        val index: Int,
        val requiredFrame: Int? = null,
        val durationMs: Int? = null,
        val alphaType: SkAlphaType? = null,
        val frameRect: SkIRect? = null,
    )

    data class FramePixelProbe(
        val frameIndex: Int,
        val pixelProbes: List<PixelProbe>,
    )

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

        @JvmStatic
        fun negativeFixtures(): Stream<Named<NegativeFixture>> = negativeRealImageFixtures
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
                hasICCProfile = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF5E522B.toInt())),
            ),
            RealImageFixture(
                name = "png rgb mandrill_128 icc",
                path = "/codec-real-images/png/mandrill_128_icc.png",
                format = SkEncodedImageFormat.kPNG,
                width = 128,
                height = 128,
                hasAlpha = false,
                hasICCProfile = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF645828.toInt())),
            ),
            RealImageFixture(
                name = "png rgba color_wheel",
                path = "/codec-real-images/png/color_wheel.png",
                format = SkEncodedImageFormat.kPNG,
                width = 128,
                height = 128,
                hasAlpha = true,
                hasICCProfile = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0x00000000)),
            ),
            RealImageFixture(
                name = "png rgba ducky icc",
                path = "/codec-real-images/png/ducky_rgba_icc.png",
                format = SkEncodedImageFormat.kPNG,
                width = 489,
                height = 537,
                hasAlpha = true,
                hasICCProfile = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0x00FFFFFF)),
            ),
            RealImageFixture(
                name = "png grayscale 8 bpc",
                path = "/codec-real-images/png/grayscale_8.png",
                format = SkEncodedImageFormat.kPNG,
                width = 3,
                height = 2,
                hasAlpha = false,
                hasICCProfile = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFF000000.toInt()),
                    PixelProbe(2, 0, 0xFFFFFFFF.toInt()),
                    PixelProbe(1, 1, 0xFF7F7F7F.toInt()),
                ),
            ),
            RealImageFixture(
                name = "png palette with transparency",
                path = "/codec-real-images/png/palette_alpha.png",
                format = SkEncodedImageFormat.kPNG,
                width = 3,
                height = 2,
                hasAlpha = true,
                hasICCProfile = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0x00000000),
                    PixelProbe(1, 0, 0xFFFF0000.toInt()),
                    PixelProbe(2, 1, 0xFFFF0000.toInt()),
                ),
            ),
            RealImageFixture(
                name = "png grayscale 16 bpc",
                path = "/codec-real-images/png/grayscale_16.png",
                format = SkEncodedImageFormat.kPNG,
                width = 2,
                height = 2,
                hasAlpha = false,
                hasICCProfile = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFF000000.toInt()),
                    PixelProbe(1, 0, 0xFFFFFFFF.toInt()),
                ),
            ),
            RealImageFixture(
                name = "png libpng pngsuite icon",
                path = "/codec-real-images/libpng/pngsuite2.png",
                format = SkEncodedImageFormat.kPNG,
                width = 48,
                height = 48,
                hasAlpha = false,
                hasICCProfile = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF000200.toInt())),
            ),
            RealImageFixture(
                name = "png imagemagick rose",
                path = "/codec-real-images/imagemagick/rose.png",
                format = SkEncodedImageFormat.kPNG,
                width = 70,
                height = 46,
                hasAlpha = false,
                hasICCProfile = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF302F2D.toInt())),
            ),
            RealImageFixture(
                name = "png gimp graphics by gimp",
                path = "/codec-real-images/gimp/gfx_by_gimp.png",
                format = SkEncodedImageFormat.kPNG,
                width = 90,
                height = 36,
                hasAlpha = true,
                hasICCProfile = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0x00928A74)),
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
                name = "jpeg baseline icc ducky",
                path = "/codec-real-images/jpeg/ducky_icc_exif.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 489,
                height = 537,
                hasAlpha = false,
                hasICCProfile = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFFFFFFFF.toInt(), tolerance = 32)),
            ),
            RealImageFixture(
                name = "jpeg baseline 4_2_0 color wheel",
                path = "/codec-real-images/jpeg/color_wheel_420.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 128,
                height = 128,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(64, 64, 0xFF000000.toInt(), tolerance = 24)),
            ),
            RealImageFixture(
                name = "jpeg baseline 4_4_4 mandrill",
                path = "/codec-real-images/jpeg/mandrill_h1v1_444.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 512,
                height = 512,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFFA39362.toInt(), tolerance = 16)),
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
                name = "jpeg progressive grayscale legacy",
                path = "/codec-real-images/jpeg/grayscale_progressive_legacy.jpg",
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
                name = "jpeg device camera motorola moto e6 play",
                path = "/codec-real-images/camera/motorola_moto_e6_play.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 256,
                height = 166,
                hasAlpha = false,
                hasICCProfile = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF5D5C6A.toInt(), tolerance = 16)),
            ),
            RealImageFixture(
                name = "jpeg browser canvas screenshot",
                path = "/codec-real-images/browser/canvas_checker.jpg",
                format = SkEncodedImageFormat.kJPEG,
                width = 24,
                height = 16,
                hasAlpha = false,
                hasICCProfile = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFFEBD04F.toInt(), tolerance = 8)),
            ),
            *jpegOrientationFixtures().toTypedArray(),
            RealImageFixture(
                name = "gif static box",
                path = "/codec-real-images/gif/box.gif",
                format = SkEncodedImageFormat.kGIF,
                width = 200,
                height = 55,
                hasAlpha = false,
                frameInfo = listOf(
                    FrameInfoExpectation(
                        index = 0,
                        requiredFrame = SkCodec.kNoFrame,
                        durationMs = 0,
                        frameRect = SkIRect.MakeXYWH(0, 0, 200, 55),
                    ),
                ),
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
                frameInfo = (0 until 4).map { index ->
                    FrameInfoExpectation(
                        index = index,
                        durationMs = 200,
                        frameRect = SkIRect.MakeXYWH(0, 0, 640, 479),
                    )
                },
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF4FEFB9.toInt())),
                framePixelProbes = (0 until 4).map { index ->
                    FramePixelProbe(index, listOf(PixelProbe(0, 0, 0xFF4FEFB9.toInt())))
                },
            ),
            RealImageFixture(
                name = "gif disposal methods 3x1",
                path = "/codec-real-images/gif/disposal_methods_3x1.gif",
                format = SkEncodedImageFormat.kGIF,
                width = 3,
                height = 1,
                frameCount = 5,
                hasAlpha = false,
                frameInfo = listOf(
                    FrameInfoExpectation(0, requiredFrame = SkCodec.kNoFrame, durationMs = 50, frameRect = SkIRect.MakeXYWH(0, 0, 3, 1)),
                    FrameInfoExpectation(1, requiredFrame = 0, durationMs = 60, frameRect = SkIRect.MakeXYWH(1, 0, 1, 1)),
                    FrameInfoExpectation(2, requiredFrame = 1, durationMs = 70, frameRect = SkIRect.MakeXYWH(2, 0, 1, 1)),
                    FrameInfoExpectation(3, requiredFrame = 2, durationMs = 80, frameRect = SkIRect.MakeXYWH(1, 0, 1, 1)),
                    FrameInfoExpectation(4, requiredFrame = 2, durationMs = 90, frameRect = SkIRect.MakeXYWH(2, 0, 1, 1)),
                ),
                pixelProbes = listOf(PixelProbe(0, 0, 0xFFFF0000.toInt())),
                framePixelProbes = listOf(
                    FramePixelProbe(
                        2,
                        listOf(
                            PixelProbe(0, 0, 0xFFFF0000.toInt()),
                            PixelProbe(1, 0, 0xFF00FF00.toInt()),
                            PixelProbe(2, 0, 0xFF0000FF.toInt()),
                        ),
                    ),
                    FramePixelProbe(
                        4,
                        listOf(
                            PixelProbe(0, 0, 0xFFFF0000.toInt()),
                            PixelProbe(1, 0, 0xFF00FF00.toInt()),
                            PixelProbe(2, 0, 0xFFFFFF00.toInt()),
                        ),
                    ),
                ),
            ),
            RealImageFixture(
                name = "bmp bottom-up 24 bpp",
                path = "/codec-real-images/bmp/bottom_up_24.bmp",
                format = SkEncodedImageFormat.kBMP,
                width = 3,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFFFF0000.toInt()),
                    PixelProbe(1, 1, 0xFF000000.toInt()),
                    PixelProbe(2, 1, 0xFF808080.toInt()),
                ),
            ),
            RealImageFixture(
                name = "bmp top-down 32 bpp alpha",
                path = "/codec-real-images/bmp/top_down_32_alpha.bmp",
                format = SkEncodedImageFormat.kBMP,
                width = 2,
                height = 2,
                hasAlpha = true,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0x80FF0000.toInt()),
                    PixelProbe(1, 1, 0x00000000),
                ),
            ),
            RealImageFixture(
                name = "bmp indexed palette 8 bpp",
                path = "/codec-real-images/bmp/palette_8.bmp",
                format = SkEncodedImageFormat.kBMP,
                width = 4,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFF000000.toInt()),
                    PixelProbe(1, 0, 0xFFFF0000.toInt()),
                    PixelProbe(3, 1, 0xFF000000.toInt()),
                ),
            ),
            RealImageFixture(
                name = "wbmp type0 checker 3x2",
                path = "/codec-real-images/wbmp/type0_3x2.wbmp",
                format = SkEncodedImageFormat.kWBMP,
                width = 3,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFFFFFFFF.toInt()),
                    PixelProbe(1, 0, 0xFF000000.toInt()),
                    PixelProbe(2, 1, 0xFF000000.toInt()),
                ),
            ),
            RealImageFixture(
                name = "ico embedded png",
                path = "/codec-real-images/ico/embedded_png.ico",
                format = SkEncodedImageFormat.kPNG,
                width = 3,
                height = 2,
                hasAlpha = true,
                pixelProbes = listOf(PixelProbe(1, 0, 0xFFFF0000.toInt())),
            ),
            RealImageFixture(
                name = "ico embedded bmp",
                path = "/codec-real-images/ico/embedded_bmp.ico",
                format = SkEncodedImageFormat.kBMP,
                width = 2,
                height = 2,
                hasAlpha = true,
                pixelProbes = listOf(PixelProbe(0, 0, 0x80FF0000.toInt())),
            ),
            RealImageFixture(
                name = "ico largest entry prefers bmp",
                path = "/codec-real-images/ico/largest_entry_prefers_bmp.ico",
                format = SkEncodedImageFormat.kBMP,
                width = 3,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(2, 0, 0xFF0000FF.toInt())),
            ),
            RealImageFixture(
                name = "ico tie prefers png",
                path = "/codec-real-images/ico/tie_prefers_png.ico",
                format = SkEncodedImageFormat.kPNG,
                width = 2,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF000000.toInt())),
            ),
            RealImageFixture(
                name = "webp vp8l lossless still",
                path = "/codec-real-images/webp/vp8l_lossless_2x1.webp",
                format = SkEncodedImageFormat.kWEBP,
                width = 2,
                height = 1,
                hasAlpha = true,
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0xFFFF0000.toInt()),
                    PixelProbe(1, 0, 0x800000FF.toInt()),
                ),
            ),
            RealImageFixture(
                name = "webp vp8 lossy supported still",
                path = "/codec-real-images/webp/vp8_lossy_gray_2x2.webp",
                format = SkEncodedImageFormat.kWEBP,
                width = 2,
                height = 2,
                hasAlpha = false,
                pixelProbes = listOf(PixelProbe(0, 0, 0xFF808080.toInt(), tolerance = 1)),
            ),
            RealImageFixture(
                name = "webp animated vp8l blend dispose",
                path = "/codec-real-images/webp/animated_vp8l_blend_dispose_4x1.webp",
                format = SkEncodedImageFormat.kWEBP,
                width = 4,
                height = 1,
                frameCount = 3,
                hasAlpha = true,
                frameInfo = listOf(
                    FrameInfoExpectation(
                        index = 0,
                        requiredFrame = SkCodec.kNoFrame,
                        durationMs = 10,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(0, 0, 4, 1),
                    ),
                    FrameInfoExpectation(
                        index = 1,
                        requiredFrame = 0,
                        durationMs = 20,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(0, 0, 4, 1),
                    ),
                    FrameInfoExpectation(
                        index = 2,
                        requiredFrame = 1,
                        durationMs = 30,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(2, 0, 2, 1),
                    ),
                ),
                pixelProbes = listOf(
                    PixelProbe(0, 0, 0x00000000),
                    PixelProbe(1, 0, 0xFFC80000.toInt()),
                    PixelProbe(3, 0, 0xFFC80000.toInt()),
                ),
                framePixelProbes = listOf(
                    FramePixelProbe(
                        1,
                        listOf(
                            PixelProbe(0, 0, 0x00000000),
                            PixelProbe(1, 0, 0xFF640064.toInt()),
                            PixelProbe(2, 0, 0xFF640064.toInt()),
                            PixelProbe(3, 0, 0xFFC80000.toInt()),
                        ),
                    ),
                    FramePixelProbe(
                        2,
                        listOf(
                            PixelProbe(0, 0, 0x00000000),
                            PixelProbe(1, 0, 0x00000000),
                            PixelProbe(2, 0, 0xFF00C800.toInt()),
                            PixelProbe(3, 0, 0xFF00C800.toInt()),
                        ),
                    ),
                ),
            ),
            RealImageFixture(
                name = "webp vp8x animated stoplight",
                path = "/codec-real-images/webp/stoplight.webp",
                format = SkEncodedImageFormat.kWEBP,
                width = 11,
                height = 29,
                frameCount = 3,
                hasAlpha = true,
                frameInfo = listOf(
                    FrameInfoExpectation(
                        index = 0,
                        requiredFrame = SkCodec.kNoFrame,
                        durationMs = 1000,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(0, 0, 11, 29),
                    ),
                    FrameInfoExpectation(
                        index = 1,
                        requiredFrame = 0,
                        durationMs = 500,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(2, 10, 7, 17),
                    ),
                    FrameInfoExpectation(
                        index = 2,
                        requiredFrame = 1,
                        durationMs = 1000,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(2, 2, 7, 16),
                    ),
                ),
                expectedResult = SkCodec.Result.kErrorInInput,
                pixelProbes = emptyList(),
            ),
        )

        private val negativeRealImageFixtures = listOf(
            NegativeFixture(
                name = "wbmp truncated header",
                path = "/codec-real-images/wbmp/invalid_truncated_header.wbmp",
                reason = "VLQ width is truncated",
            ),
            NegativeFixture(
                name = "wbmp truncated raster",
                path = "/codec-real-images/wbmp/invalid_truncated_raster.wbmp",
                reason = "declared bitmap raster is incomplete",
            ),
            NegativeFixture(
                name = "webp truncated RIFF header",
                path = "/codec-real-images/webp/invalid_truncated_riff.webp",
                reason = "RIFF header is incomplete",
            ),
            NegativeFixture(
                name = "webp truncated VP8X chunk",
                path = "/codec-real-images/webp/invalid_truncated_vp8x.webp",
                reason = "VP8X chunk declares bytes past the file end",
            ),
            NegativeFixture(
                name = "webp truncated VP8L chunk",
                path = "/codec-real-images/webp/invalid_truncated_vp8l.webp",
                reason = "VP8L chunk declares bytes past the file end",
            ),
            NegativeFixture(
                name = "webp truncated VP8 chunk",
                path = "/codec-real-images/webp/invalid_truncated_vp8.webp",
                reason = "VP8 chunk declares bytes past the file end",
            ),
        )

        private fun jpegOrientationFixtures(): List<RealImageFixture> {
            val origins = listOf(
                SkEncodedOrigin.kTopLeft,
                SkEncodedOrigin.kTopRight,
                SkEncodedOrigin.kBottomRight,
                SkEncodedOrigin.kBottomLeft,
                SkEncodedOrigin.kLeftTop,
                SkEncodedOrigin.kRightTop,
                SkEncodedOrigin.kRightBottom,
                SkEncodedOrigin.kLeftBottom,
            )
            return origins.mapIndexed { index, origin ->
                val exifValue = index + 1
                RealImageFixture(
                    name = "jpeg exif orientation $exifValue",
                    path = "/codec-real-images/jpeg/orientation/${exifValue}_444.jpg",
                    format = SkEncodedImageFormat.kJPEG,
                    width = 100,
                    height = 80,
                    hasAlpha = false,
                    origin = origin,
                    pixelProbes = listOf(PixelProbe(0, 0, if (exifValue in listOf(1, 4, 5, 8)) 0xFF000019.toInt() else 0xFF000015.toInt())),
                )
            }
        }

        private fun readFixture(path: String): ByteArray {
            val stream = CodecAllKotlinRealImageTest::class.java.getResourceAsStream(path)
            require(stream != null) { "Missing real image fixture resource $path" }
            return stream.use { it.readBytes() }
        }

        private fun assertPixel(label: String, bitmap: SkBitmap, probe: PixelProbe) {
            val actual = bitmap.getPixel(probe.x, probe.y)
            val delta = maxChannelDelta(actual, probe.argb)
            assertTrue(
                delta <= probe.tolerance,
                "$label pixel(${probe.x}, ${probe.y}) expected ${probe.argb.hex()} +/- ${probe.tolerance}, " +
                    "got ${actual.hex()} with max channel delta $delta",
            )
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
