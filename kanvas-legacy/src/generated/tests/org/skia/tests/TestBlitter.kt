package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.core.SkBlitter
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class TestBlitter : public SkBlitter {
 * public:
 *     TestBlitter(SkIRect bounds, skiatest::Reporter* reporter)
 *         : fBounds(bounds)
 *         , fReporter(reporter) { }
 *
 *     void blitH(int x, int y, int width) override {
 *
 *         REPORTER_ASSERT(fReporter, x >= fBounds.fLeft && x < fBounds.fRight);
 *         REPORTER_ASSERT(fReporter, y >= fBounds.fTop && y < fBounds.fBottom);
 *         int right = x + width;
 *         REPORTER_ASSERT(fReporter, right > fBounds.fLeft && right <= fBounds.fRight);
 *     }
 *
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
 *         SkDEBUGFAIL("blitAntiH not implemented");
 *     }
 *
 * private:
 *     SkIRect fBounds;
 *     skiatest::Reporter* fReporter;
 * }
 * ```
 */
public open class TestBlitter public constructor(
  bounds: SkIRect,
  reporter: Reporter?,
) : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkIRect fBounds
   * ```
   */
  private var fBounds: SkIRect = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  private var fReporter: Reporter? = TODO("Initialize fReporter")

  /**
   * C++ original:
   * ```cpp
   * void blitH(int x, int y, int width) override {
   *
   *         REPORTER_ASSERT(fReporter, x >= fBounds.fLeft && x < fBounds.fRight);
   *         REPORTER_ASSERT(fReporter, y >= fBounds.fTop && y < fBounds.fBottom);
   *         int right = x + width;
   *         REPORTER_ASSERT(fReporter, right > fBounds.fLeft && right <= fBounds.fRight);
   *     }
   * ```
   */
  public override fun blitH(
    x: Int,
    y: Int,
    width: Int,
  ) {
    TODO("Implement blitH")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
   *         SkDEBUGFAIL("blitAntiH not implemented");
   *     }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }
}
