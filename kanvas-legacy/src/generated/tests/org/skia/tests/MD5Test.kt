package org.skia.tests

import kotlin.String
import org.skia.core.SkMD5

/**
 * C++ original:
 * ```cpp
 * struct MD5Test {
 *     const char* message;
 *     SkMD5::Digest digest;
 * }
 * ```
 */
public data class MD5Test public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* message
   * ```
   */
  public val message: String?,
  /**
   * C++ original:
   * ```cpp
   * SkMD5::Digest digest
   * ```
   */
  public var digest: SkMD5.Digest,
)
