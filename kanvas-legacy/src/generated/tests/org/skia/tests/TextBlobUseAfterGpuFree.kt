package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TextBlobUseAfterGpuFree : public GM {
 * public:
 *     TextBlobUseAfterGpuFree() { }
 *
 * protected:
 *     SkString getName() const override { return SkString("textblobuseaftergpufree"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const char text[] = "Hamburgefons";
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
 *         auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *
 *         // draw textblob
 *         SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
 *         SkPaint rectPaint;
 *         rectPaint.setColor(0xffffffff);
 *         canvas->drawRect(rect, rectPaint);
 *         canvas->drawTextBlob(blob, 20, 60, SkPaint());
 *
 * #if defined(SK_GANESH)
 *         // This text should look fine
 *         if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *             dContext->freeGpuResources();
 *         }
 * #endif
 *
 *         canvas->drawTextBlob(blob, 20, 160, SkPaint());
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
public open class TextBlobUseAfterGpuFree public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("textblobuseaftergpufree"); }
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
   *         auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
   *
   *         // draw textblob
   *         SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
   *         SkPaint rectPaint;
   *         rectPaint.setColor(0xffffffff);
   *         canvas->drawRect(rect, rectPaint);
   *         canvas->drawTextBlob(blob, 20, 60, SkPaint());
   *
   * #if defined(SK_GANESH)
   *         // This text should look fine
   *         if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
   *             dContext->freeGpuResources();
   *         }
   * #endif
   *
   *         canvas->drawTextBlob(blob, 20, 160, SkPaint());
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
