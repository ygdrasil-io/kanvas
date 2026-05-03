package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkSemaphore
import org.skia.foundation.SkRefCnt
import org.skia.gpu.Recording
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class Listener : public SkRefCnt {
 * public:
 *     Listener(int numSenders) : fNumActiveSenders(numSenders) {}
 *
 *     void addRecording(std::unique_ptr<Recording> recording) SK_EXCLUDES(fLock) {
 *         {
 *             SkAutoMutexExclusive lock(fLock);
 *             fRecordings.push_back(std::move(recording));
 *         }
 *
 *         fWorkAvailable.signal(1);
 *     }
 *
 *     void deregister() SK_EXCLUDES(fLock) {
 *         {
 *             SkAutoMutexExclusive lock(fLock);
 *             fNumActiveSenders--;
 *         }
 *
 *         fWorkAvailable.signal(1);
 *     }
 *
 *     void insertRecordings(Context* context) {
 *         do {
 *             fWorkAvailable.wait();
 *         } while (this->insertRecording(context));
 *     }
 *
 * private:
 *     // This entry point is run in a loop waiting on the 'fWorkAvailable' semaphore until there
 *     // are no senders remaining (at which point it returns false) c.f. 'insertRecordings'.
 *     bool insertRecording(Context* context) SK_EXCLUDES(fLock) {
 *         Recording* recording = nullptr;
 *         int numSendersLeft;
 *
 *         {
 *             SkAutoMutexExclusive lock(fLock);
 *
 *             numSendersLeft = fNumActiveSenders;
 *
 *             SkASSERT(fRecordings.size() >= fCurHandled);
 *             if (fRecordings.size() > fCurHandled) {
 *                 recording = fRecordings[fCurHandled++].get();
 *             }
 *         }
 *
 *         if (recording) {
 *             context->insertRecording({recording});
 *             return true;  // continue looping
 *         }
 *
 *         return SkToBool(numSendersLeft); // continue looping if there are still active senders
 *     }
 *
 *     SkMutex fLock;
 *     SkSemaphore fWorkAvailable;
 *
 *     skia_private::TArray<std::unique_ptr<Recording>> fRecordings SK_GUARDED_BY(fLock);
 *     int fCurHandled SK_GUARDED_BY(fLock) = 0;
 *     int fNumActiveSenders SK_GUARDED_BY(fLock);
 * }
 * ```
 */
public open class Listener public constructor(
  numSenders: Int,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkMutex fLock
   * ```
   */
  private var fLock: SkMutex = TODO("Initialize fLock")

  /**
   * C++ original:
   * ```cpp
   * SkSemaphore fWorkAvailable
   * ```
   */
  private var fWorkAvailable: SkSemaphore = TODO("Initialize fWorkAvailable")

  /**
   * C++ original:
   * ```cpp
   * int fCurHandled SK_GUARDED_BY(fLock) = 0
   * ```
   */
  private var fCurHandled: Int = TODO("Initialize fCurHandled")

  /**
   * C++ original:
   * ```cpp
   * int fNumActiveSenders
   * ```
   */
  private var fNumActiveSenders: Int = TODO("Initialize fNumActiveSenders")

  /**
   * C++ original:
   * ```cpp
   * void addRecording(std::unique_ptr<Recording> recording) SK_EXCLUDES(fLock) {
   *         {
   *             SkAutoMutexExclusive lock(fLock);
   *             fRecordings.push_back(std::move(recording));
   *         }
   *
   *         fWorkAvailable.signal(1);
   *     }
   * ```
   */
  public fun addRecording(recording: Recording?) {
    TODO("Implement addRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * void deregister() SK_EXCLUDES(fLock) {
   *         {
   *             SkAutoMutexExclusive lock(fLock);
   *             fNumActiveSenders--;
   *         }
   *
   *         fWorkAvailable.signal(1);
   *     }
   * ```
   */
  public fun deregister() {
    TODO("Implement deregister")
  }

  /**
   * C++ original:
   * ```cpp
   * void insertRecordings(Context* context) {
   *         do {
   *             fWorkAvailable.wait();
   *         } while (this->insertRecording(context));
   *     }
   * ```
   */
  public fun insertRecordings(context: Context?) {
    TODO("Implement insertRecordings")
  }

  /**
   * C++ original:
   * ```cpp
   * bool insertRecording(Context* context) SK_EXCLUDES(fLock) {
   *         Recording* recording = nullptr;
   *         int numSendersLeft;
   *
   *         {
   *             SkAutoMutexExclusive lock(fLock);
   *
   *             numSendersLeft = fNumActiveSenders;
   *
   *             SkASSERT(fRecordings.size() >= fCurHandled);
   *             if (fRecordings.size() > fCurHandled) {
   *                 recording = fRecordings[fCurHandled++].get();
   *             }
   *         }
   *
   *         if (recording) {
   *             context->insertRecording({recording});
   *             return true;  // continue looping
   *         }
   *
   *         return SkToBool(numSendersLeft); // continue looping if there are still active senders
   *     }
   * ```
   */
  private fun insertRecording(context: Context?): Boolean {
    TODO("Implement insertRecording")
  }
}
