package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkComposePathEffect : public SkPairPathEffect {
 * public:
 *     /** Construct a pathEffect whose effect is to apply first the inner pathEffect
 *      and the the outer pathEffect (e.g. outer(inner(path)))
 *      The reference counts for outer and inner are both incremented in the constructor,
 *      and decremented in the destructor.
 *      */
 *     static sk_sp<SkPathEffect> Make(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner) {
 *         if (!outer) {
 *             return inner;
 *         }
 *         if (!inner) {
 *             return outer;
 *         }
 *         return sk_sp<SkPathEffect>(new SkComposePathEffect(outer, inner));
 *     }
 *
 *     SkComposePathEffect(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner)
 *             : INHERITED(std::move(outer), std::move(inner)) {}
 *
 *     bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec* rec,
 *                        const SkRect* cullRect, const SkMatrix& ctm) const override {
 *         SkPath          tmp;
 *         const SkPath*   ptr = &src;
 *
 *         if (fPE1->filterPath(builder, src, rec, cullRect, ctm)) {
 *             tmp = builder->detach();
 *             ptr = &tmp;
 *         }
 *         return fPE0->filterPath(builder, *ptr, rec, cullRect, ctm);
 *     }
 *
 *     SK_FLATTENABLE_HOOKS(SkComposePathEffect)
 *
 *     bool computeFastBounds(SkRect* bounds) const override {
 *         // inner (fPE1) is computed first, automatically updating bounds before computing outer.
 *         return as_PEB(fPE1)->computeFastBounds(bounds) &&
 *                as_PEB(fPE0)->computeFastBounds(bounds);
 *     }
 *
 * private:
 *     // illegal
 *     SkComposePathEffect(const SkComposePathEffect&);
 *     SkComposePathEffect& operator=(const SkComposePathEffect&);
 *     friend class SkPathEffect;
 *
 *     using INHERITED = SkPairPathEffect;
 * }
 * ```
 */
public open class SkComposePathEffect public constructor(
  outer: SkSp<SkPathEffect>,
  `inner`: SkSp<SkPathEffect>,
) : SkPairPathEffect(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkComposePathEffect(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner)
   *             : INHERITED(std::move(outer), std::move(inner)) {}
   * ```
   */
  public constructor(param0: SkComposePathEffect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec* rec,
   *                        const SkRect* cullRect, const SkMatrix& ctm) const override {
   *         SkPath          tmp;
   *         const SkPath*   ptr = &src;
   *
   *         if (fPE1->filterPath(builder, src, rec, cullRect, ctm)) {
   *             tmp = builder->detach();
   *             ptr = &tmp;
   *         }
   *         return fPE0->filterPath(builder, *ptr, rec, cullRect, ctm);
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
   *         // inner (fPE1) is computed first, automatically updating bounds before computing outer.
   *         return as_PEB(fPE1)->computeFastBounds(bounds) &&
   *                as_PEB(fPE0)->computeFastBounds(bounds);
   *     }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkComposePathEffect& operator=(const SkComposePathEffect&)
   * ```
   */
  private fun assign(param0: SkComposePathEffect) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkComposePathEffect::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkPathEffect> pe0(buffer.readPathEffect());
   *     sk_sp<SkPathEffect> pe1(buffer.readPathEffect());
   *     return SkComposePathEffect::Make(std::move(pe0), std::move(pe1));
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
     * static sk_sp<SkPathEffect> Make(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner) {
     *         if (!outer) {
     *             return inner;
     *         }
     *         if (!inner) {
     *             return outer;
     *         }
     *         return sk_sp<SkPathEffect>(new SkComposePathEffect(outer, inner));
     *     }
     * ```
     */
    public fun make(outer: SkSp<SkPathEffect>, `inner`: SkSp<SkPathEffect>): SkSp<SkPathEffect> {
      TODO("Implement make")
    }
  }
}
