package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API MtlBackendContext {
 *     sk_cfp<CFTypeRef> fDevice;
 *     sk_cfp<CFTypeRef> fQueue;
 * }
 * ```
 */
public data class MtlBackendContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<CFTypeRef> fDevice
   * ```
   */
  public var fDevice: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<CFTypeRef> fQueue
   * ```
   */
  public var fQueue: Int,
)
