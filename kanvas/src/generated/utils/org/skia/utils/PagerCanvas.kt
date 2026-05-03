package org.skia.utils

import kotlin.Int
import kotlin.String
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkData
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.pdf.SkDocumentPage

/**
 * C++ original:
 * ```cpp
 * struct PagerCanvas : public SkNWayCanvas {
 *     SkPictureRecorder fRecorder;
 *     SkDocumentPage* fDst;
 *     int fCount;
 *     int fIndex = 0;
 *     PagerCanvas(SkISize wh, SkDocumentPage* dst, int count)
 *             : SkNWayCanvas(wh.width(), wh.height()), fDst(dst), fCount(count) {
 *         this->nextCanvas();
 *     }
 *     void nextCanvas() {
 *         if (fIndex < fCount) {
 *             SkRect bounds = SkRect::MakeSize(fDst[fIndex].fSize);
 *             this->addCanvas(fRecorder.beginRecording(bounds));
 *         }
 *     }
 *     void onDrawAnnotation(const SkRect& r, const char* key, SkData* d) override {
 *         if (0 == strcmp(key, kEndPage)) {
 *             this->removeAll();
 *             if (fIndex < fCount) {
 *                 fDst[fIndex].fPicture = fRecorder.finishRecordingAsPicture();
 *                 ++fIndex;
 *             }
 *             this->nextCanvas();
 *         } else {
 *             this->SkNWayCanvas::onDrawAnnotation(r, key, d);
 *         }
 *     }
 * }
 * ```
 */
public open class PagerCanvas public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPictureRecorder fRecorder
   * ```
   */
  public var fRecorder: SkPictureRecorder,
  /**
   * C++ original:
   * ```cpp
   * SkDocumentPage* fDst
   * ```
   */
  public var fDst: SkDocumentPage?,
  /**
   * C++ original:
   * ```cpp
   * int fCount
   * ```
   */
  public var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fIndex = 0
   * ```
   */
  public var fIndex: Int,
) : SkNWayCanvas(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * PagerCanvas(SkISize wh, SkDocumentPage* dst, int count)
   *             : SkNWayCanvas(wh.width(), wh.height()), fDst(dst), fCount(count) {
   *         this->nextCanvas();
   *     }
   * ```
   */
  public constructor(
    wh: SkISize,
    dst: SkDocumentPage?,
    count: Int,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void nextCanvas() {
   *         if (fIndex < fCount) {
   *             SkRect bounds = SkRect::MakeSize(fDst[fIndex].fSize);
   *             this->addCanvas(fRecorder.beginRecording(bounds));
   *         }
   *     }
   * ```
   */
  public fun nextCanvas() {
    TODO("Implement nextCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawAnnotation(const SkRect& r, const char* key, SkData* d) override {
   *         if (0 == strcmp(key, kEndPage)) {
   *             this->removeAll();
   *             if (fIndex < fCount) {
   *                 fDst[fIndex].fPicture = fRecorder.finishRecordingAsPicture();
   *                 ++fIndex;
   *             }
   *             this->nextCanvas();
   *         } else {
   *             this->SkNWayCanvas::onDrawAnnotation(r, key, d);
   *         }
   *     }
   * ```
   */
  public override fun onDrawAnnotation(
    r: SkRect,
    key: String?,
    d: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }
}
