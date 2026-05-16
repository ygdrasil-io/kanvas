package org.skia.tests

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import kotlin.UByte
import org.skia.core.SkPathOp
import org.skia.math.SkPathFillType

/**
 * C++ original:
 * ```cpp
 * struct PathOpsThreadState {
 *     unsigned char fA;
 *     unsigned char fB;
 *     unsigned char fC;
 *     unsigned char fD;
 *     std::string fPathStr;
 *     const char* fKey;
 *     char fSerialNo[256];
 *     skiatest::Reporter* fReporter;
 *     SkBitmap* fBitmap;
 *
 *     void outputProgress(const char* pathStr, SkPathFillType);
 *     void outputProgress(const char* pathStr, SkPathOp);
 * }
 * ```
 */
public data class PathOpsThreadState public constructor(
  /**
   * C++ original:
   * ```cpp
   * unsigned char fA
   * ```
   */
  public var fA: UByte,
  /**
   * C++ original:
   * ```cpp
   * unsigned char fB
   * ```
   */
  public var fB: UByte,
  /**
   * C++ original:
   * ```cpp
   * unsigned char fC
   * ```
   */
  public var fC: UByte,
  /**
   * C++ original:
   * ```cpp
   * unsigned char fD
   * ```
   */
  public var fD: UByte,
  /**
   * C++ original:
   * ```cpp
   * std::string fPathStr
   * ```
   */
  public var fPathStr: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* fKey
   * ```
   */
  public val fKey: String?,
  /**
   * C++ original:
   * ```cpp
   * char fSerialNo[256]
   * ```
   */
  public var fSerialNo: CharArray,
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  public var fReporter: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * SkBitmap* fBitmap
   * ```
   */
  public var fBitmap: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * void outputProgress(const char* pathStr, SkPathFillType)
   * ```
   */
  public fun outputProgress(pathStr: String?, param1: SkPathFillType) {
    TODO("Implement outputProgress")
  }

  /**
   * C++ original:
   * ```cpp
   * void outputProgress(const char* pathStr, SkPathOp)
   * ```
   */
  public fun outputProgress(pathStr: String?, param1: SkPathOp) {
    TODO("Implement outputProgress")
  }
}
