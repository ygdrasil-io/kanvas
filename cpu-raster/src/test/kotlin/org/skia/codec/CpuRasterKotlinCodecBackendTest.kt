package org.skia.codec

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.codec.test.CodecTestFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

class CpuRasterKotlinCodecBackendTest {
    @BeforeEach
    fun requireKotlinBackendTask() {
        assumeTrue(System.getProperty("kanvas.codec.expectedBackend") == "kotlin")
    }

    @Test
    fun `cpu-raster codec tests can run with codec-all-kotlin providers`() {
        val providers = ServiceLoader.load(CodecDecoderProvider::class.java)
            .map { provider -> provider::class.qualifiedName.orEmpty() }
            .toList()

        assertTrue(providers.any { it.endsWith(".PngKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".JpegKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".GifKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".BmpKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".WbmpKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".WebpKotlinDecoderProvider") })
        assertTrue(providers.any { it.endsWith(".IcoKotlinDecoderProvider") })
        assertFalse(
            providers.any { it.contains("ImageIo") },
            "testCodecWithKotlinBackend must not load temporary ImageIO codec providers",
        )

        val decoders = SkCodec.Decoders.all()
        assertTrue(decoders.any { it.name == "png" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "jpeg" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "gif" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "bmp" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "wbmp" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "webp" && it::class.qualifiedName.orEmpty().contains("Kotlin") })
        assertTrue(decoders.any { it.name == "ico" })

        val codec = SkCodec.MakeFromData(CodecTestFixtures.simpleRgbaPng())
        assertNotNull(codec)
        assertEquals("org.skia.codec.png.SkPngKotlinCodec", codec!!::class.qualifiedName)
        CodecTestFixtures.SIMPLE_RGBA_PIXELS.zip(CodecTestFixtures.decodePixels(codec)).forEach { (expected, actual) ->
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `full cpu-raster codec suite still has legacy backend blockers`() {
        val codecTestRoot = Path.of("src/test/kotlin/org/skia/codec")
        val backendBlockedTests = Files.walk(codecTestRoot).use { paths ->
            paths
                .filter { path -> path.toString().endsWith("Test.kt") }
                .filter { path -> path.fileName.toString() != "CpuRasterKotlinCodecBackendTest.kt" }
                .filter { path ->
                    val text = Files.readString(path)
                    text.contains("javax.imageio.ImageIO") ||
                        text.contains("java.awt.image.BufferedImage") ||
                        text.contains("SkWebpCodec")
                }
                .map { path -> codecTestRoot.relativize(path).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "SkAndroidCodecComputeSampleSizeJpegTest.kt",
                "SkAndroidCodecGetAndroidPixelsTest.kt",
                "SkAndroidCodecTest.kt",
                "bmp/SkBmpCodecTest.kt",
                "gif/SkGifCodecTest.kt",
                "jpeg/SkJpegCodecTest.kt",
                "png/SkPngCodecTest.kt",
                "wbmp/SkWbmpCodecTest.kt",
                "webp/SkWebpCodecTest.kt",
            ),
            backendBlockedTests,
            "The full :cpu-raster:test --tests '*codec*' switch is blocked until these legacy tests " +
                "stop generating fixtures through AWT/ImageIO or asserting temporary ImageIO-only WebP behavior.",
        )
    }
}
