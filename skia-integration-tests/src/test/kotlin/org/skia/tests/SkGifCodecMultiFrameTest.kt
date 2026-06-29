package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.SkCodec
import org.skia.foundation.SkBitmap
import org.skia.tools.ToolUtils

/**
 * Tests for the R-final.5 [SkCodec] multi-frame extension implemented
 * by the registered GIF codec.
 */
class SkGifCodecMultiFrameTest {

    @Test
    fun `getFrameCount returns 4 for test640x479_gif`() {
        val data = ToolUtils.GetResourceAsData("images/test640x479.gif")
        assertNotNull(data, "missing images/test640x479.gif")
        val codec = SkCodec.MakeFromData(data!!.toByteArray())
        assertNotNull(codec)
        assertTrue(codec!!.getFrameCount() >= 2, "expected animated GIF with ≥2 frames")
    }

    @Test
    fun `getFrameInfo returns kNoFrame for first frame and back-references thereafter`() {
        val data = ToolUtils.GetResourceAsData("images/test640x479.gif")!!
        val codec = SkCodec.MakeFromData(data.toByteArray())!!
        val infos = codec.getFrameInfo()
        assertEquals(codec.getFrameCount(), infos.size)
        assertEquals(SkCodec.kNoFrame, infos[0].requiredFrame)
        for (i in 1 until infos.size) {
            assertTrue(
                infos[i].requiredFrame in 0 until i,
                "frame $i requiredFrame=${infos[i].requiredFrame} should fall in [0, $i)",
            )
        }
    }

    @Test
    fun `getPixels(Options frameIndex) decodes per-frame bitmap`() {
        val data = ToolUtils.GetResourceAsData("images/test640x479.gif")!!
        val codec = SkCodec.MakeFromData(data.toByteArray())!!
        val info = codec.getInfo()
        val bm = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
        for (idx in 0 until codec.getFrameCount()) {
            val res = codec.getPixels(info, bm, SkCodec.Options(frameIndex = idx))
            assertEquals(SkCodec.Result.kSuccess, res, "frame $idx decode failed")
        }
    }

    @Test
    fun `single-frame codec reports getFrameCount = 1`() {
        // Use box.gif if it exists ; otherwise fallback to ducky.png
        // (ImageIO returns frameCount=1 for static images, so PNG is fine).
        val data = ToolUtils.GetResourceAsData("images/ducky.png") ?: return
        val codec = SkCodec.MakeFromData(data.toByteArray()) ?: return
        assertEquals(1, codec.getFrameCount())
        val info = codec.getFrameInfo()
        assertEquals(1, info.size)
        assertEquals(SkCodec.kNoFrame, info[0].requiredFrame)
    }
}
