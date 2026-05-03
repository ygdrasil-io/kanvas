package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkRasterClip {
 * public:
 *     SkRasterClip();
 *     explicit SkRasterClip(const SkIRect&);
 *     explicit SkRasterClip(const SkRegion&);
 *     explicit SkRasterClip(const SkRasterClip&);
 *     SkRasterClip(const SkPath& path, const SkIRect& bounds, bool doAA);
 *
 *     ~SkRasterClip();
 *
 *     SkRasterClip& operator=(const SkRasterClip&);
 *
 *     bool isBW() const { return fIsBW; }
 *     bool isAA() const { return !fIsBW; }
 *     const SkRegion& bwRgn() const { SkASSERT(fIsBW); return fBW; }
 *     const SkAAClip& aaRgn() const { SkASSERT(!fIsBW); return fAA; }
 *
 *     bool isEmpty() const {
 *         SkASSERT(this->computeIsEmpty() == fIsEmpty);
 *         return fIsEmpty;
 *     }
 *
 *     bool isRect() const {
 *         SkASSERT(this->computeIsRect() == fIsRect);
 *         return fIsRect;
 *     }
 *
 *     bool isComplex() const {
 *         return fIsBW ? fBW.isComplex() : !fAA.isEmpty();
 *     }
 *     const SkIRect& getBounds() const {
 *         return fIsBW ? fBW.getBounds() : fAA.getBounds();
 *     }
 *
 *     bool setEmpty();
 *     bool setRect(const SkIRect&);
 *
 *     bool op(const SkIRect&, SkClipOp);
 *     bool op(const SkRegion&, SkClipOp);
 *     bool op(const SkRect&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     bool op(const SkRRect&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     bool op(const SkPath&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     bool op(sk_sp<SkShader>);
 *
 *     void translate(int dx, int dy, SkRasterClip* dst) const;
 *
 *     bool quickContains(const SkIRect& rect) const {
 *         return fIsBW ? fBW.quickContains(rect) : fAA.quickContains(rect);
 *     }
 *
 *     /**
 *      *  Return true if this region is empty, or if the specified rectangle does
 *      *  not intersect the region. Returning false is not a guarantee that they
 *      *  intersect, but returning true is a guarantee that they do not.
 *      */
 *     bool quickReject(const SkIRect& rect) const {
 *         return !SkIRect::Intersects(this->getBounds(), rect);
 *     }
 *
 * #ifdef SK_DEBUG
 *     void validate() const;
 * #else
 *     void validate() const {}
 * #endif
 *
 *     sk_sp<SkShader> clipShader() const { return fShader; }
 *
 * private:
 *     SkRegion    fBW;
 *     SkAAClip    fAA;
 *     bool        fIsBW;
 *     // these 2 are caches based on querying the right obj based on fIsBW
 *     bool        fIsEmpty;
 *     bool        fIsRect;
 *     // if present, this augments the clip, not replaces it
 *     sk_sp<SkShader> fShader;
 *
 *     bool computeIsEmpty() const {
 *         return fIsBW ? fBW.isEmpty() : fAA.isEmpty();
 *     }
 *
 *     bool computeIsRect() const {
 *         return fIsBW ? fBW.isRect() : fAA.isRect();
 *     }
 *
 *     bool updateCacheAndReturnNonEmpty(bool detectAARect = true) {
 *         fIsEmpty = this->computeIsEmpty();
 *
 *         // detect that our computed AA is really just a (hard-edged) rect
 *         if (detectAARect && !fIsEmpty && !fIsBW && fAA.isRect()) {
 *             fBW.setRect(fAA.getBounds());
 *             fAA.setEmpty(); // don't need this anymore
 *             fIsBW = true;
 *         }
 *
 *         fIsRect = this->computeIsRect();
 *         return !fIsEmpty;
 *     }
 *
 *     void convertToAA();
 *
 *     bool op(const SkRasterClip&, SkClipOp);
 * }
 * ```
 */
public data class SkRasterClip public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRegion    fBW
   * ```
   */
  private var fBW: SkRegion,
  /**
   * C++ original:
   * ```cpp
   * SkAAClip    fAA
   * ```
   */
  private var fAA: SkAAClip,
  /**
   * C++ original:
   * ```cpp
   * bool        fIsBW
   * ```
   */
  private var fIsBW: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool        fIsEmpty
   * ```
   */
  private var fIsEmpty: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool        fIsRect
   * ```
   */
  private var fIsRect: Boolean,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkRasterClip& SkRasterClip::operator=(const SkRasterClip& that) {
   *     AUTO_RASTERCLIP_VALIDATE(that);
   *
   *     fIsBW = that.fIsBW;
   *     if (fIsBW) {
   *         fBW = that.fBW;
   *     } else {
   *         fAA = that.fAA;
   *     }
   *
   *     fIsEmpty = that.isEmpty();
   *     fIsRect = that.isRect();
   *     fShader = that.fShader;
   *     SkDEBUGCODE(this->validate();)
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: SkRasterClip) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isBW() const { return fIsBW; }
   * ```
   */
  public fun isBW(): Boolean {
    TODO("Implement isBW")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAA() const { return !fIsBW; }
   * ```
   */
  public fun isAA(): Boolean {
    TODO("Implement isAA")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRegion& bwRgn() const { SkASSERT(fIsBW); return fBW; }
   * ```
   */
  public fun bwRgn(): SkRegion {
    TODO("Implement bwRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkAAClip& aaRgn() const { SkASSERT(!fIsBW); return fAA; }
   * ```
   */
  public fun aaRgn(): SkAAClip {
    TODO("Implement aaRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const {
   *         SkASSERT(this->computeIsEmpty() == fIsEmpty);
   *         return fIsEmpty;
   *     }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRect() const {
   *         SkASSERT(this->computeIsRect() == fIsRect);
   *         return fIsRect;
   *     }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isComplex() const {
   *         return fIsBW ? fBW.isComplex() : !fAA.isEmpty();
   *     }
   * ```
   */
  public fun isComplex(): Boolean {
    TODO("Implement isComplex")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& getBounds() const {
   *         return fIsBW ? fBW.getBounds() : fAA.getBounds();
   *     }
   * ```
   */
  public fun getBounds(): SkIRect {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::setEmpty() {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     fIsBW = true;
   *     fBW.setEmpty();
   *     fAA.setEmpty();
   *     fIsEmpty = true;
   *     fIsRect = false;
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
   * bool SkRasterClip::setRect(const SkIRect& rect) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     fIsBW = true;
   *     fAA.setEmpty();
   *     fIsRect = fBW.setRect(rect);
   *     fIsEmpty = !fIsRect;
   *     return fIsRect;
   * }
   * ```
   */
  public fun setRect(rect: SkIRect): Boolean {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkIRect& rect, SkClipOp op) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     if (fIsBW) {
   *         fBW.op(rect, (SkRegion::Op) op);
   *     } else {
   *         fAA.op(rect, op);
   *     }
   *     return this->updateCacheAndReturnNonEmpty();
   * }
   * ```
   */
  public fun op(rect: SkIRect, op: SkClipOp): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkRegion& rgn, SkClipOp op) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     if (fIsBW) {
   *         (void)fBW.op(rgn, (SkRegion::Op) op);
   *     } else {
   *         SkAAClip tmp;
   *         tmp.setRegion(rgn);
   *         (void)fAA.op(tmp, op);
   *     }
   *     return this->updateCacheAndReturnNonEmpty();
   * }
   * ```
   */
  public fun op(rgn: SkRegion, op: SkClipOp): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkRect& localRect, const SkMatrix& matrix, SkClipOp op, bool doAA) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     const bool isScaleTrans = matrix.isScaleTranslate();
   *     if (!isScaleTrans) {
   *         return this->op(SkPath::Rect(localRect), matrix, op, doAA);
   *     }
   *
   *     SkRect devRect = matrix.mapRect(localRect);
   *     if (fIsBW && doAA) {
   *         // check that the rect really needs aa, or is it close enought to
   *         // integer boundaries that we can just treat it as a BW rect?
   *         if (nearly_integral(devRect.fLeft) && nearly_integral(devRect.fTop) &&
   *             nearly_integral(devRect.fRight) && nearly_integral(devRect.fBottom)) {
   *             doAA = false;
   *         }
   *     }
   *
   *     if (fIsBW && !doAA) {
   *         (void)fBW.op(devRect.round(), (SkRegion::Op) op);
   *     } else {
   *         if (fIsBW) {
   *             this->convertToAA();
   *         }
   *         (void)fAA.op(devRect, op, doAA);
   *     }
   *     return this->updateCacheAndReturnNonEmpty();
   * }
   * ```
   */
  public fun op(
    localRect: SkRect,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkRRect& rrect, const SkMatrix& matrix, SkClipOp op, bool doAA) {
   *     return this->op(SkPath::RRect(rrect), matrix, op, doAA);
   * }
   * ```
   */
  public fun op(
    rrect: SkRRect,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkPath& path, const SkMatrix& matrix, SkClipOp op, bool doAA) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     SkPath devPath = path.makeTransform(matrix);
   *
   *     // Since op is either intersect or difference, the clip is always shrinking; that means we can
   *     // always use our current bounds as the limiting factor for region/aaclip operations.
   *     if (this->isRect() && op == SkClipOp::kIntersect) {
   *         // However, in the relatively common case of intersecting a new path with a rectangular
   *         // clip, it's faster to convert the path into a region/aa-mask in place than evaluate the
   *         // actual intersection. See skbug.com/40043482
   *         if (doAA && fIsBW) {
   *             this->convertToAA();
   *         }
   *         if (fIsBW) {
   *             fBW.setPath(devPath, SkRegion(this->getBounds()));
   *         } else {
   *             fAA.setPath(devPath, this->getBounds(), doAA);
   *         }
   *         return this->updateCacheAndReturnNonEmpty();
   *     } else {
   *         return this->op(SkRasterClip(devPath, this->getBounds(), doAA), op);
   *     }
   * }
   * ```
   */
  public fun op(
    path: SkPath,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(sk_sp<SkShader> sh) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     if (!fShader) {
   *         fShader = sh;
   *     } else {
   *         fShader = SkShaders::Blend(SkBlendMode::kSrcIn, sh, fShader);
   *     }
   *     return !this->isEmpty();
   * }
   * ```
   */
  public fun op(sh: SkSp<SkShader>): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterClip::translate(int dx, int dy, SkRasterClip* dst) const {
   *     if (nullptr == dst) {
   *         return;
   *     }
   *
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     if (this->isEmpty()) {
   *         dst->setEmpty();
   *         return;
   *     }
   *     if (0 == (dx | dy)) {
   *         *dst = *this;
   *         return;
   *     }
   *
   *     dst->fIsBW = fIsBW;
   *     if (fIsBW) {
   *         fBW.translate(dx, dy, &dst->fBW);
   *         dst->fAA.setEmpty();
   *     } else {
   *         fAA.translate(dx, dy, &dst->fAA);
   *         dst->fBW.setEmpty();
   *     }
   *     dst->updateCacheAndReturnNonEmpty();
   * }
   * ```
   */
  public fun translate(
    dx: Int,
    dy: Int,
    dst: SkRasterClip?,
  ) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickContains(const SkIRect& rect) const {
   *         return fIsBW ? fBW.quickContains(rect) : fAA.quickContains(rect);
   *     }
   * ```
   */
  public fun quickContains(rect: SkIRect): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickReject(const SkIRect& rect) const {
   *         return !SkIRect::Intersects(this->getBounds(), rect);
   *     }
   * ```
   */
  public fun quickReject(rect: SkIRect): Boolean {
    TODO("Implement quickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterClip::validate() const {
   *     // can't ever assert that fBW is empty, since we may have called forceGetBW
   *     if (fIsBW) {
   *         SkASSERT(fAA.isEmpty());
   *     }
   *
   *     SkRegionPriv::Validate(fBW);
   *     fAA.validate();
   *
   *     SkASSERT(this->computeIsEmpty() == fIsEmpty);
   *     SkASSERT(this->computeIsRect() == fIsRect);
   * }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> clipShader() const { return fShader; }
   * ```
   */
  public fun clipShader(): SkSp<SkShader> {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeIsEmpty() const {
   *         return fIsBW ? fBW.isEmpty() : fAA.isEmpty();
   *     }
   * ```
   */
  private fun computeIsEmpty(): Boolean {
    TODO("Implement computeIsEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeIsRect() const {
   *         return fIsBW ? fBW.isRect() : fAA.isRect();
   *     }
   * ```
   */
  private fun computeIsRect(): Boolean {
    TODO("Implement computeIsRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool updateCacheAndReturnNonEmpty(bool detectAARect = true) {
   *         fIsEmpty = this->computeIsEmpty();
   *
   *         // detect that our computed AA is really just a (hard-edged) rect
   *         if (detectAARect && !fIsEmpty && !fIsBW && fAA.isRect()) {
   *             fBW.setRect(fAA.getBounds());
   *             fAA.setEmpty(); // don't need this anymore
   *             fIsBW = true;
   *         }
   *
   *         fIsRect = this->computeIsRect();
   *         return !fIsEmpty;
   *     }
   * ```
   */
  private fun updateCacheAndReturnNonEmpty(detectAARect: Boolean = true): Boolean {
    TODO("Implement updateCacheAndReturnNonEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRasterClip::convertToAA() {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *
   *     SkASSERT(fIsBW);
   *     fAA.setRegion(fBW);
   *     fIsBW = false;
   *
   *     // since we are being explicitly asked to convert-to-aa, we pass false so we don't "optimize"
   *     // ourselves back to BW.
   *     (void)this->updateCacheAndReturnNonEmpty(false);
   * }
   * ```
   */
  private fun convertToAA() {
    TODO("Implement convertToAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRasterClip::op(const SkRasterClip& clip, SkClipOp op) {
   *     AUTO_RASTERCLIP_VALIDATE(*this);
   *     clip.validate();
   *
   *     if (this->isBW() && clip.isBW()) {
   *         (void)fBW.op(clip.fBW, (SkRegion::Op) op);
   *     } else {
   *         SkAAClip tmp;
   *         const SkAAClip* other;
   *
   *         if (this->isBW()) {
   *             this->convertToAA();
   *         }
   *         if (clip.isBW()) {
   *             tmp.setRegion(clip.bwRgn());
   *             other = &tmp;
   *         } else {
   *             other = &clip.aaRgn();
   *         }
   *         (void)fAA.op(*other, op);
   *     }
   *     return this->updateCacheAndReturnNonEmpty();
   * }
   * ```
   */
  private fun op(clip: SkRasterClip, op: SkClipOp): Boolean {
    TODO("Implement op")
  }
}
