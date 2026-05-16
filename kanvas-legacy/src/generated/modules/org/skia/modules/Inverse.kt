package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class Inverse final : public Transform {
 * public:
 *     template <typename = std::enable_if<std::is_same<T, SkMatrix>::value ||
 *                                         std::is_same<T, SkM44   >::value >>
 *     explicit Inverse(sk_sp<Transform> t)
 *         : fT(std::move(t)) {
 *         SkASSERT(fT);
 *
 *         this->observeInval(fT);
 *     }
 *
 *     ~Inverse() override {
 *         this->unobserveInval(fT);
 *     }
 *
 * protected:
 *     SkRect onRevalidate(InvalidationController* ic, const SkMatrix& ctm) override {
 *         fT->revalidate(ic, ctm);
 *
 *         if (!TransformPriv::As<T>(fT).invert(&fInverted)) {
 *             fInverted.setIdentity();
 *         }
 *
 *         return SkRect::MakeEmpty();
 *     }
 *
 *     bool is44() const override { return std::is_same<T, SkM44>::value; }
 *
 *     SkMatrix asMatrix() const override {
 *         SkASSERT(!this->hasInval());
 *         return AsSkMatrix(fInverted);
 *     }
 *
 *     SkM44 asM44() const override {
 *         SkASSERT(!this->hasInval());
 *         return AsSkM44(fInverted);
 *     }
 *
 * private:
 *     const sk_sp<Transform> fT;
 *     T                      fInverted;
 *
 *     using INHERITED = Transform;
 * }
 * ```
 */
public open class Inverse<T> : Transform() {
  /**
   * C++ original:
   * ```cpp
   * T                      fInverted
   * ```
   */
  private var fInverted: T = TODO("Initialize fInverted")
}
