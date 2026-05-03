package org.skia.tools

import kotlin.Array
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.memory.AutoTArray

/**
 * C++ original:
 * ```cpp
 * class DrawEdgeAAImageSetCommand : public DrawCommand {
 * public:
 *     DrawEdgeAAImageSetCommand(const SkCanvas::ImageSetEntry[],
 *                               int count,
 *                               const SkPoint[],
 *                               const SkMatrix[],
 *                               const SkSamplingOptions&,
 *                               const SkPaint*,
 *                               SkCanvas::SrcRectConstraint);
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     skia_private::AutoTArray<SkCanvas::ImageSetEntry> fSet;
 *     int                                               fCount;
 *     skia_private::AutoTArray<SkPoint>                 fDstClips;
 *     skia_private::AutoTArray<SkMatrix>                fPreViewMatrices;
 *     SkSamplingOptions                                 fSampling;
 *     std::optional<SkPaint>                            fPaint;
 *     SkCanvas::SrcRectConstraint                       fConstraint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawEdgeAAImageSetCommand public constructor(
  `set`: Array<SkCanvas.ImageSetEntry>,
  count: Int,
  dstClips: Array<SkPoint>,
  preViewMatrices: Array<SkMatrix>,
  sampling: SkSamplingOptions,
  paint: SkPaint?,
  constraint: SkCanvas.SrcRectConstraint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTArray<SkCanvas::ImageSetEntry> fSet
   * ```
   */
  private var fSet: AutoTArray<SkCanvas.ImageSetEntry> = TODO("Initialize fSet")

  /**
   * C++ original:
   * ```cpp
   * int                                               fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTArray<SkPoint>                 fDstClips
   * ```
   */
  private var fDstClips: AutoTArray<SkPoint> = TODO("Initialize fDstClips")

  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTArray<SkMatrix>                fPreViewMatrices
   * ```
   */
  private var fPreViewMatrices: AutoTArray<SkMatrix> = TODO("Initialize fPreViewMatrices")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions                                 fSampling
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>                            fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SrcRectConstraint                       fConstraint
   * ```
   */
  private var fConstraint: SkCanvas.SrcRectConstraint = TODO("Initialize fConstraint")

  /**
   * C++ original:
   * ```cpp
   * void DrawEdgeAAImageSetCommand::execute(SkCanvas* canvas) const {
   *     canvas->experimental_DrawEdgeAAImageSet(fSet.get(),
   *                                             fCount,
   *                                             fDstClips.get(),
   *                                             fPreViewMatrices.get(),
   *                                             fSampling,
   *                                             SkOptAddressOrNull(fPaint),
   *                                             fConstraint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
