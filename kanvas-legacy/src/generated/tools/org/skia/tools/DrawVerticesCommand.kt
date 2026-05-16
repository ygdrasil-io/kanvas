package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.core.SkVertices
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DrawVerticesCommand : public DrawCommand {
 * public:
 *     DrawVerticesCommand(sk_sp<SkVertices>, SkBlendMode, const SkPaint&);
 *
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     sk_sp<SkVertices> fVertices;
 *     SkBlendMode       fBlendMode;
 *     SkPaint           fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawVerticesCommand public constructor(
  vertices: SkSp<SkVertices>,
  bmode: SkBlendMode,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> fVertices
   * ```
   */
  private var fVertices: SkSp<SkVertices> = TODO("Initialize fVertices")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode       fBlendMode
   * ```
   */
  private var fBlendMode: SkBlendMode = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * SkPaint           fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawVerticesCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawVertices(fVertices, fBlendMode, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
