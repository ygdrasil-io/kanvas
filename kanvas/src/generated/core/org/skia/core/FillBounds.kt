package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.gpu.DrawAtlas
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.memory.SkTDArray
import org.skia.modules.Concat
import org.skia.utils.ClipRect
import undefined.Bounds
import undefined.ClipPath
import undefined.ClipRRect
import undefined.ClipRegion
import undefined.ClipShader
import undefined.Concat44
import undefined.DrawAnnotation
import undefined.DrawArc
import undefined.DrawBehind
import undefined.DrawDRRect
import undefined.DrawDrawable
import undefined.DrawEdgeAAImageSet
import undefined.DrawEdgeAAQuad
import undefined.DrawImageLattice
import undefined.DrawMesh
import undefined.DrawOval
import undefined.DrawPaint
import undefined.DrawPatch
import undefined.DrawPath
import undefined.DrawPicture
import undefined.DrawPoints
import undefined.DrawRRect
import undefined.DrawRect
import undefined.DrawRegion
import undefined.DrawShadowRec
import undefined.DrawSlug
import undefined.DrawTextBlob
import undefined.DrawVertices
import undefined.NoOp
import undefined.ResetClip
import undefined.Restore
import undefined.Save
import undefined.SaveBehind
import undefined.SaveLayer
import undefined.Scale
import undefined.SetM44
import undefined.SetMatrix
import undefined.Translate

