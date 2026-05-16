package org.skia.gpu

import MTLTextureDescriptor
import kotlin.Int
import org.skia.foundation.SkSp
import undefined.MTLResourceOptions
import undefined.NSUInteger

/**
 * C++ original:
 * ```cpp
 * class MtlMemoryAllocatorImpl : public MtlMemoryAllocator {
 * public:
 *     static sk_sp<MtlMemoryAllocator> Make(id<MTLDevice>);
 *
 *     ~MtlMemoryAllocatorImpl() override {}
 *
 *     id<MTLBuffer> newBufferWithLength(NSUInteger length, MTLResourceOptions options,
 *                                       sk_sp<skgpu::MtlAlloc>* allocation) override;
 *     id<MTLTexture> newTextureWithDescriptor(MTLTextureDescriptor* texDesc,
 *                                             sk_sp<skgpu::MtlAlloc>* allocation) override;
 *
 *     class Alloc : public MtlAlloc {
 *     public:
 *         Alloc() {}
 *         ~Alloc() override {
 *             // TODO: free allocation
 *         }
 *     private:
 *         friend class MtlMemoryAllocatorImpl;
 *         // TODO: allocation data goes here
 *     };
 *
 * private:
 *     MtlMemoryAllocatorImpl(id<MTLDevice> device) : fDevice(device) {}
 *
 *     id<MTLDevice> fDevice;
 * }
 * ```
 */
public open class MtlMemoryAllocatorImpl : MtlMemoryAllocator() {
  /**
   * C++ original:
   * ```cpp
   * id<MTLBuffer> MtlMemoryAllocatorImpl::newBufferWithLength(NSUInteger length,
   *                                                           MTLResourceOptions options,
   *                                                           sk_sp<MtlAlloc>* allocation) {
   *     // TODO: suballocate and fill in Alloc
   *     allocation->reset(new Alloc());
   *     return [fDevice newBufferWithLength:length options:options];
   * }
   * ```
   */
  public override fun newBufferWithLength(
    length: NSUInteger,
    options: MTLResourceOptions,
    allocation: SkSp<MtlAlloc>?,
  ): Int {
    TODO("Implement newBufferWithLength")
  }

  /**
   * C++ original:
   * ```cpp
   * id<MTLTexture> MtlMemoryAllocatorImpl::newTextureWithDescriptor(MTLTextureDescriptor* texDesc,
   *                                                                 sk_sp<MtlAlloc>* allocation) {
   *     // TODO: suballocate and fill in Alloc
   *     allocation->reset(new Alloc());
   *     return [fDevice newTextureWithDescriptor:texDesc];
   * }
   * ```
   */
  public override fun newTextureWithDescriptor(texDesc: MTLTextureDescriptor?, allocation: SkSp<MtlAlloc>?): Int {
    TODO("Implement newTextureWithDescriptor")
  }

  public open class Alloc public constructor() : MtlAlloc()

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlMemoryAllocator> MtlMemoryAllocatorImpl::Make(id<MTLDevice> device) {
     *     return sk_sp<MtlMemoryAllocator>(new MtlMemoryAllocatorImpl(device));
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
