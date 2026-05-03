package org.skia.math

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * class SK_API SkM44 {
 * public:
 *     SkM44(const SkM44& src) = default;
 *     SkM44& operator=(const SkM44& src) = default;
 *
 *     constexpr SkM44()
 *         : fMat{1, 0, 0, 0,
 *                0, 1, 0, 0,
 *                0, 0, 1, 0,
 *                0, 0, 0, 1}
 *         {}
 *
 *     SkM44(const SkM44& a, const SkM44& b) {
 *         this->setConcat(a, b);
 *     }
 *
 *     enum Uninitialized_Constructor {
 *         kUninitialized_Constructor
 *     };
 *     explicit SkM44(Uninitialized_Constructor) {}
 *
 *     enum NaN_Constructor {
 *         kNaN_Constructor
 *     };
 *     constexpr SkM44(NaN_Constructor)
 *         : fMat{SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
 *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
 *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
 *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN}
 *     {}
 *
 *     /**
 *      *  The constructor parameters are in row-major order.
 *      */
 *     constexpr SkM44(SkScalar m0, SkScalar m4, SkScalar m8,  SkScalar m12,
 *                     SkScalar m1, SkScalar m5, SkScalar m9,  SkScalar m13,
 *                     SkScalar m2, SkScalar m6, SkScalar m10, SkScalar m14,
 *                     SkScalar m3, SkScalar m7, SkScalar m11, SkScalar m15)
 *         // fMat is column-major order in memory.
 *         : fMat{m0,  m1,  m2,  m3,
 *                m4,  m5,  m6,  m7,
 *                m8,  m9,  m10, m11,
 *                m12, m13, m14, m15}
 *     {}
 *
 *     static SkM44 Rows(const SkV4& r0, const SkV4& r1, const SkV4& r2, const SkV4& r3) {
 *         SkM44 m(kUninitialized_Constructor);
 *         m.setRow(0, r0);
 *         m.setRow(1, r1);
 *         m.setRow(2, r2);
 *         m.setRow(3, r3);
 *         return m;
 *     }
 *     static SkM44 Cols(const SkV4& c0, const SkV4& c1, const SkV4& c2, const SkV4& c3) {
 *         SkM44 m(kUninitialized_Constructor);
 *         m.setCol(0, c0);
 *         m.setCol(1, c1);
 *         m.setCol(2, c2);
 *         m.setCol(3, c3);
 *         return m;
 *     }
 *
 *     static SkM44 RowMajor(const SkScalar r[16]) {
 *         return SkM44(r[ 0], r[ 1], r[ 2], r[ 3],
 *                      r[ 4], r[ 5], r[ 6], r[ 7],
 *                      r[ 8], r[ 9], r[10], r[11],
 *                      r[12], r[13], r[14], r[15]);
 *     }
 *     static SkM44 ColMajor(const SkScalar c[16]) {
 *         return SkM44(c[0], c[4], c[ 8], c[12],
 *                      c[1], c[5], c[ 9], c[13],
 *                      c[2], c[6], c[10], c[14],
 *                      c[3], c[7], c[11], c[15]);
 *     }
 *
 *     static SkM44 Translate(SkScalar x, SkScalar y, SkScalar z = 0) {
 *         return SkM44(1, 0, 0, x,
 *                      0, 1, 0, y,
 *                      0, 0, 1, z,
 *                      0, 0, 0, 1);
 *     }
 *
 *     static SkM44 Scale(SkScalar x, SkScalar y, SkScalar z = 1) {
 *         return SkM44(x, 0, 0, 0,
 *                      0, y, 0, 0,
 *                      0, 0, z, 0,
 *                      0, 0, 0, 1);
 *     }
 *
 *     static SkM44 Rotate(SkV3 axis, SkScalar radians) {
 *         SkM44 m(kUninitialized_Constructor);
 *         m.setRotate(axis, radians);
 *         return m;
 *     }
 *
 *     // Scales and translates 'src' to fill 'dst' exactly.
 *     static SkM44 RectToRect(const SkRect& src, const SkRect& dst);
 *
 *     static SkM44 LookAt(const SkV3& eye, const SkV3& center, const SkV3& up);
 *     static SkM44 Perspective(float near, float far, float angle);
 *
 *     bool operator==(const SkM44& other) const;
 *     bool operator!=(const SkM44& other) const {
 *         return !(other == *this);
 *     }
 *
 *     void getColMajor(SkScalar v[]) const {
 *         memcpy(v, fMat, sizeof(fMat));
 *     }
 *     void getRowMajor(SkScalar v[]) const;
 *
 *     SkScalar rc(int r, int c) const {
 *         SkASSERT(r >= 0 && r <= 3);
 *         SkASSERT(c >= 0 && c <= 3);
 *         return fMat[c*4 + r];
 *     }
 *     void setRC(int r, int c, SkScalar value) {
 *         SkASSERT(r >= 0 && r <= 3);
 *         SkASSERT(c >= 0 && c <= 3);
 *         fMat[c*4 + r] = value;
 *     }
 *
 *     SkV4 row(int i) const {
 *         SkASSERT(i >= 0 && i <= 3);
 *         return {fMat[i + 0], fMat[i + 4], fMat[i + 8], fMat[i + 12]};
 *     }
 *     SkV4 col(int i) const {
 *         SkASSERT(i >= 0 && i <= 3);
 *         return {fMat[i*4 + 0], fMat[i*4 + 1], fMat[i*4 + 2], fMat[i*4 + 3]};
 *     }
 *
 *     void setRow(int i, const SkV4& v) {
 *         SkASSERT(i >= 0 && i <= 3);
 *         fMat[i + 0]  = v.x;
 *         fMat[i + 4]  = v.y;
 *         fMat[i + 8]  = v.z;
 *         fMat[i + 12] = v.w;
 *     }
 *     void setCol(int i, const SkV4& v) {
 *         SkASSERT(i >= 0 && i <= 3);
 *         memcpy(&fMat[i*4], v.ptr(), sizeof(v));
 *     }
 *
 *     SkM44& setIdentity() {
 *         *this = { 1, 0, 0, 0,
 *                   0, 1, 0, 0,
 *                   0, 0, 1, 0,
 *                   0, 0, 0, 1 };
 *         return *this;
 *     }
 *
 *     SkM44& setTranslate(SkScalar x, SkScalar y, SkScalar z = 0) {
 *         *this = { 1, 0, 0, x,
 *                   0, 1, 0, y,
 *                   0, 0, 1, z,
 *                   0, 0, 0, 1 };
 *         return *this;
 *     }
 *
 *     SkM44& setScale(SkScalar x, SkScalar y, SkScalar z = 1) {
 *         *this = { x, 0, 0, 0,
 *                   0, y, 0, 0,
 *                   0, 0, z, 0,
 *                   0, 0, 0, 1 };
 *         return *this;
 *     }
 *
 *     /**
 *      *  Set this matrix to rotate about the specified unit-length axis vector,
 *      *  by an angle specified by its sin() and cos().
 *      *
 *      *  This does not attempt to verify that axis.length() == 1 or that the sin,cos values
 *      *  are correct.
 *      */
 *     SkM44& setRotateUnitSinCos(SkV3 axis, SkScalar sinAngle, SkScalar cosAngle);
 *
 *     /**
 *      *  Set this matrix to rotate about the specified unit-length axis vector,
 *      *  by an angle specified in radians.
 *      *
 *      *  This does not attempt to verify that axis.length() == 1.
 *      */
 *     SkM44& setRotateUnit(SkV3 axis, SkScalar radians) {
 *         return this->setRotateUnitSinCos(axis, SkScalarSin(radians), SkScalarCos(radians));
 *     }
 *
 *     /**
 *      *  Set this matrix to rotate about the specified axis vector,
 *      *  by an angle specified in radians.
 *      *
 *      *  Note: axis is not assumed to be unit-length, so it will be normalized internally.
 *      *        If axis is already unit-length, call setRotateAboutUnitRadians() instead.
 *      */
 *     SkM44& setRotate(SkV3 axis, SkScalar radians);
 *
 *     SkM44& setConcat(const SkM44& a, const SkM44& b);
 *
 *     friend SkM44 operator*(const SkM44& a, const SkM44& b) {
 *         return SkM44(a, b);
 *     }
 *
 *     SkM44& preConcat(const SkM44& m) {
 *         return this->setConcat(*this, m);
 *     }
 *
 *     SkM44& postConcat(const SkM44& m) {
 *         return this->setConcat(m, *this);
 *     }
 *
 *     /**
 *      *  A matrix is categorized as 'perspective' if the bottom row is not [0, 0, 0, 1].
 *      *  For most uses, a bottom row of [0, 0, 0, X] behaves like a non-perspective matrix, though
 *      *  it will be categorized as perspective. Calling normalizePerspective() will change the
 *      *  matrix such that, if its bottom row was [0, 0, 0, X], it will be changed to [0, 0, 0, 1]
 *      *  by scaling the rest of the matrix by 1/X.
 *      *
 *      *  | A B C D |    | A/X B/X C/X D/X |
 *      *  | E F G H | -> | E/X F/X G/X H/X |   for X != 0
 *      *  | I J K L |    | I/X J/X K/X L/X |
 *      *  | 0 0 0 X |    |  0   0   0   1  |
 *      */
 *     void normalizePerspective();
 *
 *     /** Returns true if all elements of the matrix are finite. Returns false if any
 *         element is infinity, or NaN.
 *
 *         @return  true if matrix has only finite elements
 *     */
 *     bool isFinite() const { return SkIsFinite(fMat, 16); }
 *
 *     /** If this is invertible, return that in inverse and return true. If it is
 *      *  not invertible, return false and leave the inverse parameter unchanged.
 *      */
 *     [[nodiscard]] bool invert(SkM44* inverse) const;
 *
 *     [[nodiscard]] SkM44 transpose() const;
 *
 *     void dump() const;
 *
 *     ////////////
 *
 *     SkV4 map(float x, float y, float z, float w) const;
 *     SkV4 operator*(const SkV4& v) const {
 *         return this->map(v.x, v.y, v.z, v.w);
 *     }
 *     SkV3 operator*(SkV3 v) const {
 *         auto v4 = this->map(v.x, v.y, v.z, 0);
 *         return {v4.x, v4.y, v4.z};
 *     }
 *     ////////////////////// Converting to/from SkMatrix
 *
 *     /* When converting from SkM44 to SkMatrix, the third row and
 *      * column is dropped.  When converting from SkMatrix to SkM44
 *      * the third row and column remain as identity:
 *      * [ a b c ]      [ a b 0 c ]
 *      * [ d e f ]  ->  [ d e 0 f ]
 *      * [ g h i ]      [ 0 0 1 0 ]
 *      *                [ g h 0 i ]
 *      */
 *     SkMatrix asM33() const {
 *         return SkMatrix::MakeAll(fMat[0], fMat[4], fMat[12],
 *                                  fMat[1], fMat[5], fMat[13],
 *                                  fMat[3], fMat[7], fMat[15]);
 *     }
 *
 *     explicit SkM44(const SkMatrix& src)
 *     : SkM44(src[SkMatrix::kMScaleX], src[SkMatrix::kMSkewX],  0, src[SkMatrix::kMTransX],
 *             src[SkMatrix::kMSkewY],  src[SkMatrix::kMScaleY], 0, src[SkMatrix::kMTransY],
 *             0,                       0,                       1, 0,
 *             src[SkMatrix::kMPersp0], src[SkMatrix::kMPersp1], 0, src[SkMatrix::kMPersp2])
 *     {}
 *
 *     SkM44& preTranslate(SkScalar x, SkScalar y, SkScalar z = 0);
 *     SkM44& postTranslate(SkScalar x, SkScalar y, SkScalar z = 0);
 *
 *     SkM44& preScale(SkScalar x, SkScalar y);
 *     SkM44& preScale(SkScalar x, SkScalar y, SkScalar z);
 *     SkM44& preConcat(const SkMatrix&);
 *
 * private:
 *     /* Stored in column-major.
 *      *  Indices
 *      *  0  4  8  12        1 0 0 trans_x
 *      *  1  5  9  13  e.g.  0 1 0 trans_y
 *      *  2  6 10  14        0 0 1 trans_z
 *      *  3  7 11  15        0 0 0 1
 *      */
 *     SkScalar fMat[16];
 *
 *     friend class SkMatrixPriv;
 * }
 * ```
 */
public abstract class SkM44 public constructor(
  src: SkM44,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fMat[16]
   * ```
   */
  private var fMat: IntArray = TODO("Initialize fMat")

  /**
   * C++ original:
   * ```cpp
   * SkM44(const SkM44& src) = default
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkM44()
   *         : fMat{1, 0, 0, 0,
   *                0, 1, 0, 0,
   *                0, 0, 1, 0,
   *                0, 0, 0, 1}
   *         {}
   * ```
   */
  public constructor(a: SkM44, b: SkM44) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44(const SkM44& a, const SkM44& b) {
   *         this->setConcat(a, b);
   *     }
   * ```
   */
  public constructor(param0: UninitializedConstructor) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkM44(Uninitialized_Constructor) {}
   * ```
   */
  public constructor(param0: NaNConstructor) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkM44(NaN_Constructor)
   *         : fMat{SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
   *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
   *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN,
   *                SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN, SK_ScalarNaN}
   *     {}
   * ```
   */
  public constructor(
    m0: SkScalar,
    m4: SkScalar,
    m8: SkScalar,
    m12: SkScalar,
    m1: SkScalar,
    m5: SkScalar,
    m9: SkScalar,
    m13: SkScalar,
    m2: SkScalar,
    m6: SkScalar,
    m10: SkScalar,
    m14: SkScalar,
    m3: SkScalar,
    m7: SkScalar,
    m11: SkScalar,
    m15: SkScalar,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkM44(SkScalar m0, SkScalar m4, SkScalar m8,  SkScalar m12,
   *                     SkScalar m1, SkScalar m5, SkScalar m9,  SkScalar m13,
   *                     SkScalar m2, SkScalar m6, SkScalar m10, SkScalar m14,
   *                     SkScalar m3, SkScalar m7, SkScalar m11, SkScalar m15)
   *         // fMat is column-major order in memory.
   *         : fMat{m0,  m1,  m2,  m3,
   *                m4,  m5,  m6,  m7,
   *                m8,  m9,  m10, m11,
   *                m12, m13, m14, m15}
   *     {}
   * ```
   */
  public constructor(src: SkMatrix) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& operator=(const SkM44& src) = default
   * ```
   */
  public fun assign(src: SkM44) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkM44::operator==(const SkM44& other) const {
   *     if (this == &other) {
   *         return true;
   *     }
   *
   *     auto a0 = skvx::float4::Load(fMat +  0);
   *     auto a1 = skvx::float4::Load(fMat +  4);
   *     auto a2 = skvx::float4::Load(fMat +  8);
   *     auto a3 = skvx::float4::Load(fMat + 12);
   *
   *     auto b0 = skvx::float4::Load(other.fMat +  0);
   *     auto b1 = skvx::float4::Load(other.fMat +  4);
   *     auto b2 = skvx::float4::Load(other.fMat +  8);
   *     auto b3 = skvx::float4::Load(other.fMat + 12);
   *
   *     auto eq = (a0 == b0) & (a1 == b1) & (a2 == b2) & (a3 == b3);
   *     return (eq[0] & eq[1] & eq[2] & eq[3]) == ~0;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkM44& other) const {
   *         return !(other == *this);
   *     }
   * ```
   */
  public fun getColMajor(v: Array<SkScalar>) {
    TODO("Implement getColMajor")
  }

  /**
   * C++ original:
   * ```cpp
   * void getColMajor(SkScalar v[]) const {
   *         memcpy(v, fMat, sizeof(fMat));
   *     }
   * ```
   */
  public fun getRowMajor(v: Array<SkScalar>) {
    TODO("Implement getRowMajor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkM44::getRowMajor(SkScalar v[]) const {
   *     transpose_arrays(v, fMat);
   * }
   * ```
   */
  public fun rc(r: Int, c: Int): Int {
    TODO("Implement rc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar rc(int r, int c) const {
   *         SkASSERT(r >= 0 && r <= 3);
   *         SkASSERT(c >= 0 && c <= 3);
   *         return fMat[c*4 + r];
   *     }
   * ```
   */
  public fun setRC(
    r: Int,
    c: Int,
    `value`: SkScalar,
  ) {
    TODO("Implement setRC")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRC(int r, int c, SkScalar value) {
   *         SkASSERT(r >= 0 && r <= 3);
   *         SkASSERT(c >= 0 && c <= 3);
   *         fMat[c*4 + r] = value;
   *     }
   * ```
   */
  public fun row(i: Int): SkV4 {
    TODO("Implement row")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 row(int i) const {
   *         SkASSERT(i >= 0 && i <= 3);
   *         return {fMat[i + 0], fMat[i + 4], fMat[i + 8], fMat[i + 12]};
   *     }
   * ```
   */
  public fun col(i: Int): SkV4 {
    TODO("Implement col")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 col(int i) const {
   *         SkASSERT(i >= 0 && i <= 3);
   *         return {fMat[i*4 + 0], fMat[i*4 + 1], fMat[i*4 + 2], fMat[i*4 + 3]};
   *     }
   * ```
   */
  public fun setRow(i: Int, v: SkV4) {
    TODO("Implement setRow")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRow(int i, const SkV4& v) {
   *         SkASSERT(i >= 0 && i <= 3);
   *         fMat[i + 0]  = v.x;
   *         fMat[i + 4]  = v.y;
   *         fMat[i + 8]  = v.z;
   *         fMat[i + 12] = v.w;
   *     }
   * ```
   */
  public fun setCol(i: Int, v: SkV4) {
    TODO("Implement setCol")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCol(int i, const SkV4& v) {
   *         SkASSERT(i >= 0 && i <= 3);
   *         memcpy(&fMat[i*4], v.ptr(), sizeof(v));
   *     }
   * ```
   */
  public fun setIdentity(): SkM44 {
    TODO("Implement setIdentity")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& setIdentity() {
   *         *this = { 1, 0, 0, 0,
   *                   0, 1, 0, 0,
   *                   0, 0, 1, 0,
   *                   0, 0, 0, 1 };
   *         return *this;
   *     }
   * ```
   */
  public abstract fun setTranslate(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar = 0,
  ): SkM44

  /**
   * C++ original:
   * ```cpp
   * SkM44& setTranslate(SkScalar x, SkScalar y, SkScalar z = 0) {
   *         *this = { 1, 0, 0, x,
   *                   0, 1, 0, y,
   *                   0, 0, 1, z,
   *                   0, 0, 0, 1 };
   *         return *this;
   *     }
   * ```
   */
  public fun setScale(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar = 1,
  ): SkM44 {
    TODO("Implement setScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& setScale(SkScalar x, SkScalar y, SkScalar z = 1) {
   *         *this = { x, 0, 0, 0,
   *                   0, y, 0, 0,
   *                   0, 0, z, 0,
   *                   0, 0, 0, 1 };
   *         return *this;
   *     }
   * ```
   */
  public fun setRotateUnitSinCos(
    axis: SkV3,
    sinAngle: SkScalar,
    cosAngle: SkScalar,
  ): SkM44 {
    TODO("Implement setRotateUnitSinCos")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::setRotateUnitSinCos(SkV3 axis, SkScalar sinAngle, SkScalar cosAngle) {
   *     // Taken from "Essential Mathematics for Games and Interactive Applications"
   *     //             James M. Van Verth and Lars M. Bishop -- third edition
   *     SkScalar x = axis.x;
   *     SkScalar y = axis.y;
   *     SkScalar z = axis.z;
   *     SkScalar c = cosAngle;
   *     SkScalar s = sinAngle;
   *     SkScalar t = 1 - c;
   *
   *     *this = { t*x*x + c,   t*x*y - s*z, t*x*z + s*y, 0,
   *               t*x*y + s*z, t*y*y + c,   t*y*z - s*x, 0,
   *               t*x*z - s*y, t*y*z + s*x, t*z*z + c,   0,
   *               0,           0,           0,           1 };
   *     return *this;
   * }
   * ```
   */
  public fun setRotateUnit(axis: SkV3, radians: SkScalar): SkM44 {
    TODO("Implement setRotateUnit")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& setRotateUnit(SkV3 axis, SkScalar radians) {
   *         return this->setRotateUnitSinCos(axis, SkScalarSin(radians), SkScalarCos(radians));
   *     }
   * ```
   */
  public fun setRotate(axis: SkV3, radians: SkScalar): SkM44 {
    TODO("Implement setRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::setRotate(SkV3 axis, SkScalar radians) {
   *     SkScalar len = axis.length();
   *     if (len > 0 && SkIsFinite(len)) {
   *         this->setRotateUnit(axis * (SK_Scalar1 / len), radians);
   *     } else {
   *         this->setIdentity();
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun setConcat(a: SkM44, b: SkM44): SkM44 {
    TODO("Implement setConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::setConcat(const SkM44& a, const SkM44& b) {
   *     auto c0 = skvx::float4::Load(a.fMat +  0);
   *     auto c1 = skvx::float4::Load(a.fMat +  4);
   *     auto c2 = skvx::float4::Load(a.fMat +  8);
   *     auto c3 = skvx::float4::Load(a.fMat + 12);
   *
   *     auto compute = [&](skvx::float4 r) {
   *         return c0*r[0] + (c1*r[1] + (c2*r[2] + c3*r[3]));
   *     };
   *
   *     auto m0 = compute(skvx::float4::Load(b.fMat +  0));
   *     auto m1 = compute(skvx::float4::Load(b.fMat +  4));
   *     auto m2 = compute(skvx::float4::Load(b.fMat +  8));
   *     auto m3 = compute(skvx::float4::Load(b.fMat + 12));
   *
   *     m0.store(fMat +  0);
   *     m1.store(fMat +  4);
   *     m2.store(fMat +  8);
   *     m3.store(fMat + 12);
   *     return *this;
   * }
   * ```
   */
  public fun preConcat(m: SkM44): SkM44 {
    TODO("Implement preConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& preConcat(const SkM44& m) {
   *         return this->setConcat(*this, m);
   *     }
   * ```
   */
  public fun postConcat(m: SkM44): SkM44 {
    TODO("Implement postConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& postConcat(const SkM44& m) {
   *         return this->setConcat(m, *this);
   *     }
   * ```
   */
  public fun normalizePerspective() {
    TODO("Implement normalizePerspective")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkM44::normalizePerspective() {
   *     // If the bottom row of the matrix is [0, 0, 0, not_one], we will treat the matrix as if it
   *     // is in perspective, even though it stills behaves like its affine. If we divide everything
   *     // by the not_one value, then it will behave the same, but will be treated as affine,
   *     // and therefore faster (e.g. clients can forward-difference calculations).
   *     if (fMat[15] != 1 && fMat[15] != 0 && fMat[3] == 0 && fMat[7] == 0 && fMat[11] == 0) {
   *         double inv = 1.0 / fMat[15];
   *         (skvx::float4::Load(fMat +  0) * inv).store(fMat +  0);
   *         (skvx::float4::Load(fMat +  4) * inv).store(fMat +  4);
   *         (skvx::float4::Load(fMat +  8) * inv).store(fMat +  8);
   *         (skvx::float4::Load(fMat + 12) * inv).store(fMat + 12);
   *         fMat[15] = 1.0f;
   *     }
   * }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const { return SkIsFinite(fMat, 16); }
   * ```
   */
  public fun invert(inverse: SkM44?): Boolean {
    TODO("Implement invert")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkM44::invert(SkM44* inverse) const {
   *     SkScalar tmp[16];
   *     if (SkInvert4x4Matrix(fMat, tmp) == 0.0f) {
   *         return false;
   *     }
   *     memcpy(inverse->fMat, tmp, sizeof(tmp));
   *     return true;
   * }
   * ```
   */
  public fun transpose(): SkM44 {
    TODO("Implement transpose")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44 SkM44::transpose() const {
   *     SkM44 trans(SkM44::kUninitialized_Constructor);
   *     transpose_arrays(trans.fMat, fMat);
   *     return trans;
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkM44::dump() const {
   *     SkDebugf("|%g %g %g %g|\n"
   *              "|%g %g %g %g|\n"
   *              "|%g %g %g %g|\n"
   *              "|%g %g %g %g|\n",
   *              fMat[0], fMat[4], fMat[8],  fMat[12],
   *              fMat[1], fMat[5], fMat[9],  fMat[13],
   *              fMat[2], fMat[6], fMat[10], fMat[14],
   *              fMat[3], fMat[7], fMat[11], fMat[15]);
   * }
   * ```
   */
  public fun map(
    x: Float,
    y: Float,
    z: Float,
    w: Float,
  ): SkV4 {
    TODO("Implement map")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 SkM44::map(float x, float y, float z, float w) const {
   *     auto c0 = skvx::float4::Load(fMat +  0);
   *     auto c1 = skvx::float4::Load(fMat +  4);
   *     auto c2 = skvx::float4::Load(fMat +  8);
   *     auto c3 = skvx::float4::Load(fMat + 12);
   *
   *     SkV4 v;
   *     (c0*x + (c1*y + (c2*z + c3*w))).store(&v.x);
   *     return v;
   * }
   * ```
   */
  public operator fun times(v: SkV4): SkV4 {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV4 operator*(const SkV4& v) const {
   *         return this->map(v.x, v.y, v.z, v.w);
   *     }
   * ```
   */
  public operator fun times(v: SkV3): SkV3 {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 operator*(SkV3 v) const {
   *         auto v4 = this->map(v.x, v.y, v.z, 0);
   *         return {v4.x, v4.y, v4.z};
   *     }
   * ```
   */
  public fun asM33(): Int {
    TODO("Implement asM33")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix asM33() const {
   *         return SkMatrix::MakeAll(fMat[0], fMat[4], fMat[12],
   *                                  fMat[1], fMat[5], fMat[13],
   *                                  fMat[3], fMat[7], fMat[15]);
   *     }
   * ```
   */
  public abstract fun preTranslate(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar = 0,
  ): SkM44

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::preTranslate(SkScalar x, SkScalar y, SkScalar z) {
   *     auto c0 = skvx::float4::Load(fMat +  0);
   *     auto c1 = skvx::float4::Load(fMat +  4);
   *     auto c2 = skvx::float4::Load(fMat +  8);
   *     auto c3 = skvx::float4::Load(fMat + 12);
   *
   *     // only need to update the last column
   *     (c0*x + (c1*y + (c2*z + c3))).store(fMat + 12);
   *     return *this;
   * }
   * ```
   */
  public abstract fun postTranslate(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar = 0,
  ): SkM44

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::postTranslate(SkScalar x, SkScalar y, SkScalar z) {
   *     skvx::float4 t = { x, y, z, 0 };
   *     (t * fMat[ 3] + skvx::float4::Load(fMat +  0)).store(fMat +  0);
   *     (t * fMat[ 7] + skvx::float4::Load(fMat +  4)).store(fMat +  4);
   *     (t * fMat[11] + skvx::float4::Load(fMat +  8)).store(fMat +  8);
   *     (t * fMat[15] + skvx::float4::Load(fMat + 12)).store(fMat + 12);
   *     return *this;
   * }
   * ```
   */
  public fun preScale(x: SkScalar, y: SkScalar): SkM44 {
    TODO("Implement preScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::preScale(SkScalar x, SkScalar y) {
   *     auto c0 = skvx::float4::Load(fMat +  0);
   *     auto c1 = skvx::float4::Load(fMat +  4);
   *
   *     (c0 * x).store(fMat + 0);
   *     (c1 * y).store(fMat + 4);
   *     return *this;
   * }
   * ```
   */
  public fun preScale(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar,
  ): SkM44 {
    TODO("Implement preScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44& SkM44::preScale(SkScalar x, SkScalar y, SkScalar z) {
   *     auto c0 = skvx::float4::Load(fMat +  0);
   *     auto c1 = skvx::float4::Load(fMat +  4);
   *     auto c2 = skvx::float4::Load(fMat +  8);
   *
   *     (c0 * x).store(fMat + 0);
   *     (c1 * y).store(fMat + 4);
   *     (c2 * z).store(fMat + 8);
   *     return *this;
   * }
   * ```
   */
  public fun preConcat(param0: SkMatrix): SkM44 {
    TODO("Implement preConcat")
  }

  public enum class UninitializedConstructor {
    kUninitialized_Constructor,
  }

  public enum class NaNConstructor {
    kNaN_Constructor,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkM44 Rows(const SkV4& r0, const SkV4& r1, const SkV4& r2, const SkV4& r3) {
     *         SkM44 m(kUninitialized_Constructor);
     *         m.setRow(0, r0);
     *         m.setRow(1, r1);
     *         m.setRow(2, r2);
     *         m.setRow(3, r3);
     *         return m;
     *     }
     * ```
     */
    public fun rows(
      r0: SkV4,
      r1: SkV4,
      r2: SkV4,
      r3: SkV4,
    ): SkM44 {
      TODO("Implement rows")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 Cols(const SkV4& c0, const SkV4& c1, const SkV4& c2, const SkV4& c3) {
     *         SkM44 m(kUninitialized_Constructor);
     *         m.setCol(0, c0);
     *         m.setCol(1, c1);
     *         m.setCol(2, c2);
     *         m.setCol(3, c3);
     *         return m;
     *     }
     * ```
     */
    public fun cols(
      c0: SkV4,
      c1: SkV4,
      c2: SkV4,
      c3: SkV4,
    ): SkM44 {
      TODO("Implement cols")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 RowMajor(const SkScalar r[16]) {
     *         return SkM44(r[ 0], r[ 1], r[ 2], r[ 3],
     *                      r[ 4], r[ 5], r[ 6], r[ 7],
     *                      r[ 8], r[ 9], r[10], r[11],
     *                      r[12], r[13], r[14], r[15]);
     *     }
     * ```
     */
    public fun rowMajor(r: Array<SkScalar>): SkM44 {
      TODO("Implement rowMajor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 ColMajor(const SkScalar c[16]) {
     *         return SkM44(c[0], c[4], c[ 8], c[12],
     *                      c[1], c[5], c[ 9], c[13],
     *                      c[2], c[6], c[10], c[14],
     *                      c[3], c[7], c[11], c[15]);
     *     }
     * ```
     */
    public fun colMajor(c: Array<SkScalar>): SkM44 {
      TODO("Implement colMajor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 Translate(SkScalar x, SkScalar y, SkScalar z = 0) {
     *         return SkM44(1, 0, 0, x,
     *                      0, 1, 0, y,
     *                      0, 0, 1, z,
     *                      0, 0, 0, 1);
     *     }
     * ```
     */
    public fun translate(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar = 0,
    ): SkM44 {
      TODO("Implement translate")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 Scale(SkScalar x, SkScalar y, SkScalar z = 1) {
     *         return SkM44(x, 0, 0, 0,
     *                      0, y, 0, 0,
     *                      0, 0, z, 0,
     *                      0, 0, 0, 1);
     *     }
     * ```
     */
    public fun scale(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar = 1,
    ): SkM44 {
      TODO("Implement scale")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkM44 Rotate(SkV3 axis, SkScalar radians) {
     *         SkM44 m(kUninitialized_Constructor);
     *         m.setRotate(axis, radians);
     *         return m;
     *     }
     * ```
     */
    public fun rotate(axis: SkV3, radians: SkScalar): SkM44 {
      TODO("Implement rotate")
    }

    /**
     * C++ original:
     * ```cpp
     * SkM44 SkM44::RectToRect(const SkRect& src, const SkRect& dst) {
     *         if (src.isEmpty()) {
     *         return SkM44();
     *     } else if (dst.isEmpty()) {
     *         return SkM44::Scale(0.f, 0.f, 0.f);
     *     }
     *
     *     float sx = dst.width()  / src.width();
     *     float sy = dst.height() / src.height();
     *
     *     float tx = dst.fLeft - sx * src.fLeft;
     *     float ty = dst.fTop  - sy * src.fTop;
     *
     *     return SkM44{sx,  0.f, 0.f, tx,
     *                  0.f, sy,  0.f, ty,
     *                  0.f, 0.f, 1.f, 0.f,
     *                  0.f, 0.f, 0.f, 1.f};
     * }
     * ```
     */
    public fun rectToRect(src: SkRect, dst: SkRect): SkM44 {
      TODO("Implement rectToRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkM44 SkM44::LookAt(const SkV3& eye, const SkV3& center, const SkV3& up) {
     *     SkV3 f = normalize(center - eye);
     *     SkV3 u = normalize(up);
     *     SkV3 s = normalize(f.cross(u));
     *
     *     SkM44 m(SkM44::kUninitialized_Constructor);
     *     if (!SkM44::Cols(v4(s, 0), v4(s.cross(f), 0), v4(-f, 0), v4(eye, 1)).invert(&m)) {
     *         m.setIdentity();
     *     }
     *     return m;
     * }
     * ```
     */
    public fun lookAt(
      eye: SkV3,
      center: SkV3,
      up: SkV3,
    ): SkM44 {
      TODO("Implement lookAt")
    }

    /**
     * C++ original:
     * ```cpp
     * SkM44 SkM44::Perspective(float near, float far, float angle) {
     *     SkASSERT(far > near);
     *
     *     float denomInv = sk_ieee_float_divide(1, far - near);
     *     float halfAngle = angle * 0.5f;
     *     SkASSERT(halfAngle != 0);
     *     float cot = sk_ieee_float_divide(1, std::tan(halfAngle));
     *
     *     SkM44 m;
     *     m.setRC(0, 0, cot);
     *     m.setRC(1, 1, cot);
     *     m.setRC(2, 2, (far + near) * denomInv);
     *     m.setRC(2, 3, 2 * far * near * denomInv);
     *     m.setRC(3, 2, -1);
     *     return m;
     * }
     * ```
     */
    public fun perspective(
      near: Float,
      far: Float,
      angle: Float,
    ): SkM44 {
      TODO("Implement perspective")
    }
  }
}
