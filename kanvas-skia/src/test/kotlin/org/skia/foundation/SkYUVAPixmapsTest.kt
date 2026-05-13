package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * R3.11 verification suite for [SkYUVAPixmaps]. The class is currently
 * a metadata holder — these tests cover the structural invariants
 * (validity ↔ plane count, plane index bounds, the
 * [SkYUVAPixmaps.FromExternalPixmaps] factory).
 */
class SkYUVAPixmapsTest {

    private fun makeAlpha8Pixmap(w: Int, h: Int): SkPixmap {
        val info = SkImageInfo.Make(w, h, SkColorType.kAlpha_8, SkAlphaType.kUnpremul)
        val buf = ByteBuffer.allocate(info.minRowBytes() * h).order(ByteOrder.LITTLE_ENDIAN)
        return SkPixmap(info, buf, info.minRowBytes())
    }

    @Test
    fun `isValid is true when planes count matches info numPlanes`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        val planes = arrayOf(
            makeAlpha8Pixmap(8, 8),
            makeAlpha8Pixmap(8, 8),
            makeAlpha8Pixmap(8, 8),
        )
        val pixmaps = SkYUVAPixmaps(info, planes)
        assertTrue(pixmaps.isValid())
        assertEquals(3, pixmaps.numPlanes())
    }

    @Test
    fun `isValid is false when planes count disagrees with info numPlanes`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        // Only 2 planes supplied — info expects 3.
        val planes = arrayOf(
            makeAlpha8Pixmap(8, 8),
            makeAlpha8Pixmap(8, 8),
        )
        val pixmaps = SkYUVAPixmaps(info, planes)
        assertFalse(pixmaps.isValid())
    }

    @Test
    fun `isValid is false when info is kUnknown`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kUnknown,
            subsampling = SkYUVAInfo.Subsampling.kUnknown,
        )
        val pixmaps = SkYUVAPixmaps(info, emptyArray())
        assertFalse(pixmaps.isValid())
    }

    @Test
    fun `plane returns the i-th pixmap`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        val y = makeAlpha8Pixmap(8, 8)
        val uv = makeAlpha8Pixmap(4, 4)
        val pixmaps = SkYUVAPixmaps(info, arrayOf(y, uv))
        assertSame(y, pixmaps.plane(0))
        assertSame(uv, pixmaps.plane(1))
    }

    @Test
    fun `plane throws for out-of-range index`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(8, 8),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        val pixmaps = SkYUVAPixmaps(info, arrayOf(makeAlpha8Pixmap(8, 8), makeAlpha8Pixmap(4, 4)))
        assertThrows(IllegalArgumentException::class.java) { pixmaps.plane(-1) }
        assertThrows(IllegalArgumentException::class.java) { pixmaps.plane(2) }
    }

    @Test
    fun `FromExternalPixmaps truncates the input array to numPlanes`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(4, 4),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k420,
        )
        val external = arrayOf(
            makeAlpha8Pixmap(4, 4),
            makeAlpha8Pixmap(2, 2),
            makeAlpha8Pixmap(0, 0),
            makeAlpha8Pixmap(0, 0),
        )
        val pixmaps = SkYUVAPixmaps.FromExternalPixmaps(info, external)
        assertEquals(2, pixmaps.numPlanes())
        assertTrue(pixmaps.isValid())
    }

    @Test
    fun `FromExternalPixmaps rejects an undersized input array`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(4, 4),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        assertThrows(IllegalArgumentException::class.java) {
            // Only two pixmaps when the info expects three.
            SkYUVAPixmaps.FromExternalPixmaps(info, arrayOf(makeAlpha8Pixmap(4, 4), makeAlpha8Pixmap(4, 4)))
        }
    }
}
