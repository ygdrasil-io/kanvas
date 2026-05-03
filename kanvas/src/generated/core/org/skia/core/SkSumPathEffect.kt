package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkSumPathEffect : public SkPairPathEffect {
 * public:
 *     /** Construct a pathEffect whose effect is to apply two effects, in sequence.
 *      (e.g. first(path) + second(path))
 *      The reference counts for first and second are both incremented in the constructor,
 *      and decremented in the destructor.
 *      */
 *     static sk_sp<SkPathEffect> Make(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second) {
 *         if (!first) {
 *             return second;
 *         }
 *         if (!second) {
 *             return first;
 *         }
 *         return sk_sp<SkPathEffect>(new SkSumPathEffect(first, second));
 *     }
 *
 *     SkSumPathEffect(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second)
 *             : INHERITED(std::move(first), std::move(second)) {}
 *
 *     bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec* rec,
 *                       const SkRect* cullRect, const SkMatrix& ctm) const override {
 *         // always call both, even if the first one succeeds
 *         bool filteredFirst = fPE0->filterPath(builder, src, rec, cullRect, ctm);
 *         bool filteredSecond = fPE1->filterPath(builder, src, rec, cullRect, ctm);
 *         return filteredFirst || filteredSecond;
 *     }
 *
 *     SK_FLATTENABLE_HOOKS(SkSumPathEffect)
 *
 *     bool computeFastBounds(SkRect* bounds) const override {
 *         // Unlike Compose(), PE0 modifies the path first for Sum
 *         return as_PEB(fPE0)->computeFastBounds(bounds) &&
 *                as_PEB(fPE1)->computeFastBounds(bounds);
 *     }
 *
 * private:
 *     // illegal
 *     SkSumPathEffect(const SkSumPathEffect&);
 *     SkSumPathEffect& operator=(const SkSumPathEffect&);
 *     friend class SkPathEffect;
 *
 *     using INHERITED = SkPairPathEffect;
 * }
 * ```
 */
public open class SkSumPathEffect public constructor(
  first: SkSp<SkPathEffect>,
  second: SkSp<SkPathEffect>,
) : SkPairPathEffect(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkSumPathEffect(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second)
   *             : INHERITED(std::move(first), std::move(second)) {}
   * ```
   */
  public constructor(param0: SkSumPathEffect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec* rec,
   *                       const SkRect* cullRect, const SkMatrix& ctm) const override {
   *         // always call both, even if the first one succeeds
   *         bool filteredFirst = fPE0->filterPath(builder, src, rec, cullRect, ctm);
   *         bool filteredSecond = fPE1->filterPath(builder, src, rec, cullRect, ctm);
   *         return filteredFirst || filteredSecond;
   *     }
   * ```
   */
  public override fun onFilterPath(
    builder: SkPathBuilder?,
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
   * bool computeFastBounds(SkRect* bounds) const override {
   *         // Unlike Compose(), PE0 modifies the path first for Sum
   *         return as_PEB(fPE0)->computeFastBounds(bounds) &&
   *                as_PEB(fPE1)->computeFastBounds(bounds);
   *     }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSumPathEffect& operator=(const SkSumPathEffect&)
   * ```
   */
  private fun assign(param0: SkSumPathEffect) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkSumPathEffect::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkPathEffect> pe0(buffer.readPathEffect());
   *     sk_sp<SkPathEffect> pe1(buffer.readPathEffect());
   *     return SkSumPathEffect::Make(pe0, pe1);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkPathEffect> Make(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second) {
     *         if (!first) {
     *             return second;
     *         }
     *         if (!second) {
     *             return first;
     *         }
     *         return sk_sp<SkPathEffect>(new SkSumPathEffect(first, second));
     *     }
     * ```
     */
    public fun make(first: SkSp<SkPathEffect>, second: SkSp<SkPathEffect>): SkSp<SkPathEffect> {
      TODO("Implement make")
    }
  }
}
