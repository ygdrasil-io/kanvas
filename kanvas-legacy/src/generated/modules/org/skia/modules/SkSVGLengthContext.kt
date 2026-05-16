package org.skia.modules

import kotlin.Int
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGLengthContext {
 * public:
 *     SkSVGLengthContext(const SkSize& viewport, SkScalar dpi = 90)
 *         : fViewport(viewport), fDPI(dpi) {}
 *
 *     enum class LengthType {
 *         kHorizontal,
 *         kVertical,
 *         kOther,
 *     };
 *
 *     const SkSize& viewPort() const { return fViewport; }
 *     void setViewPort(const SkSize& viewport) { fViewport = viewport; }
 *
 *     SkScalar resolve(const SkSVGLength&, LengthType) const;
 *     SkRect   resolveRect(const SkSVGLength& x, const SkSVGLength& y,
 *                          const SkSVGLength& w, const SkSVGLength& h) const;
 *
 * private:
 *     SkSize   fViewport;
 *     SkScalar fDPI;
 * }
 * ```
 */
public data class SkSVGLengthContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSize   fViewport
   * ```
   */
  private var fViewport: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fDPI
   * ```
   */
  private var fDPI: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkSize& viewPort() const { return fViewport; }
   * ```
   */
  public fun viewPort(): Int {
    TODO("Implement viewPort")
  }

  /**
   * C++ original:
   * ```cpp
   * void setViewPort(const SkSize& viewport) { fViewport = viewport; }
   * ```
   */
  public fun setViewPort(viewport: SkSize) {
    TODO("Implement setViewPort")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkSVGLengthContext::resolve(const SkSVGLength& l, LengthType t) const {
   *     switch (l.unit()) {
   *     case SkSVGLength::Unit::kNumber:
   *         // Fall through.
   *     case SkSVGLength::Unit::kPX:
   *         return l.value();
   *     case SkSVGLength::Unit::kPercentage:
   *         return l.value() * length_size_for_type(fViewport, t) / 100;
   *     case SkSVGLength::Unit::kCM:
   *         return l.value() * fDPI * kCMMultiplier;
   *     case SkSVGLength::Unit::kMM:
   *         return l.value() * fDPI * kMMMultiplier;
   *     case SkSVGLength::Unit::kIN:
   *         return l.value() * fDPI * kINMultiplier;
   *     case SkSVGLength::Unit::kPT:
   *         return l.value() * fDPI * kPTMultiplier;
   *     case SkSVGLength::Unit::kPC:
   *         return l.value() * fDPI * kPCMultiplier;
   *     default:
   *         SkDEBUGF("unsupported unit type: <%d>\n", (int)l.unit());
   *         return 0;
   *     }
   * }
   * ```
   */
  public fun resolve(l: SkSVGLength, t: LengthType): Int {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGLengthContext::resolveRect(const SkSVGLength& x, const SkSVGLength& y,
   *                                        const SkSVGLength& w, const SkSVGLength& h) const {
   *     return SkRect::MakeXYWH(
   *         this->resolve(x, SkSVGLengthContext::LengthType::kHorizontal),
   *         this->resolve(y, SkSVGLengthContext::LengthType::kVertical),
   *         this->resolve(w, SkSVGLengthContext::LengthType::kHorizontal),
   *         this->resolve(h, SkSVGLengthContext::LengthType::kVertical));
   * }
   * ```
   */
  public fun resolveRect(
    x: SkSVGLength,
    y: SkSVGLength,
    w: SkSVGLength,
    h: SkSVGLength,
  ): Int {
    TODO("Implement resolveRect")
  }

  public enum class LengthType {
    kHorizontal,
    kVertical,
    kOther,
  }
}
