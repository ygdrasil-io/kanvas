package org.skia.modules

import kotlin.Float
import kotlin.Int
import org.skia.gpu.Type

/**
 * C++ original:
 * ```cpp
 * class NumberValue final : public Value {
 * public:
 *     inline static constexpr Type kType = Type::kNumber;
 *
 *     explicit NumberValue(int32_t);
 *     explicit NumberValue(float);
 *
 *     double operator*() const {
 *         SkASSERT(this->getTag() == Tag::kInt || this->getTag() == Tag::kFloat);
 *
 *         return this->getTag() == Tag::kInt ? static_cast<double>(*this->cast<int32_t>())
 *                                            : static_cast<double>(*this->cast<float>());
 *     }
 * }
 * ```
 */
public class NumberValue public constructor(
  i: Int,
) : Value() {
  /**
   * C++ original:
   * ```cpp
   * NumberValue::NumberValue(int32_t i) {
   *     this->init_tagged(Tag::kInt);
   *     *this->cast<int32_t>() = i;
   *     SkASSERT(this->getTag() == Tag::kInt);
   * }
   * ```
   */
  public constructor(f: Float) : this() {
    TODO("Implement constructor")
  }

  public companion object {
    public val kType: Type = TODO("Initialize kType")
  }
}
