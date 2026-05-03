package org.skia.tests

import kotlin.Boolean
import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class VeryLargeBitmapGM : public skiagm::GM {
 *     ImageMakerProc  fProc;
 *     const char*     fBaseName;
 *     bool            fManuallyTile;
 *
 * public:
 *     VeryLargeBitmapGM(ImageMakerProc proc, const char baseName[], bool manuallyTile)
 *             : fProc(proc)
 *             , fBaseName(baseName)
 *             , fManuallyTile(manuallyTile) {}
 *
 * private:
 *     SkString getName() const override {
 *         SkString name(fBaseName);
 *
 *         if (fManuallyTile) {
 *             name.append("_manual");
 *         }
 *
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return {500, 600}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         int veryBig = 65*1024; // 64K < size
 *         int big = 33*1024;     // 32K < size < 64K
 *         // smaller than many max texture sizes, but large enough to gpu-tile for memory reasons.
 *         int medium = 5*1024;
 *         int small = 150;
 *
 *         SkColor colors[2];
 *
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(10));
 *         colors[0] = SK_ColorRED;
 *         colors[1] = SK_ColorGREEN;
 *         show_image(canvas, small, small, colors, fProc, fManuallyTile);
 *         canvas->translate(0, SkIntToScalar(150));
 *
 *         colors[0] = SK_ColorBLUE;
 *         colors[1] = SK_ColorMAGENTA;
 *         show_image(canvas, big, small, colors, fProc, fManuallyTile);
 *         canvas->translate(0, SkIntToScalar(150));
 *
 *         colors[0] = SK_ColorMAGENTA;
 *         colors[1] = SK_ColorYELLOW;
 *         show_image(canvas, medium, medium, colors, fProc, fManuallyTile);
 *         canvas->translate(0, SkIntToScalar(150));
 *
 *         colors[0] = SK_ColorGREEN;
 *         colors[1] = SK_ColorYELLOW;
 *         // This used to be big enough that we didn't draw on CPU, but now we do.
 *         show_image(canvas, veryBig, small, colors, fProc, fManuallyTile);
 *     }
 * }
 * ```
 */
public open class VeryLargeBitmapGM public constructor(
  proc: ImageMakerProc,
  baseName: CharArray,
  manuallyTile: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * ImageMakerProc  fProc
   * ```
   */
  private var fProc: ImageMakerProc = TODO("Initialize fProc")

  /**
   * C++ original:
   * ```cpp
   * const char*     fBaseName
   * ```
   */
  private val fBaseName: String? = TODO("Initialize fBaseName")

  /**
   * C++ original:
   * ```cpp
   * bool            fManuallyTile
   * ```
   */
  private var fManuallyTile: Boolean = TODO("Initialize fManuallyTile")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name(fBaseName);
   *
   *         if (fManuallyTile) {
   *             name.append("_manual");
   *         }
   *
   *         return name;
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {500, 600}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         int veryBig = 65*1024; // 64K < size
   *         int big = 33*1024;     // 32K < size < 64K
   *         // smaller than many max texture sizes, but large enough to gpu-tile for memory reasons.
   *         int medium = 5*1024;
   *         int small = 150;
   *
   *         SkColor colors[2];
   *
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(10));
   *         colors[0] = SK_ColorRED;
   *         colors[1] = SK_ColorGREEN;
   *         show_image(canvas, small, small, colors, fProc, fManuallyTile);
   *         canvas->translate(0, SkIntToScalar(150));
   *
   *         colors[0] = SK_ColorBLUE;
   *         colors[1] = SK_ColorMAGENTA;
   *         show_image(canvas, big, small, colors, fProc, fManuallyTile);
   *         canvas->translate(0, SkIntToScalar(150));
   *
   *         colors[0] = SK_ColorMAGENTA;
   *         colors[1] = SK_ColorYELLOW;
   *         show_image(canvas, medium, medium, colors, fProc, fManuallyTile);
   *         canvas->translate(0, SkIntToScalar(150));
   *
   *         colors[0] = SK_ColorGREEN;
   *         colors[1] = SK_ColorYELLOW;
   *         // This used to be big enough that we didn't draw on CPU, but now we do.
   *         show_image(canvas, veryBig, small, colors, fProc, fManuallyTile);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
