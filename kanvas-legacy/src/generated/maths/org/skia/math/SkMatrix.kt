package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SK_API SkMatrix {
 * public:
 *
 *     /** Creates an identity SkMatrix:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *             | 0 0 1 |
 *     */
 *     constexpr SkMatrix() : SkMatrix(1,0,0, 0,1,0, 0,0,1, kIdentity_Mask | kRectStaysRect_Mask) {}
 *
 *     /** Sets SkMatrix to scale by (sx, sy). Returned matrix is:
 *
 *             | sx  0  0 |
 *             |  0 sy  0 |
 *             |  0  0  1 |
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *         @return    SkMatrix with scale
 *     */
 *     [[nodiscard]] static SkMatrix Scale(SkScalar sx, SkScalar sy) {
 *         SkMatrix m;
 *         m.setScale(sx, sy);
 *         return m;
 *     }
 *
 *     /** Sets SkMatrix to translate by (dx, dy). Returned matrix is:
 *
 *             | 1 0 dx |
 *             | 0 1 dy |
 *             | 0 0  1 |
 *
 *         @param dx  horizontal translation
 *         @param dy  vertical translation
 *         @return    SkMatrix with translation
 *     */
 *     [[nodiscard]] static SkMatrix Translate(SkScalar dx, SkScalar dy) {
 *         SkMatrix m;
 *         m.setTranslate(dx, dy);
 *         return m;
 *     }
 *     [[nodiscard]] static SkMatrix Translate(SkVector t) { return Translate(t.x(), t.y()); }
 *     [[nodiscard]] static SkMatrix Translate(SkIVector t) { return Translate(t.x(), t.y()); }
 *
 *     [[nodiscard]] static SkMatrix ScaleTranslate(float sx, float sy, float tx, float ty);
 *
 *     /** Sets SkMatrix to rotate by |deg| about a pivot point at (0, 0).
 *
 *         @param deg  rotation angle in degrees (positive rotates clockwise)
 *         @return     SkMatrix with rotation
 *     */
 *     [[nodiscard]] static SkMatrix RotateDeg(SkScalar deg) {
 *         SkMatrix m;
 *         m.setRotate(deg);
 *         return m;
 *     }
 *     [[nodiscard]] static SkMatrix RotateDeg(SkScalar deg, SkPoint pt) {
 *         SkMatrix m;
 *         m.setRotate(deg, pt.x(), pt.y());
 *         return m;
 *     }
 *     [[nodiscard]] static SkMatrix RotateRad(SkScalar rad) {
 *         return RotateDeg(SkRadiansToDegrees(rad));
 *     }
 *
 *     /** Sets SkMatrix to skew by (kx, ky) about pivot point (0, 0).
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *         @return    SkMatrix with skew
 *     */
 *     [[nodiscard]] static SkMatrix Skew(SkScalar kx, SkScalar ky) {
 *         SkMatrix m;
 *         m.setSkew(kx, ky);
 *         return m;
 *     }
 *
 *     /** \enum SkMatrix::ScaleToFit
 *         ScaleToFit describes how SkMatrix is constructed to map one SkRect to another.
 *         ScaleToFit may allow SkMatrix to have unequal horizontal and vertical scaling,
 *         or may restrict SkMatrix to square scaling. If restricted, ScaleToFit specifies
 *         how SkMatrix maps to the side or center of the destination SkRect.
 *     */
 *     enum ScaleToFit {
 *         kFill_ScaleToFit,   //!< scales in x and y to fill destination SkRect
 *         kStart_ScaleToFit,  //!< scales and aligns to left and top
 *         kCenter_ScaleToFit, //!< scales and aligns to center
 *         kEnd_ScaleToFit,    //!< scales and aligns to right and bottom
 *     };
 *
 *     /** Sets SkMatrix to:
 *
 *             | scaleX  skewX transX |
 *             |  skewY scaleY transY |
 *             |  pers0  pers1  pers2 |
 *
 *         @param scaleX  horizontal scale factor
 *         @param skewX   horizontal skew factor
 *         @param transX  horizontal translation
 *         @param skewY   vertical skew factor
 *         @param scaleY  vertical scale factor
 *         @param transY  vertical translation
 *         @param pers0   input x-axis perspective factor
 *         @param pers1   input y-axis perspective factor
 *         @param pers2   perspective scale factor
 *         @return        SkMatrix constructed from parameters
 *     */
 *     [[nodiscard]] static SkMatrix MakeAll(SkScalar scaleX, SkScalar skewX,  SkScalar transX,
 *                                           SkScalar skewY,  SkScalar scaleY, SkScalar transY,
 *                                           SkScalar pers0, SkScalar pers1, SkScalar pers2) {
 *         SkMatrix m;
 *         m.setAll(scaleX, skewX, transX, skewY, scaleY, transY, pers0, pers1, pers2);
 *         return m;
 *     }
 *
 *     /** \enum SkMatrix::TypeMask
 *         Enum of bit fields for mask returned by getType().
 *         Used to identify the complexity of SkMatrix, to optimize performance.
 *     */
 *     enum TypeMask {
 *         kIdentity_Mask    = 0,    //!< identity SkMatrix; all bits clear
 *         kTranslate_Mask   = 0x01, //!< translation SkMatrix
 *         kScale_Mask       = 0x02, //!< scale SkMatrix
 *         kAffine_Mask      = 0x04, //!< skew or rotate SkMatrix
 *         kPerspective_Mask = 0x08, //!< perspective SkMatrix
 *     };
 *
 *     /** Returns a bit field describing the transformations the matrix may
 *         perform. The bit field is computed conservatively, so it may include
 *         false positives. For example, when kPerspective_Mask is set, all
 *         other bits are set.
 *
 *         @return  kIdentity_Mask, or combinations of: kTranslate_Mask, kScale_Mask,
 *                  kAffine_Mask, kPerspective_Mask
 *     */
 *     TypeMask getType() const {
 *         if (fTypeMask & kUnknown_Mask) {
 *             fTypeMask = this->computeTypeMask();
 *         }
 *         // only return the public masks
 *         return (TypeMask)(fTypeMask & 0xF);
 *     }
 *
 *     /** Returns true if SkMatrix is identity.  Identity matrix is:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *             | 0 0 1 |
 *
 *         @return  true if SkMatrix has no effect
 *     */
 *     bool isIdentity() const {
 *         return this->getType() == 0;
 *     }
 *
 *     /** Returns true if SkMatrix at most scales and translates. SkMatrix may be identity,
 *         contain only scale elements, only translate elements, or both. SkMatrix form is:
 *
 *             | scale-x    0    translate-x |
 *             |    0    scale-y translate-y |
 *             |    0       0         1      |
 *
 *         @return  true if SkMatrix is identity; or scales, translates, or both
 *     */
 *     bool isScaleTranslate() const {
 *         return !(this->getType() & ~(kScale_Mask | kTranslate_Mask));
 *     }
 *
 *     /** Returns true if SkMatrix is identity, or translates. SkMatrix form is:
 *
 *             | 1 0 translate-x |
 *             | 0 1 translate-y |
 *             | 0 0      1      |
 *
 *         @return  true if SkMatrix is identity, or translates
 *     */
 *     bool isTranslate() const { return !(this->getType() & ~(kTranslate_Mask)); }
 *
 *     /** Returns true SkMatrix maps SkRect to another SkRect. If true, SkMatrix is identity,
 *         or scales, or rotates a multiple of 90 degrees, or mirrors on axes. In all
 *         cases, SkMatrix may also have translation. SkMatrix form is either:
 *
 *             | scale-x    0    translate-x |
 *             |    0    scale-y translate-y |
 *             |    0       0         1      |
 *
 *         or
 *
 *             |    0     rotate-x translate-x |
 *             | rotate-y    0     translate-y |
 *             |    0        0          1      |
 *
 *         for non-zero values of scale-x, scale-y, rotate-x, and rotate-y.
 *
 *         Also called preservesAxisAlignment(); use the one that provides better inline
 *         documentation.
 *
 *         @return  true if SkMatrix maps one SkRect into another
 *     */
 *     bool rectStaysRect() const {
 *         if (fTypeMask & kUnknown_Mask) {
 *             fTypeMask = this->computeTypeMask();
 *         }
 *         return (fTypeMask & kRectStaysRect_Mask) != 0;
 *     }
 *
 *     /** Returns true SkMatrix maps SkRect to another SkRect. If true, SkMatrix is identity,
 *         or scales, or rotates a multiple of 90 degrees, or mirrors on axes. In all
 *         cases, SkMatrix may also have translation. SkMatrix form is either:
 *
 *             | scale-x    0    translate-x |
 *             |    0    scale-y translate-y |
 *             |    0       0         1      |
 *
 *         or
 *
 *             |    0     rotate-x translate-x |
 *             | rotate-y    0     translate-y |
 *             |    0        0          1      |
 *
 *         for non-zero values of scale-x, scale-y, rotate-x, and rotate-y.
 *
 *         Also called rectStaysRect(); use the one that provides better inline
 *         documentation.
 *
 *         @return  true if SkMatrix maps one SkRect into another
 *     */
 *     bool preservesAxisAlignment() const { return this->rectStaysRect(); }
 *
 *     /** Returns true if the matrix contains perspective elements. SkMatrix form is:
 *
 *             |       --            --              --          |
 *             |       --            --              --          |
 *             | perspective-x  perspective-y  perspective-scale |
 *
 *         where perspective-x or perspective-y is non-zero, or perspective-scale is
 *         not one. All other elements may have any value.
 *
 *         @return  true if SkMatrix is in most general form
 *     */
 *     bool hasPerspective() const {
 *         return SkToBool(this->getPerspectiveTypeMaskOnly() &
 *                         kPerspective_Mask);
 *     }
 *
 *     /** Returns true if SkMatrix contains only translation, rotation, reflection, and
 *         uniform scale.
 *         Returns false if SkMatrix contains different scales, skewing, perspective, or
 *         degenerate forms that collapse to a line or point.
 *
 *         Describes that the SkMatrix makes rendering with and without the matrix are
 *         visually alike; a transformed circle remains a circle. Mathematically, this is
 *         referred to as similarity of a Euclidean space, or a similarity transformation.
 *
 *         Preserves right angles, keeping the arms of the angle equal lengths.
 *
 *         @param tol  to be deprecated
 *         @return     true if SkMatrix only rotates, uniformly scales, translates
 *
 *         example: https://fiddle.skia.org/c/@Matrix_isSimilarity
 *     */
 *     bool isSimilarity(SkScalar tol = SK_ScalarNearlyZero) const;
 *
 *     /** Returns true if SkMatrix contains only translation, rotation, reflection, and
 *         scale. Scale may differ along rotated axes.
 *         Returns false if SkMatrix skewing, perspective, or degenerate forms that collapse
 *         to a line or point.
 *
 *         Preserves right angles, but not requiring that the arms of the angle
 *         retain equal lengths.
 *
 *         @param tol  to be deprecated
 *         @return     true if SkMatrix only rotates, scales, translates
 *
 *         example: https://fiddle.skia.org/c/@Matrix_preservesRightAngles
 *     */
 *     bool preservesRightAngles(SkScalar tol = SK_ScalarNearlyZero) const;
 *
 *     /** SkMatrix organizes its values in row-major order. These members correspond to
 *         each value in SkMatrix.
 *     */
 *     static constexpr int kMScaleX = 0; //!< horizontal scale factor
 *     static constexpr int kMSkewX  = 1; //!< horizontal skew factor
 *     static constexpr int kMTransX = 2; //!< horizontal translation
 *     static constexpr int kMSkewY  = 3; //!< vertical skew factor
 *     static constexpr int kMScaleY = 4; //!< vertical scale factor
 *     static constexpr int kMTransY = 5; //!< vertical translation
 *     static constexpr int kMPersp0 = 6; //!< input x perspective factor
 *     static constexpr int kMPersp1 = 7; //!< input y perspective factor
 *     static constexpr int kMPersp2 = 8; //!< perspective bias
 *
 *     /** Affine arrays are in column-major order to match the matrix used by
 *         PDF and XPS.
 *     */
 *     static constexpr int kAScaleX = 0; //!< horizontal scale factor
 *     static constexpr int kASkewY  = 1; //!< vertical skew factor
 *     static constexpr int kASkewX  = 2; //!< horizontal skew factor
 *     static constexpr int kAScaleY = 3; //!< vertical scale factor
 *     static constexpr int kATransX = 4; //!< horizontal translation
 *     static constexpr int kATransY = 5; //!< vertical translation
 *
 *     /** Returns one matrix value. Asserts if index is out of range and SK_DEBUG is
 *         defined.
 *
 *         @param index  one of: kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY,
 *                       kMPersp0, kMPersp1, kMPersp2
 *         @return       value corresponding to index
 *     */
 *     SkScalar operator[](int index) const {
 *         SkASSERT((unsigned)index < 9);
 *         return fMat[index];
 *     }
 *
 *     /** Returns one matrix value. Asserts if index is out of range and SK_DEBUG is
 *         defined.
 *
 *         @param index  one of: kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY,
 *                       kMPersp0, kMPersp1, kMPersp2
 *         @return       value corresponding to index
 *     */
 *     SkScalar get(int index) const {
 *         SkASSERT((unsigned)index < 9);
 *         return fMat[index];
 *     }
 *
 *     /** Returns one matrix value from a particular row/column. Asserts if index is out
 *         of range and SK_DEBUG is defined.
 *
 *         @param r  matrix row to fetch
 *         @param c  matrix column to fetch
 *         @return   value at the given matrix position
 *     */
 *     SkScalar rc(int r, int c) const {
 *         SkASSERT(r >= 0 && r <= 2);
 *         SkASSERT(c >= 0 && c <= 2);
 *         return fMat[r*3 + c];
 *     }
 *
 *     /** Returns scale factor multiplied by x-axis input, contributing to x-axis output.
 *         With mapPoints(), scales SkPoint along the x-axis.
 *
 *         @return  horizontal scale factor
 *     */
 *     SkScalar getScaleX() const { return fMat[kMScaleX]; }
 *
 *     /** Returns scale factor multiplied by y-axis input, contributing to y-axis output.
 *         With mapPoints(), scales SkPoint along the y-axis.
 *
 *         @return  vertical scale factor
 *     */
 *     SkScalar getScaleY() const { return fMat[kMScaleY]; }
 *
 *     /** Returns scale factor multiplied by x-axis input, contributing to y-axis output.
 *         With mapPoints(), skews SkPoint along the y-axis.
 *         Skewing both axes can rotate SkPoint.
 *
 *         @return  vertical skew factor
 *     */
 *     SkScalar getSkewY() const { return fMat[kMSkewY]; }
 *
 *     /** Returns scale factor multiplied by y-axis input, contributing to x-axis output.
 *         With mapPoints(), skews SkPoint along the x-axis.
 *         Skewing both axes can rotate SkPoint.
 *
 *         @return  horizontal scale factor
 *     */
 *     SkScalar getSkewX() const { return fMat[kMSkewX]; }
 *
 *     /** Returns translation contributing to x-axis output.
 *         With mapPoints(), moves SkPoint along the x-axis.
 *
 *         @return  horizontal translation factor
 *     */
 *     SkScalar getTranslateX() const { return fMat[kMTransX]; }
 *
 *     /** Returns translation contributing to y-axis output.
 *         With mapPoints(), moves SkPoint along the y-axis.
 *
 *         @return  vertical translation factor
 *     */
 *     SkScalar getTranslateY() const { return fMat[kMTransY]; }
 *
 *     /** Returns factor scaling input x-axis relative to input y-axis.
 *
 *         @return  input x-axis perspective factor
 *     */
 *     SkScalar getPerspX() const { return fMat[kMPersp0]; }
 *
 *     /** Returns factor scaling input y-axis relative to input x-axis.
 *
 *         @return  input y-axis perspective factor
 *     */
 *     SkScalar getPerspY() const { return fMat[kMPersp1]; }
 *
 *     /** Returns writable SkMatrix value. Asserts if index is out of range and SK_DEBUG is
 *         defined. Clears internal cache anticipating that caller will change SkMatrix value.
 *
 *         Next call to read SkMatrix state may recompute cache; subsequent writes to SkMatrix
 *         value must be followed by dirtyMatrixTypeCache().
 *
 *         @param index  one of: kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY,
 *                       kMPersp0, kMPersp1, kMPersp2
 *         @return       writable value corresponding to index
 *     */
 *     SkScalar& operator[](int index) {
 *         SkASSERT((unsigned)index < 9);
 *         this->setTypeMask(kUnknown_Mask);
 *         return fMat[index];
 *     }
 *
 *     /** Sets SkMatrix value. Asserts if index is out of range and SK_DEBUG is
 *         defined. Safer than operator[]; internal cache is always maintained.
 *
 *         @param index  one of: kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY,
 *                       kMPersp0, kMPersp1, kMPersp2
 *         @param value  scalar to store in SkMatrix
 *     */
 *     SkMatrix& set(int index, SkScalar value) {
 *         SkASSERT((unsigned)index < 9);
 *         fMat[index] = value;
 *         this->setTypeMask(kUnknown_Mask);
 *         return *this;
 *     }
 *
 *     /** Sets horizontal scale factor.
 *
 *         @param v  horizontal scale factor to store
 *     */
 *     SkMatrix& setScaleX(SkScalar v) { return this->set(kMScaleX, v); }
 *
 *     /** Sets vertical scale factor.
 *
 *         @param v  vertical scale factor to store
 *     */
 *     SkMatrix& setScaleY(SkScalar v) { return this->set(kMScaleY, v); }
 *
 *     /** Sets vertical skew factor.
 *
 *         @param v  vertical skew factor to store
 *     */
 *     SkMatrix& setSkewY(SkScalar v) { return this->set(kMSkewY, v); }
 *
 *     /** Sets horizontal skew factor.
 *
 *         @param v  horizontal skew factor to store
 *     */
 *     SkMatrix& setSkewX(SkScalar v) { return this->set(kMSkewX, v); }
 *
 *     /** Sets horizontal translation.
 *
 *         @param v  horizontal translation to store
 *     */
 *     SkMatrix& setTranslateX(SkScalar v) { return this->set(kMTransX, v); }
 *
 *     /** Sets vertical translation.
 *
 *         @param v  vertical translation to store
 *     */
 *     SkMatrix& setTranslateY(SkScalar v) { return this->set(kMTransY, v); }
 *
 *     /** Sets input x-axis perspective factor, which causes mapPoints() to vary input x-axis values
 *         inversely proportional to input y-axis values.
 *
 *         @param v  perspective factor
 *     */
 *     SkMatrix& setPerspX(SkScalar v) { return this->set(kMPersp0, v); }
 *
 *     /** Sets input y-axis perspective factor, which causes mapPoints() to vary input y-axis values
 *         inversely proportional to input x-axis values.
 *
 *         @param v  perspective factor
 *     */
 *     SkMatrix& setPerspY(SkScalar v) { return this->set(kMPersp1, v); }
 *
 *     /** Sets all values from parameters. Sets matrix to:
 *
 *             | scaleX  skewX transX |
 *             |  skewY scaleY transY |
 *             | persp0 persp1 persp2 |
 *
 *         @param scaleX  horizontal scale factor to store
 *         @param skewX   horizontal skew factor to store
 *         @param transX  horizontal translation to store
 *         @param skewY   vertical skew factor to store
 *         @param scaleY  vertical scale factor to store
 *         @param transY  vertical translation to store
 *         @param persp0  input x-axis values perspective factor to store
 *         @param persp1  input y-axis values perspective factor to store
 *         @param persp2  perspective scale factor to store
 *     */
 *     SkMatrix& setAll(SkScalar scaleX, SkScalar skewX,  SkScalar transX,
 *                      SkScalar skewY,  SkScalar scaleY, SkScalar transY,
 *                      SkScalar persp0, SkScalar persp1, SkScalar persp2) {
 *         fMat[kMScaleX] = scaleX;
 *         fMat[kMSkewX]  = skewX;
 *         fMat[kMTransX] = transX;
 *         fMat[kMSkewY]  = skewY;
 *         fMat[kMScaleY] = scaleY;
 *         fMat[kMTransY] = transY;
 *         fMat[kMPersp0] = persp0;
 *         fMat[kMPersp1] = persp1;
 *         fMat[kMPersp2] = persp2;
 *         this->setTypeMask(kUnknown_Mask);
 *         return *this;
 *     }
 *
 *     /** Copies nine scalar values contained by SkMatrix into buffer, in member value
 *         ascending order: kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY,
 *         kMPersp0, kMPersp1, kMPersp2.
 *
 *         @param buffer  storage for nine scalar values
 *     */
 *     void get9(SkScalar buffer[9]) const {
 *         memcpy(buffer, fMat, 9 * sizeof(SkScalar));
 *     }
 *
 *     /** Sets SkMatrix to nine scalar values in buffer, in member value ascending order:
 *         kMScaleX, kMSkewX, kMTransX, kMSkewY, kMScaleY, kMTransY, kMPersp0, kMPersp1,
 *         kMPersp2.
 *
 *         Sets matrix to:
 *
 *             | buffer[0] buffer[1] buffer[2] |
 *             | buffer[3] buffer[4] buffer[5] |
 *             | buffer[6] buffer[7] buffer[8] |
 *
 *         In the future, set9 followed by get9 may not return the same values. Since SkMatrix
 *         maps non-homogeneous coordinates, scaling all nine values produces an equivalent
 *         transformation, possibly improving precision.
 *
 *         @param buffer  nine scalar values
 *     */
 *     SkMatrix& set9(const SkScalar buffer[9]);
 *
 *     /** Sets SkMatrix to identity; which has no effect on mapped SkPoint. Sets SkMatrix to:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *             | 0 0 1 |
 *
 *         Also called setIdentity(); use the one that provides better inline
 *         documentation.
 *     */
 *     SkMatrix& reset();
 *
 *     /** Sets SkMatrix to identity; which has no effect on mapped SkPoint. Sets SkMatrix to:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *             | 0 0 1 |
 *
 *         Also called reset(); use the one that provides better inline
 *         documentation.
 *     */
 *     SkMatrix& setIdentity() { return this->reset(); }
 *
 *     /** Sets SkMatrix to translate by (dx, dy).
 *
 *         @param dx  horizontal translation
 *         @param dy  vertical translation
 *     */
 *     SkMatrix& setTranslate(SkScalar dx, SkScalar dy);
 *
 *     /** Sets SkMatrix to translate by (v.fX, v.fY).
 *
 *         @param v  vector containing horizontal and vertical translation
 *     */
 *     SkMatrix& setTranslate(const SkVector& v) { return this->setTranslate(v.fX, v.fY); }
 *
 *     /** Sets SkMatrix to scale by sx and sy, about a pivot point at (px, py).
 *         The pivot point is unchanged when mapped with SkMatrix.
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& setScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to scale by sx and sy about at pivot point at (0, 0).
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *     */
 *     SkMatrix& setScale(SkScalar sx, SkScalar sy);
 *
 *     /** Sets SkMatrix to rotate by degrees about a pivot point at (px, py).
 *         The pivot point is unchanged when mapped with SkMatrix.
 *
 *         Positive degrees rotates clockwise.
 *
 *         @param degrees  angle of axes relative to upright axes
 *         @param px       pivot on x-axis
 *         @param py       pivot on y-axis
 *     */
 *     SkMatrix& setRotate(SkScalar degrees, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to rotate by degrees about a pivot point at (0, 0).
 *         Positive degrees rotates clockwise.
 *
 *         @param degrees  angle of axes relative to upright axes
 *     */
 *     SkMatrix& setRotate(SkScalar degrees);
 *
 *     /** Sets SkMatrix to rotate by sinValue and cosValue, about a pivot point at (px, py).
 *         The pivot point is unchanged when mapped with SkMatrix.
 *
 *         Vector (sinValue, cosValue) describes the angle of rotation relative to (0, 1).
 *         Vector length specifies scale.
 *
 *         @param sinValue  rotation vector x-axis component
 *         @param cosValue  rotation vector y-axis component
 *         @param px        pivot on x-axis
 *         @param py        pivot on y-axis
 *     */
 *     SkMatrix& setSinCos(SkScalar sinValue, SkScalar cosValue,
 *                    SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to rotate by sinValue and cosValue, about a pivot point at (0, 0).
 *
 *         Vector (sinValue, cosValue) describes the angle of rotation relative to (0, 1).
 *         Vector length specifies scale.
 *
 *         @param sinValue  rotation vector x-axis component
 *         @param cosValue  rotation vector y-axis component
 *     */
 *     SkMatrix& setSinCos(SkScalar sinValue, SkScalar cosValue);
 *
 *     /** Sets SkMatrix to rotate, scale, and translate using a compressed matrix form.
 *
 *         Vector (rsxForm.fSSin, rsxForm.fSCos) describes the angle of rotation relative
 *         to (0, 1). Vector length specifies scale. Mapped point is rotated and scaled
 *         by vector, then translated by (rsxForm.fTx, rsxForm.fTy).
 *
 *         @param rsxForm  compressed SkRSXform matrix
 *         @return         reference to SkMatrix
 *
 *         example: https://fiddle.skia.org/c/@Matrix_setRSXform
 *     */
 *     SkMatrix& setRSXform(const SkRSXform& rsxForm);
 *
 *     /** Sets SkMatrix to skew by kx and ky, about a pivot point at (px, py).
 *         The pivot point is unchanged when mapped with SkMatrix.
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& setSkew(SkScalar kx, SkScalar ky, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to skew by kx and ky, about a pivot point at (0, 0).
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *     */
 *     SkMatrix& setSkew(SkScalar kx, SkScalar ky);
 *
 *     /** Sets SkMatrix to SkMatrix a multiplied by SkMatrix b. Either a or b may be this.
 *
 *         Given:
 *
 *                 | A B C |      | J K L |
 *             a = | D E F |, b = | M N O |
 *                 | G H I |      | P Q R |
 *
 *         sets SkMatrix to:
 *
 *                     | A B C |   | J K L |   | AJ+BM+CP AK+BN+CQ AL+BO+CR |
 *             a * b = | D E F | * | M N O | = | DJ+EM+FP DK+EN+FQ DL+EO+FR |
 *                     | G H I |   | P Q R |   | GJ+HM+IP GK+HN+IQ GL+HO+IR |
 *
 *         @param a  SkMatrix on left side of multiply expression
 *         @param b  SkMatrix on right side of multiply expression
 *     */
 *     SkMatrix& setConcat(const SkMatrix& a, const SkMatrix& b);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from translation (dx, dy).
 *         This can be thought of as moving the point to be mapped before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |               | 1 0 dx |
 *             Matrix = | D E F |,  T(dx, dy) = | 0 1 dy |
 *                      | G H I |               | 0 0  1 |
 *
 *         sets SkMatrix to:
 *
 *                                  | A B C | | 1 0 dx |   | A B A*dx+B*dy+C |
 *             Matrix * T(dx, dy) = | D E F | | 0 1 dy | = | D E D*dx+E*dy+F |
 *                                  | G H I | | 0 0  1 |   | G H G*dx+H*dy+I |
 *
 *         @param dx  x-axis translation before applying SkMatrix
 *         @param dy  y-axis translation before applying SkMatrix
 *     */
 *     SkMatrix& preTranslate(SkScalar dx, SkScalar dy);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from scaling by (sx, sy)
 *         about pivot point (px, py).
 *         This can be thought of as scaling about a pivot point before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |                       | sx  0 dx |
 *             Matrix = | D E F |,  S(sx, sy, px, py) = |  0 sy dy |
 *                      | G H I |                       |  0  0  1 |
 *
 *         where
 *
 *             dx = px - sx * px
 *             dy = py - sy * py
 *
 *         sets SkMatrix to:
 *
 *                                          | A B C | | sx  0 dx |   | A*sx B*sy A*dx+B*dy+C |
 *             Matrix * S(sx, sy, px, py) = | D E F | |  0 sy dy | = | D*sx E*sy D*dx+E*dy+F |
 *                                          | G H I | |  0  0  1 |   | G*sx H*sy G*dx+H*dy+I |
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& preScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from scaling by (sx, sy)
 *         about pivot point (0, 0).
 *         This can be thought of as scaling about the origin before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |               | sx  0  0 |
 *             Matrix = | D E F |,  S(sx, sy) = |  0 sy  0 |
 *                      | G H I |               |  0  0  1 |
 *
 *         sets SkMatrix to:
 *
 *                                  | A B C | | sx  0  0 |   | A*sx B*sy C |
 *             Matrix * S(sx, sy) = | D E F | |  0 sy  0 | = | D*sx E*sy F |
 *                                  | G H I | |  0  0  1 |   | G*sx H*sy I |
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *     */
 *     SkMatrix& preScale(SkScalar sx, SkScalar sy);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from rotating by degrees
 *         about pivot point (px, py).
 *         This can be thought of as rotating about a pivot point before applying SkMatrix.
 *
 *         Positive degrees rotates clockwise.
 *
 *         Given:
 *
 *                      | A B C |                        | c -s dx |
 *             Matrix = | D E F |,  R(degrees, px, py) = | s  c dy |
 *                      | G H I |                        | 0  0  1 |
 *
 *         where
 *
 *             c  = cos(degrees)
 *             s  = sin(degrees)
 *             dx =  s * py + (1 - c) * px
 *             dy = -s * px + (1 - c) * py
 *
 *         sets SkMatrix to:
 *
 *                                           | A B C | | c -s dx |   | Ac+Bs -As+Bc A*dx+B*dy+C |
 *             Matrix * R(degrees, px, py) = | D E F | | s  c dy | = | Dc+Es -Ds+Ec D*dx+E*dy+F |
 *                                           | G H I | | 0  0  1 |   | Gc+Hs -Gs+Hc G*dx+H*dy+I |
 *
 *         @param degrees  angle of axes relative to upright axes
 *         @param px       pivot on x-axis
 *         @param py       pivot on y-axis
 *     */
 *     SkMatrix& preRotate(SkScalar degrees, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from rotating by degrees
 *         about pivot point (0, 0).
 *         This can be thought of as rotating about the origin before applying SkMatrix.
 *
 *         Positive degrees rotates clockwise.
 *
 *         Given:
 *
 *                      | A B C |                        | c -s 0 |
 *             Matrix = | D E F |,  R(degrees, px, py) = | s  c 0 |
 *                      | G H I |                        | 0  0 1 |
 *
 *         where
 *
 *             c  = cos(degrees)
 *             s  = sin(degrees)
 *
 *         sets SkMatrix to:
 *
 *                                           | A B C | | c -s 0 |   | Ac+Bs -As+Bc C |
 *             Matrix * R(degrees, px, py) = | D E F | | s  c 0 | = | Dc+Es -Ds+Ec F |
 *                                           | G H I | | 0  0 1 |   | Gc+Hs -Gs+Hc I |
 *
 *         @param degrees  angle of axes relative to upright axes
 *     */
 *     SkMatrix& preRotate(SkScalar degrees);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from skewing by (kx, ky)
 *         about pivot point (px, py).
 *         This can be thought of as skewing about a pivot point before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |                       |  1 kx dx |
 *             Matrix = | D E F |,  K(kx, ky, px, py) = | ky  1 dy |
 *                      | G H I |                       |  0  0  1 |
 *
 *         where
 *
 *             dx = -kx * py
 *             dy = -ky * px
 *
 *         sets SkMatrix to:
 *
 *                                          | A B C | |  1 kx dx |   | A+B*ky A*kx+B A*dx+B*dy+C |
 *             Matrix * K(kx, ky, px, py) = | D E F | | ky  1 dy | = | D+E*ky D*kx+E D*dx+E*dy+F |
 *                                          | G H I | |  0  0  1 |   | G+H*ky G*kx+H G*dx+H*dy+I |
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& preSkew(SkScalar kx, SkScalar ky, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix constructed from skewing by (kx, ky)
 *         about pivot point (0, 0).
 *         This can be thought of as skewing about the origin before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |               |  1 kx 0 |
 *             Matrix = | D E F |,  K(kx, ky) = | ky  1 0 |
 *                      | G H I |               |  0  0 1 |
 *
 *         sets SkMatrix to:
 *
 *                                  | A B C | |  1 kx 0 |   | A+B*ky A*kx+B C |
 *             Matrix * K(kx, ky) = | D E F | | ky  1 0 | = | D+E*ky D*kx+E F |
 *                                  | G H I | |  0  0 1 |   | G+H*ky G*kx+H I |
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *     */
 *     SkMatrix& preSkew(SkScalar kx, SkScalar ky);
 *
 *     /** Sets SkMatrix to SkMatrix multiplied by SkMatrix other.
 *         This can be thought of mapping by other before applying SkMatrix.
 *
 *         Given:
 *
 *                      | A B C |          | J K L |
 *             Matrix = | D E F |, other = | M N O |
 *                      | G H I |          | P Q R |
 *
 *         sets SkMatrix to:
 *
 *                              | A B C |   | J K L |   | AJ+BM+CP AK+BN+CQ AL+BO+CR |
 *             Matrix * other = | D E F | * | M N O | = | DJ+EM+FP DK+EN+FQ DL+EO+FR |
 *                              | G H I |   | P Q R |   | GJ+HM+IP GK+HN+IQ GL+HO+IR |
 *
 *         @param other  SkMatrix on right side of multiply expression
 *     */
 *     SkMatrix& preConcat(const SkMatrix& other);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from translation (dx, dy) multiplied by SkMatrix.
 *         This can be thought of as moving the point to be mapped after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |               | 1 0 dx |
 *             Matrix = | M N O |,  T(dx, dy) = | 0 1 dy |
 *                      | P Q R |               | 0 0  1 |
 *
 *         sets SkMatrix to:
 *
 *                                  | 1 0 dx | | J K L |   | J+dx*P K+dx*Q L+dx*R |
 *             T(dx, dy) * Matrix = | 0 1 dy | | M N O | = | M+dy*P N+dy*Q O+dy*R |
 *                                  | 0 0  1 | | P Q R |   |      P      Q      R |
 *
 *         @param dx  x-axis translation after applying SkMatrix
 *         @param dy  y-axis translation after applying SkMatrix
 *     */
 *     SkMatrix& postTranslate(SkScalar dx, SkScalar dy);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from scaling by (sx, sy) about pivot point
 *         (px, py), multiplied by SkMatrix.
 *         This can be thought of as scaling about a pivot point after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |                       | sx  0 dx |
 *             Matrix = | M N O |,  S(sx, sy, px, py) = |  0 sy dy |
 *                      | P Q R |                       |  0  0  1 |
 *
 *         where
 *
 *             dx = px - sx * px
 *             dy = py - sy * py
 *
 *         sets SkMatrix to:
 *
 *                                          | sx  0 dx | | J K L |   | sx*J+dx*P sx*K+dx*Q sx*L+dx+R |
 *             S(sx, sy, px, py) * Matrix = |  0 sy dy | | M N O | = | sy*M+dy*P sy*N+dy*Q sy*O+dy*R |
 *                                          |  0  0  1 | | P Q R |   |         P         Q         R |
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& postScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from scaling by (sx, sy) about pivot point
 *         (0, 0), multiplied by SkMatrix.
 *         This can be thought of as scaling about the origin after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |               | sx  0  0 |
 *             Matrix = | M N O |,  S(sx, sy) = |  0 sy  0 |
 *                      | P Q R |               |  0  0  1 |
 *
 *         sets SkMatrix to:
 *
 *                                  | sx  0  0 | | J K L |   | sx*J sx*K sx*L |
 *             S(sx, sy) * Matrix = |  0 sy  0 | | M N O | = | sy*M sy*N sy*O |
 *                                  |  0  0  1 | | P Q R |   |    P    Q    R |
 *
 *         @param sx  horizontal scale factor
 *         @param sy  vertical scale factor
 *     */
 *     SkMatrix& postScale(SkScalar sx, SkScalar sy);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from rotating by degrees about pivot point
 *         (px, py), multiplied by SkMatrix.
 *         This can be thought of as rotating about a pivot point after applying SkMatrix.
 *
 *         Positive degrees rotates clockwise.
 *
 *         Given:
 *
 *                      | J K L |                        | c -s dx |
 *             Matrix = | M N O |,  R(degrees, px, py) = | s  c dy |
 *                      | P Q R |                        | 0  0  1 |
 *
 *         where
 *
 *             c  = cos(degrees)
 *             s  = sin(degrees)
 *             dx =  s * py + (1 - c) * px
 *             dy = -s * px + (1 - c) * py
 *
 *         sets SkMatrix to:
 *
 *                                           |c -s dx| |J K L|   |cJ-sM+dx*P cK-sN+dx*Q cL-sO+dx+R|
 *             R(degrees, px, py) * Matrix = |s  c dy| |M N O| = |sJ+cM+dy*P sK+cN+dy*Q sL+cO+dy*R|
 *                                           |0  0  1| |P Q R|   |         P          Q          R|
 *
 *         @param degrees  angle of axes relative to upright axes
 *         @param px       pivot on x-axis
 *         @param py       pivot on y-axis
 *     */
 *     SkMatrix& postRotate(SkScalar degrees, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from rotating by degrees about pivot point
 *         (0, 0), multiplied by SkMatrix.
 *         This can be thought of as rotating about the origin after applying SkMatrix.
 *
 *         Positive degrees rotates clockwise.
 *
 *         Given:
 *
 *                      | J K L |                        | c -s 0 |
 *             Matrix = | M N O |,  R(degrees, px, py) = | s  c 0 |
 *                      | P Q R |                        | 0  0 1 |
 *
 *         where
 *
 *             c  = cos(degrees)
 *             s  = sin(degrees)
 *
 *         sets SkMatrix to:
 *
 *                                           | c -s dx | | J K L |   | cJ-sM cK-sN cL-sO |
 *             R(degrees, px, py) * Matrix = | s  c dy | | M N O | = | sJ+cM sK+cN sL+cO |
 *                                           | 0  0  1 | | P Q R |   |     P     Q     R |
 *
 *         @param degrees  angle of axes relative to upright axes
 *     */
 *     SkMatrix& postRotate(SkScalar degrees);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from skewing by (kx, ky) about pivot point
 *         (px, py), multiplied by SkMatrix.
 *         This can be thought of as skewing about a pivot point after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |                       |  1 kx dx |
 *             Matrix = | M N O |,  K(kx, ky, px, py) = | ky  1 dy |
 *                      | P Q R |                       |  0  0  1 |
 *
 *         where
 *
 *             dx = -kx * py
 *             dy = -ky * px
 *
 *         sets SkMatrix to:
 *
 *                                          | 1 kx dx| |J K L|   |J+kx*M+dx*P K+kx*N+dx*Q L+kx*O+dx+R|
 *             K(kx, ky, px, py) * Matrix = |ky  1 dy| |M N O| = |ky*J+M+dy*P ky*K+N+dy*Q ky*L+O+dy*R|
 *                                          | 0  0  1| |P Q R|   |          P           Q           R|
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *         @param px  pivot on x-axis
 *         @param py  pivot on y-axis
 *     */
 *     SkMatrix& postSkew(SkScalar kx, SkScalar ky, SkScalar px, SkScalar py);
 *
 *     /** Sets SkMatrix to SkMatrix constructed from skewing by (kx, ky) about pivot point
 *         (0, 0), multiplied by SkMatrix.
 *         This can be thought of as skewing about the origin after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |               |  1 kx 0 |
 *             Matrix = | M N O |,  K(kx, ky) = | ky  1 0 |
 *                      | P Q R |               |  0  0 1 |
 *
 *         sets SkMatrix to:
 *
 *                                  |  1 kx 0 | | J K L |   | J+kx*M K+kx*N L+kx*O |
 *             K(kx, ky) * Matrix = | ky  1 0 | | M N O | = | ky*J+M ky*K+N ky*L+O |
 *                                  |  0  0 1 | | P Q R |   |      P      Q      R |
 *
 *         @param kx  horizontal skew factor
 *         @param ky  vertical skew factor
 *     */
 *     SkMatrix& postSkew(SkScalar kx, SkScalar ky);
 *
 *     /** Sets SkMatrix to SkMatrix other multiplied by SkMatrix.
 *         This can be thought of mapping by other after applying SkMatrix.
 *
 *         Given:
 *
 *                      | J K L |           | A B C |
 *             Matrix = | M N O |,  other = | D E F |
 *                      | P Q R |           | G H I |
 *
 *         sets SkMatrix to:
 *
 *                              | A B C |   | J K L |   | AJ+BM+CP AK+BN+CQ AL+BO+CR |
 *             other * Matrix = | D E F | * | M N O | = | DJ+EM+FP DK+EN+FQ DL+EO+FR |
 *                              | G H I |   | P Q R |   | GJ+HM+IP GK+HN+IQ GL+HO+IR |
 *
 *         @param other  SkMatrix on left side of multiply expression
 *     */
 *     SkMatrix& postConcat(const SkMatrix& other);
 *
 *     /** If possible, return a matrix that will transform the src rect to the dst rect.
 *      *  If the src is empty, this will return {}.
 *      *  If the dst is empty, this will return the zero matrix (degenerate).
 *      */
 *     static std::optional<SkMatrix> Rect2Rect(const SkRect& src, const SkRect& dst,
 *                                              ScaleToFit = kFill_ScaleToFit);
 *
 *     static SkMatrix RectToRectOrIdentity(const SkRect& src, const SkRect& dst,
 *                                          ScaleToFit stf = kFill_ScaleToFit) {
 *         return Rect2Rect(src, dst, stf).value_or(SkMatrix::I());
 *     }
 *
 * #ifdef SK_SUPPORT_LEGACY_MATRIX_RECTTORECT
 *     bool setRectToRect(const SkRect& src, const SkRect& dst, ScaleToFit stf) {
 *         if (auto mx = Rect2Rect(src, dst, stf)) {
 *             *this = *mx;
 *             return true;
 *         }
 *         this->reset();
 *         return false;
 *     }
 *
 *     static SkMatrix MakeRectToRect(const SkRect& src, const SkRect& dst, ScaleToFit stf) {
 *         if (auto mx = Rect2Rect(src, dst, stf)) {
 *             return *mx;
 *         }
 *         return SkMatrix::I();
 *     }
 *
 *     [[nodiscard]] static SkMatrix RectToRect(const SkRect& src, const SkRect& dst,
 *                                              ScaleToFit mode = kFill_ScaleToFit) {
 *         return MakeRectToRect(src, dst, mode);
 *     }
 * #endif
 *
 *     /** Compute a matrix from two polygons, such that if the matrix was applied
 *      *  to the src polygon, it would produce the dst polygon.
 *      *
 *      *  If the size of the two spans are not equal, or if they are > 4, return {}.
 *      *  If the resulting matrix is non-invertible, return {}.
 *      *
 *      *  example: https://fiddle.skia.org/c/@Matrix_setPolyToPoly
 *      */
 *     static std::optional<SkMatrix> PolyToPoly(SkSpan<const SkPoint> src, SkSpan<const SkPoint> dst);
 *
 *     bool setPolyToPoly(SkSpan<const SkPoint> src, SkSpan<const SkPoint> dst) {
 *         if (auto mx = PolyToPoly(src, dst)) {
 *             *this = *mx;
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /*
 *      * If this matrix is invertible, return its inverse, else return {}.
 *     */
 *     std::optional<SkMatrix> invert() const;
 *
 *     // deprecated
 *     [[nodiscard]] bool invert(SkMatrix* inverse) const {
 *         if (auto inv = this->invert()) {
 *             if (inverse) {
 *                 *inverse = *inv;
 *             }
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /** Fills affine with identity values in column major order.
 *         Sets affine to:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *
 *         Affine 3 by 2 matrices in column major order are used by OpenGL and XPS.
 *
 *         @param affine  storage for 3 by 2 affine matrix
 *
 *         example: https://fiddle.skia.org/c/@Matrix_SetAffineIdentity
 *     */
 *     static void SetAffineIdentity(SkScalar affine[6]);
 *
 *     /** Fills affine in column major order. Sets affine to:
 *
 *             | scale-x  skew-x translate-x |
 *             | skew-y  scale-y translate-y |
 *
 *         If SkMatrix contains perspective, returns false and leaves affine unchanged.
 *
 *         @param affine  storage for 3 by 2 affine matrix; may be nullptr
 *         @return        true if SkMatrix does not contain perspective
 *     */
 *     [[nodiscard]] bool asAffine(SkScalar affine[6]) const;
 *
 *     /** Sets SkMatrix to affine values, passed in column major order. Given affine,
 *         column, then row, as:
 *
 *             | scale-x  skew-x translate-x |
 *             |  skew-y scale-y translate-y |
 *
 *         SkMatrix is set, row, then column, to:
 *
 *             | scale-x  skew-x translate-x |
 *             |  skew-y scale-y translate-y |
 *             |       0       0           1 |
 *
 *         @param affine  3 by 2 affine matrix
 *     */
 *     SkMatrix& setAffine(const SkScalar affine[6]);
 *
 *     /**
 *      *  A matrix is categorized as 'perspective' if the bottom row is not [0, 0, 1].
 *      *  However, for most uses (e.g. mapPoints) a bottom row of [0, 0, X] behaves like a
 *      *  non-perspective matrix, though it will be categorized as perspective. Calling
 *      *  normalizePerspective() will change the matrix such that, if its bottom row was [0, 0, X],
 *      *  it will be changed to [0, 0, 1] by scaling the rest of the matrix by 1/X.
 *      *
 *      *  | A B C |    | A/X B/X C/X |
 *      *  | D E F | -> | D/X E/X F/X |   for X != 0
 *      *  | 0 0 X |    |  0   0   1  |
 *      */
 *     void normalizePerspective() {
 *         if (fMat[8] != 1) {
 *             this->doNormalizePerspective();
 *         }
 *     }
 *
 *     /** Maps src SkPoint array of length count to dst SkPoint array of equal or greater
 *         length. SkPoint are mapped by multiplying each SkPoint by SkMatrix. Given:
 *
 *                      | A B C |        | x |
 *             Matrix = | D E F |,  pt = | y |
 *                      | G H I |        | 1 |
 *
 *         where
 *
 *             for (i = 0; i < count; ++i) {
 *                 x = src[i].fX
 *                 y = src[i].fY
 *             }
 *
 *         each dst SkPoint is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         src and dst may point to the same storage.
 *
 *         @param dst    span where the transformed points are written
 *         @param src    spen where the points are read from
 *
 *         Note: min(dst.size(), src.size()) is the number of points that will be written to dst.
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapPoints
 *     */
 *     void mapPoints(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src) const;
 *
 *     /** Maps pts SkPoint array of length count in place. SkPoint are mapped by multiplying
 *         each SkPoint by SkMatrix. Given:
 *
 *                      | A B C |        | x |
 *             Matrix = | D E F |,  pt = | y |
 *                      | G H I |        | 1 |
 *
 *         where
 *
 *             for (i = 0; i < count; ++i) {
 *                 x = pts[i].fX
 *                 y = pts[i].fY
 *             }
 *
 *         each resulting pts SkPoint is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         @param pts    span of points to be transformed in-place
 *     */
 *     void mapPoints(SkSpan<SkPoint> pts) const {
 *         this->mapPoints(pts, pts);
 *     }
 *
 *     /** Maps src SkPoint3 array of length count to dst SkPoint3 array, which must of length count or
 *         greater. SkPoint3 array is mapped by multiplying each SkPoint3 by SkMatrix. Given:
 *
 *                      | A B C |         | x |
 *             Matrix = | D E F |,  src = | y |
 *                      | G H I |         | z |
 *
 *         each resulting dst SkPoint is computed as:
 *
 *                            |A B C| |x|
 *             Matrix * src = |D E F| |y| = |Ax+By+Cz Dx+Ey+Fz Gx+Hy+Iz|
 *                            |G H I| |z|
 *
 *         @param dst    span where the transformed points are written
 *         @param src    spen where the points are read from
 *
 *         Note: min(dst.size(), src.size()) is the number of points that will be written to dst.
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapHomogeneousPoints
 *     */
 *     void mapHomogeneousPoints(SkSpan<SkPoint3> dst, SkSpan<const SkPoint3> src) const;
 *
 *     SkPoint3 mapHomogeneousPoint(SkPoint3 src) const {
 *         SkPoint3 dst;
 *         this->mapHomogeneousPoints({&dst, 1}, {&src, 1});
 *         return dst;
 *     }
 *
 *     /**
 *      *  Returns homogeneous points, starting with 2D src points (with implied w = 1).
 *      *
 *      *  Note: min(dst.size(), src.size()) is the number of points that will be written to dst.
 *
 *      */
 *     void mapPointsToHomogeneous(SkSpan<SkPoint3> dst, SkSpan<const SkPoint> src) const;
 *
 *     SkPoint3 mapPointToHomogeneous(SkPoint src) const {
 *         SkPoint3 dst;
 *         this->mapPointsToHomogeneous({&dst, 1}, {&src, 1});
 *         return dst;
 *     }
 *
 *     /** Returns SkPoint pt multiplied by SkMatrix. Given:
 *
 *                      | A B C |        | x |
 *             Matrix = | D E F |,  pt = | y |
 *                      | G H I |        | 1 |
 *
 *         result is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         @param p  SkPoint to map
 *         @return mapped SkPoint
 *     */
 *     SkPoint mapPoint(SkPoint p) const {
 *         if (this->hasPerspective()) {
 *             return this->mapPointPerspective(p);
 *         } else {
 *             return this->mapPointAffine(p);
 *         }
 *     }
 *
 *     /*
 *      *  If the caller knows the matrix has no perspective, this will inline the
 *      *  math, making it more efficient than calling mapPoint().
 *      */
 *     SkPoint mapPointAffine(SkPoint p) const {
 *         SkASSERT(!this->hasPerspective());
 *         return {
 *             (p.fX * fMat[0] + p.fY * fMat[1]) + fMat[2],
 *             (p.fX * fMat[3] + p.fY * fMat[4]) + fMat[5],
 *         };
 *     }
 *
 *     /** Returns (0, 0) multiplied by SkMatrix. Given:
 *
 *                      | A B C |        | 0 |
 *             Matrix = | D E F |,  pt = | 0 |
 *                      | G H I |        | 1 |
 *
 *         result is computed as:
 *
 *                           |A B C| |0|             C    F
 *             Matrix * pt = |D E F| |0| = |C F I| = -  , -
 *                           |G H I| |1|             I    I
 *
 *         @return   mapped (0, 0)
 *     */
 *     SkPoint mapOrigin() const {
 *         SkScalar x = this->getTranslateX(),
 *                  y = this->getTranslateY();
 *         if (this->hasPerspective()) {
 *             SkScalar w = fMat[kMPersp2];
 *             if ((bool)w) { w = 1 / w; }
 *             x *= w;
 *             y *= w;
 *         }
 *         return {x, y};
 *     }
 *
 *     /** Maps src vector array of length count to vector SkPoint array of equal or greater
 *         length. Vectors are mapped by multiplying each vector by SkMatrix, treating
 *         SkMatrix translation as zero. Given:
 *
 *                      | A B 0 |         | x |
 *             Matrix = | D E 0 |,  src = | y |
 *                      | G H I |         | 1 |
 *
 *         where
 *
 *             for (i = 0; i < count; ++i) {
 *                 x = src[i].fX
 *                 y = src[i].fY
 *             }
 *
 *         each dst vector is computed as:
 *
 *                            |A B 0| |x|                            Ax+By     Dx+Ey
 *             Matrix * src = |D E 0| |y| = |Ax+By Dx+Ey Gx+Hy+I| = ------- , -------
 *                            |G H I| |1|                           Gx+Hy+I   Gx+Hy+I
 *
 *         src and dst may point to the same storage.
 *
 *          @param dst    span where the transformed vectors are written
 *          @param src    spen where the vectors are read from
 *
 *         Note: min(dst.size(), src.size()) is the number of points that will be written to dst.
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapVectors
 *     */
 *     void mapVectors(SkSpan<SkVector> dst, SkSpan<const SkVector> src) const;
 *
 *     /** Maps vecs vector array of length count in place, multiplying each vector by
 *         SkMatrix, treating SkMatrix translation as zero. Given:
 *
 *                      | A B 0 |         | x |
 *             Matrix = | D E 0 |,  vec = | y |
 *                      | G H I |         | 1 |
 *
 *         where
 *
 *             for (i = 0; i < count; ++i) {
 *                 x = vecs[i].fX
 *                 y = vecs[i].fY
 *             }
 *
 *         each result vector is computed as:
 *
 *                            |A B 0| |x|                            Ax+By     Dx+Ey
 *             Matrix * vec = |D E 0| |y| = |Ax+By Dx+Ey Gx+Hy+I| = ------- , -------
 *                            |G H I| |1|                           Gx+Hy+I   Gx+Hy+I
 *
 *         @param vecs   vectors to transform, and storage for mapped vectors
 *     */
 *     void mapVectors(SkSpan<SkVector> vecs) const {
 *         this->mapVectors(vecs, vecs);
 *     }
 *
 *     /** Returns vector (dx, dy) multiplied by SkMatrix, treating SkMatrix translation as zero.
 *         Given:
 *
 *                      | A B 0 |         | dx |
 *             Matrix = | D E 0 |,  vec = | dy |
 *                      | G H I |         |  1 |
 *
 *         each result vector is computed as:
 *
 *                        |A B 0| |dx|                                        A*dx+B*dy     D*dx+E*dy
 *         Matrix * vec = |D E 0| |dy| = |A*dx+B*dy D*dx+E*dy G*dx+H*dy+I| = ----------- , -----------
 *                        |G H I| | 1|                                       G*dx+H*dy+I   G*dx+*dHy+I
 *
 *         @param dx  x-axis value of vector to map
 *         @param dy  y-axis value of vector to map
 *         @return    mapped vector
 *     */
 *     SkVector mapVector(SkVector vec) const {
 *         this->mapVectors({&vec, 1});
 *         return vec;
 *     }
 *     SkVector mapVector(SkScalar dx, SkScalar dy) const {
 *         return this->mapVector({dx, dy});
 *     }
 *
 *     /** Sets dst to bounds of src corners mapped by SkMatrix.
 *         Returns true if mapped corners are dst corners.
 *
 *         Returned value is the same as calling rectStaysRect().
 *
 *         @param dst  storage for bounds of mapped SkPoint
 *         @param src  SkRect to map
 *         @param pc   whether to apply perspective clipping
 *         @return     true if dst is equivalent to mapped src
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapRect
 *     */
 *     bool mapRect(SkRect* dst, const SkRect& src) const;
 *
 *     /** Sets rect to bounds of rect corners mapped by SkMatrix.
 *         Returns true if mapped corners are computed rect corners.
 *
 *         Returned value is the same as calling rectStaysRect().
 *
 *         @param rect  rectangle to map, and storage for bounds of mapped corners
 *         @param pc    whether to apply perspective clipping
 *         @return      true if result is equivalent to mapped rect
 *     */
 *     bool mapRect(SkRect* rect) const {
 *         return this->mapRect(rect, *rect);
 *     }
 *
 *     /** Returns bounds of src corners mapped by SkMatrix.
 *
 *         @param src  rectangle to map
 *         @return     mapped bounds
 *     */
 *     SkRect mapRect(const SkRect& src) const {
 *         SkRect dst;
 *         (void)this->mapRect(&dst, src);
 *         return dst;
 *     }
 *
 *     /** Maps four corners of rect to dst. SkPoint are mapped by multiplying each
 *         rect corner by SkMatrix. rect corner is processed in this order:
 *         (rect.fLeft, rect.fTop), (rect.fRight, rect.fTop), (rect.fRight, rect.fBottom),
 *         (rect.fLeft, rect.fBottom).
 *
 *         rect may be empty: rect.fLeft may be greater than or equal to rect.fRight;
 *         rect.fTop may be greater than or equal to rect.fBottom.
 *
 *         Given:
 *
 *                      | A B C |        | x |
 *             Matrix = | D E F |,  pt = | y |
 *                      | G H I |        | 1 |
 *
 *         where pt is initialized from each of (rect.fLeft, rect.fTop),
 *         (rect.fRight, rect.fTop), (rect.fRight, rect.fBottom), (rect.fLeft, rect.fBottom),
 *         each dst SkPoint is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         @param dst   storage for mapped corner SkPoint
 *         @param rect  SkRect to map
 *
 *         Note: this does not perform perspective clipping (as that might result in more than
 *               4 points, so results are suspect if the matrix contains perspective.
 *     */
 *     void mapRectToQuad(SkPoint dst[4], const SkRect& rect) const {
 *         // This could potentially be faster if we only transformed each x and y of the rect once.
 *         this->mapPoints({dst, 4}, rect.toQuad());
 *     }
 *
 *     /** Sets dst to bounds of src corners mapped by SkMatrix. If matrix contains
 *         elements other than scale or translate: asserts if SK_DEBUG is defined;
 *         otherwise, results are undefined.
 *
 *         @param dst  storage for bounds of mapped SkPoint
 *         @param src  SkRect to map
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapRectScaleTranslate
 *     */
 *     void mapRectScaleTranslate(SkRect* dst, const SkRect& src) const;
 *
 *     /** Returns geometric mean radius of ellipse formed by constructing circle of
 *         size radius, and mapping constructed circle with SkMatrix. The result squared is
 *         equal to the major axis length times the minor axis length.
 *         Result is not meaningful if SkMatrix contains perspective elements.
 *
 *         @param radius  circle size to map
 *         @return        average mapped radius
 *
 *         example: https://fiddle.skia.org/c/@Matrix_mapRadius
 *     */
 *     SkScalar mapRadius(SkScalar radius) const;
 *
 *     /** Compares a and b; returns true if a and b are numerically equal. Returns true
 *         even if sign of zero values are different. Returns false if either SkMatrix
 *         contains NaN, even if the other SkMatrix also contains NaN.
 *
 *         @param a  SkMatrix to compare
 *         @param b  SkMatrix to compare
 *         @return   true if SkMatrix a and SkMatrix b are numerically equal
 *     */
 *     friend SK_API bool operator==(const SkMatrix& a, const SkMatrix& b);
 *
 *     /** Compares a and b; returns true if a and b are not numerically equal. Returns false
 *         even if sign of zero values are different. Returns true if either SkMatrix
 *         contains NaN, even if the other SkMatrix also contains NaN.
 *
 *         @param a  SkMatrix to compare
 *         @param b  SkMatrix to compare
 *         @return   true if SkMatrix a and SkMatrix b are numerically not equal
 *     */
 *     friend SK_API bool operator!=(const SkMatrix& a, const SkMatrix& b) {
 *         return !(a == b);
 *     }
 *
 *     /** Writes text representation of SkMatrix to standard output. Floating point values
 *         are written with limited precision; it may not be possible to reconstruct
 *         original SkMatrix from output.
 *
 *         example: https://fiddle.skia.org/c/@Matrix_dump
 *     */
 *     void dump() const;
 *
 *     /** Returns the minimum scaling factor of SkMatrix by decomposing the scaling and
 *         skewing elements.
 *         Returns -1 if scale factor overflows or SkMatrix contains perspective.
 *
 *         @return  minimum scale factor
 *
 *         example: https://fiddle.skia.org/c/@Matrix_getMinScale
 *     */
 *     SkScalar getMinScale() const;
 *
 *     /** Returns the maximum scaling factor of SkMatrix by decomposing the scaling and
 *         skewing elements.
 *         Returns -1 if scale factor overflows or SkMatrix contains perspective.
 *
 *         @return  maximum scale factor
 *
 *         example: https://fiddle.skia.org/c/@Matrix_getMaxScale
 *     */
 *     SkScalar getMaxScale() const;
 *
 *     /** Sets scaleFactors[0] to the minimum scaling factor, and scaleFactors[1] to the
 *         maximum scaling factor. Scaling factors are computed by decomposing
 *         the SkMatrix scaling and skewing elements.
 *
 *         Returns true if scaleFactors are found; otherwise, returns false and sets
 *         scaleFactors to undefined values.
 *
 *         @param scaleFactors  storage for minimum and maximum scale factors
 *         @return              true if scale factors were computed correctly
 *     */
 *     [[nodiscard]] bool getMinMaxScales(SkScalar scaleFactors[2]) const;
 *
 *     /** Decomposes SkMatrix into scale components and whatever remains. Returns false if
 *         SkMatrix could not be decomposed.
 *
 *         Sets scale to portion of SkMatrix that scale axes. Sets remaining to SkMatrix
 *         with scaling factored out. remaining may be passed as nullptr
 *         to determine if SkMatrix can be decomposed without computing remainder.
 *
 *         Returns true if scale components are found. scale and remaining are
 *         unchanged if SkMatrix contains perspective; scale factors are not finite, or
 *         are nearly zero.
 *
 *         On success: Matrix = Remaining * scale.
 *
 *         @param scale      axes scaling factors; may be nullptr
 *         @param remaining  SkMatrix without scaling; may be nullptr
 *         @return           true if scale can be computed
 *
 *         example: https://fiddle.skia.org/c/@Matrix_decomposeScale
 *     */
 *     bool decomposeScale(SkSize* scale, SkMatrix* remaining = nullptr) const;
 *
 *     /** Returns reference to const identity SkMatrix. Returned SkMatrix is set to:
 *
 *             | 1 0 0 |
 *             | 0 1 0 |
 *             | 0 0 1 |
 *
 *         @return  const identity SkMatrix
 *
 *         example: https://fiddle.skia.org/c/@Matrix_I
 *     */
 *     static const SkMatrix& I();
 *
 *     /** Returns reference to a const SkMatrix with invalid values. Returned SkMatrix is set
 *         to:
 *
 *             | SK_ScalarMax SK_ScalarMax SK_ScalarMax |
 *             | SK_ScalarMax SK_ScalarMax SK_ScalarMax |
 *             | SK_ScalarMax SK_ScalarMax SK_ScalarMax |
 *
 *         @return  const invalid SkMatrix
 *
 *         example: https://fiddle.skia.org/c/@Matrix_InvalidMatrix
 *     */
 *     static const SkMatrix& InvalidMatrix();
 *
 *     /** Returns SkMatrix a multiplied by SkMatrix b.
 *
 *         Given:
 *
 *                 | A B C |      | J K L |
 *             a = | D E F |, b = | M N O |
 *                 | G H I |      | P Q R |
 *
 *         sets SkMatrix to:
 *
 *                     | A B C |   | J K L |   | AJ+BM+CP AK+BN+CQ AL+BO+CR |
 *             a * b = | D E F | * | M N O | = | DJ+EM+FP DK+EN+FQ DL+EO+FR |
 *                     | G H I |   | P Q R |   | GJ+HM+IP GK+HN+IQ GL+HO+IR |
 *
 *         @param a  SkMatrix on left side of multiply expression
 *         @param b  SkMatrix on right side of multiply expression
 *         @return   SkMatrix computed from a times b
 *     */
 *     static SkMatrix Concat(const SkMatrix& a, const SkMatrix& b) {
 *         SkMatrix result;
 *         result.setConcat(a, b);
 *         return result;
 *     }
 *
 *     friend SkMatrix operator*(const SkMatrix& a, const SkMatrix& b) {
 *         return Concat(a, b);
 *     }
 *
 *     /** Sets internal cache to unknown state. Use to force update after repeated
 *         modifications to SkMatrix element reference returned by operator[](int index).
 *     */
 *     void dirtyMatrixTypeCache() {
 *         this->setTypeMask(kUnknown_Mask);
 *     }
 *
 *     /** Initializes SkMatrix with scale and translate elements.
 *
 *             | sx  0 tx |
 *             |  0 sy ty |
 *             |  0  0  1 |
 *
 *         @param sx  horizontal scale factor to store
 *         @param sy  vertical scale factor to store
 *         @param tx  horizontal translation to store
 *         @param ty  vertical translation to store
 *     */
 *     void setScaleTranslate(SkScalar sx, SkScalar sy, SkScalar tx, SkScalar ty) {
 *         *this = SkMatrix::ScaleTranslate(sx, sy, tx, ty);
 *     }
 *
 *     /** Returns true if all elements of the matrix are finite. Returns false if any
 *         element is infinity, or NaN.
 *
 *         @return  true if matrix has only finite elements
 *     */
 *     bool isFinite() const { return SkIsFinite(fMat, 9); }
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     bool setPolyToPoly(const SkPoint src[], const SkPoint dst[], int count) {
 *         return this->setPolyToPoly({src, count}, {dst, count});
 *     }
 *
 *     void mapPoints(SkPoint dst[], const SkPoint src[], int count) const {
 *         this->mapPoints({dst, count}, {src, count});
 *     }
 *     void mapPoints(SkPoint pts[], int count) const {
 *         this->mapPoints(pts, pts, count);
 *     }
 *
 *     void mapHomogeneousPoints(SkPoint3 dst[], const SkPoint3 src[], int count) const {
 *         this->mapHomogeneousPoints({dst, count}, {src, count});
 *     }
 *     void mapHomogeneousPoints(SkPoint3 dst[], const SkPoint src[], int count) const {
 *         this->mapPointsToHomogeneous({dst, count}, {src, count});
 *     }
 *
 *     void mapVectors(SkVector dst[], const SkVector src[], int count) const {
 *         this->mapVectors({dst, count}, {src, count});
 *     }
 *     void mapVectors(SkVector vecs[], int count) const {
 *         this->mapVectors({vecs, count});
 *     }
 *     void mapXY(SkScalar x, SkScalar y, SkPoint* result) const {
 *         *result = this->mapPoint({x, y});
 *     }
 *     SkPoint mapXY(SkScalar x, SkScalar y) const {
 *         return this->mapPoint({x, y});
 *     }
 *     void mapVector(SkScalar dx, SkScalar dy, SkVector* result) const {
 *         SkVector vec = { dx, dy };
 *         this->mapVectors({result, 1}, {&vec, 1});
 *     }
 * #endif
 *
 * private:
 *     /** Set if the matrix will map a rectangle to another rectangle. This
 *         can be true if the matrix is scale-only, or rotates a multiple of
 *         90 degrees.
 *
 *         This bit will be set on identity matrices
 *     */
 *     static constexpr int kRectStaysRect_Mask = 0x10;
 *
 *     /** Set if the perspective bit is valid even though the rest of
 *         the matrix is Unknown.
 *     */
 *     static constexpr int kOnlyPerspectiveValid_Mask = 0x40;
 *
 *     static constexpr int kUnknown_Mask = 0x80;
 *
 *     static constexpr int kORableMasks = kTranslate_Mask |
 *                                         kScale_Mask |
 *                                         kAffine_Mask |
 *                                         kPerspective_Mask;
 *
 *     static constexpr int kAllMasks = kTranslate_Mask |
 *                                      kScale_Mask |
 *                                      kAffine_Mask |
 *                                      kPerspective_Mask |
 *                                      kRectStaysRect_Mask;
 *
 *     SkScalar        fMat[9];
 *     mutable int32_t fTypeMask;
 *
 *     constexpr SkMatrix(SkScalar sx, SkScalar kx, SkScalar tx,
 *                        SkScalar ky, SkScalar sy, SkScalar ty,
 *                        SkScalar p0, SkScalar p1, SkScalar p2, int typeMask)
 *         : fMat{sx, kx, tx,
 *                ky, sy, ty,
 *                p0, p1, p2}
 *         , fTypeMask(typeMask) {}
 *
 *     static void ComputeInv(SkScalar dst[9], const SkScalar src[9], double invDet, bool isPersp);
 *
 *     uint8_t computeTypeMask() const;
 *     uint8_t computePerspectiveTypeMask() const;
 *
 *     void setTypeMask(int mask) {
 *         // allow kUnknown or a valid mask
 *         SkASSERT(kUnknown_Mask == mask || (mask & kAllMasks) == mask ||
 *                  ((kUnknown_Mask | kOnlyPerspectiveValid_Mask) & mask)
 *                  == (kUnknown_Mask | kOnlyPerspectiveValid_Mask));
 *         fTypeMask = mask;
 *     }
 *
 *     void orTypeMask(int mask) {
 *         SkASSERT((mask & kORableMasks) == mask);
 *         fTypeMask |= mask;
 *     }
 *
 *     void clearTypeMask(int mask) {
 *         // only allow a valid mask
 *         SkASSERT((mask & kAllMasks) == mask);
 *         fTypeMask &= ~mask;
 *     }
 *
 *     TypeMask getPerspectiveTypeMaskOnly() const {
 *         if ((fTypeMask & kUnknown_Mask) &&
 *             !(fTypeMask & kOnlyPerspectiveValid_Mask)) {
 *             fTypeMask = this->computePerspectiveTypeMask();
 *         }
 *         return (TypeMask)(fTypeMask & 0xF);
 *     }
 *
 *     /** Returns true if we already know that the matrix is identity;
 *         false otherwise.
 *     */
 *     bool isTriviallyIdentity() const {
 *         if (fTypeMask & kUnknown_Mask) {
 *             return false;
 *         }
 *         return ((fTypeMask & 0xF) == 0);
 *     }
 *
 *     inline void updateTranslateMask() {
 *         if ((fMat[kMTransX] != 0) | (fMat[kMTransY] != 0)) {
 *             fTypeMask |= kTranslate_Mask;
 *         } else {
 *             fTypeMask &= ~kTranslate_Mask;
 *         }
 *     }
 *
 *     /*
 *      *  If the caller knows the matrix perspective, this dos the extra work to
 *      *  correctly compute the mapping. mapPoint() calls this, but only after
 *      *  checking if the matrix includes perspective.
 *      */
 *     SkPoint mapPointPerspective(SkPoint pt) const;
 *
 *     typedef void (*MapPtsProc)(const SkMatrix& mat, SkPoint dst[],
 *                                   const SkPoint src[], int count);
 *
 *     static MapPtsProc GetMapPtsProc(TypeMask mask) {
 *         SkASSERT((mask & ~kAllMasks) == 0);
 *         return gMapPtsProcs[mask & kAllMasks];
 *     }
 *
 *     MapPtsProc getMapPtsProc() const {
 *         return GetMapPtsProc(this->getType());
 *     }
 *
 *     static bool Poly2Proc(const SkPoint[], SkMatrix*);
 *     static bool Poly3Proc(const SkPoint[], SkMatrix*);
 *     static bool Poly4Proc(const SkPoint[], SkMatrix*);
 *
 *     static void Identity_pts(const SkMatrix&, SkPoint[], const SkPoint[], int);
 *     static void Trans_pts(const SkMatrix&, SkPoint dst[], const SkPoint[], int);
 *     static void Scale_pts(const SkMatrix&, SkPoint dst[], const SkPoint[], int);
 *     static void ScaleTrans_pts(const SkMatrix&, SkPoint dst[], const SkPoint[],
 *                                int count);
 *     static void Persp_pts(const SkMatrix&, SkPoint dst[], const SkPoint[], int);
 *
 *     static void Affine_vpts(const SkMatrix&, SkPoint dst[], const SkPoint[], int);
 *
 *     static const MapPtsProc gMapPtsProcs[];
 *
 *     // return the number of bytes written, whether or not buffer is null
 *     size_t writeToMemory(void* buffer) const;
 *     /**
 *      * Reads data from the buffer parameter
 *      *
 *      * @param buffer Memory to read from
 *      * @param length Amount of memory available in the buffer
 *      * @return number of bytes read (must be a multiple of 4) or
 *      *         0 if there was not enough memory available
 *      */
 *     size_t readFromMemory(const void* buffer, size_t length);
 *
 *     // legacy method -- still needed? why not just postScale(1/divx, ...)?
 *     bool postIDiv(int divx, int divy);
 *     void doNormalizePerspective();
 *
 *     friend class SkPerspIter;
 *     friend class SkMatrixPriv;
 *     friend class SerializationTest;
 * }
 * ```
 */
public open class SkMatrix public constructor() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMScaleX = 0
   * ```
   */
  private var fMat: IntArray = TODO("Initialize fMat")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMSkewX  = 1
   * ```
   */
  private var fTypeMask: Int = TODO("Initialize fTypeMask")

  /**
   * C++ original:
   * ```cpp
   * constexpr SkMatrix() : SkMatrix(1,0,0, 0,1,0, 0,0,1, kIdentity_Mask | kRectStaysRect_Mask) {}
   * ```
   */
  public constructor(
    sx: SkScalar,
    kx: SkScalar,
    tx: SkScalar,
    ky: SkScalar,
    sy: SkScalar,
    ty: SkScalar,
    p0: SkScalar,
    p1: SkScalar,
    p2: SkScalar,
    typeMask: Int,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * TypeMask getType() const {
   *         if (fTypeMask & kUnknown_Mask) {
   *             fTypeMask = this->computeTypeMask();
   *         }
   *         // only return the public masks
   *         return (TypeMask)(fTypeMask & 0xF);
   *     }
   * ```
   */
  public fun getType(): TypeMask {
    TODO("Implement getType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isIdentity() const {
   *         return this->getType() == 0;
   *     }
   * ```
   */
  public fun isIdentity(): Boolean {
    TODO("Implement isIdentity")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isScaleTranslate() const {
   *         return !(this->getType() & ~(kScale_Mask | kTranslate_Mask));
   *     }
   * ```
   */
  public fun isScaleTranslate(): Boolean {
    TODO("Implement isScaleTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTranslate() const { return !(this->getType() & ~(kTranslate_Mask)); }
   * ```
   */
  public fun isTranslate(): Boolean {
    TODO("Implement isTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rectStaysRect() const {
   *         if (fTypeMask & kUnknown_Mask) {
   *             fTypeMask = this->computeTypeMask();
   *         }
   *         return (fTypeMask & kRectStaysRect_Mask) != 0;
   *     }
   * ```
   */
  public fun rectStaysRect(): Boolean {
    TODO("Implement rectStaysRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool preservesAxisAlignment() const { return this->rectStaysRect(); }
   * ```
   */
  public fun preservesAxisAlignment(): Boolean {
    TODO("Implement preservesAxisAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPerspective() const {
   *         return SkToBool(this->getPerspectiveTypeMaskOnly() &
   *                         kPerspective_Mask);
   *     }
   * ```
   */
  public fun hasPerspective(): Boolean {
    TODO("Implement hasPerspective")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::isSimilarity(SkScalar tol) const {
   *     // if identity or translate matrix
   *     TypeMask mask = this->getType();
   *     if (mask <= kTranslate_Mask) {
   *         return true;
   *     }
   *     if (mask & kPerspective_Mask) {
   *         return false;
   *     }
   *
   *     SkScalar mx = fMat[kMScaleX];
   *     SkScalar my = fMat[kMScaleY];
   *     // if no skew, can just compare scale factors
   *     if (!(mask & kAffine_Mask)) {
   *         return !SkScalarNearlyZero(mx) && SkScalarNearlyEqual(SkScalarAbs(mx), SkScalarAbs(my));
   *     }
   *     SkScalar sx = fMat[kMSkewX];
   *     SkScalar sy = fMat[kMSkewY];
   *
   *     if (is_degenerate_2x2(mx, sx, sy, my)) {
   *         return false;
   *     }
   *
   *     // upper 2x2 is rotation/reflection + uniform scale if basis vectors
   *     // are 90 degree rotations of each other
   *     return (SkScalarNearlyEqual(mx, my, tol) && SkScalarNearlyEqual(sx, -sy, tol))
   *         || (SkScalarNearlyEqual(mx, -my, tol) && SkScalarNearlyEqual(sx, sy, tol));
   * }
   * ```
   */
  public fun isSimilarity(tol: SkScalar = TODO()): Boolean {
    TODO("Implement isSimilarity")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::preservesRightAngles(SkScalar tol) const {
   *     TypeMask mask = this->getType();
   *
   *     if (mask <= kTranslate_Mask) {
   *         // identity, translate and/or scale
   *         return true;
   *     }
   *     if (mask & kPerspective_Mask) {
   *         return false;
   *     }
   *
   *     SkASSERT(mask & (kAffine_Mask | kScale_Mask));
   *
   *     SkScalar mx = fMat[kMScaleX];
   *     SkScalar my = fMat[kMScaleY];
   *     SkScalar sx = fMat[kMSkewX];
   *     SkScalar sy = fMat[kMSkewY];
   *
   *     if (is_degenerate_2x2(mx, sx, sy, my)) {
   *         return false;
   *     }
   *
   *     // upper 2x2 is scale + rotation/reflection if basis vectors are orthogonal
   *     SkVector vec[2];
   *     vec[0].set(mx, sy);
   *     vec[1].set(sx, my);
   *
   *     return SkScalarNearlyZero(vec[0].dot(vec[1]), SkScalarSquare(tol));
   * }
   * ```
   */
  public fun preservesRightAngles(tol: SkScalar = TODO()): Boolean {
    TODO("Implement preservesRightAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar operator[](int index) const {
   *         SkASSERT((unsigned)index < 9);
   *         return fMat[index];
   *     }
   * ```
   */
  public operator fun `get`(index: Int): Int {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar get(int index) const {
   *         SkASSERT((unsigned)index < 9);
   *         return fMat[index];
   *     }
   * ```
   */
  public fun rc(r: Int, c: Int): Int {
    TODO("Implement rc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar rc(int r, int c) const {
   *         SkASSERT(r >= 0 && r <= 2);
   *         SkASSERT(c >= 0 && c <= 2);
   *         return fMat[r*3 + c];
   *     }
   * ```
   */
  public fun getScaleX(): Int {
    TODO("Implement getScaleX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getScaleX() const { return fMat[kMScaleX]; }
   * ```
   */
  public fun getScaleY(): Int {
    TODO("Implement getScaleY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getScaleY() const { return fMat[kMScaleY]; }
   * ```
   */
  public fun getSkewY(): Int {
    TODO("Implement getSkewY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getSkewY() const { return fMat[kMSkewY]; }
   * ```
   */
  public fun getSkewX(): Int {
    TODO("Implement getSkewX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getSkewX() const { return fMat[kMSkewX]; }
   * ```
   */
  public fun getTranslateX(): Int {
    TODO("Implement getTranslateX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getTranslateX() const { return fMat[kMTransX]; }
   * ```
   */
  public fun getTranslateY(): Int {
    TODO("Implement getTranslateY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getTranslateY() const { return fMat[kMTransY]; }
   * ```
   */
  public fun getPerspX(): Int {
    TODO("Implement getPerspX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getPerspX() const { return fMat[kMPersp0]; }
   * ```
   */
  public fun getPerspY(): Int {
    TODO("Implement getPerspY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getPerspY() const { return fMat[kMPersp1]; }
   * ```
   */
  public fun `set`(index: Int, `value`: SkScalar): SkMatrix {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar& operator[](int index) {
   *         SkASSERT((unsigned)index < 9);
   *         this->setTypeMask(kUnknown_Mask);
   *         return fMat[index];
   *     }
   * ```
   */
  public fun setScaleX(v: SkScalar): SkMatrix {
    TODO("Implement setScaleX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& set(int index, SkScalar value) {
   *         SkASSERT((unsigned)index < 9);
   *         fMat[index] = value;
   *         this->setTypeMask(kUnknown_Mask);
   *         return *this;
   *     }
   * ```
   */
  public fun setScaleY(v: SkScalar): SkMatrix {
    TODO("Implement setScaleY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setScaleX(SkScalar v) { return this->set(kMScaleX, v); }
   * ```
   */
  public fun setSkewY(v: SkScalar): SkMatrix {
    TODO("Implement setSkewY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setScaleY(SkScalar v) { return this->set(kMScaleY, v); }
   * ```
   */
  public fun setSkewX(v: SkScalar): SkMatrix {
    TODO("Implement setSkewX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setSkewY(SkScalar v) { return this->set(kMSkewY, v); }
   * ```
   */
  public fun setTranslateX(v: SkScalar): SkMatrix {
    TODO("Implement setTranslateX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setSkewX(SkScalar v) { return this->set(kMSkewX, v); }
   * ```
   */
  public fun setTranslateY(v: SkScalar): SkMatrix {
    TODO("Implement setTranslateY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setTranslateX(SkScalar v) { return this->set(kMTransX, v); }
   * ```
   */
  public fun setPerspX(v: SkScalar): SkMatrix {
    TODO("Implement setPerspX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setTranslateY(SkScalar v) { return this->set(kMTransY, v); }
   * ```
   */
  public fun setPerspY(v: SkScalar): SkMatrix {
    TODO("Implement setPerspY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setPerspX(SkScalar v) { return this->set(kMPersp0, v); }
   * ```
   */
  public fun setAll(
    scaleX: SkScalar,
    skewX: SkScalar,
    transX: SkScalar,
    skewY: SkScalar,
    scaleY: SkScalar,
    transY: SkScalar,
    persp0: SkScalar,
    persp1: SkScalar,
    persp2: SkScalar,
  ): SkMatrix {
    TODO("Implement setAll")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setPerspY(SkScalar v) { return this->set(kMPersp1, v); }
   * ```
   */
  public fun get9(buffer: Array<SkScalar>) {
    TODO("Implement get9")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setAll(SkScalar scaleX, SkScalar skewX,  SkScalar transX,
   *                      SkScalar skewY,  SkScalar scaleY, SkScalar transY,
   *                      SkScalar persp0, SkScalar persp1, SkScalar persp2) {
   *         fMat[kMScaleX] = scaleX;
   *         fMat[kMSkewX]  = skewX;
   *         fMat[kMTransX] = transX;
   *         fMat[kMSkewY]  = skewY;
   *         fMat[kMScaleY] = scaleY;
   *         fMat[kMTransY] = transY;
   *         fMat[kMPersp0] = persp0;
   *         fMat[kMPersp1] = persp1;
   *         fMat[kMPersp2] = persp2;
   *         this->setTypeMask(kUnknown_Mask);
   *         return *this;
   *     }
   * ```
   */
  public fun set9(buffer: Array<SkScalar>): SkMatrix {
    TODO("Implement set9")
  }

  /**
   * C++ original:
   * ```cpp
   * void get9(SkScalar buffer[9]) const {
   *         memcpy(buffer, fMat, 9 * sizeof(SkScalar));
   *     }
   * ```
   */
  public fun reset(): SkMatrix {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::set9(const SkScalar buffer[9]) {
   *     memcpy(fMat, buffer, 9 * sizeof(SkScalar));
   *     this->setTypeMask(kUnknown_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setIdentity(): SkMatrix {
    TODO("Implement setIdentity")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::reset() { *this = SkMatrix(); return *this; }
   * ```
   */
  public fun setTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement setTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setIdentity() { return this->reset(); }
   * ```
   */
  public fun setTranslate(v: SkVector): SkMatrix {
    TODO("Implement setTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setTranslate(SkScalar dx, SkScalar dy) {
   *     *this = SkMatrix(1, 0, dx,
   *                      0, 1, dy,
   *                      0, 0, 1,
   *                      (dx != 0 || dy != 0) ? kTranslate_Mask | kRectStaysRect_Mask
   *                                           : kIdentity_Mask  | kRectStaysRect_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& setTranslate(const SkVector& v) { return this->setTranslate(v.fX, v.fY); }
   * ```
   */
  public fun setScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement setScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     if (1 == sx && 1 == sy) {
   *         this->reset();
   *     } else {
   *         this->setScaleTranslate(sx, sy, px - sx * px, py - sy * py);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun setRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setScale(SkScalar sx, SkScalar sy) {
   *     auto rectMask = (sx == 0 || sy == 0) ? 0 : kRectStaysRect_Mask;
   *     *this = SkMatrix(sx, 0,  0,
   *                      0,  sy, 0,
   *                      0,  0,  1,
   *                      (sx == 1 && sy == 1) ? kIdentity_Mask | rectMask
   *                                           : kScale_Mask    | rectMask);
   *     return *this;
   * }
   * ```
   */
  public fun setRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement setRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setRotate(SkScalar degrees, SkScalar px, SkScalar py) {
   *     SkScalar rad = SkDegreesToRadians(degrees);
   *     return this->setSinCos(SkScalarSinSnapToZero(rad), SkScalarCosSnapToZero(rad), px, py);
   * }
   * ```
   */
  public fun setSinCos(
    sinValue: SkScalar,
    cosValue: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setSinCos")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setRotate(SkScalar degrees) {
   *     SkScalar rad = SkDegreesToRadians(degrees);
   *     return this->setSinCos(SkScalarSinSnapToZero(rad), SkScalarCosSnapToZero(rad));
   * }
   * ```
   */
  public fun setSinCos(sinValue: SkScalar, cosValue: SkScalar): SkMatrix {
    TODO("Implement setSinCos")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setSinCos(SkScalar sinV, SkScalar cosV, SkScalar px, SkScalar py) {
   *     const SkScalar oneMinusCosV = 1 - cosV;
   *
   *     fMat[kMScaleX]  = cosV;
   *     fMat[kMSkewX]   = -sinV;
   *     fMat[kMTransX]  = sdot(sinV, py, oneMinusCosV, px);
   *
   *     fMat[kMSkewY]   = sinV;
   *     fMat[kMScaleY]  = cosV;
   *     fMat[kMTransY]  = sdot(-sinV, px, oneMinusCosV, py);
   *
   *     fMat[kMPersp0] = fMat[kMPersp1] = 0;
   *     fMat[kMPersp2] = 1;
   *
   *     this->setTypeMask(kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setRSXform(rsxForm: SkRSXform): SkMatrix {
    TODO("Implement setRSXform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setSinCos(SkScalar sinV, SkScalar cosV) {
   *     fMat[kMScaleX]  = cosV;
   *     fMat[kMSkewX]   = -sinV;
   *     fMat[kMTransX]  = 0;
   *
   *     fMat[kMSkewY]   = sinV;
   *     fMat[kMScaleY]  = cosV;
   *     fMat[kMTransY]  = 0;
   *
   *     fMat[kMPersp0] = fMat[kMPersp1] = 0;
   *     fMat[kMPersp2] = 1;
   *
   *     this->setTypeMask(kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement setSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setRSXform(const SkRSXform& xform) {
   *     fMat[kMScaleX]  = xform.fSCos;
   *     fMat[kMSkewX]   = -xform.fSSin;
   *     fMat[kMTransX]  = xform.fTx;
   *
   *     fMat[kMSkewY]   = xform.fSSin;
   *     fMat[kMScaleY]  = xform.fSCos;
   *     fMat[kMTransY]  = xform.fTy;
   *
   *     fMat[kMPersp0] = fMat[kMPersp1] = 0;
   *     fMat[kMPersp2] = 1;
   *
   *     this->setTypeMask(kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement setSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setSkew(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     *this = SkMatrix(1,  sx, -sx * py,
   *                      sy, 1,  -sy * px,
   *                      0,  0,  1,
   *                      kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun setConcat(a: SkMatrix, b: SkMatrix): SkMatrix {
    TODO("Implement setConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setSkew(SkScalar sx, SkScalar sy) {
   *     fMat[kMScaleX]  = 1;
   *     fMat[kMSkewX]   = sx;
   *     fMat[kMTransX]  = 0;
   *
   *     fMat[kMSkewY]   = sy;
   *     fMat[kMScaleY]  = 1;
   *     fMat[kMTransY]  = 0;
   *
   *     fMat[kMPersp0] = fMat[kMPersp1] = 0;
   *     fMat[kMPersp2] = 1;
   *
   *     this->setTypeMask(kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun preTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement preTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setConcat(const SkMatrix& a, const SkMatrix& b) {
   *     TypeMask aType = a.getType();
   *     TypeMask bType = b.getType();
   *
   *     if (a.isTriviallyIdentity()) {
   *         *this = b;
   *     } else if (b.isTriviallyIdentity()) {
   *         *this = a;
   *     } else if (only_scale_and_translate(aType | bType)) {
   *         this->setScaleTranslate(a.fMat[kMScaleX] * b.fMat[kMScaleX],
   *                                 a.fMat[kMScaleY] * b.fMat[kMScaleY],
   *                                 a.fMat[kMScaleX] * b.fMat[kMTransX] + a.fMat[kMTransX],
   *                                 a.fMat[kMScaleY] * b.fMat[kMTransY] + a.fMat[kMTransY]);
   *     } else {
   *         SkMatrix tmp;
   *
   *         if ((aType | bType) & kPerspective_Mask) {
   *             tmp.fMat[kMScaleX] = rowcol3(&a.fMat[0], &b.fMat[0]);
   *             tmp.fMat[kMSkewX]  = rowcol3(&a.fMat[0], &b.fMat[1]);
   *             tmp.fMat[kMTransX] = rowcol3(&a.fMat[0], &b.fMat[2]);
   *             tmp.fMat[kMSkewY]  = rowcol3(&a.fMat[3], &b.fMat[0]);
   *             tmp.fMat[kMScaleY] = rowcol3(&a.fMat[3], &b.fMat[1]);
   *             tmp.fMat[kMTransY] = rowcol3(&a.fMat[3], &b.fMat[2]);
   *             tmp.fMat[kMPersp0] = rowcol3(&a.fMat[6], &b.fMat[0]);
   *             tmp.fMat[kMPersp1] = rowcol3(&a.fMat[6], &b.fMat[1]);
   *             tmp.fMat[kMPersp2] = rowcol3(&a.fMat[6], &b.fMat[2]);
   *
   *             tmp.setTypeMask(kUnknown_Mask);
   *         } else {
   *             tmp.fMat[kMScaleX] = muladdmul(a.fMat[kMScaleX],
   *                                            b.fMat[kMScaleX],
   *                                            a.fMat[kMSkewX],
   *                                            b.fMat[kMSkewY]);
   *
   *             tmp.fMat[kMSkewX]  = muladdmul(a.fMat[kMScaleX],
   *                                            b.fMat[kMSkewX],
   *                                            a.fMat[kMSkewX],
   *                                            b.fMat[kMScaleY]);
   *
   *             tmp.fMat[kMTransX] = muladdmul(a.fMat[kMScaleX],
   *                                            b.fMat[kMTransX],
   *                                            a.fMat[kMSkewX],
   *                                            b.fMat[kMTransY]) + a.fMat[kMTransX];
   *
   *             tmp.fMat[kMSkewY]  = muladdmul(a.fMat[kMSkewY],
   *                                            b.fMat[kMScaleX],
   *                                            a.fMat[kMScaleY],
   *                                            b.fMat[kMSkewY]);
   *
   *             tmp.fMat[kMScaleY] = muladdmul(a.fMat[kMSkewY],
   *                                            b.fMat[kMSkewX],
   *                                            a.fMat[kMScaleY],
   *                                            b.fMat[kMScaleY]);
   *
   *             tmp.fMat[kMTransY] = muladdmul(a.fMat[kMSkewY],
   *                                            b.fMat[kMTransX],
   *                                            a.fMat[kMScaleY],
   *                                            b.fMat[kMTransY]) + a.fMat[kMTransY];
   *
   *             tmp.fMat[kMPersp0] = 0;
   *             tmp.fMat[kMPersp1] = 0;
   *             tmp.fMat[kMPersp2] = 1;
   *             //SkDebugf("Concat mat non-persp type: %d\n", tmp.getType());
   *             //SkASSERT(!(tmp.getType() & kPerspective_Mask));
   *             tmp.setTypeMask(kUnknown_Mask | kOnlyPerspectiveValid_Mask);
   *         }
   *         *this = tmp;
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun preScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preTranslate(SkScalar dx, SkScalar dy) {
   *     const unsigned mask = this->getType();
   *
   *     if (mask <= kTranslate_Mask) {
   *         fMat[kMTransX] += dx;
   *         fMat[kMTransY] += dy;
   *     } else if (mask & kPerspective_Mask) {
   *         SkMatrix    m;
   *         m.setTranslate(dx, dy);
   *         return this->preConcat(m);
   *     } else {
   *         fMat[kMTransX] += sdot(fMat[kMScaleX], dx, fMat[kMSkewX], dy);
   *         fMat[kMTransY] += sdot(fMat[kMSkewY], dx, fMat[kMScaleY], dy);
   *     }
   *     this->updateTranslateMask();
   *     return *this;
   * }
   * ```
   */
  public fun preScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement preScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     if (1 == sx && 1 == sy) {
   *         return *this;
   *     }
   *
   *     SkMatrix    m;
   *     m.setScale(sx, sy, px, py);
   *     return this->preConcat(m);
   * }
   * ```
   */
  public fun preRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preScale(SkScalar sx, SkScalar sy) {
   *     if (1 == sx && 1 == sy) {
   *         return *this;
   *     }
   *
   *     // the assumption is that these multiplies are very cheap, and that
   *     // a full concat and/or just computing the matrix type is more expensive.
   *     // Also, the fixed-point case checks for overflow, but the float doesn't,
   *     // so we can get away with these blind multiplies.
   *
   *     fMat[kMScaleX] *= sx;
   *     fMat[kMSkewY]  *= sx;
   *     fMat[kMPersp0] *= sx;
   *
   *     fMat[kMSkewX]  *= sy;
   *     fMat[kMScaleY] *= sy;
   *     fMat[kMPersp1] *= sy;
   *
   *     // Attempt to simplify our type when applying an inverse scale.
   *     // TODO: The persp/affine preconditions are in place to keep the mask consistent with
   *     //       what computeTypeMask() would produce (persp/skew always implies kScale).
   *     //       We should investigate whether these flag dependencies are truly needed.
   *     if (fMat[kMScaleX] == 1 && fMat[kMScaleY] == 1
   *         && !(fTypeMask & (kPerspective_Mask | kAffine_Mask))) {
   *         this->clearTypeMask(kScale_Mask);
   *     } else {
   *         this->orTypeMask(kScale_Mask);
   *         // Remove kRectStaysRect if the preScale factors were 0
   *         if (!sx || !sy) {
   *             this->clearTypeMask(kRectStaysRect_Mask);
   *         }
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun preRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement preRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preRotate(SkScalar degrees, SkScalar px, SkScalar py) {
   *     SkMatrix    m;
   *     m.setRotate(degrees, px, py);
   *     return this->preConcat(m);
   * }
   * ```
   */
  public fun preSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement preSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preRotate(SkScalar degrees) {
   *     SkMatrix    m;
   *     m.setRotate(degrees);
   *     return this->preConcat(m);
   * }
   * ```
   */
  public fun preSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement preSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preSkew(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     SkMatrix    m;
   *     m.setSkew(sx, sy, px, py);
   *     return this->preConcat(m);
   * }
   * ```
   */
  public fun preConcat(other: SkMatrix): SkMatrix {
    TODO("Implement preConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preSkew(SkScalar sx, SkScalar sy) {
   *     SkMatrix    m;
   *     m.setSkew(sx, sy);
   *     return this->preConcat(m);
   * }
   * ```
   */
  public fun postTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
    TODO("Implement postTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::preConcat(const SkMatrix& mat) {
   *     // check for identity first, so we don't do a needless copy of ourselves
   *     // to ourselves inside setConcat()
   *     if(!mat.isIdentity()) {
   *         this->setConcat(*this, mat);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun postScale(
    sx: SkScalar,
    sy: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postTranslate(SkScalar dx, SkScalar dy) {
   *     if (this->hasPerspective()) {
   *         SkMatrix    m;
   *         m.setTranslate(dx, dy);
   *         this->postConcat(m);
   *     } else {
   *         fMat[kMTransX] += dx;
   *         fMat[kMTransY] += dy;
   *         this->updateTranslateMask();
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun postScale(sx: SkScalar, sy: SkScalar): SkMatrix {
    TODO("Implement postScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postScale(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     if (1 == sx && 1 == sy) {
   *         return *this;
   *     }
   *     SkMatrix    m;
   *     m.setScale(sx, sy, px, py);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun postRotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postScale(SkScalar sx, SkScalar sy) {
   *     if (1 == sx && 1 == sy) {
   *         return *this;
   *     }
   *     SkMatrix    m;
   *     m.setScale(sx, sy);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun postRotate(degrees: SkScalar): SkMatrix {
    TODO("Implement postRotate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postRotate(SkScalar degrees, SkScalar px, SkScalar py) {
   *     SkMatrix    m;
   *     m.setRotate(degrees, px, py);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun postSkew(
    kx: SkScalar,
    ky: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ): SkMatrix {
    TODO("Implement postSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postRotate(SkScalar degrees) {
   *     SkMatrix    m;
   *     m.setRotate(degrees);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun postSkew(kx: SkScalar, ky: SkScalar): SkMatrix {
    TODO("Implement postSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postSkew(SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
   *     SkMatrix    m;
   *     m.setSkew(sx, sy, px, py);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun postConcat(other: SkMatrix): SkMatrix {
    TODO("Implement postConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postSkew(SkScalar sx, SkScalar sy) {
   *     SkMatrix    m;
   *     m.setSkew(sx, sy);
   *     return this->postConcat(m);
   * }
   * ```
   */
  public fun setRectToRect(
    src: SkRect,
    dst: SkRect,
    stf: ScaleToFit,
  ): Boolean {
    TODO("Implement setRectToRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::postConcat(const SkMatrix& mat) {
   *     // check for identity first, so we don't do a needless copy of ourselves
   *     // to ourselves inside setConcat()
   *     if (!mat.isIdentity()) {
   *         this->setConcat(mat, *this);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun setPolyToPoly(src: SkSpan<SkPoint>, dst: SkSpan<SkPoint>): Boolean {
    TODO("Implement setPolyToPoly")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setRectToRect(const SkRect& src, const SkRect& dst, ScaleToFit stf) {
   *         if (auto mx = Rect2Rect(src, dst, stf)) {
   *             *this = *mx;
   *             return true;
   *         }
   *         this->reset();
   *         return false;
   *     }
   * ```
   */
  public fun invert(): SkMatrix? {
    TODO("Implement invert")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setPolyToPoly(SkSpan<const SkPoint> src, SkSpan<const SkPoint> dst) {
   *         if (auto mx = PolyToPoly(src, dst)) {
   *             *this = *mx;
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun invert(inverse: SkMatrix?): Boolean {
    TODO("Implement invert")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMatrix> SkMatrix::invert() const {
   *     TypeMask mask = this->getType();
   *
   *     if (mask == kIdentity_Mask) {
   *         return *this;
   *     }
   *
   *     // Optimized invert for only scale and/or translation matrices.
   *     if (0 == (mask & ~(kScale_Mask | kTranslate_Mask))) {
   *         if (mask & kScale_Mask) {
   *             // Scale + (optional) Translate
   *             SkScalar invSX = sk_ieee_float_divide(1.f, fMat[kMScaleX]);
   *             SkScalar invSY = sk_ieee_float_divide(1.f, fMat[kMScaleY]);
   *             // Denormalized (non-zero) scale factors will overflow when inverted, in which case
   *             // the inverse matrix would not be finite, so return false.
   *             if (!SkIsFinite(invSX, invSY)) {
   *                 return {};
   *             }
   *             SkScalar invTX = -fMat[kMTransX] * invSX;
   *             SkScalar invTY = -fMat[kMTransY] * invSY;
   *             // Make sure inverse translation didn't overflow/underflow after dividing by scale.
   *             // Also catches cases where the original matrix's translation values are not finite.
   *             if (!SkIsFinite(invTX, invTY)) {
   *                 return {};
   *             }
   *
   *             SkMatrix inv;
   *             inv.fMat[kMSkewX] = inv.fMat[kMSkewY] =
   *             inv.fMat[kMPersp0] = inv.fMat[kMPersp1] = 0;
   *
   *             inv.fMat[kMScaleX] = invSX;
   *             inv.fMat[kMScaleY] = invSY;
   *             inv.fMat[kMPersp2] = 1;
   *             inv.fMat[kMTransX] = invTX;
   *             inv.fMat[kMTransY] = invTY;
   *
   *             inv.setTypeMask(mask | kRectStaysRect_Mask);
   *             return inv;
   *         }
   *
   *         // Translate-only
   *         if (!SkIsFinite(fMat[kMTransX], fMat[kMTransY])) {
   *             // Translation components aren't finite, so inverse isn't possible
   *             return {};
   *         }
   *
   *         return SkMatrix::Translate(-fMat[kMTransX], -fMat[kMTransY]);
   *     }
   *
   *     int    isPersp = mask & kPerspective_Mask;
   *     double invDet = sk_inv_determinant(fMat, isPersp);
   *
   *     if (invDet == 0) { // underflow
   *         return {};
   *     }
   *
   *     SkMatrix inv;
   *     ComputeInv(inv.fMat, fMat, invDet, isPersp);
   *     if (!inv.isFinite()) {
   *         return {};
   *     }
   *     inv.setTypeMask(fTypeMask);
   *     return inv;
   * }
   * ```
   */
  public fun asAffine(affine: Array<SkScalar>): Boolean {
    TODO("Implement asAffine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool invert(SkMatrix* inverse) const {
   *         if (auto inv = this->invert()) {
   *             if (inverse) {
   *                 *inverse = *inv;
   *             }
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun setAffine(affine: Array<SkScalar>): SkMatrix {
    TODO("Implement setAffine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::asAffine(SkScalar affine[6]) const {
   *     if (this->hasPerspective()) {
   *         return false;
   *     }
   *     if (affine) {
   *         affine[kAScaleX] = this->fMat[kMScaleX];
   *         affine[kASkewY] = this->fMat[kMSkewY];
   *         affine[kASkewX] = this->fMat[kMSkewX];
   *         affine[kAScaleY] = this->fMat[kMScaleY];
   *         affine[kATransX] = this->fMat[kMTransX];
   *         affine[kATransY] = this->fMat[kMTransY];
   *     }
   *     return true;
   * }
   * ```
   */
  public fun normalizePerspective() {
    TODO("Implement normalizePerspective")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix& SkMatrix::setAffine(const SkScalar buffer[6]) {
   *     fMat[kMScaleX] = buffer[kAScaleX];
   *     fMat[kMSkewX]  = buffer[kASkewX];
   *     fMat[kMTransX] = buffer[kATransX];
   *     fMat[kMSkewY]  = buffer[kASkewY];
   *     fMat[kMScaleY] = buffer[kAScaleY];
   *     fMat[kMTransY] = buffer[kATransY];
   *     fMat[kMPersp0] = 0;
   *     fMat[kMPersp1] = 0;
   *     fMat[kMPersp2] = 1;
   *     this->setTypeMask(kUnknown_Mask);
   *     return *this;
   * }
   * ```
   */
  public fun mapPoints(dst: SkSpan<SkPoint>, src: SkSpan<SkPoint>) {
    TODO("Implement mapPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void normalizePerspective() {
   *         if (fMat[8] != 1) {
   *             this->doNormalizePerspective();
   *         }
   *     }
   * ```
   */
  public fun mapPoints(pts: SkSpan<SkPoint>) {
    TODO("Implement mapPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::mapPoints(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src) const {
   *     const auto count = min_count(dst, src);
   *     this->getMapPtsProc()(*this, dst.data(), src.data(), count);
   * }
   * ```
   */
  public fun mapHomogeneousPoints(dst: SkSpan<SkPoint3>, src: SkSpan<SkPoint3>) {
    TODO("Implement mapHomogeneousPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void mapPoints(SkSpan<SkPoint> pts) const {
   *         this->mapPoints(pts, pts);
   *     }
   * ```
   */
  public fun mapHomogeneousPoint(src: SkPoint3): Int {
    TODO("Implement mapHomogeneousPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::mapHomogeneousPoints(SkSpan<SkPoint3> dst, SkSpan<const SkPoint3> src) const {
   *     const auto count = min_count(dst, src);
   *     SkMatrixPriv::MapHomogeneousPointsWithStride(*this, dst.data(), sizeof(SkPoint3), src.data(),
   *                                                  sizeof(SkPoint3), count);
   * }
   * ```
   */
  public fun mapPointsToHomogeneous(dst: SkSpan<SkPoint3>, src: SkSpan<SkPoint>) {
    TODO("Implement mapPointsToHomogeneous")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 mapHomogeneousPoint(SkPoint3 src) const {
   *         SkPoint3 dst;
   *         this->mapHomogeneousPoints({&dst, 1}, {&src, 1});
   *         return dst;
   *     }
   * ```
   */
  public fun mapPointToHomogeneous(src: SkPoint): Int {
    TODO("Implement mapPointToHomogeneous")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::mapPointsToHomogeneous(SkSpan<SkPoint3> dst, SkSpan<const SkPoint> src) const {
   *     const auto count = min_count(dst, src);
   *
   *     if (this->isIdentity()) {
   *         for (int i = 0; i < count; ++i) {
   *             dst[i] = { src[i].fX, src[i].fY, 1 };
   *         }
   *     } else if (this->hasPerspective()) {
   *         for (int i = 0; i < count; ++i) {
   *             dst[i] = {
   *                 fMat[0] * src[i].fX + fMat[1] * src[i].fY + fMat[2],
   *                 fMat[3] * src[i].fX + fMat[4] * src[i].fY + fMat[5],
   *                 fMat[6] * src[i].fX + fMat[7] * src[i].fY + fMat[8],
   *             };
   *         }
   *     } else {    // affine
   *         for (int i = 0; i < count; ++i) {
   *             dst[i] = {
   *                 fMat[0] * src[i].fX + fMat[1] * src[i].fY + fMat[2],
   *                 fMat[3] * src[i].fX + fMat[4] * src[i].fY + fMat[5],
   *                 1,
   *             };
   *         }
   *     }
   * }
   * ```
   */
  public fun mapPoint(p: SkPoint): Int {
    TODO("Implement mapPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 mapPointToHomogeneous(SkPoint src) const {
   *         SkPoint3 dst;
   *         this->mapPointsToHomogeneous({&dst, 1}, {&src, 1});
   *         return dst;
   *     }
   * ```
   */
  public fun mapPointAffine(p: SkPoint): Int {
    TODO("Implement mapPointAffine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint mapPoint(SkPoint p) const {
   *         if (this->hasPerspective()) {
   *             return this->mapPointPerspective(p);
   *         } else {
   *             return this->mapPointAffine(p);
   *         }
   *     }
   * ```
   */
  public fun mapOrigin(): Int {
    TODO("Implement mapOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint mapPointAffine(SkPoint p) const {
   *         SkASSERT(!this->hasPerspective());
   *         return {
   *             (p.fX * fMat[0] + p.fY * fMat[1]) + fMat[2],
   *             (p.fX * fMat[3] + p.fY * fMat[4]) + fMat[5],
   *         };
   *     }
   * ```
   */
  public fun mapVectors(dst: SkSpan<SkVector>, src: SkSpan<SkVector>) {
    TODO("Implement mapVectors")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint mapOrigin() const {
   *         SkScalar x = this->getTranslateX(),
   *                  y = this->getTranslateY();
   *         if (this->hasPerspective()) {
   *             SkScalar w = fMat[kMPersp2];
   *             if ((bool)w) { w = 1 / w; }
   *             x *= w;
   *             y *= w;
   *         }
   *         return {x, y};
   *     }
   * ```
   */
  public fun mapVectors(vecs: SkSpan<SkVector>) {
    TODO("Implement mapVectors")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::mapVectors(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src) const {
   *     if (this->hasPerspective()) {
   *         const SkPoint origin = this->mapPointPerspective({0, 0});
   *
   *         for (int i = min_count(dst, src) - 1; i >= 0; --i) {
   *             dst[i] = this->mapPointPerspective(src[i]) - origin;
   *         }
   *     } else {
   *         SkMatrix tmp = *this;
   *
   *         tmp.fMat[kMTransX] = tmp.fMat[kMTransY] = 0;
   *         tmp.clearTypeMask(kTranslate_Mask);
   *         tmp.mapPoints(dst, src);
   *     }
   * }
   * ```
   */
  public fun mapVector(vec: SkVector): Int {
    TODO("Implement mapVector")
  }

  /**
   * C++ original:
   * ```cpp
   * void mapVectors(SkSpan<SkVector> vecs) const {
   *         this->mapVectors(vecs, vecs);
   *     }
   * ```
   */
  public fun mapVector(dx: SkScalar, dy: SkScalar): Int {
    TODO("Implement mapVector")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector mapVector(SkVector vec) const {
   *         this->mapVectors({&vec, 1});
   *         return vec;
   *     }
   * ```
   */
  public fun mapRect(dst: SkRect?, src: SkRect): Boolean {
    TODO("Implement mapRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector mapVector(SkScalar dx, SkScalar dy) const {
   *         return this->mapVector({dx, dy});
   *     }
   * ```
   */
  public fun mapRect(rect: SkRect?): Boolean {
    TODO("Implement mapRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::mapRect(SkRect* dst, const SkRect& src) const {
   *     SkASSERT(dst);
   *
   *     if (this->getType() <= kTranslate_Mask) {
   *         SkScalar tx = fMat[kMTransX];
   *         SkScalar ty = fMat[kMTransY];
   *         skvx::float4 trans(tx, ty, tx, ty);
   *         sort_as_rect(skvx::float4::Load(&src.fLeft) + trans).store(&dst->fLeft);
   *         return true;
   *     }
   *     if (this->isScaleTranslate()) {
   *         this->mapRectScaleTranslate(dst, src);
   *         return true;
   *     } else if (this->hasPerspective()) {
   *         SkPathBuilder builder;
   *         builder.addRect(src);
   *         builder.transform(*this);
   *         *dst = builder.computeBounds();
   *         return false;
   *     } else {
   *         std::array<SkPoint, 4> quad = src.toQuad();
   *         this->mapPoints(quad);
   *         dst->setBoundsNoCheck(quad);
   *         return this->rectStaysRect();   // might still return true if rotated by 90, etc.
   *     }
   * }
   * ```
   */
  public fun mapRect(src: SkRect): Int {
    TODO("Implement mapRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool mapRect(SkRect* rect) const {
   *         return this->mapRect(rect, *rect);
   *     }
   * ```
   */
  public fun mapRectToQuad(dst: Array<SkPoint>, rect: SkRect) {
    TODO("Implement mapRectToQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect mapRect(const SkRect& src) const {
   *         SkRect dst;
   *         (void)this->mapRect(&dst, src);
   *         return dst;
   *     }
   * ```
   */
  public fun mapRectScaleTranslate(dst: SkRect?, src: SkRect) {
    TODO("Implement mapRectScaleTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void mapRectToQuad(SkPoint dst[4], const SkRect& rect) const {
   *         // This could potentially be faster if we only transformed each x and y of the rect once.
   *         this->mapPoints({dst, 4}, rect.toQuad());
   *     }
   * ```
   */
  public fun mapRadius(radius: SkScalar): Int {
    TODO("Implement mapRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::mapRectScaleTranslate(SkRect* dst, const SkRect& src) const {
   *     SkASSERT(dst);
   *     SkASSERT(this->isScaleTranslate());
   *
   *     SkScalar sx = fMat[kMScaleX];
   *     SkScalar sy = fMat[kMScaleY];
   *     SkScalar tx = fMat[kMTransX];
   *     SkScalar ty = fMat[kMTransY];
   *     skvx::float4 scale(sx, sy, sx, sy);
   *     skvx::float4 trans(tx, ty, tx, ty);
   *     sort_as_rect(skvx::float4::Load(&src.fLeft) * scale + trans).store(&dst->fLeft);
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkMatrix::mapRadius(SkScalar radius) const {
   *     SkVector    vec[2];
   *
   *     vec[0].set(radius, 0);
   *     vec[1].set(0, radius);
   *     this->mapVectors(vec);
   *
   *     SkScalar d0 = vec[0].length();
   *     SkScalar d1 = vec[1].length();
   *
   *     // return geometric mean
   *     return SkScalarSqrt(d0 * d1);
   * }
   * ```
   */
  public fun getMinScale(): Int {
    TODO("Implement getMinScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrix::dump() const {
   *     SkString str;
   *     str.appendf("[%8.4f %8.4f %8.4f][%8.4f %8.4f %8.4f][%8.4f %8.4f %8.4f]",
   *              fMat[0], fMat[1], fMat[2], fMat[3], fMat[4], fMat[5],
   *              fMat[6], fMat[7], fMat[8]);
   *     SkDebugf("%s\n", str.c_str());
   * }
   * ```
   */
  public fun getMaxScale(): Int {
    TODO("Implement getMaxScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkMatrix::getMinScale() const {
   *     SkScalar factor;
   *     if (get_scale_factor<kMin_MinMaxOrBoth>(this->getType(), fMat, &factor)) {
   *         return factor;
   *     } else {
   *         return -1;
   *     }
   * }
   * ```
   */
  public fun getMinMaxScales(scaleFactors: Array<SkScalar>): Boolean {
    TODO("Implement getMinMaxScales")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkMatrix::getMaxScale() const {
   *     SkScalar factor;
   *     if (get_scale_factor<kMax_MinMaxOrBoth>(this->getType(), fMat, &factor)) {
   *         return factor;
   *     } else {
   *         return -1;
   *     }
   * }
   * ```
   */
  public fun decomposeScale(scale: SkSize?, remaining: SkMatrix? = TODO()): Boolean {
    TODO("Implement decomposeScale")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::getMinMaxScales(SkScalar scaleFactors[2]) const {
   *     return get_scale_factor<kBoth_MinMaxOrBoth>(this->getType(), fMat, scaleFactors);
   * }
   * ```
   */
  public fun dirtyMatrixTypeCache() {
    TODO("Implement dirtyMatrixTypeCache")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrix::decomposeScale(SkSize* scale, SkMatrix* remaining) const {
   *     if (this->hasPerspective()) {
   *         return false;
   *     }
   *
   *     const SkScalar sx = SkVector::Length(this->getScaleX(), this->getSkewY());
   *     const SkScalar sy = SkVector::Length(this->getSkewX(), this->getScaleY());
   *     if (!SkIsFinite(sx, sy) ||
   *         SkScalarNearlyZero(sx) || SkScalarNearlyZero(sy)) {
   *         return false;
   *     }
   *
   *     if (scale) {
   *         scale->set(sx, sy);
   *     }
   *     if (remaining) {
   *         *remaining = *this;
   *         remaining->preScale(SkScalarInvert(sx), SkScalarInvert(sy));
   *     }
   *     return true;
   * }
   * ```
   */
  public fun setScaleTranslate(
    sx: SkScalar,
    sy: SkScalar,
    tx: SkScalar,
    ty: SkScalar,
  ) {
    TODO("Implement setScaleTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void dirtyMatrixTypeCache() {
   *         this->setTypeMask(kUnknown_Mask);
   *     }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * void setScaleTranslate(SkScalar sx, SkScalar sy, SkScalar tx, SkScalar ty) {
   *         *this = SkMatrix::ScaleTranslate(sx, sy, tx, ty);
   *     }
   * ```
   */
  private fun computeTypeMask(): UByte {
    TODO("Implement computeTypeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const { return SkIsFinite(fMat, 9); }
   * ```
   */
  private fun computePerspectiveTypeMask(): UByte {
    TODO("Implement computePerspectiveTypeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMatrix::computeTypeMask() const {
   *     unsigned mask = 0;
   *
   *     if (fMat[kMPersp0] != 0 || fMat[kMPersp1] != 0 || fMat[kMPersp2] != 1) {
   *         // Once it is determined that that this is a perspective transform,
   *         // all other flags are moot as far as optimizations are concerned.
   *         return SkToU8(kORableMasks);
   *     }
   *
   *     if (fMat[kMTransX] != 0 || fMat[kMTransY] != 0) {
   *         mask |= kTranslate_Mask;
   *     }
   *
   *     int m00 = SkScalarAs2sCompliment(fMat[SkMatrix::kMScaleX]);
   *     int m01 = SkScalarAs2sCompliment(fMat[SkMatrix::kMSkewX]);
   *     int m10 = SkScalarAs2sCompliment(fMat[SkMatrix::kMSkewY]);
   *     int m11 = SkScalarAs2sCompliment(fMat[SkMatrix::kMScaleY]);
   *
   *     if (m01 | m10) {
   *         // The skew components may be scale-inducing, unless we are dealing
   *         // with a pure rotation.  Testing for a pure rotation is expensive,
   *         // so we opt for being conservative by always setting the scale bit.
   *         // along with affine.
   *         // By doing this, we are also ensuring that matrices have the same
   *         // type masks as their inverses.
   *         mask |= kAffine_Mask | kScale_Mask;
   *
   *         // For rectStaysRect, in the affine case, we only need check that
   *         // the primary diagonal is all zeros and that the secondary diagonal
   *         // is all non-zero.
   *
   *         // map non-zero to 1
   *         m01 = m01 != 0;
   *         m10 = m10 != 0;
   *
   *         int dp0 = 0 == (m00 | m11) ;  // true if both are 0
   *         int ds1 = m01 & m10;        // true if both are 1
   *
   *         mask |= (dp0 & ds1) << kRectStaysRect_Shift;
   *     } else {
   *         // Only test for scale explicitly if not affine, since affine sets the
   *         // scale bit.
   *         if ((m00 ^ kScalar1Int) | (m11 ^ kScalar1Int)) {
   *             mask |= kScale_Mask;
   *         }
   *
   *         // Not affine, therefore we already know secondary diagonal is
   *         // all zeros, so we just need to check that primary diagonal is
   *         // all non-zero.
   *
   *         // map non-zero to 1
   *         m00 = m00 != 0;
   *         m11 = m11 != 0;
   *
   *         // record if the (p)rimary diagonal is all non-zero
   *         mask |= (m00 & m11) << kRectStaysRect_Shift;
   *     }
   *
   *     return SkToU8(mask);
   * }
   * ```
   */
  private fun setTypeMask(mask: Int) {
    TODO("Implement setTypeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t SkMatrix::computePerspectiveTypeMask() const {
   *     // Benchmarking suggests that replacing this set of SkScalarAs2sCompliment
   *     // is a win, but replacing those below is not. We don't yet understand
   *     // that result.
   *     if (fMat[kMPersp0] != 0 || fMat[kMPersp1] != 0 || fMat[kMPersp2] != 1) {
   *         // If this is a perspective transform, we return true for all other
   *         // transform flags - this does not disable any optimizations, respects
   *         // the rule that the type mask must be conservative, and speeds up
   *         // type mask computation.
   *         return SkToU8(kORableMasks);
   *     }
   *
   *     return SkToU8(kOnlyPerspectiveValid_Mask | kUnknown_Mask);
   * }
   * ```
   */
  private fun orTypeMask(mask: Int) {
    TODO("Implement orTypeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTypeMask(int mask) {
   *         // allow kUnknown or a valid mask
   *         SkASSERT(kUnknown_Mask == mask || (mask & kAllMasks) == mask ||
   *                  ((kUnknown_Mask | kOnlyPerspectiveValid_Mask) & mask)
   *                  == (kUnknown_Mask | kOnlyPerspectiveValid_Mask));
   *         fTypeMask = mask;
   *     }
   * ```
   */
  private fun clearTypeMask(mask: Int) {
    TODO("Implement clearTypeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void orTypeMask(int mask) {
   *         SkASSERT((mask & kORableMasks) == mask);
   *         fTypeMask |= mask;
   *     }
   * ```
   */
  private fun getPerspectiveTypeMaskOnly(): TypeMask {
    TODO("Implement getPerspectiveTypeMaskOnly")
  }

  /**
   * C++ original:
   * ```cpp
   * void clearTypeMask(int mask) {
   *         // only allow a valid mask
   *         SkASSERT((mask & kAllMasks) == mask);
   *         fTypeMask &= ~mask;
   *     }
   * ```
   */
  private fun isTriviallyIdentity(): Boolean {
    TODO("Implement isTriviallyIdentity")
  }

  /**
   * C++ original:
   * ```cpp
   * TypeMask getPerspectiveTypeMaskOnly() const {
   *         if ((fTypeMask & kUnknown_Mask) &&
   *             !(fTypeMask & kOnlyPerspectiveValid_Mask)) {
   *             fTypeMask = this->computePerspectiveTypeMask();
   *         }
   *         return (TypeMask)(fTypeMask & 0xF);
   *     }
   * ```
   */
  private fun updateTranslateMask() {
    TODO("Implement updateTranslateMask")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTriviallyIdentity() const {
   *         if (fTypeMask & kUnknown_Mask) {
   *             return false;
   *         }
   *         return ((fTypeMask & 0xF) == 0);
   *     }
   * ```
   */
  private fun mapPointPerspective(pt: SkPoint): Int {
    TODO("Implement mapPointPerspective")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void updateTranslateMask() {
   *         if ((fMat[kMTransX] != 0) | (fMat[kMTransY] != 0)) {
   *             fTypeMask |= kTranslate_Mask;
   *         } else {
   *             fTypeMask &= ~kTranslate_Mask;
   *         }
   *     }
   * ```
   */
  private fun getMapPtsProc(): SkMatrixMapPtsProc {
    TODO("Implement getMapPtsProc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint SkMatrix::mapPointPerspective(SkPoint p) const {
   *     SkScalar x = sdot(p.fX, fMat[kMScaleX], p.fY, fMat[kMSkewX])  + fMat[kMTransX];
   *     SkScalar y = sdot(p.fX, fMat[kMSkewY],  p.fY, fMat[kMScaleY]) + fMat[kMTransY];
   *     SkScalar z = sdot(p.fX, fMat[kMPersp0], p.fY, fMat[kMPersp1]) + fMat[kMPersp2];
   *     if (z) {
   *         z = 1 / z;
   *     }
   *     return {x * z, y * z};
   * }
   * ```
   */
  private fun writeToMemory(buffer: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * MapPtsProc getMapPtsProc() const {
   *         return GetMapPtsProc(this->getType());
   *     }
   * ```
   */
  private fun readFromMemory(buffer: Unit?, length: ULong): ULong {
    TODO("Implement readFromMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMatrix::writeToMemory(void* buffer) const {
   *     // TODO write less for simple matrices
   *     static const size_t sizeInMemory = 9 * sizeof(SkScalar);
   *     if (buffer) {
   *         memcpy(buffer, fMat, sizeInMemory);
   *     }
   *     return sizeInMemory;
   * }
   * ```
   */
  private fun postIDiv(divx: Int, divy: Int): Boolean {
    TODO("Implement postIDiv")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMatrix::readFromMemory(const void* buffer, size_t length) {
   *     static const size_t sizeInMemory = 9 * sizeof(SkScalar);
   *     if (length < sizeInMemory) {
   *         return 0;
   *     }
   *     memcpy(fMat, buffer, sizeInMemory);
   *     this->setTypeMask(kUnknown_Mask);
   *     // Figure out the type now so that we're thread-safe
   *     (void)this->getType();
   *     return sizeInMemory;
   * }
   * ```
   */
  private fun doNormalizePerspective() {
    TODO("Implement doNormalizePerspective")
  }

  public enum class ScaleToFit {
    kFill_ScaleToFit,
    kStart_ScaleToFit,
    kCenter_ScaleToFit,
    kEnd_ScaleToFit,
  }

  public enum class TypeMask {
    kIdentity_Mask,
    kTranslate_Mask,
    kScale_Mask,
    kAffine_Mask,
    kPerspective_Mask,
  }

  public companion object {
    public val kMScaleX: Int = TODO("Initialize kMScaleX")

    public val kMSkewX: Int = TODO("Initialize kMSkewX")

    public val kMTransX: Int = TODO("Initialize kMTransX")

    public val kMSkewY: Int = TODO("Initialize kMSkewY")

    public val kMScaleY: Int = TODO("Initialize kMScaleY")

    public val kMTransY: Int = TODO("Initialize kMTransY")

    public val kMPersp0: Int = TODO("Initialize kMPersp0")

    public val kMPersp1: Int = TODO("Initialize kMPersp1")

    public val kMPersp2: Int = TODO("Initialize kMPersp2")

    public val kAScaleX: Int = TODO("Initialize kAScaleX")

    public val kASkewY: Int = TODO("Initialize kASkewY")

    public val kASkewX: Int = TODO("Initialize kASkewX")

    public val kAScaleY: Int = TODO("Initialize kAScaleY")

    public val kATransX: Int = TODO("Initialize kATransX")

    public val kATransY: Int = TODO("Initialize kATransY")

    private val kRectStaysRectMask: Int = TODO("Initialize kRectStaysRectMask")

    private val kOnlyPerspectiveValidMask: Int = TODO("Initialize kOnlyPerspectiveValidMask")

    private val kUnknownMask: Int = TODO("Initialize kUnknownMask")

    private val kORableMasks: Int = TODO("Initialize kORableMasks")

    private val kAllMasks: Int = TODO("Initialize kAllMasks")

    private val gMapPtsProcs: Array<SkMatrixMapPtsProc> = TODO("Initialize gMapPtsProcs")

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Scale(SkScalar sx, SkScalar sy) {
     *         SkMatrix m;
     *         m.setScale(sx, sy);
     *         return m;
     *     }
     * ```
     */
    public fun scale(sx: SkScalar, sy: SkScalar): SkMatrix {
      TODO("Implement scale")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Translate(SkScalar dx, SkScalar dy) {
     *         SkMatrix m;
     *         m.setTranslate(dx, dy);
     *         return m;
     *     }
     * ```
     */
    public fun translate(dx: SkScalar, dy: SkScalar): SkMatrix {
      TODO("Implement translate")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Translate(SkVector t) { return Translate(t.x(), t.y()); }
     * ```
     */
    public fun translate(t: SkVector): SkMatrix {
      TODO("Implement translate")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Translate(SkIVector t) { return Translate(t.x(), t.y()); }
     * ```
     */
    public fun translate(t: SkIVector): SkMatrix {
      TODO("Implement translate")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMatrix SkMatrix::ScaleTranslate(float sx, float sy, float tx, float ty) {
     *     uint8_t mask = 0;
     *     if (sx != 1 || sy != 1) {
     *         mask |= SkMatrix::kScale_Mask;
     *     }
     *     if (tx != 0.0f || ty != 0.0f) {
     *         mask |= SkMatrix::kTranslate_Mask;
     *     }
     *     if (sx != 0 && sy != 0) {
     *         mask |= SkMatrix::kRectStaysRect_Mask;
     *     }
     *     return SkMatrix(sx,  0, tx,
     *                      0, sy, ty,
     *                      0,  0,  1,
     *                      mask);
     * }
     * ```
     */
    public fun scaleTranslate(
      sx: Float,
      sy: Float,
      tx: Float,
      ty: Float,
    ): SkMatrix {
      TODO("Implement scaleTranslate")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix RotateDeg(SkScalar deg) {
     *         SkMatrix m;
     *         m.setRotate(deg);
     *         return m;
     *     }
     * ```
     */
    public fun rotateDeg(deg: SkScalar): SkMatrix {
      TODO("Implement rotateDeg")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix RotateDeg(SkScalar deg, SkPoint pt) {
     *         SkMatrix m;
     *         m.setRotate(deg, pt.x(), pt.y());
     *         return m;
     *     }
     * ```
     */
    public fun rotateDeg(deg: SkScalar, pt: SkPoint): SkMatrix {
      TODO("Implement rotateDeg")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix RotateRad(SkScalar rad) {
     *         return RotateDeg(SkRadiansToDegrees(rad));
     *     }
     * ```
     */
    public fun rotateRad(rad: SkScalar): SkMatrix {
      TODO("Implement rotateRad")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Skew(SkScalar kx, SkScalar ky) {
     *         SkMatrix m;
     *         m.setSkew(kx, ky);
     *         return m;
     *     }
     * ```
     */
    public fun skew(kx: SkScalar, ky: SkScalar): SkMatrix {
      TODO("Implement skew")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix MakeAll(SkScalar scaleX, SkScalar skewX,  SkScalar transX,
     *                                           SkScalar skewY,  SkScalar scaleY, SkScalar transY,
     *                                           SkScalar pers0, SkScalar pers1, SkScalar pers2) {
     *         SkMatrix m;
     *         m.setAll(scaleX, skewX, transX, skewY, scaleY, transY, pers0, pers1, pers2);
     *         return m;
     *     }
     * ```
     */
    public fun makeAll(
      scaleX: SkScalar,
      skewX: SkScalar,
      transX: SkScalar,
      skewY: SkScalar,
      scaleY: SkScalar,
      transY: SkScalar,
      pers0: SkScalar,
      pers1: SkScalar,
      pers2: SkScalar,
    ): SkMatrix {
      TODO("Implement makeAll")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkMatrix> SkMatrix::Rect2Rect(const SkRect& src, const SkRect& dst, ScaleToFit stf) {
     *     if (src.isEmpty()) {
     *         return {};
     *     }
     *
     *     SkScalar tx, sx = sk_ieee_float_divide(dst.width(), src.width());
     *     SkScalar ty, sy = sk_ieee_float_divide(dst.height(), src.height());
     *     bool     xLarger = false;
     *
     *     if (stf != kFill_ScaleToFit) {
     *         if (sx > sy) {
     *             xLarger = true;
     *             sx = sy;
     *         } else {
     *             sy = sx;
     *         }
     *     }
     *
     *     tx = dst.fLeft - src.fLeft * sx;
     *     ty = dst.fTop - src.fTop * sy;
     *     if (stf == kCenter_ScaleToFit || stf == kEnd_ScaleToFit) {
     *         SkScalar diff;
     *
     *         if (xLarger) {
     *             diff = dst.width() - src.width() * sy;
     *         } else {
     *             diff = dst.height() - src.height() * sy;
     *         }
     *
     *         if (stf == kCenter_ScaleToFit) {
     *             diff = SkScalarHalf(diff);
     *         }
     *
     *         if (xLarger) {
     *             tx += diff;
     *         } else {
     *             ty += diff;
     *         }
     *     }
     *     return ScaleTranslate(sx, sy, tx, ty);
     * }
     * ```
     */
    public fun rect2Rect(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit = TODO(),
    ): SkMatrix? {
      TODO("Implement rect2Rect")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix RectToRectOrIdentity(const SkRect& src, const SkRect& dst,
     *                                          ScaleToFit stf = kFill_ScaleToFit) {
     *         return Rect2Rect(src, dst, stf).value_or(SkMatrix::I());
     *     }
     * ```
     */
    public fun rectToRectOrIdentity(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit = TODO(),
    ): SkMatrix {
      TODO("Implement rectToRectOrIdentity")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix MakeRectToRect(const SkRect& src, const SkRect& dst, ScaleToFit stf) {
     *         if (auto mx = Rect2Rect(src, dst, stf)) {
     *             return *mx;
     *         }
     *         return SkMatrix::I();
     *     }
     * ```
     */
    public fun makeRectToRect(
      src: SkRect,
      dst: SkRect,
      stf: ScaleToFit,
    ): SkMatrix {
      TODO("Implement makeRectToRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix RectToRect(const SkRect& src, const SkRect& dst,
     *                                              ScaleToFit mode = kFill_ScaleToFit) {
     *         return MakeRectToRect(src, dst, mode);
     *     }
     * ```
     */
    public fun rectToRect(
      src: SkRect,
      dst: SkRect,
      mode: ScaleToFit = TODO(),
    ): SkMatrix {
      TODO("Implement rectToRect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkMatrix> SkMatrix::PolyToPoly(SkSpan<const SkPoint> src, SkSpan<const SkPoint> dst) {
     *     if (src.size() != dst.size() || src.size() > 4) {
     *         return {};
     *     }
     *
     *     const PolyMapProc gPolyMapProcs[] = {
     *         SkMatrix::Poly2Proc, SkMatrix::Poly3Proc, SkMatrix::Poly4Proc
     *     };
     *
     *     switch (src.size()) {
     *         case 0: return SkMatrix::I();
     *         case 1: return SkMatrix::Translate(dst[0] - src[0]);
     *         case 2: [[fallthrough]];
     *         case 3: [[fallthrough]];
     *         case 4: {
     *             PolyMapProc proc = gPolyMapProcs[src.size() - 2];
     *
     *             SkMatrix tempMap;
     *             if (!proc(src.data(), &tempMap)) {
     *                 return {};
     *             }
     *             auto inverse = tempMap.invert();
     *             if (!inverse) {
     *                 return {};
     *             }
     *             if (!proc(dst.data(), &tempMap)) {
     *                 return {};
     *             }
     *             return tempMap * inverse.value();
     *         }
     *     }
     *     SkUNREACHABLE;
     * }
     * ```
     */
    public fun polyToPoly(src: SkSpan<SkPoint>, dst: SkSpan<SkPoint>): SkMatrix? {
      TODO("Implement polyToPoly")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::SetAffineIdentity(SkScalar affine[6]) {
     *     affine[kAScaleX] = 1;
     *     affine[kASkewY] = 0;
     *     affine[kASkewX] = 0;
     *     affine[kAScaleY] = 1;
     *     affine[kATransX] = 0;
     *     affine[kATransY] = 0;
     * }
     * ```
     */
    public fun setAffineIdentity(affine: Array<SkScalar>) {
      TODO("Implement setAffineIdentity")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkMatrix& SkMatrix::I() {
     *     static constexpr SkMatrix identity;
     *     SkASSERT(identity.isIdentity());
     *     return identity;
     * }
     * ```
     */
    public fun i(): SkMatrix {
      TODO("Implement i")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkMatrix& SkMatrix::InvalidMatrix() {
     *     static constexpr SkMatrix invalid(SK_ScalarMax, SK_ScalarMax, SK_ScalarMax,
     *                                       SK_ScalarMax, SK_ScalarMax, SK_ScalarMax,
     *                                       SK_ScalarMax, SK_ScalarMax, SK_ScalarMax,
     *                                       kTranslate_Mask | kScale_Mask |
     *                                       kAffine_Mask | kPerspective_Mask);
     *     return invalid;
     * }
     * ```
     */
    public fun invalidMatrix(): SkMatrix {
      TODO("Implement invalidMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix Concat(const SkMatrix& a, const SkMatrix& b) {
     *         SkMatrix result;
     *         result.setConcat(a, b);
     *         return result;
     *     }
     * ```
     */
    public fun concat(a: SkMatrix, b: SkMatrix): SkMatrix {
      TODO("Implement concat")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::ComputeInv(SkScalar dst[9], const SkScalar src[9], double invDet, bool isPersp) {
     *     SkASSERT(src != dst);
     *     SkASSERT(src && dst);
     *
     *     if (isPersp) {
     *         dst[kMScaleX] = scross_dscale(src[kMScaleY], src[kMPersp2], src[kMTransY], src[kMPersp1], invDet);
     *         dst[kMSkewX]  = scross_dscale(src[kMTransX], src[kMPersp1], src[kMSkewX],  src[kMPersp2], invDet);
     *         dst[kMTransX] = scross_dscale(src[kMSkewX],  src[kMTransY], src[kMTransX], src[kMScaleY], invDet);
     *
     *         dst[kMSkewY]  = scross_dscale(src[kMTransY], src[kMPersp0], src[kMSkewY],  src[kMPersp2], invDet);
     *         dst[kMScaleY] = scross_dscale(src[kMScaleX], src[kMPersp2], src[kMTransX], src[kMPersp0], invDet);
     *         dst[kMTransY] = scross_dscale(src[kMTransX], src[kMSkewY],  src[kMScaleX], src[kMTransY], invDet);
     *
     *         dst[kMPersp0] = scross_dscale(src[kMSkewY],  src[kMPersp1], src[kMScaleY], src[kMPersp0], invDet);
     *         dst[kMPersp1] = scross_dscale(src[kMSkewX],  src[kMPersp0], src[kMScaleX], src[kMPersp1], invDet);
     *         dst[kMPersp2] = scross_dscale(src[kMScaleX], src[kMScaleY], src[kMSkewX],  src[kMSkewY],  invDet);
     *     } else {   // not perspective
     *         dst[kMScaleX] = SkDoubleToScalar(src[kMScaleY] * invDet);
     *         dst[kMSkewX]  = SkDoubleToScalar(-src[kMSkewX] * invDet);
     *         dst[kMTransX] = dcross_dscale(src[kMSkewX], src[kMTransY], src[kMScaleY], src[kMTransX], invDet);
     *
     *         dst[kMSkewY]  = SkDoubleToScalar(-src[kMSkewY] * invDet);
     *         dst[kMScaleY] = SkDoubleToScalar(src[kMScaleX] * invDet);
     *         dst[kMTransY] = dcross_dscale(src[kMSkewY], src[kMTransX], src[kMScaleX], src[kMTransY], invDet);
     *
     *         dst[kMPersp0] = 0;
     *         dst[kMPersp1] = 0;
     *         dst[kMPersp2] = 1;
     *     }
     * }
     * ```
     */
    private fun computeInv(
      dst: Array<SkScalar>,
      src: Array<SkScalar>,
      invDet: Double,
      isPersp: Boolean,
    ) {
      TODO("Implement computeInv")
    }

    /**
     * C++ original:
     * ```cpp
     * static MapPtsProc GetMapPtsProc(TypeMask mask) {
     *         SkASSERT((mask & ~kAllMasks) == 0);
     *         return gMapPtsProcs[mask & kAllMasks];
     *     }
     * ```
     */
    private fun getMapPtsProc(mask: TypeMask): SkMatrixMapPtsProc {
      TODO("Implement getMapPtsProc")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkMatrix::Poly2Proc(const SkPoint srcPt[], SkMatrix* dst) {
     *     dst->fMat[kMScaleX] = srcPt[1].fY - srcPt[0].fY;
     *     dst->fMat[kMSkewY]  = srcPt[0].fX - srcPt[1].fX;
     *     dst->fMat[kMPersp0] = 0;
     *
     *     dst->fMat[kMSkewX]  = srcPt[1].fX - srcPt[0].fX;
     *     dst->fMat[kMScaleY] = srcPt[1].fY - srcPt[0].fY;
     *     dst->fMat[kMPersp1] = 0;
     *
     *     dst->fMat[kMTransX] = srcPt[0].fX;
     *     dst->fMat[kMTransY] = srcPt[0].fY;
     *     dst->fMat[kMPersp2] = 1;
     *     dst->setTypeMask(kUnknown_Mask);
     *     return true;
     * }
     * ```
     */
    private fun poly2Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly2Proc")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkMatrix::Poly3Proc(const SkPoint srcPt[], SkMatrix* dst) {
     *     dst->fMat[kMScaleX] = srcPt[2].fX - srcPt[0].fX;
     *     dst->fMat[kMSkewY]  = srcPt[2].fY - srcPt[0].fY;
     *     dst->fMat[kMPersp0] = 0;
     *
     *     dst->fMat[kMSkewX]  = srcPt[1].fX - srcPt[0].fX;
     *     dst->fMat[kMScaleY] = srcPt[1].fY - srcPt[0].fY;
     *     dst->fMat[kMPersp1] = 0;
     *
     *     dst->fMat[kMTransX] = srcPt[0].fX;
     *     dst->fMat[kMTransY] = srcPt[0].fY;
     *     dst->fMat[kMPersp2] = 1;
     *     dst->setTypeMask(kUnknown_Mask);
     *     return true;
     * }
     * ```
     */
    private fun poly3Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly3Proc")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkMatrix::Poly4Proc(const SkPoint srcPt[], SkMatrix* dst) {
     *     float   a1, a2;
     *     float   x0, y0, x1, y1, x2, y2;
     *
     *     x0 = srcPt[2].fX - srcPt[0].fX;
     *     y0 = srcPt[2].fY - srcPt[0].fY;
     *     x1 = srcPt[2].fX - srcPt[1].fX;
     *     y1 = srcPt[2].fY - srcPt[1].fY;
     *     x2 = srcPt[2].fX - srcPt[3].fX;
     *     y2 = srcPt[2].fY - srcPt[3].fY;
     *
     *     /* check if abs(x2) > abs(y2) */
     *     if ( x2 > 0 ? y2 > 0 ? x2 > y2 : x2 > -y2 : y2 > 0 ? -x2 > y2 : x2 < y2) {
     *         float denom = sk_ieee_float_divide(x1 * y2, x2) - y1;
     *         if (checkForZero(denom)) {
     *             return false;
     *         }
     *         a1 = (((x0 - x1) * y2 / x2) - y0 + y1) / denom;
     *     } else {
     *         float denom = x1 - sk_ieee_float_divide(y1 * x2, y2);
     *         if (checkForZero(denom)) {
     *             return false;
     *         }
     *         a1 = (x0 - x1 - sk_ieee_float_divide((y0 - y1) * x2, y2)) / denom;
     *     }
     *
     *     /* check if abs(x1) > abs(y1) */
     *     if ( x1 > 0 ? y1 > 0 ? x1 > y1 : x1 > -y1 : y1 > 0 ? -x1 > y1 : x1 < y1) {
     *         float denom = y2 - sk_ieee_float_divide(x2 * y1, x1);
     *         if (checkForZero(denom)) {
     *             return false;
     *         }
     *         a2 = (y0 - y2 - sk_ieee_float_divide((x0 - x2) * y1, x1)) / denom;
     *     } else {
     *         float denom = sk_ieee_float_divide(y2 * x1, y1) - x2;
     *         if (checkForZero(denom)) {
     *             return false;
     *         }
     *         a2 = (sk_ieee_float_divide((y0 - y2) * x1, y1) - x0 + x2) / denom;
     *     }
     *
     *     dst->fMat[kMScaleX] = a2 * srcPt[3].fX + srcPt[3].fX - srcPt[0].fX;
     *     dst->fMat[kMSkewY]  = a2 * srcPt[3].fY + srcPt[3].fY - srcPt[0].fY;
     *     dst->fMat[kMPersp0] = a2;
     *
     *     dst->fMat[kMSkewX]  = a1 * srcPt[1].fX + srcPt[1].fX - srcPt[0].fX;
     *     dst->fMat[kMScaleY] = a1 * srcPt[1].fY + srcPt[1].fY - srcPt[0].fY;
     *     dst->fMat[kMPersp1] = a1;
     *
     *     dst->fMat[kMTransX] = srcPt[0].fX;
     *     dst->fMat[kMTransY] = srcPt[0].fY;
     *     dst->fMat[kMPersp2] = 1;
     *     dst->setTypeMask(kUnknown_Mask);
     *     return true;
     * }
     * ```
     */
    private fun poly4Proc(srcPt: Array<SkPoint>, dst: SkMatrix?): Boolean {
      TODO("Implement poly4Proc")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::Identity_pts(const SkMatrix& m, SkPoint dst[], const SkPoint src[], int count) {
     *     SkASSERT(m.getType() == 0);
     *
     *     if (dst != src && count > 0) {
     *         memcpy(dst, src, count * sizeof(SkPoint));
     *     }
     * }
     * ```
     */
    private fun identityPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement identityPts")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::Trans_pts(const SkMatrix& m, SkPoint dst[], const SkPoint src[], int count) {
     *     SkASSERT(m.getType() <= SkMatrix::kTranslate_Mask);
     *     if (count > 0) {
     *         SkScalar tx = m.getTranslateX();
     *         SkScalar ty = m.getTranslateY();
     *         if (count & 1) {
     *             dst->fX = src->fX + tx;
     *             dst->fY = src->fY + ty;
     *             src += 1;
     *             dst += 1;
     *         }
     *         skvx::float4 trans4(tx, ty, tx, ty);
     *         count >>= 1;
     *         if (count & 1) {
     *             (skvx::float4::Load(src) + trans4).store(dst);
     *             src += 2;
     *             dst += 2;
     *         }
     *         count >>= 1;
     *         for (int i = 0; i < count; ++i) {
     *             (skvx::float4::Load(src+0) + trans4).store(dst+0);
     *             (skvx::float4::Load(src+2) + trans4).store(dst+2);
     *             src += 4;
     *             dst += 4;
     *         }
     *     }
     * }
     * ```
     */
    private fun transPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement transPts")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::Scale_pts(const SkMatrix& m, SkPoint dst[], const SkPoint src[], int count) {
     *     SkASSERT(m.getType() <= (SkMatrix::kScale_Mask | SkMatrix::kTranslate_Mask));
     *     if (count > 0) {
     *         SkScalar tx = m.getTranslateX();
     *         SkScalar ty = m.getTranslateY();
     *         SkScalar sx = m.getScaleX();
     *         SkScalar sy = m.getScaleY();
     *         skvx::float4 trans4(tx, ty, tx, ty);
     *         skvx::float4 scale4(sx, sy, sx, sy);
     *         if (count & 1) {
     *             skvx::float4 p(src->fX, src->fY, 0, 0);
     *             p = p * scale4 + trans4;
     *             dst->fX = p[0];
     *             dst->fY = p[1];
     *             src += 1;
     *             dst += 1;
     *         }
     *         count >>= 1;
     *         if (count & 1) {
     *             (skvx::float4::Load(src) * scale4 + trans4).store(dst);
     *             src += 2;
     *             dst += 2;
     *         }
     *         count >>= 1;
     *         for (int i = 0; i < count; ++i) {
     *             (skvx::float4::Load(src+0) * scale4 + trans4).store(dst+0);
     *             (skvx::float4::Load(src+2) * scale4 + trans4).store(dst+2);
     *             src += 4;
     *             dst += 4;
     *         }
     *     }
     * }
     * ```
     */
    private fun scalePts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement scalePts")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ScaleTrans_pts(const SkMatrix&, SkPoint dst[], const SkPoint[],
     *                                int count)
     * ```
     */
    private fun scaleTransPts(
      param0: SkMatrix,
      dst: Array<SkPoint>,
      param2: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement scaleTransPts")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::Persp_pts(const SkMatrix& m, SkPoint dst[],
     *                          const SkPoint src[], int count) {
     *     SkASSERT(m.hasPerspective());
     *
     *     if (count > 0) {
     *         do {
     *             SkScalar sy = src->fY;
     *             SkScalar sx = src->fX;
     *             src += 1;
     *
     *             SkScalar x = sdot(sx, m.fMat[kMScaleX], sy, m.fMat[kMSkewX])  + m.fMat[kMTransX];
     *             SkScalar y = sdot(sx, m.fMat[kMSkewY],  sy, m.fMat[kMScaleY]) + m.fMat[kMTransY];
     *             SkScalar z = sdot(sx, m.fMat[kMPersp0], sy, m.fMat[kMPersp1]) + m.fMat[kMPersp2];
     *             if (z) {
     *                 z = 1 / z;
     *             }
     *
     *             dst->fY = y * z;
     *             dst->fX = x * z;
     *             dst += 1;
     *         } while (--count);
     *     }
     * }
     * ```
     */
    private fun perspPts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement perspPts")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrix::Affine_vpts(const SkMatrix& m, SkPoint dst[], const SkPoint src[], int count) {
     *     SkASSERT(m.getType() != SkMatrix::kPerspective_Mask);
     *     if (count > 0) {
     *         SkScalar tx = m.getTranslateX();
     *         SkScalar ty = m.getTranslateY();
     *         SkScalar sx = m.getScaleX();
     *         SkScalar sy = m.getScaleY();
     *         SkScalar kx = m.getSkewX();
     *         SkScalar ky = m.getSkewY();
     *         skvx::float4 trans4(tx, ty, tx, ty);
     *         skvx::float4 scale4(sx, sy, sx, sy);
     *         skvx::float4  skew4(kx, ky, kx, ky);    // applied to swizzle of src4
     *         bool trailingElement = (count & 1);
     *         count >>= 1;
     *         skvx::float4 src4;
     *         for (int i = 0; i < count; ++i) {
     *             src4 = skvx::float4::Load(src);
     *             skvx::float4 swz4 = skvx::shuffle<1,0,3,2>(src4);  // y0 x0, y1 x1
     *             (src4 * scale4 + swz4 * skew4 + trans4).store(dst);
     *             src += 2;
     *             dst += 2;
     *         }
     *         if (trailingElement) {
     *             // We use the same logic here to ensure that the math stays consistent throughout, even
     *             // though the high float2 is ignored.
     *             src4.lo = skvx::float2::Load(src);
     *             skvx::float4 swz4 = skvx::shuffle<1,0,3,2>(src4);  // y0 x0, y1 x1
     *             (src4 * scale4 + swz4 * skew4 + trans4).lo.store(dst);
     *         }
     *     }
     * }
     * ```
     */
    private fun affineVpts(
      m: SkMatrix,
      dst: Array<SkPoint>,
      src: Array<SkPoint>,
      count: Int,
    ) {
      TODO("Implement affineVpts")
    }
  }
}
