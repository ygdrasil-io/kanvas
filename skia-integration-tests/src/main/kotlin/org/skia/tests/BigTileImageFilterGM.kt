package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SkColor
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.core.SrcRectConstraint

/**
 * Port of Skia's `gm/bigtileimagefilter.cpp::BigTileImageFilterGM`
 * (512 × 512).
 *
 * Two image-filter exercises stacked on a black background :
 *
 *  1. **Top-left tile** — an [SkImageFilters.Image] of a 64 × 64 red
 *     circle texture is wrapped in [SkImageFilters.Tile] with
 *     `srcRect = (0,0,64,64)` and `dstRect = (0,0,512,512)`. The
 *     pipeline is applied to an empty `saveLayer(bounds=fullCanvas)`
 *     so the tiled red-circle stamp fills the whole 512×512 layer.
 *
 *  2. **Bottom-right tile (320,320 → 384,384)** — a `saveLayer` of a
 *     64×64 bound is opened with a `Tile(src=(0,0,64,64),
 *     dst=(0,0,64,64), input=null)` image filter. With a `null` input
 *     the tile filter passes its source rect through unchanged ; the
 *     real content for the tile comes from a subsequent
 *     `drawImageRect` of a green-circle 64×64 texture. The
 *     `setMatrix(I)` between the saveLayer and the drawImageRect
 *     resets the CTM so the dst rect coordinates are in canvas root
 *     space — that lands the green tile at `(320,320,384,384)`.
 *
 * **kanvas-skia adaptation** : upstream's `SkSurfaces::Raster` factory
 * is mirrored 1-for-1 by [SkSurface.MakeRaster]. Everything else maps
 * directly — `SkImageFilters::Image / Tile`, `saveLayer`, `setMatrix`,
 * `drawImageRect`, and `SrcRectConstraint::kStrict` are all available.
 *
 * C++ original (full file at `gm/bigtileimagefilter.cpp`) :
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *   canvas->clear(SK_ColorBLACK);
 *   {
 *     SkPaint p;
 *     const SkRect bound = SkRect::MakeIWH(kWidth, kHeight);
 *     sk_sp<SkImageFilter> imageSource(
 *         SkImageFilters::Image(fRedImage, SkFilterMode::kLinear));
 *     sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
 *         SkRect::MakeIWH(kBitmapSize, kBitmapSize),
 *         SkRect::MakeIWH(kWidth, kHeight),
 *         std::move(imageSource)));
 *     p.setImageFilter(std::move(tif));
 *     canvas->saveLayer(&bound, &p);
 *     canvas->restore();
 *   }
 *   {
 *     SkPaint p2;
 *     const SkRect bound2 = SkRect::MakeIWH(kBitmapSize, kBitmapSize);
 *     sk_sp<SkImageFilter> tif(SkImageFilters::Tile(
 *         SkRect::MakeIWH(kBitmapSize, kBitmapSize),
 *         SkRect::MakeIWH(kBitmapSize, kBitmapSize),
 *         nullptr));
 *     p2.setImageFilter(std::move(tif));
 *     canvas->translate(320, 320);
 *     canvas->saveLayer(&bound2, &p2);
 *     canvas->setMatrix(SkMatrix::I());
 *     SkRect bound3 = SkRect::MakeXYWH(320, 320,
 *                                       SkIntToScalar(kBitmapSize),
 *                                       SkIntToScalar(kBitmapSize));
 *     canvas->drawImageRect(fGreenImage.get(), bound2, bound3,
 *                           SkSamplingOptions(), nullptr,
 *                           SkCanvas::kStrict_SrcRectConstraint);
 *     canvas->restore();
 *   }
 * }
 * ```
 */
public class BigTileImageFilterGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
    }

    private lateinit var fRedImage: SkImage
    private lateinit var fGreenImage: SkImage

    override fun getName(): String = "bigtileimagefilter"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onOnceBeforeDraw() {
        fRedImage = createCircleTexture(kBitmapSize, SK_ColorRED)
        fGreenImage = createCircleTexture(kBitmapSize, SK_ColorGREEN)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        // ── Block 1 — Tile(redCircle) filling the whole canvas ───────
        run {
            val bound = SkRect.MakeIWH(kWidth, kHeight)
            val imageSource = SkImageFilters.Image(
                fRedImage,
                SkSamplingOptions(SkFilterMode.kLinear),
            )
            val tif = SkImageFilters.Tile(
                srcRect = SkRect.MakeIWH(kBitmapSize, kBitmapSize),
                dstRect = SkRect.MakeIWH(kWidth, kHeight),
                input = imageSource,
            )
            val p = SkPaint().apply { imageFilter = tif }
            c.saveLayer(bound, p)
            c.restore()
        }

        // ── Block 2 — empty Tile + drawImageRect of greenCircle ──────
        run {
            val bound2 = SkRect.MakeIWH(kBitmapSize, kBitmapSize)
            val tif = SkImageFilters.Tile(
                srcRect = SkRect.MakeIWH(kBitmapSize, kBitmapSize),
                dstRect = SkRect.MakeIWH(kBitmapSize, kBitmapSize),
                input = null,
            )
            val p2 = SkPaint().apply { imageFilter = tif }

            c.translate(320f, 320f)
            c.saveLayer(bound2, p2)
            c.setMatrix(SkMatrix.Identity)

            val bound3 = SkRect.MakeXYWH(
                320f, 320f,
                kBitmapSize.toFloat(),
                kBitmapSize.toFloat(),
            )
            c.drawImageRect(
                fGreenImage,
                bound2,
                bound3,
                SkSamplingOptions.Default,
                null,
                SrcRectConstraint.kStrict,
            )
            c.restore()
        }
    }

    // Upstream `create_circle_texture` : a `size × size` raster surface,
    // cleared to opaque black, then a stroked (width 3) circle in `color`
    // drawn at the centre with radius `size / 2`.
    private fun createCircleTexture(size: Int, color: SkColor): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(size, size))
        val canvas = surface.canvas
        canvas.clear(0xFF000000.toInt())

        val paint = SkPaint().apply {
            this.color = color
            strokeWidth = 3f
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.5f, paint)
        return surface.makeImageSnapshot()
    }

    private companion object {
        const val kWidth: Int = 512
        const val kHeight: Int = 512
        const val kBitmapSize: Int = 64
    }
}
