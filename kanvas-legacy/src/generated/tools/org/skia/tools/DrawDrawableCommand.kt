package org.skia.tools

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class DrawDrawableCommand : public DrawCommand {
 * public:
 *     DrawDrawableCommand(SkDrawable*, const SkMatrix*);
 *     void execute(SkCanvas* canvas) const override;
 *
 * private:
 *     sk_sp<SkDrawable>       fDrawable;
 *     std::optional<SkMatrix> fMatrix;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawDrawableCommand public constructor(
  drawable: SkDrawable?,
  matrix: SkMatrix?,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable>       fDrawable
   * ```
   */
  private var fDrawable: SkSp<SkDrawable> = TODO("Initialize fDrawable")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMatrix> fMatrix
   * ```
   */
  private var fMatrix: Int = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * void DrawDrawableCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawDrawable(fDrawable.get(), SkOptAddressOrNull(fMatrix));
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }
}
