package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import undefined.AnyBackendSemaphoreData
import undefined.CFTypeRef

/**
 * C++ original:
 * ```cpp
 * class MtlBackendSemaphoreData final : public BackendSemaphoreData {
 * public:
 *     MtlBackendSemaphoreData(CFTypeRef mtlEvent, uint64_t value)
 *             : fMtlEvent(mtlEvent), fMtlValue(value) {}
 *
 * #if defined(SK_DEBUG)
 *     skgpu::BackendApi type() const override { return skgpu::BackendApi::kMetal; }
 * #endif
 *
 *     CFTypeRef event() const { return fMtlEvent; }
 *     uint64_t value() const { return fMtlValue; }
 *
 * private:
 *     CFTypeRef fMtlEvent;  // Expected to be an id<MTLEvent>
 *     uint64_t fMtlValue;
 *
 *     void copyTo(AnyBackendSemaphoreData& dstData) const override {
 *         // Don't assert that dstData is a metal type because it could be
 *         // uninitialized and that assert would fail.
 *         dstData.emplace<MtlBackendSemaphoreData>(fMtlEvent, fMtlValue);
 *     }
 * }
 * ```
 */
public class MtlBackendSemaphoreData public constructor(
  mtlEvent: CFTypeRef,
  `value`: ULong,
) : BackendSemaphoreData() {
  /**
   * C++ original:
   * ```cpp
   * CFTypeRef fMtlEvent
   * ```
   */
  private var fMtlEvent: Int = TODO("Initialize fMtlEvent")

  /**
   * C++ original:
   * ```cpp
   * uint64_t fMtlValue
   * ```
   */
  private var fMtlValue: Int = TODO("Initialize fMtlValue")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi type() const override { return skgpu::BackendApi::kMetal; }
   * ```
   */
  public override fun type(): BackendApi {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * CFTypeRef event() const { return fMtlEvent; }
   * ```
   */
  public fun event(): Int {
    TODO("Implement event")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t value() const { return fMtlValue; }
   * ```
   */
  public fun `value`(): Int {
    TODO("Implement value")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyTo(AnyBackendSemaphoreData& dstData) const override {
   *         // Don't assert that dstData is a metal type because it could be
   *         // uninitialized and that assert would fail.
   *         dstData.emplace<MtlBackendSemaphoreData>(fMtlEvent, fMtlValue);
   *     }
   * ```
   */
  public override fun copyTo(dstData: AnyBackendSemaphoreData) {
    TODO("Implement copyTo")
  }
}
