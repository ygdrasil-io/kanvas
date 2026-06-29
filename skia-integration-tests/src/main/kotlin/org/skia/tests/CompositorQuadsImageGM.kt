package org.skia.tests

import org.graphiks.kanvas.codec.SkCodec
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.gpu.YUVUtils
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Minimal port of upstream Skia's
 * [`gm/compositor_quads.cpp::CompositorGM("image", ...)`](https://github.com/google/skia/blob/main/gm/compositor_quads.cpp#L1060)
 * — the `compositor_quads_image` GM.
 *
 * **Upstream scope.** The full GM is GPU-only — it stresses
 * `SkCanvas::experimental_DrawEdgeAAImageSet`, BSP clipping, perspective
 * transforms, and the `LazyYUVImage` helper that composites a YUV-decoded
 * mandrill JPEG over the tile grid. About 1000 lines of C++ ; fully
 * porting it requires the EdgeAA image-set API plus a Ganesh compositor.
 *
 * **R-final.8 scope.** kanvas-skia ships the [YUVUtils] half of the
 * upstream toolchain — the helper that materialises an RGBA bitmap
 * from JPEG-decoded Y/U/V planes. This GM exercises that surface
 * end-to-end :
 *  1. Decode `images/mandrill_h1v1.jpg` via [SkCodec] → RGBA bitmap.
 *  2. Round-trip the RGBA into 4:2:0 BT.601 Y/U/V planes via
 *     [YUVUtils.yuvFromRgba] (mirrors the libjpeg-native YUV decode
 *     upstream's `LazyYUVImage::Make` does).
 *  3. Decode the planes back to RGBA via [YUVUtils.yuvToRgb].
 *  4. Tile the YUV-roundtripped image across a 5-column × 4-row grid
 *     mirroring the upstream layout (5 transforms × `kRowCount` = 4).
 *
 * The output dimensions match upstream's reported PNG reference
 * (955 × 699) so the [org.skia.testing.SimilarityTracker] ratchet has
 * a meaningful denominator. Pixel-fidelity vs. the GPU-rendered upstream
 * will start very low and ratchet up as we grow more of the compositor
 * surface.
 */
public class CompositorQuadsImageGM : GM() {

    override fun getName(): String = "compositor_quads_image"

    override fun getISize(): SkISize = SkISize.Make(IMG_W, IMG_H)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Build (or fall back to a synthetic checker for) the
        // YUV-roundtripped mandrill.
        val yuvImage = buildYuvMandrill() ?: buildCheckerFallback()

        // Layout parameters match upstream's grid (kColCount=3,
        // kRowCount=4, kTileWidth=40, kTileHeight=30 — see
        // `gm/compositor_quads.cpp:62-63`). The MxN cell metrics are
        // computed only to be document the upstream sizing ; the
        // simplified port draws the YUV bitmap directly into the
        // tile-grid bounds without the inter-cell gaps.
        val gridW = COL_COUNT * TILE_W
        val gridH = ROW_COUNT * TILE_H

        c.save()
        c.translate(15f + 120f, 15f) // upstream offsets : kOffset + kBannerWidth
        for (col in 0 until MATRIX_COUNT) {
            c.save()
            c.translate(0f, 40f) // upstream's kGap below the column header
            for (row in 0 until ROW_COUNT) {
                val paint = SkPaint().apply {
                    isAntiAlias = true
                    alpha = 128 // upstream uses 0.5 alpha in YUVTextureSetRenderer
                }
                c.drawImageRect(
                    image = yuvImage.asImage(),
                    src = SkRect.MakeWH(yuvImage.width.toFloat(), yuvImage.height.toFloat()),
                    dst = SkRect.MakeWH(gridW.toFloat(), gridH.toFloat()),
                    paint = paint,
                )
                c.translate(0f, 40f + ROW_COUNT * TILE_H)
            }
            c.restore()
            c.translate((40f + COL_COUNT * TILE_W), 0f)
        }
        c.restore()
    }

    private fun buildYuvMandrill(): SkBitmap? {
        val data = ToolUtils.GetResourceAsData("images/mandrill_h1v1.jpg")?.toByteArray()
            ?: return null
        val codec = SkCodec.MakeFromData(data) ?: return null
        val (rgba, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || rgba == null) return null

        // Round-trip RGBA → 4:2:0 BT.601 YUV → RGBA. This both
        // exercises [YUVUtils] symmetrically (encode + decode) and
        // mirrors the upstream `LazyYUVImage::reset` fall-back path
        // when no Ganesh context is available.
        val (yPlane, uPlane, vPlane) = YUVUtils.yuvFromRgba(
            rgba = rgba,
            subsampling = YUVUtils.YUVSubsampling.k420,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
        )
        return YUVUtils.yuvToRgb(
            y = yPlane,
            u = uPlane,
            v = vPlane,
            width = rgba.width,
            height = rgba.height,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
            subsampling = YUVUtils.YUVSubsampling.k420,
        )
    }

    private fun buildCheckerFallback(): SkBitmap {
        val w = 64
        val h = 64
        val bm = SkBitmap(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val checker = (((x / 8) + (y / 8)) and 1) == 0
                val argb = if (checker) {
                    SkColorSetARGB(0xFF, 0xC0, 0x40, 0x40)
                } else {
                    SkColorSetARGB(0xFF, 0x40, 0x40, 0xC0)
                }
                bm.setPixel(x, y, argb)
            }
        }
        return bm
    }

    public companion object {
        private const val IMG_W: Int = 955
        private const val IMG_H: Int = 699
        private const val TILE_W: Int = 40
        private const val TILE_H: Int = 30
        private const val ROW_COUNT: Int = 4
        private const val COL_COUNT: Int = 3
        private const val MATRIX_COUNT: Int = 5
    }
}
