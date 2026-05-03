package org.skia.core

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct SkBitmapCacheDesc {
 *     uint32_t    fImageID;       // != 0
 *     SkIRect     fSubset;        // always set to a valid rect (entire or subset)
 *
 *     void validate() const {
 *         SkASSERT(fImageID);
 *         SkASSERT(fSubset.fLeft >= 0 && fSubset.fTop >= 0);
 *         SkASSERT(fSubset.width() > 0 && fSubset.height() > 0);
 *     }
 *
 *     static SkBitmapCacheDesc Make(const SkImage*);
 *     static SkBitmapCacheDesc Make(uint32_t genID, const SkIRect& subset);
 * }
 * ```
 */
public data class SkBitmapCacheDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fImageID
   * ```
   */
  public var fImageID: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect     fSubset
   * ```
   */
  public var fSubset: SkIRect,
) {
  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *         SkASSERT(fImageID);
   *         SkASSERT(fSubset.fLeft >= 0 && fSubset.fTop >= 0);
   *         SkASSERT(fSubset.width() > 0 && fSubset.height() > 0);
   *     }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkBitmapCacheDesc SkBitmapCacheDesc::Make(const SkImage* image) {
     *     SkIRect bounds = SkIRect::MakeWH(image->width(), image->height());
     *     return Make(image->uniqueID(), bounds);
     * }
     * ```
     */
    public fun make(image: SkImage?): SkBitmapCacheDesc {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkBitmapCacheDesc SkBitmapCacheDesc::Make(uint32_t imageID, const SkIRect& subset) {
     *     SkASSERT(imageID);
     *     SkASSERT(subset.width() > 0 && subset.height() > 0);
     *     return { imageID, subset };
     * }
     * ```
     */
    public fun make(genID: UInt, subset: SkIRect): SkBitmapCacheDesc {
      TODO("Implement make")
    }
  }
}
