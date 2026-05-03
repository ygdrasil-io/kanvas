package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class CrBug224618GM : public skiagm::GM {
 * public:
 *     CrBug224618GM() : fTime(0.f) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("crbug_224618"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kMaxVW, kMaxVW); }
 *
 *     // This animates the FOV in viewer, to ensure the panorama covering rects are stable across
 *     // a variety of perspective matrices
 *     bool onAnimate(double nanos) override {
 *         fTime = TimeUtils::Scaled(1e-9f * nanos, 0.5f);
 *         return true;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         SkColorConverter conv({SK_ColorTRANSPARENT, SkColorSetARGB(128, 255, 255, 255)});
 *         sk_sp<SkShader> gradient = SkShaders::RadialGradient(
 *                 {200.f, 200.f}, 25.f,
 *                 {{conv.colors4f(), {}, SkTileMode::kMirror},
 *                  {SkGradient::Interpolation::InPremul::kYes}});
 *
 *         sk_sp<SkSurface> surface = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(400, 400));
 *
 *         SkPaint bgPaint;
 *         bgPaint.setShader(gradient);
 *         surface->getCanvas()->drawPaint(bgPaint);
 *
 *         fCubeImage = surface->makeImageSnapshot();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar viewportWidth = SkScalarMod(fTime, 10.f) / 10.f * (kMaxVW - kMinVW) + kMinVW;
 *         SkScalar radius = viewportWidth / 2.f; // round?
 *         // See https://developer.mozilla.org/en-US/docs/Web/CSS/transform-function/perspective
 *         SkM44 proj{1.f, 0.f, 0.f, 0.f,
 *                    0.f, 1.f, 0.f, 0.f,
 *                    0.f, 0.f, 1.f, 0.f,
 *                    0.f, 0.f, -1.f / radius, 1.f};
 *         SkM44 zoom             = SkM44::Translate(0.f, 0.f, radius);
 *         SkM44 postZoom         = SkM44::Translate(0.f, 0.f, -radius - 1.f);
 *         SkM44 rotateHorizontal = SkM44::Rotate({0, 1, 0}, 2.356194490192345f);
 *
 *         // w in degrees will need to be converted to radians
 *         SkV4 axisAngles[6] = {
 *             {0.f, 1.f, 0.f, -90.f}, // rotateY(-90deg)
 *             {1.f, 0.f, 0.f, 0.f},   // <none>
 *             {0.f, 1.f, 0.f, 90.f},  // rotateY(90deg)
 *             {0.f, 1.f, 0.f, 180.f}, // rotateY(180deg)
 *             {1.f, 0.f, 0.f, -90.f}, // rotateX(-90deg)
 *             {1.f, 0.f, 0.f, 90.f},  // rotateX(90deg)
 *         };
 *         SkColor faceColors[6] = {
 *             SK_ColorRED,
 *             SK_ColorGREEN,
 *             SK_ColorBLUE,
 *             SK_ColorYELLOW,
 *             SkColorSetARGB(0xFF, 0xFF, 0xA5, 0x00), // orange css
 *             SkColorSetARGB(0xFF, 0x80, 0x00, 0x80)  // purple css
 *         };
 *
 *         for (int i = 0; i < 6; ++i) {
 *             SkM44 model = SkM44::Rotate({axisAngles[i].x, axisAngles[i].y, axisAngles[i].z},
 *                                             SkDegreesToRadians(axisAngles[i].w));
 *             model = SkM44::Translate(radius, radius) * proj *    // project and place content
 *                     zoom * rotateHorizontal * model * postZoom * // main model matrix
 *                     SkM44::Translate(-radius, -radius);          // center content
 *
 *             canvas->save();
 *             canvas->concat(model);
 *
 *             SkPaint fillPaint;
 *             fillPaint.setAntiAlias(true);
 *             fillPaint.setColor(faceColors[i]);
 *
 *             // Leverages FillRectOp on GPU backend
 *             canvas->drawRect(SkRect::MakeWH(viewportWidth, viewportWidth), fillPaint);
 *
 *             // Leverages TextureOp on GPU backend, to ensure sure both quad paths handle clipping
 *             canvas->drawImageRect(fCubeImage.get(),
 *                                   SkRect::MakeWH(fCubeImage->width(), fCubeImage->height()),
 *                                   SkRect::MakeWH(viewportWidth, viewportWidth),
 *                                   SkSamplingOptions(SkFilterMode::kLinear), &fillPaint,
 *                                   SkCanvas::kFast_SrcRectConstraint);
 *
 *             canvas->restore();
 *         }
 *     }
 * private:
 *     static const int kMaxVW = 800;
 *     static const int kMinVW = 300;
 *
 *     SkScalar fTime;
 *     sk_sp<SkImage> fCubeImage;
 * }
 * ```
 */
public open class CrBug224618GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * static const int kMaxVW = 800
   * ```
   */
  private var fTime: SkScalar = TODO("Initialize fTime")

  /**
   * C++ original:
   * ```cpp
   * static const int kMinVW = 300
   * ```
   */
  private var fCubeImage: SkSp<SkImage> = TODO("Initialize fCubeImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("crbug_224618"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kMaxVW, kMaxVW); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fTime = TimeUtils::Scaled(1e-9f * nanos, 0.5f);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkColorConverter conv({SK_ColorTRANSPARENT, SkColorSetARGB(128, 255, 255, 255)});
   *         sk_sp<SkShader> gradient = SkShaders::RadialGradient(
   *                 {200.f, 200.f}, 25.f,
   *                 {{conv.colors4f(), {}, SkTileMode::kMirror},
   *                  {SkGradient::Interpolation::InPremul::kYes}});
   *
   *         sk_sp<SkSurface> surface = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(400, 400));
   *
   *         SkPaint bgPaint;
   *         bgPaint.setShader(gradient);
   *         surface->getCanvas()->drawPaint(bgPaint);
   *
   *         fCubeImage = surface->makeImageSnapshot();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar viewportWidth = SkScalarMod(fTime, 10.f) / 10.f * (kMaxVW - kMinVW) + kMinVW;
   *         SkScalar radius = viewportWidth / 2.f; // round?
   *         // See https://developer.mozilla.org/en-US/docs/Web/CSS/transform-function/perspective
   *         SkM44 proj{1.f, 0.f, 0.f, 0.f,
   *                    0.f, 1.f, 0.f, 0.f,
   *                    0.f, 0.f, 1.f, 0.f,
   *                    0.f, 0.f, -1.f / radius, 1.f};
   *         SkM44 zoom             = SkM44::Translate(0.f, 0.f, radius);
   *         SkM44 postZoom         = SkM44::Translate(0.f, 0.f, -radius - 1.f);
   *         SkM44 rotateHorizontal = SkM44::Rotate({0, 1, 0}, 2.356194490192345f);
   *
   *         // w in degrees will need to be converted to radians
   *         SkV4 axisAngles[6] = {
   *             {0.f, 1.f, 0.f, -90.f}, // rotateY(-90deg)
   *             {1.f, 0.f, 0.f, 0.f},   // <none>
   *             {0.f, 1.f, 0.f, 90.f},  // rotateY(90deg)
   *             {0.f, 1.f, 0.f, 180.f}, // rotateY(180deg)
   *             {1.f, 0.f, 0.f, -90.f}, // rotateX(-90deg)
   *             {1.f, 0.f, 0.f, 90.f},  // rotateX(90deg)
   *         };
   *         SkColor faceColors[6] = {
   *             SK_ColorRED,
   *             SK_ColorGREEN,
   *             SK_ColorBLUE,
   *             SK_ColorYELLOW,
   *             SkColorSetARGB(0xFF, 0xFF, 0xA5, 0x00), // orange css
   *             SkColorSetARGB(0xFF, 0x80, 0x00, 0x80)  // purple css
   *         };
   *
   *         for (int i = 0; i < 6; ++i) {
   *             SkM44 model = SkM44::Rotate({axisAngles[i].x, axisAngles[i].y, axisAngles[i].z},
   *                                             SkDegreesToRadians(axisAngles[i].w));
   *             model = SkM44::Translate(radius, radius) * proj *    // project and place content
   *                     zoom * rotateHorizontal * model * postZoom * // main model matrix
   *                     SkM44::Translate(-radius, -radius);          // center content
   *
   *             canvas->save();
   *             canvas->concat(model);
   *
   *             SkPaint fillPaint;
   *             fillPaint.setAntiAlias(true);
   *             fillPaint.setColor(faceColors[i]);
   *
   *             // Leverages FillRectOp on GPU backend
   *             canvas->drawRect(SkRect::MakeWH(viewportWidth, viewportWidth), fillPaint);
   *
   *             // Leverages TextureOp on GPU backend, to ensure sure both quad paths handle clipping
   *             canvas->drawImageRect(fCubeImage.get(),
   *                                   SkRect::MakeWH(fCubeImage->width(), fCubeImage->height()),
   *                                   SkRect::MakeWH(viewportWidth, viewportWidth),
   *                                   SkSamplingOptions(SkFilterMode::kLinear), &fillPaint,
   *                                   SkCanvas::kFast_SrcRectConstraint);
   *
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kMaxVW: Int = TODO("Initialize kMaxVW")

    private val kMinVW: Int = TODO("Initialize kMinVW")
  }
}
