package org.skia.foundation

/**
 * C++ original:
 * ```cpp
 * struct SkMipmapDownSampler {
 *     virtual ~SkMipmapDownSampler() {}
 *
 *     virtual void buildLevel(const SkPixmap& dst, const SkPixmap& src) = 0;
 * }
 * ```
 */
public abstract class SkMipmapDownSampler {
  /**
   * C++ original:
   * ```cpp
   * virtual void buildLevel(const SkPixmap& dst, const SkPixmap& src) = 0
   * ```
   */
  public abstract fun buildLevel(dst: SkPixmap, src: SkPixmap)
}
