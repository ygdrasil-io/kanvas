package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkCanvasPriv {
 * public:
 *     // The lattice has pointers directly into the readbuffer
 *     static bool ReadLattice(SkReadBuffer&, SkCanvas::Lattice*);
 *
 *     static void WriteLattice(SkWriteBuffer&, const SkCanvas::Lattice&);
 *
 *     // return the byte-size of the lattice, even if the buffer is null
 *     // storage must be 4-byte aligned
 *     static size_t WriteLattice(void* storage, const SkCanvas::Lattice&);
 *
 *     static int SaveBehind(SkCanvas* canvas, const SkRect* subset) {
 *         return canvas->only_axis_aligned_saveBehind(subset);
 *     }
 *     static void DrawBehind(SkCanvas* canvas, const SkPaint& paint) {
 *         canvas->drawClippedToSaveBehind(paint);
 *     }
 *
 *     // Exposed for testing on non-Android framework builds
 *     static void ResetClip(SkCanvas* canvas) {
 *         canvas->internal_private_resetClip();
 *     }
 *
 *     static SkDevice* TopDevice(const SkCanvas* canvas) {
 *         return canvas->topDevice();
 *     }
 *
 *     // The experimental_DrawEdgeAAImageSet API accepts separate dstClips and preViewMatrices arrays,
 *     // where entries refer into them, but no explicit size is provided. Given a set of entries,
 *     // computes the minimum length for these arrays that would provide index access errors.
 *     static void GetDstClipAndMatrixCounts(const SkCanvas::ImageSetEntry set[], int count,
 *                                           int* totalDstClipCount, int* totalMatrixCount);
 *
 *     static SkCanvas::SaveLayerRec ScaledBackdropLayer(const SkRect* bounds,
 *                                                       const SkPaint* paint,
 *                                                       const SkImageFilter* backdrop,
 *                                                       SkScalar backdropScale,
 *                                                       SkTileMode backdropTileMode,
 *                                                       SkCanvas::SaveLayerFlags saveLayerFlags,
 *                                                       SkCanvas::FilterSpan filters = {}) {
 *         return SkCanvas::SaveLayerRec(bounds, paint, backdrop, nullptr, backdropScale,
 *                                       backdropTileMode, saveLayerFlags, filters);
 *     }
 *
 *     static SkCanvas::SaveLayerRec ScaledBackdropLayer(const SkRect* bounds,
 *                                                       const SkPaint* paint,
 *                                                       const SkImageFilter* backdrop,
 *                                                       SkScalar backdropScale,
 *                                                       SkCanvas::SaveLayerFlags saveLayerFlags,
 *                                                       SkCanvas::FilterSpan filters = {}) {
 *         return ScaledBackdropLayer(bounds, paint, backdrop, backdropScale, SkTileMode::kClamp,
 *                                    saveLayerFlags, filters);
 *     }
 *
 *     static SkScalar GetBackdropScaleFactor(const SkCanvas::SaveLayerRec& rec) {
 *         return rec.fExperimentalBackdropScale;
 *     }
 *
 *     static void SetBackdropScaleFactor(SkCanvas::SaveLayerRec* rec, SkScalar scale) {
 *         rec->fExperimentalBackdropScale = scale;
 *     }
 *
 *     // Attempts to convert an image filter to its equivalent color filter, which if possible,
 *     // modifies the paint to compose the image filter's color filter into the paint's color filter
 *     // slot.
 *     // Returns true if the paint has been modified.
 *     // Requires the paint to have an image filter and the copy-on-write be initialized.
 *     static bool ImageToColorFilter(SkPaint*);
 * }
 * ```
 */
public open class SkCanvasPriv {
  /**
   * C++ original:
   * ```cpp
   * size_t SkCanvasPriv::WriteLattice(void* buffer, const SkCanvas::Lattice& lattice) {
   *     int flagCount = lattice.fRectTypes ? (lattice.fXCount + 1) * (lattice.fYCount + 1) : 0;
   *
   *     const size_t size = (1 + lattice.fXCount + 1 + lattice.fYCount + 1) * sizeof(int32_t) +
   *                         SkAlign4(flagCount * sizeof(SkCanvas::Lattice::RectType)) +
   *                         SkAlign4(flagCount * sizeof(SkColor)) +
   *                         sizeof(SkIRect);
   *
   *     if (buffer) {
   *         SkWriter32 writer(buffer, size);
   *         writer.write32(lattice.fXCount);
   *         writer.write(lattice.fXDivs, lattice.fXCount * sizeof(uint32_t));
   *         writer.write32(lattice.fYCount);
   *         writer.write(lattice.fYDivs, lattice.fYCount * sizeof(uint32_t));
   *         writer.write32(flagCount);
   *         writer.writePad(lattice.fRectTypes, flagCount * sizeof(uint8_t));
   *         writer.write(lattice.fColors, flagCount * sizeof(SkColor));
   *         SkASSERT(lattice.fBounds);
   *         writer.write(lattice.fBounds, sizeof(SkIRect));
   *         SkASSERT(writer.bytesWritten() == size);
   *     }
   *     return size;
   * }
   * ```
   */
  public fun writeLattice(buffer: Unit?, lattice: SkCanvas.Lattice): ULong {
    TODO("Implement writeLattice")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkCanvasPriv::ReadLattice(SkReadBuffer& buffer, SkCanvas::Lattice* lattice) {
     *     lattice->fXCount = buffer.readInt();
     *     lattice->fXDivs = buffer.skipT<int32_t>(lattice->fXCount);
     *     lattice->fYCount = buffer.readInt();
     *     lattice->fYDivs = buffer.skipT<int32_t>(lattice->fYCount);
     *     int flagCount = buffer.readInt();
     *     lattice->fRectTypes = nullptr;
     *     lattice->fColors = nullptr;
     *     if (flagCount) {
     *         lattice->fRectTypes = buffer.skipT<SkCanvas::Lattice::RectType>(flagCount);
     *         lattice->fColors = buffer.skipT<SkColor>(flagCount);
     *     }
     *     lattice->fBounds = buffer.skipT<SkIRect>();
     *     return buffer.isValid();
     * }
     * ```
     */
    public fun readLattice(buffer: SkReadBuffer, lattice: SkCanvas.Lattice?): Boolean {
      TODO("Implement readLattice")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkCanvasPriv::WriteLattice(SkWriteBuffer& buffer, const SkCanvas::Lattice& lattice) {
     *     const size_t size = WriteLattice(nullptr, lattice);
     *     SkAutoSMalloc<1024> storage(size);
     *     WriteLattice(storage.get(), lattice);
     *     buffer.writePad32(storage.get(), size);
     * }
     * ```
     */
    public fun writeLattice(buffer: SkWriteBuffer, lattice: SkCanvas.Lattice) {
      TODO("Implement writeLattice")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t WriteLattice(void* storage, const SkCanvas::Lattice&)
     * ```
     */
    public fun writeLattice(storage: Unit?, param1: SkCanvas.Lattice): Int {
      TODO("Implement writeLattice")
    }

    /**
     * C++ original:
     * ```cpp
     * static int SaveBehind(SkCanvas* canvas, const SkRect* subset) {
     *         return canvas->only_axis_aligned_saveBehind(subset);
     *     }
     * ```
     */
    public fun saveBehind(canvas: SkCanvas?, subset: SkRect?): Int {
      TODO("Implement saveBehind")
    }

    /**
     * C++ original:
     * ```cpp
     * static void DrawBehind(SkCanvas* canvas, const SkPaint& paint) {
     *         canvas->drawClippedToSaveBehind(paint);
     *     }
     * ```
     */
    public fun drawBehind(canvas: SkCanvas?, paint: SkPaint) {
      TODO("Implement drawBehind")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ResetClip(SkCanvas* canvas) {
     *         canvas->internal_private_resetClip();
     *     }
     * ```
     */
    public fun resetClip(canvas: SkCanvas?) {
      TODO("Implement resetClip")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDevice* TopDevice(const SkCanvas* canvas) {
     *         return canvas->topDevice();
     *     }
     * ```
     */
    public fun topDevice(canvas: SkCanvas?): SkDevice {
      TODO("Implement topDevice")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkCanvasPriv::GetDstClipAndMatrixCounts(const SkCanvas::ImageSetEntry set[], int count,
     *                                              int* totalDstClipCount, int* totalMatrixCount) {
     *     int dstClipCount = 0;
     *     int maxMatrixIndex = -1;
     *     for (int i = 0; i < count; ++i) {
     *         dstClipCount += 4 * set[i].fHasClip;
     *         if (set[i].fMatrixIndex > maxMatrixIndex) {
     *             maxMatrixIndex = set[i].fMatrixIndex;
     *         }
     *     }
     *
     *     *totalDstClipCount = dstClipCount;
     *     *totalMatrixCount = maxMatrixIndex + 1;
     * }
     * ```
     */
    public fun getDstClipAndMatrixCounts(
      `set`: Array<SkCanvas.ImageSetEntry>,
      count: Int,
      totalDstClipCount: Int?,
      totalMatrixCount: Int?,
    ) {
      TODO("Implement getDstClipAndMatrixCounts")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkCanvas::SaveLayerRec ScaledBackdropLayer(const SkRect* bounds,
     *                                                       const SkPaint* paint,
     *                                                       const SkImageFilter* backdrop,
     *                                                       SkScalar backdropScale,
     *                                                       SkTileMode backdropTileMode,
     *                                                       SkCanvas::SaveLayerFlags saveLayerFlags,
     *                                                       SkCanvas::FilterSpan filters = {}) {
     *         return SkCanvas::SaveLayerRec(bounds, paint, backdrop, nullptr, backdropScale,
     *                                       backdropTileMode, saveLayerFlags, filters);
     *     }
     * ```
     */
    public fun scaledBackdropLayer(
      param0: SkRect?,
      param1: SkPaint?,
      param2: SkImageFilter?,
      param3: SkScalar,
      param4: SkTileMode,
      param5: SkCanvas.SaveLayerFlagsSet,
      param6: SkCanvas.FilterSpan,
    ): SkCanvas.SaveLayerRec {
      TODO("Implement scaledBackdropLayer")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkCanvas::SaveLayerRec ScaledBackdropLayer(const SkRect* bounds,
     *                                                       const SkPaint* paint,
     *                                                       const SkImageFilter* backdrop,
     *                                                       SkScalar backdropScale,
     *                                                       SkCanvas::SaveLayerFlags saveLayerFlags,
     *                                                       SkCanvas::FilterSpan filters = {}) {
     *         return ScaledBackdropLayer(bounds, paint, backdrop, backdropScale, SkTileMode::kClamp,
     *                                    saveLayerFlags, filters);
     *     }
     * ```
     */
    public fun scaledBackdropLayer(
      param0: SkRect?,
      param1: SkPaint?,
      param2: SkImageFilter?,
      param3: SkScalar,
      param4: SkCanvas.SaveLayerFlagsSet,
      param5: SkCanvas.FilterSpan,
    ): SkCanvas.SaveLayerRec {
      TODO("Implement scaledBackdropLayer")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar GetBackdropScaleFactor(const SkCanvas::SaveLayerRec& rec) {
     *         return rec.fExperimentalBackdropScale;
     *     }
     * ```
     */
    public fun getBackdropScaleFactor(rec: SkCanvas.SaveLayerRec): SkScalar {
      TODO("Implement getBackdropScaleFactor")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetBackdropScaleFactor(SkCanvas::SaveLayerRec* rec, SkScalar scale) {
     *         rec->fExperimentalBackdropScale = scale;
     *     }
     * ```
     */
    public fun setBackdropScaleFactor(rec: SkCanvas.SaveLayerRec?, scale: SkScalar) {
      TODO("Implement setBackdropScaleFactor")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkCanvasPriv::ImageToColorFilter(SkPaint* paint) {
     *     SkASSERT(SkToBool(paint) && paint->getImageFilter());
     *
     *     // An image filter logically runs after any mask filter and the src-over blending against the
     *     // layer's transparent black initial content. Moving the image filter (as a color filter) into
     *     // the color filter slot causes it to run before the mask filter or blending.
     *     //
     *     // Src-over blending against transparent black is a no-op, so skipping the layer and drawing the
     *     // output of the color filter-image filter with the original blender is valid.
     *     //
     *     // If there's also a mask filter on the paint, it will operate on an alpha-only layer that's
     *     // then shaded with the paint's effects. Moving the CF-IF into the paint's color filter slot
     *     // will mean that the CF-IF operates on the output of the original CF *before* it's combined
     *     // with the coverage value. Under normal circumstances the CF-IF evaluates the color after
     *     // coverage has been multiplied into the alpha channel.
     *     //
     *     // Some color filters may behave the same, e.g. cf(color)*coverage == cf(color*coverage), but
     *     // that's hard to detect so we disable the optimization when both image filters and mask filters
     *     // are present.
     *     if (paint->getMaskFilter()) {
     *         return false;
     *     }
     *
     *     SkColorFilter* imgCFPtr;
     *     if (!paint->getImageFilter()->asAColorFilter(&imgCFPtr)) {
     *         return false;
     *     }
     *     sk_sp<SkColorFilter> imgCF(imgCFPtr);
     *
     *     SkColorFilter* paintCF = paint->getColorFilter();
     *     if (paintCF) {
     *         // The paint has both a colorfilter(paintCF) and an imagefilter-that-is-a-colorfilter(imgCF)
     *         // and we need to combine them into a single colorfilter.
     *         imgCF = imgCF->makeComposed(sk_ref_sp(paintCF));
     *     }
     *
     *     paint->setColorFilter(std::move(imgCF));
     *     paint->setImageFilter(nullptr);
     *     return true;
     * }
     * ```
     */
    public fun imageToColorFilter(paint: SkPaint?): Boolean {
      TODO("Implement imageToColorFilter")
    }
  }
}
