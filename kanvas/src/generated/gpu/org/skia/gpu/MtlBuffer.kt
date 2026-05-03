package org.skia.gpu

import kotlin.Int
import kotlin.String
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class MtlBuffer : public Buffer {
 * public:
 *     static sk_sp<Buffer> Make(const MtlSharedContext*, size_t size, BufferType type, AccessPattern);
 *
 *     id<MTLBuffer> mtlBuffer() const { return fBuffer.get(); }
 *
 * private:
 *     MtlBuffer(const MtlSharedContext*, size_t size, sk_cfp<id<MTLBuffer>>);
 *
 *     void onMap() override;
 *     void onUnmap() override;
 *
 *     void freeGpuData() override;
 *
 *     void setBackendLabel(char const* label) override;
 *
 *     sk_cfp<id<MTLBuffer>> fBuffer;
 * }
 * ```
 */
public open class MtlBuffer public constructor(
  param0: MtlSharedContext,
  size: ULong,
) : Buffer() {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLBuffer>> fBuffer
   * ```
   */
  private var fBuffer: Int = TODO("Initialize fBuffer")

  /**
   * C++ original:
   * ```cpp
   * MtlBuffer(const MtlSharedContext*, size_t size, sk_cfp<id<MTLBuffer>>)
   * ```
   */
  public constructor(sharedContext: MtlSharedContext?, size: ULong) : this(TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * id<MTLBuffer> mtlBuffer() const { return fBuffer.get(); }
   * ```
   */
  public fun mtlBuffer(): Int {
    TODO("Implement mtlBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlBuffer::onMap() {
   *     SkASSERT(fBuffer);
   *     SkASSERT(!this->isMapped());
   *
   *     if ((*fBuffer).storageMode == MTLStorageModePrivate) {
   *         return;
   *     }
   *
   *     fMapPtr = static_cast<char*>((*fBuffer).contents);
   * }
   * ```
   */
  public override fun onMap() {
    TODO("Implement onMap")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlBuffer::onUnmap() {
   *     SkASSERT(fBuffer);
   *     SkASSERT(this->isMapped());
   * #ifdef SK_BUILD_FOR_MAC
   *     if ((*fBuffer).storageMode == MTLStorageModeManaged) {
   *         [*fBuffer didModifyRange: NSMakeRange(0, this->size())];
   *     }
   * #endif
   *     fMapPtr = nullptr;
   * }
   * ```
   */
  public override fun onUnmap() {
    TODO("Implement onUnmap")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlBuffer::freeGpuData() {
   *     fBuffer.reset();
   * }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlBuffer::setBackendLabel(char const* label) {
   *     SkASSERT(label);
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     NSString* labelStr = @(label);
   *     this->mtlBuffer().label = labelStr;
   * #endif
   * }
   * ```
   */
  public override fun setBackendLabel(label: String?) {
    TODO("Implement setBackendLabel")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Buffer> MtlBuffer::Make(const MtlSharedContext* sharedContext,
     *                               size_t size,
     *                               BufferType type,
     *                               AccessPattern accessPattern) {
     *     if (size <= 0) {
     *         return nullptr;
     *     }
     *
     *     NSUInteger options = 0;
     *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
     *         if (accessPattern == AccessPattern::kHostVisible) {
     * #ifdef SK_BUILD_FOR_MAC
     *             const MtlCaps& mtlCaps = sharedContext->mtlCaps();
     *             if (mtlCaps.isMac()) {
     *                 options |= MTLResourceStorageModeManaged;
     *             } else {
     *                 SkASSERT(mtlCaps.isApple());
     *                 options |= MTLResourceStorageModeShared;
     *             }
     * #else
     *             options |= MTLResourceStorageModeShared;
     * #endif
     *         } else {
     *             options |= MTLResourceStorageModePrivate;
     *         }
     *     }
     *
     *     sk_cfp<id<MTLBuffer>> buffer([sharedContext->device() newBufferWithLength:size
     *                                                                       options:options]);
     *
     *     return sk_sp<Buffer>(new MtlBuffer(sharedContext, size, std::move(buffer)));
     * }
     * ```
     */
    public fun make(
      sharedContext: MtlSharedContext?,
      size: ULong,
      type: BufferType,
      accessPattern: AccessPattern,
    ): Int {
      TODO("Implement make")
    }
  }
}
