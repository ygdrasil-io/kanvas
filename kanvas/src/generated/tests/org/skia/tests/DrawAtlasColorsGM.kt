package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawAtlasColorsGM : public skiagm::GM {
 * public:
 *     DrawAtlasColorsGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("draw-atlas-colors"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(kNumXferModes * (kAtlasSize + kPad) + kPad,
 *                              2 * kNumColors * (kAtlasSize + kPad) + kTextPad + kPad);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkRect target = SkRect::MakeWH(SkIntToScalar(kAtlasSize), SkIntToScalar(kAtlasSize));
 *
 *         auto atlas = make_atlas(canvas, kAtlasSize);
 *
 *         const SkBlendMode gModes[] = {
 *             SkBlendMode::kClear,
 *             SkBlendMode::kSrc,
 *             SkBlendMode::kDst,
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *             SkBlendMode::kSrcOut,
 *             SkBlendMode::kDstOut,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *             SkBlendMode::kXor,
 *             SkBlendMode::kPlus,
 *             SkBlendMode::kModulate,
 *             SkBlendMode::kScreen,
 *             SkBlendMode::kOverlay,
 *             SkBlendMode::kDarken,
 *             SkBlendMode::kLighten,
 *             SkBlendMode::kColorDodge,
 *             SkBlendMode::kColorBurn,
 *             SkBlendMode::kHardLight,
 *             SkBlendMode::kSoftLight,
 *             SkBlendMode::kDifference,
 *             SkBlendMode::kExclusion,
 *             SkBlendMode::kMultiply,
 *             SkBlendMode::kHue,
 *             SkBlendMode::kSaturation,
 *             SkBlendMode::kColor,
 *             SkBlendMode::kLuminosity,
 *         };
 *
 *         SkColor gColors[] = {
 *             SK_ColorWHITE,
 *             SK_ColorRED,
 *             0x88888888,         // transparent grey
 *             0x88000088          // transparent blue
 *         };
 *
 *         const int numModes = std::size(gModes);
 *         SkASSERT(numModes == kNumXferModes);
 *         const int numColors = std::size(gColors);
 *         SkASSERT(numColors == kNumColors);
 *         SkRSXform xforms[numColors];
 *         SkRect rects[numColors];
 *         SkColor quadColors[numColors];
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         for (int i = 0; i < numColors; ++i) {
 *             xforms[i].set(1.0f, 0.0f, SkIntToScalar(kPad), i*(target.width()+kPad));
 *             rects[i] = target;
 *             quadColors[i] = gColors[i];
 *         }
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextPad);
 *
 *         for (int i = 0; i < numModes; ++i) {
 *             const char* label = SkBlendMode_Name(gModes[i]);
 *             canvas->drawString(label, i*(target.width()+kPad)+kPad, SkIntToScalar(kTextPad),
 *                                font, paint);
 *         }
 *
 *         for (int i = 0; i < numModes; ++i) {
 *             canvas->save();
 *             canvas->translate(SkIntToScalar(i*(target.height()+kPad)),
 *                               SkIntToScalar(kTextPad+kPad));
 *             // w/o a paint
 *             canvas->drawAtlas(atlas.get(), xforms, rects, quadColors,
 *                               gModes[i], SkSamplingOptions(), nullptr, nullptr);
 *             canvas->translate(0.0f, numColors*(target.height()+kPad));
 *             // w a paint
 *             canvas->drawAtlas(atlas.get(), xforms, rects, quadColors,
 *                               gModes[i], SkSamplingOptions(), nullptr, &paint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kNumXferModes = 29;
 *     inline static constexpr int kNumColors = 4;
 *     inline static constexpr int kAtlasSize = 30;
 *     inline static constexpr int kPad = 2;
 *     inline static constexpr int kTextPad = 8;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class DrawAtlasColorsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("draw-atlas-colors"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(kNumXferModes * (kAtlasSize + kPad) + kPad,
   *                              2 * kNumColors * (kAtlasSize + kPad) + kTextPad + kPad);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkRect target = SkRect::MakeWH(SkIntToScalar(kAtlasSize), SkIntToScalar(kAtlasSize));
   *
   *         auto atlas = make_atlas(canvas, kAtlasSize);
   *
   *         const SkBlendMode gModes[] = {
   *             SkBlendMode::kClear,
   *             SkBlendMode::kSrc,
   *             SkBlendMode::kDst,
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *             SkBlendMode::kSrcOut,
   *             SkBlendMode::kDstOut,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *             SkBlendMode::kXor,
   *             SkBlendMode::kPlus,
   *             SkBlendMode::kModulate,
   *             SkBlendMode::kScreen,
   *             SkBlendMode::kOverlay,
   *             SkBlendMode::kDarken,
   *             SkBlendMode::kLighten,
   *             SkBlendMode::kColorDodge,
   *             SkBlendMode::kColorBurn,
   *             SkBlendMode::kHardLight,
   *             SkBlendMode::kSoftLight,
   *             SkBlendMode::kDifference,
   *             SkBlendMode::kExclusion,
   *             SkBlendMode::kMultiply,
   *             SkBlendMode::kHue,
   *             SkBlendMode::kSaturation,
   *             SkBlendMode::kColor,
   *             SkBlendMode::kLuminosity,
   *         };
   *
   *         SkColor gColors[] = {
   *             SK_ColorWHITE,
   *             SK_ColorRED,
   *             0x88888888,         // transparent grey
   *             0x88000088          // transparent blue
   *         };
   *
   *         const int numModes = std::size(gModes);
   *         SkASSERT(numModes == kNumXferModes);
   *         const int numColors = std::size(gColors);
   *         SkASSERT(numColors == kNumColors);
   *         SkRSXform xforms[numColors];
   *         SkRect rects[numColors];
   *         SkColor quadColors[numColors];
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         for (int i = 0; i < numColors; ++i) {
   *             xforms[i].set(1.0f, 0.0f, SkIntToScalar(kPad), i*(target.width()+kPad));
   *             rects[i] = target;
   *             quadColors[i] = gColors[i];
   *         }
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextPad);
   *
   *         for (int i = 0; i < numModes; ++i) {
   *             const char* label = SkBlendMode_Name(gModes[i]);
   *             canvas->drawString(label, i*(target.width()+kPad)+kPad, SkIntToScalar(kTextPad),
   *                                font, paint);
   *         }
   *
   *         for (int i = 0; i < numModes; ++i) {
   *             canvas->save();
   *             canvas->translate(SkIntToScalar(i*(target.height()+kPad)),
   *                               SkIntToScalar(kTextPad+kPad));
   *             // w/o a paint
   *             canvas->drawAtlas(atlas.get(), xforms, rects, quadColors,
   *                               gModes[i], SkSamplingOptions(), nullptr, nullptr);
   *             canvas->translate(0.0f, numColors*(target.height()+kPad));
   *             // w a paint
   *             canvas->drawAtlas(atlas.get(), xforms, rects, quadColors,
   *                               gModes[i], SkSamplingOptions(), nullptr, &paint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kNumXferModes: Int = TODO("Initialize kNumXferModes")

    private val kNumColors: Int = TODO("Initialize kNumColors")

    private val kAtlasSize: Int = TODO("Initialize kAtlasSize")

    private val kPad: Int = TODO("Initialize kPad")

    private val kTextPad: Int = TODO("Initialize kTextPad")
  }
}
