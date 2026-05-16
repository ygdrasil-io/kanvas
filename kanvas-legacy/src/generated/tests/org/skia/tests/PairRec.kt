package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct PairRec {
 *     T   fYin;
 *     T   fYang;
 * }
 * ```
 */
public data class PairRec<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * template <typename T> struct PairRec {
   *     T   fYin
   * ```
   */
  private var fYin: T,
  /**
   * C++ original:
   * ```cpp
   * T   fYang
   * ```
   */
  private var fYang: T,
)
