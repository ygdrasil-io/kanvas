package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPictureRecorder {
 * public:
 *     SkPictureRecorder();
 *     ~SkPictureRecorder();
 *
 *     /** Returns the canvas that records the drawing commands.
 *         @param bounds the cull rect used when recording this picture. Any drawing the falls outside
 *                       of this rect is undefined, and may be drawn or it may not.
 *         @param bbh         optional acceleration structure
 *         @param recordFlags optional flags that control recording.
 *         @return the canvas.
 *     */
 *     SkCanvas* beginRecording(const SkRect& bounds, sk_sp<SkBBoxHierarchy> bbh);
 *
 *     SkCanvas* beginRecording(const SkRect& bounds, SkBBHFactory* bbhFactory = nullptr);
 *
 *     SkCanvas* beginRecording(SkScalar width, SkScalar height,
 *                              SkBBHFactory* bbhFactory = nullptr) {
 *         return this->beginRecording(SkRect::MakeWH(width, height), bbhFactory);
 *     }
 *
 *     /** Returns the recording canvas if one is active, or NULL if recording is
 *         not active. This does not alter the refcnt on the canvas (if present).
 *     */
 *     SkCanvas* getRecordingCanvas();
 *
 *     /**
 *      *  Signal that the caller is done recording. This invalidates the canvas returned by
 *      *  beginRecording/getRecordingCanvas. Ownership of the object is passed to the caller, who
 *      *  must call unref() when they are done using it.
 *      *
 *      *  The returned picture is immutable. If during recording drawables were added to the canvas,
 *      *  these will have been "drawn" into a recording canvas, so that this resulting picture will
 *      *  reflect their current state, but will not contain a live reference to the drawables
 *      *  themselves.
 *      */
 *     sk_sp<SkPicture> finishRecordingAsPicture();
 *
 *     /**
 *      *  Signal that the caller is done recording, and update the cull rect to use for bounding
 *      *  box hierarchy (BBH) generation. The behavior is the same as calling
 *      *  finishRecordingAsPicture(), except that this method updates the cull rect initially passed
 *      *  into beginRecording.
 *      *  @param cullRect the new culling rectangle to use as the overall bound for BBH generation
 *      *                  and subsequent culling operations.
 *      *  @return the picture containing the recorded content.
 *      */
 *     sk_sp<SkPicture> finishRecordingAsPictureWithCull(const SkRect& cullRect);
 *
 *     /**
 *      *  Signal that the caller is done recording. This invalidates the canvas returned by
 *      *  beginRecording/getRecordingCanvas. Ownership of the object is passed to the caller, who
 *      *  must call unref() when they are done using it.
 *      *
 *      *  Unlike finishRecordingAsPicture(), which returns an immutable picture, the returned drawable
 *      *  may contain live references to other drawables (if they were added to the recording canvas)
 *      *  and therefore this drawable will reflect the current state of those nested drawables anytime
 *      *  it is drawn or a new picture is snapped from it (by calling drawable->makePictureSnapshot()).
 *      */
 *     sk_sp<SkDrawable> finishRecordingAsDrawable();
 *
 * private:
 *     void reset();
 *
 *     /** Replay the current (partially recorded) operation stream into
 *         canvas. This call doesn't close the current recording.
 *     */
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     friend class android::Picture;
 * #endif
 *     friend class SkPictureRecorderReplayTester; // for unit testing
 *     void partialReplay(SkCanvas* canvas) const;
 *
 *     sk_sp<SkBBoxHierarchy> fBBH;
 *     std::unique_ptr<SkRecordCanvas> fRecorder;
 *     sk_sp<SkRecord> fRecord;
 *     SkRect fCullRect;
 *     bool fActivelyRecording;
 *
 *     SkPictureRecorder(SkPictureRecorder&&) = delete;
 *     SkPictureRecorder& operator=(SkPictureRecorder&&) = delete;
 * }
 * ```
 */
public data class SkPictureRecorder public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBBoxHierarchy> fBBH
   * ```
   */
  private var fBBH: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkRecordCanvas> fRecorder
   * ```
   */
  private var fRecorder: SkRecordCanvas?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRecord> fRecord
   * ```
   */
  private var fRecord: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRect fCullRect
   * ```
   */
  private var fCullRect: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fActivelyRecording
   * ```
   */
  private var fActivelyRecording: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* beginRecording(const SkRect& bounds, sk_sp<SkBBoxHierarchy> bbh)
   * ```
   */
  public fun beginRecording(bounds: SkRect, bbh: SkSp<SkBBoxHierarchy>): SkCanvas {
    TODO("Implement beginRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* beginRecording(const SkRect& bounds, SkBBHFactory* bbhFactory = nullptr)
   * ```
   */
  public fun beginRecording(bounds: SkRect, bbhFactory: SkBBHFactory? = TODO()): SkCanvas {
    TODO("Implement beginRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* beginRecording(SkScalar width, SkScalar height,
   *                              SkBBHFactory* bbhFactory = nullptr) {
   *         return this->beginRecording(SkRect::MakeWH(width, height), bbhFactory);
   *     }
   * ```
   */
  public fun beginRecording(
    width: SkScalar,
    height: SkScalar,
    bbhFactory: SkBBHFactory? = TODO(),
  ): SkCanvas {
    TODO("Implement beginRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkPictureRecorder::getRecordingCanvas() {
   *     return fActivelyRecording ? fRecorder.get() : nullptr;
   * }
   * ```
   */
  public fun getRecordingCanvas(): SkCanvas {
    TODO("Implement getRecordingCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkPictureRecorder::finishRecordingAsPicture() {
   *     fActivelyRecording = false;
   *     fRecorder->restoreToCount(1);  // If we were missing any restores, add them now.
   *
   *     if (fRecord->count() == 0) {
   *         return sk_make_sp<SkEmptyPicture>();
   *     }
   *
   *     // TODO: delay as much of this work until just before first playback?
   *     SkRecordOptimize(fRecord.get());
   *
   *     SkDrawableList* drawableList = fRecorder->getDrawableList();
   *     std::unique_ptr<SkBigPicture::SnapshotArray> pictList{
   *         drawableList ? drawableList->newDrawableSnapshot() : nullptr
   *     };
   *
   *     if (fBBH) {
   *         AutoTArray<SkRect> bounds(fRecord->count());
   *         AutoTMalloc<SkBBoxHierarchy::Metadata> meta(fRecord->count());
   *         SkRecordFillBounds(fCullRect, *fRecord, bounds.data(), meta);
   *
   *         fBBH->insert(bounds.data(), meta, fRecord->count());
   *
   *         // Now that we've calculated content bounds, we can update fCullRect, often trimming it.
   *         SkRect bbhBound = SkRect::MakeEmpty();
   *         for (int i = 0; i < fRecord->count(); i++) {
   *             bbhBound.join(bounds[i]);
   *         }
   *         SkASSERT((bbhBound.isEmpty() || fCullRect.contains(bbhBound))
   *               || (bbhBound.isEmpty() && fCullRect.isEmpty()));
   *         fCullRect = bbhBound;
   *     }
   *
   *     size_t subPictureBytes = fRecorder->approxBytesUsedBySubPictures();
   *     for (int i = 0; pictList && i < pictList->count(); i++) {
   *         subPictureBytes += pictList->begin()[i]->approximateBytesUsed();
   *     }
   *     return sk_make_sp<SkBigPicture>(fCullRect,
   *                                     std::move(fRecord),
   *                                     std::move(pictList),
   *                                     std::move(fBBH),
   *                                     subPictureBytes);
   * }
   * ```
   */
  public fun finishRecordingAsPicture(): Int {
    TODO("Implement finishRecordingAsPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkPictureRecorder::finishRecordingAsPictureWithCull(const SkRect& cullRect) {
   *     fCullRect = cullRect;
   *     return this->finishRecordingAsPicture();
   * }
   * ```
   */
  public fun finishRecordingAsPictureWithCull(cullRect: SkRect): Int {
    TODO("Implement finishRecordingAsPictureWithCull")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable> SkPictureRecorder::finishRecordingAsDrawable() {
   *     fActivelyRecording = false;
   *     fRecorder->restoreToCount(1);  // If we were missing any restores, add them now.
   *
   *     SkRecordOptimize(fRecord.get());
   *
   *     if (fBBH) {
   *         AutoTArray<SkRect> bounds(fRecord->count());
   *         AutoTMalloc<SkBBoxHierarchy::Metadata> meta(fRecord->count());
   *         SkRecordFillBounds(fCullRect, *fRecord, bounds.data(), meta);
   *         fBBH->insert(bounds.data(), meta, fRecord->count());
   *     }
   *
   *     sk_sp<SkDrawable> drawable =
   *          sk_make_sp<SkRecordedDrawable>(std::move(fRecord), std::move(fBBH),
   *                                         fRecorder->detachDrawableList(), fCullRect);
   *
   *     return drawable;
   * }
   * ```
   */
  public fun finishRecordingAsDrawable(): Int {
    TODO("Implement finishRecordingAsDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset()
   * ```
   */
  private fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureRecorder::partialReplay(SkCanvas* canvas) const {
   *     if (nullptr == canvas) {
   *         return;
   *     }
   *
   *     int drawableCount = 0;
   *     SkDrawable* const* drawables = nullptr;
   *     SkDrawableList* drawableList = fRecorder->getDrawableList();
   *     if (drawableList) {
   *         drawableCount = drawableList->count();
   *         drawables = drawableList->begin();
   *     }
   *     SkRecordDraw(*fRecord, canvas, nullptr, drawables, drawableCount, nullptr/*bbh*/, nullptr/*callback*/);
   * }
   * ```
   */
  private fun partialReplay(canvas: SkCanvas?) {
    TODO("Implement partialReplay")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPictureRecorder& operator=(SkPictureRecorder&&) = delete
   * ```
   */
  private fun assign(param0: SkPictureRecorder) {
    TODO("Implement assign")
  }
}
