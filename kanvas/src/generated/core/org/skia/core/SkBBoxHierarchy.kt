package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.collections.List
import org.skia.foundation.SkRefCnt
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkBBoxHierarchy : public SkRefCnt {
 * public:
 *     struct Metadata {
 *         bool isDraw;  // The corresponding SkRect bounds a draw command, not a pure state change.
 *     };
 *
 *     /**
 *      * Insert N bounding boxes into the hierarchy.
 *      */
 *     virtual void insert(const SkRect[], int N) = 0;
 *     virtual void insert(const SkRect[], const Metadata[], int N);
 *
 *     /**
 *      * Populate results with the indices of bounding boxes intersecting that query.
 *      */
 *     virtual void search(const SkRect& query, std::vector<int>* results) const = 0;
 *
 *     /**
 *      * Return approximate size in memory of *this.
 *      */
 *     virtual size_t bytesUsed() const = 0;
 *
 * protected:
 *     SkBBoxHierarchy() = default;
 *     SkBBoxHierarchy(const SkBBoxHierarchy&) = delete;
 *     SkBBoxHierarchy& operator=(const SkBBoxHierarchy&) = delete;
 * }
 * ```
 */
public abstract class SkBBoxHierarchy public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkBBoxHierarchy() = default
   * ```
   */
  public constructor(param0: SkBBoxHierarchy) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void insert(const SkRect[], int N) = 0
   * ```
   */
  public abstract fun insert(param0: Array<SkRect>, n: Int)

  /**
   * C++ original:
   * ```cpp
   * void SkBBoxHierarchy::insert(const SkRect rects[], const Metadata[], int N) {
   *     // Ignore Metadata.
   *     this->insert(rects, N);
   * }
   * ```
   */
  public open fun insert(
    rects: Array<SkRect>,
    param1: Array<org.skia.core.Metadata>,
    n: Int,
  ) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void search(const SkRect& query, std::vector<int>* results) const = 0
   * ```
   */
  public abstract fun search(query: SkRect, results: List<Int>?)

  /**
   * C++ original:
   * ```cpp
   * virtual size_t bytesUsed() const = 0
   * ```
   */
  public abstract fun bytesUsed(): ULong

  /**
   * C++ original:
   * ```cpp
   * SkBBoxHierarchy& operator=(const SkBBoxHierarchy&) = delete
   * ```
   */
  protected fun assign(param0: SkBBoxHierarchy) {
    TODO("Implement assign")
  }

  public data class Metadata public constructor(
    public var isDraw: Boolean,
  )
}
