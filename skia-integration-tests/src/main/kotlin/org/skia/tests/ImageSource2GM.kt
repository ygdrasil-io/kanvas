package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/imagesource2.cpp::ImageSourceGM` GM family
 * (`imagesrc2_none` / `_low` / `_med` / `_high`, 256 × 256, background
 * white).
 *
 * Repro for `crbug.com/472795`: applies [SkImageFilters.Image] in a
 * `saveLayer/restore` with a fractional destination rect
 * (`(0.75, 0.75, 225.75, 225.75)`) to expose any half-pixel
 * inconsistency between CPU and GPU resamplers.
 *
 * The source image is a 503 × 503 high-frequency stripe pattern
 * (3-pixel-wide vertical bars cycling through Red/Gray/Green/Gray/Blue/
 * Gray/Cyan/Gray/Magenta/Gray/Yellow/Gray/White/Gray), built once in
 * [onOnceBeforeDraw] via an offscreen [SkSurface]. The four variants
 * differ only in their [SkSamplingOptions] :
 *
 *  - `none` : default-ctor `SkSamplingOptions()` (nearest).
 *  - `low`  : `SkSamplingOptions(kLinear)`.
 *  - `med`  : `SkSamplingOptions(kLinear, kLinear)` (mip-mapped linear).
 *  - `high` : `SkSamplingOptions({1/3, 1/3})` (Mitchell cubic).
 *
 * C++ original :
 * ```cpp
 * static constexpr int kImageSize = 503;
 *
 * void onOnceBeforeDraw() override {
 *     constexpr SkPMColor gColors[] = {
 *         SK_ColorRED, SK_ColorGRAY, SK_ColorGREEN, SK_ColorGRAY,
 *         SK_ColorBLUE, SK_ColorGRAY, SK_ColorCYAN, SK_ColorGRAY,
 *         SK_ColorMAGENTA, SK_ColorGRAY, SK_ColorYELLOW, SK_ColorGRAY,
 *         SK_ColorWHITE, SK_ColorGRAY,
 *     };
 *     auto surface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(kImageSize, kImageSize)));
 *     SkCanvas* c = surface->getCanvas();
 *     int curColor = 0;
 *     for (int x = 0; x < kImageSize; x += 3) {
 *         SkRect r = SkRect::MakeXYWH(x, 0, 3, kImageSize);
 *         SkPaint p; p.setColor(gColors[curColor]);
 *         c->drawRect(r, p);
 *         curColor = (curColor + 1) % std::size(gColors);
 *     }
 *     fImage = surface->makeImageSnapshot();
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     const SkRect srcRect = SkRect::MakeLTRB(0, 0, kImageSize, kImageSize);
 *     const SkRect dstRect = SkRect::MakeLTRB(0.75, 0.75, 225.75, 225.75);
 *     SkPaint p;
 *     p.setImageFilter(SkImageFilters::Image(fImage, srcRect, dstRect, fSampling));
 *     canvas->saveLayer(nullptr, &p);
 *     canvas->restore();
 * }
 * ```
 */
public open class ImageSource2GM(
    private val suffix: String,
    private val sampling: SkSamplingOptions,
) : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    private lateinit var fImage: SkImage

    override fun getName(): String = "imagesrc2_$suffix"

    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onOnceBeforeDraw() {
        val colors = intArrayOf(
            SK_ColorRED, SK_ColorGRAY,
            SK_ColorGREEN, SK_ColorGRAY,
            SK_ColorBLUE, SK_ColorGRAY,
            SK_ColorCYAN, SK_ColorGRAY,
            SK_ColorMAGENTA, SK_ColorGRAY,
            SK_ColorYELLOW, SK_ColorGRAY,
            SK_ColorWHITE, SK_ColorGRAY,
        )
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(kImageSize, kImageSize))
        val c = surface.canvas
        var curColor = 0
        var x = 0
        while (x < kImageSize) {
            val r = SkRect.MakeXYWH(x.toFloat(), 0f, 3f, kImageSize.toFloat())
            val p = SkPaint().apply { color = colors[curColor] }
            c.drawRect(r, p)
            curColor = (curColor + 1) % colors.size
            x += 3
        }
        fImage = surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val srcRect = SkRect.MakeLTRB(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat())
        val dstRect = SkRect.MakeLTRB(0.75f, 0.75f, 225.75f, 225.75f)
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.Image(fImage, srcRect, dstRect, sampling)
        }
        c.saveLayer(null, paint)
        c.restore()
    }

    private companion object {
        const val kImageSize: Int = 503
    }
}

public class ImageSource2NoneGM : ImageSource2GM("none", SkSamplingOptions())
public class ImageSource2LowGM : ImageSource2GM("low", SkSamplingOptions(SkFilterMode.kLinear))
public class ImageSource2MedGM : ImageSource2GM(
    "med",
    SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
)
public class ImageSource2HighGM : ImageSource2GM(
    "high",
    SkSamplingOptions(SkCubicResampler(1f / 3f, 1f / 3f)),
)
