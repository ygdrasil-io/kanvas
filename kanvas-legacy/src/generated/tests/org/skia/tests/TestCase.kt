package org.skia.tests

import kotlin.Array
import kotlin.String
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct TestCase {
 *     const char* fTestCase;
 *     FactoryT    fFactory;
 *     SkColor4f   fExpectedColors[2];   /* [ w/o mipmaps, w/ mipmaps ] */
 * }
 * ```
 */
public data class TestCase public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fTestCase
   * ```
   */
  public val fTestCase: String?,
  /**
   * C++ original:
   * ```cpp
   * FactoryT    fFactory
   * ```
   */
  public var fFactory: FactoryT,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f   fExpectedColors[2]
   * ```
   */
  public var fExpectedColors: Array<SkColor4f>,
)
