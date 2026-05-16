package org.skia.tools

import kotlin.Array
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.math.SkPoint
import org.skia.math.SkRect
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class DrawEdgeAAQuadCommand : public DrawCommand {
 * public:
 *     DrawEdgeAAQuadCommand(const SkRect&         rect,
 *                           const SkPoint         clip[4],
 *                           SkCanvas::QuadAAFlags aa,
 *                           const SkColor4f&      color,
 *                           SkBlendMode           mode);
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     SkRect                fRect;
 *     SkPoint               fClip[4];
 *     int                   fHasClip;
 *     SkCanvas::QuadAAFlags fAA;
 *     SkColor4f             fColor;
 *     SkBlendMode           fMode;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawEdgeAAQuadCommand public constructor(
  rect: SkRect,
  clip: Array<SkPoint>,
  aa: SkCanvas.QuadAAFlags,
  color: SkColor4f,
  mode: SkBlendMode,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect                fRect
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

  /**
   * C++ original:
   * ```cpp
   * SkPoint               fClip[4]
   * ```
   */
  private var fClip: Array<SkPoint> = TODO("Initialize fClip")

  /**
   * C++ original:
   * ```cpp
   * int                   fHasClip
   * ```
   */
  private var fHasClip: Int = TODO("Initialize fHasClip")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::QuadAAFlags fAA
   * ```
   */
  private var fAA: SkCanvas.QuadAAFlags = TODO("Initialize fAA")

  /**
   * C++ original:
   * ```cpp
   * SkColor4f             fColor
   * ```
   */
  private var fColor: SkColor4f = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode           fMode
   * ```
   */
  private var fMode: SkBlendMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * void DrawEdgeAAQuadCommand::execute(SkCanvas* canvas) const {
   *     canvas->experimental_DrawEdgeAAQuad(fRect, fHasClip ? fClip : nullptr, fAA, fColor, fMode);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
