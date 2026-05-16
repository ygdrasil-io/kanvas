package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkUnichar
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class MacAAFontsGM : public skiagm::GM {
 *     SkScalar fSize = 16;
 *     SkScalar fXPos = 0;
 *
 * public:
 *     MacAAFontsGM() {}
 *     ~MacAAFontsGM() override {}
 *
 * protected:
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         test_mac_fonts(canvas, fSize, fXPos);
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     SkISize getISize() override { return {1024, 768}; }
 *
 *     SkString getName() const override { return SkString("macaatest"); }
 *
 *     bool onChar(SkUnichar uni) override {
 *         switch (uni) {
 *             case 'i': fSize += 1; return true;
 *             case 'k': fSize -= 1; return true;
 *             case 'j': fXPos -= 1.0f/16; return true;
 *             case 'l': fXPos += 1.0f/16; return true;
 *             default: break;
 *         }
 *         return false;
 *     }
 * }
 * ```
 */
public open class MacAAFontsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSize = 16
   * ```
   */
  private var fSize: SkScalar = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fXPos = 0
   * ```
   */
  private var fXPos: SkScalar = TODO("Initialize fXPos")

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         test_mac_fonts(canvas, fSize, fXPos);
   *
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
   * SkISize getISize() override { return {1024, 768}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("macaatest"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onChar(SkUnichar uni) override {
   *         switch (uni) {
   *             case 'i': fSize += 1; return true;
   *             case 'k': fSize -= 1; return true;
   *             case 'j': fXPos -= 1.0f/16; return true;
   *             case 'l': fXPos += 1.0f/16; return true;
   *             default: break;
   *         }
   *         return false;
   *     }
   * ```
   */
  protected override fun onChar(uni: SkUnichar): Boolean {
    TODO("Implement onChar")
  }
}
