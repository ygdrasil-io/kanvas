package org.skia.tests

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct Failure {
 *     Failure(const char* f, int l, const char* c, const SkString& m)
 *         : fileName(f), lineNo(l), condition(c), message(m) {}
 *     const char* fileName;
 *     int lineNo;
 *     const char* condition;
 *     SkString message;
 *     SkString toString() const;
 * }
 * ```
 */
public data class Failure public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fileName
   * ```
   */
  public val fileName: String?,
  /**
   * C++ original:
   * ```cpp
   * int lineNo
   * ```
   */
  public var lineNo: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* condition
   * ```
   */
  public val condition: String?,
  /**
   * C++ original:
   * ```cpp
   * SkString message
   * ```
   */
  public var message: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkString skiatest::Failure::toString() const {
   *     SkString result = SkStringPrintf("%s:%d\t", this->fileName, this->lineNo);
   *     if (!this->message.isEmpty()) {
   *         result.append(this->message);
   *         if (strlen(this->condition) > 0) {
   *             result.append(": ");
   *         }
   *     }
   *     result.append(this->condition);
   *     return result;
   * }
   * ```
   */
  public override fun toString(): Int {
    TODO("Implement toString")
  }
}
