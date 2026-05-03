package org.skia.utils

import kotlin.Int
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.core.SkDocument
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkWStream
import org.skia.math.SkScalar
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * struct MultiPictureDocument final : public SkDocument {
 *     const SkSerialProcs fProcs;
 *     SkPictureRecorder fPictureRecorder;
 *     SkSize fCurrentPageSize;
 *     TArray<sk_sp<SkPicture>> fPages;
 *     TArray<SkSize> fSizes;
 *     std::function<void(const SkPicture*)> fOnEndPage;
 *     MultiPictureDocument(SkWStream* s,
 *                          const SkSerialProcs* procs,
 *                          std::function<void(const SkPicture*)> onEndPage)
 *             : SkDocument(s)
 *             , fProcs(procs ? *procs : SkSerialProcs())
 *             , fOnEndPage(std::move(onEndPage)) {}
 *
 *     ~MultiPictureDocument() override { this->close(); }
 *
 *     SkCanvas* onBeginPage(SkScalar w, SkScalar h) override {
 *         fCurrentPageSize.set(w, h);
 *         return fPictureRecorder.beginRecording(w, h);
 *     }
 *     void onEndPage() override {
 *         fSizes.push_back(fCurrentPageSize);
 *         sk_sp<SkPicture> lastPage = fPictureRecorder.finishRecordingAsPicture();
 *         fPages.push_back(lastPage);
 *         if (fOnEndPage) {
 *             fOnEndPage(lastPage.get());
 *         }
 *     }
 *     void onClose(SkWStream* wStream) override {
 *         SkASSERT(wStream);
 *         SkASSERT(wStream->bytesWritten() == 0);
 *         wStream->writeText(kMagic);
 *         wStream->write32(kVersion);
 *         wStream->write32(SkToU32(fPages.size()));
 *         for (SkSize s : fSizes) {
 *             wStream->write(&s, sizeof(s));
 *         }
 *         SkSize bigsize = join(fSizes);
 *         SkCanvas* c = fPictureRecorder.beginRecording(SkRect::MakeSize(bigsize));
 *         for (const sk_sp<SkPicture>& page : fPages) {
 *             c->drawPicture(page);
 *             // Annotations must include some data.
 *             c->drawAnnotation(SkRect::MakeEmpty(), kEndPage, SkData::MakeWithCString("X"));
 *         }
 *         sk_sp<SkPicture> p = fPictureRecorder.finishRecordingAsPicture();
 *         p->serialize(wStream, &fProcs);
 *         fPages.clear();
 *         fSizes.clear();
 *     }
 *     void onAbort() override {
 *         fPages.clear();
 *         fSizes.clear();
 *     }
 * }
 * ```
 */
public class MultiPictureDocument public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkSerialProcs fProcs
   * ```
   */
  public val fProcs: SkSerialProcs,
  /**
   * C++ original:
   * ```cpp
   * SkPictureRecorder fPictureRecorder
   * ```
   */
  public var fPictureRecorder: SkPictureRecorder,
  /**
   * C++ original:
   * ```cpp
   * SkSize fCurrentPageSize
   * ```
   */
  public var fCurrentPageSize: SkSize,
  /**
   * C++ original:
   * ```cpp
   * TArray<sk_sp<SkPicture>> fPages
   * ```
   */
  public var fPages: Int,
  /**
   * C++ original:
   * ```cpp
   * TArray<SkSize> fSizes
   * ```
   */
  public var fSizes: Int,
  /**
   * C++ original:
   * ```cpp
   * std::function<void(const SkPicture*)> fOnEndPage
   * ```
   */
  public var fOnEndPage: Int,
) : SkDocument(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * MultiPictureDocument(SkWStream* s,
   *                          const SkSerialProcs* procs,
   *                          std::function<void(const SkPicture*)> onEndPage)
   *             : SkDocument(s)
   *             , fProcs(procs ? *procs : SkSerialProcs())
   *             , fOnEndPage(std::move(onEndPage)) {}
   * ```
   */
  public constructor(
    s: SkWStream?,
    procs: SkSerialProcs?,
    onEndPage: (SkPicture?) -> Unit,
  ) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* onBeginPage(SkScalar w, SkScalar h) override {
   *         fCurrentPageSize.set(w, h);
   *         return fPictureRecorder.beginRecording(w, h);
   *     }
   * ```
   */
  public override fun onBeginPage(w: SkScalar, h: SkScalar): SkCanvas {
    TODO("Implement onBeginPage")
  }

  /**
   * C++ original:
   * ```cpp
   * void onEndPage() override {
   *         fSizes.push_back(fCurrentPageSize);
   *         sk_sp<SkPicture> lastPage = fPictureRecorder.finishRecordingAsPicture();
   *         fPages.push_back(lastPage);
   *         if (fOnEndPage) {
   *             fOnEndPage(lastPage.get());
   *         }
   *     }
   * ```
   */
  public override fun onEndPage() {
    TODO("Implement onEndPage")
  }

  /**
   * C++ original:
   * ```cpp
   * void onClose(SkWStream* wStream) override {
   *         SkASSERT(wStream);
   *         SkASSERT(wStream->bytesWritten() == 0);
   *         wStream->writeText(kMagic);
   *         wStream->write32(kVersion);
   *         wStream->write32(SkToU32(fPages.size()));
   *         for (SkSize s : fSizes) {
   *             wStream->write(&s, sizeof(s));
   *         }
   *         SkSize bigsize = join(fSizes);
   *         SkCanvas* c = fPictureRecorder.beginRecording(SkRect::MakeSize(bigsize));
   *         for (const sk_sp<SkPicture>& page : fPages) {
   *             c->drawPicture(page);
   *             // Annotations must include some data.
   *             c->drawAnnotation(SkRect::MakeEmpty(), kEndPage, SkData::MakeWithCString("X"));
   *         }
   *         sk_sp<SkPicture> p = fPictureRecorder.finishRecordingAsPicture();
   *         p->serialize(wStream, &fProcs);
   *         fPages.clear();
   *         fSizes.clear();
   *     }
   * ```
   */
  public override fun onClose(wStream: SkWStream?) {
    TODO("Implement onClose")
  }

  /**
   * C++ original:
   * ```cpp
   * void onAbort() override {
   *         fPages.clear();
   *         fSizes.clear();
   *     }
   * ```
   */
  public override fun onAbort() {
    TODO("Implement onAbort")
  }
}
