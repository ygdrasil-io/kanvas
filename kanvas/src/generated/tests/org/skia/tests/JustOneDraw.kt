package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPicture

/**
 * C++ original:
 * ```cpp
 * class JustOneDraw : public SkPicture::AbortCallback {
 * public:
 *     JustOneDraw() : fCalls(0) {}
 *
 *     bool abort() override { return fCalls++ > 0; }
 * private:
 *     int fCalls;
 * }
 * ```
 */
public open class JustOneDraw public constructor() : SkPicture.AbortCallback() {
  /**
   * C++ original:
   * ```cpp
   * int fCalls
   * ```
   */
  private var fCalls: Int = TODO("Initialize fCalls")

  /**
   * C++ original:
   * ```cpp
   * bool abort() override { return fCalls++ > 0; }
   * ```
   */
  public override fun abort(): Boolean {
    TODO("Implement abort")
  }
}