/**
 * C++ original:
 * ```cpp
 * class FillBounds : SkNoncopyable {
 * public:
 *     FillBounds(const SkRect& cullRect, const SkRecord& record,
 *                SkRect bounds[], SkBBoxHierarchy::Metadata meta[])
 *         : fCullRect(cullRect)
 *         , fBounds(bounds)
 *         , fMeta(meta) {
 *         fCTM = SkMatrix::I();
 *
 *         // We push an extra save block to track the bounds of any top-level control operations.
 *         fSaveStack.push_back({ 0, Bounds::MakeEmpty(), nullptr, fCTM });
 *     }
 *
 *     ~FillBounds() {
 *         // If we have any lingering unpaired Saves, simulate restores to make
 *         // sure all ops in those Save blocks have their bounds calculated.
 *         while (!fSaveStack.empty()) {
 *             this->popSaveBlock();
 *         }
 *
 *         // Any control ops not part of any Save/Restore block draw everywhere.
 *         while (!fControlIndices.empty()) {
 *             this->popControl(fCullRect);
 *         }
 *     }
 *
 *     void setCurrentOp(int currentOp) { fCurrentOp = currentOp; }
 *
 *
 *     template <typename T> void operator()(const T& op) {
 *         this->updateCTM(op);
 *         this->trackBounds(op);
 *     }
 *
 *     // In this file, SkRect are in local coordinates, Bounds are translated back to identity space.
 *     typedef SkRect Bounds;
 *
 *     // Adjust rect for all paints that may affect its geometry, then map it to identity space.
 *     Bounds adjustAndMap(SkRect rect, const SkPaint* paint) const {
 *         // Inverted rectangles really confuse our BBHs.
 *         rect.sort();
 *
 *         // Adjust the rect for its own paint.
 *         if (!AdjustForPaint(paint, &rect)) {
 *             // The paint could do anything to our bounds.  The only safe answer is the cull.
 *             return fCullRect;
 *         }
 *
 *         // Adjust rect for all the paints from the SaveLayers we're inside.
 *         if (!this->adjustForSaveLayerPaints(&rect)) {
 *             // Same deal as above.
 *             return fCullRect;
 *         }
 *
 *         // Map the rect back to identity space.
 *         fCTM.mapRect(&rect);
 *
 *         // Nothing can draw outside the cull rect.
 *         if (!rect.intersect(fCullRect)) {
 *             return Bounds::MakeEmpty();
 *         }
 *
 *         return rect;
 *     }
 *
 * private:
 *     struct SaveBounds {
 *         int controlOps;        // Number of control ops in this Save block, including the Save.
 *         Bounds bounds;         // Bounds of everything in the block.
 *         const SkPaint* paint;  // Unowned.  If set, adjusts the bounds of all ops in this block.
 *         SkMatrix ctm;
 *     };
 *
 *     // Only Restore, SetMatrix, Concat, and Translate change the CTM.
 *     template <typename T> void updateCTM(const T&) {}
 *     void updateCTM(const Restore& op)   { fCTM = op.matrix; }
 *     void updateCTM(const SetMatrix& op) { fCTM = op.matrix; }
 *     void updateCTM(const SetM44& op)    { fCTM = op.matrix.asM33(); }
 *     void updateCTM(const Concat44& op)  { fCTM.preConcat(op.matrix.asM33()); }
 *     void updateCTM(const Concat& op)    { fCTM.preConcat(op.matrix); }
 *     void updateCTM(const Scale& op)     { fCTM.preScale(op.sx, op.sy); }
 *     void updateCTM(const Translate& op) { fCTM.preTranslate(op.dx, op.dy); }
 *
 *     // The bounds of these ops must be calculated when we hit the Restore
 *     // from the bounds of the ops in the same Save block.
 *     void trackBounds(const Save&) {
 *         this->pushSaveBlock(nullptr, /*hasBackdropFilter=*/false);
 *     }
 *     void trackBounds(const SaveLayer& op) {
 *         this->pushSaveBlock(op.paint, /*hasBackdropFilter=*/op.backdrop != nullptr);
 *     }
 *     void trackBounds(const SaveBehind&) {
 *         this->pushSaveBlock(nullptr, /*hasBackdropFilter=*/false);
 *     }
 *     void trackBounds(const Restore&) {
 *         const bool isSaveLayer = fSaveStack.back().paint != nullptr;
 *         fBounds[fCurrentOp] = this->popSaveBlock();
 *         fMeta  [fCurrentOp].isDraw = isSaveLayer;
 *     }
 *
 *     void trackBounds(const SetMatrix&)         { this->pushControl(); }
 *     void trackBounds(const SetM44&)            { this->pushControl(); }
 *     void trackBounds(const Concat&)            { this->pushControl(); }
 *     void trackBounds(const Concat44&)          { this->pushControl(); }
 *     void trackBounds(const Scale&)             { this->pushControl(); }
 *     void trackBounds(const Translate&)         { this->pushControl(); }
 *     void trackBounds(const ClipRect&)          { this->pushControl(); }
 *     void trackBounds(const ClipRRect&)         { this->pushControl(); }
 *     void trackBounds(const ClipPath&)          { this->pushControl(); }
 *     void trackBounds(const ClipRegion&)        { this->pushControl(); }
 *     void trackBounds(const ClipShader&)        { this->pushControl(); }
 *     void trackBounds(const ResetClip&)         { this->pushControl(); }
 *
 *
 *     // For all other ops, we can calculate and store the bounds directly now.
 *     template <typename T> void trackBounds(const T& op) {
 *         fBounds[fCurrentOp] = this->bounds(op);
 *         fMeta  [fCurrentOp].isDraw = true;
 *         this->updateSaveBounds(fBounds[fCurrentOp]);
 *     }
 *
 *     void pushSaveBlock(const SkPaint* paint, bool hasBackdropFilter) {
 *         // Starting a new Save block.  Push a new entry to represent that.
 *         SaveBounds sb;
 *         sb.controlOps = 0;
 *
 *         // If the paint affects transparent black, or we have a backdrop filter,
 *         // the bound shouldn't be smaller than the cull.
 *         bool affectsFullCullRect = hasBackdropFilter || PaintMayAffectTransparentBlack(paint);
 *         sb.bounds = affectsFullCullRect ? fCullRect : Bounds::MakeEmpty();
 *         sb.paint = paint;
 *         sb.ctm = this->fCTM;
 *
 *         fSaveStack.push_back(sb);
 *         this->pushControl();
 *     }
 *
 *     static bool PaintMayAffectTransparentBlack(const SkPaint* paint) {
 *         if (paint) {
 *             // FIXME: this is very conservative
 *             if ((paint->getImageFilter() &&
 *                  as_IFB(paint->getImageFilter())->affectsTransparentBlack()) ||
 *                 (paint->getColorFilter() &&
 *                  as_CFB(paint->getColorFilter())->affectsTransparentBlack())) {
 *                 return true;
 *             }
 *             const auto bm = paint->asBlendMode();
 *             if (!bm) {
 *                 return true;    // can we query other blenders for this?
 *             }
 *
 *             // Unusual blendmodes require us to process a saved layer
 *             // even with operations outisde the clip.
 *             // For example, DstIn is used by masking layers.
 *             // https://code.google.com/p/skia/issues/detail?id=1291
 *             // https://crbug.com/401593
 *             switch (bm.value()) {
 *                 // For each of the following transfer modes, if the source
 *                 // alpha is zero (our transparent black), the resulting
 *                 // blended alpha is not necessarily equal to the original
 *                 // destination alpha.
 *                 case SkBlendMode::kClear:
 *                 case SkBlendMode::kSrc:
 *                 case SkBlendMode::kSrcIn:
 *                 case SkBlendMode::kDstIn:
 *                 case SkBlendMode::kSrcOut:
 *                 case SkBlendMode::kDstATop:
 *                 case SkBlendMode::kModulate:
 *                     return true;
 *                 default:
 *                     break;
 *             }
 *         }
 *         return false;
 *     }
 *
 *     Bounds popSaveBlock() {
 *         // We're done the Save block.  Apply the block's bounds to all control ops inside it.
 *         SaveBounds sb = fSaveStack.back();
 *         fSaveStack.pop_back();
 *
 *         while (sb.controlOps --> 0) {
 *             this->popControl(sb.bounds);
 *         }
 *
 *         // This whole Save block may be part another Save block.
 *         this->updateSaveBounds(sb.bounds);
 *
 *         // If called from a real Restore (not a phony one for balance), it'll need the bounds.
 *         return sb.bounds;
 *     }
 *
 *     void pushControl() {
 *         fControlIndices.push_back(fCurrentOp);
 *         if (!fSaveStack.empty()) {
 *             fSaveStack.back().controlOps++;
 *         }
 *     }
 *
 *     void popControl(const Bounds& bounds) {
 *         fBounds[fControlIndices.back()] = bounds;
 *         fMeta  [fControlIndices.back()].isDraw = false;
 *         fControlIndices.pop_back();
 *     }
 *
 *     void updateSaveBounds(const Bounds& bounds) {
 *         // If we're in a Save block, expand its bounds to cover these bounds too.
 *         if (!fSaveStack.empty()) {
 *             fSaveStack.back().bounds.join(bounds);
 *         }
 *     }
 *
 *     Bounds bounds(const DrawPaint&) const { return fCullRect; }
 *     Bounds bounds(const DrawBehind&) const { return fCullRect; }
 *     Bounds bounds(const NoOp&)  const { return Bounds::MakeEmpty(); }    // NoOps don't draw.
 *
 *     Bounds bounds(const DrawRect& op) const { return this->adjustAndMap(op.rect, &op.paint); }
 *     Bounds bounds(const DrawRegion& op) const {
 *         SkRect rect = SkRect::Make(op.region.getBounds());
 *         return this->adjustAndMap(rect, &op.paint);
 *     }
 *     Bounds bounds(const DrawOval& op) const { return this->adjustAndMap(op.oval, &op.paint); }
 *     // Tighter arc bounds?
 *     Bounds bounds(const DrawArc& op) const { return this->adjustAndMap(op.oval, &op.paint); }
 *     Bounds bounds(const DrawRRect& op) const {
 *         return this->adjustAndMap(op.rrect.rect(), &op.paint);
 *     }
 *     Bounds bounds(const DrawDRRect& op) const {
 *         return this->adjustAndMap(op.outer.rect(), &op.paint);
 *     }
 *     Bounds bounds(const DrawImage& op) const {
 *         const SkImage* image = op.image.get();
 *         SkRect rect = SkRect::MakeXYWH(op.left, op.top, image->width(), image->height());
 *
 *         return this->adjustAndMap(rect, op.paint);
 *     }
 *     Bounds bounds(const DrawImageLattice& op) const {
 *         return this->adjustAndMap(op.dst, op.paint);
 *     }
 *     Bounds bounds(const DrawImageRect& op) const {
 *         return this->adjustAndMap(op.dst, op.paint);
 *     }
 *     Bounds bounds(const DrawPath& op) const {
 *         return op.path.isInverseFillType() ? fCullRect
 *                                            : this->adjustAndMap(op.path.getBounds(), &op.paint);
 *     }
 *     Bounds bounds(const DrawPoints& op) const {
 *         SkRect dst = SkRect::BoundsOrEmpty({op.pts.data(), op.count});
 *
 *         // Pad the bounding box a little to make sure hairline points' bounds aren't empty.
 *         SkScalar stroke = std::max(op.paint.getStrokeWidth(), 0.01f);
 *         dst.outset(stroke/2, stroke/2);
 *
 *         return this->adjustAndMap(dst, &op.paint);
 *     }
 *     Bounds bounds(const DrawPatch& op) const {
 *         const auto dst = SkRect::BoundsOrEmpty({op.cubics.data(), (size_t)SkPatchUtils::kNumCtrlPts});
 *         return this->adjustAndMap(dst, &op.paint);
 *     }
 *     Bounds bounds(const DrawVertices& op) const {
 *         return this->adjustAndMap(op.vertices->bounds(), &op.paint);
 *     }
 *     Bounds bounds(const DrawMesh& op) const {
 *         return this->adjustAndMap(op.mesh.bounds(), &op.paint);
 *     }
 *     Bounds bounds(const DrawAtlas& op) const {
 *         if (op.cull) {
 *             // TODO: <reed> can we pass nullptr for the paint? Isn't cull already "correct"
 *             // for the paint (by the caller)?
 *             return this->adjustAndMap(*op.cull, op.paint);
 *         } else {
 *             return fCullRect;
 *         }
 *     }
 *
 *     Bounds bounds(const DrawShadowRec& op) const {
 *         SkRect bounds;
 *         SkDrawShadowMetrics::GetLocalBounds(op.path, op.rec, fCTM, &bounds);
 *         return this->adjustAndMap(bounds, nullptr);
 *     }
 *
 *     Bounds bounds(const DrawPicture& op) const {
 *         SkRect dst = op.picture->cullRect();
 *         op.matrix.mapRect(&dst);
 *         return this->adjustAndMap(dst, op.paint);
 *     }
 *
 *     Bounds bounds(const DrawTextBlob& op) const {
 *         SkRect dst = op.blob->bounds();
 *         dst.offset(op.x, op.y);
 *         return this->adjustAndMap(dst, &op.paint);
 *     }
 *
 *     Bounds bounds(const DrawSlug& op) const {
 *         SkRect dst = op.slug->sourceBoundsWithOrigin();
 *         return this->adjustAndMap(dst, &op.paint);
 *     }
 *
 *     Bounds bounds(const DrawDrawable& op) const {
 *         return this->adjustAndMap(op.worstCaseBounds, nullptr);
 *     }
 *
 *     Bounds bounds(const DrawAnnotation& op) const {
 *         return this->adjustAndMap(op.rect, nullptr);
 *     }
 *     Bounds bounds(const DrawEdgeAAQuad& op) const {
 *         const auto bounds = op.clip ? SkRect::BoundsOrEmpty({op.clip.data(), 4}) : op.rect;
 *         return this->adjustAndMap(bounds, nullptr);
 *     }
 *     Bounds bounds(const DrawEdgeAAImageSet& op) const {
 *         SkRect rect = SkRect::MakeEmpty();
 *         int clipIndex = 0;
 *         for (int i = 0; i < op.count; ++i) {
 *             SkRect entryBounds = op.set[i].fDstRect;
 *             if (op.set[i].fHasClip) {
 *                 entryBounds = SkRect::BoundsOrEmpty({op.dstClips + clipIndex, 4});
 *                 clipIndex += 4;
 *             }
 *             if (op.set[i].fMatrixIndex >= 0) {
 *                 op.preViewMatrices[op.set[i].fMatrixIndex].mapRect(&entryBounds);
 *             }
 *             rect.join(this->adjustAndMap(entryBounds, nullptr));
 *         }
 *         return rect;
 *     }
 *
 *     // Returns true if rect was meaningfully adjusted for the effects of paint,
 *     // false if the paint could affect the rect in unknown ways.
 *     static bool AdjustForPaint(const SkPaint* paint, SkRect* rect) {
 *         if (paint) {
 *             if (paint->canComputeFastBounds()) {
 *                 *rect = paint->computeFastBounds(*rect, rect);
 *                 return true;
 *             }
 *             return false;
 *         }
 *         return true;
 *     }
 *
 *     bool adjustForSaveLayerPaints(SkRect* rect, int savesToIgnore = 0) const {
 *         for (int i = fSaveStack.size() - 1 - savesToIgnore; i >= 0; i--) {
 *             auto inverse = fSaveStack[i].ctm.invert();
 *             if (!inverse) {
 *                 return false;
 *             }
 *             inverse->mapRect(rect);
 *             if (!AdjustForPaint(fSaveStack[i].paint, rect)) {
 *                 return false;
 *             }
 *             fSaveStack[i].ctm.mapRect(rect);
 *         }
 *         return true;
 *     }
 *
 *     // We do not guarantee anything for operations outside of the cull rect
 *     const SkRect fCullRect;
 *
 *     // Conservative identity-space bounds for each op in the SkRecord.
 *     Bounds* fBounds;
 *
 *     // Parallel array to fBounds, holding metadata for each bounds rect.
 *     SkBBoxHierarchy::Metadata* fMeta;
 *
 *     // We walk fCurrentOp through the SkRecord,
 *     // as we go using updateCTM() to maintain the exact CTM (fCTM).
 *     int fCurrentOp;
 *     SkMatrix fCTM;
 *
 *     // Used to track the bounds of Save/Restore blocks and the control ops inside them.
 *     SkTDArray<SaveBounds> fSaveStack;
 *     SkTDArray<int>   fControlIndices;
 * }
 * ```
 */
public abstract class FillBounds public constructor(
  cullRect: SkRect,
  record: SkRecord,
  bounds: Array<SkRect>,
  meta: Array<SkBBoxHierarchy.Metadata>,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const SkRect fCullRect
   * ```
   */
  private val fCullRect: SkRect = TODO("Initialize fCullRect")

  /**
   * C++ original:
   * ```cpp
   * Bounds* fBounds
   * ```
   */
  private var fBounds: FillBoundsBounds? = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * SkBBoxHierarchy::Metadata* fMeta
   * ```
   */
  private var fMeta: SkBBoxHierarchy.Metadata? = TODO("Initialize fMeta")

  /**
   * C++ original:
   * ```cpp
   * int fCurrentOp
   * ```
   */
  private var fCurrentOp: Int = TODO("Initialize fCurrentOp")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix fCTM
   * ```
   */
  private var fCTM: SkMatrix = TODO("Initialize fCTM")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SaveBounds> fSaveStack
   * ```
   */
  private var fSaveStack: SkTDArray<SaveBounds> = TODO("Initialize fSaveStack")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<int>   fControlIndices
   * ```
   */
  private var fControlIndices: SkTDArray<Int> = TODO("Initialize fControlIndices")

  /**
   * C++ original:
   * ```cpp
   * void setCurrentOp(int currentOp) { fCurrentOp = currentOp; }
   * ```
   */
  public fun setCurrentOp(currentOp: Int) {
    TODO("Implement setCurrentOp")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator()(const T& op) {
   *         this->updateCTM(op);
   *         this->trackBounds(op);
   *     }
   * ```
   */
  public operator fun invoke(op: T) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds adjustAndMap(SkRect rect, const SkPaint* paint) const {
   *         // Inverted rectangles really confuse our BBHs.
   *         rect.sort();
   *
   *         // Adjust the rect for its own paint.
   *         if (!AdjustForPaint(paint, &rect)) {
   *             // The paint could do anything to our bounds.  The only safe answer is the cull.
   *             return fCullRect;
   *         }
   *
   *         // Adjust rect for all the paints from the SaveLayers we're inside.
   *         if (!this->adjustForSaveLayerPaints(&rect)) {
   *             // Same deal as above.
   *             return fCullRect;
   *         }
   *
   *         // Map the rect back to identity space.
   *         fCTM.mapRect(&rect);
   *
   *         // Nothing can draw outside the cull rect.
   *         if (!rect.intersect(fCullRect)) {
   *             return Bounds::MakeEmpty();
   *         }
   *
   *         return rect;
   *     }
   * ```
   */
  public fun adjustAndMap(rect: SkRect, paint: SkPaint?): FillBoundsBounds {
    TODO("Implement adjustAndMap")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const T&) {}
   * ```
   */
  private fun updateCTM(param0: T) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T> void updateCTM(const T&) {}
   *     void updateCTM(const Restore& op)   { fCTM = op.matrix; }
   * ```
   */
  private fun updateCTM(op: SetMatrix) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const SetMatrix& op) { fCTM = op.matrix; }
   * ```
   */
  private fun updateCTM(op: SetM44) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const SetM44& op)    { fCTM = op.matrix.asM33(); }
   * ```
   */
  private fun updateCTM(op: Concat44) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const Concat44& op)  { fCTM.preConcat(op.matrix.asM33()); }
   * ```
   */
  private fun updateCTM(op: Concat) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const Concat& op)    { fCTM.preConcat(op.matrix); }
   * ```
   */
  private fun updateCTM(op: Scale) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const Scale& op)     { fCTM.preScale(op.sx, op.sy); }
   * ```
   */
  private fun updateCTM(op: Translate) {
    TODO("Implement updateCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateCTM(const Translate& op) { fCTM.preTranslate(op.dx, op.dy); }
   * ```
   */
  private fun trackBounds(param0: Save) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Save&) {
   *         this->pushSaveBlock(nullptr, /*hasBackdropFilter=*/false);
   *     }
   * ```
   */
  private fun trackBounds(op: SaveLayer) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const SaveLayer& op) {
   *         this->pushSaveBlock(op.paint, /*hasBackdropFilter=*/op.backdrop != nullptr);
   *     }
   * ```
   */
  private fun trackBounds(param0: SaveBehind) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const SaveBehind&) {
   *         this->pushSaveBlock(nullptr, /*hasBackdropFilter=*/false);
   *     }
   * ```
   */
  private fun trackBounds(param0: Restore) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Restore&) {
   *         const bool isSaveLayer = fSaveStack.back().paint != nullptr;
   *         fBounds[fCurrentOp] = this->popSaveBlock();
   *         fMeta  [fCurrentOp].isDraw = isSaveLayer;
   *     }
   * ```
   */
  private fun trackBounds(param0: SetMatrix) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const SetMatrix&)         { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: SetM44) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const SetM44&)            { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: Concat) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Concat&)            { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: Concat44) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Concat44&)          { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: Scale) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Scale&)             { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: Translate) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const Translate&)         { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ClipRect) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ClipRect&)          { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ClipRRect) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ClipRRect&)         { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ClipPath) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ClipPath&)          { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ClipRegion) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ClipRegion&)        { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ClipShader) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ClipShader&)        { this->pushControl(); }
   * ```
   */
  private fun trackBounds(param0: ResetClip) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const ResetClip&)         { this->pushControl(); }
   * ```
   */
  private fun trackBounds(op: T) {
    TODO("Implement trackBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackBounds(const T& op) {
   *         fBounds[fCurrentOp] = this->bounds(op);
   *         fMeta  [fCurrentOp].isDraw = true;
   *         this->updateSaveBounds(fBounds[fCurrentOp]);
   *     }
   * ```
   */
  private fun pushSaveBlock(paint: SkPaint?, hasBackdropFilter: Boolean) {
    TODO("Implement pushSaveBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushSaveBlock(const SkPaint* paint, bool hasBackdropFilter) {
   *         // Starting a new Save block.  Push a new entry to represent that.
   *         SaveBounds sb;
   *         sb.controlOps = 0;
   *
   *         // If the paint affects transparent black, or we have a backdrop filter,
   *         // the bound shouldn't be smaller than the cull.
   *         bool affectsFullCullRect = hasBackdropFilter || PaintMayAffectTransparentBlack(paint);
   *         sb.bounds = affectsFullCullRect ? fCullRect : Bounds::MakeEmpty();
   *         sb.paint = paint;
   *         sb.ctm = this->fCTM;
   *
   *         fSaveStack.push_back(sb);
   *         this->pushControl();
   *     }
   * ```
   */
  private fun popSaveBlock(): FillBoundsBounds {
    TODO("Implement popSaveBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds popSaveBlock() {
   *         // We're done the Save block.  Apply the block's bounds to all control ops inside it.
   *         SaveBounds sb = fSaveStack.back();
   *         fSaveStack.pop_back();
   *
   *         while (sb.controlOps --> 0) {
   *             this->popControl(sb.bounds);
   *         }
   *
   *         // This whole Save block may be part another Save block.
   *         this->updateSaveBounds(sb.bounds);
   *
   *         // If called from a real Restore (not a phony one for balance), it'll need the bounds.
   *         return sb.bounds;
   *     }
   * ```
   */
  private fun pushControl() {
    TODO("Implement pushControl")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushControl() {
   *         fControlIndices.push_back(fCurrentOp);
   *         if (!fSaveStack.empty()) {
   *             fSaveStack.back().controlOps++;
   *         }
   *     }
   * ```
   */
  private fun popControl(bounds: FillBoundsBounds) {
    TODO("Implement popControl")
  }

  /**
   * C++ original:
   * ```cpp
   * void popControl(const Bounds& bounds) {
   *         fBounds[fControlIndices.back()] = bounds;
   *         fMeta  [fControlIndices.back()].isDraw = false;
   *         fControlIndices.pop_back();
   *     }
   * ```
   */
  private fun updateSaveBounds(bounds: FillBoundsBounds) {
    TODO("Implement updateSaveBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateSaveBounds(const Bounds& bounds) {
   *         // If we're in a Save block, expand its bounds to cover these bounds too.
   *         if (!fSaveStack.empty()) {
   *             fSaveStack.back().bounds.join(bounds);
   *         }
   *     }
   * ```
   */
  private fun bounds(param0: DrawPaint): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawPaint&) const { return fCullRect; }
   * ```
   */
  private fun bounds(param0: DrawBehind): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawBehind&) const { return fCullRect; }
   * ```
   */
  private fun bounds(param0: NoOp): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const NoOp&)  const { return Bounds::MakeEmpty(); }
   * ```
   */
  private fun bounds(op: DrawRect): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawRect& op) const { return this->adjustAndMap(op.rect, &op.paint); }
   * ```
   */
  private fun bounds(op: DrawRegion): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawRegion& op) const {
   *         SkRect rect = SkRect::Make(op.region.getBounds());
   *         return this->adjustAndMap(rect, &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawOval): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawOval& op) const { return this->adjustAndMap(op.oval, &op.paint); }
   * ```
   */
  private fun bounds(op: DrawArc): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawArc& op) const { return this->adjustAndMap(op.oval, &op.paint); }
   * ```
   */
  private fun bounds(op: DrawRRect): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawRRect& op) const {
   *         return this->adjustAndMap(op.rrect.rect(), &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawDRRect): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawDRRect& op) const {
   *         return this->adjustAndMap(op.outer.rect(), &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawImage): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawImage& op) const {
   *         const SkImage* image = op.image.get();
   *         SkRect rect = SkRect::MakeXYWH(op.left, op.top, image->width(), image->height());
   *
   *         return this->adjustAndMap(rect, op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawImageLattice): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawImageLattice& op) const {
   *         return this->adjustAndMap(op.dst, op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawImageRect): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawImageRect& op) const {
   *         return this->adjustAndMap(op.dst, op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawPath): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawPath& op) const {
   *         return op.path.isInverseFillType() ? fCullRect
   *                                            : this->adjustAndMap(op.path.getBounds(), &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawPoints): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawPoints& op) const {
   *         SkRect dst = SkRect::BoundsOrEmpty({op.pts.data(), op.count});
   *
   *         // Pad the bounding box a little to make sure hairline points' bounds aren't empty.
   *         SkScalar stroke = std::max(op.paint.getStrokeWidth(), 0.01f);
   *         dst.outset(stroke/2, stroke/2);
   *
   *         return this->adjustAndMap(dst, &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawPatch): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawPatch& op) const {
   *         const auto dst = SkRect::BoundsOrEmpty({op.cubics.data(), (size_t)SkPatchUtils::kNumCtrlPts});
   *         return this->adjustAndMap(dst, &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawVertices): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawVertices& op) const {
   *         return this->adjustAndMap(op.vertices->bounds(), &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawMesh): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawMesh& op) const {
   *         return this->adjustAndMap(op.mesh.bounds(), &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawAtlas): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawAtlas& op) const {
   *         if (op.cull) {
   *             // TODO: <reed> can we pass nullptr for the paint? Isn't cull already "correct"
   *             // for the paint (by the caller)?
   *             return this->adjustAndMap(*op.cull, op.paint);
   *         } else {
   *             return fCullRect;
   *         }
   *     }
   * ```
   */
  private fun bounds(op: DrawShadowRec): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawShadowRec& op) const {
   *         SkRect bounds;
   *         SkDrawShadowMetrics::GetLocalBounds(op.path, op.rec, fCTM, &bounds);
   *         return this->adjustAndMap(bounds, nullptr);
   *     }
   * ```
   */
  private fun bounds(op: DrawPicture): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawPicture& op) const {
   *         SkRect dst = op.picture->cullRect();
   *         op.matrix.mapRect(&dst);
   *         return this->adjustAndMap(dst, op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawTextBlob): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawTextBlob& op) const {
   *         SkRect dst = op.blob->bounds();
   *         dst.offset(op.x, op.y);
   *         return this->adjustAndMap(dst, &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawSlug): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawSlug& op) const {
   *         SkRect dst = op.slug->sourceBoundsWithOrigin();
   *         return this->adjustAndMap(dst, &op.paint);
   *     }
   * ```
   */
  private fun bounds(op: DrawDrawable): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawDrawable& op) const {
   *         return this->adjustAndMap(op.worstCaseBounds, nullptr);
   *     }
   * ```
   */
  private fun bounds(op: DrawAnnotation): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawAnnotation& op) const {
   *         return this->adjustAndMap(op.rect, nullptr);
   *     }
   * ```
   */
  private fun bounds(op: DrawEdgeAAQuad): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawEdgeAAQuad& op) const {
   *         const auto bounds = op.clip ? SkRect::BoundsOrEmpty({op.clip.data(), 4}) : op.rect;
   *         return this->adjustAndMap(bounds, nullptr);
   *     }
   * ```
   */
  private fun bounds(op: DrawEdgeAAImageSet): FillBoundsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Bounds bounds(const DrawEdgeAAImageSet& op) const {
   *         SkRect rect = SkRect::MakeEmpty();
   *         int clipIndex = 0;
   *         for (int i = 0; i < op.count; ++i) {
   *             SkRect entryBounds = op.set[i].fDstRect;
   *             if (op.set[i].fHasClip) {
   *                 entryBounds = SkRect::BoundsOrEmpty({op.dstClips + clipIndex, 4});
   *                 clipIndex += 4;
   *             }
   *             if (op.set[i].fMatrixIndex >= 0) {
   *                 op.preViewMatrices[op.set[i].fMatrixIndex].mapRect(&entryBounds);
   *             }
   *             rect.join(this->adjustAndMap(entryBounds, nullptr));
   *         }
   *         return rect;
   *     }
   * ```
   */
  private abstract fun adjustForSaveLayerPaints(rect: SkRect?, savesToIgnore: Int = 0): Boolean

  public data class SaveBounds public constructor(
    public var controlOps: Int,
    public var bounds: Bounds,
    public val paint: SkPaint?,
    public var ctm: SkMatrix,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool PaintMayAffectTransparentBlack(const SkPaint* paint) {
     *         if (paint) {
     *             // FIXME: this is very conservative
     *             if ((paint->getImageFilter() &&
     *                  as_IFB(paint->getImageFilter())->affectsTransparentBlack()) ||
     *                 (paint->getColorFilter() &&
     *                  as_CFB(paint->getColorFilter())->affectsTransparentBlack())) {
     *                 return true;
     *             }
     *             const auto bm = paint->asBlendMode();
     *             if (!bm) {
     *                 return true;    // can we query other blenders for this?
     *             }
     *
     *             // Unusual blendmodes require us to process a saved layer
     *             // even with operations outisde the clip.
     *             // For example, DstIn is used by masking layers.
     *             // https://code.google.com/p/skia/issues/detail?id=1291
     *             // https://crbug.com/401593
     *             switch (bm.value()) {
     *                 // For each of the following transfer modes, if the source
     *                 // alpha is zero (our transparent black), the resulting
     *                 // blended alpha is not necessarily equal to the original
     *                 // destination alpha.
     *                 case SkBlendMode::kClear:
     *                 case SkBlendMode::kSrc:
     *                 case SkBlendMode::kSrcIn:
     *                 case SkBlendMode::kDstIn:
     *                 case SkBlendMode::kSrcOut:
     *                 case SkBlendMode::kDstATop:
     *                 case SkBlendMode::kModulate:
     *                     return true;
     *                 default:
     *                     break;
     *             }
     *         }
     *         return false;
     *     }
     * ```
     */
    private fun paintMayAffectTransparentBlack(paint: SkPaint?): Boolean {
      TODO("Implement paintMayAffectTransparentBlack")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool AdjustForPaint(const SkPaint* paint, SkRect* rect) {
     *         if (paint) {
     *             if (paint->canComputeFastBounds()) {
     *                 *rect = paint->computeFastBounds(*rect, rect);
     *                 return true;
     *             }
     *             return false;
     *         }
     *         return true;
     *     }
     * ```
     */
    private fun adjustForPaint(paint: SkPaint?, rect: SkRect?): Boolean {
      TODO("Implement adjustForPaint")
    }
  }
}
