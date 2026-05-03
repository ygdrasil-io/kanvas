package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriter32
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.memory.SkTDArray
import org.skia.utils.Slug
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
 * class SkPictureRecord : public SkCanvasVirtualEnforcer<SkCanvas> {
 * public:
 *     SkPictureRecord(const SkISize& dimensions, uint32_t recordFlags);
 *
 *     SkPictureRecord(const SkIRect& dimensions, uint32_t recordFlags);
 *
 *     const skia_private::TArray<sk_sp<const SkPicture>>& getPictures() const {
 *         return fPictures;
 *     }
 *
 *     const skia_private::TArray<sk_sp<SkDrawable>>& getDrawables() const {
 *         return fDrawables;
 *     }
 *
 *     const skia_private::TArray<sk_sp<const SkTextBlob>>& getTextBlobs() const {
 *         return fTextBlobs;
 *     }
 *
 *     const skia_private::TArray<sk_sp<const sktext::gpu::Slug>>& getSlugs() const {
 *         return fSlugs;
 *     }
 *
 *     const skia_private::TArray<sk_sp<const SkVertices>>& getVertices() const {
 *         return fVertices;
 *     }
 *
 *     const skia_private::TArray<sk_sp<const SkImage>>& getImages() const {
 *         return fImages;
 *     }
 *
 *     sk_sp<SkData> opData() const {
 *         this->validate(fWriter.bytesWritten(), 0);
 *
 *         if (fWriter.bytesWritten() == 0) {
 *             return SkData::MakeEmpty();
 *         }
 *         return fWriter.snapshotAsData();
 *     }
 *
 *     void setFlags(uint32_t recordFlags) {
 *         fRecordFlags = recordFlags;
 *     }
 *
 *     const SkWriter32& writeStream() const {
 *         return fWriter;
 *     }
 *
 *     void beginRecording();
 *     void endRecording();
 *
 * protected:
 *     void addNoOp();
 *
 * private:
 *     void handleOptimization(int opt);
 *     size_t recordRestoreOffsetPlaceholder();
 *     void fillRestoreOffsetPlaceholdersForCurrentStackLevel(uint32_t restoreOffset);
 *
 *     SkTDArray<int32_t> fRestoreOffsetStack;
 *
 *     SkTDArray<uint32_t> fCullOffsetStack;
 *
 *     /*
 *      * Write the 'drawType' operation and chunk size to the skp. 'size'
 *      * can potentially be increased if the chunk size needs its own storage
 *      * location (i.e., it overflows 24 bits).
 *      * Returns the start offset of the chunk. This is the location at which
 *      * the opcode & size are stored.
 *      * TODO: since we are handing the size into here we could call reserve
 *      * and then return a pointer to the memory storage. This could decrease
 *      * allocation overhead but could lead to more wasted space (the tail
 *      * end of blocks could go unused). Possibly add a second addDraw that
 *      * operates in this manner.
 *      */
 *     size_t addDraw(DrawType drawType, size_t* size) {
 *         size_t offset = fWriter.bytesWritten();
 *
 *         SkASSERT_RELEASE(this->predrawNotify());
 *
 *         SkASSERT(0 != *size);
 *         SkASSERT(((uint8_t) drawType) == drawType);
 *
 *         if (0 != (*size & ~MASK_24) || *size == MASK_24) {
 *             fWriter.writeInt(PACK_8_24(drawType, MASK_24));
 *             *size += 1;
 *             fWriter.writeInt(SkToU32(*size));
 *         } else {
 *             fWriter.writeInt(PACK_8_24(drawType, SkToU32(*size)));
 *         }
 *
 *         return offset;
 *     }
 *
 *     void addInt(int value) {
 *         fWriter.writeInt(value);
 *     }
 *     void addScalar(SkScalar scalar) {
 *         fWriter.writeScalar(scalar);
 *     }
 *
 *     void addImage(const SkImage*);
 *     void addMatrix(const SkMatrix& matrix);
 *     void addPaint(const SkPaint& paint) { this->addPaintPtr(&paint); }
 *     void addPaintPtr(const SkPaint* paint);
 *     void addPatch(const SkPoint cubics[12]);
 *     void addPath(const SkPath& path);
 *     void addPicture(const SkPicture* picture);
 *     void addDrawable(SkDrawable* picture);
 *     void addPoint(const SkPoint& point);
 *     void addPoints(const SkPoint pts[], int count);
 *     void addRect(const SkRect& rect);
 *     void addRectPtr(const SkRect* rect);
 *     void addIRect(const SkIRect& rect);
 *     void addIRectPtr(const SkIRect* rect);
 *     void addRRect(const SkRRect&);
 *     void addRegion(const SkRegion& region);
 *     void addSampling(const SkSamplingOptions&);
 *     void addText(const void* text, size_t byteLength);
 *     void addTextBlob(const SkTextBlob* blob);
 *     void addSlug(const sktext::gpu::Slug* slug);
 *     void addVertices(const SkVertices*);
 *
 *     int find(const SkBitmap& bitmap);
 *
 * protected:
 *     void validate(size_t initialOffset, size_t size) const {
 *         SkASSERT(fWriter.bytesWritten() == initialOffset + size);
 *     }
 *
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo&, const SkSurfaceProps&) override;
 *     bool onPeekPixels(SkPixmap*) override { return false; }
 *
 *     void willSave() override;
 *     SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec&) override;
 *     bool onDoSaveBehind(const SkRect*) override;
 *     void willRestore() override;
 *
 *     void didConcat44(const SkM44&) override;
 *     void didSetM44(const SkM44&) override;
 *     void didScale(SkScalar, SkScalar) override;
 *     void didTranslate(SkScalar, SkScalar) override;
 *
 *     void onDrawDRRect(const SkRRect&, const SkRRect&, const SkPaint&) override;
 *
 *     void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
 *                                 const SkPaint& paint) override;
 *     void onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) override;
 *     void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                      const SkPoint texCoords[4], SkBlendMode, const SkPaint& paint) override;
 *
 *     void onDrawPaint(const SkPaint&) override;
 *     void onDrawBehind(const SkPaint&) override;
 *     void onDrawPoints(PointMode, size_t count, const SkPoint pts[], const SkPaint&) override;
 *     void onDrawRect(const SkRect&, const SkPaint&) override;
 *     void onDrawRegion(const SkRegion&, const SkPaint&) override;
 *     void onDrawOval(const SkRect&, const SkPaint&) override;
 *     void onDrawArc(const SkRect&, SkScalar, SkScalar, bool, const SkPaint&) override;
 *     void onDrawRRect(const SkRRect&, const SkPaint&) override;
 *     void onDrawPath(const SkPath&, const SkPaint&) override;
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
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override;
 *     void onDrawVerticesObject(const SkVertices*, SkBlendMode, const SkPaint&) override;
 *
 *     void onClipRect(const SkRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipRRect(const SkRRect&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipPath(const SkPath&, SkClipOp, ClipEdgeStyle) override;
 *     void onClipShader(sk_sp<SkShader>, SkClipOp) override;
 *     void onClipRegion(const SkRegion&, SkClipOp) override;
 *     void onResetClip() override;
 *
 *     void onDrawPicture(const SkPicture*, const SkMatrix*, const SkPaint*) override;
 *
 *     void onDrawDrawable(SkDrawable*, const SkMatrix*) override;
 *     void onDrawAnnotation(const SkRect&, const char[], SkData*) override;
 *
 *     void onDrawEdgeAAQuad(const SkRect&, const SkPoint[4], QuadAAFlags, const SkColor4f&,
 *                           SkBlendMode) override;
 *     void onDrawEdgeAAImageSet2(const ImageSetEntry[], int count, const SkPoint[], const SkMatrix[],
 *                                const SkSamplingOptions&,const SkPaint*, SrcRectConstraint) override;
 *
 *     int addPathToHeap(const SkPath& path);  // does not write to ops stream
 *
 *     // These entry points allow the writing of matrices, clips, saves &
 *     // restores to be deferred (e.g., if the MC state is being collapsed and
 *     // only written out as needed).
 *     void recordConcat(const SkMatrix& matrix);
 *     void recordTranslate(const SkMatrix& matrix);
 *     void recordScale(const SkMatrix& matrix);
 *     size_t recordClipRect(const SkRect& rect, SkClipOp op, bool doAA);
 *     size_t recordClipRRect(const SkRRect& rrect, SkClipOp op, bool doAA);
 *     size_t recordClipPath(int pathID, SkClipOp op, bool doAA);
 *     size_t recordClipRegion(const SkRegion& region, SkClipOp op);
 *     void recordSave();
 *     void recordSaveLayer(const SaveLayerRec&);
 *     void recordRestore(bool fillInSkips = true);
 *
 * private:
 *     skia_private::TArray<SkPaint>  fPaints;
 *
 *     struct PathHash {
 *         uint32_t operator()(const SkPath& p) { return p.getGenerationID(); }
 *     };
 *     skia_private::THashMap<SkPath, int, PathHash> fPaths;
 *
 *     SkWriter32 fWriter;
 *
 *     skia_private::TArray<sk_sp<const SkImage>>    fImages;
 *     skia_private::TArray<sk_sp<const SkPicture>>  fPictures;
 *     skia_private::TArray<sk_sp<SkDrawable>>       fDrawables;
 *     skia_private::TArray<sk_sp<const SkTextBlob>> fTextBlobs;
 *     skia_private::TArray<sk_sp<const SkVertices>> fVertices;
 *     skia_private::TArray<sk_sp<const sktext::gpu::Slug>> fSlugs;
 *
 *     uint32_t fRecordFlags;
 *     int      fInitialSaveCount;
 *
 *     friend class SkPictureData;   // for SkPictureData's SkPictureRecord-based constructor
 * }
 * ```
 */
public open class SkPictureRecord public constructor(
  dimensions: SkISize,
  recordFlags: UInt,
) : SkCanvasVirtualEnforcer(),
    SkCanvas {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<int32_t> fRestoreOffsetStack
   * ```
   */
  private var fRestoreOffsetStack: SkTDArray<Int> = TODO("Initialize fRestoreOffsetStack")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<uint32_t> fCullOffsetStack
   * ```
   */
  private var fCullOffsetStack: SkTDArray<UInt> = TODO("Initialize fCullOffsetStack")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkPaint>  fPaints
   * ```
   */
  private var fPaints: Int = TODO("Initialize fPaints")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<SkPath, int, PathHash> fPaths
   * ```
   */
  private var fPaths: THashMap<SkPath, Int, PathHash> = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * SkWriter32 fWriter
   * ```
   */
  private var fWriter: SkWriter32 = TODO("Initialize fWriter")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkImage>>    fImages
   * ```
   */
  private var fImages: Int = TODO("Initialize fImages")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkPicture>>  fPictures
   * ```
   */
  private var fPictures: Int = TODO("Initialize fPictures")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkDrawable>>       fDrawables
   * ```
   */
  private var fDrawables: Int = TODO("Initialize fDrawables")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkTextBlob>> fTextBlobs
   * ```
   */
  private var fTextBlobs: Int = TODO("Initialize fTextBlobs")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkVertices>> fVertices
   * ```
   */
  private var fVertices: Int = TODO("Initialize fVertices")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const sktext::gpu::Slug>> fSlugs
   * ```
   */
  public var fSlugs: Int = TODO("Initialize fSlugs")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fRecordFlags
   * ```
   */
  private var fRecordFlags: UInt = TODO("Initialize fRecordFlags")

  /**
   * C++ original:
   * ```cpp
   * int      fInitialSaveCount
   * ```
   */
  private var fInitialSaveCount: Int = TODO("Initialize fInitialSaveCount")

  /**
   * C++ original:
   * ```cpp
   * SkPictureRecord::SkPictureRecord(const SkISize& dimensions, uint32_t flags)
   *     : SkPictureRecord(SkIRect::MakeSize(dimensions), flags) {}
   * ```
   */
  public constructor(dimensions: SkIRect, recordFlags: UInt) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<const SkPicture>>& getPictures() const {
   *         return fPictures;
   *     }
   * ```
   */
  public fun getPictures(): TArray<SkSp<SkPicture>> {
    TODO("Implement getPictures")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<SkDrawable>>& getDrawables() const {
   *         return fDrawables;
   *     }
   * ```
   */
  public fun getDrawables(): TArray<SkSp<SkDrawable>> {
    TODO("Implement getDrawables")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<const SkTextBlob>>& getTextBlobs() const {
   *         return fTextBlobs;
   *     }
   * ```
   */
  public fun getTextBlobs(): TArray<SkSp<SkTextBlob>> {
    TODO("Implement getTextBlobs")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<const sktext::gpu::Slug>>& getSlugs() const {
   *         return fSlugs;
   *     }
   * ```
   */
  public fun getSlugs(): TArray<SkSp<Slug>> {
    TODO("Implement getSlugs")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<const SkVertices>>& getVertices() const {
   *         return fVertices;
   *     }
   * ```
   */
  public fun getVertices(): TArray<SkSp<SkVertices>> {
    TODO("Implement getVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<sk_sp<const SkImage>>& getImages() const {
   *         return fImages;
   *     }
   * ```
   */
  public fun getImages(): TArray<SkSp<SkImage>> {
    TODO("Implement getImages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> opData() const {
   *         this->validate(fWriter.bytesWritten(), 0);
   *
   *         if (fWriter.bytesWritten() == 0) {
   *             return SkData::MakeEmpty();
   *         }
   *         return fWriter.snapshotAsData();
   *     }
   * ```
   */
  public fun opData(): SkSp<SkData> {
    TODO("Implement opData")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFlags(uint32_t recordFlags) {
   *         fRecordFlags = recordFlags;
   *     }
   * ```
   */
  public fun setFlags(recordFlags: UInt) {
    TODO("Implement setFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkWriter32& writeStream() const {
   *         return fWriter;
   *     }
   * ```
   */
  public fun writeStream(): SkWriter32 {
    TODO("Implement writeStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::beginRecording() {
   *     // we have to call this *after* our constructor, to ensure that it gets
   *     // recorded. This is balanced by restoreToCount() call from endRecording,
   *     // which in-turn calls our overridden restore(), so those get recorded too.
   *     fInitialSaveCount = this->save();
   * }
   * ```
   */
  public fun beginRecording() {
    TODO("Implement beginRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::endRecording() {
   *     SkASSERT(kNoInitialSave != fInitialSaveCount);
   *     this->restoreToCount(fInitialSaveCount);
   * }
   * ```
   */
  public fun endRecording() {
    TODO("Implement endRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addNoOp() {
   *     size_t size = kUInt32Size; // op
   *     this->addDraw(NOOP, &size);
   * }
   * ```
   */
  protected fun addNoOp() {
    TODO("Implement addNoOp")
  }

  /**
   * C++ original:
   * ```cpp
   * void handleOptimization(int opt)
   * ```
   */
  private fun handleOptimization(opt: Int) {
    TODO("Implement handleOptimization")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureRecord::recordRestoreOffsetPlaceholder() {
   *     if (fRestoreOffsetStack.empty()) {
   *         return -1;
   *     }
   *
   *     // The RestoreOffset field is initially filled with a placeholder
   *     // value that points to the offset of the previous RestoreOffset
   *     // in the current stack level, thus forming a linked list so that
   *     // the restore offsets can be filled in when the corresponding
   *     // restore command is recorded.
   *     int32_t prevOffset = fRestoreOffsetStack.back();
   *
   *     size_t offset = fWriter.bytesWritten();
   *     this->addInt(prevOffset);
   *     fRestoreOffsetStack.back() = SkToU32(offset);
   *     return offset;
   * }
   * ```
   */
  private fun recordRestoreOffsetPlaceholder(): ULong {
    TODO("Implement recordRestoreOffsetPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::fillRestoreOffsetPlaceholdersForCurrentStackLevel(uint32_t restoreOffset) {
   *     int32_t offset = fRestoreOffsetStack.back();
   *     while (offset > 0) {
   *         uint32_t peek = fWriter.readTAt<uint32_t>(offset);
   *         fWriter.overwriteTAt(offset, restoreOffset);
   *         offset = peek;
   *     }
   *
   * #ifdef SK_DEBUG
   *     // offset of 0 has been disabled, so we skip it
   *     if (offset > 0) {
   *         // assert that the final offset value points to a save verb
   *         uint32_t opSize;
   *         DrawType drawOp = peek_op_and_size(&fWriter, -offset, &opSize);
   *         SkASSERT(SAVE == drawOp || SAVE_LAYER_SAVELAYERREC == drawOp);
   *     }
   * #endif
   * }
   * ```
   */
  private fun fillRestoreOffsetPlaceholdersForCurrentStackLevel(restoreOffset: UInt) {
    TODO("Implement fillRestoreOffsetPlaceholdersForCurrentStackLevel")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t addDraw(DrawType drawType, size_t* size) {
   *         size_t offset = fWriter.bytesWritten();
   *
   *         SkASSERT_RELEASE(this->predrawNotify());
   *
   *         SkASSERT(0 != *size);
   *         SkASSERT(((uint8_t) drawType) == drawType);
   *
   *         if (0 != (*size & ~MASK_24) || *size == MASK_24) {
   *             fWriter.writeInt(PACK_8_24(drawType, MASK_24));
   *             *size += 1;
   *             fWriter.writeInt(SkToU32(*size));
   *         } else {
   *             fWriter.writeInt(PACK_8_24(drawType, SkToU32(*size)));
   *         }
   *
   *         return offset;
   *     }
   * ```
   */
  private fun addDraw(drawType: DrawType, size: ULong?): ULong {
    TODO("Implement addDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void addInt(int value) {
   *         fWriter.writeInt(value);
   *     }
   * ```
   */
  private fun addInt(`value`: Int) {
    TODO("Implement addInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void addScalar(SkScalar scalar) {
   *         fWriter.writeScalar(scalar);
   *     }
   * ```
   */
  private fun addScalar(scalar: SkScalar) {
    TODO("Implement addScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addImage(const SkImage* image) {
   *     // convention for images is 0-based index
   *     this->addInt(find_or_append(fImages, image));
   * }
   * ```
   */
  private fun addImage(image: SkImage?) {
    TODO("Implement addImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addMatrix(const SkMatrix& matrix) {
   *     fWriter.writeMatrix(matrix);
   * }
   * ```
   */
  private fun addMatrix(matrix: SkMatrix) {
    TODO("Implement addMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void addPaint(const SkPaint& paint) { this->addPaintPtr(&paint); }
   * ```
   */
  private fun addPaint(paint: SkPaint) {
    TODO("Implement addPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPaintPtr(const SkPaint* paint) {
   *     if (paint) {
   *         fPaints.push_back(*paint);
   *         this->addInt(fPaints.size());
   *     } else {
   *         this->addInt(0);
   *     }
   * }
   * ```
   */
  private fun addPaintPtr(paint: SkPaint?) {
    TODO("Implement addPaintPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPatch(const SkPoint cubics[12]) {
   *     fWriter.write(cubics, SkPatchUtils::kNumCtrlPts * sizeof(SkPoint));
   * }
   * ```
   */
  private fun addPatch(cubics: Array<SkPoint>) {
    TODO("Implement addPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPath(const SkPath& path) {
   *     this->addInt(this->addPathToHeap(path));
   * }
   * ```
   */
  private fun addPath(path: SkPath) {
    TODO("Implement addPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPicture(const SkPicture* picture) {
   *     // follow the convention of recording a 1-based index
   *     this->addInt(find_or_append(fPictures, picture) + 1);
   * }
   * ```
   */
  private fun addPicture(picture: SkPicture?) {
    TODO("Implement addPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addDrawable(SkDrawable* drawable) {
   *     // follow the convention of recording a 1-based index
   *     this->addInt(find_or_append(fDrawables, drawable) + 1);
   * }
   * ```
   */
  private fun addDrawable(picture: SkDrawable?) {
    TODO("Implement addDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPoint(const SkPoint& point) {
   *     fWriter.writePoint(point);
   * }
   * ```
   */
  private fun addPoint(point: SkPoint) {
    TODO("Implement addPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addPoints(const SkPoint pts[], int count) {
   *     fWriter.writeMul4(pts, count * sizeof(SkPoint));
   * }
   * ```
   */
  private fun addPoints(pts: Array<SkPoint>, count: Int) {
    TODO("Implement addPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addRect(const SkRect& rect) {
   *     fWriter.writeRect(rect);
   * }
   * ```
   */
  private fun addRect(rect: SkRect) {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addRectPtr(const SkRect* rect) {
   *     if (fWriter.writeBool(rect != nullptr)) {
   *         fWriter.writeRect(*rect);
   *     }
   * }
   * ```
   */
  private fun addRectPtr(rect: SkRect?) {
    TODO("Implement addRectPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addIRect(const SkIRect& rect) {
   *     fWriter.write(&rect, sizeof(rect));
   * }
   * ```
   */
  private fun addIRect(rect: SkIRect) {
    TODO("Implement addIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addIRectPtr(const SkIRect* rect) {
   *     if (fWriter.writeBool(rect != nullptr)) {
   *         *(SkIRect*)fWriter.reserve(sizeof(SkIRect)) = *rect;
   *     }
   * }
   * ```
   */
  private fun addIRectPtr(rect: SkIRect?) {
    TODO("Implement addIRectPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addRRect(const SkRRect& rrect) {
   *     fWriter.writeRRect(rrect);
   * }
   * ```
   */
  private fun addRRect(rrect: SkRRect) {
    TODO("Implement addRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addRegion(const SkRegion& region) {
   *     fWriter.writeRegion(region);
   * }
   * ```
   */
  private fun addRegion(region: SkRegion) {
    TODO("Implement addRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addSampling(const SkSamplingOptions& sampling) {
   *     fWriter.writeSampling(sampling);
   * }
   * ```
   */
  private fun addSampling(sampling: SkSamplingOptions) {
    TODO("Implement addSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addText(const void* text, size_t byteLength) {
   *     addInt(SkToInt(byteLength));
   *     fWriter.writePad(text, byteLength);
   * }
   * ```
   */
  private fun addText(text: Unit?, byteLength: ULong) {
    TODO("Implement addText")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addTextBlob(const SkTextBlob* blob) {
   *     // follow the convention of recording a 1-based index
   *     this->addInt(find_or_append(fTextBlobs, blob) + 1);
   * }
   * ```
   */
  private fun addTextBlob(blob: SkTextBlob?) {
    TODO("Implement addTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addSlug(const sktext::gpu::Slug* slug) {
   *     // follow the convention of recording a 1-based index
   *     this->addInt(find_or_append(fSlugs, slug) + 1);
   * }
   * ```
   */
  private fun addSlug(slug: Slug?) {
    TODO("Implement addSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::addVertices(const SkVertices* vertices) {
   *     // follow the convention of recording a 1-based index
   *     this->addInt(find_or_append(fVertices, vertices) + 1);
   * }
   * ```
   */
  private fun addVertices(vertices: SkVertices?) {
    TODO("Implement addVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * int find(const SkBitmap& bitmap)
   * ```
   */
  private fun find(bitmap: SkBitmap): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate(size_t initialOffset, size_t size) const {
   *         SkASSERT(fWriter.bytesWritten() == initialOffset + size);
   *     }
   * ```
   */
  protected fun validate(initialOffset: ULong, size: ULong) {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkPictureRecord::onNewSurface(const SkImageInfo& info, const SkSurfaceProps&) {
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onNewSurface(info: SkImageInfo, param1: SkSurfaceProps): SkSp<SkSurface> {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onPeekPixels(SkPixmap*) override { return false; }
   * ```
   */
  protected override fun onPeekPixels(param0: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::willSave() {
   *     // record the offset to us, making it non-positive to distinguish a save
   *     // from a clip entry.
   *     fRestoreOffsetStack.push_back(-(int32_t)fWriter.bytesWritten());
   *     this->recordSave();
   *
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::willSave();
   * }
   * ```
   */
  protected override fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SaveLayerStrategy SkPictureRecord::getSaveLayerStrategy(const SaveLayerRec& rec) {
   *     // record the offset to us, making it non-positive to distinguish a save
   *     // from a clip entry.
   *     fRestoreOffsetStack.push_back(-(int32_t)fWriter.bytesWritten());
   *     this->recordSaveLayer(rec);
   *
   *     (void)this->SkCanvasVirtualEnforcer<SkCanvas>::getSaveLayerStrategy(rec);
   *     /*  No need for a (potentially very big) layer which we don't actually need
   *         at this time (and may not be able to afford since during record our
   *         clip starts out the size of the picture, which is often much larger
   *         than the size of the actual device we'll use during playback).
   *      */
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
   * bool SkPictureRecord::onDoSaveBehind(const SkRect* subset) {
   *     fRestoreOffsetStack.push_back(-(int32_t)fWriter.bytesWritten());
   *
   *     size_t size = sizeof(kUInt32Size) + sizeof(uint32_t); // op + flags
   *     uint32_t flags = 0;
   *     if (subset) {
   *         flags |= SAVEBEHIND_HAS_SUBSET;
   *         size += sizeof(*subset);
   *     }
   *
   *     size_t initialOffset = this->addDraw(SAVE_BEHIND, &size);
   *     this->addInt(flags);
   *     if (subset) {
   *         this->addRect(*subset);
   *     }
   *
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::willRestore() {
   * #if 0
   *     SkASSERT(fRestoreOffsetStack.count() > 1);
   * #endif
   *
   *     // check for underflow
   *     if (fRestoreOffsetStack.empty()) {
   *         return;
   *     }
   *
   *     this->recordRestore();
   *
   *     fRestoreOffsetStack.pop_back();
   *
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::willRestore();
   * }
   * ```
   */
  protected override fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::didConcat44(const SkM44& m) {
   *     this->validate(fWriter.bytesWritten(), 0);
   *     // op + matrix
   *     size_t size = kUInt32Size + 16 * sizeof(SkScalar);
   *     size_t initialOffset = this->addDraw(CONCAT44, &size);
   *     fWriter.write(SkMatrixPriv::M44ColMajor(m), 16 * sizeof(SkScalar));
   *     this->validate(initialOffset, size);
   *
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::didConcat44(m);
   * }
   * ```
   */
  protected override fun didConcat44(m: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::didSetM44(const SkM44& m) {
   *     this->validate(fWriter.bytesWritten(), 0);
   *     // op + matrix
   *     size_t size = kUInt32Size + 16 * sizeof(SkScalar);
   *     size_t initialOffset = this->addDraw(SET_M44, &size);
   *     fWriter.write(SkMatrixPriv::M44ColMajor(m), 16 * sizeof(SkScalar));
   *     this->validate(initialOffset, size);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::didSetM44(m);
   * }
   * ```
   */
  protected override fun didSetM44(m: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::didScale(SkScalar x, SkScalar y) {
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
   * void SkPictureRecord::didTranslate(SkScalar x, SkScalar y) {
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
   * void SkPictureRecord::onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
   *                                    const SkPaint& paint) {
   *     // op + paint index + rrects
   *     size_t size = 2 * kUInt32Size + SkRRect::kSizeInMemory * 2;
   *     size_t initialOffset = this->addDraw(DRAW_DRRECT, &size);
   *     this->addPaint(paint);
   *     this->addRRect(outer);
   *     this->addRRect(inner);
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
   *                                      const SkPaint& paint) {
   *
   *     // op + paint index + blob index + x/y
   *     size_t size = 3 * kUInt32Size + 2 * sizeof(SkScalar);
   *     size_t initialOffset = this->addDraw(DRAW_TEXT_BLOB, &size);
   *
   *     this->addPaint(paint);
   *     this->addTextBlob(blob);
   *     this->addScalar(x);
   *     this->addScalar(y);
   *
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint) {
   *     // op + paint index + slug id
   *     size_t size = 3 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_SLUG, &size);
   *
   *     this->addPaint(paint);
   *     this->addSlug(slug);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement onDrawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                                   const SkPoint texCoords[4], SkBlendMode bmode,
   *                                   const SkPaint& paint) {
   *     // op + paint index + patch 12 control points + flag + patch 4 colors + 4 texture coordinates
   *     size_t size = 2 * kUInt32Size + SkPatchUtils::kNumCtrlPts * sizeof(SkPoint) + kUInt32Size;
   *     uint32_t flag = 0;
   *     if (colors) {
   *         flag |= DRAW_VERTICES_HAS_COLORS;
   *         size += SkPatchUtils::kNumCorners * sizeof(SkColor);
   *     }
   *     if (texCoords) {
   *         flag |= DRAW_VERTICES_HAS_TEXS;
   *         size += SkPatchUtils::kNumCorners * sizeof(SkPoint);
   *     }
   *     if (SkBlendMode::kModulate != bmode) {
   *         flag |= DRAW_VERTICES_HAS_XFER;
   *         size += kUInt32Size;
   *     }
   *
   *     size_t initialOffset = this->addDraw(DRAW_PATCH, &size);
   *     this->addPaint(paint);
   *     this->addPatch(cubics);
   *     this->addInt(flag);
   *
   *     // write optional parameters
   *     if (colors) {
   *         fWriter.write(colors, SkPatchUtils::kNumCorners * sizeof(SkColor));
   *     }
   *     if (texCoords) {
   *         fWriter.write(texCoords, SkPatchUtils::kNumCorners * sizeof(SkPoint));
   *     }
   *     if (flag & DRAW_VERTICES_HAS_XFER) {
   *         this->addInt((int)bmode);
   *     }
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawPaint(const SkPaint& paint) {
   *     // op + paint index
   *     size_t size = 2 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_PAINT, &size);
   *     this->addPaint(paint);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawBehind(const SkPaint& paint) {
   *     // logically the same as drawPaint, but with a diff enum
   *     // op + paint index
   *     size_t size = 2 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_BEHIND_PAINT, &size);
   *     this->addPaint(paint);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawPoints(PointMode mode, size_t count, const SkPoint pts[],
   *                                    const SkPaint& paint) {
   *     // op + paint index + mode + count + point data
   *     size_t size = 4 * kUInt32Size + count * sizeof(SkPoint);
   *     size_t initialOffset = this->addDraw(DRAW_POINTS, &size);
   *     this->addPaint(paint);
   *
   *     this->addInt(mode);
   *     this->addInt(SkToInt(count));
   *     fWriter.writeMul4(pts, count * sizeof(SkPoint));
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawRect(const SkRect& rect, const SkPaint& paint) {
   *     // op + paint index + rect
   *     size_t size = 2 * kUInt32Size + sizeof(rect);
   *     size_t initialOffset = this->addDraw(DRAW_RECT, &size);
   *     this->addPaint(paint);
   *     this->addRect(rect);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     // op + paint index + region
   *     size_t regionBytes = region.writeToMemory(nullptr);
   *     size_t size = 2 * kUInt32Size + regionBytes;
   *     size_t initialOffset = this->addDraw(DRAW_REGION, &size);
   *     this->addPaint(paint);
   *     fWriter.writeRegion(region);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawOval(const SkRect& oval, const SkPaint& paint) {
   *     // op + paint index + rect
   *     size_t size = 2 * kUInt32Size + sizeof(oval);
   *     size_t initialOffset = this->addDraw(DRAW_OVAL, &size);
   *     this->addPaint(paint);
   *     this->addRect(oval);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle,
   *                                 bool useCenter, const SkPaint& paint) {
   *     // op + paint index + rect + start + sweep + bool (as int)
   *     size_t size = 2 * kUInt32Size + sizeof(oval) + sizeof(startAngle) + sizeof(sweepAngle) +
   *                   sizeof(int);
   *     size_t initialOffset = this->addDraw(DRAW_ARC, &size);
   *     this->addPaint(paint);
   *     this->addRect(oval);
   *     this->addScalar(startAngle);
   *     this->addScalar(sweepAngle);
   *     this->addInt(useCenter);
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     // op + paint index + rrect
   *     size_t size = 2 * kUInt32Size + SkRRect::kSizeInMemory;
   *     size_t initialOffset = this->addDraw(DRAW_RRECT, &size);
   *     this->addPaint(paint);
   *     this->addRRect(rrect);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     // op + paint index + path index
   *     size_t size = 3 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_PATH, &size);
   *     this->addPaint(paint);
   *     this->addPath(path);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawImage2(const SkImage* image, SkScalar x, SkScalar y,
   *                                    const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     // op + paint_index + image_index + x + y
   *     size_t size = 3 * kUInt32Size + 2 * sizeof(SkScalar) + SkSamplingPriv::FlatSize(sampling);
   *     size_t initialOffset = this->addDraw(DRAW_IMAGE2, &size);
   *     this->addPaintPtr(paint);
   *     this->addImage(image);
   *     this->addScalar(x);
   *     this->addScalar(y);
   *     this->addSampling(sampling);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawImage2(
    image: SkImage?,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawImageRect2(const SkImage* image, const SkRect& src, const SkRect& dst,
   *                                        const SkSamplingOptions& sampling, const SkPaint* paint,
   *                                        SrcRectConstraint constraint) {
   *     // id + paint_index + image_index + constraint
   *     size_t size = 3 * kUInt32Size + 2 * sizeof(dst) + SkSamplingPriv::FlatSize(sampling) +
   *                   kUInt32Size;
   *
   *     size_t initialOffset = this->addDraw(DRAW_IMAGE_RECT2, &size);
   *     this->addPaintPtr(paint);
   *     this->addImage(image);
   *     this->addRect(src);
   *     this->addRect(dst);
   *     this->addSampling(sampling);
   *     this->addInt(constraint);
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawImageLattice2(const SkImage* image, const Lattice& lattice,
   *                                           const SkRect& dst, SkFilterMode filter,
   *                                           const SkPaint* paint) {
   *     size_t latticeSize = SkCanvasPriv::WriteLattice(nullptr, lattice);
   *     // op + paint index + image index + lattice + dst rect
   *     size_t size = 3 * kUInt32Size + latticeSize + sizeof(dst) + sizeof(uint32_t); // filter
   *     size_t initialOffset = this->addDraw(DRAW_IMAGE_LATTICE2, &size);
   *     this->addPaintPtr(paint);
   *     this->addImage(image);
   *     (void)SkCanvasPriv::WriteLattice(fWriter.reservePad(latticeSize), lattice);
   *     this->addRect(dst);
   *     this->addInt(static_cast<uint32_t>(filter));
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawAtlas2(const SkImage* atlas, const SkRSXform xform[], const SkRect tex[],
   *                                    const SkColor colors[], int count, SkBlendMode mode,
   *                                    const SkSamplingOptions& sampling, const SkRect* cull,
   *                                    const SkPaint* paint) {
   *     // [op + paint-index + atlas-index + flags + count] + [xform] + [tex] + [*colors + mode] + cull
   *     size_t size = 5 * kUInt32Size + count * sizeof(SkRSXform) + count * sizeof(SkRect);
   *     size += SkSamplingPriv::FlatSize(sampling);
   *     uint32_t flags = 0;
   *     if (colors) {
   *         flags |= DRAW_ATLAS_HAS_COLORS;
   *         size += count * sizeof(SkColor);
   *         size += sizeof(uint32_t);   // xfermode::mode
   *     }
   *     if (cull) {
   *         flags |= DRAW_ATLAS_HAS_CULL;
   *         size += sizeof(SkRect);
   *     }
   *     flags |= DRAW_ATLAS_HAS_SAMPLING;
   *
   *     size_t initialOffset = this->addDraw(DRAW_ATLAS, &size);
   *     this->addPaintPtr(paint);
   *     this->addImage(atlas);
   *     this->addInt(flags);
   *     this->addInt(count);
   *     fWriter.write(xform, count * sizeof(SkRSXform));
   *     fWriter.write(tex, count * sizeof(SkRect));
   *
   *     // write optional parameters
   *     if (colors) {
   *         fWriter.write(colors, count * sizeof(SkColor));
   *         this->addInt((int)mode);
   *     }
   *     if (cull) {
   *         fWriter.write(cull, sizeof(SkRect));
   *     }
   *     this->addSampling(sampling);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawAtlas2(
    atlas: SkImage?,
    xform: Array<SkRSXform>,
    tex: Array<SkRect>,
    colors: Array<SkColor>,
    count: Int,
    mode: SkBlendMode,
    sampling: SkSamplingOptions,
    cull: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     // op + path index + zParams + lightPos + lightRadius + spot/ambient alphas + color + flags
   *     size_t size = 2 * kUInt32Size + 2 * sizeof(SkPoint3) + 1 * sizeof(SkScalar) + 3 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_SHADOW_REC, &size);
   *
   *     this->addPath(path);
   *
   *     fWriter.writePoint3(rec.fZPlaneParams);
   *     fWriter.writePoint3(rec.fLightPos);
   *     fWriter.writeScalar(rec.fLightRadius);
   *     fWriter.write32(rec.fAmbientColor);
   *     fWriter.write32(rec.fSpotColor);
   *     fWriter.write32(rec.fFlags);
   *
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawVerticesObject(const SkVertices* vertices,
   *                                            SkBlendMode mode, const SkPaint& paint) {
   *     // op + paint index + vertices index + zero_bones + mode
   *     size_t size = 5 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(DRAW_VERTICES_OBJECT, &size);
   *
   *     this->addPaint(paint);
   *     this->addVertices(vertices);
   *     this->addInt(0);    // legacy bone count
   *     this->addInt(static_cast<uint32_t>(mode));
   *
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawVerticesObject(
    vertices: SkVertices?,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->recordClipRect(rect, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onClipRect(rect, op, edgeStyle);
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
   * void SkPictureRecord::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     this->recordClipRRect(rrect, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onClipRRect(rrect, op, edgeStyle);
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
   * void SkPictureRecord::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     int pathID = this->addPathToHeap(path);
   *     this->recordClipPath(pathID, op, kSoft_ClipEdgeStyle == edgeStyle);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onClipPath(path, op, edgeStyle);
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
   * void SkPictureRecord::onClipShader(sk_sp<SkShader> cs, SkClipOp op) {
   *     // Overkill to store a whole paint, but we don't have an existing structure to just store
   *     // shaders. If size becomes an issue in the future, we can optimize this.
   *     SkPaint paint;
   *     paint.setShader(cs);
   *
   *     // op + paint index + clipop
   *     size_t size = 3 * kUInt32Size;
   *     size_t initialOffset = this->addDraw(CLIP_SHADER_IN_PAINT, &size);
   *     this->addPaint(paint);
   *     this->addInt((int)op);
   *     this->validate(initialOffset, size);
   *
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onClipShader(std::move(cs), op);
   * }
   * ```
   */
  protected override fun onClipShader(cs: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onClipRegion(const SkRegion& region, SkClipOp op) {
   *     this->recordClipRegion(region, op);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onClipRegion(region, op);
   * }
   * ```
   */
  protected override fun onClipRegion(region: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onResetClip() {
   *     if (!fRestoreOffsetStack.empty()) {
   *         // Run back through any previous clip ops, and mark their offset to
   *         // be 0, disabling their ability to trigger a jump-to-restore, otherwise
   *         // they could hide this expansion of the clip.
   *         this->fillRestoreOffsetPlaceholdersForCurrentStackLevel(0);
   *     }
   *     size_t size = sizeof(kUInt32Size);
   *     size_t initialOffset = this->addDraw(RESET_CLIP, &size);
   *     this->validate(initialOffset, size);
   *     this->SkCanvasVirtualEnforcer<SkCanvas>::onResetClip();
   * }
   * ```
   */
  protected override fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
   *                                     const SkPaint* paint) {
   *     // op + picture index
   *     size_t size = 2 * kUInt32Size;
   *     size_t initialOffset;
   *
   *     if (nullptr == matrix && nullptr == paint) {
   *         initialOffset = this->addDraw(DRAW_PICTURE, &size);
   *         this->addPicture(picture);
   *     } else {
   *         const SkMatrix& m = matrix ? *matrix : SkMatrix::I();
   *         size += SkMatrixPriv::WriteToMemory(m, nullptr) + kUInt32Size;    // matrix + paint
   *         initialOffset = this->addDraw(DRAW_PICTURE_MATRIX_PAINT, &size);
   *         this->addPaintPtr(paint);
   *         this->addMatrix(m);
   *         this->addPicture(picture);
   *     }
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) {
   *     // op + drawable index
   *     size_t size = 2 * kUInt32Size;
   *     size_t initialOffset;
   *
   *     if (nullptr == matrix) {
   *         initialOffset = this->addDraw(DRAW_DRAWABLE, &size);
   *         this->addDrawable(drawable);
   *     } else {
   *         size += SkMatrixPriv::WriteToMemory(*matrix, nullptr);    // matrix
   *         initialOffset = this->addDraw(DRAW_DRAWABLE_MATRIX, &size);
   *         this->addMatrix(*matrix);
   *         this->addDrawable(drawable);
   *     }
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) {
   *     size_t keyLen = SkWriter32::WriteStringSize(key);
   *     size_t valueLen = SkWriter32::WriteDataSize(value);
   *     size_t size = 4 + sizeof(SkRect) + keyLen + valueLen;
   *
   *     size_t initialOffset = this->addDraw(DRAW_ANNOTATION, &size);
   *     this->addRect(rect);
   *     fWriter.writeString(key);
   *     fWriter.writeData(value);
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
   *                                        SkCanvas::QuadAAFlags aa, const SkColor4f& color,
   *                                        SkBlendMode mode) {
   *
   *     // op + rect + aa flags + color + mode + hasClip(as int) + clipCount*points
   *     size_t size = 4 * kUInt32Size + sizeof(SkColor4f) + sizeof(rect) +
   *             (clip ? 4 : 0) * sizeof(SkPoint);
   *     size_t initialOffset = this->addDraw(DRAW_EDGEAA_QUAD, &size);
   *     this->addRect(rect);
   *     this->addInt((int) aa);
   *     fWriter.write(&color, sizeof(SkColor4f));
   *     this->addInt((int) mode);
   *     this->addInt(clip != nullptr);
   *     if (clip) {
   *         this->addPoints(clip, 4);
   *     }
   *     this->validate(initialOffset, size);
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
   * void SkPictureRecord::onDrawEdgeAAImageSet2(const SkCanvas::ImageSetEntry set[], int count,
   *                                             const SkPoint dstClips[],
   *                                             const SkMatrix preViewMatrices[],
   *                                             const SkSamplingOptions& sampling,
   *                                             const SkPaint* paint,
   *                                             SkCanvas::SrcRectConstraint constraint) {
   *     static constexpr size_t kMatrixSize = 9 * sizeof(SkScalar); // *not* sizeof(SkMatrix)
   *     // op + count + paint + constraint + (image index, src rect, dst rect, alpha, aa flags,
   *     // hasClip(int), matrixIndex) * cnt + totalClipCount + dstClips + totalMatrixCount + matrices
   *     int totalDstClipCount, totalMatrixCount;
   *     SkCanvasPriv::GetDstClipAndMatrixCounts(set, count, &totalDstClipCount, &totalMatrixCount);
   *
   *     size_t size = 6 * kUInt32Size + sizeof(SkPoint) * totalDstClipCount +
   *                   kMatrixSize * totalMatrixCount +
   *                   (4 * kUInt32Size + 2 * sizeof(SkRect) + sizeof(SkScalar)) * count +
   *                   SkSamplingPriv::FlatSize(sampling);
   *     size_t initialOffset = this->addDraw(DRAW_EDGEAA_IMAGE_SET2, &size);
   *     this->addInt(count);
   *     this->addPaintPtr(paint);
   *     this->addSampling(sampling);
   *     this->addInt((int) constraint);
   *     for (int i = 0; i < count; ++i) {
   *         this->addImage(set[i].fImage.get());
   *         this->addRect(set[i].fSrcRect);
   *         this->addRect(set[i].fDstRect);
   *         this->addInt(set[i].fMatrixIndex);
   *         this->addScalar(set[i].fAlpha);
   *         this->addInt((int)set[i].fAAFlags);
   *         this->addInt(set[i].fHasClip);
   *     }
   *     this->addInt(totalDstClipCount);
   *     this->addPoints(dstClips, totalDstClipCount);
   *     this->addInt(totalMatrixCount);
   *     for (int i = 0; i < totalMatrixCount; ++i) {
   *         this->addMatrix(preViewMatrices[i]);
   *     }
   *     this->validate(initialOffset, size);
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
   * int SkPictureRecord::addPathToHeap(const SkPath& path) {
   *     if (int* n = fPaths.find(path)) {
   *         return *n;
   *     }
   *     int n = fPaths.count() + 1;  // 0 is reserved for null / error.
   *     fPaths.set(path, n);
   *     return n;
   * }
   * ```
   */
  protected fun addPathToHeap(path: SkPath): Int {
    TODO("Implement addPathToHeap")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordConcat(const SkMatrix& matrix) {
   *     this->validate(fWriter.bytesWritten(), 0);
   *     // op + matrix
   *     size_t size = kUInt32Size + SkMatrixPriv::WriteToMemory(matrix, nullptr);
   *     size_t initialOffset = this->addDraw(CONCAT, &size);
   *     this->addMatrix(matrix);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordConcat(matrix: SkMatrix) {
    TODO("Implement recordConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordTranslate(const SkMatrix& m) {
   *     SkASSERT(SkMatrix::kTranslate_Mask == m.getType());
   *
   *     // op + dx + dy
   *     size_t size = 1 * kUInt32Size + 2 * sizeof(SkScalar);
   *     size_t initialOffset = this->addDraw(TRANSLATE, &size);
   *     this->addScalar(m.getTranslateX());
   *     this->addScalar(m.getTranslateY());
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordTranslate(matrix: SkMatrix) {
    TODO("Implement recordTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordScale(const SkMatrix& m) {
   *     SkASSERT(SkMatrix::kScale_Mask == m.getType());
   *
   *     // op + sx + sy
   *     size_t size = 1 * kUInt32Size + 2 * sizeof(SkScalar);
   *     size_t initialOffset = this->addDraw(SCALE, &size);
   *     this->addScalar(m.getScaleX());
   *     this->addScalar(m.getScaleY());
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordScale(matrix: SkMatrix) {
    TODO("Implement recordScale")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureRecord::recordClipRect(const SkRect& rect, SkClipOp op, bool doAA) {
   *     // id + rect + clip params
   *     size_t size = 1 * kUInt32Size + sizeof(rect) + 1 * kUInt32Size;
   *     // recordRestoreOffsetPlaceholder doesn't always write an offset
   *     if (!fRestoreOffsetStack.empty()) {
   *         // + restore offset
   *         size += kUInt32Size;
   *     }
   *     size_t initialOffset = this->addDraw(CLIP_RECT, &size);
   *     this->addRect(rect);
   *     this->addInt(ClipParams_pack(op, doAA));
   *     size_t offset = this->recordRestoreOffsetPlaceholder();
   *
   *     this->validate(initialOffset, size);
   *     return offset;
   * }
   * ```
   */
  protected fun recordClipRect(
    rect: SkRect,
    op: SkClipOp,
    doAA: Boolean,
  ): ULong {
    TODO("Implement recordClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureRecord::recordClipRRect(const SkRRect& rrect, SkClipOp op, bool doAA) {
   *     // op + rrect + clip params
   *     size_t size = 1 * kUInt32Size + SkRRect::kSizeInMemory + 1 * kUInt32Size;
   *     // recordRestoreOffsetPlaceholder doesn't always write an offset
   *     if (!fRestoreOffsetStack.empty()) {
   *         // + restore offset
   *         size += kUInt32Size;
   *     }
   *     size_t initialOffset = this->addDraw(CLIP_RRECT, &size);
   *     this->addRRect(rrect);
   *     this->addInt(ClipParams_pack(op, doAA));
   *     size_t offset = recordRestoreOffsetPlaceholder();
   *     this->validate(initialOffset, size);
   *     return offset;
   * }
   * ```
   */
  protected fun recordClipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    doAA: Boolean,
  ): ULong {
    TODO("Implement recordClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureRecord::recordClipPath(int pathID, SkClipOp op, bool doAA) {
   *     // op + path index + clip params
   *     size_t size = 3 * kUInt32Size;
   *     // recordRestoreOffsetPlaceholder doesn't always write an offset
   *     if (!fRestoreOffsetStack.empty()) {
   *         // + restore offset
   *         size += kUInt32Size;
   *     }
   *     size_t initialOffset = this->addDraw(CLIP_PATH, &size);
   *     this->addInt(pathID);
   *     this->addInt(ClipParams_pack(op, doAA));
   *     size_t offset = recordRestoreOffsetPlaceholder();
   *     this->validate(initialOffset, size);
   *     return offset;
   * }
   * ```
   */
  protected fun recordClipPath(
    pathID: Int,
    op: SkClipOp,
    doAA: Boolean,
  ): ULong {
    TODO("Implement recordClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureRecord::recordClipRegion(const SkRegion& region, SkClipOp op) {
   *     // op + clip params + region
   *     size_t size = 2 * kUInt32Size + region.writeToMemory(nullptr);
   *     // recordRestoreOffsetPlaceholder doesn't always write an offset
   *     if (!fRestoreOffsetStack.empty()) {
   *         // + restore offset
   *         size += kUInt32Size;
   *     }
   *     size_t initialOffset = this->addDraw(CLIP_REGION, &size);
   *     this->addRegion(region);
   *     this->addInt(ClipParams_pack(op, false));
   *     size_t offset = this->recordRestoreOffsetPlaceholder();
   *
   *     this->validate(initialOffset, size);
   *     return offset;
   * }
   * ```
   */
  protected fun recordClipRegion(region: SkRegion, op: SkClipOp): ULong {
    TODO("Implement recordClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordSave() {
   *     // op only
   *     size_t size = sizeof(kUInt32Size);
   *     size_t initialOffset = this->addDraw(SAVE, &size);
   *
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordSave() {
    TODO("Implement recordSave")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordSaveLayer(const SaveLayerRec& rec) {
   *     // op + flatflags
   *     size_t size = 2 * kUInt32Size;
   *     uint32_t flatFlags = 0;
   *     uint32_t filterCount = SkToU32(rec.fFilters.size());
   *
   *     if (rec.fBounds) {
   *         flatFlags |= SAVELAYERREC_HAS_BOUNDS;
   *         size += sizeof(*rec.fBounds);
   *     }
   *     if (rec.fPaint) {
   *         flatFlags |= SAVELAYERREC_HAS_PAINT;
   *         size += sizeof(uint32_t); // index
   *     }
   *     if (rec.fBackdrop) {
   *         flatFlags |= SAVELAYERREC_HAS_BACKDROP;
   *         size += sizeof(uint32_t); // (paint) index
   *     }
   *     if (rec.fSaveLayerFlags) {
   *         flatFlags |= SAVELAYERREC_HAS_FLAGS;
   *         size += sizeof(uint32_t);
   *     }
   *     if (SkCanvasPriv::GetBackdropScaleFactor(rec) != 1.f) {
   *         flatFlags |= SAVELAYERREC_HAS_BACKDROP_SCALE;
   *         size += sizeof(SkScalar);
   *     }
   *     if (filterCount) {
   *         flatFlags |= SAVELAYERREC_HAS_MULTIPLE_FILTERS;
   *         size += sizeof(uint32_t);  // count
   *         size += sizeof(uint32_t) * filterCount;  // N (paint) indices
   *     }
   *     if (rec.fBackdropTileMode != SkTileMode::kClamp) {
   *         flatFlags |= SAVELAYERREC_HAS_BACKDROP_TILEMODE;
   *         size += sizeof(uint32_t); // SkTileMode
   *     }
   *
   *     const size_t initialOffset = this->addDraw(SAVE_LAYER_SAVELAYERREC, &size);
   *     this->addInt(flatFlags);
   *     if (flatFlags & SAVELAYERREC_HAS_BOUNDS) {
   *         this->addRect(*rec.fBounds);
   *     }
   *     if (flatFlags & SAVELAYERREC_HAS_PAINT) {
   *         this->addPaintPtr(rec.fPaint);
   *     }
   *     if (flatFlags & SAVELAYERREC_HAS_BACKDROP) {
   *         // overkill, but we didn't already track single flattenables, so using a paint for that
   *         SkPaint paint;
   *         paint.setImageFilter(sk_ref_sp(const_cast<SkImageFilter*>(rec.fBackdrop)));
   *         this->addPaint(paint);
   *     }
   *     if (flatFlags & SAVELAYERREC_HAS_FLAGS) {
   *         this->addInt(rec.fSaveLayerFlags);
   *     }
   *     if (flatFlags & SAVELAYERREC_HAS_BACKDROP_SCALE) {
   *         this->addScalar(SkCanvasPriv::GetBackdropScaleFactor(rec));
   *     }
   *     if (flatFlags & SAVELAYERREC_HAS_MULTIPLE_FILTERS) {
   *         this->addInt(filterCount);
   *         for (uint32_t i = 0; i < filterCount; ++i) {
   *             // overkill to store a paint, oh well.
   *             SkPaint paint;
   *             paint.setImageFilter(rec.fFilters[i]);
   *             this->addPaint(paint);
   *         }
   *     }
   *     if (rec.fBackdropTileMode != SkTileMode::kClamp) {
   *         this->addInt((int) rec.fBackdropTileMode);
   *     }
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordSaveLayer(rec: SaveLayerRec) {
    TODO("Implement recordSaveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecord::recordRestore(bool fillInSkips) {
   *     if (fillInSkips) {
   *         this->fillRestoreOffsetPlaceholdersForCurrentStackLevel((uint32_t)fWriter.bytesWritten());
   *     }
   *     size_t size = 1 * kUInt32Size; // RESTORE consists solely of 1 op code
   *     size_t initialOffset = this->addDraw(RESTORE, &size);
   *     this->validate(initialOffset, size);
   * }
   * ```
   */
  protected fun recordRestore(fillInSkips: Boolean = true) {
    TODO("Implement recordRestore")
  }

  public open class PathHash {
    public operator fun invoke(p: SkPath): UInt {
      TODO("Implement invoke")
    }
  }
}
