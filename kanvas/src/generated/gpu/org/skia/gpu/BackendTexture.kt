package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.math.SkISize
import undefined.SomeBackendTextureData

/**
 * C++ original:
 * ```cpp
 * class SK_API BackendTexture {
 * public:
 *     BackendTexture();
 *     BackendTexture(const BackendTexture&);
 *
 *     ~BackendTexture();
 *
 *     BackendTexture& operator=(const BackendTexture&);
 *
 *     bool operator==(const BackendTexture&) const;
 *     bool operator!=(const BackendTexture& that) const { return !(*this == that); }
 *
 *     bool isValid() const { return fInfo.isValid(); }
 *     BackendApi backend() const { return fInfo.backend(); }
 *
 *     SkISize dimensions() const { return fDimensions; }
 *
 *     const TextureInfo& info() const { return fInfo; }
 *
 * private:
 *     friend class BackendTextureData;
 *     friend class BackendTexturePriv;
 *
 *     // Size determined by looking at the BackendTextureData subclasses, then guessing-and-checking.
 *     // Compiler will complain if this is too small - in that case, just increase the number.
 *     inline constexpr static size_t kMaxSubclassSize = 72;
 *     using AnyBackendTextureData = SkAnySubclass<BackendTextureData, kMaxSubclassSize>;
 *
 *     template <typename SomeBackendTextureData>
 *     BackendTexture(SkISize dimensions, TextureInfo info, const SomeBackendTextureData& textureData)
 *             : fDimensions(dimensions), fInfo(info) {
 *         fTextureData.emplace<SomeBackendTextureData>(textureData);
 *     }
 *
 *     SkISize fDimensions;
 *     TextureInfo fInfo;
 *     AnyBackendTextureData fTextureData;
 * }
 * ```
 */
public open class BackendTexture public constructor() {
  /**
   * C++ original:
   * ```cpp
   * inline constexpr static size_t kMaxSubclassSize = 72
   * ```
   */
  private var fDimensions: Int = TODO("Initialize fDimensions")

  /**
   * C++ original:
   * ```cpp
   * SkISize fDimensions
   * ```
   */
  private var fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * TextureInfo fInfo
   * ```
   */
  private var fTextureData: BackendTextureData = TODO("Initialize fTextureData")

  /**
   * C++ original:
   * ```cpp
   * BackendTexture::BackendTexture()
   * ```
   */
  public constructor(that: BackendTexture) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendTexture::BackendTexture(const BackendTexture& that) {
   *     *this = that;
   * }
   * ```
   */
  public constructor(
    dimensions: SkISize,
    info: TextureInfo,
    textureData: SomeBackendTextureData,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendTexture& BackendTexture::operator=(const BackendTexture& that) {
   *     if (!that.isValid()) {
   *         fInfo = {};
   *         return *this;
   *     }
   *     // We shouldn't be mixing backends.
   *     SkASSERT(!this->isValid() || this->backend() == that.backend());
   *     // If that was valid, it should have a supported backend.
   *     assert_is_supported_backend(that.backend());
   *     fDimensions = that.fDimensions;
   *     fInfo = that.fInfo;
   *
   *     fTextureData.reset();
   *     that.fTextureData->copyTo(fTextureData);
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: BackendTexture) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool BackendTexture::operator==(const BackendTexture& that) const {
   *     if (!this->isValid() || !that.isValid()) {
   *         return false;
   *     }
   *
   *     if (fDimensions != that.fDimensions || fInfo != that.fInfo) {
   *         return false;
   *     }
   *     assert_is_supported_backend(this->backend());
   *     return fTextureData->equal(that.fTextureData.get());
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const BackendTexture& that) const { return !(*this == that); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fInfo.isValid(); }
   * ```
   */
  public fun backend(): BackendApi {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi backend() const { return fInfo.backend(); }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fDimensions; }
   * ```
   */
  public fun info(): Int {
    TODO("Implement info")
  }

  public companion object {
    private val kMaxSubclassSize: ULong = TODO("Initialize kMaxSubclassSize")
  }
}
