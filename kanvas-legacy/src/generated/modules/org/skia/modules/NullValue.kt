package org.skia.modules

import org.skia.gpu.Type

/**
 * C++ original:
 * ```cpp
 * class NullValue final : public Value {
 * public:
 *     inline static constexpr Type kType = Type::kNull;
 *
 *     NullValue();
 * }
 * ```
 */
public class NullValue public constructor() : Value() {
  public companion object {
    public val kType: Type = TODO("Initialize kType")
  }
}
