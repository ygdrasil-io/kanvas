package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SimpleGM : public GM {
 *     public:
 *         using DrawProc = DrawResult(*)(SkCanvas*, SkString*);
 *
 *         SimpleGM(SkColor bgColor, const SkString& name, const SkISize& size, DrawProc drawProc)
 *                 : GM(bgColor), fName(name), fSize(size), fDrawProc(drawProc) {}
 *
 *         SkString getName() const override;
 *         SkISize getISize() override;
 *
 *     private:
 *         DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override;
 *
 *         const SkString fName;
 *         const SkISize fSize;
 *         const DrawProc fDrawProc;
 *     }
 * ```
 */
public open class SimpleGM public constructor(
  bgColor: SkColor,
  name: String,
  size: SkISize,
  drawProc: SimpleGMDrawProc,
) : GM(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkString fName
   * ```
   */
  private val fName: Int = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * const SkISize fSize
   * ```
   */
  private val fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * const DrawProc fDrawProc
   * ```
   */
  private val fDrawProc: SimpleGMDrawProc = TODO("Initialize fDrawProc")

  /**
   * C++ original:
   * ```cpp
   * SkString SimpleGM::getName() const { return fName; }
   * ```
   */
  public override fun getName(): Int {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize SimpleGM::getISize() { return fSize; }
   * ```
   */
  public override fun getISize(): Int {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult SimpleGM::onDraw(SkCanvas* canvas, SkString* errorMsg) {
   *     return fDrawProc(canvas, errorMsg);
   * }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
