package org.skia.tools

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class ResetClipCommand : public DrawCommand {
 * public:
 *     ResetClipCommand();
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ResetClipCommand public constructor() : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void ResetClipCommand::execute(SkCanvas* canvas) const { SkCanvasPriv::ResetClip(canvas); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
