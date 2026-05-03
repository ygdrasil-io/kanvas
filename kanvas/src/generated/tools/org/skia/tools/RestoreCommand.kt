package org.skia.tools

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class RestoreCommand : public DrawCommand {
 * public:
 *     RestoreCommand();
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class RestoreCommand public constructor() : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void RestoreCommand::execute(SkCanvas* canvas) const { canvas->restore(); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
