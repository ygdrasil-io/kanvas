package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class EndDrawPictureCommand : public DrawCommand {
 * public:
 *     EndDrawPictureCommand(bool restore);
 *
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     bool fRestore;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class EndDrawPictureCommand public constructor(
  restore: Boolean,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool fRestore
   * ```
   */
  private var fRestore: Boolean = TODO("Initialize fRestore")

  /**
   * C++ original:
   * ```cpp
   * void EndDrawPictureCommand::execute(SkCanvas* canvas) const {
   *     if (fRestore) {
   *         canvas->restore();
   *     }
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
