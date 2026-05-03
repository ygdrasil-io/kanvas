package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class TrimGM : public skiagm::GM {
 * public:
 *     TrimGM() {}
 *
 *     void onOnceBeforeDraw() override {
 *         std::optional<SkPath> path;
 *         SkAssertResult((path = SkParsePath::FromSVGString(
 *             "M   0,100 C  10, 50 190, 50 200,100"
 *             "M 200,100 C 210,150 390,150 400,100"
 *             "M 400,100 C 390, 50 210, 50 200,100"
 *             "M 200,100 C 190,150  10,150   0,100")));
 *         fPaths.push_back(*path);
 *
 *         SkAssertResult((path = SkParsePath::FromSVGString(
 *             "M   0, 75 L 200, 75"
 *             "M 200, 91 L 200, 91"
 *             "M 200,108 L 200,108"
 *             "M 200,125 L 400,125")));
 *         fPaths.push_back(*path);
 *
 *         SkAssertResult((path = SkParsePath::FromSVGString(
 *             "M   0,100 L  50, 50"
 *             "M  50, 50 L 150,150"
 *             "M 150,150 L 250, 50"
 *             "M 250, 50 L 350,150"
 *             "M 350,150 L 400,100")));
 *         fPaths.push_back(*path);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("trimpatheffect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1400, 1000); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static constexpr SkSize kCellSize = { 440, 150 };
 *         static constexpr SkScalar kOffsets[][2] = {
 *             { -0.33f, -0.66f },
 *             {  0    ,  1    },
 *             {  0    ,  0.25f},
 *             {  0.25f,  0.75f},
 *             {  0.75f,  1    },
 *             {  1    ,  0.75f},
 *         };
 *
 *         SkPaint hairlinePaint;
 *         hairlinePaint.setAntiAlias(true);
 *         hairlinePaint.setStroke(true);
 *         hairlinePaint.setStrokeCap(SkPaint::kRound_Cap);
 *         hairlinePaint.setStrokeWidth(2);
 *         SkPaint normalPaint = hairlinePaint;
 *         normalPaint.setStrokeWidth(10);
 *         normalPaint.setColor(0x8000ff00);
 *         SkPaint invertedPaint = normalPaint;
 *         invertedPaint.setColor(0x80ff0000);
 *
 *         for (const auto& offset : kOffsets) {
 *             auto start = offset[0] + fOffset,
 *                  stop  = offset[1] + fOffset;
 *
 *             auto normalMode   = SkTrimPathEffect::Mode::kNormal,
 *                  invertedMode = SkTrimPathEffect::Mode::kInverted;
 *             if (fOffset) {
 *                 start -= SkScalarFloorToScalar(start);
 *                 stop  -= SkScalarFloorToScalar(stop);
 *                 if (start > stop) {
 *                     using std::swap;
 *                     swap(start, stop);
 *                     swap(normalMode, invertedMode);
 *                 }
 *             }
 *
 *             normalPaint.setPathEffect(SkTrimPathEffect::Make(start, stop, normalMode));
 *             invertedPaint.setPathEffect(SkTrimPathEffect::Make(start, stop, invertedMode));
 *
 *             {
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 for (const auto& path : fPaths) {
 *                     canvas->drawPath(path, normalPaint);
 *                     canvas->drawPath(path, invertedPaint);
 *                     canvas->drawPath(path, hairlinePaint);
 *                     canvas->translate(kCellSize.width(), 0);
 *                 }
 *             }
 *
 *             canvas->translate(0, kCellSize.height());
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fOffset = TimeUtils::NanosToMSec(nanos) / 2000.0f;
 *         fOffset -= floorf(fOffset);
 *         return true;
 *     }
 *
 * private:
 *     TArray<SkPath> fPaths;
 *     SkScalar         fOffset = 0;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TrimGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<SkPath> fPaths
   * ```
   */
  private var fPaths: Int = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * SkScalar         fOffset = 0
   * ```
   */
  private var fOffset: SkScalar = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         std::optional<SkPath> path;
   *         SkAssertResult((path = SkParsePath::FromSVGString(
   *             "M   0,100 C  10, 50 190, 50 200,100"
   *             "M 200,100 C 210,150 390,150 400,100"
   *             "M 400,100 C 390, 50 210, 50 200,100"
   *             "M 200,100 C 190,150  10,150   0,100")));
   *         fPaths.push_back(*path);
   *
   *         SkAssertResult((path = SkParsePath::FromSVGString(
   *             "M   0, 75 L 200, 75"
   *             "M 200, 91 L 200, 91"
   *             "M 200,108 L 200,108"
   *             "M 200,125 L 400,125")));
   *         fPaths.push_back(*path);
   *
   *         SkAssertResult((path = SkParsePath::FromSVGString(
   *             "M   0,100 L  50, 50"
   *             "M  50, 50 L 150,150"
   *             "M 150,150 L 250, 50"
   *             "M 250, 50 L 350,150"
   *             "M 350,150 L 400,100")));
   *         fPaths.push_back(*path);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("trimpatheffect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1400, 1000); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static constexpr SkSize kCellSize = { 440, 150 };
   *         static constexpr SkScalar kOffsets[][2] = {
   *             { -0.33f, -0.66f },
   *             {  0    ,  1    },
   *             {  0    ,  0.25f},
   *             {  0.25f,  0.75f},
   *             {  0.75f,  1    },
   *             {  1    ,  0.75f},
   *         };
   *
   *         SkPaint hairlinePaint;
   *         hairlinePaint.setAntiAlias(true);
   *         hairlinePaint.setStroke(true);
   *         hairlinePaint.setStrokeCap(SkPaint::kRound_Cap);
   *         hairlinePaint.setStrokeWidth(2);
   *         SkPaint normalPaint = hairlinePaint;
   *         normalPaint.setStrokeWidth(10);
   *         normalPaint.setColor(0x8000ff00);
   *         SkPaint invertedPaint = normalPaint;
   *         invertedPaint.setColor(0x80ff0000);
   *
   *         for (const auto& offset : kOffsets) {
   *             auto start = offset[0] + fOffset,
   *                  stop  = offset[1] + fOffset;
   *
   *             auto normalMode   = SkTrimPathEffect::Mode::kNormal,
   *                  invertedMode = SkTrimPathEffect::Mode::kInverted;
   *             if (fOffset) {
   *                 start -= SkScalarFloorToScalar(start);
   *                 stop  -= SkScalarFloorToScalar(stop);
   *                 if (start > stop) {
   *                     using std::swap;
   *                     swap(start, stop);
   *                     swap(normalMode, invertedMode);
   *                 }
   *             }
   *
   *             normalPaint.setPathEffect(SkTrimPathEffect::Make(start, stop, normalMode));
   *             invertedPaint.setPathEffect(SkTrimPathEffect::Make(start, stop, invertedMode));
   *
   *             {
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 for (const auto& path : fPaths) {
   *                     canvas->drawPath(path, normalPaint);
   *                     canvas->drawPath(path, invertedPaint);
   *                     canvas->drawPath(path, hairlinePaint);
   *                     canvas->translate(kCellSize.width(), 0);
   *                 }
   *             }
   *
   *             canvas->translate(0, kCellSize.height());
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fOffset = TimeUtils::NanosToMSec(nanos) / 2000.0f;
   *         fOffset -= floorf(fOffset);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
