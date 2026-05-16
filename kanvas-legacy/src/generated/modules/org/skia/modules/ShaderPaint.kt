package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class ShaderPaint final : public PaintNode {
 * public:
 *     ~ShaderPaint() override;
 *
 *     static sk_sp<ShaderPaint> Make(sk_sp<Shader>);
 *
 * protected:
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 *     void onApplyToPaint(SkPaint*) const override;
 *
 * private:
 *     explicit ShaderPaint(sk_sp<Shader>);
 *
 *     const sk_sp<Shader> fShader;
 *
 *     using INHERITED = PaintNode;
 * }
 * ```
 */
public class ShaderPaint public constructor(
  sh: SkSp<Shader>,
) : PaintNode() {
  /**
   * C++ original:
   * ```cpp
   * explicit ShaderPaint(sk_sp<Shader>)
   * ```
   */
  private var skSp: ShaderPaint = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Shader> fShader
   * ```
   */
  private val fShader: Int = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * SkRect ShaderPaint::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     return fShader->revalidate(ic, ctm);
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaderPaint::onApplyToPaint(SkPaint* paint) const {
   *     paint->setShader(fShader->getShader());
   * }
   * ```
   */
  protected override fun onApplyToPaint(paint: SkPaint?) {
    TODO("Implement onApplyToPaint")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ShaderPaint> ShaderPaint::Make(sk_sp<Shader> sh) {
     *     return sh ? sk_sp<ShaderPaint>(new ShaderPaint(std::move(sh)))
     *               : nullptr;
     * }
     * ```
     */
    public fun make(sh: SkSp<Shader>): Int {
      TODO("Implement make")
    }
  }
}
