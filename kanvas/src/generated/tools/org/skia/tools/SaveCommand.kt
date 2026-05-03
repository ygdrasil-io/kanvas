package org.skia.tools

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SaveCommand : public DrawCommand {
 * public:
 *     SaveCommand();
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class SaveCommand public constructor() : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SaveCommand::execute(SkCanvas* canvas) const { canvas->save(); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
