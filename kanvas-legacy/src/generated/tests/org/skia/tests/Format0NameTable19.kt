package org.skia.tests

import kotlin.Array
import kotlin.CharArray
import org.skia.pdf.SkOTTableName

/**
 * C++ original:
 * ```cpp
 * struct Format0NameTable {
 *     SkOTTableName header;
 *     SkOTTableName::Record nameRecord[R];
 *     char data[D];
 * }
 * ```
 */
public data class Format0NameTable19 public constructor(
  /**
   * C++ original:
   * ```cpp
   * template <size_t R, size_t D> struct Format0NameTable {
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
)

public typealias SimpleFormat0NameTable = Format0NameTable19
