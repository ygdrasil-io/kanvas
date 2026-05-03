package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.core.SkCanvasVirtualEnforcer
import org.skia.core.SkDrawShadowRec
import org.skia.core.SkFilterMode
import org.skia.core.SkPicture
import org.skia.core.SkTextBlob
import org.skia.core.SkVertices
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.ImageSetEntry
import undefined.Lattice
import undefined.PointMode
import undefined.QuadAAFlags
import undefined.SaveLayerRec
import undefined.SkColor4f
import undefined.SrcRectConstraint

/**
 * C++ original:
 * ```cpp
 * class SK_API SkNoDrawCanvas : public SkCanvasVirtualEnforcer<SkCanvas> {
 * public:
 *     SkNoDrawCanvas(int width, int height);
 *     explicit SkNoDrawCanvas(const SkIRect&);
 *
 *     // Optimization to reset state to be the same as after construction.
 *     void resetCanvas(int w, int h)        { this->resetForNextPicture(SkIRect::MakeWH(w, h)); }
 *     void resetCanvas(const SkIRect& rect) { this->resetForNextPicture(rect); }
 *
 * protected:
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec& rec) override;
 *     bool onDoSaveBehind(const SkRect*) override;
 *
 *     // No-op overrides for aborting rasterization earlier than SkNullBlitter.
 *     void onDrawAnnotation(const SkRect&, const char[], SkData*) override {}
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override {}
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override {}
 *     void onDrawTextBlob(const SkTextBlob*, SkScalar, SkScalar, const SkPaint&) override {}
 *     void onDrawPatch(const SkPoint[12], const SkColor[4], const SkPoint[4], SkBlendMode,
 *                      const SkPaint&) override {}
 *
 *     void onDrawPaint(const SkPaint&) override {}
 *     void onDrawBehind(const SkPaint&) override {}
 *     void onDrawPoints(PointMode, size_t, const SkPoint[], const SkPaint&) override {}
 *     void onDrawRect(const SkRect&, const SkPaint&) override {}
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override {}
 *     void onDrawOval(const SkRect&, const SkPaint&) override {}
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override {}
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override {}
 *     void onDrawPath(const SkPath&, const SkPaint&) override {}
 *
 *     void onDrawImage2(const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&,
 *                       const SkPaint*) override {}
 *     void onDrawImageRect2(const SkImage*, const SkRect&, const SkRect&, const SkSamplingOptions&,
 *                           const SkPaint*, SrcRectConstraint) override {}
 *     void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect&, SkFilterMode,
 *                              const SkPaint*) override {}
 *     void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect[], const SkColor[], int,
 *                   SkBlendMode, const SkSamplingOptions&, const SkRect*, const SkPaint*) override {}
 *
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override {}
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override {}
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override {}
 *
 *     void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&,
 *                           SkBlendMode) override {}
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[], int, const SkPoint[], const SkMatrix[],
 *                                const SkSamplingOptions&, const SkPaint*,
 *                                SrcRectConstraint) override {}
 *
 * private:
 *     using INHERITED = SkCanvasVirtualEnforcer<SkCanvas>;
 * }
 * ```
 */
