package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * R-suivi.41 verification suite for [SkYUVAPixmaps.toRGBA8888] and the
 * companion [SkImages.YUVA] factory. Covers :
 *  - JPEG full-range Y_U_V (the most permissive matrix) → known RGB values
 *  - Rec.709 limited-range Y_U_V → known RGB values + asymmetry vs JPEG
 *  - Bi-planar kY_UV (NV12 chroma layout) → same JPEG matrix path
 *  - [SkImages.YUVA] factory round-trips through [SkImage]
 */
class SkYUVAPixmapsToRGBATest {

    // ─── helpers ──────────────────────────────────────────────────────

    /** Wraps an `n×n` byte plane (channel data in `[0..255]`) as an [SkPixmap]. */
    private fun makeAlpha8Plane(w: Int, h: Int, bytes: ByteArray): SkPixmap {
        require(bytes.size >= w * h) { "bytes.size=${bytes.size} < w*h=${w * h}" }
        val info = SkImageInfo.Make(w, h, SkColorType.kAlpha_8, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) {
            for (x in 0 until w) {
                buf.put(y * rowBytes + x, bytes[y * w + x])
            }
        }
        return SkPixmap(info, buf, rowBytes)
    }

    /** Per-channel near-equal — YUV→RGB has float rounding noise of `±1`. */
    private fun assertColorClose(expected: SkColor, actual: SkColor, tolerance: Int = 1) {
        val ae = SkColorGetA(expected); val aa = SkColorGetA(actual)
        val re = SkColorGetR(expected); val ra = SkColorGetR(actual)
        val ge = SkColorGetG(expected); val ga = SkColorGetG(actual)
        val be = SkColorGetB(expected); val ba = SkColorGetB(actual)
        assertTrue(abs(ae - aa) <= tolerance, "alpha: expected=$ae actual=$aa")
        assertTrue(abs(re - ra) <= tolerance, "R: expected=$re actual=$ra")
        assertTrue(abs(ge - ga) <= tolerance, "G: expected=$ge actual=$ga")
        assertTrue(abs(be - ba) <= tolerance, "B: expected=$be actual=$ba")
    }

    // ─── JPEG full-range Y_U_V ────────────────────────────────────────

