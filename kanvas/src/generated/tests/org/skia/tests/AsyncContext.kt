package org.skia.tests

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AsyncContext {
 *     bool fCalled = false;
 *     std::unique_ptr<const SkImage::AsyncReadResult> fResult;
 * }
 * ```
 */
public data class AsyncContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fCalled = false
   * ```
   */
  public var fCalled: Boolean,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<const SkImage::AsyncReadResult> fResult
   * ```
   */
  public var fResult: Int,
)
