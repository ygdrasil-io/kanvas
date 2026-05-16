package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * R-suivi.48 — verifies that [SkYUVAPixmaps.toRGBA8888] supports every
 * [SkYUVAInfo.PlaneConfig] enum entry :
 *  - bi-planar (`kY_UV` / `kY_VU`)
 *  - interleaved (`kYUV` / `kUYV`)
 *  - alpha-bearing (`kY_U_V_A`, `kY_UV_A`, `kYUVA`)
 *
 * Pre-R-suivi.48 the non-three-plane configs raised
 * `IllegalStateException` ; the new sampler dispatch shares the per-pixel
 * YUV→RGB matrix path with the existing three-plane code, so the colour
 * fidelity should match within the existing ±1 tolerance.
 */
class SkYUVAPixmapsBiPlanarTest {

    private fun makeAlpha8Plane(w: Int, h: Int, bytes: ByteArray): SkPixmap {
        val info = SkImageInfo.Make(w, h, SkColorType.kAlpha_8, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) for (x in 0 until w) {
            buf.put(y * rowBytes + x, bytes[y * w + x])
        }
        return SkPixmap(info, buf, rowBytes)
    }

    /**
     * Build a 2-byte-per-pixel plane (e.g. UV interleaved for NV12).
     * Uses `kRGBA_8888`-shaped storage but we only consume the first 2
     * channels via [SkYUVAPixmaps]'s `readChannel` — the upper 2 bytes
     * are zero-padded.
     */
    private fun makeUVPlane(w: Int, h: Int, uv: ByteArray): SkPixmap {
        // 2 channels per pixel — kanvas-skia doesn't ship a `kR8G8` color
        // type so we wrap a 4-byte-per-pixel `kRGBA_8888` plane and write
        // only the first 2 bytes per pixel ; readChannel respects
        // bytesPerPixel + channel offset.
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val base = y * rowBytes + x * 4
                buf.put(base, uv[(y * w + x) * 2])
                buf.put(base + 1, uv[(y * w + x) * 2 + 1])
                buf.put(base + 2, 0)
                buf.put(base + 3, 0)
            }
        }
        return SkPixmap(info, buf, rowBytes)
    }

    /**
     * Build an `n`-channel interleaved plane (n = 3 for kYUV / kUYV,
     * n = 4 for kYUVA / kUYVA). Each channel is one byte.
     */
    private fun makeNChannelPlane(w: Int, h: Int, channels: Int, data: ByteArray): SkPixmap {
        require(channels == 3 || channels == 4) { "channels must be 3 or 4" }
        // Use kRGBA_8888 (4 bytes/pixel) for 4-channel data ; for
        // 3-channel data we still use a 4-byte stride but only fill the
        // first 3 channels (the readChannel offset only walks `channel`
        // bytes so the trailing pad is harmless).
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) for (x in 0 until w) {
            val base = y * rowBytes + x * 4
            for (c in 0 until channels) {
                buf.put(base + c, data[(y * w + x) * channels + c])
            }
            // Pad the trailing channel with zero for 3-channel data.
            if (channels == 3) buf.put(base + 3, 0)
        }
        return SkPixmap(info, buf, rowBytes)
    }

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

    // ─── kY_UV (NV12-style) ────────────────────────────────────────────

    @Test
    fun `kY_UV bi-planar 4x4 with neutral chroma renders grayscale ramp`() {
        val w = 4; val h = 4
        val yBytes = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            yBytes[y * w + x] = when (y) {
                0 -> 0
                1 -> 85
                2 -> 170.toByte()
                else -> 255.toByte()
            }
        }
        // UV at half resolution (k420) — 2×2 plane, U + V interleaved.
        val uvBytes = ByteArray(2 * 2 * 2)
        for (i in uvBytes.indices) uvBytes[i] = 128.toByte() // neutral

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k420,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(makeAlpha8Plane(w, h, yBytes), makeUVPlane(2, 2, uvBytes)),
        )
        val rgba = pixmaps.toRGBA8888()
        // Each row carries one Y value → grayscale ramp.
        assertColorClose(SkColorSetARGB(0xFF, 0, 0, 0), rgba.getPixel(0, 0))
        assertColorClose(SkColorSetARGB(0xFF, 85, 85, 85), rgba.getPixel(0, 1))
        assertColorClose(SkColorSetARGB(0xFF, 170, 170, 170), rgba.getPixel(0, 2))
        assertColorClose(SkColorSetARGB(0xFF, 255, 255, 255), rgba.getPixel(0, 3))
    }

    @Test
    fun `kY_UV reads U from channel 0 and V from channel 1 of plane 1`() {
        // Place Y=128 + a chroma offset (U=200, V=64) — JPEG matrix maps
        // this to a known RGB. kY_UV expects U in channel 0, V in
        // channel 1 ; if the implementation swapped channels we'd see
        // the kY_VU result instead.
        val w = 2; val h = 2
        val yBytes = ByteArray(w * h) { 128.toByte() }
        val uvBytes = ByteArray(2 * 2 * 2)
        // 1×1 UV plane (k420). Single pair : U=200, V=64.
        uvBytes[0] = 200.toByte() // U at channel 0
        uvBytes[1] = 64.toByte() // V at channel 1
        uvBytes[2] = 200.toByte(); uvBytes[3] = 64.toByte()
        uvBytes[4] = 200.toByte(); uvBytes[5] = 64.toByte()
        uvBytes[6] = 200.toByte(); uvBytes[7] = 64.toByte()

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(makeAlpha8Plane(w, h, yBytes), makeUVPlane(w, h, uvBytes)),
        )
        val infoVU = info.copy(planeConfig = SkYUVAInfo.PlaneConfig.kY_VU)
        val pixmapsVU = SkYUVAPixmaps(
            infoVU,
            arrayOf(makeAlpha8Plane(w, h, yBytes), makeUVPlane(w, h, uvBytes)),
        )
        val viaUV = pixmaps.toRGBA8888().getPixel(0, 0)
        val viaVU = pixmapsVU.toRGBA8888().getPixel(0, 0)
        assertNotEquals(viaUV, viaVU, "kY_UV and kY_VU must produce different RGB")
    }

    // ─── kYUV (interleaved) ────────────────────────────────────────────

    @Test
    fun `kYUV interleaved single plane produces grayscale for neutral chroma`() {
        // Each pixel : (Y, U, V, pad). Set varying Y, neutral chroma.
        val w = 4; val h = 1
        val data = ByteArray(w * h * 3)
        data[0] = 0; data[1] = 128.toByte(); data[2] = 128.toByte()
        data[3] = 85; data[4] = 128.toByte(); data[5] = 128.toByte()
        data[6] = 170.toByte(); data[7] = 128.toByte(); data[8] = 128.toByte()
        data[9] = 255.toByte(); data[10] = 128.toByte(); data[11] = 128.toByte()

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kYUV,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(info, arrayOf(makeNChannelPlane(w, h, 3, data)))
        val rgba = pixmaps.toRGBA8888()
        assertColorClose(SkColorSetARGB(0xFF, 0, 0, 0), rgba.getPixel(0, 0))
        assertColorClose(SkColorSetARGB(0xFF, 85, 85, 85), rgba.getPixel(1, 0))
        assertColorClose(SkColorSetARGB(0xFF, 170, 170, 170), rgba.getPixel(2, 0))
        assertColorClose(SkColorSetARGB(0xFF, 255, 255, 255), rgba.getPixel(3, 0))
    }

    // ─── kY_U_V_A (three plane + alpha) ────────────────────────────────

    @Test
    fun `kY_U_V_A applies alpha plane to the rendered colour`() {
        val w = 2; val h = 2
        val yBytes = ByteArray(w * h) { 128.toByte() }
        val u = ByteArray(w * h) { 128.toByte() }
        val v = ByteArray(w * h) { 128.toByte() }
        // Alpha varies : 0, 64, 128, 255.
        val a = byteArrayOf(0, 64, 128.toByte(), 255.toByte())

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V_A,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                makeAlpha8Plane(w, h, yBytes),
                makeAlpha8Plane(w, h, u),
                makeAlpha8Plane(w, h, v),
                makeAlpha8Plane(w, h, a),
            ),
        )
        val rgba = pixmaps.toRGBA8888()
        // Alpha channel propagated to ARGB output ; RGB at all pixels is
        // mid-gray (Y=128, neutral chroma).
        assertEquals(0, SkColorGetA(rgba.getPixel(0, 0)), "alpha=0")
        assertEquals(64, SkColorGetA(rgba.getPixel(1, 0)), "alpha=64")
        assertEquals(128, SkColorGetA(rgba.getPixel(0, 1)), "alpha=128")
        assertEquals(255, SkColorGetA(rgba.getPixel(1, 1)), "alpha=255")
    }

    // ─── kY_UV_A (bi-planar + alpha) ───────────────────────────────────

    @Test
    fun `kY_UV_A bi-planar with alpha plane preserves per-pixel alpha`() {
        val w = 2; val h = 2
        val yBytes = ByteArray(w * h) { 128.toByte() }
        val uvBytes = ByteArray(2 * 2 * 2) { 128.toByte() }
        val a = byteArrayOf(0, 64, 128.toByte(), 255.toByte())

        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_UV_A,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                makeAlpha8Plane(w, h, yBytes),
                makeUVPlane(w, h, uvBytes),
                makeAlpha8Plane(w, h, a),
            ),
        )
        val rgba = pixmaps.toRGBA8888()
        assertEquals(0, SkColorGetA(rgba.getPixel(0, 0)))
        assertEquals(64, SkColorGetA(rgba.getPixel(1, 0)))
        assertEquals(128, SkColorGetA(rgba.getPixel(0, 1)))
        assertEquals(255, SkColorGetA(rgba.getPixel(1, 1)))
    }

    // ─── kYUVA (single interleaved plane with alpha) ───────────────────

    @Test
    fun `kYUVA interleaved single plane carries alpha in channel 3`() {
        val w = 4; val h = 1
        val data = ByteArray(w * h * 4)
        // Pixel (i) : Y=128, U=128, V=128, A=(0, 64, 128, 255).
        val alphas = intArrayOf(0, 64, 128, 255)
        for (i in 0 until w) {
            data[i * 4] = 128.toByte()
            data[i * 4 + 1] = 128.toByte()
            data[i * 4 + 2] = 128.toByte()
            data[i * 4 + 3] = alphas[i].toByte()
        }
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kYUVA,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(info, arrayOf(makeNChannelPlane(w, h, 4, data)))
        val rgba = pixmaps.toRGBA8888()
        for (i in 0 until w) {
            assertEquals(alphas[i], SkColorGetA(rgba.getPixel(i, 0)), "pixel $i alpha")
        }
    }

    // ─── kUYV / kUYVA — channel-order siblings ─────────────────────────

    @Test
    fun `kUYV reads Y from channel 1`() {
        // Channel order : (U, Y, V) per pixel.
        val w = 2; val h = 1
        val data = ByteArray(w * h * 3)
        data[0] = 128.toByte() // U
        data[1] = 200.toByte() // Y (≈ light gray)
        data[2] = 128.toByte() // V
        data[3] = 128.toByte() // U
        data[4] = 50.toByte() // Y (dark gray)
        data[5] = 128.toByte() // V
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kUYV,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(info, arrayOf(makeNChannelPlane(w, h, 3, data)))
        val rgba = pixmaps.toRGBA8888()
        // Pixel 0 should be light gray ; pixel 1 dark.
        val p0 = rgba.getPixel(0, 0)
        val p1 = rgba.getPixel(1, 0)
        assertTrue(SkColorGetR(p0) > SkColorGetR(p1), "p0=$p0 should be brighter than p1=$p1")
    }
}
