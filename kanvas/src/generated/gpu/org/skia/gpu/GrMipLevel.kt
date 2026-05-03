package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct GrMipLevel {
 *     const void* fPixels = nullptr;
 *     size_t fRowBytes = 0;
 *     // This may be used to keep fPixels from being freed while a GrMipLevel exists.
 *     sk_sp<SkData> fOptionalStorage;
 *
 *     static_assert(::sk_is_trivially_relocatable<decltype(fPixels)>::value);
 *     static_assert(::sk_is_trivially_relocatable<decltype(fOptionalStorage)>::value);
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 * }
 * ```
 */
public data class GrMipLevel public constructor(
  /**
   * C++ original:
   * ```cpp
   * const void* fPixels = nullptr
   * ```
   */
  public val fPixels: Unit?,
  /**
   * C++ original:
   * ```cpp
   * size_t fRowBytes = 0
   * ```
   */
  public var fRowBytes: ULong,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fOptionalStorage
   * ```
   */
  public var fOptionalStorage: Int,
)
