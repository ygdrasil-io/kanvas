package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSp
import org.skia.gpu.TextureProxy

/**
 * C++ original:
 * ```cpp
 * struct ProxyCacheSetup {
 *     bool valid() const {
 *         return !fBitmap1.empty() && !fBitmap2.empty() && fProxy1 && fProxy2;
 *     }
 *
 *     SkBitmap fBitmap1;
 *     sk_sp<TextureProxy> fProxy1;
 *     SkBitmap fBitmap2;
 *     sk_sp<TextureProxy> fProxy2;
 *
 *     skgpu::StdSteadyClock::time_point fTimeBetweenProxyCreation;
 *     skgpu::StdSteadyClock::time_point fTimeAfterAllProxyCreation;
 * }
 * ```
 */
public data class ProxyCacheSetup public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap1
   * ```
   */
  public var fBitmap1: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fProxy1
   * ```
   */
  public var fProxy1: SkSp<TextureProxy>,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap2
   * ```
   */
  public var fBitmap2: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fProxy2
   * ```
   */
  public var fProxy2: SkSp<TextureProxy>,
  /**
   * C++ original:
   * ```cpp
   * skgpu::StdSteadyClock::time_point fTimeBetweenProxyCreation
   * ```
   */
  public var fTimeBetweenProxyCreation: Int,
  /**
   * C++ original:
   * ```cpp
   * skgpu::StdSteadyClock::time_point fTimeAfterAllProxyCreation
   * ```
   */
  public var fTimeAfterAllProxyCreation: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool valid() const {
   *         return !fBitmap1.empty() && !fBitmap2.empty() && fProxy1 && fProxy2;
   *     }
   * ```
   */
  public fun valid(): Boolean {
    TODO("Implement valid")
  }
}
