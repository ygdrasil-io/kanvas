package org.skia.modules

import kotlin.String
import kotlin.ULong
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkWStream
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class DOM final : public SkNoncopyable {
 * public:
 *     DOM(const char*, size_t);
 *
 *     const Value& root() const { return fRoot; }
 *
 *     void write(SkWStream*) const;
 *
 * private:
 *     SkArenaAlloc fAlloc;
 *     Value        fRoot;
 * }
 * ```
 */
public class DOM public constructor(
  `data`: String?,
  size: ULong,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc fAlloc
   * ```
   */
  private var fAlloc: SkArenaAlloc = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * Value        fRoot
   * ```
   */
  private var fRoot: Value = TODO("Initialize fRoot")

  /**
   * C++ original:
   * ```cpp
   * const Value& root() const { return fRoot; }
   * ```
   */
  public fun root(): Value {
    TODO("Implement root")
  }

  /**
   * C++ original:
   * ```cpp
   * void DOM::write(SkWStream* stream) const { Write(fRoot, stream); }
   * ```
   */
  public fun write(stream: SkWStream?) {
    TODO("Implement write")
  }
}
