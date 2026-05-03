package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPathEffect : public SkFlattenable {
 * public:
 *     /**
 *      *  Returns a patheffect that apples each effect (first and second) to the original path,
 *      *  and returns a path with the sum of these.
 *      *
 *      *  result = first(path) + second(path)
 *      *
 *      */
 *     static sk_sp<SkPathEffect> MakeSum(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second);
 *
 *     /**
 *      *  Returns a patheffect that applies the inner effect to the path, and then applies the
 *      *  outer effect to the result of the inner's.
 *      *
 *      *  result = outer(inner(path))
 *      */
 *     static sk_sp<SkPathEffect> MakeCompose(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner);
 *
 *     static SkFlattenable::Type GetFlattenableType() {
 *         return kSkPathEffect_Type;
 *     }
 *
 *     /**
 *      *  Given a src path (input) and a stroke-rec (input and output), apply
 *      *  this effect to the src path, returning the new path in dst, and return
 *      *  true. If this effect cannot be applied, return false and ignore dst
 *      *  and stroke-rec.
 *      *
 *      *  The stroke-rec specifies the initial request for stroking (if any).
 *      *  The effect can treat this as input only, or it can choose to change
 *      *  the rec as well. For example, the effect can decide to change the
 *      *  stroke's width or join, or the effect can change the rec from stroke
 *      *  to fill (or fill to stroke) in addition to returning a new (dst) path.
 *      *
 *      *  If this method returns true, the caller will apply (as needed) the
 *      *  resulting stroke-rec to dst and then draw.
 *      */
 *     bool filterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect* cullR,
 *                     const SkMatrix& ctm) const;
 *     bool filterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*) const;
 *
 *     /** True if this path effect requires a valid CTM */
 *     bool needsCTM() const;
 *
 *     static sk_sp<SkPathEffect> Deserialize(const void* data, size_t size,
 *                                            const SkDeserialProcs* procs = nullptr);
 *
 * #ifdef SK_SUPPORT_MUTABLE_PATHEFFECT
 *     bool filterPath(SkPath* dst, const SkPath& src, SkStrokeRec*, const SkRect* cullR) const;
 *
 *     /** Version of filterPath that can be called when the CTM is known. */
 *     bool filterPath(SkPath* dst, const SkPath& src, SkStrokeRec*, const SkRect* cullR,
 *                     const SkMatrix& ctm) const;
 * #endif
 *
 * private:
 *     SkPathEffect() = default;
 *     friend class SkPathEffectBase;
 *
 *     using INHERITED = SkFlattenable;
 * }
 * ```
 */
public open class SkPathEffect public constructor() : SkFlattenable() {
  /**
   * C++ original:
   * ```cpp
   * bool SkPathEffect::filterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
   *                               const SkRect* bounds, const SkMatrix& ctm) const {
   *     return as_PEB(this)->onFilterPath(dst, src, rec, bounds, ctm);
   * }
   * ```
   */
  public fun filterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
    cullR: SkRect?,
    ctm: SkMatrix,
  ): Boolean {
    TODO("Implement filterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathEffect::filterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec) const {
   *     return this->filterPath(dst, src, rec, nullptr, SkMatrix::I());
   * }
   * ```
   */
  public fun filterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
  ): Boolean {
    TODO("Implement filterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathEffect::needsCTM() const {
   *     return as_PEB(this)->onNeedsCTM();
   * }
   * ```
   */
  public fun needsCTM(): Boolean {
    TODO("Implement needsCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathEffect::filterPath(SkPath* dst, const SkPath& src, SkStrokeRec* rec,
   *                               const SkRect* bounds) const {
   *     return this->filterPath(dst, src, rec, bounds, SkMatrix::I());
   * }
   * ```
   */
  public fun filterPath(
    dst: SkPath?,
    src: SkPath,
    rec: SkStrokeRec?,
    bounds: SkRect?,
  ): Boolean {
    TODO("Implement filterPath")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkPathEffect::MakeSum(sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second) {
     *     return SkSumPathEffect::Make(std::move(first), std::move(second));
     * }
     * ```
     */
    public fun makeSum(first: SkSp<SkPathEffect>, second: SkSp<SkPathEffect>): Int {
      TODO("Implement makeSum")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkPathEffect::MakeCompose(sk_sp<SkPathEffect> outer,
     *                                               sk_sp<SkPathEffect> inner) {
     *     return SkComposePathEffect::Make(std::move(outer), std::move(inner));
     * }
     * ```
     */
    public fun makeCompose(outer: SkSp<SkPathEffect>, `inner`: SkSp<SkPathEffect>): Int {
      TODO("Implement makeCompose")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() {
     *         return kSkPathEffect_Type;
     *     }
     * ```
     */
    public fun getFlattenableType(): Int {
      TODO("Implement getFlattenableType")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkPathEffect::Deserialize(const void* data, size_t size,
     *                                               const SkDeserialProcs* procs) {
     *     return sk_sp<SkPathEffect>(static_cast<SkPathEffect*>(
     *                                SkFlattenable::Deserialize(
     *                                kSkPathEffect_Type, data, size, procs).release()));
     * }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = null,
    ): Int {
      TODO("Implement deserialize")
    }
  }
}

public typealias SkPathEffectBaseINHERITED = SkPathEffect
