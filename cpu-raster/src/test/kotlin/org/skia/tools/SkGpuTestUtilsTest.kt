package org.skia.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkYUVAInfo
import org.skia.gpu.YUVUtils
import org.graphiks.math.SkColorSetARGB

class SkGpuTestUtilsTest {
    @Test
    fun `MakeYUVAPlanesAsA8 produces deterministic planes for JPEG full and Rec601 limited`() {
        val src = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                src.setPixel(x, y, SkColorSetARGB(255, x * 30 + 10, y * 40 + 20, (x + y) * 20 + 15))
            }
        }
        val image = SkImage.Make(src)

        val jpeg = SkGpuTestUtils.MakeYUVAPlanesAsA8(
            src = image,
            colorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
            subsampling = YUVUtils.YUVSubsampling.k420,
        )
        val rec601 = SkGpuTestUtils.MakeYUVAPlanesAsA8(
            src = image,
            colorSpace = SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace,
            subsampling = YUVUtils.YUVSubsampling.k420,
        )

        assertEquals(3, jpeg.planes.size)
        assertEquals(3, rec601.planes.size)
        assertEquals(4, jpeg.planes[0].width)
        assertEquals(4, jpeg.planes[0].height)
        assertEquals(2, jpeg.planes[1].width)
        assertEquals(2, jpeg.planes[1].height)
        assertTrue(planesDiffer(jpeg.planes[0], rec601.planes[0]))
    }

    private fun planesDiffer(a: SkImage, b: SkImage): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        val pa = ByteArray(a.width * a.height)
        val pb = ByteArray(b.width * b.height)
        a.readAlpha8(pa)
        b.readAlpha8(pb)
        return pa.indices.any { pa[it] != pb[it] }
    }

    private fun SkImage.readAlpha8(out: ByteArray) {
        val info = org.skia.foundation.SkImageInfo.Make(
            width,
            height,
            SkColorType.kAlpha_8,
            org.skia.foundation.SkAlphaType.kUnpremul,
            SkColorSpace.makeSRGB(),
        )
        val buf = java.nio.ByteBuffer.allocate(out.size)
        check(readPixels(info, buf, width, 0, 0))
        buf.rewind()
        buf.get(out)
    }
}
