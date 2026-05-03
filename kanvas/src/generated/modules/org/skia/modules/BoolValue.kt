package org.skia.modules

import kotlin.Boolean
import org.skia.gpu.Type

/**
 * C++ original:
 * ```cpp
 * class BoolValue final : public Value {
 * public:
 *     inline static constexpr Type kType = Type::kBool;
 *
 *     explicit BoolValue(bool);
 *
 *     bool operator*() const {
 *         SkASSERT(this->getTag() == Tag::kBool);
 *         return *this->cast<bool>();
 *     }
 * }
 * ```
 */
public class BoolValue public constructor(
  b: Boolean,
) : Value() {
  public companion object {
    public val kType: Type = TODO("Initialize kType")
  }
}
