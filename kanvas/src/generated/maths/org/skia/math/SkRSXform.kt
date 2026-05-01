package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkRSXform {
 *     static SkRSXform Make(SkScalar scos, SkScalar ssin, SkScalar tx, SkScalar ty) {
 *         SkRSXform xform = { scos, ssin, tx, ty };
 *         return xform;
 *     }
 *
 *     /*
 *      *  Initialize a new xform based on the scale, rotation (in radians), final tx,ty location
 *      *  and anchor-point ax,ay within the src quad.
 *      *
 *      *  Note: the anchor point is not normalized (e.g. 0...1) but is in pixels of the src image.
 *      */
 *     static SkRSXform MakeFromRadians(SkScalar scale, SkScalar radians, SkScalar tx, SkScalar ty,
 *                                      SkScalar ax, SkScalar ay) {
 *         const SkScalar s = SkScalarSin(radians) * scale;
 *         const SkScalar c = SkScalarCos(radians) * scale;
 *         return Make(c, s, tx + -c * ax + s * ay, ty + -s * ax - c * ay);
 *     }
 *
 *     SkScalar fSCos;
 *     SkScalar fSSin;
 *     SkScalar fTx;
 *     SkScalar fTy;
 *
 *     bool rectStaysRect() const {
 *         return 0 == fSCos || 0 == fSSin;
 *     }
 *
 *     void setIdentity() {
 *         fSCos = 1;
 *         fSSin = fTx = fTy = 0;
 *     }
 *
 *     void set(SkScalar scos, SkScalar ssin, SkScalar tx, SkScalar ty) {
 *         fSCos = scos;
 *         fSSin = ssin;
 *         fTx = tx;
 *         fTy = ty;
 *     }
 *
 *     void toQuad(SkScalar width, SkScalar height, SkPoint quad[4]) const;
 *     void toQuad(const SkSize& size, SkPoint quad[4]) const {
 *         this->toQuad(size.width(), size.height(), quad);
 *     }
 *     void toTriStrip(SkScalar width, SkScalar height, SkPoint strip[4]) const;
 * }
 * ```
 */
public data class SkRSXform public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSCos
   * ```
   */
  public var fSCos: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSSin
   * ```
   */
  public var fSSin: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fTx
   * ```
   */
  public var fTx: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fTy
   * ```
   */
  public var fTy: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool rectStaysRect() const {
   *         return 0 == fSCos || 0 == fSSin;
   *     }
   * ```
   */
  public fun rectStaysRect(): Boolean {
    return fSCos == 0 || fSSin == 0
  }

  /**
   * C++ original:
   * ```cpp
   * void setIdentity() {
   *         fSCos = 1;
   *         fSSin = fTx = fTy = 0;
   *     }
   * ```
   */
  public fun setIdentity() {
    fSCos = 1
    fSSin = 0
    fTx = 0
    fTy = 0
  }

  /**
   * C++ original:
   * ```cpp
   * void set(SkScalar scos, SkScalar ssin, SkScalar tx, SkScalar ty) {
   *         fSCos = scos;
   *         fSSin = ssin;
   *         fTx = tx;
   *         fTy = ty;
   *     }
   * ```
   */
  public fun `set`(
    scos: SkScalar,
    ssin: SkScalar,
    tx: SkScalar,
    ty: SkScalar,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRSXform::toQuad(SkScalar width, SkScalar height, SkPoint quad[4]) const {
   * #if 0
   *     // This is the slow way, but it documents what we're doing
   *     quad[0].set(0, 0);
   *     quad[1].set(width, 0);
   *     quad[2].set(width, height);
   *     quad[3].set(0, height);
   *     SkMatrix m;
   *     m.setRSXform(*this).mapPoints({quad, 4});
   * #else
   *     const SkScalar m00 = fSCos;
   *     const SkScalar m01 = -fSSin;
   *     const SkScalar m02 = fTx;
   *     const SkScalar m10 = -m01;
   *     const SkScalar m11 = m00;
   *     const SkScalar m12 = fTy;
   *
   *     quad[0].set(m02, m12);
   *     quad[1].set(m00 * width + m02, m10 * width + m12);
   *     quad[2].set(m00 * width + m01 * height + m02, m10 * width + m11 * height + m12);
   *     quad[3].set(m01 * height + m02, m11 * height + m12);
   * #endif
   * }
   * ```
   */
  public fun toQuad(
    width: SkScalar,
    height: SkScalar,
    quad: Array<SkPoint>,
  ) {
    quad[0].set(fSCos * width + fTx, fSSin * height + fTy)
    quad[1].set(-fSSin * width + fTx, fSCos * height + fTy)
    quad[2].set(-fSCos * width + fTx, -fSSin * height + fTy)
    quad[3].set(fSSin * width + fTx, -fSCos * height + fTy)
  }

  /**
   * C++ original:
   * ```cpp
   * void toQuad(const SkSize& size, SkPoint quad[4]) const {
   *         this->toQuad(size.width(), size.height(), quad);
   *     }
   * ```
   */
  public fun toQuad(size: SkSize, quad: Array<SkPoint>) {
    TODO("Implement toQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRSXform::toTriStrip(SkScalar width, SkScalar height, SkPoint strip[4]) const {
   *     const SkScalar m00 = fSCos;
   *     const SkScalar m01 = -fSSin;
   *     const SkScalar m02 = fTx;
   *     const SkScalar m10 = -m01;
   *     const SkScalar m11 = m00;
   *     const SkScalar m12 = fTy;
   *
   *     strip[0].set(m02, m12);
   *     strip[1].set(m01 * height + m02, m11 * height + m12);
   *     strip[2].set(m00 * width + m02, m10 * width + m12);
   *     strip[3].set(m00 * width + m01 * height + m02, m10 * width + m11 * height + m12);
   * }
   * ```
   */
  public fun toTriStrip(
    width: SkScalar,
    height: SkScalar,
    strip: Array<SkPoint>,
  ) {
    strip[0] = SkPoint.Companion.make(0f, 0f)
    strip[1] = SkPoint.Companion.make(width, 0f)
    strip[2] = SkPoint.Companion.make(0f, height)
    strip[3] = SkPoint.Companion.make(width, height)
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkRSXform Make(SkScalar scos, SkScalar ssin, SkScalar tx, SkScalar ty) {
     *         SkRSXform xform = { scos, ssin, tx, ty };
     *         return xform;
     *     }
     * ```
     */
    public fun make(
      scos: SkScalar,
      ssin: SkScalar,
      tx: SkScalar,
      ty: SkScalar,
    ): SkRSXform {
      return Companion.make(scos, ssin, tx, ty)
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRSXform MakeFromRadians(SkScalar scale, SkScalar radians, SkScalar tx, SkScalar ty,
     *                                      SkScalar ax, SkScalar ay) {
     *         const SkScalar s = SkScalarSin(radians) * scale;
     *         const SkScalar c = SkScalarCos(radians) * scale;
     *         return Make(c, s, tx + -c * ax + s * ay, ty + -s * ax - c * ay);
     *     }
     * ```
     */
    public fun makeFromRadians(
      scale: SkScalar,
      radians: SkScalar,
      tx: SkScalar,
      ty: SkScalar,
      ax: SkScalar,
      ay: SkScalar,
    ): SkRSXform {
      return Companion.makeFromRadians(scale, radians, tx, ty, ax, ay)
    }
  }
}
