package org.skia.tools

import kotlin.Array
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class DrawAtlasCommand : public DrawCommand {
 * public:
 *     DrawAtlasCommand(const SkImage*,
 *                      const SkRSXform[],
 *                      const SkRect[],
 *                      const SkColor[],
 *                      int,
 *                      SkBlendMode,
 *                      const SkSamplingOptions&,
 *                      const SkRect*,
 *                      const SkPaint*);
 *
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     sk_sp<const SkImage> fImage;
 *     SkTDArray<SkRSXform> fXform;
 *     SkTDArray<SkRect>    fTex;
 *     SkTDArray<SkColor>   fColors;
 *     SkBlendMode          fBlendMode;
 *     SkSamplingOptions    fSampling;
 *     std::optional<SkRect>  fCull;
 *     std::optional<SkPaint> fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawAtlasCommand public constructor(
  image: SkImage?,
  xform: Array<SkRSXform>,
  tex: Array<SkRect>,
  colors: Array<SkColor>,
  count: Int,
  bmode: SkBlendMode,
  sampling: SkSamplingOptions,
  cull: SkRect?,
  paint: SkPaint?,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkImage> fImage
   * ```
   */
  private val fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkRSXform> fXform
   * ```
   */
  private var fXform: SkTDArray<SkRSXform> = TODO("Initialize fXform")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkRect>    fTex
   * ```
   */
  private var fTex: SkTDArray<SkRect> = TODO("Initialize fTex")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkColor>   fColors
   * ```
   */
  private var fColors: SkTDArray<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode          fBlendMode
   * ```
   */
  private var fBlendMode: SkBlendMode = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions    fSampling
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRect>  fCull
   * ```
   */
  private var fCull: Int = TODO("Initialize fCull")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint> fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawAtlasCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawAtlas(fImage.get(),
   *                       fXform, fTex, fColors,
   *                       fBlendMode,
   *                       fSampling,
   *                       SkOptAddressOrNull(fCull),
   *                       SkOptAddressOrNull(fPaint));
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
