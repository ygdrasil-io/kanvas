package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComposeColorFilterGM : public skiagm::GM {
 *     enum {
 *         COLOR_COUNT = 3,
 *         MODE_COUNT = 4,
 *     };
 *     const SkColor*      fColors;
 *     const SkBlendMode*  fModes;
 *     const char*         fName;
 *
 * public:
 *     ComposeColorFilterGM(const SkColor colors[], const SkBlendMode modes[], const char* name)
 *         : fColors(colors), fModes(modes), fName(name) {}
 *
 * private:
 *     SkString getName() const override { return SkString(fName); }
 *
 *     SkISize getISize() override { return {790, 790}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBitmap bm;
 *         make_bm1(&bm);
 *
 *         canvas->drawColor(0xFFDDDDDD);
 *
 *         const int MODES = MODE_COUNT * COLOR_COUNT;
 *         sk_sp<SkColorFilter> filters[MODES];
 *         int index = 0;
 *         for (int i = 0; i < MODE_COUNT; ++i) {
 *             for (int j = 0; j < COLOR_COUNT; ++j) {
 *                 filters[index++] = SkColorFilters::Blend(fColors[j], fModes[i]);
 *             }
 *         }
 *
 *         SkPaint paint;
 *         paint.setShader(make_shader1(50, 50));
 *         SkRect r = SkRect::MakeWH(50, 50);
 *         const SkScalar spacer = 10;
 *
 *         canvas->translate(spacer, spacer);
 *
 *         canvas->drawRect(r, paint); // orig
 *
 *         for (int i = 0; i < MODES; ++i) {
 *             paint.setColorFilter(filters[i]);
 *
 *             canvas->save();
 *             canvas->translate((i + 1) * (r.width() + spacer), 0);
 *             canvas->drawRect(r, paint);
 *             canvas->restore();
 *
 *             canvas->save();
 *             canvas->translate(0, (i + 1) * (r.width() + spacer));
 *             canvas->drawRect(r, paint);
 *             canvas->restore();
 *         }
 *
 *         canvas->translate(r.width() + spacer, r.width() + spacer);
 *
 *         for (int y = 0; y < MODES; ++y) {
 *             canvas->save();
 *             for (int x = 0; x < MODES; ++x) {
 *                 paint.setColorFilter(filters[y]->makeComposed(filters[x]));
 *                 canvas->drawRect(r, paint);
 *                 canvas->translate(r.width() + spacer, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height() + spacer);
 *         }
 *     }
 * }
 * ```
 */
public open class ComposeColorFilterGM public constructor(
  colors: Array<SkColor>,
  modes: Array<SkBlendMode>,
  name: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkColor*      fColors
   * ```
   */
  private val fColors: SkColor? = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * const SkBlendMode*  fModes
   * ```
   */
  private val fModes: SkBlendMode? = TODO("Initialize fModes")

  /**
   * C++ original:
   * ```cpp
   * const char*         fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString(fName); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {790, 790}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBitmap bm;
   *         make_bm1(&bm);
   *
   *         canvas->drawColor(0xFFDDDDDD);
   *
   *         const int MODES = MODE_COUNT * COLOR_COUNT;
   *         sk_sp<SkColorFilter> filters[MODES];
   *         int index = 0;
   *         for (int i = 0; i < MODE_COUNT; ++i) {
   *             for (int j = 0; j < COLOR_COUNT; ++j) {
   *                 filters[index++] = SkColorFilters::Blend(fColors[j], fModes[i]);
   *             }
   *         }
   *
   *         SkPaint paint;
   *         paint.setShader(make_shader1(50, 50));
   *         SkRect r = SkRect::MakeWH(50, 50);
   *         const SkScalar spacer = 10;
   *
   *         canvas->translate(spacer, spacer);
   *
   *         canvas->drawRect(r, paint); // orig
   *
   *         for (int i = 0; i < MODES; ++i) {
   *             paint.setColorFilter(filters[i]);
   *
   *             canvas->save();
   *             canvas->translate((i + 1) * (r.width() + spacer), 0);
   *             canvas->drawRect(r, paint);
   *             canvas->restore();
   *
   *             canvas->save();
   *             canvas->translate(0, (i + 1) * (r.width() + spacer));
   *             canvas->drawRect(r, paint);
   *             canvas->restore();
   *         }
   *
   *         canvas->translate(r.width() + spacer, r.width() + spacer);
   *
   *         for (int y = 0; y < MODES; ++y) {
   *             canvas->save();
   *             for (int x = 0; x < MODES; ++x) {
   *                 paint.setColorFilter(filters[y]->makeComposed(filters[x]));
   *                 canvas->drawRect(r, paint);
   *                 canvas->translate(r.width() + spacer, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height() + spacer);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    public val colorCOUNT: Int = TODO("Initialize colorCOUNT")

    public val modeCOUNT: Int = TODO("Initialize modeCOUNT")
  }
}
