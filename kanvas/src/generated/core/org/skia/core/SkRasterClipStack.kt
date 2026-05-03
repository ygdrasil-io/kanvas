package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkNoncopyable
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
 * class SkRasterClipStack : SkNoncopyable {
 * public:
 *     SkRasterClipStack(int width, int height)
 *             : fRootBounds(SkIRect::MakeWH(width, height))
 *             , fDisableAA(SkScan::PathRequiresTiling(fRootBounds)) {
 *         fStack.emplace_back(SkRasterClip(fRootBounds));
 *         SkASSERT(fStack.count() == 1);
 *     }
 *
 *     void setNewSize(int w, int h) {
 *         fRootBounds.setXYWH(0, 0, w, h);
 *
 *         SkASSERT(fStack.count() == 1);
 *         Rec& rec = fStack.back();
 *         SkASSERT(rec.fDeferredCount == 0);
 *         rec.fRC.setRect(fRootBounds);
 *     }
 *
 *     const SkRasterClip& rc() const { return fStack.back().fRC; }
 *
 *     void save() {
 *         SkDEBUGCODE(fCounter += 1);
 *         SkASSERT(fStack.back().fDeferredCount >= 0);
 *         fStack.back().fDeferredCount += 1;
 *     }
 *
 *     void restore() {
 *         SkDEBUGCODE(fCounter -= 1);
 *         SkASSERT(fCounter >= 0);
 *
 *         if (--fStack.back().fDeferredCount < 0) {
 *             SkASSERT(fStack.back().fDeferredCount == -1);
 *             SkASSERT(fStack.count() > 1);
 *             fStack.pop_back();
 *         }
 *     }
 *
 *     void clipRect(const SkMatrix& ctm, const SkRect& rect, SkClipOp op, bool aa) {
 *         this->writable_rc().op(rect, ctm, op, this->finalAA(aa));
 *         this->validate();
 *     }
 *
 *     void clipRRect(const SkMatrix& ctm, const SkRRect& rrect, SkClipOp op, bool aa) {
 *         this->writable_rc().op(rrect, ctm, op, this->finalAA(aa));
 *         this->validate();
 *     }
 *
 *     void clipPath(const SkMatrix& ctm, const SkPath& path, SkClipOp op, bool aa) {
 *         this->writable_rc().op(path, ctm, op, this->finalAA(aa));
 *         this->validate();
 *     }
 *
 *     void clipShader(sk_sp<SkShader> sh) {
 *         this->writable_rc().op(std::move(sh));
 *         this->validate();
 *     }
 *
 *     void clipRegion(const SkRegion& rgn, SkClipOp op) {
 *         this->writable_rc().op(rgn, op);
 *         this->validate();
 *     }
 *
 *     void replaceClip(const SkIRect& rect) {
 *         SkIRect devRect = rect;
 *         if (!devRect.intersect(fRootBounds)) {
 *             this->writable_rc().setEmpty();
 *         } else {
 *             this->writable_rc().setRect(devRect);
 *         }
 *     }
 *
 *     void validate() const {
 * #ifdef SK_DEBUG
 *         const SkRasterClip& clip = this->rc();
 *         if (fRootBounds.isEmpty()) {
 *             SkASSERT(clip.isEmpty());
 *         } else if (!clip.isEmpty()) {
 *             SkASSERT(fRootBounds.contains(clip.getBounds()));
 *         }
 * #endif
 *     }
 *
 * private:
 *     struct Rec {
 *         SkRasterClip fRC;
 *         int          fDeferredCount; // 0 for a "normal" entry
 *
 *         Rec(const SkRasterClip& rc) : fRC(rc), fDeferredCount(0) {}
 *     };
 *
 *     SkTBlockList<Rec, 16> fStack;
 *     SkIRect fRootBounds;
 *     bool fDisableAA;
 *     SkDEBUGCODE(int fCounter = 0;)
 *
 *     SkRasterClip& writable_rc() {
 *         SkASSERT(fStack.back().fDeferredCount >= 0);
 *         if (fStack.back().fDeferredCount > 0) {
 *             fStack.back().fDeferredCount -= 1;
 *             fStack.emplace_back(fStack.back().fRC);
 *         }
 *         return fStack.back().fRC;
 *     }
 *
 *     bool finalAA(bool aa) const { return aa && !fDisableAA; }
 * }
 * ```
 */
public open class SkRasterClipStack public constructor(
  width: Int,
  height: Int,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkTBlockList<Rec, 16> fStack
   * ```
   */
  private var fStack: SkTBlockList16<undefined.Rec> = TODO("Initialize fStack")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fRootBounds
   * ```
   */
  private var fRootBounds: SkIRect = TODO("Initialize fRootBounds")

  /**
   * C++ original:
   * ```cpp
   * bool fDisableAA
   * ```
   */
  private var fDisableAA: Boolean = TODO("Initialize fDisableAA")

  /**
   * C++ original:
   * ```cpp
   * void setNewSize(int w, int h) {
   *         fRootBounds.setXYWH(0, 0, w, h);
   *
   *         SkASSERT(fStack.count() == 1);
   *         Rec& rec = fStack.back();
   *         SkASSERT(rec.fDeferredCount == 0);
   *         rec.fRC.setRect(fRootBounds);
   *     }
   * ```
   */
  public fun setNewSize(w: Int, h: Int) {
    TODO("Implement setNewSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRasterClip& rc() const { return fStack.back().fRC; }
   * ```
   */
  public fun rc(): SkRasterClip {
    TODO("Implement rc")
  }

  /**
   * C++ original:
   * ```cpp
   * void save() {
   *         SkDEBUGCODE(fCounter += 1);
   *         SkASSERT(fStack.back().fDeferredCount >= 0);
   *         fStack.back().fDeferredCount += 1;
   *     }
   * ```
   */
  public fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void restore() {
   *         SkDEBUGCODE(fCounter -= 1);
   *         SkASSERT(fCounter >= 0);
   *
   *         if (--fStack.back().fDeferredCount < 0) {
   *             SkASSERT(fStack.back().fDeferredCount == -1);
   *             SkASSERT(fStack.count() > 1);
   *             fStack.pop_back();
   *         }
   *     }
   * ```
   */
  public fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRect(const SkMatrix& ctm, const SkRect& rect, SkClipOp op, bool aa) {
   *         this->writable_rc().op(rect, ctm, op, this->finalAA(aa));
   *         this->validate();
   *     }
   * ```
   */
  public fun clipRect(
    ctm: SkMatrix,
    rect: SkRect,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRRect(const SkMatrix& ctm, const SkRRect& rrect, SkClipOp op, bool aa) {
   *         this->writable_rc().op(rrect, ctm, op, this->finalAA(aa));
   *         this->validate();
   *     }
   * ```
   */
  public fun clipRRect(
    ctm: SkMatrix,
    rrect: SkRRect,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipPath(const SkMatrix& ctm, const SkPath& path, SkClipOp op, bool aa) {
   *         this->writable_rc().op(path, ctm, op, this->finalAA(aa));
   *         this->validate();
   *     }
   * ```
   */
  public fun clipPath(
    ctm: SkMatrix,
    path: SkPath,
    op: SkClipOp,
    aa: Boolean,
  ) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipShader(sk_sp<SkShader> sh) {
   *         this->writable_rc().op(std::move(sh));
   *         this->validate();
   *     }
   * ```
   */
  public fun clipShader(sh: SkSp<SkShader>) {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRegion(const SkRegion& rgn, SkClipOp op) {
   *         this->writable_rc().op(rgn, op);
   *         this->validate();
   *     }
   * ```
   */
  public fun clipRegion(rgn: SkRegion, op: SkClipOp) {
    TODO("Implement clipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void replaceClip(const SkIRect& rect) {
   *         SkIRect devRect = rect;
   *         if (!devRect.intersect(fRootBounds)) {
   *             this->writable_rc().setEmpty();
   *         } else {
   *             this->writable_rc().setRect(devRect);
   *         }
   *     }
   * ```
   */
  public fun replaceClip(rect: SkIRect) {
    TODO("Implement replaceClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   * #ifdef SK_DEBUG
   *         const SkRasterClip& clip = this->rc();
   *         if (fRootBounds.isEmpty()) {
   *             SkASSERT(clip.isEmpty());
   *         } else if (!clip.isEmpty()) {
   *             SkASSERT(fRootBounds.contains(clip.getBounds()));
   *         }
   * #endif
   *     }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterClip& writable_rc() {
   *         SkASSERT(fStack.back().fDeferredCount >= 0);
   *         if (fStack.back().fDeferredCount > 0) {
   *             fStack.back().fDeferredCount -= 1;
   *             fStack.emplace_back(fStack.back().fRC);
   *         }
   *         return fStack.back().fRC;
   *     }
   * ```
   */
  private fun writableRc(): SkRasterClip {
    TODO("Implement writableRc")
  }

  /**
   * C++ original:
   * ```cpp
   * bool finalAA(bool aa) const { return aa && !fDisableAA; }
   * ```
   */
  private fun finalAA(aa: Boolean): Boolean {
    TODO("Implement finalAA")
  }

  public open class Rec public constructor(
    public var fRC: SkRasterClip,
    public var fDeferredCount: Int,
  ) {
    public constructor(rc: SkRasterClip) : this() {
      TODO("Implement constructor")
    }
  }
}
