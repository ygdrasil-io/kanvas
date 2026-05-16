package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkPath
import org.skia.foundation.SkRegion
import org.skia.math.SkIRect
import org.skia.math.SkRect
import undefined.RunHead

/**
 * C++ original:
 * ```cpp
 * class SkAAClip {
 * public:
 *     SkAAClip();
 *     SkAAClip(const SkAAClip&);
 *     ~SkAAClip();
 *
 *     SkAAClip& operator=(const SkAAClip&);
 *
 *     bool isEmpty() const { return nullptr == fRunHead; }
 *     const SkIRect& getBounds() const { return fBounds; }
 *
 *     // Returns true iff the clip is not empty, and is just a hard-edged rect (no partial alpha).
 *     // If true, getBounds() can be used in place of this clip.
 *     bool isRect() const;
 *
 *     bool setEmpty();
 *     bool setRect(const SkIRect&);
 *     bool setPath(const SkPath&, const SkIRect& bounds, bool doAA = true);
 *     bool setRegion(const SkRegion&);
 *
 *     bool op(const SkIRect&, SkClipOp);
 *     bool op(const SkRect&, SkClipOp, bool doAA);
 *     bool op(const SkAAClip&, SkClipOp);
 *
 *     bool translate(int dx, int dy, SkAAClip* dst) const;
 *
 *     /**
 *      *  Allocates a mask the size of the aaclip, and expands its data into
 *      *  the mask, using kA8_Format. Used for tests and visualization purposes.
 *      */
 *     void copyToMask(SkMaskBuilder*) const;
 *
 *     bool quickContains(const SkIRect& r) const {
 *         return this->quickContains(r.fLeft, r.fTop, r.fRight, r.fBottom);
 *     }
 *
 * #ifdef SK_DEBUG
 *     void validate() const;
 *     void debug(bool compress_y=false) const;
 * #else
 *     void validate() const {}
 *     void debug(bool compress_y=false) const {}
 * #endif
 *
 * private:
 *     class Builder;
 *     struct RunHead;
 *     friend class SkAAClipBlitter;
 *
 *     SkIRect  fBounds;
 *     RunHead* fRunHead;
 *
 *     void freeRuns();
 *
 *     bool quickContains(int left, int top, int right, int bottom) const;
 *
 *     bool trimBounds();
 *     bool trimTopBottom();
 *     bool trimLeftRight();
 *
 *     // For SkAAClipBlitter and quickContains
 *     const uint8_t* findRow(int y, int* lastYForRow = nullptr) const;
 *     const uint8_t* findX(const uint8_t data[], int x, int* initialCount = nullptr) const;
 * }
 * ```
 */
public data class SkAAClip public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkIRect  fBounds
   * ```
   */
  private var fBounds: SkIRect,
  /**
   * C++ original:
   * ```cpp
   * RunHead* fRunHead
   * ```
   */
  private var fRunHead: RunHead?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkAAClip& SkAAClip::operator=(const SkAAClip& src) {
   *     AUTO_AACLIP_VALIDATE(*this);
   *     src.validate();
   *
   *     if (this != &src) {
   *         this->freeRuns();
   *         fBounds = src.fBounds;
   *         fRunHead = src.fRunHead;
   *         if (fRunHead) {
   *             fRunHead->fRefCnt++;
   *         }
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(src: SkAAClip) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return nullptr == fRunHead; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& getBounds() const { return fBounds; }
   * ```
   */
  public fun getBounds(): SkIRect {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::isRect() const {
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *
   *     const RunHead* head = fRunHead;
   *     if (head->fRowCount != 1) {
   *         return false;
   *     }
   *     const YOffset* yoff = head->yoffsets();
   *     if (yoff->fY != fBounds.fBottom - 1) {
   *         return false;
   *     }
   *
   *     const uint8_t* row = head->data() + yoff->fOffset;
   *     int width = fBounds.width();
   *     do {
   *         if (row[1] != 0xFF) {
   *             return false;
   *         }
   *         int n = row[0];
   *         SkASSERT(n <= width);
   *         width -= n;
   *         row += 2;
   *     } while (width > 0);
   *     return true;
   * }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::setEmpty() {
   *     this->freeRuns();
   *     fBounds.setEmpty();
   *     fRunHead = nullptr;
   *     return false;
   * }
   * ```
   */
  public fun setEmpty(): Boolean {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::setRect(const SkIRect& bounds) {
   *     if (bounds.isEmpty()) {
   *         return this->setEmpty();
   *     }
   *
   *     AUTO_AACLIP_VALIDATE(*this);
   *
   *     this->freeRuns();
   *     fBounds = bounds;
   *     fRunHead = RunHead::AllocRect(bounds);
   *     SkASSERT(!this->isEmpty());
   *     return true;
   * }
   * ```
   */
  public fun setRect(bounds: SkIRect): Boolean {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::setPath(const SkPath& path, const SkIRect& clip, bool doAA) {
   *     AUTO_AACLIP_VALIDATE(*this);
   *
   *     if (clip.isEmpty()) {
   *         return this->setEmpty();
   *     }
   *
   *     SkIRect ibounds;
   *     // Since we assert that the BuilderBlitter will never blit outside the intersection
   *     // of clip and ibounds, we create the builder with the snug bounds.
   *     if (path.isInverseFillType()) {
   *         ibounds = clip;
   *     } else {
   *         path.getBounds().roundOut(&ibounds);
   *         // It's possible the bounds of our path might exceed SK_MaxS32 in width
   *         // but since our clip is within that width (otherwise isEmpty() above
   *         // would catch it), we can use isEmpty64() safely here. blitPath will
   *         // interesect the two bounds before drawing.
   *         if (ibounds.isEmpty64() || !ibounds.intersect(clip)) {
   *             return this->setEmpty();
   *         }
   *     }
   *
   *     Builder builder(ibounds);
   *     return builder.blitPath(this, path, doAA);
   * }
   * ```
   */
  public fun setPath(
    path: SkPath,
    bounds: SkIRect,
    doAA: Boolean = TODO(),
  ): Boolean {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::setRegion(const SkRegion& rgn) {
   *     if (rgn.isEmpty()) {
   *         return this->setEmpty();
   *     }
   *     if (rgn.isRect()) {
   *         return this->setRect(rgn.getBounds());
   *     }
   *
   *
   *     const SkIRect& bounds = rgn.getBounds();
   *     const int offsetX = bounds.fLeft;
   *     const int offsetY = bounds.fTop;
   *
   *     SkTDArray<YOffset> yArray;
   *     SkTDArray<uint8_t> xArray;
   *
   *     yArray.reserve(std::min(bounds.height(), 1024));
   *     xArray.reserve(std::min(bounds.width(), 512) * 128);
   *
   *     auto appendXRun = [&xArray](uint8_t value, int count) {
   *         SkASSERT(count >= 0);
   *         while (count > 0) {
   *             int n = count;
   *             if (n > 255) {
   *                 n = 255;
   *             }
   *             uint8_t* data = xArray.append(2);
   *             data[0] = n;
   *             data[1] = value;
   *             count -= n;
   *         }
   *     };
   *
   *     SkRegion::Iterator iter(rgn);
   *     int prevRight = 0;
   *     int prevBot = 0;
   *     YOffset* currY = nullptr;
   *
   *     for (; !iter.done(); iter.next()) {
   *         const SkIRect& r = iter.rect();
   *         SkASSERT(bounds.contains(r));
   *
   *         int bot = r.fBottom - offsetY;
   *         SkASSERT(bot >= prevBot);
   *         if (bot > prevBot) {
   *             if (currY) {
   *                 // flush current row
   *                 appendXRun(0, bounds.width() - prevRight);
   *             }
   *             // did we introduce an empty-gap from the prev row?
   *             int top = r.fTop - offsetY;
   *             if (top > prevBot) {
   *                 currY = yArray.append();
   *                 currY->fY = top - 1;
   *                 currY->fOffset = xArray.size();
   *                 appendXRun(0, bounds.width());
   *             }
   *             // create a new record for this Y value
   *             currY = yArray.append();
   *             currY->fY = bot - 1;
   *             currY->fOffset = xArray.size();
   *             prevRight = 0;
   *             prevBot = bot;
   *         }
   *
   *         int x = r.fLeft - offsetX;
   *         appendXRun(0, x - prevRight);
   *
   *         int w = r.fRight - r.fLeft;
   *         appendXRun(0xFF, w);
   *         prevRight = x + w;
   *         SkASSERT(prevRight <= bounds.width());
   *     }
   *     // flush last row
   *     appendXRun(0, bounds.width() - prevRight);
   *
   *     // now pack everything into a RunHead
   *     RunHead* head = RunHead::Alloc(yArray.size(), xArray.size_bytes());
   *     memcpy(head->yoffsets(), yArray.begin(), yArray.size_bytes());
   *     memcpy(head->data(), xArray.begin(), xArray.size_bytes());
   *
   *     this->setEmpty();
   *     fBounds = bounds;
   *     fRunHead = head;
   *     this->validate();
   *     return true;
   * }
   * ```
   */
  public fun setRegion(rgn: SkRegion): Boolean {
    TODO("Implement setRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::op(const SkIRect& rect, SkClipOp op) {
   *     // It can be expensive to build a local aaclip before applying the op, so
   *     // we first see if we can restrict the bounds of new rect to our current
   *     // bounds, or note that the new rect subsumes our current clip.
   *     SkIRect pixelBounds = fBounds;
   *     if (!pixelBounds.intersect(rect)) {
   *         // No change or clip becomes empty depending on 'op'
   *         switch(op) {
   *             case SkClipOp::kDifference: return !this->isEmpty();
   *             case SkClipOp::kIntersect:  return this->setEmpty();
   *         }
   *         SkUNREACHABLE;
   *     } else if (pixelBounds == fBounds) {
   *         // Wholly inside 'rect', so clip becomes empty or remains unchanged
   *         switch(op) {
   *             case SkClipOp::kDifference: return this->setEmpty();
   *             case SkClipOp::kIntersect:  return !this->isEmpty();
   *         }
   *         SkUNREACHABLE;
   *     } else if (op == SkClipOp::kIntersect && this->quickContains(pixelBounds)) {
   *         // We become just the remaining rectangle
   *         return this->setRect(pixelBounds);
   *     } else {
   *         SkAAClip clip;
   *         clip.setRect(rect);
   *         return this->op(clip, op);
   *     }
   * }
   * ```
   */
  public fun op(rect: SkIRect, op: SkClipOp): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::op(const SkRect& rect, SkClipOp op, bool doAA) {
   *     if (!doAA) {
   *         return this->op(rect.round(), op);
   *     } else {
   *         // Tighten bounds for "path" aaclip of the rect
   *         SkIRect pixelBounds = fBounds;
   *         if (!pixelBounds.intersect(rect.roundOut())) {
   *             // No change or clip becomes empty depending on 'op'
   *             switch(op) {
   *                 case SkClipOp::kDifference: return !this->isEmpty();
   *                 case SkClipOp::kIntersect:  return this->setEmpty();
   *             }
   *             SkUNREACHABLE;
   *         } else if (rect.contains(SkRect::Make(fBounds))) {
   *             // Wholly inside 'rect', so clip becomes empty or remains unchanged
   *             switch(op) {
   *                 case SkClipOp::kDifference: return this->setEmpty();
   *                 case SkClipOp::kIntersect:  return !this->isEmpty();
   *             }
   *             SkUNREACHABLE;
   *         } else if (op == SkClipOp::kIntersect && this->quickContains(pixelBounds)) {
   *             // We become just the rect intersected with pixel bounds (preserving fractional coords
   *             // for AA edges).
   *             return this->setPath(SkPath::Rect(rect), pixelBounds, /*doAA=*/true);
   *         } else {
   *             SkAAClip rectClip;
   *             rectClip.setPath(SkPath::Rect(rect),
   *                              op == SkClipOp::kDifference ? fBounds : pixelBounds,
   *                              /*doAA=*/true);
   *             return this->op(rectClip, op);
   *         }
   *     }
   * }
   * ```
   */
  public fun op(
    rect: SkRect,
    op: SkClipOp,
    doAA: Boolean,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::op(const SkAAClip& other, SkClipOp op) {
   *     AUTO_AACLIP_VALIDATE(*this);
   *
   *     if (this->isEmpty()) {
   *         // Once the clip is empty, it cannot become un-empty.
   *         return false;
   *     }
   *
   *     SkIRect bounds = fBounds;
   *     switch(op) {
   *         case SkClipOp::kDifference:
   *             if (other.isEmpty() || !SkIRect::Intersects(fBounds, other.fBounds)) {
   *                 // this remains unmodified and isn't empty
   *                 return true;
   *             }
   *             break;
   *
   *         case SkClipOp::kIntersect:
   *             if (other.isEmpty() || !bounds.intersect(other.fBounds)) {
   *                 // the intersected clip becomes empty
   *                 return this->setEmpty();
   *             }
   *             break;
   *     }
   *
   *
   *     SkASSERT(SkIRect::Intersects(bounds, fBounds));
   *     SkASSERT(SkIRect::Intersects(bounds, other.fBounds));
   *
   *     Builder builder(bounds);
   *     return builder.applyClipOp(this, other, op);
   * }
   * ```
   */
  public fun op(other: SkAAClip, op: SkClipOp): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::translate(int dx, int dy, SkAAClip* dst) const {
   *     if (nullptr == dst) {
   *         return !this->isEmpty();
   *     }
   *
   *     if (this->isEmpty()) {
   *         return dst->setEmpty();
   *     }
   *
   *     if (this != dst) {
   *         fRunHead->fRefCnt++;
   *         dst->freeRuns();
   *         dst->fRunHead = fRunHead;
   *         dst->fBounds = fBounds;
   *     }
   *     dst->fBounds.offset(dx, dy);
   *     return true;
   * }
   * ```
   */
  public fun translate(
    dx: Int,
    dy: Int,
    dst: SkAAClip?,
  ): Boolean {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClip::copyToMask(SkMaskBuilder* mask) const {
   *     auto expandRowToMask = [](uint8_t* dst, const uint8_t* row, int width) {
   *         while (width > 0) {
   *             int n = row[0];
   *             SkASSERT(width >= n);
   *             memset(dst, row[1], n);
   *             dst += n;
   *             row += 2;
   *             width -= n;
   *         }
   *         SkASSERT(0 == width);
   *     };
   *
   *     mask->format() = SkMask::kA8_Format;
   *     if (this->isEmpty()) {
   *         mask->bounds().setEmpty();
   *         mask->image() = nullptr;
   *         mask->rowBytes() = 0;
   *         return;
   *     }
   *
   *     mask->bounds() = fBounds;
   *     mask->rowBytes() = fBounds.width();
   *     size_t size = mask->computeImageSize();
   *     mask->image() = SkMaskBuilder::AllocImage(size);
   *
   *     Iter iter = RunHead::Iterate(*this);
   *     uint8_t* dst = mask->image();
   *     const int width = fBounds.width();
   *
   *     int y = fBounds.fTop;
   *     while (!iter.done()) {
   *         do {
   *             expandRowToMask(dst, iter.data(), width);
   *             dst += mask->fRowBytes;
   *         } while (++y < iter.bottom());
   *         iter.next();
   *     }
   * }
   * ```
   */
  public fun copyToMask(mask: SkMaskBuilder?) {
    TODO("Implement copyToMask")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickContains(const SkIRect& r) const {
   *         return this->quickContains(r.fLeft, r.fTop, r.fRight, r.fBottom);
   *     }
   * ```
   */
  public fun quickContains(r: SkIRect): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClip::validate() const {
   *     if (nullptr == fRunHead) {
   *         SkASSERT(fBounds.isEmpty());
   *         return;
   *     }
   *     SkASSERT(!fBounds.isEmpty());
   *
   *     const RunHead* head = fRunHead;
   *     SkASSERT(head->fRefCnt.load() > 0);
   *     SkASSERT(head->fRowCount > 0);
   *
   *     const YOffset* yoff = head->yoffsets();
   *     const YOffset* ystop = yoff + head->fRowCount;
   *     const int lastY = fBounds.height() - 1;
   *
   *     // Y and offset must be monotonic
   *     int prevY = -1;
   *     int32_t prevOffset = -1;
   *     while (yoff < ystop) {
   *         SkASSERT(prevY < yoff->fY);
   *         SkASSERT(yoff->fY <= lastY);
   *         prevY = yoff->fY;
   *         SkASSERT(prevOffset < (int32_t)yoff->fOffset);
   *         prevOffset = yoff->fOffset;
   *         const uint8_t* row = head->data() + yoff->fOffset;
   *         size_t rowLength = compute_row_length(row, fBounds.width());
   *         SkASSERT(yoff->fOffset + rowLength <= head->fDataSize);
   *         yoff += 1;
   *     }
   *     // check the last entry;
   *     --yoff;
   *     SkASSERT(yoff->fY == lastY);
   * }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClip::debug(bool compress_y) const {
   *     Iter iter = RunHead::Iterate(*this);
   *     const int width = fBounds.width();
   *
   *     int y = fBounds.fTop;
   *     while (!iter.done()) {
   *         if (compress_y) {
   *             dump_one_row(iter.data(), width, iter.bottom() - iter.top() + 1);
   *         } else {
   *             do {
   *                 dump_one_row(iter.data(), width, 0);
   *             } while (++y < iter.bottom());
   *         }
   *         iter.next();
   *     }
   * }
   * ```
   */
  public fun debug(compressY: Boolean = TODO()) {
    TODO("Implement debug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClip::freeRuns() {
   *     if (fRunHead) {
   *         SkASSERT(fRunHead->fRefCnt.load() >= 1);
   *         if (1 == fRunHead->fRefCnt--) {
   *             sk_free(fRunHead);
   *         }
   *     }
   * }
   * ```
   */
  private fun freeRuns() {
    TODO("Implement freeRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::quickContains(int left, int top, int right, int bottom) const {
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *     if (!fBounds.contains(SkIRect{left, top, right, bottom})) {
   *         return false;
   *     }
   *
   *     int lastY SK_INIT_TO_AVOID_WARNING;
   *     const uint8_t* row = this->findRow(top, &lastY);
   *     if (lastY < bottom) {
   *         return false;
   *     }
   *     // now just need to check in X
   *     int count;
   *     row = this->findX(row, left, &count);
   *
   *     int rectWidth = right - left;
   *     while (0xFF == row[1]) {
   *         if (count >= rectWidth) {
   *             return true;
   *         }
   *         rectWidth -= count;
   *         row += 2;
   *         count = row[0];
   *     }
   *     return false;
   * }
   * ```
   */
  private fun quickContains(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::trimBounds() {
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *
   *     const RunHead* head = fRunHead;
   *     const YOffset* yoff = head->yoffsets();
   *
   *     SkASSERT(head->fRowCount > 0);
   *     const YOffset& lastY = yoff[head->fRowCount - 1];
   *     SkASSERT(lastY.fY + 1 <= fBounds.height());
   *     fBounds.fBottom = fBounds.fTop + lastY.fY + 1;
   *     SkASSERT(lastY.fY + 1 == fBounds.height());
   *     SkASSERT(!fBounds.isEmpty());
   *
   *     return this->trimTopBottom() && this->trimLeftRight();
   * }
   * ```
   */
  private fun trimBounds(): Boolean {
    TODO("Implement trimBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::trimTopBottom() {
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *
   *     this->validate();
   *
   *     const int width = fBounds.width();
   *     RunHead* head = fRunHead;
   *     YOffset* yoff = head->yoffsets();
   *     YOffset* stop = yoff + head->fRowCount;
   *     const uint8_t* base = head->data();
   *
   *     //  Look to trim away empty rows from the top.
   *     //
   *     int skip = 0;
   *     while (yoff < stop) {
   *         const uint8_t* data = base + yoff->fOffset;
   *         if (!row_is_all_zeros(data, width)) {
   *             break;
   *         }
   *         skip += 1;
   *         yoff += 1;
   *     }
   *     SkASSERT(skip <= head->fRowCount);
   *     if (skip == head->fRowCount) {
   *         return this->setEmpty();
   *     }
   *     if (skip > 0) {
   *         // adjust fRowCount and fBounds.fTop, and slide all the data up
   *         // as we remove [skip] number of YOffset entries
   *         yoff = head->yoffsets();
   *         int dy = yoff[skip - 1].fY + 1;
   *         for (int i = skip; i < head->fRowCount; ++i) {
   *             SkASSERT(yoff[i].fY >= dy);
   *             yoff[i].fY -= dy;
   *         }
   *         YOffset* dst = head->yoffsets();
   *         size_t size = head->fRowCount * sizeof(YOffset) + head->fDataSize;
   *         memmove(dst, dst + skip, size - skip * sizeof(YOffset));
   *
   *         fBounds.fTop += dy;
   *         SkASSERT(!fBounds.isEmpty());
   *         head->fRowCount -= skip;
   *         SkASSERT(head->fRowCount > 0);
   *
   *         this->validate();
   *         // need to reset this after the memmove
   *         base = head->data();
   *     }
   *
   *     //  Look to trim away empty rows from the bottom.
   *     //  We know that we have at least one non-zero row, so we can just walk
   *     //  backwards without checking for running past the start.
   *     //
   *     stop = yoff = head->yoffsets() + head->fRowCount;
   *     do {
   *         yoff -= 1;
   *     } while (row_is_all_zeros(base + yoff->fOffset, width));
   *     skip = SkToInt(stop - yoff - 1);
   *     SkASSERT(skip >= 0 && skip < head->fRowCount);
   *     if (skip > 0) {
   *         // removing from the bottom is easier than from the top, as we don't
   *         // have to adjust any of the Y values, we just have to trim the array
   *         memmove(stop - skip, stop, head->fDataSize);
   *
   *         fBounds.fBottom = fBounds.fTop + yoff->fY + 1;
   *         SkASSERT(!fBounds.isEmpty());
   *         head->fRowCount -= skip;
   *         SkASSERT(head->fRowCount > 0);
   *     }
   *     this->validate();
   *
   *     return true;
   * }
   * ```
   */
  private fun trimTopBottom(): Boolean {
    TODO("Implement trimTopBottom")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAAClip::trimLeftRight() {
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *
   *     AUTO_AACLIP_VALIDATE(*this);
   *
   *     const int width = fBounds.width();
   *     RunHead* head = fRunHead;
   *     YOffset* yoff = head->yoffsets();
   *     YOffset* stop = yoff + head->fRowCount;
   *     uint8_t* base = head->data();
   *
   *     // After this loop, 'leftZeros' & 'rightZeros' will contain the minimum
   *     // number of zeros on the left and right of the clip. This information
   *     // can be used to shrink the bounding box.
   *     int leftZeros = width;
   *     int riteZeros = width;
   *     while (yoff < stop) {
   *         int L, R;
   *         count_left_right_zeros(base + yoff->fOffset, width, &L, &R);
   *         SkASSERT(L + R < width || (L == width && R == width));
   *         if (L < leftZeros) {
   *             leftZeros = L;
   *         }
   *         if (R < riteZeros) {
   *             riteZeros = R;
   *         }
   *         if (0 == (leftZeros | riteZeros)) {
   *             // no trimming to do
   *             return true;
   *         }
   *         yoff += 1;
   *     }
   *
   *     SkASSERT(leftZeros || riteZeros);
   *     if (width == leftZeros) {
   *         SkASSERT(width == riteZeros);
   *         return this->setEmpty();
   *     }
   *
   *     this->validate();
   *
   *     fBounds.fLeft += leftZeros;
   *     fBounds.fRight -= riteZeros;
   *     SkASSERT(!fBounds.isEmpty());
   *
   *     // For now we don't realloc the storage (for time), we just shrink in place
   *     // This means we don't have to do any memmoves either, since we can just
   *     // play tricks with the yoff->fOffset for each row
   *     yoff = head->yoffsets();
   *     while (yoff < stop) {
   *         uint8_t* row = base + yoff->fOffset;
   *         SkDEBUGCODE((void)compute_row_length(row, width);)
   *         yoff->fOffset += trim_row_left_right(row, width, leftZeros, riteZeros);
   *         SkDEBUGCODE((void)compute_row_length(base + yoff->fOffset, width - leftZeros - riteZeros);)
   *         yoff += 1;
   *     }
   *     return true;
   * }
   * ```
   */
  private fun trimLeftRight(): Boolean {
    TODO("Implement trimLeftRight")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* SkAAClip::findRow(int y, int* lastYForRow) const {
   *     SkASSERT(fRunHead);
   *
   *     if (y < fBounds.fTop || y >= fBounds.fBottom) {
   *         return nullptr;
   *     }
   *     y -= fBounds.y();  // our yoffs values are relative to the top
   *
   *     const YOffset* yoff = fRunHead->yoffsets();
   *     while (yoff->fY < y) {
   *         yoff += 1;
   *         SkASSERT(yoff - fRunHead->yoffsets() < fRunHead->fRowCount);
   *     }
   *
   *     if (lastYForRow) {
   *         *lastYForRow = fBounds.y() + yoff->fY;
   *     }
   *     return fRunHead->data() + yoff->fOffset;
   * }
   * ```
   */
  private fun findRow(y: Int, lastYForRow: Int? = TODO()): Int {
    TODO("Implement findRow")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* SkAAClip::findX(const uint8_t data[], int x, int* initialCount) const {
   *     SkASSERT(x >= fBounds.fLeft && x < fBounds.fRight);
   *     x -= fBounds.x();
   *
   *     // first skip up to X
   *     for (;;) {
   *         int n = data[0];
   *         if (x < n) {
   *             if (initialCount) {
   *                 *initialCount = n - x;
   *             }
   *             break;
   *         }
   *         data += 2;
   *         x -= n;
   *     }
   *     return data;
   * }
   * ```
   */
  private fun findX(
    `data`: Array<UByte>,
    x: Int,
    initialCount: Int? = TODO(),
  ): Int {
    TODO("Implement findX")
  }
}
