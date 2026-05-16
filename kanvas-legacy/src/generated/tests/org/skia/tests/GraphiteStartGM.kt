package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GraphiteStartGM : public GM {
 * public:
 *     GraphiteStartGM() = default;
 *
 * protected:
 *     static constexpr int kTileWidth = 128;
 *     static constexpr int kTileHeight = 128;
 *     static constexpr int kWidth = 3 * kTileWidth;
 *     static constexpr int kHeight = 3 * kTileHeight;
 *     static constexpr int kClipInset = 4;
 *
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SK_ColorBLACK);
 *         ToolUtils::GetResourceAsBitmap("images/color_wheel.gif", &fBitmap);
 *     }
 *
 *     SkString getName() const override { return SkString("graphitestart"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         const SkRect clipRect = SkRect::MakeWH(kWidth, kHeight).makeInset(kClipInset, kClipInset);
 *
 *         canvas->save();
 *         canvas->clipRRect(SkRRect::MakeRectXY(clipRect, 32.f, 32.f), true);
 *
 *         // Upper-left corner
 *         draw_image_shader_tile(canvas, SkRect::MakeXYWH(0, 0, kTileWidth, kTileHeight));
 *
 *         // Upper-middle tile
 *         draw_gradient_tile(canvas, SkRect::MakeXYWH(kTileWidth, 0, kTileWidth, kTileHeight));
 *
 *         // Upper-right corner
 *         draw_colorfilter_swatches(canvas, SkRect::MakeXYWH(2*kTileWidth, 0,
 *                                                            kTileWidth, kTileWidth));
 *
 *         // Middle-left tile
 *         {
 *             SkPaint p;
 *             p.setColor(SK_ColorRED);
 *
 *             SkRect r = SkRect::MakeXYWH(0, kTileHeight, kTileWidth, kTileHeight);
 *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
 *         }
 *
 *         // Middle-middle tile
 *         {
 *             SkPaint p;
 *             p.setShader(create_blend_shader(canvas, SkBlendMode::kModulate));
 *
 *             SkRect r = SkRect::MakeXYWH(kTileWidth, kTileHeight, kTileWidth, kTileHeight);
 *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
 *         }
 *
 *         // Middle-right tile
 *         {
 *             sk_sp<SkImage> image(ToolUtils::GetResourceAsImage("images/mandrill_128.png"));
 *             sk_sp<SkShader> shader;
 *
 *             if (image) {
 *                 shader = image->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, {});
 *                 shader = shader->makeWithColorFilter(create_grayscale_colorfilter());
 *             }
 *
 *             SkPaint p;
 *             p.setShader(std::move(shader));
 *
 *             SkRect r = SkRect::MakeXYWH(2*kTileWidth, kTileHeight, kTileWidth, kTileHeight);
 *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
 *         }
 *
 *         canvas->restore();
 *
 *         // Bottom-left corner
 * #if defined(SK_GRAPHITE)
 *         // TODO: failing serialize test on Linux, not sure what's going on
 *         canvas->writePixels(fBitmap, 0, 2*kTileHeight);
 * #endif
 *
 *         // Bottom-middle tile
 *         draw_blend_mode_swatches(canvas, SkRect::MakeXYWH(kTileWidth, 2*kTileHeight,
 *                                                           kTileWidth, kTileHeight));
 *
 *         // Bottom-right corner
 *         {
 *             const SkRect kTile = SkRect::MakeXYWH(2*kTileWidth, 2*kTileHeight,
 *                                                   kTileWidth, kTileHeight);
 *
 *             SkPaint circlePaint;
 *             circlePaint.setColor(SK_ColorBLUE);
 *             circlePaint.setBlendMode(SkBlendMode::kSrc);
 *
 *             canvas->clipRect(kTile);
 *             canvas->drawRect(kTile.makeInset(10, 20), circlePaint);
 *
 *             SkPaint restorePaint;
 *             restorePaint.setBlendMode(SkBlendMode::kPlus);
 *
 *             canvas->saveLayer(nullptr, &restorePaint);
 *                 circlePaint.setColor(SK_ColorRED);
 *                 circlePaint.setBlendMode(SkBlendMode::kSrc);
 *
 *                 canvas->drawRect(kTile.makeInset(15, 25), circlePaint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     SkBitmap fBitmap;
 * }
 * ```
 */
public open class GraphiteStartGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kTileWidth = 128
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SK_ColorBLACK);
   *         ToolUtils::GetResourceAsBitmap("images/color_wheel.gif", &fBitmap);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("graphitestart"); }
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
   *
   *         const SkRect clipRect = SkRect::MakeWH(kWidth, kHeight).makeInset(kClipInset, kClipInset);
   *
   *         canvas->save();
   *         canvas->clipRRect(SkRRect::MakeRectXY(clipRect, 32.f, 32.f), true);
   *
   *         // Upper-left corner
   *         draw_image_shader_tile(canvas, SkRect::MakeXYWH(0, 0, kTileWidth, kTileHeight));
   *
   *         // Upper-middle tile
   *         draw_gradient_tile(canvas, SkRect::MakeXYWH(kTileWidth, 0, kTileWidth, kTileHeight));
   *
   *         // Upper-right corner
   *         draw_colorfilter_swatches(canvas, SkRect::MakeXYWH(2*kTileWidth, 0,
   *                                                            kTileWidth, kTileWidth));
   *
   *         // Middle-left tile
   *         {
   *             SkPaint p;
   *             p.setColor(SK_ColorRED);
   *
   *             SkRect r = SkRect::MakeXYWH(0, kTileHeight, kTileWidth, kTileHeight);
   *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
   *         }
   *
   *         // Middle-middle tile
   *         {
   *             SkPaint p;
   *             p.setShader(create_blend_shader(canvas, SkBlendMode::kModulate));
   *
   *             SkRect r = SkRect::MakeXYWH(kTileWidth, kTileHeight, kTileWidth, kTileHeight);
   *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
   *         }
   *
   *         // Middle-right tile
   *         {
   *             sk_sp<SkImage> image(ToolUtils::GetResourceAsImage("images/mandrill_128.png"));
   *             sk_sp<SkShader> shader;
   *
   *             if (image) {
   *                 shader = image->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, {});
   *                 shader = shader->makeWithColorFilter(create_grayscale_colorfilter());
   *             }
   *
   *             SkPaint p;
   *             p.setShader(std::move(shader));
   *
   *             SkRect r = SkRect::MakeXYWH(2*kTileWidth, kTileHeight, kTileWidth, kTileHeight);
   *             canvas->drawRect(r.makeInset(1.0f, 1.0f), p);
   *         }
   *
   *         canvas->restore();
   *
   *         // Bottom-left corner
   * #if defined(SK_GRAPHITE)
   *         // TODO: failing serialize test on Linux, not sure what's going on
   *         canvas->writePixels(fBitmap, 0, 2*kTileHeight);
   * #endif
   *
   *         // Bottom-middle tile
   *         draw_blend_mode_swatches(canvas, SkRect::MakeXYWH(kTileWidth, 2*kTileHeight,
   *                                                           kTileWidth, kTileHeight));
   *
   *         // Bottom-right corner
   *         {
   *             const SkRect kTile = SkRect::MakeXYWH(2*kTileWidth, 2*kTileHeight,
   *                                                   kTileWidth, kTileHeight);
   *
   *             SkPaint circlePaint;
   *             circlePaint.setColor(SK_ColorBLUE);
   *             circlePaint.setBlendMode(SkBlendMode::kSrc);
   *
   *             canvas->clipRect(kTile);
   *             canvas->drawRect(kTile.makeInset(10, 20), circlePaint);
   *
   *             SkPaint restorePaint;
   *             restorePaint.setBlendMode(SkBlendMode::kPlus);
   *
   *             canvas->saveLayer(nullptr, &restorePaint);
   *                 circlePaint.setColor(SK_ColorRED);
   *                 circlePaint.setBlendMode(SkBlendMode::kSrc);
   *
   *                 canvas->drawRect(kTile.makeInset(15, 25), circlePaint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    protected val kTileWidth: Int = TODO("Initialize kTileWidth")

    protected val kTileHeight: Int = TODO("Initialize kTileHeight")

    protected val kWidth: Int = TODO("Initialize kWidth")

    protected val kHeight: Int = TODO("Initialize kHeight")

    protected val kClipInset: Int = TODO("Initialize kClipInset")
  }
}
