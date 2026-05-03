package org.skia.effects

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class SkLine2DPathEffectImpl : public Sk2DPathEffect {
 * public:
 *     SkLine2DPathEffectImpl(SkScalar width, const SkMatrix& matrix)
 *         : Sk2DPathEffect(matrix)
 *         , fWidth(width)
 *     {
 *         SkASSERT(width >= 0);
 *     }
 *
 *     bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
 *                       const SkRect* cullRect, const SkMatrix& ctm) const override {
 *         if (this->INHERITED::onFilterPath(dst, src, rec, cullRect, ctm)) {
 *             rec->setStrokeStyle(fWidth);
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     void nextSpan(int u, int v, int ucount, SkPathBuilder* dst) const override {
 *         if (ucount > 1) {
 *             SkPoint    src[2], dstP[2];
 *
 *             src[0].set(SkIntToScalar(u) + SK_ScalarHalf, SkIntToScalar(v) + SK_ScalarHalf);
 *             src[1].set(SkIntToScalar(u+ucount) + SK_ScalarHalf, SkIntToScalar(v) + SK_ScalarHalf);
 *             this->getMatrix().mapPoints(dstP, src);
 *
 *             dst->moveTo(dstP[0]);
 *             dst->lineTo(dstP[1]);
 *         }
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         SkMatrix matrix;
 *         buffer.readMatrix(&matrix);
 *         SkScalar width = buffer.readScalar();
 *         return SkLine2DPathEffect::Make(width, matrix);
 *     }
 *
 *     void flatten(SkWriteBuffer &buffer) const override {
 *         buffer.writeMatrix(this->getMatrix());
 *         buffer.writeScalar(fWidth);
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *     const char* getTypeName() const override { return "SkLine2DPathEffect"; }
 *
 * private:
 *     SkScalar fWidth;
 *
 *     using INHERITED = Sk2DPathEffect;
 * }
 * ```
 */
public open class SkLine2DPathEffectImpl public constructor(
  width: SkScalar,
  matrix: SkMatrix,
) : Sk2DPathEffect(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWidth
   * ```
   */
  private var fWidth: SkScalar = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
   *                       const SkRect* cullRect, const SkMatrix& ctm) const override {
   *         if (this->INHERITED::onFilterPath(dst, src, rec, cullRect, ctm)) {
   *             rec->setStrokeStyle(fWidth);
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
    cullRect: SkRect?,
    ctm: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void nextSpan(int u, int v, int ucount, SkPathBuilder* dst) const override {
   *         if (ucount > 1) {
   *             SkPoint    src[2], dstP[2];
   *
   *             src[0].set(SkIntToScalar(u) + SK_ScalarHalf, SkIntToScalar(v) + SK_ScalarHalf);
   *             src[1].set(SkIntToScalar(u+ucount) + SK_ScalarHalf, SkIntToScalar(v) + SK_ScalarHalf);
   *             this->getMatrix().mapPoints(dstP, src);
   *
   *             dst->moveTo(dstP[0]);
   *             dst->lineTo(dstP[1]);
   *         }
   *     }
   * ```
   */
  public override fun nextSpan(
    u: Int,
    v: Int,
    ucount: Int,
    dst: SkPathBuilder?,
  ) {
    TODO("Implement nextSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer &buffer) const override {
   *         buffer.writeMatrix(this->getMatrix());
   *         buffer.writeScalar(fWidth);
   *     }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return CreateProc; }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "SkLine2DPathEffect"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
     *         SkMatrix matrix;
     *         buffer.readMatrix(&matrix);
     *         SkScalar width = buffer.readScalar();
     *         return SkLine2DPathEffect::Make(width, matrix);
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