    @Test
    fun `JPEG full Y_U_V 4x4 maps neutral chroma + varying luma to grayscale`() {
        val w = 4; val h = 4
        // Y goes 0, 85, 170, 255 row-wise — neutral chroma (U=V=128) ⇒ grayscale ramp.
        val yBytes = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            yBytes[y * w + x] = when (y) {
                0 -> 0
                1 -> 85
                2 -> 170.toByte()
                else -> 255.toByte()
            }
        }
        val neutral = ByteArray(w * h) { 128.toByte() }

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                makeAlpha8Plane(w, h, yBytes),
                makeAlpha8Plane(w, h, neutral),
                makeAlpha8Plane(w, h, neutral),
            ),
        )
        val rgba = pixmaps.toRGBA8888()
        assertEquals(SkColorType.kRGBA_8888, rgba.colorType)

        // Row 0 (Y=0)   ⇒ black.
        assertColorClose(SkColorSetARGB(0xFF, 0, 0, 0), rgba.getPixel(0, 0))
        // Row 1 (Y=85)  ⇒ ~33% gray (85/255 ≈ 0.333).
        assertColorClose(SkColorSetARGB(0xFF, 85, 85, 85), rgba.getPixel(0, 1))
        // Row 2 (Y=170) ⇒ ~67% gray.
        assertColorClose(SkColorSetARGB(0xFF, 170, 170, 170), rgba.getPixel(0, 2))
        // Row 3 (Y=255) ⇒ white.
        assertColorClose(SkColorSetARGB(0xFF, 255, 255, 255), rgba.getPixel(0, 3))
    }

    @Test
    fun `JPEG full Y_U_V maps pure red luma+chroma to sRGB red`() {
        // From JPEG matrix : RGB=(255, 0, 0) corresponds to
        // Y=76.245, U=84.972 (≈85), V=255 (clamped). Reverse path
        // should round-trip near (255, 0, 0).
        val w = 1; val h = 1
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                makeAlpha8Plane(w, h, byteArrayOf(76)),
                makeAlpha8Plane(w, h, byteArrayOf(85.toByte())),
                makeAlpha8Plane(w, h, byteArrayOf(255.toByte())),
            ),
        )
        val rgba = pixmaps.toRGBA8888()
        // The 1.402 chroma coefficient + clamping leaves R near 255, G+B near 0.
        val px = rgba.getPixel(0, 0)
        assertTrue(SkColorGetR(px) >= 250, "R=${SkColorGetR(px)} should be near 255")
        assertTrue(SkColorGetG(px) <= 5, "G=${SkColorGetG(px)} should be near 0")
        assertTrue(SkColorGetB(px) <= 5, "B=${SkColorGetB(px)} should be near 0")
    }

    // ─── Rec.709 limited-range ────────────────────────────────────────

    @Test
    fun `Rec709 limited Y_U_V maps Y=16 to black and Y=235 to white`() {
        val w = 2; val h = 1
        // Limited range : Y=16 is black, Y=235 is white ; neutral chroma=128.
        val yBytes = byteArrayOf(16, 235.toByte())
        val u = ByteArray(w * h) { 128.toByte() }
        val v = ByteArray(w * h) { 128.toByte() }
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kRec709_Limited_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(makeAlpha8Plane(w, h, yBytes), makeAlpha8Plane(w, h, u), makeAlpha8Plane(w, h, v)),
        )
        val rgba = pixmaps.toRGBA8888()
        assertColorClose(SkColorSetARGB(0xFF, 0, 0, 0), rgba.getPixel(0, 0))
        assertColorClose(SkColorSetARGB(0xFF, 255, 255, 255), rgba.getPixel(1, 0))
    }

    @Test
    fun `Rec709 limited and JPEG full produce different RGB for the same YUV input`() {
        // Both matrices share neutral-grey behavior at (Y=128, U=128, V=128) ;
        // but they differ for other luma values because of the limited-range
        // 16..235 scaling. Spot-check (Y=200, U=128, V=128).
        val w = 1; val h = 1
        val planes = { -> arrayOf(
            makeAlpha8Plane(w, h, byteArrayOf(200.toByte())),
            makeAlpha8Plane(w, h, byteArrayOf(128.toByte())),
            makeAlpha8Plane(w, h, byteArrayOf(128.toByte())),
        ) }
        val infoJpeg = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val infoRec709 = infoJpeg.copy(
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kRec709_Limited_YUV_ColorSpace,
        )
        val rgbJpeg = SkYUVAPixmaps(infoJpeg, planes()).toRGBA8888().getPixel(0, 0)
        val rgbRec709 = SkYUVAPixmaps(infoRec709, planes()).toRGBA8888().getPixel(0, 0)
        assertNotEquals(rgbJpeg, rgbRec709)
    }

    // ─── Subsampled chroma — 4:2:0 with kY_U_V (3-plane) ─────────────

    @Test
    fun `kY_U_V with 4-2-0 subsampling samples U-V plane at half resolution`() {
        val w = 4; val h = 4
        // Y : full 4×4, all 128 (mid-gray luma in JPEG-full).
        val yBytes = ByteArray(w * h) { 128.toByte() }
        // U / V : 2×2 (k420), all neutral (128) — result must be uniform mid-gray.
        val uBytes = ByteArray(2 * 2) { 128.toByte() }
        val vBytes = ByteArray(2 * 2) { 128.toByte() }
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k420,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                makeAlpha8Plane(w, h, yBytes),
                makeAlpha8Plane(2, 2, uBytes),
                makeAlpha8Plane(2, 2, vBytes),
            ),
        )
        val rgba = pixmaps.toRGBA8888()
        for (y in 0 until h) for (x in 0 until w) {
            assertColorClose(SkColorSetARGB(0xFF, 128, 128, 128), rgba.getPixel(x, y))
        }
    }

    // ─── SkImages.YUVA factory ────────────────────────────────────────

    @Test
    fun `SkImages YUVA produces a non-null image whose pixels match toRGBA8888`() {
        val w = 4; val h = 4
        val yBytes = ByteArray(w * h) { ((it * 16) and 0xFF).toByte() }
        val u = ByteArray(w * h) { 128.toByte() }
        val v = ByteArray(w * h) { 128.toByte() }
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(makeAlpha8Plane(w, h, yBytes), makeAlpha8Plane(w, h, u), makeAlpha8Plane(w, h, v)),
        )
        val image = SkImages.YUVA(pixmaps)
        assertNotNull(image)
        assertEquals(w, image!!.width)
        assertEquals(h, image.height)

        // The image's pixel array (Pascal-Argb) must agree with
        // `toRGBA8888().getPixel(x, y)` everywhere.
        val viaBitmap = pixmaps.toRGBA8888()
        for (y in 0 until h) for (x in 0 until w) {
            assertColorClose(viaBitmap.getPixel(x, y), image.peekPixel(x, y))
        }
    }

    @Test
    fun `SkImages YUVA returns null for invalid pixmaps`() {
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(4, 4),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
        )
        // Only two planes when info wants three ⇒ invalid.
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(makeAlpha8Plane(4, 4, ByteArray(16)), makeAlpha8Plane(4, 4, ByteArray(16))),
        )
        assertEquals(null, SkImages.YUVA(pixmaps))
    }
}
