package org.skia.foundation


import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.ImageGeneratorImages
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase R2 — exercises [SkImageGenerator] (abstract base) and the
 * [ImageGeneratorImages.DeferredFromGenerator] factory.
 */
class SkImageGeneratorTest {

    /**
     * A minimal generator that fills the destination with a single
     * constant non-premul ARGB colour. Mirrors how an upstream
     * `gm`-style test would subclass `SkImageGenerator` to stand in
     * for a codec.
     */
    private class ConstantColorGenerator(
        info: SkImageInfo,
        private val color: SkColor,
    ) : SkImageGenerator(info) {
        override fun onGetPixels(
            info: SkImageInfo,
            pixels: ByteBuffer,
            rowBytes: Int,
        ): Boolean {
            // Layout has to match SkPixmap's host-LE RGBA byte order
            // (see SkPixmap.readPixel KDoc).
            val a = SkColorGetA(color)
            val r = SkColorGetR(color)
            val g = SkColorGetG(color)
            val b = SkColorGetB(color)
            for (y in 0 until info.height) {
                val rowOffset = y * rowBytes
                for (x in 0 until info.width) {
                    val off = rowOffset + x * 4
                    when (info.colorType) {
                        SkColorType.kRGBA_8888 -> {
                            pixels.put(off, r.toByte())
                            pixels.put(off + 1, g.toByte())
                            pixels.put(off + 2, b.toByte())
                            pixels.put(off + 3, a.toByte())
                        }
                        SkColorType.kBGRA_8888 -> {
                            pixels.put(off, b.toByte())
                            pixels.put(off + 1, g.toByte())
                            pixels.put(off + 2, r.toByte())
                            pixels.put(off + 3, a.toByte())
                        }
                        else -> return false
                    }
                }
            }
            return true
        }
    }

    @Test
    fun `getInfo returns constructor info`() {
        val info = SkImageInfo.Make(4, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = ConstantColorGenerator(info, SK_ColorRED)
        assertEquals(info, gen.getInfo())
    }

    @Test
    fun `uniqueID is non-zero and distinct across generators`() {
        val info = SkImageInfo.Make(2, 2)
        val a = ConstantColorGenerator(info, SK_ColorRED)
        val b = ConstantColorGenerator(info, SK_ColorBLUE)
        assertTrue(a.uniqueID() != 0)
        assertTrue(b.uniqueID() != 0)
        assertNotEquals(a.uniqueID(), b.uniqueID())
    }

    @Test
    fun `default isValid returns true`() {
        val gen = ConstantColorGenerator(SkImageInfo.Make(1, 1), SK_ColorBLACK)
        assertTrue(gen.isValid())
    }

    @Test
    fun `getPixels copies the constant colour into a caller buffer`() {
        val info = SkImageInfo.Make(4, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = ConstantColorGenerator(info, 0xCC112233.toInt())
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * info.height).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(gen.getPixels(info, buf, rowBytes))
        // Verify via SkPixmap.
        val pm = SkPixmap(info, buf, rowBytes)
        for (y in 0 until info.height) {
            for (x in 0 until info.width) {
                assertEquals(0xCC112233.toInt(), pm.getColor(x, y), "at ($x, $y)")
            }
        }
    }

    @Test
    fun `getPixels via SkPixmap delegates to onGetPixels`() {
        val info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = ConstantColorGenerator(info, SK_ColorGREEN)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * info.height).order(ByteOrder.LITTLE_ENDIAN)
        val pm = SkPixmap(info, buf, rowBytes)
        assertTrue(gen.getPixels(pm))
        assertEquals(SK_ColorGREEN, pm.getColor(0, 0))
        assertEquals(SK_ColorGREEN, pm.getColor(1, 1))
    }

    @Test
    fun `getPixels rejects too-small rowBytes`() {
        val info = SkImageInfo.Make(4, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = ConstantColorGenerator(info, SK_ColorRED)
        val buf = ByteBuffer.allocate(64)
        assertFalse(gen.getPixels(info, buf, rowBytes = 4))
    }

    @Test
    fun `getPixels rejects empty info`() {
        val info = SkImageInfo.Make(0, 0)
        val gen = ConstantColorGenerator(info, SK_ColorRED)
        val buf = ByteBuffer.allocate(16)
        assertFalse(gen.getPixels(info, buf, 16))
    }

    @Test
    fun `DeferredFromGenerator returns a populated SkImage`() {
        val info = SkImageInfo.Make(3, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = ConstantColorGenerator(info, 0xCC112233.toInt())
        val img = ImageGeneratorImages.DeferredFromGenerator(gen)
        assertNotNull(img)
        img!!
        assertEquals(3, img.width)
        assertEquals(2, img.height)
        for (y in 0 until 2) {
            for (x in 0 until 3) {
                assertEquals(0xCC112233.toInt(), img.peekPixel(x, y), "at ($x, $y)")
            }
        }
    }
}
