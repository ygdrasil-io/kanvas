package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class Concat final : public Transform {
 * public:
 *     template <typename = std::enable_if<std::is_same<T, SkMatrix>::value ||
 *                                         std::is_same<T, SkM44   >::value >>
 *     Concat(sk_sp<Transform> a, sk_sp<Transform> b)
 *         : fA(std::move(a)), fB(std::move(b)) {
 *         SkASSERT(fA);
 *         SkASSERT(fB);
 *
 *         this->observeInval(fA);
 *         this->observeInval(fB);
 *     }
 *
 *     ~Concat() override {
 *         this->unobserveInval(fA);
 *         this->unobserveInval(fB);
 *     }
 *
 * protected:
 *     SkRect onRevalidate(InvalidationController* ic, const SkMatrix& ctm) override {
 *         fA->revalidate(ic, ctm);
 *         fB->revalidate(ic, ctm);
 *
 *         fComposed.setConcat(TransformPriv::As<T>(fA),
 *                             TransformPriv::As<T>(fB));
 *         return SkRect::MakeEmpty();
 *     }
 *
 *     bool is44() const override { return std::is_same<T, SkM44>::value; }
 *
 *     SkMatrix asMatrix() const override {
 *         SkASSERT(!this->hasInval());
 *         return AsSkMatrix(fComposed);
 *     }
 *
 *     SkM44 asM44() const override {
 *         SkASSERT(!this->hasInval());
 *         return AsSkM44(fComposed);
 *     }
 *
 * private:
 *     const sk_sp<Transform> fA, fB;
 *     T                      fComposed;
 *
 *     using INHERITED = Transform;
 * }
 * ```
 */
public open class Concat<T> : Transform() {
  /**
   * C++ original:
   * ```cpp
   * T                      fComposed
   * ```
   */
  private var fComposed: T = TODO("Initialize fComposed")
}
