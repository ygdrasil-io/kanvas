package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp
import org.skia.math.SkRect
import undefined.AbortCallback

/**
 * C++ original:
 * ```cpp
 * class SkBigPicture final : public SkPicture {
 * public:
 *     // An array of refcounted const SkPicture pointers.
 *     class SnapshotArray : ::SkNoncopyable {
 *     public:
 *         SnapshotArray(const SkPicture* pics[], int count) : fPics(pics), fCount(count) {}
 *         ~SnapshotArray() { for (int i = 0; i < fCount; i++) { fPics[i]->unref(); } }
 *
 *         const SkPicture* const* begin() const { return fPics; }
 *         int count() const { return fCount; }
 *     private:
 *         skia_private::AutoTMalloc<const SkPicture*> fPics;
 *         int fCount;
 *     };
 *
 *     SkBigPicture(const SkRect& cull,
 *                  sk_sp<SkRecord>,
 *                  std::unique_ptr<SnapshotArray>,
 *                  sk_sp<SkBBoxHierarchy>,
 *                  size_t approxBytesUsedBySubPictures);
 *
 *
 * // SkPicture overrides
 *     void playback(SkCanvas*, AbortCallback*) const override;
 *     SkRect cullRect() const override;
 *     int approximateOpCount(bool nested) const override;
 *     size_t approximateBytesUsed() const override;
 *     const SkBigPicture* asSkBigPicture() const override { return this; }
 *
 * // Used by GrRecordReplaceDraw
 *     const SkBBoxHierarchy* bbh() const { return fBBH.get(); }
 *     const SkRecord*     record() const { return fRecord.get(); }
 *
 * private:
 *     int drawableCount() const;
 *     SkPicture const* const* drawablePicts() const;
 *
 *     const SkRect                         fCullRect;
 *     const size_t                         fApproxBytesUsedBySubPictures;
 *     sk_sp<const SkRecord>                fRecord;
 *     std::unique_ptr<const SnapshotArray> fDrawablePicts;
 *     sk_sp<const SkBBoxHierarchy>         fBBH;
 * }
 * ```
 */
public class SkBigPicture public constructor(
  cull: SkRect,
  record: SkSp<SkRecord>,
  drawablePicts: SnapshotArray?,
  bbh: SkSp<SkBBoxHierarchy>,
  approxBytesUsedBySubPictures: ULong,
) : SkPicture() {
  /**
   * C++ original:
   * ```cpp
   * const SkRect                         fCullRect
   * ```
   */
  private val fCullRect: SkRect = TODO("Initialize fCullRect")

  /**
   * C++ original:
   * ```cpp
   * const size_t                         fApproxBytesUsedBySubPictures
   * ```
   */
  private val fApproxBytesUsedBySubPictures: ULong =
      TODO("Initialize fApproxBytesUsedBySubPictures")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkRecord>                fRecord
   * ```
   */
  private val fRecord: SkSp<SkRecord> = TODO("Initialize fRecord")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<const SnapshotArray> fDrawablePicts
   * ```
   */
  private var fDrawablePicts: Int = TODO("Initialize fDrawablePicts")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkBBoxHierarchy>         fBBH
   * ```
   */
  private val fBBH: SkSp<SkBBoxHierarchy> = TODO("Initialize fBBH")

  /**
   * C++ original:
   * ```cpp
   * void SkBigPicture::playback(SkCanvas* canvas, AbortCallback* callback) const {
   *     SkASSERT(canvas);
   *
   *     // If the query contains the whole picture, don't bother with the BBH.
   *     const bool useBBH = !canvas->getLocalClipBounds().contains(this->cullRect());
   *
   *     SkRecordDraw(*fRecord,
   *                  canvas,
   *                  this->drawablePicts(),
   *                  nullptr,
   *                  this->drawableCount(),
   *                  useBBH ? fBBH.get() : nullptr,
   *                  callback);
   * }
   * ```
   */
  public override fun playback(canvas: SkCanvas?, callback: AbortCallback?) {
    TODO("Implement playback")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkBigPicture::cullRect()            const { return fCullRect; }
   * ```
   */
  public override fun cullRect(): SkRect {
    TODO("Implement cullRect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkBigPicture::approximateOpCount(bool nested) const {
   *     if (nested) {
   *         NestedApproxOpCounter visitor;
   *         for (int i = 0; i < fRecord->count(); i++) {
   *             fRecord->visit(i, visitor);
   *         }
   *         return visitor.fCount;
   *     } else {
   *         return fRecord->count();
   *     }
   * }
   * ```
   */
  public override fun approximateOpCount(nested: Boolean): Int {
    TODO("Implement approximateOpCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkBigPicture::approximateBytesUsed() const {
   *     size_t bytes = sizeof(*this) + fRecord->bytesUsed() + fApproxBytesUsedBySubPictures;
   *     if (fBBH) { bytes += fBBH->bytesUsed(); }
   *     return bytes;
   * }
   * ```
   */
  public override fun approximateBytesUsed(): ULong {
    TODO("Implement approximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBigPicture* asSkBigPicture() const override { return this; }
   * ```
   */
  public override fun asSkBigPicture(): SkBigPicture {
    TODO("Implement asSkBigPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBBoxHierarchy* bbh() const { return fBBH.get(); }
   * ```
   */
  private fun bbh(): SkBBoxHierarchy {
    TODO("Implement bbh")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRecord*     record() const { return fRecord.get(); }
   * ```
   */
  private fun record(): SkRecord {
    TODO("Implement record")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkBigPicture::drawableCount() const {
   *     return fDrawablePicts ? fDrawablePicts->count() : 0;
   * }
   * ```
   */
  private fun drawableCount(): Int {
    TODO("Implement drawableCount")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPicture const* const* SkBigPicture::drawablePicts() const {
   *     return fDrawablePicts ? fDrawablePicts->begin() : nullptr;
   * }
   * ```
   */
  private fun drawablePicts(): SkPicture? {
    TODO("Implement drawablePicts")
  }

  public open class SnapshotArray public constructor(
    pics: Int,
    count: Int,
  ) : SkNoncopyable() {
    private var fPics: Int = TODO("Initialize fPics")

    private var fCount: Int = TODO("Initialize fCount")

    public fun begin(): SkPicture? {
      TODO("Implement begin")
    }

    public fun count(): Int {
      TODO("Implement count")
    }
  }
}
