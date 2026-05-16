package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkBBoxHierarchy
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct CountingBBH : public SkBBoxHierarchy {
 *     mutable int searchCalls;
 *
 *     CountingBBH() : searchCalls(0) {}
 *
 *     void search(const SkRect& query, std::vector<int>* results) const override {
 *         this->searchCalls++;
 *     }
 *
 *     void insert(const SkRect[], int) override {}
 *     size_t bytesUsed() const override { return 0; }
 * }
 * ```
 */
public open class CountingBBH public constructor(
  /**
   * C++ original:
   * ```cpp
   * mutable int searchCalls
   * ```
   */
  public var searchCalls: Int,
) : SkBBoxHierarchy(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * CountingBBH() : searchCalls(0) {}
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void search(const SkRect& query, std::vector<int>* results) const override {
   *         this->searchCalls++;
   *     }
   * ```
   */
  public override fun search(query: SkRect, results: List<Int>?) {
    TODO("Implement search")
  }

  /**
   * C++ original:
   * ```cpp
   * void insert(const SkRect[], int) override {}
   * ```
   */
  public override fun insert(param0: Array<SkRect>, param1: Int) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesUsed() const override { return 0; }
   * ```
   */
  public override fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }
}
