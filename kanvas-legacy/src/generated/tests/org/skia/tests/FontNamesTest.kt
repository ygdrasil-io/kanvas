package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.tools.Record

/**
 * C++ original:
 * ```cpp
 * struct FontNamesTest {
 *     const uint8_t* data;
 *     size_t size;
 *     SkOTTableName::Record::NameID nameID;
 *     size_t nameCount;
 *     struct {
 *         const char* name;
 *         const char* language;
 *     } names[10];
 *
 * }
 * ```
 */
public data class FontNamesTest public constructor(
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* data
   * ```
   */
  public val `data`: Int?,
  /**
   * C++ original:
   * ```cpp
   * size_t size
   * ```
   */
  public var size: Int,
  /**
   * C++ original:
   * ```cpp
   * SkOTTableName::Record::NameID nameID
   * ```
   */
  public var nameID: Record.NameID,
  /**
   * C++ original:
   * ```cpp
   * size_t nameCount
   * ```
   */
  public var nameCount: Int,
  public val name: String?,
  public val language: String?,
)
