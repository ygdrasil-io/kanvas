package org.skia.modules

import org.skia.sksl.Position
import undefined.Level

/**
 * C++ original:
 * ```cpp
 * class SKUNICODE_API SkBidiIterator {
 * public:
 *     typedef int32_t Position;
 *     typedef uint8_t Level;
 *     struct Region {
 *         Region(Position start, Position end, Level level)
 *             : start(start), end(end), level(level) { }
 *         Position start;
 *         Position end;
 *         Level level;
 *     };
 *     enum Direction {
 *         kLTR,
 *         kRTL,
 *     };
 *     SkBidiIterator() = default;
 *     SkBidiIterator(const SkBidiIterator&) = default;
 *     SkBidiIterator& operator=(const SkBidiIterator&) = default;
 *     virtual ~SkBidiIterator() = default;
 *     virtual Position getLength() = 0;
 *     virtual Level getLevelAt(Position) = 0;
 * }
 * ```
 */
public abstract class SkBidiIterator public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator() = default
   * ```
   */
  public constructor(param0: SkBidiIterator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator& operator=(const SkBidiIterator&) = default
   * ```
   */
  public fun assign(param0: SkBidiIterator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Position getLength() = 0
   * ```
   */
  public abstract fun getLength(): SkBidiIteratorPosition

  /**
   * C++ original:
   * ```cpp
   * virtual Level getLevelAt(Position) = 0
   * ```
   */
  public abstract fun getLevelAt(param0: SkBidiIteratorPosition): SkBidiIteratorLevel

  public data class Region public constructor(
    public var start: Position,
    public var end: Position,
    public var level: Level,
  )

  public enum class Direction {
    kLTR,
    kRTL,
  }
}
