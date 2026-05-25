package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkYUVAInfo
import org.skia.foundation.SkYUVAPixmaps
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stub port of Skia's `gm/yuvtorgbeffect.cpp::YUVMakeColorSpaceGM`
 * (registered as `yuv_make_color_space`, 1100 x 750).
 *
 * Upstream constructs an `SkImage` from YUVA pixmaps in one
 * `SkColorSpace`, then re-tags it into a wider gamut via
 * `SkImage.makeColorSpace(newCS)` and lays both versions out
 * side-by-side. The point is to verify that the colour-space
 * re-tag preserves the YUV-to-RGB matrix transformation and
 * only re-encodes the RGB output.
 *
 * `:kanvas-skia` does not implement
 * `SkImage.MakeFromYUVAPixmaps` (see [WackyYUVFormatsGM]) nor
 * `SkImage.makeColorSpace` for YUVA-backed images. The full
 * path is part of the GPU plan.
 *
 * TODO: missing API -- `SkImage.MakeFromYUVAPixmaps` +
 * `SkImage.makeColorSpace`. Flag-planting stub: empty draw,
 * fixed size.
 */
public class YUVMakeColorSpaceGM : GM() {

    override fun getName(): String = "yuv_make_color_space"
    override fun getISize(): SkISize = SkISize.Make(1100, 750)

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        val src = makeYuvaSampleImage() ?: return
        val p3 = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3) ?: return
        val dst = src.makeColorSpace(p3) ?: return
        canvas.drawImage(src, 40f, 40f)
        canvas.drawImage(dst, 560f, 40f)
    }

    private fun makeYuvaSampleImage(): SkImage? {
        val w = 420
        val h = 320
        val y = ByteArray(w * h)
        val u = ByteArray(w * h)
        val v = ByteArray(w * h)
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                val i = yy * w + xx
                y[i] = (32 + (190 * xx) / (w - 1)).toByte()
                u[i] = (96 + (80 * yy) / (h - 1)).toByte()
                v[i] = (64 + (140 * xx) / (w - 1)).toByte()
            }
        }
        val info = SkYUVAInfo(
            dimensions = SkISize.Make(w, h),
            planeConfig = SkYUVAInfo.PlaneConfig.kY_U_V,
            subsampling = SkYUVAInfo.Subsampling.k444,
            yuvColorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
        )
        val pixmaps = SkYUVAPixmaps(
            info,
            arrayOf(
                alpha8Plane(w, h, y),
                alpha8Plane(w, h, u),
                alpha8Plane(w, h, v),
            ),
        )
        return SkImage.MakeFromYUVAPixmaps(pixmaps)
    }

    private fun alpha8Plane(w: Int, h: Int, bytes: ByteArray): SkPixmap {
        val info = SkImageInfo.Make(w, h, SkColorType.kAlpha_8, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                buf.put(yy * rowBytes + xx, bytes[yy * w + xx])
            }
        }
        return SkPixmap(info, buf, rowBytes)
    }
}
