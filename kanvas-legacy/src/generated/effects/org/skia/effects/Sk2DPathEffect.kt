package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkPath
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class Sk2DPathEffect : public SkPathEffectBase {
 * public:
 *     Sk2DPathEffect(const SkMatrix& mat) : fMatrix(mat) {
 *         // Calling invert will set the type mask on both matrices, making them thread safe.
 *         fMatrixIsInvertible = fMatrix.invert(&fInverse);
 *     }
 *
 * protected:
 *     /** New virtual, to be overridden by subclasses.
 *         This is called once from filterPath, and provides the
 *         uv parameter bounds for the path. Subsequent calls to
 *         next() will receive u and v values within these bounds,
 *         and then a call to end() will signal the end of processing.
 *     */
 *     virtual void begin(const SkIRect& uvBounds, SkPathBuilder* dst) const {}
 *     virtual void next(const SkPoint& loc, int u, int v, SkPathBuilder* dst) const {}
 *     virtual void end(SkPathBuilder* dst) const {}
 *
 *     /** Low-level virtual called per span of locations in the u-direction.
 *         The default implementation calls next() repeatedly with each
 *         location.
 *     */
 *     virtual void nextSpan(int x, int y, int ucount, SkPathBuilder* builder) const {
 *         if (!fMatrixIsInvertible) {
 *             return;
 *         }
 *     #if defined(SK_BUILD_FOR_FUZZER)
 *         if (ucount > 100) {
 *             return;
 *         }
 *     #endif
 *
 *         const SkMatrix& mat = this->getMatrix();
 *         SkPoint src, dst;
 *
 *         src.set(SkIntToScalar(x) + SK_ScalarHalf, SkIntToScalar(y) + SK_ScalarHalf);
 *         do {
 *             dst = mat.mapPoint(src);
 *             this->next(dst, x++, y, builder);
 *             src.fX += SK_Scalar1;
 *         } while (--ucount > 0);
 *     }
 *
 *     const SkMatrix& getMatrix() const { return fMatrix; }
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeMatrix(fMatrix);
 *     }
 *
 *     bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
 *                       const SkRect* cullRect, const SkMatrix&) const override {
 *         if (!fMatrixIsInvertible) {
 *             return false;
 *         }
 *
 *         SkPath tmp = src.makeTransform(fInverse);
 *         SkIRect ir = tmp.getBounds().round();
 *         if (!ir.isEmpty()) {
 *             this->begin(ir, dst);
 *
 *             SkRegion rgn;
 *             rgn.setPath(tmp, SkRegion(ir));
 *             SkRegion::Iterator iter(rgn);
 *             for (; !iter.done(); iter.next()) {
 *                 const SkIRect& rect = iter.rect();
 * #if defined(SK_BUILD_FOR_FUZZER)
 *                 if (rect.height() > 100) {
 *                     continue;
 *                 }
 * #endif
 *                 for (int y = rect.fTop; y < rect.fBottom; ++y) {
 *                     this->nextSpan(rect.fLeft, y, rect.width(), dst);
 *                 }
 *             }
 *
 *             this->end(dst);
 *         }
 *         return true;
 *     }
 *
 * private:
 *     SkMatrix    fMatrix, fInverse;
 *     bool        fMatrixIsInvertible;
 *
 *     // For simplicity, assume fast bounds cannot be computed
 *     bool computeFastBounds(SkRect*) const override { return false; }
 *
 *     friend class Sk2DPathEffectBlitter;
 * }
 * ```
 */
public open class Sk2DPathEffect public constructor(
  mat: SkMatrix,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fMatrix
   * ```
   */
  private var fMatrix: SkMatrix = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fMatrix, fInverse
   * ```
   */
  private var fInverse: SkMatrix = TODO("Initialize fInverse")

  /**
   * C++ original:
   * ```cpp
   * bool        fMatrixIsInvertible
   * ```
   */
  private var fMatrixIsInvertible: Boolean = TODO("Initialize fMatrixIsInvertible")

  /**
   * C++ original:
   * ```cpp
   * virtual void begin(const SkIRect& uvBounds, SkPathBuilder* dst) const {}
   * ```
   */
  protected open fun begin(uvBounds: SkIRect, dst: SkPathBuilder?) {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void next(const SkPoint& loc, int u, int v, SkPathBuilder* dst) const {}
   * ```
   */
  protected open fun next(
    loc: SkPoint,
    u: Int,
    v: Int,
    dst: SkPathBuilder?,
  ) {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void end(SkPathBuilder* dst) const {}
   * ```
   */
  protected open fun end(dst: SkPathBuilder?) {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void nextSpan(int x, int y, int ucount, SkPathBuilder* builder) const {
   *         if (!fMatrixIsInvertible) {
   *             return;
   *         }
   *     #if defined(SK_BUILD_FOR_FUZZER)
   *         if (ucount > 100) {
   *             return;
   *         }
   *     #endif
   *
   *         const SkMatrix& mat = this->getMatrix();
   *         SkPoint src, dst;
   *
   *         src.set(SkIntToScalar(x) + SK_ScalarHalf, SkIntToScalar(y) + SK_ScalarHalf);
   *         do {
   *             dst = mat.mapPoint(src);
   *             this->next(dst, x++, y, builder);
   *             src.fX += SK_Scalar1;
   *         } while (--ucount > 0);
   *     }
   * ```
   */
  protected open fun nextSpan(
    x: Int,
    y: Int,
    ucount: Int,
    builder: SkPathBuilder?,
  ) {
    TODO("Implement nextSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& getMatrix() const { return fMatrix; }
   * ```
   */
  protected fun getMatrix(): SkMatrix {
    TODO("Implement getMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeMatrix(fMatrix);
   *     }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
   *                       const SkRect* cullRect, const SkMatrix&) const override {
   *         if (!fMatrixIsInvertible) {
   *             return false;
   *         }
   *
   *         SkPath tmp = src.makeTransform(fInverse);
   *         SkIRect ir = tmp.getBounds().round();
   *         if (!ir.isEmpty()) {
   *             this->begin(ir, dst);
   *
   *             SkRegion rgn;
   *             rgn.setPath(tmp, SkRegion(ir));
   *             SkRegion::Iterator iter(rgn);
   *             for (; !iter.done(); iter.next()) {
   *                 const SkIRect& rect = iter.rect();
   * #if defined(SK_BUILD_FOR_FUZZER)
   *                 if (rect.height() > 100) {
   *                     continue;
   *                 }
   * #endif
   *                 for (int y = rect.fTop; y < rect.fBottom; ++y) {
   *                     this->nextSpan(rect.fLeft, y, rect.width(), dst);
   *                 }
   *             }
   *
   *             this->end(dst);
   *         }
   *         return true;
   *     }
   * ```
   */
  protected override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
    cullRect: SkRect?,
    param4: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect*) const override { return false; }
   * ```
   */
  public override fun computeFastBounds(param0: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }
}

public typealias SkLine2DPathEffectImplINHERITED = Sk2DPathEffect

public typealias SkPath2DPathEffectImplINHERITED = Sk2DPathEffect
