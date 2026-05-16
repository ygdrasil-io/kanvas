package org.skia.tests

import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct ImplicitString : public SkString {
 *     // This constructor is intentionally not explicit to allow for convenient implicit conversions.
 *     template <typename T>
 *     ImplicitString(const T& s) : SkString(s) {}
 *     ImplicitString() : SkString("") {}
 * }
 * ```
 */
public open class ImplicitString<T> public constructor(
  s: T,
) : String(TODO()) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     ImplicitString(const T& s) : SkString(s) {}
   * ```
   */
  public constructor() : this(TODO()) {
    TODO("Implement constructor")
  }
}

public typealias Name = ImplicitString

public typealias Path = ImplicitString
