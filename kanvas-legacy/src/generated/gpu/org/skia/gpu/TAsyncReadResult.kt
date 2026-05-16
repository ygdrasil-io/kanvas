package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.STArray
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import undefined.IDType

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename IDType, typename TransferResultType>
 * class TAsyncReadResult : public SkImage::AsyncReadResult {
 * public:
 *     TAsyncReadResult(IDType intendedRecipient)
 *         : fIntendedRecipient(intendedRecipient) {
 *     }
 *
 *     ~TAsyncReadResult() override {
 *         for (int i = 0; i < fPlanes.size(); ++i) {
 *             fPlanes[i].releaseMappedBuffer(fIntendedRecipient);
 *         }
 *     }
 *
 *     int count() const override { return fPlanes.size(); }
 *     const void* data(int i) const override { return fPlanes[i].data(); }
 *     size_t rowBytes(int i) const override { return fPlanes[i].rowBytes(); }
 *
 *     bool addTransferResult(const TransferResultType& result,
 *                            SkISize dimensions,
 *                            size_t rowBytes,
 *                            TClientMappedBufferManager<T, IDType>* manager) {
 *         const void* mappedData = result.fTransferBuffer->map();
 *         if (!mappedData) {
 *             return false;
 *         }
 *         if (result.fPixelConverter) {
 *             size_t size = rowBytes*dimensions.height();
 *             sk_sp<SkData> data = SkData::MakeUninitialized(size);
 *             result.fPixelConverter(data->writable_data(), mappedData);
 *             this->addCpuPlane(std::move(data), rowBytes);
 *             result.fTransferBuffer->unmap();
 *         } else {
 *             manager->insert(result.fTransferBuffer);
 *             this->addMappedPlane(mappedData, rowBytes, std::move(result.fTransferBuffer));
 *         }
 *         return true;
 *     }
 *
 *     void addCpuPlane(sk_sp<SkData> data, size_t rowBytes) {
 *         SkASSERT(data);
 *         SkASSERT(rowBytes > 0);
 *         fPlanes.emplace_back(std::move(data), rowBytes);
 *     }
 *
 * private:
 *     void addMappedPlane(const void* data, size_t rowBytes, sk_sp<T> mappedBuffer) {
 *         SkASSERT(data);
 *         SkASSERT(rowBytes > 0);
 *         SkASSERT(mappedBuffer);
 *         SkASSERT(mappedBuffer->isMapped());
 *         fPlanes.emplace_back(std::move(mappedBuffer), rowBytes);
 *     }
 *
 *     class Plane {
 *     public:
 *         Plane(sk_sp<T> buffer, size_t rowBytes)
 *                 : fMappedBuffer(std::move(buffer)), fRowBytes(rowBytes) {}
 *         Plane(sk_sp<SkData> data, size_t rowBytes) : fData(std::move(data)), fRowBytes(rowBytes) {}
 *
 *         Plane(Plane&&) = default;
 *
 *         ~Plane() { SkASSERT(!fMappedBuffer); }
 *
 *         Plane& operator=(const Plane&) = delete;
 *         Plane& operator=(Plane&&) = default;
 *
 *         void releaseMappedBuffer(IDType intendedRecipient) {
 *             if (fMappedBuffer) {
 *                 TClientMappedBufferManager<T, IDType>::BufferFinishedMessageBus::Post(
 *                         {std::move(fMappedBuffer), intendedRecipient});
 *             }
 *         }
 *
 *         const void* data() const {
 *             if (fMappedBuffer) {
 *                 SkASSERT(!fData);
 *                 SkASSERT(fMappedBuffer->isMapped());
 *                 return fMappedBuffer->map();
 *             }
 *             SkASSERT(fData);
 *             return fData->data();
 *         }
 *
 *         size_t rowBytes() const { return fRowBytes; }
 *
 *     private:
 *         sk_sp<SkData> fData;
 *         sk_sp<T> fMappedBuffer;
 *         size_t fRowBytes;
 *     };
 *     skia_private::STArray<4, Plane> fPlanes;
 *     IDType fIntendedRecipient;
 * }
 * ```
 */
public open class TAsyncReadResult<T, IDType, TransferResultType> public constructor(
  intendedRecipient: IDType,
) : SkImage.AsyncReadResult() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<4, Plane> fPlanes
   * ```
   */
  private var fPlanes: STArray<org.skia.modules.Plane> = TODO("Initialize fPlanes")

  /**
   * C++ original:
   * ```cpp
   * IDType fIntendedRecipient
   * ```
   */
  private var fIntendedRecipient: IDType = TODO("Initialize fIntendedRecipient")

  /**
   * C++ original:
   * ```cpp
   * int count() const override { return fPlanes.size(); }
   * ```
   */
  public override fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* data(int i) const override { return fPlanes[i].data(); }
   * ```
   */
  public override fun `data`(i: Int) {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t rowBytes(int i) const override { return fPlanes[i].rowBytes(); }
   * ```
   */
  public override fun rowBytes(i: Int): Int {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * bool addTransferResult(const TransferResultType& result,
   *                            SkISize dimensions,
   *                            size_t rowBytes,
   *                            TClientMappedBufferManager<T, IDType>* manager) {
   *         const void* mappedData = result.fTransferBuffer->map();
   *         if (!mappedData) {
   *             return false;
   *         }
   *         if (result.fPixelConverter) {
   *             size_t size = rowBytes*dimensions.height();
   *             sk_sp<SkData> data = SkData::MakeUninitialized(size);
   *             result.fPixelConverter(data->writable_data(), mappedData);
   *             this->addCpuPlane(std::move(data), rowBytes);
   *             result.fTransferBuffer->unmap();
   *         } else {
   *             manager->insert(result.fTransferBuffer);
   *             this->addMappedPlane(mappedData, rowBytes, std::move(result.fTransferBuffer));
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun addTransferResult(
    result: TransferResultType,
    dimensions: SkISize,
    rowBytes: ULong,
    manager: TClientMappedBufferManager<T, IDType>?,
  ): Boolean {
    TODO("Implement addTransferResult")
  }

  /**
   * C++ original:
   * ```cpp
   * void addCpuPlane(sk_sp<SkData> data, size_t rowBytes) {
   *         SkASSERT(data);
   *         SkASSERT(rowBytes > 0);
   *         fPlanes.emplace_back(std::move(data), rowBytes);
   *     }
   * ```
   */
  public fun addCpuPlane(`data`: SkSp<SkData>, rowBytes: ULong) {
    TODO("Implement addCpuPlane")
  }

  /**
   * C++ original:
   * ```cpp
   * void addMappedPlane(const void* data, size_t rowBytes, sk_sp<T> mappedBuffer) {
   *         SkASSERT(data);
   *         SkASSERT(rowBytes > 0);
   *         SkASSERT(mappedBuffer);
   *         SkASSERT(mappedBuffer->isMapped());
   *         fPlanes.emplace_back(std::move(mappedBuffer), rowBytes);
   *     }
   * ```
   */
  private fun addMappedPlane(
    `data`: Unit?,
    rowBytes: ULong,
    mappedBuffer: SkSp<T>,
  ) {
    TODO("Implement addMappedPlane")
  }

  public data class Plane<T> public constructor(
    private var fData: SkSp<SkData>,
    private var fMappedBuffer: SkSp<T>,
    private var fRowBytes: Int,
  ) {
    public fun assign(param0: org.skia.modules.Plane) {
      TODO("Implement assign")
    }

    public fun releaseMappedBuffer(intendedRecipient: IDType) {
      TODO("Implement releaseMappedBuffer")
    }

    public fun `data`() {
      TODO("Implement data")
    }

    public fun rowBytes(): Int {
      TODO("Implement rowBytes")
    }
  }
}
