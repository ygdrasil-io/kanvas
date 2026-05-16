package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.core.SkIPoint16
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class RectanizerSkyline final : public Rectanizer {
 * public:
 *     RectanizerSkyline(int w, int h) : Rectanizer(w, h) {
 *         this->reset();
 *     }
 *
 *     ~RectanizerSkyline() final { }
 *
 *     void reset() final {
 *         fAreaSoFar = 0;
 *         fSkyline.clear();
 *         fSkyline.push_back(SkylineSegment{0, 0, this->width()});
 *     }
 *
 *     bool addRect(int w, int h, SkIPoint16* loc) final;
 *
 *     float percentFull() const final {
 *         return fAreaSoFar / ((float)this->width() * this->height());
 *     }
 *
 * private:
 *     struct SkylineSegment {
 *         int  fX;
 *         int  fY;
 *         int  fWidth;
 *     };
 *
 *     SkTDArray<SkylineSegment> fSkyline;
 *
 *     int32_t fAreaSoFar;
 *
 *     // Can a width x height rectangle fit in the free space represented by
 *     // the skyline segments >= 'skylineIndex'? If so, return true and fill in
 *     // 'y' with the y-location at which it fits (the x location is pulled from
 *     // 'skylineIndex's segment.
 *     bool rectangleFits(int skylineIndex, int width, int height, int* y) const;
 *     // Update the skyline structure to include a width x height rect located
 *     // at x,y.
 *     void addSkylineLevel(int skylineIndex, int x, int y, int width, int height);
 * }
 * ```
 */
public class RectanizerSkyline public constructor(
  w: Int,
  h: Int,
) : Rectanizer(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkylineSegment> fSkyline
   * ```
   */
  private var fSkyline: SkTDArray<SkylineSegment> = TODO("Initialize fSkyline")

  /**
   * C++ original:
   * ```cpp
   * int32_t fAreaSoFar
   * ```
   */
  private var fAreaSoFar: Int = TODO("Initialize fAreaSoFar")

  /**
   * C++ original:
   * ```cpp
   * void reset() final {
   *         fAreaSoFar = 0;
   *         fSkyline.clear();
   *         fSkyline.push_back(SkylineSegment{0, 0, this->width()});
   *     }
   * ```
   */
  public override fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RectanizerSkyline::addRect(int width, int height, SkIPoint16* loc) {
   *     if ((unsigned)width > (unsigned)this->width() ||
   *         (unsigned)height > (unsigned)this->height()) {
   *         return false;
   *     }
   *
   *     // find position for new rectangle
   *     int bestWidth = this->width() + 1;
   *     int bestX = 0;
   *     int bestY = this->height() + 1;
   *     int bestIndex = -1;
   *     for (int i = 0; i < fSkyline.size(); ++i) {
   *         int y;
   *         if (this->rectangleFits(i, width, height, &y)) {
   *             // minimize y position first, then width of skyline
   *             if (y < bestY || (y == bestY && fSkyline[i].fWidth < bestWidth)) {
   *                 bestIndex = i;
   *                 bestWidth = fSkyline[i].fWidth;
   *                 bestX = fSkyline[i].fX;
   *                 bestY = y;
   *             }
   *         }
   *     }
   *
   *     // add rectangle to skyline
   *     if (-1 != bestIndex) {
   *         this->addSkylineLevel(bestIndex, bestX, bestY, width, height);
   *         loc->fX = bestX;
   *         loc->fY = bestY;
   *
   *         fAreaSoFar += width*height;
   *         return true;
   *     }
   *
   *     loc->fX = 0;
   *     loc->fY = 0;
   *     return false;
   * }
   * ```
   */
  public override fun addRect(
    w: Int,
    h: Int,
    loc: SkIPoint16?,
  ): Boolean {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * float percentFull() const final {
   *         return fAreaSoFar / ((float)this->width() * this->height());
   *     }
   * ```
   */
  public override fun percentFull(): Float {
    TODO("Implement percentFull")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RectanizerSkyline::rectangleFits(int skylineIndex, int width, int height, int* ypos) const {
   *     int x = fSkyline[skylineIndex].fX;
   *     if (x + width > this->width()) {
   *         return false;
   *     }
   *
   *     int widthLeft = width;
   *     int i = skylineIndex;
   *     int y = fSkyline[skylineIndex].fY;
   *     while (widthLeft > 0) {
   *         y = std::max(y, fSkyline[i].fY);
   *         if (y + height > this->height()) {
   *             return false;
   *         }
   *         widthLeft -= fSkyline[i].fWidth;
   *         ++i;
   *         SkASSERT(i < fSkyline.size() || widthLeft <= 0);
   *     }
   *
   *     *ypos = y;
   *     return true;
   * }
   * ```
   */
  private fun rectangleFits(
    skylineIndex: Int,
    width: Int,
    height: Int,
    y: Int?,
  ): Boolean {
    TODO("Implement rectangleFits")
  }

  /**
   * C++ original:
   * ```cpp
   * void RectanizerSkyline::addSkylineLevel(int skylineIndex, int x, int y, int width, int height) {
   *     SkylineSegment newSegment;
   *     newSegment.fX = x;
   *     newSegment.fY = y + height;
   *     newSegment.fWidth = width;
   *     fSkyline.insert(skylineIndex, 1, &newSegment);
   *
   *     SkASSERT(newSegment.fX + newSegment.fWidth <= this->width());
   *     SkASSERT(newSegment.fY <= this->height());
   *
   *     // delete width of the new skyline segment from following ones
   *     for (int i = skylineIndex+1; i < fSkyline.size(); ++i) {
   *         // The new segment subsumes all or part of fSkyline[i]
   *         SkASSERT(fSkyline[i-1].fX <= fSkyline[i].fX);
   *
   *         if (fSkyline[i].fX < fSkyline[i-1].fX + fSkyline[i-1].fWidth) {
   *             int shrink = fSkyline[i-1].fX + fSkyline[i-1].fWidth - fSkyline[i].fX;
   *
   *             fSkyline[i].fX += shrink;
   *             fSkyline[i].fWidth -= shrink;
   *
   *             if (fSkyline[i].fWidth <= 0) {
   *                 // fully consumed
   *                 fSkyline.remove(i);
   *                 --i;
   *             } else {
   *                 // only partially consumed
   *                 break;
   *             }
   *         } else {
   *             break;
   *         }
   *     }
   *
   *     // merge fSkylines
   *     for (int i = 0; i < fSkyline.size()-1; ++i) {
   *         if (fSkyline[i].fY == fSkyline[i+1].fY) {
   *             fSkyline[i].fWidth += fSkyline[i+1].fWidth;
   *             fSkyline.remove(i+1);
   *             --i;
   *         }
   *     }
   * }
   * ```
   */
  private fun addSkylineLevel(
    skylineIndex: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    TODO("Implement addSkylineLevel")
  }

  public data class SkylineSegment public constructor(
    public var fX: Int,
    public var fY: Int,
    public var fWidth: Int,
  )
}
