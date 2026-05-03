package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ClippedBitmapShadersGM : public skiagm::GM {
 * public:
 *     ClippedBitmapShadersGM(SkTileMode mode, bool hq=false)
 *     : fMode(mode), fHQ(hq) {
 *     }
 *
 * protected:
 *     SkTileMode fMode;
 *     bool fHQ;
 *
 *     SkString getName() const override {
 *         SkString descriptor;
 *         switch (fMode) {
 *             case SkTileMode::kRepeat:
 *                 descriptor = "tile";
 *             break;
 *             case SkTileMode::kMirror:
 *                 descriptor = "mirror";
 *             break;
 *             case SkTileMode::kClamp:
 *                 descriptor = "clamp";
 *             break;
 *             case SkTileMode::kDecal:
 *                 descriptor = "decal";
 *                 break;
 *         }
 *         descriptor.prepend("clipped-bitmap-shaders-");
 *         if (fHQ) {
 *             descriptor.append("-hq");
 *         }
 *         return descriptor;
 *     }
 *
 *     SkISize getISize() override { return {300, 300}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBitmap bmp = create_bitmap();
 *         SkMatrix s;
 *         s.reset();
 *         s.setScale(8, 8);
 *         s.postTranslate(SLIDE_SIZE / 2, SLIDE_SIZE / 2);
 *         SkPaint paint;
 *         paint.setShader(bmp.makeShader(fMode, fMode,
 *                                        fHQ ? SkSamplingOptions(SkCubicResampler::Mitchell())
 *                                            : SkSamplingOptions(),
 *                                        s));
 *
 *         SkScalar margin = (SLIDE_SIZE / 3 - RECT_SIZE) / 2;
 *         for (int i = 0; i < 3; i++) {
 *             SkScalar yOrigin = SLIDE_SIZE / 3 * i + margin;
 *             for (int j = 0; j < 3; j++) {
 *                 SkScalar xOrigin = SLIDE_SIZE / 3 * j + margin;
 *                 if (i == 1 && j == 1) {
 *                     continue;   // skip center element
 *                 }
 *                 SkRect rect = SkRect::MakeXYWH(xOrigin, yOrigin,
 *                                                RECT_SIZE, RECT_SIZE);
 *                 canvas->save();
 *                 canvas->clipRect(rect);
 *                 canvas->drawRect(rect, paint);
 *                 canvas->restore();
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ClippedBitmapShadersGM public constructor(
  mode: SkTileMode,
  hq: Boolean = TODO(),
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkTileMode fMode
   * ```
   */
  protected var fMode: SkTileMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * bool fHQ
   * ```
   */
  protected var fHQ: Boolean = TODO("Initialize fHQ")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString descriptor;
   *         switch (fMode) {
   *             case SkTileMode::kRepeat:
   *                 descriptor = "tile";
   *             break;
   *             case SkTileMode::kMirror:
   *                 descriptor = "mirror";
   *             break;
   *             case SkTileMode::kClamp:
   *                 descriptor = "clamp";
   *             break;
   *             case SkTileMode::kDecal:
   *                 descriptor = "decal";
   *                 break;
   *         }
   *         descriptor.prepend("clipped-bitmap-shaders-");
   *         if (fHQ) {
   *             descriptor.append("-hq");
   *         }
   *         return descriptor;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {300, 300}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBitmap bmp = create_bitmap();
   *         SkMatrix s;
   *         s.reset();
   *         s.setScale(8, 8);
   *         s.postTranslate(SLIDE_SIZE / 2, SLIDE_SIZE / 2);
   *         SkPaint paint;
   *         paint.setShader(bmp.makeShader(fMode, fMode,
   *                                        fHQ ? SkSamplingOptions(SkCubicResampler::Mitchell())
   *                                            : SkSamplingOptions(),
   *                                        s));
   *
   *         SkScalar margin = (SLIDE_SIZE / 3 - RECT_SIZE) / 2;
   *         for (int i = 0; i < 3; i++) {
   *             SkScalar yOrigin = SLIDE_SIZE / 3 * i + margin;
   *             for (int j = 0; j < 3; j++) {
   *                 SkScalar xOrigin = SLIDE_SIZE / 3 * j + margin;
   *                 if (i == 1 && j == 1) {
   *                     continue;   // skip center element
   *                 }
   *                 SkRect rect = SkRect::MakeXYWH(xOrigin, yOrigin,
   *                                                RECT_SIZE, RECT_SIZE);
   *                 canvas->save();
   *                 canvas->clipRect(rect);
   *                 canvas->drawRect(rect, paint);
   *                 canvas->restore();
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
