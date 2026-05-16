package org.skia.tests

import kotlin.String
import kotlin.unique_ptr

/**
 * C++ original:
 * ```cpp
 * struct TaggedSink : public std::unique_ptr<Sink> {
 *     SkString tag;
 * }
 * ```
 */
public open class TaggedSink public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString tag
   * ```
   */
  public var tag: String,
) : unique_ptr(TODO()),
    Sink
