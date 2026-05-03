package org.skia.tests

import kotlin.String
import kotlin.unique_ptr

/**
 * C++ original:
 * ```cpp
 * struct TaggedSrc : public std::unique_ptr<Src> {
 *     SkString tag;
 *     SkString options;
 * }
 * ```
 */
public open class TaggedSrc public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString tag
   * ```
   */
  public var tag: String,
  /**
   * C++ original:
   * ```cpp
   * SkString options
   * ```
   */
  public var options: String,
) : unique_ptr(TODO()),
    Src
