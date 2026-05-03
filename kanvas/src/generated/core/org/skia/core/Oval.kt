package org.skia.core

import kotlin.Array
import kotlin.UInt
import org.skia.math.SkPathDirection
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct Oval : public SkPathRaw {
 *     SkPoint fStorage[9];   // move + 4 conics (+ close)
 *
 *     explicit Oval(const SkRect&, SkPathDirection = SkPathDirection::kCW, unsigned index = 1);
 * }
 * ```
 */
public open class Oval public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fStorage[9]
   * ```
   */
  public var fStorage: Array<SkPoint>,
) : SkPathRaw(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPathRawShapes::Oval::Oval(const SkRect& r, SkPathDirection dir, unsigned index) {
   *     set_as_oval(this, fStorage, r, dir, index);
   * }
   * ```
   */
  public constructor(
    r: SkRect,
    dir: SkPathDirection = TODO(),
    index: UInt = 1u,
  ) : this() {
    TODO("Implement constructor")
  }
}
