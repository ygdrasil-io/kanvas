package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkImage
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API ImageProvider : public SkRefCnt {
 * public:
 *     // If the client's derived class already has a Graphite-backed image that has the same
 *     // contents as 'image' and meets the requirements, then it can be returned.
 *     // makeTextureImage can always be called to create an acceptable Graphite-backed image
 *     // which could then be cached.
 *     virtual sk_sp<SkImage> findOrCreate(Recorder* recorder,
 *                                         const SkImage* image,
 *                                         SkImage::RequiredProperties) = 0;
 * }
 * ```
 */
public abstract class ImageProvider : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> findOrCreate(Recorder* recorder,
   *                                         const SkImage* image,
   *                                         SkImage::RequiredProperties) = 0
   * ```
   */
  public abstract fun findOrCreate(
    recorder: Recorder?,
    image: SkImage?,
    param2: SkImage.RequiredProperties,
  ): Int
}
