package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Token {
 * public:
 *     static Token InvalidToken() { return Token(0); }
 *
 *     Token(const Token&) = default;
 *     Token& operator=(const Token&) = default;
 *
 *     bool operator==(const Token& that) const { return fSequenceNumber == that.fSequenceNumber; }
 *     bool operator!=(const Token& that) const { return !(*this == that); }
 *     bool operator<(const Token that) const { return fSequenceNumber < that.fSequenceNumber; }
 *     bool operator<=(const Token that) const { return fSequenceNumber <= that.fSequenceNumber; }
 *     bool operator>(const Token that) const { return fSequenceNumber > that.fSequenceNumber; }
 *     bool operator>=(const Token that) const { return fSequenceNumber >= that.fSequenceNumber; }
 *
 *     Token& operator++() {
 *         ++fSequenceNumber;
 *         return *this;
 *     }
 *     Token operator++(int) {
 *         auto old = fSequenceNumber;
 *         ++fSequenceNumber;
 *         return Token(old);
 *     }
 *
 *     Token next() const { return Token(fSequenceNumber + 1); }
 *
 *     /** Returns the raw value for debugging and comparison. */
 *     uint64_t value() const { return fSequenceNumber; }
 *
 *     /** Is this token in the [start, end] inclusive interval? */
 *     bool inInterval(const Token& start, const Token& end) {
 *         return *this >= start && *this <= end;
 *     }
 *
 * private:
 *     explicit Token(uint64_t sequenceNumber) : fSequenceNumber(sequenceNumber) {}
 *     uint64_t fSequenceNumber;
 *
 *     friend class TokenTracker; // Allow TokenTracker to construct one
 * }
 * ```
 */
public data class Token public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint64_t fSequenceNumber
   * ```
   */
  private var fSequenceNumber: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Token& operator=(const Token&) = default
   * ```
   */
  public fun assign(param0: Token) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const Token& that) const { return fSequenceNumber == that.fSequenceNumber; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const Token& that) const { return !(*this == that); }
   * ```
   */
  public operator fun compareTo(that: Token): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator<(const Token that) const { return fSequenceNumber < that.fSequenceNumber; }
   * ```
   */
  public operator fun inc(): Token {
    TODO("Implement inc")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator<=(const Token that) const { return fSequenceNumber <= that.fSequenceNumber; }
   * ```
   */
  public fun next(): Token {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator>(const Token that) const { return fSequenceNumber > that.fSequenceNumber; }
   * ```
   */
  public fun `value`(): Int {
    TODO("Implement value")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator>=(const Token that) const { return fSequenceNumber >= that.fSequenceNumber; }
   * ```
   */
  public fun inInterval(start: Token, end: Token): Boolean {
    TODO("Implement inInterval")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Token InvalidToken() { return Token(0); }
     * ```
     */
    public fun invalidToken(): Token {
      TODO("Implement invalidToken")
    }
  }
}
