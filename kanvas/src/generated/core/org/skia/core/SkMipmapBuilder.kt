package org.skia.core

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkMipmapBuilder {
 * public:
 *     explicit SkMipmapBuilder(const SkImageInfo&);
 *     ~SkMipmapBuilder();
 *
 *     int countLevels() const;
 *     SkPixmap level(int index) const;
 *
 *     /**
 *      *  If these levels are compatible with src, return a new Image that combines src's base level
 *      *  with these levels as mip levels. If not compatible, this returns nullptr.
 *      */
 *     sk_sp<SkImage> attachTo(const sk_sp<const SkImage>& src);
 *
 * private:
 *     sk_sp<SkMipmap> fMM;
 * }
 * ```
 */
public data class SkMipmapBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMipmap> fMM
   * ```
   */
  private var fMM: SkSp<SkMipmap>,
) {
  /**
   * C++ original:
   * ```cpp
   * int SkMipmapBuilder::countLevels() const {
   *     return fMM ? fMM->countLevels() : 0;
   * }
   * ```
   */
  public fun countLevels(): Int {
    TODO("Implement countLevels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixmap SkMipmapBuilder::level(int index) const {
   *     SkPixmap pm;
   *
   *     SkMipmap::Level level;
   *     if (fMM && fMM->getLevel(index, &level)) {
   *         pm = level.fPixmap;
   *     }
   *     return pm;
   * }
   * ```
   */
  public fun level(index: Int): SkPixmap {
    TODO("Implement level")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkMipmapBuilder::attachTo(const sk_sp<const SkImage>& src) {
   *     return src->withMipmaps(fMM);
   * }
   * ```
   */
  public fun attachTo(src: SkSp<SkImage>): SkSp<SkImage> {
    TODO("Implement attachTo")
  }
}
