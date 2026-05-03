package org.skia.core

import kotlin.Int
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkDrawableList : SkNoncopyable {
 * public:
 *     SkDrawableList() {}
 *     ~SkDrawableList();
 *
 *     int count() const { return fArray.size(); }
 *     SkDrawable* const* begin() const { return fArray.begin(); }
 *     SkDrawable* const* end() const { return fArray.end(); }
 *
 *     void append(SkDrawable* drawable);
 *
 *     // Return a new or ref'd array of pictures that were snapped from our drawables.
 *     SkBigPicture::SnapshotArray* newDrawableSnapshot();
 *
 * private:
 *     SkTDArray<SkDrawable*> fArray;
 * }
 * ```
 */
public open class SkDrawableList public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkDrawable*> fArray
   * ```
   */
  private var fArray: SkTDArray<SkDrawable?> = TODO("Initialize fArray")

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fArray.size(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDrawable* const* begin() const { return fArray.begin(); }
   * ```
   */
  public fun begin(): SkDrawable? {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDrawable* const* end() const { return fArray.end(); }
   * ```
   */
  public fun end(): SkDrawable? {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDrawableList::append(SkDrawable* drawable) { *fArray.append() = SkRef(drawable); }
   * ```
   */
  public fun append(drawable: SkDrawable?) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBigPicture::SnapshotArray* SkDrawableList::newDrawableSnapshot() {
   *     const int count = fArray.size();
   *     if (0 == count) {
   *         return nullptr;
   *     }
   *     AutoTMalloc<const SkPicture*> pics(count);
   *     for (int i = 0; i < count; ++i) {
   *         pics[i] = fArray[i]->makePictureSnapshot().release();
   *     }
   *     return new SkBigPicture::SnapshotArray(pics.release(), count);
   * }
   * ```
   */
  public fun newDrawableSnapshot(): SkBigPicture.SnapshotArray {
    TODO("Implement newDrawableSnapshot")
  }
}
