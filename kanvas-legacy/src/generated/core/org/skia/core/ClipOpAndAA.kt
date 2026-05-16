package org.skia.core

import kotlin.Boolean
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct ClipOpAndAA {
 *     ClipOpAndAA() {}
 *     ClipOpAndAA(SkClipOp op, bool aa) : fOp(static_cast<unsigned>(op)), fAA(aa) {}
 *
 *     SkClipOp op() const { return static_cast<SkClipOp>(fOp); }
 *     bool aa() const { return fAA != 0; }
 *
 * private:
 *     unsigned fOp : 31;  // This really only needs to be 3, but there's no win today to do so.
 *     unsigned fAA :  1;  // MSVC won't pack an enum with an bool, so we call this an unsigned.
 * }
 * ```
 */
public data class ClipOpAndAA public constructor(
  /**
   * C++ original:
   * ```cpp
   * unsigned fOp : 31
   * ```
   */
  private var fOp: UInt,
  /**
   * C++ original:
   * ```cpp
   * unsigned fAA :  1
   * ```
   */
  private var fAA: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * SkClipOp op() const { return static_cast<SkClipOp>(fOp); }
   * ```
   */
  public fun op(): SkClipOp {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool aa() const { return fAA != 0; }
   * ```
   */
  public fun aa(): Boolean {
    TODO("Implement aa")
  }
}
