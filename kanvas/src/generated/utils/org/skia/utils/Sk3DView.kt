package org.skia.utils

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkNoncopyable
import org.skia.math.SkMatrix
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API Sk3DView : SkNoncopyable {
 * public:
 *     Sk3DView();
 *     ~Sk3DView();
 *
 *     void save();
 *     void restore();
 *
 *     void translate(SkScalar x, SkScalar y, SkScalar z);
 *     void rotateX(SkScalar deg);
 *     void rotateY(SkScalar deg);
 *     void rotateZ(SkScalar deg);
 *
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     void setCameraLocation(SkScalar x, SkScalar y, SkScalar z);
 *     SkScalar getCameraLocationX() const;
 *     SkScalar getCameraLocationY() const;
 *     SkScalar getCameraLocationZ() const;
 * #endif
 *
 *     void getMatrix(SkMatrix*) const;
 *     void applyToCanvas(SkCanvas*) const;
 *
 *     SkScalar dotWithNormal(SkScalar dx, SkScalar dy, SkScalar dz) const;
 *
 * private:
 *     struct Rec {
 *         Rec*    fNext;
 *         SkM44   fMatrix;
 *     };
 *     Rec*        fRec;
 *     Rec         fInitialRec;
 *     SkCamera3D  fCamera;
 * }
 * ```
 */
public open class Sk3DView public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * Rec*        fRec
   * ```
   */
  private var fRec: Rec? = TODO("Initialize fRec")

  /**
   * C++ original:
   * ```cpp
   * Rec         fInitialRec
   * ```
   */
  private var fInitialRec: Rec = TODO("Initialize fInitialRec")

  /**
   * C++ original:
   * ```cpp
   * SkCamera3D  fCamera
   * ```
   */
  private var fCamera: SkCamera3D = TODO("Initialize fCamera")

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::save() {
   *     Rec* rec = new Rec;
   *     rec->fNext = fRec;
   *     rec->fMatrix = fRec->fMatrix;
   *     fRec = rec;
   * }
   * ```
   */
  public fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::restore() {
   *     SkASSERT(fRec != &fInitialRec);
   *     Rec* next = fRec->fNext;
   *     delete fRec;
   *     fRec = next;
   * }
   * ```
   */
  public fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::translate(SkScalar x, SkScalar y, SkScalar z) {
   *     fRec->fMatrix.preTranslate(x, y, z);
   * }
   * ```
   */
  public fun translate(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar,
  ) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::rotateX(SkScalar deg) {
   *     fRec->fMatrix.preConcat(SkM44::Rotate({1, 0, 0}, deg * SK_ScalarPI / 180));
   * }
   * ```
   */
  public fun rotateX(deg: SkScalar) {
    TODO("Implement rotateX")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::rotateY(SkScalar deg) {
   *     fRec->fMatrix.preConcat(SkM44::Rotate({0,-1, 0}, deg * SK_ScalarPI / 180));
   * }
   * ```
   */
  public fun rotateY(deg: SkScalar) {
    TODO("Implement rotateY")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::rotateZ(SkScalar deg) {
   *     fRec->fMatrix.preConcat(SkM44::Rotate({0, 0, 1}, deg * SK_ScalarPI / 180));
   * }
   * ```
   */
  public fun rotateZ(deg: SkScalar) {
    TODO("Implement rotateZ")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::getMatrix(SkMatrix* matrix) const {
   *     if (matrix != nullptr) {
   *         SkPatch3D   patch;
   *         patch.transform(fRec->fMatrix);
   *         fCamera.patchToMatrix(patch, matrix);
   *     }
   * }
   * ```
   */
  public fun getMatrix(matrix: SkMatrix?) {
    TODO("Implement getMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void Sk3DView::applyToCanvas(SkCanvas* canvas) const {
   *     SkMatrix    matrix;
   *
   *     this->getMatrix(&matrix);
   *     canvas->concat(matrix);
   * }
   * ```
   */
  public fun applyToCanvas(canvas: SkCanvas?) {
    TODO("Implement applyToCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Sk3DView::dotWithNormal(SkScalar x, SkScalar y, SkScalar z) const {
   *     SkPatch3D   patch;
   *     patch.transform(fRec->fMatrix);
   *     return patch.dotWith(x, y, z);
   * }
   * ```
   */
  public fun dotWithNormal(
    dx: SkScalar,
    dy: SkScalar,
    dz: SkScalar,
  ): Int {
    TODO("Implement dotWithNormal")
  }

  public open class Rec public constructor(
    public var fNext: undefined.Rec?,
    public var fMatrix: Int,
  )
}
