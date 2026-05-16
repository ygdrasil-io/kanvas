package org.skia.tests

import kotlin.Array
import kotlin.CharArray
import org.skia.pdf.SkOTTableName

/**
 * C++ original:
 * ```cpp
 * struct Format1NameTable {
 *     SkOTTableName header;
 *     SkOTTableName::Record nameRecord[R];
 *     struct {
 *         SkOTTableName::Format1Ext header;
 *         SkOTTableName::Format1Ext::LangTagRecord langTagRecord[L];
 *     } format1ext;
 *     char data[D];
 * }
 * ```
 */
public data class Format1NameTable1119 public constructor(
  /**
   * C++ original:
   * ```cpp
   * template <size_t R, size_t L, size_t D> struct Format1NameTable {
   *     SkOTTableName header
   * ```
   */
  private var `header`: SkOTTableName,
  /**
   * C++ original:
   * ```cpp
   * SkOTTableName::Record nameRecord[R]
   * ```
   */
  private var nameRecord: Array<SkOTTableName.Record>,
  /**
   * C++ original:
   * ```cpp
   * char data[D]
   * ```
   */
  private var `data`: CharArray,
  /**
   * C++ original:
   * ```cpp
   * template <size_t R, size_t L, size_t D> struct Format1NameTable {
   *     SkOTTableName header
   * ```
   */
  private var `header`: SkOTTableName,
  public var langTagRecord: Array<SkOTTableName.Format1Ext.LangTagRecord>,
)

public typealias SimpleFormat1NameTable = Format1NameTable1119
