package org.skia.gpu

import MTLTextureDescriptor
import kotlin.Any
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import undefined.MTLResourceOptions
import undefined.NSUInteger

/**
 * C++ original:
 * ```cpp
 * class MtlMemoryAllocator : public SkRefCnt {
 * public:
 *     virtual id<MTLBuffer> newBufferWithLength(NSUInteger length, MTLResourceOptions options,
 *                                               sk_sp<MtlAlloc>* allocation) = 0;
 *     virtual id<MTLTexture> newTextureWithDescriptor(MTLTextureDescriptor* texDesc,
 *                                                     sk_sp<MtlAlloc>* allocation) = 0;
 * }
 * ```
 */
public abstract class MtlMemoryAllocator : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual id<MTLBuffer> newBufferWithLength(NSUInteger length, MTLResourceOptions options,
   *                                               sk_sp<MtlAlloc>* allocation) = 0
   * ```
   */
  public abstract fun newBufferWithLength(
    length: NSUInteger,
    options: MTLResourceOptions,
    allocation: SkSp<MtlAlloc>?,
  ): Any

  /**
   * C++ original:
   * ```cpp
   * virtual id<MTLTexture> newTextureWithDescriptor(MTLTextureDescriptor* texDesc,
   *                                                     sk_sp<MtlAlloc>* allocation) = 0
   * ```
   */
  public abstract fun newTextureWithDescriptor(texDesc: MTLTextureDescriptor?, allocation: SkSp<MtlAlloc>?): Any
}
