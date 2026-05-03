package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class UserFontGM : public skiagm::GM {
 *     sk_sp<SkTypeface> fTF;
 *
 * public:
 *     UserFontGM() {}
 *
 *     void onOnceBeforeDraw() override {
 *         fTF = make_tf();
 *         // test serialization
 *         fTF = round_trip(fTF);
 *     }
 *
 *     static sk_sp<SkTextBlob> make_blob(sk_sp<SkTypeface> tf, float size, float* spacing) {
 *         SkFont font(tf);
 *         font.setSize(size);
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         *spacing = font.getMetrics(nullptr);
 *         return SkTextBlob::MakeFromString("Typeface", font);
 *     }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("user_typeface"); }
 *
 *     SkISize getISize() override { return {810, 452}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto waterfall = [&](sk_sp<SkTypeface> tf, bool defaultFace) {
 *             SkPaint paint;
 *             paint.setAntiAlias(true);
 *
 *             float spacing;
 *             float x = 20,
 *                   y = 16;
 *             for (float size = 9; size <= 100; size *= 1.25f) {
 *                 auto blob = make_blob(tf, size, &spacing);
 *
 *                 // shared baseline
 *                 if (defaultFace) {
 *                     paint.setColor(0xFFDDDDDD);
 *                     canvas->drawRect({0, y, 810, y+1}, paint);
 *                 }
 *
 *                 paint.setColor(0xFFCCCCCC);
 *                 paint.setStyle(SkPaint::kStroke_Style);
 *                 canvas->drawRect(blob->bounds().makeOffset(x, y), paint);
 *
 *                 paint.setStyle(SkPaint::kFill_Style);
 *                 paint.setColor(SK_ColorBLACK);
 *                 canvas->drawTextBlob(blob, x, y, paint);
 *
 *                 y += SkScalarRoundToInt(spacing * 1.25f + 2);
 *             }
 *         };
 *
 *         waterfall(ToolUtils::DefaultTypeface(), true);
 *         canvas->translate(400, 0);
 *         waterfall(fTF, false);
 *     }
 * }
 * ```
 */
public open class UserFontGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTF
   * ```
   */
  private var fTF: SkSp<SkTypeface> = TODO("Initialize fTF")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fTF = make_tf();
   *         // test serialization
   *         fTF = round_trip(fTF);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  public override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("user_typeface"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {810, 452}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto waterfall = [&](sk_sp<SkTypeface> tf, bool defaultFace) {
   *             SkPaint paint;
   *             paint.setAntiAlias(true);
   *
   *             float spacing;
   *             float x = 20,
   *                   y = 16;
   *             for (float size = 9; size <= 100; size *= 1.25f) {
   *                 auto blob = make_blob(tf, size, &spacing);
   *
   *                 // shared baseline
   *                 if (defaultFace) {
   *                     paint.setColor(0xFFDDDDDD);
   *                     canvas->drawRect({0, y, 810, y+1}, paint);
   *                 }
   *
   *                 paint.setColor(0xFFCCCCCC);
   *                 paint.setStyle(SkPaint::kStroke_Style);
   *                 canvas->drawRect(blob->bounds().makeOffset(x, y), paint);
   *
   *                 paint.setStyle(SkPaint::kFill_Style);
   *                 paint.setColor(SK_ColorBLACK);
   *                 canvas->drawTextBlob(blob, x, y, paint);
   *
   *                 y += SkScalarRoundToInt(spacing * 1.25f + 2);
   *             }
   *         };
   *
   *         waterfall(ToolUtils::DefaultTypeface(), true);
   *         canvas->translate(400, 0);
   *         waterfall(fTF, false);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTextBlob> make_blob(sk_sp<SkTypeface> tf, float size, float* spacing) {
     *         SkFont font(tf);
     *         font.setSize(size);
     *         font.setEdging(SkFont::Edging::kAntiAlias);
     *         *spacing = font.getMetrics(nullptr);
     *         return SkTextBlob::MakeFromString("Typeface", font);
     *     }
     * ```
     */
    public fun makeBlob(
      tf: SkSp<SkTypeface>,
      size: Float,
      spacing: Float?,
    ): SkSp<SkTextBlob> {
      TODO("Implement makeBlob")
    }
  }
}
