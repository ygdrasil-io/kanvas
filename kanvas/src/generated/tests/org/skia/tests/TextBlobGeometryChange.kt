package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TextBlobGeometryChange : public GM {
 * public:
 *     TextBlobGeometryChange() { }
 *
 * protected:
 *     SkString getName() const override { return SkString("textblobgeometrychange"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const char text[] = "Hamburgefons";
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         SkTextBlobBuilder builder;
 *
 *         ToolUtils::add_to_text_blob(&builder, text, font, 10, 10);
 *
 *         sk_sp<SkTextBlob> blob(builder.make());
 *
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(200, 200);
 *         SkSurfaceProps props(0, kUnknown_SkPixelGeometry);
 *         auto           surface = ToolUtils::makeSurface(canvas, info, &props);
 *         SkCanvas* c = surface->getCanvas();
 *
 *         // LCD text on white background
 *         SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
 *         SkPaint rectPaint;
 *         rectPaint.setColor(0xffffffff);
 *         canvas->drawRect(rect, rectPaint);
 *         canvas->drawTextBlob(blob, 10, 50, SkPaint());
 *
 *         // This should not look garbled since we should disable LCD text in this case
 *         // (i.e., unknown pixel geometry)
 *         c->clear(0x00ffffff);
 *         c->drawTextBlob(blob, 10, 150, SkPaint());
 *         surface->draw(canvas, 0, 0);
 *     }
 *
 * private:
 *     inline static constexpr int kWidth = 200;
 *     inline static constexpr int kHeight = 200;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TextBlobGeometryChange public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("textblobgeometrychange"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const char text[] = "Hamburgefons";
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *         SkTextBlobBuilder builder;
   *
   *         ToolUtils::add_to_text_blob(&builder, text, font, 10, 10);
   *
   *         sk_sp<SkTextBlob> blob(builder.make());
   *
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(200, 200);
   *         SkSurfaceProps props(0, kUnknown_SkPixelGeometry);
   *         auto           surface = ToolUtils::makeSurface(canvas, info, &props);
   *         SkCanvas* c = surface->getCanvas();
   *
   *         // LCD text on white background
   *         SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
   *         SkPaint rectPaint;
   *         rectPaint.setColor(0xffffffff);
   *         canvas->drawRect(rect, rectPaint);
   *         canvas->drawTextBlob(blob, 10, 50, SkPaint());
   *
   *         // This should not look garbled since we should disable LCD text in this case
   *         // (i.e., unknown pixel geometry)
   *         c->clear(0x00ffffff);
   *         c->drawTextBlob(blob, 10, 150, SkPaint());
   *         surface->draw(canvas, 0, 0);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")
  }
}
