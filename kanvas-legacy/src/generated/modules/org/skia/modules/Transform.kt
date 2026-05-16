package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp

public typealias ConcatINHERITED = Transform

public typealias InverseINHERITED = Transform

/**
 * C++ original:
 * ```cpp
 * class Transform : public Node {
 * public:
 *     // Compose T' = A x B
 *     static sk_sp<Transform> MakeConcat(sk_sp<Transform> a, sk_sp<Transform> b);
 *
 *     // T' = Inv(T)
 *     static sk_sp<Transform> MakeInverse(sk_sp<Transform> t);
 *
 * protected:
 *     Transform();
 *
 *     virtual bool is44() const = 0;
 *
 *     virtual SkMatrix asMatrix() const = 0;
 *     virtual SkM44    asM44   () const = 0;
 *
 * private:
 *     friend class TransformPriv;
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class Transform public constructor() : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * virtual bool is44() const = 0
   * ```
   */
  protected abstract fun is44(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual SkMatrix asMatrix() const = 0
   * ```
   */
  protected abstract fun asMatrix(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkM44    asM44   () const = 0
   * ```
   */
  protected abstract fun asM44(): Int

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Transform> Transform::MakeConcat(sk_sp<Transform> a, sk_sp<Transform> b) {
     *     if (!a) {
     *         return b;
     *     }
     *
     *     if (!b) {
     *         return a;
     *     }
     *
     *     return TransformPriv::Is44(a) || TransformPriv::Is44(b)
     *         ? sk_sp<Transform>(new Concat<SkM44   >(std::move(a), std::move(b)))
     *         : sk_sp<Transform>(new Concat<SkMatrix>(std::move(a), std::move(b)));
     * }
     * ```
     */
    public fun makeConcat(a: SkSp<Transform>, b: SkSp<Transform>): Int {
      TODO("Implement makeConcat")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Transform> Transform::MakeInverse(sk_sp<Transform> t) {
     *     if (!t) {
     *         return nullptr;
     *     }
     *
     *     return TransformPriv::Is44(t)
     *         ? sk_sp<Transform>(new Inverse<SkM44   >(std::move(t)))
     *         : sk_sp<Transform>(new Inverse<SkMatrix>(std::move(t)));
     * }
     * ```
     */
    public fun makeInverse(t: SkSp<Transform>): Int {
      TODO("Implement makeInverse")
    }
  }
}

public typealias MatrixINHERITED = Transform
