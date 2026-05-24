package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/imagefiltersbase.cpp::ImageFiltersTextBaseGM`
 * (abstract base, never registered directly).
 *
 * Exercises the interaction of image / color filters with text rendering
 * (LCD downgrade) and `saveLayer` : for each of the four combinations
 * (with/without filter) × (with/without saveLayer) it renders
 * "Hamburgefon" in three edging modes (alias, AA, subpixelAA) so that
 * differences in LCD-stripping behaviour are visible.
 *
 * Layout: 2 columns (no filter / filter) × 2 rows (no saveLayer /
 * saveLayer), each cell is a 250-wide waterfall of three text lines
 * at 30 px, translated (20, 40) from the origin.
 *
 * Canvas size: 512 × 342 (matches upstream `getISize()`).
 *
 * Upstream C++ reference:
 * ```cpp
 * class ImageFiltersTextBaseGM : public skiagm::GM {
 *     SkString fSuffix;
 * public:
 *     ImageFiltersTextBaseGM(const char suffix[]) : fSuffix(suffix) {}
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name;
 *         name.printf("%s_%s", "textfilter", fSuffix.c_str());
 *         return name;
 *     }
 *     SkISize getISize() override { return SkISize::Make(512, 342); }
 *
 *     void drawWaterfall(SkCanvas* canvas, const SkPaint& paint) {
 *         static const SkFont::Edging kEdgings[3] = {
 *             SkFont::Edging::kAlias,
 *             SkFont::Edging::kAntiAlias,
 *             SkFont::Edging::kSubpixelAntiAlias,
 *         };
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 30);
 *         SkAutoCanvasRestore acr(canvas, true);
 *         for (SkFont::Edging edging : kEdgings) {
 *             font.setEdging(edging);
 *             canvas->drawString("Hamburgefon", 0, 0, font, paint);
 *             canvas->translate(0, 40);
 *         }
 *     }
 *
 *     virtual void installFilter(SkPaint* paint) = 0;
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(20, 40);
 *         for (int doSaveLayer = 0; doSaveLayer <= 1; ++doSaveLayer) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *             for (int useFilter = 0; useFilter <= 1; ++useFilter) {
 *                 SkAutoCanvasRestore acr2(canvas, true);
 *                 SkPaint paint;
 *                 if (useFilter) {
 *                     this->installFilter(&paint);
 *                 }
 *                 if (doSaveLayer) {
 *                     canvas->saveLayer(nullptr, &paint);
 *                     paint.setImageFilter(nullptr);
 *                 }
 *                 this->drawWaterfall(canvas, paint);
 *                 acr2.restore();
 *                 canvas->translate(250, 0);
 *             }
 *             acr.restore();
 *             canvas->translate(0, 200);
 *         }
 *     }
 * };
 * ```
 */
public abstract class ImageFiltersTextBaseGM(private val suffix: String) : GM() {

    override fun getName(): String = "textfilter_$suffix"
    override fun getISize(): SkISize = SkISize.Make(512, 342)

    /** Subclass installs the desired filter into [paint]. */
    protected abstract fun installFilter(paint: SkPaint)

    private fun drawWaterfall(canvas: SkCanvas, paint: SkPaint) {
        val edgings = arrayOf(
            SkFont.Edging.kAlias,
            SkFont.Edging.kAntiAlias,
            SkFont.Edging.kSubpixelAntiAlias,
        )
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 30f)
        canvas.save()
        for (edging in edgings) {
            font.edging = edging
            canvas.drawString("Hamburgefon", 0f, 0f, font, paint)
            canvas.translate(0f, 40f)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(20f, 40f)

        for (doSaveLayer in 0..1) {
            c.save()
            for (useFilter in 0..1) {
                c.save()
                val paint = SkPaint()
                if (useFilter == 1) {
                    installFilter(paint)
                }
                if (doSaveLayer == 1) {
                    c.saveLayer(null, paint)
                    paint.imageFilter = null
                }
                drawWaterfall(c, paint)
                c.restore()
                c.translate(250f, 0f)
            }
            c.restore()
            c.translate(0f, 200f)
        }
    }
}

/**
 * Port of `ImageFiltersText_IF` (registered as `textfilter_image`).
 *
 * Installs `SkImageFilters::Blur(1.5, 1.5, nullptr)` — a mild blur applied
 * as an image filter — to verify that LCD text is properly demoted to AA
 * when a filter is active.
 *
 * Upstream C++ reference:
 * ```cpp
 * class ImageFiltersText_IF : public ImageFiltersTextBaseGM {
 * public:
 *     ImageFiltersText_IF() : ImageFiltersTextBaseGM("image") {}
 *     void installFilter(SkPaint* paint) override {
 *         paint->setImageFilter(SkImageFilters::Blur(1.5f, 1.5f, nullptr));
 *     }
 * };
 * DEF_GM( return new ImageFiltersText_IF; )
 * ```
 */
public class ImageFiltersTextIfGM : ImageFiltersTextBaseGM("image") {
    override fun installFilter(paint: SkPaint) {
        paint.imageFilter = SkImageFilters.Blur(1.5f, 1.5f, null)
    }
}

/**
 * Port of `ImageFiltersText_CF` (registered as `textfilter_color`).
 *
 * Installs `SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn)` —
 * a color filter that tints the text to blue while preserving its alpha —
 * to verify that LCD text is properly demoted to AA when a color filter is
 * active.
 *
 * Upstream C++ reference:
 * ```cpp
 * class ImageFiltersText_CF : public ImageFiltersTextBaseGM {
 * public:
 *     ImageFiltersText_CF() : ImageFiltersTextBaseGM("color") {}
 *     void installFilter(SkPaint* paint) override {
 *         paint->setColorFilter(
 *             SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn));
 *     }
 * };
 * DEF_GM( return new ImageFiltersText_CF; )
 * ```
 */
public class ImageFiltersTextCfGM : ImageFiltersTextBaseGM("color") {
    override fun installFilter(paint: SkPaint) {
        paint.colorFilter = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kSrcIn)
    }
}

public typealias ImageFiltersText_IFGM = ImageFiltersTextIfGM
public typealias ImageFiltersText_CFGM = ImageFiltersTextCfGM
