package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import org.skia.core.Iter
import org.skia.core.SkCanvas
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkNWayCanvas::Iter {
 * public:
 *     Iter(const SkTDArray<SkCanvas*>& list) : fList(list) {
 *         fIndex = 0;
 *     }
 *     bool next() {
 *         if (fIndex < fList.size()) {
 *             fCanvas = fList[fIndex++];
 *             return true;
 *         }
 *         return false;
 *     }
 *     SkCanvas* operator->() { return fCanvas; }
 *     SkCanvas* get() const { return fCanvas; }
 *
 * private:
 *     const SkTDArray<SkCanvas*>& fList;
 *     int fIndex;
 *     SkCanvas* fCanvas;
 * }
 * ```
 */
public open class Iter public constructor(
  list: SkTDArray<SkCanvas?>,
) : Iter() {
  /**
   * C++ original:
   * ```cpp
   * const SkTDArray<SkCanvas*>& fList
   * ```
   */
  private val fList: SkTDArray<SkCanvas?> = TODO("Initialize fList")

  /**
   * C++ original:
   * ```cpp
   * int fIndex
   * ```
   */
  private var fIndex: Int = TODO("Initialize fIndex")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* fCanvas
   * ```
   */
  private var fCanvas: SkCanvas? = TODO("Initialize fCanvas")

  /**
   * C++ original:
   * ```cpp
   * bool next() {
   *         if (fIndex < fList.size()) {
   *             fCanvas = fList[fIndex++];
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public override fun next(): Boolean {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* operator->() { return fCanvas; }
   * ```
   */
  public fun `get`(): SkCanvas {
    TODO("Implement get")
  }
}
