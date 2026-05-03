package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class CropImageFilterGM : public GM {
 * public:
 *     CropImageFilterGM(SkTileMode inputMode, SkTileMode outputMode)
 *             : fInputMode(inputMode)
 *             , fOutputMode(outputMode) {}
 *
 * protected:
 *     SkISize getISize() override {
 *         return {SkScalarRoundToInt(4.f * (kExampleBounds.fRight + 1.f) - 1.f),
 *                 SkScalarRoundToInt(5.f * (kExampleBounds.fBottom + 1.f) - 1.f)};
 *     }
 *     SkString getName() const override {
 *         SkString name("crop_imagefilter_");
 *         switch(fInputMode) {
 *             case SkTileMode::kDecal:  name.append("decal");  break;
 *             case SkTileMode::kClamp:  name.append("clamp");  break;
 *             case SkTileMode::kRepeat: name.append("repeat"); break;
 *             case SkTileMode::kMirror: name.append("mirror"); break;
 *         }
 *         name.append("-in_");
 *
 *         switch (fOutputMode) {
 *             case SkTileMode::kDecal:  name.append("decal");  break;
 *             case SkTileMode::kClamp:  name.append("clamp");  break;
 *             case SkTileMode::kRepeat: name.append("repeat"); break;
 *             case SkTileMode::kMirror: name.append("mirror"); break;
 *         }
 *         name.append("-out");
 *         return name;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         draw_example_grid(canvas, fInputMode, fOutputMode);
 *     }
 *
 * private:
 *     SkTileMode fInputMode;
 *     SkTileMode fOutputMode;
 * }
 * ```
 */
public open class CropImageFilterGM public constructor(
  inputMode: SkTileMode,
  outputMode: SkTileMode,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkTileMode fInputMode
   * ```
   */
  private var fInputMode: SkTileMode = TODO("Initialize fInputMode")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fOutputMode
   * ```
   */
  private var fOutputMode: SkTileMode = TODO("Initialize fOutputMode")

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return {SkScalarRoundToInt(4.f * (kExampleBounds.fRight + 1.f) - 1.f),
   *                 SkScalarRoundToInt(5.f * (kExampleBounds.fBottom + 1.f) - 1.f)};
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("crop_imagefilter_");
   *         switch(fInputMode) {
   *             case SkTileMode::kDecal:  name.append("decal");  break;
   *             case SkTileMode::kClamp:  name.append("clamp");  break;
   *             case SkTileMode::kRepeat: name.append("repeat"); break;
   *             case SkTileMode::kMirror: name.append("mirror"); break;
   *         }
   *         name.append("-in_");
   *
   *         switch (fOutputMode) {
   *             case SkTileMode::kDecal:  name.append("decal");  break;
   *             case SkTileMode::kClamp:  name.append("clamp");  break;
   *             case SkTileMode::kRepeat: name.append("repeat"); break;
   *             case SkTileMode::kMirror: name.append("mirror"); break;
   *         }
   *         name.append("-out");
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         draw_example_grid(canvas, fInputMode, fOutputMode);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
