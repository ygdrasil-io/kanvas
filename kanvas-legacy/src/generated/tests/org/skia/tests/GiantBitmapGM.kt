package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GiantBitmapGM : public skiagm::GM {
 *     SkBitmap* fBM;
 *     SkTileMode fMode;
 *     bool fDoFilter;
 *     bool fDoRotate;
 *
 *     const SkBitmap& getBitmap() {
 *         if (nullptr == fBM) {
 *             fBM = new SkBitmap;
 *             fBM->allocN32Pixels(W, H);
 *             fBM->eraseColor(SK_ColorWHITE);
 *
 *             const SkColor colors[] = {
 *                 SK_ColorBLUE, SK_ColorRED, SK_ColorBLACK, SK_ColorGREEN
 *             };
 *
 *             SkCanvas canvas(*fBM);
 *             SkPaint paint;
 *             paint.setAntiAlias(true);
 *             paint.setStrokeWidth(SkIntToScalar(20));
 *
 * #if 0
 *             for (int y = -H*2; y < H; y += 50) {
 *                 SkScalar yy = SkIntToScalar(y);
 *                 paint.setColor(colors[y/50 & 0x3]);
 *                 canvas.drawLine(0, yy, SkIntToScalar(W), yy + SkIntToScalar(W),
 *                                 paint);
 *             }
 * #else
 *             for (int x = -W; x < W; x += 60) {
 *                 paint.setColor(colors[x/60 & 0x3]);
 *
 *                 SkScalar xx = SkIntToScalar(x);
 *                 canvas.drawLine(xx, 0, xx, SkIntToScalar(H),
 *                                 paint);
 *             }
 * #endif
 *         }
 *         return *fBM;
 *     }
 *
 * public:
 *     GiantBitmapGM(SkTileMode mode, bool doFilter, bool doRotate) : fBM(nullptr) {
 *         fMode = mode;
 *         fDoFilter = doFilter;
 *         fDoRotate = doRotate;
 *     }
 *
 *     ~GiantBitmapGM() override { delete fBM; }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str("giantbitmap_");
 *         switch (fMode) {
 *             case SkTileMode::kClamp:
 *                 str.append("clamp");
 *                 break;
 *             case SkTileMode::kRepeat:
 *                 str.append("repeat");
 *                 break;
 *             case SkTileMode::kMirror:
 *                 str.append("mirror");
 *                 break;
 *             case SkTileMode::kDecal:
 *                 str.append("decal");
 *                 break;
 *             default:
 *                 break;
 *         }
 *         str.append(fDoFilter ? "_bilerp" : "_point");
 *         str.append(fDoRotate ? "_rotate" : "_scale");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *
 *         SkMatrix m;
 *         if (fDoRotate) {
 *             m.setSkew(SK_Scalar1, 0, 0, 0);
 *         } else {
 *             SkScalar scale = 11*SK_Scalar1/12;
 *             m.setScale(scale, scale);
 *         }
 *         paint.setShader(getBitmap().makeShader(
 *                                            fMode, fMode,
 *                                            SkSamplingOptions(fDoFilter ? SkFilterMode::kLinear
 *                                                                        : SkFilterMode::kNearest),
 *                                            m));
 *
 *         canvas->translate(50, 50);
 *
 *         canvas->drawPaint(paint);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class GiantBitmapGM public constructor(
  mode: SkTileMode,
  doFilter: Boolean,
  doRotate: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap* fBM
   * ```
   */
  private var fBM: SkBitmap? = TODO("Initialize fBM")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fMode
   * ```
   */
  private var fMode: SkTileMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * bool fDoFilter
   * ```
   */
  private var fDoFilter: Boolean = TODO("Initialize fDoFilter")

  /**
   * C++ original:
   * ```cpp
   * bool fDoRotate
   * ```
   */
  private var fDoRotate: Boolean = TODO("Initialize fDoRotate")

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& getBitmap() {
   *         if (nullptr == fBM) {
   *             fBM = new SkBitmap;
   *             fBM->allocN32Pixels(W, H);
   *             fBM->eraseColor(SK_ColorWHITE);
   *
   *             const SkColor colors[] = {
   *                 SK_ColorBLUE, SK_ColorRED, SK_ColorBLACK, SK_ColorGREEN
   *             };
   *
   *             SkCanvas canvas(*fBM);
   *             SkPaint paint;
   *             paint.setAntiAlias(true);
   *             paint.setStrokeWidth(SkIntToScalar(20));
   *
   * #if 0
   *             for (int y = -H*2; y < H; y += 50) {
   *                 SkScalar yy = SkIntToScalar(y);
   *                 paint.setColor(colors[y/50 & 0x3]);
   *                 canvas.drawLine(0, yy, SkIntToScalar(W), yy + SkIntToScalar(W),
   *                                 paint);
   *             }
   * #else
   *             for (int x = -W; x < W; x += 60) {
   *                 paint.setColor(colors[x/60 & 0x3]);
   *
   *                 SkScalar xx = SkIntToScalar(x);
   *                 canvas.drawLine(xx, 0, xx, SkIntToScalar(H),
   *                                 paint);
   *             }
   * #endif
   *         }
   *         return *fBM;
   *     }
   * ```
   */
  private fun getBitmap(): SkBitmap {
    TODO("Implement getBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str("giantbitmap_");
   *         switch (fMode) {
   *             case SkTileMode::kClamp:
   *                 str.append("clamp");
   *                 break;
   *             case SkTileMode::kRepeat:
   *                 str.append("repeat");
   *                 break;
   *             case SkTileMode::kMirror:
   *                 str.append("mirror");
   *                 break;
   *             case SkTileMode::kDecal:
   *                 str.append("decal");
   *                 break;
   *             default:
   *                 break;
   *         }
   *         str.append(fDoFilter ? "_bilerp" : "_point");
   *         str.append(fDoRotate ? "_rotate" : "_scale");
   *         return str;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *
   *         SkMatrix m;
   *         if (fDoRotate) {
   *             m.setSkew(SK_Scalar1, 0, 0, 0);
   *         } else {
   *             SkScalar scale = 11*SK_Scalar1/12;
   *             m.setScale(scale, scale);
   *         }
   *         paint.setShader(getBitmap().makeShader(
   *                                            fMode, fMode,
   *                                            SkSamplingOptions(fDoFilter ? SkFilterMode::kLinear
   *                                                                        : SkFilterMode::kNearest),
   *                                            m));
   *
   *         canvas->translate(50, 50);
   *
   *         canvas->drawPaint(paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
