package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class Buffer : public Resource {
 * public:
 *     size_t size() const { return fSize; }
 *     Protected isProtected() const { return fIsProtected; }
 *
 *     // TODO(b/262249983): Separate into mapRead(), mapWrite() methods.
 *     // If the buffer is already mapped then pointer is returned. If an asyncMap() was started then
 *     // it is waited on. Otherwise, a synchronous map is performed.
 *     void* map();
 *     // Starts a new asynchronous map.
 *     void asyncMap(GpuFinishedProc = nullptr, GpuFinishedContext = nullptr);
 *     // If the buffer is mapped then unmaps. If an async map is pending then it is cancelled.
 *     void unmap();
 *
 *     bool isMapped() const { return fMapPtr; }
 *
 *     // Returns true if mapped or an asyncMap was started and hasn't been completed or canceled.
 *     virtual bool isUnmappable() const;
 *
 *     const char* getResourceType() const override { return "Buffer"; }
 *
 * protected:
 *     Buffer(const SharedContext* sharedContext,
 *            size_t size,
 *            Protected isProtected,
 *            bool reusableRequiresPurgeable = false,
 *            bool requiresPrepareForReturnToCache = false)
 *             : Resource(sharedContext,
 *                        Ownership::kOwned,
 *                        size,
 *                        reusableRequiresPurgeable,
 *                        requiresPrepareForReturnToCache)
 *             , fSize(size)
 *             , fIsProtected(isProtected) {}
 *
 *     void* fMapPtr = nullptr;
 *
 * private:
 *     virtual void onMap() = 0;
 *     virtual void onAsyncMap(GpuFinishedProc, GpuFinishedContext);
 *     virtual void onUnmap() = 0;
 *
 *     size_t    fSize;
 *     Protected fIsProtected;
 * }
 * ```
 */
public abstract class Buffer public constructor(
  sharedContext: SharedContext?,
  size: ULong,
  isProtected: Protected,
  reusableRequiresPurgeable: Boolean = TODO(),
  requiresPrepareForReturnToCache: Boolean = TODO(),
) : Resource(TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void* fMapPtr = nullptr
   * ```
   */
  protected var fMapPtr: Unit? = TODO("Initialize fMapPtr")

  /**
   * C++ original:
   * ```cpp
   * size_t    fSize
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * Protected fIsProtected
   * ```
   */
  private var fIsProtected: Protected = TODO("Initialize fIsProtected")

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fSize; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const { return fIsProtected; }
   * ```
   */
  public fun isProtected(): Protected {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * void* Buffer::map() {
   *     SkASSERT(this->isUnmappable() || !this->sharedContext()->caps()->bufferMapsAreAsync());
   *     SkASSERT(this->isProtected() == Protected::kNo);
   *     if (!this->isMapped()) {
   *         this->onMap();
   *     }
   *     return fMapPtr;
   * }
   * ```
   */
  public fun map() {
    TODO("Implement map")
  }

  /**
   * C++ original:
   * ```cpp
   * void Buffer::asyncMap(GpuFinishedProc proc, GpuFinishedContext ctx) {
   *     SkASSERT(this->sharedContext()->caps()->bufferMapsAreAsync());
   *     SkASSERT(this->isProtected() == Protected::kNo);
   *     this->onAsyncMap(proc, ctx);
   * }
   * ```
   */
  public fun asyncMap(proc: GpuFinishedProc = TODO(), ctx: GpuFinishedContext = TODO()) {
    TODO("Implement asyncMap")
  }

  /**
   * C++ original:
   * ```cpp
   * void Buffer::unmap() {
   *     SkASSERT(this->isUnmappable());
   *     this->onUnmap();
   *     fMapPtr = nullptr;
   * }
   * ```
   */
  public fun unmap() {
    TODO("Implement unmap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isMapped() const { return fMapPtr; }
   * ```
   */
  public fun isMapped(): Boolean {
    TODO("Implement isMapped")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Buffer::isUnmappable() const { return isMapped(); }
   * ```
   */
  public open fun isUnmappable(): Boolean {
    TODO("Implement isUnmappable")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Buffer"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onMap() = 0
   * ```
   */
  private abstract fun onMap()

  /**
   * C++ original:
   * ```cpp
   * void Buffer::onAsyncMap(skgpu::graphite::GpuFinishedProc, skgpu::graphite::GpuFinishedContext) {
   *     SkASSERT(!this->sharedContext()->caps()->bufferMapsAreAsync());
   *     SK_ABORT("Async buffer mapping not supported");
   * }
   * ```
   */
  public open fun onAsyncMap(param0: GpuFinishedProc, param1: GpuFinishedContext) {
    TODO("Implement onAsyncMap")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onUnmap() = 0
   * ```
   */
  private abstract fun onUnmap()
}
