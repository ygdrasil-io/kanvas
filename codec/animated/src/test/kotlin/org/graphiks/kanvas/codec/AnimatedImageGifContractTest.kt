package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimatedImageGifContractTest {

    @Test
    fun `plays composed GIF frames through animated image`() {
        val codec = Codec.MakeFromData(
            gif(
                width = 3,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                frames = listOf(
                    GifFrameSpec(0, 0, 3, 1, intArrayOf(0, 0, 0), delayCs = 4),
                    GifFrameSpec(1, 0, 1, 1, intArrayOf(2), delayCs = 7),
                ),
            ),
        )
        assertNotNull(codec)

        val animated = AnimatedImage.MakeFromCodec(codec!!)
        assertNotNull(animated)
        animated!!

        assertEquals(2, animated.getFrameCount())
        assertEquals(40, animated.currentFrameDuration())
        assertFalse(animated.isFinished())
        assertFrame(animated, RED, RED, RED)

        assertEquals(70, animated.decodeNextFrame())
        assertEquals(70, animated.currentFrameDuration())
        assertFrame(animated, RED, BLUE, RED)

        assertEquals(AnimatedImage.kFinished, animated.decodeNextFrame())
        assertEquals(AnimatedImage.kFinished, animated.currentFrameDuration())
        assertTrue(animated.isFinished())

        animated.reset()
        assertFalse(animated.isFinished())
        assertEquals(40, animated.currentFrameDuration())
        assertFrame(animated, RED, RED, RED)
    }

    @Test
    fun `uses GIF loop count as default repetition count`() {
        val codec = Codec.MakeFromData(
            gif(
                width = 2,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                extensions = listOf(netscapeLoopExtension(loopCount = 1)),
                frames = listOf(
                    GifFrameSpec(0, 0, 2, 1, intArrayOf(0, 0), delayCs = 4),
                    GifFrameSpec(1, 0, 1, 1, intArrayOf(2), delayCs = 7),
                ),
            ),
        )
        assertNotNull(codec)

        val animated = AnimatedImage.MakeFromCodec(codec!!)
        assertNotNull(animated)
        animated!!

        assertEquals(1, animated.getRepetitionCount())
        assertEquals(40, animated.currentFrameDuration())
        assertEquals(70, animated.decodeNextFrame())
        assertEquals(40, animated.decodeNextFrame())
        assertEquals(70, animated.decodeNextFrame())
        assertEquals(AnimatedImage.kFinished, animated.decodeNextFrame())
    }

    private fun assertFrame(animated: AnimatedImage, vararg expected: Int) {
        val image = animated.getCurrentFrame()
        assertEquals(expected.size, image.width)
        assertEquals(1, image.height)
        for (x in expected.indices) {
            assertEquals(expected[x], image.peekPixel(x, 0), "x=$x")
        }
    }

    private fun gif(
        width: Int,
        height: Int,
        palette: IntArray,
        extensions: List<ByteArray> = emptyList(),
        frames: List<GifFrameSpec>,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out.addAscii("GIF89a")
        out.addU16LE(width)
        out.addU16LE(height)
        out += (0x80 or 0x70 or colorTableSizeBits(palette.size)).toByte()
        out += 0.toByte()
        out += 0.toByte()
        out.addColorTable(palette)
        for (extension in extensions) {
            for (byte in extension) out += byte
        }

        for (frame in frames) {
            out += 0x21.toByte()
            out += 0xF9.toByte()
            out += 4.toByte()
            out += 0.toByte()
            out.addU16LE(frame.delayCs)
            out += 0.toByte()
            out += 0.toByte()

            out += 0x2C.toByte()
            out.addU16LE(frame.left)
            out.addU16LE(frame.top)
            out.addU16LE(frame.width)
            out.addU16LE(frame.height)
            out += 0.toByte()
            out += MIN_CODE_SIZE.toByte()
            out.addSubBlocks(lzwData(frame.indexes))
        }
        out += 0x3B.toByte()
        return out.toByteArray()
    }

    private fun netscapeLoopExtension(loopCount: Int): ByteArray {
        val out = ArrayList<Byte>()
        out += 0x21.toByte()
        out += 0xFF.toByte()
        out += 11.toByte()
        out.addAscii("NETSCAPE2.0")
        out += 3.toByte()
        out += 1.toByte()
        out.addU16LE(loopCount)
        out += 0.toByte()
        return out.toByteArray()
    }

    private fun lzwData(indexes: IntArray): ByteArray {
        val clearCode = 1 shl MIN_CODE_SIZE
        val endCode = clearCode + 1
        val codes = buildList {
            var offset = 0
            while (offset < indexes.size) {
                add(clearCode)
                add(indexes[offset++])
                if (offset < indexes.size) add(indexes[offset++])
            }
            add(endCode)
        }
        val out = ArrayList<Byte>()
        var current = 0
        var bits = 0
        for (code in codes) {
            current = current or (code shl bits)
            bits += MIN_CODE_SIZE + 1
            while (bits >= 8) {
                out += (current and 0xFF).toByte()
                current = current ushr 8
                bits -= 8
            }
        }
        if (bits > 0) out += (current and 0xFF).toByte()
        return out.toByteArray()
    }

    private fun colorTableSizeBits(size: Int): Int {
        require(size >= 2)
        require(size.countOneBits() == 1)
        return size.countTrailingZeroBits() - 1
    }

    private fun ArrayList<Byte>.addAscii(value: String) {
        value.forEach { this += it.code.toByte() }
    }

    private fun ArrayList<Byte>.addU16LE(value: Int) {
        this += value.toByte()
        this += (value ushr 8).toByte()
    }

    private fun ArrayList<Byte>.addColorTable(colors: IntArray) {
        for (color in colors) {
            this += ((color ushr 16) and 0xFF).toByte()
            this += ((color ushr 8) and 0xFF).toByte()
            this += (color and 0xFF).toByte()
        }
    }

    private fun ArrayList<Byte>.addSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(255, data.size - offset)
            this += size.toByte()
            for (i in 0 until size) this += data[offset + i]
            offset += size
        }
        this += 0.toByte()
    }

    private data class GifFrameSpec(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val indexes: IntArray,
        val delayCs: Int,
    )

    private companion object {
        private const val MIN_CODE_SIZE = 2
        private const val RED = -0x10000
        private const val GREEN = -0xff0100
        private const val BLUE = -0xffff01
        private const val YELLOW = -0x100
    }
}
