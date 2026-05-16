package org.skia.core

import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoAsciiToLC {
 * public:
 *     SkAutoAsciiToLC(const char str[], size_t len = (size_t)-1);
 *     ~SkAutoAsciiToLC();
 *
 *     const char* lc() const { return fLC; }
 *     size_t      length() const { return fLength; }
 *
 * private:
 *     char*   fLC;    // points to either the heap or fStorage
 *     size_t  fLength;
 *     enum {
 *         STORAGE = 64
 *     };
 *     char    fStorage[STORAGE+1];
 * }
 * ```
 */
public data class SkAutoAsciiToLC public constructor(
  /**
   * C++ original:
   * ```cpp
   * char*   fLC
   * ```
   */
  private var fLC: String?,
  /**
   * C++ original:
   * ```cpp
   * size_t  fLength
   * ```
   */
  private var fLength: Int,
  /**
   * C++ original:
   * ```cpp
   * char    fStorage[STORAGE+1]
   * ```
   */
  private var fStorage: CharArray,
) {
  /**
   * C++ original:
   * ```cpp
   * const char* lc() const { return fLC; }
   * ```
   */
  public fun lc(): Char {
    TODO("Implement lc")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t      length() const { return fLength; }
   * ```
   */
  public fun length(): Int {
    TODO("Implement length")
  }

  public companion object {
    public val storage: Int = TODO("Initialize storage")
  }
}
