package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import undefined.AnyTextureInfoData

/**
 * C++ original:
 * ```cpp
 * class SK_API TextureInfo {
 * private:
 *     class Data;
 *     friend class MtlTextureInfo;
 *     friend class DawnTextureInfo;
 *     friend class VulkanTextureInfo;
 *
 *     // Size is the largest of the Data subclasses assuming a 64-bit compiler.
 *     inline constexpr static size_t kMaxSubclassSize = 112;
 *     using AnyTextureInfoData = SkAnySubclass<Data, kMaxSubclassSize>;
 *
 *     // Base properties for all backend-specific properties. Clients managing textures directly
 *     // should use the public subclasses of Data directly, e.g. MtlTextureInfo/DawnTextureInfo.
 *     //
 *     // Each backend subclass must expose to TextureInfo[Priv]:
 *     //   static constexpr BackendApi kBackend;
 *     //   Protected isProtected() const;
 *     //   TextureFormat viewFormat() const;
 *     class Data {
 *     public:
 *         virtual ~Data() = default;
 *
 *         Data(SampleCount sampleCount, skgpu::Mipmapped mipmapped)
 *                 : fSampleCount(sampleCount)
 *                 , fMipmapped(mipmapped) {}
 *
 *         Data() = default;
 *         Data(const Data&) = default;
 *
 *         Data& operator=(const Data&) = default;
 *
 *         // NOTE: These fields are accessible via the backend-specific subclasses.
 *         SampleCount fSampleCount = SampleCount::k1;
 *         Mipmapped fMipmapped = Mipmapped::kNo;
 *
 *     private:
 *         friend class TextureInfo;
 *         friend class TextureInfoPriv;
 *
 *         virtual SkString toBackendString() const = 0;
 *
 *         virtual void copyTo(AnyTextureInfoData&) const = 0;
 *         // Passed in TextureInfo will have data of the same backend type and subclass, and
 *         // base properties of Data have already been checked for equality/compatibility.
 *         virtual bool isCompatible(const TextureInfo& that, bool requireExact) const = 0;
 *     };
 *
 * public:
 *     TextureInfo() = default;
 *     ~TextureInfo() = default;
 *
 *     TextureInfo(const TextureInfo&);
 *     TextureInfo& operator=(const TextureInfo&);
 *
 *     bool operator==(const TextureInfo& that) const {
 *         return this->isCompatible(that, /*requireExact=*/true);
 *     }
 *     bool operator!=(const TextureInfo& that) const { return !(*this == that); }
 *
 *     bool isValid() const { return fData.has_value(); }
 *     BackendApi backend() const {
 *         SkASSERT(fData.has_value() || fBackend == BackendApi::kUnsupported);
 *         return fBackend;
 *     }
 *
 *     Protected isProtected() const { return fProtected; }
 *     SampleCount sampleCount() const {
 *         return fData.has_value() ? fData->fSampleCount : SampleCount::k1;
 *     }
 *     Mipmapped mipmapped() const {
 *         return fData.has_value() ? fData->fMipmapped   : Mipmapped::kNo;
 *     }
 *
 *     // Return true if `that` describes a texture that is compatible with this info and can validly
 *     // be used to fulfill a promise image that was created with this TextureInfo.
 *     bool canBeFulfilledBy(const TextureInfo& that) const {
 *         return this->isCompatible(that, /*requireExact=*/false);
 *     }
 *
 *     // Return a string containing the full description of this TextureInfo.
 *     SkString toString() const;
 *
 * private:
 *     friend class TextureInfoPriv;
 *
 *     template <typename BackendTextureData,
 *               std::enable_if_t<std::is_base_of_v<Data, BackendTextureData>, bool> = true>
 *     explicit TextureInfo(const BackendTextureData& data)
 *             : fBackend(BackendTextureData::kBackend)
 *             , fViewFormat(data.viewFormat())
 *             , fProtected(data.isProtected()) {
 *         fData.emplace<BackendTextureData>(data);
 *     }
 *
 *     bool isCompatible(const TextureInfo& that, bool requireExact) const;
 *
 *     skgpu::BackendApi  fBackend = BackendApi::kUnsupported;
 *     AnyTextureInfoData fData;
 *
 *     // Derived properties from the backend data, cached to avoid a virtual function call
 *     TextureFormat fViewFormat;
 *     Protected fProtected = Protected::kNo;
 * }
 * ```
 */
public data class TextureInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline constexpr static size_t kMaxSubclassSize = 112
   * ```
   */
  private var fBackend: Int,
  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi  fBackend
   * ```
   */
  private var fData: Int,
  /**
   * C++ original:
   * ```cpp
   * AnyTextureInfoData fData
   * ```
   */
  private var fViewFormat: TextureFormat,
  /**
   * C++ original:
   * ```cpp
   * TextureFormat fViewFormat
   * ```
   */
  private var fProtected: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TextureInfo& TextureInfo::operator=(const TextureInfo& that) {
   *     if (this != &that) {
   *         this->~TextureInfo();
   *         new (this) TextureInfo(that);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: TextureInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const TextureInfo& that) const {
   *         return this->isCompatible(that, /*requireExact=*/true);
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const TextureInfo& that) const { return !(*this == that); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fData.has_value(); }
   * ```
   */
  public fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi backend() const {
   *         SkASSERT(fData.has_value() || fBackend == BackendApi::kUnsupported);
   *         return fBackend;
   *     }
   * ```
   */
  public fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const { return fProtected; }
   * ```
   */
  public fun sampleCount(): Int {
    TODO("Implement sampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleCount sampleCount() const {
   *         return fData.has_value() ? fData->fSampleCount : SampleCount::k1;
   *     }
   * ```
   */
  public fun mipmapped(): Int {
    TODO("Implement mipmapped")
  }

  /**
   * C++ original:
   * ```cpp
   * Mipmapped mipmapped() const {
   *         return fData.has_value() ? fData->fMipmapped   : Mipmapped::kNo;
   *     }
   * ```
   */
  public fun canBeFulfilledBy(that: TextureInfo): Boolean {
    TODO("Implement canBeFulfilledBy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canBeFulfilledBy(const TextureInfo& that) const {
   *         return this->isCompatible(that, /*requireExact=*/false);
   *     }
   * ```
   */
  public override fun toString(): Int {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString TextureInfo::toString() const {
   *     if (!this->isValid()) {
   *         return SkString("{}");
   *     }
   *
   *     // Strip the leading "k" from the enum name when creating the TextureInfo string.
   *     SkASSERT(BackendApiToStr(fBackend)[0] == 'k');
   *     const char* backendName = BackendApiToStr(fBackend) + 1;
   *
   *     return SkStringPrintf("%s(viewFormat=%s,%s,bpp=%zu,sampleCount=%u,mipmapped=%d,protected=%d)",
   *                           backendName,
   *                           TextureFormatName(fViewFormat),
   *                           fData->toBackendString().c_str(),
   *                           TextureFormatBytesPerBlock(fViewFormat),
   *                           (unsigned) fData->fSampleCount,
   *                           static_cast<int>(fData->fMipmapped),
   *                           static_cast<int>(fProtected));
   * }
   * ```
   */
  public fun isCompatible(that: TextureInfo, requireExact: Boolean): Boolean {
    TODO("Implement isCompatible")
  }

  public abstract class Data public constructor(
    sampleCount: SampleCount,
    mipmapped: Mipmapped,
  ) {
    public var fSampleCount: Int = TODO("Initialize fSampleCount")

    public var fMipmapped: Int = TODO("Initialize fMipmapped")

    public constructor() : this() {
      TODO("Implement constructor")
    }

    public constructor(param0: undefined.Data) : this() {
      TODO("Implement constructor")
    }

    public fun assign(param0: undefined.Data) {
      TODO("Implement assign")
    }

    private abstract fun toBackendString(): Int

    private abstract fun copyTo(param0: AnyTextureInfoData)

    private abstract fun isCompatible(that: TextureInfo, requireExact: Boolean): Boolean
  }

  public companion object {
    private val kMaxSubclassSize: Int = TODO("Initialize kMaxSubclassSize")
  }
}
