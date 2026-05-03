package org.skia.tools

import kotlin.Int
import org.skia.foundation.SkPixmap
import org.skia.math.SkIPoint

/**
 * C++ original:
 * ```cpp
 * class PixelIter {
 * public:
 *     PixelIter();
 *     PixelIter(SkSurface* surf) {
 *         SkPixmap pm;
 *         if (!surf->peekPixels(&pm)) {
 *             pm.reset();
 *         }
 *         this->reset(pm);
 *     }
 *
 *     void reset(const SkPixmap& pm) {
 *         fPM  = pm;
 *         fLoc = {-1, 0};
 *     }
 *
 *     void* next(SkIPoint* loc = nullptr) {
 *         if (!fPM.addr()) {
 *             return nullptr;
 *         }
 *         fLoc.fX += 1;
 *         if (fLoc.fX >= fPM.width()) {
 *             fLoc.fX = 0;
 *             if (++fLoc.fY >= fPM.height()) {
 *                 this->setDone();
 *                 return nullptr;
 *             }
 *         }
 *         if (loc) {
 *             *loc = fLoc;
 *         }
 *         return fPM.writable_addr(fLoc.fX, fLoc.fY);
 *     }
 *
 *     void setDone() { fPM.reset(); }
 *
 * private:
 *     SkPixmap fPM;
 *     SkIPoint fLoc;
 * }
 * ```
 */
public data class PixelIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPixmap fPM
   * ```
   */
  private var fPM: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIPoint fLoc
   * ```
   */
  private var fLoc: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset(const SkPixmap& pm) {
   *         fPM  = pm;
   *         fLoc = {-1, 0};
   *     }
   * ```
   */
  public fun reset(pm: SkPixmap) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void* next(SkIPoint* loc = nullptr) {
   *         if (!fPM.addr()) {
   *             return nullptr;
   *         }
   *         fLoc.fX += 1;
   *         if (fLoc.fX >= fPM.width()) {
   *             fLoc.fX = 0;
   *             if (++fLoc.fY >= fPM.height()) {
   *                 this->setDone();
   *                 return nullptr;
   *             }
   *         }
   *         if (loc) {
   *             *loc = fLoc;
   *         }
   *         return fPM.writable_addr(fLoc.fX, fLoc.fY);
   *     }
   * ```
   */
  public fun next(loc: SkIPoint? = TODO()) {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDone() { fPM.reset(); }
   * ```
   */
  public fun setDone() {
    TODO("Implement setDone")
  }
}
