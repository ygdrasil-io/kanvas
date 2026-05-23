package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkYUVAInfo
import org.skia.foundation.SkYUVAPixmaps
import org.skia.gpu.YUVUtils.YUVSubsampling
import org.skia.tools.SkGpuTestUtils
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/wacky_yuv_formats.cpp::YUVSplitterGM`
 * (registered as `yuv_splitter`, 1280 x 768, upstream GPU-only).
 *
 * Body walks the four reference [SkYUVAInfo.YUVColorSpace]s (Rec.709,
 * Rec.601, JPEG, BT.2020), splits a fixed `mandrill_256.png` into A8
 * YUV planes via [SkGpuTestUtils.MakeYUVAPlanesAsA8], wraps the
 * resulting planes as an [SkYUVAPixmaps] via
 * [SkYUVAPixmaps.FromExternalPixmaps], converts back through
 * [SkImages.YUVA] (the raster equivalent of upstream's
 * `SkImages::TextureFromYUVAPixmaps`), and draws the round-trip
 * result alongside the per-plane A8 images at the bottom of the
 * canvas.
 *
 * ## Bucket : INTRACTABLE (GPU-only, but stubbed with real call sites)
 *
 * The split kernel ([SkGpuTestUtils.MakeYUVAPlanesAsA8]) is
 * `TODO("STUB.YUVA_PIXMAPS")` — the per-channel YUV mixer matrices for
 * Rec.709 / Rec.601 / JPEG / BT.2020 are not yet wired. As soon as the
 * Kotlin port lands the matrix tables (mirror upstream's
 * `SkColorMatrix_RGB2YUV`), the call sites here resolve into a real
 * raster image and the matching [YUVSplitterTest] can drop its
 * `@Disabled`.
 *
 * Upstream cpp : lines 1321-1376 of `gm/wacky_yuv_formats.cpp`.
 */
public class YUVSplitterGM : GM() {

    private var orig: SkImage? = null

    override fun getName(): String = "yuv_splitter"

    override fun getISize(): SkISize = SkISize.Make(1280, 768)

    override fun onOnceBeforeDraw() {
        orig = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val src = orig ?: return

        c.translate(src.width.toFloat(), 0f)
        c.save()

        var lastInfo: SkYUVAInfo? = null
        var lastPlanes: List<SkImage> = emptyList()

        for (cs in listOf(
            SkYUVAInfo.YUVColorSpace.kRec709_Limited_YUV_ColorSpace,
            SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace,
            SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
            SkYUVAInfo.YUVColorSpace.kBT2020_8bit_Limited_YUV_ColorSpace,
        )) {
            // First iteration throws STUB.YUVA_PIXMAPS — caught by the
            // @Disabled test wrapper. Body deliberately wires every
            // upstream call site so `grep TODO()` surfaces the gap.
            val planesInfo = SkGpuTestUtils.MakeYUVAPlanesAsA8(
                src = src,
                colorSpace = cs,
                subsampling = YUVSubsampling.k444,
            )
            lastInfo = planesInfo.info
            lastPlanes = planesInfo.planes

            // Upstream peeks the pixels via `SkImage::peekPixels(SkPixmap*)` ;
            // `:kanvas-skia` doesn't expose `peekPixels` (borrowed view), so
            // we fall back to `readPixels(SkPixmap, srcX, srcY)` (eager copy)
            // — same end state, the SkPixmap holds the plane's pixels.
            val pixmaps = Array(planesInfo.info.numPlanes()) { SkPixmap() }
            for (i in 0 until planesInfo.info.numPlanes()) {
                planesInfo.planes[i].readPixels(pixmaps[i], 0, 0)
            }
            val yuvaPixmaps = SkYUVAPixmaps.FromExternalPixmaps(planesInfo.info, pixmaps)
            val img = SkImages.YUVA(yuvaPixmaps)
            if (img != null) {
                c.drawImage(img, 0f, 0f)
                drawDiff(c, 0f, src.height.toFloat(), src, img)
            }
            c.translate(src.width.toFloat(), 0f)
        }

        c.restore()
        c.translate(-src.width.toFloat(), 0f)
        var y = 0
        for (plane in lastPlanes) {
            c.drawImage(plane, 0f, y.toFloat())
            y += plane.height
        }
    }

    /**
     * Mirrors upstream's `draw_diff(canvas, x, y, orig, reconstructed)` —
     * draws a scaled absolute difference between [orig] and
     * [reconstructed] to make YUV round-trip error visible. Stubbed for
     * now (alongside `STUB.YUVA_PIXMAPS` — the diff is only meaningful
     * once the split kernel produces real planes). Upstream uses
     * `SkShaders::Blend(kDifference, ...)` with a 2x magnification.
     */
    private fun drawDiff(canvas: SkCanvas, x: Float, y: Float, orig: SkImage, reconstructed: SkImage) {
        // No-op until STUB.YUVA_PIXMAPS lands.
    }
}