public open class SkNoDrawCanvas public constructor(
  width: Int,
  height: Int,
) : SkCanvasVirtualEnforcer(),
    SkCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkNoDrawCanvas::SkNoDrawCanvas(int width, int height)
   *     : INHERITED(SkIRect::MakeWH(width, height)) {}
   * ```
   */
  public constructor(bounds: SkIRect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCanvas(int w, int h)        { this->resetForNextPicture(SkIRect::MakeWH(w, h)); }
   * ```
   */
  public fun resetCanvas(w: Int, h: Int) {
    TODO("Implement resetCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCanvas(const SkIRect& rect) { this->resetForNextPicture(rect); }
   * ```
   */
  public fun resetCanvas(rect: SkIRect) {
    TODO("Implement resetCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy SkNoDrawCanvas::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     (void)this->INHERITED::getSaveLayerStrategy(rec);
   *     return kNoLayer_SaveLayerStrategy;
   * }
   * ```
   */
  protected override fun getSaveLayerStrategy(rec: SaveLayerRec): Int {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkNoDrawCanvas::onDoSaveBehind(const SkRect*) {
   *     return false;
   * }
   * ```
   */
  protected override fun onDoSaveBehind(param0: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawAnnotation(const SkRect&, const char[], SkData*) override {}
   * ```
   */
  protected override fun onDrawAnnotation(
    param0: SkRect,
    param1: CharArray,
    param2: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawDRRect(
    param0: SkRRect,
    param1: SkRRect,
    param2: SkPaint,
  ) {
    TODO("Implement onDrawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawDrawable(SkDrawable*, const SkMatrix*) override {}
   * ```
   */
  protected override fun onDrawDrawable(param0: SkDrawable?, param1: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawTextBlob(const SkTextBlob*, SkScalar, SkScalar, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawTextBlob(
    param0: SkTextBlob?,
    param1: SkScalar,
    param2: SkScalar,
    param3: SkPaint,
  ) {
    TODO("Implement onDrawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPatch(const SkPoint[12], const SkColor[4], const SkPoint[4], SkBlendMode,
   *                      const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawPatch(
    param0: Array<SkPoint>,
    param1: Array<SkColor>,
    param2: Array<SkPoint>,
    param3: SkBlendMode,
    param4: SkPaint,
  ) {
    TODO("Implement onDrawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPaint(const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawPaint(param0: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawBehind(const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawBehind(param0: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPoints(PointMode, size_t, const SkPoint[], const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawPoints(
    param0: PointMode,
    param1: ULong,
    param2: Array<SkPoint>,
    param3: SkPaint,
  ) {
    TODO("Implement onDrawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRect(const SkRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawRect(param0: SkRect, param1: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRegion(const SkRegion&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawRegion(param0: SkRegion, param1: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawOval(const SkRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawOval(param0: SkRect, param1: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawArc(
    param0: SkRect,
    param1: SkScalar,
    param2: SkScalar,
    param3: Boolean,
    param4: SkPaint,
  ) {
    TODO("Implement onDrawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRRect(const SkRRect&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawRRect(param0: SkRRect, param1: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPath(const SkPath&, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawPath(param0: SkPath, param1: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawImage2(const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&,
   *                       const SkPaint*) override {}
   * ```
   */
  protected override fun onDrawImage2(
    param0: SkImage?,
    param1: SkScalar,
    param2: SkScalar,
    param3: SkSamplingOptions,
    param4: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawImageRect2(const SkImage*, const SkRect&, const SkRect&, const SkSamplingOptions&,
   *                           const SkPaint*, SrcRectConstraint) override {}
   * ```
   */
  protected override fun onDrawImageRect2(
    param0: SkImage?,
    param1: SkRect,
    param2: SkRect,
    param3: SkSamplingOptions,
    param4: SkPaint?,
    param5: SrcRectConstraint,
  ) {
    TODO("Implement onDrawImageRect2")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect&, SkFilterMode,
   *                              const SkPaint*) override {}
   * ```
   */
  protected override fun onDrawImageLattice2(
    param0: SkImage?,
    param1: Lattice,
    param2: SkRect,
    param3: SkFilterMode,
    param4: SkPaint?,
  ) {
    TODO("Implement onDrawImageLattice2")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect[], const SkColor[], int,
   *                   SkBlendMode, const SkSamplingOptions&, const SkRect*, const SkPaint*) override {}
   * ```
   */
  protected override fun onDrawAtlas2(
    param0: SkImage?,
    param1: Array<SkRSXform>,
    param2: Array<SkRect>,
    param3: Array<SkColor>,
    param4: Int,
    param5: SkBlendMode,
    param6: SkSamplingOptions,
    param7: SkRect?,
    param8: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawVerticesObject(
    param0: SkVertices?,
    param1: SkBlendMode,
    param2: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override {}
   * ```
   */
  protected override fun onDrawShadowRec(param0: SkPath, param1: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override {}
   * ```
   */
  protected override fun onDrawPicture(
    param0: SkPicture?,
    param1: SkMatrix?,
    param2: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&,
   *                           SkBlendMode) override {}
   * ```
   */
  protected override fun onDrawEdgeAAQuad(
    param0: SkRect,
    param1: Array<SkPoint>,
    param2: QuadAAFlags,
    param3: SkColor4f,
    param4: SkBlendMode,
  ) {
    TODO("Implement onDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawEdgeAAImageSet2(const ImageSetEntry[], int, const SkPoint[], const SkMatrix[],
   *                                const SkSamplingOptions&, const SkPaint*,
   *                                SrcRectConstraint) override {}
   * ```
   */
  protected override fun onDrawEdgeAAImageSet2(
    param0: Array<ImageSetEntry>,
    param1: Int,
    param2: Array<SkPoint>,
    param3: Array<SkMatrix>,
    param4: SkSamplingOptions,
    param5: SkPaint?,
    param6: SrcRectConstraint,
  ) {
    TODO("Implement onDrawEdgeAAImageSet2")
  }
}
