package org.skia.gpu

import `typename TClientMappedBufferManagerBufferFinishedMessageBus`.Inbox
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename IDType>
 * class TClientMappedBufferManager {
 * public:
 *     /**
 *      * The message type that internal users of this should post to unmap the buffer.
 *      * Set fInboxID to inboxID(). fBuffer must have been previously passed to insert().
 *      */
 *     struct BufferFinishedMessage {
 *         BufferFinishedMessage(sk_sp<T> buffer,
 *                               IDType intendedRecipient)
 *                 : fBuffer(std::move(buffer)), fIntendedRecipient(intendedRecipient) {}
 *         BufferFinishedMessage(BufferFinishedMessage&& other) {
 *             fBuffer = std::move(other.fBuffer);
 *             fIntendedRecipient = other.fIntendedRecipient;
 *             other.fIntendedRecipient.makeInvalid();
 *         }
 *         sk_sp<T> fBuffer;
 *         IDType   fIntendedRecipient;
 *     };
 *     using BufferFinishedMessageBus = SkMessageBus<BufferFinishedMessage,
 *                                                   IDType,
 *                                                   false>;
 *
 *     TClientMappedBufferManager(IDType ownerID)
 *             : fFinishedBufferInbox(ownerID) {}
 *     TClientMappedBufferManager(const TClientMappedBufferManager&) = delete;
 *     TClientMappedBufferManager(TClientMappedBufferManager&&) = delete;
 *
 *     ~TClientMappedBufferManager() {
 *         this->process();
 *         if (!fAbandoned) {
 *             // If we're going down before we got the messages we go ahead and unmap all the buffers.
 *             // It's up to the client to ensure that they aren't being accessed on another thread
 *             // while this is happening (or afterwards on any thread).
 *             for (auto& b : fClientHeldBuffers) {
 *                 b->unmap();
 *             }
 *         }
 *     }
 *
 *     TClientMappedBufferManager& operator=(const TClientMappedBufferManager&) = delete;
 *     TClientMappedBufferManager& operator=(TClientMappedBufferManager&&) = delete;
 *
 *     /** Initialize BufferFinishedMessage::fIntendedRecipient to this value. It is the
 *      *  unique ID of the object that owns this buffer manager.
 *      */
 *     IDType ownerID() const {
 *         return fFinishedBufferInbox.uniqueID();
 *     }
 *
 *     /**
 *      * Let the manager know to expect a message with buffer 'b'. It's illegal for a buffer to be
 *      * inserted again before it is unmapped by process().
 *      */
 *     void insert(sk_sp<T> b) {
 *         SkDEBUGCODE(auto end = fClientHeldBuffers.end());
 *         SkASSERT(std::find(fClientHeldBuffers.begin(), end, b) == end);
 *         fClientHeldBuffers.emplace_front(std::move(b));
 *     }
 *
 *     /** Poll for messages and unmap any incoming buffers. */
 *     void process() {
 *         skia_private::STArray<4, BufferFinishedMessage> messages;
 *         fFinishedBufferInbox.poll(&messages);
 *         if (!fAbandoned) {
 *             for (auto& m : messages) {
 *                 this->remove(m.fBuffer);
 *                 m.fBuffer->unmap();
 *             }
 *         }
 *     }
 *
 *     /** Notifies the manager that the context has been abandoned. No more unmaps() will occur.*/
 *     void abandon() {
 *         fAbandoned = true;
 *         fClientHeldBuffers.clear();
 *     }
 *
 * private:
 *     typename BufferFinishedMessageBus::Inbox fFinishedBufferInbox;
 *     std::forward_list<sk_sp<T>> fClientHeldBuffers;
 *     bool fAbandoned = false;
 *
 *     void remove(const sk_sp<T>& b) {
 *         // There is no convenient remove only the first element that equals a value functionality in
 *         // std::forward_list.
 *         auto prev = fClientHeldBuffers.before_begin();
 *         auto end = fClientHeldBuffers.end();
 *         SkASSERT(std::find(fClientHeldBuffers.begin(), end, b) != end);
 *         for (auto cur = fClientHeldBuffers.begin(); cur != end; prev = cur++) {
 *             if (*cur == b) {
 *                 fClientHeldBuffers.erase_after(prev);
 *                 break;
 *             }
 *         }
 *         SkASSERT(std::find(fClientHeldBuffers.begin(), end, b) == end);
 *     }
 * }
 * ```
 */
public open class TClientMappedBufferManager<T, IDType> public constructor(
  ownerID: IDType,
) {
  /**
   * C++ original:
   * ```cpp
   * typename BufferFinishedMessageBus::Inbox fFinishedBufferInbox
   * ```
   */
  private var fFinishedBufferInbox: Inbox = TODO("Initialize fFinishedBufferInbox")

  /**
   * C++ original:
   * ```cpp
   * std::forward_list<sk_sp<T>> fClientHeldBuffers
   * ```
   */
  private var fClientHeldBuffers: Int = TODO("Initialize fClientHeldBuffers")

  /**
   * C++ original:
   * ```cpp
   * bool fAbandoned = false
   * ```
   */
  private var fAbandoned: Boolean = TODO("Initialize fAbandoned")

  /**
   * C++ original:
   * ```cpp
   * TClientMappedBufferManager(IDType ownerID)
   *             : fFinishedBufferInbox(ownerID) {}
   * ```
   */
  public constructor(param0: TClientMappedBufferManager<T, IDType>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * TClientMappedBufferManager& operator=(const TClientMappedBufferManager&) = delete
   * ```
   */
  public fun assign(param0: TClientMappedBufferManager<T, IDType>) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * TClientMappedBufferManager& operator=(TClientMappedBufferManager&&) = delete
   * ```
   */
  public fun ownerID(): IDType {
    TODO("Implement ownerID")
  }

  /**
   * C++ original:
   * ```cpp
   * IDType ownerID() const {
   *         return fFinishedBufferInbox.uniqueID();
   *     }
   * ```
   */
  public fun insert(b: SkSp<T>) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * void insert(sk_sp<T> b) {
   *         SkDEBUGCODE(auto end = fClientHeldBuffers.end());
   *         SkASSERT(std::find(fClientHeldBuffers.begin(), end, b) == end);
   *         fClientHeldBuffers.emplace_front(std::move(b));
   *     }
   * ```
   */
  public fun process() {
    TODO("Implement process")
  }

  /**
   * C++ original:
   * ```cpp
   * void process() {
   *         skia_private::STArray<4, BufferFinishedMessage> messages;
   *         fFinishedBufferInbox.poll(&messages);
   *         if (!fAbandoned) {
   *             for (auto& m : messages) {
   *                 this->remove(m.fBuffer);
   *                 m.fBuffer->unmap();
   *             }
   *         }
   *     }
   * ```
   */
  public fun abandon() {
    TODO("Implement abandon")
  }

  /**
   * C++ original:
   * ```cpp
   * void abandon() {
   *         fAbandoned = true;
   *         fClientHeldBuffers.clear();
   *     }
   * ```
   */
  private fun remove(b: SkSp<T>) {
    TODO("Implement remove")
  }

  public data class BufferFinishedMessage<T, IDType> public constructor(
    public var fBuffer: SkSp<T>,
    public var fIntendedRecipient: IDType,
  )
}
