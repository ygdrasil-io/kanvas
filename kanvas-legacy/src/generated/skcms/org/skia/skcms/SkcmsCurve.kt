package org.skia.skcms

import kotlin.Any
import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * union skcms_Curve {
 *     struct {
 *         // this needs to line up with alias_of_table_entries so we can tell if there are or
 *         // are not table entries. If this is 0, this struct is a parametric function,
 *         // otherwise it's a table entry.
 *         uint32_t alias_of_table_entries;
 *         skcms_TransferFunction parametric;
 *     };
 *     struct {
 *         uint32_t table_entries;
 *         const uint8_t* table_8;
 *         const uint8_t* table_16;
 *     };
 * }
 * ```
 */
public data class SkcmsCurve public constructor(
  public var aliasOfTableEntries: UInt,
  public var parametric: SkcmsTransferFunction,
  public var tableEntries: UInt,
  public val table8: UByte?,
  public val table16: UByte?,
)

public typealias SkcmsCurve = Any
