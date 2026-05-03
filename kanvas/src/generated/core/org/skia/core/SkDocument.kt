package org.skia.core

import org.skia.foundation.SkRefCnt
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDocument : public SkRefCnt {
 * public:
 *
 *     /**
 *      *  Begin a new page for the document, returning the canvas that will draw
 *      *  into the page. The document owns this canvas, and it will go out of
 *      *  scope when endPage() or close() is called, or the document is deleted.
 *      *  This will call endPage() if there is a currently active page.
 *      */
 *     SkCanvas* beginPage(SkScalar width, SkScalar height, const SkRect* content = nullptr);
 *
 *     /**
 *      *  Call endPage() when the content for the current page has been drawn
 *      *  (into the canvas returned by beginPage()). After this call the canvas
 *      *  returned by beginPage() will be out-of-scope.
 *      */
 *     void endPage();
 *
 *     /**
 *      *  Call close() when all pages have been drawn. This will close the file
 *      *  or stream holding the document's contents. After close() the document
 *      *  can no longer add new pages. Deleting the document will automatically
 *      *  call close() if need be.
 *      */
 *     void close();
 *
 *     /**
 *      *  Call abort() to stop producing the document immediately.
 *      *  The stream output must be ignored, and should not be trusted.
 *      */
 *     void abort();
 *
 * protected:
 *     explicit SkDocument(SkWStream*);
 *
 *     // note: subclasses must call close() in their destructor, as the base class
 *     // cannot do this for them.
 *     ~SkDocument() override;
 *
 *     virtual SkCanvas* onBeginPage(SkScalar width, SkScalar height) = 0;
 *     virtual void onEndPage() = 0;
 *     virtual void onClose(SkWStream*) = 0;
 *     virtual void onAbort() = 0;
 *
 *     // Allows subclasses to write to the stream as pages are written.
 *     SkWStream* getStream() { return fStream; }
 *
 *     enum State {
 *         kBetweenPages_State,
 *         kInPage_State,
 *         kClosed_State
 *     };
 *     State getState() const { return fState; }
 *
 * private:
 *     SkWStream* fStream;
 *     State      fState;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class SkDocument public constructor(
  stream: SkWStream?,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkWStream* fStream
   * ```
   */
  private var fStream: SkWStream? = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * State      fState
   * ```
   */
  private var fState: State = TODO("Initialize fState")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkDocument::beginPage(SkScalar width, SkScalar height,
   *                                 const SkRect* content) {
   *     if (width <= 0 || height <= 0 || kClosed_State == fState) {
   *         return nullptr;
   *     }
   *     if (kInPage_State == fState) {
   *         this->endPage();
   *     }
   *     SkASSERT(kBetweenPages_State == fState);
   *     fState = kInPage_State;
   *     return trim(this->onBeginPage(width, height), width, height, content);
   * }
   * ```
   */
  public fun beginPage(
    width: SkScalar,
    height: SkScalar,
    content: SkRect? = null,
  ): SkCanvas {
    TODO("Implement beginPage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDocument::endPage() {
   *     if (kInPage_State == fState) {
   *         fState = kBetweenPages_State;
   *         this->onEndPage();
   *     }
   * }
   * ```
   */
  public fun endPage() {
    TODO("Implement endPage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDocument::close() {
   *     for (;;) {
   *         switch (fState) {
   *             case kBetweenPages_State: {
   *                 fState = kClosed_State;
   *                 this->onClose(fStream);
   *                 // we don't own the stream, but we mark it nullptr since we can
   *                 // no longer write to it.
   *                 fStream = nullptr;
   *                 return;
   *             }
   *             case kInPage_State:
   *                 this->endPage();
   *                 break;
   *             case kClosed_State:
   *                 return;
   *         }
   *     }
   * }
   * ```
   */
  public fun close() {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDocument::abort() {
   *     this->onAbort();
   *
   *     fState = kClosed_State;
   *     // we don't own the stream, but we mark it nullptr since we can
   *     // no longer write to it.
   *     fStream = nullptr;
   * }
   * ```
   */
  public fun abort() {
    TODO("Implement abort")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkCanvas* onBeginPage(SkScalar width, SkScalar height) = 0
   * ```
   */
  protected abstract fun onBeginPage(width: SkScalar, height: SkScalar): SkCanvas

  /**
   * C++ original:
   * ```cpp
   * virtual void onEndPage() = 0
   * ```
   */
  protected abstract fun onEndPage()

  /**
   * C++ original:
   * ```cpp
   * virtual void onClose(SkWStream*) = 0
   * ```
   */
  protected abstract fun onClose(param0: SkWStream?)

  /**
   * C++ original:
   * ```cpp
   * virtual void onAbort() = 0
   * ```
   */
  protected abstract fun onAbort()

  /**
   * C++ original:
   * ```cpp
   * SkWStream* getStream() { return fStream; }
   * ```
   */
  protected fun getStream(): SkWStream {
    TODO("Implement getStream")
  }

  /**
   * C++ original:
   * ```cpp
   * State getState() const { return fState; }
   * ```
   */
  protected fun getState(): State {
    TODO("Implement getState")
  }

  public enum class State {
    kBetweenPages_State,
    kInPage_State,
    kClosed_State,
  }
}
