package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class CopyTo4444GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("copyTo4444"); }
 *
 *     SkISize getISize() override { return {360, 180}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkBitmap bm, bm4444;
 *         if (!ToolUtils::GetResourceAsBitmap("images/dog.jpg", &bm)) {
 *             *errorMsg = "Could not decode the file. Did you forget to set the resourcePath?";
 *             return DrawResult::kFail;
 *         }
 *         canvas->drawImage(bm.asImage(), 0, 0);
 *
 *         // This should dither or we will see artifacts in the background of the image.
 *         SkAssertResult(ToolUtils::copy_to(&bm4444, kARGB_4444_SkColorType, bm));
 *         canvas->drawImage(bm4444.asImage(), SkIntToScalar(bm.width()), 0);
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class CopyTo4444GM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("copyTo4444"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {360, 180}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkBitmap bm, bm4444;
   *         if (!ToolUtils::GetResourceAsBitmap("images/dog.jpg", &bm)) {
   *             *errorMsg = "Could not decode the file. Did you forget to set the resourcePath?";
   *             return DrawResult::kFail;
   *         }
   *         canvas->drawImage(bm.asImage(), 0, 0);
   *
   *         // This should dither or we will see artifacts in the background of the image.
   *         SkAssertResult(ToolUtils::copy_to(&bm4444, kARGB_4444_SkColorType, bm));
   *         canvas->drawImage(bm4444.asImage(), SkIntToScalar(bm.width()), 0);
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
