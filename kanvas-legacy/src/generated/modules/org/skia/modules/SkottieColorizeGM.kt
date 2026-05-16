package org.skia.modules

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.foundation.SkUnichar
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tests.DrawResult
import org.skia.tests.GM

/**
 * C++ original:
 * ```cpp
 * class SkottieColorizeGM : public skiagm::GM {
 * public:
 *     SkottieColorizeGM(const char* name, const char* resource)
 *         : fName(name)
 *         , fResource(resource)
 *     {}
 *
 * protected:
 *     SkString getName() const override { return SkStringPrintf("skottie_colorize_%s", fName); }
 *
 *     SkISize getISize() override { return SkISize::Make(kSize, kSize); }
 *
 *     void onOnceBeforeDraw() override {
 *         if (auto stream = GetResourceAsStream(fResource)) {
 *             fPropManager = std::make_unique<skottie_utils::CustomPropertyManager>();
 *             fAnimation   = skottie::Animation::Builder()
 *                               .setFontManager(ToolUtils::TestFontMgr())
 *                               .setPropertyObserver(fPropManager->getPropertyObserver())
 *                               .setTextShapingFactory(SkShapers::BestAvailable())
 *                               .make(stream.get());
 *             fColorProps  = fPropManager->getColorProps();
 *             fTextProps   = fPropManager->getTextProps();
 *         }
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!fAnimation) {
 *             *errorMsg = "No animation";
 *             return DrawResult::kFail;
 *         }
 *
 *         auto dest = SkRect::MakeWH(kSize, kSize);
 *         fAnimation->render(canvas, &dest);
 *         return DrawResult::kOk;
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         if (!fAnimation) {
 *             return false;
 *         }
 *
 *         const auto duration = fAnimation->duration();
 *         fAnimation->seek(std::fmod(1e-9 * nanos, duration) / duration);
 *         return true;
 *     }
 *
 *     bool onChar(SkUnichar uni) override {
 *         static constexpr SkColor kColors[] = {
 *             SK_ColorBLACK,
 *             SK_ColorRED,
 *             SK_ColorGREEN,
 *             SK_ColorYELLOW,
 *             SK_ColorCYAN,
 *         };
 *
 *         if (uni == 'c') {
 *             fColorIndex = (fColorIndex + 1) % std::size(kColors);
 *             for (const auto& prop : fColorProps) {
 *                 fPropManager->setColor(prop, kColors[fColorIndex]);
 *             }
 *             for (const auto& prop : fTextProps) {
 *                 auto txtval = fPropManager->getText(prop);
 *                 txtval.fFillColor = kColors[fColorIndex];
 *                 fPropManager->setText(prop, txtval);
 *             }
 *             return true;
 *         }
 *
 *         return false;
 *     }
 *
 * private:
 *     inline static constexpr SkScalar kSize = 800;
 *
 *     const char*                                                fName;
 *     const char*                                                fResource;
 *
 *     sk_sp<skottie::Animation>                                  fAnimation;
 *     std::unique_ptr<skottie_utils::CustomPropertyManager>      fPropManager;
 *     std::vector<skottie_utils::CustomPropertyManager::PropKey> fColorProps,
 *                                                                fTextProps;
 *     size_t                                                     fColorIndex = 0;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class SkottieColorizeGM public constructor(
  name: String?,
  resource: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kSize = 800
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * const char*                                                fName
   * ```
   */
  private val fResource: String? = TODO("Initialize fResource")

  /**
   * C++ original:
   * ```cpp
   * const char*                                                fResource
   * ```
   */
  private var fAnimation: SkSp<Animation> = TODO("Initialize fAnimation")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skottie::Animation>                                  fAnimation
   * ```
   */
  private var fPropManager: Int = TODO("Initialize fPropManager")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skottie_utils::CustomPropertyManager>      fPropManager
   * ```
   */
  private var fColorProps: Int = TODO("Initialize fColorProps")

  /**
   * C++ original:
   * ```cpp
   * std::vector<skottie_utils::CustomPropertyManager::PropKey> fColorProps
   * ```
   */
  private var fTextProps: Int = TODO("Initialize fTextProps")

  /**
   * C++ original:
   * ```cpp
   * std::vector<skottie_utils::CustomPropertyManager::PropKey> fColorProps,
   *                                                                fTextProps
   * ```
   */
  private var fColorIndex: ULong = TODO("Initialize fColorIndex")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkStringPrintf("skottie_colorize_%s", fName); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kSize, kSize); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         if (auto stream = GetResourceAsStream(fResource)) {
   *             fPropManager = std::make_unique<skottie_utils::CustomPropertyManager>();
   *             fAnimation   = skottie::Animation::Builder()
   *                               .setFontManager(ToolUtils::TestFontMgr())
   *                               .setPropertyObserver(fPropManager->getPropertyObserver())
   *                               .setTextShapingFactory(SkShapers::BestAvailable())
   *                               .make(stream.get());
   *             fColorProps  = fPropManager->getColorProps();
   *             fTextProps   = fPropManager->getTextProps();
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!fAnimation) {
   *             *errorMsg = "No animation";
   *             return DrawResult::kFail;
   *         }
   *
   *         auto dest = SkRect::MakeWH(kSize, kSize);
   *         fAnimation->render(canvas, &dest);
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         if (!fAnimation) {
   *             return false;
   *         }
   *
   *         const auto duration = fAnimation->duration();
   *         fAnimation->seek(std::fmod(1e-9 * nanos, duration) / duration);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onChar(SkUnichar uni) override {
   *         static constexpr SkColor kColors[] = {
   *             SK_ColorBLACK,
   *             SK_ColorRED,
   *             SK_ColorGREEN,
   *             SK_ColorYELLOW,
   *             SK_ColorCYAN,
   *         };
   *
   *         if (uni == 'c') {
   *             fColorIndex = (fColorIndex + 1) % std::size(kColors);
   *             for (const auto& prop : fColorProps) {
   *                 fPropManager->setColor(prop, kColors[fColorIndex]);
   *             }
   *             for (const auto& prop : fTextProps) {
   *                 auto txtval = fPropManager->getText(prop);
   *                 txtval.fFillColor = kColors[fColorIndex];
   *                 fPropManager->setText(prop, txtval);
   *             }
   *             return true;
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  protected override fun onChar(uni: SkUnichar): Boolean {
    TODO("Implement onChar")
  }

  public companion object {
    private val kSize: SkScalar = TODO("Initialize kSize")
  }
}
