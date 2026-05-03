package org.skia.core

/**
 * C++ original:
 * ```cpp
 * union SkSharedTTHeader {
 *     SkSFNTHeader    fSingle;
 *     SkTTCFHeader    fCollection;
 * }
 * ```
 */
public data class SkSharedTTHeader public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSFNTHeader    fSingle
   * ```
   */
  private var fSingle: SkSFNTHeader,
  /**
   * C++ original:
   * ```cpp
   * SkTTCFHeader    fCollection
   * ```
   */
  private var fCollection: SkTTCFHeader,
)
