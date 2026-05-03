package org.skia.core

import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkRecordedDrawable : public SkDrawable {
 * public:
 *     SkRecordedDrawable(sk_sp<SkRecord> record, sk_sp<SkBBoxHierarchy> bbh,
 *                        std::unique_ptr<SkDrawableList> drawableList, const SkRect& bounds)
 *         : fRecord(std::move(record))
 *         , fBBH(std::move(bbh))
 *         , fDrawableList(std::move(drawableList))
 *         , fBounds(bounds)
 *     {}
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 * protected:
 *     SkRect onGetBounds() override { return fBounds; }
 *     size_t onApproximateBytesUsed() override;
 *
 *     void onDraw(SkCanvas* canvas) override;
 *
 *     sk_sp<SkPicture> onMakePictureSnapshot() override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkRecordedDrawable)
 *
 *     sk_sp<SkRecord>                 fRecord;
 *     sk_sp<SkBBoxHierarchy>          fBBH;
 *     std::unique_ptr<SkDrawableList> fDrawableList;
 *     const SkRect                    fBounds;
 * }
 * ```
 */
public open class SkRecordedDrawable public constructor(
  record: SkSp<SkRecord>,
  bbh: SkSp<SkBBoxHierarchy>,
  drawableList: SkDrawableList?,
  bounds: SkRect,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRecord>                 fRecord
   * ```
   */
  private var fRecord: SkSp<SkRecord> = TODO("Initialize fRecord")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBBoxHierarchy>          fBBH
   * ```
   */
  private var fBBH: SkSp<SkBBoxHierarchy> = TODO("Initialize fBBH")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkDrawableList> fDrawableList
   * ```
   */
  private var fDrawableList: Int = TODO("Initialize fDrawableList")

  /**
   * C++ original:
   * ```cpp
   * const SkRect                    fBounds
   * ```
   */
  private val fBounds: SkRect = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * void SkRecordedDrawable::flatten(SkWriteBuffer& buffer) const {
   *     // Write the bounds.
   *     buffer.writeRect(fBounds);
   *
   *     // Create an SkPictureRecord to record the draw commands.
   *     SkPictInfo info;
   *     SkPictureRecord pictureRecord(SkISize::Make(fBounds.width(), fBounds.height()), 0);
   *
   *     // If the query contains the whole picture, don't bother with the bounding box hierarchy.
   *     SkBBoxHierarchy* bbh;
   *     if (pictureRecord.getLocalClipBounds().contains(fBounds)) {
   *         bbh = nullptr;
   *     } else {
   *         bbh = fBBH.get();
   *     }
   *
   *     // Record the draw commands.
   *     SkDrawable* const* drawables = fDrawableList ? fDrawableList->begin() : nullptr;
   *     int drawableCount            = fDrawableList ? fDrawableList->count() : 0;
   *     pictureRecord.beginRecording();
   *     SkRecordDraw(*fRecord, &pictureRecord, nullptr, drawables, drawableCount, bbh, nullptr);
   *     pictureRecord.endRecording();
   *
   *     // Flatten the recorded commands and drawables.
   *     SkPictureData pictureData(pictureRecord, info);
   *     pictureData.flatten(buffer);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onGetBounds() override { return fBounds; }
   * ```
   */
  protected override fun onGetBounds(): SkRect {
    TODO("Implement onGetBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRecordedDrawable::onApproximateBytesUsed() {
   *     size_t drawablesSize = 0;
   *     if (fDrawableList) {
   *         for (auto&& drawable : *fDrawableList) {
   *             drawablesSize += drawable->approximateBytesUsed();
   *         }
   *     }
   *     return sizeof(*this) +
   *            (fRecord ? fRecord->bytesUsed() : 0) +
   *            (fBBH ? fBBH->bytesUsed() : 0) +
   *            drawablesSize;
   * }
   * ```
   */
  protected override fun onApproximateBytesUsed(): ULong {
    TODO("Implement onApproximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRecordedDrawable::onDraw(SkCanvas* canvas) {
   *     SkDrawable* const* drawables = nullptr;
   *     int drawableCount = 0;
   *     if (fDrawableList) {
   *         drawables = fDrawableList->begin();
   *         drawableCount = fDrawableList->count();
   *     }
   *     SkRecordDraw(*fRecord, canvas, nullptr, drawables, drawableCount, fBBH.get(), nullptr);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkRecordedDrawable::onMakePictureSnapshot() {
   *     // TODO: should we plumb-down the BBHFactory and recordFlags from our host
   *     //       PictureRecorder?
   *     std::unique_ptr<SkBigPicture::SnapshotArray> pictList{
   *         fDrawableList ? fDrawableList->newDrawableSnapshot() : nullptr
   *     };
   *
   *     size_t subPictureBytes = 0;
   *     for (int i = 0; pictList && i < pictList->count(); i++) {
   *         subPictureBytes += pictList->begin()[i]->approximateBytesUsed();
   *     }
   *     return sk_make_sp<SkBigPicture>(fBounds, fRecord, std::move(pictList), fBBH, subPictureBytes);
   * }
   * ```
   */
  protected override fun onMakePictureSnapshot(): SkSp<SkPicture> {
    TODO("Implement onMakePictureSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkRecordedDrawable::CreateProc(SkReadBuffer& buffer) {
   *     // Read the bounds.
   *     SkRect bounds;
   *     buffer.readRect(&bounds);
   *
   *     // Unflatten into a SkPictureData.
   *     SkPictInfo info;
   *     info.setVersion(buffer.getVersion());
   *     info.fCullRect = bounds;
   *     std::unique_ptr<SkPictureData> pictureData(SkPictureData::CreateFromBuffer(buffer, info));
   *     if (!pictureData) {
   *         return nullptr;
   *     }
   *
   *     // Create a drawable.
   *     SkPicturePlayback playback(pictureData.get());
   *     SkPictureRecorder recorder;
   *     playback.draw(recorder.beginRecording(bounds), nullptr, &buffer);
   *     return recorder.finishRecordingAsDrawable();
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
