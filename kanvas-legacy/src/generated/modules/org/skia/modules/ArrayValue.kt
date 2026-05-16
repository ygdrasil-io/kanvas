package org.skia.modules

import kotlin.ULong
import org.skia.gpu.Type
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class ArrayValue final : public VectorValue<Value, Value::Type::kArray> {
 * public:
 *     ArrayValue(const Value* src, size_t size, SkArenaAlloc& alloc);
 * }
 * ```
 */
public class ArrayValue public constructor(
  src: Value?,
  size: ULong,
  alloc: SkArenaAlloc,
) : VectorValue(),
    Value,
    Type.KArray
