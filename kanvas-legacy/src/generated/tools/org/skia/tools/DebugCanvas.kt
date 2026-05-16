package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.core.SkCanvasVirtualEnforcer
import org.skia.core.SkClipOp
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
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.memory.SkTDArray
import undefined.ClipEdgeStyle
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
 * class DebugCanvas : public SkCanvasVirtualEnforcer<SkCanvas> {
 * public:
 *     DebugCanvas(int width, int height);
 *
 *     explicit DebugCanvas(SkIRect bounds);
 *
 *     ~DebugCanvas() override;
 *
 *     /**
 *      * Provide a DebugLayerManager for mskp files containing layer information
 *      * when set this DebugCanvas will attempt to parse layer info from annotations.
 *      * it will store layer pictures to the layer manager, and interpret some drawImageRects
 *      * as layer draws, deferring to the layer manager for images.
 *      * Provide a frame number that will be passed to all layer manager functions to identify this
 *      * DebugCanvas.
 *      *
 *      * Used only in wasm debugger animations.
 *      */
 *     void setLayerManagerAndFrame(DebugLayerManager* lm, int frame) {
 *         fLayerManager = lm;
 *         fFrame = frame;
 *     }
 *
 *     /**
 *      * Enable or disable overdraw visualization
 *      */
 *     void setOverdrawViz(bool overdrawViz);
 *
 *     bool getOverdrawViz() const { return fOverdrawViz; }
 *
 *     /**
 *      * Set the color of the clip visualization. An alpha of zero renders the clip invisible.
 *      */
 *     void setClipVizColor(SkColor clipVizColor) { this->fClipVizColor = clipVizColor; }
 *
 *     void setAndroidClipViz(bool enable) { this->fShowAndroidClip = enable; }
 *
 *     void setOriginVisible(bool enable) { this->fShowOrigin = enable; }
 *
 *     void setDrawGpuOpBounds(bool drawGpuOpBounds) { fDrawGpuOpBounds = drawGpuOpBounds; }
 *
 *     bool getDrawGpuOpBounds() const { return fDrawGpuOpBounds; }
 *
 *     /**
 *         Executes all draw calls to the canvas.
 *         @param canvas  The canvas being drawn to
 *      */
 *     void draw(SkCanvas* canvas);
 *
 *     /**
 *         Executes the draw calls up to the specified index.
 *         Does not clear the canvas to transparent black first,
 *         if needed, caller should do that first.
 *         @param canvas  The canvas being drawn to
 *         @param index  The index of the final command being executed
 *         @param m an optional Mth gpu op to highlight, or -1
 *      */
 *     void drawTo(SkCanvas* canvas, int index, int m = -1);
 *
 *     /**
 *         Returns the most recently calculated transformation matrix
 *      */
 *     const SkM44& getCurrentMatrix() { return fMatrix; }
 *
 *     /**
 *         Returns the most recently calculated clip
 *      */
 *     const SkIRect& getCurrentClip() { return fClip; }
 *
 *     /**
 *         Removes the command at the specified index
 *         @param index  The index of the command to delete
 *      */
 *     void deleteDrawCommandAt(int index);
 *
 *     /**
 *         Returns the draw command at the given index.
 *         @param index  The index of the command
 *      */
 *     DrawCommand* getDrawCommandAt(int index) const;
 *
 *     /**
 *         Returns length of draw command vector.
 *      */
 *     int getSize() const { return fCommandVector.size(); }
 *
 *     /**
 *         Toggles the visibility / execution of the draw command at index i with
 *         the value of toggle.
 *      */
 *     void toggleCommand(int index, bool toggle);
 *
 *     /**
 *         Returns a JSON object representing all commands in the picture.
 *         The encoder may use the UrlDataManager to store binary data such
 *         as images, referring to them via URLs embedded in the JSON.
 *      */
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager, SkCanvas*);
 *
 *     void toJSONOpsTask(SkJSONWriter& writer, SkCanvas*);
 *
 *     void detachCommands(SkTDArray<DrawCommand*>* dst) { fCommandVector.swap(*dst); }
 *
 *     /**
 *         Returns a map from image IDs to command indices where they are used.
 *      */
 *     std::map<int, std::vector<int>> getImageIdToCommandMap(UrlDataManager& udm) const;
 *
 * protected:
 *     void              willSave() override;
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec&) override;
 *     bool              onDoSaveBehind(const SkRect*) override;
 *     void              willRestore() override;
 *
 *     void didConcat44(const SkM44&) override;
 *     void didSetM44(const SkM44&) override;
 *     void didScale(SkScalar, SkScalar) override;
 *     void didTranslate(SkScalar, SkScalar) override;
 *
 *     void onDrawAnnotation(const SkRect&, const char[], SkData*) override;
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *     void onDrawTextBlob(const SkTextBlob* blob,
 *                         SkScalar          x,
 *                         SkScalar          y,
 *                         const SkPaint&    paint) override;
 *
 *     void onDrawPatch(const SkPoint cubics[12],
 *                      const SkColor colors[4],
 *                      const SkPoint texCoords[4],
 *                      SkBlendMode,
 *                      const SkPaint& paint) override;
 *     void onDrawPaint(const SkPaint&) override;
 *     void onDrawBehind(const SkPaint&) override;
 *
 *     void onDrawRect(const SkRect&, const SkPaint&) override;
 *     void onDrawOval(const SkRect&, const SkPaint&) override;
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override;
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override;
 *     void onDrawPoints(PointMode, size_t count, const SkPoint pts[], const SkPaint&) override;
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override;
 *     void onDrawPath(const SkPath&, const SkPaint&) override;
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override;
 *
 *     void onDrawImage2(const SkImage*, SkScalar, SkScalar, const SkSamplingOptions&,
 *                       const SkPaint*) override;
 *     void onDrawImageRect2(const SkImage*, const SkRect&, const SkRect&, const SkSamplingOptions&,
 *                           const SkPaint*, SrcRectConstraint) override;
 *     void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect&, SkFilterMode,
 *                              const SkPaint*) override;
 *     void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect[], const SkColor[], int,
 *                      SkBlendMode, const SkSamplingOptions&, const SkRect*, const SkPaint*) override;
 *
 *     void onClipRect(const SkRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipRRect(const SkRRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipPath(const SkPath&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipShader(sk_sp<SkShader>, SkClipOp) override;
 *     void onClipRegion(const SkRegion& region, SkClipOp) override;
 *     void onResetClip() override;
 *
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override;
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *
 *     void onDrawEdgeAAQuad(const SkRect&,
 *                           const SkPoint[4],
 *                           QuadAAFlags,
 *                           const SkColor4f&,
 *                           SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[],
 *                                int count,
 *                                const SkPoint[],
 *                                const SkMatrix[],
 *                                const SkSamplingOptions&,
 *                                const SkPaint*,
 *                                SrcRectConstraint) override;
 *
 * private:
 *     SkTDArray<DrawCommand*> fCommandVector;
 *     SkM44                   fMatrix;
 *     SkIRect                 fClip;
 *
 *     bool    fOverdrawViz = false;
 *     SkColor fClipVizColor;
 *     bool    fDrawGpuOpBounds = false;
 *     bool    fShowAndroidClip = false;
 *     bool    fShowOrigin = false;
 *
 *     // When not negative, indicates the render node id of the layer represented by the next
 *     // drawPicture call.
 *     int         fnextDrawPictureLayerId = -1;
 *     int         fnextDrawImageRectLayerId = -1;
 *     SkIRect     fnextDrawPictureDirtyRect;
 *     // may be null, in which case layer annotations are ignored.
 *     DebugLayerManager* fLayerManager = nullptr;
 *     // May be set when DebugCanvas is used in playing back an animation.
 *     // Only used for passing to fLayerManager to identify itself.
 *     int fFrame = -1;
 *     SkRect fAndroidClip = SkRect::MakeEmpty();
 *
 *     /**
 *         Adds the command to the class' vector of commands.
 *         @param command  The draw command for execution
 *      */
 *     void addDrawCommand(DrawCommand* command);
 *
 * #if defined(SK_GANESH)
 *     GrAuditTrail* getAuditTrail(SkCanvas*);
 *     void drawAndCollectOps(SkCanvas*);
 *     void cleanupAuditTrail(GrAuditTrail*);
 * #endif
 *
 *     using INHERITED = SkCanvasVirtualEnforcer<SkCanvas>;
 * }
 * ```
 */
public open class DebugCanvas public constructor(
  width: Int,
  height: Int,
) : SkCanvasVirtualEnforcer(TODO(), TODO()),
    SkCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<DrawCommand*> fCommandVector
   * ```
   */
  private var fCommandVector: SkTDArray<DrawCommand?> = TODO("Initialize fCommandVector")

  /**
   * C++ original:
   * ```cpp
   * SkM44                   fMatrix
   * ```
   */
  private var fMatrix: SkM44 = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkIRect                 fClip
   * ```
   */
  private var fClip: SkIRect = TODO("Initialize fClip")

  /**
   * C++ original:
   * ```cpp
   * bool    fOverdrawViz = false
   * ```
   */
  private var fOverdrawViz: Boolean = TODO("Initialize fOverdrawViz")

  /**
   * C++ original:
   * ```cpp
   * SkColor fClipVizColor
   * ```
   */
  private var fClipVizColor: SkColor = TODO("Initialize fClipVizColor")

  /**
   * C++ original:
   * ```cpp
   * bool    fDrawGpuOpBounds = false
   * ```
   */
  private var fDrawGpuOpBounds: Boolean = TODO("Initialize fDrawGpuOpBounds")

  /**
   * C++ original:
   * ```cpp
   * bool    fShowAndroidClip = false
   * ```
   */
  private var fShowAndroidClip: Boolean = TODO("Initialize fShowAndroidClip")

  /**
   * C++ original:
   * ```cpp
   * bool    fShowOrigin = false
   * ```
   */
  private var fShowOrigin: Boolean = TODO("Initialize fShowOrigin")

  /**
   * C++ original:
   * ```cpp
   * int         fnextDrawPictureLayerId = -1
   * ```
   */
  private var fnextDrawPictureLayerId: Int = TODO("Initialize fnextDrawPictureLayerId")

  /**
   * C++ original:
   * ```cpp
   * int         fnextDrawImageRectLayerId = -1
   * ```
   */
  private var fnextDrawImageRectLayerId: Int = TODO("Initialize fnextDrawImageRectLayerId")

  /**
   * C++ original:
   * ```cpp
   * SkIRect     fnextDrawPictureDirtyRect
   * ```
   */
  private var fnextDrawPictureDirtyRect: SkIRect = TODO("Initialize fnextDrawPictureDirtyRect")

  /**
   * C++ original:
   * ```cpp
   * DebugLayerManager* fLayerManager = nullptr
   * ```
   */
  private var fLayerManager: DebugLayerManager? = TODO("Initialize fLayerManager")

  /**
   * C++ original:
   * ```cpp
   * int fFrame = -1
   * ```
   */
  private var fFrame: Int = TODO("Initialize fFrame")

  /**
   * C++ original:
   * ```cpp
   * SkRect fAndroidClip = SkRect::MakeEmpty()
   * ```
   */
  private var fAndroidClip: SkRect = TODO("Initialize fAndroidClip")

  /**
   * C++ original:
   * ```cpp
   * DebugCanvas::DebugCanvas(int width, int height)
   *         : INHERITED(width, height)
   *         , fOverdrawViz(false)
   *         , fClipVizColor(SK_ColorTRANSPARENT)
   *         , fDrawGpuOpBounds(false)
   *         , fShowAndroidClip(false)
   *         , fShowOrigin(false)
   *         , fnextDrawPictureLayerId(-1)
   *         , fnextDrawImageRectLayerId(-1)
   *         , fAndroidClip(SkRect::MakeEmpty()) {
   *     // SkPicturePlayback uses the base-class' quickReject calls to cull clipped
   *     // operations. This can lead to problems in the debugger which expects all
   *     // the operations in the captured skp to appear in the debug canvas. To
   *     // circumvent this we create a wide open clip here (an empty clip rect
   *     // is not sufficient).
   *     // Internally, the SkRect passed to clipRect is converted to an SkIRect and
   *     // rounded out. The following code creates a nearly maximal rect that will
   *     // not get collapsed by the coming conversions (Due to precision loss the
   *     // inset has to be surprisingly large).
   *     SkIRect largeIRect = SkRectPriv::MakeILarge();
   *     largeIRect.inset(1024, 1024);
   *     SkRect large = SkRect::Make(largeIRect);
   * #ifdef SK_DEBUG
   *     SkASSERT(!large.roundOut().isEmpty());
   * #endif
   *     // call the base class' version to avoid adding a draw command
   *     this->INHERITED::onClipRect(large, SkClipOp::kIntersect, kHard_ClipEdgeStyle);
   * }
   * ```
   */
  public constructor(bounds: SkIRect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLayerManagerAndFrame(DebugLayerManager* lm, int frame) {
   *         fLayerManager = lm;
   *         fFrame = frame;
   *     }
   * ```
   */
  public fun setLayerManagerAndFrame(lm: DebugLayerManager?, frame: Int) {
    TODO("Implement setLayerManagerAndFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::setOverdrawViz(bool overdrawViz) { fOverdrawViz = overdrawViz; }
   * ```
   */
  public fun setOverdrawViz(overdrawViz: Boolean) {
    TODO("Implement setOverdrawViz")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getOverdrawViz() const { return fOverdrawViz; }
   * ```
   */
  public fun getOverdrawViz(): Boolean {
    TODO("Implement getOverdrawViz")
  }

  /**
   * C++ original:
   * ```cpp
   * void setClipVizColor(SkColor clipVizColor) { this->fClipVizColor = clipVizColor; }
   * ```
   */
  public fun setClipVizColor(clipVizColor: SkColor) {
    TODO("Implement setClipVizColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAndroidClipViz(bool enable) { this->fShowAndroidClip = enable; }
   * ```
   */
  public fun setAndroidClipViz(enable: Boolean) {
    TODO("Implement setAndroidClipViz")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOriginVisible(bool enable) { this->fShowOrigin = enable; }
   * ```
   */
  public fun setOriginVisible(enable: Boolean) {
    TODO("Implement setOriginVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDrawGpuOpBounds(bool drawGpuOpBounds) { fDrawGpuOpBounds = drawGpuOpBounds; }
   * ```
   */
  public fun setDrawGpuOpBounds(drawGpuOpBounds: Boolean) {
    TODO("Implement setDrawGpuOpBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getDrawGpuOpBounds() const { return fDrawGpuOpBounds; }
   * ```
   */
  public fun getDrawGpuOpBounds(): Boolean {
    TODO("Implement getDrawGpuOpBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::draw(SkCanvas* canvas) {
   *     if (!fCommandVector.empty()) {
   *         this->drawTo(canvas, fCommandVector.size() - 1);
   *     }
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::drawTo(SkCanvas* originalCanvas, int index, int m) {
   *     SkASSERT(!fCommandVector.empty());
   *     SkASSERT(index < fCommandVector.size());
   *
   *     int saveCount = originalCanvas->save();
   *
   *     originalCanvas->resetMatrix();
   *     SkCanvasPriv::ResetClip(originalCanvas);
   *
   *     DebugPaintFilterCanvas filterCanvas(originalCanvas);
   *     SkCanvas* finalCanvas = fOverdrawViz ? &filterCanvas : originalCanvas;
   *
   * #if defined(SK_GANESH)
   *     auto dContext = GrAsDirectContext(finalCanvas->recordingContext());
   *
   *     // If we have a GPU backend we can also visualize the op information
   *     GrAuditTrail* at = nullptr;
   *     if (fDrawGpuOpBounds || m != -1) {
   *         // The audit trail must be obtained from the original canvas.
   *         at = this->getAuditTrail(originalCanvas);
   *     }
   * #endif
   *
   *     for (int i = 0; i <= index; i++) {
   * #if defined(SK_GANESH)
   *         GrAuditTrail::AutoCollectOps* acb = nullptr;
   *         if (at) {
   *             // We need to flush any pending operations, or they might combine with commands below.
   *             // Previous operations were not registered with the audit trail when they were
   *             // created, so if we allow them to combine, the audit trail will fail to find them.
   *             if (dContext) {
   *                 dContext->flush();
   *             }
   *             acb = new GrAuditTrail::AutoCollectOps(at, i);
   *         }
   * #endif
   *         if (fCommandVector[i]->isVisible()) {
   *             fCommandVector[i]->execute(finalCanvas);
   *         }
   * #if defined(SK_GANESH)
   *         if (at && acb) {
   *             delete acb;
   *         }
   * #endif
   *     }
   *
   *     if (SkColorGetA(fClipVizColor) != 0) {
   *         finalCanvas->save();
   *         SkPaint clipPaint;
   *         clipPaint.setColor(fClipVizColor);
   *         finalCanvas->drawPaint(clipPaint);
   *         finalCanvas->restore();
   *     }
   *
   *     fMatrix = finalCanvas->getLocalToDevice();
   *     fClip   = finalCanvas->getDeviceClipBounds();
   *     if (fShowOrigin) {
   *         const SkPaint originXPaint = SkPaint({1.0, 0, 0, 1.0});
   *         const SkPaint originYPaint = SkPaint({0, 1.0, 0, 1.0});
   *         // Draw an origin cross at the origin before restoring to assist in visualizing the
   *         // current matrix.
   *         drawArrow(finalCanvas, {-50, 0}, {50, 0}, originXPaint);
   *         drawArrow(finalCanvas, {0, -50}, {0, 50}, originYPaint);
   *     }
   *     finalCanvas->restoreToCount(saveCount);
   *
   *     if (fShowAndroidClip) {
   *         // Draw visualization of android device clip restriction
   *         SkPaint androidClipPaint;
   *         androidClipPaint.setARGB(80, 255, 100, 0);
   *         finalCanvas->drawRect(fAndroidClip, androidClipPaint);
   *     }
   *
   * #if defined(SK_GANESH)
   *     // draw any ops if required and issue a full reset onto GrAuditTrail
   *     if (at) {
   *         // just in case there is global reordering, we flush the canvas before querying
   *         // GrAuditTrail
   *         GrAuditTrail::AutoEnable ae(at);
   *         if (dContext) {
   *             dContext->flush();
   *         }
   *
   *         // we pick three colorblind-safe colors, 75% alpha
   *         static const SkColor kTotalBounds     = SkColorSetARGB(0xC0, 0x6A, 0x3D, 0x9A);
   *         static const SkColor kCommandOpBounds = SkColorSetARGB(0xC0, 0xE3, 0x1A, 0x1C);
   *         static const SkColor kOtherOpBounds   = SkColorSetARGB(0xC0, 0xFF, 0x7F, 0x00);
   *
   *         // get the render target of the top device (from the original canvas) so we can ignore ops
   *         // drawn offscreen
   *         GrRenderTargetProxy* rtp = skgpu::ganesh::TopDeviceTargetProxy(originalCanvas);
   *         GrSurfaceProxy::UniqueID proxyID = rtp->uniqueID();
   *
   *         // get the bounding boxes to draw
   *         TArray<GrAuditTrail::OpInfo> childrenBounds;
   *         if (m == -1) {
   *             at->getBoundsByClientID(&childrenBounds, index);
   *         } else {
   *             // the client wants us to draw the mth op
   *             at->getBoundsByOpsTaskID(&childrenBounds.push_back(), m);
   *         }
   *         // Shift the rects half a pixel, so they appear as exactly 1px thick lines.
   *         finalCanvas->save();
   *         finalCanvas->translate(0.5, -0.5);
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(1);
   *         for (int i = 0; i < childrenBounds.size(); i++) {
   *             if (childrenBounds[i].fProxyUniqueID != proxyID) {
   *                 // offscreen draw, ignore for now
   *                 continue;
   *             }
   *             paint.setColor(kTotalBounds);
   *             finalCanvas->drawRect(childrenBounds[i].fBounds, paint);
   *             for (int j = 0; j < childrenBounds[i].fOps.size(); j++) {
   *                 const GrAuditTrail::OpInfo::Op& op = childrenBounds[i].fOps[j];
   *                 if (op.fClientID != index) {
   *                     paint.setColor(kOtherOpBounds);
   *                 } else {
   *                     paint.setColor(kCommandOpBounds);
   *                 }
   *                 finalCanvas->drawRect(op.fBounds, paint);
   *             }
   *         }
   *         finalCanvas->restore();
   *         this->cleanupAuditTrail(at);
   *     }
   * #endif
   * }
   * ```
   */
  public fun drawTo(
    canvas: SkCanvas?,
    index: Int,
    m: Int = TODO(),
  ) {
    TODO("Implement drawTo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& getCurrentMatrix() { return fMatrix; }
   * ```
   */
  public fun getCurrentMatrix(): SkM44 {
    TODO("Implement getCurrentMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& getCurrentClip() { return fClip; }
   * ```
   */
  public fun getCurrentClip(): SkIRect {
    TODO("Implement getCurrentClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::deleteDrawCommandAt(int index) {
   *     SkASSERT(index < fCommandVector.size());
   *     delete fCommandVector[index];
   *     fCommandVector.remove(index);
   * }
   * ```
   */
  public fun deleteDrawCommandAt(index: Int) {
    TODO("Implement deleteDrawCommandAt")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawCommand* DebugCanvas::getDrawCommandAt(int index) const {
   *     SkASSERT(index < fCommandVector.size());
   *     return fCommandVector[index];
   * }
   * ```
   */
  public fun getDrawCommandAt(index: Int): DrawCommand {
    TODO("Implement getDrawCommandAt")
  }

  /**
   * C++ original:
   * ```cpp
   * int getSize() const { return fCommandVector.size(); }
   * ```
   */
  public fun getSize(): Int {
    TODO("Implement getSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::toggleCommand(int index, bool toggle) {
   *     SkASSERT(index < fCommandVector.size());
   *     fCommandVector[index]->setVisible(toggle);
   * }
   * ```
   */
  public fun toggleCommand(index: Int, toggle: Boolean) {
    TODO("Implement toggleCommand")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::toJSON(SkJSONWriter&   writer,
   *                          UrlDataManager& urlDataManager,
   *                          SkCanvas*       canvas) {
   * #if defined(SK_GANESH)
   *     this->drawAndCollectOps(canvas);
   *
   *     // now collect json
   *     GrAuditTrail* at = this->getAuditTrail(canvas);
   * #endif
   *     writer.appendS32(SKDEBUGCANVAS_ATTRIBUTE_VERSION, SKDEBUGCANVAS_VERSION);
   *     writer.beginArray(SKDEBUGCANVAS_ATTRIBUTE_COMMANDS);
   *
   *     for (int i = 0; i < this->getSize(); i++) {
   *         writer.beginObject();  // command
   *         this->getDrawCommandAt(i)->toJSON(writer, urlDataManager);
   *
   * #if defined(SK_GANESH)
   *         if (at && at->isEnabled()) {
   *             writer.appendName(SKDEBUGCANVAS_ATTRIBUTE_AUDITTRAIL);
   *             at->toJson(writer, i);
   *         }
   * #endif
   *         writer.endObject();  // command
   *     }
   *
   *     writer.endArray();  // commands
   * #if defined(SK_GANESH)
   *     this->cleanupAuditTrail(at);
   * #endif
   * }
   * ```
   */
  public fun toJSON(
    writer: SkJSONWriter,
    urlDataManager: UrlDataManager,
    canvas: SkCanvas?,
  ) {
    TODO("Implement toJSON")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::toJSONOpsTask(SkJSONWriter& writer, SkCanvas* canvas) {
   * #if defined(SK_GANESH)
   *     this->drawAndCollectOps(canvas);
   *
   *     GrAuditTrail* at = this->getAuditTrail(canvas);
   *     if (at) {
   *         GrAuditTrail::AutoManageOpsTask enable(at);
   *         at->toJson(writer);
   *         this->cleanupAuditTrail(at);
   *         return;
   *     }
   * #endif
   *
   *     writer.beginObject();
   *     writer.endObject();
   * }
   * ```
   */
  public fun toJSONOpsTask(writer: SkJSONWriter, canvas: SkCanvas?) {
    TODO("Implement toJSONOpsTask")
  }

  /**
   * C++ original:
   * ```cpp
   * void detachCommands(SkTDArray<DrawCommand*>* dst) { fCommandVector.swap(*dst); }
   * ```
   */
  public fun detachCommands(dst: SkTDArray<DrawCommand?>?) {
    TODO("Implement detachCommands")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::willSave() {
   *     this->addDrawCommand(new SaveCommand());
   *     this->INHERITED::willSave();
   * }
   * ```
   */
  protected override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy DebugCanvas::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     this->addDrawCommand(new SaveLayerCommand(rec));
   *     (void)this->INHERITED::getSaveLayerStrategy(rec);
   *     // No need for a full layer.
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
   * bool DebugCanvas::onDoSaveBehind(const SkRect* subset) {
   *     // TODO
   *     return false;
   * }
   * ```
   */
  protected override fun onDoSaveBehind(subset: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::willRestore() {
   *     this->addDrawCommand(new RestoreCommand());
   *     this->INHERITED::willRestore();
   * }
   * ```
   */
  protected override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::didConcat44(const SkM44& m) {
   *     this->addDrawCommand(new Concat44Command(m));
   *     this->INHERITED::didConcat44(m);
   * }
   * ```
   */
  protected override fun didConcat44(m: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::didSetM44(const SkM44& matrix) {
   *     this->addDrawCommand(new SetM44Command(matrix));
   *     this->INHERITED::didSetM44(matrix);
   * }
   * ```
   */
  protected override fun didSetM44(matrix: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::didScale(SkScalar x, SkScalar y) {
   *     this->didConcat44(SkM44::Scale(x, y));
   * }
   * ```
   */
  protected override fun didScale(x: SkScalar, y: SkScalar) {
    TODO("Implement didScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::didTranslate(SkScalar x, SkScalar y) {
   *     this->didConcat44(SkM44::Translate(x, y));
   * }
   * ```
   */
  protected override fun didTranslate(x: SkScalar, y: SkScalar) {
    TODO("Implement didTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) {
   *     // Parse layer-releated annotations added in SkiaPipeline.cpp and RenderNodeDrawable.cpp
   *     // the format of the annotations is <Indicator|RenderNodeId>
   *     TArray<SkString> tokens;
   *     SkStrSplit(key, "|", kStrict_SkStrSplitMode, &tokens);
   *     if (tokens.size() == 2) {
   *         if (tokens[0].equals(kOffscreenLayerDraw)) {
   *             // Indicates that the next drawPicture command contains the SkPicture to render the
   *             // node at this id in an offscreen buffer.
   *             fnextDrawPictureLayerId = std::stoi(tokens[1].c_str());
   *             fnextDrawPictureDirtyRect = rect.roundOut();
   *             return; // don't record it
   *         } else if (tokens[0].equals(kSurfaceID)) {
   *             // Indicates that the following drawImageRect should draw the offscreen buffer.
   *             fnextDrawImageRectLayerId = std::stoi(tokens[1].c_str());
   *             return; // don't record it
   *         }
   *     }
   *     if (strcmp(kAndroidClip, key) == 0) {
   *         // Store this frame's android device clip restriction for visualization later.
   *         // This annotation stands in place of the androidFramework_setDeviceClipRestriction
   *         // which is unrecordable.
   *         fAndroidClip = rect;
   *     }
   *     this->addDrawCommand(new DrawAnnotationCommand(rect, key, sk_ref_sp(value)));
   * }
   * ```
   */
  protected override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint) {
   *     this->addDrawCommand(new DrawDRRectCommand(outer, inner, paint));
   * }
   * ```
   */
  protected override fun onDrawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawTextBlob(const SkTextBlob* blob,
   *                                  SkScalar          x,
   *                                  SkScalar          y,
   *                                  const SkPaint&    paint) {
   *     this->addDrawCommand(
   *             new DrawTextBlobCommand(sk_ref_sp(const_cast<SkTextBlob*>(blob)), x, y, paint));
   * }
   * ```
   */
  protected override fun onDrawTextBlob(
    blob: SkTextBlob?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawPatch(const SkPoint  cubics[12],
   *                               const SkColor  colors[4],
   *                               const SkPoint  texCoords[4],
   *                               SkBlendMode    bmode,
   *                               const SkPaint& paint) {
   *     this->addDrawCommand(new DrawPatchCommand(cubics, colors, texCoords, bmode, paint));
   * }
   * ```
   */
  protected override fun onDrawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    bmode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawPaint(const SkPaint& paint) {
   *     this->addDrawCommand(new DrawPaintCommand(paint));
   * }
   * ```
   */
  protected override fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawBehind(const SkPaint& paint) {
   *     this->addDrawCommand(new DrawBehindCommand(paint));
   * }
   * ```
   */
  protected override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     // NOTE(chudy): Messing up when renamed to DrawRect... Why?
   *     addDrawCommand(new DrawRectCommand(rect, paint));
   * }
   * ```
   */
  protected override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
   *     this->addDrawCommand(new DrawOvalCommand(oval, paint));
   * }
   * ```
   */
  protected override fun onDrawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawArc(const SkRect&  oval,
   *                             SkScalar       startAngle,
   *                             SkScalar       sweepAngle,
   *                             bool           useCenter,
   *                             const SkPaint& paint) {
   *     this->addDrawCommand(new DrawArcCommand(oval, startAngle, sweepAngle, useCenter, paint));
   * }
   * ```
   */
  protected override fun onDrawArc(
    oval: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     this->addDrawCommand(new DrawRRectCommand(rrect, paint));
   * }
   * ```
   */
  protected override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawPoints(PointMode      mode,
   *                                size_t         count,
   *                                const SkPoint  pts[],
   *                                const SkPaint& paint) {
   *     this->addDrawCommand(new DrawPointsCommand(mode, count, pts, paint));
   * }
   * ```
   */
  protected override fun onDrawPoints(
    mode: PointMode,
    count: ULong,
    pts: Array<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawVerticesObject(const SkVertices*      vertices,
   *                                        SkBlendMode            bmode,
   *                                        const SkPaint&         paint) {
   *     this->addDrawCommand(
   *             new DrawVerticesCommand(sk_ref_sp(const_cast<SkVertices*>(vertices)), bmode, paint));
   * }
   * ```
   */
  protected override fun onDrawVerticesObject(
    vertices: SkVertices?,
    bmode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     this->addDrawCommand(new DrawPathCommand(path, paint));
   * }
   * ```
   */
  protected override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     this->addDrawCommand(new DrawRegionCommand(region, paint));
   * }
   * ```
   */
  protected override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawImage2(const SkImage*           image,
   *                                SkScalar                 left,
   *                                SkScalar                 top,
   *                                const SkSamplingOptions& sampling,
   *                                const SkPaint*           paint) {
   *     this->addDrawCommand(new DrawImageCommand(image, left, top, sampling, paint));
   * }
   * ```
   */
  protected override fun onDrawImage2(
    image: SkImage?,
    left: SkScalar,
    top: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawImageRect2(const SkImage*           image,
   *                                    const SkRect&            src,
   *                                    const SkRect&            dst,
   *                                    const SkSamplingOptions& sampling,
   *                                    const SkPaint*           paint,
   *                                    SrcRectConstraint        constraint) {
   *     if (fnextDrawImageRectLayerId != -1 && fLayerManager) {
   *         // This drawImageRect command would have drawn the offscreen buffer for a layer.
   *         // On Android, we recorded an SkPicture of the commands that drew to the layer.
   *         // To render the layer as it would have looked on the frame this DebugCanvas draws, we need
   *         // to call fLayerManager->getLayerAsImage(id). This must be done just before
   *         // drawTo(command), since it depends on the index into the layer's commands
   *         // (managed by fLayerManager)
   *         // Instead of adding a DrawImageRectCommand, we need a deferred command, that when
   *         // executed, will call drawImageRect(fLayerManager->getLayerAsImage())
   *         this->addDrawCommand(new DrawImageRectLayerCommand(
   *             fLayerManager, fnextDrawImageRectLayerId, fFrame, src, dst, sampling,
   *                                                            paint, constraint));
   *     } else {
   *         this->addDrawCommand(new DrawImageRectCommand(image, src, dst, sampling, paint, constraint));
   *     }
   *     // Reset expectation so next drawImageRect is not special.
   *     fnextDrawImageRectLayerId = -1;
   * }
   * ```
   */
  protected override fun onDrawImageRect2(
    image: SkImage?,
    src: SkRect,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawImageRect2")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawImageLattice2(const SkImage* image,
   *                                       const Lattice& lattice,
   *                                       const SkRect&  dst,
   *                                       SkFilterMode filter,   // todo
   *                                       const SkPaint* paint) {
   *     this->addDrawCommand(new DrawImageLatticeCommand(image, lattice, dst, filter, paint));
   * }
   * ```
   */
  protected override fun onDrawImageLattice2(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImageLattice2")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawAtlas2(const SkImage*           image,
   *                                const SkRSXform          xform[],
   *                                const SkRect             tex[],
   *                                const SkColor            colors[],
   *                                int                      count,
   *                                SkBlendMode              bmode,
   *                                const SkSamplingOptions& sampling,
   *                                const SkRect*            cull,
   *                                const SkPaint*           paint) {
   *     this->addDrawCommand(
   *             new DrawAtlasCommand(image, xform, tex, colors, count, bmode, sampling, cull, paint));
   * }
   * ```
   */
  protected override fun onDrawAtlas2(
    image: SkImage?,
    xform: Array<SkRSXform>,
    tex: Array<SkRect>,
    colors: Array<SkColor>,
    count: Int,
    bmode: SkBlendMode,
    sampling: SkSamplingOptions,
    cull: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->addDrawCommand(new ClipRectCommand(rect, op, kSoft_ClipEdgeStyle == edgeStyle));
   * }
   * ```
   */
  protected override fun onClipRect(
    rect: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->addDrawCommand(new ClipRRectCommand(rrect, op, kSoft_ClipEdgeStyle == edgeStyle));
   * }
   * ```
   */
  protected override fun onClipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->addDrawCommand(new ClipPathCommand(path, op, kSoft_ClipEdgeStyle == edgeStyle));
   * }
   * ```
   */
  protected override fun onClipPath(
    path: SkPath,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onClipShader(sk_sp<SkShader> cs, SkClipOp op) {
   *     this->addDrawCommand(new ClipShaderCommand(std::move(cs), op));
   * }
   * ```
   */
  protected override fun onClipShader(cs: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onClipRegion(const SkRegion& region, SkClipOp op) {
   *     this->addDrawCommand(new ClipRegionCommand(region, op));
   * }
   * ```
   */
  protected override fun onClipRegion(region: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onResetClip() {
   *     this->addDrawCommand(new ResetClipCommand());
   * }
   * ```
   */
  protected override fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     this->addDrawCommand(new DrawShadowCommand(path, rec));
   * }
   * ```
   */
  protected override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     this->addDrawCommand(new DrawDrawableCommand(drawable, matrix));
   * }
   * ```
   */
  protected override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawPicture(const SkPicture* picture,
   *                                 const SkMatrix*  matrix,
   *                                 const SkPaint*   paint) {
   *     if (fnextDrawPictureLayerId != -1 && fLayerManager) {
   *         fLayerManager->storeSkPicture(fnextDrawPictureLayerId, fFrame, sk_ref_sp(picture),
   *            fnextDrawPictureDirtyRect);
   *     } else {
   *         this->addDrawCommand(new BeginDrawPictureCommand(picture, matrix, paint));
   *         SkAutoCanvasMatrixPaint acmp(this, matrix, paint, picture->cullRect());
   *         picture->playback(this);
   *         this->addDrawCommand(new EndDrawPictureCommand(SkToBool(matrix) || SkToBool(paint)));
   *     }
   *     fnextDrawPictureLayerId = -1;
   * }
   * ```
   */
  protected override fun onDrawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawEdgeAAQuad(const SkRect&    rect,
   *                                    const SkPoint    clip[4],
   *                                    QuadAAFlags      aa,
   *                                    const SkColor4f& color,
   *                                    SkBlendMode      mode) {
   *     this->addDrawCommand(new DrawEdgeAAQuadCommand(rect, clip, aa, color, mode));
   * }
   * ```
   */
  protected override fun onDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aa: QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement onDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry set[],
   *                                         int                 count,
   *                                         const SkPoint       dstClips[],
   *                                         const SkMatrix      preViewMatrices[],
   *                                         const SkSamplingOptions& sampling,
   *                                         const SkPaint*      paint,
   *                                         SrcRectConstraint   constraint) {
   *     this->addDrawCommand(new DrawEdgeAAImageSetCommand(
   *             set, count, dstClips, preViewMatrices, sampling, paint, constraint));
   * }
   * ```
   */
  protected override fun onDrawEdgeAAImageSet2(
    `set`: Array<ImageSetEntry>,
    count: Int,
    dstClips: Array<SkPoint>,
    preViewMatrices: Array<SkMatrix>,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawEdgeAAImageSet2")
  }

  /**
   * C++ original:
   * ```cpp
   * void DebugCanvas::addDrawCommand(DrawCommand* command) { fCommandVector.push_back(command); }
   * ```
   */
  private fun addDrawCommand(command: DrawCommand?) {
    TODO("Implement addDrawCommand")
  }
}
