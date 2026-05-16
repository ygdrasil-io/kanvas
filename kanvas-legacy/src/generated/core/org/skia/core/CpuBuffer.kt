package org.skia.core

import Base
import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext

/**
 * C++ original:
 * ```cpp
 * class CpuBuffer final : public Base {
 * public:
 *     ~CpuBuffer() override = default;
 *
 *     static sk_sp<Base> Make(const void* data, size_t size);
 *
 *     const void* peek() const override { return fData->data(); }
 *
 *     size_t size() const override { return fData->size(); }
 *
 * private:
 *     CpuBuffer(sk_sp<SkData> data) : fData(std::move(data)) {}
 *
 *     bool onUpdate(GrDirectContext*, const void* data, size_t offset, size_t size) override;
 *
 *     sk_sp<SkData> fData;
 * }
 * ```
 */
public class CpuBuffer<Base> public constructor(
  `data`: SkSp<SkData>,
) : Base() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  private var fData: SkSp<SkData> = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * const void* peek() const override { return fData->data(); }
   * ```
   */
  public override fun peek() {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const override { return fData->size(); }
   * ```
   */
  public override fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename Base> bool SkMeshPriv::CpuBuffer<Base>::onUpdate(GrDirectContext* dc,
   *                                                                     const void* data,
   *                                                                     size_t offset,
   *                                                                     size_t size) {
   *     if (dc) {
   *         return false;
   *     }
   *     std::memcpy(SkTAddOffset<void>(fData->writable_data(), offset), data, size);
   *     return true;
   * }
   * ```
   */
  public override fun onUpdate(
    dc: GrDirectContext?,
    `data`: Unit?,
    offset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement onUpdate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * template <typename Base> sk_sp<Base> SkMeshPriv::CpuBuffer<Base>::Make(const void* data,
     *                                                                        size_t size) {
     *     SkASSERT(size);
     *     sk_sp<SkData> storage;
     *     if (data) {
     *         storage = SkData::MakeWithCopy(data, size);
     *     } else {
     *         storage = SkData::MakeZeroInitialized(size);
     *     }
     *     return sk_sp<Base>(new CpuBuffer<Base>(std::move(storage)));
     * }
     * ```
     */
    public fun make(`data`: Unit?, size: ULong): SkSp<Base> {
      TODO("Implement make")
    }
  }
}

public typealias CpuIndexBuffer = CpuBuffer<IB>

public typealias CpuVertexBuffer = CpuBuffer<VB>
