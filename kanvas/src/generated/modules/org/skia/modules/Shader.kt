package org.skia.modules

import kotlin.Int
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class Shader : public Node {
 * public:
 *     ~Shader() override;
 *
 *     const sk_sp<SkShader>& getShader() const {
 *         SkASSERT(!this->hasInval());
 *         return fShader;
 *     }
 *
 * protected:
 *     Shader();
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) final;
 *
 *     virtual sk_sp<SkShader> onRevalidateShader() = 0;
 *
 * private:
 *     sk_sp<SkShader> fShader;
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class Shader public constructor() : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: Int = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkShader>& getShader() const {
   *         SkASSERT(!this->hasInval());
   *         return fShader;
   *     }
   * ```
   */
  public fun getShader(): Int {
    TODO("Implement getShader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Shader::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     fShader = this->onRevalidateShader();
   *     return SkRect::MakeEmpty();
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkShader> onRevalidateShader() = 0
   * ```
   */
  protected abstract fun onRevalidateShader(): Int
}
