package org.skia.tests

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkPixelGeometry
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SurfacePropsGM : public skiagm::GM {
 * public:
 *     SurfacePropsGM(uint32_t flags) : fFlags(flags) {
 *         recs = {
 *                 {kUnknown_SkPixelGeometry,
 *                  "Unknown geometry, default contrast/gamma",
 *                  SK_GAMMA_CONTRAST,
 *                  SK_GAMMA_EXPONENT},
 *                 {kRGB_H_SkPixelGeometry,
 *                  "RGB_H, default contrast/gamma",
 *                  SK_GAMMA_CONTRAST,
 *                  SK_GAMMA_EXPONENT},
 *                 {kBGR_H_SkPixelGeometry,
 *                  "BGR_H, default contrast/gamma",
 *                  SK_GAMMA_CONTRAST,
 *                  SK_GAMMA_EXPONENT},
 *                 {kRGB_V_SkPixelGeometry,
 *                  "RGB_V, default contrast/gamma",
 *                  SK_GAMMA_CONTRAST,
 *                  SK_GAMMA_EXPONENT},
 *                 {kBGR_V_SkPixelGeometry,
 *                  "BGR_V, default contrast/gamma",
 *                  SK_GAMMA_CONTRAST,
 *                  SK_GAMMA_EXPONENT},
 *                 {kRGB_H_SkPixelGeometry, "RGB_H contrast : 0 gamma: 0", 0, 0},
 *                 {kRGB_H_SkPixelGeometry, "RGB_H contrast : 1 gamma: 0", 1, 0},
 *                 {kRGB_H_SkPixelGeometry, "RGB_H contrast : 0 gamma: 3.9", 0, 3.9f},
 *                 {kRGB_H_SkPixelGeometry, "RGB_H contrast : 1 gamma: 3.9", 1, 3.9f},
 *         };
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         return SkStringPrintf("surfaceprops%s",
 *                               fFlags != 0 ? "_df" : "");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * recs.size()); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto ctx = canvas->recordingContext();
 *         auto recorder = canvas->recorder();
 *
 *         // must be opaque to have a hope of testing LCD text
 *         const SkImageInfo info = SkImageInfo::MakeN32(W, H, kOpaque_SkAlphaType);
 *
 *         SkScalar x = 0;
 *         SkScalar y = 0;
 *         for (const auto& rec : recs) {
 *             auto surface(make_surface(ctx, recorder, info, fFlags, rec.fGeo, rec.fContrast,
 *                                       rec.fGamma));
 *             if (!surface) {
 *                 SkDebugf("failed to create surface! label: %s", rec.fLabel);
 *                 continue;
 *             }
 *             test_draw(surface->getCanvas(), rec.fLabel);
 *             surface->draw(canvas, x, y);
 *             y += H;
 *         }
 *     }
 *
 * private:
 *     struct SurfacePropsInput {
 *         SkPixelGeometry fGeo;
 *         const char*     fLabel;
 *         SkScalar fContrast;
 *         SkScalar fGamma;
 *     };
 *     std::vector<SurfacePropsInput> recs;
 *
 *     uint32_t fFlags;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SurfacePropsGM public constructor(
  flags: UInt,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<SurfacePropsInput> recs
   * ```
   */
  private var recs: Int = TODO("Initialize recs")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fFlags
   * ```
   */
  private var fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("surfaceprops%s",
   *                               fFlags != 0 ? "_df" : "");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * recs.size()); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto ctx = canvas->recordingContext();
   *         auto recorder = canvas->recorder();
   *
   *         // must be opaque to have a hope of testing LCD text
   *         const SkImageInfo info = SkImageInfo::MakeN32(W, H, kOpaque_SkAlphaType);
   *
   *         SkScalar x = 0;
   *         SkScalar y = 0;
   *         for (const auto& rec : recs) {
   *             auto surface(make_surface(ctx, recorder, info, fFlags, rec.fGeo, rec.fContrast,
   *                                       rec.fGamma));
   *             if (!surface) {
   *                 SkDebugf("failed to create surface! label: %s", rec.fLabel);
   *                 continue;
   *             }
   *             test_draw(surface->getCanvas(), rec.fLabel);
   *             surface->draw(canvas, x, y);
   *             y += H;
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public data class SurfacePropsInput public constructor(
    public var fGeo: SkPixelGeometry,
    public val fLabel: String?,
    public var fContrast: SkScalar,
    public var fGamma: SkScalar,
  )
}
