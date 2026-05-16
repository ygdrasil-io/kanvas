package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.math.SkRect
import undefined.AbortCallback

/**
 * C++ original:
 * ```cpp
 * class SkEmptyPicture final : public SkPicture {
 * public:
 *     void playback(SkCanvas*, AbortCallback*) const override { }
 *
 *     size_t approximateBytesUsed() const override { return sizeof(*this); }
 *     int    approximateOpCount(bool nested)   const override { return 0; }
 *     SkRect cullRect()             const override { return SkRect::MakeEmpty(); }
 * }
 * ```
 */
public class SkEmptyPicture : SkPicture() {
  /**
   * C++ original:
   * ```cpp
   * void playback(SkCanvas*, AbortCallback*) const override { }
   * ```
   */
  public override fun playback(param0: SkCanvas?, param1: AbortCallback?) {
    TODO("Implement playback")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t approximateBytesUsed() const override { return sizeof(*this); }
   * ```
   */
  public override fun approximateBytesUsed(): ULong {
    TODO("Implement approximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * int    approximateOpCount(bool nested)   const override { return 0; }
   * ```
   */
  public override fun approximateOpCount(nested: Boolean): Int {
    TODO("Implement approximateOpCount")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect cullRect()             const override { return SkRect::MakeEmpty(); }
   * ```
   */
  public override fun cullRect(): SkRect {
    TODO("Implement cullRect")
  }
}
